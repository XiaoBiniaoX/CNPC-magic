package top.cnpcmagic.casting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.ScriptMagic;
import top.cnpcmagic.server.NpcAnimationSync;
import java.util.*;

/** All entry points use this server-thread lifecycle; the existing scheduler still owns its tick cadence. */
public final class CastingService {
    public record Context(UUID instanceId, LivingEntity caster, String source, String spellId,
                          int level, SpellTarget target, double power) {}
    public record Attempt(SpellResult result, MagicCast cast) {}
    public interface Resource {
        boolean canPay(LivingEntity caster, double amount);
        void pay(LivingEntity caster, double amount);
    }
    public static final Map<String, Resource> RESOURCES = new LinkedHashMap<>();
    private static final Map<UUID, Session> ACTIVE = new LinkedHashMap<>();
    private static final LinkedHashMap<UUID, SpellResult> FINISHED = new LinkedHashMap<>();
    private static final Map<LivingEntity, Map<String, Long>> COOLDOWNS = new WeakHashMap<>();
    public record Completion(String spellId, long tick, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<LivingEntity, Completion> LAST_SUCCESS = new WeakHashMap<>();
    public static Completion lastSuccess(LivingEntity caster) { return LAST_SUCCESS.get(caster); }
    static {
        RESOURCES.put("health", new Resource() {
            public boolean canPay(LivingEntity c, double n) { return c.getHealth() > n; }
            public void pay(LivingEntity c, double n) { c.setHealth(c.getHealth() - (float)n); }
        });
    }
    private CastingService() {}
    public static Attempt start(LivingEntity caster, String spellId, int level, SpellTarget target, String source) {
        return start(caster, spellId, level, target, source, 1);
    }
    public static Attempt start(LivingEntity caster, String spellId, int level, SpellTarget target, String source, double power) {
        if (caster == null || !(caster.level() instanceof ServerLevel world) || !world.getServer().isSameThread())
            return failed("INVALID_CASTER", "A server-side living caster is required");
        if (!caster.isAlive() || caster.isRemoved()) return failed("INVALID_CASTER", "Caster is dead or removed");
        if (target == null || target.kind() == null || target.position() == null || !Double.isFinite(power) || power <= 0 || power > 100) return failed("INVALID_TARGET", "Invalid target or power");
        if (!Double.isFinite(target.position().x) || !Double.isFinite(target.position().y) || !Double.isFinite(target.position().z)
            || !Double.isFinite(target.radius()) || target.radius() < 0 || target.radius() > 16)
            return failed("INVALID_TARGET", "Target coordinates must be finite; area radius is 0–16");
        LivingEntity victim = target.entity();
        if (target.kind() == SpellTarget.Kind.SELF && victim != caster) return failed("INVALID_TARGET", "Self target must be the caster");
        if ((target.kind() == SpellTarget.Kind.ENTITY || target.kind() == SpellTarget.Kind.SELF)
            && (victim == null || !victim.isAlive() || victim.isRemoved() || victim.level() != world))
            return failed("INVALID_TARGET", "Target must be alive in the caster's dimension");
        if (target.kind() == SpellTarget.Kind.DIRECTION && target.position().lengthSqr() < 0.000001)
            return failed("INVALID_TARGET", "Direction cannot be zero");
        MagicProvider provider = MagicProviders.forSpell(spellId);
        if (provider == null) return failed("ADAPTER_UNAVAILABLE", "Spell provider is not installed");
        if (!provider.isValidSpell(spellId)) return failed("UNKNOWN_SPELL", "Spell is missing, disabled or unsupported");
        if (level < 1 || level > provider.getMaxLevel(spellId)) return failed("INVALID_LEVEL", "Level is outside the spell's supported range");
        boolean builtin = provider.getId().equals("cnpcmagic");
        SpellDefinition definition = builtin ? SpellLibrary.get(world.getServer()).spells.get(spellId) : null;
        if (builtin && definition == null) return failed("UNKNOWN_SPELL", "Spell definition is missing");
        if (definition == null && victim == null) return failed("UNSUPPORTED_TARGET", "This adapter supports living entity targets only");
        double cost = definition == null ? 0 : definition.healthCost;
        Resource resource = RESOURCES.get(definition == null ? "health" : definition.resource);
        if (definition != null) {
            if (!definition.enabled) return failed("DISABLED", "Spell is disabled");
            if (cooldown(caster, spellId) > 0) return failed("COOLDOWN", "Spell is cooling down");
            if (resource == null) return failed("RESOURCE_UNAVAILABLE", "Resource provider is unavailable");
            if (!resource.canPay(caster, cost)) return failed("INSUFFICIENT_RESOURCE", "Not enough " + definition.resource);
            if (target.kind() != SpellTarget.Kind.NONE && target.kind() != SpellTarget.Kind.DIRECTION) {
                var p = victim == null ? target.position() : victim.position();
                if (caster.position().distanceToSqr(p) > definition.range * definition.range) return failed("OUT_OF_RANGE", "Target is out of range");
                if (definition.lineOfSight && victim != null && victim != caster && !caster.hasLineOfSight(victim))
                    return failed("NO_LINE_OF_SIGHT", "Target is obstructed");
            }
        }
        if (ACTIVE.size() >= 256) return failed("LIMIT_REACHED", "Too many active spell instances");
        Context context = new Context(UUID.randomUUID(), caster, source, spellId, level, target, power);
        if (before(context, "spellRequest") || before(context, "spellPreCast")) return failed("CANCELLED", "Cast cancelled by an event");
        try {
            MagicCast nativeCast = definition == null
                ? ScriptMagic.withTarget(caster, victim, () -> provider.startCast(caster, spellId, level, victim))
                : new BuiltinSpell.Cast(context, SpellDefinition.load(definition.save()));
            if (nativeCast == null) {
                SpellResult result = SpellResult.fail("CAST_REJECTED", "Adapter rejected the cast");
                notify(context, "spellFail", result); return new Attempt(result, null);
            }
            if (definition != null) {
                resource.pay(caster, cost);
                COOLDOWNS.computeIfAbsent(caster, c -> new HashMap<>()).put(spellId, world.getGameTime() + definition.cooldownTicks);
            }
            Session session = new Session(context, nativeCast, cost);
            ACTIVE.put(context.instanceId, session);
            return new Attempt(session.result(), session);
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("Spell adapter failed: {}", spellId, error);
            SpellResult result = SpellResult.fail("ADAPTER_ERROR", "Spell adapter failed; see server log");
            notify(context, "spellFail", result); return new Attempt(result, null);
        }
    }
    private static Attempt failed(String code, String text) { return new Attempt(SpellResult.fail(code, text), null); }
    public static int cooldown(LivingEntity caster, String id) {
        return (int)Math.max(0, COOLDOWNS.getOrDefault(caster, Map.of()).getOrDefault(id, 0L) - caster.level().getGameTime());
    }
    public static SpellResult result(String id) {
        if (id == null) return SpellResult.fail("UNKNOWN_INSTANCE", "Invalid instance ID");
        UUID key;
        try { key = UUID.fromString(id); } catch (IllegalArgumentException e) { return SpellResult.fail("UNKNOWN_INSTANCE", "Invalid instance ID"); }
        Session session = ACTIVE.get(key);
        return session == null ? FINISHED.getOrDefault(key, SpellResult.fail("UNKNOWN_INSTANCE", "Instance is not retained")) : session.result();
    }
    public static boolean before(Context context, String phase) { return dispatch(new SpellEvent.Before(context, phase)); }
    public static void notify(Context context, String phase, SpellResult result) { dispatch(new SpellEvent(context, phase, result)); }
    private static boolean dispatch(SpellEvent event) {
        boolean cancelled = MinecraftForge.EVENT_BUS.post(event);
        // CNPC Forge scripts can declare function spellCast(event), spellImpact(event), etc.
        var controller = noppes.npcs.controllers.ScriptController.Instance;
        if (controller != null && controller.forgeScripts.isEnabled())
            for (var script : controller.forgeScripts.getScripts()) script.run(event.phase, event);
        return cancelled || event.isCancelable() && event.isCanceled();
    }
    public static final class Session implements MagicCast {
        public final Context context;
        private final MagicCast delegate;
        private final double cost;
        private final ServerLevel world;
        private final NpcAnimationSync.Owner animationOwner;
        private int ticks;
        private String state = "CASTING", code = "SUCCESS", message = "Accepted";
        private boolean ended;
        Session(Context context, MagicCast delegate, double cost) {
            this.context = context; this.delegate = delegate; this.cost = cost; this.world = (ServerLevel)context.caster.level();
            this.animationOwner = NpcAnimationSync.owner();
        }
        public SpellResult result() { return new SpellResult(code, message, context.instanceId, state, cost); }
        public boolean tick(LivingEntity caster, LivingEntity target) {
            if (ended) return true;
            if (!caster.isAlive() || caster.isRemoved() || caster.level() != world || ++ticks > 24000 || context.target.entity() != null &&
                (!context.target.entity().isAlive() || context.target.entity().isRemoved() || context.target.entity().level() != caster.level())) {
                cancel(caster); return true;
            }
            try {
                if (ticks == 1 && !(delegate instanceof BuiltinSpell.Cast)) CastingService.notify(context, "spellCast", result());
                boolean done = NpcAnimationSync.withOwner(animationOwner.token(), animationOwner.foreground(),
                    () -> ScriptMagic.withTarget(caster, context.target.entity(), () -> delegate.tick(caster, context.target.entity())));
                if (done) finish(delegate.wasSuccessful() ? "COMPLETED" : "FAILED", delegate.wasSuccessful() ? "SUCCESS" : "CAST_FAILED");
                return done;
            } catch (RuntimeException | LinkageError error) {
                cancel(caster); throw error;
            }
        }
        public boolean wasSuccessful() { return ended && state.equals("COMPLETED"); }
        public void cancel(LivingEntity caster) {
            if (ended) return;
            try { NpcAnimationSync.withOwner(animationOwner.token(), animationOwner.foreground(),
                () -> ScriptMagic.withTarget(caster, context.target.entity(), () -> { delegate.cancel(caster); return null; })); }
            finally { finish("CANCELLED", "CANCELLED"); }
        }
        private void finish(String next, String nextCode) {
            ended = true; state = next; code = nextCode; message = next;
            if (next.equals("COMPLETED")) LAST_SUCCESS.put(context.caster, new Completion(context.spellId, world.getGameTime(), world.dimension()));
            ACTIVE.remove(context.instanceId); FINISHED.put(context.instanceId, result());
            if (FINISHED.size() > 512) FINISHED.remove(FINISHED.keySet().iterator().next());
            CastingService.notify(context, next.equals("CANCELLED") ? "spellCancel" : next.equals("FAILED") ? "spellFail" : "spellEnd", result());
        }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        for (Session session : new ArrayList<>(ACTIVE.values()))
            if (session.context.caster() == event.getEntity() || session.context.target().entity() == event.getEntity()) session.cancel(session.context.caster());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Session session : new ArrayList<>(ACTIVE.values())) session.cancel(session.context.caster());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); FINISHED.clear(); COOLDOWNS.clear(); LAST_SUCCESS.clear(); }
}

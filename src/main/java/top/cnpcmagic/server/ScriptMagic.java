package top.cnpcmagic.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.casting.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Internal cast service for methods mixed into CNPC script wrappers. No persistent configuration. */
public final class ScriptMagic {
    private static final Map<LivingEntity, Session> ACTIVE = new HashMap<>();
    private static boolean stopping;
    private static LivingEntity invokingCaster;
    private static LivingEntity invokingTarget;

    private ScriptMagic() {
    }

    /** Returns true when accepted, not when the asynchronous charge/channel has finished. */
    public static boolean cast(Object casterObject, String spellId, int level, Object targetObject) {
        return castResult(casterObject, spellId, level, targetObject).isSuccess();
    }

    public static SpellResult castResult(Object casterObject, String spellId, int level, Object targetObject) {
        LivingEntity caster = entity(casterObject);
        LivingEntity livingTarget = entity(targetObject);
        SpellTarget descriptor = targetObject instanceof SpellTarget t ? t
            : livingTarget == null ? null : SpellTarget.entity(livingTarget);
        if (stopping || caster == null || caster.getServer() == null || !caster.getServer().isSameThread())
            return SpellResult.fail("INVALID_CASTER", "A server-side living caster is required");
        if (ACTIVE.containsKey(caster)) return SpellResult.fail("BUSY", "Caster already has a script cast");
        if (descriptor == null) return SpellResult.fail("INVALID_TARGET", "Use a living target or an explicit target descriptor");
        LivingEntity target = descriptor.entity();
        if (caster instanceof EntityNPCInterface npc) MagicCombatHandler.reset(npc);
        LivingEntity previous = caster instanceof Mob mob ? mob.getTarget() : null;
        if (caster instanceof Mob mob) mob.setTarget(target);
        if (target != null && target != caster) MagicCombatHandler.face(caster, target);
        CastingService.Attempt attempt = CastingService.start(caster, spellId, level, descriptor,
            caster instanceof net.minecraft.world.entity.player.Player ? "player_script" : "entity_script");
        if (attempt.cast() != null) {
            ACTIVE.put(caster, new Session(attempt.cast(), target, previous));
            return attempt.result();
        }
        if (caster instanceof Mob mob) mob.setTarget(previous);
        return attempt.result();
    }

    public static SpellResult castAt(Object caster, String id, int level, double x, double y, double z) {
        return castResult(caster, id, level, SpellTarget.position(x, y, z));
    }
    public static SpellResult castDirection(Object caster, String id, int level, double x, double y, double z) {
        return castResult(caster, id, level, SpellTarget.direction(x, y, z));
    }
    public static SpellResult castSelf(Object casterObject, String id, int level) {
        LivingEntity caster = entity(casterObject);
        return caster == null ? SpellResult.fail("INVALID_CASTER", "Living caster required") : castResult(caster, id, level, SpellTarget.self(caster));
    }
    public static String[] query(Object casterObject, String filter) {
        LivingEntity caster = entity(casterObject);
        if (caster == null || caster.getServer() == null) return new String[0];
        String text = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT);
        return SpellLibrary.get(caster.getServer()).spells.values().stream()
            .filter(d -> (d.id + " " + d.name + " " + d.school + " " + d.tags).toLowerCase(java.util.Locale.ROOT).contains(text)).map(d -> d.id).toArray(String[]::new);
    }
    public static String info(Object casterObject, String id) {
        LivingEntity caster = entity(casterObject);
        if (caster == null || caster.getServer() == null) return "{}";
        SpellDefinition d = SpellLibrary.get(caster.getServer()).spells.get(id);
        return d == null ? "{}" : d.save().toString();
    }
    public static int cooldown(Object casterObject, String id) { LivingEntity c = entity(casterObject); return c == null ? 0 : CastingService.cooldown(c, id); }

    public static boolean isCasting(Object caster) {
        return ACTIVE.containsKey(entity(caster));
    }

    public static boolean cancel(Object casterObject) {
        LivingEntity caster = entity(casterObject);
        if (caster == null || caster.getServer() == null || !caster.getServer().isSameThread()) return false;
        Session session = ACTIVE.remove(caster);
        if (session == null) return false;
        try {
            withTarget(caster, session.target, () -> { session.cast.cancel(caster); return null; });
        } catch (RuntimeException | LinkageError e) {
            CNPCMagic.LOGGER.error("Script cast failed to cancel", e);
        } finally {
            NpcAnimationSync.stop(caster);
            restoreTarget(caster, session);
        }
        return true;
    }

    private static LivingEntity entity(Object object) {
        if (object instanceof IEntity<?> wrapper) object = wrapper.getMCEntity();
        return object instanceof LivingEntity living ? living : null;
    }

    /** Optional Goety hook: override ray-selected targets only inside this script's native spell call. */
    public static LivingEntity getCastTarget(LivingEntity caster) {
        return caster == invokingCaster ? invokingTarget : null;
    }

    public static <T> T withTarget(LivingEntity caster, LivingEntity target, Supplier<T> action) {
        LivingEntity previousCaster = invokingCaster;
        LivingEntity previousTarget = invokingTarget;
        invokingCaster = caster;
        invokingTarget = target;
        try {
            return action.get();
        } finally {
            invokingCaster = previousCaster;
            invokingTarget = previousTarget;
        }
    }

    private static boolean valid(LivingEntity caster, LivingEntity target) {
        return !caster.level().isClientSide() && caster.getServer() != null && caster.isAlive() && !caster.isRemoved()
            && (target == null || target.isAlive() && !target.isRemoved() && target.level() == caster.level());
    }

    private static void restoreTarget(LivingEntity caster, Session session) {
        if (caster instanceof Mob mob && mob.getTarget() == session.target) mob.setTarget(session.previous);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : new ArrayList<>(ACTIVE.entrySet())) {
            LivingEntity caster = entry.getKey();
            Session session = entry.getValue();
            if (!valid(caster, session.target)) {
                cancel(caster);
                continue;
            }
            if (caster instanceof Mob mob) mob.setTarget(session.target);
            if (session.target != null && session.target != caster) MagicCombatHandler.face(caster, session.target);
            try {
                if (withTarget(caster, session.target, () -> session.cast.tick(caster, session.target))) {
                    ACTIVE.remove(caster);
                    restoreTarget(caster, session);
                }
            } catch (RuntimeException | LinkageError e) {
                CNPCMagic.LOGGER.error("Script cast failed during session", e);
                cancel(caster);
            }
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity caster) cancel(caster);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        stopping = true;
        new ArrayList<>(ACTIVE.keySet()).forEach(ScriptMagic::cancel);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        stopping = false;
        ACTIVE.clear();
    }

    private record Session(MagicCast cast, LivingEntity target, LivingEntity previous) {
    }
}

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
        LivingEntity caster = entity(casterObject);
        LivingEntity target = entity(targetObject);
        ResourceLocation id = spellId == null ? null : ResourceLocation.tryParse(spellId);
        if (stopping || caster == null || target == null || id == null || !valid(caster, target)
            || !caster.getServer().isSameThread() || ACTIVE.containsKey(caster)) return false;
        MagicProvider provider = MagicProviders.get(id.getNamespace());
        if (provider == null || !provider.isValidSpell(spellId) || level < 1 || level > provider.getMaxLevel(spellId)) return false;
        if (caster instanceof EntityNPCInterface npc) MagicCombatHandler.reset(npc);
        LivingEntity previous = caster instanceof Mob mob ? mob.getTarget() : null;
        if (caster instanceof Mob mob) mob.setTarget(target);
        MagicCombatHandler.face(caster, target);
        try {
            MagicCast cast = withTarget(caster, target, () -> provider.startCast(caster, spellId, level, target));
            if (cast != null) {
                ACTIVE.put(caster, new Session(cast, target, previous));
                return true;
            }
        } catch (RuntimeException | LinkageError e) {
            CNPCMagic.LOGGER.error("Script cast failed to start {}", spellId, e);
        }
        if (caster instanceof Mob mob) mob.setTarget(previous);
        return false;
    }

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

    private static <T> T withTarget(LivingEntity caster, LivingEntity target, Supplier<T> action) {
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
            && target.isAlive() && !target.isRemoved() && target.level() == caster.level();
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
            MagicCombatHandler.face(caster, session.target);
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

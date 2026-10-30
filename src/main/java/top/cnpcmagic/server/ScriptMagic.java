package top.cnpcmagic.server;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.wizardry.CastHandle;
import top.cnpcmagic.wizardry.WizardryCasting;
import top.cnpcmagic.wizardry.WizardrySpells;

/** Backing service for the methods mixed directly into CustomNPCs script wrappers. */
public final class ScriptMagic {
    private static final Map<EntityLivingBase, Session> ACTIVE = new IdentityHashMap<>();

    private ScriptMagic() {
    }

    /** Returns true when the cast was accepted. */
    public static boolean cast(Object casterObject, String spellId, int level, Object targetObject) {
        return cast(casterObject, spellId, level, targetObject, MagicRule.DEFAULT_DURATION_TICKS);
    }

    public static boolean cast(Object casterObject, String spellId, int level, Object targetObject, int durationTicks) {
        EntityLivingBase caster = unwrap(casterObject);
        EntityLivingBase target = unwrap(targetObject);
        if (!valid(caster, target) || level < 1 || level > MagicRule.MAX_LEVEL || ACTIVE.containsKey(caster)) {
            return false;
        }
        Spell spell = WizardrySpells.get(spellId);
        if (spell == null) return false;
        if (spell.isContinuous && (durationTicks < 1 || durationTicks > MagicRule.MAX_DURATION_TICKS)) return false;
        if (caster instanceof EntityNPCInterface) {
            if (!WizardrySpells.isSupported(spellId, (EntityNPCInterface) caster)) return false;
            MagicCombatHandler.reset((EntityNPCInterface) caster);
        } else if (!(caster instanceof EntityPlayer) || !spell.isEnabled(SpellProperties.Context.COMMANDS)) {
            return false;
        }

        try {
            CastHandle handle = WizardryCasting.start(caster, spell, level, target, durationTicks);
            if (handle == null) return false;
            ACTIVE.put(caster, new Session(handle, target));
            return true;
        } catch (RuntimeException | LinkageError error) {
            log("Script spell failed to start: " + spellId, error);
            return false;
        }
    }

    public static boolean isCasting(Object casterObject) {
        return ACTIVE.containsKey(unwrap(casterObject));
    }

    public static boolean cancel(Object casterObject) {
        EntityLivingBase caster = unwrap(casterObject);
        if (caster == null || !onServerThread(caster)) return false;
        Session session = ACTIVE.remove(caster);
        if (session == null) return false;
        try {
            session.handle.cancel(caster);
        } catch (RuntimeException | LinkageError error) {
            log("Script spell cancellation failed", error);
        }
        return true;
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<EntityLivingBase, Session> entry : new ArrayList<>(ACTIVE.entrySet())) {
            EntityLivingBase caster = entry.getKey();
            Session session = entry.getValue();
            if (!valid(caster, session.target)) {
                cancel(caster);
                continue;
            }
            WizardryCasting.face(caster, session.target);
            try {
                if (session.handle.tick(caster, session.target)) ACTIVE.remove(caster);
            } catch (RuntimeException | LinkageError error) {
                log("Script spell tick failed", error);
                cancel(caster);
            }
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (!event.getEntityLiving().world.isRemote) cancel(event.getEntityLiving());
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        for (EntityLivingBase caster : new ArrayList<>(ACTIVE.keySet())) {
            if (caster.world == event.getWorld()) cancel(caster);
        }
    }

    private static EntityLivingBase unwrap(Object value) {
        if (value instanceof IEntity) value = ((IEntity) value).getMCEntity();
        return value instanceof EntityLivingBase ? (EntityLivingBase) value : null;
    }

    private static boolean valid(EntityLivingBase caster, EntityLivingBase target) {
        return caster != null && target != null && !caster.world.isRemote && caster.world == target.world
            && !caster.isDead && !target.isDead && onServerThread(caster);
    }

    private static boolean onServerThread(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server != null && server.isCallingFromMinecraftThread();
    }

    private static void log(String message, Throwable error) {
        if (CNPCMagic.LOGGER != null) CNPCMagic.LOGGER.error(message, error);
    }

    private static final class Session {
        private final CastHandle handle;
        private final EntityLivingBase target;

        private Session(CastHandle handle, EntityLivingBase target) {
            this.handle = handle;
            this.target = target;
        }
    }
}

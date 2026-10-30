package top.cnpcmagic.server;

import electroblob.wizardry.spell.Spell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.wizardry.CastHandle;
import top.cnpcmagic.wizardry.WizardryCasting;
import top.cnpcmagic.wizardry.WizardrySpells;
import top.cnpcmagic.wizardry.MagicCasterAccess;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.registry.Spells;

/** Runs ordered rounds and independently cooled rules for configured NPCs. */
public final class MagicCombatHandler {
    private static final Map<UUID, NpcState> STATES = new HashMap<>();

    private MagicCombatHandler() {
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityNPCInterface)) return;
        EntityNPCInterface npc = (EntityNPCInterface) event.getEntityLiving();
        if (npc.world.isRemote || ScriptMagic.isCasting(npc)) return;
        if (npc.isDead) {
            reset(npc);
            return;
        }

        EntityLivingBase target = npc.getAttackTarget();
        NpcState state = STATES.get(npc.getUniqueID());
        if (!validTarget(npc, target)) {
            if (state != null) cancelAll(npc, state);
            return;
        }
        if (state == null) {
            state = new NpcState(npc);
            STATES.put(npc.getUniqueID(), state);
        }

        long now = npc.world.getTotalWorldTime();
        List<MagicRule> rules = MagicDataStore.get(npc.getServer()).getRules(npc.getUniqueID());
        List<MagicRule> ordered = new ArrayList<>();
        for (MagicRule rule : rules) if (!rule.independent) ordered.add(rule);
        tickOrdered(npc, target, state, now, ordered);
        tickIndependent(npc, target, state, now, rules);

        for (MagicRule known : new ArrayList<>(state.rules.keySet())) {
            if (rules.contains(known)) continue;
            RuleState runtime = state.rules.remove(known);
            if (runtime != null && runtime.active != null) cancelHandle(npc, runtime.active);
        }
    }

    private static void tickOrdered(EntityNPCInterface npc, EntityLivingBase target, NpcState state,
                                    long now, List<MagicRule> rules) {
        if (state.active != null) {
            if (state.targetId != target.getEntityId() || !rules.contains(state.activeRule)
                || !state.activeRule.enabled) {
                cancelOrdered(npc, state, now);
                return;
            }
            WizardryCasting.face(npc, target);
            try {
                if (state.active.tick(npc, target)) {
                    RuleState runtime = state.rules.get(state.activeRule);
                    if (state.active.wasSuccessful() && runtime.completed(now, state.activeRule)) state.nextIndex++;
                    else if (!state.active.wasSuccessful()) {
                        runtime.readyAt = now + 20;
                        state.nextIndex++;
                    }
                    state.active = null;
                    state.activeRule = null;
                    state.nextCast = now + 10;
                }
            } catch (RuntimeException | LinkageError error) {
                log("Ordered NPC spell tick failed", error);
                cancelOrdered(npc, state, now);
            }
            return;
        }
        if (rules.isEmpty() || now < state.nextCast) return;

        for (int offset = 0; offset < rules.size(); offset++) {
            int index = Math.floorMod(state.nextIndex + offset, rules.size());
            MagicRule rule = rules.get(index);
            if (!usable(npc, target, rule)) continue;
            RuleState runtime = state.rules.computeIfAbsent(rule, ignored -> new RuleState());
            if (now < runtime.readyAt) continue;
            try {
                Spell spell = WizardrySpells.get(rule.spellId);
                CastHandle handle = WizardryCasting.start(npc, spell, rule.level, target, rule.durationTicks);
                if (handle != null) {
                    state.active = handle;
                    state.activeRule = rule;
                    state.targetId = target.getEntityId();
                    state.nextIndex = index;
                    return;
                }
                runtime.readyAt = now + 20;
            } catch (RuntimeException | LinkageError error) {
                runtime.readyAt = now + 100;
                log("Ordered NPC spell start failed: " + rule.spellId, error);
            }
        }
    }

    private static void tickIndependent(EntityNPCInterface npc, EntityLivingBase target, NpcState state,
                                        long now, List<MagicRule> rules) {
        for (MagicRule rule : rules) {
            RuleState runtime = state.rules.get(rule);
            if (!rule.independent || !rule.enabled || rule.spellId.isEmpty()) {
                if (runtime != null && runtime.active != null) {
                    cancelHandle(npc, runtime.active);
                    runtime.active = null;
                }
                continue;
            }
            if (runtime == null) {
                runtime = new RuleState();
                state.rules.put(rule, runtime);
            }
            if (runtime.active != null) {
                if (runtime.targetId != target.getEntityId()) {
                    cancelHandle(npc, runtime.active);
                    runtime.active = null;
                    runtime.readyAt = now + 20;
                    continue;
                }
                WizardryCasting.face(npc, target);
                try {
                    if (runtime.active.tick(npc, target)) {
                        if (runtime.active.wasSuccessful()) runtime.completed(now, rule);
                        else runtime.readyAt = now + 20;
                        runtime.active = null;
                    }
                } catch (RuntimeException | LinkageError error) {
                    cancelHandle(npc, runtime.active);
                    runtime.active = null;
                    runtime.readyAt = now + 100;
                    log("Independent NPC spell tick failed: " + rule.spellId, error);
                }
                continue;
            }
            if (now < runtime.readyAt || !usable(npc, target, rule)) continue;
            try {
                runtime.active = WizardryCasting.start(npc, WizardrySpells.get(rule.spellId), rule.level,
                    target, rule.durationTicks);
                if (runtime.active == null) runtime.readyAt = now + 20;
                else runtime.targetId = target.getEntityId();
            } catch (RuntimeException | LinkageError error) {
                runtime.readyAt = now + 100;
                log("Independent NPC spell start failed: " + rule.spellId, error);
            }
        }
    }

    private static boolean usable(EntityNPCInterface npc, EntityLivingBase target, MagicRule rule) {
        return rule.enabled && !rule.spellId.isEmpty() && WizardrySpells.isSupported(rule.spellId, npc)
            && npc.getDistanceSq(target) <= (double) rule.range * rule.range;
    }

    private static boolean validTarget(EntityNPCInterface npc, EntityLivingBase target) {
        return target != null && !target.isDead && target.world == npc.world;
    }

    private static void cancelOrdered(EntityNPCInterface npc, NpcState state, long now) {
        if (state.active != null) cancelHandle(npc, state.active);
        state.active = null;
        state.activeRule = null;
        state.nextCast = now + 20;
    }

    private static void cancelAll(EntityNPCInterface npc, NpcState state) {
        cancelOrdered(npc, state, npc.world.getTotalWorldTime());
        for (RuleState runtime : state.rules.values()) {
            if (runtime.active != null) cancelHandle(npc, runtime.active);
            runtime.active = null;
        }
    }

    private static void cancelHandle(EntityNPCInterface npc, CastHandle handle) {
        try {
            handle.cancel(npc);
        } catch (RuntimeException | LinkageError error) {
            log("NPC spell cancellation failed", error);
        }
    }

    public static void reset(EntityNPCInterface npc) {
        NpcState state = STATES.remove(npc.getUniqueID());
        if (state != null) cancelAll(npc, state);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (!event.getEntityLiving().world.isRemote && event.getEntityLiving() instanceof EntityNPCInterface) {
            reset((EntityNPCInterface) event.getEntityLiving());
        }
    }

    /** The native shield entity handles direct collisions; this closes melee/AOE paths that hit its NPC owner. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void shieldBlock(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityNPCInterface) || event.getSource().isUnblockable()) return;
        EntityNPCInterface npc = (EntityNPCInterface) event.getEntityLiving();
        if (npc.world.isRemote || ((ISpellCaster) npc).getContinuousSpell() != Spells.shield) return;
        if (((MagicCasterAccess) npc).cnpcmagic$getShield() == null
            || ((MagicCasterAccess) npc).cnpcmagic$getShield().isDead) return;
        Entity immediate = event.getSource().getImmediateSource();
        Entity source = event.getSource().getTrueSource();
        if (source == null) source = immediate;
        if (source == null || source == npc) return;
        Vec3d incoming;
        if (source == immediate && immediate instanceof IProjectile
            && immediate.motionX * immediate.motionX + immediate.motionY * immediate.motionY
                + immediate.motionZ * immediate.motionZ > 0) {
            // At impact the projectile may already overlap the NPC, so use the direction it came from.
            incoming = new Vec3d(-immediate.motionX, -immediate.motionY, -immediate.motionZ);
        } else {
            incoming = new Vec3d(source.posX - npc.posX,
                source.posY + source.height * 0.5 - (npc.posY + npc.getEyeHeight()), source.posZ - npc.posZ);
        }
        if (incoming.lengthSquared() > 0 && npc.getLookVec().dotProduct(incoming.normalize()) > 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        for (NpcState state : new ArrayList<>(STATES.values())) {
            if (state.npc.world != event.getWorld()) continue;
            reset(state.npc);
        }
    }

    private static void log(String message, Throwable error) {
        if (CNPCMagic.LOGGER != null) CNPCMagic.LOGGER.error(message, error);
    }

    private static final class NpcState {
        private final EntityNPCInterface npc;
        private int targetId = -1;
        private int nextIndex;
        private long nextCast;
        private CastHandle active;
        private MagicRule activeRule;
        private final Map<MagicRule, RuleState> rules = new IdentityHashMap<>();

        private NpcState(EntityNPCInterface npc) {
            this.npc = npc;
        }
    }

    private static final class RuleState {
        private int casts;
        private long readyAt;
        private int targetId = -1;
        private CastHandle active;

        private boolean completed(long now, MagicRule rule) {
            if (rule.independent) {
                casts = 0;
                readyAt = now + Math.max(1, rule.cooldownTicks);
                return true;
            }
            if (++casts >= rule.casts) {
                casts = 0;
                readyAt = now + rule.cooldownTicks;
                return true;
            }
            return false;
        }
    }
}

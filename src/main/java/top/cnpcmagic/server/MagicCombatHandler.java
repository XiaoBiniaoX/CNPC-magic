package top.cnpcmagic.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.provider.MagicCast;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class MagicCombatHandler {
    private static final Map<UUID, NpcState> STATES = new HashMap<>();

    private MagicCombatHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc) || npc.level().isClientSide()) {
            return;
        }
        if (ScriptMagic.isCasting(npc) || MagicPreview.isPreview(npc)) {
            CombatDecisions.remove(npc.getUUID());
            return;
        }
        if (!npc.isAlive()) {
            reset(npc);
            return;
        }
        LivingEntity target = npc.getTarget();
        NpcState state = STATES.get(npc.getUUID());
        if (target == null || !target.isAlive() || target.level() != npc.level()) {
            if (state != null) cancelAll(npc, state);
            CombatDecisions.remove(npc.getUUID());
            return;
        }
        if (state == null) {
            state = new NpcState();
            STATES.put(npc.getUUID(), state);
        }
        long now = npc.level().getGameTime();
        if (npc.level().getServer() == null) return;
        MagicDataStore store = MagicDataStore.get(npc.level().getServer());
        List<Entry> entries = new ArrayList<>();
        for (MagicProvider provider : MagicProviders.all()) {
            for (MagicRule rule : store.getRules(npc, provider.getId())) entries.add(new Entry(provider, rule, entries.size()));
        }
        var order = CombatDecisions.ordered(npc.getUUID(), entries.stream()
            .map(e -> new CombatDecisions.Order(e.rule.sequenceOrder(), e.rule.independent)).toList());
        if (Boolean.getBoolean("cnpcmagic.verify.combatParity"))
            top.cnpcmagic.verify.MagicVerification.checkOrder(entries.stream().map(Entry::rule).toList(), order);
        tickOrdered(npc, target, state, now, order.stream().map(entries::get).toList());
        tickIndependent(npc, target, state, now, entries);
        // Editor saves normally reset the state; also handle rules removed by in-process integrations.
        for (MagicRule rule : new ArrayList<>(state.rules.keySet())) {
            if (entries.stream().anyMatch(e -> e.rule == rule)) continue;
            RuleState runtime = state.rules.remove(rule);
            if (runtime.active != null) cancelIndependent(npc, runtime);
            NpcAnimationSync.withOwner(runtime, false, () -> { NpcAnimationSync.stop(npc); return null; });
        }
    }

    private static void tickOrdered(EntityNPCInterface npc, LivingEntity target, NpcState state, long now, List<Entry> entries) {
        if (state.active != null) {
            if (!target.getUUID().equals(state.target) || entries.stream().noneMatch(e -> e.rule == state.activeRule)
                || !state.activeRule.enabled) {
                cancel(npc, state);
                return;
            }
            face(npc, target);
            try {
                RuleState owner = state.rules.get(state.activeRule);
                if (NpcAnimationSync.withOwner(owner, true, () -> state.active.tick(npc, target))) {
                    MagicRule rule = state.activeRule;
                    boolean success = state.active.wasSuccessful();
                    state.active = null;
                    state.nextCast = now + 10;
                    if (success) {
                        if (owner.completed(now, rule)) {
                            state.nextIndex++;
                        }
                        if (state.meleeFollowup) meleeFollowup(npc, target);
                    } else {
                        owner.readyAt = now + 20;
                        state.nextIndex++;
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                CNPCMagic.LOGGER.error("NPC {} failed during {}", npc.getUUID(), state.activeRule.spellId, e);
                if (state.active != null) cancel(npc, state);
                state.rules.get(state.activeRule).readyAt = now + 100;
            }
            return;
        }
        if (now < state.nextCast || npc.level().getServer() == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            int index = Math.floorMod(state.nextIndex + i, entries.size());
            Entry entry = entries.get(index);
            MagicRule rule = entry.rule;
            if (!rule.enabled || rule.spellId.isEmpty() || !entry.provider.isValidSpell(rule.spellId)) {
                continue;
            }
            RuleState runtime = state.rules.computeIfAbsent(rule, k -> new RuleState());
            if (!runtime.isReady(now)) {
                continue;
            }
            if (!testConditions(npc, target, entry)) {
                continue;
            }
            face(npc, target);
            try {
                MagicCast cast = NpcAnimationSync.withOwner(runtime, true,
                    () -> top.cnpcmagic.casting.CastingService.start(npc, rule.spellId, rule.level,
                        top.cnpcmagic.casting.SpellTarget.entity(target), "npc_ai").cast());
                if (cast != null) {
                    state.active = cast;
                    state.activeRule = rule;
                    state.meleeFollowup = entry.provider.requiresMeleeTarget(rule.spellId);
                    state.target = target.getUUID();
                    state.nextIndex = index;
                    return;
                }
                runtime.readyAt = now + 20;
            } catch (RuntimeException | LinkageError e) {
                runtime.readyAt = now + 100;
                CNPCMagic.LOGGER.error("NPC {} failed to start {}", npc.getUUID(), rule.spellId, e);
            }
        }
    }

    private static void tickIndependent(EntityNPCInterface npc, LivingEntity target, NpcState state, long now, List<Entry> entries) {
        for (Entry entry : entries) {
            MagicRule rule = entry.rule;
            RuleState runtime = state.rules.get(rule);
            if (!rule.independent || !rule.enabled || rule.spellId.isEmpty() || !entry.provider.isValidSpell(rule.spellId)) {
                if (runtime != null && runtime.active != null) cancelIndependent(npc, runtime);
                continue;
            }
            if (runtime == null) {
                runtime = new RuleState();
                state.rules.put(rule, runtime);
            }
            RuleState owner = runtime;
            if (runtime.active != null) {
                if (!target.getUUID().equals(runtime.target)) {
                    cancelIndependent(npc, runtime);
                    continue;
                }
                face(npc, target);
                try {
                    if (NpcAnimationSync.withOwner(owner, false, () -> owner.active.tick(npc, target))) {
                        boolean success = runtime.active.wasSuccessful();
                        runtime.active = null;
                        if (success) {
                            runtime.completed(now, rule);
                            if (entry.provider.requiresMeleeTarget(rule.spellId)) meleeFollowup(npc, target);
                        } else runtime.readyAt = now + 20;
                    }
                } catch (RuntimeException | LinkageError e) {
                    CNPCMagic.LOGGER.error("NPC {} failed during independent {}", npc.getUUID(), rule.spellId, e);
                    if (runtime.active != null) cancelIndependent(npc, runtime);
                    runtime.readyAt = now + 100;
                }
                continue;
            }
            if (!runtime.isReady(now) || !testConditions(npc, target, entry)) continue;
            face(npc, target);
            try {
                runtime.active = NpcAnimationSync.withOwner(owner, false,
                    () -> top.cnpcmagic.casting.CastingService.start(npc, rule.spellId, rule.level,
                        top.cnpcmagic.casting.SpellTarget.entity(target), "npc_ai").cast());
                if (runtime.active != null) runtime.target = target.getUUID();
                else runtime.readyAt = now + 20;
            } catch (RuntimeException | LinkageError e) {
                runtime.readyAt = now + 100;
                NpcAnimationSync.withOwner(owner, false, () -> { NpcAnimationSync.stop(npc); return null; });
                CNPCMagic.LOGGER.error("NPC {} failed to start independent {}", npc.getUUID(), rule.spellId, e);
            }
        }
    }

    private static void cancelIndependent(EntityNPCInterface npc, RuleState runtime) {
        try {
            NpcAnimationSync.withOwner(runtime, false, () -> { runtime.active.cancel(npc); return null; });
        } catch (RuntimeException | LinkageError e) {
            CNPCMagic.LOGGER.error("NPC {} failed to cancel independent cast", npc.getUUID(), e);
        } finally {
            runtime.active = null;
            runtime.readyAt = Math.max(runtime.readyAt, npc.level().getGameTime() + 20);
            NpcAnimationSync.withOwner(runtime, false, () -> { NpcAnimationSync.stop(npc); return null; });
        }
    }

    private static void cancelAll(EntityNPCInterface npc, NpcState state) {
        if (state.active != null) cancel(npc, state);
        for (RuleState runtime : new ArrayList<>(state.rules.values())) {
            if (runtime.active != null) cancelIndependent(npc, runtime);
        }
    }

    private static void cancel(EntityNPCInterface npc, NpcState state) {
        try {
            NpcAnimationSync.withOwner(state.rules.get(state.activeRule), true, () -> { state.active.cancel(npc); return null; });
        } catch (RuntimeException | LinkageError e) {
            CNPCMagic.LOGGER.error("NPC {} failed to cancel cast", npc.getUUID(), e);
        } finally {
            state.active = null;
            state.nextCast = npc.level().getGameTime() + 20;
            NpcAnimationSync.withOwner(state.rules.get(state.activeRule), true, () -> { NpcAnimationSync.stop(npc); return null; });
        }
    }

    public static void reset(EntityNPCInterface npc) {
        CombatDecisions.remove(npc.getUUID());
        NpcState state = STATES.remove(npc.getUUID());
        if (state != null) cancelAll(npc, state);
        NpcAnimationSync.stopAll(npc);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityNPCInterface npc) {
            reset(npc);
            if (npc.getRemovalReason() != null && npc.getRemovalReason().shouldDestroy())
                top.cnpcmagic.data.MagicRuntimeData.get(npc.getServer()).removeSoulbound(npc.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Spell cleanup can remove auxiliary entities. Finish it before the world iterates sections to save/unload.
        List<EntityNPCInterface> npcs = new ArrayList<>();
        for (var level : event.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof EntityNPCInterface npc) npcs.add(npc);
            }
        }
        npcs.forEach(MagicCombatHandler::reset);
        STATES.clear();
        NpcAnimationSync.clear();
        CombatDecisions.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        NpcAnimationSync.clear();
        CombatDecisions.clear();
    }

    static void meleeFollowup(EntityNPCInterface npc, LivingEntity target) {
        float range = npc.stats.melee.getRange();
        if (target.isAlive() && npc.distanceToSqr(target) <= range * range + target.getBbWidth() && npc.hasLineOfSight(target)) {
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            npc.doHurtTarget(target);
        }
    }

    public static void face(LivingEntity npc, LivingEntity target) {
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        double dy = target.getEyeY() - npc.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * (180.0 / Math.PI)));
        npc.setYRot(yaw);
        npc.setXRot(pitch);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        if (npc instanceof Mob mob) mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private static boolean testConditions(EntityNPCInterface npc, LivingEntity target, Entry entry) {
        boolean matches = CombatDecisions.test(npc.getUUID(), entry.slot, entry.rule.conditions.snapshot(npc, target, entry.rule.range));
        if (Boolean.getBoolean("cnpcmagic.verify.combatParity"))
            top.cnpcmagic.verify.MagicVerification.checkDecision(npc, target, entry.rule, matches);
        return matches;
    }

    private record Entry(MagicProvider provider, MagicRule rule, int slot) {
    }

    private static final class NpcState {
        private UUID target;
        private int nextIndex;
        private long nextCast;
        private MagicCast active;
        private MagicRule activeRule;
        private boolean meleeFollowup;
        // Rule configuration is mutable; runtime ownership follows the rule instance, not its value hash.
        private final Map<MagicRule, RuleState> rules = new IdentityHashMap<>();
    }

    public static final class RuleState {
        private int casts;
        private long readyAt;
        private MagicCast active;
        private UUID target;

        public boolean isReady(long now) {
            return now >= readyAt;
        }

        public boolean completed(long now, MagicRule rule) {
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

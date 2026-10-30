package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.NpcAnimationSync;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Opt-in main-thread concurrency, native-spell overlap, animation ownership and real GUI save probes. */
public final class IndependentVerification {
    private static final List<Probe> PROBES = new ArrayList<>();
    private static EntityNPCInterface guiNpc;
    private static ServerPlayer viewer;
    private static MagicRule cancellable;
    private static MagicRule failedRule;
    private static long start;
    private static boolean reported;

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static MagicRule rule(String id, int level, boolean independent, int cooldown) {
        MagicRule rule = new MagicRule();
        rule.enabled = true;
        rule.spellId = id;
        rule.level = level;
        rule.independent = independent;
        rule.cooldownTicks = cooldown;
        rule.range = 32;
        rule.casts = independent ? 99 : 2; // Prove independent casts do not wait for 99 completions before their CD.
        return rule;
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (Boolean.getBoolean("cnpcmagic.verify.reload")) {
            var saved = MagicDataStore.get(event.getServer()).save(new net.minecraft.nbt.CompoundTag()).getCompound("npcs");
            check(saved.toString().contains("independent:1b"), "independent setting survived real restart");
            CNPCMagic.LOGGER.info("VERIFY PASS independent real restart flag");
            return;
        }
        try {
            PROBES.clear();
            reported = false;
            viewer = null;
            guiNpc = null;
            ServerLevel world = event.getServer().overworld();
            start = world.getGameTime();
            MagicProviders.register(new TestProvider());
            cancellable = rule("parallel_test", 4, true, 50);
            failedRule = rule("parallel_test", 5, true, 200);
            create(world, 320, "cnpcmagic_parallel_verify", List.of(
                rule("parallel_test", 1, false, 60), rule("parallel_test", 2, false, 80),
                rule("parallel_test", 3, true, 20), cancellable, failedRule));
            animationOwnership(PROBES.get(0).npc);
            int x = 360;
            for (String id : List.of("irons_spellbooks", "goety")) {
                MagicProvider provider = MagicProviders.get(id);
                if (provider == null) continue;
                MagicProviders.register(new WatchingProvider(provider));
                String first = id + (id.equals("goety") ? ":fire_breath_focus" : ":fire_breath");
                String second = id + (id.equals("goety") ? ":prisma_beam_focus" : ":ray_of_siphoning");
                create(world, x, id, List.of(rule(first, 1, false, 60), rule(second, 1, true, 20)));
                x += 40;
            }
            if (MagicProviders.get("irons_spellbooks") != null) ironDataIsolation(PROBES.get(0).npc);
            if (MagicProviders.get("irons_spellbooks") != null || MagicProviders.get("goety") != null) {
                int view = MagicProviders.get("goety") != null ? (MagicProviders.get("irons_spellbooks") != null ? 180 : 100) : 0;
                guiNpc = CustomEntities.entityCustomNpc.create(world);
                guiNpc.moveTo(view + 3.5, 80, -1.5, 0, 0);
                guiNpc.setNoAi(true);
                guiNpc.setInvulnerable(true);
                guiNpc.setPersistenceRequired();
                guiNpc.display.setName("independent GUI");
                world.addFreshEntity(guiNpc);
                for (String id : List.of("irons_spellbooks", "goety")) {
                    if (MagicProviders.get(id) == null) continue;
                    String spell = id + (id.equals("goety") ? ":soul_bolt_focus" : ":firebolt");
                    MagicRule a = rule(spell, 1, false, 40);
                    MagicRule b = rule(spell, 1, false, 80);
                    a.enabled = b.enabled = false;
                    MagicDataStore.get(event.getServer()).setRules(guiNpc.getUUID(), id, List.of(a, b));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            reported = true;
            CNPCMagic.LOGGER.error("VERIFY FAIL independent setup", e);
        }
    }

    private static void create(ServerLevel world, int x, String provider, List<MagicRule> rules) {
        for (int cx = (x - 4) >> 4; cx <= (x + 4) >> 4; cx++) world.setChunkForced(cx, 0, true);
        for (int dx = -4; dx <= 4; dx++) {
            for (int z = -2; z <= 10; z++) world.setBlock(new BlockPos(x + dx, 79, z), Blocks.STONE.defaultBlockState(), 3);
        }
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(world);
        npc.moveTo(x + 0.5, 80, 0.5, 0, 0);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setPersistenceRequired();
        npc.display.setName("independent " + provider);
        Pig target = EntityType.PIG.create(world);
        target.moveTo(x + 0.5, 80, provider.equals("goety") ? 3.5 : 8.5, 0, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        target.setHealth(10000);
        world.addFreshEntity(npc);
        world.addFreshEntity(target);
        npc.setTarget(target);
        MagicDataStore.get(world.getServer()).setRules(npc.getUUID(), provider, rules);
        PROBES.add(new Probe(npc, target, provider));
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void ironDataIsolation(LivingEntity caster) throws ReflectiveOperationException {
        Class<?> type = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
        var getter = type.getMethod("getPlayerMagicData", LivingEntity.class);
        var scope = Class.forName("top.cnpcmagic.compat.iron.IronAdapter")
            .getDeclaredMethod("withData", LivingEntity.class, type, java.util.function.Supplier.class);
        scope.setAccessible(true);
        Object original = getter.invoke(null, caster);
        Object first = type.getConstructor(boolean.class).newInstance(true);
        Object second = type.getConstructor(boolean.class).newInstance(true);
        java.util.function.Supplier<Object> nested = () -> {
            try {
                check(getter.invoke(null, caster) == second, "nested session data isolated");
                return null;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        };
        java.util.function.Supplier<Object> outer = () -> {
            try {
                check(getter.invoke(null, caster) == first, "native helper sees session data");
                scope.invoke(null, caster, second, nested);
                check(getter.invoke(null, caster) == first, "nested data scope restored");
                return null;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        };
        scope.invoke(null, caster, first, outer);
        check(getter.invoke(null, caster) == original, "ordinary native data unchanged outside session");
        CNPCMagic.LOGGER.info("VERIFY PASS independent Iron scoped data and native restoration");
    }

    private static void animationOwnership(EntityNPCInterface npc) throws ReflectiveOperationException {
        Object ordered = new Object(), independent = new Object();
        NpcAnimationSync.withOwner(ordered, true, () -> { NpcAnimationSync.play(npc, "verify-primary", 120); return null; });
        NpcAnimationSync.withOwner(independent, false, () -> { NpcAnimationSync.play(npc, "verify-independent", 200); return null; });
        Field active = NpcAnimationSync.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        Object state = ((Map<?, ?>) active.get(null)).get(npc);
        Map<?, ?> animations = (Map<?, ?>) field(state, "animations");
        check(animations.size() == 2 && field(state, "visible") == animations.get(ordered), "ordered pose priority");
        NpcAnimationSync.withOwner(ordered, true, () -> { NpcAnimationSync.stop(npc); return null; });
        check(animations.size() == 1 && field(state, "visible") == animations.get(independent), "cancel only own pose and resume sibling");
        NpcAnimationSync.stopAll(npc);
        CNPCMagic.LOGGER.info("VERIFY PASS independent animation ownership and fallback");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (guiNpc == null || Boolean.getBoolean("cnpcmagic.verify.reload")) return;
        viewer = (ServerPlayer) event.getEntity();
        viewer.server.getPlayerList().op(viewer.getGameProfile());
        NoppesUtilServer.setEditingNpc(viewer, guiNpc);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || reported || PROBES.isEmpty() || Boolean.getBoolean("cnpcmagic.verify.reload")) return;
        long elapsed = PROBES.get(0).npc.level().getGameTime() - start;
        for (Probe probe : PROBES) {
            probe.target.setPos(probe.anchor);
            probe.target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            probe.npc.setTarget(elapsed >= 300 && elapsed < 305 ? null : probe.target);
        }
        if (elapsed == 10) cancellable.enabled = false;
        if (elapsed == 40) cancellable.enabled = true;
        Stats failedStats = PROBES.get(0).stats.get("5");
        if (failedStats != null && !failedStats.starts.isEmpty()) {
            long first = failedStats.starts.get(0);
            // Cancel before its three-tick natural end, regardless of setup/first-entity-tick timing.
            if (elapsed == first + 1) failedRule.enabled = false;
            if (elapsed == first + 2) failedRule.enabled = true;
        }
        if (viewer != null && !viewer.isRemoved()) {
            NoppesUtilServer.setEditingNpc(viewer, guiNpc);
            if (elapsed >= 700) guiNpc.setPos(viewer.getX() - 3, 80, -1.5);
        }
        if (elapsed < 1100) return;
        reported = true;
        try {
            Probe fake = PROBES.get(0);
            Stats primary = fake.stats.get("1"), follower = fake.stats.get("2"), fast = fake.stats.get("3");
            Stats slow = fake.stats.get("4"), failed = fake.stats.get("5");
            CNPCMagic.LOGGER.info("VERIFY independent failure timeline starts={} ends={} completed={} cancelled={}",
                failed.starts, failed.ends, failed.completed, failed.cancelled);
            check(fake.maxActive >= 4 && primary.completed >= 2 && follower.completed >= 2, "concurrent sessions and ordered progress");
            check(follower.starts.get(0) >= 170 && fast.starts.stream().filter(t -> t < follower.starts.get(0)).count() >= 4,
                "independent bypasses ordered round");
            verifyTiming(fast, 5, 20, 20, false);
            verifyTiming(slow, 60, 50, 5, false);
            check(slow.cancelled >= 1 && slow.starts.get(1) >= 40, "disable cancels only one independent session");
            check(failed.completed == 0, "failed casts do not count as successes");
            verifyTiming(failed, 3, 20, 20, true);
            CNPCMagic.LOGGER.info("VERIFY PASS independent scheduler maxActive={} fast={} slow={} follower={} cancelled={}",
                fake.maxActive, fast.starts, slow.starts, follower.starts, slow.cancelled);
            for (Probe probe : PROBES) {
                if (probe == fake) continue;
                check(probe.maxActive >= 2 && probe.stats.size() == 2 && probe.stats.values().stream().allMatch(s -> s.completed >= 3),
                    "native overlapping lifecycles " + probe.provider);
                check(probe.target.getHealth() < 10000, "native parallel real damage " + probe.provider);
                CNPCMagic.LOGGER.info("VERIFY PASS independent native {} maxActive={} damage={}",
                    probe.provider, probe.maxActive, 10000 - probe.target.getHealth());
            }
            if (guiNpc != null) {
                for (String id : List.of("irons_spellbooks", "goety")) {
                    if (MagicProviders.get(id) == null) continue;
                    List<MagicRule> rules = MagicDataStore.get(viewer.server).getRules(guiNpc.getUUID(), id);
                    check(rules.size() == 2 && rules.get(0).independent && !rules.get(1).independent, "GUI packet preserved per-entry flags " + id);
                }
                CNPCMagic.LOGGER.info("VERIFY PASS independent GUI server save per-entry flags");
            }
        } catch (RuntimeException | LinkageError e) {
            CNPCMagic.LOGGER.error("VERIFY FAIL independent checks", e);
        }
    }

    private static void verifyTiming(Stats stats, int durationTicks, int cooldown, int minimumNatural, boolean requireEarlyCancel) {
        check(stats.ends.size() >= stats.starts.size() - 1, "every retry has a recorded termination");
        int naturalRetries = 0, earlyCancelledRetries = 0;
        for (int i = 1; i < stats.starts.size(); i++) {
            End end = stats.ends.get(i - 1);
            long duration = end.tick - stats.starts.get(i - 1);
            long backoff = stats.starts.get(i) - end.tick;
            int expected = end.cancelled ? 20 : cooldown;
            check(backoff >= expected, "retry waits its own CD/backoff after actual end");
            check(duration >= 1 && (end.cancelled ? duration <= durationTicks : duration == durationTicks),
                "lifecycle has full duration unless cancelled");
            if (backoff == expected) {
                if (!end.cancelled) naturalRetries++;
                else if (duration < durationTicks) earlyCancelledRetries++;
            }
        }
        check(naturalRetries >= minimumNatural && (!requireEarlyCancel || earlyCancelledRetries >= 1),
            "independent retries use own CD/backoff, ignore round count and preserve early cancellation");
        CNPCMagic.LOGGER.info("VERIFY PASS independent end-based timing duration={} cd={} natural={} earlyCancelled={}",
            durationTicks, cooldown, naturalRetries, earlyCancelledRetries);
    }

    private record End(long tick, boolean cancelled) {
    }

    private static final class Stats {
        final List<Long> starts = new ArrayList<>();
        final List<End> ends = new ArrayList<>();
        int completed, cancelled;
    }

    private static final class Probe {
        final EntityNPCInterface npc;
        final Pig target;
        final String provider;
        final net.minecraft.world.phys.Vec3 anchor;
        final Map<String, Stats> stats = new HashMap<>();
        int active, maxActive;
        Probe(EntityNPCInterface npc, Pig target, String provider) {
            this.npc = npc; this.target = target; this.provider = provider; anchor = target.position();
        }
    }

    private static MagicCast watch(LivingEntity caster, String key, MagicCast cast) {
        Probe probe = PROBES.stream().filter(p -> p.npc == caster).findFirst().orElse(null);
        if (probe == null || cast == null) return cast;
        Stats stats = probe.stats.computeIfAbsent(key, k -> new Stats());
        stats.starts.add(caster.level().getGameTime() - start);
        probe.maxActive = Math.max(probe.maxActive, ++probe.active);
        return new MagicCast() {
            private boolean finished;
            public boolean tick(LivingEntity mob, LivingEntity target) {
                boolean done = cast.tick(mob, target);
                if (done && !finished) {
                    finished = true; probe.active--;
                    stats.ends.add(new End(mob.level().getGameTime() - start, false));
                    if (cast.wasSuccessful()) stats.completed++;
                }
                return done;
            }
            public boolean wasSuccessful() { return cast.wasSuccessful(); }
            public void cancel(LivingEntity mob) {
                cast.cancel(mob);
                if (!finished) {
                    finished = true; probe.active--; stats.cancelled++;
                    stats.ends.add(new End(mob.level().getGameTime() - start, true));
                }
            }
        };
    }

    private record WatchingProvider(MagicProvider delegate) implements MagicProvider {
        public String getId() { return delegate.getId(); }
        public boolean isValidSpell(String spell) { return delegate.isValidSpell(spell); }
        public int getMaxLevel(String spell) { return delegate.getMaxLevel(spell); }
        public MagicCast startCast(LivingEntity caster, String spell, int level, LivingEntity target) {
            return watch(caster, spell, delegate.startCast(caster, spell, level, target));
        }
    }

    private static final class TestProvider implements MagicProvider {
        public String getId() { return "cnpcmagic_parallel_verify"; }
        public boolean isValidSpell(String spell) { return spell.equals("parallel_test"); }
        public int getMaxLevel(String spell) { return 5; }
        public MagicCast startCast(LivingEntity caster, String spell, int level, LivingEntity target) {
            return watch(caster, String.valueOf(level), new MagicCast() {
                int ticks;
                boolean cancelled;
                public boolean tick(LivingEntity mob, LivingEntity victim) {
                    return ++ticks >= switch (level) { case 1 -> 80; case 3 -> 5; case 4 -> 60; case 5 -> 3; default -> 1; };
                }
                public boolean wasSuccessful() { return !cancelled && level != 5; }
                public void cancel(LivingEntity mob) { cancelled = true; }
            });
        }
    }
}

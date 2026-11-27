package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.MagicCombatHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in integration fixtures, inactive unless launched with -Dcnpcmagic.verify=true. */
public final class MagicVerification {
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static final List<Long> FIRST = new ArrayList<>();
    private static final List<Long> SECOND = new ArrayList<>();
    private static final String ECHO = "irons_spellbooks:echoing_strikes";
    private static final String ECHO_SWORD = "irons_spellbooks:echoing_sword";
    private static long started;
    private static boolean reported;
    private static long decisionChecks, orderChecks;
    private static final UUID ASYNC_PROBE = UUID.randomUUID();
    private static int asyncPhase;
    private static long asyncReused;

    public static void checkDecision(EntityNPCInterface caster, LivingEntity target, MagicRule rule, boolean matches) {
        if (matches != rule.conditions.test(caster, target, rule.range)) {
            CNPCMagic.LOGGER.error("VERIFY FAIL combat/source same-tick condition parity {}", rule.spellId);
            throw new IllegalStateException("Combat decision differs from 1.20.1 conditions");
        }
        decisionChecks++;
    }

    public static void checkOrder(List<MagicRule> rules, List<Integer> actual) {
        var expected = java.util.stream.IntStream.range(0, rules.size()).boxed().filter(i -> !rules.get(i).independent)
            .sorted(java.util.Comparator.comparingInt(i -> rules.get(i).sequenceOrder())).toList();
        if (!expected.equals(actual)) {
            CNPCMagic.LOGGER.error("VERIFY FAIL combat/source ordered candidate parity");
            throw new IllegalStateException("Combat order differs from 1.20.1");
        }
        orderChecks++;
    }

    private static void asyncChecks() {
        var far = new top.cnpcmagic.data.RuleConditions.Snapshot(1, false, 100, 0, 8, 0, 0, 0, 0, false, false, false, false);
        var near = new top.cnpcmagic.data.RuleConditions.Snapshot(1, false, 64, 0, 8, 0, 0, 0, 0, false, false, false, false);
        var before = top.cnpcmagic.server.CombatDecisions.stats();
        if (asyncPhase == 0) {
            check(!top.cnpcmagic.server.CombatDecisions.test(ASYNC_PROBE, 0, far), "unready worker uses same-tick fallback");
            asyncReused = before.reusedConditions(); asyncPhase++;
        } else if (asyncPhase == 1) {
            check(!top.cnpcmagic.server.CombatDecisions.test(ASYNC_PROBE, 0, far), "cached far decision");
            if (top.cnpcmagic.server.CombatDecisions.stats().reusedConditions() == before.reusedConditions()) return;
            check(top.cnpcmagic.server.CombatDecisions.test(ASYNC_PROBE, 0, near), "stale rejection cannot suppress in-range attack");
            asyncPhase++;
        } else if (asyncPhase == 2) {
            check(top.cnpcmagic.server.CombatDecisions.test(ASYNC_PROBE, 0, near), "cached near decision");
            if (top.cnpcmagic.server.CombatDecisions.stats().reusedConditions() == before.reusedConditions()) return;
            check(!top.cnpcmagic.server.CombatDecisions.test(ASYNC_PROBE, 0, far), "stale acceptance cannot authorize out-of-range attack");
            top.cnpcmagic.server.CombatDecisions.remove(ASYNC_PROBE);
            check(top.cnpcmagic.server.CombatDecisions.stats().activeNpcs() == before.activeNpcs() - 1, "removed NPC leaves coordinator");
            asyncPhase++;
            CNPCMagic.LOGGER.info("VERIFY PASS safe async decisions: real worker reuse, unready same-tick fallback, stale false/true rejection, removal");
        }
    }

    private MagicVerification() {
    }

    // Keep optional Ars types out of the verifier also used by hard-dependency-only parity checks.
    private static final class ArsFixture {
        private static String harm() {
            return top.cnpcmagic.compat.ars.ArsAdapter.getId(
                com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile.INSTANCE,
                com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm.INSTANCE);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static MagicRule rule(String spell, int casts, int cooldown, int level) {
        MagicRule rule = new MagicRule();
        rule.spellId = spell;
        rule.enabled = true;
        rule.casts = casts;
        rule.cooldownTicks = cooldown;
        rule.level = level;
        rule.range = 32;
        return rule;
    }

    private static void dataTests() {
        UUID id = UUID.randomUUID();
        CompoundTag providers = new CompoundTag();
        providers.put("irons_spellbooks", rule("irons_spellbooks:firebolt", 2, 60, 3).save());
        CompoundTag npcs = new CompoundTag();
        npcs.put(id.toString(), providers);
        CompoundTag root = new CompoundTag();
        root.put("npcs", npcs);
        MagicDataStore store = new MagicDataStore(root);
        check(store.getRules(id, "irons_spellbooks").size() == 1, "old data migration");
        CompoundTag legacy = rule("irons_spellbooks:firebolt", 2, 60, 3).save();
        legacy.remove("independent");
        legacy.remove("sequence");
        check(!MagicRule.load(legacy).independent && !new MagicRule().independent, "independent defaults off");
        check(MagicRule.load(legacy).sequence == -1, "legacy sequence default");
        List<MagicRule> entries = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            MagicRule entry = rule("irons_spellbooks:firebolt", i + 1, i * 20, i + 1);
            entry.independent = i % 2 == 1;
            entry.sequence = entry.independent ? -1 : 199 - i;
            entries.add(entry);
        }
        store.setRules(id, "irons_spellbooks", entries);
        store = new MagicDataStore(store.save(new CompoundTag()));
        List<MagicRule> restored = store.getRules(id, "irons_spellbooks");
        check(restored.size() == 200 && restored.get(199).level == 200 && restored.get(0).level == 1,
            "array independence and round trip");
        check(!restored.get(0).independent && restored.get(199).independent, "per-entry independent round trip");
        check(restored.equals(entries), "sequence survives the same SavedData round trip");
        MagicRule invalid = rule("irons_spellbooks:does_not_exist", -1, -1, -1);
        invalid.sanitize(new CounterProvider());
        check(invalid.spellId.isEmpty() && invalid.casts == 1 && invalid.cooldownTicks == 0 && invalid.level == 1,
            "server validation");
        MagicProvider iron = MagicProviders.get("irons_spellbooks");
        if (iron != null) {
            String acupuncture = "irons_spellbooks:acupuncture";
            check(iron.isValidSpell(acupuncture) && iron.getMaxLevel(acupuncture) >= 100,
                "Iron acupuncture level limit");
            MagicRule level100 = rule(acupuncture, 1, 60, 100).sanitize(iron);
            MagicRule restoredLevel100 = MagicRule.load(level100.save()).sanitize(iron);
            check(level100.level == 100 && restoredLevel100.level == 100,
                "Iron level 100 persistence round trip");
            CNPCMagic.LOGGER.info("VERIFY PASS Iron acupuncture level 100 validation and persistence");
        }
        MagicRule twoCasts = rule("irons_spellbooks:firebolt", 2, 60, 1);
        MagicCombatHandler.RuleState state = new MagicCombatHandler.RuleState();
        check(!state.completed(1, twoCasts) && state.completed(2, twoCasts), "round count");
        check(!state.isReady(61) && state.isReady(62), "cooldown boundary");
        check(!state.completed(62, twoCasts) && state.completed(63, twoCasts), "round reset");
        twoCasts.independent = true;
        check(state.completed(70, twoCasts) && !state.isReady(129) && state.isReady(130), "independent skips round count");
        CNPCMagic.LOGGER.info("VERIFY PASS data: 200 independent entries, old migration, validation, repeated rounds");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        try {
            for (String id : List.of("irons_spellbooks", "goety", "ars_nouveau")) {
                check((MagicProviders.get(id) != null) == ModList.get().isLoaded(id), "optional provider " + id);
            }
            CNPCMagic.LOGGER.info("VERIFY PASS installed providers {}", MagicProviders.all().stream().map(MagicProvider::getId).toList());
            dataTests();
            ConditionVerification.snapshots(event.getServer().overworld());
            if (Boolean.getBoolean("cnpcmagic.verify.reload")) {
                var saved = MagicDataStore.get(event.getServer()).save(new CompoundTag()).getCompound("npcs");
                check(!saved.isEmpty(), "SavedData present after real restart");
                check(saved.toString().contains("counter"), "duplicate fixture persisted");
                if (MagicProviders.get("goety") != null && MagicProviders.get("irons_spellbooks") != null) {
                    check(saved.getAllKeys().stream().map(saved::getCompound).anyMatch(providers ->
                        MagicDataStore.loadRules(providers.get("goety")).stream().anyMatch(r -> r.sequence == 0)
                        && MagicDataStore.loadRules(providers.get("irons_spellbooks")).stream().anyMatch(r -> r.sequence == 1)),
                        "cross-provider order survived restart");
                    CNPCMagic.LOGGER.info("VERIFY PASS cross-provider order persisted after restart");
                }
                CNPCMagic.LOGGER.info("VERIFY PASS real restart SavedData NPCs={}", saved.getAllKeys().size());
                return;
            }
            TRIALS.clear();
            FIRST.clear();
            SECOND.clear();
            reported = false;
            ServerLevel level = event.getServer().overworld();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            level.setDayTime(6000);
            started = level.getGameTime();
            for (MagicProvider provider : new ArrayList<>(MagicProviders.all())) {
                MagicProviders.register(new CountingProvider(provider));
            }
            MagicProviders.register(new CounterProvider());
            BlockPos origin = new BlockPos(0, 80, 0);
            for (int x = -1; x <= 19; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setChunkForced(x, z, true);
                }
            }
            for (int x = -4; x < 304; x++) {
                for (int z = -4; z < 16; z++) {
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            int view = ModList.get().isLoaded("goety") ? (ModList.get().isLoaded("irons_spellbooks") ? 180 : 100) : 0;
            level.setDefaultSpawnPos(origin.offset(view + 6, 0, -3), 60);
            if (ModList.get().isLoaded("irons_spellbooks")) {
                fixture(level, origin, "irons_spellbooks", List.of(rule("irons_spellbooks:chain_lightning", 2, 60, 1)));
                fixture(level, origin.offset(20, 0, 0), "irons_spellbooks", List.of(rule("irons_spellbooks:firebolt", 2, 60, 1)));
                fixture(level, origin.offset(40, 0, 0), "irons_spellbooks", List.of(rule("irons_spellbooks:fire_breath", 1, 60, 1)));
                fixture(level, origin.offset(60, 0, 0), "irons_spellbooks", List.of(rule("irons_spellbooks:ray_of_siphoning", 1, 60, 1)));
                fixture(level, origin.offset(200, 0, 0), "irons_spellbooks",
                    List.of(rule("irons_spellbooks:acupuncture", 1, 60, 100)));
                fixture(level, origin.offset(230, 0, 0), "irons_spellbooks", List.of(rule(ECHO, 1, 60, 1)));
                fixture(level, origin.offset(260, 0, 0), "irons_spellbooks", List.of(rule(ECHO, 1, 60, 1)));
            }
            if (ModList.get().isLoaded("goety")) {
                fixture(level, origin.offset(100, 0, 0), "goety", List.of(rule("goety:soul_bolt_focus", 2, 60, 1)));
                fixture(level, origin.offset(120, 0, 0), "goety", List.of(rule("goety:magic_bolt_focus", 2, 60, 2)));
                fixture(level, origin.offset(320, 0, 0), "goety", List.of(rule("goety:water_jet_focus", 1, 60, 1)));
                fixture(level, origin.offset(140, 0, 0), "goety", List.of(rule("goety:fire_breath_focus", 1, 60, 1)));
                fixture(level, origin.offset(160, 0, 0), "goety", List.of(rule("goety:prisma_beam_focus", 1, 60, 1)));
                fixture(level, origin.offset(340, 0, 0), "goety", List.of(rule("goety:steaming_focus", 1, 60, 1)));
            }
            if (ModList.get().isLoaded("ars_nouveau")) {
                fixture(level, origin.offset(280, 0, 0), "ars_nouveau", List.of(rule(ArsFixture.harm(), 1, 60, 1)));
            }
            fixture(level, origin.offset(80, 0, 0), "cnpcmagic_verify", List.of(
                rule("cnpcmagic_verify:counter", 2, 60, 1), rule("cnpcmagic_verify:counter", 1, 100, 2)));
            if (ModList.get().isLoaded("goety") && ModList.get().isLoaded("irons_spellbooks")) {
                MagicRule goety = rule("goety:soul_bolt_focus", 1, 80, 1);
                MagicRule iron = rule("irons_spellbooks:firebolt", 1, 80, 1);
                goety.sequence = 0; iron.sequence = 1;
                fixture(level, origin.offset(180, 0, 0), "goety", List.of(goety));
                Trial mixed = TRIALS.get(TRIALS.size() - 1);
                mixed.mixed = true;
                MagicDataStore.get(event.getServer()).setRules(mixed.npc.getUUID(), "irons_spellbooks",
                    List.of(iron));
            }
            CNPCMagic.LOGGER.info("VERIFY fixtures ready at 0 80 0; deadline 1200 ticks");
        } catch (RuntimeException | LinkageError e) {
            reported = true;
            CNPCMagic.LOGGER.error("VERIFY FAIL setup", e);
            if (Boolean.getBoolean("cnpcmagic.verify.exit")) event.getServer().halt(false);
        }
    }

    private static void fixture(ServerLevel level, BlockPos pos, String provider, List<MagicRule> rules) {
        prepareArena(level, pos);
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(level);
        Pig target = EntityType.PIG.create(level);
        check(npc != null && target != null, "fixture entities");
        check(MagicProviders.get(provider).isValidSpell(rules.get(0).spellId), "fixture spell " + rules.get(0).spellId);
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setPersistenceRequired();
        npc.display.setName(rules.get(0).spellId);
        boolean closeEcho = rules.get(0).spellId.equals(ECHO) && pos.getX() == 230;
        target.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + (closeEcho ? 1.6 : provider.equals("goety") ? 3.5 : 8.5), 0, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        target.setHealth(10000);
        level.addFreshEntity(npc);
        level.addFreshEntity(target);
        npc.setTarget(target);
        CompoundTag before = npc.saveWithoutId(new CompoundTag());
        MagicDataStore.get(level.getServer()).setRules(npc.getUUID(), provider, rules);
        CompoundTag after = npc.saveWithoutId(new CompoundTag());
        // CNPC serialization can change its own transient fields even within the same tick.
        var changed = before.getAllKeys().stream().filter(key -> !java.util.Objects.equals(before.get(key), after.get(key))).toList();
        CNPCMagic.LOGGER.info("VERIFY NPC serializer changed keys {}", changed);
        check(!hasRule(after), "magic rule fields must not enter NPC NBT");
        CNPCMagic.LOGGER.info("VERIFY PASS configuration outside NPC NBT {}", rules.get(0).spellId);
        TRIALS.add(new Trial(npc, target, rules.get(0).spellId, rules.get(0).level));
    }

    static void prepareArena(ServerLevel level, BlockPos origin) {
        for (int x = (origin.getX() - 4) >> 4; x <= (origin.getX() + 4) >> 4; x++)
            for (int z = (origin.getZ() - 2) >> 4; z <= (origin.getZ() + 12) >> 4; z++)
                level.setChunkForced(x, z, true);
        int cleared = 0;
        for (int x = -4; x <= 4; x++) for (int z = -2; z <= 12; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y <= 6; y++) {
                BlockPos pos = origin.offset(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    cleared++;
                }
            }
        }
        CNPCMagic.LOGGER.info("VERIFY arena origin={} removedObstructions={}", origin, cleared);
    }

    @SubscribeEvent
    public static void entityJoined(EntityJoinLevelEvent event) {
        if (reported || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)) return;
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString();
        for (Trial trial : TRIALS) {
            if (trial.npc != projectile.getOwner()) continue;
            if (id.equals(ECHO_SWORD)) trial.echoes++;
            if (trial.spell.equals("goety:steaming_focus") && id.equals("goety:steam_missile")) trial.projectiles++;
        }
    }

    private static boolean hasRule(net.minecraft.nbt.Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("spell") && compound.contains("casts") && compound.contains("cooldown")) return true;
            return compound.getAllKeys().stream().anyMatch(key -> hasRule(compound.get(key)));
        }
        if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag item : list) if (hasRule(item)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (reported || event.getEntity().level().isClientSide || event.getNewDamage() <= 0) return;
        for (Trial trial : TRIALS) {
            if (event.getEntity() != trial.target) continue;
            var attacker = event.getSource().getEntity();
            // Ars Harm uses AbstractEffect.getPlayer: its FakePlayer retains the real caster's UUID.
            boolean arsOwner = trial.spell.startsWith("ars_nouveau:")
                && attacker instanceof net.neoforged.neoforge.common.util.FakePlayer
                && attacker.getUUID().equals(trial.npc.getUUID());
            if (attacker != trial.npc && !arsOwner) {
                trial.otherDamageTypes.add(event.getSource().getMsgId());
                continue;
            }
            if (arsOwner) trial.fakePlayerDamage += event.getNewDamage();
            trial.nativeDamage += event.getNewDamage();
            trial.damageTypes.add(event.getSource().getMsgId());
            if (!trial.casting) continue;
            long tick = trial.npc.level().getGameTime();
            if (tick == trial.lastPulseTick) continue;
            trial.lastPulseTick = tick;
            trial.pulsesInCast++;
            trial.maxPulsesInCast = Math.max(trial.maxPulsesInCast, trial.pulsesInCast);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (reported || TRIALS.isEmpty()) {
            return;
        }
        for (Trial trial : TRIALS) {
            trial.target.setPos(trial.anchor);
            trial.target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            trial.npc.setTarget(trial.target);
        }
        asyncChecks();
        long elapsed = TRIALS.get(0).npc.level().getGameTime() - started;
        if (elapsed < 1200) {
            return;
        }
        reported = true;
        for (Trial trial : TRIALS) {
            if (trial.spell.equals("cnpcmagic_verify:counter")) {
                continue;
            }
            float damage = 10000 - trial.target.getHealth();
            if (trial.spell.equals(ECHO)) {
                boolean close = trial.npc.distanceToSqr(trial.target) < 4;
                boolean passed = trial.completed >= 3 && (close ? trial.echoes > 0 && damage > 0 : trial.echoes == 0 && damage == 0);
                CNPCMagic.LOGGER.info("VERIFY {} Iron echoing melee follow-up near={} casts={} nativeSwords={} damage={}",
                    passed ? "PASS" : "FAIL", close, trial.completed, trial.echoes, damage);
                continue;
            }
            if (trial.mixed) {
                boolean passed = trial.startOrder.size() >= 4 && trial.startOrder.subList(0, 4)
                    .equals(List.of("goety", "irons_spellbooks", "goety", "irons_spellbooks"));
                CNPCMagic.LOGGER.info("VERIFY {} cross-provider order {}", passed ? "PASS" : "FAIL", trial.startOrder);
            }
            boolean passed = trial.starts >= 3 && trial.completed >= 3 && trial.nativeDamage > 0;
            if (trial.spell.equals("irons_spellbooks:fire_breath") || trial.spell.equals("irons_spellbooks:ray_of_siphoning")
                || trial.spell.equals("goety:fire_breath_focus") || trial.spell.equals("goety:water_jet_focus")) {
                passed &= trial.maxPulsesInCast >= 3;
                CNPCMagic.LOGGER.info("VERIFY {} continuous damage {} maxPulsesInOneCast={} damageTypes={}",
                    passed ? "PASS" : "FAIL", trial.spell, trial.maxPulsesInCast, trial.damageTypes);
            }
            if (trial.spell.equals("irons_spellbooks:acupuncture")) {
                passed = trial.starts >= 1 && trial.completed >= 1 && trial.levelsMatch
                    && trial.observedLevel == 100 && trial.nativeDamage > 0;
            }
            if (trial.spell.equals("goety:steaming_focus")) {
                passed &= trial.completed >= 3 && trial.projectiles >= trial.completed * 5;
                CNPCMagic.LOGGER.info("VERIFY {} Goety finite charged volleys casts={} steamMissiles={} minimum={}",
                    passed ? "PASS" : "FAIL", trial.completed, trial.projectiles, trial.completed * 5);
            }
            CNPCMagic.LOGGER.info("VERIFY {} {} starts={} completed={} damage={} nativeDamage={} casterUuidFakePlayerDamage={} providers={} otherDamageTypes={}",
                passed ? "PASS" : "FAIL", trial.spell,
                trial.starts, trial.completed, damage, trial.nativeDamage, trial.fakePlayerDamage, trial.providers, trial.otherDamageTypes);
        }
        boolean counters = FIRST.size() >= 6 && SECOND.size() >= 3;
        for (int i = 2; i < FIRST.size(); i += 2) {
            counters &= FIRST.get(i) - FIRST.get(i - 1) >= 60;
        }
        for (int i = 1; i < SECOND.size(); i++) {
            counters &= SECOND.get(i) - SECOND.get(i - 1) >= 100;
        }
        CNPCMagic.LOGGER.info("VERIFY {} scheduler duplicate entries first={} second={}",
            counters ? "PASS" : "FAIL", FIRST, SECOND);
        var async = top.cnpcmagic.server.CombatDecisions.stats();
        boolean combatParity = asyncPhase == 3 && decisionChecks > 0 && orderChecks > 0 && async.batches() > 0
            && async.reusedConditions() > asyncReused && async.reusedOrders() > 0 && async.workersOffThread();
        CNPCMagic.LOGGER.info("VERIFY {} combat/source same-tick parity decisions={} orders={} async={}",
            combatParity ? "PASS" : "FAIL", decisionChecks, orderChecks, async);
        CNPCMagic.LOGGER.info("VERIFY COMPLETE; fixtures retained for visual checks");
        if (Boolean.getBoolean("cnpcmagic.verify.exit")) event.getServer().halt(false);
    }

    private static final class Trial {
        private final EntityNPCInterface npc;
        private final Pig target;
        private final String spell;
        private final int expectedLevel;
        private final net.minecraft.world.phys.Vec3 anchor;
        private int starts;
        private int rejectedStarts;
        private int completed;
        private int observedLevel;
        private boolean levelsMatch = true;
        private boolean mixed;
        private int echoes;
        private int projectiles;
        private float nativeDamage;
        private float fakePlayerDamage;
        private boolean casting;
        private long lastPulseTick = -1;
        private int pulsesInCast, maxPulsesInCast;
        private final java.util.Set<String> damageTypes = new java.util.HashSet<>();
        private final java.util.Set<String> otherDamageTypes = new java.util.HashSet<>();
        private final List<String> startOrder = new ArrayList<>();
        private final java.util.Set<String> providers = new java.util.HashSet<>();

        private Trial(EntityNPCInterface npc, Pig target, String spell, int expectedLevel) {
            this.npc = npc;
            this.target = target;
            this.spell = spell;
            this.expectedLevel = expectedLevel;
            this.anchor = target.position();
        }
    }

    private record CountingProvider(MagicProvider delegate) implements MagicProvider {
        public String getId() { return delegate.getId(); }
        public boolean isValidSpell(String spell) { return delegate.isValidSpell(spell); }
        public int getMaxLevel(String spell) { return delegate.getMaxLevel(spell); }
        public boolean requiresMeleeTarget(String spell) { return delegate.requiresMeleeTarget(spell); }
        public MagicCast startCast(LivingEntity caster, String spell, int level, LivingEntity target) {
            Trial trial = TRIALS.stream().filter(t -> t.npc == caster).findFirst().orElse(null);
            MagicCast cast = delegate.startCast(caster, spell, level, target);
            if (cast == null) {
                if (trial != null && spell.equals("irons_spellbooks:chain_lightning") && trial.rejectedStarts++ % 10 == 0) {
                    var hit = io.redspace.ironsspellbooks.api.util.RaycastBuilder.begin(caster.level(), caster)
                        .range(32).checkForBlocks(true).bbInflation(0.35f).build();
                    CNPCMagic.LOGGER.info("VERIFY chain rejected={} caster={} target={} look={} hit={} victim={}",
                        trial.rejectedStarts, caster.position(), target.position(), caster.getLookAngle(), hit.getType(),
                        hit instanceof net.minecraft.world.phys.EntityHitResult entityHit ? entityHit.getEntity().getType() : hit.getLocation());
                }
                return null;
            }
            if (trial == null) return cast;
            trial.starts++;
            trial.pulsesInCast = 0;
            trial.casting = true;
            trial.observedLevel = level;
            trial.levelsMatch &= level == trial.expectedLevel;
            trial.providers.add(getId());
            if (trial.mixed) trial.startOrder.add(getId());
            return new MagicCast() {
                public boolean tick(LivingEntity mob, LivingEntity victim) {
                    boolean done = cast.tick(mob, victim);
                    if (done && cast.wasSuccessful()) trial.completed++;
                    if (done) trial.casting = false;
                    return done;
                }
                public boolean wasSuccessful() { return cast.wasSuccessful(); }
                public void cancel(LivingEntity mob) { trial.casting = false; cast.cancel(mob); }
            };
        }
    }

    private static final class CounterProvider implements MagicProvider {
        public String getId() { return "cnpcmagic_verify"; }
        public boolean isValidSpell(String spell) { return spell.equals("cnpcmagic_verify:counter"); }
        public int getMaxLevel(String spell) { return 2; }
        public MagicCast startCast(LivingEntity caster, String spell, int level, LivingEntity target) {
            (level == 1 ? FIRST : SECOND).add(caster.level().getGameTime() - started);
            return new MagicCast() {
                private boolean cancelled;
                public boolean tick(LivingEntity mob, LivingEntity victim) { return true; }
                public boolean wasSuccessful() { return !cancelled; }
                public void cancel(LivingEntity mob) { cancelled = true; }
            };
        }
    }
}

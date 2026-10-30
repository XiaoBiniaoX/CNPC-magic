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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
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
    private static long started;
    private static boolean reported;

    private MagicVerification() {
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
        List<MagicRule> entries = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            entries.add(rule("irons_spellbooks:firebolt", i + 1, i * 20, i + 1));
        }
        store.setRules(id, "irons_spellbooks", entries);
        store = new MagicDataStore(store.save(new CompoundTag()));
        List<MagicRule> restored = store.getRules(id, "irons_spellbooks");
        check(restored.size() == 200 && restored.get(199).level == 200 && restored.get(0).level == 1,
            "array independence and round trip");
        MagicRule invalid = rule("irons_spellbooks:does_not_exist", -1, -1, -1);
        invalid.sanitize(new CounterProvider());
        check(invalid.spellId.isEmpty() && invalid.casts == 1 && invalid.cooldownTicks == 0 && invalid.level == 1,
            "server validation");
        MagicRule twoCasts = rule("irons_spellbooks:firebolt", 2, 60, 1);
        MagicCombatHandler.RuleState state = new MagicCombatHandler.RuleState();
        check(!state.completed(1, twoCasts) && state.completed(2, twoCasts), "round count");
        check(!state.isReady(61) && state.isReady(62), "cooldown boundary");
        check(!state.completed(62, twoCasts) && state.completed(63, twoCasts), "round reset");
        CNPCMagic.LOGGER.info("VERIFY PASS data: 200 independent entries, old migration, validation, repeated rounds");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        try {
            for (String id : List.of("irons_spellbooks", "goety")) {
                check((MagicProviders.get(id) != null) == ModList.get().isLoaded(id), "optional provider " + id);
            }
            CNPCMagic.LOGGER.info("VERIFY PASS installed providers {}", MagicProviders.all().stream().map(MagicProvider::getId).toList());
            dataTests();
            if (Boolean.getBoolean("cnpcmagic.verify.reload")) {
                var saved = MagicDataStore.get(event.getServer()).save(new CompoundTag()).getCompound("npcs");
                check(!saved.isEmpty(), "SavedData present after real restart");
                check(saved.toString().contains("counter"), "duplicate fixture persisted");
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
            }
            if (ModList.get().isLoaded("goety")) {
                fixture(level, origin.offset(100, 0, 0), "goety", List.of(rule("goety:soul_bolt_focus", 2, 60, 1)));
                fixture(level, origin.offset(120, 0, 0), "goety", List.of(rule("goety:magic_bolt_focus", 2, 60, 2)));
                fixture(level, origin.offset(140, 0, 0), "goety", List.of(rule("goety:fire_breath_focus", 1, 60, 1)));
                fixture(level, origin.offset(160, 0, 0), "goety", List.of(rule("goety:prisma_beam_focus", 1, 60, 1)));
            }
            fixture(level, origin.offset(80, 0, 0), "cnpcmagic_verify", List.of(
                rule("counter", 2, 60, 1), rule("counter", 1, 100, 2)));
            if (ModList.get().isLoaded("goety") && ModList.get().isLoaded("irons_spellbooks")) {
                fixture(level, origin.offset(180, 0, 0), "goety", List.of(rule("goety:soul_bolt_focus", 1, 80, 1)));
                Trial mixed = TRIALS.get(TRIALS.size() - 1);
                MagicDataStore.get(event.getServer()).setRules(mixed.npc.getUUID(), "irons_spellbooks",
                    List.of(rule("irons_spellbooks:firebolt", 1, 80, 1)));
            }
            CNPCMagic.LOGGER.info("VERIFY fixtures ready at 0 80 0; deadline 1200 ticks");
        } catch (RuntimeException | LinkageError e) {
            reported = true;
            CNPCMagic.LOGGER.error("VERIFY FAIL setup", e);
        }
    }

    private static void fixture(ServerLevel level, BlockPos pos, String provider, List<MagicRule> rules) {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(level);
        Pig target = EntityType.PIG.create(level);
        check(npc != null && target != null, "fixture entities");
        check(MagicProviders.get(provider).isValidSpell(rules.get(0).spellId), "fixture spell " + rules.get(0).spellId);
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.setNoAi(true);
        npc.setInvulnerable(true);
        npc.setPersistenceRequired();
        npc.display.setName(rules.get(0).spellId);
        target.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + (provider.equals("goety") ? 3.5 : 8.5), 0, 0);
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
        check(!after.toString().contains("cnpcmagic"), "configuration must not enter NPC NBT");
        check(!hasRule(after), "magic rule fields must not enter NPC NBT");
        CNPCMagic.LOGGER.info("VERIFY PASS configuration outside NPC NBT {}", rules.get(0).spellId);
        TRIALS.add(new Trial(npc, target, rules.get(0).spellId));
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
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || reported || TRIALS.isEmpty()) {
            return;
        }
        for (Trial trial : TRIALS) {
            trial.target.setPos(trial.anchor);
            trial.target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            trial.npc.setTarget(trial.target);
        }
        long elapsed = TRIALS.get(0).npc.level().getGameTime() - started;
        if (elapsed < 1200) {
            return;
        }
        reported = true;
        for (Trial trial : TRIALS) {
            if (trial.spell.equals("counter")) {
                continue;
            }
            float damage = 10000 - trial.target.getHealth();
            CNPCMagic.LOGGER.info("VERIFY {} {} starts={} completed={} damage={} providers={}",
                trial.starts >= 3 && trial.completed >= 3 && damage > 0 ? "PASS" : "FAIL", trial.spell,
                trial.starts, trial.completed, damage, trial.providers);
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
        CNPCMagic.LOGGER.info("VERIFY COMPLETE; fixtures retained for visual checks");
    }

    private static final class Trial {
        private final EntityNPCInterface npc;
        private final Pig target;
        private final String spell;
        private final net.minecraft.world.phys.Vec3 anchor;
        private int starts;
        private int completed;
        private final java.util.Set<String> providers = new java.util.HashSet<>();

        private Trial(EntityNPCInterface npc, Pig target, String spell) {
            this.npc = npc;
            this.target = target;
            this.spell = spell;
            this.anchor = target.position();
        }
    }

    private record CountingProvider(MagicProvider delegate) implements MagicProvider {
        public String getId() { return delegate.getId(); }
        public boolean isValidSpell(String spell) { return delegate.isValidSpell(spell); }
        public int getMaxLevel(String spell) { return delegate.getMaxLevel(spell); }
        public MagicCast startCast(LivingEntity caster, String spell, int level, LivingEntity target) {
            MagicCast cast = delegate.startCast(caster, spell, level, target);
            if (cast == null) return null;
            Trial trial = TRIALS.stream().filter(t -> t.npc == caster).findFirst().orElse(null);
            if (trial == null) return cast;
            trial.starts++;
            trial.providers.add(getId());
            return new MagicCast() {
                public boolean tick(LivingEntity mob, LivingEntity victim) {
                    boolean done = cast.tick(mob, victim);
                    if (done && cast.wasSuccessful()) trial.completed++;
                    return done;
                }
                public boolean wasSuccessful() { return cast.wasSuccessful(); }
                public void cancel(LivingEntity mob) { cast.cancel(mob); }
            };
        }
    }

    private static final class CounterProvider implements MagicProvider {
        public String getId() { return "cnpcmagic_verify"; }
        public boolean isValidSpell(String spell) { return spell.equals("counter"); }
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

package top.cnpcmagic.verify;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import noppes.npcs.CustomEntities;
import top.cnpcmagic.casting.CastingService;
import top.cnpcmagic.casting.SpellTarget;
import top.cnpcmagic.data.*;
import top.cnpcmagic.provider.*;
import top.cnpcmagic.server.MagicCombatHandler;
import java.util.List;

final class ConditionVerification {
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    static void run(ServerLevel world) {
        int[] starts = {0};
        MagicProviders.register(new MagicProvider() {
            public String getId() { return "condition_probe"; }
            public boolean isValidSpell(String id) { return id.startsWith("condition_probe:"); }
            public int getMaxLevel(String id) { return 1; }
            public MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
                starts[0]++;
                return new MagicCast() {
                    public boolean tick(LivingEntity c, LivingEntity t) { return true; }
                    public boolean wasSuccessful() { return !id.endsWith("failure"); }
                    public void cancel(LivingEntity c) {}
                };
            }
        });
        var caster = CustomEntities.entityCustomNpc.create(world); var target = EntityType.HUSK.create(world);
        check(caster != null && target != null, "condition fixtures");
        caster.moveTo(0, 90, 0, 0, 0); target.moveTo(8, 90, 0, 0, 0);
        RuleConditions conditions = new RuleConditions(); conditions.enabled.clear();
        conditions.casterHealth = 0; conditions.targetHealth = 0; conditions.previousSpell = "missing:spell";
        conditions.casterEffect = "minecraft:wither"; conditions.targetEffect = "minecraft:wither";
        for (RuleConditions.Type type : RuleConditions.Type.values()) {
            conditions.enabled.clear();
            for (boolean any : List.of(false, true)) {
                conditions.any = any; check(conditions.test(caster, target, 1), "empty " + any);
            }
            if (type == RuleConditions.Type.LINE_OF_SIGHT) continue;
            conditions.enabled.add(type); conditions.any = false;
            check(!conditions.test(caster, target, 1), "enabled failing condition " + type);
            conditions.enabled.clear(); check(conditions.test(caster, target, 1), "disabled condition ignored " + type);
        }
        conditions.enabled.add(RuleConditions.Type.LINE_OF_SIGHT);
        check(conditions.test(caster, target, 64), "visible target passes");
        for (int y = 88; y <= 94; y++) world.setBlock(new net.minecraft.core.BlockPos(4, y, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        check(!conditions.test(caster, target, 64), "occluded target fails enabled sight");
        conditions.enabled.clear(); check(conditions.test(caster, target, 64), "occlusion ignored when sight disabled");
        for (int y = 88; y <= 94; y++) world.removeBlock(new net.minecraft.core.BlockPos(4, y, 0), false);
        conditions.enabled.addAll(List.of(RuleConditions.Type.RANGE, RuleConditions.Type.TARGET_HEALTH));
        conditions.any = false; check(!conditions.test(caster, target, 64), "ALL rejects one failed enabled condition");
        conditions.any = true; check(conditions.test(caster, target, 64), "ANY accepts one matching condition");
        check(!conditions.test(caster, target, 1), "ANY rejects when all enabled conditions fail");
        conditions.enabled.clear(); conditions.enabled.add(RuleConditions.Type.TARGET_EFFECT);
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)); check(conditions.test(caster, target, 1), "status condition matches");
        conditions.enabled.clear(); conditions.enabled.add(RuleConditions.Type.PREVIOUS_SPELL);
        conditions.previousSpell = "condition_probe:alpha";
        var success = CastingService.start(caster, conditions.previousSpell, 1, SpellTarget.entity(target), "condition_verification");
        check(success.cast() != null && success.cast().tick(caster, target), "real successful predecessor");
        check(conditions.test(caster, target, 1), "combo alone passes despite distance/health/status being disabled");
        var failed = CastingService.start(caster, "condition_probe:failure", 1, SpellTarget.entity(target), "condition_verification"); failed.cast().tick(caster, target);
        var cancelled = CastingService.start(caster, "condition_probe:cancelled", 1, SpellTarget.entity(target), "condition_verification"); cancelled.cast().cancel(caster);
        check(conditions.test(caster, target, 1), "failure/cancel do not replace successful predecessor");
        conditions.comboTicks = -1; check(!conditions.test(caster, target, 1), "combo expires outside configured window"); conditions.comboTicks = 100;
        MagicRule rule = new MagicRule(); rule.conditions = conditions;
        check(rule.equals(MagicRule.load(rule.save())), "condition NBT round trip");
        CompoundTag legacy = rule.save(); legacy.remove("conditions");
        check(MagicRule.load(legacy).conditions.enabled.equals(java.util.EnumSet.of(RuleConditions.Type.RANGE)), "legacy range-only default");
        RuleConditions disabled = new RuleConditions(); disabled.enabled.clear(); disabled.minimumRange = Double.NaN;
        disabled.casterHealth = Double.NaN; disabled.targetHealth = Double.NaN; disabled.comboTicks = 0;
        disabled.casterEffect = "invalid effect"; disabled.targetEffect = "invalid effect"; disabled.previousSpell = "invalid spell";
        check(disabled.validate(1).isEmpty() && disabled.test(caster, target, 1), "disabled values neither invalidate nor gate rules");
        for (boolean independent : List.of(false, true)) {
            var npc = CustomEntities.entityCustomNpc.create(world); check(npc != null, "scheduler condition NPC");
            npc.moveTo(0, 90, 0, 0, 0); npc.setTarget(target);
            MagicRule gate = new MagicRule(); gate.enabled = true; gate.independent = independent; gate.spellId = "condition_probe:followup"; gate.range = 1;
            gate.conditions.enabled.clear(); gate.conditions.targetHealth = 0;
            MagicDataStore.get(world.getServer()).setRules(npc.getUUID(), "condition_probe", List.of(gate));
            int before = starts[0]; MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 1, "scheduler ignores disabled gates " + independent);
            MagicCombatHandler.reset(npc);
            gate.conditions.enabled.add(RuleConditions.Type.TARGET_HEALTH);
            MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 1, "scheduler enforces enabled gate " + independent);
            MagicCombatHandler.reset(npc);
            gate.conditions.enabled.clear(); gate.conditions.enabled.add(RuleConditions.Type.PREVIOUS_SPELL);
            gate.conditions.previousSpell = "condition_probe:alpha";
            var predecessor = CastingService.start(npc, gate.conditions.previousSpell, 1, SpellTarget.entity(target), "condition_verification");
            check(predecessor.cast() != null && predecessor.cast().tick(npc, target), "scheduler predecessor completed " + independent);
            before = starts[0]; MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 1, "scheduler starts combo with unrelated gates off " + independent);
            MagicCombatHandler.reset(npc);
            gate.conditions.previousSpell = "condition_probe:other";
            MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 1, "scheduler rejects wrong predecessor " + independent);
            MagicCombatHandler.reset(npc);
            gate.conditions.previousSpell = "condition_probe:alpha"; gate.conditions.enabled.add(RuleConditions.Type.TARGET_HEALTH);
            MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 1, "scheduler ALL enforces enabled health with combo " + independent);
            MagicCombatHandler.reset(npc);
            gate.conditions.any = true;
            MagicCombatHandler.onLivingTick(new LivingEvent.LivingTickEvent(npc));
            check(starts[0] == before + 2, "scheduler ANY accepts combo despite failing enabled health " + independent);
            MagicCombatHandler.reset(npc);
        }
        top.cnpcmagic.CNPCMagic.LOGGER.info("REWRITE PASS conditions: independent switches, ALL/ANY, empty-set, sight/status, successful predecessor, ordered/independent scheduler, legacy NBT");
    }
}

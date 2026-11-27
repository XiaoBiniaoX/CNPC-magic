package top.cnpcmagic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import top.cnpcmagic.casting.CastingService;
import java.util.EnumSet;

/** Only explicitly enabled conditions take part in the ALL/ANY decision. */
public final class RuleConditions {
    public enum Type { RANGE, CASTER_HEALTH, TARGET_HEALTH, LINE_OF_SIGHT, CASTER_EFFECT, TARGET_EFFECT, PREVIOUS_SPELL }
    public final EnumSet<Type> enabled = EnumSet.of(Type.RANGE);
    public boolean any;
    public double minimumRange, casterHealth = 50, targetHealth = 50;
    public String casterEffect = "", targetEffect = "", previousSpell = "";
    public int comboTicks = 100;

    /** Capture on the server thread. The returned value contains no entities, worlds or registry holders. */
    public Snapshot snapshot(LivingEntity caster, LivingEntity target, int maximumRange) {
        int mask = 0;
        for (Type type : enabled) mask |= 1 << type.ordinal();
        boolean casterHasEffect = false, targetHasEffect = false, previousMatches = false;
        if (enabled.contains(Type.CASTER_EFFECT)) {
            var id = ResourceLocation.tryParse(casterEffect);
            var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
            casterHasEffect = effect != null && caster.hasEffect(effect);
        }
        if (enabled.contains(Type.TARGET_EFFECT)) {
            var id = ResourceLocation.tryParse(targetEffect);
            var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
            targetHasEffect = effect != null && target.hasEffect(effect);
        }
        if (enabled.contains(Type.PREVIOUS_SPELL)) {
            var last = CastingService.lastSuccess(caster);
            long now = caster.level().getGameTime();
            previousMatches = last != null && last.spellId().equals(previousSpell) && last.dimension().equals(caster.level().dimension())
                && now - last.tick() >= 0 && now - last.tick() <= comboTicks;
        }
        return new Snapshot(mask, any,
            enabled.contains(Type.RANGE) ? caster.distanceToSqr(target) : 0, minimumRange, maximumRange,
            enabled.contains(Type.CASTER_HEALTH) ? caster.getHealth() * 100.0 / caster.getMaxHealth() : 0, casterHealth,
            enabled.contains(Type.TARGET_HEALTH) ? target.getHealth() * 100.0 / target.getMaxHealth() : 0, targetHealth,
            enabled.contains(Type.LINE_OF_SIGHT) && caster.hasLineOfSight(target), casterHasEffect, targetHasEffect, previousMatches);
    }

    public record Snapshot(int enabledMask, boolean any, double distanceSquared, double minimumRange, int maximumRange,
                           double casterHealthPercent, double casterHealthLimit, double targetHealthPercent, double targetHealthLimit,
                           boolean lineOfSight, boolean casterHasEffect, boolean targetHasEffect, boolean previousMatches) {
        /** Pure decision: safe to evaluate on a worker thread. Keep source ALL/ANY and boundary semantics. */
        public boolean test() {
            for (Type type : Type.values()) {
                if ((enabledMask & (1 << type.ordinal())) == 0) continue;
                boolean matches = switch (type) {
                    case RANGE -> distanceSquared >= minimumRange * minimumRange && distanceSquared <= (double)maximumRange * maximumRange;
                    case CASTER_HEALTH -> casterHealthPercent <= casterHealthLimit;
                    case TARGET_HEALTH -> targetHealthPercent <= targetHealthLimit;
                    case LINE_OF_SIGHT -> lineOfSight;
                    case CASTER_EFFECT -> casterHasEffect;
                    case TARGET_EFFECT -> targetHasEffect;
                    case PREVIOUS_SPELL -> previousMatches;
                };
                if (matches == any) return any;
            }
            return !any || enabledMask == 0;
        }
    }

    public boolean test(LivingEntity caster, LivingEntity target, int maximumRange) {
        if (enabled.isEmpty()) return true;
        for (Type type : enabled) {
            boolean matches = switch (type) {
                case RANGE -> caster.distanceToSqr(target) >= minimumRange * minimumRange && caster.distanceToSqr(target) <= (double)maximumRange * maximumRange;
                case CASTER_HEALTH -> caster.getHealth() * 100.0 / caster.getMaxHealth() <= casterHealth;
                case TARGET_HEALTH -> target.getHealth() * 100.0 / target.getMaxHealth() <= targetHealth;
                case LINE_OF_SIGHT -> caster.hasLineOfSight(target);
                case CASTER_EFFECT, TARGET_EFFECT -> {
                    var id = ResourceLocation.tryParse(type == Type.CASTER_EFFECT ? casterEffect : targetEffect);
                    var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                    yield effect != null && (type == Type.CASTER_EFFECT ? caster : target).hasEffect(effect);
                }
                case PREVIOUS_SPELL -> {
                    var last = CastingService.lastSuccess(caster);
                    yield last != null && last.spellId().equals(previousSpell) && last.dimension().equals(caster.level().dimension())
                        && caster.level().getGameTime() - last.tick() >= 0 && caster.level().getGameTime() - last.tick() <= comboTicks;
                }
            };
            if (matches == any) return any;
        }
        return !any;
    }
    public String validate(int maximumRange) {
        if (enabled.contains(Type.RANGE) && (!Double.isFinite(minimumRange) || minimumRange < 0 || minimumRange > maximumRange)) return "distance: 0 <= minimum <= maximum";
        if (enabled.contains(Type.CASTER_HEALTH) && (!Double.isFinite(casterHealth) || casterHealth < 0 || casterHealth > 100)
            || enabled.contains(Type.TARGET_HEALTH) && (!Double.isFinite(targetHealth) || targetHealth < 0 || targetHealth > 100)) return "health: 0–100%";
        if (enabled.contains(Type.CASTER_EFFECT) && ResourceLocation.tryParse(casterEffect) == null
            || enabled.contains(Type.TARGET_EFFECT) && ResourceLocation.tryParse(targetEffect) == null) return "effect: namespaced ID required";
        if (enabled.contains(Type.PREVIOUS_SPELL) && (ResourceLocation.tryParse(previousSpell) == null || comboTicks < 1 || comboTicks > 72000)) return "combo: spell ID and window of 1–72000 ticks required";
        return "";
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Type type : Type.values()) tag.putBoolean(type.name(), enabled.contains(type));
        tag.putBoolean("any", any); tag.putDouble("minimumRange", minimumRange);
        tag.putDouble("casterHealth", casterHealth); tag.putDouble("targetHealth", targetHealth);
        tag.putString("casterEffect", casterEffect); tag.putString("targetEffect", targetEffect);
        tag.putString("previousSpell", previousSpell); tag.putInt("comboTicks", comboTicks); return tag;
    }
    public static RuleConditions load(CompoundTag tag) {
        RuleConditions c = new RuleConditions(); c.enabled.clear();
        for (Type type : Type.values()) if (tag.getBoolean(type.name())) c.enabled.add(type);
        c.any = tag.getBoolean("any"); c.minimumRange = tag.getDouble("minimumRange");
        c.casterHealth = tag.getDouble("casterHealth"); c.targetHealth = tag.getDouble("targetHealth");
        c.casterEffect = tag.getString("casterEffect"); c.targetEffect = tag.getString("targetEffect");
        c.previousSpell = tag.getString("previousSpell"); c.comboTicks = tag.getInt("comboTicks"); return c;
    }
}

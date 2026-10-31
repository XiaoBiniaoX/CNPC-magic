package top.cnpcmagic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import top.cnpcmagic.provider.MagicProvider;

public class MagicRule {
    public static final int MAX_CASTS = 100;
    public static final int MAX_COOLDOWN_TICKS = 72000;
    public static final int MAX_RANGE = 64;

    public String spellId = "";
    public int level = 1;
    public int casts = 3;
    public int cooldownTicks = 200;
    public int range = 6;
    public boolean enabled = false;
    public boolean independent = false;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MagicRule rule)) return false;
        return level == rule.level && casts == rule.casts && cooldownTicks == rule.cooldownTicks
            && range == rule.range && enabled == rule.enabled && independent == rule.independent
            && java.util.Objects.equals(spellId == null ? "" : spellId, rule.spellId == null ? "" : rule.spellId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(spellId, level, casts, cooldownTicks, range, enabled, independent);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("spell", spellId == null ? "" : spellId);
        tag.putInt("level", level);
        tag.putInt("casts", casts);
        tag.putInt("cooldown", cooldownTicks);
        tag.putInt("range", range);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("independent", independent);
        return tag;
    }

    public static MagicRule load(CompoundTag tag) {
        MagicRule rule = new MagicRule();
        rule.spellId = tag.getString("spell");
        rule.level = tag.getInt("level");
        rule.casts = tag.getInt("casts");
        rule.cooldownTicks = tag.getInt("cooldown");
        rule.range = tag.getInt("range");
        rule.enabled = tag.getBoolean("enabled");
        rule.independent = tag.getBoolean("independent");
        return rule;
    }

    public MagicRule sanitize(MagicProvider provider) {
        if (spellId == null) {
            spellId = "";
        }
        if (!spellId.isEmpty() && !provider.isValidSpell(spellId)) {
            spellId = "";
        }
        int maxLevel = spellId.isEmpty() ? 1 : Math.max(1, provider.getMaxLevel(spellId));
        level = Mth.clamp(level, 1, maxLevel);
        casts = Mth.clamp(casts, 1, MAX_CASTS);
        cooldownTicks = Mth.clamp(cooldownTicks, 0, MAX_COOLDOWN_TICKS);
        range = Mth.clamp(range, 1, MAX_RANGE);
        return this;
    }
}

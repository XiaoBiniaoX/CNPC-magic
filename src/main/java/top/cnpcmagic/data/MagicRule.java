package top.cnpcmagic.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.wizardry.WizardrySpells;

public final class MagicRule {
    public static final int MAX_LEVEL = 100;
    public static final int MAX_CASTS = 100;
    public static final int MAX_COOLDOWN_TICKS = 72000;
    public static final int MAX_RANGE = 64;

    public String spellId = "";
    public int level = 1;
    public int casts = 3;
    public int cooldownTicks = 200;
    public int range = 6;
    public boolean enabled;
    public boolean independent;

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("spell", spellId == null ? "" : spellId);
        tag.setInteger("level", level);
        tag.setInteger("casts", casts);
        tag.setInteger("cooldown", cooldownTicks);
        tag.setInteger("range", range);
        tag.setBoolean("enabled", enabled);
        tag.setBoolean("independent", independent);
        return tag;
    }

    public static MagicRule read(NBTTagCompound tag) {
        MagicRule rule = new MagicRule();
        rule.spellId = tag.getString("spell");
        rule.level = tag.hasKey("level", 3) ? tag.getInteger("level") : 1;
        rule.casts = tag.hasKey("casts", 3) ? tag.getInteger("casts") : 3;
        rule.cooldownTicks = tag.hasKey("cooldown", 3) ? tag.getInteger("cooldown") : 200;
        rule.range = tag.hasKey("range", 3) ? tag.getInteger("range") : 6;
        rule.enabled = tag.getBoolean("enabled");
        rule.independent = tag.getBoolean("independent");
        return rule;
    }

    public MagicRule sanitize(EntityNPCInterface npc) {
        if (spellId == null || spellId.length() > 128 || !WizardrySpells.isSupported(spellId, npc)) {
            spellId = "";
            enabled = false;
        }
        level = MathHelper.clamp(level, 1, MAX_LEVEL);
        casts = MathHelper.clamp(casts, 1, MAX_CASTS);
        cooldownTicks = MathHelper.clamp(cooldownTicks, 0, MAX_COOLDOWN_TICKS);
        range = MathHelper.clamp(range, 1, MAX_RANGE);
        return this;
    }
}

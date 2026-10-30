package top.cnpcmagic.provider;

import net.minecraft.world.entity.LivingEntity;

public interface MagicProvider {
    String getId();

    boolean isValidSpell(String spellId);

    int getMaxLevel(String spellId);

    MagicCast startCast(LivingEntity caster, String spellId, int level, LivingEntity target);
}

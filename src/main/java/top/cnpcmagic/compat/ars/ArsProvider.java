package top.cnpcmagic.compat.ars;

import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;

public class ArsProvider implements MagicProvider {
    @Override
    public String getId() {
        return "ars_nouveau";
    }

    @Override
    public boolean isValidSpell(String id) {
        return ArsAdapter.getSpell(id, 1) != null;
    }

    @Override
    public int getMaxLevel(String id) {
        return ArsAdapter.getMaxLevel(id);
    }

    @Override
    public MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        return ArsAdapter.startCast(caster, id, level, target);
    }
}

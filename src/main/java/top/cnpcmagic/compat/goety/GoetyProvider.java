package top.cnpcmagic.compat.goety;

import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;

public class GoetyProvider implements MagicProvider {
    @Override
    public String getId() {
        return "goety";
    }

    @Override
    public boolean isValidSpell(String id) {
        return GoetyAdapter.getSpell(id) != null;
    }

    @Override
    public int getMaxLevel(String id) {
        return GoetyAdapter.getMaxLevel(id);
    }

    @Override
    public MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        return GoetyAdapter.startCast(caster, id, level, target);
    }
}

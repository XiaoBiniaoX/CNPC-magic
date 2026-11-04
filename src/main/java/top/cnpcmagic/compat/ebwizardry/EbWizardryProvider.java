package top.cnpcmagic.compat.ebwizardry;

import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProvider;

public class EbWizardryProvider implements MagicProvider {
    @Override
    public String getId() {
        return "ebwizardry";
    }

    @Override
    public boolean isValidSpell(String id) {
        return EbWizardryAdapter.getSpell(id) != null;
    }

    @Override
    public int getMaxLevel(String id) {
        return EbWizardryAdapter.getMaxLevel(id);
    }

    @Override
    public MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        return EbWizardryAdapter.startCast(caster, id, level, target);
    }
}

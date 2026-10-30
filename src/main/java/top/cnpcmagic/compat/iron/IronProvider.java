package top.cnpcmagic.compat.iron;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicCast;

public class IronProvider implements MagicProvider {
    public static final String ID = "irons_spellbooks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isValidSpell(String spellId) {
        return IronAdapter.isSupportedSpell(IronAdapter.getSpell(spellId));
    }

    @Override
    public int getMaxLevel(String spellId) {
        AbstractSpell spell = IronAdapter.getSpell(spellId);
        return spell == null ? 0 : spell.getMaxLevel();
    }

    @Override
    public MagicCast startCast(LivingEntity caster, String spellId, int level, LivingEntity target) {
        return IronAdapter.startCast(caster, spellId, level, target);
    }
}

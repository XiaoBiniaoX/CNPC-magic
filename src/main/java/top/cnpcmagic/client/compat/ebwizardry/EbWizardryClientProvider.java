package top.cnpcmagic.client.compat.ebwizardry;

import com.binaris.wizardry.api.content.spell.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.ebwizardry.EbWizardryAdapter;

import java.util.List;

public class EbWizardryClientProvider implements ClientMagicProvider {
    @Override
    public String getId() {
        return "ebwizardry";
    }

    @Override
    public List<String> getSpellIds() {
        return EbWizardryAdapter.getSpellIds();
    }

    @Override
    public String getDisplayName(String id) {
        Spell spell = EbWizardryAdapter.getSpell(id);
        return spell == null ? id : spell.getDescriptionFormatted().getString();
    }

    @Override
    public int getMaxLevel(String id) {
        return EbWizardryAdapter.getMaxLevel(id);
    }

    @Override
    public List<Component> describe(String id, int level, LivingEntity caster) {
        Spell spell = EbWizardryAdapter.getSpell(id);
        if (spell == null) return List.of();
        return List.of(Component.translatable("cnpcmagic.editor.propertyMana", spell.getCost()),
            Component.translatable("cnpcmagic.editor.propertyCooldown", spell.getCooldown() / 20.0),
            Component.translatable("cnpcmagic.editor.propertyStrength", 100 + 25 * (level - 1)));
    }
}

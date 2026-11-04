package top.cnpcmagic.client.compat.ebwizardry;

import com.binaris.wizardry.api.content.spell.Spell;
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
}

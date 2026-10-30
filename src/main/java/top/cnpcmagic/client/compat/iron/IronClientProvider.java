package top.cnpcmagic.client.compat.iron;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.iron.IronAdapter;

import java.util.ArrayList;
import java.util.List;

public class IronClientProvider implements ClientMagicProvider {
    @Override
    public String getId() {
        return "irons_spellbooks";
    }

    @Override
    public List<String> getSpellIds() {
        List<String> ids = new ArrayList<>();
        for (AbstractSpell spell : IronAdapter.getEnabledSpells()) {
            ids.add(spell.getSpellId());
        }
        return ids;
    }

    @Override
    public String getDisplayName(String spellId) {
        AbstractSpell spell = IronAdapter.getSpell(spellId);
        if (spell == null) {
            return spellId;
        }
        Player player = Minecraft.getInstance().player;
        return player == null ? spell.getSpellId() : spell.getDisplayName(player).getString();
    }

    @Override
    public int getMaxLevel(String spellId) {
        AbstractSpell spell = IronAdapter.getSpell(spellId);
        return spell == null ? 0 : spell.getMaxLevel();
    }
}

package top.cnpcmagic.client.compat.iron;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.compat.iron.IronAdapter;
import top.cnpcmagic.compat.iron.IronProvider;

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
        return spell == null ? 0 : Math.max(IronProvider.EXTENDED_LEVEL_LIMIT, spell.getMaxLevel());
    }

    @Override
    public boolean requiresMeleeTarget(String spellId) {
        return spellId.equals("irons_spellbooks:echoing_strikes");
    }

    @Override
    public List<Component> describe(String id, int level, LivingEntity caster) {
        AbstractSpell spell = IronAdapter.getSpell(id);
        if (!IronAdapter.isSupportedSpell(spell)) return List.of();
        var lines = new ArrayList<Component>();
        lines.add(spell.getSchoolType().getDisplayName());
        lines.addAll(spell.getUniqueInfo(level, caster));
        lines.add(Component.translatable("cnpcmagic.editor.propertyTimes", spell.getEffectiveCastTime(level, caster) / 20.0, spell.getSpellCooldown() / 20.0));
        lines.add(Component.translatable("cnpcmagic.editor.propertyMana", spell.getManaCost(level)));
        lines.add(Component.translatable("cnpcmagic.editor.ironConfigLevel", spell.getMaxLevel()));
        return lines;
    }
}

package top.cnpcmagic.wizardry;

import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;

public final class WizardrySpells {
    private WizardrySpells() {
    }

    public static Spell get(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            Spell spell = Spell.registry.getValue(new ResourceLocation(id));
            return spell == Spells.none ? null : spell;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isSupported(String id, EntityNPCInterface npc) {
        Spell spell = get(id);
        if (spell == null || !spell.isEnabled(SpellProperties.Context.NPCS)) return false;
        try {
            return spell.canBeCastBy(npc, true);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static List<Spell> supported(EntityNPCInterface npc) {
        List<Spell> spells = new ArrayList<>();
        for (Spell spell : Spell.getAllSpells()) {
            ResourceLocation id = spell.getRegistryName();
            if (id != null && isSupported(id.toString(), npc)) spells.add(spell);
        }
        spells.sort(Comparator.comparing(spell -> spell.getRegistryName().toString()));
        return spells;
    }
}

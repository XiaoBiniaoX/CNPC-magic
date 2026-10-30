package top.cnpcmagic.mixin;

import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.entity.EntityShield;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import java.util.Collections;
import java.util.List;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.wizardry.MagicCasterAccess;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCInterface implements ISpellCaster, MagicCasterAccess {
    @Unique private Spell cnpcmagic$continuousSpell = Spells.none;
    @Unique private int cnpcmagic$spellCounter;
    @Unique private SpellModifiers cnpcmagic$modifiers = new SpellModifiers();
    @Unique private EntityShield cnpcmagic$shield;

    @Override
    public List<Spell> getSpells() {
        return Collections.emptyList();
    }

    @Override
    public Spell getContinuousSpell() {
        return cnpcmagic$continuousSpell == null ? Spells.none : cnpcmagic$continuousSpell;
    }

    @Override
    public void setContinuousSpell(Spell spell) {
        cnpcmagic$continuousSpell = spell == null ? Spells.none : spell;
    }

    @Override
    public int getSpellCounter() {
        return cnpcmagic$spellCounter;
    }

    @Override
    public void setSpellCounter(int count) {
        cnpcmagic$spellCounter = count;
    }

    @Override
    public SpellModifiers getModifiers() {
        return cnpcmagic$modifiers;
    }

    @Override
    public void cnpcmagic$setModifiers(SpellModifiers modifiers) {
        cnpcmagic$modifiers = modifiers == null ? new SpellModifiers() : modifiers.copy();
    }

    @Override public EntityShield cnpcmagic$getShield() { return cnpcmagic$shield; }
    @Override public void cnpcmagic$setShield(EntityShield shield) { cnpcmagic$shield = shield; }
}

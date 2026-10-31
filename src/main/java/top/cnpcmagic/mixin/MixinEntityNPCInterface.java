package top.cnpcmagic.mixin;

import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.entity.EntityShield;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.data.MagicDataAccess;
import top.cnpcmagic.wizardry.MagicCasterAccess;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCInterface implements ISpellCaster, MagicCasterAccess, MagicDataAccess {
    @Unique private Spell cnpcmagic$continuousSpell = Spells.none;
    @Unique private int cnpcmagic$spellCounter;
    @Unique private int cnpcmagic$spellTargetId = -1;
    @Unique private SpellModifiers cnpcmagic$modifiers = new SpellModifiers();
    @Unique private EntityShield cnpcmagic$shield;
    @Unique private UUID cnpcmagic$dataId;

    @Inject(method = "func_70014_b", at = @At("RETURN"), require = 1)
    private void cnpcmagic$writeDataReference(NBTTagCompound compound, CallbackInfo callback) {
        UUID reference = cnpcmagic$dataId;
        if (reference == null) reference = ((EntityNPCInterface) (Object) this).getUniqueID();
        compound.setString("CNPCMagicDataId", reference.toString());
    }

    @Inject(method = "func_70037_a", at = @At("RETURN"), require = 1)
    private void cnpcmagic$readDataReference(NBTTagCompound compound, CallbackInfo callback) {
        cnpcmagic$dataId = null;
        String reference = compound.getString("CNPCMagicDataId");
        if (reference.length() != 36) return;
        try {
            cnpcmagic$dataId = UUID.fromString(reference);
        } catch (IllegalArgumentException ignored) {
            // Old NPCs and malformed references fall back to their entity UUID, without touching the store.
        }
    }

    @Override public UUID cnpcmagic$getDataId() { return cnpcmagic$dataId; }
    @Override public void cnpcmagic$setDataId(UUID dataId) { cnpcmagic$dataId = dataId; }

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
    @Override public int cnpcmagic$getSpellTargetId() { return cnpcmagic$spellTargetId; }
    @Override public void cnpcmagic$setSpellTargetId(int targetId) { cnpcmagic$spellTargetId = targetId; }
}

package top.cnpcmagic.mixin;

import electroblob.wizardry.client.ClientProxy;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.packet.PacketNPCCastSpell;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.wizardry.MagicCasterAccess;

@Mixin(value = ClientProxy.class, remap = false)
public abstract class MixinWizardryClientProxy {
    @Inject(method = "handleNPCCastSpellPacket", at = @At("HEAD"))
    private void cnpcmagic$syncModifiers(PacketNPCCastSpell.Message message, CallbackInfo ci) {
        if (Minecraft.getMinecraft().world == null) return;
        Entity entity = Minecraft.getMinecraft().world.getEntityByID(message.casterID);
        if (!(entity instanceof MagicCasterAccess)) return;
        Spell spell = Spell.byNetworkID(message.spellID);
        if (spell != null && (spell.isContinuous || spell == Spells.none)) {
            MagicCasterAccess access = (MagicCasterAccess) entity;
            access.cnpcmagic$setModifiers(message.modifiers);
            access.cnpcmagic$setSpellTargetId(spell == Spells.none ? -1 : message.targetID);
            ((ISpellCaster) entity).setContinuousSpell(spell);
            ((ISpellCaster) entity).setSpellCounter(spell == Spells.none ? 0 : 1);
        }
    }

    @Redirect(method = "handleNPCCastSpellPacket", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityLiving;setAttackTarget(Lnet/minecraft/entity/EntityLivingBase;)V",
        remap = true), require = 1)
    private void cnpcmagic$keepSpellTargetSeparate(EntityLiving caster, EntityLivingBase target) {
        // CNPC filters creative players/owners and fires target scripts here. A visual target is not AI aggro.
        if (!(caster instanceof MagicCasterAccess)) caster.setAttackTarget(target);
    }
}

package top.cnpcmagic.mixin;

import electroblob.wizardry.client.ClientProxy;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.packet.PacketNPCCastSpell;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.wizardry.MagicCasterAccess;

@Mixin(value = ClientProxy.class, remap = false)
public abstract class MixinWizardryClientProxy {
    @Inject(method = "handleNPCCastSpellPacket", at = @At("HEAD"))
    private void cnpcmagic$syncModifiers(PacketNPCCastSpell.Message message, CallbackInfo ci) {
        if (Minecraft.getMinecraft().world == null) return;
        Entity entity = Minecraft.getMinecraft().world.getEntityByID(message.casterID);
        if (!(entity instanceof MagicCasterAccess)) return;
        ((MagicCasterAccess) entity).cnpcmagic$setModifiers(message.modifiers);
        Spell spell = Spell.byNetworkID(message.spellID);
        if (spell == Spells.none && entity instanceof ISpellCaster) {
            ((ISpellCaster) entity).setContinuousSpell(Spells.none);
            ((ISpellCaster) entity).setSpellCounter(0);
        }
    }
}

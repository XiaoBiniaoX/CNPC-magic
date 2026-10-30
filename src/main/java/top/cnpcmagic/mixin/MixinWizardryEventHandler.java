package top.cnpcmagic.mixin;

import electroblob.wizardry.WizardryEventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcmagic.wizardry.MagicCasterAccess;

/** Keeps Wizardry's native per-tick effects aimed at the target carried by its cast packet. */
@Mixin(value = WizardryEventHandler.class, remap = false)
public abstract class MixinWizardryEventHandler {
    @Redirect(method = "onLivingUpdateEvent", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityLiving;getAttackTarget()Lnet/minecraft/entity/EntityLivingBase;",
        remap = true), require = 1)
    private static EntityLivingBase cnpcmagic$spellTarget(EntityLiving caster) {
        if (!(caster instanceof MagicCasterAccess)) return caster.getAttackTarget();
        Entity target = caster.world.getEntityByID(((MagicCasterAccess) caster).cnpcmagic$getSpellTargetId());
        return target instanceof EntityLivingBase && !target.isDead ? (EntityLivingBase) target : null;
    }
}

package top.cnpcmagic.mixin;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.entity.EntityShield;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.util.EntityUtils;
import java.lang.ref.WeakReference;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.wizardry.ShieldAccess;

@Mixin(value = EntityShield.class, remap = false)
public abstract class MixinEntityShield implements ShieldAccess {
    @Shadow public WeakReference<EntityPlayer> player;
    @Unique private EntityLivingBase cnpcmagic$owner;

    @Override public void cnpcmagic$setOwner(EntityLivingBase owner) { cnpcmagic$owner = owner; }

    @Inject(method = "func_70071_h_", at = @At("HEAD"), cancellable = true)
    private void cnpcmagic$commandedShield(CallbackInfo ci) {
        EntityShield shield = (EntityShield) (Object) this;
        EntityLivingBase owner = cnpcmagic$owner;
        if (owner == null && player != null) {
            EntityPlayer candidate = player.get();
            WizardData data = candidate == null ? null : WizardData.get(candidate);
            if (data != null && data.currentlyCasting() == Spells.shield) owner = candidate;
        }
        if (owner == null) return; // Preserve ordinary wand casting unchanged.
        ci.cancel();
        if (owner.isDead || owner.world != shield.world || !EntityUtils.isCasting(owner, Spells.shield)) {
            shield.setDead();
            return;
        }
        Vec3d look = owner.getLookVec();
        shield.setPositionAndRotation(owner.posX + look.x * 0.3, owner.posY + 1 + look.y * 0.3,
            owner.posZ + look.z * 0.3, owner.rotationYawHead, owner.rotationPitch);
        shield.setEntityBoundingBox(new AxisAlignedBB(shield.posX - 0.6, shield.posY - 0.7, shield.posZ - 0.6,
            shield.posX + 0.6, shield.posY + 0.7, shield.posZ + 0.6));
    }
}

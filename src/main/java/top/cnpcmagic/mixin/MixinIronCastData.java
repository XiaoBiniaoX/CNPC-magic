package top.cnpcmagic.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcmagic.compat.iron.IronAdapter;

/** Native helpers see the current session's isolated data; ordinary Iron calls retain their original capability. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.MagicData", remap = false)
public abstract class MixinIronCastData {
    @Inject(method = "getPlayerMagicData(Lnet/minecraft/world/entity/LivingEntity;)Lio/redspace/ironsspellbooks/api/magic/MagicData;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcmagic$sessionData(LivingEntity caster, CallbackInfoReturnable<Object> cir) {
        Object data = IronAdapter.getCastData(caster);
        if (data != null) cir.setReturnValue(data);
    }
}

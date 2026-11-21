package top.cnpcmagic.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcmagic.server.MagicPreview;

@Mixin(ServerLevel.class)
public abstract class MixinPreviewServerParticles {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void cnpcmagic$previewParticles(ParticleOptions type, double x, double y, double z, int count,
                                          double dx, double dy, double dz, double speed, CallbackInfoReturnable<Integer> ci) {
        MagicPreview.particles((ServerLevel)(Object)this, type, x, y, z, count, dx, dy, dz, speed);
    }
}

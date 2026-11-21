package top.cnpcmagic.mixin;

import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcmagic.client.PreviewScene;
import java.util.Map;

/** Native particle factories are shared; preview particles belong only to the detached scene. */
@Mixin(ParticleEngine.class)
public abstract class MixinPreviewParticles implements PreviewScene.ParticleProviders {
    @Shadow @Final private Map<ResourceLocation, ParticleProvider<?>> providers;
    @Override public Map<ResourceLocation, ParticleProvider<?>> cnpcmagic$providers() { return providers; }
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void cnpcmagic$previewParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> ci) {
        if (PreviewScene.current() != null) ci.setReturnValue(PreviewScene.current().particle(options, x, y, z, dx, dy, dz));
    }
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void cnpcmagic$previewAdd(Particle particle, CallbackInfo ci) {
        if (PreviewScene.current() != null) { PreviewScene.current().add(particle); ci.cancel(); }
    }
}

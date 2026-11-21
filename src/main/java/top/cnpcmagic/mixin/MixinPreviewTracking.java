package top.cnpcmagic.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.server.MagicPreview;

@Mixin(ServerChunkCache.class)
public abstract class MixinPreviewTracking {
    @Inject(method = {"broadcast", "broadcastAndSend"}, at = @At("HEAD"))
    private void cnpcmagic$previewTracking(Entity entity, Packet<?> packet, CallbackInfo ci) {
        MagicPreview.nativePacket(entity.level().dimension(), entity.getX(), packet);
    }
}

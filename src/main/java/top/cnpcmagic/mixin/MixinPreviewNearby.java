package top.cnpcmagic.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.server.MagicPreview;

@Mixin(PlayerList.class)
public abstract class MixinPreviewNearby {
    @Inject(method = "broadcast", at = @At("HEAD"))
    private void cnpcmagic$previewNearby(Player excluded, double x, double y, double z, double radius,
                                       ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        MagicPreview.nativePacket(dimension, x, packet);
    }
}

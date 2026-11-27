package top.cnpcmagic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcmagic.server.MagicSync;

import java.util.function.Supplier;

public class C2SMagicRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<C2SMagicRequest> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cnpcmagic", "c2_s_magic_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SMagicRequest> CODEC = net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> C2SMagicRequest.encode(packet, buffer), C2SMagicRequest::decode);
    @Override public Type<C2SMagicRequest> type() { return TYPE; }

    public static void encode(C2SMagicRequest msg, FriendlyByteBuf buf) {
    }

    public static C2SMagicRequest decode(FriendlyByteBuf buf) {
        return new C2SMagicRequest();
    }

    public static void handle(C2SMagicRequest msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) ctx.player();
            if (player != null) {
                MagicSync.sendFull(player);
            }
        });
        
    }
}

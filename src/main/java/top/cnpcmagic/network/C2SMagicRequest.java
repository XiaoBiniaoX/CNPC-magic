package top.cnpcmagic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcmagic.server.MagicSync;

import java.util.function.Supplier;

public class C2SMagicRequest {
    public static void encode(C2SMagicRequest msg, FriendlyByteBuf buf) {
    }

    public static C2SMagicRequest decode(FriendlyByteBuf buf) {
        return new C2SMagicRequest();
    }

    public static void handle(C2SMagicRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MagicSync.sendFull(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

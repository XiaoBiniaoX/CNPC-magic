package top.cnpcmagic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcmagic.client.NpcAnimations;

import java.util.UUID;
import java.util.function.Supplier;

public record S2CNpcAnimation(UUID npc, String animation, int elapsed, int duration) {
    public static void encode(S2CNpcAnimation packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.npc);
        buf.writeUtf(packet.animation, 256);
        buf.writeVarInt(packet.elapsed);
        buf.writeVarInt(packet.duration);
    }

    public static S2CNpcAnimation decode(FriendlyByteBuf buf) {
        return new S2CNpcAnimation(buf.readUUID(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CNpcAnimation packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> NpcAnimations.receive(packet)));
        context.get().setPacketHandled(true);
    }
}

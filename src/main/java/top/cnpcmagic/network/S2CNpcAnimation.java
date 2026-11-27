package top.cnpcmagic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcmagic.client.NpcAnimations;

import java.util.UUID;
import java.util.function.Supplier;

public record S2CNpcAnimation(UUID npc, String animation, int elapsed, int duration) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<S2CNpcAnimation> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cnpcmagic", "s2_c_npc_animation"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CNpcAnimation> CODEC = net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> S2CNpcAnimation.encode(packet, buffer), S2CNpcAnimation::decode);
    @Override public Type<S2CNpcAnimation> type() { return TYPE; }

    public static void encode(S2CNpcAnimation packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.npc);
        buf.writeUtf(packet.animation, 256);
        buf.writeVarInt(packet.elapsed);
        buf.writeVarInt(packet.duration);
    }

    public static S2CNpcAnimation decode(FriendlyByteBuf buf) {
        return new S2CNpcAnimation(buf.readUUID(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CNpcAnimation packet, IPayloadContext context) {
        context.enqueueWork(() -> NpcAnimations.receive(packet));
        
    }
}

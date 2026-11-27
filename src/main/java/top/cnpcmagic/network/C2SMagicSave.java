package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcmagic.server.MagicSync;

import java.util.function.Supplier;

public class C2SMagicSave implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<C2SMagicSave> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cnpcmagic", "c2_s_magic_save"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SMagicSave> CODEC = net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> C2SMagicSave.encode(packet, buffer), C2SMagicSave::decode);
    @Override public Type<C2SMagicSave> type() { return TYPE; }

    private final CompoundTag data;

    public C2SMagicSave(CompoundTag data) {
        this.data = data;
    }

    public static void encode(C2SMagicSave msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static C2SMagicSave decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new C2SMagicSave(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(C2SMagicSave msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) ctx.player();
            if (player != null) {
                MagicSync.save(player, msg.data);
            }
        });
        
    }
}

package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcmagic.server.MagicSync;

import java.util.function.Supplier;

public class C2SMagicSave {
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

    public static void handle(C2SMagicSave msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MagicSync.save(player, msg.data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

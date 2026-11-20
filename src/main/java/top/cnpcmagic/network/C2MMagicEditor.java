package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcmagic.server.MagicPreview;
import top.cnpcmagic.server.MagicSync;
import java.util.function.Supplier;

public record C2MMagicEditor(CompoundTag data) {
    public static void encode(C2MMagicEditor p, FriendlyByteBuf b) { b.writeNbt(p.data); }
    public static C2MMagicEditor decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new C2MMagicEditor(t == null ? new CompoundTag() : t); }
    public static void handle(C2MMagicEditor p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var player = c.get().getSender(); if (player == null) return;
            switch (p.data.getString("action")) {
                case "preview" -> MagicPreview.start(player, p.data);
                case "return" -> MagicPreview.finish(player, true);
                case "definition" -> MagicSync.saveDefinition(player, p.data);
                case "deleteDefinition" -> MagicSync.deleteDefinition(player, p.data.getString("id"));
            }
        });
        c.get().setPacketHandled(true);
    }
}

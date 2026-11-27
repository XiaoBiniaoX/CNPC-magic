package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcmagic.server.MagicPreview;
import top.cnpcmagic.server.MagicSync;
import java.util.function.Supplier;

public record C2MMagicEditor(CompoundTag data) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<C2MMagicEditor> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cnpcmagic", "c2_m_magic_editor"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2MMagicEditor> CODEC = net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> C2MMagicEditor.encode(packet, buffer), C2MMagicEditor::decode);
    @Override public Type<C2MMagicEditor> type() { return TYPE; }

    public static void encode(C2MMagicEditor p, FriendlyByteBuf b) { b.writeNbt(p.data); }
    public static C2MMagicEditor decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new C2MMagicEditor(t == null ? new CompoundTag() : t); }
    public static void handle(C2MMagicEditor p, IPayloadContext c) {
        c.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) c.player(); if (player == null) return;
            switch (p.data.getString("action")) {
                case "preview" -> MagicPreview.start(player, p.data);
                case "return" -> MagicPreview.finish(player, true);
                case "definition" -> MagicSync.saveDefinition(player, p.data);
                case "deleteDefinition" -> MagicSync.deleteDefinition(player, p.data.getString("id"));
            }
        });
        
    }
}

package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.Supplier;

public record S2CMagicEditor(CompoundTag data) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<S2CMagicEditor> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cnpcmagic", "s2_c_magic_editor"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CMagicEditor> CODEC = net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> S2CMagicEditor.encode(packet, buffer), S2CMagicEditor::decode);
    @Override public Type<S2CMagicEditor> type() { return TYPE; }

    public static void encode(S2CMagicEditor p, FriendlyByteBuf b) { b.writeNbt(p.data); }
    public static S2CMagicEditor decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new S2CMagicEditor(t == null ? new CompoundTag() : t); }
    public static void handle(S2CMagicEditor p, IPayloadContext c) {
        c.enqueueWork(() -> top.cnpcmagic.client.gui.MagicEditor.receive(p.data));
        
    }
}

package top.cnpcmagic.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CMagicEditor(CompoundTag data) {
    public static void encode(S2CMagicEditor p, FriendlyByteBuf b) { b.writeNbt(p.data); }
    public static S2CMagicEditor decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new S2CMagicEditor(t == null ? new CompoundTag() : t); }
    public static void handle(S2CMagicEditor p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> top.cnpcmagic.client.gui.MagicEditor.receive(p.data)));
        c.get().setPacketHandled(true);
    }
}

package top.cnpcmagic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import top.cnpcmagic.server.MagicSync;

public final class C2SMagicSave implements IMessage {
    private NBTTagCompound data = new NBTTagCompound();

    public C2SMagicSave() {
    }

    public C2SMagicSave(NBTTagCompound data) {
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        data = tag == null ? new NBTTagCompound() : tag;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public static final class Handler implements IMessageHandler<C2SMagicSave, IMessage> {
        @Override
        public IMessage onMessage(C2SMagicSave message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> MagicSync.save(player, message.data));
            return null;
        }
    }
}

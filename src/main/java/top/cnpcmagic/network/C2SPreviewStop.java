package top.cnpcmagic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import top.cnpcmagic.server.MagicPreview;

public final class C2SPreviewStop implements IMessage {
    public C2SPreviewStop() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static final class Handler implements IMessageHandler<C2SPreviewStop, IMessage> {
        @Override
        public IMessage onMessage(C2SPreviewStop message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> MagicPreview.finish(player, true));
            return null;
        }
    }
}

package top.cnpcmagic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import top.cnpcmagic.client.ClientPreview;

public final class S2CPreviewStatus implements IMessage {
    private String npcId = "";
    private String status = "";

    public S2CPreviewStatus() {
    }

    public S2CPreviewStatus(String npcId, String status) {
        this.npcId = npcId == null ? "" : npcId;
        this.status = status == null ? "" : status;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        npcId = ByteBufUtils.readUTF8String(buf);
        status = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, npcId);
        ByteBufUtils.writeUTF8String(buf, status);
    }

    public static final class Handler implements IMessageHandler<S2CPreviewStatus, IMessage> {
        @Override
        public IMessage onMessage(S2CPreviewStatus message, MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(
                () -> ClientPreview.handle(message.npcId, message.status));
            return null;
        }
    }
}

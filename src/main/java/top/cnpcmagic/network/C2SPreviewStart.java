package top.cnpcmagic.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import top.cnpcmagic.server.MagicPreview;

public final class C2SPreviewStart implements IMessage {
    private String npcId = "";
    private String spellId = "";
    private int level;

    public C2SPreviewStart() {
    }

    public C2SPreviewStart(UUID npcId, String spellId, int level) {
        this.npcId = npcId == null ? "" : npcId.toString();
        this.spellId = spellId == null ? "" : spellId;
        this.level = level;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        npcId = ByteBufUtils.readUTF8String(buf);
        spellId = ByteBufUtils.readUTF8String(buf);
        level = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, npcId);
        ByteBufUtils.writeUTF8String(buf, spellId);
        buf.writeInt(level);
    }

    public static final class Handler implements IMessageHandler<C2SPreviewStart, IMessage> {
        @Override
        public IMessage onMessage(C2SPreviewStart message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            UUID id;
            try {
                id = UUID.fromString(message.npcId);
            } catch (IllegalArgumentException error) {
                return null;
            }
            player.getServerWorld().addScheduledTask(
                () -> MagicPreview.start(player, id, message.spellId, message.level));
            return null;
        }
    }
}

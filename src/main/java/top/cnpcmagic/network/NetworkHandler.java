package top.cnpcmagic.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcmagic.CNPCMagic;
import java.util.Optional;

public final class NetworkHandler {
    private static final String PROTOCOL = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CNPCMagic.MODID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private NetworkHandler() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SMagicRequest.class, C2SMagicRequest::encode, C2SMagicRequest::decode,
            C2SMagicRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SMagicSave.class, C2SMagicSave::encode, C2SMagicSave::decode,
            C2SMagicSave::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CNpcAnimation.class, S2CNpcAnimation::encode, S2CNpcAnimation::decode,
            S2CNpcAnimation::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CMagicEditor.class, S2CMagicEditor::encode, S2CMagicEditor::decode,
            S2CMagicEditor::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2MMagicEditor.class, C2MMagicEditor::encode, C2MMagicEditor::decode,
            C2MMagicEditor::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}

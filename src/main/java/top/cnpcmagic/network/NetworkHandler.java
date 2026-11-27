package top.cnpcmagic.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import top.cnpcmagic.CNPCMagic;
import java.util.Optional;

public final class NetworkHandler {
    public static final String PROTOCOL = "6";

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL);
        registrar.playToServer(C2SMagicRequest.TYPE, C2SMagicRequest.CODEC, C2SMagicRequest::handle);
        registrar.playToServer(C2SMagicSave.TYPE, C2SMagicSave.CODEC, C2SMagicSave::handle);
        registrar.playToServer(C2MMagicEditor.TYPE, C2MMagicEditor.CODEC, C2MMagicEditor::handle);
        registrar.playToClient(S2CNpcAnimation.TYPE, S2CNpcAnimation.CODEC, S2CNpcAnimation::handle);
        registrar.playToClient(S2CMagicEditor.TYPE, S2CMagicEditor.CODEC, S2CMagicEditor::handle);
    }
}

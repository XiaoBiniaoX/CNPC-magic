package top.cnpcmagic.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import top.cnpcmagic.CNPCMagic;

public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CNPCMagic.MOD_ID);
    private static boolean registered;

    private NetworkHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(C2SMagicRequest.Handler.class, C2SMagicRequest.class, 0, Side.SERVER);
        CHANNEL.registerMessage(C2SMagicSave.Handler.class, C2SMagicSave.class, 1, Side.SERVER);
    }
}

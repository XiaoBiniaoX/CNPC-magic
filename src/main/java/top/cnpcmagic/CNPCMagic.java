package top.cnpcmagic;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.Logger;
import top.cnpcmagic.data.MagicNpcData;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.MagicPreview;
import top.cnpcmagic.server.ScriptMagic;

@Mod(
    modid = CNPCMagic.MOD_ID,
    name = CNPCMagic.NAME,
    version = CNPCMagic.VERSION,
    dependencies = "required-after:mixinbooter@[10.7,);required-after:customnpcs@[1.12,);required-after:ebwizardry@[4.3.19];required-after:cnpcplus@[4.1.0,)"
)
public final class CNPCMagic {
    public static final String MOD_ID = "cnpcmagic";
    public static final String NAME = "CNPC Magic";
    public static final String VERSION = "1.3.0";

    public static Logger LOGGER;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        MagicNpcData.register();
        MagicPreview.registerDimension();
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(MagicCombatHandler.class);
        MinecraftForge.EVENT_BUS.register(MagicPreview.class);
        MinecraftForge.EVENT_BUS.register(ScriptMagic.class);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MagicPreview.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        MagicPreview.serverStopping(event);
    }
}

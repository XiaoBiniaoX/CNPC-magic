package top.cnpcmagic;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.ScriptMagic;

@Mod(
    modid = CNPCMagic.MOD_ID,
    name = CNPCMagic.NAME,
    version = CNPCMagic.VERSION,
    dependencies = "required-after:mixinbooter@[10.7,);required-after:customnpcs@[1.12,);required-after:ebwizardry@[4.3.19]"
)
public final class CNPCMagic {
    public static final String MOD_ID = "cnpcmagic";
    public static final String NAME = "CNPC Magic";
    public static final String VERSION = "1.0.0";

    public static Logger LOGGER;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(MagicCombatHandler.class);
        MinecraftForge.EVENT_BUS.register(ScriptMagic.class);
    }
}

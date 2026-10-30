package top.cnpcmagic;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import top.cnpcmagic.compat.iron.IronProvider;
import top.cnpcmagic.compat.goety.GoetyProvider;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.NpcAnimationSync;

@Mod(CNPCMagic.MODID)
public class CNPCMagic {
    public static final String MODID = "cnpcmagic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CNPCMagic() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        NetworkHandler.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("irons_spellbooks")) {
                try {
                    MagicProviders.register(new IronProvider());
                } catch (Throwable t) {
                    LOGGER.error("Failed to register Iron's Spellbooks provider", t);
                }
            }
            MinecraftForge.EVENT_BUS.register(MagicCombatHandler.class);
            if (ModList.get().isLoaded("goety")) {
                MagicProviders.register(new GoetyProvider());
            }
            MinecraftForge.EVENT_BUS.register(NpcAnimationSync.class);
            MinecraftForge.EVENT_BUS.register(top.cnpcmagic.server.ScriptMagic.class);
            if (Boolean.getBoolean("cnpcmagic.verify")) {
                MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicVerification.class);
                MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicScriptVerification.class);
            }
        });
    }
}

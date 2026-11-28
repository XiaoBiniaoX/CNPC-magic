package top.cnpcmagic.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.client.compat.iron.IronClientProvider;
import top.cnpcmagic.client.compat.goety.GoetyClientProvider;
import top.cnpcmagic.client.compat.ebwizardry.EbWizardryClientProvider;
import top.cnpcmagic.client.compat.ars.ArsClientProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;

@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CnpcmagicClient {
    private CnpcmagicClient() {
    }

    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        top.cnpcmagic.client.gui.MagicEditor.registerBoltRenderer(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("irons_spellbooks")) {
                try {
                    ClientMagicProviders.register(new IronClientProvider());
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(IronClientProvider::renderNpc);
                } catch (Throwable t) {
                    CNPCMagic.LOGGER.error("Failed to register Iron's Spellbooks client provider", t);
                }
            }
            if (ModList.get().isLoaded("goety")) {
                ClientMagicProviders.register(new GoetyClientProvider());
            }
            if (ModList.get().isLoaded("ebwizardry")) {
                try {
                    ClientMagicProviders.register(new EbWizardryClientProvider());
                } catch (Throwable t) {
                    CNPCMagic.LOGGER.error("Failed to register Wizardry client provider", t);
                }
            }
            if (ModList.get().isLoaded("ars_nouveau")) {
                try {
                    ClientMagicProviders.register(new ArsClientProvider());
                } catch (Throwable t) {
                    CNPCMagic.LOGGER.error("Failed to register Ars Nouveau client provider", t);
                }
            }
        });
    }
}

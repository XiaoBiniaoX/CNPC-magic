package top.cnpcmagic.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.client.compat.iron.IronClientProvider;
import top.cnpcmagic.client.compat.goety.GoetyClientProvider;
import top.cnpcmagic.client.compat.ars.ArsClientProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;

@net.neoforged.fml.common.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class CnpcmagicClient {
    private CnpcmagicClient() {
    }

    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        top.cnpcmagic.client.gui.MagicEditor.registerBoltRenderer(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("irons_spellbooks")) {
                try {
                    ClientMagicProviders.register(new IronClientProvider());
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(IronClientProvider::renderNpc);
                } catch (Throwable t) {
                    CNPCMagic.LOGGER.error("Failed to register Iron's Spellbooks client provider", t);
                }
            }
            if (ModList.get().isLoaded("goety")) {
                ClientMagicProviders.register(new GoetyClientProvider());
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GoetyClientProvider::renderNpc);
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

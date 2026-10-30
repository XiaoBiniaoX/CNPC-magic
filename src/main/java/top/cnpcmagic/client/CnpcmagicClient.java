package top.cnpcmagic.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.client.compat.iron.IronClientProvider;
import top.cnpcmagic.client.compat.goety.GoetyClientProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;

@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CnpcmagicClient {
    private CnpcmagicClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("irons_spellbooks")) {
                try {
                    ClientMagicProviders.register(new IronClientProvider());
                } catch (Throwable t) {
                    CNPCMagic.LOGGER.error("Failed to register Iron's Spellbooks client provider", t);
                }
            }
            if (ModList.get().isLoaded("goety")) {
                ClientMagicProviders.register(new GoetyClientProvider());
            }
        });
    }
}

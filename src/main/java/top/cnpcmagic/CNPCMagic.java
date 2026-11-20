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
import top.cnpcmagic.compat.ebwizardry.EbWizardryProvider;
import top.cnpcmagic.compat.ars.ArsProvider;
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
        top.cnpcmagic.casting.BuiltinSpell.ENTITIES.register(modBus);
        NetworkHandler.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MagicProviders.register(new top.cnpcmagic.casting.BuiltinSpell());
            MinecraftForge.EVENT_BUS.register(top.cnpcmagic.casting.BuiltinSpell.class);
            MinecraftForge.EVENT_BUS.register(top.cnpcmagic.casting.CastingService.class);
            MinecraftForge.EVENT_BUS.register(top.cnpcmagic.server.MagicPreview.class);
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
            if (ModList.get().isLoaded("ebwizardry")) {
                try {
                    MagicProviders.register(new EbWizardryProvider());
                } catch (Throwable t) {
                    LOGGER.error("Failed to register Wizardry provider", t);
                }
            }
            if (ModList.get().isLoaded("ars_nouveau")) {
                try {
                    MagicProviders.register(new ArsProvider());
                } catch (Throwable t) {
                    LOGGER.error("Failed to register Ars Nouveau provider", t);
                }
            }
            MinecraftForge.EVENT_BUS.register(NpcAnimationSync.class);
            MinecraftForge.EVENT_BUS.register(top.cnpcmagic.server.ScriptMagic.class);
            if (Boolean.getBoolean("cnpcmagic.verify.rewrite")) MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.RewriteVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify.editor")) MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.EditorVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify.wizardry")) MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.WizardryVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify.wizardryCatalog")) MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.WizardryCatalogVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify")) {
                MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicVerification.class);
                MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicScriptVerification.class);
                MinecraftForge.EVENT_BUS.register(top.cnpcmagic.verify.IndependentVerification.class);
            }
        });
    }
}

package top.cnpcmagic;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import top.cnpcmagic.compat.iron.IronProvider;
import top.cnpcmagic.compat.goety.GoetyProvider;
import top.cnpcmagic.compat.ars.ArsProvider;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.NpcAnimationSync;

@Mod(CNPCMagic.MODID)
public class CNPCMagic {
    public static final String MODID = "cnpcmagic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CNPCMagic(IEventBus modBus) {
        modBus.addListener(this::commonSetup);
        top.cnpcmagic.casting.BuiltinSpell.ENTITIES.register(modBus);
        modBus.addListener(NetworkHandler::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MagicProviders.register(new top.cnpcmagic.casting.BuiltinSpell());
            NeoForge.EVENT_BUS.register(top.cnpcmagic.casting.BuiltinSpell.class);
            NeoForge.EVENT_BUS.register(top.cnpcmagic.casting.CastingService.class);
            NeoForge.EVENT_BUS.register(top.cnpcmagic.server.MagicPreview.class);
            if (ModList.get().isLoaded("irons_spellbooks")) {
                try {
                    MagicProviders.register(new IronProvider());
                } catch (Throwable t) {
                    LOGGER.error("Failed to register Iron's Spellbooks provider", t);
                }
            }
            NeoForge.EVENT_BUS.register(MagicCombatHandler.class);
            NeoForge.EVENT_BUS.register(top.cnpcmagic.server.CombatDecisions.class);
            if (ModList.get().isLoaded("goety")) {
                MagicProviders.register(new GoetyProvider());
            }
            
            if (ModList.get().isLoaded("ars_nouveau")) {
                try {
                    MagicProviders.register(new ArsProvider());
                } catch (Throwable t) {
                    LOGGER.error("Failed to register Ars Nouveau provider", t);
                }
            }
            NeoForge.EVENT_BUS.register(NpcAnimationSync.class);
            NeoForge.EVENT_BUS.register(top.cnpcmagic.server.ScriptMagic.class);
            if (Boolean.getBoolean("cnpcmagic.verify.rewrite")) NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.RewriteVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify.editor")) NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.EditorVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify.ironCatalog")) NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.IronCatalogVerification.class);
            if (Boolean.getBoolean("cnpcmagic.verify")) {
                NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicVerification.class);
                NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.MagicScriptVerification.class);
                NeoForge.EVENT_BUS.register(top.cnpcmagic.verify.IndependentVerification.class);
            }
        });
    }
}

package top.cnpcmagic.client;

import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.client.gui.MagicEditor;

/** Keeps the editor visible only while its server-owned preview transfer is in progress. */
@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class PreviewTransitionClient {
    private PreviewTransitionClient() {
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (MagicEditor.isPreviewTransitionActive()
            && (event.getNewScreen() instanceof ReceivingLevelScreen || event.getNewScreen() instanceof ProgressScreen)) {
            event.setCanceled(true);
        }
    }
}

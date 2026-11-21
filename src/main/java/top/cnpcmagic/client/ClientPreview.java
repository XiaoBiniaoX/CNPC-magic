package top.cnpcmagic.client;

import net.minecraft.client.Minecraft;
import top.cnpcmagic.client.gui.GuiMagicPreview;

public final class ClientPreview {
    private static String npcId = "";
    private static GuiMagicPreview screen;

    private ClientPreview() {
    }

    public static void handle(String id, String status) {
        npcId = id == null ? "" : id;
        Minecraft minecraft = Minecraft.getMinecraft();
        if ("READY".equals(status)) {
            screen = new GuiMagicPreview();
            minecraft.displayGuiScreen(screen);
        } else if (screen != null) {
            screen.setStatus(status);
            if ("RETURNED".equals(status)) {
                screen = null;
                minecraft.displayGuiScreen(null);
            }
        }
    }

    public static String getNpcId() {
        return npcId;
    }
}

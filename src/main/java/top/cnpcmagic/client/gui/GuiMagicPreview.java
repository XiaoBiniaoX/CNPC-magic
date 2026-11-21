package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.translation.I18n;
import top.cnpcmagic.network.C2SPreviewStop;
import top.cnpcmagic.network.NetworkHandler;

/** Transparent in-world overlay so the isolated actors and native effects remain visible. */
public final class GuiMagicPreview extends GuiScreen {
    private String status = "cnpcmagic.preview.running";

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, 8, 8, 84, 20, I18n.translateToLocal("cnpcmagic.gui.return")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(6, 6, 238, 34, 0xB0202824);
        drawString(fontRenderer, I18n.translateToLocal(status), 12, 13, 0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) NetworkHandler.CHANNEL.sendToServer(new C2SPreviewStop());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) {
            NetworkHandler.CHANNEL.sendToServer(new C2SPreviewStop());
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public void setStatus(String value) {
        if ("COMPLETE".equals(value)) status = "cnpcmagic.preview.complete";
        else if ("FAILED".equals(value)) status = "cnpcmagic.preview.failed";
        else if ("READY".equals(value)) status = "cnpcmagic.preview.running";
    }
}

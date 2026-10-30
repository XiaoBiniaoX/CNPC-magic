package top.cnpcmagic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

public final class MagicTab {
    public static final int TAB_ID = 7;

    private MagicTab() {
    }

    public static GuiMenuTopButton[] insert(GuiMenuTopButton[] buttons, IGuiInterface parent,
                                            EntityNPCInterface npc, int guiLeft, int guiTop, int activeMenu) {
        GuiMenuTopButton anchor = null;
        for (GuiMenuTopButton button : buttons) {
            if (button.id == 6) {
                anchor = button;
                break;
            }
        }
 int x = anchor == null ? guiLeft + 4 : anchor.getX() + anchor.getWidth();
        GuiMenuTopButton magic = new GuiMenuTopButton(parent, TAB_ID, x, guiTop - 17, "menu.magic") {
            @Override
            public void onClick(double mouseX, double mouseY) {
                GuiTextFieldNop.unfocus();
                parent.save();
                Screen screen = new GuiNpcMagic(npc);
                Minecraft.getInstance().setScreen(screen);
            }
        };
        magic.active = activeMenu == TAB_ID;

        GuiMenuTopButton[] result = new GuiMenuTopButton[buttons.length + 1];
        int index = 0;
        boolean inserted = false;
        for (GuiMenuTopButton button : buttons) {
            if (!inserted && button.id == 0) {
                result[index++] = magic;
                inserted = true;
            }
            result[index++] = button;
        }
        if (!inserted) {
            result[index] = magic;
        }
        return result;
    }
}
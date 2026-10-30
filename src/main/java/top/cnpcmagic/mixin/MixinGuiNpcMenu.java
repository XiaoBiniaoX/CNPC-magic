package top.cnpcmagic.mixin;

import java.util.Arrays;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiMenuTopButton;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.client.gui.GuiNpcMagic;

@Mixin(value = GuiNpcMenu.class, remap = false)
public abstract class MixinGuiNpcMenu {
    @Shadow private GuiScreen parent;
    @Shadow private GuiMenuTopButton[] topButtons;
    @Shadow private int activeMenu;
    @Shadow private EntityNPCInterface npc;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cnpcmagic$addMagicTab(int guiLeft, int guiTop, int width, CallbackInfo ci) {
        GuiMenuTopButton anchor = null;
        for (GuiMenuTopButton button : topButtons) {
            if (button.field_146127_k == 5) {
                anchor = button;
                break;
            }
        }
        int x = anchor == null ? guiLeft + 4 : anchor.x + anchor.getWidth();
        GuiMenuTopButton magic = new GuiMenuTopButton(7, x, guiTop - 17, "menu.magic");
        magic.active = activeMenu == 7;
        magic.listener = ignored -> {
            GuiNpcTextField.unfocus();
            if (parent instanceof GuiContainerNPCInterface2) {
                ((GuiContainerNPCInterface2) parent).save();
            } else if (parent instanceof GuiNPCInterface2) {
                ((GuiNPCInterface2) parent).save();
            }
            NoppesUtil.openGUI(parent.mc.player, new GuiNpcMagic(npc));
        };

        GuiMenuTopButton[] result = Arrays.copyOf(topButtons, topButtons.length + 1);
        int insert = topButtons.length;
        for (int i = 0; i < topButtons.length; i++) {
            if (topButtons[i].field_146127_k == 0) {
                insert = i;
                break;
            }
        }
        System.arraycopy(result, insert, result, insert + 1, topButtons.length - insert);
        result[insert] = magic;
        topButtons = result;
    }
}

package top.cnpcmagic.mixin;

import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiMenuTopButton;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.client.gui.MagicTab;

@Mixin(value = GuiNpcMenu.class, remap = false)
public class MixinGuiNpcMenu {
    @Shadow
    private IGuiInterface parent;
    @Shadow
    private GuiMenuTopButton[] topButtons;
    @Shadow
    private int activeMenu;
    @Shadow
    private EntityNPCInterface npc;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cnpcmagic$addMagicTab(int guiLeft, int guiTop, int width, CallbackInfo ci) {
        this.topButtons = MagicTab.insert(this.topButtons, this.parent, this.npc, guiLeft, guiTop, this.activeMenu);
    }
}
package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class GuiSelectSpell extends GuiBasic implements ICustomScrollListener {
    private final Map<String, String> byName;
    private final String current;
    private final Consumer<String> onPick;
    private GuiCustomScrollNop scroll;

    public GuiSelectSpell(Screen parent, Map<String, String> byName, String current, Consumer<String> onPick) {
        this.byName = byName;
        this.current = current;
        this.onPick = onPick;
        this.imageWidth = 220;
        this.imageHeight = 200;
        setBackground("menubg.png");
    }

    @Override
    public void init() {
        super.init();
        title = Component.translatable("cnpcmagic.gui.selectSpell").getString();
        addLabel(new GuiLabel(0, "cnpcmagic.gui.selectSpell", guiLeft + 10, guiTop + 8));
        scroll = new GuiCustomScrollNop(this, 0);
        scroll.setSize(200, 148);
        scroll.guiLeft = guiLeft + 10;
        scroll.guiTop = guiTop + 24;
        addScroll(scroll);
        scroll.setList(new ArrayList<>(byName.keySet()));
        scroll.setSelected(current);
        addButton(new GuiButtonNop(this, 1, guiLeft + 10, guiTop + 176, 200, 18, "cnpcmagic.gui.select"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 1) {
            pick(scroll == null ? null : scroll.getSelected());
        }
    }

    @Override
    public void scrollClicked(double mouseX, double mouseY, int mouseButton, GuiCustomScrollNop scroll) {
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
        pick(selection);
    }

    private void pick(String name) {
        if (name == null) {
            return;
        }
        String id = byName.get(name);
        if (id == null) {
            return;
        }
        onPick.accept(id);
        close();
    }
}

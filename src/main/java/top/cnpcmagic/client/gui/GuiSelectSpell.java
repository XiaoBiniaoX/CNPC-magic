package top.cnpcmagic.client.gui;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.SubGuiInterface;

final class GuiSelectSpell extends SubGuiInterface implements ICustomScrollListener {
    private final Map<String, String> byName;
    private final String current;
    private final Consumer<String> onPick;
    private GuiCustomScroll scroll;

    GuiSelectSpell(Map<String, String> byName, String current, Consumer<String> onPick) {
        this.byName = byName;
        this.current = current;
        this.onPick = onPick;
        this.xSize = 220;
        this.ySize = 200;
        setBackground("menubg.png");
    }

    @Override
    public void func_73866_w_() {
        super.func_73866_w_();
        this.title = "";
        addLabel(new GuiNpcLabel(0, "cnpcmagic.gui.selectSpell", guiLeft + 10, guiTop + 8));
        scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(200, 148);
        scroll.guiLeft = guiLeft + 10;
        scroll.guiTop = guiTop + 24;
        addScroll(scroll);
        scroll.setList(new ArrayList<>(byName.keySet()));
        scroll.setSelected(current);
        addButton(new GuiNpcButton(1, guiLeft + 10, guiTop + 176, 200, 18, "cnpcmagic.gui.select"));
    }

    @Override
    protected void func_146284_a(GuiButton guiButton) {
        if (guiButton.id == 1) pick(scroll == null ? null : scroll.getSelected());
    }

    @Override
    public void scrollClicked(int mouseX, int mouseY, int mouseButton, GuiCustomScroll scroll) {
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
        pick(selection);
    }

    private void pick(String name) {
        if (name == null) return;
        String id = byName.get(name);
        if (id == null) return;
        onPick.accept(id);
        close();
    }

    @Override
    public void save() {
    }
}

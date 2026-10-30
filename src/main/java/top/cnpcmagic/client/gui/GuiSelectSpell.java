package top.cnpcmagic.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.lwjgl.input.Keyboard;

final class GuiSelectSpell extends SubGuiInterface implements ICustomScrollListener {
    private final Map<String, String> byName;
    private final Consumer<String> onPick;
    private GuiCustomScroll scroll;
    private String selection;
    private String search = "";

    GuiSelectSpell(Map<String, String> byName, String current, Consumer<String> onPick) {
        this.byName = byName;
        this.selection = current;
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
        addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.search", guiLeft + 10, guiTop + 22));
        addTextField(new GuiNpcTextField(0, this, guiLeft + 10, guiTop + 34, 140, 18, search));
        getTextField(0).setFocused(true);
        addButton(new GuiNpcButton(2, guiLeft + 154, guiTop + 34, 56, 18, "cnpcmagic.gui.clear"));
        scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(200, 116);
        scroll.guiLeft = guiLeft + 10;
        scroll.guiTop = guiTop + 56;
        addScroll(scroll);
        addButton(new GuiNpcButton(1, guiLeft + 10, guiTop + 176, 200, 18, "cnpcmagic.gui.select"));
        updateResults();
        scroll.scrollTo(selection);
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        if (scroll.getList().isEmpty()) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcmagic.gui.noResults"),
                guiLeft + 14, guiTop + 64, 0xCCCCCC);
        }
    }

    @Override
    public void buttonEvent(GuiButton guiButton) {
        if (guiButton.id == 1) pick(scroll == null ? null : scroll.getSelected());
        if (guiButton.id == 2) {
            search = "";
            getTextField(0).setText(search);
            getTextField(0).setFocused(true);
            updateResults();
        }
    }

    @Override
    public void func_73869_a(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        // Let the native field handle cursor movement, selection, deletion and clipboard shortcuts.
        super.func_73869_a(typedChar, keyCode);
        String text = getTextField(0).getText();
        if (!search.equals(text)) {
            search = text;
            updateResults();
        }
    }

    private void updateResults() {
        String query = search.trim().toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String name : byName.keySet()) {
            // Each display name also contains the full registry ID in brackets.
            if (name.toLowerCase(Locale.ROOT).contains(query)) matches.add(name);
        }
        scroll.setList(matches);
        scroll.setSelected(selection);
        getButton(1).enabled = scroll.getSelected() != null;
        getButton(2).enabled = !search.isEmpty();
    }

    @Override
    public void scrollClicked(int mouseX, int mouseY, int mouseButton, GuiCustomScroll scroll) {
        selection = scroll.getSelected();
        getButton(1).enabled = selection != null;
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

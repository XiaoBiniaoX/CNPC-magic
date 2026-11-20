package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.*;
import java.util.function.*;

public abstract class EditorScreen extends Screen {
    static final int IRON = 0xFFC6C7C1, PAPER = 0xFFEEECE2, EDGE = 0xFF656963, INK = 0xFF505653,
        GREEN = 0xFF75836B, MUTED = 0xFF858A83, RED = 0xFF9C6961, WHITE = 0xFFF5F3EA;
    final Screen parent;
    String status = "";
    final List<Label> labels = new ArrayList<>();
    EditorScreen(Screen parent, String title) { super(tr(title)); this.parent = parent; }
    static Component tr(String key) { return Component.translatable(key.startsWith("cnpcmagic.") || key.startsWith("menu.") ? key : "cnpcmagic.editor." + key); }
    static Component format(String key, Object... args) { return Component.translatable("cnpcmagic.editor." + key, args); }
    static String text(String key) { return tr(key).getString(); }
    Minecraft client() { return minecraft; }
    Font editorFont() { return font; }
    Rows addRows(Rows rows) { return addRenderableWidget(rows); }
    void rebuild() { setFocused(null); setDragging(false); clearWidgets(); labels.clear(); init(); }
    @Override protected void init() {
        labels.clear();
        button(8, 7, 84, "back", this::onClose);
    }
    Button button(int x, int y, int w, String key, Runnable run) { return buttonText(x, y, w, tr(key), run); }
    Button buttonText(int x, int y, int w, Component title, Runnable run) {
        return addRenderableWidget(new EditorButton(x, y, Math.max(12, w), title, b -> run.run()));
    }
    void label(int x, int y, String key) { labels.add(new Label(x, y, tr(key))); }
    EditBox field(int x, int y, int w, String key, String value, Consumer<String> change) {
        label(x, y + 5, key);
        int caption = Math.min(120, Math.max(56, w / 2));
        return input(x + caption, y, Math.max(36, w - caption), key, value, change);
    }
    EditBox input(int x, int y, int w, String key, String value, Consumer<String> change) {
        EditBox box = new EditBox(font, x, y, w, 18, tr(key));
        box.setMaxLength(4096); box.setValue(value); box.setResponder(change); addRenderableWidget(box); return box;
    }
    void segments(int x, int y, int w, String label, String[] keys, int selected, IntConsumer change) {
        int caption = Math.min(104, Math.max(32, w / 4));
        label(x, y + 5, label); segments(x + caption, y, w - caption, keys, selected, change);
    }
    void segments(int start, int y, int available, String[] keys, int selected, IntConsumer change) {
        for (int i = 0; i < keys.length; i++) {
            int index = i, left = start + available * i / keys.length, right = start + available * (i + 1) / keys.length;
            EditorButton button = new EditorButton(left, y, right - left, tr(keys[i]), b -> { change.accept(index); rebuild(); });
            button.selected = i == selected; addRenderableWidget(button);
        }
    }
    void segmentsGrid(int x, int y, int w, int columns, String[] keys, int selected, IntConsumer change) {
        for (int i = 0; i < keys.length; i++) {
            int index = i, row = i / columns, column = i % columns;
            int left = x + w * column / columns, right = x + w * (column + 1) / columns;
            EditorButton button = new EditorButton(left, y + row * 20, right - left, tr(keys[i]), b -> { change.accept(index); rebuild(); });
            button.setTooltip(Tooltip.create(button.getMessage()));
            button.selected = i == selected; addRenderableWidget(button);
        }
    }
    @Override public void tick() { for (var child : children()) if (child instanceof EditBox box) box.tick(); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, IRON); panel(g, 6, 34, width - 12, height - 62, PAPER);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 220)), 102, 13, INK, false);
        drawControls(g, mx, my, partial);
        drawStatus(g, status, 9, height - 18, width - 18);
    }
    void drawControls(GuiGraphics g, int mx, int my, float partial) {
        for (Label label : labels) g.drawString(font, font.plainSubstrByWidth(label.text.getString(), Math.max(1, width - label.x - 14)), label.x, label.y, INK, false);
        super.render(g, mx, my, partial);
    }
    void drawStatus(GuiGraphics g, String message, int x, int y, int w) {
        if (!message.isEmpty()) g.drawString(font, font.plainSubstrByWidth(message, w), x, y, RED, false);
    }
    static void panel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, EDGE); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.hLine(x + 1, x + w - 2, y + 1, WHITE); g.vLine(x + 1, y + 1, y + h - 2, WHITE);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    record Label(int x, int y, Component text) {}
    static final class EditorButton extends Button {
        boolean selected;
        EditorButton(int x, int y, int w, Component text, OnPress press) { super(x, y, w, 20, text, press, DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            panel(g, getX(), getY(), width, height, selected ? GREEN : isHoveredOrFocused() && active ? 0xFFDCE1D1 : IRON);
            Font font = Minecraft.getInstance().font;
            String name = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 6));
            g.drawString(font, name, getX() + (width - font.width(name)) / 2, getY() + 6, !active ? MUTED : selected ? WHITE : INK, false);
        }
    }
    final class Rows extends AbstractWidget {
        final List<String> names;
        final IntConsumer pick;
        int selected, offset;
        Rows(int x, int y, int w, int h, List<String> names, int selected, IntConsumer pick) {
            super(x, y, w, Math.max(22, h), tr("list")); this.names = names; this.selected = selected; this.pick = pick;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            panel(g, getX(), getY(), width, height, PAPER);
            int count = capacity(); offset = Mth.clamp(offset, 0, Math.max(0, names.size() - count));
            g.enableScissor(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2);
            for (int i = offset; i < Math.min(names.size(), offset + count); i++) {
                int y = getY() + 2 + (i - offset) * 22;
                if (i == selected) g.fill(getX() + 2, y, getX() + width - 2, y + 21, GREEN);
                g.drawString(font, font.plainSubstrByWidth(names.get(i), Math.max(1, width - 12)), getX() + 6, y + 6, i == selected ? WHITE : INK, false);
            }
            g.disableScissor();
            if (names.size() > count) {
                int thumb = Math.max(8, (height - 4) * count / names.size());
                int y = getY() + 2 + (height - 4 - thumb) * offset / Math.max(1, names.size() - count);
                g.fill(getX() + width - 3, y, getX() + width - 1, y + thumb, MUTED);
            }
        }
        @Override public void onClick(double x, double y) {
            int row = (int)y - getY() - 2;
            int index = offset + row / 22;
            if (row >= 0 && row / 22 < capacity() && index < names.size()) { selected = index; pick.accept(index); }
        }
        int capacity() { return Math.max(1, (height - 4) / 22); }
        @Override public boolean mouseScrolled(double x, double y, double delta) { if (!isMouseOver(x, y)) return false; offset -= (int)(delta * 3); return true; }
         @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (names.isEmpty()) return super.keyPressed(key, scan, modifiers);
            if (key == 264 || key == 265) { selected = Mth.clamp(selected + (key == 264 ? 1 : -1), 0, names.size() - 1); offset = Math.max(0, selected - (height - 4) / 22 + 1); if (!names.isEmpty()) pick.accept(selected); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}

package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.components.Tooltip;
import top.cnpcmagic.data.MagicRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One ordered cast chain spanning every installed magic provider. */
final class SequenceScreen extends EditorScreen {
    private final GuiNpcMagic editor;
    private final List<Entry> entries = new ArrayList<>();
    private int selected;
    private int offset;
    private Rows rows;

    SequenceScreen(GuiNpcMagic editor) {
        super(editor, "sequence");
        this.editor = editor;
        for (String provider : editor.providerIds()) {
            List<MagicRule> rules = editor.rules.getOrDefault(provider, List.of());
            for (int i = 0; i < rules.size(); i++) {
                MagicRule rule = rules.get(i);
                if (!rule.independent) entries.add(new Entry(provider, i + 1, rule));
            }
        }
        entries.sort(Comparator.comparingInt(entry -> entry.rule.sequenceOrder()));
    }

    @Override protected void init() {
        super.init();
        int x = Math.max(14, (width - 460) / 2), w = Math.min(460, width - 28), bottom = height - 54;
        if (entries.isEmpty()) label(x, 46, "noRules");
        List<String> names = new ArrayList<>();
        for (Entry entry : entries) names.add((names.size() + 1) + ". " + text("cnpcmagic.provider." + entry.provider)
            + " #" + entry.index + " · " + editor.name(entry.rule.spellId)
            + (entry.rule.enabled ? "" : " · " + text("cnpcmagic.gui.off")));
        int bw = (w - 6) / 3;
        var up = button(x, bottom, bw, "moveUp", () -> move(-1));
        var down = button(x + bw + 3, bottom, bw, "moveDown", () -> move(1));
        var apply = button(x + (bw + 3) * 2, bottom, w - 2 * (bw + 3), "applySequence", this::apply);
        apply.active = !entries.isEmpty();
        apply.setTooltip(Tooltip.create(tr("sequenceHint")));
        up.active = selected > 0;
        down.active = selected + 1 < entries.size();
        rows = addRenderableWidget(new Rows(x, 58, w, bottom - 62, names, selected, i -> {
            selected = i; up.active = i > 0; down.active = i + 1 < entries.size();
        }));
        rows.offset = Math.max(selected - rows.capacity() + 1, Math.min(offset, selected));
    }

    private void move(int direction) {
        int next = selected + direction;
        if (next < 0 || next >= entries.size()) return;
        java.util.Collections.swap(entries, selected, next);
        selected = next;
        offset = rows.offset;
        rebuild();
    }

    private void apply() {
        for (int i = 0; i < entries.size(); i++) entries.get(i).rule.sequence = i;
        editor.detail = null;
        editor.status = EditorScreen.text("sequenceApplied");
        minecraft.setScreen(editor);
    }

    private record Entry(String provider, int index, MagicRule rule) {}
}

package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import top.cnpcmagic.casting.SpellDefinition;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;
import top.cnpcmagic.data.MagicRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.cnpcmagic.client.gui.EditorScreen.*;

/** Modal controls mounted directly on the editor; only the view opening shows the preview world. */
final class SpellPicker {
    private static final List<String> TARGETS = List.of("entity", "self", "position", "none");
    final GuiNpcMagic editor;
    final RuleEditor edit;
    final ClientMagicProvider provider;
    final List<String> ids;
    String selected, level, target = "entity", status = "";
    boolean active, opening, closing, confirm;
    int request;
    private String search = "";
    private List<String> filtered = List.of();
    private List<FormattedCharSequence> info = List.of();
    private EditorScreen.Rows rows;
    private Button add, replay, back;
    private int x, y, w, h, vx, vy, vw, vh, ix, iw, scroll;

    SpellPicker(GuiNpcMagic editor, RuleEditor edit) {
        this.editor = editor; this.edit = edit;
        provider = ClientMagicProviders.get(editor.provider);
        ids = provider == null ? editor.library.values().stream().filter(d -> d.enabled).map(d -> d.id).toList() : provider.getSpellIds();
        selected = edit == null ? "" : edit.rule.spellId;
        level = edit == null ? "1" : Integer.toString(edit.rule.level);
    }

    void controls() {
        w = Math.min(750, editor.width - 22); h = Math.min(404, editor.height - 20);
        x = (editor.width - w) / 2; y = (editor.height - h) / 2;
        int left = x + 10, top = y + 34, side = Math.min(172, w * 27 / 100), foot = y + h - 33;
        vx = left + side + 8; vy = top; vw = w - 36 - side * 2; vh = foot - top - 30;
        ix = vx + vw + 8; iw = side;
        back = editor.button(left, y + 7, 62, "back", () -> close(false));
        add = editor.button(x + w - 93, y + 7, 83, edit == null ? "addSpell" : "cnpcmagic.gui.select", () -> close(true));
        var searchBox = editor.input(left, top, side, "search", search, s -> { search = s; rows.offset = 0; filter(); });
        searchBox.setHint(tr("search"));
        int oldOffset = rows == null ? 0 : rows.offset;
        rows = editor.addRows(editor.new Rows(left, top + 23, side, foot - top - 23, new ArrayList<>(), -1, i -> {
            selected = filtered.get(i); scroll = 0; update(); preview();
        }));
        rows.offset = oldOffset;
        filter();
        int levelWidth = Math.min(100, vw / 2);
        editor.label(vx, foot - 20, "cnpcmagic.gui.level");
        int caption = editor.editorFont().width(text("cnpcmagic.gui.level")) + 5;
        editor.input(vx + caption, foot - 25, Math.max(26, levelWidth - caption), "cnpcmagic.gui.level", level, value -> { level = value; update(); });
        replay = editor.button(vx + levelWidth + 4, foot - 25, vw - levelWidth - 4, "replay", this::preview);
        String[] targets = provider == null ? TARGETS.toArray(String[]::new) : new String[]{"entity", "self"};
        editor.segments(vx, foot, vw, targets, TARGETS.indexOf(target), i -> { target = targets[i]; if (!selected.isEmpty() && !opening) preview(); });
        update();
    }

    private void filter() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        filtered = ids.stream().filter(id -> (id + " " + editor.name(id)).toLowerCase(Locale.ROOT).contains(query)).toList();
        rows.names.clear(); filtered.forEach(id -> rows.names.add(editor.name(id))); rows.selected = filtered.indexOf(selected);
    }

    private int parsedLevel() {
        try { return Integer.parseInt(level); } catch (NumberFormatException e) { return 0; }
    }

    private void update() {
        int value = parsedLevel(), max = editor.maxLevel(selected);
        boolean valid = !selected.isEmpty() && value >= 1 && value <= max;
        add.active = valid && !closing;
        replay.active = valid && !opening && !closing;
        back.active = !closing;
        rows.active = !opening && !closing;
        var details = new ArrayList<Component>();
        if (valid) {
            SpellDefinition d = editor.library.get(selected);
            if (d != null) {
                details.add(format("propertyDelivery", tr("delivery." + d.delivery.name())));
                details.add(format("propertyTimes", d.castTicks / 20.0, d.cooldownTicks / 20.0));
                details.add(format("propertyRange", d.range, d.radius));
                if (d.healthCost > 0) details.add(format("propertyCost", d.resource, d.healthCost));
                for (var effect : d.effects) if (effect.enabled) {
                    double amount = switch (effect.type) {
                        case DAMAGE, HEALING, MOTION -> effect.amount * d.power * value;
                        default -> effect.amount;
                    };
                    details.add(format("propertyEffect", tr("effect." + effect.type.name()), amount, effect.id));
                }
                if (!d.description.isBlank()) details.add(Component.literal(d.description));
            } else if (provider != null) details.addAll(provider.describe(selected, value, editor.npc));
            if (target.equals("entity") && provider != null && provider.requiresMeleeTarget(selected)) details.add(tr("meleeFollowup"));
            details.add(Component.literal(selected));
        } else if (!selected.isEmpty()) details.add(format("levelRange", max));
        info = details.stream().flatMap(line -> editor.editorFont().split(line, Math.max(1, iw - 12)).stream()).toList();
        scroll = Mth.clamp(scroll, 0, Math.max(0, info.size() - infoRows()));
    }

    private int infoRows() { return Math.max(1, (h - 85) / 11); }

    void preview() {
        if (!replay.active) return;
        opening = !active; status = text(opening ? "openingPreview" : "castingPreview");
        CompoundTag packet = new CompoundTag(); packet.putString("action", "preview"); packet.putUUID("npc", editor.npc.getUUID());
        packet.putInt("request", ++request); packet.putString("spell", selected); packet.putInt("level", parsedLevel()); packet.putString("target", target);
        MagicEditor.send(packet); update();
    }

    void begin() {
        active = true; opening = false;
        if (!closing) status = text("castingPreview");
        if (editor.client().screen != editor) editor.client().setScreen(editor); else update();
    }

    void close(boolean confirm) {
        if (closing) return;
        this.confirm = confirm;
        if (opening || active) {
            closing = true; status = text("returning"); MagicEditor.send("return"); update();
        } else { closing = true; end(); }
    }

    void end() {
        active = false; opening = false;
        if (closing) {
            editor.picker = null;
            if (confirm) {
                if (edit == null) {
                    MagicRule r = new MagicRule(); r.spellId = selected; r.level = parsedLevel(); r.enabled = true; r.range = 16;
                    editor.insert(r);
                    if (editor.client().screen != editor) editor.client().setScreen(editor);
                    return;
                } else {
                    edit.rule.spellId = selected; edit.rule.level = parsedLevel();
                    editor.detail = new RuleEditor(editor, edit.rule);
                }
            }
        } else status = text("previewEnded");
        if (editor.client().screen != editor) editor.client().setScreen(editor); else editor.rebuild();
    }

    void error(String message) { opening = false; status = "! " + message; update(); }

    boolean scroll(double mx, double my, double delta) {
        if (mx < ix || mx >= ix + iw || my < vy || my >= y + h - 10) return false;
        scroll = Mth.clamp(scroll + (delta < 0 ? 3 : -3), 0, Math.max(0, info.size() - infoRows()));
        return true;
    }

    void render(GuiGraphics g, int mx, int my, float partial) {
        int right = x + w, bottom = y + h;
        int backdrop = 0xFF444749;
        g.fill(0, 0, editor.width, y, backdrop); g.fill(0, bottom, editor.width, editor.height, backdrop);
        g.fill(0, y, x, bottom, backdrop); g.fill(right, y, editor.width, bottom, backdrop);
        if (active && editor.client().level != null && editor.client().level.dimension().equals(top.cnpcmagic.server.MagicPreview.DIMENSION)) {
            g.fill(x, y, right, vy, PAPER); g.fill(x, vy + vh, right, bottom, PAPER);
            g.fill(x, vy, vx, vy + vh, PAPER); g.fill(vx + vw, vy, right, vy + vh, PAPER);
            g.renderOutline(vx - 1, vy - 1, vw + 2, vh + 2, EDGE);
        } else {
            g.fill(x, y, right, bottom, PAPER);
            panel(g, vx, vy, vw, vh, 0xFF353D3F);
            g.drawCenteredString(editor.editorFont(), tr(opening ? "openingPreview" : "preview"), vx + vw / 2, vy + vh / 2 - 4, WHITE);
        }
        g.renderOutline(x, y, w, h, EDGE);
        g.drawString(editor.editorFont(), tr(edit == null ? "addSpell" : "replaceSpell"), x + 81, y + 13, INK, false);
        g.drawString(editor.editorFont(), tr("spellProperties"), ix + 4, vy + 5, MUTED, false);
        if (!selected.isEmpty()) g.drawString(editor.editorFont(), editor.editorFont().plainSubstrByWidth(editor.name(selected), iw - 8), ix + 4, vy + 19, INK, false);
        for (int i = 0; i < infoRows() && i + scroll < info.size(); i++)
            g.drawString(editor.editorFont(), info.get(i + scroll), ix + 4, vy + 33 + i * 11, INK, false);
        if (info.size() > infoRows()) {
            int track = h - 84, thumb = Math.max(6, track * infoRows() / info.size());
            int top = vy + 32 + (track - thumb) * scroll / (info.size() - infoRows());
            g.fill(ix + iw - 3, top, ix + iw - 1, top + thumb, MUTED);
        }
        if (filtered.isEmpty()) g.drawString(editor.editorFont(), tr("noSpells"), x + 15, vy + 30, MUTED, false);
        if (!status.isEmpty()) g.drawString(editor.editorFont(), editor.editorFont().plainSubstrByWidth(status, Math.max(1, w - 12)), x + 10, bottom - 10,
            status.startsWith("!") ? RED : MUTED, false);
        editor.drawControls(g, mx, my, partial);
    }

    boolean hasProperties() { return !info.isEmpty(); }
}

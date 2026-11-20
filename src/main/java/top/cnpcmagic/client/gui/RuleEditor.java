package top.cnpcmagic.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.data.RuleConditions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

import static top.cnpcmagic.client.gui.EditorScreen.*;

final class RuleEditor {
    final GuiNpcMagic editor;
    final MagicRule rule;
    private final Map<String, String> values = new HashMap<>();
    int firstCondition, visibleConditions, conditionX, conditionY;

    RuleEditor(GuiNpcMagic editor, MagicRule rule) {
        this.editor = editor; this.rule = rule;
        var c = rule.conditions;
        values.put("cnpcmagic.gui.level", Integer.toString(rule.level));
        values.put("cnpcmagic.gui.casts", Integer.toString(rule.casts));
        values.put("cnpcmagic.gui.cooldown", Double.toString(rule.cooldownTicks / 20.0));
        values.put("minimumRange", Double.toString(c.minimumRange));
        values.put("maximumRange", Integer.toString(rule.range));
        values.put("casterPercent", Double.toString(c.casterHealth));
        values.put("targetPercent", Double.toString(c.targetHealth));
        values.put("casterEffectId", c.casterEffect); values.put("targetEffectId", c.targetEffect);
        values.put("previousSpell", c.previousSpell); values.put("comboTicks", Integer.toString(c.comboTicks));
    }

    void controls(int x, int y, int w, int bottom) {
        int action = Math.min(64, w / 5);
        var replace = editor.buttonText(x, y, w - 2 * (action + 3), Component.literal(editor.name(rule.spellId)), () -> {
            if (editor.flushDetail()) editor.openPicker(this);
        });
        replace.setTooltip(Tooltip.create(tr("replaceSpell")));
        editor.button(x + w - 2 * action - 3, y, action, "duplicate", () -> {
            if (editor.flushDetail()) editor.insert(MagicRule.load(rule.save()));
        });
        editor.button(x + w - action, y, action, "cnpcmagic.gui.remove", editor::deleteSelected);
        editor.segments(x, y + 22, w / 2 - 3, new String[]{"disabledRule", "enabledRule"}, rule.enabled ? 1 : 0, i -> rule.enabled = i == 1);
        editor.segments(x + w / 2 + 3, y + 22, w - w / 2 - 3, new String[]{"ordered", "independent"}, rule.independent ? 1 : 0, i -> rule.independent = i == 1);
        int cell = w / 3;
        compact(x, y + 45, cell - 3, "cnpcmagic.gui.level", true);
        compact(x + cell, y + 45, cell - 3, "cnpcmagic.gui.casts", !rule.independent);
        compact(x + cell * 2, y + 45, w - cell * 2, "cnpcmagic.gui.cooldown", true);

        editor.label(x, y + 71, "conditions");
        var c = rule.conditions;
        editor.segments(x + Math.min(85, w / 3), y + 66, w - Math.min(85, w / 3), new String[]{"matchAll", "matchAny"}, c.any ? 1 : 0, i -> c.any = i == 1);
        var types = RuleConditions.Type.values();
        conditionX = x; conditionY = y + 88;
        visibleConditions = Math.max(1, Math.min(types.length, (bottom - conditionY) / 20));
        firstCondition = Math.min(firstCondition, types.length - visibleConditions);
        for (int i = firstCondition; i < firstCondition + visibleConditions; i++) {
            var type = types[i]; int row = conditionY + (i - firstCondition) * 20;
            boolean on = c.enabled.contains(type);
            String key = "condition." + type.name();
            var toggle = (EditorButton)editor.buttonText(x, row, 19, Component.literal(on ? "\u2713" : "-"), () -> {
                if (on) c.enabled.remove(type); else c.enabled.add(type);
                editor.rebuild();
            });
            toggle.selected = on; toggle.setTooltip(Tooltip.create(tr(key)));
            editor.label(x + 24, row + 6, key);
            int offset = Math.min(126, Math.max(93, w * 2 / 5));
            int start = x + offset, space = w - offset - 5;
            switch (type) {
                case RANGE -> {
                    compact(start, row + 1, space / 2 - 2, "minimumRange", on);
                    compact(start + space / 2 + 2, row + 1, space - space / 2 - 2, "maximumRange", on);
                }
                case CASTER_HEALTH -> compact(start, row + 1, space, "casterPercent", on);
                case TARGET_HEALTH -> compact(start, row + 1, space, "targetPercent", on);
                case CASTER_EFFECT -> input(start, row + 1, space, "casterEffectId", on);
                case TARGET_EFFECT -> input(start, row + 1, space, "targetEffectId", on);
                case PREVIOUS_SPELL -> {
                    int ticks = Math.min(96, space / 3);
                    input(start, row + 1, space - ticks - 4, "previousSpell", on);
                    compact(start + space - ticks, row + 1, ticks, "comboTicks", on);
                }
                case LINE_OF_SIGHT -> { }
            }
        }
    }

    private void compact(int x, int y, int w, String key, boolean on) {
        editor.label(x, y + 5, key);
        int caption = Math.min(w - 20, editor.editorFont().width(text(key)) + 5);
        input(x + caption, y, w - caption, key, on);
    }

    private void input(int x, int y, int w, String key, boolean on) {
        EditBox box = editor.input(x, y, w, key, values.get(key), v -> values.put(key, v));
        box.active = on; box.setEditable(on); box.setTextColor(on ? 0xFFF1F1EA : 0xFF99998C);
        box.setTooltip(Tooltip.create(tr(key)));
    }

    boolean scroll(double x, double y, double delta) {
        if (x < conditionX || y < conditionY || y >= conditionY + visibleConditions * 20) return false;
        int next = net.minecraft.util.Mth.clamp(firstCondition + (delta < 0 ? 1 : -1), 0, RuleConditions.Type.values().length - visibleConditions);
        if (next == firstCondition) return false;
        firstCondition = next; editor.rebuild(); return true;
    }

    boolean apply() {
        try {
            int level = Integer.parseInt(values.get("cnpcmagic.gui.level"));
            int casts = rule.independent ? rule.casts : Integer.parseInt(values.get("cnpcmagic.gui.casts"));
            double seconds = Double.parseDouble(values.get("cnpcmagic.gui.cooldown"));
            if (level < 1 || level > editor.maxLevel(rule.spellId) || casts < 1 || casts > MagicRule.MAX_CASTS
                || !Double.isFinite(seconds) || seconds < 0 || seconds * 20 > MagicRule.MAX_COOLDOWN_TICKS) throw new IllegalArgumentException();
            var c = RuleConditions.load(rule.conditions.save());
            int range = rule.range;
            if (c.enabled.contains(RuleConditions.Type.RANGE)) {
                range = Integer.parseInt(values.get("maximumRange"));
                if (range < 1 || range > MagicRule.MAX_RANGE) throw new IllegalArgumentException();
                c.minimumRange = Double.parseDouble(values.get("minimumRange"));
            }
            if (c.enabled.contains(RuleConditions.Type.CASTER_HEALTH)) c.casterHealth = Double.parseDouble(values.get("casterPercent"));
            if (c.enabled.contains(RuleConditions.Type.TARGET_HEALTH)) c.targetHealth = Double.parseDouble(values.get("targetPercent"));
            if (c.enabled.contains(RuleConditions.Type.CASTER_EFFECT)) c.casterEffect = values.get("casterEffectId");
            if (c.enabled.contains(RuleConditions.Type.TARGET_EFFECT)) c.targetEffect = values.get("targetEffectId");
            if (c.enabled.contains(RuleConditions.Type.PREVIOUS_SPELL)) {
                c.previousSpell = values.get("previousSpell"); c.comboTicks = Integer.parseInt(values.get("comboTicks"));
            }
            String invalid = c.validate(range);
            if (!invalid.isEmpty()) { editor.status = invalid; return false; }
            rule.level = level; rule.casts = casts; rule.cooldownTicks = (int)Math.round(seconds * 20); rule.range = range; rule.conditions = c;
            editor.status = ""; return true;
        } catch (IllegalArgumentException e) { editor.status = text("invalidNumbers"); return false; }
    }
}

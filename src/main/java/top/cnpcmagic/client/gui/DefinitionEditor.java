package top.cnpcmagic.client.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import top.cnpcmagic.casting.SpellDefinition;
import java.util.*;

final class DefinitionEditor extends EditorScreen {
    final GuiNpcMagic editor;
    final SpellDefinition draft;
    private final CompoundTag initial;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initialValues;
    int page, part;
    boolean saving;
    DefinitionEditor(GuiNpcMagic editor, SpellDefinition original) {
        super(editor, "definition"); this.editor = editor;
        draft = original == null ? SpellDefinition.template("custom_" + Long.toString(System.currentTimeMillis(), 36), SpellDefinition.Delivery.PROJECTILE, SpellDefinition.EffectType.DAMAGE) : SpellDefinition.load(original.save());
        initial = draft.save().copy();
        for (String key : List.of("id", "name", "description", "school", "tags", "resource")) values.put(key, initial.getString(key));
        for (String key : List.of("range", "radius", "speed", "gravity", "power", "healthCost")) values.put(key, Double.toString(initial.getDouble(key)));
        for (String key : List.of("castTicks", "cooldown", "duration", "interval", "maxTargets")) values.put(key, Integer.toString(initial.getInt(key)));
        initialValues = Map.copyOf(values);
        MagicEditor.definition = this;
    }
    @Override protected void init() {
        super.init(); button(width - 100, 7, 92, "save", this::save).active = !saving;
        if (saving) { label(18, 48, "saving"); return; }
        int x = Math.max(14, (width - 430) / 2), w = Math.min(430, width - 28);
        segments(x, 43, w, "page", new String[]{"identity", "delivery", "effects", "costs"}, page, i -> { page = i; part = 0; });
        if (page == 2) {
            var effects = draft.effects;
            addRenderableWidget(new Rows(x, 72, w, height - 139, effects.stream().map(e -> (e.enabled ? "" : "– ") + text("effect." + e.type.name()) + " · " + e.amount).toList(), -1,
                i -> minecraft.setScreen(new EffectEditor(this, effects.get(i)))));
            button(x, height - 62, w, "addEffect", () -> {
                if (draft.effects.size() >= 32) { status = text("effectLimit"); return; }
                var effect = new SpellDefinition.Effect(); draft.effects.add(effect); minecraft.setScreen(new EffectEditor(this, effect));
            });
            return;
        }
        String[][] sections;
        if (page == 0) sections = new String[][]{{"id", "name", "description"}, {"school", "tags"}};
        else if (page == 1) sections = new String[][]{{"range", "radius"}, {"speed", "gravity", "power"}, {"duration", "interval", "maxTargets"}};
        else sections = new String[][]{{"castTicks", "cooldown", "healthCost"}, {"resource"}};
        part = Math.min(part, sections.length - 1);
        if (page == 1 && part == 0) {
            label(x, 68, "delivery");
            segmentsGrid(x, 82, w, 4, Arrays.stream(SpellDefinition.Delivery.values()).map(d -> "delivery." + d.name()).toArray(String[]::new), draft.delivery.ordinal(), i -> draft.delivery = SpellDefinition.Delivery.values()[i]);
            int half = (w - 6) / 2;
            entry(x, 126, half, "range"); entry(x + half + 6, 126, w - half - 6, "radius");
        } else {
            int y = 72; for (String key : sections[part]) { entry(x, y, w, key); y += 26; }
        }
        if (page == 0 && part == 1) segments(x, 124, w, "cnpcmagic.gui.enabled", new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, draft.enabled ? 1 : 0, i -> draft.enabled = i == 1);
        if (page == 1 && part == 0) segments(x, 150, w, "filter", new String[]{"enemies", "allies", "all", "self"}, draft.filter.ordinal(), i -> draft.filter = SpellDefinition.Filter.values()[i]);
        if (page == 1 && part == 1) segments(x, 152, w, "homing", new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, draft.homing ? 1 : 0, i -> draft.homing = i == 1);
        if (page == 1 && part == 2) segments(x, 152, w, "lineOfSight", new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, draft.lineOfSight ? 1 : 0, i -> draft.lineOfSight = i == 1);
        int bottom = height - 54, bw = (w - 9) / 4, pages = sections.length;
        button(x, bottom, bw, "previous", () -> { part = Math.floorMod(part - 1, pages); rebuild(); });
        buttonText(x + bw + 3, bottom, bw, Component.literal((part + 1) + " / " + pages), () -> { part = (part + 1) % pages; rebuild(); });
        button(x + (bw + 3) * 2, bottom, bw, "export", () -> { if (apply()) { minecraft.keyboardHandler.setClipboard(draft.save().toString()); status = text("copied"); } });
        button(x + (bw + 3) * 3, bottom, bw, "delete", () -> {
            minecraft.setScreen(new ConfirmScreen(this, () -> { CompoundTag p = new CompoundTag(); p.putString("action", "deleteDefinition"); p.putString("id", initial.getString("id")); MagicEditor.send(p); minecraft.setScreen(editor); }));
        });
    }
    private void entry(int x, int y, int w, String key) { field(x, y, w, key, values.getOrDefault(key, ""), s -> values.put(key, s)); }
    private boolean apply() {
        try {
            CompoundTag tag = draft.save();
            for (String key : List.of("id", "name", "description", "school", "tags", "resource")) tag.putString(key, values.get(key));
            for (String key : List.of("range", "radius", "speed", "gravity", "power", "healthCost")) tag.putDouble(key, Double.parseDouble(values.get(key)));
            for (String key : List.of("castTicks", "cooldown", "duration", "interval", "maxTargets")) tag.putInt(key, Integer.parseInt(values.get(key)));
            SpellDefinition parsed = SpellDefinition.load(tag);
            draft.id = parsed.id; draft.name = parsed.name; draft.description = parsed.description; draft.school = parsed.school; draft.tags = parsed.tags; draft.resource = parsed.resource;
            draft.range = parsed.range; draft.radius = parsed.radius; draft.speed = parsed.speed; draft.gravity = parsed.gravity; draft.power = parsed.power; draft.healthCost = parsed.healthCost;
            draft.castTicks = parsed.castTicks; draft.cooldownTicks = parsed.cooldownTicks; draft.duration = parsed.duration; draft.interval = parsed.interval; draft.maxTargets = parsed.maxTargets;
            return true;
        } catch (IllegalArgumentException e) { status = e.getMessage() == null ? text("invalidNumbers") : e.getMessage(); return false; }
    }
    private void save() {
        if (!apply() || saving) return;
        if (!draft.id.equals(initial.getString("id")) && editor.library.containsKey(draft.id)) { status = text("duplicateId"); return; }
        saving = true; status = text("saving"); CompoundTag t = new CompoundTag(); t.putString("action", "definition"); t.put("definition", draft.save()); MagicEditor.send(t); rebuild();
    }
    void saved() { saving = false; MagicEditor.definition = null; minecraft.setScreen(new LibraryScreen(editor)); }
    @Override public void onClose() {
        if (saving) return;
        // Raw field values are checked as well, so an invalid number cannot bypass the unsaved-changes prompt.
        boolean changed = !draft.save().equals(initial) || !values.equals(initialValues);
        if (!changed) { MagicEditor.definition = null; minecraft.setScreen(editor); return; }
        minecraft.setScreen(new GuiNpcMagic.LeaveScreen(this, () -> { minecraft.setScreen(this); save(); }, () -> { MagicEditor.definition = null; minecraft.setScreen(editor); }));
    }
    static final class ChoiceScreen extends EditorScreen {
        final String[] options; final java.util.function.IntConsumer pick;
        ChoiceScreen(EditorScreen parent, String title, String[] options, java.util.function.IntConsumer pick) { super(parent, title); this.options = options; this.pick = pick; }
        @Override protected void init() { super.init(); addRenderableWidget(new Rows(14, 43, width - 28, height - 82, Arrays.stream(options).map(EditorScreen::text).toList(), -1, i -> { pick.accept(i); minecraft.setScreen(parent); })); }
    }
    static final class ConfirmScreen extends EditorScreen {
        final Runnable confirm;
        ConfirmScreen(EditorScreen parent, Runnable confirm) { super(parent, "confirmDelete"); this.confirm = confirm; }
        @Override protected void init() { super.init(); label(18, 60, "deleteHint"); button(18, 88, width - 36, "delete", confirm); }
    }
}

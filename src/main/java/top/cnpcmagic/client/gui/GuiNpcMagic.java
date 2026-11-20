package top.cnpcmagic.client.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import top.cnpcmagic.casting.SpellDefinition;
import top.cnpcmagic.client.provider.*;
import top.cnpcmagic.data.*;
import top.cnpcmagic.network.*;
import java.util.*;

/** Standalone Gun-style editor with explicit draft/save acknowledgement. */
public class GuiNpcMagic extends EditorScreen implements IGuiData {
    final EntityNPCInterface npc;
    final Map<String, List<MagicRule>> rules = new LinkedHashMap<>();
    final Map<String, SpellDefinition> library = new LinkedHashMap<>();
    private CompoundTag baseline = new CompoundTag();
    private CompoundTag pendingSave;
    RuleEditor detail;
    SpellPicker picker;
    private Rows ruleRows;
    String provider = "cnpcmagic";
    int selected;
    boolean synced, saving, leaveAfterSave;
    private boolean requested;
    public GuiNpcMagic(EntityNPCInterface npc) { super(null, "title"); this.npc = npc; MagicEditor.editor = this; }
    @Override public void init() {
        if (picker != null) { picker.controls(); return; }
        super.init();
        int orderWidth = Math.max(82, font.width(text("sequence")) + 10);
        button(width - 108 - orderWidth, 7, orderWidth, "sequence", () -> { if (flushDetail()) minecraft.setScreen(new SequenceScreen(this)); }).active = synced && !saving;
        button(width - 100, 7, 92, "save", this::save).active = synced && !saving;
        if (saving) { label(18, 48, "saving"); return; }
        int side = Math.min(180, Math.max(104, width / 4));
        int strip = width - 24;
        List<String> providers = providerIds();
        for (int i = 0; i < providers.size(); i++) {
            String id = providers.get(i); rules.computeIfAbsent(id, k -> new ArrayList<>());
            Component caption = Component.translatable("cnpcmagic.provider." + id);
            int left = strip * i / providers.size(), right = strip * (i + 1) / providers.size();
            var b = (EditorButton)buttonText(12 + left, 40, right - left, caption, () -> {
                if (provider.equals(id) || !flushDetail()) return;
                detail = null; ruleRows = null; provider = id; selected = 0; rebuild();
            });
            b.selected = provider.equals(id); b.setTooltip(Tooltip.create(caption));
        }
        int x = side + 18, w = width - x - 14, bottom = height - 32;
        if (synced) {
            List<MagicRule> entries = rules.getOrDefault(provider, List.of());
            List<String> names = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                MagicRule r = entries.get(i); names.add((i + 1) + ". " + name(r.spellId) + " · " + text(r.enabled ? "cnpcmagic.gui.on" : "cnpcmagic.gui.off"));
            }
            selected = Math.min(selected, Math.max(0, entries.size() - 1));
            int offset = ruleRows == null ? 0 : ruleRows.offset;
            ruleRows = addRenderableWidget(new Rows(12, 66, side, bottom - 66 - (provider.equals("cnpcmagic") ? 47 : 23), names, selected, i -> {
                if (i == selected) return;
                if (!flushDetail()) { ruleRows.selected = selected; return; }
                detail = null; selected = i; rebuild();
            }));
            ruleRows.offset = Math.max(selected - ruleRows.capacity() + 1, Math.min(offset, selected));
            if (!entries.isEmpty()) {
                if (detail == null || detail.rule != entries.get(selected)) detail = new RuleEditor(this, entries.get(selected));
                detail.controls(x, 66, w, bottom);
            } else label(x, 74, "noRules");
            boolean builtin = provider.equals("cnpcmagic");
            button(12, bottom - (builtin ? 44 : 20), side, "addSpell", () -> { if (flushDetail()) openPicker(null); });
            if (builtin) {
                int half = (side - 2) / 2;
                button(12, bottom - 20, half, "newSpell", () -> { if (flushDetail()) minecraft.setScreen(new DefinitionEditor(this, null)); });
                button(14 + half, bottom - 20, side - half - 2, "library", () -> { if (flushDetail()) minecraft.setScreen(new LibraryScreen(this)); });
            }
        } else label(x, 68, "cnpcmagic.gui.loading");
        if (!requested) { requested = true; NetworkHandler.CHANNEL.sendToServer(new C2SMagicRequest()); }
    }
    List<String> providerIds() {
        List<String> ids = new ArrayList<>(); ids.add("cnpcmagic");
        for (ClientMagicProvider p : ClientMagicProviders.all()) ids.add(p.getId());
        return ids;
    }
    String name(String id) {
        SpellDefinition definition = library.get(id);
        if (definition != null) {
            String path = id.substring(id.indexOf(':') + 1);
            String key = "cnpcmagic.spell." + path;
            return definition.name.equals(path.replace('_', ' ')) && net.minecraft.locale.Language.getInstance().has(key)
                ? Component.translatable(key).getString() : definition.name;
        }
        int colon = id.indexOf(':'); ClientMagicProvider p = colon < 0 ? null : ClientMagicProviders.get(id.substring(0, colon));
        return p == null ? id.isEmpty() ? text("cnpcmagic.gui.none") : id : p.getDisplayName(id);
    }
    int maxLevel(String id) { int colon = id.indexOf(':'); var p = colon < 0 ? null : ClientMagicProviders.get(id.substring(0, colon)); return id.startsWith("cnpcmagic:") ? 10 : p == null ? Integer.MAX_VALUE : Math.max(1, p.getMaxLevel(id)); }
    void openPicker(RuleEditor edit) {
        picker = new SpellPicker(this, edit); rebuild();
        if (edit != null && !edit.rule.spellId.isEmpty()) picker.preview();
    }
    void insert(MagicRule rule) {
        var entries = rules.get(provider);
        rule.sequence = -1;
        entries.add(rule); selected = entries.size() - 1; detail = null; rebuild();
    }
    void deleteSelected() {
        var entries = rules.get(provider);
        entries.remove(selected); detail = null; status = ""; rebuild();
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (picker != null && picker.scroll(x, y, delta)) return true;
        if (picker == null && detail != null && detail.scroll(x, y, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (picker != null) { picker.render(g, mx, my, partial); return; }
        super.render(g, mx, my, partial);
        if (detail != null && detail.visibleConditions < RuleConditions.Type.values().length) {
            int y = detail.conditionY, h = detail.visibleConditions * 20;
            g.fill(width - 18, y, width - 15, y + h, IRON);
            int thumb = h * detail.visibleConditions / RuleConditions.Type.values().length;
            int top = y + h * detail.firstCondition / RuleConditions.Type.values().length;
            g.fill(width - 18, top, width - 15, top + thumb, MUTED);
        }
    }
    CompoundTag snapshot() {
        CompoundTag providers = new CompoundTag(); rules.forEach((id, list) -> providers.put(id, MagicDataStore.saveRules(list)));
        CompoundTag result = new CompoundTag(); result.put("providers", providers); result.putUUID("npc", npc.getUUID()); return result;
    }
    @Override public void setGuiData(CompoundTag data) {
        if (!data.hasUUID("npc") || !data.getUUID("npc").equals(npc.getUUID())) return;
        detail = null; rules.clear(); CompoundTag providers = data.getCompound("providers");
        for (String id : providers.getAllKeys()) rules.put(id, MagicDataStore.loadRules(providers.get(id)));
        library.clear(); CompoundTag definitions = data.getCompound("library");
        for (String id : definitions.getAllKeys()) { SpellDefinition d = SpellDefinition.load(definitions.getCompound(id)); library.put(d.id, d); }
        synced = true; baseline = snapshot().copy(); if (minecraft.screen == this) rebuild();
    }
    boolean flushDetail() { return detail == null || detail.apply(); }
    public void save() { if (!synced || saving || !flushDetail()) return; pendingSave = snapshot().copy(); saving = true; status = text("saving"); NetworkHandler.CHANNEL.sendToServer(new C2SMagicSave(pendingSave)); rebuild(); }
    void saved(CompoundTag data) {
        if (!data.hasUUID("npc") || !data.getUUID("npc").equals(npc.getUUID())) return;
        if (!saving || pendingSave == null) return;
        if (snapshot().equals(pendingSave)) {
            detail = null; rules.clear(); CompoundTag accepted = data.getCompound("providers");
            for (String id : accepted.getAllKeys()) rules.put(id, MagicDataStore.loadRules(accepted.get(id)));
        }
        baseline = new CompoundTag(); baseline.putUUID("npc", npc.getUUID()); baseline.put("providers", data.getCompound("providers").copy());
        saving = false; pendingSave = null; status = text("saved");
        if (leaveAfterSave && baseline.equals(snapshot())) returnAppearance(); else { leaveAfterSave = false; if (minecraft.screen == this) rebuild(); }
    }
    private void returnAppearance() {
        EntityNPCInterface current = npc;
        if (minecraft.level != null) for (var entity : minecraft.level.entitiesForRendering()) if (entity instanceof EntityNPCInterface n && n.getUUID().equals(npc.getUUID())) { current = n; break; }
        MagicEditor.editor = null; minecraft.setScreen(new GuiNpcDisplay(current));
    }
    public boolean ready() { return synced && !saving; }
    public void verifyIndependent(boolean change) {
        for (ClientMagicProvider p : ClientMagicProviders.all()) {
            List<MagicRule> entries = rules.get(p.getId());
            if (entries == null || entries.size() < 2) continue;
            if (entries.get(1).independent || entries.get(0).independent != !change) throw new IllegalStateException("Per-entry independent setting mismatch");
            if (change) entries.get(0).independent = true;
        }
    }
    @Override public void onClose() {
        if (picker != null) { picker.close(false); return; }
        if (saving) { status = text("saving"); return; }
        if (!flushDetail()) return;
        if (!synced || baseline.equals(snapshot())) { returnAppearance(); return; }
        minecraft.setScreen(new LeaveScreen(this, () -> { leaveAfterSave = true; minecraft.setScreen(this); save(); }, this::returnAppearance));
    }
    static final class LeaveScreen extends EditorScreen {
        final Runnable save, discard;
        LeaveScreen(EditorScreen parent, Runnable save, Runnable discard) { super(parent, "unsaved"); this.save = save; this.discard = discard; }
        @Override protected void init() {
            super.init(); int x = Math.max(14, (width - 280) / 2), w = Math.min(280, width - 28);
            label(x, 54, "unsavedHint"); button(x, 82, w, "save", save); button(x, 108, w, "discard", discard); button(x, 134, w, "continue", this::onClose);
        }
    }
}

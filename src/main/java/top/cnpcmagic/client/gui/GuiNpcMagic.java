package top.cnpcmagic.client.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.network.C2SMagicRequest;
import top.cnpcmagic.network.C2SMagicSave;
import top.cnpcmagic.network.NetworkHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GuiNpcMagic extends GuiNPCInterface2 implements IGuiData, ITextfieldListener, ICustomScrollListener {
    private final Map<String, List<MagicRule>> rules = new LinkedHashMap<>();
    private String selectedProvider = "";
    private int selectedRule = 0;
    private boolean syncRequested = false;
    private boolean synced = false;

    public GuiNpcMagic(EntityNPCInterface npc) {
        super(npc, 7);
    }

    @Override
    public void init() {
        super.init();
        List<ClientMagicProvider> providers = new ArrayList<>(ClientMagicProviders.all());
        for (ClientMagicProvider provider : providers) {
            rules.computeIfAbsent(provider.getId(), k -> new ArrayList<>());
        }
        if (selectedProvider.isEmpty() && !providers.isEmpty()) {
            selectedProvider = providers.get(0).getId();
        }

        addLabel(new GuiLabel(0, "cnpcmagic.gui.providers", guiLeft + 8, guiTop + 10));
        GuiCustomScrollNop scroll = new GuiCustomScrollNop(this, 100);
        scroll.setSize(84, 158);
        scroll.guiLeft = guiLeft + 8;
        scroll.guiTop = guiTop + 24;
        addScroll(scroll);
        List<String> names = new ArrayList<>();
        for (ClientMagicProvider provider : providers) {
            names.add(providerName(provider.getId()));
        }
        scroll.setList(names);
        scroll.setSelected(providerName(selectedProvider));

        if (synced) {
            drawProviderPanel();
        } else {
            addLabel(new GuiLabel(1, "cnpcmagic.gui.loading", guiLeft + 100, guiTop + 24));
        }

        if (!syncRequested) {
            syncRequested = true;
            NetworkHandler.CHANNEL.sendToServer(new C2SMagicRequest());
        }
    }

    private void drawProviderPanel() {
        ClientMagicProvider provider = ClientMagicProviders.get(selectedProvider);
        if (provider == null) {
            addLabel(new GuiLabel(1, "cnpcmagic.gui.noProvider", guiLeft + 130, guiTop + 24));
            return;
        }
        List<MagicRule> entries = rules.get(selectedProvider);
        GuiCustomScrollNop spellScroll = new GuiCustomScrollNop(this, 101);
        spellScroll.setSize(108, 134);
        spellScroll.guiLeft = guiLeft + 98;
        spellScroll.guiTop = guiTop + 24;
        addScroll(spellScroll);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            names.add((i + 1) + ". " + spellName(provider, entries.get(i)));
        }
        spellScroll.setList(names);
        selectedRule = Mth.clamp(selectedRule, 0, Math.max(0, entries.size() - 1));
        if (!names.isEmpty()) {
            spellScroll.setSelected(names.get(selectedRule));
        }
        addButton(new GuiButtonNop(this, 13, guiLeft + 98, guiTop + 162, 52, 18, "cnpcmagic.gui.add"));
        addButton(new GuiButtonNop(this, 14, guiLeft + 154, guiTop + 162, 52, 18, "cnpcmagic.gui.remove"));
        MagicRule rule = currentRule();
        if (rule == null) {
            addLabel(new GuiLabel(1, "cnpcmagic.gui.addHint", guiLeft + 214, guiTop + 30));
            return;
        }
        int x = guiLeft + 214;
        int y = guiTop + 20;

        addLabel(new GuiLabel(1, "cnpcmagic.gui.enabled", x, y + 5));
        addButton(new GuiButtonNop(this, 10, x + 102, y, 64, 18,
            new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, rule.enabled ? 1 : 0));

        y += 26;
        addLabel(new GuiLabel(2, "cnpcmagic.gui.spell", x, y + 5));
        GuiButtonNop spellButton = new GuiButtonNop(this, 11, x + 62, y, 134, 18, "");
        spellButton.setMessage(Component.literal(spellName(provider, rule)));
        addButton(spellButton);

        y += 24;
        addLabel(new GuiLabel(3, selectedProvider.equals("goety") ? "cnpcmagic.gui.goetyLevel" : "cnpcmagic.gui.level", x, y + 5));
        addTextField(numberField(20, x + 102, y, rule.level, 1, Math.max(1, provider.getMaxLevel(rule.spellId)), 1));

        y += 24;
        addLabel(new GuiLabel(4, "cnpcmagic.gui.casts", x, y + 5));
        addTextField(numberField(21, x + 102, y, rule.casts, 1, 100, 3));

        y += 24;
        addLabel(new GuiLabel(5, "cnpcmagic.gui.cooldown", x, y + 5));
        addTextField(numberField(22, x + 102, y, rule.cooldownTicks / 20, 0, 3600, 10));

        y += 24;
        addLabel(new GuiLabel(6, "cnpcmagic.gui.range", x, y + 5));
        addTextField(numberField(23, x + 102, y, rule.range, 1, 64, 6));

        addLabel(new GuiLabel(7, "cnpcmagic.gui.hint", guiLeft + 8, guiTop + 186, 0x777777));
    }

    private MagicRule currentRule() {
        List<MagicRule> entries = rules.get(selectedProvider);
        return entries == null || selectedRule < 0 || selectedRule >= entries.size() ? null : entries.get(selectedRule);
    }

    private GuiTextFieldNop numberField(int id, int x, int y, int value, int min, int max, int def) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 52, 18, String.valueOf(value));
        field.numbersOnly = true;
        field.setMinMaxDefault(min, max, def);
        return field;
    }

    private String providerName(String id) {
        return Component.translatable("cnpcmagic.provider." + id).getString();
    }

    private String spellName(ClientMagicProvider provider, MagicRule rule) {
        if (rule.spellId == null || rule.spellId.isEmpty()) {
            return Component.translatable("cnpcmagic.gui.none").getString();
        }
        return provider.getDisplayName(rule.spellId);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (!synced) {
            return;
        }
        applyFields();
        MagicRule rule = currentRule();
        switch (button.id) {
            case 10 -> {
                if (rule != null) {
                    rule.enabled = button.getValue() == 1;
                }
            }
            case 11 -> openSpellPicker(rule);
            case 13 -> {
                List<MagicRule> entries = rules.get(selectedProvider);
                if (entries != null) {
                    entries.add(new MagicRule());
                    selectedRule = entries.size() - 1;
                    init();
                    openSpellPicker(currentRule());
                }
            }
            case 14 -> {
                if (rule != null) {
                    rules.get(selectedProvider).remove(selectedRule);
                    selectedRule = Math.max(0, selectedRule - 1);
                    init();
                }
            }
            default -> {
            }
        }
    }

    private void openSpellPicker(MagicRule rule) {
        ClientMagicProvider provider = ClientMagicProviders.get(selectedProvider);
        if (provider == null || rule == null) {
            return;
        }
        Map<String, String> byName = new LinkedHashMap<>();
        String current = "";
        for (String spellId : provider.getSpellIds()) {
            String name = provider.getDisplayName(spellId) + " [" + spellId + "]";
            byName.put(name, spellId);
            if (spellId.equals(rule.spellId)) {
                current = name;
            }
        }
        setSubGui(new GuiSelectSpell(this, byName, current, picked -> {
            rule.spellId = picked;
            rule.enabled = true;
            int maxLevel = Math.max(1, provider.getMaxLevel(picked));
            if (rule.level > maxLevel) {
                rule.level = maxLevel;
            }
        }));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        MagicRule rule = currentRule();
        if (rule == null) {
            return;
        }
        switch (field.id) {
            case 20 -> rule.level = readInt(field, 1, 100, 1);
            case 21 -> rule.casts = readInt(field, 1, 100, 3);
            case 22 -> rule.cooldownTicks = readInt(field, 0, 3600, 10) * 20;
            case 23 -> rule.range = readInt(field, 1, 64, 6);
            default -> {
            }
        }
    }

    private int readInt(GuiTextFieldNop field, int min, int max, int def) {
        return field.isInteger() ? Mth.clamp(field.getInteger(), min, max) : def;
    }

    private void applyFields() {
        for (int id : new int[]{20, 21, 22, 23}) {
            GuiTextFieldNop field = getTextField(id);
            if (field != null) {
                unFocused(field);
            }
        }
    }

    @Override
    public void scrollClicked(double mouseX, double mouseY, int mouseButton, GuiCustomScrollNop scroll) {
        if (scroll.id != 100 && scroll.id != 101) {
            return;
        }
        applyFields();
        String name = scroll.getSelected();
        if (name == null) {
            return;
        }
        if (scroll.id == 101) {
            // Number prefixes keep repeated spells independently selectable.
            selectedRule = Integer.parseInt(name.substring(0, name.indexOf('.'))) - 1;
            init();
            return;
        }
        for (ClientMagicProvider provider : ClientMagicProviders.all()) {
            if (providerName(provider.getId()).equals(name)) {
                selectedProvider = provider.getId();
                selectedRule = 0;
                init();
                return;
            }
        }
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
    }

    @Override
    public void setGuiData(CompoundTag data) {
        if (!data.contains("providers")) {
            return;
        }
        CompoundTag providers = data.getCompound("providers");
        for (String providerId : providers.getAllKeys()) {
            rules.put(providerId, MagicDataStore.loadRules(providers.get(providerId)));
        }
        synced = true;
        init();
    }

    @Override
    public void save() {
        if (!synced) {
            return;
        }
        applyFields();
        CompoundTag providers = new CompoundTag();
        for (Map.Entry<String, List<MagicRule>> entry : rules.entrySet()) {
            providers.put(entry.getKey(), MagicDataStore.saveRules(entry.getValue()));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("providers", providers);
        NetworkHandler.CHANNEL.sendToServer(new C2SMagicSave(tag));
    }
}

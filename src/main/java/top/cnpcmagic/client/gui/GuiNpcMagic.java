package top.cnpcmagic.client.gui;

import electroblob.wizardry.spell.Spell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.IGuiData;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.network.C2SMagicRequest;
import top.cnpcmagic.network.C2SMagicSave;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.wizardry.WizardrySpells;

/** CustomNPCs editor page for ordered and independent Wizardry spell rules. */
public final class GuiNpcMagic extends GuiNPCInterface2
    implements IGuiData, ITextfieldListener, ICustomScrollListener {

    private static final int PROVIDER_SCROLL = 100;
    private static final int RULE_SCROLL = 101;

    private final List<MagicRule> rules = new ArrayList<>();
    private int selectedRule;
    private boolean syncRequested;
    private boolean synced;

    public GuiNpcMagic(EntityNPCInterface npc) {
        super(npc, 7);
    }

    @Override
    public void func_73866_w_() {
        super.func_73866_w_();

        addLabel(new GuiNpcLabel(0, "cnpcmagic.gui.providers", guiLeft + 8, guiTop + 10));
        GuiCustomScroll providerScroll = new GuiCustomScroll(this, PROVIDER_SCROLL);
        providerScroll.setSize(84, 158);
        providerScroll.guiLeft = guiLeft + 8;
        providerScroll.guiTop = guiTop + 24;
        addScroll(providerScroll);
        List<String> providers = new ArrayList<>();
        providers.add(providerName());
        providerScroll.setList(providers);
        providerScroll.setSelected(providerName());

        if (synced) {
            drawRulePanel();
        } else {
            addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.loading", guiLeft + 100, guiTop + 24));
        }

        if (!syncRequested) {
            syncRequested = true;
            NetworkHandler.CHANNEL.sendToServer(new C2SMagicRequest());
        }
    }

    private void drawRulePanel() {
        GuiCustomScroll ruleScroll = new GuiCustomScroll(this, RULE_SCROLL);
        ruleScroll.setSize(108, 134);
        ruleScroll.guiLeft = guiLeft + 98;
        ruleScroll.guiTop = guiTop + 24;
        addScroll(ruleScroll);

        List<String> names = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            names.add((i + 1) + ". " + spellName(rules.get(i)));
        }
        ruleScroll.setList(names);
        selectedRule = MathHelper.clamp(selectedRule, 0, Math.max(0, rules.size() - 1));
        if (!names.isEmpty()) ruleScroll.setSelected(names.get(selectedRule));

        addButton(new GuiNpcButton(13, guiLeft + 98, guiTop + 162, 52, 18, "cnpcmagic.gui.add"));
        addButton(new GuiNpcButton(14, guiLeft + 154, guiTop + 162, 52, 18, "cnpcmagic.gui.remove"));

        MagicRule rule = currentRule();
        if (rule == null) {
            addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.addHint", guiLeft + 214, guiTop + 30));
            return;
        }

        int x = guiLeft + 214;
        int y = guiTop + 20;
        addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.enabled", x, y + 5));
        addButton(new GuiNpcButton(10, x + 102, y, 64, 18,
            new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, rule.enabled ? 1 : 0));

        y += 22;
        addLabel(new GuiNpcLabel(8, "cnpcmagic.gui.independent", x, y + 5));
        addButton(new GuiNpcButton(12, x + 102, y, 64, 18,
            new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, rule.independent ? 1 : 0));

        y += 22;
        addLabel(new GuiNpcLabel(2, "cnpcmagic.gui.spell", x, y + 5));
        addButton(new GuiNpcButton(11, x + 50, y, 96, 18, spellName(rule)));
        addButton(new GuiNpcButton(15, x + 150, y, 48, 18, "cnpcmagic.gui.clear"));

        y += 22;
        addLabel(new GuiNpcLabel(3, "cnpcmagic.gui.level", x, y + 5));
        addTextField(numberField(20, x + 102, y, rule.level, 1, MagicRule.MAX_LEVEL, 1));

        y += 22;
        addLabel(new GuiNpcLabel(4,
            rule.independent ? "cnpcmagic.gui.castsUnused" : "cnpcmagic.gui.casts", x, y + 5));
        GuiNpcTextField casts = numberField(21, x + 102, y, rule.casts, 1, MagicRule.MAX_CASTS, 3);
        casts.enabled = !rule.independent;
        casts.canEdit = !rule.independent;
        addTextField(casts);

        y += 22;
        addLabel(new GuiNpcLabel(5, "cnpcmagic.gui.cooldown", x, y + 5));
        addTextField(numberField(22, x + 102, y, rule.cooldownTicks / 20, 0, 3600, 10));

        y += 22;
        addLabel(new GuiNpcLabel(6, "cnpcmagic.gui.range", x, y + 5));
        addTextField(numberField(23, x + 102, y, rule.range, 1, MagicRule.MAX_RANGE, 6));

        addLabel(new GuiNpcLabel(7, "cnpcmagic.gui.hint", guiLeft + 8, guiTop + 186, 0x777777));
    }

    private GuiNpcTextField numberField(int id, int x, int y, int value, int min, int max, int def) {
        GuiNpcTextField field = new GuiNpcTextField(id, this, x, y, 52, 18, Integer.toString(value));
        field.numbersOnly = true;
        field.setMinMaxDefault(min, max, def);
        return field;
    }

    private MagicRule currentRule() {
        return selectedRule < 0 || selectedRule >= rules.size() ? null : rules.get(selectedRule);
    }

    private String providerName() {
        return I18n.translateToLocal("cnpcmagic.provider.ebwizardry");
    }

    private String spellName(MagicRule rule) {
        if (rule.spellId == null || rule.spellId.isEmpty()) {
            return I18n.translateToLocal("cnpcmagic.gui.none");
        }
        Spell spell = WizardrySpells.get(rule.spellId);
        return spell == null ? rule.spellId : spell.getDisplayName();
    }

    @Override
    protected void func_146284_a(GuiButton guiButton) {
        if (!synced || !(guiButton instanceof GuiNpcButton)) return;
        applyFields();
        GuiNpcButton button = (GuiNpcButton) guiButton;
        MagicRule rule = currentRule();
        switch (button.field_146127_k) {
            case 10:
                if (rule != null) rule.enabled = button.getValue() == 1;
                break;
            case 11:
                openSpellPicker(rule);
                break;
            case 12:
                if (rule != null) {
                    rule.independent = button.getValue() == 1;
                    func_73866_w_();
                }
                break;
            case 13:
                if (rules.size() < MagicDataStore.MAX_RULES) {
                    rules.add(new MagicRule());
                    selectedRule = rules.size() - 1;
                    func_73866_w_();
                    openSpellPicker(currentRule());
                }
                break;
            case 14:
                if (rule != null) {
                    rules.remove(selectedRule);
                    selectedRule = Math.max(0, selectedRule - 1);
                    func_73866_w_();
                }
                break;
            case 15:
                if (rule != null) {
                    rule.spellId = "";
                    rule.enabled = false;
                    func_73866_w_();
                }
                break;
            default:
                break;
        }
    }

    private void openSpellPicker(MagicRule rule) {
        if (rule == null) return;
        Map<String, String> byName = new LinkedHashMap<>();
        String current = "";
        for (Spell spell : WizardrySpells.supported(npc)) {
            String id = spell.getRegistryName().toString();
            String display = spell.getDisplayName() + " [" + id + "]";
            byName.put(display, id);
            if (id.equals(rule.spellId)) current = display;
        }
        setSubGui(new GuiSelectSpell(byName, current, picked -> {
            rule.spellId = picked;
            rule.enabled = true;
        }));
    }

    @Override
    public void unFocused(GuiNpcTextField field) {
        MagicRule rule = currentRule();
        if (rule == null) return;
        switch (field.getId()) {
            case 20:
                rule.level = readInt(field, 1, MagicRule.MAX_LEVEL, 1);
                break;
            case 21:
                rule.casts = readInt(field, 1, MagicRule.MAX_CASTS, 3);
                break;
            case 22:
                rule.cooldownTicks = readInt(field, 0, 3600, 10) * 20;
                break;
            case 23:
                rule.range = readInt(field, 1, MagicRule.MAX_RANGE, 6);
                break;
            default:
                break;
        }
    }

    private int readInt(GuiNpcTextField field, int min, int max, int def) {
        return field.isInteger() ? MathHelper.clamp(field.getInteger(), min, max) : def;
    }

    private void applyFields() {
        for (int id : new int[]{20, 21, 22, 23}) {
            GuiNpcTextField field = getTextField(id);
            if (field != null) unFocused(field);
        }
    }

    @Override
    public void scrollClicked(int mouseX, int mouseY, int mouseButton, GuiCustomScroll scroll) {
        if (scroll.id != RULE_SCROLL) return;
        applyFields();
        String selected = scroll.getSelected();
        if (selected == null) return;
        int dot = selected.indexOf('.');
        if (dot <= 0) return;
        try {
            selectedRule = Integer.parseInt(selected.substring(0, dot)) - 1;
            func_73866_w_();
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
    }

    @Override
    public void setGuiData(NBTTagCompound data) {
        if (!data.hasKey("providers", 10)) return;
        NBTTagCompound providers = data.getCompoundTag("providers");
        rules.clear();
        if (providers.hasKey(MagicDataStore.PROVIDER_ID)) {
            rules.addAll(MagicDataStore.readRules(providers.getTag(MagicDataStore.PROVIDER_ID)));
        }
        synced = true;
        func_73866_w_();
    }

    @Override
    public void closeSubGui(SubGuiInterface gui) {
        super.closeSubGui(gui);
        func_73866_w_();
    }

    @Override
    public void save() {
        if (!synced) return;
        applyFields();
        NBTTagCompound providers = new NBTTagCompound();
        providers.setTag(MagicDataStore.PROVIDER_ID, MagicDataStore.writeRules(rules));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("providers", providers);
        NetworkHandler.CHANNEL.sendToServer(new C2SMagicSave(root));
    }
}

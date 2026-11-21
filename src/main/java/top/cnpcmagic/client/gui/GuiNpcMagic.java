package top.cnpcmagic.client.gui;

import electroblob.wizardry.spell.Spell;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.network.C2SMagicRequest;
import top.cnpcmagic.network.C2SMagicSave;
import top.cnpcmagic.network.C2SPreviewStart;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.wizardry.WizardrySpells;

/** CustomNPCs editor page for ordered and independent Wizardry spell rules. */
public final class GuiNpcMagic extends GuiNPCInterface2
    implements IGuiData, ITextfieldListener, ICustomScrollListener {

    private static final int RULE_SCROLL = 101;
    private static final int INK = 0xFF303A38;
    private static final int DEEP_INK = 0xFF27312F;
    private static final int PAPER = 0xFFE8E9E1;
    private static final int SAGE = 0xFF788675;
    private static final int BRASS = 0xFFAD8954;

    private final List<MagicRule> rules = new ArrayList<>();
    private GuiCustomScroll ruleScroll;
    private int ruleListOffset;
    private int selectedRule;
    private boolean syncRequested;
    private boolean synced;
    private boolean dirty;
    private String status = "";
    private boolean spellPickerOpen;
    private MagicRule spellPickerRule;
    private GuiTextField spellSearch;
    private final List<Spell> pickerSpells = new ArrayList<>();
    private final List<Spell> pickerMatches = new ArrayList<>();
    private String pickerSelectedId = "";
    private int pickerScroll;

    public GuiNpcMagic(EntityNPCInterface npc) {
        super(npc, 7);
    }

    @Override
    public void func_73866_w_() {
        super.func_73866_w_();

        if (synced) {
            drawRulePanel();
        } else {
            addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.loading", guiLeft + 8, guiTop + 30));
        }

        if (!syncRequested) {
            syncRequested = true;
            NetworkHandler.CHANNEL.sendToServer(new C2SMagicRequest());
        }
    }

    private void drawRulePanel() {
        ruleScroll = new GuiCustomScroll(this, RULE_SCROLL) {
            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks, int mouseScrolled) {
                if (mouseScrolled != 0 && isMouseOver(mouseX, mouseY)) {
                    ruleListOffset = MathHelper.clamp(ruleListOffset + (mouseScrolled > 0 ? -1 : 1),
                        0, Math.max(0, getList().size() - 7));
                }
            }

            @Override
            public void func_73864_a(int mouseX, int mouseY, int button) {
                if (button != 0 || mouseX < guiLeft || mouseX >= guiLeft + getXSize()
                    || mouseY < guiTop || mouseY >= guiTop + getYSize()) return;
                if (mouseY < this.guiTop + 1 || mouseY >= this.guiTop + 1 + 7 * 15) return;
                int index = ruleListOffset + (mouseY - this.guiTop - 1) / 15;
                if (index >= getList().size()) return;
                setSelected(getList().get(index));
                GuiNpcMagic.this.scrollClicked(mouseX, mouseY, button, this);
            }
        };
        ruleScroll.setSize(140, 108);
        ruleScroll.guiLeft = guiLeft + 8;
        ruleScroll.guiTop = guiTop + 24;
        addScroll(ruleScroll);

        List<String> names = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            names.add((i + 1) + ". " + spellName(rules.get(i)));
        }
        ruleScroll.setUnsortedList(names);
        ruleListOffset = Math.min(ruleListOffset, Math.max(0, names.size() - 7));
        selectedRule = MathHelper.clamp(selectedRule, 0, Math.max(0, rules.size() - 1));
        if (!names.isEmpty()) ruleScroll.setSelected(names.get(selectedRule));

        addLabel(new GuiNpcLabel(0, "cnpcmagic.provider.ebwizardry", guiLeft + 8, guiTop + 10));
        addLabel(new GuiNpcLabel(10, "cnpcmagic.gui.ruleSettings", guiLeft + 160, guiTop + 10));
        addButton(new GuiNpcButton(13, guiLeft + 8, guiTop + 138, 68, 18, "cnpcmagic.gui.add"));
        addButton(new GuiNpcButton(16, guiLeft + 80, guiTop + 138, 68, 18, "cnpcmagic.gui.copy"));
        addButton(new GuiNpcButton(14, guiLeft + 8, guiTop + 158, 52, 18, "cnpcmagic.gui.remove"));
        addButton(new GuiNpcButton(17, guiLeft + 64, guiTop + 158, 40, 18, "cnpcmagic.gui.up"));
        addButton(new GuiNpcButton(18, guiLeft + 108, guiTop + 158, 40, 18, "cnpcmagic.gui.down"));
        addButton(new GuiNpcButton(20, guiLeft + 298, guiTop + 160, 66, 18, "cnpcmagic.gui.preview"));
        addButton(new GuiNpcButton(19, guiLeft + 369, guiTop + 160, 45, 18, "cnpcmagic.gui.save"));

        MagicRule rule = currentRule();
        if (rule == null) {
            addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.addHint", guiLeft + 160, guiTop + 30));
            return;
        }

        int x = guiLeft + 160;
        int y = guiTop + 20;
        addLabel(new GuiNpcLabel(1, "cnpcmagic.gui.enabled", x, y + 5));
        addButton(new GuiNpcButton(10, x + 102, y, 64, 18,
            new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, rule.enabled ? 1 : 0));

        y += 20;
        addLabel(new GuiNpcLabel(8, "cnpcmagic.gui.independent", x, y + 5));
        addButton(new GuiNpcButton(12, x + 102, y, 64, 18,
            new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, rule.independent ? 1 : 0));

        y += 20;
        addLabel(new GuiNpcLabel(2, "cnpcmagic.gui.spell", x, y + 5));
        addButton(new GuiNpcButton(11, x + 50, y, 96, 18, spellName(rule)));
        addButton(new GuiNpcButton(15, x + 150, y, 48, 18, "cnpcmagic.gui.clear"));

        y += 20;
        addLabel(new GuiNpcLabel(3, "cnpcmagic.gui.level", x, y + 5));
        addTextField(numberField(20, x + 102, y, rule.level, 1, MagicRule.MAX_LEVEL, 1));

        y += 20;
        addLabel(new GuiNpcLabel(4,
            rule.independent ? "cnpcmagic.gui.castsUnused" : "cnpcmagic.gui.casts", x, y + 5));
        GuiNpcTextField casts = numberField(21, x + 102, y, rule.casts, 1, MagicRule.MAX_CASTS, 3);
        casts.enabled = !rule.independent;
        casts.canEdit = !rule.independent;
        addTextField(casts);

        y += 20;
        addLabel(new GuiNpcLabel(5, "cnpcmagic.gui.cooldown", x, y + 5));
        addTextField(numberField(22, x + 102, y, rule.cooldownTicks / 20, 0, 3600, 10));

        y += 20;
        addLabel(new GuiNpcLabel(6, "cnpcmagic.gui.range", x, y + 5));
        addTextField(numberField(23, x + 102, y, rule.range, 1, MagicRule.MAX_RANGE, 6));

        Spell spell = WizardrySpells.get(rule.spellId);
        if (spell != null && spell.isContinuous) {
            y += 20;
            addLabel(new GuiNpcLabel(9, "cnpcmagic.gui.duration", x, y + 5));
            addTextField(numberField(24, x + 102, y, rule.durationTicks, 1,
                MagicRule.MAX_DURATION_TICKS, MagicRule.DEFAULT_DURATION_TICKS));
        }

        addLabel(new GuiNpcLabel(7, "cnpcmagic.gui.hint", guiLeft + 8, guiTop + 186, 0x777777));
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        drawEditorSurface();
        drawRuleRows(mouseX, mouseY);
        drawEditorLabels();
        drawStyledFields(mouseX, mouseY);
        drawStyledButtons(mouseX, mouseY);
        if (spellPickerOpen) drawSpellPicker(mouseX, mouseY);
    }

    private void drawEditorSurface() {
        int x = guiLeft, y = guiTop;
        drawRect(x + 1, y + 1, x + xSize - 1, y + ySize - 1, 0xFFE4E0D5);
        drawFrame(x, y, xSize, ySize, DEEP_INK);
        drawRect(x + 2, y + 2, x + xSize - 2, y + 22, DEEP_INK);
        drawRect(x + 2, y + 21, x + xSize - 2, y + 23, BRASS);
        drawRect(x + 7, y + 24, x + 150, y + 133, 0xFF394640);
        drawFrame(x + 7, y + 24, 143, 109, 0xFF7F8878);
        drawRect(x + 7, y + 136, x + 150, y + 180, 0xFFD5D0C4);
        drawFrame(x + 7, y + 136, 143, 44, 0xFFB4AA95);
        drawRect(x + 156, y + 24, x + xSize - 7, y + 180, 0xFFF0ECE2);
        drawFrame(x + 156, y + 24, xSize - 163, 156, 0xFFB5AD9B);
        drawRect(x + 153, y + 24, x + 154, y + 180, BRASS);
        for (int row = 0; row < 6; row++) {
            int lineY = y + 43 + row * 20;
            drawRect(x + 164, lineY, x + xSize - 12, lineY + 1, 0x33818A80);
        }
        drawRect(x + 8, y + 182, x + xSize - 8, y + 183, 0xFFB9B09F);
    }

    private void drawRuleRows(int mouseX, int mouseY) {
        if (ruleScroll == null) return;
        List<String> names = ruleScroll.getList();
        int x = guiLeft + 9, y = guiTop + 25;
        for (int row = 0; row < 7 && ruleListOffset + row < names.size(); row++) {
            int index = ruleListOffset + row;
            int rowY = y + row * 15;
            boolean selected = index == ruleScroll.selected;
            boolean hovered = mouseX >= x && mouseX < guiLeft + 147 && mouseY >= rowY && mouseY < rowY + 14;
            if (selected) {
                drawRect(x + 1, rowY, guiLeft + 147, rowY + 14, 0xFF73806D);
                drawRect(x + 1, rowY, x + 4, rowY + 14, BRASS);
            } else if (hovered) {
                drawRect(x + 1, rowY, guiLeft + 147, rowY + 14, 0xFF4B5950);
            }
            Spell spell = WizardrySpells.get(rules.get(index).spellId);
            if (spell != null) drawSpellIcon(spell, x + 3, rowY + 1, 12);
            String name = getFontRenderer().trimStringToWidth(names.get(index), 112);
            getFontRenderer().drawString(name, x + 19, rowY + 3,
                selected || hovered ? 0xFFFFFFFF : 0xFFE2E4DA);
        }
        if (names.size() > 7) {
            int trackX = guiLeft + 143, trackY = guiTop + 27;
            drawRect(trackX, trackY, trackX + 2, trackY + 104, 0xFF29332F);
            int thumbY = trackY + (int) (76.0 * ruleListOffset / (names.size() - 7));
            drawRect(trackX, thumbY, trackX + 2, thumbY + 28, BRASS);
        }
    }

    private void drawEditorLabels() {
        int x = guiLeft + 164, y = guiTop;
        drawHeaderLabel("cnpcmagic.provider.ebwizardry", guiLeft + 9, y + 8, 0xFFF0ECE2);
        drawHeaderLabel("cnpcmagic.gui.ruleSettings", x, y + 8, 0xFFF0ECE2);
        if (dirty || !status.isEmpty()) {
            String key = dirty ? "cnpcmagic.gui.unsaved" : status;
            getFontRenderer().drawString(I18n.translateToLocal(key), guiLeft + 320, y + 8,
                dirty ? 0xFFD8A29A : 0xFFC5D2BD);
        }

        MagicRule rule = currentRule();
        if (!synced) {
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.loading"), x, y + 38, INK);
            return;
        }
        if (rule == null) {
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.addHint"), x, y + 38, INK);
            drawRect(guiLeft + 74, y + 68, guiLeft + 76, y + 84, BRASS);
            drawRect(guiLeft + 67, y + 75, guiLeft + 83, y + 77, BRASS);
            return;
        }
        String[] labels = {
            "cnpcmagic.gui.enabled", "cnpcmagic.gui.independent", "cnpcmagic.gui.spell",
            "cnpcmagic.gui.level", rule.independent ? "cnpcmagic.gui.castsUnused" : "cnpcmagic.gui.casts",
            "cnpcmagic.gui.cooldown", "cnpcmagic.gui.range"
        };
        for (int row = 0; row < labels.length; row++) {
            getFontRenderer().drawString(I18n.translateToLocal(labels[row]), x, y + 25 + row * 20, INK);
        }
        Spell spell = WizardrySpells.get(rule.spellId);
        if (spell != null && spell.isContinuous) {
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.duration"), x, y + 165, INK);
        }
        getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.hint"), guiLeft + 10, y + 187, 0xFF626B62);
        drawRect(guiLeft + 137, y + 8, guiLeft + 140, y + 11, 0xFFE2C48D);
        drawRect(guiLeft + 138, y + 7, guiLeft + 139, y + 12, 0xFFE2C48D);
    }

    private void drawHeaderLabel(String key, int x, int y, int color) {
        getFontRenderer().drawString(I18n.translateToLocal(key), x, y, color);
    }

    private void drawStyledFields(int mouseX, int mouseY) {
        for (int id = 20; id <= 24; id++) {
            GuiNpcTextField field = getTextField(id);
            if (field == null) continue;
            drawRect(field.x - 3, field.y - 2, field.x + field.width + 3, field.y + field.height + 2, 0xFFCFCCBF);
            boolean editable = field.enabled;
            drawRect(field.x - 2, field.y - 1, field.x + field.width + 2, field.y + field.height + 1,
                editable ? PAPER : 0xFFDFDCD2);
            drawFrame(field.x - 2, field.y - 1, field.width + 4, field.height + 2,
                field.isFocused() ? SAGE : 0xFFB3AA96);
            if (editable) field.drawTextBox(mouseX, mouseY);
            else drawCenteredString(getFontRenderer(), "—", field.x + field.width / 2,
                field.y + (field.height - 8) / 2, 0xFF8B9187);
        }
    }

    private void drawSpellIcon(Spell spell, int x, int y, int size) {
        mc.getTextureManager().bindTexture(spell.getIcon());
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 128, 128, size, size, 128, 128);
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
    }

    private void drawStyledButtons(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (!(button instanceof GuiNpcButton)) continue;
            GuiNpcButton npcButton = (GuiNpcButton) button;
            int id = npcButton.field_146127_k;
            if (id < 10 || id > 20) continue;
            boolean active = (id == 10 || id == 12) && npcButton.getValue() == 1 || id == 19;
            boolean hovered = mouseX >= npcButton.x && mouseY >= npcButton.y
                && mouseX < npcButton.x + npcButton.width && mouseY < npcButton.y + npcButton.height;
            int fill = !npcButton.enabled ? 0xFFD2D5CD : active ? SAGE : hovered ? 0xFFF0F0E9 : PAPER;
            int ink = active && npcButton.enabled ? 0xFFFFFFFF : INK;
            drawRect(npcButton.x, npcButton.y,
                npcButton.x + npcButton.width, npcButton.y + npcButton.height, fill);
            drawFrame(npcButton.x, npcButton.y, npcButton.width, npcButton.height,
                active ? DEEP_INK : 0xFFADB5AA);
            if (active) drawRect(npcButton.x + 1, npcButton.y + 1,
                npcButton.x + 3, npcButton.y + npcButton.height - 1, BRASS);
            drawCenteredString(getFontRenderer(), npcButton.displayString,
                npcButton.x + npcButton.width / 2, npcButton.y + (npcButton.height - 8) / 2, ink);
        }
    }

    private void drawFrame(int x, int y, int frameWidth, int frameHeight, int color) {
        drawRect(x, y, x + frameWidth, y + 1, color);
        drawRect(x, y + frameHeight - 1, x + frameWidth, y + frameHeight, color);
        drawRect(x, y + 1, x + 1, y + frameHeight - 1, color);
        drawRect(x + frameWidth - 1, y + 1, x + frameWidth, y + frameHeight - 1, color);
    }

    @Override
    public void func_73876_c() {
        super.func_73876_c();
        if (spellPickerOpen) spellSearch.updateCursorCounter();
    }

    private GuiNpcTextField numberField(int id, int x, int y, int value, int min, int max, int def) {
        GuiNpcTextField field = new GuiNpcTextField(id, this, x, y, 52, 18, Integer.toString(value));
        field.numbersOnly = true;
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(INK);
        field.setDisabledTextColour(SAGE);
        field.setMinMaxDefault(min, max, def);
        return field;
    }

    private MagicRule currentRule() {
        return selectedRule < 0 || selectedRule >= rules.size() ? null : rules.get(selectedRule);
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
                if (rule != null) {
                    rule.enabled = button.getValue() == 1;
                    markDirty();
                }
                break;
            case 11:
                openSpellPicker(rule);
                break;
            case 12:
                if (rule != null) {
                    rule.independent = button.getValue() == 1;
                    markDirty();
                    func_73866_w_();
                }
                break;
            case 13:
                if (rules.size() < MagicDataStore.MAX_RULES) {
                    rules.add(new MagicRule());
                    selectedRule = rules.size() - 1;
                    markDirty();
                    func_73866_w_();
                    openSpellPicker(currentRule());
                }
                break;
            case 14:
                if (rule != null) {
                    rules.remove(selectedRule);
                    selectedRule = Math.max(0, selectedRule - 1);
                    markDirty();
                    func_73866_w_();
                }
                break;
            case 15:
                if (rule != null) {
                    rule.spellId = "";
                    rule.enabled = false;
                    markDirty();
                    func_73866_w_();
                }
                break;
            case 16:
                if (rule != null && rules.size() < MagicDataStore.MAX_RULES) {
                    rules.add(selectedRule + 1, MagicRule.read(rule.write()));
                    selectedRule++;
                    markDirty();
                    func_73866_w_();
                }
                break;
            case 17:
                moveRule(-1);
                break;
            case 18:
                moveRule(1);
                break;
            case 19:
                save();
                break;
            case 20:
                if (rule != null && rule.spellId != null && !rule.spellId.isEmpty()) {
                    applyFields();
                    NetworkHandler.CHANNEL.sendToServer(new C2SPreviewStart(
                        npc.getUniqueID(), rule.spellId, rule.level));
                    status = "cnpcmagic.preview.starting";
                }
                break;
            default:
                break;
        }
    }

    private void moveRule(int offset) {
        int destination = selectedRule + offset;
        if (destination < 0 || destination >= rules.size()) return;
        MagicRule rule = rules.remove(selectedRule);
        rules.add(destination, rule);
        selectedRule = destination;
        markDirty();
        func_73866_w_();
    }

    private void markDirty() {
        dirty = true;
        status = "";
    }

    private void openSpellPicker(MagicRule rule) {
        if (rule == null) return;
        GuiNpcTextField.unfocus();
        applyFields();
        spellPickerOpen = true;
        spellPickerRule = rule;
        pickerSpells.clear();
        pickerSpells.addAll(WizardrySpells.supported(npc));
        pickerSelectedId = rule.spellId == null ? "" : rule.spellId;
        pickerScroll = 0;
        spellSearch = new GuiTextField(90, getFontRenderer(), pickerX() + 8, pickerY() + 30, 146, 18);
        spellSearch.setMaxStringLength(128);
        spellSearch.setFocused(true);
        spellSearch.setEnableBackgroundDrawing(false);
        spellSearch.setTextColor(INK);
        refreshPickerResults();
    }

    private int pickerX() { return (width - 320) / 2; }
    private int pickerY() { return (height - 176) / 2; }

    private void refreshPickerResults() {
        String query = spellSearch.getText().trim().toLowerCase(Locale.ROOT);
        pickerMatches.clear();
        for (Spell spell : pickerSpells) {
            String id = spell.getRegistryName().toString();
            if ((spell.getDisplayName() + " " + id).toLowerCase(Locale.ROOT).contains(query)) {
                pickerMatches.add(spell);
            }
        }
        if (pickerMatches.stream().noneMatch(spell -> spell.getRegistryName().toString().equals(pickerSelectedId))) {
            pickerSelectedId = pickerMatches.isEmpty() ? "" : pickerMatches.get(0).getRegistryName().toString();
        }
        pickerScroll = MathHelper.clamp(pickerScroll, 0, Math.max(0, pickerMatches.size() - 5));
    }

    private Spell selectedPickerSpell() {
        for (Spell spell : pickerMatches) {
            if (spell.getRegistryName().toString().equals(pickerSelectedId)) return spell;
        }
        return null;
    }

    private void drawSpellPicker(int mouseX, int mouseY) {
        int x = pickerX(), y = pickerY();
        drawRect(0, 0, width, height, 0xA019211F);
        drawRect(x, y, x + 320, y + 176, 0xFFE4E0D5);
        drawFrame(x, y, 320, 176, DEEP_INK);
        drawRect(x + 1, y + 1, x + 319, y + 22, DEEP_INK);
        drawRect(x + 1, y + 21, x + 319, y + 23, BRASS);
        getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.selectSpell"), x + 8, y + 8, 0xFFF0ECE2);
        drawRect(x + 7, y + 29, x + 155, y + 49, 0xFFCFCCBF);
        drawRect(x + 8, y + 30, x + 154, y + 48, PAPER);
        spellSearch.drawTextBox();

        int listX = x + 8, listY = y + 53, listW = 146, rowH = 18;
        drawRect(listX, listY, listX + listW, listY + rowH * 5, 0xFF394640);
        for (int row = 0; row < 5; row++) {
            int index = pickerScroll + row;
            int rowY = listY + row * rowH;
            if (index >= pickerMatches.size()) break;
            Spell spell = pickerMatches.get(index);
            boolean selected = spell.getRegistryName().toString().equals(pickerSelectedId);
            if (!selected && row % 2 == 1) {
                drawRect(listX + 1, rowY + 1, listX + listW - 1, rowY + rowH - 1, 0xFF414E46);
            }
            if (selected) drawRect(listX + 1, rowY + 1, listX + listW - 1, rowY + rowH - 1, SAGE);
            if (selected) drawRect(listX + 1, rowY + 1, listX + 4, rowY + rowH - 1, BRASS);
            drawSpellIcon(spell, listX + 2, rowY + 2, 14);
            String name = getFontRenderer().trimStringToWidth(spell.getDisplayName(), listW - 25);
            getFontRenderer().drawString(name, listX + 20, rowY + 5, 0xFFF0ECE2);
        }
        if (pickerMatches.isEmpty()) {
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.noResults"), listX + 4, listY + 5, 0xFFD9DCD4);
        }

        int infoX = x + 164, infoW = 148;
        drawRect(x + 160, y + 28, x + 312, y + 149, 0xFFF0ECE2);
        drawFrame(x + 160, y + 28, 152, 121, 0xFFB5AD9B);
        drawRect(x + 161, y + 46, x + 311, y + 47, 0xFFAD8954);
        getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.spellInfo"), infoX, y + 33, SAGE);
        Spell spell = selectedPickerSpell();
        if (spell != null) {
            drawSpellIcon(spell, infoX + 1, y + 50, 20);
            getFontRenderer().drawString(getFontRenderer().trimStringToWidth(spell.getDisplayName(), 116),
                infoX + 25, y + 51, INK);
            getFontRenderer().drawString(getFontRenderer().trimStringToWidth(spell.getRegistryName().toString(), 116),
                infoX + 25, y + 63, 0xFF69766E);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoTier") + ": " + spell.getTier().getDisplayName(), infoX + 3, y + 74, INK);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoElement") + ": " + spell.getElement().getDisplayName(), infoX + 3, y + 83, INK);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoType") + ": " + spell.getType().getDisplayName(), infoX + 3, y + 92, INK);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoCost") + ": " + spell.getCost(), infoX + 3, y + 101, INK);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoCharge") + ": " + spell.getChargeup(), infoX + 3, y + 110, INK);
            getFontRenderer().drawString(I18n.translateToLocal("cnpcmagic.gui.infoCooldown") + ": " + spell.getCooldown(), infoX + 3, y + 119, INK);
            List<String> description = getFontRenderer().listFormattedStringToWidth(spell.getDescription(), infoW - 6);
            for (int i = 0; i < Math.min(2, description.size()); i++) {
                getFontRenderer().drawString(description.get(i), infoX + 3, y + 130 + i * 9, 0xFF69766E);
            }
        }

        drawRect(x + 8, y + 151, x + 72, y + 169, 0xFFD1D5CC);
        drawFrame(x + 8, y + 151, 64, 18, 0xFFB4BBB0);
        drawRect(x + 248, y + 151, x + 312, y + 169, spell == null ? 0xFFD1D5CC : SAGE);
        drawFrame(x + 248, y + 151, 64, 18, spell == null ? 0xFFB4BBB0 : DEEP_INK);
        if (spell != null) drawRect(x + 249, y + 151, x + 251, y + 169, BRASS);
        drawCenteredString(getFontRenderer(), I18n.translateToLocal("cnpcmagic.gui.cancel"), x + 40, y + 156, INK);
        drawCenteredString(getFontRenderer(), I18n.translateToLocal("cnpcmagic.gui.select"), x + 280, y + 156,
            spell == null ? 0xFF777777 : 0xFFFFFFFF);
    }

    private void finishSpellPicker(boolean accept) {
        if (accept && spellPickerRule != null && selectedPickerSpell() != null) {
            spellPickerRule.spellId = pickerSelectedId;
            spellPickerRule.enabled = true;
            markDirty();
        }
        spellPickerOpen = false;
        spellPickerRule = null;
        spellSearch.setFocused(false);
        spellSearch = null;
        if (accept) func_73866_w_();
    }

    @Override
    public void func_73864_a(int mouseX, int mouseY, int button) {
        if (!spellPickerOpen) {
            super.func_73864_a(mouseX, mouseY, button);
            return;
        }
        int x = pickerX(), y = pickerY();
        spellSearch.mouseClicked(mouseX, mouseY, button);
        if (mouseX < x + 8 || mouseX >= x + 154 || mouseY < y + 30 || mouseY >= y + 48) {
            spellSearch.setFocused(false);
        }
        if (button != 0) return;
        int listX = x + 8, listY = y + 53, listW = 146, rowH = 18;
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + rowH * 5) {
            int index = pickerScroll + (mouseY - listY) / rowH;
            if (index < pickerMatches.size()) pickerSelectedId = pickerMatches.get(index).getRegistryName().toString();
        } else if (mouseY >= y + 151 && mouseY < y + 169) {
            if (mouseX >= x + 8 && mouseX < x + 72) finishSpellPicker(false);
            else if (mouseX >= x + 248 && mouseX < x + 312) finishSpellPicker(true);
        }
    }

    @Override
    public void func_73869_a(char typedChar, int keyCode) {
        if (!spellPickerOpen) {
            super.func_73869_a(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            finishSpellPicker(false);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            finishSpellPicker(true);
        } else if (spellSearch.isFocused()) {
            if (spellSearch.textboxKeyTyped(typedChar, keyCode)) {
                pickerScroll = 0;
                refreshPickerResults();
            }
        } else if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            int index = pickerIndex(pickerSelectedId) + (keyCode == Keyboard.KEY_UP ? -1 : 1);
            if (!pickerMatches.isEmpty()) {
                index = MathHelper.clamp(index, 0, pickerMatches.size() - 1);
                pickerSelectedId = pickerMatches.get(index).getRegistryName().toString();
                pickerScroll = MathHelper.clamp(pickerScroll, 0, Math.max(0, index - 4));
            }
        } else if (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT) {
            pickerScroll = MathHelper.clamp(pickerScroll + (keyCode == Keyboard.KEY_PRIOR ? -5 : 5),
                0, Math.max(0, pickerMatches.size() - 5));
        }
    }

    private int pickerIndex(String id) {
        for (int i = 0; i < pickerMatches.size(); i++) {
            if (pickerMatches.get(i).getRegistryName().toString().equals(id)) return i;
        }
        return 0;
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        if (spellPickerOpen && Mouse.getEventDWheel() != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (mouseX >= pickerX() + 8 && mouseX < pickerX() + 154
                && mouseY >= pickerY() + 53 && mouseY < pickerY() + 143) {
                pickerScroll = MathHelper.clamp(pickerScroll + (Mouse.getEventDWheel() > 0 ? -3 : 3),
                    0, Math.max(0, pickerMatches.size() - 5));
            }
            return;
        }
        super.handleMouseInput();
    }

    @Override
    public void unFocused(GuiNpcTextField field) {
        MagicRule rule = currentRule();
        if (rule == null) return;
        int previous;
        switch (field.getId()) {
            case 20:
                previous = rule.level;
                rule.level = readInt(field, 1, MagicRule.MAX_LEVEL, 1);
                if (previous != rule.level) markDirty();
                break;
            case 21:
                previous = rule.casts;
                rule.casts = readInt(field, 1, MagicRule.MAX_CASTS, 3);
                if (previous != rule.casts) markDirty();
                break;
            case 22:
                previous = rule.cooldownTicks;
                rule.cooldownTicks = readInt(field, 0, 3600, 10) * 20;
                if (previous != rule.cooldownTicks) markDirty();
                break;
            case 23:
                previous = rule.range;
                rule.range = readInt(field, 1, MagicRule.MAX_RANGE, 6);
                if (previous != rule.range) markDirty();
                break;
            case 24:
                previous = rule.durationTicks;
                rule.durationTicks = readInt(field, 1, MagicRule.MAX_DURATION_TICKS, MagicRule.DEFAULT_DURATION_TICKS);
                if (previous != rule.durationTicks) markDirty();
                break;
            default:
                break;
        }
    }

    private int readInt(GuiNpcTextField field, int min, int max, int def) {
        return field.isInteger() ? MathHelper.clamp(field.getInteger(), min, max) : def;
    }

    private void applyFields() {
        for (int id : new int[]{20, 21, 22, 23, 24}) {
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
        dirty = false;
        status = "";
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
        if (!dirty) return;
        NBTTagCompound providers = new NBTTagCompound();
        providers.setTag(MagicDataStore.PROVIDER_ID, MagicDataStore.writeRules(rules));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("providers", providers);
        NetworkHandler.CHANNEL.sendToServer(new C2SMagicSave(root));
        dirty = false;
        status = "cnpcmagic.gui.saved";
    }
}

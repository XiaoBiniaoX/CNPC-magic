package top.cnpcmagic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.RuleConditions;

/** Drives the current same-screen editor and captures the actual game framebuffer. */
@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class EditorClientVerification {
    private static final String ECHO = "irons_spellbooks:echoing_strikes";
    private static int ticks;
    private static int stage;
    private static int at;
    private static boolean finished;
    private static String capture;
    private static GuiNpcMagic editor;
    private static EntityNPCInterface npc;
    private static int poseTick;
    private static int poseRun;
    private static boolean heldPose;
    private static int suppressedLoadingScreens;
    private static int suppressedProgressScreens;
    private static boolean loadingScreenEscaped;
    private static int wizardrySigils;
    private static final String[] WIZARDRY_PREVIEWS = {"fire_sigil", "detonate", "enrage", "entrapment", "heal", "frost_ray", "cobwebs", "ice_spikes", "arcane_lock", "font_of_mana", "frost_sigil"};
    private static int wizardryIndex;
    private static java.util.UUID wizardryActor;
    private static net.minecraft.client.multiplayer.ClientLevel originalLevel;
    private static net.minecraft.client.player.LocalPlayer originalPlayer;
    private static net.minecraft.world.phys.Vec3 originalPosition;
    private static float originalYaw, originalPitch;
    private static net.minecraft.world.level.GameType originalMode;
    private static int stationaryChecks;

    @SubscribeEvent
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (Boolean.getBoolean("cnpcmagic.verify.wizardryEditor") && event.getLevel().isClientSide()
            && event.getEntity().getClass().getName().equals("com.binaris.wizardry.content.entity.construct.FireSigilConstruct")) {
            wizardrySigils++;
            CNPCMagic.LOGGER.info("WIZARDRY CLIENT native FireSigilConstruct observed count={} position={}",
                wizardrySigils, event.getEntity().position());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void opening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (!Boolean.getBoolean("cnpcmagic.verify.editor")
            || !MagicEditor.isPreviewTransitionActive()
            || !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || event.getNewScreen() instanceof net.minecraft.client.gui.screens.ProgressScreen)) return;
        if (event.isCanceled()) {
            if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) suppressedLoadingScreens++;
            else suppressedProgressScreens++;
        }
        else loadingScreenEscaped = true;
    }

    public static void applied(net.minecraft.world.entity.LivingEntity entity,
                               net.minecraft.client.model.HumanoidModel<?> model) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("cnpcmagic.verify.editor") || editor == null || editor.picker == null
            || !editor.picker.active || mc.level == null
            || top.cnpcmagic.client.PreviewScene.instance() == null
            || !entity.getName().getString().equals("Preview") || Math.abs(model.rightArm.xRot) < 0.5) return;
        if (poseTick == ticks) return;
        poseRun = poseTick == ticks - 1 ? poseRun + 1 : 1;
        poseTick = ticks;
        if (poseRun >= 6) heldPose = true;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    private static void press(Screen screen, String key) {
        String message = EditorScreen.text(key);
        Button action = screen.children().stream()
            .filter(child -> child instanceof Button button && button.getMessage().getString().equals(message))
            .map(child -> (Button) child).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing button: " + key));
        check(action.active && action.visible, "active button " + key);
        double x = action.getX() + action.getWidth() / 2.0;
        double y = action.getY() + action.getHeight() / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void layout(EditorScreen screen) {
        var language = net.minecraft.locale.Language.getInstance();
        for (var label : screen.labels) {
            if (label.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)
                check(language.has(text.getKey()), "Missing label translation: " + text.getKey());
        }
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            check(widget.getX() >= 0 && widget.getY() >= 0
                && widget.getX() + widget.getWidth() <= screen.width
                && widget.getY() + widget.getHeight() <= screen.height,
                "widget outside screen at " + widget.getX() + "," + widget.getY());
            if (widget.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)
                check(language.has(text.getKey()), "Missing widget translation: " + text.getKey());
        }
    }

    private static void shot(String name, Screen screen) {
        if (screen instanceof EditorScreen editorScreen) layout(editorScreen);
        capture = name + ".png";
    }

    private static EditorScreen.EditorButton conditionButton(GuiNpcMagic screen, RuleConditions.Type type) {
        int row = screen.detail.conditionY + (type.ordinal() - screen.detail.firstCondition) * 20;
        return screen.children().stream()
            .filter(child -> child instanceof EditorScreen.EditorButton button
                && button.getX() == screen.detail.conditionX && button.getY() == row)
            .map(child -> (EditorScreen.EditorButton) child).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing condition button " + type));
    }

    private static void toggleCondition(RuleConditions.Type type) {
        EditorScreen.EditorButton button = conditionButton(editor, type);
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        editor.mouseClicked(x, y, 0);
        editor.mouseReleased(x, y, 0);
    }

    private static EditorScreen.Rows rows(Screen screen) {
        return screen.children().stream()
            .filter(child -> child instanceof EditorScreen.Rows)
            .map(child -> (EditorScreen.Rows) child)
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing spell rows"));
    }

    private static void chooseRow(Screen screen, int index) {
        EditorScreen.Rows rows = rows(screen);
        int visible = index - rows.offset;
        check(index >= 0 && visible >= 0 && visible < rows.capacity(), "Spell is outside visible rows");
        double x = rows.getX() + 12;
        double y = rows.getY() + 2 + visible * 22 + 10;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("cnpcmagic.verify.editor") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("cnpcmagic.verify.hotfix") && mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
            && mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())) {
            CNPCMagic.LOGGER.info("HOTFIX isolated copied world: accepting experimental-dimension confirmation");
            mc.screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().getString().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED.getString()))
                .map(Button.class::cast).findFirst().orElseThrow().onPress();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        ticks++;
        try {
            if (originalLevel != null) {
                check(mc.level == originalLevel && mc.player == originalPlayer, "preview never replaces real level/player");
                check(mc.player.position().distanceToSqr(originalPosition) < 0.0001, "preview never moves real player");
                check(Math.abs(mc.player.getYRot() - originalYaw) < 0.01 && Math.abs(mc.player.getXRot() - originalPitch) < 0.01,
                    "preview never rotates real player");
                check(mc.gameMode.getPlayerMode() == originalMode, "preview never changes real game mode");
                stationaryChecks++;
            }
            check(!loadingScreenEscaped, "preview loading screen was cancelled before display");
            if (ticks > (Boolean.getBoolean("cnpcmagic.verify.wizardryEditor") ? 5000 : 1800))
                throw new IllegalStateException("Editor screenshot fixture timed out at stage " + stage + " spell=" + WIZARDRY_PREVIEWS[wizardryIndex]);
            if (ticks < 160) return;
            if (ticks - at < 8) return;
            if (Boolean.getBoolean("cnpcmagic.verify.wizardryEditor") && stage >= 2) {
                switch (stage) {
                    case 2 -> {
                        press(editor, "cnpcmagic.provider.ebwizardry");
                        press(editor, "addSpell");
                        var search = (EditBox)editor.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                        search.setValue("ebwizardry:fire_sigil");
                        chooseRow(editor, 0);
                    }
                    case 3, 5 -> {
                        if (editor.picker == null || !editor.picker.active
                            || top.cnpcmagic.client.PreviewScene.instance() == null) return;
                        check(mc.screen == editor && MagicEditor.isPreviewTransitionActive(), "Wizardry preview retains editor");
                        String selected = "ebwizardry:" + WIZARDRY_PREVIEWS[wizardryIndex];
                        check(editor.picker.selected.equals(selected) && editor.picker.level.equals("1")
                            && editor.picker.hasProperties(), "Wizardry selected spell and properties retained");
                        var actors = java.util.stream.StreamSupport.stream(top.cnpcmagic.client.PreviewScene.instance().entitiesForRendering().spliterator(), false).toList();
                        var caster = actors.stream().filter(entity -> entity instanceof EntityNPCInterface && entity.getName().getString().equals("Preview"))
                            .map(entity -> (EntityNPCInterface) entity).findFirst().orElse(null);
                        var target = actors.stream().filter(entity -> entity instanceof net.minecraft.world.entity.Mob && entity.getName().getString().equals("Target"))
                            .map(entity -> (net.minecraft.world.entity.Mob) entity).findFirst().orElse(null);
                        if (caster == null || target == null || caster.getUUID().equals(wizardryActor) || ticks - at < 45) return;
                        boolean effect = switch (WIZARDRY_PREVIEWS[wizardryIndex]) {
                            case "entrapment" -> target.getVehicle() != null && target.getVehicle().getClass().getSimpleName().equals("BubbleConstruct");
                            case "heal" -> caster.getHealth() > caster.getMaxHealth() / 2;
                            case "enrage", "arcane_lock", "font_of_mana" -> true; // Launcher additionally requires each native server-side effect twice.
                            case "cobwebs" -> java.util.stream.StreamSupport.stream(net.minecraft.core.BlockPos.betweenClosed(
                                target.blockPosition().offset(-2, -1, -2), target.blockPosition().offset(2, 2, 2)).spliterator(), false)
                                .anyMatch(pos -> new net.minecraft.resources.ResourceLocation("ebwizardry:vanishing_cobweb").equals(
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(top.cnpcmagic.client.PreviewScene.instance().getBlockState(pos).getBlock())));
                            default -> target.getHealth() < target.getMaxHealth();
                        };
                        if (!effect || !editor.picker.status.isEmpty()) return;
                        if (wizardryIndex == 0 && wizardrySigils < (stage == 3 ? 1 : 2)) return;
                        wizardryActor = caster.getUUID();
                        CNPCMagic.LOGGER.info("EDITOR CLIENT WIZARDRY PREVIEW spell={} replay={} actor={} targetHealth={} casterHealth={} vehicle={}",
                            selected, stage == 5, wizardryActor, target.getHealth(), caster.getHealth(), target.getVehicle());
                        shot(String.format("wizardry-%02d-%s-%s", wizardryIndex, WIZARDRY_PREVIEWS[wizardryIndex], stage == 3 ? "initial" : "replay"), editor);
                    }
                    case 4 -> press(editor, "replay");
                    case 6 -> {
                        if (wizardryIndex + 1 < WIZARDRY_PREVIEWS.length) {
                            wizardryIndex++;
                            var search = (EditBox)editor.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                            search.setValue("ebwizardry:" + WIZARDRY_PREVIEWS[wizardryIndex]);
                            chooseRow(editor, 0);
                            stage = 2;
                        } else press(editor, "addSpell");
                    }
                    case 7 -> {
                        if (editor.picker != null || editor.saving) return;
                        check(mc.screen == editor && !MagicEditor.isPreviewTransitionActive(), "Wizardry returns to editor and clears transition");
                        check(suppressedLoadingScreens == 0 && suppressedProgressScreens == 0, "detached preview needs no loading screens");
                        check(top.cnpcmagic.client.PreviewScene.instance() == null, "preview scene released on close");
                        for (Screen screen : java.util.List.of(new net.minecraft.client.gui.screens.ReceivingLevelScreen(),
                            new net.minecraft.client.gui.screens.ProgressScreen(true))) {
                            check(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                                new net.minecraftforge.client.event.ScreenEvent.Opening(editor, screen)),
                                "ordinary loading screens are available after Wizardry preview");
                        }
                        shot("04-wizardry-return", editor);
                        press(editor, "save");
                    }
                    case 8 -> {
                        if (!editor.ready() || !editor.status.equals(EditorScreen.text("saved"))) return;
                        editor = new GuiNpcMagic(npc);
                        mc.setScreen(editor);
                    }
                    case 9 -> {
                        if (!editor.ready()) return;
                        check(editor.rules.getOrDefault("ebwizardry", java.util.List.of()).stream()
                            .anyMatch(rule -> rule.spellId.equals("ebwizardry:" + WIZARDRY_PREVIEWS[wizardryIndex]) && rule.level == 1),
                            "Wizardry selected draft survives save and reopen");
                        shot("05-wizardry-reopened", editor);
                    }
                    case 10 -> {
                        CNPCMagic.LOGGER.info("HOTFIX PLAYER UNCHANGED checks={} dimension={} position={} mode={}", stationaryChecks,
                            mc.level.dimension().location(), mc.player.position(), mc.gameMode.getPlayerMode());
                        CNPCMagic.LOGGER.info("EDITOR CLIENT PASS COMPLETE: WIZARDRY {} spells real preview, replay, switching, return, draft save and reopen; nativeSigils={}, suppressedLoadingScreens={}, suppressedProgressScreens={}",
                            WIZARDRY_PREVIEWS.length, wizardrySigils, suppressedLoadingScreens, suppressedProgressScreens);
                        finished = true;
                        mc.stop();
                    }
                }
                stage++;
                at = ticks;
                return;
            }
            switch (stage) {
                case 0 -> {
                    for (var entity : mc.level.entitiesForRendering())
                        if (entity instanceof EntityNPCInterface candidate
                            && candidate.getName().getString().equals("Magic editor verification")) npc = candidate;
                    if (npc == null) return;
                    editor = new GuiNpcMagic(npc);
                    mc.setScreen(editor);
                }
                case 1 -> {
                    if (!editor.ready()) return;
                    originalLevel = mc.level; originalPlayer = mc.player; originalPosition = mc.player.position();
                    originalYaw = mc.player.getYRot(); originalPitch = mc.player.getXRot(); originalMode = mc.gameMode.getPlayerMode();
                    shot("01-main-wide", editor);
                }
                case 2 -> {
                    check(editor.detail != null, "rule detail is embedded in main editor");
                    press(editor, "disabledRule");
                    check(!editor.detail.rule.enabled, "disable rule widget");
                    press(editor, "enabledRule");
                    check(editor.detail.rule.enabled, "enable rule widget");
                    shot("02-conditions-wide", editor);
                }
                case 3 -> {
                    var conditions = editor.detail.rule.conditions;
                    for (RuleConditions.Type type : RuleConditions.Type.values()) {
                        if (type == RuleConditions.Type.PREVIOUS_SPELL) {
                            if (!conditions.enabled.contains(type)) toggleCondition(type);
                            check(conditions.enabled.contains(type), "condition on " + type);
                        } else {
                            toggleCondition(type);
                            check(conditions.enabled.contains(type), "condition on " + type);
                            toggleCondition(type);
                            check(!conditions.enabled.contains(type), "condition off " + type);
                        }
                    }
                    press(editor, "matchAny");
                    check(conditions.any, "ANY widget");
                    press(editor, "matchAll");
                    check(!conditions.any, "ALL widget");
                    check(conditions.enabled.equals(java.util.EnumSet.of(RuleConditions.Type.PREVIOUS_SPELL)),
                        "independent condition switches");
                    shot("03-combo-only-wide", editor);
                }
                case 4 -> {
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                }
                case 5 -> {
                    check(editor.width < 640, "narrow editor layout");
                    editor.mouseScrolled(editor.detail.conditionX + 4, editor.detail.conditionY + 4, -1);
                    check(editor.detail.firstCondition > 0, "condition list scroll");
                    shot("04-conditions-narrow", editor);
                }
                case 6 -> {
                    press(editor, "addSpell");
                    check(editor.picker != null && mc.screen == editor, "embedded spell picker");
                    shot("05-spell-picker", editor);
                }
                case 7 -> {
                    chooseRow(editor, rows(editor).names.indexOf(editor.name("cnpcmagic:channel_beam")));
                    check(editor.picker.selected.equals("cnpcmagic:channel_beam"), "spell selection");
                    check(editor.picker.hasProperties(), "spell properties");
                }
                case 8 -> {
                    if (editor.picker == null || !editor.picker.active
                        || top.cnpcmagic.client.PreviewScene.instance() == null) return;
                    check(MagicEditor.isPreviewTransitionActive(), "preview transition state during initial transfer");
                    check(mc.screen == editor, "editor remains visible during initial preview transfer: "
                        + (mc.screen == null ? "null" : mc.screen.getClass().getName()));
                    check(editor.picker.selected.equals("cnpcmagic:channel_beam")
                        && editor.picker.level.equals("1") && editor.picker.hasProperties(),
                        "selected spell draft survives initial preview transfer");
                    boolean targetDamaged = java.util.stream.StreamSupport.stream(top.cnpcmagic.client.PreviewScene.instance().entitiesForRendering().spliterator(), false)
                        .anyMatch(entity -> entity instanceof net.minecraft.world.entity.LivingEntity living
                            && living.getName().getString().equals("Target") && living.getHealth() < living.getMaxHealth());
                    if (!heldPose || !targetDamaged) return;
                    check(heldPose, "preview NPC held casting pose for six consecutive ticks");
                    check(targetDamaged, "real preview target damage");
                    shot("06-real-preview-casting", editor);
                }
                case 9 -> {
                    press(editor, "replay");
                }
                case 10 -> {
                    if (editor.picker == null || !editor.picker.active
                        || top.cnpcmagic.client.PreviewScene.instance() == null || ticks - at < 55) return;
                    check(mc.screen == editor, "editor remains visible during preview replay");
                    check(editor.picker.selected.equals("cnpcmagic:channel_beam")
                        && editor.picker.level.equals("1") && editor.picker.hasProperties(),
                        "selected spell draft survives preview replay");
                    shot("07-real-preview-replay", editor);
                }
                case 11 -> {
                    press(editor, "addSpell");
                }
                case 12 -> {
                    if (editor.picker != null || editor.saving) return;
                    press(editor, "sequence");
                    check(mc.screen instanceof SequenceScreen, "cast order screen");
                    var rows = rows(mc.screen);
                    int goety = editor.rules.containsKey("goety") ? 3 : 1;
                    check(rows.names.size() == (goety == 3 ? 4 : 3), "independent rule stays out of the ordered list");
                    chooseRow(mc.screen, goety);
                    for (int i = 0; i < goety; i++) press(mc.screen, "moveUp");
                    shot("11-cast-order", mc.screen);
                }
                case 13 -> {
                    press(mc.screen, "applySequence");
                    press(editor, "save");
                }
                case 14 -> {
                    if (!editor.ready() || !editor.status.equals(EditorScreen.text("saved"))) return;
                    shot("08-main-narrow-saved", editor);
                }
                case 15 -> {
                    editor = new GuiNpcMagic(npc);
                    mc.setScreen(editor);
                }
                case 16 -> {
                    if (!editor.ready()) return;
                    var entries = editor.rules.get("cnpcmagic");
                    check(entries.size() >= 3, "saved picker rule");
                    check(entries.get(0).conditions.enabled.equals(java.util.EnumSet.of(RuleConditions.Type.PREVIOUS_SPELL)),
                        "saved condition switches");
                    check(entries.get(1).enabled && entries.get(1).spellId.equals("cnpcmagic:magic_missile"),
                        "saved neighbouring rule");
                    check(entries.stream().anyMatch(rule -> rule.spellId.equals("cnpcmagic:channel_beam")),
                         "saved selected spell");
                    if (editor.rules.containsKey("goety")) {
                        check(editor.rules.get("goety").get(0).sequence == 0 && entries.get(0).sequence == 1,
                            "cross-provider order survives save and reopen");
                    } else check(entries.get(1).sequence == 0 && entries.get(0).sequence == 1, "order survives save and reopen");
                    if (editor.rules.containsKey("irons_spellbooks")) {
                        var rule = editor.rules.get("irons_spellbooks").get(0);
                        check(rule.independent && rule.sequence == 70, "independent rule unchanged when applying order");
                    }
                    shot("09-main-narrow-reopened", editor);
                }
                case 17 -> {
                    press(editor, "newSpell"); press(mc.screen, "delivery");
                    var definition = (DefinitionEditor)mc.screen;
                    var before = definition.draft.delivery;
                    press(definition, "delivery.RAY");
                    check(definition.draft.delivery != before, "delivery button updates same screen");
                    int selected = 0;
                    for (var child : definition.children()) if (child instanceof EditorScreen.EditorButton button
                        && (button.getY() == 82 || button.getY() == 102) && button.selected) selected++;
                    check(selected == 1, "delivery buttons mutually exclusive");
                    shot("12-delivery-segments", definition);
                }
                case 18 -> {
                    press(mc.screen, "back"); press(mc.screen, "discard");
                    if (!editor.rules.containsKey("irons_spellbooks")) { stage = 21; break; }
                    press(editor, "cnpcmagic.provider.irons_spellbooks"); press(editor, "addSpell");
                    var search = (EditBox)editor.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                    search.setValue(ECHO);
                    chooseRow(editor, 0);
                }
                case 19 -> {
                    if (editor.picker == null || !editor.picker.active) return;
                    boolean echo = java.util.stream.StreamSupport.stream(top.cnpcmagic.client.PreviewScene.instance().entitiesForRendering().spliterator(), false)
                        .anyMatch(entity -> new net.minecraft.resources.ResourceLocation("irons_spellbooks:echoing_sword")
                            .equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
                    if (!echo) return;
                    shot("13-iron-native-echo", editor);
                }
                case 20 -> press(editor, "self");
                case 21 -> {
                    if (ticks - at < 35) return;
                    var actors = java.util.stream.StreamSupport.stream(top.cnpcmagic.client.PreviewScene.instance().entitiesForRendering().spliterator(), false).toList();
                    check(actors.stream().anyMatch(entity -> entity instanceof net.minecraft.world.entity.LivingEntity living
                        && living.getName().getString().equals("Target") && living.getHealth() == living.getMaxHealth()),
                        "self-mode preview does not strike the target");
                    press(editor, "back");
                }
                case 22 -> {
                    if (editor.picker != null) return;
                    check(!MagicEditor.isPreviewTransitionActive(), "preview transition state cleared after return");
                    check(mc.screen == editor, "editor restored after preview return");
                    check(suppressedLoadingScreens == 0 && suppressedProgressScreens == 0, "no preview loading screens or transitions");
                    for (Screen screen : java.util.List.of(new net.minecraft.client.gui.screens.ReceivingLevelScreen(),
                        new net.minecraft.client.gui.screens.ProgressScreen(true))) {
                        var opening = new net.minecraftforge.client.event.ScreenEvent.Opening(editor, screen);
                        check(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(opening),
                            "loading screens remain available outside preview");
                    }
                }
                case 23 -> {
                    mc.options.guiScale().set(2);
                    mc.resizeDisplay();
                    editor.onClose();
                }
                case 24 -> {
                    check(mc.screen instanceof noppes.npcs.client.gui.mainmenu.GuiNpcDisplay,
                        "upper-left return reaches Appearance");
                    shot("10-return-appearance", mc.screen);
                }
                case 25 -> {
                    CNPCMagic.LOGGER.info("HOTFIX PLAYER UNCHANGED checks={} dimension={} position={} mode={}", stationaryChecks,
                        mc.level.dimension().location(), mc.player.position(), mc.gameMode.getPlayerMode());
                    CNPCMagic.LOGGER.info("EDITOR CLIENT PASS COMPLETE: same-screen conditions, delivery segments, cast order save and reopen, real preview, suppressedLoadingScreens={}, suppressedProgressScreens={}", suppressedLoadingScreens, suppressedProgressScreens);
                    finished = true;
                    mc.stop();
                }
            }
            stage++;
            at = ticks;
        } catch (RuntimeException | LinkageError error) {
            finished = true;
            CNPCMagic.LOGGER.error("EDITOR CLIENT FAIL stage=" + stage, error);
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (capture == null || event.phase != TickEvent.Phase.END) return;
        String name = capture;
        capture = null;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
            message -> CNPCMagic.LOGGER.info("EDITOR CLIENT SCREENSHOT {}", name));
    }
}

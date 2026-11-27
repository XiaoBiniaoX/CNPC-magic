package top.cnpcmagic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.RuleConditions;

/** Drives the current same-screen editor and captures the actual game framebuffer. */
@net.neoforged.fml.common.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class EditorClientVerification {
    private static final String ECHO = "irons_spellbooks:echoing_strikes";
    private static int ticks;
    private static int startupTicks;
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
    private static java.util.UUID ironActor;
    private static java.util.UUID ironTarget;
    private static net.minecraft.world.phys.Vec3 ironTargetOrigin;
    private static net.minecraft.client.multiplayer.ClientLevel originalLevel;
    private static net.minecraft.client.player.LocalPlayer originalPlayer;
    private static net.minecraft.world.phys.Vec3 originalPosition;
    private static float originalYaw, originalPitch;
    private static net.minecraft.world.level.GameType originalMode;
    private static int stationaryChecks;
    private static final java.util.List<String> IRON_PREVIEWS = new java.util.ArrayList<>(java.util.List.of(
        "gtbcs_geomancy_plus:weeping_cluster", "gtbcs_geomancy_plus:solar_beam",
        "gtbcs_geomancy_plus:solar_chorus", "iss_magicfromtheeast:jade_bullet", "irons_spellbooks:ray_of_siphoning"));
    private static int ironIndex;
    private static final java.util.Set<java.util.UUID> ironRendered = new java.util.HashSet<>();
    private static net.minecraft.world.entity.Entity ironConstruct;
    private static int ironSamples, ironLastAge;
    private static double ironMinY, ironMaxY;
    private static final String[] NATIVE_PREVIEWS = {"irons_spellbooks:fire_breath", "irons_spellbooks:ray_of_siphoning",
        "irons_spellbooks:chain_lightning", "goety:fire_breath_focus", "goety:prisma_beam_focus", "goety:water_jet_focus"};
    private static int nativeIndex;
    private static java.util.UUID nativeActor;
    private static final java.util.Set<java.util.UUID> goetyRendered = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> nativeBeamCaptured = new java.util.HashSet<>();
    private static final java.util.Set<String> nativeBeamFrames = new java.util.HashSet<>();

    public static void goetyRendered(net.minecraft.world.entity.LivingEntity entity, String spell) {
        if (Boolean.getBoolean("cnpcmagic.verify.nativeEditor") && goetyRendered.add(entity.getUUID()))
            CNPCMagic.LOGGER.info("GOETY CLIENT NPC {} beam renderer actor={}", spell, entity.getUUID());
        if (Boolean.getBoolean("cnpcmagic.verify.nativeEditor")) {
            int time = top.cnpcmagic.client.NpcAnimations.castingTime(entity, "goety:" + (spell.equals("prisma") ? "prisma_beam" : "water_jet") + "_focus");
            int sample = time / 20;
            if (sample >= 1 && sample <= 3 && nativeBeamFrames.add(entity.getUUID() + ":" + sample)) {
                CNPCMagic.LOGGER.info("GOETY CLIENT {} sample={} time={}", spell, sample, time);
                shot(spell + "-" + (stage == 34 ? "replay-" : "initial-") + sample, editor);
            }
        }
    }

    public static void ironRendered(net.minecraft.world.entity.LivingEntity entity) {
        if ((Boolean.getBoolean("cnpcmagic.verify.ironEditor") || Boolean.getBoolean("cnpcmagic.verify.nativeEditor"))
                && ironRendered.add(entity.getUUID()))
            CNPCMagic.LOGGER.info("IRON CLIENT native beam renderer actor={}", entity.getUUID());
    }

    private static void nativeTick(Minecraft mc) {
        String id = nativeIndex < NATIVE_PREVIEWS.length ? NATIVE_PREVIEWS[nativeIndex] : "";
        switch (stage) {
            case 30 -> {
                press(editor, "cnpcmagic.provider." + id.substring(0, id.indexOf(':')));
                press(editor, "addSpell");
            }
            case 31 -> {
                var search = (EditBox)editor.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                search.setValue(id);
                check(rows(editor).names.size() == 1, "native spell found: " + id);
                chooseRow(editor, 0);
            }
            case 32, 34 -> {
                if (editor.picker.status.startsWith("!")) throw new IllegalStateException(editor.picker.status);
                var scene = top.cnpcmagic.client.PreviewScene.instance();
                if (!editor.picker.active || scene == null) return;
                var actors = java.util.stream.StreamSupport.stream(scene.entitiesForRendering().spliterator(), false).toList();
                var caster = actors.stream().filter(e -> e instanceof EntityNPCInterface && e.getName().getString().equals("Preview"))
                    .map(EntityNPCInterface.class::cast).findFirst().orElse(null);
                var target = actors.stream().filter(e -> e instanceof net.minecraft.world.entity.LivingEntity && e.getName().getString().equals("Target"))
                    .map(net.minecraft.world.entity.LivingEntity.class::cast).findFirst().orElse(null);
                if (caster == null || target == null || caster.getUUID().equals(nativeActor)) return;
                if (id.endsWith(":prisma_beam_focus")) {
                    if (!nativeBeamCaptured.contains(caster.getUUID())) {
                        if (!goetyRendered.contains(caster.getUUID())
                            || com.Polarice3.Goety.utils.MiscCapHelper.getClientTarget(caster) != target
                            || top.cnpcmagic.client.NpcAnimations.castingTime(caster, id) < 60) return;
                        nativeBeamCaptured.add(caster.getUUID());
                        shot("native-" + nativeIndex + (stage == 34 ? "-replay" : "-initial"), editor);
                    }
                    if (target.getHealth() >= target.getMaxHealth()) return;
                } else {
                    if (target.getHealth() >= target.getMaxHealth()) return;
                    if (id.endsWith(":fire_breath") || id.endsWith(":fire_breath_focus")) {
                        if (ticks - at < 45) return;
                        if (id.endsWith(":fire_breath")) {
                            var breath = actors.stream().filter(e -> e instanceof io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile)
                                .map(io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile.class::cast).findFirst().orElse(null);
                            check(breath != null && breath.getOwner() == caster, "preview native projectile owner restored");
                        }
                    }
                }
                if (id.endsWith(":ray_of_siphoning")) {
                    if (!ironRendered.contains(caster.getUUID())) return;
                    check(io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(caster).getAdditionalCastData()
                        instanceof io.redspace.ironsspellbooks.spells.CastingMobAimingData, "native beam has synchronized aiming data");
                }
                if (id.endsWith(":water_jet_focus") && (!goetyRendered.contains(caster.getUUID())
                    || com.Polarice3.Goety.utils.MiscCapHelper.getClientTarget(caster) != target
                    || top.cnpcmagic.client.NpcAnimations.castingTime(caster, id) < 60)) return;
                nativeActor = caster.getUUID();
                CNPCMagic.LOGGER.info("EDITOR CLIENT NATIVE id={} replay={} actor={} targetHealth={} entities={}",
                    id, stage == 34, nativeActor, target.getHealth(), actors.size());
                if (!id.endsWith(":prisma_beam_focus"))
                    shot("native-" + nativeIndex + (stage == 34 ? "-replay" : "-initial"), editor);
            }
            case 33 -> press(editor, "replay");
            case 35 -> {
                press(editor, "back");
                if (++nativeIndex < NATIVE_PREVIEWS.length) stage = 29;
            }
            case 36 -> {
                check(top.cnpcmagic.client.PreviewScene.instance() == null, "native preview scene released");
                CNPCMagic.LOGGER.info("EDITOR CLIENT PASS COMPLETE: native fire breath, siphoning beam, chain lightning, Goety fire breath/prisma/water jet, initial/replay, playerUnchangedChecks={}", stationaryChecks);
                finished = true; mc.stop();
            }
        }
        stage++; at = ticks;
    }

    private static void ironTick(Minecraft mc) {
        switch (stage) {
            case 2 -> {
                press(editor, "cnpcmagic.provider.irons_spellbooks"); press(editor, "addSpell");
                var p = editor.picker.provider;
                var spellIds = p.getSpellIds();
                for (String id : spellIds) check(top.cnpcmagic.client.provider.ClientMagicProviders.forSpell(id) == p, "client registered ownership " + id);
                IRON_PREVIEWS.removeIf(id -> !spellIds.contains(id));
                var updraft = top.cnpcmagic.compat.iron.IronAdapter.getSpell("zero_additions:updraft");
                if (net.neoforged.fml.ModList.get().isLoaded("zero_additions")) {
                    check(top.cnpcmagic.compat.iron.IronAdapter.isSupportedSpell(updraft), "zero_additions:updraft is enabled in Iron registry");
                    check(updraft.getSpellId().equals("zero_additions:updraft"), "updraft resolved from Iron spell registry");
                    check(p.getSpellIds().contains("zero_additions:updraft"), "updraft appears in registry-backed picker list");
                    check(top.cnpcmagic.client.provider.ClientMagicProviders.forSpell("zero_additions:updraft") == p,
                        "updraft routes to Iron client provider");
                    if (!IRON_PREVIEWS.contains("zero_additions:updraft")) IRON_PREVIEWS.add("zero_additions:updraft");
                }
                check(!IRON_PREVIEWS.isEmpty(), "at least one Iron preview spell is registered");
                for (var other : top.cnpcmagic.client.provider.ClientMagicProviders.all())
                    if (other != p) check(other.getSchools().isEmpty(), "school UI exclusive to Iron");
                check(!p.getSchools().isEmpty(), "registered Iron schools included");
                press(editor, "allSchools"); shot("iron-schools-open", editor);
            }
            case 3 -> {
                var ids = editor.picker.provider.getSpellIds();
                if (!ids.contains("iss_magicfromtheeast:jade_bullet") || !ids.contains("gtbcs_geomancy_plus:solar_beam")) {
                    CNPCMagic.LOGGER.info("IRON CLIENT optional addon school filter skipped; registered spells={}", ids.size());
                    break;
                }
                String school = editor.picker.provider.getSchoolId("iss_magicfromtheeast:jade_bullet");
                int i = rows(editor).names.indexOf(editor.picker.schools.get(school).getString());
                rows(editor).offset = Math.max(0, i - rows(editor).capacity() + 1); chooseRow(editor, i);
                check(editor.picker.school.equals(school), "native school chosen");
                check(rows(editor).names.contains(editor.name("iss_magicfromtheeast:jade_bullet")), "addon school contains own spell");
                check(!rows(editor).names.contains(editor.name("gtbcs_geomancy_plus:solar_beam")), "other school excluded");
                shot("iron-addon-school-filter", editor);
            }
            case 4 -> {
                ironConstruct = null; ironSamples = 0; ironTarget = null; ironTargetOrigin = null;
                if (!editor.picker.school.isEmpty()) {
                    String name = editor.picker.schools.get(editor.picker.school).getString();
                    editor.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(name))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress();
                    rows(editor).offset = 0; chooseRow(editor, 0);
                }
                var search = (EditBox)editor.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                search.setValue(IRON_PREVIEWS.get(ironIndex));
                check(rows(editor).names.size() == 1, "search finds registered addon spell"); chooseRow(editor, 0);
            }
            case 5, 7 -> {
                if (editor.picker.status.startsWith("!")) throw new IllegalStateException(editor.picker.status);
                if (!editor.picker.active || top.cnpcmagic.client.PreviewScene.instance() == null) return;
                var actors = java.util.stream.StreamSupport.stream(top.cnpcmagic.client.PreviewScene.instance().entitiesForRendering().spliterator(), false).toList();
                var caster = actors.stream().filter(e -> e instanceof EntityNPCInterface && e.getName().getString().equals("Preview"))
                    .map(EntityNPCInterface.class::cast).findFirst().orElse(null);
                var target = actors.stream().filter(e -> e instanceof net.minecraft.world.entity.Mob && e.getName().getString().equals("Target"))
                    .map(net.minecraft.world.entity.Mob.class::cast).findFirst().orElse(null);
                if (caster == null || target == null || caster.getUUID().equals(ironActor)) return;
                String id = IRON_PREVIEWS.get(ironIndex);
                if (!target.getUUID().equals(ironTarget)) {
                    ironTarget = target.getUUID();
                    ironTargetOrigin = target.position();
                }
                if (ironIndex == 0) {
                    var construct = actors.stream().filter(e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(e.getType()).toString().equals("gtbcs_geomancy_plus:crying_obsidian_cluster")).findFirst().orElse(null);
                    if (ironConstruct == null) {
                        if (construct == null) return;
                        ironConstruct = construct; ironMinY = ironMaxY = construct.getY();
                    }
                    check(construct == ironConstruct && !construct.isRemoved(), "native construct identity continuous");
                    check(ironSamples == 0 || construct.tickCount > ironLastAge, "native construct ticks between snapshots");
                    ironLastAge = construct.tickCount;
                    ironMinY = Math.min(ironMinY, construct.getY()); ironMaxY = Math.max(ironMaxY, construct.getY());
                    var floor = net.minecraft.core.BlockPos.containing(construct.getX(), 79, construct.getZ());
                    var collision = construct.level().getChunkForCollisions(floor.getX() >> 4, floor.getZ() >> 4);
                    check(collision != null && !collision.getBlockState(floor).isAir(), "preview platform participates in native collision");
                    check(construct.getY() >= 79.99 && ironMaxY - ironMinY < 0.08, "native construct stays on floor y=" + construct.getY());
                    if (++ironSamples == 13 || ironSamples == 29 || ironSamples == 47)
                        shot("iron-0-continuity-" + (stage == 7 ? "replay-" : "initial-") + ironSamples, editor);
                    if (ironSamples < 60) return;
                    CNPCMagic.LOGGER.info("IRON CLIENT CONTINUITY replay={} samples={} sameEntity=true yMin={} yMax={}",
                        stage == 7, ironSamples, ironMinY, ironMaxY);
                }
                if (ticks - at < 42) return;
                boolean effect = target.getHealth() < target.getMaxHealth() || actors.stream().anyMatch(e ->
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(id.substring(0, id.indexOf(':'))));
                effect |= target.position().distanceToSqr(ironTargetOrigin) > 0.25;
                if (!effect || (id.endsWith(":ray_of_siphoning") && !ironRendered.contains(caster.getUUID()))) return;
                ironActor = caster.getUUID();
                CNPCMagic.LOGGER.info("EDITOR CLIENT IRON PREVIEW id={} replay={} actor={} targetHealth={} entities={}",
                    id, stage == 7, caster.getUUID(), target.getHealth(), actors.size());
                shot("iron-" + ironIndex + (stage == 7 ? "-replay" : "-initial"), editor);
            }
            case 6 -> { ironConstruct = null; ironSamples = 0; ironTarget = null; ironTargetOrigin = null; press(editor, "replay"); }
            case 8 -> { if (++ironIndex < IRON_PREVIEWS.size()) stage = 3; else press(editor, "addSpell"); }
            case 9 -> { if (editor.picker != null || editor.saving) return; press(editor, "save"); }
            case 10 -> {
                if (!editor.ready() || !editor.status.equals(EditorScreen.text("saved"))) return;
                editor = new GuiNpcMagic(npc); mc.setScreen(editor);
            }
            case 11 -> {
                if (!editor.ready()) return;
                check(editor.rules.getOrDefault("irons_spellbooks", java.util.List.of()).stream()
                    .anyMatch(r -> r.spellId.equals(IRON_PREVIEWS.get(IRON_PREVIEWS.size() - 1))), "Iron draft saved and reopened");
                mc.options.guiScale().set(4); mc.resizeDisplay();
                press(editor, "cnpcmagic.provider.irons_spellbooks"); press(editor, "addSpell"); press(editor, "allSchools");
                shot("iron-schools-narrow", editor);
            }
            case 12 -> {
                check(top.cnpcmagic.client.PreviewScene.instance() == null, "detached scene released");
                CNPCMagic.LOGGER.info("HOTFIX PLAYER UNCHANGED checks={} dimension={} position={} mode={}", stationaryChecks,
                    mc.level.dimension().location(), mc.player.position(), mc.gameMode.getPlayerMode());
                CNPCMagic.LOGGER.info("EDITOR CLIENT PASS COMPLETE: IRON registered addons, native beams, schools, replay, draft save/reopen");
                finished = true; mc.stop();
            }
        }
        stage++; at = ticks;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void opening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
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
        check(index >= 0 && index < rows.names.size(), "Spell exists in picker");
        if (index < rows.offset || index >= rows.offset + rows.capacity())
            rows.offset = Math.max(0, index - rows.capacity() + 1);
        int visible = index - rows.offset;
        check(index >= 0 && visible >= 0 && visible < rows.capacity(), "Spell is outside visible rows");
        double x = rows.getX() + 12;
        double y = rows.getY() + 2 + visible * 22 + 10;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("cnpcmagic.verify.editor") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null && ++startupTicks > 2400) {
            CNPCMagic.LOGGER.error("EDITOR CLIENT FAIL startup screen={} title={}",
                mc.screen == null ? "null" : mc.screen.getClass().getName(),
                mc.screen == null ? "" : mc.screen.getTitle().getString());
            finished = true; mc.stop(); return;
        }
        if (Boolean.getBoolean("cnpcmagic.verify.hotfix") && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
            && mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())) {
            CNPCMagic.LOGGER.info("HOTFIX isolated copied world: accepting experimental-dimension confirmation");
            mc.screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString()))
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
            if (ticks > (Boolean.getBoolean("cnpcmagic.verify.nativeEditor") ? 4800 : 1800))
                throw new IllegalStateException("Editor screenshot fixture timed out at stage " + stage);
            if (ticks < 160) return;
            if (ticks - at < 8) return;
            if (Boolean.getBoolean("cnpcmagic.verify.ironEditor") && stage >= 2) { ironTick(mc); return; }
            if (Boolean.getBoolean("cnpcmagic.verify.nativeEditor") && stage >= 30) { nativeTick(mc); return; }
            switch (stage) {
                case 0 -> {
                    if (Boolean.getBoolean("cnpcmagic.verify.background")) {
                        long window = mc.getWindow().getWindow();
                        check(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_VISIBLE) == 0, "verification window stays hidden");
                        check(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == 0, "verification window never takes focus");
                        CNPCMagic.LOGGER.info("EDITOR CLIENT BACKGROUND visible=false focused=false language={}", mc.options.languageCode);
                    }
                    for (var entity : mc.level.entitiesForRendering())
                        if (entity instanceof EntityNPCInterface candidate
                            && candidate.getName().getString().equals("Magic editor verification")) npc = candidate;
                    if (npc == null) return;
                    editor = new GuiNpcMagic(npc);
                    mc.setScreen(editor);
                }
                case 1 -> {
                    if (editor.status.startsWith("No permission")) throw new IllegalStateException(editor.status);
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
                    editor.mouseScrolled(editor.detail.conditionX + 4, editor.detail.conditionY + 4, 0, -1);
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
                        .anyMatch(entity -> net.minecraft.resources.ResourceLocation.parse("irons_spellbooks:echoing_sword")
                            .equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
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
                    for (Screen screen : java.util.List.of(new net.minecraft.client.gui.screens.ReceivingLevelScreen(() -> false, net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason.OTHER),
                        new net.minecraft.client.gui.screens.ProgressScreen(true))) {
                        var opening = new net.neoforged.neoforge.client.event.ScreenEvent.Opening(editor, screen);
                        check(!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(opening).isCanceled(),
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
                    try {
                        var menuField = noppes.npcs.client.gui.util.GuiNPCInterface2.class.getDeclaredField("menu");
                        menuField.setAccessible(true);
                        var menu = menuField.get(mc.screen);
                        var buttonsField = noppes.npcs.client.gui.util.GuiNpcMenu.class.getDeclaredField("topButtons");
                        buttonsField.setAccessible(true);
                        var buttons = (noppes.npcs.shared.client.gui.components.GuiMenuTopButton[])buttonsField.get(menu);
                        var magic = java.util.Arrays.stream(buttons).filter(b -> b.id == MagicTab.TAB_ID).findFirst().orElseThrow();
                        check(java.util.Arrays.stream(buttons).filter(b -> b.id == MagicTab.TAB_ID).count() == 1, "exactly one injected magic tab");
                        for (int id = 1; id <= 6; id++) {
                            int tabId = id;
                            check(java.util.Arrays.stream(buttons).anyMatch(b -> b.id == tabId), "original CNPC tab retained " + id);
                        }
                        int mouseX = magic.getX() + magic.getWidth() / 2;
                        int mouseY = magic.getY() + 8;
                        var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
                        magic.renderWidget(graphics, mouseX, mouseY, 0);
                        graphics.flush();
                        check(magic.hover, "CNPC tab receives pointer hover before click");
                        mc.screen.mouseClicked(mouseX, mouseY, 0);
                        check(mc.screen instanceof GuiNpcMagic, "CNPC magic tab opens editor through actual mouse handler");
                        editor = (GuiNpcMagic)mc.screen;
                    } catch (ReflectiveOperationException error) { throw new IllegalStateException("CNPC menu fixture", error); }
                }
                case 26 -> {
                    if (!editor.ready()) return;
                    shot("14-main-tab-reopened", editor);
                }
                case 27 -> {
                    if (Boolean.getBoolean("cnpcmagic.verify.nativeEditor")) { stage = 29; break; }
                    CNPCMagic.LOGGER.info("HOTFIX PLAYER UNCHANGED checks={} dimension={} position={} mode={}", stationaryChecks,
                        mc.level.dimension().location(), mc.player.position(), mc.gameMode.getPlayerMode());
                    CNPCMagic.LOGGER.info("EDITOR CLIENT PASS COMPLETE: CNPC main tab, same-screen conditions, delivery segments, cast order save and reopen, real preview, suppressedLoadingScreens={}, suppressedProgressScreens={}", suppressedLoadingScreens, suppressedProgressScreens);
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
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        String name = capture;
        capture = null;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
            message -> CNPCMagic.LOGGER.info("EDITOR CLIENT SCREENSHOT {}", name));
    }
}

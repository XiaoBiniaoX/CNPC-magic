package top.cnpcmagic.client;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.client.provider.ClientMagicProvider;
import top.cnpcmagic.client.provider.ClientMagicProviders;
import top.cnpcmagic.client.gui.GuiNpcMagic;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/** Opt-in checks use the game's framebuffer, never desktop focus/input automation. */
@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class MagicClientVerification {
    private static final Set<String> POSES = new HashSet<>();
    private static final Set<String> HELD = new HashSet<>();
    private static final Map<UUID, PoseRun> RUNS = new HashMap<>();
    private static int ticks;
    private static boolean capture;
    private static GuiNpcMagic gui;
    private static EntityNPCInterface guiNpc;
    private static int guiStage;
    private static int guiReadyAt;
    private static LivingEntity beamNpc;
    private static int beamTick, beamRun;
    private static boolean beamHeld, beamCleared;

    public static void ironRendered(LivingEntity entity) {
        if (!Boolean.getBoolean("cnpcmagic.verify.ironGameplay") || entity.level() != Minecraft.getInstance().level) return;
        if (beamTick == ticks) return;
        beamRun = beamNpc == entity && beamTick == ticks - 1 ? beamRun + 1 : 1;
        beamNpc = entity; beamTick = ticks;
        if (beamRun >= 6 && !beamHeld) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(entity);
            require(data.getAdditionalCastData() instanceof io.redspace.ironsspellbooks.spells.CastingMobAimingData,
                "ordinary NPC native aiming data received");
            beamHeld = true; capture = true;
            CNPCMagic.LOGGER.info("VERIFY CLIENT PASS ordinary NPC native beam held={} aiming=true actor={}", beamRun, entity.getUUID());
        }
    }

    public static void applied(LivingEntity entity, String animation, HumanoidModel<?> model) {
        if (!Boolean.getBoolean("cnpcmagic.verify.client")) return;
        if (animation.isEmpty() || Math.abs(model.rightArm.xRot) <= 0.5) return;
        if (POSES.add(animation)) {
            CNPCMagic.LOGGER.info("VERIFY CLIENT PASS rendered pose {} right={} left={}", animation, model.rightArm.xRot, model.leftArm.xRot);
        }
        PoseRun previous = RUNS.get(entity.getUUID());
        if (previous != null && previous.tick == ticks && previous.animation.equals(animation)) return;
        int count = previous != null && previous.tick == ticks - 1 && previous.animation.equals(animation) ? previous.count + 1 : 1;
        RUNS.put(entity.getUUID(), new PoseRun(animation, ticks, count));
        if (count >= 6 && HELD.add(animation)) {
            CNPCMagic.LOGGER.info("VERIFY CLIENT PASS held pose {} ticks={}", animation, count);
            capture = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("cnpcmagic.verify.client") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        boolean gameplay = Boolean.getBoolean("cnpcmagic.verify.ironGameplay");
        if (ticks >= 450 && ticks < 600) verifyIndependentGui(mc);
        if (gameplay && beamHeld && !beamCleared) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(beamNpc);
            if (!data.isCasting() && data.getAdditionalCastData() == null) {
                beamCleared = true;
                CNPCMagic.LOGGER.info("VERIFY CLIENT PASS ordinary NPC native cast and aiming cleanup");
            }
        }
        if (ticks == 40) {
            try {
                verifyLanguage(mc);
            } catch (IOException | RuntimeException e) {
                CNPCMagic.LOGGER.error("VERIFY CLIENT FAIL language", e);
            }
            boolean valid = true;
            for (String id : java.util.List.of("irons_spellbooks", "goety", "ebwizardry", "ars_nouveau")) {
                ClientMagicProvider provider = ClientMagicProviders.get(id);
                valid &= (provider != null) == ModList.get().isLoaded(id);
                if (provider != null) valid &= !provider.getSpellIds().isEmpty();
            }
            CNPCMagic.LOGGER.info("VERIFY CLIENT {} connected providers={}", valid ? "PASS" : "FAIL",
                ClientMagicProviders.all().stream().map(ClientMagicProvider::getId).toList());
            capture = true;
        }
        if (ticks == 600) {
            if (guiStage < 4) CNPCMagic.LOGGER.error("VERIFY CLIENT FAIL independent GUI did not finish (stage={})", guiStage);
            if (gameplay && (!beamHeld || !beamCleared)) CNPCMagic.LOGGER.error("VERIFY CLIENT FAIL ordinary NPC beam held={} cleanup={}", beamHeld, beamCleared);
            boolean valid = POSES.stream().anyMatch(id -> id.startsWith("goety:")) == ModList.get().isLoaded("goety")
                && POSES.stream().anyMatch(id -> id.startsWith("irons_spellbooks:")) == ModList.get().isLoaded("irons_spellbooks")
                && HELD.stream().anyMatch(id -> id.startsWith("goety:")) == ModList.get().isLoaded("goety")
                && HELD.stream().anyMatch(id -> id.startsWith("irons_spellbooks:")) == ModList.get().isLoaded("irons_spellbooks");
            CNPCMagic.LOGGER.info("VERIFY CLIENT {} COMPLETE rendered={} held={}", valid ? "PASS" : "FAIL", POSES, HELD);
        }
        if (ticks == 640) mc.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void verifyLanguage(Minecraft mc) throws IOException {
        String language = System.getProperty("cnpcmagic.verify.language", mc.options.languageCode);
        require(language.equals(mc.options.languageCode), "selected Minecraft language matches test language");
        var resource = mc.getResourceManager().getResource(new ResourceLocation(CNPCMagic.MODID, "lang/" + language + ".json")).orElseThrow();
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            var translations = JsonParser.parseReader(reader).getAsJsonObject();
            require(translations.size() >= 100, "complete translation key set");
            for (var entry : translations.entrySet()) {
                require(Component.translatable(entry.getKey()).getString().equals(entry.getValue().getAsString()),
                    "Minecraft loaded translation " + entry.getKey());
            }
        }
        for (String key : java.util.List.of("enabled", "independent", "level", "goetyLevel", "casts", "castsUnused", "cooldown", "range")) {
            require(mc.font.width(Component.translatable("cnpcmagic.gui." + key)) <= 98, "translated field label fits " + key);
        }
        require(mc.font.width(Component.translatable("cnpcmagic.gui.spell")) <= 58, "spell label fits before picker");
        require(mc.font.width(Component.translatable("cnpcmagic.gui.hint")) <= 404, "translated footer fits panel");
        require(mc.font.width(Component.translatable("cnpcmagic.gui.addHint")) <= 196, "empty spell list hint fits panel");
        require(mc.font.width(Component.translatable("cnpcmagic.gui.add")) <= 64, "add button text fits");
        require(mc.font.width(Component.translatable("cnpcmagic.gui.remove")) <= 64, "remove button text fits");
        CNPCMagic.LOGGER.info("VERIFY CLIENT PASS language={} editor translations loaded", language);
    }

    private static void verifyIndependentGui(Minecraft mc) {
        if (guiStage >= 4) return;
        if (ClientMagicProviders.all().isEmpty()) {
            guiStage = 4;
            CNPCMagic.LOGGER.info("VERIFY CLIENT PASS independent GUI not applicable without providers");
            return;
        }
        try {
            if (guiStage == 0) {
                for (var entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof EntityNPCInterface npc && npc.getName().getString().equals("independent GUI")) guiNpc = npc;
                }
                if (guiNpc == null) return;
                gui = new GuiNpcMagic(guiNpc);
                mc.setScreen(gui);
                guiStage = 1;
                return;
            }
            if (!gui.ready()) return; // Wait for the real server GUI-data response.
            if (guiReadyAt == 0) {
                guiReadyAt = ticks;
                capture = true;
                return;
            }
            if (ticks - guiReadyAt < 5) return; // Let the actual layout render before exercising controls.
            if (guiStage == 1) {
                gui.verifyIndependent(true);
                gui.save();
                guiStage = 2;
                guiReadyAt = ticks;
                return;
            }
            if (guiStage == 2) {
                if (ticks - guiReadyAt < 20) return;
                if (!gui.ready()) return;
                gui = new GuiNpcMagic(guiNpc);
                mc.setScreen(gui);
                guiStage = 3;
                guiReadyAt = 0;
                return;
            }
            gui.verifyIndependent(false);
            gui.onClose();
            guiStage = 4;
            CNPCMagic.LOGGER.info("VERIFY CLIENT PASS editor independent draft/save/reopen (widget interaction not covered)");
        } catch (RuntimeException | LinkageError e) {
            guiStage = 4;
            CNPCMagic.LOGGER.error("VERIFY CLIENT FAIL independent GUI", e);
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!capture || event.phase != TickEvent.Phase.END) return;
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "verify-" + ticks + ".png", mc.getMainRenderTarget(),
            message -> CNPCMagic.LOGGER.info("VERIFY CLIENT framebuffer {}", message.getString()));
    }

    private record PoseRun(String animation, int tick, int count) {
    }
}

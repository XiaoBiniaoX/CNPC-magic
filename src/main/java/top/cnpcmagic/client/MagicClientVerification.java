package top.cnpcmagic.client;

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
        if (ticks == 40) {
            boolean valid = true;
            for (String id : java.util.List.of("irons_spellbooks", "goety")) {
                ClientMagicProvider provider = ClientMagicProviders.get(id);
                valid &= (provider != null) == ModList.get().isLoaded(id);
                if (provider != null) valid &= !provider.getSpellIds().isEmpty();
            }
            CNPCMagic.LOGGER.info("VERIFY CLIENT {} connected providers={}", valid ? "PASS" : "FAIL",
                ClientMagicProviders.all().stream().map(ClientMagicProvider::getId).toList());
            capture = true;
        }
        if (ticks == 600) {
            boolean valid = POSES.stream().anyMatch(id -> id.startsWith("goety:")) == ModList.get().isLoaded("goety")
                && POSES.stream().anyMatch(id -> id.startsWith("irons_spellbooks:")) == ModList.get().isLoaded("irons_spellbooks")
                && HELD.stream().anyMatch(id -> id.startsWith("goety:")) == ModList.get().isLoaded("goety")
                && HELD.stream().anyMatch(id -> id.startsWith("irons_spellbooks:")) == ModList.get().isLoaded("irons_spellbooks");
            CNPCMagic.LOGGER.info("VERIFY CLIENT {} COMPLETE rendered={} held={}", valid ? "PASS" : "FAIL", POSES, HELD);
        }
        if (ticks == 640) mc.stop();
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

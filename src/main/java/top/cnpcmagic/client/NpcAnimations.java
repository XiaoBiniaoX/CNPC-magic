package top.cnpcmagic.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.cnpcmagic.client.compat.goety.GoetyClientProvider;
import top.cnpcmagic.compat.goety.GoetyAdapter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.network.S2CNpcAnimation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class NpcAnimations {
    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final KeyframeAnimation FALLBACK = fallback();

    private NpcAnimations() {
    }

    public static void receive(S2CNpcAnimation packet) {
        if (packet.duration() <= packet.elapsed()) {
            ACTIVE.remove(packet.npc());
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(packet.animation());
        KeyframeAnimation animation = id == null ? null : PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            animation = FALLBACK;
        }
        ACTIVE.put(packet.npc(), new State(new KeyframeAnimationPlayer(animation, packet.elapsed()),
            packet.duration() - packet.elapsed(), packet.animation()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ACTIVE.values().removeIf(state -> {
                state.player.tick();
                return --state.remaining <= 0;
            });
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ACTIVE.clear();
        }
    }

    public static void apply(LivingEntity npc, HumanoidModel<?> model, float partialTick) {
        State state = ACTIVE.get(npc.getUUID());
        if (state == null || !state.player.isActive()) {
            return;
        }
        boolean goetyPose = state.animation.startsWith("goety:") && ModList.get().isLoaded("goety")
            && GoetyClientProvider.applyPose(state.animation, state.staff, npc, model);
        if (!goetyPose) {
            state.player.setupAnim(partialTick);
            transform(state.player, "head", model.head, partialTick);
            transform(state.player, "body", model.body, partialTick);
            transform(state.player, "rightArm", model.rightArm, partialTick);
            transform(state.player, "leftArm", model.leftArm, partialTick);
            transform(state.player, "rightLeg", model.rightLeg, partialTick);
            transform(state.player, "leftLeg", model.leftLeg, partialTick);
        }
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.jacket.copyFrom(player.body);
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightPants.copyFrom(player.rightLeg);
            player.leftPants.copyFrom(player.leftLeg);
        }
        MagicClientVerification.applied(npc, state.animation, model);
    }

    private static void transform(KeyframeAnimationPlayer animation, String name, ModelPart part, float partialTick) {
        Vec3f rotation = animation.get3DTransform(name, TransformType.ROTATION, partialTick,
            new Vec3f(part.xRot, part.yRot, part.zRot));
        Vec3f position = animation.get3DTransform(name, TransformType.POSITION, partialTick,
            new Vec3f(part.x, part.y, part.z));
        part.setRotation(rotation.getX(), rotation.getY(), rotation.getZ());
        part.setPos(position.getX(), position.getY(), position.getZ());
    }

    private static KeyframeAnimation fallback() {
        KeyframeAnimation.AnimationBuilder builder = new KeyframeAnimation.AnimationBuilder(AnimationFormat.UNKNOWN);
        builder.endTick = 20;
        builder.stopTick = 21;
        builder.isLooped = true;
        builder.returnTick = 5;
        builder.rightArm.pitch.setEnabled(true);
        builder.rightArm.yaw.setEnabled(true);
        builder.leftArm.pitch.setEnabled(true);
        builder.leftArm.yaw.setEnabled(true);
        builder.rightArm.pitch.addKeyFrame(0, 0, Ease.LINEAR);
        builder.rightArm.pitch.addKeyFrame(5, -1.5f, Ease.INOUTSINE);
        builder.rightArm.pitch.addKeyFrame(20, -1.5f, Ease.LINEAR);
        builder.rightArm.yaw.addKeyFrame(5, -0.25f, Ease.LINEAR);
        builder.leftArm.pitch.addKeyFrame(0, 0, Ease.LINEAR);
        builder.leftArm.pitch.addKeyFrame(5, -1.2f, Ease.INOUTSINE);
        builder.leftArm.pitch.addKeyFrame(20, -1.2f, Ease.LINEAR);
        builder.leftArm.yaw.addKeyFrame(5, 0.25f, Ease.LINEAR);
        return builder.build();
    }

    private static final class State {
        private final KeyframeAnimationPlayer player;
        private int remaining;
        private final String animation;
        private final ItemStack staff;

        private State(KeyframeAnimationPlayer player, int remaining, String animation) {
            this.player = player;
            this.remaining = remaining;
            this.animation = animation;
            this.staff = animation.startsWith("goety:") && ModList.get().isLoaded("goety")
                ? GoetyAdapter.makeStaff(animation) : ItemStack.EMPTY;
        }
    }
}

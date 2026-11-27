package top.cnpcmagic.client;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.math.Vec3f;
import com.zigythebird.playeranimcore.molang.MolangLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.cnpcmagic.client.compat.goety.GoetyClientProvider;
import top.cnpcmagic.compat.goety.GoetyAdapter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.network.S2CNpcAnimation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@net.neoforged.fml.common.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class NpcAnimations {
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private NpcAnimations() {
    }

    public static void receive(S2CNpcAnimation packet) {
        if (packet.duration() <= packet.elapsed()) {
            ACTIVE.remove(packet.npc());
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(packet.animation());
        Animation animation = id == null ? null : PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            animation = PlayerAnimResources.getAnimation(ResourceLocation.fromNamespaceAndPath("cnpcmagic", "cast"));
        }
        if (animation == null) return;
        Controller controller = new Controller();
        controller.triggerAnimation(animation, packet.elapsed());
        controller.tick(new AnimationData(0, 0));
        ACTIVE.put(packet.npc(), new State(controller,
            packet.duration() - packet.elapsed(), packet.elapsed(), packet.animation()));
    }

    public static int castingTime(LivingEntity npc, String animation) {
        State state = ACTIVE.get(npc.getUUID());
        return state != null && state.animation.equals(animation) ? state.elapsed : -1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ACTIVE.values().removeIf(state -> {
            state.player.tick(new AnimationData(0, 0));
            state.elapsed++;
            return --state.remaining <= 0;
        });
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
            state.player.setupAnim(new AnimationData((float) npc.getDeltaMovement().length(), partialTick));
            transform(state.player, "head", model.head, partialTick);
            transform(state.player, "body", model.body, partialTick);
            transform(state.player, "right_arm", model.rightArm, partialTick);
            transform(state.player, "left_arm", model.leftArm, partialTick);
            transform(state.player, "right_leg", model.rightLeg, partialTick);
            transform(state.player, "left_leg", model.leftLeg, partialTick);
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
        top.cnpcmagic.client.gui.EditorClientVerification.applied(npc, model);
    }

    private static void transform(AnimationController animation, String name, ModelPart part, float partialTick) {
        PlayerAnimBone bone = new PlayerAnimBone(name);
        bone.updateRotation(part.xRot, part.yRot, part.zRot);
        animation.get3DTransform(bone);
        part.setRotation(bone.rotX, bone.rotY, bone.rotZ);
        part.setPos(part.x + bone.positionX, part.y + bone.positionY, part.z + bone.positionZ);
    }

    private static final class Controller extends AnimationController {
        private Controller() { super((controller, data, setter) -> PlayState.CONTINUE, MolangLoader::createNewEngine); }
        @Override public void registerBones() {
            for (String name : new String[]{"head", "body", "torso", "right_arm", "left_arm", "right_leg", "left_leg"})
                registerPlayerAnimBone(name);
        }
        @Override public Vec3f getBonePosition(String name) { return Vec3f.ZERO; }
    }

    private static final class State {
        private final AnimationController player;
        private int remaining;
        private int elapsed;
        private final String animation;
        private final ItemStack staff;

        private State(AnimationController player, int remaining, int elapsed, String animation) {
            this.player = player;
            this.remaining = remaining;
            this.elapsed = elapsed;
            this.animation = animation;
            this.staff = animation.startsWith("goety:") && ModList.get().isLoaded("goety")
                ? GoetyAdapter.makeStaff(animation) : ItemStack.EMPTY;
        }
    }
}

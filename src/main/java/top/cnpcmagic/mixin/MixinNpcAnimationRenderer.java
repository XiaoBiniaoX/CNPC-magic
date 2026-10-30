package top.cnpcmagic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcmagic.client.NpcAnimations;

/** Apply after CNPC's own setupAnim, before both the model and armor layers render. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinNpcAnimationRenderer<T extends LivingEntity> {
    @Shadow protected EntityModel<T> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void cnpcmagic$animate(T entity, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        if ((entity instanceof EntityNPCInterface || entity instanceof Player) && model instanceof HumanoidModel<?> humanoid) {
            NpcAnimations.apply(entity, humanoid, partialTick);
        }
    }
}

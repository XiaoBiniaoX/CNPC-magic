package top.cnpcmagic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse Goety's entity-independent native water layers for NPC rendering. */
@Pseudo
@Mixin(targets = "com.Polarice3.Goety.client.render.WaterJetRenderer", remap = false)
public interface MixinGoetyWaterJetRenderer {
    @Invoker(value = "renderJetLayer", remap = false)
    static void cnpcmagic$renderLayer(VertexConsumer vertices, PoseStack pose, float radius, float length, float ratio, float offset) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}

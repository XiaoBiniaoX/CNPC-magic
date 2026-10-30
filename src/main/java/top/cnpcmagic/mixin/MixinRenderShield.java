package top.cnpcmagic.mixin;

import electroblob.wizardry.client.renderer.effect.RenderShield;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses Wizardry's actual shield mesh rather than approximating its appearance. */
@Mixin(value = RenderShield.class, remap = false)
public interface MixinRenderShield {
    @Invoker("render")
    static void cnpcmagic$render(Tessellator tessellator) { throw new AssertionError(); }
}

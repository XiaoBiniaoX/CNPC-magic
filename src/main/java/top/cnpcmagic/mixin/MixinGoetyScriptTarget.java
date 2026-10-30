package top.cnpcmagic.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcmagic.server.ScriptMagic;

/** Goety's Player ray target must not replace an explicit script target. Absent Goety is skipped. */
@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.magic.Spell", remap = false)
public abstract class MixinGoetyScriptTarget {
    @Inject(method = "getTarget(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/world/entity/LivingEntity;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcmagic$scriptTarget(LivingEntity caster, int range, CallbackInfoReturnable<LivingEntity> ci) {
        LivingEntity target = ScriptMagic.getCastTarget(caster);
        if (target != null) ci.setReturnValue(target);
    }
}

package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.spell.internal.CastContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.necromancy.Enrage", remap = false)
public abstract class MixinWizardryEnrage {
    @Inject(method = "affectEntity", at = @At("HEAD"), remap = false)
    private void cnpcmagic$npcAggression(CastContext ctx, Vec3 origin, LivingEntity target, int count,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!ctx.world().isClientSide && ctx.caster() instanceof EntityNPCInterface npc && target instanceof PathfinderMob)
            target.setLastHurtByMob(npc);
    }
}

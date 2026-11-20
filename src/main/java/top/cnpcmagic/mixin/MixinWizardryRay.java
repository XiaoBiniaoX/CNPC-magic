package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.util.RayTracer;
import com.binaris.wizardry.content.spell.abstr.RaySpell;
import com.binaris.wizardry.setup.registries.Spells;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcmagic.compat.ebwizardry.EbWizardryAdapter;
import java.util.function.Predicate;

/** Aim native block-only spells at the actual surface below the NPC's living target. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.abstr.RaySpell", remap = false)
public abstract class MixinWizardryRay {
    @Shadow protected abstract boolean shootSpell(CastContext ctx, Vec3 origin, Vec3 direction);

    @Unique private boolean cnpcmagic$blockAim(CastContext ctx) {
        return ctx.caster() instanceof EntityNPCInterface && ((Object) this == Spells.DETONATE
            || (Object) this == Spells.COBWEBS || (Object) this == Spells.ARCANE_LOCK);
    }

    @Redirect(method = "cast(Lcom/binaris/wizardry/api/content/spell/internal/EntityCastContext;)Z",
        at = @At(value = "INVOKE", target = "Lcom/binaris/wizardry/content/spell/abstr/RaySpell;shootSpell(Lcom/binaris/wizardry/api/content/spell/internal/CastContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Z"), remap = false)
    private boolean cnpcmagic$aimAtSurface(RaySpell spell, CastContext context, Vec3 origin, Vec3 direction) {
        if (cnpcmagic$blockAim(context) && context instanceof EntityCastContext ctx && ctx.target() != null) {
            direction = EbWizardryAdapter.targetSurface(ctx.target()).subtract(origin).normalize();
        }
        return shootSpell(context, origin, direction);
    }

    @Redirect(method = "shootSpell", at = @At(value = "INVOKE", target = "Lcom/binaris/wizardry/api/content/util/RayTracer;rayTrace(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FZLjava/lang/Class;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"), remap = false)
    private HitResult cnpcmagic$traceSurface(Level world, Entity caster, Vec3 origin, Vec3 end, float assist,
            boolean liquids, Class<? extends Entity> type, Predicate<? super Entity> filter, CastContext ctx, Vec3 castOrigin, Vec3 castDirection) {
        return RayTracer.rayTrace(world, caster, origin, end, assist, liquids, type,
            cnpcmagic$blockAim(ctx) ? entity -> true : filter);
    }

    @ModifyVariable(method = "shootSpell", at = @At("STORE"), ordinal = 0, remap = false)
    private HitResult cnpcmagic$missIsNotBlock(HitResult hit, CastContext ctx, Vec3 origin, Vec3 direction) {
        return ctx.caster() instanceof EntityNPCInterface && hit.getType() == HitResult.Type.MISS ? null : hit;
    }
}

package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.content.spell.abstr.ConstructRangedSpell;
import com.binaris.wizardry.content.spell.abstr.ConstructSpell;
import com.binaris.wizardry.api.content.entity.construct.MagicConstructEntity;
import com.binaris.wizardry.setup.registries.Spells;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcmagic.compat.ebwizardry.EbWizardryAdapter;

/** Wizardry 0.8.10 tests instanceof BlockHitResult even for a MISS; actual block hits stay untouched. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.abstr.ConstructRangedSpell", remap = false)
public abstract class MixinWizardryConstruct extends ConstructSpell<MagicConstructEntity> {
    protected MixinWizardryConstruct(java.util.function.Function<net.minecraft.world.level.Level, MagicConstructEntity> factory, boolean permanent) {
        super(factory, permanent);
    }

    @ModifyVariable(method = "cast(Lcom/binaris/wizardry/api/content/spell/internal/EntityCastContext;)Z",
        at = @At("STORE"), ordinal = 0, remap = false)
    private HitResult cnpcmagic$unobstructedConstruct(HitResult hit, EntityCastContext ctx) {
        return ctx.caster() instanceof EntityNPCInterface
            && hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    @Redirect(method = "cast(Lcom/binaris/wizardry/api/content/spell/internal/EntityCastContext;)Z",
        at = @At(value = "INVOKE", target = "Lcom/binaris/wizardry/content/spell/abstr/ConstructRangedSpell;spawnConstruct(Lcom/binaris/wizardry/api/content/spell/internal/CastContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;)Z"), remap = false)
    private boolean cnpcmagic$iceSurface(ConstructRangedSpell<?> spell, CastContext ctx, Vec3 origin, Direction side) {
        if (ctx.caster() instanceof EntityNPCInterface && spell == Spells.ICE_SPIKES && ctx instanceof EntityCastContext entityCtx) {
            var hit = ctx.world().clip(new ClipContext(ctx.caster().getEyePosition(), EbWizardryAdapter.targetSurface(entityCtx.target()),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                origin = hit.getLocation();
                side = hit.getDirection();
            }
        }
        return spawnConstruct(ctx, origin, side);
    }
}

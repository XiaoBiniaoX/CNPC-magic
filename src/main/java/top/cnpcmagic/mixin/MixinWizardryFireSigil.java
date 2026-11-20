package top.cnpcmagic.mixin;

import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.setup.registries.Spells;
import net.minecraft.world.phys.HitResult;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Wizardry 0.8.10 tests instanceof BlockHitResult even for a MISS; actual block hits stay untouched. */
@Pseudo
@Mixin(targets = "com.binaris.wizardry.content.spell.abstr.ConstructRangedSpell", remap = false)
public abstract class MixinWizardryFireSigil {
    @ModifyVariable(method = "cast(Lcom/binaris/wizardry/api/content/spell/internal/EntityCastContext;)Z",
        at = @At("STORE"), ordinal = 0, remap = false)
    private HitResult cnpcmagic$unobstructedFireSigil(HitResult hit, EntityCastContext ctx) {
        return (Object) this == Spells.FIRE_SIGIL && ctx.caster() instanceof EntityNPCInterface
            && hit.getType() == HitResult.Type.MISS ? null : hit;
    }
}

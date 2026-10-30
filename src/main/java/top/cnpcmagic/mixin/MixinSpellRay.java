package top.cnpcmagic.mixin;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.spell.SpellRay;
import electroblob.wizardry.util.RayTracer;
import electroblob.wizardry.util.SpellModifiers;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcmagic.wizardry.MagicCasterAccess;
import top.cnpcmagic.wizardry.NpcRayTrace;

@Mixin(value = SpellRay.class, remap = false)
public abstract class MixinSpellRay {
    @Redirect(method = "shootSpell", at = @At(value = "INVOKE",
        target = "Lelectroblob/wizardry/util/RayTracer;rayTrace(Lnet/minecraft/world/World;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;FZZZLjava/lang/Class;Ljava/util/function/Predicate;)Lnet/minecraft/util/math/RayTraceResult;"),
        require = 1)
    private RayTraceResult cnpcmagic$nearbyTarget(World world, Vec3d origin, Vec3d endpoint, float aimAssist,
            boolean hitLiquids, boolean ignoreUncollidables, boolean returnLastUncollidable,
            Class<? extends Entity> entityType, Predicate<? super Entity> filter,
            World spellWorld, Vec3d spellOrigin, Vec3d direction, EntityLivingBase caster,
            int ticksInUse, SpellModifiers modifiers) {
        RayTraceResult result = RayTracer.rayTrace(world, origin, endpoint, aimAssist, hitLiquids,
            ignoreUncollidables, returnLastUncollidable, entityType, filter);
        if (!(caster instanceof MagicCasterAccess) || !((Spell) (Object) this).isContinuous) return result;
        return NpcRayTrace.includeOverlappingTarget(world, origin, endpoint, aimAssist, entityType, filter, result);
    }
}

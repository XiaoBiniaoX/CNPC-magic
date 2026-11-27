package top.cnpcmagic.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.cnpcmagic.compat.iron.IronAdapter;
import top.cnpcmagic.server.ScriptMagic;

/** These native block-only raycasts need the NPC target's ground, while retaining native range and collisions. */
@Pseudo
@Mixin(targets = {
    "com.gametechbc.gtbcs_geomancy_plus.spells.geo.WeepingClusterSpell",
    "com.gametechbc.gtbcs_geomancy_plus.spells.geo.GeoConductorSpell",
    "com.gametechbc.gtbcs_geomancy_plus.spells.geo.CrystallineConduitSpell"
}, remap = false)
public abstract class MixinIronGeomancyGround {
    @ModifyVariable(method = "performRaycast", at = @At("STORE"), ordinal = 1, remap = false)
    private Vec3 cnpcmagic$aimAtGround(Vec3 direction, LivingEntity caster, double range) {
        LivingEntity target = ScriptMagic.getCastTarget(caster);
        if (caster instanceof EntityNPCInterface && IronAdapter.getCastData(caster) != null && target != null)
            return target.position().add(0, -0.05, 0).subtract(caster.getEyePosition()).normalize();
        return direction;
    }
}

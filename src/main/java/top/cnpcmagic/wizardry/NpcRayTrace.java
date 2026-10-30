package top.cnpcmagic.wizardry;

import electroblob.wizardry.entity.ICustomHitbox;
import electroblob.wizardry.util.EntityUtils;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Repairs native ray misses when an NPC's ray starts inside a nearby target's aim-assist box. */
public final class NpcRayTrace {
    private NpcRayTrace() {
    }

    public static RayTraceResult includeOverlappingTarget(World world, Vec3d origin, Vec3d endpoint,
            float aimAssist, Class<? extends Entity> entityType, Predicate<? super Entity> filter,
            RayTraceResult result) {
        // Keep the native nearest hit as the limit, so this cannot shoot through walls or other entities.
        Vec3d limit = result == null ? endpoint : result.hitVec;
        double closest = origin.squareDistanceTo(limit);
        AxisAlignedBB nearby = new AxisAlignedBB(origin, origin).grow(1 + Math.max(0, aimAssist));
        for (Entity entity : world.getEntitiesWithinAABB(entityType, nearby)) {
            if (filter.test(entity) || !EntityUtils.isLiving(entity) || entity instanceof ICustomHitbox) continue;
            AxisAlignedBB bounds = entity.getEntityBoundingBox();
            if (bounds == null) continue;
            bounds = bounds.grow(entity.getCollisionBorderSize());
            if (!bounds.grow(aimAssist).contains(origin)) continue;

            // calculateIntercept alone finds the EXIT when starting inside; the floor may precede that exit.
            Vec3d hit;
            if (bounds.contains(origin)) {
                hit = origin;
            } else {
                RayTraceResult entry = bounds.calculateIntercept(origin, limit);
                if (entry == null) continue;
                hit = entry.hitVec;
            }
            double distance = origin.squareDistanceTo(hit);
            if (distance < closest) {
                result = new RayTraceResult(entity, hit);
                closest = distance;
            }
        }
        return result;
    }
}

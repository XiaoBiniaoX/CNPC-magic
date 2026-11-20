package top.cnpcmagic.casting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Explicit targeting: a missing target never silently becomes self. */
public record SpellTarget(Kind kind, LivingEntity entity, Vec3 position, double radius) {
    public enum Kind { ENTITY, BLOCK, POSITION, DIRECTION, AREA, SELF, NONE }
    public static SpellTarget entity(LivingEntity e) { return new SpellTarget(Kind.ENTITY, e, e.position(), 0); }
    public static SpellTarget self(LivingEntity e) { return new SpellTarget(Kind.SELF, e, e.position(), 0); }
    public static SpellTarget position(double x, double y, double z) { return new SpellTarget(Kind.POSITION, null, new Vec3(x, y, z), 0); }
    public static SpellTarget block(BlockPos p) { return new SpellTarget(Kind.BLOCK, null, Vec3.atCenterOf(p), 0); }
    public static SpellTarget direction(double x, double y, double z) { return new SpellTarget(Kind.DIRECTION, null, new Vec3(x, y, z), 0); }
    public static SpellTarget area(double x, double y, double z, double radius) { return new SpellTarget(Kind.AREA, null, new Vec3(x, y, z), radius); }
    public static SpellTarget none() { return new SpellTarget(Kind.NONE, null, Vec3.ZERO, 0); }
}

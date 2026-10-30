package top.cnpcmagic.wizardry;

import net.minecraft.entity.EntityLivingBase;

/** Gives Wizardry's existing collidable shield an NPC owner. */
public interface ShieldAccess {
    void cnpcmagic$setOwner(EntityLivingBase owner);
}

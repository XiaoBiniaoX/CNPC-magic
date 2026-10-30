package top.cnpcmagic.wizardry;

import net.minecraft.entity.EntityLivingBase;

public interface CastHandle {
    /** @return true once this cast has ended. */
    boolean tick(EntityLivingBase caster, EntityLivingBase target);

    boolean wasSuccessful();

    void cancel(EntityLivingBase caster);
}

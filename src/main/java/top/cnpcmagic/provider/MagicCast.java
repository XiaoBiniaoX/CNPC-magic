package top.cnpcmagic.provider;

import net.minecraft.world.entity.LivingEntity;

/** One complete cast, including charging/channeling and cleanup. */
public interface MagicCast {
    boolean tick(LivingEntity caster, LivingEntity target);

    boolean wasSuccessful();

    void cancel(LivingEntity caster);
}

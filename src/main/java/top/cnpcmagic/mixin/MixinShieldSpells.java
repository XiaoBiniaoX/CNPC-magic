package top.cnpcmagic.mixin;

import electroblob.wizardry.entity.EntityShield;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Shield;
import electroblob.wizardry.spell.ShadowWard;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumAction;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import top.cnpcmagic.wizardry.MagicCasterAccess;
import top.cnpcmagic.wizardry.ShieldAccess;

/** Only extends the two player-only wards to CNPC; other Wizardry mobs are unchanged. */
@Mixin(value = {Shield.class, ShadowWard.class}, remap = false)
public abstract class MixinShieldSpells extends Spell {
    protected MixinShieldSpells() { super("ebwizardry", "cnpcmagic_shield_bridge", EnumAction.BLOCK, true); }

    @Override
    public boolean canBeCastBy(EntityLiving caster, boolean override) {
        return caster instanceof EntityNPCInterface || super.canBeCastBy(caster, override);
    }

    @Override
    public boolean cast(World world, EntityLiving caster, EnumHand hand, int ticks,
                        EntityLivingBase target, SpellModifiers modifiers) {
        if (!(caster instanceof EntityNPCInterface)) return false;
        if ((Object) this == Spells.shadow_ward) {
            if (world.isRemote) {
                double dx = -1 + 2 * world.rand.nextFloat();
                double dy = -1 + world.rand.nextFloat();
                double dz = -1 + 2 * world.rand.nextFloat();
                world.spawnParticle(EnumParticleTypes.PORTAL, caster.posX,
                    caster.posY + caster.getEyeHeight(), caster.posZ, dx, dy, dz);
            }
            if (ticks % 50 == 0) playSound(world, caster, ticks, -1, modifiers);
            return true;
        }
        if ((Object) this == Spells.shield && !world.isRemote) {
            caster.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 10,
                getProperty(EFFECT_STRENGTH).intValue(), false, false));
            MagicCasterAccess access = (MagicCasterAccess) caster;
            EntityShield shield = access.cnpcmagic$getShield();
            if (shield == null || shield.isDead) {
                shield = new EntityShield(world);
                ((ShieldAccess) shield).cnpcmagic$setOwner(caster);
                shield.setPosition(caster.posX, caster.posY + 1, caster.posZ);
                access.cnpcmagic$setShield(shield);
                world.spawnEntity(shield);
            }
        }
        playSound(world, caster, ticks, -1, modifiers);
        return true;
    }
}

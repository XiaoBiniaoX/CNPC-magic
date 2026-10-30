package top.cnpcmagic.wizardry;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.entity.living.ISpellCaster;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.packet.PacketCastSpell;
import electroblob.wizardry.packet.PacketNPCCastSpell;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Executes Wizardry's native player and NPC cast paths without duplicating spell effects. */
public final class WizardryCasting {
    public static final int CONTINUOUS_TICKS = 50;

    private WizardryCasting() {
    }

    public static CastHandle start(EntityLivingBase caster, Spell spell, int level, EntityLivingBase target) {
        if (caster == null || target == null || spell == null || caster.world.isRemote
            || caster.world != target.world || caster.isDead || target.isDead) return null;

        face(caster, target);
        SpellModifiers modifiers = new SpellModifiers().set(SpellModifiers.POTENCY, level, true);
        if (caster instanceof EntityPlayer) return startPlayer((EntityPlayer) caster, spell, modifiers);
        if (caster instanceof EntityLiving && caster instanceof ISpellCaster && caster instanceof MagicCasterAccess) {
            return startNpc((EntityLiving) caster, spell, modifiers, target);
        }
        return null;
    }

    private static CastHandle startNpc(EntityLiving caster, Spell spell, SpellModifiers modifiers,
                                       EntityLivingBase target) {
        ISpellCaster spellCaster = (ISpellCaster) caster;
        if (spell.isContinuous && spellCaster.getContinuousSpell() != Spells.none) return null;
        if (MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Pre(
            SpellCastEvent.Source.NPC, spell, caster, modifiers))) return null;
        if (!spell.cast(caster.world, caster, EnumHand.MAIN_HAND, 0, target, modifiers)) return null;

        ((MagicCasterAccess) caster).cnpcmagic$setModifiers(modifiers);
        MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(
            SpellCastEvent.Source.NPC, spell, caster, modifiers));

        if (spell.isContinuous) {
            spellCaster.setContinuousSpell(spell);
            spellCaster.setSpellCounter(1);
            sendNpcPacket(caster, target, spell, modifiers);
            return new NpcContinuous(spell, modifiers, target.getEntityId());
        }

        if (spell.requiresPacket()) sendNpcPacket(caster, target, spell, modifiers);
        return Completed.SUCCESS;
    }

    private static CastHandle startPlayer(EntityPlayer caster, Spell spell, SpellModifiers modifiers) {
        if (MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Pre(
            SpellCastEvent.Source.COMMAND, spell, caster, modifiers))) return null;

        if (spell.isContinuous) {
            WizardData data = WizardData.get(caster);
            if (data == null || data.isCasting()) return null;
            data.startCastingContinuousSpell(spell, modifiers, CONTINUOUS_TICKS);
            return new PlayerContinuous(spell, modifiers);
        }

        if (!spell.cast(caster.world, caster, EnumHand.MAIN_HAND, 0, modifiers)) return null;
        MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(
            SpellCastEvent.Source.COMMAND, spell, caster, modifiers));
        if (spell.requiresPacket()) {
            IMessage packet = new PacketCastSpell.Message(caster.getEntityId(), EnumHand.MAIN_HAND, spell, modifiers);
            WizardryPacketHandler.net.sendToDimension(packet, caster.dimension);
        }
        return Completed.SUCCESS;
    }

    private static void sendNpcPacket(EntityLiving caster, EntityLivingBase target, Spell spell,
                                      SpellModifiers modifiers) {
        WizardryPacketHandler.net.sendToAllAround(
            new PacketNPCCastSpell.Message(caster.getEntityId(), target == null ? -1 : target.getEntityId(),
                EnumHand.MAIN_HAND, spell, modifiers),
            new TargetPoint(caster.dimension, caster.posX, caster.posY, caster.posZ, 128));
    }

    public static void face(EntityLivingBase caster, EntityLivingBase target) {
        double dx = target.posX - caster.posX;
        double dz = target.posZ - caster.posZ;
        double dy = target.posY + target.getEyeHeight() - (caster.posY + caster.getEyeHeight());
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        caster.rotationYaw = yaw;
        caster.rotationYawHead = yaw;
        caster.renderYawOffset = yaw;
        caster.rotationPitch = pitch;
        if (caster instanceof EntityLiving) {
            ((EntityLiving) caster).getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        }
    }

    private enum Completed implements CastHandle {
        SUCCESS;

        @Override public boolean tick(EntityLivingBase caster, EntityLivingBase target) { return true; }
        @Override public boolean wasSuccessful() { return true; }
        @Override public void cancel(EntityLivingBase caster) { }
    }

    private static final class NpcContinuous implements CastHandle {
        private final Spell spell;
        private final SpellModifiers modifiers;
        private final int targetId;
        private int ticks = 1;
        private boolean successful;
        private boolean finished;

        private NpcContinuous(Spell spell, SpellModifiers modifiers, int targetId) {
            this.spell = spell;
            this.modifiers = modifiers;
            this.targetId = targetId;
        }

        @Override
        public boolean tick(EntityLivingBase base, EntityLivingBase target) {
            if (finished) return true;
            EntityLiving caster = (EntityLiving) base;
            if (target == null || target.isDead || target.getEntityId() != targetId || caster.world != target.world
                || ticks >= CONTINUOUS_TICKS
                || MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Tick(
                    SpellCastEvent.Source.NPC, spell, caster, modifiers, ticks))
                || !spell.cast(caster.world, caster, EnumHand.MAIN_HAND, ticks, target, modifiers)) {
                successful = ticks >= CONTINUOUS_TICKS;
                finish(caster, target);
                return true;
            }
            ticks++;
            return false;
        }

        @Override
        public boolean wasSuccessful() {
            return successful;
        }

        @Override
        public void cancel(EntityLivingBase caster) {
            if (!finished) finish((EntityLiving) caster, null);
        }

        private void finish(EntityLiving caster, EntityLivingBase target) {
            finished = true;
            MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Finish(
                SpellCastEvent.Source.NPC, spell, caster, modifiers, ticks));
            spell.finishCasting(caster.world, caster, Double.NaN, Double.NaN, Double.NaN, null, ticks, modifiers);
            MagicCasterAccess access = (MagicCasterAccess) caster;
            if (spell == Spells.shield && access.cnpcmagic$getShield() != null) {
                access.cnpcmagic$getShield().setDead();
                access.cnpcmagic$setShield(null);
            }
            ((ISpellCaster) caster).setContinuousSpell(Spells.none);
            ((ISpellCaster) caster).setSpellCounter(0);
            ((MagicCasterAccess) caster).cnpcmagic$setModifiers(new SpellModifiers());
            sendNpcPacket(caster, target, Spells.none, new SpellModifiers());
        }
    }

    private static final class PlayerContinuous implements CastHandle {
        private final Spell spell;
        private final SpellModifiers modifiers;
        private int ticks;
        private boolean successful;
        private boolean finished;

        private PlayerContinuous(Spell spell, SpellModifiers modifiers) {
            this.spell = spell;
            this.modifiers = modifiers;
        }

        @Override
        public boolean tick(EntityLivingBase base, EntityLivingBase target) {
            if (finished) return true;
            EntityPlayer player = (EntityPlayer) base;
            WizardData data = WizardData.get(player);
            ticks++;
            if (data == null || !data.isCasting() || ticks >= CONTINUOUS_TICKS) {
                successful = ticks >= CONTINUOUS_TICKS;
                finish(player);
                return true;
            }
            return false;
        }

        @Override public boolean wasSuccessful() { return successful; }

        @Override
        public void cancel(EntityLivingBase caster) {
            if (!finished) finish((EntityPlayer) caster);
        }

        private void finish(EntityPlayer player) {
            finished = true;
            WizardData data = WizardData.get(player);
            if (data != null && data.isCasting()) data.stopCastingContinuousSpell();
            MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Finish(
                SpellCastEvent.Source.COMMAND, spell, player, modifiers, ticks));
            spell.finishCasting(player.world, player, Double.NaN, Double.NaN, Double.NaN, null, ticks, modifiers);
        }
    }
}

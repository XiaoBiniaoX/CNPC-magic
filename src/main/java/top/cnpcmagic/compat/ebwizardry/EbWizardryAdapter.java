package top.cnpcmagic.compat.ebwizardry;

import com.binaris.wizardry.api.content.event.SpellCastEvent;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.SpellContexts;
import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.content.spell.abstr.BuffSpell;
import com.binaris.wizardry.content.entity.construct.FireSigilConstruct;
import com.binaris.wizardry.core.event.WizardryEventBus;
import com.binaris.wizardry.core.networking.s2c.NPCSpellCastS2C;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.Spells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.server.NpcAnimationSync;

import java.util.List;

/** Only loaded when Electroblob's Wizardry is installed; mirrors AttackSpellGoal without player impersonation. */
public final class EbWizardryAdapter {
    /** Continuous NPC channels are bounded, matching the mod's own AttackSpellBasicGoal duration. */
    private static final int CONTINUOUS_TICKS = 40;
    private static final int MAX_LEVEL = 5;
    private static final String FIRE_SIGIL = "ebwizardry:fire_sigil";

    private EbWizardryAdapter() {
    }

    public static Spell getSpell(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        Spell spell = Services.REGISTRY_UTIL.getSpell(location);
        // cast() and canCastByEntity() both default to false, so unoverridden spells would silently do nothing.
        return spell != null && spell != Spells.NONE && spell.canCastByEntity()
            && spell.isEnabled(SpellContexts.NPCS) ? spell : null;
    }

    public static List<String> getSpellIds() {
        return Services.REGISTRY_UTIL.getSpells().stream()
            .filter(spell -> spell != Spells.NONE && spell.canCastByEntity() && spell.isEnabled(SpellContexts.NPCS))
            .map(spell -> spell.getLocation().toString())
            .sorted().toList();
    }

    public static int getMaxLevel(String id) {
        return getSpell(id) == null ? 1 : MAX_LEVEL;
    }

    /** Aim just inside the support's collision surface, including slabs and chests. */
    public static Vec3 targetSurface(LivingEntity target) {
        BlockPos pos = BlockPos.containing(target.getX(), target.getY() - 0.05, target.getZ());
        var shape = target.level().getBlockState(pos).getCollisionShape(target.level(), pos);
        double y = shape.isEmpty() ? target.getY() : pos.getY() + shape.max(Direction.Axis.Y);
        return new Vec3(target.getX(), y - 0.0001, target.getZ());
    }

    public static MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        Spell spell = getSpell(id);
        if (spell == null || target == null) {
            return null;
        }
        SpellModifiers modifiers = new SpellModifiers()
            .multiplyTotal(SpellModifiers.POTENCY, 1.0f + 0.25f * Math.max(0, level - 1));
        EntityCastContext ctx = context(caster, target, 0, modifiers);
        if (WizardryEventBus.fireEvent(new SpellCastEvent.Pre(SpellCastEvent.Sources.NPC, spell, ctx))) {
            diagnose(spell, id, ctx, "pre_event_cancelled", false, -1, -1);
            return null;
        }
        // A buff spell's client mirror returns early once the caster has the effect, and the effect sync packet
        // arrives before the cast packet, so NPC buff casts would never render. Mirror those before applying.
        boolean preBroadcast = spell.isInstantCast() && spell.requiresPacket() && spell instanceof BuffSpell buff
            && !buff.getMobEffects().isEmpty()
            && !caster.getActiveEffectsMap().keySet().containsAll(buff.getMobEffects());
        if (preBroadcast) {
            broadcast(caster, target, spell, modifiers);
        }
        int constructsBefore = countFireSigils(id, ctx);
        boolean accepted = spell.cast(ctx);
        int constructsAfter = countFireSigils(id, ctx);
        diagnose(spell, id, ctx, accepted
            ? constructsAfter > constructsBefore ? "construct_spawned" : "construct_not_observed"
            : "native_cast_rejected", accepted, constructsBefore, constructsAfter);
        if (!accepted) {
            return null;
        }
        boolean continuous = !spell.isInstantCast();
        Session session = new Session(spell, modifiers, id, target, continuous);
        if (continuous) {
            broadcast(caster, target, spell, modifiers);
            NpcAnimationSync.play(caster, id, CONTINUOUS_TICKS);
        } else {
            WizardryEventBus.fireEvent(new SpellCastEvent.Post(SpellCastEvent.Sources.NPC, spell, ctx));
            if (!preBroadcast && spell.requiresPacket()) {
                broadcast(caster, target, spell, modifiers);
            }
            NpcAnimationSync.play(caster, id, 10);
        }
        return session;
    }

    private static int countFireSigils(String id, EntityCastContext ctx) {
        if (!FIRE_SIGIL.equals(id) || ctx.world().isClientSide || ctx.target() == null) {
            return -1;
        }
        return ctx.world().getEntitiesOfClass(FireSigilConstruct.class,
            ctx.target().getBoundingBox().inflate(4.0)).size();
    }

    private static void diagnose(Spell spell, String id, EntityCastContext ctx, String phase,
                                 boolean accepted, int constructsBefore, int constructsAfter) {
        if (!FIRE_SIGIL.equals(id)) {
            return;
        }
        CNPCMagic.LOGGER.info("Wizardry fire_sigil phase={} accepted={} constructs={} -> {} distance={} casterGround={} targetGround={} caster={} target={}",
            phase, accepted, constructsBefore, constructsAfter, ctx.caster().distanceTo(ctx.target()),
            ctx.caster().onGround(), ctx.target().onGround(), ctx.caster().blockPosition(), ctx.target().blockPosition());
        if (!accepted && phase.equals("native_cast_rejected")) {
            var hit = ctx.world().clip(new ClipContext(ctx.caster().getEyePosition(1), ctx.target().position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.target()));
            CNPCMagic.LOGGER.info("Wizardry fire_sigil native diagnostic range={} rayType={} rayBlock={} nativeObstructionReject={}",
                ctx.modifiers().get(SpellModifiers.RANGE, spell.property(com.binaris.wizardry.content.spell.DefaultProperties.RANGE)),
                hit.getType(), hit.getBlockPos(), hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                    && !hit.getBlockPos().equals(ctx.caster().blockPosition()));
        }
    }

    private static EntityCastContext context(LivingEntity caster, LivingEntity target, int castingTicks,
                                             SpellModifiers modifiers) {
        return new EntityCastContext(caster.level(), caster, InteractionHand.MAIN_HAND, castingTicks, target, modifiers);
    }

    /** The client handler needs a living target to rebuild its context, and we always have one. */
    private static void broadcast(LivingEntity caster, LivingEntity target, Spell spell, SpellModifiers modifiers) {
        if (caster.level().isClientSide) {
            return;
        }
        Services.NETWORK_HELPER.sendToTracking(caster,
            new NPCSpellCastS2C(caster.getId(), target == null ? -1 : target.getId(), InteractionHand.MAIN_HAND,
                spell, modifiers));
    }

    private static final class Session implements MagicCast {
        private final Spell spell;
        private final SpellModifiers modifiers;
        private final String animation;
        private final boolean continuous;
        private LivingEntity target;
        private int ticks;
        private boolean successful;
        private boolean finished;

        private Session(Spell spell, SpellModifiers modifiers, String animation, LivingEntity target,
                        boolean continuous) {
            this.spell = spell;
            this.modifiers = modifiers;
            this.animation = animation;
            this.target = target;
            this.continuous = continuous;
            this.successful = true;
        }

        @Override
        public boolean tick(LivingEntity caster, LivingEntity target) {
            if (finished) {
                return true;
            }
            this.target = target;
            if (!continuous) {
                complete(caster);
                return true;
            }
            ticks++;
            if (ticks >= CONTINUOUS_TICKS) {
                complete(caster);
                return true;
            }
            EntityCastContext ctx = context(caster, target, ticks, modifiers);
            if (WizardryEventBus.fireEvent(new SpellCastEvent.Tick(SpellCastEvent.Sources.NPC, spell, ctx))
                || !spell.cast(ctx)) {
                complete(caster);
                return true;
            }
            // The NPC is not an ISpellCaster, so the client cannot tick particles on its own.
            broadcast(caster, target, spell, modifiers);
            return false;
        }

        @Override
        public boolean wasSuccessful() {
            return successful;
        }

        @Override
        public void cancel(LivingEntity caster) {
            successful = false;
            complete(caster);
        }

        private void complete(LivingEntity caster) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                if (continuous) {
                    EntityCastContext ctx = context(caster, target, ticks, modifiers);
                    WizardryEventBus.fireEvent(new SpellCastEvent.Finish(SpellCastEvent.Sources.NPC, spell, ctx));
                    spell.endCast(ctx);
                    broadcast(caster, target, Spells.NONE, new SpellModifiers());
                }
            } finally {
                if (successful) {
                    NpcAnimationSync.play(caster, animation, 10);
                } else {
                    NpcAnimationSync.stop(caster);
                }
            }
        }
    }
}

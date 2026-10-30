package top.cnpcmagic.compat.iron;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.spells.CastingMobAimingData;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.BurningDashSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.server.NpcAnimationSync;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class IronAdapter {
    private static final ThreadLocal<CastData> INVOKING = new ThreadLocal<>();
    private static final Set<String> PLAYER_ONLY = Set.of(
        "irons_spellbooks:summon_ender_chest", "irons_spellbooks:recall", "irons_spellbooks:pocket_dimension"
    );
    private IronAdapter() {
    }

    public static AbstractSpell getSpell(String spellId) {
        if (spellId == null || spellId.isEmpty()) {
            return null;
        }
        return SpellRegistry.getSpell(spellId);
    }

    public static List<AbstractSpell> getEnabledSpells() {
        return SpellRegistry.getEnabledSpells().stream().filter(IronAdapter::isSupportedSpell).toList();
    }

    public static boolean isSupportedSpell(AbstractSpell spell) {
        return spell != null && spell != SpellRegistry.none() && spell.isEnabled()
            && !PLAYER_ONLY.contains(spell.getSpellId());
    }

    public static MagicCast startCast(LivingEntity caster, String spellId, int level, LivingEntity target) {
        AbstractSpell spell = getSpell(spellId);
        if (!isSupportedSpell(spell)) {
            return null;
        }
        MagicData nativeData = MagicData.getPlayerMagicData(caster);
        if (nativeData == null || nativeData.isCasting()) {
            return null;
        }
        // Each session owns casting/aiming data; native player ticks must not advance it, nor sibling casts overwrite it.
        MagicData magicData = new MagicData(true);
        return withData(caster, magicData, () -> prepareCast(caster, spellId, level, target, spell, magicData));
    }

    /** Optional Mixin hook, only substitutes data during this session's synchronous native calls. */
    public static MagicData getCastData(LivingEntity caster) {
        CastData current = INVOKING.get();
        return current != null && current.caster == caster ? current.data : null;
    }

    private static <T> T withData(LivingEntity caster, MagicData data, Supplier<T> action) {
        CastData previous = INVOKING.get();
        INVOKING.set(new CastData(caster, data));
        try {
            return action.get();
        } finally {
            if (previous == null) INVOKING.remove();
            else INVOKING.set(previous);
        }
    }

    private record CastData(LivingEntity caster, MagicData data) {
    }

    private static MagicCast prepareCast(LivingEntity caster, String spellId, int level, LivingEntity target,
                                         AbstractSpell spell, MagicData magicData) {
        if (magicData.getSyncedData().getServerPlayerId() != caster.getId()) {
            magicData.setSyncedData(new SyncedSpellData(caster));
        }
        magicData.resetAdditionalCastData();
        try {
            if (!spell.checkPreCastConditions(caster.level(), level, caster, magicData)) {
                magicData.resetAdditionalCastData();
                return null;
            }
            switch (spellId) {
                case "irons_spellbooks:teleport", "irons_spellbooks:frost_step", "irons_spellbooks:blood_step" -> {
                    int distance = spellId.endsWith("blood_step") ? 3 : 10;
                    Vec3 pos = caster.position();
                    for (int i = 0; i < 24; i++) {
                        Vec3 candidate = target.position().subtract(new Vec3(0, 0, distance / (float) (i / 7 + 1))
                            .yRot((float) -Math.toRadians(target.getYRot() + i * 45)));
                        candidate = Utils.moveToRelativeGroundLevel(caster.level(), candidate, 5).add(0, 0.1, 0);
                        if (caster.level().noCollision(caster, caster.getBoundingBox().move(candidate.subtract(caster.position())))) {
                            pos = candidate;
                            break;
                        }
                    }
                    magicData.setAdditionalCastData(new TeleportSpell.TeleportData(pos));
                }
                case "irons_spellbooks:burning_dash" ->
                    magicData.setAdditionalCastData(new BurningDashSpell.BurningDashDirectionOverrideCastData());
                case "irons_spellbooks:ray_of_siphoning" -> {
                    CastingMobAimingData aiming = new CastingMobAimingData();
                    if (caster instanceof Mob) {
                        aiming.updateAim(target, 1);
                        magicData.setAdditionalCastData(aiming);
                    }
                }
            }
            int duration = Math.max(0, spell.getEffectiveCastTime(level, caster));
            magicData.initiateCast(spell, level, duration, caster instanceof Player ? CastSource.COMMAND : CastSource.MOB,
                SpellSelectionManager.MAINHAND);
            spell.onServerPreCast(caster.level(), level, caster, magicData);
            NpcAnimationSync.play(caster, spell.getCastStartAnimation().getForPlayer().map(Object::toString).orElse(""),
                Math.max(10, duration));
            return new Session(spell, level, magicData);
        } catch (RuntimeException | LinkageError e) {
            magicData.resetCastingState();
            magicData.resetAdditionalCastData();
            throw e;
        }
    }

    private static final class Session implements MagicCast {
        private final AbstractSpell spell;
        private final int level;
        private final MagicData data;
        private boolean successful;
        private boolean finished;

        private Session(AbstractSpell spell, int level, MagicData data) {
            this.spell = spell;
            this.level = level;
            this.data = data;
        }

        @Override
        public boolean tick(LivingEntity caster, LivingEntity target) {
            return withData(caster, data, () -> tickScoped(caster, target));
        }

        private boolean tickScoped(LivingEntity caster, LivingEntity target) {
            if (finished) {
                return true;
            }
            if (!data.isCasting()) {
                cancel(caster);
                return true;
            }
            if (data.getAdditionalCastData() instanceof CastingMobAimingData aiming) {
                aiming.updateAim(target, 1);
            }
            data.handleCastDuration();
            spell.onServerCastTick(caster.level(), level, caster, data);
            int remaining = data.getCastDurationRemaining();
            if (remaining <= 0) {
                if (spell.getCastType() != CastType.CONTINUOUS) {
                    spell.onCast(caster.level(), level, caster, data.getCastSource(), data);
                    successful = true;
                }
                complete(caster, false);
                return true;
            }
            if (spell.getCastType() == CastType.CONTINUOUS && (remaining + 1) % 10 == 0) {
                spell.onCast(caster.level(), level, caster, data.getCastSource(), data);
                successful = true;
            }
            return false;
        }

        @Override
        public boolean wasSuccessful() {
            return successful;
        }

        @Override
        public void cancel(LivingEntity caster) {
            successful = false;
            withData(caster, data, () -> { complete(caster, true); return null; });
        }

        private void complete(LivingEntity caster, boolean cancelled) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                spell.onServerCastComplete(caster.level(), level, caster, data, cancelled);
            } finally {
                data.resetCastingState();
                data.resetAdditionalCastData();
                if (cancelled) {
                    NpcAnimationSync.stop(caster);
                } else {
                    NpcAnimationSync.play(caster,
                        spell.getCastFinishAnimation().getForPlayer().map(Object::toString).orElse(""), 10);
                }
            }
        }
    }
}

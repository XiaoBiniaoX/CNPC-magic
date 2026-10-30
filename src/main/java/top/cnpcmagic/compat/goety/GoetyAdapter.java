package top.cnpcmagic.compat.goety;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.IBreathingSpell;
import com.Polarice3.Goety.api.magic.IBlockSpell;
import com.Polarice3.Goety.api.magic.IChargingSpell;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.ITouchSpell;
import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import com.Polarice3.Goety.common.items.ModItems;
import com.Polarice3.Goety.common.magic.SpellStat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.server.NpcAnimationSync;

import java.util.List;
import java.util.Set;

/** Only loaded when Goety is installed; no player impersonation or NPC NBT/equipment writes. */
public final class GoetyAdapter {
    private static final Set<String> PLAYER_ONLY = Set.of(
        "goety:crafting_focus", "goety:ender_chest_focus", "goety:end_walk_focus", "goety:recall_focus",
        "goety:call_focus", "goety:troop_focus", "goety:command_focus", "goety:order_focus",
        "goety:grapple_focus", "goety:burrowing_focus", "goety:warding_focus"
    );

    private GoetyAdapter() {
    }

    public static ISpell getSpell(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location == null || !location.getNamespace().equals("goety") || PLAYER_ONLY.contains(id)) {
            return null;
        }
        if (BuiltInRegistries.ITEM.get(location) instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            // Block-only utility spells need a clicked block, not an NPC combat target.
            return spell instanceof IBlockSpell && !(spell instanceof ITouchSpell) ? null : spell;
        }
        return null;
    }

    public static List<String> getSpellIds() {
        return BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString)
            .filter(id -> getSpell(id) != null).sorted().toList();
    }

    public static int getMaxLevel(String id) {
        ISpell spell = getSpell(id);
        return spell != null && spell.acceptedEnchantments().contains(ModEnchantments.POTENCY.get())
            ? ModEnchantments.POTENCY.get().getMaxLevel() + 1 : 1;
    }

    public static ItemStack makeStaff(String id) {
        ItemStack staff = new ItemStack(ModItems.DARK_WAND.get());
        staff.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            if (handler instanceof IItemHandlerModifiable inventory) {
                inventory.setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))));
            }
        });
        return staff;
    }

    public static MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        ISpell prototype = getSpell(id);
        if (prototype == null || !(caster.level() instanceof ServerLevel world)) {
            return null;
        }
        ISpell spell;
        try {
            // Goety's registry instances can hold a victim/beam: never share them between sessions/NPCs.
            spell = prototype.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create independent Goety spell " + id, e);
        }
        ItemStack staff = makeStaff(id);
        SpellStat stats = spell.defaultStats().increasePotency(Math.max(0, level - 1));
        if (!spell.conditionsMet(world, caster) || !spell.conditionsMet(world, caster, stats)) {
            return null;
        }
        if (spell instanceof ITouchSpell touch && !touch.targetConditions(world, caster, target, staff, stats)) {
            return null;
        }
        Session session = new Session(spell, staff, stats, id);
        try {
            session.initialize(caster);
            spell.startSpell(world, caster, staff, stats);
            SoundEvent sound = spell.CastingSound(caster, staff);
            if (sound != null) {
                spell.playSound(world, caster, sound, spell.castingVolume(), spell.castingPitch());
            }
            NpcAnimationSync.play(caster, id, Math.max(10, session.duration));
            return session;
        } catch (RuntimeException | LinkageError e) {
            session.cancel(caster);
            throw e;
        }
    }

    private static final class Session implements MagicCast {
        private final ISpell spell;
        private final ItemStack staff;
        private final SpellStat stats;
        private final ItemStack focus;
        private final String animation;
        private int duration;
        private int elapsed;
        private int chargeUp;
        private int interval;
        private int shots;
        private int shotLimit;
        private int sinceShot;
        private boolean successful;
        private boolean finished;

        private Session(ISpell spell, ItemStack staff, SpellStat stats, String id) {
            this.spell = spell;
            this.staff = staff;
            this.stats = stats;
            animation = id;
            focus = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
        }

        private void initialize(LivingEntity caster) {
            if (spell instanceof IChargingSpell charging) {
                chargeUp = Math.max(0, charging.castUp(caster, staff));
                interval = Math.max(1, charging.Cooldown(caster, staff, 0));
                shotLimit = charging.shotsNumber(caster, staff);
                // ponytail: unbounded player channels get a finite 5-second NPC round; add a duration field if needed.
                if (shotLimit <= 0) {
                    shotLimit = Math.max(1, 100 / interval);
                }
                duration = chargeUp + interval * shotLimit;
            } else {
                duration = Math.max(1, spell.castDuration(caster, staff));
            }
        }

        @Override
        public boolean tick(LivingEntity caster, LivingEntity target) {
            if (finished) {
                return true;
            }
            ServerLevel world = (ServerLevel) caster.level();
            if (!spell.conditionsMet(world, caster) || !spell.conditionsMet(world, caster, stats)) {
                cancel(caster);
                return true;
            }
            elapsed++;
            if (elapsed + 10 >= duration && spell instanceof IChargingSpell) {
                duration = elapsed + 20;
                NpcAnimationSync.play(caster, animation, 20);
            }
            spell.useSpell(world, caster, staff, elapsed, stats);
            spell.useParticle(world, caster, staff);
            if (elapsed % 20 == 0 && spell.loopSound(caster) != null) {
                spell.playSound(world, caster, spell.loopSound(caster));
            }
            if (spell instanceof IChargingSpell charging) {
                if (elapsed >= chargeUp && ++sinceShot >= interval) {
                    sinceShot = 0;
                    if (spell instanceof IBreathingSpell breathing) {
                        breathing.showWandBreath(caster, staff, stats);
                    }
                    spell.SpellResult(world, caster, staff, stats);
                    successful = true;
                    shots++;
                    interval = Math.max(1, charging.Cooldown(caster, staff, shots));
                }
                if (shots >= shotLimit) {
                    complete(caster);
                    return true;
                }
            } else if (elapsed >= duration) {
                if (spell instanceof ITouchSpell touch) {
                    touch.touchResult(world, caster, target, staff, stats);
                } else {
                    spell.SpellResult(world, caster, staff, stats);
                }
                successful = true;
                complete(caster);
                return true;
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
            complete(caster);
        }

        private void complete(LivingEntity caster) {
            if (!finished) {
                finished = true;
                try {
                    spell.stopSpell((ServerLevel) caster.level(), caster, staff, focus, elapsed, stats);
                } finally {
                    if (successful) {
                        // Instant Goety spells have zero cast time: retain a visible follow-through, not a one-tick flicker.
                        NpcAnimationSync.play(caster, animation, 10);
                    } else {
                        NpcAnimationSync.stop(caster);
                    }
                }
            }
        }
    }
}

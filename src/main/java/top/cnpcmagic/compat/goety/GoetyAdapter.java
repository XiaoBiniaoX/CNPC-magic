package top.cnpcmagic.compat.goety;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.IBreathingSpell;
import com.Polarice3.Goety.api.magic.IBlockSpell;
import com.Polarice3.Goety.api.magic.IChargingSpell;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.api.magic.ITouchSpell;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.common.items.ModItems;
import com.Polarice3.Goety.common.enchantments.ModEnchantments;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import com.Polarice3.Goety.common.magic.SpellStat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
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
        if (spell == null || !spell.acceptedEnchantments().contains(ModEnchantments.POTENCY)) return 1;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        // Goety's shipped datapack defines potency max_level=3; the server uses its active registry.
        return server == null ? 4 : server.registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getHolderOrThrow(ModEnchantments.POTENCY).value().getMaxLevel() + 1;
    }

    public static ItemStack makeStaff(String id) {
        ItemStack staff = new ItemStack(ModItems.DARK_WAND.get());
        if (staff.getCapability(Capabilities.ItemHandler.ITEM) instanceof IItemHandlerModifiable inventory)
            inventory.setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
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
        Session session = new Session(spell, staff, stats, id);
        try {
            session.initialize(caster);
            if (!spell.conditionsMet(world, caster) || !spell.conditionsMet(world, caster, stats)) {
                return null;
            }
            if (spell instanceof ITouchSpell touch && !touch.targetConditions(world, caster, target, staff, stats)) {
                return null;
            }
            spell.startSpell(world, caster, staff, stats);
            SoundEvent sound = spell.CastingSound(caster, staff);
            if (sound != null) {
                world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), sound,
                    net.minecraft.sounds.SoundSource.HOSTILE, spell.castingVolume(), spell.castingPitch());
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
        private final DarkWand wand;
        private final SpellStat stats;
        private final String animation;
        private int duration;
        private int elapsed;
        private int chargeUp;
        private boolean successful;
        private boolean finished;

        private Session(ISpell spell, ItemStack staff, SpellStat stats, String id) {
            this.spell = spell;
            this.staff = staff;
            wand = (DarkWand)staff.getItem();
            this.stats = stats;
            animation = id;
        }

        private void initialize(LivingEntity caster) {
            CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> {
                tag.putInt(IWand.SOULCOST, 0);
                tag.putInt(IWand.DURATION, 0);
                tag.putInt(IWand.COOLDOWN, 0);
                tag.putInt(IWand.COOL, 0);
                tag.putInt(IWand.SHOTS, 0);
                tag.putInt(IWand.SECONDS, 0);
                tag.putInt(IWand.SOULUSE, 0);
                tag.putInt(IWand.CASTTIME, 0);
            });
            refreshStaffConditions(caster);
            if (spell instanceof IChargingSpell charging) {
                chargeUp = Math.max(0, charging.castUp(caster, staff));
                int shotLimit = charging.shotsNumber(caster, staff);
                duration = shotLimit > 0 ? chargeUp + Math.max(1, wand.Cooldown(staff)) * shotLimit : chargeUp + 100;
            } else {
                duration = Math.max(1, spell.castDuration(caster, staff));
            }
        }

        private void refreshStaffConditions(LivingEntity caster) {
            wand.setSpellConditions(spell, staff, caster);
            CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> {
                tag.putInt(IWand.SOULUSE, wand.SoulUse(caster, staff));
                tag.putInt(IWand.CASTTIME, wand.CastDuration(staff));
            });
        }

        @Override
        public boolean tick(LivingEntity caster, LivingEntity target) {
            if (finished) {
                return true;
            }
            ServerLevel world = (ServerLevel) caster.level();
            refreshStaffConditions(caster);
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
                world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), spell.loopSound(caster),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1, 1);
            }
            if (spell instanceof IChargingSpell charging) {
                int currentChargeUp = Math.max(0, charging.castUp(caster, staff));
                if (elapsed >= currentChargeUp || currentChargeUp <= 0) {
                    CustomData.update(DataComponents.CUSTOM_DATA, staff,
                        tag -> tag.putInt(IWand.COOL, tag.getInt(IWand.COOL) + 1));
                    int cool = staff.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(IWand.COOL);
                    if (cool >= wand.Cooldown(staff)) {
                        CustomData.update(DataComponents.CUSTOM_DATA, staff, tag -> tag.putInt(IWand.COOL, 0));
                        int shotLimit = charging.shotsNumber(caster, staff);
                        if (shotLimit > 0) wand.increaseShots(staff);

                        if (spell instanceof IBreathingSpell breathing) {
                            breathing.showWandBreath(caster, staff, stats);
                        }
                        spell.SpellResult(world, caster, staff, stats);
                        successful = true;
                        int currentShotLimit = charging.shotsNumber(caster, staff);
                        if (currentShotLimit > 0 && wand.ShotsFired(staff) >= currentShotLimit) {
                            complete(caster);
                            return true;
                        }
                    }
                }
                if (charging.shotsNumber(caster, staff) <= 0 && elapsed >= chargeUp + 100) {
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
                    spell.stopSpell((ServerLevel) caster.level(), caster, staff, IWand.getFocus(staff), elapsed, stats);
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

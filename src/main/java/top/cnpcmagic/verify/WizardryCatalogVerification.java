package top.cnpcmagic.verify;

import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.core.platform.Services;
import com.binaris.wizardry.setup.registries.EBMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.casting.CastingService;
import top.cnpcmagic.casting.SpellTarget;
import top.cnpcmagic.compat.ebwizardry.EbWizardryAdapter;
import top.cnpcmagic.compat.ebwizardry.EbWizardryPreview;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.MagicPreview;

import java.util.*;

/** Real isolated casts for every spell offered by the Wizardry picker; no fabricated effects. */
public final class WizardryCatalogVerification {
    private static ServerLevel level;
    private static List<String> spells;
    private static int index = -1, ticks, passed, failed;
    private static EntityNPCInterface caster;
    private static Mob target;
    private static MagicCast cast;
    private static String result;
    private static float casterHealth, targetHealth;
    private static Set<String> casterEffects, targetEffects;
    private static Vec3 targetPosition;
    private static FakePlayer manaRecipient;
    private static boolean reflected;
    private static final List<String> spawned = new ArrayList<>();
    private static final List<Entity> creations = new ArrayList<>();
    private static final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);

    private WizardryCatalogVerification() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        level = event.getServer().getLevel(MagicPreview.DIMENSION);
        spells = EbWizardryAdapter.getSpellIds();
        String selected = System.getProperty("cnpcmagic.verify.catalogSpells", "");
        if (!selected.isBlank()) spells = Arrays.stream(selected.split(",")).map(id -> id.contains(":") ? id : "ebwizardry:" + id).toList();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setChunkForced(x, z, true);
        CNPCMagic.LOGGER.info("WIZARDRY CATALOG START count={} ids={}", spells.size(), spells);
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (level != null && event.getLevel() == level && index >= 0 && index < spells.size()
            && event.getEntity() != caster && event.getEntity() != target && event.getEntity() != manaRecipient) {
            Entity e = event.getEntity();
            spawned.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + "/" + e.getClass().getSimpleName());
            creations.add(e);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null) return;
        try {
            if (index < 0 || ticks++ >= Integer.getInteger("cnpcmagic.verify.catalogTicks", 80)) {
                if (index >= 0) {
                    finishRow();
                    if (cast != null) cast.cancel(caster);
                }
                var all = new ArrayList<Entity>(); level.getAllEntities().forEach(all::add); all.forEach(Entity::discard);
                if (++index == spells.size()) {
                    CNPCMagic.LOGGER.info("WIZARDRY CATALOG COMPLETE count={} pass={} fail={}", spells.size(), passed, failed);
                    if (failed != 0) CNPCMagic.LOGGER.error("WIZARDRY CATALOG FAIL {} spells lacked verified native effects", failed);
                    level = null; return;
                }
                String id = spells.get(index);
                EbWizardryPreview.resetArena(level, ORIGIN);
                caster = CustomEntities.entityCustomNpc.create(level);
                target = (id.equals("ebwizardry:ray_of_purification") ? EntityType.HUSK : EntityType.PILLAGER).create(level);
                caster.moveTo(0.5, 80, 0.5, -90, 0); caster.setNoAi(true); caster.setPersistenceRequired();
                target.moveTo(0.5 + EbWizardryPreview.targetDistance(id), 80, 0.5, 90, 0); target.setNoAi(true); target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
                EbWizardryPreview.prepare(id, caster, target);
                caster.setTarget(target); MagicCombatHandler.face(caster, target);
                level.addFreshEntity(caster); level.addFreshEntity(target);
                manaRecipient = null;
                if (id.equals("ebwizardry:font_of_mana")) {
                    // A real native recipient, never a substitute caster. No network client/world is touched.
                    manaRecipient = EbWizardryPreview.manaRecipient(caster);
                }
                spawned.clear(); creations.clear(); ticks = 0; cast = null; reflected = false;
            }
            if (ticks == 4) {
                String id = spells.get(index);
                Spell spell = EbWizardryAdapter.getSpell(id);
                if (id.equals("ebwizardry:arcane_lock")) {
                    Vec3 origin = caster.getEyePosition().add(0, -0.25, 0);
                    Vec3 direction = EbWizardryAdapter.targetSurface(target).subtract(origin).normalize();
                    var hit = level.clip(new net.minecraft.world.level.ClipContext(origin, origin.add(direction.scale(spell.property(DefaultProperties.RANGE))),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
                    CNPCMagic.LOGGER.info("WIZARDRY CATALOG SURFACE target={} surface={} hit={} block={} container={} nativeLockData={}", target.position(),
                        EbWizardryAdapter.targetSurface(target), hit.getType(), hit.getBlockPos(), level.getBlockEntity(hit.getBlockPos()),
                        level.getBlockEntity(hit.getBlockPos()) == null ? "none" : Services.OBJECT_DATA.getArcaneLockData(level.getBlockEntity(hit.getBlockPos())));
                }
                CNPCMagic.LOGGER.info("WIZARDRY CATALOG CAST id={} class={} range={} radius={} duration={} potency={}", id,
                    spell.getClass().getSimpleName(), spell.property(DefaultProperties.RANGE),
                    spell.property(DefaultProperties.EFFECT_RADIUS), spell.property(DefaultProperties.EFFECT_DURATION),
                    new com.binaris.wizardry.api.content.spell.internal.SpellModifiers().getFactor("ebwizardry.potency"));
                casterHealth = caster.getHealth(); targetHealth = target.getHealth(); targetPosition = target.position();
                casterEffects = effects(caster); targetEffects = effects(target);
                blocks.clear();
                for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-12, -1, -12), ORIGIN.offset(32, 5, 12)))
                    blocks.put(pos.immutable(), level.getBlockState(pos));
                var attempt = CastingService.start(caster, id, 1, SpellTarget.entity(target), "verify_wizardry_catalog");
                cast = attempt.cast(); result = attempt.result().code();
            }
            if (cast != null) cast.tick(caster, target);
            if (ticks == 12 && spells.get(index).equals("ebwizardry:curse_of_soulbinding")) {
                float health = target.getHealth();
                caster.hurt(caster.damageSources().magic(), 2);
                reflected = target.getHealth() < health;
            }
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("WIZARDRY CATALOG FAIL id=" + (index >= 0 && index < spells.size() ? spells.get(index) : "startup"), error);
            level = null;
        }
    }

    private static Set<String> effects(net.minecraft.world.entity.LivingEntity entity) {
        Set<String> result = new TreeSet<>();
        entity.getActiveEffects().forEach(e -> result.add(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()) + ":" + e.getAmplifier()));
        return result;
    }

    private static void finishRow() {
        String id = spells.get(index);
        var changed = new TreeSet<String>();
        blocks.forEach((pos, state) -> { if (!state.equals(level.getBlockState(pos))) changed.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()); });
        long minions = creations.stream().filter(e -> e instanceof Mob mob && Services.OBJECT_DATA.isMinion(mob)
            && caster.getUUID().equals(Services.OBJECT_DATA.getMinionData(mob).getOwnerUUID()) && mob.getTarget() == target).count();
        long hail = creations.stream().filter(e -> e instanceof com.binaris.wizardry.content.entity.projectile.IceShardEntity shard
            && shard.getOwner() == caster && shard.getDeltaMovement().y < 0).count();
        boolean observed = target.getHealth() < targetHealth || !effects(caster).equals(casterEffects)
            || !effects(target).equals(targetEffects);
        observed = switch (id) {
            case "ebwizardry:heal", "ebwizardry:greater_heal", "ebwizardry:group_heal", "ebwizardry:healing_aura" -> caster.getHealth() > casterHealth;
            case "ebwizardry:heal_ally" -> target.getHealth() > targetHealth;
            case "ebwizardry:cure_effects" -> casterEffects.contains("minecraft:poison:0") && !caster.hasEffect(MobEffects.POISON);
            case "ebwizardry:remove_curse" -> !casterEffects.isEmpty() && !caster.hasEffect(EBMobEffects.CURSE_OF_UNDEATH.get());
            case "ebwizardry:reversal" -> !caster.hasEffect(MobEffects.POISON) && target.hasEffect(MobEffects.POISON);
            case "ebwizardry:enrage" -> target.getLastHurtByMob() == caster && target.getTarget() == caster;
            case "ebwizardry:arcane_lock" -> level.getBlockEntity(BlockPos.containing(EbWizardryAdapter.targetSurface(target))) instanceof BaseContainerBlockEntity container
                && caster.getUUID().equals(Services.OBJECT_DATA.getArcaneLockData(container).getArcaneLockOwnerUUID());
            case "ebwizardry:curse_of_soulbinding" -> reflected && target.hasEffect(EBMobEffects.CURSE_OF_SOULBINDING.get());
            case "ebwizardry:font_of_mana" -> manaRecipient.hasEffect(EBMobEffects.FONT_OF_MANA.get());
            case "ebwizardry:detonate", "ebwizardry:poison", "ebwizardry:blinding_flash", "ebwizardry:forests_curse",
                 "ebwizardry:plague_of_darkness", "ebwizardry:shockwave" -> target.getHealth() < targetHealth
                    || !effects(target).equals(targetEffects) || target.position().distanceToSqr(targetPosition) > 1;
            case "ebwizardry:banish" -> target.position().distanceToSqr(targetPosition) > 1;
            case "ebwizardry:bubble", "ebwizardry:entrapment" -> target.getVehicle() instanceof com.binaris.wizardry.content.entity.construct.BubbleConstruct;
            case "ebwizardry:cobwebs" -> changed.contains("ebwizardry:vanishing_cobweb");
            case "ebwizardry:conjure_block" -> changed.contains("ebwizardry:spectral_block");
            case "ebwizardry:permafrost" -> changed.contains("ebwizardry:permafrost");
            case "ebwizardry:hailstorm" -> hail > 0;
            case "ebwizardry:spider_swarm", "ebwizardry:vex_swarm", "ebwizardry:summon_blaze", "ebwizardry:summon_lightning_wraith",
                 "ebwizardry:summon_skeleton", "ebwizardry:summon_skeleton_legion", "ebwizardry:summon_snow_golem",
                 "ebwizardry:summon_wither_skeleton", "ebwizardry:summon_zombie", "ebwizardry:zombie_apocalypse" -> minions > 0;
            default -> observed;
        };
        boolean ok = cast != null && cast.wasSuccessful() && observed;
        if (ok) passed++; else failed++;
        CNPCMagic.LOGGER.info("WIZARDRY CATALOG ROW id={} verdict={} result={} success={} damage={} casterHealth={} -> {} casterEffects={} -> {} targetEffects={} -> {} distance={} targetAggro={} reflected={} riding={} blocks={} minions={} hail={} recipientEffects={} spawned={}",
            id, ok ? "PASS" : "FAIL", result, cast != null && cast.wasSuccessful(), targetHealth - target.getHealth(),
            casterHealth, caster.getHealth(), casterEffects, effects(caster), targetEffects, effects(target),
            caster.distanceTo(target), target.getTarget() == caster, reflected,
            target.getVehicle() == null ? "none" : target.getVehicle().getClass().getSimpleName(), changed, minions, hail,
            manaRecipient == null ? Set.of() : effects(manaRecipient), spawned);
    }
}

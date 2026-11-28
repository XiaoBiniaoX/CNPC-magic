package top.cnpcmagic.verify;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.casting.CastingService;
import top.cnpcmagic.casting.SpellTarget;
import top.cnpcmagic.compat.iron.IronAdapter;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.MagicCombatHandler;
import top.cnpcmagic.server.MagicPreview;
import java.util.*;

/** Opt-in isolated catalog: reports native effects separately from accepted casts. */
public final class IronCatalogVerification {
    private static ServerLevel world;
    private static List<String> ids;
    private static int index = -1, ticks, duration, accepted, observed;
    private static EntityNPCInterface caster;
    private static Mob target;
    private static MagicCast cast;
    private static String result;
    private static final Set<String> spawned = new TreeSet<>();
    private static boolean effect;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        world = event.getServer().getLevel(MagicPreview.DIMENSION);
        ids = new ArrayList<>(IronAdapter.getEnabledSpells().stream().map(s -> s.getSpellId())
            .filter(id -> !id.startsWith("irons_spellbooks:")).toList());
        ids.add("irons_spellbooks:ray_of_siphoning");
        for (String id : ids) if (MagicProviders.forSpell(id) != MagicProviders.get("irons_spellbooks"))
            throw new IllegalStateException("Unclaimed registered Iron spell " + id);
        if (MagicProviders.forSpell("missing:spell") != null) throw new IllegalStateException("Unknown spell claimed");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.setChunkForced(x, z, true);
        for (BlockPos p : BlockPos.betweenClosed(-16, 79, -16, 32, 79, 16)) world.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        CNPCMagic.LOGGER.info("IRON CATALOG START count={} ids={}", ids.size(), ids);
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (world != null && event.getLevel() == world && event.getEntity() != caster && event.getEntity() != target)
            spawned.add(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString());
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (world == null || event.phase != TickEvent.Phase.END) return;
        try {
            if (index < 0 || ticks++ >= duration) {
                if (index >= 0) {
                    if (cast != null) cast.cancel(caster);
                    if (!"REJECTED".equals(result)) accepted++;
                    if (effect || !spawned.isEmpty()) observed++;
                    CNPCMagic.LOGGER.info("IRON CATALOG ROW id={} result={} effect={} spawned={} targetHealth={} casterPos={}",
                        ids.get(index), result, effect, spawned, target.getHealth(), caster.position());
                }
                var entities = new ArrayList<Entity>(); world.getAllEntities().forEach(entities::add); entities.forEach(Entity::discard);
                if (++index == ids.size()) {
                    CNPCMagic.LOGGER.info("IRON CATALOG COMPLETE count={} accepted={} observed={}", ids.size(), accepted, observed);
                    world = null; return;
                }
                caster = CustomEntities.entityCustomNpc.create(world); target = EntityType.PILLAGER.create(world);
                caster.moveTo(0.5, 80, 0.5, -90, 0); caster.setNoAi(true); caster.setPersistenceRequired();
                target.moveTo(6.5, 80, 0.5, 90, 0); target.setNoAi(true); target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); target.setHealth(10000);
                caster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); caster.setHealth(5000);
                world.addFreshEntity(caster); world.addFreshEntity(target);
                if (!(caster instanceof IMagicEntity)) throw new IllegalStateException("Native Iron NPC bridge absent");
                caster.setTarget(target); MagicCombatHandler.face(caster, target);
                ticks = 0; cast = null; effect = false; result = "REJECTED"; spawned.clear();
                duration = Math.max(120, Math.min(500, IronAdapter.getSpell(ids.get(index)).getEffectiveCastTime(1, caster) + 60));
            }
            MagicCombatHandler.face(caster, target);
            if (ticks == 4) {
                var attempt = CastingService.start(caster, ids.get(index), 1, SpellTarget.entity(target), "verify_iron_catalog");
                cast = attempt.cast(); result = cast == null ? "REJECTED" : "ACCEPTED";
                CNPCMagic.LOGGER.info("IRON CATALOG CAST id={} code={}", ids.get(index), attempt.result().code());
            }
            if (cast != null && cast.tick(caster, target)) { result = cast.wasSuccessful() ? "COMPLETED" : "FAILED"; cast = null; }
            effect |= target.getHealth() < 10000 || caster.getHealth() > 5000 || !target.getActiveEffects().isEmpty()
                || !caster.getActiveEffects().isEmpty() || caster.position().distanceToSqr(0.5, 80, 0.5) > 4;
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("IRON CATALOG FAIL id=" + (index >= 0 && index < ids.size() ? ids.get(index) : "setup"), error);
            world = null;
        }
    }
}

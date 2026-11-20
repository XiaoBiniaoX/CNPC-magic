package top.cnpcmagic.verify;

import com.binaris.wizardry.content.entity.construct.FireSigilConstruct;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.casting.CastingService;
import top.cnpcmagic.casting.SpellTarget;
import top.cnpcmagic.provider.MagicProviders;

/** Isolated native Wizardry construct probe; active only in the dedicated verification launch. */
public final class WizardryVerification {
    private static ServerLevel fixtureLevel;
    private static EntityNPCInterface fixtureCaster;
    private static Pig fixtureTarget;
    private static boolean pending;

    private WizardryVerification() {
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try {
            if (MagicProviders.get("ebwizardry") == null) {
                throw new IllegalStateException("Wizardry provider is unavailable");
            }
            ServerLevel level = event.getServer().overworld();
            BlockPos origin = new BlockPos(0, 80, 0);
            for (int x = -4; x <= 12; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            EntityNPCInterface caster = CustomEntities.entityCustomNpc.create(level);
            Pig target = EntityType.PIG.create(level);
            if (caster == null || target == null) {
                throw new IllegalStateException("Wizardry fixture entities are unavailable");
            }
            caster.moveTo(0.5, 80, 0.5, -90, 0);
            caster.setNoAi(true);
            caster.setPersistenceRequired();
            target.moveTo(6.5, 80, 0.5, 90, 0);
            target.setNoAi(true);
            target.setPersistenceRequired();
            level.addFreshEntity(caster);
            level.addFreshEntity(target);

            fixtureLevel = level;
            fixtureCaster = caster;
            fixtureTarget = target;
            pending = true;
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("WIZARDRY FAIL", error);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) {
            return;
        }
        pending = false;
        try {
            EntityNPCInterface caster = fixtureCaster;
            Pig target = fixtureTarget;
            ServerLevel level = fixtureLevel;
            if (caster == null || target == null || level == null) {
                throw new IllegalStateException("Wizardry fixture entities are unavailable after startup");
            }

            String spell = "ebwizardry:fire_sigil";
            if (!MagicProviders.get("ebwizardry").isValidSpell(spell)) {
                throw new IllegalStateException("fire_sigil is not valid for entity casts");
            }
            var hit = level.clip(new ClipContext(caster.getEyePosition(1), target.position(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            CastingService.Attempt distant = CastingService.start(caster, spell, 1,
                SpellTarget.entity(target), "verify_wizardry");
            if (hit.getType() != HitResult.Type.MISS || hit.getBlockPos().equals(caster.blockPosition())
                || distant.cast() == null || !distant.cast().tick(caster, target) || !distant.cast().wasSuccessful()
                || level.getEntitiesOfClass(FireSigilConstruct.class, new AABB(target.blockPosition()).inflate(4.0)).size() != 1) {
                throw new IllegalStateException("fire_sigil six-block unobstructed cast did not create exactly one native construct");
            }
            CNPCMagic.LOGGER.info("WIZARDRY PASS fire_sigil six-block native construct spawned distance={} rayType={} rayBlock={} constructs=1",
                caster.distanceTo(target), hit.getType(), hit.getBlockPos());

            int constructs = level.getEntitiesOfClass(FireSigilConstruct.class,
                new AABB(target.blockPosition()).inflate(4.0)).size();
            CastingService.Attempt overlap = CastingService.start(caster, spell, 1,
                SpellTarget.entity(target), "verify_wizardry");
            if (overlap.cast() != null || !overlap.result().code().equals("CAST_REJECTED")
                || level.getEntitiesOfClass(FireSigilConstruct.class, new AABB(target.blockPosition()).inflate(4.0)).size() != constructs) {
                throw new IllegalStateException("fire_sigil native overlap check was not preserved");
            }
            CNPCMagic.LOGGER.info("WIZARDRY PASS fire_sigil native overlap rejected constructs={}", constructs);
            level.getEntitiesOfClass(FireSigilConstruct.class, new AABB(target.blockPosition()).inflate(4.0))
                .forEach(net.minecraft.world.entity.Entity::discard);

            for (String test : new String[]{"wall", "range", "floor"}) {
                if (test.equals("wall")) {
                    for (int y = 80; y <= 82; y++) level.setBlock(new BlockPos(3, y, 0), Blocks.STONE.defaultBlockState(), 3);
                } else if (test.equals("range")) {
                    for (int y = 80; y <= 82; y++) level.setBlock(new BlockPos(3, y, 0), Blocks.AIR.defaultBlockState(), 3);
                    target.moveTo(10.5, 80, 0.5, 90, 0);
                } else {
                    target.moveTo(6.5, 80, 0.5, 90, 0);
                    for (int y = 76; y <= 79; y++) level.setBlock(new BlockPos(6, y, 0), Blocks.AIR.defaultBlockState(), 3);
                }
                var ray = level.clip(new ClipContext(caster.getEyePosition(1), target.position(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
                CastingService.Attempt rejected = CastingService.start(caster, spell, 1,
                    SpellTarget.entity(target), "verify_wizardry");
                if (rejected.cast() != null || !rejected.result().code().equals("CAST_REJECTED")
                    || !level.getEntitiesOfClass(FireSigilConstruct.class, new AABB(target.blockPosition()).inflate(4.0)).isEmpty()
                    || test.equals("wall") && ray.getType() != HitResult.Type.BLOCK
                    || !test.equals("wall") && ray.getType() != HitResult.Type.MISS) {
                    throw new IllegalStateException("fire_sigil native " + test + " rejection was not preserved");
                }
                CNPCMagic.LOGGER.info("WIZARDRY PASS fire_sigil native {} rejected distance={} rayType={} constructs=0",
                    test, caster.distanceTo(target), ray.getType());
            }
            CNPCMagic.LOGGER.info("WIZARDRY COMPLETE");
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("WIZARDRY FAIL", error);
        }
    }
}

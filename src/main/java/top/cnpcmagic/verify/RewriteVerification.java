package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import noppes.npcs.CustomEntities;
import noppes.npcs.CustomNpcs;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.controllers.Jsr223Executor;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.casting.*;
import top.cnpcmagic.provider.MagicProviders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small, tick-driven dedicated-server regression; never registered in normal games. */
public final class RewriteVerification {
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static EntityNPCInterface caster;
    private static ServerLevel world;
    private static int ticks;
    private static boolean reported;

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        try {
            world = event.getServer().overworld(); ticks = 0; reported = false; TRIALS.clear();
            check(event.getServer().getLevel(top.cnpcmagic.server.MagicPreview.DIMENSION) != null, "preview dimension registered");
            for (String id : List.of("irons_spellbooks", "goety", "ebwizardry", "ars_nouveau"))
                check((MagicProviders.get(id) != null) == ModList.get().isLoaded(id), "optional provider " + id);
            PersistenceVerification.run(event.getServer(), Boolean.getBoolean("cnpcmagic.verify.reload"));
            SpellLibrary library = SpellLibrary.get(event.getServer());
            if (Boolean.getBoolean("cnpcmagic.verify.reload")) {
                check(library.spells.containsKey("cnpcmagic:verify_instant"), "custom definition survives restart");
                check(library.spells.size() >= 16, "builtins and custom definitions retained");
                CNPCMagic.LOGGER.info("REWRITE PASS reload: custom library and preview dimension; providers={}", MagicProviders.all().stream().map(p -> p.getId()).toList());
                reported = true; return;
            }
            for (int x = -1; x <= 4; x++) for (int z = -1; z <= 1; z++) world.setChunkForced(x, z, true);
            for (int x = -2; x <= 62; x++) for (int z = -2; z <= 5; z++) world.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
            ConditionVerification.run(world);
            caster = CustomEntities.entityCustomNpc.create(world);
            check(caster != null, "NPC created"); caster.moveTo(0, 80, 0, -90, 0); caster.setNoAi(true); caster.setPersistenceRequired(); world.addFreshEntity(caster);
            int x = 4;
            for (SpellDefinition.Delivery delivery : SpellDefinition.Delivery.values()) {
                LivingEntity target = EntityType.HUSK.create(world);
                check(target != null, "target created"); target.moveTo(x, 80, 0, 0, 0); target.setNoGravity(true); ((net.minecraft.world.entity.Mob)target).setNoAi(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100); world.addFreshEntity(target);
                SpellDefinition d = SpellDefinition.template("verify_" + delivery.name().toLowerCase(java.util.Locale.ROOT), delivery,
                    delivery == SpellDefinition.Delivery.SUMMON ? SpellDefinition.EffectType.SUMMON : delivery == SpellDefinition.Delivery.SELF ? SpellDefinition.EffectType.HEALING : SpellDefinition.EffectType.DAMAGE);
                d.castTicks = 0; d.cooldownTicks = 5; d.duration = 30; d.range = 64; d.lineOfSight = false; d.radius = 1;
                if (delivery == SpellDefinition.Delivery.PROJECTILE || delivery == SpellDefinition.Delivery.RAY || delivery == SpellDefinition.Delivery.CHANNEL) target.setPos(4, 80, x);
                if (delivery == SpellDefinition.Delivery.SUMMON) d.effects.get(0).duration = 15;
                library.spells.put(d.id, SpellDefinition.load(d.save()));
                SpellTarget descriptor = delivery == SpellDefinition.Delivery.SELF ? SpellTarget.self(caster) : SpellTarget.entity(target);
                if (delivery == SpellDefinition.Delivery.AREA) descriptor = SpellTarget.area(target.getX(), target.getY(), target.getZ(), 1);
                if (delivery == SpellDefinition.Delivery.GROUND || delivery == SpellDefinition.Delivery.SUMMON) descriptor = SpellTarget.position(target.getX(), target.getY(), target.getZ());
                var attempt = CastingService.start(caster, d.id, 1, descriptor, "verification");
                check(attempt.result().isSuccess() && attempt.cast() != null, "accepted " + delivery);
                check(CastingService.start(caster, d.id, 1, descriptor, "verification").result().code().equals("COOLDOWN"), "shared cooldown " + delivery);
                TRIALS.add(new Trial(d, target, attempt)); x += 6;
            }
            library.setDirty();
            check(CastingService.start(caster, "missing:spell", 1, SpellTarget.none(), "verification").result().code().equals("ADAPTER_UNAVAILABLE"), "missing adapter");
            check(CastingService.start(caster, "cnpcmagic:magic_ray", 1, SpellTarget.direction(0, 0, 0), "verification").result().code().equals("INVALID_TARGET"), "zero direction");
            check(CastingService.start(caster, "cnpcmagic:magic_ray", 1, SpellTarget.position(Double.NaN, 0, 0), "verification").result().code().equals("INVALID_TARGET"), "NaN coordinates");
            var cancelled = CastingService.start(caster, "cnpcmagic:channel_beam", 1, SpellTarget.entity(TRIALS.get(0).target), "verification");
            check(cancelled.cast() != null, "cancellable channel"); cancelled.cast().cancel(caster);
            check(CastingService.result(cancelled.result().getInstanceId()).state().equals("CANCELLED"), "cancel terminal state");
            var vetoed = CastingService.start(caster, "cnpcmagic:magic_ray", 1, SpellTarget.none(), "verify_veto");
            check(vetoed.result().code().equals("CANCELLED"), "event veto is synchronous");
            var forgeScripts = ScriptController.Instance.forgeScripts;
            boolean enabled = forgeScripts.getEnabled(), scripting = CustomNpcs.EnableScripting, started = ScriptController.HasStart;
            String language = forgeScripts.getLanguage();
            ScriptContainer script = new ScriptContainer(forgeScripts);
            script.script = "function spellPreCast(event) { if(event.context.source()=='verify_script_veto') event.setCanceled(true); }";
            forgeScripts.getScripts().add(script); forgeScripts.setLanguage("ECMAScript"); forgeScripts.setEnabled(true);
            CustomNpcs.EnableScripting = true; ScriptController.HasStart = true;
            try {
                var scriptVetoed = CastingService.start(caster, "cnpcmagic:magic_ray", 1, SpellTarget.none(), "verify_script_veto");
                check(!script.isErrored() && scriptVetoed.result().code().equals("CANCELLED"), "CNPC Forge script veto is synchronous");
            } finally {
                forgeScripts.getScripts().remove(script); forgeScripts.setEnabled(enabled); forgeScripts.setLanguage(language);
                CustomNpcs.EnableScripting = scripting; ScriptController.HasStart = started;
            }
            Jsr223Executor executor = new Jsr223Executor();
            try {
                executor.initialize("ECMAScript", Map.of("npc", NpcAPI.Instance().getIEntity(caster)));
                executor.setScript("function verify(event) { var r=npc.castSpellResult('missing:spell',1,npc); if(r.getCode()!='ADAPTER_UNAVAILABLE') throw 'result getter'; if(npc.findSpells('verify_instant').length!=1) throw 'query'; }");
                String output = executor.run("verify", null); check(!executor.isErrored(), "direct script structured result/query: " + output);
            } finally { executor.close(); }
            CNPCMagic.LOGGER.info("REWRITE PASS validation, cooldown, cancel, synchronous Forge/CNPC-script veto, direct ECMAScript; providers={}", MagicProviders.all().stream().map(p -> p.getId()).toList());
        } catch (RuntimeException | LinkageError error) { reported = true; CNPCMagic.LOGGER.error("REWRITE FAIL setup", error); }
    }

    @SubscribeEvent public static void veto(SpellEvent.Before event) {
        if (event.context.source().equals("verify_veto") && event.phase.equals("spellPreCast")) event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || reported || caster == null) return;
        try {
            ticks++;
            for (Trial trial : TRIALS) if (!trial.done) trial.done = trial.attempt.cast().tick(caster, trial.target);
            if (ticks < 100) return;
            for (Trial trial : TRIALS) {
                check(trial.done && trial.attempt.cast().wasSuccessful(), "delivery completes " + trial.definition.delivery);
                check(CastingService.result(trial.attempt.result().getInstanceId()).state().equals("COMPLETED"), "retained terminal result " + trial.definition.delivery);
                if (trial.definition.effects.get(0).type == SpellDefinition.EffectType.DAMAGE) check(trial.target.getHealth() < 100, "real damage " + trial.definition.delivery);
            }
            boolean orphan = false;
            for (var entity : world.getAllEntities()) if (!top.cnpcmagic.data.MagicRuntimeData.get(world.getServer()).getSummon(entity.getUUID()).isEmpty() || entity instanceof BuiltinSpell.Bolt) orphan = true;
            check(!orphan, "projectile and expired summon cleanup");
            check(!caster.saveWithoutId(new CompoundTag()).toString().contains("verify_instant"), "definitions outside NPC NBT");
            reported = true; CNPCMagic.LOGGER.info("REWRITE PASS eight deliveries: actual damage, terminal states, projectile/summon expiry and NBT isolation");
            CNPCMagic.LOGGER.info("REWRITE COMPLETE");
        } catch (RuntimeException | LinkageError error) { reported = true; CNPCMagic.LOGGER.error("REWRITE FAIL tick", error); }
    }

    private static final class Trial {
        final SpellDefinition definition; final LivingEntity target; final CastingService.Attempt attempt; boolean done;
        Trial(SpellDefinition d, LivingEntity target, CastingService.Attempt attempt) { definition = d; this.target = target; this.attempt = attempt; }
    }
}

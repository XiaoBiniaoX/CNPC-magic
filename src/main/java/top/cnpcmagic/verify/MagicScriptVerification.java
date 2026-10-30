package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.CustomBlocks;
import noppes.npcs.CustomEntities;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlockScripted;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.event.BlockEvent;
import noppes.npcs.api.event.ItemEvent;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.item.IItemScripted;
import noppes.npcs.controllers.Jsr223Executor;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.server.ScriptMagic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real CNPC ECMAScript executor + real entities; event objects are injected, without desktop input. */
public final class MagicScriptVerification {
    private static final List<Trial> TRIALS = new ArrayList<>();
    private static long start;
    private static boolean deadline;
    private static net.minecraft.server.level.ServerPlayer viewer;
    private static long viewStart;
    private static int viewStage;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (Boolean.getBoolean("cnpcmagic.verify.reload")) return;
        TRIALS.clear();
        start = event.getServer().overworld().getGameTime();
        deadline = false;
        viewer = null;
        viewStage = 0;
        ServerLevel world = event.getServer().overworld();
        var guardNpc = CustomEntities.entityCustomNpc.create(world);
        guardNpc.moveTo(210.5, 80, 0.5, 0, 0);
        guardNpc.setNoAi(true);
        guardNpc.setInvulnerable(true);
        guardNpc.setPersistenceRequired();
        world.addFreshEntity(guardNpc);
        directGuards("NPC", new NpcEvent((ICustomNpc) NpcAPI.Instance().getIEntity(guardNpc)), "event.npc", guardNpc);
        directGuards("block", blockEvent(world, 210, guardNpc), "event.entity", guardNpc);
        int x = 220;
        for (String provider : List.of("irons_spellbooks", "goety")) {
            if (MagicProviders.get(provider) == null) continue;
            for (String context : List.of("NPC", "block")) {
                var npc = CustomEntities.entityCustomNpc.create(world);
                npc.moveTo(x + 0.5, 80, 0.5, 0, 0);
                npc.setNoAi(true);
                npc.setInvulnerable(true);
                npc.setPersistenceRequired();
                npc.display.setName("script " + context + " " + provider);
                world.addFreshEntity(npc);
                Object scriptEvent = context.equals("NPC")
                    ? new NpcEvent((ICustomNpc) NpcAPI.Instance().getIEntity(npc))
                    : blockEvent(world, x, npc);
                String spell = provider + (context.equals("NPC")
                    ? (provider.equals("goety") ? ":soul_bolt_focus" : ":firebolt")
                    : (provider.equals("goety") ? ":fire_breath_focus" : ":fire_breath"));
                add(npc, spell, context, scriptEvent, context.equals("NPC") ? "event.npc" : "event.entity", x + 0.5, 3.5);
                x += 20;
            }
        }
    }

    private static BlockEvent.CollidedEvent blockEvent(ServerLevel world, int x, LivingEntity caster) {
        BlockPos pos = new BlockPos(x, 79, 0);
        world.setBlock(pos, CustomBlocks.scripted.defaultBlockState(), 3);
        var block = NpcAPI.Instance().getIBlock(world, pos);
        if (!(block instanceof IBlockScripted)) throw new IllegalStateException("Not a real CNPC scripted block");
        return new BlockEvent.CollidedEvent(block, caster);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("cnpcmagic.verify.reload")) return;
        var player = event.getEntity();
        viewer = (net.minecraft.server.level.ServerPlayer) player;
        viewStart = player.level().getGameTime();
        IPlayer wrapper = (IPlayer) NpcAPI.Instance().getIEntity(player);
        directGuards("Player", new noppes.npcs.api.event.PlayerEvent(wrapper), "event.player", player);
        directGuards("item", new ItemEvent.UpdateEvent((IItemScripted) wrapper.getWorld().createItem("customnpcs:scripted_item", 1), wrapper),
            "event.player", player);
        for (String provider : List.of("irons_spellbooks", "goety")) {
            if (MagicProviders.get(provider) == null) continue;
            add(player, provider + (provider.equals("goety") ? ":magic_bolt_focus" : ":firebolt"), "Player",
                new noppes.npcs.api.event.PlayerEvent(wrapper), "event.player", player.getX() - 2, 2.5);
            add(player, provider + (provider.equals("goety") ? ":prisma_beam_focus" : ":ray_of_siphoning"), "item",
                new ItemEvent.UpdateEvent((IItemScripted) wrapper.getWorld().createItem("customnpcs:scripted_item", 1), wrapper),
                "event.player", player.getX() - 2, 5.5);
        }
    }

    private static Pig target(ServerLevel world, double x, double z) {
        Pig target = EntityType.PIG.create(world);
        target.moveTo(x, 80, z, 0, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        target.setHealth(10000);
        world.addFreshEntity(target);
        return target;
    }

    private static void add(LivingEntity caster, String spell, String context, Object event, String expression, double x, double z) {
        Pig target = target((ServerLevel) caster.level(), x, z);
        Jsr223Executor executor = new Jsr223Executor();
        executor.initialize("ECMAScript", Map.of("target", NpcAPI.Instance().getIEntity(target)));
        String receiver = receiver(context);
        String args = carrier(context) ? expression + ", " : "";
        String casterArg = carrier(context) ? expression : "";
        String cast = receiver + ".castSpell(" + args + "'" + spell + "', 1, target)";
        String casting = receiver + ".isCastingSpell(" + casterArg + ")";
        String cancel = receiver + ".cancelSpell(" + casterArg + ")";
        executor.setScript("function verify(event) { "
            + "if (" + receiver + ".castSpell(" + args + "'" + spell + "', 0, target)) throw 'invalid level accepted'; "
            + "if (!" + cast + " || !" + cancel + " || " + casting + " || " + cancel + ") throw 'cancel/cleanup failed'; "
            + "if (!" + cast + ") throw 'cast rejected'; "
            + "if (!" + casting + " || " + cast + ") throw 'busy guard failed'; }");
        TRIALS.add(new Trial(caster, target, spell, context, event, executor));
    }

    private static boolean carrier(String context) {
        return context.equals("block") || context.equals("item");
    }

    private static String receiver(String context) {
        return "event." + (context.equals("NPC") ? "npc" : context.equals("Player") ? "player" : context);
    }

    private static void directGuards(String context, Object event, String expression, LivingEntity caster) {
        Jsr223Executor executor = new Jsr223Executor();
        executor.initialize("ECMAScript", Map.of("target", NpcAPI.Instance().getIEntity(caster)));
        String receiver = receiver(context);
        String args = carrier(context) ? expression + ", " : "";
        String casterArg = carrier(context) ? expression : "";
        String script = "function verify(event) { if (" + receiver + ".isCastingSpell(" + casterArg + ") || "
            + receiver + ".cancelSpell(" + casterArg + ")) throw 'idle state invalid'; "
            + "if (" + receiver + ".castSpell(" + args + "'missing:spell', 1, target)) throw 'missing spell accepted'; ";
        for (String provider : List.of("irons_spellbooks", "goety")) {
            if (MagicProviders.get(provider) != null) continue;
            String spell = provider + (provider.equals("goety") ? ":soul_bolt_focus" : ":firebolt");
            script += "if (" + receiver + ".castSpell(" + args + "'" + spell + "', 1, target)) throw 'missing provider accepted'; ";
        }
        if (carrier(context)) script += "if (" + receiver + ".castSpell(null, 'missing:spell', 1, target)) throw 'null caster accepted'; ";
        executor.setScript(script + "}");
        String output = executor.run("verify", event);
        CNPCMagic.LOGGER.info("VERIFY {} script direct methods {} invalid/missing-provider guards: {}",
            executor.isErrored() ? "FAIL" : "PASS", context, output);
        executor.close();
    }

    private static boolean nativeTargetIsolated(Trial trial) {
        if (!trial.spell.startsWith("goety:") || !trial.context.equals("item")) return true;
        try {
            Class<?> type = Class.forName("com.Polarice3.Goety.common.magic.spells.abyss.PrismaBeamSpell");
            Object spell = type.getConstructor().newInstance();
            Object other = type.getMethod("getTarget", LivingEntity.class).invoke(spell, trial.caster);
            return other != null && other != trial.target && ScriptMagic.getCastTarget(trial.caster) == null;
        } catch (ReflectiveOperationException e) {
            CNPCMagic.LOGGER.error("VERIFY FAIL native target isolation", e);
            return false;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Boolean.getBoolean("cnpcmagic.verify.reload")) return;
        for (Trial trial : TRIALS) {
            if (trial.reported) continue;
            trial.target.setPos(trial.anchor);
            trial.target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (!trial.started) {
                if (ScriptMagic.isCasting(trial.caster)) continue;
                trial.started = true;
                String output = trial.executor.run("verify", trial.event);
                if (trial.executor.isErrored() || !ScriptMagic.isCasting(trial.caster) || !nativeTargetIsolated(trial)) {
                    trial.reported = true;
                    CNPCMagic.LOGGER.error("VERIFY FAIL script {} {}: {}", trial.context, trial.spell, output);
                } else {
                    CNPCMagic.LOGGER.info("VERIFY PASS script guards/cancel/scope {} {} direct-wrapper=true", trial.context, trial.spell);
                }
            } else if (!ScriptMagic.isCasting(trial.caster)) {
                if (++trial.after < 12) continue; // Allow the actual projectile to reach its target.
                float damage = 10000 - trial.target.getHealth();
                trial.reported = true;
                CNPCMagic.LOGGER.info("VERIFY {} script {} {} damage={}", damage > 0 ? "PASS" : "FAIL", trial.context, trial.spell, damage);
                trial.executor.close();
            }
        }
        // Inspect continuous poses as well as the initial instant spell, using in-game server teleports only.
        if (viewer != null && !viewer.isRemoved() && TRIALS.stream().allMatch(t -> t.reported)) {
            long elapsed = viewer.level().getGameTime() - viewStart;
            boolean goety = MagicProviders.get("goety") != null;
            boolean iron = MagicProviders.get("irons_spellbooks") != null;
            int x = goety ? 140 : 40;
            if (viewStage == 0 && elapsed >= 200) {
                viewer.connection.teleport(x + 6.5, 80, -2.5, 60, 5);
                viewStage++;
            } else if (viewStage == 1 && elapsed >= 400 && goety && iron) {
                viewer.connection.teleport(46.5, 80, -2.5, 60, 5);
                viewStage++;
            }
        }
        if (!deadline && !TRIALS.isEmpty() && TRIALS.get(0).caster.level().getGameTime() - start >= 1100) {
            deadline = true;
            boolean all = TRIALS.stream().allMatch(t -> t.reported);
            CNPCMagic.LOGGER.info("VERIFY {} script completed contexts={} (CNPC ECMAScript executor)", all ? "PASS" : "FAIL", TRIALS.size());
        }
    }

    private static final class Trial {
        final LivingEntity caster;
        final Pig target;
        final String spell;
        final String context;
        final Object event;
        final Jsr223Executor executor;
        final net.minecraft.world.phys.Vec3 anchor;
        boolean started;
        boolean reported;
        int after;

        Trial(LivingEntity caster, Pig target, String spell, String context, Object event, Jsr223Executor executor) {
            this.caster = caster;
            this.target = target;
            this.spell = spell;
            this.context = context;
            this.event = event;
            this.executor = executor;
            anchor = target.position();
        }
    }
}

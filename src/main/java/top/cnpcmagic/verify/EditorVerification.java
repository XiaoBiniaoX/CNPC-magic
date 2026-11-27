package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.data.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated real-network UI fixture, explicitly enabled by the screenshot launcher. */
public final class EditorVerification {
    private static EntityNPCInterface npc;
    private static ServerPlayer pendingPlayer;
    private static net.minecraft.server.level.ServerLevel preview;
    private static final java.util.Set<java.util.UUID> enraged = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> nativeEffects = new java.util.HashSet<>();
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var world = event.getServer().overworld();
        preview = event.getServer().getLevel(top.cnpcmagic.server.MagicPreview.DIMENSION);
        for (int x = -8; x <= 12; x++) for (int z = -8; z <= 8; z++) world.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
        npc = CustomEntities.entityCustomNpc.create(world);
        if (npc == null) throw new IllegalStateException("Editor fixture NPC unavailable");
        npc.moveTo(0.5, 80, 0.5, -90, 0); npc.display.setName("Magic editor verification");
        npc.setNoAi(true); npc.setPersistenceRequired(); npc.setInvulnerable(true); world.addFreshEntity(npc);
        MagicRule combo = new MagicRule(); combo.enabled = true; combo.spellId = "cnpcmagic:magic_ray"; combo.range = 32;
        combo.conditions.enabled.clear(); combo.conditions.enabled.add(RuleConditions.Type.PREVIOUS_SPELL);
        combo.conditions.previousSpell = "cnpcmagic:magic_missile"; combo.conditions.casterHealth = 0; combo.conditions.targetHealth = 0;
        MagicRule basic = new MagicRule(); basic.enabled = true; basic.spellId = "cnpcmagic:magic_missile"; basic.range = 32;
        Map<String, List<MagicRule>> rules = new LinkedHashMap<>();
        rules.put("cnpcmagic", List.of(combo, basic));
        if (top.cnpcmagic.provider.MagicProviders.get("irons_spellbooks") != null) {
            MagicRule iron = new MagicRule(); iron.spellId = "irons_spellbooks:firebolt"; iron.independent = true; iron.sequence = 70;
            rules.put("irons_spellbooks", List.of(iron));
        }
        if (top.cnpcmagic.provider.MagicProviders.get("goety") != null) {
            MagicRule goety = new MagicRule(); goety.spellId = "goety:soul_bolt_focus";
            rules.put("goety", List.of(goety));
        }
        MagicDataStore.get(event.getServer()).apply(npc, rules);
        world.setDefaultSpawnPos(new BlockPos(5, 80, -3), 60);
        top.cnpcmagic.CNPCMagic.LOGGER.info("EDITOR SERVER READY");
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 4, false));
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(player.server.overworld(), 5.5, 80, -3.5, 55, 5);
        pendingPlayer = player;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (pendingPlayer != null && pendingPlayer.tickCount >= 40) {
            NoppesUtilServer.setEditingNpc(pendingPlayer, npc);
            top.cnpcmagic.CNPCMagic.LOGGER.info("EDITOR SERVER PERMISSION level4={} editingNpc={}",
                pendingPlayer.hasPermissions(4), NoppesUtilServer.getEditingNpc(pendingPlayer) == npc);
            pendingPlayer = null;
        }
        if (preview == null) return;
        for (var entity : preview.getAllEntities()) if (entity instanceof EntityNPCInterface caster
            && caster.getTarget() instanceof net.minecraft.world.entity.Mob target
            && target.getLastHurtByMob() == caster && target.getTarget() == caster && enraged.add(caster.getUUID()))
            top.cnpcmagic.CNPCMagic.LOGGER.info("EDITOR SERVER NATIVE enrage actual retaliation caster={} target={}", caster.getUUID(), target.getUUID());
    }
}

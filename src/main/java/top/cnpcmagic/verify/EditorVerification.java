package top.cnpcmagic.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var world = event.getServer().overworld();
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
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.server.getPlayerList().op(player.getGameProfile()); player.setGameMode(GameType.CREATIVE);
        player.teleportTo(player.server.overworld(), 5.5, 80, -3.5, 55, 5);
        NoppesUtilServer.setEditingNpc(player, npc);
    }
}

package top.cnpcmagic.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketGuiData;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;

import java.util.List;

public final class MagicSync {
    private MagicSync() {
    }

    public static void sendFull(ServerPlayer player) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) {
            return;
        }
        MagicDataStore store = MagicDataStore.get(player.server);
        CompoundTag providers = new CompoundTag();
        for (MagicProvider provider : MagicProviders.all()) {
            providers.put(provider.getId(), MagicDataStore.saveRules(store.getRules(npc.getUUID(), provider.getId())));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("providers", providers);
        Packets.send(player, new PacketGuiData(tag));
    }

    public static void save(ServerPlayer player, CompoundTag tag) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null) {
            return;
        }
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) {
            return;
        }
        MagicDataStore store = MagicDataStore.get(player.server);
        CompoundTag providers = tag.getCompound("providers");
        for (MagicProvider provider : MagicProviders.all()) {
            if (!providers.contains(provider.getId())) {
                continue;
            }
            List<MagicRule> rules = MagicDataStore.loadRules(providers.get(provider.getId()));
            rules.forEach(rule -> rule.sanitize(provider));
            store.setRules(npc.getUUID(), provider.getId(), rules);
        }
        MagicCombatHandler.reset(npc);
    }
}

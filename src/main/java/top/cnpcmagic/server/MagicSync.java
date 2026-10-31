package top.cnpcmagic.server;

import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;

public final class MagicSync {
    private MagicSync() {
    }

    public static void send(EntityPlayerMP player) {
        EntityNPCInterface npc = editable(player);
        if (npc == null) return;

        NBTTagCompound providers = new NBTTagCompound();
        providers.setTag(MagicDataStore.PROVIDER_ID,
            MagicDataStore.writeRules(MagicDataStore.get(player.getServer()).getRules(npc)));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("providers", providers);
        Server.sendDataChecked(player, EnumPacketClient.GUI_DATA, root);
    }

    public static void save(EntityPlayerMP player, NBTTagCompound root) {
        EntityNPCInterface npc = editable(player);
        if (npc == null || !root.hasKey("providers", 10)) return;

        NBTTagCompound providers = root.getCompoundTag("providers");
        if (!providers.hasKey(MagicDataStore.PROVIDER_ID)) return;
        List<MagicRule> rules = MagicDataStore.readRules(providers.getTag(MagicDataStore.PROVIDER_ID));
        for (MagicRule rule : rules) rule.sanitize(npc);
        MagicDataStore.get(player.getServer()).setRules(npc, rules);
        MagicCombatHandler.reset(npc);
    }

    private static EntityNPCInterface editable(EntityPlayerMP player) {
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) return null;
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null || npc.world != player.world || npc.isDead) return null;
        return npc;
    }
}

package top.cnpcmagic.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class MagicDataStore extends WorldSavedData {
    public static final String PROVIDER_ID = "ebwizardry";
    public static final int MAX_RULES = 200;
    private static final String DATA_NAME = "cnpcmagic_npc_data";

    private final Map<UUID, List<MagicRule>> data = new HashMap<>();

    public MagicDataStore() {
        this(DATA_NAME);
    }

    public MagicDataStore(String name) {
        super(name);
    }

    public static MagicDataStore get(MinecraftServer server) {
        World world = server.getWorld(0);
        MapStorage storage = world.getMapStorage();
        MagicDataStore store = (MagicDataStore) storage.getOrLoadData(MagicDataStore.class, DATA_NAME);
        if (store == null) {
            store = new MagicDataStore();
            storage.setData(DATA_NAME, store);
        }
        return store;
    }

    public List<MagicRule> getRules(UUID npcId) {
        List<MagicRule> rules = data.get(npcId);
        return rules == null ? Collections.emptyList() : rules;
    }

    public void setRules(UUID npcId, List<MagicRule> rules) {
        if (rules.isEmpty()) {
            data.remove(npcId);
        } else {
            data.put(npcId, new ArrayList<>(rules));
        }
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        data.clear();
        NBTTagCompound npcs = root.getCompoundTag("npcs");
        for (String key : npcs.getKeySet()) {
            try {
                List<MagicRule> rules = readRules(npcs.getTag(key));
                if (!rules.isEmpty()) data.put(UUID.fromString(key), rules);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID keys from old or manually edited data.
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound root) {
        NBTTagCompound npcs = new NBTTagCompound();
        for (Map.Entry<UUID, List<MagicRule>> entry : data.entrySet()) {
            npcs.setTag(entry.getKey().toString(), writeRules(entry.getValue()));
        }
        root.setTag("npcs", npcs);
        return root;
    }

    public static List<MagicRule> readRules(net.minecraft.nbt.NBTBase tag) {
        List<MagicRule> rules = new ArrayList<>();
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            int count = Math.min(list.tagCount(), MAX_RULES);
            for (int i = 0; i < count; i++) rules.add(MagicRule.read(list.getCompoundTagAt(i)));
        } else if (tag instanceof NBTTagCompound) {
            rules.add(MagicRule.read((NBTTagCompound) tag));
        }
        return rules;
    }

    public static NBTTagList writeRules(List<MagicRule> rules) {
        NBTTagList list = new NBTTagList();
        int count = Math.min(rules.size(), MAX_RULES);
        for (int i = 0; i < count; i++) list.appendTag(rules.get(i).write());
        return list;
    }
}

package top.cnpcmagic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MagicDataStore extends SavedData {
    private static final String DATA_NAME = "cnpcmagic_npc_data";

    private final Map<UUID, Map<String, List<MagicRule>>> data = new HashMap<>();

    public MagicDataStore() {
    }

    public MagicDataStore(CompoundTag tag) {
        CompoundTag npcs = tag.getCompound("npcs");
        for (String uuidString : npcs.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag providers = npcs.getCompound(uuidString);
            Map<String, List<MagicRule>> rules = new HashMap<>();
            for (String providerId : providers.getAllKeys()) {
                rules.put(providerId, loadRules(providers.get(providerId)));
            }
            if (!rules.isEmpty()) {
                data.put(uuid, rules);
            }
        }
    }

    public static MagicDataStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            MagicDataStore::new, MagicDataStore::new, DATA_NAME
        );
    }

    public List<MagicRule> getRules(UUID npc, String providerId) {
        Map<String, List<MagicRule>> rules = data.get(npc);
        return rules == null ? List.of() : rules.getOrDefault(providerId, List.of());
    }

    public List<MagicRule> getRules(EntityNPCInterface npc, String providerId) {
        return getRules(dataId(npc), providerId);
    }

    public static UUID dataId(EntityNPCInterface npc) {
        if (npc instanceof MagicDataAccess access) {
            UUID reference = access.cnpcmagic$getDataId();
            if (reference != null) {
                return reference;
            }
        }
        return npc.getUUID();
    }

    /**
     * Saves a full provider snapshot for this NPC. Unchanged content keeps the current
     * snapshot; a real change writes a new one and moves the NPC's reference, so copies
     * already stored in soulstones or clone lists keep the configuration they captured.
     */
    public void apply(EntityNPCInterface npc, Map<String, List<MagicRule>> providers) {
        if (!(npc instanceof MagicDataAccess access)) {
            // Without the reference mixin, keep the old per-UUID behaviour instead of failing.
            for (Map.Entry<String, List<MagicRule>> entry : providers.entrySet()) {
                setRules(npc.getUUID(), entry.getKey(), entry.getValue());
            }
            return;
        }
        UUID current = dataId(npc);
        Map<String, List<MagicRule>> existing = normalize(data.get(current));
        Map<String, List<MagicRule>> merged = new HashMap<>(existing);
        for (Map.Entry<String, List<MagicRule>> entry : providers.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                merged.remove(entry.getKey());
            } else {
                merged.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        if (merged.equals(existing)) {
            return;
        }
        UUID id = UUID.randomUUID();
        if (!merged.isEmpty()) {
            data.put(id, merged);
        }
        access.cnpcmagic$setDataId(id);
        setDirty(true);
    }

    private static Map<String, List<MagicRule>> normalize(Map<String, List<MagicRule>> providers) {
        Map<String, List<MagicRule>> result = new HashMap<>();
        if (providers != null) {
            for (Map.Entry<String, List<MagicRule>> entry : providers.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    result.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
        }
        return result;
    }

    public void setRules(UUID npc, String providerId, List<MagicRule> rules) {
        data.computeIfAbsent(npc, k -> new HashMap<>()).put(providerId, List.copyOf(rules));
        setDirty(true);
    }

    public static List<MagicRule> loadRules(Tag tag) {
        List<MagicRule> rules = new ArrayList<>();
        if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof CompoundTag compound) {
                    rules.add(MagicRule.load(compound));
                }
            }
        } else if (tag instanceof CompoundTag oldRule) {
            // The first release stored one rule directly under the provider id.
            rules.add(MagicRule.load(oldRule));
        }
        return rules;
    }

    public static ListTag saveRules(List<MagicRule> rules) {
        ListTag list = new ListTag();
        for (MagicRule rule : rules) {
            list.add(rule.save());
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag npcs = new CompoundTag();
        for (Map.Entry<UUID, Map<String, List<MagicRule>>> entry : data.entrySet()) {
            CompoundTag providers = new CompoundTag();
            for (Map.Entry<String, List<MagicRule>> ruleEntry : entry.getValue().entrySet()) {
                providers.put(ruleEntry.getKey(), saveRules(ruleEntry.getValue()));
            }
            npcs.put(entry.getKey().toString(), providers);
        }
        tag.put("npcs", npcs);
        return tag;
    }
}

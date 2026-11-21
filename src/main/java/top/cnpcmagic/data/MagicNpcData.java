package top.cnpcmagic.data;

import bin.cnpcplus.api.NbtSeparationApi;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;

/** Bridges Magic's lightweight NPC reference to CNPCplus' external NBT store. */
public final class MagicNpcData {
    public static final String DATA_ID_KEY = "CNPCMagicDataId";

    private static NbtSeparationApi storage;

    private MagicNpcData() {}

    public static void register() {
        storage = NbtSeparationApi.register(
                new ResourceLocation("cnpcmagic", "npc_data"), DATA_ID_KEY);
    }

    public static void prepareLoad(EntityNPCInterface npc, NBTTagCompound tag) {
        storage.prepareLoad(npc, tag);
    }

    public static void saveNpc(EntityNPCInterface npc, NBTTagCompound tag) {
        storage.saveNpc(npc, tag);
    }
}

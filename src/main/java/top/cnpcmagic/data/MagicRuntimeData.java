package top.cnpcmagic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Recovery and temporary-entity metadata live outside player/entity NBT as well. */
public final class MagicRuntimeData extends CnpcPlusData {
    private final CompoundTag returns, summons, soulbound;
    private MagicRuntimeData() { this(new CompoundTag()); }
    private MagicRuntimeData(CompoundTag tag) {
        super("magic_runtime"); returns = tag.getCompound("returns").copy(); summons = tag.getCompound("summons").copy();
        soulbound = tag.getCompound("soulbound").copy();
    }
    public static MagicRuntimeData get(MinecraftServer server) {
        return CnpcPlusData.get(server, "cnpcmagic_runtime", "magic_runtime", MagicRuntimeData::new, MagicRuntimeData::new);
    }
    public CompoundTag getReturn(UUID player) { return returns.getCompound(player.toString()).copy(); }
    public void putReturn(UUID player, CompoundTag tag) {
        String key = player.toString(); var previous = returns.get(key); returns.put(key, tag.copy());
        try { flush(); }
        catch (IllegalStateException error) { if (previous == null) returns.remove(key); else returns.put(key, previous); throw error; }
    }
    public void removeReturn(UUID player) { returns.remove(player.toString()); setDirty(); flush(); }
    public CompoundTag getSummon(UUID entity) { return summons.getCompound(entity.toString()).copy(); }
    public void putSummon(UUID entity, CompoundTag tag) {
        String key = entity.toString(); var previous = summons.get(key); summons.put(key, tag.copy());
        try { flush(); }
        catch (IllegalStateException error) { if (previous == null) summons.remove(key); else summons.put(key, previous); throw error; }
    }
    public void removeSummon(UUID entity) { if (summons.contains(entity.toString())) { summons.remove(entity.toString()); setDirty(); } }
    public ListTag getSoulbound(UUID caster) { return soulbound.getList(caster.toString(), Tag.TAG_INT_ARRAY).copy(); }
    public void putSoulbound(UUID caster, ListTag targets) {
        if (targets.isEmpty()) soulbound.remove(caster.toString()); else soulbound.put(caster.toString(), targets.copy());
        setDirty();
    }
    public void removeSoulbound(UUID caster) { if (soulbound.contains(caster.toString())) { soulbound.remove(caster.toString()); setDirty(); } }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.put("returns", returns.copy()); tag.put("summons", summons.copy()); tag.put("soulbound", soulbound.copy()); tag.putInt("schema", 2); return tag;
    }
}

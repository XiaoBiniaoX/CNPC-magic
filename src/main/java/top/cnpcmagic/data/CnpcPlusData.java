package top.cnpcmagic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import noppes.npcs.CustomNpcs;
import top.cnpcmagic.CNPCMagic;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Uses CNPCplus 4.2.0's verified external store; SavedData supplies only the world lifecycle. */
public abstract class CnpcPlusData extends SavedData {
    private static final String REFERENCE = "CNPCMagicStoreId", STATE = "CNPCMagicStoreState", PAYLOAD = "CNPCMagicPayload";
    private static final Constructor<?> CONSTRUCTOR;
    private static final Method LOAD, SAVE;
    static {
        try {
            Class<?> type = Class.forName("top.cnpcplus.persist.NpcDataExternalStore");
            CONSTRUCTOR = type.getDeclaredConstructor(String.class, String.class, String.class, Predicate.class, Predicate.class);
            LOAD = type.getDeclaredMethod("prepareLoad", Object.class, UUID.class, CompoundTag.class, File.class);
            SAVE = type.getDeclaredMethod("saveNpc", Object.class, UUID.class, CompoundTag.class, File.class);
            CONSTRUCTOR.setAccessible(true); LOAD.setAccessible(true); SAVE.setAccessible(true);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private final String category;
    protected CnpcPlusData(String category) { this.category = category; }

    public static <T extends CnpcPlusData> T get(MinecraftServer server, String legacyName, String category,
                                                Function<CompoundTag, T> loader, Supplier<T> fresh) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> {
            CompoundTag external = read(category);
            T data = external == null ? fresh.get() : loader.apply(external); data.setDirty(); return data;
        }, (legacy, registries) -> {
            CompoundTag external = read(category);
            T data = loader.apply(external == null ? legacy : external); data.setDirty(); return data;
        }), legacyName);
    }
    public static File directory(String category) {
        File root = CustomNpcs.getLevelSaveDirectory();
        if (root == null) throw new IllegalStateException("CNPCplus storage root is unavailable");
        return new File(root, "data/cnpcplus/" + category);
    }
    private static UUID id(String category) { return UUID.nameUUIDFromBytes(("cnpcmagic:" + category).getBytes(StandardCharsets.UTF_8)); }
    public static File file(String category) { return new File(directory(category), id(category) + ".dat"); }
    private static Object store(String category) throws ReflectiveOperationException {
        return CONSTRUCTOR.newInstance(category, REFERENCE, STATE, (Predicate<String>) PAYLOAD::equals,
            (Predicate<CompoundTag>) tag -> tag.contains(PAYLOAD, Tag.TAG_COMPOUND));
    }
    private static CompoundTag read(String category) {
        if (!file(category).exists()) return null;
        CompoundTag tag = new CompoundTag(); tag.putString(REFERENCE, id(category).toString());
        boolean loaded;
        try { loaded = Boolean.TRUE.equals(LOAD.invoke(store(category), new Object(), id(category), tag, directory(category))); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not read CNPCplus " + category, error); }
        if (!loaded || !tag.contains(PAYLOAD, Tag.TAG_COMPOUND)) throw new IllegalStateException("Unreadable CNPCplus store; original retained: " + file(category));
        return tag.getCompound(PAYLOAD);
    }
    /** Returns only after CNPCplus has written and reread the complete payload successfully. */
    public final void flush() {
        setDirty();
        CompoundTag tag = new CompoundTag(); tag.putString(REFERENCE, id(category).toString());
        tag.put(PAYLOAD, save(new CompoundTag()));
        try { SAVE.invoke(store(category), this, id(category), tag, directory(category)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not save CNPCplus " + category, error); }
        if (tag.contains(PAYLOAD)) throw new IllegalStateException("CNPCplus did not verify the write; original retained: " + file(category));
        setDirty(false);
    }
    public abstract CompoundTag save(CompoundTag tag);
    @Override public final CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { return save(tag); }
    @Override public final void save(File ignoredLegacyFile, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        try { flush(); }
        catch (IllegalStateException error) { CNPCMagic.LOGGER.error("CNPCplus magic data remains dirty for retry", error); }
    }
}

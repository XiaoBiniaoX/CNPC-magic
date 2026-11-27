package top.cnpcmagic.api;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import top.cnpcmagic.casting.*;
import top.cnpcmagic.server.ScriptMagic;

import java.util.concurrent.CompletableFuture;

/** Public entry points. Entity/world operations belong to the logical server thread. */
public final class MagicApi {
    public static final int API_VERSION = 1;

    private MagicApi() { }

    /** Starts a managed cast; its ticks, target restoration and shutdown are owned by the mod. */
    public static SpellResult cast(LivingEntity caster, String spell, int level, SpellTarget target) {
        return ScriptMagic.castResult(caster, spell, level, target);
    }

    public static boolean cancel(LivingEntity caster) { return ScriptMagic.cancel(caster); }
    public static SpellResult result(String instanceId) { return CastingService.result(instanceId); }
    public static String[] find(LivingEntity caster, String query) { return ScriptMagic.query(caster, query); }

    /** Returns a detached definition, so editing it cannot mutate the live library. */
    public static CompoundTag definition(MinecraftServer server, String id) {
        if (!server.isSameThread()) throw new IllegalStateException("Read definitions on the server thread");
        SpellDefinition definition = SpellLibrary.get(server).spells.get(id);
        return definition == null ? new CompoundTag() : definition.save();
    }

    /** Copies on the caller's thread; worker code only parses and validates its private NBT. */
    public static CompletableFuture<SpellDefinition> decodeDefinitionAsync(CompoundTag data) {
        CompoundTag snapshot = data.copy();
        return CompletableFuture.supplyAsync(() -> SpellDefinition.load(snapshot), Util.backgroundExecutor());
    }
}

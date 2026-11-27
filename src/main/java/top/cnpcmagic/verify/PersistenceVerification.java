package top.cnpcmagic.verify;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;
import noppes.npcs.CustomEntities;
import top.cnpcmagic.casting.SpellDefinition;
import top.cnpcmagic.casting.SpellLibrary;
import top.cnpcmagic.data.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real CNPCplus IO, run only by the existing isolated rewrite fixture. */
final class PersistenceVerification {
    private static final UUID LEGACY = UUID.fromString("9748a05c-d0e7-48be-a59c-0cd758742c45");
    private static final UUID RECOVERY = UUID.fromString("b246e394-2026-4000-8000-000000000001");
    private static final UUID SUMMON = UUID.fromString("b246e394-2026-4000-8000-000000000002");
    private static UUID discardedSummon;
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    static void run(MinecraftServer server, boolean reload) {
        try {
            var legacyFile = server.getWorldPath(LevelResource.ROOT).resolve("data/cnpcmagic_npc_data.dat");
            if (!reload) {
                CompoundTag providers = new CompoundTag(); MagicRule rule = new MagicRule(); rule.spellId = "cnpcmagic:legacy_probe";
                providers.put("cnpcmagic", rule.save()); CompoundTag npcs = new CompoundTag(); npcs.put(LEGACY.toString(), providers);
                CompoundTag data = new CompoundTag(); data.put("npcs", npcs); CompoundTag root = new CompoundTag(); root.put("data", data);
                Files.createDirectories(legacyFile.getParent()); NbtIo.writeCompressed(root, legacyFile);
                SpellLibrary library = new SpellLibrary(); var definition = SpellDefinition.template("legacy_library", SpellDefinition.Delivery.INSTANT, SpellDefinition.EffectType.DAMAGE);
                library.spells.put(definition.id, definition); root = new CompoundTag(); root.put("data", library.save(new CompoundTag()));
                NbtIo.writeCompressed(root, legacyFile.resolveSibling("cnpcmagic_spell_library.dat"));
            }
            MagicDataStore rules = MagicDataStore.get(server); SpellLibrary library = SpellLibrary.get(server);
            MagicRuntimeData runtime = MagicRuntimeData.get(server);
            check(rules.getRules(LEGACY, "cnpcmagic").get(0).spellId.equals("cnpcmagic:legacy_probe"), "legacy singleton rule migrated");
            check(library.spells.containsKey("cnpcmagic:legacy_library"), "legacy library migrated");
            if (!reload) {
                var npc = CustomEntities.entityCustomNpc.create(server.overworld()); check(npc != null, "reference NPC");
                MagicRule original = new MagicRule(); original.spellId = "cnpcmagic:reference_original";
                rules.apply(npc, Map.of("cnpcmagic", List.of(original))); UUID reference = MagicDataStore.dataId(npc);
                CompoundTag snapshot = npc.saveWithoutId(new CompoundTag());
                check(snapshot.getString("CNPCMagicDataId").equals(reference.toString()) && !snapshot.toString().contains(original.spellId), "NPC/clone NBT reference only");
                var clone = CustomEntities.entityCustomNpc.create(server.overworld()); check(clone != null, "clone created");
                clone.load(snapshot); clone.setUUID(UUID.randomUUID());
                check(rules.getRules(clone, "cnpcmagic").get(0).spellId.equals(original.spellId), "real clone load follows reference");
                MagicRule changed = new MagicRule(); changed.spellId = "cnpcmagic:reference_changed";
                rules.apply(clone, Map.of("cnpcmagic", List.of(changed)));
                check(rules.getRules(npc, "cnpcmagic").get(0).spellId.equals(original.spellId), "clone edit preserves source");
                check(!reference.equals(MagicDataStore.dataId(clone)), "clone edit forks reference");
                CompoundTag recovery = new CompoundTag(); recovery.putString("dimension", "minecraft:overworld"); recovery.putDouble("x", 17.5);
                runtime.putReturn(RECOVERY, recovery);
                CompoundTag summon = new CompoundTag(); summon.putUUID("CNPCMagicOwner", npc.getUUID()); summon.putLong("CNPCMagicExpires", 12345);
                runtime.putSummon(SUMMON, summon);
                var legacySummon = EntityType.ALLAY.create(server.overworld()); check(legacySummon != null, "legacy summon created");
                legacySummon.getPersistentData().merge(summon); legacySummon.moveTo(0, 81, 0, 0, 0);
                check(server.overworld().addFreshEntity(legacySummon), "legacy summon joined");
                check(!legacySummon.getPersistentData().contains("CNPCMagicOwner") && !runtime.getSummon(legacySummon.getUUID()).isEmpty(), "legacy summon metadata externalized");
                legacySummon.discard();
                discardedSummon = legacySummon.getUUID();
                rules.flush(); library.flush(); runtime.flush();
                CompoundTag broken = new CompoundTag(); broken.putString("broken", "retained");
                var corrupt = CnpcPlusData.file("magic_corrupt_probe").toPath(); Files.createDirectories(corrupt.getParent());
                NbtIo.writeCompressed(broken, corrupt); byte[] before = Files.readAllBytes(corrupt);
                CnpcPlusData probe = new CnpcPlusData("magic_corrupt_probe") { public CompoundTag save(CompoundTag tag) { tag.putInt("value", 1); return tag; } };
                boolean rejected = false; try { probe.flush(); } catch (IllegalStateException expected) { rejected = true; }
                check(rejected && probe.isDirty() && java.util.Arrays.equals(before, Files.readAllBytes(corrupt)), "corrupt external file rejected, retained and dirty for retry");
                Files.delete(corrupt);
            } else {
                check(runtime.getReturn(RECOVERY).getDouble("x") == 17.5, "external player return survives restart");
                check(runtime.getSummon(SUMMON).getLong("CNPCMagicExpires") == 12345, "external summon metadata survives restart");
            }
            check(Files.isRegularFile(legacyFile), "legacy SavedData retained at original path");
            CompoundTag retained = NbtIo.readCompressed(legacyFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            check(retained.getCompound("data").getCompound("npcs").getCompound(LEGACY.toString())
                .getCompound("cnpcmagic").getString("spell").equals("cnpcmagic:legacy_probe"), "legacy migration backup content unchanged");
            for (String category : List.of("magic_rules", "magic_library", "magic_runtime")) {
                CompoundTag root = NbtIo.readCompressed(CnpcPlusData.file(category).toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                check(root.contains("Owner") && root.getInt("MigrationState") == 5 && root.getCompound("Data").contains("CNPCMagicPayload"), "real CNPCplus format " + category);
            }
            top.cnpcmagic.CNPCMagic.LOGGER.info("REWRITE PASS CNPCplus persistence: legacy migration, clone references, recovery/summon {}", reload ? "restart" : "IO and corrupt-file retention");
        } catch (IOException error) { throw new IllegalStateException("Persistence fixture IO", error); }
    }

    static void verifyDiscardCleanup(MinecraftServer server) {
        check(discardedSummon != null && MagicRuntimeData.get(server).getSummon(discardedSummon).isEmpty(),
            "discard removes external summon metadata after server cleanup tick");
    }
}

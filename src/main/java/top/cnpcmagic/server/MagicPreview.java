package top.cnpcmagic.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.casting.*;
import top.cnpcmagic.data.MagicRuntimeData;
import top.cnpcmagic.provider.MagicCast;
import java.util.*;

/** Real spells run only in this dedicated void dimension. No preview rule is saved on the source NPC. */
public final class MagicPreview {
    public interface EntityData { List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> cnpcmagic$values(); }
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("cnpcmagic", "preview"));
    private static final String RETURN = "CNPCMagicPreviewReturn";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();
    private static int sequence;
    private MagicPreview() {}
    public static void start(ServerPlayer player, CompoundTag packet) {
        int request = packet.getInt("request"), level = packet.getInt("level");
        String spell = packet.getString("spell"), mode = packet.getString("target");
        Session existing = SESSIONS.get(player.getUUID());
        EntityNPCInterface original = existing == null ? NoppesUtilServer.getEditingNpc(player) : existing.original;
        if (!noppes.npcs.CustomNpcsPermissions.hasPermission(player, noppes.npcs.CustomNpcsPermissions.NPC_GUI)
            || original == null || !packet.hasUUID("npc") || !original.getUUID().equals(packet.getUUID("npc"))) {
            reply(player, "previewError", request, "Preview is unavailable"); return;
        }
        var provider = top.cnpcmagic.provider.MagicProviders.forSpell(spell);
        if (provider == null || !provider.isValidSpell(spell) || level < 1 || level > provider.getMaxLevel(spell)
            || provider.getId().equals("cnpcmagic") && !SpellLibrary.get(player.server).spells.containsKey(spell)) {
            reply(player, "previewError", request, "Invalid spell or level"); return;
        }
        boolean meleeFollowup = mode.equals("entity") && provider.requiresMeleeTarget(spell);
        if (existing != null) {
            existing.queue(spell, level, mode, request, 6, meleeFollowup);
            reply(player, "previewStatus", request, ""); return;
        }
        if (original.level() != player.level() || original.distanceToSqr(player) > 4096) {
            reply(player, "previewError", request, "NPC is too far away"); return;
        }
        ServerLevel arena = player.server.getLevel(DIMENSION);
        if (arena == null) { reply(player, "previewError", request, "Preview dimension is missing"); return; }
        BlockPos origin = new BlockPos((++sequence % 4096) * 512, 80, 0);
        for (int x = -12; x <= 32; x++) for (int z = -12; z <= 12; z++) arena.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        Session session = new Session(arena, origin, original);
        session.queue(spell, level, mode, request, 28, meleeFollowup);
        SESSIONS.put(player.getUUID(), session);
        for (int x = (origin.getX() - 16) >> 4; x <= (origin.getX() + 32) >> 4; x++)
            for (int z = -1; z <= 0; z++) arena.setChunkForced(x, z, true);
        CompoundTag begin = new CompoundTag(); begin.putString("action", "previewBegin");
        begin.putLong("origin", origin.asLong()); MagicSync.send(player, begin);
    }
    private static void reply(ServerPlayer player, String action, int request, String message) {
        CompoundTag t = new CompoundTag(); t.putString("action", action); t.putInt("request", request); t.putString("message", message); MagicSync.send(player, t);
    }
    public static void finish(ServerPlayer player, boolean reopen) {
        MagicRuntimeData runtime = MagicRuntimeData.get(player.server);
        CompoundTag saved = runtime.getReturn(player.getUUID());
        if (saved.isEmpty() && player.getPersistentData().contains(RETURN)) {
            saved = player.getPersistentData().getCompound(RETURN).copy();
            try { runtime.putReturn(player.getUUID(), saved); }
            catch (IllegalStateException error) { top.cnpcmagic.CNPCMagic.LOGGER.error("Could not migrate preview return; inline data retained", error); }
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            s.clear(player.getUUID());
            if (reopen) s.sendFrame(player);
            SESSIONS.remove(player.getUUID());
            for (int x = (s.origin.getX() - 16) >> 4; x <= (s.origin.getX() + 32) >> 4; x++)
                for (int z = -1; z <= 0; z++) s.arena.setChunkForced(x, z, false);
        }
        if (!saved.isEmpty()) {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.getString("dimension")));
            ServerLevel destination = player.server.getLevel(key); if (destination == null) destination = player.server.overworld();
            player.teleportTo(destination, saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"), saved.getFloat("yaw"), saved.getFloat("pitch"));
            player.setGameMode(GameType.byId(saved.getInt("mode"))); player.getPersistentData().remove(RETURN);
            try { runtime.removeReturn(player.getUUID()); }
            catch (IllegalStateException error) { top.cnpcmagic.CNPCMagic.LOGGER.error("Preview return cleanup will retry at world save", error); }
            if (saved.hasUUID("npc") && destination.getEntity(saved.getUUID("npc")) instanceof EntityNPCInterface npc) NoppesUtilServer.setEditingNpc(player, npc);
        }
        if (reopen) { CompoundTag t = new CompoundTag(); t.putString("action", "previewEnd"); MagicSync.send(player, t); }
    }
    private static List<Entity> entities(ServerLevel level) { List<Entity> all = new ArrayList<>(); level.getAllEntities().forEach(all::add); return all; }
    public static boolean isPreview(Entity entity) { return entity.level().dimension().equals(DIMENSION) && OWNERS.containsKey(entity.getUUID()); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        
        for (var entry : new ArrayList<>(SESSIONS.entrySet())) {
            Session s = entry.getValue(); ServerPlayer player = s.arena.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (++s.ticks > 1200 || player.level() != s.original.level()) { finish(player, true); continue; }
            try {
                if (s.nextCast != 0 && s.ticks >= s.nextCast - 6 && !s.prepared) {
                    s.clear(player.getUUID()); s.prepared = true;
                    if (!s.spawn(player.getUUID())) {
                        s.nextCast = 0; reply(player, "previewError", s.request, "Could not create preview actors"); continue;
                    }
                }
                if (s.nextCast == s.ticks) {
                    s.nextCast = 0;
                    SpellTarget target = switch (s.targetMode) {
                        case "self" -> SpellTarget.self(s.npc); case "position" -> SpellTarget.position(s.target.getX(), s.target.getY(), s.target.getZ());
                        case "none" -> SpellTarget.none(); default -> SpellTarget.entity(s.target);
                    };
                    CastingService.Attempt attempt = CastingService.start(s.npc, s.spell, s.level, target, "editor_preview");
                    s.cast = attempt.cast();
                    if (s.cast == null) reply(player, "previewError", s.request, attempt.result().code() + ": " + attempt.result().message());
                }
                if (s.cast != null && s.cast.tick(s.npc, s.target)) {
                    boolean success = s.cast.wasSuccessful(); s.cast = null;
                    if (success && s.meleeFollowup) MagicCombatHandler.meleeFollowup(s.npc, s.target);
                    reply(player, success ? "previewStatus" : "previewError", s.request, success ? "" : "CAST_FAILED");
                }
                if (s.ticks % 2 == 0) s.sendFrame(player);
            } catch (RuntimeException | LinkageError error) {
                top.cnpcmagic.CNPCMagic.LOGGER.error("Preview adapter failed", error);
                s.clear(player.getUUID()); s.nextCast = 0; reply(player, "previewError", s.request, "Preview adapter failed");
            }
        }
    }
    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) finish(p, false); }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && (p.getPersistentData().contains(RETURN)
            || !MagicRuntimeData.get(p.server).getReturn(p.getUUID()).isEmpty())) finish(p, false);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        ServerLevel arena = event.getServer().getLevel(DIMENSION);
        if (arena != null) for (long chunk : arena.getForcedChunks().toLongArray()) {
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunk);
            arena.setChunkForced(pos.x, pos.z, false);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void stopping(ServerStoppingEvent event) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) { ServerPlayer p = event.getServer().getPlayerList().getPlayer(id); if (p != null) finish(p, false); }
        SESSIONS.clear(); OWNERS.clear();
    }
    @SubscribeEvent public static void entityJoined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getLevel().dimension().equals(DIMENSION) || event.getEntity() instanceof ServerPlayer) return;
        if (event.loadedFromDisk()) { event.setCanceled(true); return; }
        Entity entity = event.getEntity();
        for (var entry : SESSIONS.entrySet()) if (Math.abs(entity.getX() - entry.getValue().origin.getX()) < 192)
            OWNERS.put(entity.getUUID(), entry.getKey());
    }
    public static void nativePacket(ResourceKey<Level> dimension, double x, net.minecraft.network.protocol.Packet<?> packet) {
        if (!dimension.equals(DIMENSION) || !(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload)) return;
        for (Session session : SESSIONS.values()) if (Math.abs(x - session.origin.getX()) < 192) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), session.arena.registryAccess());
            try {
                net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(buffer, payload);
                byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
                session.nativePackets.add(new net.minecraft.nbt.ByteArrayTag(bytes));
            } finally { buffer.release(); }
        }
    }
    public static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
                                 int count, double dx, double dy, double dz, double speed) {
        if (!level.dimension().equals(DIMENSION)) return;
        for (Session session : SESSIONS.values()) if (Math.abs(x - session.origin.getX()) < 192) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket.STREAM_CODEC.encode(buffer,
                    new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(type, true, x, y, z,
                    (float)dx, (float)dy, (float)dz, (float)speed, count));
                byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
                session.particles.add(new net.minecraft.nbt.ByteArrayTag(bytes));
            } finally { buffer.release(); }
        }
    }
    private static final class Session {
        final ServerLevel arena; final BlockPos origin; final EntityNPCInterface original;
        EntityNPCInterface npc; Mob target;
        String spell, targetMode; int level, request, ticks, nextCast; boolean prepared, meleeFollowup; MagicCast cast;
        final Set<Integer> sentEntities = new HashSet<>();
        final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> sentBlocks = new HashMap<>();
        final net.minecraft.nbt.ListTag particles = new net.minecraft.nbt.ListTag();
        final net.minecraft.nbt.ListTag nativePackets = new net.minecraft.nbt.ListTag();
        Session(ServerLevel arena, BlockPos origin, EntityNPCInterface original) {
            this.arena = arena; this.origin = origin; this.original = original;
        }
        void queue(String spell, int level, String mode, int request, int delay, boolean meleeFollowup) {
            this.spell = spell; this.level = level; targetMode = mode; this.request = request;
            this.meleeFollowup = meleeFollowup; ticks = 0; nextCast = delay; prepared = false;
        }
        void clear(UUID owner) {
            try { if (cast != null) cast.cancel(npc); }
            catch (RuntimeException | LinkageError error) { top.cnpcmagic.CNPCMagic.LOGGER.error("Preview adapter failed to clean up", error); }
            cast = null;
            for (Entity entity : entities(arena)) if (owner.equals(OWNERS.get(entity.getUUID()))) entity.discard();
            OWNERS.entrySet().removeIf(entry -> entry.getValue().equals(owner));
        }
        void sendFrame(ServerPlayer player) {
            CompoundTag frame = new CompoundTag(); frame.putString("action", "previewFrame");
            frame.putLong("time", arena.getGameTime());
            net.minecraft.nbt.ListTag actors = new net.minecraft.nbt.ListTag();
            for (Entity entity : entities(arena)) if (player.getUUID().equals(OWNERS.get(entity.getUUID())) && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                CompoundTag actor = new CompoundTag(); actor.putInt("id", entity.getId());
                if (sentEntities.add(entity.getId())) {
                    CompoundTag initial = entity.saveWithoutId(new CompoundTag());
                    if (entity instanceof LivingEntity living) initial.put("Attributes", living.getAttributes().save());
                    initial.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                    if (entity instanceof EntityNPCInterface npc) initial.put("npcSpawn", npc.writeSpawnData());
                    if (entity instanceof net.neoforged.neoforge.entity.IEntityWithComplexSpawn extra) {
                        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), arena.registryAccess());
                        try {
                            extra.writeSpawnData(buffer); byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
                            initial.putByteArray("extraSpawn", bytes);
                        } finally { buffer.release(); }
                    }
                    actor.put("initial", initial);
                }
                actor.putDouble("x", entity.getX()); actor.putDouble("y", entity.getY()); actor.putDouble("z", entity.getZ());
                Vec3 motion = entity.getDeltaMovement();
                actor.putDouble("dx", motion.x); actor.putDouble("dy", motion.y); actor.putDouble("dz", motion.z);
                actor.putBoolean("onGround", entity.onGround());
                actor.putFloat("yaw", entity.getYRot()); actor.putFloat("pitch", entity.getXRot());
                actor.putInt("age", entity.tickCount); actor.putInt("vehicle", entity.getVehicle() == null ? -1 : entity.getVehicle().getId());
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile)
                    actor.putInt("owner", projectile.getOwner() == null ? -1 : projectile.getOwner().getId());
                if (entity instanceof LivingEntity living) {
                    actor.putFloat("head", living.getYHeadRot()); actor.putFloat("body", living.yBodyRot); actor.putInt("hurt", living.hurtTime);
                }
                var values = ((EntityData)entity.getEntityData()).cnpcmagic$values();
                if (values != null) {
                    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), arena.registryAccess());
                    try {
                        net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,
                            new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(entity.getId(), values));
                        byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); actor.putByteArray("data", bytes);
                    } finally { buffer.release(); }
                }
                actors.add(actor);
            }
            frame.put("entities", actors);
            net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(20, 5, 4))) {
                var state = arena.getBlockState(pos);
                if (state.equals(sentBlocks.get(pos))) continue;
                sentBlocks.put(pos.immutable(), state);
                CompoundTag block = new CompoundTag(); block.putLong("pos", pos.asLong());
                block.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(state)); blocks.add(block);
            }
            net.minecraft.nbt.ListTag blockEntities = new net.minecraft.nbt.ListTag();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(20, 5, 4))) {
                var blockEntity = arena.getBlockEntity(pos);
                if (blockEntity != null) blockEntities.add(blockEntity.saveWithFullMetadata(arena.registryAccess()));
            }
            frame.put("blockEntities", blockEntities);
            frame.put("blocks", blocks); frame.put("particles", particles.copy()); particles.clear();
            frame.put("nativePackets", nativePackets.copy()); nativePackets.clear(); MagicSync.send(player, frame);
        }
        boolean spawn(UUID owner) {
            if (!(original.getType().create(arena) instanceof EntityNPCInterface copy)) return false;
            target = EntityType.HUSK.create(arena);
            if (target == null) return false;
            npc = copy;
            npc.readSpawnData(original.writeSpawnData().copy()); npc.setUUID(UUID.randomUUID()); npc.setNoAi(true);
            npc.display.setName("Preview"); npc.display.setShowName(1); npc.setPersistenceRequired();
            for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, original.getItemBySlot(slot).copy());
            npc.moveTo(origin.getX(), 80, 0, -90, 0); npc.setYHeadRot(-90); npc.setYBodyRot(-90); npc.setHealth(npc.getMaxHealth());
            target.setNoAi(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            target.setCustomName(net.minecraft.network.chat.Component.literal("Target")); target.setPersistenceRequired();
            double distance = meleeFollowup ? 1.1 : 6;
            target.moveTo(origin.getX() + distance, 80, 0, 90, 0);
            OWNERS.put(npc.getUUID(), owner); OWNERS.put(target.getUUID(), owner);
            npc.setTarget(target); MagicCombatHandler.face(npc, target);
            if (!arena.addFreshEntity(npc) || !arena.addFreshEntity(target)) return false;
            return true;
        }
    }
}

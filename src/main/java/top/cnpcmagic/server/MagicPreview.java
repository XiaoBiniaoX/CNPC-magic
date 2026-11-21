package top.cnpcmagic.server;

import electroblob.wizardry.spell.Spell;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.MagicDataAccess;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.network.S2CPreviewStatus;
import top.cnpcmagic.wizardry.CastHandle;
import top.cnpcmagic.wizardry.WizardryCasting;
import top.cnpcmagic.wizardry.WizardrySpells;

/** Runs preview spells only in a non-persistent, isolated dimension. */
public final class MagicPreview {
    public static final int DIMENSION_ID = DimensionManager.getNextFreeDimId();
    public static final DimensionType DIMENSION_TYPE = DimensionType.register(
        "cnpcmagic_preview", "_cnpcmagic_preview", DIMENSION_ID, MagicPreviewWorldProvider.class, false);

    private static final String RETURN_KEY = "CNPCMagicPreviewReturn";
    private static final int PREVIEW_TIMEOUT = 1200;
    private static final int ACTOR_CLEANUP_DELAY = 120;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> PREVIEW_ACTORS = new HashMap<>();
    private static final Set<Integer> SLOTS = new HashSet<>();
    private static String saveFolder = "cnpcmagic-preview";

    private MagicPreview() {
    }

    public static void registerDimension() {
        DimensionManager.registerDimension(DIMENSION_ID, DIMENSION_TYPE);
    }

    public static String getSaveFolder() {
        return saveFolder;
    }

    public static boolean isPreviewEntity(Entity entity) {
        return entity != null && PREVIEW_ACTORS.containsKey(entity.getUniqueID());
    }

    public static void serverStarted(FMLServerStartedEvent event) {
        configureSaveFolder(FMLCommonHandler.instance().getMinecraftServerInstance());
    }

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote || event.getWorld().provider.getDimension() != DIMENSION_ID
            || !(event.getWorld() instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.getWorld();
        world.disableLevelSaving = true;
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!(entity instanceof EntityPlayer)) entity.setDead();
        }
    }

    public static void start(EntityPlayerMP player, UUID npcId, String spellId, int level) {
        EntityNPCInterface original = NoppesUtilServer.getEditingNpc(player);
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
            || original == null || original.isDead || original.world != player.world
            || !original.getUniqueID().equals(npcId) || SESSIONS.containsKey(player.getUniqueID())) {
            send(player, npcId.toString(), "FAILED");
            return;
        }

        Spell spell = WizardrySpells.get(spellId);
        if (spell == null || !spellId.startsWith("ebwizardry:") || level < 1 || level > MagicRule.MAX_LEVEL
            || !WizardrySpells.isSupported(spellId, original)) {
            send(player, npcId.toString(), "FAILED");
            return;
        }

        MinecraftServer server = player.getServer();
        configureSaveFolder(server);
        DimensionManager.initDimension(DIMENSION_ID);
        WorldServer arena = server.getWorld(DIMENSION_ID);
        if (arena == null || !arena.disableLevelSaving) {
            send(player, npcId.toString(), "FAILED");
            return;
        }

        int slot = allocateSlot();
        BlockPos origin = new BlockPos(slot * 64, 64, 0);
        Session session = new Session(player.getUniqueID(), original, arena, origin, spell, level);
        if (!session.spawnActors()) {
            session.clear();
            SLOTS.remove(slot);
            send(player, npcId.toString(), "FAILED");
            return;
        }

        NBTTagCompound saved = savePlayerState(player, original);
        player.getEntityData().setTag(RETURN_KEY, saved);
        session.saved = saved;
        SESSIONS.put(player.getUniqueID(), session);

        try {
            player.setGameType(GameType.SPECTATOR);
            EntityPlayerMP moved = (EntityPlayerMP) player.changeDimension(DIMENSION_ID,
                teleporter(origin.getX() + 0.5, origin.getY() + 0.1, origin.getZ() - 10.5, 0, 0));
            if (moved == null || moved.dimension != DIMENSION_ID) throw new IllegalStateException("dimension transfer failed");
            moved.connection.setPlayerLocation(origin.getX() + 0.5, origin.getY() + 0.1,
                origin.getZ() - 10.5, 0, 0);
            send(moved, npcId.toString(), "READY");
        } catch (RuntimeException | LinkageError error) {
            CNPCMagic.LOGGER.error("Could not enter the isolated spell preview", error);
            finish(player, false);
            send(player, npcId.toString(), "FAILED");
        }
    }

    public static void finish(EntityPlayerMP player, boolean notify) {
        Session session = SESSIONS.remove(player.getUniqueID());
        NBTTagCompound saved = player.getEntityData().getCompoundTag(RETURN_KEY).copy();
        if (saved.getKeySet().isEmpty() && session != null) saved = session.saved.copy();
        if (session != null) {
            session.clear();
            SLOTS.remove(session.slot);
        }

        if (!saved.getKeySet().isEmpty()) {
            restorePlayer(player, saved, session == null ? null : session.original);
        }
        if (notify) send(player, saved.getString("npc"), "RETURNED");
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.dimension != DIMENSION_ID && SESSIONS.containsKey(player.getUniqueID())) finish(player, true);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) finish((EntityPlayerMP) event.player, false);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        NBTTagCompound saved = player.getEntityData().getCompoundTag(RETURN_KEY).copy();
        if (!saved.getKeySet().isEmpty()) restorePlayer(player, saved, null);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            EntityPlayerMP player = findPlayer(entry.getValue().arena.getMinecraftServer(), entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                session.clear();
                SESSIONS.remove(entry.getKey());
                SLOTS.remove(session.slot);
                continue;
            }
            if (player.dimension != DIMENSION_ID || ++session.ticks > PREVIEW_TIMEOUT) {
                finish(player, true);
                continue;
            }
            try {
                if (!session.started && session.ticks >= 30) {
                    session.started = true;
                    session.cast = WizardryCasting.start(session.caster, session.spell, session.level, session.target);
                    if (session.cast == null) {
                        session.completed = true;
                        session.cleanupAt = session.ticks + ACTOR_CLEANUP_DELAY;
                        send(player, session.original.getUniqueID().toString(), "FAILED");
                    }
                } else if (session.cast != null && session.cast.tick(session.caster, session.target)) {
                    boolean success = session.cast.wasSuccessful();
                    session.cast = null;
                    session.completed = true;
                    session.cleanupAt = session.ticks + ACTOR_CLEANUP_DELAY;
                    send(player, session.original.getUniqueID().toString(), success ? "COMPLETE" : "FAILED");
                }
                if (session.completed && session.ticks >= session.cleanupAt) session.clearActors();
            } catch (RuntimeException | LinkageError error) {
                CNPCMagic.LOGGER.error("Wizardry preview cast failed", error);
                session.clear();
                session.completed = true;
                session.cleanupAt = session.ticks + ACTOR_CLEANUP_DELAY;
                send(player, session.original.getUniqueID().toString(), "FAILED");
            }
        }
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote || event.getWorld().provider.getDimension() != DIMENSION_ID) return;
        MinecraftServer server = ((WorldServer) event.getWorld()).getMinecraftServer();
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            if (entry.getValue().arena != event.getWorld()) continue;
            EntityPlayerMP player = findPlayer(server, entry.getKey());
            if (player != null) finish(player, false);
            else {
                entry.getValue().clear();
                SESSIONS.remove(entry.getKey());
                SLOTS.remove(entry.getValue().slot);
            }
        }
    }

    public static void serverStopping(FMLServerStoppingEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            EntityPlayerMP player = findPlayer(server, id);
            if (player != null) finish(player, false);
        }
        for (Session session : new ArrayList<>(SESSIONS.values())) session.clear();
        SESSIONS.clear();
        PREVIEW_ACTORS.clear();
        SLOTS.clear();
    }

    private static void configureSaveFolder(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) return;
        File worldRoot = overworld.getSaveHandler().getWorldDirectory().getAbsoluteFile();
        File isolatedRoot = new File(worldRoot, "cnpcmagic-preview").getAbsoluteFile();
        saveFolder = worldRoot.toPath().relativize(isolatedRoot.toPath()).toString();
    }

    private static int allocateSlot() {
        int slot = 0;
        while (SLOTS.contains(slot)) slot++;
        SLOTS.add(slot);
        return slot;
    }

    private static NBTTagCompound savePlayerState(EntityPlayerMP player, EntityNPCInterface npc) {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("dimension", player.dimension);
        saved.setDouble("x", player.posX);
        saved.setDouble("y", player.posY);
        saved.setDouble("z", player.posZ);
        saved.setFloat("yaw", player.rotationYaw);
        saved.setFloat("pitch", player.rotationPitch);
        saved.setInteger("gameType", player.interactionManager.getGameType().getID());
        saved.setString("npc", npc.getUniqueID().toString());
        return saved;
    }

    private static void restorePlayer(EntityPlayerMP player, NBTTagCompound saved, EntityNPCInterface original) {
        MinecraftServer server = player.getServer();
        int dimension = saved.getInteger("dimension");
        WorldServer destination = server.getWorld(dimension);
        if (destination == null) {
            DimensionManager.initDimension(dimension);
            destination = server.getWorld(dimension);
        }
        if (destination == null) destination = server.getWorld(0);
        if (player.dimension != destination.provider.getDimension()) {
            player = (EntityPlayerMP) player.changeDimension(destination.provider.getDimension(),
                teleporter(saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"),
                    saved.getFloat("yaw"), saved.getFloat("pitch")));
        }
        double x = saved.getDouble("x"), y = saved.getDouble("y"), z = saved.getDouble("z");
        float yaw = saved.getFloat("yaw"), pitch = saved.getFloat("pitch");
        player.setPosition(x, y, z);
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        player.prevRotationYaw = yaw;
        player.prevRotationPitch = pitch;
        player.setGameType(GameType.getByID(saved.getInteger("gameType")));
        if (player.connection != null) player.connection.setPlayerLocation(x, y, z, yaw, pitch);

        EntityNPCInterface npc = original;
        if (npc == null) {
            try {
                npc = (EntityNPCInterface) destination.getEntityFromUuid(UUID.fromString(saved.getString("npc")));
            } catch (IllegalArgumentException ignored) {
                npc = null;
            }
        }
        if (npc != null && !npc.isDead && npc.world == destination) {
            NoppesUtilServer.setEditingNpc(player, npc);
            player.getEntityData().removeTag(RETURN_KEY);
        }
    }

    private static EntityPlayerMP findPlayer(MinecraftServer server, UUID id) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getUniqueID().equals(id)) return player;
        }
        return null;
    }

    private static ITeleporter teleporter(final double x, final double y, final double z,
                                          final float yaw, final float pitch) {
        return new ITeleporter() {
            @Override
            public void placeEntity(World world, Entity entity, float oldYaw) {
                entity.setLocationAndAngles(x, y, z, yaw, pitch);
            }
        };
    }

    private static void send(EntityPlayerMP player, String npcId, String status) {
        NetworkHandler.CHANNEL.sendTo(new S2CPreviewStatus(npcId, status), player);
    }

    private static final class Session {
        private final UUID owner;
        private final EntityNPCInterface original;
        private final WorldServer arena;
        private final BlockPos origin;
        private final Spell spell;
        private final int level;
        private final int slot;
        private EntityCustomNpc caster;
        private EntityCow target;
        private NBTTagCompound saved = new NBTTagCompound();
        private CastHandle cast;
        private int ticks;
        private int cleanupAt;
        private boolean started;
        private boolean completed;

        private Session(UUID owner, EntityNPCInterface original, WorldServer arena, BlockPos origin,
                        Spell spell, int level) {
            this.owner = owner;
            this.original = original;
            this.arena = arena;
            this.origin = origin;
            this.spell = spell;
            this.level = level;
            this.slot = origin.getX() / 64;
        }

        private boolean spawnActors() {
            prepareArena();
            NBTTagCompound data = new NBTTagCompound();
            original.writeToNBT(data);
            caster = new EntityCustomNpc(arena);
            caster.readFromNBT(data);
            caster.setUniqueId(UUID.randomUUID());
            caster.setNoAI(true);
            caster.enablePersistence();
            caster.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            caster.setHealth(caster.getMaxHealth());
            caster.display.setName("Preview");
            caster.display.setShowName(1);
            ((MagicDataAccess) caster).cnpcmagic$setDataId(null);

            target = new EntityCow(arena);
            target.setNoAI(true);
            target.enablePersistence();
            target.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000.0D);
            target.setHealth(1000.0F);
            target.setCustomNameTag("Target");
            target.setAlwaysRenderNameTag(true);
            target.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 6.5);
            caster.setAttackTarget(target);

            PREVIEW_ACTORS.put(caster.getUniqueID(), owner);
            PREVIEW_ACTORS.put(target.getUniqueID(), owner);
            return arena.spawnEntity(caster) && arena.spawnEntity(target);
        }

        private void prepareArena() {
            for (int x = -8; x <= 8; x++) {
                for (int z = -14; z <= 14; z++) {
                    arena.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
                    for (int y = 0; y <= 4; y++) {
                        arena.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        private void clear() {
            if (cast != null && caster != null && !caster.isDead) cast.cancel(caster);
            cast = null;
            clearActors();
        }

        private void clearActors() {
            for (Entity entity : new ArrayList<>(arena.loadedEntityList)) {
                if (entity instanceof EntityPlayer) continue;
                if (Math.abs(entity.posX - (origin.getX() + 0.5)) > 28
                    || Math.abs(entity.posZ - origin.getZ()) > 28) continue;
                PREVIEW_ACTORS.remove(entity.getUniqueID());
                entity.setDead();
            }
            for (int x = -8; x <= 8; x++) {
                for (int z = -14; z <= 14; z++) {
                    for (int y = -1; y <= 4; y++) {
                        arena.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
            caster = null;
            target = null;
        }
    }
}

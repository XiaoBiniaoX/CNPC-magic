package top.cnpcmagic.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import top.cnpcmagic.CNPCMagic;
import java.util.*;

/** A display-only copy of the server's arena. The connected player's world is never replaced. */
@Mod.EventBusSubscriber(modid = CNPCMagic.MODID, value = Dist.CLIENT)
public final class PreviewScene extends ClientLevel {
    public interface ParticleProviders { Map<ResourceLocation, ParticleProvider<?>> cnpcmagic$providers(); }
    private static PreviewScene scene;
    private static PreviewScene current;
    private final BlockPos origin;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities = new HashMap<>();
    private final Map<ParticleRenderType, List<Particle>> particles = new LinkedHashMap<>();
    private final List<Particle> pendingParticles = new ArrayList<>();
    private final Camera camera = new Camera();
    private final Entity view;
    private TextureTarget target;

    private PreviewScene(BlockPos origin) {
        super(Minecraft.getInstance().getConnection(),
            new ClientLevelData(Minecraft.getInstance().level.getDifficulty(), false, true),
            top.cnpcmagic.server.MagicPreview.DIMENSION, Minecraft.getInstance().level.dimensionTypeRegistration(),
            2, 2, Minecraft.getInstance()::getProfiler, Minecraft.getInstance().levelRenderer, false, 0);
        this.origin = origin;
        view = EntityType.ARMOR_STAND.create(this);
        view.moveTo(origin.getX() + 4, origin.getY() + 6.8, origin.getZ() - 14, 0, 24.5f); view.setOldPosAndRot();
    }

    public static PreviewScene current() { return current; }
    public static PreviewScene instance() { return scene; }
    public static void open(BlockPos origin) { dispose(); scene = new PreviewScene(origin); }
    public static void dispose() {
        if (scene == null) return;
        for (Entity entity : java.util.stream.StreamSupport.stream(scene.entitiesForRendering().spliterator(), false).toList())
            scene.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        for (var blockEntity : scene.blockEntities.values()) blockEntity.setRemoved();
        if (scene.target != null) scene.target.destroyBuffers();
        scene = null;
    }

    @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
    public Collection<net.minecraft.world.level.block.entity.BlockEntity> previewBlockEntities() { return blockEntities.values(); }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
    @Override public int getRawBrightness(BlockPos pos, int amount) { return 15; }
    @Override public void addParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz) { particle(options, x, y, z, dx, dy, dz); }
    @Override public void addParticle(ParticleOptions options, boolean force, double x, double y, double z, double dx, double dy, double dz) { particle(options, x, y, z, dx, dy, dz); }
    @Override public void addAlwaysVisibleParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz) { particle(options, x, y, z, dx, dy, dz); }
    @Override public void addAlwaysVisibleParticle(ParticleOptions options, boolean force, double x, double y, double z, double dx, double dy, double dz) { particle(options, x, y, z, dx, dy, dz); }

    @SuppressWarnings("unchecked")
    public <T extends ParticleOptions> Particle particle(T options, double x, double y, double z, double dx, double dy, double dz) {
        ParticleProvider<T> provider = (ParticleProvider<T>)((ParticleProviders)Minecraft.getInstance().particleEngine)
            .cnpcmagic$providers().get(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()));
        Particle particle = provider == null ? null : provider.createParticle(options, this, x, y, z, dx, dy, dz);
        if (particle != null) pendingParticles.add(particle);
        return particle;
    }
    public void add(Particle particle) { pendingParticles.add(particle); }

    // Several native renderers/particles consult Minecraft.level; scope that lookup without setLevel or player changes.
    private void within(Runnable action) {
        Minecraft mc = Minecraft.getInstance(); ClientLevel original = mc.level;
        current = this; mc.level = this;
        try { action.run(); }
        finally { mc.level = original; current = null; }
    }

    public static void receive(CompoundTag frame) {
        if (scene == null) return;
        scene.within(() -> scene.apply(frame));
    }
    private void apply(CompoundTag frame) {
        setGameTime(frame.getLong("time"));
        for (var raw : frame.getList("blocks", 10)) {
            CompoundTag block = (CompoundTag)raw;
            blocks.put(BlockPos.of(block.getLong("pos")), net.minecraft.nbt.NbtUtils.readBlockState(
                registryAccess().lookupOrThrow(Registries.BLOCK), block.getCompound("state")));
        }
        for (var blockEntity : blockEntities.values()) blockEntity.setRemoved();
        blockEntities.clear();
        for (var raw : frame.getList("blockEntities", 10)) {
            CompoundTag tag = (CompoundTag)raw;
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            var blockEntity = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, getBlockState(pos), tag);
            if (blockEntity != null) { blockEntity.setLevel(this); blockEntities.put(pos, blockEntity); }
        }
        Set<Integer> ids = new HashSet<>();
        for (var raw : frame.getList("entities", 10)) {
            CompoundTag actor = (CompoundTag)raw; int id = actor.getInt("id"); ids.add(id);
            Entity entity = getEntity(id);
            if (entity == null && actor.contains("initial")) {
                CompoundTag initial = actor.getCompound("initial");
                entity = EntityType.loadEntityRecursive(initial, this, e -> e);
                if (entity == null) continue;
                if (entity instanceof LivingEntity living) living.getAttributes().load(initial.getList("Attributes", 10));
                if (entity instanceof noppes.npcs.entity.EntityNPCInterface npc) npc.readSpawnData(initial.getCompound("npcSpawn"));
                entity.setId(id); putNonPlayerEntity(id, entity);
                if (entity instanceof net.minecraftforge.entity.IEntityAdditionalSpawnData extra && initial.contains("extraSpawn")) {
                    FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(initial.getByteArray("extraSpawn")));
                    try { extra.readSpawnData(buffer); } finally { buffer.release(); }
                }
            }
            if (entity == null) continue;
            entity.moveTo(actor.getDouble("x"), actor.getDouble("y"), actor.getDouble("z"), actor.getFloat("yaw"), actor.getFloat("pitch"));
            entity.tickCount = actor.getInt("age");
            if (entity instanceof LivingEntity living) {
                living.setYHeadRot(actor.getFloat("head")); living.yBodyRot = actor.getFloat("body"); living.hurtTime = actor.getInt("hurt");
            }
            if (actor.contains("data")) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(actor.getByteArray("data")));
                try { entity.getEntityData().assignValues(new ClientboundSetEntityDataPacket(buffer).packedItems()); }
                finally { buffer.release(); }
            }
        }
        for (Entity entity : java.util.stream.StreamSupport.stream(entitiesForRendering().spliterator(), false).toList())
            if (!ids.contains(entity.getId())) removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        for (var raw : frame.getList("entities", 10)) {
            CompoundTag actor = (CompoundTag)raw; Entity entity = getEntity(actor.getInt("id")), vehicle = getEntity(actor.getInt("vehicle"));
            if (entity != null) { if (vehicle != null) entity.startRiding(vehicle, true); else entity.stopRiding(); }
        }
        for (var raw : frame.getList("nativePackets", 7)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(((net.minecraft.nbt.ByteArrayTag)raw).getAsByteArray()));
            try { new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(buffer).handle(Minecraft.getInstance().getConnection()); }
            finally { buffer.release(); }
        }
        for (var raw : frame.getList("particles", 7)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(((net.minecraft.nbt.ByteArrayTag)raw).getAsByteArray()));
            try {
                var packet = new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(buffer);
                if (packet.getCount() == 0) particle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ(),
                    packet.getMaxSpeed() * packet.getXDist(), packet.getMaxSpeed() * packet.getYDist(), packet.getMaxSpeed() * packet.getZDist());
                else for (int i = 0; i < packet.getCount(); i++) particle(packet.getParticle(),
                    packet.getX() + random.nextGaussian() * packet.getXDist(), packet.getY() + random.nextGaussian() * packet.getYDist(), packet.getZ() + random.nextGaussian() * packet.getZDist(),
                    random.nextGaussian() * packet.getMaxSpeed(), random.nextGaussian() * packet.getMaxSpeed(), random.nextGaussian() * packet.getMaxSpeed());
            } finally { buffer.release(); }
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || scene == null) return;
        if (Minecraft.getInstance().level == null) { dispose(); return; }
        scene.within(() -> {
            for (Entity entity : java.util.stream.StreamSupport.stream(scene.entitiesForRendering().spliterator(), false).toList())
                if (!entity.isRemoved() && !entity.isPassenger()) scene.tickNonPassenger(entity);
            for (var list : scene.particles.values()) list.removeIf(particle -> { particle.tick(); return !particle.isAlive(); });
            for (Particle particle : scene.pendingParticles) scene.particles.computeIfAbsent(particle.getRenderType(), key -> new ArrayList<>()).add(particle);
            scene.pendingParticles.clear();
        });
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height, float partial) {
        graphics.fill(x, y, x + width, y + height, 0xFF353D3F);
        if (scene == null) return;
        scene.within(() -> scene.draw(graphics, x, y, width, height, partial));
    }
    private void draw(GuiGraphics graphics, int x, int y, int width, int height, float partial) {
        Minecraft mc = Minecraft.getInstance(); graphics.flush();
        int pixels = mc.getWindow().getGuiScale() > 1 ? 2 : 1;
        if (target == null) target = new TextureTarget(width * pixels, height * pixels, true, Minecraft.ON_OSX);
        else if (target.width != width * pixels || target.height != height * pixels) target.resize(width * pixels, height * pixels, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack(); modelView.pushPose(); modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
        var dispatcher = mc.getEntityRenderDispatcher(); Camera originalCamera = dispatcher.camera;
        var blockDispatcher = mc.getBlockEntityRenderDispatcher();
        Entity originalCrosshair = dispatcher.crosshairPickEntity;
        target.setClearColor(0.21f, 0.24f, 0.25f, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(50), (float)width / height, 0.05f, 256), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.enableDepthTest();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            camera.setup(this, view, false, false, 1);
            dispatcher.prepare(this, camera, null); dispatcher.setRenderShadow(false);
            blockDispatcher.prepare(this, camera, null);
            PoseStack pose = new PoseStack();
            pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot())); pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180));
            var eye = camera.getPosition(); var buffers = graphics.bufferSource();
            for (var entry : blocks.entrySet()) if (!entry.getValue().isAir()) {
                if (blockEntities.containsKey(entry.getKey()) && entry.getValue().getRenderShape() == net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED) continue;
                BlockPos pos = entry.getKey(); pose.pushPose(); pose.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                mc.getBlockRenderer().renderSingleBlock(entry.getValue(), pose, buffers, 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            for (var blockEntity : blockEntities.values()) {
                BlockPos pos = blockEntity.getBlockPos(); pose.pushPose(); pose.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                blockDispatcher.render(blockEntity, partial, pose, buffers); pose.popPose();
            }
            for (Entity entity : entitiesForRendering()) if (!entity.isRemoved())
                dispatcher.render(entity, entity.getX() - eye.x, entity.getY() - eye.y, entity.getZ() - eye.z, entity.getYRot(), partial, pose, buffers, 15728880);
            buffers.endBatch();
            if (net.minecraftforge.fml.ModList.get().isLoaded("ebwizardry")) {
                com.binaris.wizardry.client.effect.ArcaneLockRender.markDirty();
                com.binaris.wizardry.client.effect.ArcaneLockRender.render(camera, pose, partial);
                com.binaris.wizardry.client.effect.ArcaneLockRender.markDirty();
            }
            PoseStack particlePose = new PoseStack(); particlePose.mulPoseMatrix(pose.last().pose());
            modelView.mulPoseMatrix(particlePose.last().pose()); RenderSystem.applyModelViewMatrix();
            // Entity buffer batches clear their lightmap state; particles bind it again, as ParticleEngine does.
            mc.gameRenderer.lightTexture().turnOnLightLayer(); RenderSystem.setShaderColor(1, 1, 1, 1);
            for (var entry : particles.entrySet()) if (entry.getKey() != ParticleRenderType.NO_RENDER && !entry.getValue().isEmpty()) {
                RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
                var tesselator = Tesselator.getInstance(); var buffer = tesselator.getBuilder();
                entry.getKey().begin(buffer, mc.getTextureManager());
                for (Particle particle : entry.getValue()) particle.render(buffer, camera, partial);
                entry.getKey().end(tesselator);
            }
        } finally {
            dispatcher.prepare(mc.player.level(), originalCamera, originalCrosshair); dispatcher.setRenderShadow(true);
            blockDispatcher.prepare(mc.player.level(), originalCamera, mc.hitResult);
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            mc.getMainRenderTarget().bindWrite(true); RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.popPose(); RenderSystem.applyModelViewMatrix(); RenderSystem.disableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        }
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId()); RenderSystem.setShaderColor(1, 1, 1, 1);
        var buffer = Tesselator.getInstance().getBuilder(); var matrix = graphics.pose().last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y + height, 0).uv(0, 0).endVertex(); buffer.vertex(matrix, x + width, y + height, 0).uv(1, 0).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv(1, 1).endVertex(); buffer.vertex(matrix, x, y, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}

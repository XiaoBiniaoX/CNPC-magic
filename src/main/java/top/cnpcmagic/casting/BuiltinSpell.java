package top.cnpcmagic.casting;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.*;
import top.cnpcmagic.provider.*;
import top.cnpcmagic.server.NpcAnimationSync;
import top.cnpcmagic.data.MagicRuntimeData;
import java.util.*;
import static top.cnpcmagic.casting.SpellDefinition.*;

/** Native entities, collision and server-side effects are shared by gameplay and the preview arena. */
public final class BuiltinSpell implements MagicProvider {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "cnpcmagic");
    public static final DeferredHolder<EntityType<?>, EntityType<Bolt>> BOLT = ENTITIES.register("spell_bolt", () -> EntityType.Builder
        .<Bolt>of(Bolt::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(64).updateInterval(1).noSave().build("cnpcmagic:spell_bolt"));
    private static final Map<UUID, Entity> SUMMONS = new HashMap<>();
    public String getId() { return "cnpcmagic"; }
    public boolean isValidSpell(String id) { var key = ResourceLocation.tryParse(id); return key != null && key.getNamespace().equals(getId()); }
    public int getMaxLevel(String id) { return 10; }
    public MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        // CastingService takes this branch directly with its complete target descriptor.
        SpellDefinition d = SpellLibrary.get(caster.getServer()).spells.get(id);
        return d == null || !d.enabled ? null : new Cast(new CastingService.Context(UUID.randomUUID(), caster, "adapter", id, level,
            target == null ? SpellTarget.none() : SpellTarget.entity(target), 1), SpellDefinition.load(d.save()));
    }
    public static final class Bolt extends ThrowableItemProjectile {
        private Cast cast;
        public Bolt(EntityType<? extends Bolt> type, Level level) { super(type, level); }
        protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
        @Override protected double getDefaultGravity() { return cast == null ? 0 : cast.definition.gravity; }
        @Override protected boolean canHitEntity(Entity entity) { return super.canHitEntity(entity) && (cast == null || entity instanceof LivingEntity l && cast.accepts(l)); }
        @Override public void tick() {
            if (!level().isClientSide && (cast == null || tickCount > cast.definition.duration || getOwner() == null || !getOwner().isAlive())) { discard(); return; }
            if (cast != null && cast.definition.homing && cast.context.target().entity() != null) {
                Vec3 direction = cast.context.target().entity().getEyePosition().subtract(position()).normalize();
                setDeltaMovement(getDeltaMovement().scale(0.8).add(direction.scale(cast.definition.speed * 0.2)));
            }
            super.tick();
            if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
        @Override protected void onHit(HitResult hit) {
            if (!level().isClientSide) {
                if (cast != null) cast.impact(hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity l ? l : null, hit.getLocation());
                discard();
            }
        }
    }
    public static final class Cast implements MagicCast {
        final CastingService.Context context;
        final SpellDefinition definition;
        final ServerLevel world;
        private final List<Pending> pending = new ArrayList<>();
        private int age;
        private boolean released, successful, ended;
        private Bolt bolt;
        private final Set<UUID> summons = new HashSet<>();
        Cast(CastingService.Context context, SpellDefinition definition) {
            this.context = context; this.definition = definition; world = (ServerLevel)context.caster().level();
            NpcAnimationSync.play(context.caster(), "cnpcmagic:cast", Math.max(10, definition.castTicks + (definition.delivery == Delivery.CHANNEL ? definition.duration : 10)));
        }
        public boolean tick(LivingEntity caster, LivingEntity target) {
            if (ended) return true;
            age++;
            if (!released && age > definition.castTicks) { released = true; CastingService.notify(context, "spellCast", null); release(); }
            if (released && definition.delivery == Delivery.CHANNEL && age <= definition.castTicks + definition.duration && (age - definition.castTicks - 1) % definition.interval == 0) {
                CastingService.notify(context, "spellChannel", null); ray();
            }
            for (Pending effect : new ArrayList<>(pending)) if (age >= effect.at) { apply(effect); pending.remove(effect); }
            boolean flying = bolt != null && !bolt.isRemoved();
            boolean channel = definition.delivery == Delivery.CHANNEL && age < definition.castTicks + definition.duration;
            if (released && !flying && !channel && pending.isEmpty()) { ended = true; NpcAnimationSync.stop(caster); return true; }
            return false;
        }
        public boolean wasSuccessful() { return successful; }
        public void cancel(LivingEntity caster) {
            ended = true; pending.clear(); if (bolt != null) bolt.discard();
            for (UUID id : summons) { Entity entity = SUMMONS.remove(id); if (entity != null) entity.discard(); MagicRuntimeData.get(world.getServer()).removeSummon(id); }
            NpcAnimationSync.stop(caster);
        }
        private Vec3 point() {
            SpellTarget t = context.target(); LivingEntity caster = context.caster();
            if (t.entity() != null) return t.entity().getEyePosition();
            if (t.kind() == SpellTarget.Kind.DIRECTION) return caster.getEyePosition().add(t.position().normalize().scale(definition.range));
            if (t.kind() == SpellTarget.Kind.NONE) return caster.getEyePosition().add(caster.getLookAngle().scale(definition.range));
            return t.position();
        }
        private void release() {
            LivingEntity caster = context.caster(); Vec3 p = point();
            switch (definition.delivery) {
                case PROJECTILE -> {
                    bolt = new Bolt(BOLT.get(), world); bolt.cast = this; bolt.setOwner(caster);
                    bolt.setPos(caster.getEyePosition().add(p.subtract(caster.getEyePosition()).normalize().scale(0.5)));
                    bolt.setDeltaMovement(p.subtract(bolt.position()).normalize().scale(definition.speed));
                    if (!world.addFreshEntity(bolt)) bolt.discard();
                }
                case RAY -> ray();
                case CHANNEL -> { /* The first pulse is emitted by tick, using the same ray collision. */ }
                case SELF -> impact(caster, caster.position());
                case AREA -> area(context.target().kind() == SpellTarget.Kind.NONE ? caster.position() : p);
                case GROUND -> {
                    var hit = world.clip(new ClipContext(p.add(0, definition.radius, 0), p.add(0, -definition.range, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                    if (hit.getType() != HitResult.Type.MISS) area(hit.getLocation());
                }
                case INSTANT, SUMMON -> impact(context.target().entity(), p);
            }
        }
        boolean accepts(LivingEntity entity) {
            if (!entity.isAlive() || entity.isRemoved() || entity.level() != world || entity.isSpectator()) return false;
            LivingEntity caster = context.caster();
            boolean ally = entity == caster || caster.isAlliedTo(entity);
            return switch (definition.filter) { case ALL -> true; case SELF -> entity == caster; case ALLIES -> ally; case ENEMIES -> !ally; };
        }
        private void ray() {
            Vec3 from = context.caster().getEyePosition(), to = from.add(point().subtract(from).normalize().scale(definition.range));
            var block = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.caster()));
            to = block.getLocation(); LivingEntity closest = null; double distance = from.distanceToSqr(to);
            for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.4), this::accepts)) {
                var clipped = entity.getBoundingBox().inflate(0.15).clip(from, to);
                if (clipped.isPresent() && from.distanceToSqr(clipped.get()) < distance) { distance = from.distanceToSqr(clipped.get()); closest = entity; to = clipped.get(); }
            }
            int steps = Math.min(96, Math.max(1, (int)(from.distanceTo(to) * 3)));
            for (int i = 0; i <= steps; i++) { Vec3 p = from.lerp(to, i / (double)steps); world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0); }
            impact(closest, to);
        }
        private void area(Vec3 p) {
            int count = 0;
            double radius = context.target().kind() == SpellTarget.Kind.AREA ? context.target().radius() : definition.radius;
            List<LivingEntity> entities = world.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(radius), this::accepts);
            entities.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(p)));
            for (LivingEntity e : entities) if (e.position().distanceToSqr(p) <= radius * radius
                && (!definition.lineOfSight || context.caster().hasLineOfSight(e))) { impact(e, e.position()); if (++count >= definition.maxTargets) break; }
            world.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 32, definition.radius / 2, 0.3, definition.radius / 2, 0.05);
        }
        void impact(LivingEntity target, Vec3 p) {
            if (CastingService.before(context, "spellImpact")) return;
            for (Effect e : definition.effects) if (e.enabled) pending.add(new Pending(age + e.delay, e, target, p));
        }
        private void apply(Pending job) {
            Effect e = job.effect; LivingEntity target = job.target, caster = context.caster(); Vec3 p = job.position;
            boolean entityEffect = e.type == EffectType.DAMAGE || e.type == EffectType.HEALING || e.type == EffectType.STATUS || e.type == EffectType.MOTION;
            if (entityEffect && (target == null || !accepts(target))) return;
            if (CastingService.before(context, "spellEffect")) return;
            double amount = e.amount * definition.power * context.power() * context.level();
            switch (e.type) {
                case DAMAGE -> successful |= target.hurt(world.damageSources().indirectMagic(caster, caster), (float)amount);
                case HEALING -> { target.heal((float)amount); successful = true; }
                case STATUS -> {
                    var status = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.id)).orElse(null);
                    if (status != null) successful |= target.addEffect(new MobEffectInstance(status, e.duration, Math.max(0, (int)e.amount - 1)), caster);
                }
                case MOTION -> {
                    Vec3 velocity = target.position().subtract(caster.position()).normalize().scale(Math.min(4, amount));
                    target.push(velocity.x, Math.max(0.2, velocity.y), velocity.z); target.hurtMarked = true; successful = true;
                }
                case VISUAL -> { world.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, Math.min(128, Math.max(1, (int)e.amount)), 0.5, 0.5, 0.5, 0.05); successful = true; }
                case AUDIO -> {
                    var sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(e.id));
                    if (sound != null) { world.playSound(null, p.x, p.y, p.z, sound, SoundSource.NEUTRAL, (float)Math.min(4, e.amount), 1); successful = true; }
                }
                case SUMMON -> {
                    if (SUMMONS.size() >= 128) return;
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(e.id));
                    if (type == null || !type.canSummon()) return;
                    Entity entity = type.create(world);
                    if (entity == null || entity instanceof noppes.npcs.entity.EntityNPCInterface || entity instanceof net.minecraft.world.entity.player.Player) return;
                    entity.moveTo(p.x, p.y, p.z, caster.getYRot(), 0);
                    CompoundTag tag = new CompoundTag(); tag.putUUID("CNPCMagicOwner", caster.getUUID());
                    tag.putLong("CNPCMagicExpires", world.getGameTime() + e.duration); tag.putUUID("CNPCMagicInstance", context.instanceId());
                    MagicRuntimeData.get(world.getServer()).putSummon(entity.getUUID(), tag);
                    if (entity instanceof TamableAnimal pet) pet.setOwnerUUID(caster.getUUID());
                    if (entity instanceof Mob mob && context.target().entity() != null && accepts(context.target().entity())) mob.setTarget(context.target().entity());
                    if (world.addFreshEntity(entity)) { SUMMONS.put(entity.getUUID(), entity); summons.add(entity.getUUID()); successful = true; CastingService.notify(context, "spellSummon", null); }
                    else MagicRuntimeData.get(world.getServer()).removeSummon(entity.getUUID());
                }
            }
            CastingService.notify(context, "spellEffectApplied", null);
        }
        private record Pending(int at, Effect effect, LivingEntity target, Vec3 position) {}
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        Entity entity = event.getEntity(); CompoundTag legacy = entity.getPersistentData();
        MagicRuntimeData runtime = MagicRuntimeData.get(world.getServer());
        if (legacy.hasUUID("CNPCMagicOwner")) {
            CompoundTag metadata = new CompoundTag();
            for (String key : List.of("CNPCMagicOwner", "CNPCMagicExpires", "CNPCMagicInstance")) if (legacy.contains(key)) metadata.put(key, legacy.get(key).copy());
            try { runtime.putSummon(entity.getUUID(), metadata); }
            catch (IllegalStateException error) { top.cnpcmagic.CNPCMagic.LOGGER.error("Could not migrate summon; inline data retained", error); SUMMONS.put(entity.getUUID(), entity); return; }
            for (String key : metadata.getAllKeys()) legacy.remove(key);
        }
        if (!runtime.getSummon(entity.getUUID()).isEmpty()) SUMMONS.put(entity.getUUID(), entity);
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        Entity entity = event.getEntity(); SUMMONS.remove(entity.getUUID());
        if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) MagicRuntimeData.get(world.getServer()).removeSummon(entity.getUUID());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        
        for (Entity entity : new ArrayList<>(SUMMONS.values())) {
            if (!(entity.level() instanceof ServerLevel world)) continue;
            MagicRuntimeData runtime = MagicRuntimeData.get(world.getServer()); CompoundTag tag = runtime.getSummon(entity.getUUID());
            if (tag.isEmpty()) tag = entity.getPersistentData(); // Failed legacy migration retains the original expiry until a later load can migrate it.
            Entity owner = tag.hasUUID("CNPCMagicOwner") ? world.getEntity(tag.getUUID("CNPCMagicOwner")) : null;
            if (entity.isRemoved() || world.getGameTime() >= tag.getLong("CNPCMagicExpires") || owner == null || !owner.isAlive()) { entity.discard(); SUMMONS.remove(entity.getUUID()); runtime.removeSummon(entity.getUUID()); }
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Entity entity : new ArrayList<>(SUMMONS.values())) { entity.discard(); MagicRuntimeData.get(event.getServer()).removeSummon(entity.getUUID()); }
        SUMMONS.clear();
    }
}

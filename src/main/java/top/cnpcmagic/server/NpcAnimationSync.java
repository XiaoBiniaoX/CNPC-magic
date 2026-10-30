package top.cnpcmagic.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.network.S2CNpcAnimation;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Transient rendering state, never NPC configuration/NBT. */
public final class NpcAnimationSync {
    private static final Map<LivingEntity, State> ACTIVE = new WeakHashMap<>();
    private static final Object DEFAULT_OWNER = new Object();
    private static final ThreadLocal<Owner> INVOKING = new ThreadLocal<>();
    private static long sequence;

    private NpcAnimationSync() {
    }

    public static void play(LivingEntity npc, String animation, int duration) {
        Owner owner = INVOKING.get();
        State state = ACTIVE.computeIfAbsent(npc, k -> new State());
        state.animations.put(owner == null ? DEFAULT_OWNER : owner.token,
            new Animation(animation, npc.level().getGameTime(), duration, owner == null || owner.foreground, ++sequence));
        refresh(npc, state);
    }

    public static void stop(LivingEntity npc) {
        State state = ACTIVE.get(npc);
        if (state != null) {
            Owner owner = INVOKING.get();
            state.animations.remove(owner == null ? DEFAULT_OWNER : owner.token);
            refresh(npc, state);
        }
    }

    public static void stopAll(LivingEntity npc) {
        if (ACTIVE.remove(npc) != null) send(npc, null);
    }

    public static <T> T withOwner(Object token, boolean foreground, Supplier<T> action) {
        Owner previous = INVOKING.get();
        INVOKING.set(new Owner(token, foreground));
        try {
            return action.get();
        } finally {
            if (previous == null) INVOKING.remove();
            else INVOKING.set(previous);
        }
    }

    private static void send(LivingEntity npc, Animation animation) {
        int elapsed = animation == null ? 0 : (int) (npc.level().getGameTime() - animation.start);
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> npc),
            new S2CNpcAnimation(npc.getUUID(), animation == null ? "" : animation.id, elapsed,
                animation == null ? 0 : animation.duration));
    }

    private static void refresh(LivingEntity npc, State state) {
        long now = npc.level().getGameTime();
        state.animations.values().removeIf(a -> now - a.start >= a.duration);
        Animation selected = null;
        for (Animation animation : state.animations.values()) {
            // A single model has one pose: ordered/script casts take precedence; otherwise the latest independent cast.
            if (selected == null || animation.foreground && !selected.foreground
                || animation.foreground == selected.foreground && animation.sequence > selected.sequence) selected = animation;
        }
        if (selected != state.visible) {
            state.visible = selected;
            send(npc, selected);
        }
        if (state.animations.isEmpty()) ACTIVE.remove(npc);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : new ArrayList<>(ACTIVE.entrySet())) refresh(entry.getKey(), entry.getValue());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity npc && event.getEntity() instanceof ServerPlayer player) {
            State state = ACTIVE.get(npc);
            Animation animation = state == null ? null : state.visible;
            if (animation != null) {
                int elapsed = (int) (npc.level().getGameTime() - animation.start);
                if (elapsed < animation.duration) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CNpcAnimation(npc.getUUID(), animation.id, elapsed, animation.duration));
                } else {
                    refresh(npc, state);
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        INVOKING.remove();
    }

    private record Owner(Object token, boolean foreground) {
    }

    private static final class State {
        final Map<Object, Animation> animations = new HashMap<>();
        Animation visible;
    }

    private record Animation(String id, long start, int duration, boolean foreground, long sequence) {
    }
}

package top.cnpcmagic.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.network.S2CNpcAnimation;

import java.util.Map;
import java.util.WeakHashMap;

/** Transient rendering state, never NPC configuration/NBT. */
public final class NpcAnimationSync {
    private static final Map<LivingEntity, Animation> ACTIVE = new WeakHashMap<>();

    private NpcAnimationSync() {
    }

    public static void play(LivingEntity npc, String animation, int duration) {
        ACTIVE.put(npc, new Animation(animation, npc.level().getGameTime(), duration));
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> npc),
            new S2CNpcAnimation(npc.getUUID(), animation, 0, duration));
    }

    public static void stop(LivingEntity npc) {
        if (ACTIVE.remove(npc) != null) {
            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> npc),
                new S2CNpcAnimation(npc.getUUID(), "", 0, 0));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity npc && event.getEntity() instanceof ServerPlayer player) {
            Animation animation = ACTIVE.get(npc);
            if (animation != null) {
                int elapsed = (int) (npc.level().getGameTime() - animation.start);
                if (elapsed < animation.duration) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CNpcAnimation(npc.getUUID(), animation.id, elapsed, animation.duration));
                } else {
                    ACTIVE.remove(npc);
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private record Animation(String id, long start, int duration) {
    }
}

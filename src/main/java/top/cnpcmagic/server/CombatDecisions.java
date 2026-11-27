package top.cnpcmagic.server;

import net.minecraft.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.cnpcmagic.CNPCMagic;
import top.cnpcmagic.data.RuleConditions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

/** Main-thread coordinator; at most one background batch, containing immutable values only. */
public final class CombatDecisions {
    private static final Map<UUID, List<Order>> ORDERS = new HashMap<>();
    private static final Map<Key, RuleConditions.Snapshot> CONDITIONS = new HashMap<>();
    private static final Map<UUID, Ordered> ORDERED = new HashMap<>();
    private static final Map<Key, Tested> TESTED = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static CompletableFuture<Batch> pending;
    private static long batches, reusedConditions, fallbackConditions, reusedOrders, fallbackOrders;
    private static boolean workersOffThread = true;

    private CombatDecisions() { }

    public record Order(int sequence, boolean independent) { }
    private record Key(UUID npc, int slot) { }
    private record Ordered(List<Order> input, List<Integer> indices) { }
    private record Tested(RuleConditions.Snapshot input, boolean matches) { }
    private record Batch(Map<UUID, Ordered> orders, Map<Key, Tested> conditions, long workerThread) { }

    public static List<Integer> ordered(UUID npc, List<Order> input) {
        input = List.copyOf(input);
        if (input.isEmpty()) { remove(npc); return List.of(); }
        ACTIVE.add(npc);
        Ordered cached = ORDERED.get(npc);
        if (cached != null && cached.input.equals(input)) {
            reusedOrders++;
            return cached.indices;
        }
        fallbackOrders++;
        ORDERS.put(npc, input);
        return sort(input);
    }

    public static boolean test(UUID npc, int slot, RuleConditions.Snapshot input) {
        Key key = new Key(npc, slot);
        ACTIVE.add(npc);
        Tested cached = TESTED.get(key);
        // An old target, condition, health, distance or combo window cannot veto a newly available attack.
        if (cached != null && cached.input.equals(input)) {
            reusedConditions++;
            return cached.matches;
        }
        fallbackConditions++;
        CONDITIONS.put(key, input);
        return input.test();
    }

    private static List<Integer> sort(List<Order> input) {
        return IntStream.range(0, input.size()).boxed().filter(i -> !input.get(i).independent)
            .sorted(Comparator.comparingInt(i -> input.get(i).sequence)).toList();
    }

    @SubscribeEvent
    public static void beforeTick(ServerTickEvent.Pre event) {
        if (pending == null || !pending.isDone()) return;
        try {
            Batch batch = pending.getNow(null);
            workersOffThread &= batch.workerThread != Thread.currentThread().threadId();
            batches++;
            batch.orders.forEach((key, value) -> { if (ACTIVE.contains(key)) ORDERED.put(key, value); });
            batch.conditions.forEach((key, value) -> { if (ACTIVE.contains(key.npc)) TESTED.put(key, value); });
        } catch (CompletionException error) {
            CNPCMagic.LOGGER.error("Combat snapshot batch failed; same-tick decisions remain available", error.getCause());
        }
        pending = null;
    }

    @SubscribeEvent
    public static void afterTick(ServerTickEvent.Post event) {
        if (pending != null || ORDERS.isEmpty() && CONDITIONS.isEmpty()) return;
        var orders = Map.copyOf(ORDERS);
        var conditions = Map.copyOf(CONDITIONS);
        ORDERS.clear(); CONDITIONS.clear();
        // No callback captures a server, entity, mutable rule, NBT, registry or coordinator map.
        pending = CompletableFuture.supplyAsync(() -> {
            Map<UUID, Ordered> sorted = new HashMap<>();
            orders.forEach((key, value) -> sorted.put(key, new Ordered(value, sort(value))));
            Map<Key, Tested> tested = new HashMap<>();
            conditions.forEach((key, value) -> tested.put(key, new Tested(value, value.test())));
            return new Batch(Map.copyOf(sorted), Map.copyOf(tested), Thread.currentThread().threadId());
        }, Util.backgroundExecutor());
    }

    public static void remove(UUID npc) {
        if (!ACTIVE.remove(npc)) return;
        ORDERS.remove(npc); ORDERED.remove(npc);
        CONDITIONS.keySet().removeIf(key -> key.npc.equals(npc));
        TESTED.keySet().removeIf(key -> key.npc.equals(npc));
    }

    public static void clear() {
        if (pending != null) pending.cancel(false);
        pending = null;
        ACTIVE.clear(); ORDERS.clear(); ORDERED.clear(); CONDITIONS.clear(); TESTED.clear();
        batches = reusedConditions = fallbackConditions = reusedOrders = fallbackOrders = 0;
        workersOffThread = true;
    }

    public record Stats(long batches, long reusedConditions, long fallbackConditions, long reusedOrders,
                        long fallbackOrders, boolean workersOffThread, int activeNpcs) { }

    public static Stats stats() {
        return new Stats(batches, reusedConditions, fallbackConditions, reusedOrders, fallbackOrders, workersOffThread, ACTIVE.size());
    }
}

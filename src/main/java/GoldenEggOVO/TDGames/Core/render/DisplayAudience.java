package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.*;

public final class DisplayAudience {
    public static final int VIEW_DISTANCE = 24;
    private static final Map<Entity, Map<UUID, Long>> VISIBILITY = new WeakHashMap<>();
    private static final Map<UUID, Long> RETIRED = Map.of();
    private record Pending(org.bukkit.plugin.Plugin plugin, long version, boolean show) {}
    private static final Map<UUID, Map<Entity, Pending>> PENDING = new HashMap<>();
    private static long nextVisibility;
    private final TDGamesPlugin plugin;
    private final Location origin;
    private final boolean enabled;
    private final boolean nativeFallback;
    private Set<Player> nativeViewers = Set.of(), packViewers = Set.of(), allViewers = Set.of();
    private final Map<Entity, Boolean> layers = new HashMap<>();
    public long generation;

    public DisplayAudience(TDGamesPlugin plugin, Location origin) {
        this(plugin, origin, true);
    }

    public DisplayAudience(TDGamesPlugin plugin, Location origin, boolean enabled) {
        this(plugin, origin, enabled, false);
    }

    public static DisplayAudience withNativeFallback(TDGamesPlugin plugin, Location origin) {
        return new DisplayAudience(plugin, origin, true, true);
    }

    private DisplayAudience(TDGamesPlugin plugin, Location origin, boolean enabled, boolean nativeFallback) {
        this.plugin = plugin;
        this.origin = origin;
        this.enabled = enabled;
        this.nativeFallback = nativeFallback;
        refresh();
    }

    public boolean managed() {
        ResourcePackState pack = plugin.resourcePackState();
        return enabled && pack != null && pack.managed();
    }

    public boolean packed(Player player) {
        ResourcePackState pack = plugin.resourcePackState();
        return pack != null && pack.packed(player);
    }

    public boolean needed(boolean packed) {
        return !managed() ? !packed : !viewers(packed).isEmpty();
    }

    public boolean canSeeTable(Player player) {
        ResourcePackState pack = plugin.resourcePackState();
        return plugin.playerViews().snapshot(player).filter(snapshot -> {
            Location location = snapshot.location();
            return snapshot.allowed() && location.getWorld() == origin.getWorld()
                    && location.distanceSquared(origin) <= VIEW_DISTANCE * VIEW_DISTANCE
                    && (nativeFallback || pack == null || !pack.required() || snapshot.packed());
        }).isPresent();
    }

    public Set<Player> viewers(boolean packed) {
        return packed ? packViewers : nativeViewers;
    }

    public Set<Player> all() {
        return allViewers;
    }

    public void refresh() {
        boolean managed = managed();
        if (!managed && !nativeFallback) return;
        Set<Player> natives = new HashSet<>(), packs = new HashSet<>();

        for (PlayerViews.Snapshot snapshot : plugin.playerViews().nearby(origin, VIEW_DISTANCE)) {
            if (!snapshot.allowed()) continue;
            Player player = snapshot.player();
            if (managed && snapshot.packed()) packs.add(player);
            else if (nativeFallback || !plugin.resourcePackState().required()) natives.add(player);
        }
        if (natives.equals(nativeViewers) && packs.equals(packViewers)) return;
        Set<Player> oldNative = nativeViewers, oldPacked = packViewers, oldAll = allViewers;
        nativeViewers = Set.copyOf(natives);
        packViewers = Set.copyOf(packs);
        natives.addAll(packs);
        allViewers = Set.copyOf(natives);
        generation++;
        for (var entry : layers.entrySet()) {
            Boolean packed = entry.getValue();
            Set<Player> before = packed == null ? oldAll : packed ? oldPacked : oldNative;
            Set<Player> after = packed == null ? allViewers : viewers(packed);
            for (Player player : before)
                if (!after.contains(player)) hide(plugin, player, entry.getKey());
            for (Player player : after)
                if (!before.contains(player)) show(plugin, player, entry.getKey());
        }
    }

    public void add(Entity entity, boolean packed) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, packed);
        for (Player player : viewers(packed)) show(plugin, player, entity);
    }

    public void common(Entity entity) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, null);
        for (Player player : allViewers) show(plugin, player, entity);
    }

    public void remove(Entity entity) {
        layers.remove(entity);
        retire(entity);
    }

    public void restore(Player player, Entity entity) {
        if (managed()) {
            if (!layers.containsKey(entity)) return;
            Boolean packed = layers.get(entity);
            if (!(packed == null ? allViewers : viewers(packed)).contains(player)) return;
        }
        show(plugin, player, entity);
    }

    public static void retire(Entity entity) {
        synchronized (VISIBILITY) {
            VISIBILITY.put(entity, RETIRED);
            PENDING.values().forEach(entities -> entities.remove(entity));
            PENDING.values().removeIf(Map::isEmpty);
        }
        if (!DisplayMovement.retire(entity)) entity.remove();
    }

    public static void show(org.bukkit.plugin.Plugin plugin, Player player, Entity entity) {
        visible(plugin, player, entity, true);
    }

    public static void hide(org.bukkit.plugin.Plugin plugin, Player player, Entity entity) {
        visible(plugin, player, entity, false);
    }

    private static void visible(org.bukkit.plugin.Plugin plugin, Player player, Entity entity, boolean show) {
        long version;
        UUID viewer = player.getUniqueId();
        synchronized (VISIBILITY) {
            if (VISIBILITY.get(entity) == RETIRED) return;
            version = ++nextVisibility;
            VISIBILITY.computeIfAbsent(entity, ignored -> new HashMap<>()).put(viewer, version);
        }
        var scheduler = plugin instanceof TDGamesPlugin games ? games.scheduler()
                : new GoldenEggOVO.TDGames.Core.platform.PlatformScheduler(plugin);
        scheduler.execute(player, () -> apply(player, entity, new Pending(plugin, version, show)));
    }

    private static void apply(Player player, Entity entity, Pending pending) {
        UUID viewer = player.getUniqueId();
        var scheduler = pending.plugin() instanceof TDGamesPlugin games ? games.scheduler()
                : new GoldenEggOVO.TDGames.Core.platform.PlatformScheduler(pending.plugin());
        synchronized (VISIBILITY) {
            Map<UUID, Long> viewers = VISIBILITY.get(entity);
            if (viewers == null || !Objects.equals(viewers.get(viewer), pending.version())) return;
            if (!scheduler.isOwned(entity)) {
                PENDING.computeIfAbsent(viewer, ignored -> new HashMap<>()).put(entity, pending);
                return;
            }
            Map<Entity, Pending> queued = PENDING.get(viewer);
            if (queued != null) {
                queued.remove(entity);
                if (queued.isEmpty()) PENDING.remove(viewer);
            }
            if (!pending.show()) viewers.remove(viewer);
        }
        if (pending.show()) player.showEntity(pending.plugin(), entity);
        else player.hideEntity(pending.plugin(), entity);
    }

    public static void retry(TDGamesPlugin plugin, Player player) {
        Map<Entity, Pending> queued;
        synchronized (VISIBILITY) {
            queued = new HashMap<>(PENDING.getOrDefault(player.getUniqueId(), Map.of()));
        }
        queued.forEach((entity, pending) -> {
            if (pending.plugin() == plugin) apply(player, entity, pending);
        });
    }

    public static void forget(Player player) {
        synchronized (VISIBILITY) {
            PENDING.remove(player.getUniqueId());
            for (Map<UUID, Long> viewers : VISIBILITY.values())
                if (viewers != RETIRED) viewers.remove(player.getUniqueId());
        }
    }

    public static void clear(TDGamesPlugin plugin) {
        synchronized (VISIBILITY) {
            PENDING.values().forEach(entities -> entities.values().removeIf(pending -> pending.plugin() == plugin));
            PENDING.values().removeIf(Map::isEmpty);
        }
    }
}

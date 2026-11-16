package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.Core.platform.PlatformEvents;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerViews implements Listener, AutoCloseable {
    public record Snapshot(Player player, UUID id, Location location, boolean allowed, boolean packed, Location eyeLocation) {
        public Snapshot {
            location = location.clone();
            eyeLocation = eyeLocation == null ? location.clone().add(0, 1.62, 0) : eyeLocation.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }

        @Override
        public Location eyeLocation() {
            return eyeLocation.clone();
        }
    }

    private final TDGamesPlugin plugin;
    private final Map<UUID, Snapshot> players = new ConcurrentHashMap<>();
    private PlatformScheduler.Task refresh;
    private volatile boolean closed;

    public PlayerViews(TDGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PlatformEvents.register(plugin, this);
        refresh = plugin.scheduler().repeat(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) capture(player);
        }, 1, 20);
    }

    public void capture(Player player) {
        if (closed) return;
        plugin.scheduler().execute(player, () -> captureHere(player, player.getLocation()));
    }

    public java.util.Optional<Snapshot> snapshot(UUID id) {
        if (!plugin.scheduler().folia()) {
            Player player = Bukkit.getPlayer(id);
            return player == null ? java.util.Optional.empty() : snapshot(player);
        }
        return java.util.Optional.ofNullable(players.get(id));
    }

    public java.util.Optional<Snapshot> snapshot(Player player) {
        if (plugin.scheduler().folia() && !plugin.scheduler().isOwned(player)) return snapshot(player.getUniqueId());
        if (!player.isOnline()) return java.util.Optional.empty();
        var pack = plugin.resourcePackState();
        return java.util.Optional.of(new Snapshot(player, player.getUniqueId(), player.getLocation(),
                plugin.allowed(player), pack != null && pack.packed(player), player.getEyeLocation()));
    }

    private void captureHere(Player player, Location location) {
        if (closed) return;
        if (!player.isOnline()) {
            players.remove(player.getUniqueId());
            return;
        }
        var pack = plugin.resourcePackState();
        players.put(player.getUniqueId(), new Snapshot(player, player.getUniqueId(), location,
                plugin.allowed(player), pack != null && pack.packed(player),
                location.clone().add(0, player.getEyeHeight(), 0)));
        DisplayAudience.retry(plugin, player);
    }

    public List<Snapshot> nearby(Location origin, double radius) {
        List<Snapshot> nearby = new ArrayList<>();
        if (!plugin.scheduler().folia()) {
            for (Player player : origin.getWorld().getPlayers()) {
                Location location = player.getLocation();
                if (location.getWorld() != origin.getWorld() || location.distanceSquared(origin) > radius * radius) continue;
                var pack = plugin.resourcePackState();
                nearby.add(new Snapshot(player, player.getUniqueId(), location,
                        plugin.allowed(player), pack != null && pack.packed(player), player.getEyeLocation()));
            }
            return List.copyOf(nearby);
        }
        for (Snapshot snapshot : players.values()) {
            Location location = snapshot.location();
            if (location.getWorld() == origin.getWorld() && location.distanceSquared(origin) <= radius * radius)
                nearby.add(snapshot);
        }
        return List.copyOf(nearby);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent event) {
        capture(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void moved(PlayerMoveEvent event) {
        if (event.getTo() != null) captureHere(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleported(PlayerTeleportEvent event) {
        if (event.getTo() != null) captureHere(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void worldChanged(PlayerChangedWorldEvent event) {
        capture(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
        DisplayAudience.forget(event.getPlayer());
    }

    @Override
    public void close() {
        closed = true;
        if (refresh != null) refresh.cancel();
        HandlerList.unregisterAll(this);
        players.clear();
        DisplayAudience.clear(plugin);
    }
}

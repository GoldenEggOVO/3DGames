package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;

final class TableChunks {
    record Retention(Set<Chunk> chunks, Throwable failure) {}
    private final TDGamesPlugin plugin;
    // Include acquired chunks while their table is still waiting for other chunks.
    private final Map<UUID, Set<Chunk>> retained = new HashMap<>();
    private final Map<Chunk, Set<UUID>> owners = new HashMap<>();

    TableChunks(TDGamesPlugin plugin) { this.plugin = plugin; }

    CompletableFuture<Retention> retain(UUID owner, Location center) {
        retained.put(owner, new HashSet<>());
        var futures = new ArrayList<CompletableFuture<Chunk>>();
        for (int x = (center.getBlockX() - 6) >> 4; x <= (center.getBlockX() + 6) >> 4; x++) {
            for (int z = (center.getBlockZ() - 6) >> 4; z <= (center.getBlockZ() + 6) >> 4; z++) {
                futures.add(Folia.retain(this, owner, center, x, z));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> {
                    Set<Chunk> chunks = new HashSet<>();
                    for (var future : futures) {
                        if (future.isCompletedExceptionally() || future.isCancelled()) continue;
                        Chunk chunk = future.join();
                        if (chunk != null) chunks.add(chunk);
                    }
                    return new Retention(Set.copyOf(chunks), failure);
                });
    }

    private boolean acquire(UUID owner, Chunk chunk) {
        synchronized (plugin) {
            Set<Chunk> chunks = retained.get(owner);
            if (chunks == null || !plugin.isEnabled()) return false;
            if (!owners.containsKey(chunk)) chunk.addPluginChunkTicket(plugin);
            owners.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(owner);
            chunks.add(chunk);
            return true;
        }
    }

    void release(UUID owner) {
        synchronized (plugin) {
            Set<Chunk> chunks = retained.remove(owner);
            if (chunks == null) return;
            for (Chunk chunk : chunks) {
                Set<UUID> remaining = owners.get(chunk);
                remaining.remove(owner);
                if (!remaining.isEmpty()) continue;
                owners.remove(chunk);
                if (!plugin.isEnabled()) continue;
                Location at = new Location(chunk.getWorld(), chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8);
                Runnable remove = () -> {
                    synchronized (plugin) {
                        if (plugin.isEnabled() && !owners.containsKey(chunk)) chunk.removePluginChunkTicket(plugin);
                    }
                };
                if (plugin.scheduler().isOwned(at)) remove.run();
                else plugin.scheduler().execute(at, remove);
            }
        }
    }

    void close() {
        for (UUID owner : Set.copyOf(retained.keySet())) release(owner);
    }

    private static final class Folia {
        private static CompletableFuture<Chunk> retain(TableChunks tickets, UUID owner, Location center, int x, int z) {
            CompletableFuture<Chunk> loaded;
            try { loaded = center.getWorld().getChunkAtAsync(x, z); }
            catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
            return loaded.thenCompose(chunk -> {
                var ready = new CompletableFuture<Chunk>();
                if (!tickets.plugin.isEnabled()) return CompletableFuture.completedFuture(null);
                Location at = new Location(center.getWorld(), x * 16 + 8, center.getY(), z * 16 + 8);
                try {
                    tickets.plugin.scheduler().execute(at, () -> {
                        try { ready.complete(tickets.acquire(owner, chunk) ? chunk : null); }
                        catch (RuntimeException failure) { ready.completeExceptionally(failure); }
                    });
                } catch (RuntimeException failure) { ready.completeExceptionally(failure); }
                return ready;
            });
        }
    }
}

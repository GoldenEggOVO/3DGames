package GoldenEggOVO.TDGames.Core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Consumer;

/** Routes server work to the thread that owns its context. */
public final class PlatformScheduler {
    public interface Task {
        void cancel();
    }

    private static final Task FINISHED = () -> {};
    private final Plugin plugin;
    private final boolean folia;

    public PlatformScheduler(Plugin plugin) {
        this(plugin, hasFolia());
    }

    PlatformScheduler(Plugin plugin, boolean folia) {
        this.plugin = Objects.requireNonNull(plugin);
        this.folia = folia;
    }

    private static boolean hasFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    public boolean folia() {
        return folia;
    }

    public boolean isOwned(Entity entity) {
        return folia ? Folia.owned(entity) : Bukkit.isPrimaryThread();
    }

    public boolean isOwned(Location location) {
        return folia ? Folia.owned(location) : Bukkit.isPrimaryThread();
    }

    public Task execute(Runnable action) {
        if (folia ? Folia.global() : Bukkit.isPrimaryThread()) {
            guarded(action).run();
            return FINISHED;
        }
        return later(action, 1);
    }

    public Task execute(Entity entity, Runnable action) {
        if (isOwned(entity)) {
            guarded(action).run();
            return FINISHED;
        }
        return later(entity, action, 1);
    }

    public Task execute(Location location, Runnable action) {
        if (isOwned(location)) {
            guarded(action).run();
            return FINISHED;
        }
        return later(location, action, 1);
    }

    public Task later(Runnable action, long delay) {
        action = guarded(action);
        if (folia) return Folia.later(plugin, action, Math.max(1, delay));
        return Bukkit.getScheduler().runTaskLater(plugin, action, Math.max(1, delay))::cancel;
    }

    public Task later(Entity entity, Runnable action, long delay) {
        if (folia) return Folia.later(plugin, entity, guarded(action), Math.max(1, delay));
        return later(action, delay);
    }

    public Task later(Location location, Runnable action, long delay) {
        if (folia) return Folia.later(plugin, location.clone(), guarded(action), Math.max(1, delay));
        return later(action, delay);
    }

    public Task repeat(Runnable action, long delay, long period) {
        action = guarded(action);
        if (folia) return Folia.repeat(plugin, action, Math.max(1, delay), Math.max(1, period));
        return Bukkit.getScheduler().runTaskTimer(plugin, action, Math.max(1, delay), Math.max(1, period))::cancel;
    }

    public Task repeat(Entity entity, Runnable action, long delay, long period) {
        if (folia) return Folia.repeat(plugin, entity, guarded(action), Math.max(1, delay), Math.max(1, period));
        return repeat(action, delay, period);
    }

    public Task repeat(Location location, Runnable action, long delay, long period) {
        if (folia) return Folia.repeat(plugin, location.clone(), guarded(action), Math.max(1, delay), Math.max(1, period));
        return repeat(action, delay, period);
    }

    public Task async(Runnable action) {
        if (folia) return Folia.async(plugin, action);
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, action)::cancel;
    }

    private Runnable guarded(Runnable action) {
        return () -> {
            synchronized (plugin) {
                action.run();
            }
        };
    }

    /** The callback runs on the entity owner, and is discarded if the entity retires. */
    public void teleport(Entity entity, Location target, Consumer<Boolean> completion) {
        Location destination = target.clone();
        execute(entity, () -> {
            if (folia) Folia.teleport(this, entity, destination, completion);
            else completion.accept(entity.teleport(destination));
        });
    }

    // Isolate Paper-only linkage so the outer class can load on Spigot.
    private static final class Folia {
        static boolean owned(Entity entity) { return Bukkit.isOwnedByCurrentRegion(entity); }
        static boolean owned(Location location) { return Bukkit.isOwnedByCurrentRegion(location); }
        static boolean global() { return Bukkit.isGlobalTickThread(); }

        static Task later(Plugin plugin, Runnable action, long delay) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> action.run(), delay)::cancel;
        }

        static Task later(Plugin plugin, Entity entity, Runnable action, long delay) {
            var task = entity.getScheduler().runDelayed(plugin, ignored -> action.run(), null, delay);
            return task == null ? FINISHED : task::cancel;
        }

        static Task later(Plugin plugin, Location location, Runnable action, long delay) {
            return Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> action.run(), delay)::cancel;
        }

        static Task repeat(Plugin plugin, Runnable action, long delay, long period) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> action.run(), delay, period)::cancel;
        }

        static Task repeat(Plugin plugin, Entity entity, Runnable action, long delay, long period) {
            var task = entity.getScheduler().runAtFixedRate(plugin, ignored -> action.run(), null, delay, period);
            return task == null ? FINISHED : task::cancel;
        }

        static Task repeat(Plugin plugin, Location location, Runnable action, long delay, long period) {
            return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task -> action.run(), delay, period)::cancel;
        }

        static Task async(Plugin plugin, Runnable action) {
            return Bukkit.getAsyncScheduler().runNow(plugin, task -> action.run())::cancel;
        }

        static void teleport(PlatformScheduler scheduler, Entity entity, Location target, Consumer<Boolean> completion) {
            entity.teleportAsync(target).whenComplete((success, failure) -> {
                if (scheduler.plugin.isEnabled()) scheduler.execute(entity,
                        () -> completion.accept(failure == null && Boolean.TRUE.equals(success)));
            });
        }
    }
}

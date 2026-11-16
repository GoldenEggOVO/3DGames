package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class DisplayMovement {
    private static final Map<Entity, Move> MOVES = new WeakHashMap<>();
    private static final boolean FOLIA = folia();

    private static final class Move {
        Location target;
        long version;
        boolean inFlight, retired;
    }

    private DisplayMovement() {}

    private static boolean folia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void move(Entity entity, Location target) {
        if (!FOLIA) {
            entity.teleport(target);
            return;
        }
        move(JavaPlugin.getPlugin(TDGamesPlugin.class), entity, target);
    }

    public static void move(TDGamesPlugin plugin, Entity entity, Location target) {
        if (!plugin.scheduler().folia()) {
            entity.teleport(target);
            return;
        }
        if (!plugin.isEnabled()) return;
        Move move;
        synchronized (MOVES) {
            move = MOVES.get(entity);
            if (move != null && move.retired) return;
            if (move == null && entity instanceof Display && plugin.scheduler().isOwned(entity)
                    && entity.getWorld().equals(target.getWorld()) && plugin.scheduler().isOwned(target)) {
                Native.snap(entity, target);
                return;
            }
            if (move == null) {
                move = new Move();
                MOVES.put(entity, move);
            }
            move.target = target.clone();
            move.version++;
            if (move.inFlight) return;
        }
        dispatch(plugin, entity, move);
    }

    private static void dispatch(TDGamesPlugin plugin, Entity entity, Move move) {
        Location target;
        long version;
        synchronized (MOVES) {
            if (MOVES.get(entity) != move || move.retired) return;
            target = move.target.clone();
            version = move.version;
            move.inFlight = true;
        }
        plugin.scheduler().teleport(entity, target, success -> {
            if (!plugin.isEnabled()) {
                synchronized (MOVES) { MOVES.remove(entity); }
                return;
            }
            boolean retired, again;
            synchronized (MOVES) {
                if (MOVES.get(entity) != move) return;
                move.inFlight = false;
                retired = move.retired;
                again = !retired && move.version != version;
                if (!again) MOVES.remove(entity);
            }
            if (retired && plugin.isEnabled()) entity.remove();
            else if (again) dispatch(plugin, entity, move);
            else if (!success) plugin.getLogger().warning("Could not move a tabletop display to " + target);
        });
    }

    static boolean retire(Entity entity) {
        // Region shutdown has no current world data, even when ownership checks succeed.
        boolean stopping = FOLIA && !JavaPlugin.getPlugin(TDGamesPlugin.class).isEnabled();
        synchronized (MOVES) {
            Move move = MOVES.get(entity);
            if (move == null) return stopping;
            move.retired = true;
            if (!move.inFlight || stopping) MOVES.remove(entity);
            return move.inFlight || stopping;
        }
    }

    private static final class Native {
        private static final ClassValue<Method> HANDLES = new ClassValue<>() {
            @Override protected Method computeValue(Class<?> type) {
                try { return type.getMethod("getHandle"); }
                catch (ReflectiveOperationException failure) { throw unavailable(failure); }
            }
        };
        private static final ClassValue<Method> POSITIONS = new ClassValue<>() {
            @Override protected Method computeValue(Class<?> type) {
                try { return type.getMethod("snapTo", double.class, double.class, double.class, float.class, float.class); }
                catch (ReflectiveOperationException failure) { throw unavailable(failure); }
            }
        };

        static void snap(Entity entity, Location target) {
            try {
                Object handle = HANDLES.get(entity.getClass()).invoke(entity);
                POSITIONS.get(handle.getClass()).invoke(handle, target.getX(), target.getY(), target.getZ(),
                        target.getYaw(), target.getPitch());
            } catch (ReflectiveOperationException failure) {
                throw unavailable(failure);
            }
        }

        private static IllegalStateException unavailable(ReflectiveOperationException failure) {
            return new IllegalStateException("Cannot update a region-owned tabletop display on this Folia version", failure);
        }
    }
}

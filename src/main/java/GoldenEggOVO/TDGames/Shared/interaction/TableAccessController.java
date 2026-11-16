package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.platform.PlatformEvents;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;

public final class TableAccessController implements Listener, AutoCloseable {
    public record Entry(
            String id,
            Location center,
            String kind,
            boolean sideTray,
            GameSession.Phase phase,
            List<String> names,
            int capacity,
            Set<UUID> members,
            Consumer<Player> join,
            Consumer<Player> menu) {
        int empty() {
            return Math.max(0, capacity - names.size());
        }
    }

    private final TDGamesPlugin plugin;
    private final Map<UUID, Long> clicks = new HashMap<>();
    private List<Entry> entries = List.of();
    private PlatformScheduler.Task task;
    private boolean readFailed;

    public TableAccessController(TDGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PlatformEvents.register(plugin, this);
        task = plugin.scheduler().repeat(this::refresh, 100, 20);
    }

    public List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        for (GameSession r : plugin.games().activeSessions().values()) {
            Location c = plugin.tableWorld().center(r.table);
            out.add(
                    new Entry(
                            "board:" + r.id,
                            c,
                            r.kind,
                            r.sideTray,
                            r.phase(),
                            r.seats.stream().filter(s -> !r.departed.contains(s.id())).map(GameSession.Seat::name).toList(),
                            r.capacity,
                            new HashSet<>(r.seats.stream().filter(s -> !r.departed.contains(s.id())).map(GameSession.Seat::id).toList()),
                            p -> plugin.games().join(p, r),
                            p -> plugin.menus().room(p, r)));
        }
        return out;
    }

    private boolean readEntries() {
        try {
            entries = List.copyOf(collect());
            readFailed = false;
            return true;
        } catch (RuntimeException ex) {
            if (!readFailed)
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Could not read public table seats",
                                ex);
            readFailed = true;
            return false;
        }
    }

    void refresh() {
        readEntries();
    }

    public static double hit(Entry e, Location eye, Vector direction) {
        return TableGeometry.menuHit(e.kind,e.sideTray,
                e.center.clone().add(0,TableGeometry.SURFACE,0),eye,direction);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.HAND
                && (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                    || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)
                && request(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void entity(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.HAND && request(e.getPlayer())) e.setCancelled(true);
    }

    public boolean request(Player p) {
        if (!p.isSneaking() || !plugin.allowed(p)) return false;
        GameSession room = plugin.games().session(p);
        if (room != null && room.board() != null && plugin.tableWorld() != null)
            return plugin.tableWorld().worldClick(p, true);
        if (!readEntries()) return false;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Entry target = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Entry e : entries) {
            double distance = hit(e, eye, dir);
            if (distance < nearest) {
                nearest = distance;
                target = e;
            }
        }
        if (target == null) return false;
        long now = System.nanoTime();
        if (now - clicks.getOrDefault(p.getUniqueId(), 0L) < 250_000_000L) return true;
        clicks.put(p.getUniqueId(), now);
        if (plugin.tablePlayers() != null && plugin.tablePlayers().hasTableFocus(p)) plugin.tablePlayers().releaseFocus(p);
        String id = target.id;

        plugin.scheduler()
                .later(
                        p,
                        () -> {
                            if (!p.isOnline() || !plugin.allowed(p)) return;
                            try {
                                Entry live =
                                        collect().stream()
                                                .filter(e -> e.id.equals(id))
                                                .findFirst()
                                                .orElse(null);
                                if (live == null) {
                                    plugin.tell(p, Language.component("chat.table.removed"));
                                    return;
                                }
                                if (live.members.contains(p.getUniqueId())) {
                                    live.menu.accept(p);
                                    return;
                                }
                                if (plugin.games().session(p) != null) {
                                    plugin.tell(p, Language.component("chat.leave-first"));
                                    return;
                                }
                                if (live.phase != GameSession.Phase.LOBBY) {
                                    plugin.tell(p, Language.component("chat.table.started"));
                                    return;
                                }
                                if (live.empty() == 0) {
                                    plugin.tell(p, Language.component("chat.table.full"));
                                    return;
                                }
                                live.join.accept(p);
                                refresh();
                            } catch (IllegalArgumentException ex) {
                                plugin.tell(p, Language.error(ex));
                            } catch (IllegalStateException ex) {
                                plugin.tell(p, Language.component("error.invalid"));
                            }
                        }, 1);
        return true;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        clicks.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        entries = List.of();
        HandlerList.unregisterAll(this);
    }
}

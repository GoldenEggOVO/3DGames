package GoldenEggOVO.TDGames.Multiplayer;

import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Shared.session.*;
import GoldenEggOVO.TDGames.Multiplayer.room.*;

public final class MultiplayerService {
    private final TDGamesPlugin plugin;
    private final SessionRuntime runtime;
    private final GameService games;
    private final Map<UUID, Seating> seating = new HashMap<>();
    private static final class Seating {
        final GameSession room;
        final List<GameSession.Seat> previous;
        final long revision;
        final Map<Player, Location> origins = new LinkedHashMap<>();
        int remaining;
        Seating(GameSession room, List<GameSession.Seat> previous, int remaining) {
            this.room = room;
            this.previous = previous;
            this.revision = room.revision();
            this.remaining = remaining;
        }
    }
    public MultiplayerService(TDGamesPlugin plugin, SessionRuntime runtime, GameService games) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.games = games;
    }

    public void join(Player p, GameSession r) {
        requireMultiplayer(r);
        if (!plugin.allowed(p)) return;
        games.requireLiveSession(r);
        if (r.departed.contains(p.getUniqueId()))
            throw new RuleViolation("error.this-room-is-full-or-has-started", "This room is full or has started");
        if (r.seat(p.getUniqueId()) >= 0) {
            games.resume(p, r);
            return;
        }
        if (!plugin.sessions().reserve(p.getUniqueId(), r.kind))
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        try {
            joinReserved(p, r);
        } catch (RuntimeException | Error ex) {
            try {
                if (r.removeSeat(p.getUniqueId())) {
                    games.save();
                }
            } finally {
                plugin.sessions().release(p.getUniqueId(), r.kind);
            }
            throw ex;
        }
    }

    public void joinReserved(Player p, GameSession r) {
        requireMultiplayer(r);
        if (!plugin.allowed(p)) return;
        games.requireLiveSession(r);
        if (plugin.pack() != null) plugin.pack().require(p, r.kind);
        GameSession old = games.session(p);
        if (old != null && old != r)
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        boolean already = r.seat(p.getUniqueId()) >= 0;
        r.join(p.getUniqueId(), p.getName());
        if (!runtime.enterJoinedArena(p, r, !already)) {
            if (!already) {
                r.removeSeat(p.getUniqueId());
                games.save();
            }
            throw new RuleViolation(
                    "error.teleport-cancelled-could-not-take-a-seat", "Teleport cancelled; could not take a seat. Remove the restriction and try again.");
        }
        games.announce(r, Language.component("chat.joined", "player", p.getName()));
        games.save();
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
        plugin.menus().room(p, r);
    }

    public void ready(Player p, GameSession r) {
        requireMultiplayer(r);
        if (r.stake() != null && r.stake() > 0) {
            if (plugin.allowed(p)) plugin.menus().room(p, r);
            return;
        }
        ready(p, r, r.stake());
    }

    public void ready(Player p, GameSession r, Double confirmedStake) {
        if (runtime.transferring(p.getUniqueId()) || seating.containsKey(r.id)) return;
        requireMultiplayer(r);
        if (!plugin.allowed(p)) return;
        games.requireLiveSession(r);
        if (!Objects.equals(r.stake(), confirmedStake)) throw new RuleViolation("error.stake.changed", "Stake changed; confirm the current amount");
        if (plugin.pack() != null) plugin.pack().require(p, r.kind);
        if (r.phase() != GameSession.Phase.LOBBY || r.seat(p.getUniqueId()) < 0) return;
        r.toggleReady(p.getUniqueId());
        if (r.seats.size() == r.capacity
                && r.seats.stream().allMatch(s -> s.bot() || r.ready.contains(s.id()))) games.start(r);
        else {
            games.save();
            plugin.menus().room(p, r);
        }
    }

    public void startWithBots(Player p, GameSession r) {
        if (runtime.transferring(p.getUniqueId()) || seating.containsKey(r.id)) return;
        requireMultiplayer(r);
        if (!plugin.allowed(p)) return;
        games.requireLiveSession(r);
        if (r.stake() != null && r.stake() > 0) throw new RuleViolation("error.stake.bots", "Select a free game before adding bots");
        if (plugin.pack() != null) plugin.pack().require(p, r.kind);
        if (r.phase() != GameSession.Phase.LOBBY || !r.host(p.getUniqueId()))
            throw new RuleViolation("error.only-the-host-can-add-bots", "Only the host can add bots");
        if (r.seats.stream()
                .filter(s -> !s.bot() && !s.id().equals(p.getUniqueId()))
                .anyMatch(s -> !r.ready.contains(s.id())))
            throw new RuleViolation("error.wait-for-the-other-players-to-ready", "Wait for the other players to ready");
        r.fillBots();
        games.start(r);
    }

    public boolean prepareSeats(GameSession room) {
        if (seating.containsKey(room.id)) return false;
        List<GameSession.Seat> previous = List.copyOf(room.seats);
        room.prepareSeats();
        if (room.restoring() || previous.equals(room.seats)) return true;
        if (plugin.scheduler().folia()) {
            prepareAsync(room, previous);
            return false;
        }
        Map<Player, Location> moved = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (int seat = 0; seat < room.seats.size(); seat++) {
                GameSession.Seat member = room.seats.get(seat);
                if (member.bot() || previous.indexOf(member) == seat) continue;
                Player player = Bukkit.getPlayer(member.id());
                if (player == null || !plugin.allowed(player) || !plugin.tableWorld().atTableWorld(player, room))
                    continue;
                Location target = plugin.tableWorld().seatLocation(room, seat);
                plugin.closePlayerViews(player);
                Location before = player.getLocation().clone();
                if (!player.teleport(target)) return false;
                moved.put(player, before);
            }
            success = true;
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        } finally {
            if (!success) {
                room.restoreSeating(previous);
                moved.forEach(Player::teleport);
                if (room.board() == null) runtime.preview(room);
                else plugin.tableWorld().render(room);
                games.save();
                games.announce(room, Language.component("chat.seat-change-blocked"));
                games.showRoomToHumans(room);
            }
        }
    }

    private void prepareAsync(GameSession room, List<GameSession.Seat> previous) {
        List<GameSession.Seat> moving = room.seats.stream()
                .filter(member -> !member.bot() && previous.indexOf(member) != room.seat(member.id()))
                .toList();
        Seating transfer = new Seating(room, previous, moving.size());
        seating.put(room.id, transfer);
        plugin.scheduler().later(() -> seatingFinished(transfer, false), 200);
        if (moving.isEmpty()) { seatingFinished(transfer, true); return; }
        plugin.tableWorld().atTable(room, () -> {
            if (seating.get(room.id) != transfer) return;
            for (var member : moving) {
                Player player = Bukkit.getPlayer(member.id());
                if (player == null || !plugin.allowed(player) || !plugin.tableWorld().atTableWorld(player, room)) {
                    seatingFinished(transfer, false);
                    return;
                }
                Location target;
                try { target = plugin.tableWorld().seatLocation(room, room.seat(member.id())); }
                catch (IllegalArgumentException failure) { seatingFinished(transfer, false); return; }
                plugin.scheduler().execute(player, () -> {
                    if (seating.get(room.id) != transfer) return;
                    transfer.origins.put(player, player.getLocation().clone());
                    plugin.closePlayerViews(player);
                    plugin.scheduler().teleport(player, target, moved -> {
                        if (seating.get(room.id) != transfer) {
                            if (moved) restoreSeat(player, transfer);
                            return;
                        }
                        if (!moved || room.revision() != transfer.revision) seatingFinished(transfer, false);
                        else if (--transfer.remaining == 0) seatingFinished(transfer, true);
                    });
                });
            }
        }, () -> seatingFinished(transfer, false));
    }

    private void restoreSeat(Player player, Seating transfer) {
        Location origin = transfer.origins.get(player);
        if (origin != null && games.session(player) == transfer.room) {
            if (seating.containsKey(transfer.room.id) || transfer.room.phase() != GameSession.Phase.LOBBY)
                origin = plugin.tableWorld().seatLocation(transfer.room, transfer.room.seat(player.getUniqueId()));
            plugin.scheduler().teleport(player, origin, ignored -> {});
        }
    }

    private void seatingFinished(Seating transfer, boolean success) {
        GameSession room = transfer.room;
        if (seating.get(room.id) != transfer) return;
        seating.remove(room.id);
        if (games.activeSessions().get(room.id) != room) return;
        if (success && room.revision() == transfer.revision && room.phase() == GameSession.Phase.LOBBY) {
            games.start(room);
            games.showRoomToHumans(room);
            return;
        }
        List<GameSession.Seat> current = List.copyOf(room.seats);
        List<GameSession.Seat> restored = new ArrayList<>(transfer.previous.stream().filter(current::contains).toList());
        current.stream().filter(member -> !restored.contains(member)).forEach(restored::add);
        room.restoreSeating(restored);
        transfer.origins.keySet().forEach(player -> restoreSeat(player, transfer));
        if (room.board() == null) runtime.preview(room);
        else plugin.tableWorld().render(room);
        games.save();
        games.announce(room, Language.component("chat.seat-change-blocked"));
        games.showRoomToHumans(room);
    }

    public void cancelSeating(UUID player) {
        for (Seating transfer : List.copyOf(seating.values()))
            if (transfer.previous.stream().anyMatch(member -> member.id().equals(player))) seatingFinished(transfer, false);
    }

    public void rematch(Player p, GameSession r) {
        requireMultiplayer(r);
        if (r.stake() != null && r.stake() > 0) {
            if (plugin.allowed(p)) plugin.menus().room(p, r);
            return;
        }
        rematch(p, r, r.stake());
    }

    public void rematch(Player p, GameSession r, Double confirmedStake) {
        requireMultiplayer(r);
        if (!plugin.allowed(p)) return;
        games.requireLiveSession(r);
        if (!Objects.equals(r.stake(), confirmedStake)) throw new RuleViolation("error.stake.changed", "Stake changed; confirm the current amount");
        if (!plugin.tableWorld().atTableWorld(p, r)) return;
        if (RoundActions.rematchReady(r, p.getUniqueId())) {
            for (GameSession.Seat s : r.seats) {
                Player other = Bukkit.getPlayer(s.id());
                if (!s.bot() && other != null) plugin.closePlayerViews(other);
            }
            plugin.tableWorld().remove(r);
            Set<UUID> confirmed = Set.copyOf(r.ready);
            RoundActions.fresh(r, runtime.nextSeed());
            if (r.stake() != null && r.stake() > 0) r.restoreReady(confirmed);
            plugin.tableWorld().retainTableChunks(r.table);
            games.start(r);
            games.showRoomToHumans(r);
        } else {
            games.announce(r, Language.component("chat.rematch.ready", "player", p.getName()));
            games.save();
            games.showRoomToHumans(r);
        }
    }

    public void leave(Player p) {
        GameSession r = games.session(p);
        if (r != null) requireMultiplayer(r);
        if (r != null && r.phase() == GameSession.Phase.PLAYING && r.board() instanceof ClassicGame classic
                && classic.eliminated(r.seat(p.getUniqueId()))) {
            r.markDeparted(Set.of(p.getUniqueId()));
            plugin.sessions().release(p.getUniqueId(), r.kind);
            plugin.closePlayerViews(p);
            games.save();
            plugin.menus().main(p);
            return;
        }
        if (r != null && r.stake() != null && r.stake() > 0 && r.phase() == GameSession.Phase.PAUSED
                && r.result().equals("error.economy.storage"))
            throw new RuleViolation("error.economy.storage", "Restore the economy journal before settling this game");
        plugin.closePlayerViews(p);
        if (r != null) {
            if (r.phase() == GameSession.Phase.PLAYING
                    || r.phase() == GameSession.Phase.STARTING
                    || r.phase() == GameSession.Phase.PAUSED) {
                if (plugin.integrations() != null && plugin.integrations().isStakeGame(r)) forfeit(r, Set.of(p.getUniqueId()), false);
                else games.abort(r, "result.left", Language.component("result.left", "player", p.getName()));
            } else if (r.phase() == GameSession.Phase.FINISHED) {
                games.remove(r);
            } else {
                r.removeSeat(p.getUniqueId());
                if (r.seats.stream().noneMatch(s -> !s.bot())) games.remove(r);
            }
        }
        if (r != null) plugin.sessions().release(p.getUniqueId(), r.kind);
        games.save();
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
        plugin.menus().main(p);
    }

    private void forfeit(GameSession room, Set<UUID> players, boolean expired) {
        String outcome = ForfeitPolicy.outcome(room, players);
        String names = room.seats.stream().filter(seat -> players.contains(seat.id())).map(GameSession.Seat::name)
                .collect(java.util.stream.Collectors.joining(", "));
        games.announce(room, Language.component(expired ? "chat.stake.expired" : "chat.stake.resigned", "players", names));
        room.markDeparted(players);
        for (UUID player : players) {
            room.markOnline(player);
            plugin.sessions().release(player, room.kind);
        }
        games.finish(room, outcome);
        plugin.tableWorld().render(room);
        if (room.occupied() == 0) games.remove(room);
        games.save();
    }

    public void setStake(Player player, GameSession room, long revision, double amount) {
        if (seating.containsKey(room.id)) return;
        requireMultiplayer(room);
        if (!plugin.allowed(player)) return;
        games.requireLiveSession(room);
        if (!plugin.tableWorld().atTableWorld(player, room)) throw new RuleViolation("error.game-changed", "Return to the table first");
        if (plugin.integrations() == null) throw new RuleViolation("error.stake.disabled", "Player stakes are disabled");
        plugin.integrations().changeStake(room, player.getUniqueId(), revision, amount);
        games.save();
        games.showRoomToHumans(room);
    }

    public long turnWaitMillis(GameSession room) {
        int turn = room.turn();
        if (turn < 0 || turn >= room.seats.size()) return 0;
        GameSession.Seat seat = room.seats.get(turn);
        return seat.bot()
                ? 2000
                : room.kind.equals("color-eight")
                        ? 30000
                        : room.offline.containsKey(seat.id())
                                ? 5000
                                : plugin.getConfig().getLong("turn-seconds", 60) * 1000L;
    }

    public Map<UUID, GameSession> rooms() {
        Map<UUID, GameSession> rooms = new LinkedHashMap<>();
        runtime.activeSessions().forEach((id, session) -> { if (session.capacity > 1) rooms.put(id, session); });
        return Collections.unmodifiableMap(rooms);
    }
    public void create(Player player, String kind, int capacity, Map<String, String> options) {
        requireMultiplayer(kind, capacity);
        games.createReservedForMode(player, kind, capacity, options);
    }
    public void createReserved(Player player, String kind, int capacity, Map<String, String> options) {
        requireMultiplayer(kind, capacity);
        GameSession session = runtime.createReserved(player, kind, capacity, options, runtime.nextSeed(), null);
        if (plugin.integrations() != null) plugin.integrations().configureRoom(session);
        games.save();
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
        plugin.menus().room(player, session);
    }
    public void start(GameSession session) {
        requireMultiplayer(session);
        if (session.seats.stream().anyMatch(member -> runtime.transferring(member.id()))) return;
        runtime.requireResourcePacks(session);
        if (!games.prepareSeats(session)) return;
        if (!session.restoring() && plugin.integrations() != null) plugin.integrations().beforeStart(session);
        boolean restoring = session.restoring();
        try { runtime.start(session); games.save(); }
        catch (RuntimeException exception) {
            if (restoring) throw exception;
            if (plugin.integrations() != null) plugin.integrations().cancel(session);
            games.pause(session, "chat.pause.rules");
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Board start failed", exception);
        } finally { session.endRestore(); }
    }
    public void apply(GameSession session, int seat, JsonElement action, Player source, boolean reopenMenu) {
        requireMultiplayer(session);
        games.requireLiveSession(session);
        if (session.phase() != GameSession.Phase.PLAYING || session.busy()) return;
        if (!session.offline.isEmpty()) {
            if (source != null) plugin.tell(source, Language.component("error.stake.waiting"));
            return;
        }
        var result = runtime.apply(session, seat, action, source);
        if (result == SessionRuntime.ApplyResult.HISTORY_LIMIT) {
            games.finish(session, "result.limit"); games.save(session);
        } else if (result == SessionRuntime.ApplyResult.APPLIED) {
            if (session.board().finished()) games.finish(session, session.board().outcome());
            games.save(session);
            if (reopenMenu && source != null && source.isOnline()) plugin.menus().room(source, session);
        }
    }
    public void finish(GameSession session, String result) {
        requireMultiplayer(session);
        boolean first = session.finish(result);
        if (first && !session.restoring() && plugin.integrations() != null) plugin.integrations().finish(session, result);
        runtime.finished(session, result, first);
    }
    public void remove(GameSession session) {
        requireMultiplayer(session);
        seating.remove(session.id);
        if (plugin.integrations() != null) plugin.integrations().cancel(session);
        runtime.remove(session);
    }
    public void save() {
        try { RoomStore.write(plugin.getDataFolder().toPath().resolve("rooms.json"), rooms().values()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not save room records: " + exception.getClass().getSimpleName()); }
    }
    private static void requireMultiplayer(GameSession session) { requireMultiplayer(session.kind, session.capacity); }
    private static void requireMultiplayer(String kind, int capacity) {
        if (capacity <= 1 || !GameCatalog.capacityValid(kind, capacity)) throw new IllegalArgumentException("Multiplayer requires a supported multiplayer game");
    }

    public void restore() {
        Path file = plugin.getDataFolder().toPath().resolve("rooms.json");
        if (!Files.exists(file)) return;
        try {
            for (GameSession room : RoomStore.read(file)) {
                requireMultiplayer(room);
                for (GameSession.Seat seat : room.seats)
                    if (!seat.bot() && !room.departed.contains(seat.id())) {
                        if (!plugin.sessions().restoreReservation(seat.id(), room.kind))
                            throw new IllegalStateException(
                                    "Restored seat conflicts with another game");
                        room.markOffline(seat.id(), System.currentTimeMillis());
                    }
                runtime.register(room);
                if (room.phase() == GameSession.Phase.LOBBY) {
                    runtime.preview(room);
                } else {
                    room.beginRestore();
                    games.start(room);
                }
            }
            plugin.getLogger().info("Restored " + rooms().size() + " board rooms.");
        } catch (Exception exception) {
            try {
                Files.copy(
                        file,
                        file.resolveSibling(
                                "rooms.unreadable-" + System.currentTimeMillis() + ".json"));
            } catch (IOException backupFailure) {
                exception.addSuppressed(backupFailure);
            }
            throw new IllegalStateException(
                    "Room records could not be restored; original file retained", exception);
        }
    }

    public boolean presence(GameSession r, long now) {
        requireMultiplayer(r);
        for (GameSession.Seat s : r.seats)
            if (!s.bot() && !r.departed.contains(s.id())) {
                if (r.board() instanceof ClassicGame classic && classic.eliminated(r.seat(s.id()))) {
                    r.markOnline(s.id());
                    continue;
                }
                Player p = Bukkit.getPlayer(s.id());
                boolean present =
                        p != null
                                && plugin.allowed(p)
                                && runtime.atLoadedTableWorld(p, r)
                                && (plugin.pack() == null || plugin.pack().canPlay(p, r.kind));
                if (present) r.markOnline(s.id());
                else r.markOffline(s.id(), now);
            }
        Set<UUID> expired = r.offline.entrySet().stream()
                .filter(entry -> !(r.board() instanceof ClassicGame classic && classic.eliminated(r.seat(entry.getKey()))))
                .filter(entry -> now - entry.getValue() > Math.max(0, plugin.getConfig().getLong("reconnect-seconds", 120)) * 1000L)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        if (!expired.isEmpty()) {
            if (r.phase() == GameSession.Phase.PLAYING && plugin.integrations() != null && plugin.integrations().isStakeGame(r)) forfeit(r, expired, true);
            else games.abort(r, "chat.abort.offline");
            return false;
        }
        if (r.phase() == GameSession.Phase.PLAYING && !r.offline.isEmpty()) {
            r.refreshTurnClock(now);
            long earliest = r.offline.values().stream().mapToLong(Long::longValue).min().orElse(now);
            long remaining = Math.max(0, plugin.getConfig().getLong("reconnect-seconds", 120) - (now - earliest) / 1000);
            Component message = Language.component("stake.waiting", "seconds", remaining);
            for (GameSession.Seat seat : r.seats) {
                Player player = Bukkit.getPlayer(seat.id());
                if (!seat.bot() && player != null && plugin.allowed(player))
                    plugin.scheduler().execute(player, () -> PlatformMessages.actionBar(player, message));
            }
            return false;
        }
        return true;
    }

    public void tick(GameSession r, long now) {
        requireMultiplayer(r);
        int turn = r.turn();
        if (turn < 0 || turn >= r.seats.size()) return;
        GameSession.Seat s = r.seats.get(turn);
        long wait = turnWaitMillis(r);
        plugin.tableWorld().countdown(r, now);
        if (!s.bot()
                && plugin.pack() != null
                && plugin.pack().mode == ResourcePackService.Mode.RESOURCE_PACK
                && ResourcePackService.supported(r.kind)) {
            Player player = Bukkit.getPlayer(s.id());
            if (player == null || !plugin.pack().canPlay(player, r.kind)) return;
        }
        String choice =
                TurnPolicy.choose(
                        r.board(),
                        turn,
                        s.bot(),
                        now - r.changed(),
                        wait,
                        runtime.random(),
                        !r.offline.containsKey(s.id()),
                        plugin.tableWorld().mahjongAssistance(r, turn));
        if (choice != null) games.apply(r, turn, new JsonPrimitive(choice), null);
    }
}

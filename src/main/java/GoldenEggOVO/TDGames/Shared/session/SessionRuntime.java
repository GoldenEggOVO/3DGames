package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Shared.text.BoardMessages;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.game.HandGame;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.*;

import GoldenEggOVO.TDGames.Core.game.GameService;

public final class SessionRuntime {
    private final TDGamesPlugin plugin;
    private final GameService games;
    private final Map<UUID, GameSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, GameSession> view = Collections.unmodifiableMap(sessions);
    private final SecureRandom random = new SecureRandom();
    public SessionRuntime(TDGamesPlugin plugin, GameService games) {
        this.plugin = plugin;
        this.games = games;
    }

    public Map<UUID, GameSession> activeSessions() { return view; }

    public GameSession session(Player p) {
        return session(p.getUniqueId());
    }

    public GameSession session(UUID p) {
        return sessions.values().stream()
                .filter(r -> r.seat(p) >= 0 && r.phase != GameSession.Phase.ABORTED)
                .findFirst()
                .orElse(null);
    }

    public boolean atLoadedTableWorld(Player player, GameSession room) {
        try { return plugin.tableWorld().atTableWorld(player, room); }
        catch (IllegalStateException unloaded) { return false; }
    }

    public void announce(GameSession r, Component message) {
        for (GameSession.Seat s : r.seats) {
            Player p = Bukkit.getPlayer(s.id());
            if (p != null && !s.bot()) plugin.tell(p, message);
        }
    }

    public void requireLiveSession(GameSession room) {
        if (sessions.get(room.id) != room) throw new RuleViolation("error.room-closed", "Room closed");
    }

    public GameSession createReserved(Player p, String kind, int capacity, Map<String, String> options, long seed, JsonArray history) {
        if (plugin.pack() != null) plugin.pack().require(p, kind);
        if (!GameCatalog.capacityValid(kind, capacity))
            throw new RuleViolation("error.unsupported-game-or-player-count", "Unsupported game or player count");
        if (session(p) != null)
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        if (sessions.size() >= plugin.getConfig().getInt("max-rooms", 12))
            throw new RuleViolation(
                    "error.room-limit-reached-join-an-existing-room", "Room limit reached; join an existing room");
        int index = 0;
        Set<Integer> used = new HashSet<>();
        sessions.values().forEach(r -> used.add(r.table));
        while (used.contains(index)) index++;
        GameSession r = new GameSession(UUID.randomUUID(), kind, capacity, seed, index, options);
        if (history != null) r.history.restore(history);
        r.sideTray = Set.of("ludo", "backgammon", "monopoly").contains(kind);
        plugin.tableWorld().anchor(r, p.getLocation());
        r.join(p.getUniqueId(), p.getName());
        sessions.put(r.id, r);
        try {
            for (int seat = 0; seat < r.capacity; seat++) plugin.tableWorld().seatLocation(r, seat);
            plugin.tableWorld().retainTableChunks(index);
            r.board = r.newBoard();
            plugin.tableWorld().render(r);
        } catch (RuntimeException ex) {
            plugin.tableWorld().remove(r);
            sessions.remove(r.id);
            throw ex;
        }
        if (!enterArena(p, r)) {
            remove(r);
            throw new RuleViolation(
                    "error.teleport-cancelled-room-was-not-created-remove",
                    "Teleport cancelled; room was not created. Remove the teleport restriction and try again.");
        }
        return r;
    }

    public boolean enterArena(Player p, GameSession r) {
        if (!plugin.allowed(p)) return false;
        requireLiveSession(r);
        if (plugin.pack() != null) plugin.pack().require(p, r.kind);
        if (r.seat(p.getUniqueId()) < 0)
            throw new RuleViolation("error.you-have-not-joined-a-room", "You have not joined a room");
        if (!p.teleport(plugin.tableWorld().seatLocation(r, r.seat(p.getUniqueId())))) {
            r.markOffline(p.getUniqueId(), System.currentTimeMillis());
            return false;
        }
        r.markOnline(p.getUniqueId());
        return true;
    }

    public void resume(Player p, GameSession r) {
        if (!plugin.allowed(p)) return;
        requireLiveSession(r);
        if (!enterArena(p, r)) {
            plugin.tell(p, GameCatalog.solo(r.kind, r.capacity)
                    ? Language.component("chat.resume.cancelled.solo")
                    : Language.component("chat.resume.cancelled.timed", "seconds",
                            Math.max(0, plugin.getConfig().getLong("reconnect-seconds", 120))));
            plugin.menus().room(p, r);
            return;
        }
        plugin.menus().room(p, r);
    }

    public void requireResourcePacks(GameSession r) {
        if (!r.restoring && plugin.pack() != null)
            for (GameSession.Seat seat : r.seats)
                if (!seat.bot()) {
                    Player player = Bukkit.getPlayer(seat.id());
                    if (player != null) plugin.pack().require(player, r.kind);
                }
    }

    public void start(GameSession r) {
        r.beginStart();

        try {
            r.board = r.newBoard();
            for (JsonElement e : r.history) {
                JsonObject j = e.getAsJsonObject();
                r.board.apply(j.get("seat").getAsInt(), j.get("action").getAsString());
            }
            if (!r.departed.isEmpty() && !r.completed)
                for (int seat = 0; seat < r.seats.size(); seat++)
                    if (r.departed.contains(r.seats.get(seat).id())
                            && !(r.board instanceof ClassicGame classic && classic.eliminated(seat)))
                        throw new IllegalArgumentException("An active departed player must be eliminated");
            r.started();
            plugin.tableWorld().render(r);
            if (!r.restoring && r.phase == GameSession.Phase.PLAYING) {
                if (r.history.isEmpty()) announce(r, BoardMessages.opening(r.kind));
                plugin.tableWorld().sound(r, TableSounds.START);
            }
        } catch (RuntimeException ex) {
            r.busy = false;
            throw ex;
        }
    }

    public void action(Player p, GameSession r, long revision, JsonElement action) {
        if (!plugin.allowed(p)) return;
        requireLiveSession(r);
        if (plugin.pack() != null) plugin.pack().require(p, r.kind);
        if (!plugin.tableWorld().atTableWorld(p, r))
            throw new RuleViolation(
                    "error.use-3dgames-resume-to-return-to-the",
                    "Use /3dgames resume to return to the table world first");
        r.requireAction(p.getUniqueId(), revision);
        games.apply(r, r.seat(p.getUniqueId()), action, p);
    }

    public ApplyResult apply(GameSession r, int seat, JsonElement action, Player source) {
        requireLiveSession(r);
        if (r.phase != GameSession.Phase.PLAYING || r.busy) return ApplyResult.IGNORED;
        if (plugin.tableWorld().rolling(r)) {
            if (source != null) plugin.tell(source, Language.component("hint.roll.wait"));
            return ApplyResult.IGNORED;
        }
        try {
            if (action == null || !action.isJsonPrimitive()
                    || !action.getAsJsonPrimitive().isString())
                throw new RuleViolation("error.invalid", "Action must be a string");
            if (source != null && r.board instanceof ClassicGame classic
                    && Objects.equals(classic.automaticAction(), action.getAsString()))
                throw new RuleViolation("error.game-changed", "Wait for the automatic reveal to finish");
            if (!r.history.allows(action)) return ApplyResult.HISTORY_LIMIT;
            List<Cell> before = r.board.cells();
            var mahjongBefore =
                    r.kind.equals("mahjong") ? TableSounds.mahjongState((HandGame) r.board) : null;
            boolean cardGame = Set.of("doudizhu", "liars-bar", "texas-holdem").contains(r.kind);
            var cardBefore = cardGame ? ((HandGame) r.board).publicInfo() : Map.<String, String>of();
            boolean canBeat = !r.kind.equals("doudizhu") || !action.getAsString().equals("pass")
                    || r.board.legalActions(seat).stream().anyMatch(a -> a.startsWith("play:"));
            int previousTurn = r.turn();
            String previousMonopolyPhase = r.kind.equals("monopoly") ? r.board.publicInfo().get("phase") : null;
            r.board.apply(seat, action.getAsString());
            r.event(seat, action);
            r.revision++;
            boolean backgroundManagement = previousMonopolyPhase != null && seat != previousTurn && r.turn() == previousTurn
                    && Objects.equals(previousMonopolyPhase, r.board.publicInfo().get("phase"))
                    && Set.of("build", "sell", "sell-group", "mortgage", "redeem").contains(action.getAsString().split(":")[0]);
            if (!backgroundManagement && (!r.kind.equals("color-eight")
                    || !Set.of("draw", "choose").contains(action.getAsString().split(":")[0])
                    || r.turn() != previousTurn)) r.changed = System.currentTimeMillis();
            present(r, () -> plugin.tableWorld().render(r));
            if (!r.restoring) present(r, () -> {
                if (mahjongBefore != null)
                    for (var cue :
                            TableSounds.mahjong(
                                    action.getAsString(),
                                    mahjongBefore,
                                    TableSounds.mahjongState((HandGame) r.board)))
                        plugin.tableWorld().sound(r, cue);
                else if (cardGame)
                    for (var cue : TableSounds.cards(r.kind, action.getAsString(), cardBefore,
                            ((HandGame) r.board).publicInfo(), canBeat)) plugin.tableWorld().sound(r, cue);
                else {
                    if (action.getAsString().startsWith("move:") && !r.kind.equals("klondike") && !before.equals(r.board.cells()))
                        plugin.tableWorld().detail(r, "board.move", .1f);
                    plugin.tableWorld().sound(
                            r,
                            TableSounds.move(
                                    r.kind, seat, action.getAsString(), before, r.board.cells()));
                }
            });
            return ApplyResult.APPLIED;
        } catch (IllegalArgumentException ex) {
            if (source != null) plugin.tell(source, Language.error(ex));
            return ApplyResult.IGNORED;
        }
    }

    public void finished(GameSession r, String result, boolean first) {
        if (first && !r.restoring && !(r.kind.equals("mahjong") && r.board.finished()))
            present(r, () -> plugin.tableWorld().sound(
                    r,
                    result.startsWith("winner:") || result.startsWith("winners:")
                            ? TableSounds.WIN
                            : TableSounds.DRAW));
        present(r, () -> announce(r, Language.component("chat.finished", "result", RoomText.outcome(r, result))));
        present(r, () -> games.onMain(() -> present(r, () -> showRoomToHumans(r))));
    }

    private void present(GameSession room, Runnable update) {
        try { update.run(); }
        catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not update table presentation for " + room.id, exception);
        }
    }

    public void showRoomToHumans(GameSession r) {
        if (!sessions.containsKey(r.id)) return;
        for (GameSession.Seat s : r.seats) {
            Player p = Bukkit.getPlayer(s.id());
            if (!s.bot() && !r.departed.contains(s.id()) && p != null && plugin.allowed(p) && plugin.tableWorld().atTableWorld(p, r)) plugin.menus().room(p, r);
        }
    }

    public void remove(GameSession r) {
        for (GameSession.Seat seat : r.seats) if (!seat.bot() && !r.departed.contains(seat.id())) plugin.sessions().release(seat.id(), r.kind);
        plugin.tableWorld().remove(r);
        sessions.remove(r.id);
        for (GameSession.Seat s : r.seats) {
            if (r.departed.contains(s.id())) continue;
            Player p = Bukkit.getPlayer(s.id());
            if (p != null) plugin.closePlayerViews(p);
        }
    }

    public enum ApplyResult { IGNORED, HISTORY_LIMIT, APPLIED }
    public long nextSeed() { return random.nextLong(); }
    public SecureRandom random() { return random; }
    public void register(GameSession session) {
        sessions.put(session.id, session);
        plugin.tableWorld().retainTableChunks(session.table);
    }
    public void preview(GameSession session) {
        session.board = session.newBoard();
        plugin.tableWorld().render(session);
    }
    public boolean automatic(GameSession session, long now) {
        if (session.phase != GameSession.Phase.PLAYING || session.busy) return false;
        if (session.board instanceof ClassicGame classic && classic.automaticAction() != null) {
            if (now - session.changed >= classic.automaticDelayMillis())
                games.apply(session, session.turn(), new JsonPrimitive(classic.automaticAction()), null);
            return true;
        }
        return false;
    }
}

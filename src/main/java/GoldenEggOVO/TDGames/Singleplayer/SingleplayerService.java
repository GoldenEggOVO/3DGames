package GoldenEggOVO.TDGames.Singleplayer;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Shared.session.*;

public final class SingleplayerService {
    private final TDGamesPlugin plugin;
    private final SessionRuntime runtime;
    private final GameService games;
    public SingleplayerService(TDGamesPlugin plugin, SessionRuntime runtime, GameService games) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.games = games;
    }

    private SoloProgressStore soloStore() {
        return new SoloProgressStore(plugin.getDataFolder().toPath().resolve("solo"));
    }

    public boolean hasProgress(UUID player) { return soloStore().exists(player); }
    public boolean hasProgress(UUID player, String kind) { return soloStore().exists(player, kind); }

    public List<String> progressKinds(UUID player) {
        try { return soloStore().kinds(player); }
        catch (IOException | RuntimeException ignored) { return List.of(); }
    }

    public void resume(Player player) {
        resume(player, null);
    }

    public void resume(Player player, String kind) {
        if (!plugin.allowed(player)) return;
        SoloProgressStore.Progress progress;
        try {
            progress = kind == null ? soloStore().read(player.getUniqueId()) : soloStore().read(player.getUniqueId(), kind);
            BoardGame board = GameFactory.create(progress.kind(), 1, progress.seed(), progress.options());
            for (JsonElement element : progress.history()) {
                JsonObject event = element.getAsJsonObject();
                board.apply(0, event.get("action").getAsString());
            }
            if (board.finished()) throw new IllegalArgumentException("Saved solo game is already finished");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Cannot restore solo progress; original file retained", ex);
            throw new RuleViolation("error.solo.restore", "Cannot restore saved progress; original file retained");
        }
        if (!plugin.sessions().reserve(player.getUniqueId(), progress.kind()))
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        try {
            GameSession session = runtime.createReserved(player, progress.kind(), 1, progress.options(), progress.seed(), progress.history());
            session.beginRestore();
            games.start(session);
            created(player, session);
        } catch (RuntimeException | Error ex) {
            GameSession partial = games.session(player);
            if (partial != null) games.remove(partial);
            plugin.sessions().release(player.getUniqueId(), progress.kind());
            throw ex;
        }
    }

    public void persist(GameSession room) throws IOException {
        requireSolo(room);
        if (room.seats.isEmpty()) return;
        UUID player = room.seats.getFirst().id();
        if (room.phase() == GameSession.Phase.FINISHED || room.board() != null && room.board().finished()) soloStore().delete(player, room.kind);
        else soloStore().write(player, new SoloProgressStore.Progress(room.kind, room.seed(), room.options, room.history.snapshot()));
    }

    public void saveAndLeave(Player player) {
        GameSession room = games.session(player);
        if (room == null) return;
        requireSolo(room);
        try {
            persist(room);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Cannot save solo progress; active game retained", ex);
            throw new RuleViolation("error.solo.save", "Could not save progress; the active game was retained");
        }
        games.remove(room);
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
    }

    public void create(Player player, String kind, int capacity, Map<String, String> options, boolean replace) {
        requireSolo(kind, capacity);
        if (hasProgress(player.getUniqueId(), kind) && !replace)
            throw new RuleViolation("error.solo.overwrite", "Confirm replacing the saved solo game");
        games.createReservedForMode(player, kind, capacity, options);
    }
    public void create(Player player, String kind, Map<String, String> options, boolean replace) {
        create(player, kind, 1, options, replace);
    }
    public void createReserved(Player player, String kind, int capacity, Map<String, String> options) {
        requireSolo(kind, capacity);
        GameSession session = runtime.createReserved(player, kind, capacity, options, runtime.nextSeed(), null);
        games.start(session);
        created(player, session);
    }
    private void created(Player player, GameSession session) {
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
        plugin.menus().room(player, session);
    }
    public void start(GameSession session) {
        requireSolo(session);
        runtime.requireResourcePacks(session);
        boolean restoring = session.restoring();
        try { runtime.start(session); games.save(session); }
        catch (RuntimeException exception) {
            if (restoring) throw exception;
            games.pause(session, "chat.pause.rules");
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Board start failed", exception);
        } finally { session.endRestore(); }
    }
    public void restart(Player player, GameSession session) {
        requireSolo(session);
        if (!plugin.allowed(player)) return;
        games.requireLiveSession(session);
        if (!plugin.tableWorld().atTableWorld(player, session)) return;
        if (session.phase() != GameSession.Phase.FINISHED || session.busy() || session.seat(player.getUniqueId()) < 0)
            throw new RuleViolation("error.finish-this-game-before-starting-a-rematch", "Finish this game before starting a rematch");
        plugin.closePlayerViews(player);
        plugin.tableWorld().remove(session);
        session.resetRound(runtime.nextSeed());
        plugin.tableWorld().retainTableChunks(session.table);
        games.start(session);
        games.showRoomToHumans(session);
    }
    public void apply(GameSession session, int seat, JsonElement action, Player source, boolean reopenMenu) {
        requireSolo(session);
        var result = runtime.apply(session, seat, action, source);
        if (result == SessionRuntime.ApplyResult.HISTORY_LIMIT) {
            if (source != null) plugin.tell(source, Language.component("error.solo.limit"));
        } else if (result == SessionRuntime.ApplyResult.APPLIED) {
            if (session.board().finished()) games.finish(session, session.board().outcome());
            games.save(session);
            if (reopenMenu && source != null && source.isOnline()) plugin.menus().room(source, session);
        }
    }
    public void finish(GameSession session, String result) {
        requireSolo(session);
        runtime.finished(session, result, session.finish(result));
    }
    public void save() {
        for (GameSession session : List.copyOf(runtime.activeSessions().values())) {
            if (!GameCatalog.solo(session.kind, session.capacity)) continue;
            save(session);
        }
    }
    public void save(GameSession session) {
        requireSolo(session);
        try { persist(session); }
        catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not save solo progress; active room retained: " + exception.getClass().getSimpleName());
        }
    }
    public boolean tick(GameSession session, long now) {
        requireSolo(session);
        if (session.seats.isEmpty()) return true;
        Player player = Bukkit.getPlayer(session.seats.getFirst().id());
        if (player != null && runtime.atLoadedTableWorld(player, session)) return true;
        try {
            if (player != null) saveAndLeave(player);
            else { persist(session); games.remove(session); }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not save absent solo game; room retained");
        }
        return false;
    }
    public void disconnected(Player player) {
        GameSession session = games.session(player);
        if (session == null) return;
        requireSolo(session);
        try { saveAndLeave(player); }
        catch (RuleViolation exception) { plugin.getLogger().warning("Solo game retained after disconnect save failure"); }
    }
    public void worldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        for (GameSession session : List.copyOf(runtime.activeSessions().values())) {
            if (!GameCatalog.solo(session.kind, session.capacity) || !event.getWorld().getUID().equals(session.anchorWorld)) continue;
            try { persist(session); games.remove(session); }
            catch (IOException | RuntimeException exception) {
                event.setCancelled(true);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "World unload cancelled because solo progress could not be saved", exception);
            }
        }
    }
    private static void requireSolo(GameSession session) { requireSolo(session.kind, session.capacity); }
    private static void requireSolo(String kind, int capacity) {
        if (!GameCatalog.solo(kind, capacity)) throw new IllegalArgumentException("Singleplayer requires a supported solo game");
    }
}

package GoldenEggOVO.TDGames.Core.game;

import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.*;

import GoldenEggOVO.TDGames.Shared.session.*;
import GoldenEggOVO.TDGames.Singleplayer.SingleplayerService;
import GoldenEggOVO.TDGames.Multiplayer.MultiplayerService;

public final class GameService implements Listener, AutoCloseable {
    private final TDGamesPlugin plugin;
    private final SessionRuntime runtime;
    private final SingleplayerService singleplayer;
    private final MultiplayerService multiplayer;
    private boolean loaded;
    private boolean stopping;
    private int pulse;
    public GameService(TDGamesPlugin plugin) {
        this.plugin = plugin;
        runtime = new SessionRuntime(plugin, this);
        singleplayer = new SingleplayerService(plugin, runtime, this);
        multiplayer = new MultiplayerService(plugin, runtime, this);
    }

    public SessionRuntime runtime() { return runtime; }
    public SingleplayerService singleplayer() { return singleplayer; }
    public MultiplayerService multiplayer() { return multiplayer; }
    public Map<UUID, GameSession> activeSessions() { return runtime.activeSessions(); }
    public GameSession session(Player player) { return runtime.session(player); }
    public GameSession session(UUID player) { return runtime.session(player); }
    public void requireLiveSession(GameSession session) { runtime.requireLiveSession(session); }
    public boolean hasSoloProgress(UUID player) { return singleplayer.hasProgress(player); }
    public boolean hasSoloProgress(UUID player, String kind) { return singleplayer.hasProgress(player, kind); }
    public java.util.List<String> soloProgressKinds(UUID player) { return singleplayer.progressKinds(player); }
    public void resumeSolo(Player player) { singleplayer.resume(player); }
    public void resumeSolo(Player player, String kind) { singleplayer.resume(player, kind); }
    public void saveAndLeave(Player player) { singleplayer.saveAndLeave(player); }
    private static boolean solo(GameSession session) { return GameCatalog.solo(session.kind, session.capacity); }
    public void initialize() {
        plugin.tableWorld().initialize(); restore(); loaded = true; plugin.integrations().recover();
    }
    @Override public void close() { stopping = true; save(); }
    public void restore() { multiplayer.restore(); }
    public void save() {
        if (!loaded || !plugin.getDataFolder().isDirectory()) return;
        singleplayer.save(); multiplayer.save();
    }
    public void save(GameSession session) {
        if (!loaded || !plugin.getDataFolder().isDirectory() || activeSessions().get(session.id) != session) return;
        if (solo(session)) singleplayer.save(session);
        else multiplayer.save();
    }
    public void create(Player p, String kind, int capacity) { create(p, kind, capacity, Map.of()); }
    public void create(Player p, String kind, int capacity, Map<String, String> options) { create(p, kind, capacity, options, false); }
    public void create(Player p, String kind, int capacity, Map<String, String> options, boolean replaceSolo) {
        if (GameCatalog.solo(kind, capacity)) singleplayer.create(p, kind, capacity, options, replaceSolo);
        else multiplayer.create(p, kind, capacity, options);
    }
    public void createReserved(Player p, String kind, int capacity, Map<String, String> options) {
        if (GameCatalog.solo(kind, capacity)) singleplayer.createReserved(p, kind, capacity, options);
        else multiplayer.createReserved(p, kind, capacity, options);
    }
    public void start(GameSession session) {
        if (solo(session)) singleplayer.start(session); else multiplayer.start(session);
    }
    public boolean prepareSeats(GameSession session) { return solo(session) || multiplayer.prepareSeats(session); }
    public void apply(GameSession session, int seat, JsonElement action, Player source) {
        apply(session, seat, action, source, true);
    }
    public void applyTableAction(GameSession session, int seat, JsonElement action, Player source) {
        apply(session, seat, action, source, false);
    }
    private void apply(GameSession session, int seat, JsonElement action, Player source, boolean reopenMenu) {
        if (solo(session)) singleplayer.apply(session, seat, action, source, reopenMenu);
        else multiplayer.apply(session, seat, action, source, reopenMenu);
    }
    public void finish(GameSession session, String result) {
        if (solo(session)) singleplayer.finish(session, result); else multiplayer.finish(session, result);
    }
    public void remove(GameSession session) {
        if (solo(session)) runtime.remove(session); else multiplayer.remove(session);
    }
    public void leave(Player player) {
        multiplayer.cancelSeating(player.getUniqueId());
        runtime.cancelTransfer(player.getUniqueId());
        GameSession session = session(player);
        if (session != null && solo(session)) { singleplayer.saveAndLeave(player); plugin.menus().main(player); }
        else multiplayer.leave(player);
    }
    public void rematch(Player player, GameSession session) {
        if (solo(session)) singleplayer.restart(player, session); else multiplayer.rematch(player, session);
    }
    public void rematch(Player player, GameSession session, Double stake) {
        if (solo(session)) singleplayer.restart(player, session); else multiplayer.rematch(player, session, stake);
    }
    public void join(Player player, GameSession session) { multiplayer.join(player, session); }
    public void joinReserved(Player player, GameSession session) { multiplayer.joinReserved(player, session); }
    public void ready(Player player, GameSession session) { multiplayer.ready(player, session); }
    public void ready(Player player, GameSession session, Double stake) { multiplayer.ready(player, session, stake); }
    public void startWithBots(Player player, GameSession session) { multiplayer.startWithBots(player, session); }
    public void setStake(Player player, GameSession session, long revision, double amount) { multiplayer.setStake(player, session, revision, amount); }
    public long turnWaitMillis(GameSession session) { return multiplayer.turnWaitMillis(session); }
    public void action(Player player, GameSession session, long revision, JsonElement action) { runtime.action(player, session, revision, action); }
    public boolean enterArena(Player player, GameSession session) { return runtime.enterArena(player, session); }
    public void resume(Player player, GameSession session) { runtime.resume(player, session); }
    public void announce(GameSession session, Component message) { runtime.announce(session, message); }
    public void showRoomToHumans(GameSession session) { runtime.showRoomToHumans(session); }


    public void createReservedForMode(Player p, String kind, int capacity, Map<String, String> options) {
        if (!plugin.allowed(p))
            throw new RuleViolation("error.board-game-permission-required", "Board game permission required");
        if (!plugin.sessions().reserve(p.getUniqueId(), kind))
            throw new RuleViolation("error.leave-your-current-game-first", "Leave your current game first");
        try {
            createReserved(p, kind, capacity, options);
        } catch (RuntimeException | Error ex) {
            try {
                GameSession partial = session(p);
                if (partial != null) remove(partial);
                save();
            } finally {
                plugin.sessions().release(p.getUniqueId(), kind);
            }
            throw ex;
        }
    }

    public void abort(GameSession r, String reason) {
        abort(r, reason, Language.component(reason));
    }

    public void abort(GameSession r, String reason, Component message) {
        r.abort(reason);
        announce(r, message);
        remove(r);
        save();
    }

    public void pause(GameSession r, String reason) {
        r.pause(reason);
        announce(r, Language.component(reason));
        save();
    }

    public void onMain(Runnable action) {
        if (!stopping && plugin.isEnabled()) plugin.scheduler().later(action, 1);
    }

    public void tick() {
        if (!loaded) return;
        if (plugin.tablePlayers() != null) plugin.tablePlayers().syncPlayerState();
        long now = System.currentTimeMillis(); pulse++;
        for (GameSession session : List.copyOf(activeSessions().values())) {
            if (plugin.scheduler().folia()) {
                plugin.tableWorld().atTable(session, () -> tick(session, now));
            } else tick(session, now);
        }
        if (pulse % 30 == 0) save();
        if (plugin.integrations() != null) plugin.integrations().tick();
    }

    private void tick(GameSession session, long now) {
        if (activeSessions().get(session.id) != session || session.phase() == GameSession.Phase.PAUSED) return;
        if (solo(session)) { if (!singleplayer.tick(session, now)) return; }
        else if (!multiplayer.presence(session, now)) return;
        if (session.phase() == GameSession.Phase.LOBBY && now - session.changed() > plugin.getConfig().getLong("idle-room-minutes", 30) * 60_000L) {
            abort(session, "chat.abort.idle"); return;
        }
        if (session.phase() == GameSession.Phase.FINISHED && now - session.changed() > 600_000L) { remove(session); return; }
        if (session.phase() != GameSession.Phase.PLAYING || session.busy()) return;
        if (runtime.automatic(session, now)) return;
        if (!solo(session)) multiplayer.tick(session, now);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        multiplayer.cancelSeating(event.getPlayer().getUniqueId());
        runtime.cancelTransfer(event.getPlayer().getUniqueId());
        plugin.closePlayerViews(event.getPlayer());
        GameSession session = session(event.getPlayer());
        if (session != null && solo(session)) { singleplayer.disconnected(event.getPlayer()); return; }
        if (session != null) session.markOffline(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        save();
    }
    @EventHandler public void world(PlayerChangedWorldEvent event) {
        plugin.closePlayerViews(event.getPlayer());
        GameSession session = session(event.getPlayer());
        if (session == null || runtime.atLoadedTableWorld(event.getPlayer(), session)) return;
        if (solo(session)) singleplayer.disconnected(event.getPlayer());
        else session.markOffline(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void worldUnload(org.bukkit.event.world.WorldUnloadEvent event) { singleplayer.worldUnload(event); save(); }

    @EventHandler
    public void joined(PlayerJoinEvent e) {
        plugin.scheduler().later(
                        e.getPlayer(),
                        () -> {
                            if (session(e.getPlayer()) != null)
                                plugin.tell(e.getPlayer(), Language.component("chat.seat-held"));
                        },
                        60);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        String c = e.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of(
                        "/servermenu",
                        "/servermenu:servermenu",
                        "/menu",
                        "/skin",
                        "/skins",
                        "/skinsrestorer:skin",
                        "/skinsrestorer:skins")
                .contains(c)) plugin.closePlayerViews(e.getPlayer());
    }
}

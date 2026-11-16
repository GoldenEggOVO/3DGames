package GoldenEggOVO.TDGames;

import GoldenEggOVO.TDGames.Core.player.GamePermissions;
import GoldenEggOVO.TDGames.Core.render.ResourcePackState;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController;
import GoldenEggOVO.TDGames.Shared.interaction.TableAccessController;
import GoldenEggOVO.TDGames.Core.menu.GameMenuWindow;
import GoldenEggOVO.TDGames.Core.command.GameCommand;
import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Singleplayer.SingleplayerService;
import GoldenEggOVO.TDGames.Multiplayer.MultiplayerService;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Core.pack.CraftEngineRegistration;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.platform.PlatformEvents;
import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.Core.render.PlayerViews;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;

public final class TDGamesPlugin extends JavaPlugin {
    private GameMenus menus;
    private TableWorldController tableWorld;
    private TablePlayerController tablePlayers;
    private TableAccessController tableAccess;
    private ResourcePackService pack;
    private PlayerSessions playerSessions;
    private OptionalIntegrations integrations;
    private PlatformScheduler scheduler;
    private PlayerViews playerViews;
    private final GameService games = new GameService(this);

    public GameMenus menus() { return menus; }
    public TableWorldController tableWorld() { return tableWorld; }
    public TablePlayerController tablePlayers() { return tablePlayers; }
    public TableAccessController tableAccess() { return tableAccess; }
    public ResourcePackService pack() { return pack; }
    public OptionalIntegrations integrations() { return integrations; }
    public GameService games() { return games; }
    public SingleplayerService singleplayer() { return games.singleplayer(); }
    public MultiplayerService multiplayer() { return games.multiplayer(); }

    public PlayerSessions sessions() { return playerSessions; }
    public ResourcePackState resourcePackState() { return pack; }
    public synchronized PlatformScheduler scheduler() {
        if (scheduler == null) scheduler = new PlatformScheduler(this);
        return scheduler;
    }
    public PlayerViews playerViews() { return playerViews; }

    @Override
    public void onLoad() {
        var engine = getServer().getPluginManager().getPlugin("CraftEngine");
        if (engine == null) return;
        try {
            int added = CraftEngineRegistration.install(engine.getDataFolder().toPath());
            if (added > 0) getLogger().info("Updated " + added + " CraftEngine resource registration files.");
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Cannot install CraftEngine resource registrations: " + exception.getMessage());
        }
    }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            Language.load(this);
            integrations = new OptionalIntegrations(this);
            pack = new ResourcePackService(this);
            PlatformEvents.register(this, pack);
            pack.start();
            playerViews = new PlayerViews(this);
            playerViews.start();
            menus = new GameMenus(this, new GameMenuWindow(this));
            tableWorld = new TableWorldController(this);
            playerSessions = new PlayerSessions();
            tablePlayers = new TablePlayerController(this);
            tableAccess = new TableAccessController(this);
            tableAccess.start();
            var command = new GameCommand(this);
            Objects.requireNonNull(getCommand("3dgames")).setExecutor(command);
            getCommand("3dgames").setTabCompleter(command);
            PlatformEvents.register(this, command);
            PlatformEvents.register(this, games);
            scheduler().later(
                            () -> {
                                try {
                                    games.initialize();
                                } catch (Exception ex) {
                                    getLogger()
                                            .log(
                                                    java.util.logging.Level.SEVERE,
                                                    "Could not restore the board world or rooms;"
                                                        + " original records retained",
                                                    ex);
                                    Bukkit.getPluginManager().disablePlugin(this);
                                }
                            }, 1);
            scheduler().repeat(games::tick, 20, 20);
            getLogger().info("3DGames enabled.");
        } catch (Exception | LinkageError ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not initialize table rooms", ex);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public synchronized void onDisable() {
        cleanup("rooms", games::close);
        if (integrations != null) cleanup("integrations", integrations::close);
        if (tableAccess != null) cleanup("lobby", tableAccess::close);
        if (playerSessions != null) cleanup("sessions", playerSessions::close);
        if (tablePlayers != null) cleanup("view", tablePlayers::close);
        if (menus != null) cleanup("menus", menus::close);
        if (tableWorld != null) cleanup("tables", tableWorld::close);
        if (playerViews != null) cleanup("player snapshots", playerViews::close);
        if (pack != null) cleanup("resource pack", pack::close);
    }

    private void cleanup(String module, Runnable action) {
        try { action.run(); }
        catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not fully close " + module, exception);
        }
    }

    public void closePlayerViews(Player player) {
        if (scheduler().folia() && !scheduler().isOwned(player)) {
            scheduler().execute(player, () -> closePlayerViews(player));
            return;
        }
        if (tablePlayers != null) tablePlayers.releaseFocus(player);
        if (menus != null) menus.forget(player);
        if (tableWorld != null) tableWorld.clearSelection(player);
    }

    public boolean serverMenuAvailable() {
        return Bukkit.getPluginCommand("servermenu:servermenu") != null;
    }

    public boolean allowed(Player player) {
        if (scheduler().folia() && !scheduler().isOwned(player) && playerViews != null) {
            return playerViews.snapshot(player.getUniqueId()).map(PlayerViews.Snapshot::allowed).orElse(false);
        }
        return player.isOnline() && GamePermissions.use(player);
    }

    public void tell(Player player, Component message) {
        scheduler().execute(player, () -> PlatformMessages.send(player, Language.component("chat.prefix", "message", message)));
    }
}

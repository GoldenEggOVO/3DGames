package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Core.render.PlayerViews;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaSoloEntryCommitTest {
    @TempDir Path directory;
    private final class Fixture implements AutoCloseable {
        final TDGamesPlugin plugin = mock(TDGamesPlugin.class);
        final GameService games = new GameService(plugin);
        final PlatformScheduler scheduler = mock(PlatformScheduler.class);
        final TableWorldController tables = mock(TableWorldController.class);
        final Player player = mock(Player.class);
        final List<Runnable> tablesReady = new ArrayList<>(), timeouts = new ArrayList<>();
        final List<Consumer<Boolean>> teleports = new ArrayList<>();
        final SoloProgressStore store = new SoloProgressStore(directory.resolve("solo"));
        final byte[] oldBytes;
        final Path file;
        final org.mockito.MockedStatic<Bukkit> bukkit;
        Fixture() throws Exception {
            when(plugin.games()).thenReturn(games);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(plugin.playerViews()).thenReturn(mock(PlayerViews.class));
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            when(plugin.tableWorld()).thenReturn(tables);
            when(plugin.menus()).thenReturn(mock(GameMenus.class));
            when(plugin.sessions()).thenReturn(new PlayerSessions());
            when(plugin.isEnabled()).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn("Player"); when(player.isOnline()).thenReturn(true);
            when(plugin.allowed(player)).thenReturn(true);
            when(scheduler.folia()).thenReturn(true); when(scheduler.isOwned(player)).thenReturn(true);
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Entity.class), any(Runnable.class));
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Location.class), any(Runnable.class));
            doAnswer(call -> { timeouts.add(call.getArgument(0)); return null; })
                    .when(scheduler).later(any(Runnable.class), anyLong());
            doAnswer(call -> { teleports.add(call.getArgument(2)); return null; })
                    .when(scheduler).teleport(eq(player), any(Location.class), any());
            doAnswer(call -> { tablesReady.add(call.getArgument(1)); return null; })
                    .when(tables).atTable(any(), any(Runnable.class), any(Runnable.class));
            var world = mock(World.class); var block = mock(org.bukkit.block.Block.class);
            when(block.isPassable()).thenReturn(true); when(world.getBlockAt(any(Location.class))).thenReturn(block);
            var at = new Location(world, 0, 80, 0); when(player.getLocation()).thenReturn(at);
            when(tables.seatLocation(any(), anyInt())).thenReturn(at);
            when(tables.atTableWorld(eq(player), any())).thenReturn(true);
            SessionTestSupport.set(games, "loaded", true);
            store.write(player.getUniqueId(), new SoloProgressStore.Progress("2048", 101, Map.of(), new JsonArray()));
            file = directory.resolve("solo").resolve(player.getUniqueId() + ".2048.json");
            oldBytes = Files.readAllBytes(file);
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        }
        GameSession create() { games.create(player, "2048", 1, Map.of(), true); return games.session(player); }
        void complete(boolean success) { tablesReady.getFirst().run(); teleports.getFirst().accept(success); }
        void unchanged() throws Exception { assertArrayEquals(oldBytes, Files.readAllBytes(file)); }
        @Override public void close() { bukkit.close(); }
    }

    @Test void newRoomCannotOverwriteOldProgressWhileEntryIsPending() throws Exception {
        try (var f = new Fixture()) { f.create(); f.unchanged(); }
    }
    @Test void rejectedEntryPreservesOldProgressAndReleasesTheRoom() throws Exception {
        try (var f = new Fixture()) {
            f.create(); f.complete(false); f.unchanged();
            assertNull(f.games.session(f.player));
            assertTrue(f.games.activeSessions().isEmpty());
        }
    }
    @Test void successfulEntryCommitsTheNewProgress() throws Exception {
        try (var f = new Fixture()) {
            var room = f.create(); f.unchanged(); f.complete(true);
            assertEquals(room.seed(), f.store.read(f.player.getUniqueId(), "2048").seed());
        }
    }
    @Test void timeoutPreservesOldProgress() throws Exception {
        try (var f = new Fixture()) { f.create(); f.timeouts.getFirst().run(); f.unchanged(); }
    }
    @Test void disconnectPreservesOldProgressAfterThePlayerTransferTokenIsCancelled() throws Exception {
        try (var f = new Fixture()) {
            f.create(); f.games.quit(new PlayerQuitEvent(f.player, Component.empty())); f.unchanged();
            assertNull(f.games.session(f.player));
        }
    }
    @Test void shutdownCannotCommitAnUnseatedGame() throws Exception {
        try (var f = new Fixture()) { f.create(); f.games.close(); f.unchanged(); }
    }
    @Test void pendingEntryCannotApplyMovesOrAdvanceAutomaticTurns() throws Exception {
        try (var f = new Fixture()) {
            var room = f.create();
            var cells = room.board().cells();
            f.games.applyTableAction(room, 0, new JsonPrimitive(room.board().legalActions(0).getFirst()), f.player);
            assertEquals(0, room.history.size()); assertEquals(cells, room.board().cells());
            assertFalse(f.games.singleplayer().tick(room, System.currentTimeMillis()));
            f.unchanged();
        }
    }
    @Test void immediatelyRejectedPreparationDoesNotStartAnOrphanedRoom() throws Exception {
        try (var f = new Fixture()) {
            doAnswer(call -> { call.<Runnable>getArgument(2).run(); return null; })
                    .when(f.tables).atTable(any(), any(Runnable.class), any(Runnable.class));
            assertThrows(RuleViolation.class, f::create);
            assertTrue(f.games.activeSessions().isEmpty()); f.unchanged();
            verify(f.plugin.menus(), never()).room(eq(f.player), any());
        }
    }
}

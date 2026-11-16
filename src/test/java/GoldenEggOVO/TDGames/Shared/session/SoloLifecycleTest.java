package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoloLifecycleTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    private TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        SessionTestSupport.platform(plugin);
        var service = new GameService(plugin);
        when(plugin.games()).thenReturn(service);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.tableWorld()).thenReturn(mock(TableWorldController.class));
        when(plugin.sessions()).thenReturn(mock(PlayerSessions.class));
        when(plugin.menus()).thenReturn(mock(GameMenus.class));
        SessionTestSupport.set(service, "loaded", true);
        return plugin;
    }

    private GameSession playing(TDGamesPlugin plugin, Player player) {
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Player");
        var room = new GameSession(UUID.randomUUID(), "2048", 1, 42, 5);
        room.join(id, "Player");
        room.board = GameFactory.create("2048", 1, room.seed);
        room.phase = GameSession.Phase.PLAYING;
        String action = room.board.legalActions(0).getFirst();
        room.board.apply(0, action);
        room.event(0, new JsonPrimitive(action));
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        return room;
    }

    @Test void savedSoloLeavesNoRoomOrOccupancyAndResumesAtANewTable() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        GameSession before = playing(plugin, player);
        var cells = before.board.cells();
        var info = before.board.publicInfo();
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().saveAndLeave(player);
            assertTrue(plugin.games().activeSessions().isEmpty());
            verify(plugin.sessions()).release(player.getUniqueId(), "2048");
            assertFalse(Files.exists(directory.resolve("rooms.json")));
            assertTrue(plugin.games().hasSoloProgress(player.getUniqueId()));
            when(plugin.allowed(player)).thenReturn(true);
            when(plugin.sessions().reserve(player.getUniqueId(), "2048")).thenReturn(true);
            when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
            when(plugin.tableWorld().seatLocation(any(), eq(0))).thenReturn(new Location(mock(World.class), 0, 80, 2));
            when(player.teleport(any(Location.class))).thenReturn(true);
            plugin.games().resumeSolo(player);
            GameSession resumed = plugin.games().session(player);
            assertNotNull(resumed);
            assertEquals(0, resumed.table);
            assertNotEquals(before.id, resumed.id);
            assertEquals(cells, resumed.board.cells());
            assertEquals(info, resumed.board.publicInfo());
            assertEquals(GameSession.Phase.PLAYING, resumed.phase);
        }
    }

    @Test void differentSavedGamesCanEachBeResumedWithoutOverwritingTheOther() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Player");
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.sessions().reserve(eq(id), anyString())).thenReturn(true);
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.tableWorld().seatLocation(any(), eq(0))).thenReturn(new Location(mock(World.class), 0, 80, 2));
        when(player.teleport(any(Location.class))).thenReturn(true);
        var store = new GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore(directory.resolve("solo"));
        store.write(id, new GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore.Progress("2048", 42, Map.of(), new com.google.gson.JsonArray()));
        store.write(id, new GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore.Progress("klondike", 7, Map.of(), new com.google.gson.JsonArray()));
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().resumeSolo(player, "2048");
            assertEquals("2048", plugin.games().session(player).kind);
            plugin.games().saveAndLeave(player);
            plugin.games().resumeSolo(player, "klondike");
            assertEquals("klondike", plugin.games().session(player).kind);
            assertTrue(store.exists(id, "2048"));
            assertTrue(store.exists(id, "klondike"));
        }
    }

    @Test void failedSaveKeepsTheActiveRoomAndItsOccupancy() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        GameSession room = playing(plugin, player);
        Files.writeString(directory.resolve("solo"), "not a directory");
        assertThrows(RuleViolation.class, () -> plugin.games().saveAndLeave(player));
        assertSame(room, plugin.games().session(player));
        verify(plugin.tableWorld(), never()).remove(any());
        verify(plugin.sessions(), never()).release(any(), any());
    }

    @Test void finishedProgressIsRemovedAndANewGameRequiresExplicitReplacement() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        GameSession room = playing(plugin, player);
        plugin.games().save();
        assertTrue(plugin.games().hasSoloProgress(player.getUniqueId()));
        assertThrows(RuleViolation.class, () -> plugin.games().create(player, "2048", 1));
        room.finish("winner:0");
        plugin.games().save();
        assertFalse(plugin.games().hasSoloProgress(player.getUniqueId()));
    }
}

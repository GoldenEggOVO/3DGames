package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import GoldenEggOVO.TDGames.Singleplayer.progress.SoloProgressStore;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetedPersistenceTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"2048", "klondike", "mastermind", "memory"})
    void oneSoloActionWritesOnlyThatPlayersProgress(String kind) throws Exception {
        var plugin = plugin();
        var rooms = sessions(plugin, kind);
        GameSession changed = rooms.getFirst();
        String action = changed.board.legalActions(0).getFirst();

        plugin.games().apply(changed, 0, new JsonPrimitive(action), null);

        var progress = new SoloProgressStore(directory.resolve("solo")).read(changed.seats.getFirst().id());
        assertEquals(1, progress.history().size());
        assertEquals(action, progress.history().get(0).getAsJsonObject().get("action").getAsString());
        assertEquals(1, files());
        assertFalse(Files.exists(directory.resolve("rooms.json")));
    }

    @Test
    void multiplayerActionDoesNotRewriteUnchangedSoloProgress() throws Exception {
        var plugin = plugin();
        GameSession changed = sessions(plugin, "2048").getLast();

        plugin.games().apply(changed, 0, new JsonPrimitive("drop:0"), null);

        assertFalse(Files.exists(directory.resolve("solo")));
        assertEquals(1, files());
        var stored = RoomStore.read(directory.resolve("rooms.json")).getFirst();
        assertEquals(changed.id, stored.id);
        assertEquals(1, stored.history.size());
    }

    @Test
    void savingAndLeavingSoloDoesNotWriteUnchangedSessions() throws Exception {
        var plugin = plugin();
        GameSession changed = sessions(plugin, "2048").getFirst();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(changed.seats.getFirst().id());
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().saveAndLeave(player);
        }
        assertEquals(1, files());
        assertFalse(Files.exists(directory.resolve("rooms.json")));
        assertFalse(plugin.games().activeSessions().containsKey(changed.id));
        assertEquals(3, plugin.games().activeSessions().size());
        verify(plugin.sessions()).release(player.getUniqueId(), "2048");
    }

    @Test
    void failedTargetedSoloWriteKeepsTheGameAndPeriodicSaveStillWritesMultiplayer() throws Exception {
        var plugin = plugin();
        var rooms = sessions(plugin, "2048");
        GameSession changed = rooms.getFirst();
        Files.writeString(directory.resolve("solo"), "not a directory");
        assertDoesNotThrow(() -> plugin.games().apply(changed, 0,
                new JsonPrimitive(changed.board.legalActions(0).getFirst()), null));
        assertEquals(1, changed.history.size());
        assertSame(changed, plugin.games().activeSessions().get(changed.id));
        verify(plugin.tableWorld(), never()).remove(changed);
        assertFalse(Files.exists(directory.resolve("rooms.json")));

        plugin.games().save();

        assertTrue(Files.exists(directory.resolve("rooms.json")));
        assertEquals(rooms.getLast().id, RoomStore.read(directory.resolve("rooms.json")).getFirst().id);
    }

    private TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.tableWorld()).thenReturn(mock(TableWorldController.class));
        when(plugin.sessions()).thenReturn(mock(PlayerSessions.class));
        when(plugin.menus()).thenReturn(mock(GameMenus.class));
        var games = new GameService(plugin);
        when(plugin.games()).thenReturn(games);
        SessionTestSupport.set(games, "loaded", true);
        return plugin;
    }

    private List<GameSession> sessions(TDGamesPlugin plugin, String kind) {
        return List.of(session(plugin, kind, 1, 0), session(plugin, kind, 1, 1),
                session(plugin, kind, 1, 2), session(plugin, "connectfour", 2, 3));
    }

    private GameSession session(TDGamesPlugin plugin, String kind, int capacity, int table) {
        var room = new GameSession(UUID.randomUUID(), kind, capacity, 42, table);
        for (int seat = 0; seat < capacity; seat++) room.join(UUID.randomUUID(), "Player " + seat);
        room.board = room.newBoard();
        room.phase = GameSession.Phase.PLAYING;
        plugin.games().runtime().register(room);
        return room;
    }

    private long files() throws Exception {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}

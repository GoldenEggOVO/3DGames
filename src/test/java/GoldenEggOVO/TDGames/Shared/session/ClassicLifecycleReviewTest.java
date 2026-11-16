package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassicLifecycleReviewTest {
    @TempDir Path directory;

    private TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        SessionTestSupport.platform(plugin);
        var service = new GameService(plugin);
        when(plugin.games()).thenReturn(service);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.tableWorld()).thenReturn(mock(TableWorldController.class));
        when(plugin.sessions()).thenReturn(mock(PlayerSessions.class));
        when(plugin.menus()).thenReturn(mock(GameMenus.class));
        SessionTestSupport.set(service, "loaded", true);
        return plugin;
    }

    private GameSession room(TDGamesPlugin plugin, String kind, int players) {
        var room = new GameSession(UUID.randomUUID(), kind, players, 42, 0);
        for (int seat = 0; seat < players; seat++) room.join(UUID.randomUUID(), "Player" + seat);
        room.board = GameFactory.create(kind, players, room.seed);
        room.phase = GameSession.Phase.PLAYING;
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        return room;
    }

    @Test void oneSoloWriteFailureDoesNotPreventSavingMultiplayerRooms() throws Exception {
        var plugin = plugin();
        room(plugin, "2048", 1);
        GameSession multiplayer = room(plugin, "mancala", 2);
        Files.writeString(directory.resolve("solo"), "blocked solo directory");
        plugin.games().save();
        assertTrue(Files.exists(directory.resolve("rooms.json")), "A solo write failure must not skip the multiplayer store");
        assertEquals(multiplayer.id, RoomStore.read(directory.resolve("rooms.json")).getFirst().id);
    }

    @Test void offlineSoloWriteFailureDoesNotEscapeTickOrLoseItsActiveRoom() throws Exception {
        var plugin = plugin();
        GameSession solo = room(plugin, "2048", 1);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(solo.seats.getFirst().id());
        Files.writeString(directory.resolve("solo"), "blocked solo directory");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(solo.seats.getFirst().id())).thenReturn(player);
            assertDoesNotThrow(() -> plugin.games().tick());
        }
        assertSame(solo, plugin.games().session(player));
        verify(plugin.sessions(), never()).release(any(), any());
    }

    @Test void actionHistoryLimitPreservesUnfinishedSoloProgress() throws Exception {
        var plugin = plugin();
        GameSession solo = room(plugin, "klondike", 1);
        for (int i = 0; i < 4000; i++) {
            String action = solo.board.legalActions(0).contains("draw") ? "draw" : "recycle";
            solo.board.apply(0, action);
            solo.event(0, new JsonPrimitive(action));
        }
        plugin.games().save();
        assertTrue(plugin.games().hasSoloProgress(solo.seats.getFirst().id()));
        String next = solo.board.legalActions(0).contains("draw") ? "draw" : "recycle";
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().apply(solo, 0, new JsonPrimitive(next), null);
        }
        assertFalse(solo.board.finished());
        assertTrue(plugin.games().hasSoloProgress(solo.seats.getFirst().id()), "The history cap must retain the unfinished solo save");
    }

    @Test void memoryAutomaticRevealCannotBeSubmittedByAPlayer() {
        var plugin = plugin();
        GameSession room = room(plugin, "memory", 2);
        var game = (MemoryGame) room.board;
        for (int i = 0; i < 36 && !game.waitingToHide(); i++) {
            String action = game.legalActions(game.currentPlayer()).getFirst();
            game.apply(game.currentPlayer(), action);
        }
        assertTrue(game.waitingToHide());
        var player = mock(Player.class);
        int before = room.history.size();
        plugin.games().apply(room, game.currentPlayer(), new JsonPrimitive("hide"), player);
        assertTrue(game.waitingToHide());
        assertEquals(before, room.history.size());
        plugin.games().apply(room, game.currentPlayer(), new JsonPrimitive("hide"), null);
        assertFalse(game.waitingToHide());
        assertEquals(before + 1, room.history.size());
    }
}

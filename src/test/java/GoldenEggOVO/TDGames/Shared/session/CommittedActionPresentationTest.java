package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommittedActionPresentationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"render", "render-state", "sound", "finish-sound", "announcement"})
    void presentationFailuresStillFinishAndPersistTheCommittedWinningMove(String failure) throws Exception {
        var plugin = mock(TDGamesPlugin.class);
        var games = new GameService(plugin);
        when(plugin.games()).thenReturn(games);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var loaded = GameService.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(games, true);
        var tables = mock(TableWorldController.class);
        when(plugin.tableWorld()).thenReturn(tables);
        var payments = mock(OptionalIntegrations.class);
        when(plugin.integrations()).thenReturn(payments);
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(UUID.randomUUID(), "Host");
        room.join(UUID.randomUUID(), "Guest");
        room.board = room.newBoard();
        room.phase = GameSession.Phase.PLAYING;
        games.runtime().register(room);
        for (String action : List.of("drop:0", "drop:1", "drop:0", "drop:1", "drop:0", "drop:1")) {
            int seat = room.turn();
            room.board.apply(seat, action);
            room.event(seat, new JsonPrimitive(action));
            room.revision++;
        }
        if (failure.equals("render")) doThrow(new IllegalArgumentException("Rendering failed")).when(tables).render(room);
        if (failure.equals("render-state")) doThrow(new IllegalStateException("Rendering failed")).when(tables).render(room);
        if (failure.equals("sound")) doThrow(new IllegalArgumentException("Sound failed")).when(tables).sound(eq(room), any());
        if (failure.equals("finish-sound")) doThrow(new IllegalArgumentException("Sound failed")).when(tables).sound(room, TableSounds.WIN);
        var player = mock(Player.class);
        if (failure.equals("announcement")) doThrow(new IllegalStateException("Message failed")).when(plugin).tell(eq(player), any());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(room.seats.getFirst().id())).thenReturn(player);
            assertDoesNotThrow(() -> games.apply(room, 0, new JsonPrimitive("drop:0"), null));
            games.apply(room, 0, new JsonPrimitive("drop:0"), null);
        }
        assertTrue(room.board.finished());
        assertEquals(GameSession.Phase.FINISHED, room.phase());
        assertEquals("winner:0", room.result());
        assertEquals(7, room.history.size());
        verify(payments).finish(room, "winner:0");
        var stored = RoomStore.read(directory.resolve("rooms.json")).getFirst();
        assertEquals(room.id, stored.id);
        assertEquals(GameSession.Phase.FINISHED, stored.phase());
        assertEquals("winner:0", stored.result());
        assertEquals(7, stored.history.size());
    }
}

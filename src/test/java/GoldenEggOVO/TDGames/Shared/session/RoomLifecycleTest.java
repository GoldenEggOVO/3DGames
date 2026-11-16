package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.player.PlayerSessions;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.game.GameFactory;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomLifecycleTest {
    @Test
    void roomSaveContainsOnlyCurrentRoomRecords(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        GameRulesTest.set(plugin.games(), "loaded", true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(SessionTestSupport.service(plugin)).restore();
        doCallRealMethod().when(SessionTestSupport.service(plugin)).save();
        var file = directory.resolve("rooms.json");
        java.nio.file.Files.writeString(file, "{\"schema\":1,\"rooms\":[]}");

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            plugin.games().restore();
            plugin.games().save();
        }

        var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        assertEquals(Set.of("schema", "rooms"), saved.keySet());
        assertTrue(RoomStore.read(file).isEmpty());
    }

    @Test
    void invalidPlayingHistoryStillRejectsRestoreAndPreservesItsFile(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        doCallRealMethod().when(SessionTestSupport.service(plugin)).restore();
        doCallRealMethod().when(SessionTestSupport.service(plugin)).start(any());
        when(plugin.games().prepareSeats(any())).thenReturn(true);
        GameSession room = new GameSession(UUID.randomUUID(), "gomoku", 2, 1, 0);
        room.fillBots();
        room.phase = GameSession.Phase.PLAYING;
        room.event(0, new JsonPrimitive("invalid-action"));
        var file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(room));
        byte[] original = java.nio.file.Files.readAllBytes(file);

        assertThrows(IllegalStateException.class, plugin.games()::restore);

        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        verify(plugin.tableWorld(), never()).render(any());
        verify(plugin.games(), never()).save();
    }

    @Test
    void unsupportedRecordsFailBeforeInstallingAnyRooms(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        TDGamesPlugin plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);SessionTestSupport.install(plugin);
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        doReturn(directory.toFile()).when(plugin).getDataFolder();
        doReturn(java.util.logging.Logger.getAnonymousLogger()).when(plugin).getLogger();
        UUID old = UUID.randomUUID(), other = UUID.randomUUID();
        String data =
                """
                {"returns":{},"rooms":[
                  {"id":"%s","kind":"color-eight","rulesVersion":1,"history":[{"seat":0,"action":"draw"}],"phase":"FINISHED"},
                  {"id":"%s","kind":"connectfour","capacity":2,"seed":1,"table":0,"phase":"LOBBY","revision":0,"seats":[],"history":[]} ]}
                """
                        .formatted(old, other);
        java.nio.file.Files.writeString(directory.resolve("rooms.json"), data);
        assertThrows(IllegalStateException.class, plugin.games()::restore);
        assertTrue(plugin.games().activeSessions().isEmpty());
        assertEquals(data, java.nio.file.Files.readString(directory.resolve("rooms.json")));
        try (var files = java.nio.file.Files.list(directory)) {
            var backup =
                    files.filter(p -> p.getFileName().toString().startsWith("rooms.unreadable-"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(data, java.nio.file.Files.readString(backup));
        }
    }

    @Test
    void removedRoomsRejectAllPlayerMutationsBeforeAnySideEffect() throws Exception {
        TDGamesPlugin plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);SessionTestSupport.install(plugin);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        doReturn(true).when(plugin).allowed(player);
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 1, 0);
        room.join(id, "Player");
        room.board = GameFactory.create("chess", 2, 1);
        List<Runnable> actions =
                List.of(
                        () -> plugin.games().join(player, room),
                        () -> plugin.games().joinReserved(player, room),
                        () -> plugin.games().resume(player, room),
                        () -> plugin.games().enterArena(player, room),
                        () -> plugin.games().ready(player, room),
                        () -> plugin.games().startWithBots(player, room),
                        () ->
                                plugin.games().action(
                                        player,
                                        room,
                                        room.revision,
                                        new JsonPrimitive("move:e2:e4")),
                        () -> plugin.games().apply(room, 0, new JsonPrimitive("move:e2:e4"), null),
                        () -> plugin.games().rematch(player, room));
        for (Runnable action : actions) assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(room.ready.isEmpty());
        assertTrue(room.history.isEmpty());
        assertEquals(1, room.seats.size());
        verifyNoInteractions(plugin.tableWorld(), plugin.sessions());
    }

    @Test
    void permissionRevocationPreventsReadyBotsAndResume() throws Exception {
        TDGamesPlugin plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);SessionTestSupport.install(plugin);
        Player player = mock(Player.class);
        doReturn(false).when(plugin).allowed(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 1, 0);
        room.join(player.getUniqueId(), "Player");
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        plugin.games().ready(player, room);
        plugin.games().startWithBots(player, room);
        plugin.games().resume(player, room);
        assertTrue(room.ready.isEmpty());
        assertEquals(1, room.seats.size());
        verifyNoInteractions(plugin.tableWorld());
    }
}

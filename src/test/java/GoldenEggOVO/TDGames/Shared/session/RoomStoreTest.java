package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.Multiplayer.bot.BoardBots;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.game.HandGame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomStoreTest {
    @TempDir Path directory;

    @Test
    void roomRecordsDoNotRequireUnusedReturnCoordinates() throws Exception {
        Path file = directory.resolve("rooms.json");
        String data = "{\"schema\":1,\"rooms\":[]}";
        Files.writeString(file, data);
        assertTrue(assertDoesNotThrow(() -> RoomStore.read(file)).isEmpty());
        assertEquals(data, Files.readString(file));
    }

    @Test
    void currentRoomsRoundTripIdentityOptionsSeatsAndReplay() throws Exception {
        List<GameSession> originals = new ArrayList<>();
        for (String kind : GameCatalog.IDS) {
            int capacity = GameCatalog.defaultCapacity(kind);
            GameSession room = new GameSession(UUID.randomUUID(), kind, capacity, 42, originals.size());
            room.join(UUID.randomUUID(), "<red>literal name");
            room.fillBots();
            room.anchorWorld = UUID.randomUUID();
            room.anchorX = 12.5;
            room.anchorY = 70;
            room.anchorZ = -4;
            room.sideTray = true;
            room.board = room.newBoard();
            room.phase = GameSession.Phase.PLAYING;
            for (int index = 0; index < 16 && !room.board.finished(); index++) {
                int seat = room.board.currentPlayer();
                String action = BoardBots.choose(room.board, seat, new Random(index));
                if (action == null) break;
                room.board.apply(seat, action);
                room.event(seat, new com.google.gson.JsonPrimitive(action));
                room.revision++;
            }
            originals.add(room);
        }
        Path file = directory.resolve("rooms.json");
        RoomStore.write(file, originals);
        var restored = RoomStore.read(file);
        assertFalse(com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("returns"));
        for (int index = 0; index < originals.size(); index++) {
            GameSession before = originals.get(index), after = restored.get(index);
            assertEquals(before.id, after.id);
            assertEquals(before.seats, after.seats);
            assertEquals(before.owner, after.owner);
            assertEquals(before.history.snapshot(), after.history.snapshot());
            assertEquals(before.anchorWorld, after.anchorWorld);
            assertEquals(before.anchorX, after.anchorX);
            assertEquals(before.revision, after.revision);
            assertTrue(after.sideTray);
            var replay = after.newBoard();
            for (var element : after.history) {
                var move = element.getAsJsonObject();
                replay.apply(move.get("seat").getAsInt(), move.get("action").getAsString());
            }
            assertEquals(before.board.cells(), replay.cells());
            assertEquals(before.board.publicInfo(), replay.publicInfo());
            if (before.board instanceof GoldenEggOVO.TDGames.Shared.game.HandGame original) {
                var restoredHands = (GoldenEggOVO.TDGames.Shared.game.HandGame) replay;
                for (int seat = 0; seat < before.capacity; seat++)
                    assertEquals(original.hand(seat), restoredHands.hand(seat));
            }
            assertEquals(
                    before.board.legalActions(before.turn()),
                    replay.legalActions(after.turn() < 0 ? before.turn() : after.turn()));
        }
    }

    @Test
    void aStreamingFailureKeepsTheLastGoodFileAndUsesTheIoFailureContract() throws Exception {
        Path file = directory.resolve("rooms.json");
        GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        RoomStore.write(file, List.of(room));
        byte[] saved = Files.readAllBytes(file);
        room.event(0, new com.google.gson.JsonPrimitive("x".repeat(20_000)));
        Writer failing = new Writer() {
            @Override public void write(char[] text, int offset, int length) throws IOException {
                throw new IOException("Simulated full disk");
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Path temporary = file.resolveSibling("rooms.json.tmp");
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
                    .thenReturn(new BufferedWriter(failing));
            assertThrows(IOException.class, () -> RoomStore.write(file, List.of(room)));
        }
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test
    void aFailedTemporaryWriteLeavesTheLastGoodFileAndHistoryIntact() throws Exception {
        Path file = directory.resolve("rooms.json");
        GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        room.event(0, new com.google.gson.JsonPrimitive("move:a2:a4"));
        RoomStore.write(file, List.of(room));
        byte[] saved = Files.readAllBytes(file);
        var history = room.history.snapshot();
        Files.createDirectory(directory.resolve("rooms.json.tmp"));
        assertThrows(java.io.IOException.class,
                () -> RoomStore.write(file, List.of(room)));
        assertArrayEquals(saved, Files.readAllBytes(file));
        assertEquals(history, room.history.snapshot());
        assertEquals(history, RoomStore.read(file).getFirst().history.snapshot());
    }

    @Test
    void futureSchemaFailsBeforeReturningPartialRooms() throws Exception {
        Path file = directory.resolve("rooms.json");
        String data = "{\"schema\":999,\"rooms\":[],\"returns\":{}}";
        Files.writeString(file, data);
        assertThrows(IllegalArgumentException.class, () -> RoomStore.read(file));
        assertEquals(data, Files.readString(file));
    }
}

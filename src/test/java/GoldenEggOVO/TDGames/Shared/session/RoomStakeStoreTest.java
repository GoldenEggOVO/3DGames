package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Multiplayer.room.RoundActions;
import GoldenEggOVO.TDGames.Multiplayer.room.RoomStore;
import GoldenEggOVO.TDGames.SessionTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoomStakeStoreTest {
    @TempDir Path directory;

    GameSession room() {
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(UUID.randomUUID(), "Host");
        room.join(UUID.randomUUID(), "Guest");
        return room;
    }

    @Test
    void stakeAndDepartedIdentitySurviveWithoutChangingReplaySeatIndexes() throws Exception {
        var room = room();
        UUID loser = room.seats.getFirst().id();
        room.stake = 10.0;
        SessionTestSupport.mutableDeparted(room).add(loser);
        room.phase = GameSession.Phase.FINISHED;
        room.result = "winner:1";
        Path file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(room));
        var restored = RoomStore.read(file).getFirst();
        assertEquals(10.0, restored.stake);
        assertEquals(room.seats, restored.seats);
        assertEquals(-1, restored.seat(loser));
        assertEquals(1, restored.seat(room.seats.getLast().id()));
        assertEquals(1, restored.occupied());
        assertThrows(IllegalArgumentException.class, () -> RoundActions.rematchReady(restored, room.seats.getLast().id()));
    }

    @Test
    void legacyMissingStakeAndExplicitFreeRemainDifferent() throws Exception {
        var room = room();
        Path file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(room));
        assertNull(RoomStore.read(file).getFirst().stake);
        room.stake = 0.0;
        RoomStore.write(file, List.of(room));
        assertEquals(0.0, RoomStore.read(file).getFirst().stake);
    }

    @Test
    void invalidSavedStakesAndDepartedIdentitiesAreRejected() throws Exception {
        var room = room();
        Path file = directory.resolve("rooms.json");
        RoomStore.write(file, List.of(room));
        var original = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (String bad : List.of("-1", "\"NaN\"", "\"10\"")) {
            var json = original.deepCopy();
            json.getAsJsonArray("rooms").get(0).getAsJsonObject().add("stake", com.google.gson.JsonParser.parseString(bad));
            Files.writeString(file, json.toString());
            assertThrows(IllegalArgumentException.class, () -> RoomStore.read(file));
        }
        var ids = new com.google.gson.JsonArray();
        ids.add(UUID.randomUUID().toString());
        original.getAsJsonArray("rooms").get(0).getAsJsonObject().add("departed", ids);
        Files.writeString(file, original.toString());
        assertThrows(IllegalArgumentException.class, () -> RoomStore.read(file));
    }
}

package GoldenEggOVO.TDGames.Shared.session;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SoloSeatingTest {
    @Test void singlePlayerNeverMovesToASecondSeatAfterChangingSetupCapacity() {
        for (String game : List.of("mastermind", "memory")) {
            for (String first : List.of("host", "opponent", "random")) {
                var room = new GameSession(UUID.randomUUID(), game, 1, 123, 0, Map.of("first", first));
                UUID player = UUID.randomUUID();
                room.join(player, "Player");
                assertDoesNotThrow(room::prepareSeats, game + " " + first);
                assertEquals(1, room.seats.size());
                assertEquals(0, room.seat(player));
                assertTrue(room.host(player));
            }
        }
    }
}

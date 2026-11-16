package GoldenEggOVO.TDGames.Shared.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RandomFirstPlayerTest {
    @Test void everyPlayerCanStartMultiplayerGamesWithoutChangingHostOrSeatOrder() {
        for (String kind : List.of("dots-and-boxes", "memory", "monopoly")) {
            for (int count : kind.equals("monopoly") ? List.of(3, 4, 5, 6) : List.of(3, 4)) {
                int[] starts = new int[count];
                for (int seed = 0; seed < 4096; seed++) {
                    GameSession room = new GameSession(new UUID(0, seed), kind, count, seed, 0, Map.of("first", "random"));
                    for (int seat = 0; seat < count; seat++) room.join(new UUID(1, seat), "P" + seat);
                    UUID owner = room.owner;
                    room.prepareSeats();
                    starts[(int) room.seats.get(room.newBoard().currentPlayer()).id().getLeastSignificantBits()]++;
                    assertTrue(room.host(owner));
                    for (int i = 1; i < count; i++) {
                        long before = room.seats.get(i - 1).id().getLeastSignificantBits();
                        long after = room.seats.get(i).id().getLeastSignificantBits();
                        assertEquals((before + 1) % count, after, kind);
                    }
                    var firstPreparation = List.copyOf(room.seats);
                    room.prepareSeats();
                    assertEquals(firstPreparation, room.seats, "Preparation must be idempotent");
                }
                for (int startsForPlayer : starts)
                    assertTrue(startsForPlayer > 4096 / count / 2, kind + " " + count + ": " + java.util.Arrays.toString(starts));
            }
        }
    }

    @Test void restorationDoesNotReorderSavedSeatsEvenBeforeHistoryIsReplayed() {
        GameSession room = new GameSession(UUID.randomUUID(), "memory", 3, 0, 0, Map.of("first", "random"));
        for (int seat = 0; seat < 3; seat++) room.join(new UUID(1, seat), "P" + seat);
        var saved = List.copyOf(room.seats);
        room.beginRestore();
        room.prepareSeats();
        assertEquals(saved, room.seats);
    }
}

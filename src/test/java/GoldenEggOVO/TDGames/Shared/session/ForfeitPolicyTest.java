package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Multiplayer.room.ForfeitPolicy;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForfeitPolicyTest {
    @Test void eliminatedSeatsNeverRegainPotEligibilityWhenAnotherPlayerLeaves() {
        var room = room("monopoly", 4, -1);
        var board = mock(GoldenEggOVO.TDGames.Shared.game.ClassicGame.class);
        room.board = board;
        when(board.eliminated(1)).thenReturn(true);
        assertEquals("winners:2,3", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id())));
        assertEquals("draw", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id(), room.seats.get(2).id(), room.seats.get(3).id())));
    }
    GameSession room(String game, int count, int landlord) {
        var room = new GameSession(UUID.randomUUID(), game, count, 42, 0);
        for (int i = 0; i < count; i++) room.join(UUID.randomUUID(), "Player" + i);
        room.board = mock(BoardGame.class);
        when(room.board.publicInfo()).thenReturn(Map.of("landlord", Integer.toString(landlord)));
        return room;
    }

    @Test
    void opponentsWinAndAllPlayersMissingReturnsADraw() {
        var room = room("yacht", 3, -1);
        assertEquals("winners:1,2", ForfeitPolicy.outcome(room, Set.of(room.seats.getFirst().id())));
        assertEquals("winner:2", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id(), room.seats.get(1).id())));
        assertEquals("draw", ForfeitPolicy.outcome(room, new HashSet<>(room.seats.stream().map(GameSession.Seat::id).toList())));
    }

    @Test
    void doudizhuUsesDeterminedTeamsAndOnlyOpponentsWin() {
        var room = room("doudizhu", 3, 1);
        assertEquals("winners:0,2", ForfeitPolicy.outcome(room, Set.of(room.seats.get(1).id())));
        assertEquals("winner:1", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id())));
        assertEquals("draw", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id(), room.seats.get(1).id())));
        when(room.board.publicInfo()).thenReturn(Map.of("landlord", "-1"));
        assertEquals("winners:1,2", ForfeitPolicy.outcome(room, Set.of(room.seats.get(0).id())));
    }
}

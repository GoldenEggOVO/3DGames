package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.HandGame;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.coloreight.ColorEightGame;
import GoldenEggOVO.TDGames.Shared.text.HandText;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandMenuTest {
    @Test
    void wildColorAndDirectionArePublicWithoutExposingAnyHand() {
        HandGame game = mock(HandGame.class);
        when(game.publicInfo())
                .thenReturn(
                        Map.of("color", "b", "direction", "Counterclockwise", "drawPenalty", "6"));
        when(game.cells()).thenReturn(List.of(new Cell("discard", 0, 0, "wild", -1)));
        String text = MessageText.plain(HandText.status("color-eight", game));
        assertTrue(text.contains("Blue"));
        assertTrue(text.contains("Counterclockwise"));
        assertFalse(text.contains("penalty"));
        assertTrue(text.contains("Wild"));
        assertTrue(MessageText.plain(HandText.tableHint("color-eight", game)).contains("Blue"));
        verify(game, never()).hand(anyInt());
    }

    @Test
    void spectatorMenuOmitsPublicTableDetailsAndNeverReadsConcealedHands() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        GameSession room = new GameSession(UUID.randomUUID(), "color-eight", 2, 0, 0);
        room.join(UUID.randomUUID(), "Owner");
        room.fillBots();
        SessionTestSupport.set(room, "board", spy(new ColorEightGame(2, 0)));
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(fixture.plugin).put(room.id, room);
        fixture.menus.observe(fixture.player, room);
        verify((HandGame) room.board(), never()).hand(anyInt());
        assertTrue(
                fixture.buttons.stream()
                        .noneMatch(
                                b -> Set.of("public-table", "rules", "details").contains(b.id())));
        assertTrue(MessageText.plain(fixture.description).contains("Owner"));
    }

}

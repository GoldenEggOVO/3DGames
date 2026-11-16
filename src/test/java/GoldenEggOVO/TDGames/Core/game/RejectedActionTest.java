package GoldenEggOVO.TDGames.Core.game;

import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.game.HandGame;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RejectedActionTest {
    static List<String> games() {
        return GameCatalog.IDS;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("games")
    void malformedActionsLeaveTheSameFutureGame(String kind) {
        int players = GameCatalog.defaultCapacity(kind);
        BoardGame game = GameFactory.create(kind, players, 9123);
        BoardGame reference = GameFactory.create(kind, players, 9123);
        for (int step = 0; step < 20 && !game.finished(); step++) {
            if (game instanceof ClassicGame classic && classic.automaticAction() != null) {
                String automatic = classic.automaticAction();
                game.apply(game.currentPlayer(), automatic);
                reference.apply(reference.currentPlayer(), automatic);
                assertEquals(reference.cells(), game.cells());
                assertEquals(reference.publicInfo(), game.publicInfo());
            }
            int seat = game.currentPlayer();
            List<Cell> cells = game.cells();
            Map<String, String> info = game.publicInfo();
            List<String> actions = game.legalActions(seat);
            assertFalse(actions.isEmpty(), kind + " " + step);
            assertThrows(IllegalArgumentException.class, () -> game.apply(seat, null));
            assertThrows(IllegalArgumentException.class, () -> game.apply(seat, "invalid:action"));
            assertThrows(IllegalArgumentException.class, () -> game.apply(-1, actions.getFirst()));
            assertThrows(IllegalArgumentException.class, () -> game.apply(players, actions.getFirst()));
            assertEquals(cells, game.cells());
            assertEquals(info, game.publicInfo());
            assertEquals(actions, game.legalActions(seat));
            String action = actions.get(step % actions.size());
            game.apply(seat, action);
            reference.apply(seat, action);
            assertEquals(reference.cells(), game.cells());
            assertEquals(reference.publicInfo(), game.publicInfo());
            assertEquals(reference.outcome(), game.outcome());
            if (game instanceof HandGame hand && reference instanceof HandGame expected) {
                for (int owner = 0; owner < players; owner++) {
                    assertEquals(expected.hand(owner), hand.hand(owner));
                    assertEquals(expected.discards(owner), hand.discards(owner));
                    assertEquals(expected.exposed(owner), hand.exposed(owner));
                }
            }
        }
    }
}

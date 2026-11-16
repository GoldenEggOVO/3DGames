package GoldenEggOVO.TDGames.Core.game;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ClassicRuleBoundaryAuditTest {
    private record Snapshot(int turn, boolean finished, String outcome, List<Cell> cells,
                            List<List<Cell>> views, Map<String, String> info,
                            List<Map<String, String>> privateInfo) { }

    private Snapshot snapshot(ClassicGame game) {
        List<List<Cell>> views = new ArrayList<>();
        List<Map<String, String>> info = new ArrayList<>();
        for (int seat = -1; seat <= game.playerCount(); seat++) {
            views.add(game.cellsFor(seat));
            info.add(game.infoFor(seat));
        }
        return new Snapshot(game.currentPlayer(), game.finished(), game.outcome(), game.cells(),
                List.copyOf(views), game.publicInfo(), List.copyOf(info));
    }

    @Test void everyClassicGamePreservesQueriesAndRejectedActorsAcrossReplay() {
        for (String id : GameCatalog.CLASSIC_IDS) {
            int players = GameCatalog.defaultCapacity(id);
            var game = (ClassicGame) GameFactory.create(id, players, 27);
            var replay = (ClassicGame) GameFactory.create(id, players, 27);
            Random random = new Random(31);
            for (int step = 0; step < 24 && !game.finished(); step++) {
                Snapshot before = snapshot(game);
                int actor = game.currentPlayer();
                List<String> legal = game.legalActions(actor);
                game.controlActions(actor);
                game.actionsForCell(actor, null);
                game.actionsForCell(actor, "invalid-cell");
                game.botAction(actor, new Random(step));
                assertEquals(before, snapshot(game), id + " query changed game state");
                for (int invalidSeat : new int[]{-1, players}) {
                    assertTrue(game.legalActions(invalidSeat).isEmpty(), id);
                    assertThrows(IllegalArgumentException.class, () -> game.apply(invalidSeat, "roll"), id);
                    assertEquals(before, snapshot(game), id + " rejected actor changed game state");
                }
                assertThrows(IllegalArgumentException.class, () -> game.apply(actor, "invalid-action"), id);
                assertEquals(before, snapshot(game), id + " rejected action changed game state");
                String action = game.automaticAction();
                if (action == null) action = game.botAction(actor, random);
                if (action == null && !legal.isEmpty()) action = legal.getFirst();
                assertNotNull(action, id + " has no action before finishing");
                game.apply(actor, action);
                replay.apply(actor, action);
                assertEquals(snapshot(replay), snapshot(game), id + " replay diverged");
            }
        }
    }
}

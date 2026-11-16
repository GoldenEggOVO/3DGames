package GoldenEggOVO.TDGames.Multiplayer.game.go;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GoBoardEdgesTest {
    @Test void cornerCaptureAndSuicideWorkOnEveryBoardSize() {
        for (int size : new int[] {9, 13, 19}) {
            var game = new GoGame(size);
            game.apply(0, "place:0,0");
            game.apply(1, "place:1,0");
            game.apply(0, "place:" + (size - 1) + "," + (size - 1));
            game.apply(1, "place:0,1");

            assertEquals(-1, game.cells().getFirst().owner(), "corner captured on " + size);
            assertFalse(game.legalActions(0).contains("place:0,0"), "suicide on " + size);
        }
    }
}

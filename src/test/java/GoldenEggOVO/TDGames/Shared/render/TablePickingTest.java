package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TablePickingTest {
    @Test void pointerPositionsKeepTheirNearestCellOnRegularAndSparseBoards() {
        var random = new Random(513);
        for (String kind : List.of("chess", "draughts", "reversi", "go", "go9", "go13", "gomoku", "xiangqi", "checkers", "ludo")) {
            var cells = GameFactory.create(kind, 2, 0).cells();
            var geometry = new TableGeometry(kind, cells);
            boolean squares = List.of("chess", "draughts", "reversi").contains(kind);
            for (Cell cell : cells) {
                assertEquals(cell.id(), geometry.hit(geometry.x(cell), geometry.z(cell)), kind);
            }
            for (int sample = 0; sample < 1000; sample++) {
                double x = random.nextDouble(-1.04, 1.04), z = random.nextDouble(-1.04, 1.04);
                String expected = null;
                double nearest = Double.POSITIVE_INFINITY;
                for (Cell cell : cells) {
                    double dx = geometry.x(cell) - x, dz = geometry.z(cell) - z;
                    double distance = squares ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.hypot(dx, dz);
                    if (distance < nearest && distance <= geometry.spacing * (squares ? .499 : .46)) {
                        expected = cell.id();
                        nearest = distance;
                    }
                }
                assertEquals(expected, geometry.hit(x, z), kind);
            }
            assertNull(geometry.hit(Double.POSITIVE_INFINITY, 0));
            assertNull(geometry.hit(Double.MAX_VALUE, Double.MAX_VALUE));
            assertEquals("@menu", geometry.hit(0, 1.27));
        }
    }
}

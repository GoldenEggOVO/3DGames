package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassicLayoutTest {
    @Test void shogiStartingPiecesFaceTheirOwnSeatAndHeldPieceRow() {
        for (boolean mini : new boolean[] {false, true}) {
            var game = new GoldenEggOVO.TDGames.Multiplayer.game.shogi.ShogiGame(2, 1, Map.of(), mini);
            var layout = ClassicLayout.of(game.id(), game.cells(), List.of());
            for (Cell cell : game.cells()) if (!cell.piece().isEmpty())
                assertTrue(layout.slot(cell.id()).z() * (cell.owner() == 0 ? 1 : -1) > 0,
                        game.id() + " starts a piece at the opposing seat: " + cell);
        }
    }

    @Test void longBoardsGiveMancalaWellsAndBattleGridsTheirOwnProportions() {
        var pits = new ArrayList<Cell>();
        for (int i = 0; i < 14; i++) pits.add(new Cell("" + i,
                i == 6 ? 6 : i == 13 ? -1 : i < 6 ? i : 12 - i, i < 7 ? 1 : 0, "4", i < 7 ? 0 : 1));
        var mancala = ClassicLayout.of("mancala", pits, List.of());
        assertTrue(mancala.slot("0").width() >= .28, "Seed wells must not be squeezed into a square board");
        assertTrue(mancala.slot("6").x() > 1.1);
        assertTrue(mancala.tableHalf() >= 1.6);
        var sea = new ArrayList<Cell>();
        for (int board = 0; board < 2; board++) for (int i = 0; i < 100; i++)
            sea.add(new Cell("b" + board + "_" + i, board * 11 + i % 10, i / 10, "water", board));
        var battleship = ClassicLayout.of("battleship", sea, List.of("auto"));
        assertTrue(battleship.slot("b0_1").x() - battleship.slot("b0_0").x() >= .13,
                "Two readable square sea charts must fill the long table");
        assertEquals(battleship.slot("b0_0").width(), battleship.slot("b0_0").depth(), 1e-6);
        for (var slot : battleship.slots()) assertEquals(slot.id(), battleship.hit(slot.x(), slot.z()));
    }

    @Test void allGridCentersAndEdgeCellsUseTheirRenderedHitBounds() {
        for (String kind : List.of("2048", "memory", "hex", "shogi", "mini-shogi", "monopoly")) {
            int size = kind.equals("battleship") ? 21 : kind.equals("shogi") ? 9 : 6;
            List<Cell> cells = new ArrayList<>();
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) cells.add(new Cell(x + "," + y, x, y, "", -1));
            var layout = ClassicLayout.of(kind, cells, List.of());
            for (var slot : layout.slots()) {
                assertEquals(slot.id(), layout.hit(slot.x(), slot.z()));
                assertTrue(Math.abs(slot.x()) + slot.width()/2 <= layout.tableHalf() + .00001);
                assertTrue(Math.abs(slot.z()) + slot.depth()/2 <= layout.tableHalf() + .00001);
            }
            assertNull(layout.hit(Double.NaN, 0));
            assertNull(layout.hit(0, Double.POSITIVE_INFINITY));
            assertNull(layout.hit(5, 5));
        }
    }

    @Test void klondikeCoveredCardsHaveDistinctSelectableStripsAndFullTopFace() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell("t0", 0, 2, "", -1));
        for (int i = 0; i < 35; i++) cells.add(new Cell("t0_" + i, 0, i + 3, i < 3 ? "back" : "spades_king", 0));
        var layout = ClassicLayout.of("klondike", cells, List.of("draw"));
        for (int i = 0; i < 35; i++) {
            var slot = layout.slot("t0_" + i);
            assertEquals(slot.id(), layout.hit(slot.x(), slot.z()));
            assertTrue(slot.depth() > .01);
        }
        assertTrue(layout.slot("t0_0").depth() < layout.slot("t0_34").depth());
        assertEquals(.32, layout.slot("t0_34").fullDepth(), .00001);
    }

    @Test void controlsHaveFinitePagesAndStayInsideWoodenEdge() {
        List<String> controls = new ArrayList<>();
        for (int i = 0; i < 31; i++) controls.add("control:" + i);
        var layout = ClassicLayout.of("mastermind", List.of(), controls, 1);
        assertNotNull(layout.slot("@control/10"));
        assertNull(layout.slot("@control/0"));
        assertNotNull(layout.slot("@page/next"));
        assertNotNull(layout.slot("@page/prev"));
        for (var slot : layout.slots()) assertTrue(Math.abs(slot.z()) + slot.depth()/2 <= ClassicLayout.tableHalfDepth("mastermind"));
        assertThrows(UnsupportedOperationException.class, () -> layout.slots().clear());
    }

    @Test void mastermindAttemptsAdvanceAcrossColumnsWithHintsAboveAndSecretOnRight() {
        var cells = List.of(new Cell("g1_0_0", 0, 11, "color_0", 0), new Cell("g1_9_3", 3, 20, "color_3", 0),
                new Cell("draft0", 0, 23, "back", 0), new Cell("secret0", 0, 25, "back", 1));
        var layout = ClassicLayout.of("mastermind", cells, List.of());
        for (var slot : layout.slots()) assertEquals(slot.id(), layout.hit(slot.x(), slot.z()));
        assertTrue(layout.slot("g1_9_3").x() > layout.slot("g1_0_0").x());
        assertTrue(layout.slot("secret0").x() > layout.slot("g1_9_3").x());
        assertEquals(layout.slot("g1_0_0").z(), layout.slot("secret0").z(), 1e-9);
        assertNotNull(layout.slot("@palette/5"));
    }

    @Test void shogiBoardDoesNotOverlapEitherHeldPieceRowAndMancalaStoresFitBoard() {
        for (int size : new int[] {5, 9}) {
            List<Cell> cells = new ArrayList<>();
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) cells.add(new Cell(x + "," + y, x, y, "", -1));
            cells.add(new Cell("h0P", 0, 0, "P_1", 0)); cells.add(new Cell("h1P", 0, 0, "P_1", 1));
            var layout = ClassicLayout.of(size == 9 ? "shogi" : "mini-shogi", cells, List.of());
            for (var slot : layout.slots()) if (!slot.id().startsWith("h"))
                assertTrue(Math.abs(slot.z()) + slot.depth()/2 < .79);
        }
        var layout = ClassicLayout.of("mancala", List.of(new Cell("6", 6, 1, "0", 0), new Cell("13", -1, 0, "0", 1)), List.of());
        for (var slot : layout.slots()) assertTrue(Math.abs(slot.x()) + slot.width()/2 <= layout.boardHalf());
    }

    @Test void heldShogiSlotsClearTraysWithoutRaisingBoardSquares() {
        for (String kind : List.of("shogi", "mini-shogi")) {
            var game = (GoldenEggOVO.TDGames.Shared.game.ClassicGame)
                    GoldenEggOVO.TDGames.Core.game.GameFactory.create(kind, 2, 1);
            List<Cell> cells = new ArrayList<>(game.cells());
            cells.add(new Cell("h0P", 0, 0, "P_1", 0)); cells.add(new Cell("h1P", 0, 0, "P_1", 1));
            var layout = ClassicLayout.of(kind, cells, List.of());
            for (var slot : layout.slots())
                assertEquals(slot.id().matches("h[01][PLNSGBRK]") ? .0155 : .0105, slot.height(), 1e-6,
                        "Only held pieces need to clear the raised tray");
        }
    }

    @Test void backgammonBarSlotsClearTheRailWithoutRaisingPointsOrOffTrays() {
        var game = (GoldenEggOVO.TDGames.Shared.game.ClassicGame)
                GoldenEggOVO.TDGames.Core.game.GameFactory.create("backgammon", 2, 1);
        var layout = ClassicLayout.of("backgammon", game.cells(), List.of());
        for (var slot : layout.slots())
            assertEquals(slot.id().startsWith("bar") ? .0155 : .012, slot.height(), 1e-6,
                    "Only bar pieces need to clear the center rail");
    }

    @Test void hexHitRejectsRectangleCornersOutsideHexagon() {
        var layout = ClassicLayout.of("hex", List.of(new Cell("0,0", 0, 0, "", -1)), List.of());
        var slot = layout.slot("0,0");
        assertNull(layout.hit(slot.x() + slot.width() * .48, slot.z() + slot.depth() * .48));
        assertEquals("0,0", layout.hit(slot.x() + slot.width() * .20, slot.z() + slot.depth() * .40));
    }

    @Test void hexDiamondPointsAtTheFourEdgeMidpointsOfAWideTable() {
        var game = new GoldenEggOVO.TDGames.Multiplayer.game.hex.HexGame(2, 1, Map.of());
        var layout = ClassicLayout.of("hex", game.cells(), List.of("swap"));
        assertEquals(121, game.cells().size());
        assertTrue(ClassicLayout.tableHalfWidth("hex") > ClassicLayout.tableHalfDepth("hex") * 1.25,
                "Hex needs a visibly wide rectangular table");
        for (String id : List.of("0,0", "10,10")) {
            assertEquals(0, layout.slot(id).z(), 1e-6, id + " must point at a side midpoint");
            assertTrue(Math.abs(layout.slot(id).x()) > 1);
        }
        for (String id : List.of("10,0", "0,10")) {
            assertEquals(0, layout.slot(id).x(), 1e-6, id + " must point at a front/back midpoint");
            assertTrue(Math.abs(layout.slot(id).z()) > .6);
        }
        for (var slot : layout.slots()) {
            assertEquals(slot.id(), layout.hit(slot.x(), slot.z()));
            double angle = Math.toRadians(slot.yaw());
            double halfWidth = (Math.abs(Math.cos(angle)) * slot.width() + Math.abs(Math.sin(angle)) * slot.depth()) / 2;
            double halfDepth = (Math.abs(Math.sin(angle)) * slot.width() + Math.abs(Math.cos(angle)) * slot.depth()) / 2;
            assertTrue(Math.abs(slot.x()) + halfWidth < ClassicLayout.tableHalfWidth("hex"));
            assertTrue(Math.abs(slot.z()) + halfDepth < ClassicLayout.tableHalfDepth("hex"));
        }
    }

    @Test void hexNativeGridAndHitAreasShareTheSameCentersAndBoardBounds() {
        var game = new GoldenEggOVO.TDGames.Multiplayer.game.hex.HexGame(2, 1, Map.of());
        var layout = ClassicLayout.of("hex", game.cells(), List.of());
        var model = GoldenEggOVO.TDGames.Core.render.model.ModelRepository.require("tabletop/hex/table");
        assertEquals(406, model.blocks().stream().filter(block -> block.name().startsWith("hex_grid_")).count());
        for (var cell : game.cells()) {
            var slot = layout.slot(cell.id());
            assertEquals(0, slot.yaw(), 1e-6);
            assertEquals(cell.id(), layout.hit(slot.x(), slot.z()));
        }
        var base = new GoldenEggOVO.TDGames.Core.render.model.ModelDefinition(
                model.blocks().stream().filter(block -> block.name().startsWith("wood_base")).toList(), List.of());
        var bounds = GoldenEggOVO.TDGames.Core.render.model.ModelGeometry.bounds(base);
        assertEquals(ClassicLayout.tableHalfWidth("hex") * 2, bounds.getWidthX(), 1e-6);
        assertEquals(ClassicLayout.tableHalfDepth("hex") * 2, bounds.getWidthZ(), 1e-6);
    }
}

package GoldenEggOVO.TDGames.Shared.render.packed;

import GoldenEggOVO.TDGames.Shared.render.PieceColors;

import GoldenEggOVO.TDGames.Core.pack.CraftEngineModels;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Core.text.GameSymbols;
import GoldenEggOVO.TDGames.Shared.render.BoardPieceModels;
import org.bukkit.Material;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BoardModelKeysTest {
    @Test
    void draughtsColorsStayTheSameWhenTheResourcePackChanges() {
        for (int owner = 0; owner < 2; owner++) for (String rank : List.of(GameSymbols.PAWN, GameSymbols.KING)) {
            Cell cell = new Cell("a", 0, 0, rank, owner);
            Material expected = owner == 0 ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_QUARTZ;
            assertEquals(expected, BoardPieceModels.piece("draughts", cell, Map.of()).getFirst().material());
            assertEquals("draught_" + (owner == 0 ? "black" : "white")
                    + (rank.equals(GameSymbols.KING) ? "_king" : ""), BoardModelKeys.piece("draughts", cell, Map.of()));
        }
        assertEquals("chess_white_king", BoardModelKeys.piece("chess", new Cell("a", 0, 0, GameSymbols.KING, 0), Map.of()));
    }

    @Test
    void catalogueBoardsAndEveryInitialPieceHaveRegisteredModels() {
        for (String kind : List.of("chess", "connectfour", "xiangqi", "gomoku", "go", "go9",
                "go13", "reversi", "draughts", "checkers", "ludo")) {
            assertTrue(BoardModelKeys.supported(kind), kind);
            assertTrue(ResourcePackService.supported(kind), kind);
            assertTrue(CraftEngineModels.IDS.contains(BoardModelKeys.table(kind)), kind);
            var game = GameFactory.create(kind, kind.equals("checkers") ? 6 : 2, 0);
            for (Cell cell : game.cells()) if (cell.owner() >= 0)
                assertTrue(CraftEngineModels.IDS.contains(
                        BoardModelKeys.piece(kind, cell, game.publicInfo())), kind + " " + cell);
        }
    }

    @Test
    void crownsSidesAndDynamicSeatColorsAreDistinct() {
        assertNotEquals(BoardModelKeys.piece("draughts", new Cell("a",0,0,GameSymbols.PAWN,0), Map.of()),
                BoardModelKeys.piece("draughts", new Cell("a",0,0,GameSymbols.KING,0), Map.of()));
        assertNotEquals(BoardModelKeys.piece("chess", new Cell("a",0,0,GameSymbols.KING,0), Map.of()),
                BoardModelKeys.piece("chess", new Cell("a",0,0,GameSymbols.KING,1), Map.of()));
        for (String color : PieceColors.NAMES)
            assertTrue(CraftEngineModels.IDS.contains("peg_" + color));
    }
}

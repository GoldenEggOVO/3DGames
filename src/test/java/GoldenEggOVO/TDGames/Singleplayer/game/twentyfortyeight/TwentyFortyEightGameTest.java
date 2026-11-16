package GoldenEggOVO.TDGames.Singleplayer.game.twentyfortyeight;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TwentyFortyEightGameTest {
    private TwentyFortyEightGame game() { return new TwentyFortyEightGame(1, 17, Map.of()); }
    private void board(TwentyFortyEightGame game, int... values) throws Exception {
        Field field = TwentyFortyEightGame.class.getDeclaredField("board"); field.setAccessible(true);
        int[] board = (int[]) field.get(game); Arrays.fill(board, 0);
        System.arraycopy(values, 0, board, 0, values.length);
    }
    private int value(TwentyFortyEightGame game, String id) {
        return Integer.parseInt(game.cells().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().piece());
    }
    @Test void eachTileMergesOnlyOnceAndScoreUsesMergedValues() throws Exception {
        var game = game(); board(game, 2, 2, 2, 2);
        game.apply(0, "slide:left");
        assertEquals(4, value(game, "0,0")); assertEquals(4, value(game, "1,0"));
        assertEquals("8", game.publicInfo().get("score"));
        assertEquals(3, game.cells().stream().filter(c -> !c.piece().equals("0")).count());
    }
    @Test void invalidSlideDoesNotConsumeRandomSequence() throws Exception {
        var first = game(); var replay = game(); board(first, 2); board(replay, 2);
        assertFalse(first.legalActions(0).contains("slide:left"));
        assertThrows(IllegalArgumentException.class, () -> first.apply(0, "slide:left"));
        first.apply(0, "slide:right"); replay.apply(0, "slide:right");
        assertEquals(replay.cells(), first.cells());
    }
    @Test void reachingGoalWaitsForContinueOrFinish() throws Exception {
        var game = game(); board(game, 1024, 1024);
        game.apply(0, "slide:left"); assertFalse(game.finished());
        assertEquals(List.of("continue", "finish"), game.legalActions(0));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "slide:right"));
        game.apply(0, "continue"); assertFalse(game.finished());
        assertTrue(game.legalActions(0).contains("slide:right"));
        assertFalse(game.legalActions(0).contains("continue"));
        var settle = game(); board(settle, 1024, 1024); settle.apply(0, "slide:left"); settle.apply(0, "finish");
        assertTrue(settle.finished()); assertEquals("winner:0", settle.outcome());
    }
    @Test void fourDirectionsMergeInReadingOrder() throws Exception {
        for (String direction : List.of("left", "right", "up", "down")) {
            var game = game();
            board(game, 2, 2, 0, 0, 2, 0, 0, 0);
            game.apply(0, "slide:" + direction);
            assertEquals("4", game.publicInfo().get("score"));
            assertTrue(Set.of(8, 10).contains(game.cells().stream().mapToInt(c -> Integer.parseInt(c.piece())).sum()));
        }
    }
    @Test void noMovesEndsAndMalformedActionsNeverChangeState() throws Exception {
        var game = game(); board(game, 2, 4, 2, 4, 4, 2, 4, 2, 16, 4, 2, 4, 8, 16, 8, 0);
        game.apply(0, "slide:right"); assertTrue(game.finished());
        var fresh = game(); var cells = fresh.cells();
        for (String action : Arrays.asList(null, "", "slide:diagonal", "continue", "slide:left:extra")) {
            assertThrows(IllegalArgumentException.class, () -> fresh.apply(0, action)); assertEquals(cells, fresh.cells());
        }
        assertThrows(IllegalArgumentException.class, () -> fresh.apply(1, "slide:left"));
    }
    @Test void identicalSeedAndCommittedActionsReplay() {
        var first = game(); var replay = game();
        for (int step = 0; step < 200 && !first.finished(); step++) {
            String action = first.legalActions(0).getFirst(); first.apply(0, action); replay.apply(0, action);
            assertEquals(first.cells(), replay.cells()); assertEquals(first.publicInfo(), replay.publicInfo());
        }
        assertEquals(first.outcome(), replay.outcome());
    }

    @Test void reachingGoalOnBlockedBoardStillOffersBothSettlementChoices() throws Exception {
        for (String choice : List.of("continue", "finish")) {
            var game = game();
            board(game, 1024, 1024, 2, 8, 2, 4, 2, 8, 4, 2, 4, 2, 2, 4, 2, 4);
            game.apply(0, "slide:left");
            assertEquals(List.of("continue", "finish"), game.legalActions(0));
            game.apply(0, choice);
            assertEquals("winner:0", game.outcome());
            var cells = game.cells();
            var info = game.publicInfo();
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, choice));
            assertEquals(cells, game.cells());
            assertEquals(info, game.publicInfo());
        }
    }
}

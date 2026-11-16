package GoldenEggOVO.TDGames.Multiplayer.game.mancala;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MancalaGameTest {
    private MancalaGame game() { return new MancalaGame(2, 9, Map.of()); }
    private int[] pits(MancalaGame game) throws Exception {
        Field field = MancalaGame.class.getDeclaredField("pits"); field.setAccessible(true); return (int[]) field.get(game);
    }
    private void position(MancalaGame game, int... values) throws Exception { System.arraycopy(values, 0, pits(game), 0, 14); }
    @Test void initialSowingOwnStoreGivesAdditionalTurn() throws Exception {
        var game = game(); assertEquals(48, Arrays.stream(pits(game)).sum());
        game.apply(0, "sow:2"); assertEquals(0, game.currentPlayer());
        assertArrayEquals(new int[]{4,4,0,5,5,5,1,4,4,4,4,4,4,0}, pits(game));
        assertEquals(List.of("sow:0"), game.actionsForCell(0, "0"));
    }
    @Test void multipleLapsSkipOnlyOpposingStore() throws Exception {
        var game = game(); position(game, 27,1,1,1,1,1,0,1,1,1,1,1,11,0);
        game.apply(0, "sow:0"); assertEquals(0, pits(game)[13]); assertEquals(48, Arrays.stream(pits(game)).sum());
        assertEquals(4, pits(game)[1]); assertEquals(2, pits(game)[6]); assertEquals(1, game.currentPlayer());
    }
    @Test void capturesOppositeOnlyOnOwnPreviouslyEmptyPit() throws Exception {
        var game = game(); position(game, 1,0,1,0,0,0,10,1,1,1,1,5,1,26);
        game.apply(0, "sow:0"); assertEquals(16, pits(game)[6]); assertEquals(0, pits(game)[1]);
        assertEquals(0, pits(game)[11]); assertEquals(48, Arrays.stream(pits(game)).sum());
    }
    @Test void emptySideSweepsAndCanDraw() throws Exception {
        var game = game(); position(game, 0,0,0,0,0,1,23,4,4,4,4,4,4,0);
        game.apply(0, "sow:5"); assertTrue(game.finished()); assertEquals("draw:equal-stores", game.outcome());
        assertArrayEquals(new int[]{0,0,0,0,0,0,24,0,0,0,0,0,0,24}, pits(game));
    }
    @Test void illegalInputNeverMutatesAndDeterministicBotsFinishFullReplay() {
        var game = game(); var replay = game(); var before = game.cells();
        for (String action : Arrays.asList(null,"", "sow:13", "sow:7", "sow:-1", "sow:0:1")) {
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, action)); assertEquals(before, game.cells());
        }
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "sow:7"));
        Random random = new Random(1);
        for (int step=0;step<500 && !game.finished();step++) {
            int seat=game.currentPlayer(); String action=game.botAction(seat,random);
            assertTrue(game.legalActions(seat).contains(action)); game.apply(seat,action); replay.apply(seat,action);
            assertEquals(game.cells(),replay.cells());
        }
        assertTrue(game.finished()); assertEquals(game.outcome(),replay.outcome());
    }

    @Test void secondPlayersCaptureUsesItsOwnStoreAndOppositePit() throws Exception {
        var game = game();
        position(game, 1,1,1,1,5,1,26,1,0,1,0,0,0,10);
        Field turn = MancalaGame.class.getDeclaredField("turn");
        turn.setAccessible(true);
        turn.setInt(game, 1);
        game.apply(1, "sow:7");
        assertEquals(16, pits(game)[13]);
        assertEquals(0, pits(game)[8]);
        assertEquals(0, pits(game)[4]);
        assertEquals(48, Arrays.stream(pits(game)).sum());
        assertEquals(0, game.currentPlayer());
        assertFalse(game.finished());
    }

    @Test void secondPlayersMultipleLapsSkipFirstPlayersStore() throws Exception {
        var game = game();
        position(game, 1,1,1,1,1,11,0,27,1,1,1,1,1,0);
        Field turn = MancalaGame.class.getDeclaredField("turn");
        turn.setAccessible(true);
        turn.setInt(game, 1);
        game.apply(1, "sow:7");
        assertEquals(0, pits(game)[6]);
        assertEquals(2, pits(game)[13]);
        assertEquals(4, pits(game)[8]);
        assertEquals(48, Arrays.stream(pits(game)).sum());
        assertEquals(0, game.currentPlayer());
    }
}

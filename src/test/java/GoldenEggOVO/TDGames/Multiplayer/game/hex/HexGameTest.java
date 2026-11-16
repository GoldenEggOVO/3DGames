package GoldenEggOVO.TDGames.Multiplayer.game.hex;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HexGameTest {
    private HexGame game() { return new HexGame(2, 9, Map.of()); }
    private int[] board(HexGame game) throws Exception {
        Field field=HexGame.class.getDeclaredField("board"); field.setAccessible(true); return (int[])field.get(game);
    }
    @Test void pieSwapChangesOwnersAndConsumesSecondPlayersTurn() {
        var game=game(); game.apply(0,"place:5,5"); assertTrue(game.legalActions(1).contains("swap"));
        game.apply(1,"swap"); assertEquals(0,game.currentPlayer());
        assertEquals(1,game.cells().stream().filter(c->c.id().equals("5,5")).findFirst().orElseThrow().owner());
        assertFalse(game.legalActions(0).contains("swap")); game.apply(0,"place:4,5");
        assertFalse(game.legalActions(1).contains("swap"));
    }
    @Test void placingInsteadOfSwapClosesWindowAndOptionCanDisableIt() {
        var game=game(); game.apply(0,"place:5,5"); game.apply(1,"place:4,5");
        assertFalse(game.legalActions(0).contains("swap"));
        var disabled=new HexGame(2,1,Map.of("pie-rule","false")); disabled.apply(0,"place:5,5");
        assertFalse(disabled.legalActions(1).contains("swap"));
    }
    @Test void sixNeighbourDiagonalConnectionWinsForSwappedOwner() throws Exception {
        var game=game(); game.apply(0,"place:10,0"); game.apply(1,"swap");
        Arrays.fill(board(game),-1);
        for(int y=0;y<10;y++) board(game)[y*11+10-y]=0;
        game.apply(0,"place:10,10");
        assertFalse(game.finished()); game.apply(1,"place:0,10");
        assertTrue(game.finished()); assertEquals("winner:1",game.outcome());
    }
    @Test void otherColourConnectsHorizontalEdges() throws Exception {
        var game=game(); game.apply(0,"place:0,10");
        Arrays.fill(board(game),-1); for(int x=0;x<10;x++) board(game)[x]=1;
        game.apply(1,"place:10,0"); assertEquals("winner:1",game.outcome());
    }
    @Test void squareDiagonalIsNotOneOfHexsSixNeighbours() throws Exception {
        var game=game();for(int i=0;i<10;i++)board(game)[i*11+i]=0;
        game.apply(0,"place:10,10");assertFalse(game.finished());
    }
    @Test void invalidInputsAreAtomicAndFullBotGameReplaysWithoutDraw() {
        var game=game(); var replay=game(); var before=game.cells();
        for(String action:Arrays.asList(null,"","swap","place:11,0","place:-1,0","place:0,0:1")) {
            assertThrows(IllegalArgumentException.class,()->game.apply(0,action)); assertEquals(before,game.cells());
        }
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"place:0,0"));
        Random random=new Random(17);
        for(int step=0;step<123 && !game.finished();step++) {
            int seat=game.currentPlayer(); String action=game.botAction(seat,random);
            game.apply(seat,action); replay.apply(seat,action); assertEquals(game.cells(),replay.cells());
        }
        assertTrue(game.finished()); assertTrue(game.outcome().startsWith("winner:")); assertEquals(game.outcome(),replay.outcome());
    }
}

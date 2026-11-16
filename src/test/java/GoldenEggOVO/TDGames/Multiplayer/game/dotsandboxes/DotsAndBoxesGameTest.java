package GoldenEggOVO.TDGames.Multiplayer.game.dotsandboxes;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DotsAndBoxesGameTest {
    private DotsAndBoxesGame game(int players) { return new DotsAndBoxesGame(players, 9, Map.of()); }
    @Test void fourthEdgeCanClaimTwoBoxesAndKeepTurn() {
        var game=game(2);
        for(String edge:List.of("h0,0","h1,0","h0,1","h1,1","v0,0","v2,0"))
            game.apply(game.currentPlayer(),"draw:"+edge);
        assertEquals(0,game.currentPlayer()); game.apply(0,"draw:v1,0");
        assertEquals(0,game.currentPlayer()); assertEquals("2",game.publicInfo().get("score.0"));
        assertEquals(0,game.cells().stream().filter(c->c.id().equals("b0,0")).findFirst().orElseThrow().owner());
        assertEquals(0,game.cells().stream().filter(c->c.id().equals("b1,0")).findFirst().orElseThrow().owner());
        assertEquals(List.of("draw:h2,0"),game.actionsForCell(0,"h2,0"));
    }
    @Test void supportsSequentialThreeAndFourPlayerTurns() {
        for(int players:List.of(3,4)) {
            var game=game(players);
            for(int seat=0;seat<players;seat++) { assertEquals(seat,game.currentPlayer()); game.apply(seat,"draw:h"+seat+",0"); }
            assertEquals(0,game.currentPlayer());
        }
    }
    @Test void allTwentyFiveBoxesAreScoredAndReplayEnds() {
        for(int players:List.of(2,3,4)) {
            var game=game(players); var replay=game(players); Random random=new Random(17);
            int moves=0;
            while(!game.finished()) {
                int seat=game.currentPlayer(); String action=game.botAction(seat,random);
                assertNotNull(action); game.apply(seat,action); replay.apply(seat,action); moves++;
                assertEquals(game.cells(),replay.cells()); assertEquals(game.publicInfo(),replay.publicInfo());
                assertTrue(moves<=60);
            }
            assertEquals(60,moves);
            int total=0; for(int seat=0;seat<players;seat++)total+=Integer.parseInt(game.publicInfo().get("score."+seat));
            assertEquals(25,total); assertEquals(game.outcome(),replay.outcome());
        }
    }
    @Test void invalidEdgesAndWrongSeatNeverMutate() {
        var game=game(2); var before=game.cells();
        for(String action:Arrays.asList(null,"","draw:b0,0","draw:h5,0","draw:v6,0","draw:0,0:1,1")) {
            assertThrows(IllegalArgumentException.class,()->game.apply(0,action));assertEquals(before,game.cells());
        }
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"draw:h0,0"));
        game.apply(0,"draw:h0,0"); var after=game.cells();
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"draw:h0,0"));assertEquals(after,game.cells());
    }
    @Test void multiplayerCanHaveSharedWinnersAfterDoubleFinalBox() throws Exception {
        var game=game(3);
        for(String name:List.of("horizontal","vertical")) Arrays.fill(array(game,name),0);
        int[] boxes=array(game,"boxes");Arrays.fill(boxes,-1);
        Arrays.fill(boxes,0,10,0);Arrays.fill(boxes,10,22,1);boxes[22]=2;
        array(game,"scores")[0]=10;array(game,"scores")[1]=12;array(game,"scores")[2]=1;
        array(game,"vertical")[28]=-1;
        game.apply(0,"draw:v4,4");assertEquals("winners:0,1",game.outcome());
        assertEquals("12",game.publicInfo().get("score.0"));assertEquals("0",game.publicInfo().get("remaining"));
    }
    private int[] array(DotsAndBoxesGame game,String name)throws Exception {
        Field field=DotsAndBoxesGame.class.getDeclaredField(name);field.setAccessible(true);return(int[])field.get(game);
    }
}

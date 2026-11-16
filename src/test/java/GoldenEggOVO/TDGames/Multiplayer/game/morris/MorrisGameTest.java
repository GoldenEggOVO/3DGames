package GoldenEggOVO.TDGames.Multiplayer.game.morris;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MorrisGameTest {
    private MorrisGame game() { return new MorrisGame(2,9,Map.of()); }
    private int[] array(MorrisGame game,String name)throws Exception {
        Field field=MorrisGame.class.getDeclaredField(name);field.setAccessible(true);return(int[])field.get(game);
    }
    private void moving(MorrisGame game,int[] first,int[] second)throws Exception {
        Arrays.fill(array(game,"board"),-1);Arrays.fill(array(game,"unplaced"),0);
        for(int p:first)array(game,"board")[p]=0;for(int p:second)array(game,"board")[p]=1;
    }
    @Test void millRequiresCaptureBeforeTurnCanChangeAndPreservesUnplacedPieces() throws Exception {
        var game=game();
        game.apply(0,"place:0");game.apply(1,"place:8");game.apply(0,"place:1");game.apply(1,"place:9");game.apply(0,"place:2");
        assertEquals(0,game.currentPlayer());assertEquals(Set.of("take:8","take:9"),new HashSet<>(game.legalActions(0)));
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"place:3"));game.apply(0,"take:8");
        assertEquals(1,game.currentPlayer());assertArrayEquals(new int[]{6,7},array(game,"unplaced"));
        assertFalse(game.finished());
    }
    @Test void protectedMillCanOnlyBeTakenWhenAllEnemyPiecesAreInMills() throws Exception {
        var game=game();moving(game,new int[]{0,1,3,16},new int[]{8,9,10,14});
        game.apply(0,"move:3:2");assertEquals(List.of("take:14"),game.legalActions(0));
        game.apply(0,"take:14"); assertFalse(game.finished());
        var all=game();moving(all,new int[]{0,1,7},new int[]{8,9,10});
        all.apply(0,"move:7:2");assertEquals(Set.of("take:8","take:9","take:10"),new HashSet<>(all.legalActions(0)));
        all.apply(0,"take:8");assertEquals("winner:0",all.outcome());
    }
    @Test void onlyPlayerWithThreePiecesCanFly() throws Exception {
        var game=game();moving(game,new int[]{0,4,6},new int[]{8,10,12,14});
        assertTrue(game.legalActions(0).contains("move:0:23"));game.apply(0,"move:0:23");
        assertFalse(game.legalActions(1).contains("move:8:0"));
        assertEquals(List.of("move:8:9","move:8:15"),game.actionsForCell(1,"8"));
    }
    @Test void openingTwoMillsStillCapturesOnlyOnePiece() throws Exception {
        var game=game();
        for(int point:new int[]{0,2,9,17})array(game,"board")[point]=0;
        for(int point:new int[]{8,10,12,14})array(game,"board")[point]=1;
        game.apply(0,"place:1");game.apply(0,"take:8");assertEquals(1,game.currentPlayer());
        assertTrue(game.legalActions(0).isEmpty());
    }
    @Test void thirdCompletePositionInMovementDraws() throws Exception {
        var game=game();moving(game,new int[]{0,3,6,10},new int[]{2,5,8,12});
        List<String> cycle=List.of("move:0:1","move:12:13","move:1:0","move:13:12");
        for(int n=0;n<3 && !game.finished();n++) for(String action:cycle) {
            if(game.finished())break;game.apply(game.currentPlayer(),action);
        }
        assertEquals("draw:threefold-repetition",game.outcome());
    }
    @Test void lastPlacementImmediatelyLosesWhenActorHasFewerThanThreePieces() throws Exception {
        var game=game();moving(game,new int[]{0},new int[]{8,10,12,14});array(game,"unplaced")[0]=1;
        game.apply(0,"place:4");assertEquals("winner:1",game.outcome());
    }
    @Test void blockedOpponentLosesAndReformingAnOldMillRequiresAnotherCapture() throws Exception {
        var blocked=game();moving(blocked,new int[]{0,2,4,6,9,11,13,15,16},new int[]{1,3,5,7});
        blocked.apply(0,"move:16:17");assertEquals("winner:0",blocked.outcome());
        var reform=game();moving(reform,new int[]{0,1,2,4,6},new int[]{8,10,12,14});
        reform.apply(0,"move:2:3");reform.apply(1,"move:12:13");reform.apply(0,"move:3:2");
        assertTrue(reform.legalActions(0).stream().allMatch(a->a.startsWith("take:")));
        assertFalse(reform.legalActions(0).isEmpty());
    }
    @Test void illegalInputNeverChangesBoardAndDeterministicActionsReplay() {
        var game=game();var replay=game();var before=game.cells();
        for(String action:Arrays.asList(null,"","place:24","place:-1","take:0","move:0:1","place:0:1")) {
            assertThrows(IllegalArgumentException.class,()->game.apply(0,action));assertEquals(before,game.cells());
        }
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"place:0"));
        Random random=new Random(1);
        for(int step=0;step<300&&!game.finished();step++) {
            int seat=game.currentPlayer();String action=game.botAction(seat,random);assertNotNull(action);
            game.apply(seat,action);replay.apply(seat,action);assertEquals(game.cells(),replay.cells());assertEquals(game.publicInfo(),replay.publicInfo());
        }
        assertEquals(game.outcome(),replay.outcome());
    }

    @Test void finalPlacementMillMustCaptureBeforeAdjudicatingPieceCount() throws Exception {
        var game = game();
        moving(game, new int[]{0,1}, new int[]{8,10,12});
        array(game, "unplaced")[0] = 1;
        game.apply(0, "place:2");
        assertFalse(game.finished());
        assertEquals(0, game.currentPlayer());
        assertEquals(Set.of("take:8", "take:10", "take:12"), new HashSet<>(game.legalActions(0)));
        game.apply(0, "take:8");
        assertEquals("winner:0", game.outcome());
        var before = game.cells();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "take:10"));
        assertEquals(before, game.cells());
    }
}

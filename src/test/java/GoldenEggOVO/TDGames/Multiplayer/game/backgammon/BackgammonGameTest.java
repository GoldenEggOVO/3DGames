package GoldenEggOVO.TDGames.Multiplayer.game.backgammon;

import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackgammonGameTest {
    private Object field(Object game,String name)throws Exception{Field f=game.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    private void set(Object game,String name,Object value)throws Exception{Field f=game.getClass().getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    private BackgammonGame position(int seat,int[] dice,String... entries)throws Exception{
        BackgammonGame game=new BackgammonGame(2,17,Map.of("doubling","on"));
        int[] points=(int[])field(game,"points");Arrays.fill(points,0);
        for(String entry:entries){String[] p=entry.split("=");points[Integer.parseInt(p[0])-1]=Integer.parseInt(p[1]);}
        set(game,"turn",seat);set(game,"phase","move");
        @SuppressWarnings("unchecked") List<Integer> remaining=(List<Integer>)field(game,"remaining");remaining.clear();for(int d:dice)remaining.add(d);
        return game;
    }
    @Test void openingUsesUnequalDiceAndStandardFifteenCheckers() {
        BackgammonGame game=new BackgammonGame(2,8,Map.of());
        assertEquals(15,game.cells().stream().filter(c->c.owner()==0).mapToInt(c->Integer.parseInt(c.piece())).sum());
        assertEquals(15,game.cells().stream().filter(c->c.owner()==1).mapToInt(c->Integer.parseInt(c.piece())).sum());
        assertEquals(2,game.dice().size());assertNotEquals(game.dice().get(0),game.dice().get(1));
        assertEquals(game.dice().get(0)>game.dice().get(1)?0:1,game.currentPlayer());
        assertTrue(game.legalActions(1-game.currentPlayer()).isEmpty());
    }
    @Test void invalidActionDoesNotChangeBoardDiceOrRandomReplay() {
        BackgammonGame a=new BackgammonGame(2,8,Map.of()),b=new BackgammonGame(2,8,Map.of());
        assertThrows(IllegalArgumentException.class,()->a.apply(a.currentPlayer(),"move:p24:p1:6"));
        assertEquals(b.cells(),a.cells());assertEquals(b.publicInfo(),a.publicInfo());
        Random random=new Random(5);
        for(int i=0;i<100&&!a.finished();i++){int seat=a.currentPlayer();String action=a.botAction(seat,random);assertNotNull(action);a.apply(seat,action);b.apply(seat,action);assertEquals(b.cells(),a.cells());assertEquals(b.publicInfo(),a.publicInfo());}
    }
    @Test void barMustEnterFirstAndEnemyStacksBlock()throws Exception{
        BackgammonGame game=position(0,new int[]{1,2},"24=-2","23=-1","6=14");
        ((int[])field(game,"bar"))[0]=1;
        assertEquals(List.of("move:bar0:p23:2"),game.legalActions(0));
        game.apply(0,"move:bar0:p23:2");assertEquals(0,game.bar(0));assertEquals(1,game.bar(1));
    }
    @Test void chooseOrderThatAllowsBothDiceInsteadOfStrandingSecond()throws Exception{
        BackgammonGame game=position(0,new int[]{1,2},"4=1");
        assertTrue(game.legalActions(0).contains("move:p4:p3:1"));
        assertTrue(game.legalActions(0).contains("move:p4:p2:2"));
        BackgammonGame blocked=position(0,new int[]{1,2},"4=1","2=-2");
        assertEquals(List.of("move:p4:p3:1"),blocked.legalActions(0));
    }
    @Test void onlyOnePlayableDieMustUseHigherEvenWhenLowerAlsoWorks()throws Exception{
        BackgammonGame game=position(0,new int[]{1,2},"22=-2");
        ((int[])field(game,"bar"))[0]=1;((int[])field(game,"off"))[0]=14;
        assertEquals(List.of("move:bar0:p23:2"),game.legalActions(0));
        BackgammonGame bear=position(0,new int[]{1,2},"1=1");
        ((int[])field(bear,"off"))[0]=14;
        assertEquals(List.of("move:p1:off0:2"),bear.legalActions(0));
    }
    @Test void maximumDiceSearchRejectsLocallyLegalShortSequence()throws Exception{
        BackgammonGame game=position(0,new int[]{1,2},"4=1","1=-2","2=-2");
        assertEquals(List.of("move:p4:p3:1"),game.legalActions(0));
        BackgammonGame sequence=position(0,new int[]{1,2},"3=1","1=-2");
        assertEquals(List.of("move:p3:p2:1"),sequence.legalActions(0));
        sequence.apply(0,"move:p3:p2:1");assertEquals(List.of("move:p2:off0:2"),sequence.legalActions(0));
    }
    @Test void doubleRollProvidesFourMovesAndBlockedTurnCanPass()throws Exception{
        BackgammonGame game=position(0,new int[]{2,2,2,2},"12=1","24=-15");
        for(String action:List.of("move:p12:p10:2","move:p10:p8:2","move:p8:p6:2","move:p6:p4:2"))game.apply(0,action);
        assertEquals(1,game.currentPlayer());assertTrue(game.legalActions(1).contains("roll"));
        BackgammonGame blocked=position(0,new int[]{1,2},"24=-2","23=-2");((int[])field(blocked,"bar"))[0]=1;
        assertEquals(List.of("pass"),blocked.legalActions(0));blocked.apply(0,"pass");assertEquals(1,blocked.currentPlayer());
    }
    @Test void bearingOffRequiresHomeAndOvershootHighestPoint()throws Exception{
        BackgammonGame game=position(0,new int[]{6},"6=1","4=1");
        assertTrue(game.legalActions(0).contains("move:p6:off0:6"));assertFalse(game.legalActions(0).contains("move:p4:off0:6"));
        BackgammonGame outside=position(0,new int[]{6},"7=1","4=1");assertFalse(outside.legalActions(0).contains("move:p4:off0:6"));
        BackgammonGame other=position(1,new int[]{6},"19=-1","22=-1");assertTrue(other.legalActions(1).contains("move:p19:off1:6"));assertFalse(other.legalActions(1).contains("move:p22:off1:6"));
    }
    @Test void normalGammonBackgammonScoresAreDistinct()throws Exception{
        for(int mode=1;mode<=3;mode++){
            BackgammonGame game=position(0,new int[]{1},"1=1",mode==3?"2=-15":mode==2?"20=-15":"20=-14");
            ((int[])field(game,"off"))[0]=14;((int[])field(game,"off"))[1]=mode==1?1:0;
            game.apply(0,"move:p1:off0:1");assertEquals("winner:0",game.outcome());assertEquals(mode,game.winPoints());
        }
    }
    @Test void doublingOnlyBeforeRollTransfersOwnershipAndRejectUsesOldValue()throws Exception{
        BackgammonGame game=position(0,new int[]{},"6=15","19=-15");set(game,"phase","roll");
        assertTrue(game.legalActions(0).contains("double:offer"));game.apply(0,"double:offer");
        assertEquals(1,game.currentPlayer());assertEquals(List.of("double:accept","double:reject"),game.legalActions(1));
        game.apply(1,"double:accept");assertEquals(2,game.cube());assertEquals(1,game.cubeOwner());assertEquals(0,game.currentPlayer());
        assertFalse(game.legalActions(0).contains("double:offer"));
        set(game,"turn",1);game.apply(1,"double:offer");game.apply(0,"double:reject");assertEquals("winner:1",game.outcome());assertEquals(2,game.winPoints());
    }
    @Test void doublingDisabledByDefaultAndCappedAt64()throws Exception{
        BackgammonGame disabled=new BackgammonGame(2,1,Map.of());set(disabled,"phase","roll");assertFalse(disabled.legalActions(disabled.currentPlayer()).contains("double:offer"));
        BackgammonGame capped=position(0,new int[]{},"6=15","19=-15");set(capped,"phase","roll");set(capped,"cube",64);assertFalse(capped.legalActions(0).contains("double:offer"));
        assertThrows(UnsupportedOperationException.class,()->capped.dice().add(1));
    }
    @Test void completeSeededGamesReplayAndConserveAllCheckers() {
        for(long seed=0;seed<8;seed++){
            BackgammonGame game=new BackgammonGame(2,seed,Map.of()),replay=new BackgammonGame(2,seed,Map.of());Random random=new Random(seed+100);
            List<String> history=new ArrayList<>();List<Integer> actors=new ArrayList<>();
            for(int step=0;step<2000&&!game.finished();step++){
                int actor=game.currentPlayer();String action=game.botAction(actor,random);assertNotNull(action);
                history.add(action);actors.add(actor);game.apply(actor,action);
                for(int seat=0;seat<2;seat++){final int owner=seat;assertEquals(15,game.cells().stream().filter(c->c.owner()==owner).mapToInt(c->Integer.parseInt(c.piece())).sum());}
            }
            assertTrue(game.finished(),"Seed "+seed+" did not finish within the action budget");
            for(int i=0;i<history.size();i++)replay.apply(actors.get(i),history.get(i));
            assertEquals(game.cells(),replay.cells());assertEquals(game.publicInfo(),replay.publicInfo());assertEquals(game.outcome(),replay.outcome());
        }
    }
    @Test void botAndLegalQueriesDoNotConsumeGameRandomnessOrMutateState()throws Exception{
        BackgammonGame a=new BackgammonGame(2,8,Map.of()),b=new BackgammonGame(2,8,Map.of());
        for(int i=0;i<40&&!a.finished();i++){
            int actor=a.currentPlayer();var before=a.cells();var info=a.publicInfo();
            a.legalActions(actor);a.botAction(actor,new Random(i));a.botAction(actor,new Random(i+17));
            assertEquals(before,a.cells());assertEquals(info,a.publicInfo());
            String action=b.botAction(actor,new Random(i+99));a.apply(actor,action);b.apply(actor,action);assertEquals(b.publicInfo(),a.publicInfo());
        }
    }
    @Test void ambiguousBearingOffRequiresExplicitDieAndBothChoicesAreExposed()throws Exception{
        BackgammonGame game=position(0,new int[]{4,6},"3=1","1=1");((int[])field(game,"off"))[0]=13;
        assertTrue(game.actionsForCell(0,"p3").contains("move:p3:off0:4"));
        assertTrue(game.actionsForCell(0,"p3").contains("move:p3:off0:6"));
        var before=game.cells();assertThrows(IllegalArgumentException.class,()->game.apply(0,"move:p3:off0"));assertEquals(before,game.cells());
        game.apply(0,"move:p3:off0:4");assertEquals(List.of(6),game.dice());
    }
    @Test void acceptingAt32Reaches64AndOnlyResponderCanAnswer()throws Exception{
        BackgammonGame game=position(0,new int[]{},"6=15","19=-15");set(game,"phase","roll");set(game,"cube",32);
        game.apply(0,"double:offer");var before=game.publicInfo();
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"double:accept"));assertEquals(before,game.publicInfo());
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"roll"));assertEquals(before,game.publicInfo());
        game.apply(1,"double:accept");assertEquals(64,game.cube());set(game,"turn",1);assertEquals(List.of("roll"),game.legalActions(1));
    }
    @Test void barPriorityContinuesUntilEveryBarCheckerHasEntered()throws Exception{
        BackgammonGame game=position(1,new int[]{1,2},"19=-13");((int[])field(game,"bar"))[1]=2;
        assertTrue(game.legalActions(1).stream().allMatch(a->a.startsWith("move:bar1:")));
        game.apply(1,"move:bar1:p1:1");assertEquals(List.of("move:bar1:p2:2"),game.legalActions(1));
    }
}

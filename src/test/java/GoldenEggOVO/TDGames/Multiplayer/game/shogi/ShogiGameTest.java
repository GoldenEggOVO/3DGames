package GoldenEggOVO.TDGames.Multiplayer.game.shogi;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShogiGameTest {
    private ShogiGame game(boolean mini) { return new ShogiGame(2, 19, Map.of(), mini); }
    private Cell cell(ShogiGame game, String id) { return game.cells().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(); }
    private Object field(Object game, String name) throws Exception {
        Field field = game.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(game);
    }
    private void set(Object game, String name, Object value) throws Exception {
        Field field = game.getClass().getDeclaredField(name); field.setAccessible(true); field.set(game, value);
    }
    private ShogiGame position(boolean mini, String... pieces) throws Exception {
        ShogiGame game = game(mini); int[][] board = (int[][]) field(game, "board");
        for (int[] row : board) Arrays.fill(row, 0);
        for (String entry : pieces) {
            String[] pair = entry.split("="); String id = pair[0];
            board[id.charAt(1)-'1'][id.charAt(0)-'a'] = Integer.parseInt(pair[1]);
        }
        return game;
    }
    @Test void initialBoardsHaveCorrectPiecesAndMiniOrientation() {
        ShogiGame standard = game(false), mini = game(true);
        assertEquals(40, standard.cells().stream().filter(c -> !c.piece().isEmpty()).count());
        assertEquals(12, mini.cells().stream().filter(c -> !c.piece().isEmpty()).count());
        assertEquals("K", cell(mini,"a1").piece()); assertEquals("R", cell(mini,"e1").piece());
        assertEquals("K", cell(mini,"e5").piece()); assertEquals(1,cell(mini,"e5").owner());
        assertEquals("P",cell(mini,"a2").piece()); assertEquals("P",cell(mini,"e4").piece());
        assertTrue(standard.legalActions(0).contains("move:a3:a4"));
        assertTrue(mini.legalActions(1).isEmpty());
    }
    @Test void invalidMoveAndWrongSeatPreservePosition() {
        ShogiGame game = game(false); var before = game.cells(); var info = game.publicInfo();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0,"move:a3:a5"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(1,"move:a7:a6"));
        assertEquals(before, game.cells()); assertEquals(info, game.publicInfo());
    }
    @Test void pinnedPieceCannotExposeItsKingAndKingCannotBeCaptured() throws Exception {
        ShogiGame game = position(false,"e1=8","e2=5","e9=-7","a9=-8");
        assertFalse(game.legalActions(0).contains("move:e2:d2"));
        assertFalse(game.legalActions(0).contains("move:e2:e9"));
        ShogiGame rook = position(false,"a1=8","e1=7","e9=-8");
        assertFalse(rook.legalActions(0).contains("move:e1:e9"));
    }
    @Test void rookAndBishopCannotJumpAndKnightCan() throws Exception {
        ShogiGame game = position(false,"a1=8","i9=-8","e4=7","e5=1","b2=6","c3=1","g2=3","g3=1");
        assertFalse(game.legalActions(0).contains("move:e4:e6"));
        assertFalse(game.legalActions(0).contains("move:b2:d4"));
        assertTrue(game.legalActions(0).contains("move:g2:f4"));
    }
    @Test void optionalPromotionRequiresExplicitChoiceAndCaptureUnpromotes() throws Exception {
        ShogiGame game = position(false,"a1=8","i9=-8","d6=7","d7=-12");
        game.apply(0,"move:d6:d7");
        assertEquals(List.of("promote:yes","promote:no"),game.legalActions(0));
        assertEquals("R",cell(game,"d6").piece());
        game.apply(0,"promote:yes");
        assertEquals("+R",cell(game,"d7").piece()); assertEquals(1,game.hand(0).get("S"));
        assertEquals(1,game.currentPlayer());
    }
    @Test void pawnLanceAndKnightMustPromoteAtDeadRanks() throws Exception {
        for (int piece : new int[]{1,2,3}) {
            String from = piece==3 ? "d7" : "d8", to = piece==3 ? "c9" : "d9";
            ShogiGame game = position(false,"a1=8","i9=-8",from+"="+piece);
            game.apply(0,"move:"+from+":"+to);
            assertTrue(cell(game,to).piece().startsWith("+")); assertEquals(1,game.currentPlayer());
        }
    }
    @Test void miniPromotionZoneIsOnlyTheLastRank() throws Exception {
        ShogiGame game = position(true,"a1=8","e5=-8","c3=4");
        game.apply(0,"move:c3:c4"); assertEquals(1,game.currentPlayer());
        assertEquals("S",cell(game,"c4").piece());
        ShogiGame last = position(true,"a1=8","e5=-8","c4=4");
        last.apply(0,"move:c4:c5"); assertEquals(List.of("promote:yes","promote:no"),last.legalActions(0));
    }
    @Test void dropsRejectDoublePawnsDeadRanksAndDoNotPromote() throws Exception {
        ShogiGame game = position(false,"a1=8","i9=-8","d4=1");
        int[][] hands=(int[][])field(game,"hands"); hands[0][1]=1; hands[0][2]=1; hands[0][3]=1; hands[0][4]=1;
        var actions=game.legalActions(0);
        assertFalse(actions.contains("drop:P:d6")); assertFalse(actions.contains("drop:P:c9"));
        assertFalse(actions.contains("drop:L:c9")); assertFalse(actions.contains("drop:N:c8"));
        assertTrue(actions.contains("drop:S:c9"));
        game.apply(0,"drop:S:c9"); assertEquals("S",cell(game,"c9").piece());
        assertThrows(UnsupportedOperationException.class, () -> game.hand(0).put("S",10));
    }
    @Test void dropCanBlockCheckAndPromotedPawnDoesNotCauseDoublePawn() throws Exception {
        ShogiGame game = position(false,"e1=8","a9=-8","e9=-7","d4=9");
        int[][] hands=(int[][])field(game,"hands"); hands[0][5]=1; hands[0][1]=1;
        assertTrue(game.inCheck(0)); assertTrue(game.legalActions(0).contains("drop:G:e2"));
        assertFalse(game.legalActions(0).contains("drop:P:d5"));
        game.apply(0,"drop:G:e2"); assertFalse(game.inCheck(0));
        set(game,"turn",0); assertTrue(game.legalActions(0).contains("drop:P:d5"));
    }
    @Test void pawnDropMateIsIllegalButOtherPieceDropMateIsLegal() throws Exception {
        ShogiGame game = position(false,"a1=8","e9=-8","d9=-2","f9=-2","d8=-1","f8=-1","e7=5");
        int[][] hands=(int[][])field(game,"hands"); hands[0][1]=1; hands[0][7]=1;
        assertFalse(game.legalActions(0).contains("drop:P:e8"));
        assertTrue(game.legalActions(0).contains("drop:R:e8"));
        game.apply(0,"drop:R:e8"); assertEquals("winner:0",game.outcome());
    }
    @Test void fourfoldRepetitionDrawsStandardButLosesSenteInMini() throws Exception {
        for (boolean mini : new boolean[]{false,true}) {
            ShogiGame game=position(mini,"a1=8",mini?"e5=-8":"i9=-8");
            String[] cycle=mini?new String[]{"move:a1:a2","move:e5:e4","move:a2:a1","move:e4:e5"}:
                    new String[]{"move:a1:a2","move:i9:i8","move:a2:a1","move:i8:i9"};
            for (int i=0;i<13&&!game.finished();i++) game.apply(game.currentPlayer(),cycle[i%4]);
            assertEquals(mini?"winner:1":"draw:repetition",game.outcome());
        }
    }
    @Test void continuousCheckingSideLosesEvenWhenItIsMiniGote() throws Exception {
        ShogiGame game=position(true,"a1=8","e5=-8","a4=-7");
        String[] cycle={"move:a1:b1","move:a4:b4","move:b1:a1","move:b4:a4"};
        for(int i=0;i<14&&!game.finished();i++) {
            game.apply(game.currentPlayer(),cycle[i%4]);
            if(game.legalActions(game.currentPlayer()).contains("promote:no"))game.apply(game.currentPlayer(),"promote:no");
        }
        assertEquals("winner:0",game.outcome());
    }
    @Test void impasseRequiresBothKingsInEnemyCampAndAgreement() throws Exception {
        ShogiGame game=position(false,"a9=8","i1=-8");
        int[][] hands=(int[][])field(game,"hands"); hands[0][7]=3; hands[0][6]=2; hands[1][7]=3; hands[1][6]=2;
        game.apply(0,"impasse:offer"); assertEquals(1,game.currentPlayer());
        assertFalse(game.finished()); game.apply(1,"impasse:accept"); assertEquals("draw:impasse",game.outcome());
        ShogiGame shortSide=position(false,"a9=8","i1=-8");
        int[][] shortHands=(int[][])field(shortSide,"hands"); shortHands[1][7]=5;
        shortSide.apply(0,"impasse:offer");shortSide.apply(1,"impasse:accept");assertEquals("winner:1",shortSide.outcome());
        assertFalse(game(true).legalActions(0).contains("impasse:offer"));
        assertFalse(game(false).legalActions(0).contains("impasse:offer"));
    }
    @Test void legalBotActionsReplayWithIdenticalState() {
        ShogiGame a=game(true), b=game(true); Random random=new Random(41);
        for(int i=0;i<100&&!a.finished();i++) {
            int seat=a.currentPlayer();String action=a.botAction(seat,random);
            assertNotNull(action); assertTrue(a.legalActions(seat).contains(action));
            a.apply(seat,action);b.apply(seat,action);assertEquals(a.cells(),b.cells());assertEquals(a.publicInfo(),b.publicInfo());
        }
    }
    @Test void illegalDropExplainsDoublePawnDeadRankPawnMateAndUnsafeKing() throws Exception {
        ShogiGame game=position(false,"e1=8","i9=-8","d4=1");
        int[][] hands=(int[][])field(game,"hands");hands[0][1]=1;hands[0][3]=1;
        assertEquals("error.shogi.double-pawn",assertThrows(RuleViolation.class,()->game.apply(0,"drop:P:d5")).message().key());
        assertEquals("error.shogi.dead-drop",assertThrows(RuleViolation.class,()->game.apply(0,"drop:N:c8")).message().key());
        ShogiGame checked=position(false,"e1=8","a9=-8","e9=-7");((int[][])field(checked,"hands"))[0][5]=1;
        assertEquals("error.shogi.king-unsafe",assertThrows(RuleViolation.class,()->checked.apply(0,"drop:G:d5")).message().key());
        ShogiGame mate=position(false,"a1=8","e9=-8","d9=-2","f9=-2","d8=-1","f8=-1","e7=5");((int[][])field(mate,"hands"))[0][1]=1;
        assertEquals("error.shogi.pawn-drop-mate",assertThrows(RuleViolation.class,()->mate.apply(0,"drop:P:e8")).message().key());
    }
    @Test void goldAndSilverMoveInTheCorrectDirectionForBothSeats() throws Exception {
        for(int piece:new int[]{4,5,9,10,11,12})for(int seat=0;seat<2;seat++){
            ShogiGame game=position(false,"a1=8","i9=-8","e5="+(seat==0?piece:-piece));set(game,"turn",seat);
            Set<String> expected=new HashSet<>();
            String[] targets=piece==4?(seat==0?new String[]{"d4","f4","d6","e6","f6"}:new String[]{"d6","f6","d4","e4","f4"}):
                    (seat==0?new String[]{"e4","d5","f5","d6","e6","f6"}:new String[]{"e6","d5","f5","d4","e4","f4"});
            for(String target:targets)expected.add("move:e5:"+target);
            assertEquals(expected,new HashSet<>(game.actionsForCell(seat,"e5")));
        }
    }
    @Test void promotedBishopAndRookGainOnlyOneStepInAdditionalDirections()throws Exception{
        ShogiGame bishop=position(false,"a1=8","i9=-8","e5=14");
        assertTrue(bishop.legalActions(0).contains("move:e5:e6"));assertFalse(bishop.legalActions(0).contains("move:e5:e7"));
        assertTrue(bishop.legalActions(0).contains("move:e5:g7"));
        ShogiGame rook=position(false,"a1=8","i9=-8","e5=15");
        assertTrue(rook.legalActions(0).contains("move:e5:f6"));assertFalse(rook.legalActions(0).contains("move:e5:g7"));
        assertTrue(rook.legalActions(0).contains("move:e5:e8"));
    }
    @Test void leavingPromotionZoneAlsoOffersChoiceAndDecliningPreservesPiece()throws Exception{
        ShogiGame game=position(false,"a1=8","i9=-8","d7=7");game.apply(0,"move:d7:d6");
        var cells=game.cells();var info=game.publicInfo();
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"drop:P:d5"));
        assertThrows(IllegalArgumentException.class,()->game.apply(1,"promote:yes"));
        assertEquals(cells,game.cells());assertEquals(info,game.publicInfo());
        game.apply(0,"promote:no");assertEquals("R",cell(game,"d6").piece());
    }
    @Test void miniPawnMustPromoteAndCapturedPromotedRookBecomesRookInHand()throws Exception{
        ShogiGame pawn=position(true,"a1=8","e5=-8","b4=1");pawn.apply(0,"move:b4:b5");assertEquals("+P",cell(pawn,"b5").piece());
        ShogiGame capture=position(true,"a1=8","e5=-8","c2=7","c3=-15");capture.apply(0,"move:c2:c3");
        assertEquals(Map.of("R",1),capture.hand(0));
    }
    @Test void impasseRefusalResumesOriginalTurnAndCannotBeForcedByWrongSeat()throws Exception{
        ShogiGame game=position(false,"a9=8","i1=-8");game.apply(0,"impasse:offer");
        var before=game.publicInfo();assertThrows(IllegalArgumentException.class,()->game.apply(0,"impasse:accept"));assertEquals(before,game.publicInfo());
        game.apply(1,"impasse:reject");assertEquals(0,game.currentPlayer());assertFalse(game.finished());
        ShogiGame unsafe=position(false,"a9=8","i1=-8","a1=-7");assertFalse(unsafe.legalActions(0).contains("impasse:offer"));
    }
    @Test void standardKnightMustPromoteOnPenultimateRankAndGoteMovesDownward()throws Exception{
        ShogiGame knight=position(false,"a1=8","i9=-8","d6=3");knight.apply(0,"move:d6:c8");assertEquals("+N",cell(knight,"c8").piece());
        ShogiGame gote=position(false,"a1=8","i9=-8","d2=-1");set(gote,"turn",1);gote.apply(1,"move:d2:d1");assertEquals("+P",cell(gote,"d1").piece());
    }
    @Test void botsDoNotMutatePositionWhileSearching() {
        for(boolean mini:new boolean[]{false,true}){
            ShogiGame game=game(mini);var cells=game.cells();var info=game.publicInfo();
            assertNotNull(game.botAction(0,new Random(7)));assertEquals(cells,game.cells());assertEquals(info,game.publicInfo());
            assertNull(game.botAction(1,new Random(7)));
        }
    }
    @Test void repeatedBoardWithChangingHandsIsNotARepeatedCompletePosition()throws Exception{
        ShogiGame game=position(false,"a1=8","i9=-8","d1=7","d5=-1");
        ((int[][])field(game,"hands"))[1][1]=4;
        for(int cycle=0;cycle<4;cycle++){
            game.apply(0,"move:d1:d5");
            game.apply(1,cycle%2==0?"move:i9:i8":"move:i8:i9");
            game.apply(0,"move:d5:d1");game.apply(1,"drop:P:d5");
            assertFalse(game.finished());
        }
        assertEquals(4,game.hand(0).get("P"));assertTrue(game.hand(1).isEmpty());
    }
}

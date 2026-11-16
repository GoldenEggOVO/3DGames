package GoldenEggOVO.TDGames.Singleplayer.game.klondike;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.cards.PlayingCard;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KlondikeGameTest {
    @Test void dealHasSevenVisibleCardsAndNoHiddenFacesOrIds() {
        var game = new KlondikeGame(1, 7, Map.of());
        assertEquals("24", game.publicInfo().get("stock"));
        assertEquals(21, game.cells().stream().filter(c -> c.id().startsWith("t") && c.piece().equals("back")).count());
        assertEquals(7, game.cells().stream().filter(c -> c.id().startsWith("t") && !c.piece().isEmpty() && !c.piece().equals("back")).count());
        assertEquals(game.cells(), game.cellsFor(-1));
        assertTrue(game.cells().stream().noneMatch(c -> c.id().contains(":")));
    }

    @Test void drawingThreeRecyclesOriginalOrderWithoutShuffling() {
        var game = new KlondikeGame(1, 15, Map.of("draw", "3"));
        List<String> firstPass = new ArrayList<>();
        while (game.legalActions(0).contains("draw")) {
            game.apply(0, "draw");
            firstPass.add(game.cells().stream().filter(c -> c.id().equals("w")).map(Cell::piece).findFirst().orElseThrow());
        }
        assertEquals(8, firstPass.size());
        game.apply(0, "recycle");
        for (String face : firstPass) {
            game.apply(0, "draw");
            assertEquals(face, game.cells().stream().filter(c -> c.id().equals("w")).map(Cell::piece).findFirst().orElseThrow());
        }
        assertEquals("1", game.publicInfo().get("recycles"));
    }

    @Test void legalMovesAndDrawsReplayAndRejectUnknownSourceAtomically() {
        var game = new KlondikeGame(1, 22, Map.of());
        var replay = new KlondikeGame(1, 22, Map.of());
        var random = new Random(3);
        for (int step = 0; step < 250; step++) {
            var before = game.cells();
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:t0_99:f0"));
            assertThrows(IllegalArgumentException.class, () -> game.apply(1, "draw"));
            assertEquals(before, game.cells());
            var actions = game.legalActions(0);
            if (actions.isEmpty()) break;
            String action = actions.get(random.nextInt(actions.size()));
            game.apply(0, action); replay.apply(0, action);
            assertEquals(game.cells(), replay.cells());
            assertEquals(game.publicInfo(), replay.publicInfo());
        }
        assertFalse(game.finished());
    }

    @SuppressWarnings("unchecked")
    private <T> T field(KlondikeGame game, String name) {
        try {
            var field = KlondikeGame.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(game);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private PlayingCard card(String suit, int rank) { return new PlayingCard(suit + rank, suit, rank); }

    private KlondikeGame empty(int draw) {
        var game = new KlondikeGame(1, 4, Map.of("draw", Integer.toString(draw)));
        this.<List<PlayingCard>>field(game, "stock").clear();
        this.<List<PlayingCard>>field(game, "waste").clear();
        this.<List<List<PlayingCard>>>field(game, "columns").forEach(List::clear);
        Arrays.fill(this.<int[]>field(game, "hidden"), 0);
        return game;
    }

    @Test void completeAlternateSequenceCanMoveButNonKingCannotEnterEmptyColumn() {
        var game = empty(1);
        List<List<PlayingCard>> columns = field(game, "columns");
        columns.get(0).addAll(List.of(card("spades", 13), card("hearts", 12), card("clubs", 11)));
        assertTrue(game.actionsForCell(0, "t0_0").contains("move:t0_0:t1"));
        assertFalse(game.legalActions(0).contains("move:t0_1:t1"));
        var before = game.cells();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:t0_1:t1"));
        assertEquals(before, game.cells());
        game.apply(0, "move:t0_0:t1");
        assertTrue(game.columns().get(0).isEmpty());
        assertEquals(List.of("spades_king", "hearts_queen", "clubs_jack"), game.columns().get(1).stream().map(KlondikeGame.VisibleCard::face).toList());
    }

    @Test void movingTopCardFlipsHiddenCardAndRejectsHiddenOrBrokenSequences() {
        var game = empty(1);
        List<List<PlayingCard>> columns = field(game, "columns");
        columns.get(0).addAll(List.of(card("clubs", 5), card("hearts", 4)));
        this.<int[]>field(game, "hidden")[0] = 1;
        columns.get(1).add(card("spades", 5));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:t0_0:t2"));
        game.apply(0, "move:t0_1:t1");
        assertTrue(game.columns().get(0).getFirst().faceUp());
        assertEquals("clubs_5", game.columns().get(0).getFirst().face());
        columns.get(2).addAll(List.of(card("diamonds", 13), card("hearts", 12)));
        assertFalse(game.legalActions(0).contains("move:t2_0:t3"));
    }

    @Test void foundationAcceptsOnlyNextSuitRankAndAllowsTopBackToTableau() {
        var game = empty(1);
        List<List<PlayingCard>> columns = field(game, "columns");
        columns.get(0).add(card("spades", 14));
        columns.get(1).add(card("hearts", 2));
        assertFalse(game.legalActions(0).contains("move:t0_0:f1"));
        game.apply(0, "move:t0_0:f0");
        assertTrue(game.legalActions(0).contains("move:f0:t1"));
        game.apply(0, "move:f0:t1");
        assertEquals(List.of("hearts_2", "spades_ace"), game.columns().get(1).stream().map(KlondikeGame.VisibleCard::face).toList());
        assertEquals("0", game.publicInfo().get("foundations"));
    }

    @Test void partialLastDrawAndRecyclingAfterWasteRemovalPreserveOrder() {
        var game = empty(3);
        this.<List<PlayingCard>>field(game, "stock").addAll(List.of(card("spades", 3), card("spades", 2),
                card("spades", 14), card("hearts", 5), card("clubs", 6)));
        game.apply(0, "draw");
        assertEquals(List.of("clubs_6", "hearts_5", "spades_ace"), game.wasteCards().stream().map(KlondikeGame.VisibleCard::face).toList());
        game.apply(0, "move:w:f0");
        game.apply(0, "draw");
        assertEquals("0", game.publicInfo().get("stock"));
        assertFalse(game.legalActions(0).contains("move:w:f0"));
        game.apply(0, "recycle");
        game.apply(0, "draw");
        assertEquals("spades_2", game.wasteCards().getLast().face());
        game.apply(0, "move:w:f0");
        game.apply(0, "draw");
        assertEquals("spades_3", game.wasteCards().getLast().face());
        game.apply(0, "move:w:f0");
        assertEquals("3", game.publicInfo().get("foundations"));
    }

    @Test void finalKingsCompleteAllFiftyTwoCardsAndViewsCannotMutateState() {
        var game = empty(1);
        List<List<PlayingCard>> foundations = field(game, "foundations"), columns = field(game, "columns");
        for (int suit = 0; suit < 4; suit++) {
            foundations.get(suit).add(card(PlayingCard.SUITS.get(suit), 14));
            for (int rank = 2; rank <= 12; rank++) foundations.get(suit).add(card(PlayingCard.SUITS.get(suit), rank));
            columns.get(suit).add(card(PlayingCard.SUITS.get(suit), 13));
        }
        for (int suit = 0; suit < 4; suit++) game.apply(0, "move:t" + suit + "_0:f" + suit);
        assertEquals("winner:0", game.outcome());
        assertTrue(game.legalActions(0).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> game.columns().clear());
        assertThrows(UnsupportedOperationException.class, () -> game.columns().get(0).clear());
    }

    @Test void partialDrawThreeExposesCardsButOnlyWasteTopCanMove() {
        var game = empty(3);
        this.<List<PlayingCard>>field(game, "stock").addAll(List.of(card("hearts", 2), card("spades", 14)));
        this.<List<List<PlayingCard>>>field(game, "columns").get(0).add(card("clubs", 3));
        game.apply(0, "draw");
        assertEquals(List.of("spades_ace", "hearts_2"), game.wasteCards().stream().map(KlondikeGame.VisibleCard::face).toList());
        assertFalse(game.legalActions(0).contains("move:w:f0"));
        assertTrue(game.legalActions(0).contains("move:w:t0"));
        assertTrue(game.actionsForCell(0, "w0").isEmpty());
        game.apply(0, "move:w:t0");
        assertTrue(game.legalActions(0).contains("move:w:f0"));
        game.apply(0, "move:w:f0");
        assertTrue(game.wasteCards().isEmpty());
        assertFalse(game.legalActions(0).contains("recycle"));
    }
}

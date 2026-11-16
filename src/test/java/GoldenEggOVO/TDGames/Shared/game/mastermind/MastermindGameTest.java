package GoldenEggOVO.TDGames.Shared.game.mastermind;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MastermindGameTest {
    @Test void cyclingAnAimedSlotIsOneReplayableActionAndKeepsTheDraftPrivate() {
        var game = new MastermindGame(2, 7, Map.of());
        var equivalent = new MastermindGame(2, 7, Map.of());
        for (String step : List.of("previous-color:2", "next-color:2", "next-color:2", "next-color:0")) {
            assertTrue(game.legalActions(0).contains(step));
            game.apply(0, step);
        }
        equivalent.apply(0, "slot:2");
        equivalent.apply(0, "color:1");
        equivalent.apply(0, "slot:0");
        equivalent.apply(0, "color:0");
        assertEquals(equivalent.cellsFor(0), game.cellsFor(0));
        assertEquals(equivalent.infoFor(0), game.infoFor(0));
        assertEquals(game.cellsFor(-1), game.cellsFor(1));
        assertTrue(game.controlActions(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "next-color:1"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "previous-color:4"));
    }

    private void enter(MastermindGame game, int seat, int... colors) {
        for (int i = 0; i < colors.length; i++) {
            game.apply(seat, "slot:" + i);
            game.apply(seat, "color:" + colors[i]);
        }
        game.apply(seat, "submit");
    }

    @Test void repeatedColorsAreCountedOnceAndDraftIsPrivate() {
        var game = new MastermindGame(2, 1, Map.of());
        enter(game, 0, 0, 0, 1, 1);
        game.apply(1, "color:4");
        assertEquals(game.cells(), game.cellsFor(-1));
        assertNotEquals(game.cells(), game.cellsFor(1));
        assertTrue(game.cellsFor(1).stream().noneMatch(c -> c.id().startsWith("secret") && !c.piece().equals("back")));
        enter(game, 1, 0, 1, 0, 0);
        var feedback = game.guesses().getFirst();
        assertEquals(1, feedback.exact());
        assertEquals(2, feedback.misplaced());
        assertFalse(game.publicInfo().containsKey("secret"));
    }

    @Test void twoRoundsSwapRolesAndCompareSetterScores() {
        var game = new MastermindGame(2, 2, Map.of());
        enter(game, 0, 1, 1, 1, 1);
        enter(game, 1, 1, 1, 1, 1);
        assertEquals(1, game.currentPlayer());
        enter(game, 1, 2, 2, 2, 2);
        enter(game, 0, 0, 0, 0, 0);
        enter(game, 0, 2, 2, 2, 2);
        assertTrue(game.finished());
        assertEquals("winner:1", game.outcome());
    }

    @Test void invalidAndOutOfTurnActionsPreserveState() {
        var game = new MastermindGame(2, 3, Map.of());
        var before = game.cellsFor(0);
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "color:0"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "submit"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "slot:4"));
        assertEquals(before, game.cellsFor(0));
        assertTrue(game.legalActions(0).size() < 20);
    }

    @Test void publicFeedbackBotSolvesSoloAndReplaysDeterministically() {
        for (int seed = 0; seed < 20; seed++) {
            var game = new MastermindGame(1, seed, Map.of());
            var replay = new MastermindGame(1, seed, Map.of());
            var random = new Random(seed);
            for (int step = 0; !game.finished() && step < 200; step++) {
                String action = game.botAction(0, random);
                assertNotNull(action);
                game.apply(0, action);
                replay.apply(0, action);
                assertEquals(game.cellsFor(0), replay.cellsFor(0));
                assertEquals(game.publicInfo(), replay.publicInfo());
            }
            assertEquals("winner:0", game.outcome());
        }
    }

    @Test void tenthGuessSuccessAndFailureHaveDifferentSetterScores() {
        var game = new MastermindGame(2, 3, Map.of());
        enter(game, 0, 0, 0, 0, 0);
        for (int i = 0; i < 9; i++) enter(game, 1, 1, 1, 1, 1);
        enter(game, 1, 0, 0, 0, 0);
        assertEquals(10, game.scores().getFirst());
        enter(game, 1, 2, 2, 2, 2);
        for (int i = 0; i < 10; i++) enter(game, 0, 3, 3, 3, 3);
        assertEquals(List.of(10, 11), game.scores());
        assertEquals("winner:1", game.outcome());
        assertTrue(game.legalActions(0).isEmpty());
    }

    @Test void botChoicesDependOnPublicFeedbackInsteadOfSecret() {
        var a = new MastermindGame(2, 8, Map.of());
        var b = new MastermindGame(2, 9, Map.of());
        enter(a, 0, 0, 0, 1, 1);
        enter(b, 0, 0, 0, 2, 2);
        enter(a, 1, 0, 0, 0, 0);
        enter(b, 1, 0, 0, 0, 0);
        assertEquals(a.cellsFor(1), b.cellsFor(1));
        assertEquals(a.publicInfo(), b.publicInfo());
        assertEquals(a.botAction(1, new Random(1)), b.botAction(1, new Random(1)));
        assertEquals(a.legalActions(1), b.legalActions(1));
    }

    @Test void duplicateColorFeedbackHasNoPositionCorrespondenceAndViewsAreImmutable() {
        var game = new MastermindGame(2, 1, Map.of());
        enter(game, 0, 0, 0, 1, 1);
        enter(game, 1, 1, 1, 0, 0);
        assertEquals(0, game.guesses().getFirst().exact());
        assertEquals(4, game.guesses().getFirst().misplaced());
        assertThrows(UnsupportedOperationException.class, () -> game.guesses().getFirst().colors().set(0, 5));
        assertThrows(UnsupportedOperationException.class, () -> game.cellsFor(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> game.infoFor(1).put("secret", "invalid"));
    }

    @Test void soloTenthFailedGuessRevealsOnlyTheFinishedAnswerAndAcceptsNoMoreGuesses() {
        var probe = new MastermindGame(1, 17, Map.of());
        enter(probe, 0, 0, 0, 0, 0);
        int wrongColor = probe.finished() ? 1 : 0;
        var game = new MastermindGame(1, 17, Map.of());
        for (int attempt = 0; attempt < 10; attempt++) enter(game, 0, wrongColor, wrongColor, wrongColor, wrongColor);
        assertEquals("draw:unsolved", game.outcome());
        assertEquals(10, game.guesses().size());
        assertTrue(game.cellsFor(0).stream().filter(c -> c.id().startsWith("secret")).allMatch(c -> c.piece().startsWith("color_")));
        assertEquals(game.cellsFor(0), game.cellsFor(-1));
        var before = game.guesses();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "color:1"));
        assertEquals(before, game.guesses());
    }
}

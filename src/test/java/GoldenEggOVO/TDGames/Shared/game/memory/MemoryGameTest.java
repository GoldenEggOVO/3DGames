package GoldenEggOVO.TDGames.Shared.game.memory;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryGameTest {
    @Test void hiddenCardsNeverContainTheirFaceAndMismatchWaitsForExplicitHide() {
        var game = new MemoryGame(2, 4, Map.of());
        assertTrue(game.cells().stream().allMatch(c -> c.piece().equals("back")));
        assertEquals(game.cells(), game.cellsFor(-1));
        game.apply(0, "flip:m0");
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "flip:m0"));
        int second = 1;
        game.apply(0, "flip:m" + second);
        if (game.automaticAction() != null) {
            assertEquals("hide", game.automaticAction());
            assertEquals(1200, game.automaticDelayMillis());
            assertTrue(game.legalActions(0).isEmpty());
            var shown = game.cells();
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, "flip:m2"));
            assertEquals(shown, game.cells());
            game.apply(game.currentPlayer(), "hide");
            assertEquals(1, game.currentPlayer());
            assertTrue(game.cells().stream().allMatch(c -> c.piece().equals("back")));
        } else assertEquals(1, game.scores().get(0));
    }

    @Test void botOnlyUsesExposedMemoryAndFullGamesReplay() {
        for (int players = 1; players <= 4; players++) {
            var game = new MemoryGame(players, players, Map.of());
            var replay = new MemoryGame(players, players, Map.of());
            var random = new Random(12);
            for (int step = 0; !game.finished() && step < 1000; step++) {
                int actor = game.currentPlayer();
                String action = game.automaticAction();
                if (action == null) action = game.botAction(actor, random);
                assertNotNull(action);
                game.apply(actor, action);
                replay.apply(actor, action);
                assertEquals(game.cells(), replay.cells());
                assertEquals(game.publicInfo(), replay.publicInfo());
            }
            assertTrue(game.finished());
            assertEquals(18, game.scores().stream().mapToInt(Integer::intValue).sum());
        }
    }

    @Test void firstFlipDoesNotExposeAnyOtherCardOrAllowAnotherActor() {
        var game = new MemoryGame(4, 91, Map.of());
        game.apply(0, "flip:m35");
        assertEquals(35, game.cells().stream().filter(c -> c.piece().equals("back")).count());
        var before = game.cells();
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "flip:m0"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "flip:m36"));
        assertEquals(before, game.cells());
    }

    private Map<String, List<Integer>> observedPairs(long seed) {
        Map<String, List<Integer>> pairs = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            var probe = new MemoryGame(1, seed, Map.of());
            probe.apply(0, "flip:m" + i);
            pairs.computeIfAbsent(probe.cells().get(i).piece(), ignored -> new ArrayList<>()).add(i);
        }
        return pairs;
    }

    @Test void matchingPairKeepsTurnAndEqualScoresProduceMultipleWinners() {
        long seed = 20;
        var game = new MemoryGame(2, seed, Map.of());
        List<List<Integer>> pairs = new ArrayList<>(observedPairs(seed).values());
        for (int i = 0; i < pairs.size(); i++) {
            if (i == 9) {
                game.apply(0, "flip:m" + pairs.get(9).get(0));
                game.apply(0, "flip:m" + pairs.get(10).get(0));
                assertEquals("hide", game.automaticAction());
                game.apply(0, "hide");
            }
            int seat = i < 9 ? 0 : 1;
            game.apply(seat, "flip:m" + pairs.get(i).get(0));
            game.apply(seat, "flip:m" + pairs.get(i).get(1));
            assertEquals(seat, game.currentPlayer());
            assertNull(game.automaticAction());
        }
        assertEquals(List.of(9, 9), game.scores());
        assertEquals("winners:0,1", game.outcome());
        assertTrue(game.cells().stream().allMatch(c -> c.piece().isEmpty()));
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "flip:m0"));
    }

    @Test void mismatchSurvivesReplayAndCannotBeHiddenBySpectator() {
        long seed = 31;
        var pairs = new ArrayList<>(observedPairs(seed).values());
        var a = new MemoryGame(2, seed, Map.of());
        var b = new MemoryGame(2, seed, Map.of());
        for (int index : new int[] {pairs.get(0).getFirst(), pairs.get(1).getFirst()}) {
            a.apply(0, "flip:m" + index); b.apply(0, "flip:m" + index);
        }
        assertEquals(a.cells(), b.cells());
        assertEquals("hide", b.automaticAction());
        assertThrows(IllegalArgumentException.class, () -> b.apply(-1, "hide"));
        a.apply(0, "hide"); b.apply(0, "hide");
        assertEquals(a.cells(), b.cells());
        assertEquals(a.botAction(1, new Random(0)), b.botAction(1, new Random(0)));
    }

    @Test void initialBotActionCannotDependOnUnrevealedFaces() {
        var a = new MemoryGame(2, 0, Map.of());
        var b = new MemoryGame(2, 1, Map.of());
        assertEquals(a.botAction(0, new Random(22)), b.botAction(0, new Random(22)));
        assertThrows(UnsupportedOperationException.class, () -> a.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.scores().set(0, 10));
    }
}

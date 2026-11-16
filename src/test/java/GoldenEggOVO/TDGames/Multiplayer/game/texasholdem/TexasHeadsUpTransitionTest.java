package GoldenEggOVO.TDGames.Multiplayer.game.texasholdem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TexasHeadsUpTransitionTest {
    @Test
    void eliminatingTheThreeHandedButtonDoesNotChargeTheBigBlindTwice() {
        assertHeadsUpAfterElimination(new int[] {10, 100, 100}, new int[] {0}, 1, 2);
    }

    @Test
    void eliminatingTheSmallBlindAdvancesTheBigBlindToTheFormerButton() {
        assertHeadsUpAfterElimination(new int[] {100, 5, 100}, new int[] {1}, 0, 2);
    }

    @Test
    void eliminatingTheBigBlindStillAdvancesFromItsFormerSeat() {
        assertHeadsUpAfterElimination(new int[] {100, 100, 10}, new int[] {2}, 0, 1);
    }

    @Test
    void simultaneousEliminationsFromFourPlayersKeepTheNextBigBlindInOrder() {
        assertHeadsUpAfterElimination(new int[] {10, 10, 10, 100}, new int[] {1, 2}, 3, 0);
    }

    private static void assertHeadsUpAfterElimination(
            int[] stacks, int[] eliminated, int expectedBigBlind, int expectedButton) {
        for (long seed = 0; seed < 100; seed++) {
            var game = new TexasHoldemGame(stacks.length, seed, stacks);
            while (!game.publicInfo().get("phase").equals("showdown")) {
                int seat = game.currentPlayer();
                game.apply(seat, game.controls(seat).contains("check") ? "check" : "call");
            }
            boolean matches = true;
            for (int seat = 0; seat < stacks.length; seat++) {
                boolean shouldBeEliminated = false;
                for (int loser : eliminated) shouldBeEliminated |= seat == loser;
                matches &= game.publicInfo().get("chips." + seat).equals("0") == shouldBeEliminated;
            }
            if (!matches) continue;

            game.apply(game.currentPlayer(), "continue");

            assertEquals(Integer.toString(expectedBigBlind), game.publicInfo().get("bigBlind"), "seed " + seed);
            assertEquals(Integer.toString(expectedButton), game.publicInfo().get("smallBlind"));
            assertEquals(Integer.toString(expectedButton), game.publicInfo().get("dealer"));
            assertEquals(expectedButton, game.currentPlayer());
            return;
        }
        fail("The seeded hands must include the required eliminations");
    }
}

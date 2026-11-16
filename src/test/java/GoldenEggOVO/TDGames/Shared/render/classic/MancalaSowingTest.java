package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Multiplayer.game.mancala.MancalaGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MancalaSowingTest {
    @Test void eachSeedLeavesTheChosenPitBeforeLandingInTheNextWell() {
        var before = cells(4,4,4,4,4,4,0,4,4,4,4,4,4,0);
        var after = cells(4,4,0,5,5,5,1,4,4,4,4,4,4,0);

        var steps = MancalaSowing.plan(before, after, "sow:2");

        assertEquals(List.of("3", "4", "5", "6"), steps.stream().map(MancalaSowing.Step::toId).toList());
        assertEquals(before, steps.getFirst().before());
        assertEquals(after, steps.getLast().after());
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            assertEquals("2", step.fromId());
            assertEquals(1, step.seeds());
            assertEquals(4 - i, count(step.before(), "2"));
            assertEquals(3 - i, count(step.after(), "2"));
            if (i > 0) assertEquals(steps.get(i - 1).after(), step.before());
        }
    }

    @Test void multipleLapsSkipTheOpponentsStoreAndCanReturnToTheSource() {
        var before = cells(27,1,1,1,1,1,0,1,1,1,1,1,11,0);
        var after = cells(2,4,3,3,3,3,2,3,3,3,3,3,13,0);

        var steps = MancalaSowing.plan(before, after, "sow:0");

        assertEquals(27, steps.size());
        assertTrue(steps.stream().noneMatch(step -> step.toId().equals("13")));
        assertEquals(List.of("0", "0"), steps.stream().filter(step -> step.toId().equals("0"))
                .map(MancalaSowing.Step::fromId).toList());
        var returnsToSource = steps.get(12);
        assertEquals(returnsToSource.before(), returnsToSource.after());
        assertEquals(after, steps.getLast().after());
    }

    @Test void secondSeatSowsThroughItsStoreAndSkipsTheFirstSeatsStore() {
        var before = cells(1,1,1,1,1,11,0,27,1,1,1,1,1,0);
        var after = cells(3,3,3,3,3,13,0,2,4,3,3,3,3,2);

        var steps = MancalaSowing.plan(before, after, "sow:7");

        assertEquals(27, steps.size());
        assertTrue(steps.stream().noneMatch(step -> step.toId().equals("6")));
        assertEquals(2, steps.stream().filter(step -> step.toId().equals("13")).count());
        assertEquals(after, steps.getLast().after());
    }

    @Test void captureMovesTheLandingSeedAndOppositeSeedsIntoTheOwnersStore() {
        var before = cells(1,0,1,0,0,0,10,1,1,1,1,5,1,26);
        var after = cells(0,0,1,0,0,0,16,1,1,1,1,0,1,26);

        var steps = MancalaSowing.plan(before, after, "sow:0");

        assertEquals(3, steps.size());
        assertEquals(List.of("0", "1", "11"), steps.stream().map(MancalaSowing.Step::fromId).toList());
        assertEquals(List.of("1", "6", "6"), steps.stream().map(MancalaSowing.Step::toId).toList());
        assertEquals(List.of(1, 1, 5), steps.stream().map(MancalaSowing.Step::seeds).toList());
        assertEquals(after, steps.getLast().after());
    }

    @Test void endingTheRoundCollectsEachRemainingPitIntoItsOwnStore() {
        var before = cells(0,0,0,0,0,1,23,4,4,4,4,4,4,0);
        var after = cells(0,0,0,0,0,0,24,0,0,0,0,0,0,24);

        var steps = MancalaSowing.plan(before, after, "sow:5");

        assertEquals(7, steps.size());
        assertEquals(List.of("5", "7", "8", "9", "10", "11", "12"),
                steps.stream().map(MancalaSowing.Step::fromId).toList());
        for (var step : steps.subList(1, steps.size())) {
            assertEquals("13", step.toId());
            assertEquals(4, step.seeds());
            assertEquals(4, count(step.before(), step.fromId()));
            assertEquals(0, count(step.after(), step.fromId()));
        }
        assertEquals(after, steps.getLast().after());
    }

    @Test void aCaptureCanEmptyTheMovingSideAndThenCollectTheOtherSide() {
        var before = cells(1,0,0,0,0,0,10,1,1,1,1,5,1,27);
        var after = cells(0,0,0,0,0,0,16,0,0,0,0,0,0,32);

        var steps = MancalaSowing.plan(before, after, "sow:0");

        assertEquals(8, steps.size());
        assertEquals("11", steps.get(2).fromId());
        assertEquals("6", steps.get(2).toId());
        assertEquals("7", steps.get(3).fromId());
        assertEquals("13", steps.get(3).toId());
        assertEquals(after, steps.getLast().after());
    }

    @Test void mismatchedRuleResultsAndOverlappingTransitionsHaveNoAnimation() {
        var before = cells(4,4,4,4,4,4,0,4,4,4,4,4,4,0);
        var after = cells(4,4,0,5,5,5,1,4,4,4,4,4,4,0);
        var incorrect = cells(4,4,0,5,5,5,0,4,4,4,4,4,4,1);
        var steps = MancalaSowing.plan(before, after, "sow:2");

        assertFalse(steps.isEmpty());
        assertTrue(MancalaSowing.plan(before, incorrect, "sow:2").isEmpty());
        assertTrue(MancalaSowing.plan(after, after, "sow:2").isEmpty());
        assertTrue(MancalaSowing.plan(steps.getFirst().after(), after, "sow:2").isEmpty());
        assertEquals(steps, MancalaSowing.plan(before, after, "sow:2"));
    }

    @Test void aMissingCaptureCannotBePresentedAsTheConfirmedResult() {
        var before = cells(1,0,1,0,0,0,10,1,1,1,1,5,1,26);
        var missingCapture = cells(0,1,1,0,0,0,10,1,1,1,1,5,1,26);

        assertTrue(MancalaSowing.plan(before, missingCapture, "sow:0").isEmpty());
    }

    @Test void invalidCellsAndActionsAreRejectedWithoutChangingEitherInput() {
        var before = new ArrayList<>(cells(4,4,4,4,4,4,0,4,4,4,4,4,4,0));
        var after = new ArrayList<>(cells(4,4,0,5,5,5,1,4,4,4,4,4,4,0));
        var beforeCopy = List.copyOf(before);
        var afterCopy = List.copyOf(after);
        for (String action : new String[] {null, "", "move:2", "sow:-1", "sow:6", "sow:13", "sow:14", "sow:+2", "sow:02", "sow:2:3"})
            assertTrue(MancalaSowing.plan(before, after, action).isEmpty(), String.valueOf(action));
        assertTrue(MancalaSowing.plan(null, after, "sow:2").isEmpty());
        assertTrue(MancalaSowing.plan(before, null, "sow:2").isEmpty());
        assertTrue(MancalaSowing.plan(before.subList(0, 13), after, "sow:2").isEmpty());
        var malformed = new ArrayList<>(before);
        malformed.set(2, new Cell("2", 2, 1, "-4", 0));
        assertTrue(MancalaSowing.plan(malformed, after, "sow:2").isEmpty());
        malformed.set(2, before.get(1));
        assertTrue(MancalaSowing.plan(malformed, after, "sow:2").isEmpty());
        malformed.set(2, null);
        assertTrue(MancalaSowing.plan(malformed, after, "sow:2").isEmpty());
        var wrongMetadata = new ArrayList<>(after);
        wrongMetadata.set(2, new Cell("2", 20, 1, "0", 0));
        assertTrue(MancalaSowing.plan(before, wrongMetadata, "sow:2").isEmpty());
        assertEquals(beforeCopy, before);
        assertEquals(afterCopy, after);
    }

    @Test void snapshotsAreIndependentAndRemainImmutableAfterInputsChange() {
        var before = new ArrayList<>(cells(4,4,4,4,4,4,0,4,4,4,4,4,4,0));
        var after = new ArrayList<>(cells(4,4,0,5,5,5,1,4,4,4,4,4,4,0));
        var steps = MancalaSowing.plan(before, after, "sow:2");
        assertFalse(steps.isEmpty());
        before.clear();
        after.clear();

        assertEquals(4, count(steps.getFirst().before(), "2"));
        assertEquals(0, count(steps.getLast().after(), "2"));
        assertThrows(UnsupportedOperationException.class, () -> steps.clear());
        assertThrows(UnsupportedOperationException.class, () -> steps.getFirst().before().clear());
        assertThrows(UnsupportedOperationException.class, () -> steps.getFirst().after().clear());
    }

    @Test void everyConfirmedMoveInACompleteRuleReplayHasABoundedContinuousPlan() {
        var game = new MancalaGame(2, 9, Map.of());
        var random = new Random(1);
        for (int move = 0; move < 500 && !game.finished(); move++) {
            var before = game.cells();
            int seat = game.currentPlayer();
            String action = game.botAction(seat, random);
            game.apply(seat, action);
            var after = game.cells();

            var steps = MancalaSowing.plan(before, after, action);

            assertFalse(steps.isEmpty(), action + " from " + before);
            assertTrue(steps.size() <= 50);
            assertEquals(before, steps.getFirst().before());
            assertEquals(after, steps.getLast().after());
            assertEquals(after, game.cells());
            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i);
                assertTrue(count(step.before(), step.fromId()) >= step.seeds());
                assertEquals(48, step.before().stream().mapToInt(cell -> Integer.parseInt(cell.piece())).sum());
                assertEquals(48, step.after().stream().mapToInt(cell -> Integer.parseInt(cell.piece())).sum());
                if (i > 0) assertEquals(steps.get(i - 1).after(), step.before());
            }
        }
        assertTrue(game.finished());
    }

    private static int count(List<Cell> cells, String id) {
        return Integer.parseInt(cells.get(Integer.parseInt(id)).piece());
    }

    private static List<Cell> cells(int... seeds) {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < seeds.length; i++) cells.add(new Cell(Integer.toString(i),
                i == 6 ? 6 : i == 13 ? -1 : i < 6 ? i : 12 - i, i < 7 ? 1 : 0,
                Integer.toString(seeds[i]), i < 7 ? 0 : 1));
        return List.copyOf(cells);
    }
}

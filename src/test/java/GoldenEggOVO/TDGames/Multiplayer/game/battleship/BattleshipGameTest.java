package GoldenEggOVO.TDGames.Multiplayer.game.battleship;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleshipGameTest {
    @Test void simultaneousSetupIsPrivateAndConfirmLocksFleet() {
        var game = new BattleshipGame(2, 1, Map.of());
        game.apply(1, "auto");
        assertEquals(17, game.cellsFor(1).stream().filter(c -> c.piece().startsWith("ship_")).count());
        assertTrue(game.cells().stream().noneMatch(c -> c.piece().startsWith("ship_")));
        assertEquals(game.cells(), game.cellsFor(-1));
        assertEquals(game.cells(), game.cellsFor(0));
        game.apply(1, "confirm");
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "auto"));
        game.apply(0, "auto");
        game.apply(0, "confirm");
        game.apply(0, "shoot:b1_0");
        assertEquals(1, game.currentPlayer());
        var before = game.cells();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "shoot:b1_1"));
        assertEquals(before, game.cells());
    }

    @Test void touchingShipsAreLegalAndSunkSeparately() {
        var game = new BattleshipGame(2, 3, Map.of());
        for (int seat = 0; seat < 2; seat++) {
            for (int ship = 0; ship < 5; ship++) {
                game.apply(seat, "ship:" + ship);
                game.apply(seat, "place:b" + seat + "_" + ship * 10);
            }
            game.apply(seat, "confirm");
        }
        for (int x = 0; x < 5; x++) {
            game.apply(0, "shoot:b1_" + x);
            game.apply(1, "shoot:b0_" + (90 + x));
        }
        assertEquals("4", game.publicInfo().get("remaining_1"));
        assertEquals(5, game.cells().stream().filter(c -> c.id().startsWith("b1_") && c.piece().equals("sunk")).count());
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "shoot:b1_0"));
    }

    @Test void illegalPlacementDoesNotClearPreviousFleetAndSeededGamesReplay() {
        var game = new BattleshipGame(2, 9, Map.of());
        game.apply(0, "place:b0_0");
        var before = game.cellsFor(0);
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "place:b0_9"));
        assertEquals(before, game.cellsFor(0));
        for (int seed = 0; seed < 5; seed++) {
            var a = new BattleshipGame(2, seed, Map.of());
            var b = new BattleshipGame(2, seed, Map.of());
            var random = new Random(seed);
            for (int step = 0; !a.finished() && step < 250; step++) {
                int actor = a.currentPlayer();
                String action = a.botAction(actor, random);
                assertNotNull(action);
                a.apply(actor, action); b.apply(actor, action);
                assertEquals(a.cellsFor(0), b.cellsFor(0));
                assertEquals(a.cellsFor(1), b.cellsFor(1));
            }
            assertTrue(a.finished());
        }
    }

    @Test void orientationClearAndOverlapUseOnlyOwnBoard() {
        var game = new BattleshipGame(2, 8, Map.of());
        game.apply(0, "place:b0_0");
        game.apply(0, "ship:1");
        var before = game.cellsFor(0);
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "place:b0_2"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "place:b1_10"));
        assertEquals(before, game.cellsFor(0));
        game.apply(0, "rotate");
        game.apply(0, "place:b0_19");
        assertEquals(4, game.cellsFor(0).stream().filter(c -> c.piece().equals("ship_battleship")).count());
        game.apply(0, "clear");
        assertEquals(0, game.cellsFor(0).stream().filter(c -> c.piece().equals("ship_battleship")).count());
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "confirm"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(-1, "auto"));
    }

    @Test void unseenEnemyFleetDoesNotChangePublicViewActionsOrBotChoice() {
        var a = new BattleshipGame(2, 10, Map.of());
        var b = new BattleshipGame(2, 11, Map.of());
        for (var game : List.of(a, b)) {
            game.apply(0, "auto"); game.apply(1, "auto");
            game.apply(0, "confirm"); game.apply(1, "confirm");
        }
        assertEquals(a.cells(), b.cells());
        assertEquals(a.legalActions(0), b.legalActions(0));
        assertEquals(a.botAction(0, new Random(31)), b.botAction(0, new Random(31)));
        assertEquals(a.publicInfo(), b.publicInfo());
        assertNotEquals(a.cellsFor(1), b.cellsFor(1));
    }

    @Test void hitStillAlternatesAndAllSeventeenHitsFinishGame() {
        var game = new BattleshipGame(2, 4, Map.of());
        for (int seat = 0; seat < 2; seat++) {
            game.apply(seat, "auto"); game.apply(seat, "confirm");
        }
        List<String> targets = game.cellsFor(1).stream().filter(c -> c.id().startsWith("b1_") && c.piece().startsWith("ship_"))
                .map(c -> "shoot:" + c.id()).toList();
        int index = 0;
        for (String target : targets) {
            game.apply(0, target);
            if (!game.finished()) {
                assertEquals(1, game.currentPlayer());
                game.apply(1, "shoot:b0_" + index++);
            }
        }
        assertEquals("winner:0", game.outcome());
        assertEquals("0", game.publicInfo().get("remaining_1"));
        assertTrue(game.legalActions(0).isEmpty());
    }
}

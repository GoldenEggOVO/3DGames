package GoldenEggOVO.TDGames.Multiplayer.game.monopoly;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MonopolyModernRulesTest {
    @Test void taxesUseTheCurrentPrintedAmountsWithoutAnIncomeTaxChoice() throws Exception {
        var income = game();
        positions(income)[0] = 1;
        dice(income, 1, 2);
        income.apply(0, "roll");
        assertEquals(1300, income.players().get(0).cash());
        assertEquals("manage", income.publicInfo().get("phase"));
        assertFalse(income.legalActions(0).stream().anyMatch(a -> a.startsWith("tax:")));
        var luxury = game();
        positions(luxury)[0] = 35;
        dice(luxury, 1, 2);
        luxury.apply(0, "roll");
        assertEquals(1400, luxury.players().get(0).cash());
    }

    @Test void keepingATransferredMortgageDoesNotChargeImmediateInterest() throws Exception {
        var game = game();
        own(game, 0, 1);
        ((boolean[])field(game, "mortgaged"))[1] = true;
        ((long[])field(game, "cash"))[1] = 0;
        game.apply(0, "trade:1:0:0:p1:-:-:-:0");
        game.apply(1, "accept:1");
        game.apply(1, "inherit:keep");
        assertEquals(0, game.players().get(1).cash());
        assertFalse(game.eliminated(1));
        assertEquals("roll", game.publicInfo().get("phase"));
        assertTrue(game.properties().get(1).mortgaged());
    }

    @Test void playersManageTheirOwnPropertiesBetweenOtherPlayersMoves() throws Exception {
        var game = game();
        own(game, 1, 1, 3);
        assertTrue(game.legalActions(1).contains("build:p1"));
        game.apply(1, "build:p1");
        assertEquals(1, game.properties().get(1).buildings());
        assertEquals(1450, game.players().get(1).cash());
        assertEquals(0, game.currentPlayer());
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "roll"));
        game.apply(1, "sell:p1");
        game.apply(1, "mortgage:p1");
        assertEquals(0, game.currentPlayer());
    }

    @Test void outOfTurnTradeReturnsToTheOriginalMovementPlayer() {
        var game = game();
        game.apply(1, "trade:2:100:0:-:-:-:-:0");
        assertEquals(2, game.currentPlayer());
        game.apply(2, "accept:1");
        assertEquals(0, game.currentPlayer());
        assertEquals("roll", game.publicInfo().get("phase"));
        assertEquals(1400, game.players().get(1).cash());
        assertEquals(1600, game.players().get(2).cash());
    }

    @Test void debtCanBeSettledBySellingADeedToAnotherPlayer() throws Exception {
        var game = game();
        own(game, 0, 1);
        own(game, 2, 3);
        ((long[])field(game, "cash"))[0] = 0;
        dice(game, 1, 2);
        game.apply(0, "roll");
        assertEquals("debt", game.publicInfo().get("phase"));
        game.apply(0, "trade:1:0:100:p1:-:-:-:1");
        game.apply(1, "accept:2");
        assertEquals("manage", game.publicInfo().get("phase"));
        assertEquals(96, game.players().get(0).cash());
        assertEquals(1504, game.players().get(2).cash());
        assertEquals(1, game.properties().get(1).owner());
    }

    @Test void auctionsStartAtTenAndAcceptBidsWithoutFollowingSeatOrder() throws Exception {
        var game = game();
        dice(game, 1, 2);
        game.apply(0, "roll");
        game.apply(0, "decline");
        assertTrue(game.legalActions(2).contains("bid:10"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "bid:9"));
        game.apply(2, "bid:10");
        game.apply(0, "pass");
        game.apply(1, "pass");
        assertEquals(2, game.properties().get(3).owner());
        assertEquals(1490, game.players().get(2).cash());
    }

    @Test void utilityRentUsesASeparateVisibleDiceRollWithoutMovingAgain() throws Exception {
        var game = game();
        own(game, 1, 12);
        positions(game)[0] = 9;
        dice(game, 1, 2, 3, 4);
        game.apply(0, "roll");
        assertEquals("utility-rent", game.publicInfo().get("phase"));
        assertEquals(1500, game.players().get(0).cash());
        game.apply(0, "roll");
        assertEquals(12, game.players().get(0).position());
        assertEquals(1472, game.players().get(0).cash());
        assertEquals(1528, game.players().get(1).cash());
        assertEquals("3,4", game.publicInfo().get("dice"));
        assertFalse(game.legalActions(0).contains("roll"));
    }

    @Test void theLastHouseIsAuctionedWhenAnotherPlayerCanUseIt() throws Exception {
        var game = game();
        own(game, 0, 1, 3);
        own(game, 1, 6, 8, 9);
        set(game, "houses", 1);
        game.apply(0, "build:p1");
        assertEquals("auction", game.publicInfo().get("phase"));
        assertEquals("house", game.publicInfo().get("auction-type"));
        assertEquals(0, game.properties().get(1).buildings());
        game.apply(1, "auction-target:p8");
        game.apply(1, "bid:10");
        game.apply(0, "pass");
        assertEquals(1, game.properties().get(8).buildings());
        assertEquals(1, game.properties().get(8).owner());
        assertEquals(0, game.bankHouses());
        assertEquals(1490, game.players().get(1).cash());
        assertEquals(0, game.currentPlayer());
    }

    @Test void theLastHotelReturnsFourHousesOnlyWhenTheAuctionIsAwarded() throws Exception {
        var game = game();
        own(game, 0, 1, 3);
        own(game, 1, 6, 8, 9);
        int[] buildings = (int[])field(game, "buildings");
        for (int property : new int[]{1, 3, 6, 8, 9}) buildings[property] = 4;
        set(game, "hotels", 1);
        game.apply(0, "build:p1");
        assertEquals("hotel", game.publicInfo().get("auction-type"));
        assertEquals(32, game.bankHouses());
        game.apply(1, "bid:20");
        game.apply(0, "pass");
        assertEquals(5, game.properties().get(6).buildings());
        assertEquals(36, game.bankHouses());
        assertEquals(0, game.bankHotels());
    }

    @Test void anUncontestedLastHouseIsBoughtAtTheNormalPrice() throws Exception {
        var game = game();
        own(game, 0, 1, 3);
        set(game, "houses", 1);
        game.apply(0, "build:p1");
        assertEquals("roll", game.publicInfo().get("phase"));
        assertEquals(1, game.properties().get(1).buildings());
        assertEquals(1450, game.players().get(0).cash());
        assertEquals(0, game.bankHouses());
    }

    private static MonopolyGame game() { return new MonopolyGame(3, 17, Map.of()); }
    private static int[] positions(MonopolyGame game) throws Exception { return (int[])field(game, "positions"); }
    private static void own(MonopolyGame game, int seat, int... properties) throws Exception {
        int[] owners = (int[])field(game, "owners");
        for (int property : properties) owners[property] = seat;
    }
    private static void dice(MonopolyGame game, int... values) throws Exception {
        set(game, "random", new Random(1) { int cursor; @Override public int nextInt(int bound) { return values[cursor++] - 1; } });
    }
    private static Object field(MonopolyGame game, String name) throws Exception {
        var field = MonopolyGame.class.getDeclaredField(name); field.setAccessible(true); return field.get(game);
    }
    private static void set(MonopolyGame game, String name, Object value) throws Exception {
        var field = MonopolyGame.class.getDeclaredField(name); field.setAccessible(true); field.set(game, value);
    }
}

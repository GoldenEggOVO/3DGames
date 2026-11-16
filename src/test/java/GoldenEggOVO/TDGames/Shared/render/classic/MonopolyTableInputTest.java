package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MonopolyTableInputTest {
    private MonopolyTableInput trade(MonopolyGame game) {
        return MonopolyTableInput.open(game, 0, game.controlActions(0).stream()
                .filter(action -> action.startsWith("trade:")).findFirst().orElseThrow());
    }

    @Test void tradeCashChangesStayLocalUntilExplicitConfirmation() {
        var game = new MonopolyGame(2, 12, Map.of());
        var input = trade(game);
        assertNotNull(input);
        assertNull(input.select("@edit:give-cash"));
        assertNull(input.select("@edit:change:100"));
        assertNull(input.select("@edit:change:10"));
        assertNull(input.select("@edit:back"));
        assertEquals(0, game.revision());
        assertEquals("trade:1:111:0:-:-:-:-:0", input.select("@edit:confirm"));
        assertTrue(input.closed());
        assertNull(game.trade());
    }

    @Test void amountControlsClampToAvailableCashAndNeverGoNegative() {
        var game = new MonopolyGame(2, 12, Map.of());
        var input = trade(game);
        input.select("@edit:give-cash");
        input.select("@edit:change:-100");
        input.select("@edit:change:100");
        input.select("@edit:max");
        input.select("@edit:change:100");
        input.select("@edit:back");
        assertEquals("trade:1:1500:0:-:-:-:-:0", input.select("@edit:confirm"));
    }

    @Test void unavailablePropertiesAndCardsCannotBeAddedAndCancelDoesNotSubmit() {
        var game = new MonopolyGame(2, 12, Map.of());
        var input = trade(game);
        input.select("@edit:give-properties");
        input.property(1);
        assertFalse(input.actions().contains("@edit:property:1"));
        input.select("@edit:back");
        input.select("@edit:give-cards");
        assertFalse(input.actions().contains("@edit:card:0"));
        input.select("@edit:cancel");
        assertTrue(input.closed());
        assertNull(input.select("@edit:confirm"));
        assertEquals(0, game.revision());
    }

    @Test void staleDraftCannotSubmitAfterAnotherGameAction() {
        var game = new MonopolyGame(2, 12, Map.of());
        var input = trade(game);
        game.apply(0, "roll");
        assertFalse(input.valid());
        assertNull(input.select("@edit:confirm"));
    }

    @Test void selectedDeedsAndJailCardsProduceAValidOffer() throws Exception {
        var game = new MonopolyGame(2, 12, Map.of());
        var owners = MonopolyGame.class.getDeclaredField("owners"); owners.setAccessible(true);
        ((int[]) owners.get(game))[1] = 0; ((int[]) owners.get(game))[6] = 1;
        var cards = MonopolyGame.class.getDeclaredField("jailCards"); cards.setAccessible(true);
        @SuppressWarnings("unchecked") var hands = (List<List<Integer>>) cards.get(game);
        hands.get(0).add(0); hands.get(1).add(1);
        var input = trade(game);
        for (String action : List.of("@edit:give-properties", "@edit:property:1", "@edit:back",
                "@edit:take-properties", "@edit:property:6", "@edit:back", "@edit:give-cards", "@edit:card:0",
                "@edit:back", "@edit:take-cards", "@edit:card:1", "@edit:back")) input.select(action);
        String offer = input.select("@edit:confirm");
        assertEquals("trade:1:1:0:p1:p6:0:1:0", offer);
        game.apply(0, offer);
        assertEquals(List.of(1), game.trade().giveProperties());
        assertEquals(List.of(1), game.trade().takeCards());
    }

    @Test void bidsAreEditedWithPhysicalAmountButtonsBeforeSubmission() {
        MonopolyGame game = null;
        for (int seed = 0; seed < 50; seed++) {
            var candidate = new MonopolyGame(2, seed, Map.of());
            candidate.apply(0, "roll");
            if (candidate.legalActions(0).contains("decline")) { game = candidate; break; }
        }
        assertNotNull(game);
        game.apply(0, "decline");
        int bidder = game.currentPlayer();
        String minimum = game.legalActions(bidder).stream().filter(action -> action.startsWith("bid:")).findFirst().orElseThrow();
        var input = MonopolyTableInput.open(game, bidder, minimum);
        input.select("@edit:change:100");
        input.select("@edit:change:-10");
        String bid = input.select("@edit:confirm");
        assertEquals("bid:100", bid);
        game.apply(bidder, bid);
        assertEquals("100", game.publicInfo().get("bid"));
    }
}

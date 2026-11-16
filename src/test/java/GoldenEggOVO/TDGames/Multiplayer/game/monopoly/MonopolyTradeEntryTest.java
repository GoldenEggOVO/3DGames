package GoldenEggOVO.TDGames.Multiplayer.game.monopoly;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonopolyTradeEntryTest {
    @Test void zeroCashStillAllowsAValidTradeEntry() throws Exception {
        var game = new MonopolyGame(2, 12, Map.of());
        var cashField = MonopolyGame.class.getDeclaredField("cash"); cashField.setAccessible(true);
        long[] cash = (long[]) cashField.get(game);
        cash[0] = 0;
        String request = game.controlActions(0).stream().filter(a -> a.startsWith("trade:")).findFirst().orElseThrow();
        game.apply(0, request);
        assertNotNull(game.trade());
        assertEquals(0, game.trade().giveCash());
    }

    @Test void assetOnlyOffersRemainReachableWhenBothPlayersHaveNoCash() throws Exception {
        var game = new MonopolyGame(2, 12, Map.of());
        var cashField = MonopolyGame.class.getDeclaredField("cash"); cashField.setAccessible(true);
        Arrays.fill((long[]) cashField.get(game), 0);
        var ownersField = MonopolyGame.class.getDeclaredField("owners"); ownersField.setAccessible(true);
        ((int[]) ownersField.get(game))[1] = 0;
        String offer = game.controlActions(0).stream().filter(a -> a.startsWith("trade:")).findFirst().orElseThrow();
        game.apply(0, offer);
        assertEquals(List.of(1), game.trade().giveProperties());
    }
}

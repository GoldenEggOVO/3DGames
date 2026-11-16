package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StakeWindowTest {
    @Test
    void decimalInputAllowsFreeGamesAndRejectsAmbiguousOrNonFiniteValues() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 1, 0);
        StakeWindow.submit(plugin, player, room, 7, " 10.25 ");
        verify(plugin.games()).setStake(player, room, 7, 10.25);
        StakeWindow.submit(plugin, player, room, 7, "0");
        verify(plugin.games()).setStake(player, room, 7, 0);
        for (String invalid : new String[] {null, "", "-1", "NaN", "Infinity", "1e3", "1,000", "1.2.3"})
            assertThrows(IllegalArgumentException.class, () -> StakeWindow.submit(plugin, player, room, 7, invalid));
        verifyNoMoreInteractions(plugin.games());
    }
}

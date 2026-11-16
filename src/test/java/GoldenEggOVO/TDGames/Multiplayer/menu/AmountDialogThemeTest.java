package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.Core.menu.GameMenus;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Core.menu.MenuView;
import GoldenEggOVO.TDGames.Core.menu.DialogMenus;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.texasholdem.TexasHoldemGame;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AmountDialogThemeTest {
    @Test void stakeEditorUsesSharedDialogAndReturnsToTheSameRoomWithoutSubmitting() {
        var plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        when(plugin.allowed(any())).thenReturn(true);
        SessionTestSupport.module(plugin, "integrations", mock(OptionalIntegrations.class));
        when(plugin.integrations().stakesEnabled()).thenReturn(true);
        var economy = mock(EconomyAccess.class);
        when(plugin.integrations().economy()).thenReturn(economy);
        when(economy.format(anyDouble())).thenReturn("10");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 1, 0);
        room.join(player.getUniqueId(), "Host");
        check(plugin, player, room, "stake_amount", () -> StakeWindow.open(plugin, player, room));
        verify(plugin.games(), never()).setStake(any(), any(), anyLong(), anyDouble());
    }

    @Test void pokerEditorUsesSharedDialogAndReturnsToTheSameRoomWithoutRaising() {
        var plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var room = new GameSession(UUID.randomUUID(), "texas-holdem", 3, 1, 0);
        SessionTestSupport.set(room, "board", new TexasHoldemGame(3, 1));
        room.fillBots();
        SessionTestSupport.mutableSeats(room).set(room.turn(), new GameSession.Seat(player.getUniqueId(), "Player", false));
        check(plugin, player, room, "street_total", () -> PokerRaiseWindow.open(plugin, player, room, 0));
        verify(plugin.games(), never()).apply(any(), anyInt(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private void check(TDGamesPlugin plugin, Player player, GameSession room, String field, Runnable open) {
        try (var menus = mockStatic(DialogMenus.class); var dialogs = mockStatic(Dialog.class);
             var bases = mockStatic(DialogBase.class); var inputs = mockStatic(DialogInput.class, RETURNS_MOCKS);
             var bodies = mockStatic(DialogBody.class, RETURNS_MOCKS);
             var actions = mockStatic(DialogAction.class)) {
            final MenuView[] captured = new MenuView[1];
            final BiConsumer<String, Map<String, String>>[] callbacks = new BiConsumer[1];
            menus.when(() -> DialogMenus.open(eq(plugin), eq(player), any(), any())).thenAnswer(call -> {
                captured[0] = call.getArgument(2); callbacks[0] = call.getArgument(3); return null;
            });
            open.run();
            assertNotNull(captured[0], "Amount pages must share the same themed Dialog renderer");
            assertEquals(field, captured[0].inputs().getFirst().id());
            assertEquals("back", captured[0].exit().key());
            assertTrue(captured[0].buttons().stream().noneMatch(button -> button.key().equals("back") || button.key().equals("close")));
            callbacks[0].accept("back", Map.of());
            verify(plugin.menus()).room(player, room);
        }
    }
}

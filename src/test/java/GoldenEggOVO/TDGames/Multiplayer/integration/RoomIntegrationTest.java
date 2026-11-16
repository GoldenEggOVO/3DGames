package GoldenEggOVO.TDGames.Multiplayer.integration;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RoomIntegrationTest {
    TDGamesPlugin plugin() throws Exception {
        var plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);SessionTestSupport.install(plugin);
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        SessionTestSupport.module(plugin, "integrations", mock(OptionalIntegrations.class));
        SessionTestSupport.install(plugin, new java.util.LinkedHashMap<>());
        doNothing().when(SessionTestSupport.service(plugin)).save();
        doNothing().when(SessionTestSupport.service(plugin)).save(any(GameSession.class));
        doNothing().when(SessionTestSupport.service(plugin)).start(any());
        return plugin;
    }

    @Test
    void finishingTwiceAndRemovingTheRoomNeverPayTwice() throws Exception {
        var plugin = plugin();
        var room = new GameSession(java.util.UUID.randomUUID(), "connectfour", 2, 1, 0);
        SessionTestSupport.set(room, "board", room.newBoard());
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        plugin.games().finish(room, "winner:0");
        plugin.games().finish(room, "winner:0");
        plugin.games().remove(room);
        verify(plugin.integrations(), times(1)).finish(room, "winner:0");
        verify(plugin.integrations()).cancel(room);
    }

    @Test
    void restoredBoardsNeverTriggerEntryFees() throws Exception {
        var plugin = plugin();
        doCallRealMethod().when(SessionTestSupport.service(plugin)).start(any());
        doReturn(true).when(SessionTestSupport.service(plugin)).prepareSeats(any());
        SessionTestSupport.install(plugin, new java.util.LinkedHashMap<>());
        var room = new GameSession(java.util.UUID.randomUUID(), "connectfour", 2, 1, 0);
        room.fillBots();
        SessionTestSupport.set(room, "restoring", true);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) { plugin.games().start(room); }
        verify(plugin.integrations(), never()).beforeStart(any());
    }

    @Test
    void failedBoardPresentationRefundsTheEntryFees() throws Exception {
        var plugin = plugin();
        doCallRealMethod().when(SessionTestSupport.service(plugin)).start(any());
        doReturn(true).when(SessionTestSupport.service(plugin)).prepareSeats(any());
        doReturn(java.util.logging.Logger.getAnonymousLogger()).when(plugin).getLogger();
        SessionTestSupport.install(plugin, new java.util.LinkedHashMap<>());
        var room = new GameSession(java.util.UUID.randomUUID(), "connectfour", 2, 1, 0);
        room.fillBots();
        var tableWorld = plugin.tableWorld();
        doThrow(new IllegalStateException("display failure")).when(tableWorld).render(room);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) { plugin.games().start(room); }
        verify(plugin.integrations()).beforeStart(room);
        verify(plugin.integrations()).cancel(room);
    }
}

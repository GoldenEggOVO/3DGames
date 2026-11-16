package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModeMenuRoutingTest {
    @Test void soloSummaryDoesNotDescribeThePlayerAsTheirOwnOpponent() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        var solo = new GameSession(UUID.randomUUID(), "2048", 1, 42, 4);
        solo.join(fixture.player.getUniqueId(), "Player");
        SessionTestSupport.set(solo, "board", GoldenEggOVO.TDGames.Core.game.GameFactory.create("2048", 1, 42));
        SessionTestSupport.set(solo, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(fixture.plugin).put(solo.id, solo);
        fixture.menus.room(fixture.player, solo);
        String summary = GoldenEggOVO.TDGames.Core.text.MessageText.plain(fixture.description);
        assertFalse(summary.contains("1/1"), summary);
        assertFalse(summary.contains("Current turn"), summary);
    }
    @Test void multiplayerBrowserDoesNotExposeAnotherPlayersSoloSession() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        var solo = new GameSession(UUID.randomUUID(), "2048", 1, 42, 4);
        solo.join(UUID.randomUUID(), "Other");
        SessionTestSupport.mutableSessions(fixture.plugin).put(solo.id, solo);
        var multiplayer = fixture.addRoom(5);
        fixture.menus.rooms(fixture.player);
        assertTrue(fixture.buttons.stream().anyMatch(button -> button.id().equals("room-" + multiplayer.id)));
        assertFalse(fixture.buttons.stream().anyMatch(button -> button.id().equals("room-" + solo.id)));
    }

    @Test void completedSoloMenuDoesNotReadRoomPayments() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        var solo = new GameSession(UUID.randomUUID(), "2048", 1, 42, 4);
        solo.join(fixture.player.getUniqueId(), "Player");
        solo.finish("winner:0");
        SessionTestSupport.mutableSessions(fixture.plugin).put(solo.id, solo);
        var payments = mock(OptionalIntegrations.class);
        when(fixture.plugin.integrations()).thenReturn(payments);
        fixture.menus.room(fixture.player, solo);
        assertTrue(fixture.buttons.stream().anyMatch(button -> button.id().equals("rematch")));
        assertTrue(fixture.buttons.stream().anyMatch(button -> button.id().equals("save-leave")));
        assertFalse(fixture.buttons.stream().anyMatch(button -> button.id().equals("stake")));
        verifyNoInteractions(payments);
    }
}

package GoldenEggOVO.TDGames.Multiplayer.integration.placeholder;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderValuesTest {
    GameSession room() {
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(UUID.randomUUID(), "Host");
        room.fillBots();
        SessionTestSupport.set(room, "board", room.newBoard());
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        return room;
    }

    @Test
    void absentPlayersAndUnknownVariablesHaveExplicitFallbacks() {
        var values = new PlaceholderValues(System::currentTimeMillis);
        assertEquals("0", values.resolve(null, "rooms"));
        assertEquals("false", values.resolve(null, "in_room"));
        assertEquals("0", values.resolve(null, "room_players"));
        assertEquals("", values.resolve(null, "room_name"));
        assertNull(values.resolve(null, "unknown"));
    }

    @Test
    void publishedRoomDataIsDetachedFromMutableGameState() {
        GameSession room = room();
        UUID player = room.seats.getFirst().id();
        var values = new PlaceholderValues(System::currentTimeMillis);
        values.refresh(List.of(room), mock(EconomyAccess.class));
        assertEquals("1", values.resolve(null, "rooms"));
        assertEquals("1", values.resolve(null, "players"));
        assertEquals("2", values.resolve(player, "room_players"));
        assertEquals("1", values.resolve(player, "room_bots"));
        assertEquals("connectfour", values.resolve(player, "game_id"));
        assertEquals(MessageText.plain(RoomText.name(room)), values.resolve(player, "room_name"));
        assertEquals("true", values.resolve(player, "your_turn"));
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableSeats(room).clear();
        assertEquals("playing", values.resolve(player, "state_id"));
        assertEquals("2", values.resolve(player, "room_players"));
        values.refresh(List.of(), mock(EconomyAccess.class));
        assertEquals("false", values.resolve(player, "in_room"));
        assertEquals("", values.resolve(player, "game_id"));
        assertEquals("false", values.resolve(player, "your_turn"));
    }

    @Test
    void departedPlayersAreNotCountedOrBoundToTheirPreviousRoom() {
        GameSession room = room();
        UUID player = room.seats.getFirst().id();
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableDeparted(room).add(player);
        var values = new PlaceholderValues(System::currentTimeMillis);
        values.refresh(List.of(room), mock(EconomyAccess.class));
        assertEquals("0", values.resolve(null, "players"));
        assertEquals("false", values.resolve(player, "in_room"));
    }

    @Test
    void balanceRequestsNeverCallProvidersAndExpireWithoutLeakingPlayerCaches() {
        var clock = new AtomicLong(1000);
        var values = new PlaceholderValues(clock::get);
        var economy = mock(EconomyAccess.class);
        UUID player = UUID.randomUUID();
        when(economy.available()).thenReturn(true);
        when(economy.balance(player)).thenReturn(OptionalDouble.of(123.5));
        when(economy.format(123.5)).thenReturn("123.50 coins");
        assertEquals("", values.resolve(player, "balance"));
        verifyNoInteractions(economy);
        values.refresh(List.of(), economy);
        assertEquals("123.5", values.resolve(player, "balance"));
        assertEquals("123.50 coins", values.resolve(player, "balance_formatted"));
        values.refresh(List.of(), economy);
        verify(economy, times(1)).balance(player);
        clock.addAndGet(5000);
        values.refresh(List.of(), economy);
        verify(economy, times(2)).balance(player);
        clock.addAndGet(60001);
        values.refresh(List.of(), economy);
        assertEquals("", values.resolve(player, "balance"));
        verify(economy, times(2)).balance(player);
    }

    @Test
    void reloadPersistsTheInternalExpansionAndHandlesOfflinePlayers() {
        var values = new PlaceholderValues(System::currentTimeMillis);
        var expansion = new TabletopExpansion("test", values);
        assertTrue(expansion.persist());
        assertEquals("3dgames", expansion.getIdentifier());
        assertEquals("test", expansion.getVersion());
        assertTrue(expansion.getPlaceholders().contains("%3dgames_rooms%"));
        assertEquals("0", expansion.onRequest(null, "rooms"));
        var player = mock(org.bukkit.OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals("false", expansion.onRequest(player, "in_room"));
        assertNull(expansion.onRequest(player, "unknown"));
    }

}

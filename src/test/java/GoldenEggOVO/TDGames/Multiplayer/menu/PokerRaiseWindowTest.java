package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import com.google.gson.JsonPrimitive;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.texasholdem.TexasHoldemGame;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PokerRaiseWindowTest {
    private TDGamesPlugin plugin;
    private Player player;
    private GameSession room;
    private int seat;
    private long openedRevision;

    @BeforeEach
    void setup() throws Exception {
        plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.module(plugin, "pack", mock(ResourcePackService.class));
        player = mock(Player.class);
        room = new GameSession(UUID.randomUUID(), "texas-holdem", 3, 1, 0);
        SessionTestSupport.set(room, "board", new TexasHoldemGame(3, 1));
        seat = room.turn();
        room.fillBots();
        UUID id = UUID.randomUUID();
        SessionTestSupport.mutableSeats(room).set(seat, new GameSession.Seat(id, "Player", false));
        when(player.getUniqueId()).thenReturn(id);
        when(plugin.tableWorld().atTableWorld(player, room)).thenReturn(true);
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        openedRevision = room.revision();
    }

    @Test
    void movingToAnotherWorldInvalidatesAnOpenRaiseDialog() {
        when(plugin.tableWorld().atTableWorld(player, room)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, openedRevision, seat, "20"));
        verify(plugin.games(), never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void validRaiseRechecksThePackAndUsesTheCurrentRoom() {
        PokerRaiseWindow.submit(plugin, player, room, openedRevision, seat, "20");
        verify(plugin.pack()).require(player, room.kind);
        verify(plugin.games()).apply(room, seat, new JsonPrimitive("raise:20"), null);
    }

    @Test
    void staleRoomRevisionCannotSubmitARaise() {
        SessionTestSupport.bump(room);
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, openedRevision, seat, "20"));
        verify(plugin.games(), never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void closedRoomCannotSubmitARaise() {
        SessionTestSupport.mutableSessions(plugin).clear();
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, openedRevision, seat, "20"));
        verify(plugin.games(), never()).apply(any(), anyInt(), any(), any());
    }

    @Test
    void invalidRaiseLeavesTheRoomUntouched() {
        assertThrows(IllegalArgumentException.class,
                () -> PokerRaiseWindow.submit(plugin, player, room, openedRevision, seat, "5"));
        verify(plugin.games(), never()).apply(any(), anyInt(), any(), any());
        assertEquals(openedRevision, room.revision());
        assertTrue(room.history.isEmpty());
    }
}

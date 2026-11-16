package GoldenEggOVO.TDGames;

import GoldenEggOVO.TDGames.TDGamesPlugin;

import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StakeLifecycleTest {
    TDGamesPlugin plugin;
    GameSession room;
    Player host, guest;

    Player player(String name) {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("3dgames.use")).thenReturn(true);
        return player;
    }

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);SessionTestSupport.install(plugin);
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        when(plugin.tableWorld().atTableWorld(any(), any())).thenReturn(true);
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.module(plugin, "integrations", mock(OptionalIntegrations.class));
        when(plugin.integrations().isStakeGame(any())).thenReturn(true);
        doReturn(new YamlConfiguration()).when(plugin).getConfig();
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        GameRulesTest.set(plugin.games(), "loaded", true);
        doNothing().when(SessionTestSupport.service(plugin)).save();
        doNothing().when(SessionTestSupport.service(plugin)).save(any(GameSession.class));
        host = player("Host");
        guest = player("Guest");
        room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(host.getUniqueId(), "Host");
        room.join(guest.getUniqueId(), "Guest");
        SessionTestSupport.set(room, "stake", 10.0);
        SessionTestSupport.set(room, "board", room.newBoard());
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
    }

    @Test
    void resigningSettlesForOpponentsWithoutDiscardingTheirRoomOrReplaySeats() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().leave(host);
        }
        assertEquals(GameSession.Phase.FINISHED, room.phase());
        assertEquals("winner:1", room.result());
        assertTrue(plugin.games().activeSessions().containsKey(room.id));
        assertEquals(2, room.seats.size());
        assertNull(plugin.games().session(host));
        assertSame(room, plugin.games().session(guest));
        assertTrue(room.departed.contains(host.getUniqueId()));
    }

    @Test
    void reconnectionGraceBlocksMovesAndTimeoutButAllowsPlayAfterReturning() {
        SessionTestSupport.mutableOffline(room).put(host.getUniqueId(), System.currentTimeMillis() - 5000);
        SessionTestSupport.set(room, "changed", System.currentTimeMillis() - 70_000);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(guest.getUniqueId())).thenReturn(guest);
            plugin.games().apply(room, 0, new com.google.gson.JsonPrimitive("drop:0"), null);
            plugin.games().tick();
            assertTrue(room.history.isEmpty());
            assertEquals(GameSession.Phase.PLAYING, room.phase());
            bukkit.when(() -> Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
            plugin.games().tick();
            assertTrue(room.offline.isEmpty());
            plugin.games().apply(room, 0, new com.google.gson.JsonPrimitive("drop:0"), null);
            assertEquals(1, room.history.size());
        }
    }

    @Test
    void ordinaryGamesPauseDuringReconnectAndResumeWithoutAnAutomaticMove() {
        when(plugin.integrations().isStakeGame(room)).thenReturn(false);
        SessionTestSupport.mutableOffline(room).put(host.getUniqueId(), System.currentTimeMillis() - 5000);
        SessionTestSupport.set(room, "changed", System.currentTimeMillis() - 70_000);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(guest.getUniqueId())).thenReturn(guest);
            plugin.games().apply(room, 0, new com.google.gson.JsonPrimitive("drop:0"), null);
            plugin.games().tick();
            assertTrue(room.history.isEmpty());
            assertEquals(GameSession.Phase.PLAYING, room.phase());
            bukkit.when(() -> Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
            plugin.games().tick();
            assertTrue(room.offline.isEmpty());
            assertTrue(room.history.isEmpty());
            plugin.games().apply(room, 0, new com.google.gson.JsonPrimitive("drop:0"), null);
            assertEquals(1, room.history.size());
        }
    }

    @Test
    void anExpiredPlayerForfeitsAndFinishedRoomsDoNotForfeitAgain() {
        SessionTestSupport.mutableOffline(room).put(host.getUniqueId(), System.currentTimeMillis() - 121_000);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(guest.getUniqueId())).thenReturn(guest);
            plugin.games().tick();
            assertEquals("winner:1", room.result());
            assertEquals(GameSession.Phase.FINISHED, room.phase());
            plugin.games().tick();
            assertEquals("winner:1", room.result());
            assertTrue(plugin.games().activeSessions().containsKey(room.id));
            assertFalse(room.offline.containsKey(host.getUniqueId()));
        }
    }

    @Test
    void reviewPausedStakeGamesCannotBeDiscardedByLeaving() {
        SessionTestSupport.set(room, "phase", GameSession.Phase.PAUSED);
        SessionTestSupport.set(room, "result", "error.economy.storage");
        assertThrows(IllegalArgumentException.class, () -> plugin.games().leave(host));
        assertTrue(plugin.games().activeSessions().containsKey(room.id));
        assertTrue(room.departed.isEmpty());
    }

    @Test
    void resignedPlayersCannotRejoinAndRollbackMustPreserveReplaySeats() {
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableDeparted(room).add(host.getUniqueId());
        when(plugin.sessions().reserve(host.getUniqueId(), room.kind)).thenReturn(true);
        var seats = List.copyOf(room.seats);
        assertThrows(IllegalArgumentException.class, () -> plugin.games().join(host, room));
        assertEquals(seats, room.seats);
        assertEquals(Set.of(host.getUniqueId()), room.departed);
    }

    @Test
    void paidRematchesKeepEveryoneConfirmedUntilTheStartCheck() throws Exception {
        GameRulesTest.set(plugin.games().runtime(), "random", new java.security.SecureRandom());
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableReady(room).add(host.getUniqueId());
        doAnswer(call -> {
            GameSession next = call.getArgument(0);
            assertEquals(Set.of(host.getUniqueId(), guest.getUniqueId()), next.ready);
            assertEquals(GameSession.Phase.LOBBY, next.phase());
            SessionTestSupport.set(next, "phase", GameSession.Phase.PLAYING);
            return null;
        }).when(SessionTestSupport.service(plugin)).start(room);
        try (var bukkit = mockStatic(Bukkit.class)) {
            plugin.games().rematch(guest, room, 10.0);
        }
        verify(plugin.games()).start(room);
    }

    @Test
    void removingTheFinishedTableDoesNotReleaseAResignedPlayersNewSeat() {
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableDeparted(room).add(host.getUniqueId());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
            plugin.games().remove(room);
        }
        verify(plugin.sessions(), never()).release(host.getUniqueId(), room.kind);
    }

    @Test
    void readyCommandsOpenConfirmationAndOldPriceCallbacksCannotConfirmNewPrices() {
        SessionTestSupport.set(room, "phase", GameSession.Phase.LOBBY);
        SessionTestSupport.set(room, "stake", 20.0);
        plugin.games().ready(host, room);
        assertTrue(room.ready.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> plugin.games().ready(host, room, 10.0));
        assertTrue(room.ready.isEmpty());
        plugin.games().ready(host, room, 20.0);
        assertTrue(room.ready.contains(host.getUniqueId()));
        assertThrows(IllegalArgumentException.class, () -> plugin.games().startWithBots(host, room));
    }
}

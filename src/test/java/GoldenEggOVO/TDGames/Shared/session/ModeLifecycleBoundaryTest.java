package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModeLifecycleBoundaryTest {
    @TempDir Path directory;

    private Fixture fixture() {
        TDGamesPlugin plugin = mock(TDGamesPlugin.class);
        SessionTestSupport.platform(plugin);
        GameService service = new GameService(plugin);
        when(plugin.games()).thenReturn(service);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.sessions()).thenReturn(new PlayerSessions());
        when(plugin.tableWorld()).thenReturn(mock(TableWorldController.class));
        when(plugin.menus()).thenReturn(mock(GameMenus.class));
        var money = mock(OptionalIntegrations.class);
        when(plugin.integrations()).thenReturn(money);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Solo");
        when(plugin.allowed(player)).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(plugin.tableWorld().seatLocation(any(), eq(0))).thenReturn(new Location(mock(World.class), 0, 80, 0));
        when(plugin.singleplayer()).thenReturn(service.singleplayer());
        when(plugin.multiplayer()).thenReturn(service.multiplayer());
        return new Fixture(plugin, service, player, money);
    }

    private record Fixture(TDGamesPlugin plugin, GameService service, Player player, OptionalIntegrations money) {}

    @Test void outOfTurnPropertyManagementDoesNotRestartTheMovementClock() throws Exception {
        var f = fixture();
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "monopoly", 3);
            var session = f.service().session(f.player());
            session.board = session.newBoard();
            session.phase = GameSession.Phase.PLAYING;
            session.changed = 1234;
            var field = session.board.getClass().getDeclaredField("owners");
            field.setAccessible(true);
            ((int[])field.get(session.board))[1] = 1;
            assertEquals(SessionRuntime.ApplyResult.APPLIED, f.service().runtime().apply(session, 1,
                    new com.google.gson.JsonPrimitive("mortgage:p1"), null));
            assertEquals(1234, session.changed);
            assertEquals(0, session.turn());
        }
    }

    @Test void physicalTableActionsExplainRejectionsWithoutOpeningMenus() {
        var f = fixture();
        when(f.player().isOnline()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "2048", 1);
            var session = f.service().session(f.player());
            reset(f.plugin().menus());
            f.service().applyTableAction(session, 0, new com.google.gson.JsonPrimitive("not-a-move"), f.player());
            verify(f.plugin()).tell(eq(f.player()), any(net.kyori.adventure.text.Component.class));
            verify(f.plugin().menus(), never()).room(any(), any());

            var legal = session.board().legalActions(0).getFirst();
            f.service().applyTableAction(session, 0, new com.google.gson.JsonPrimitive(legal), f.player());
            verify(f.plugin().menus(), never()).room(any(), any());
            assertEquals(1, session.history.size());
        }
    }

    @Test void cancelledResumeShowsConfiguredReconnectTime() {
        var f = fixture();
        f.plugin().getConfig().set("reconnect-seconds", 37);
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "memory", 2);
            var session = f.service().session(f.player());
            when(f.player().teleport(any(Location.class))).thenReturn(false);
            f.service().runtime().resume(f.player(), session);
            var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify(f.plugin()).tell(eq(f.player()), message.capture());
            assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(message.getValue()).contains("37"));
        }
    }

    @Test void physicalMultiplayerActionsKeepTheTableOpenAndExplainReconnectPause() {
        var f = fixture();
        when(f.player().isOnline()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "memory", 2);
            var session = f.service().session(f.player());
            session.board = session.newBoard();
            session.phase = GameSession.Phase.PLAYING;
            reset(f.plugin().menus());
            f.service().applyTableAction(session, 0,
                    new com.google.gson.JsonPrimitive(session.board().legalActions(0).getFirst()), f.player());
            assertEquals(1, session.history.size());
            verify(f.plugin().menus(), never()).room(any(), any());

            session.markOffline(UUID.randomUUID(), System.currentTimeMillis());
            when(f.money().isStakeGame(session)).thenReturn(true);
            f.service().applyTableAction(session, 0, new com.google.gson.JsonPrimitive("ignored"), f.player());
            verify(f.plugin()).tell(eq(f.player()), any(net.kyori.adventure.text.Component.class));
            assertEquals(1, session.history.size());
            verify(f.plugin().menus(), never()).room(any(), any());
        }
    }

    @Test void soloCreationNeverTouchesRoomMoney() {
        var fixture = fixture();
        var plugin = fixture.plugin();
        var service = fixture.service();
        var player = fixture.player();
        var money = fixture.money();
        try (var bukkit = mockStatic(Bukkit.class)) {
            service.create(player, "2048", 1);
            assertNotNull(service.session(player));
            assertEquals(GameSession.Phase.PLAYING, service.session(player).phase());
            assertEquals("2048", plugin.sessions().reservation(player.getUniqueId()));
            verifyNoInteractions(money);
        }
    }

    @Test void modeServicesRejectSessionsFromTheOtherMode() {
        var f = fixture();
        var solo = new GameSession(UUID.randomUUID(), "memory", 1, 42, 0);
        var multi = new GameSession(UUID.randomUUID(), "memory", 2, 42, 1);
        assertThrows(IllegalArgumentException.class, () -> f.service().singleplayer().start(multi));
        assertThrows(IllegalArgumentException.class, () -> f.service().multiplayer().start(solo));
        assertThrows(IllegalArgumentException.class, () -> f.service().singleplayer().create(f.player(), "memory", 2, Map.of(), false));
        assertThrows(IllegalArgumentException.class, () -> f.service().multiplayer().create(f.player(), "2048", 1, Map.of()));
        assertTrue(f.service().activeSessions().isEmpty());
        assertNull(f.plugin().sessions().reservation(f.player().getUniqueId()));
        verifyNoInteractions(f.money());
    }

    @Test void existingRoomLimitIncludesBothModes() {
        var f = fixture();
        f.plugin().getConfig().set("max-rooms", 1);
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(f.plugin().allowed(other)).thenReturn(true);
        when(other.teleport(any(Location.class))).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "2048", 1);
            var failure = assertThrows(IllegalArgumentException.class, () -> f.service().create(other, "chess", 2));
            assertTrue(failure.getMessage().contains("Room limit"));
            assertEquals(1, f.service().activeSessions().size());
            assertNull(f.plugin().sessions().reservation(other.getUniqueId()));
        }
    }

    @Test void bothModesShareTableSlotsAndPlayerReservations() {
        var f = fixture();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getName()).thenReturn("Guest");
        when(f.plugin().allowed(other)).thenReturn(true);
        when(other.teleport(any(Location.class))).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            f.service().create(f.player(), "2048", 1);
            f.service().create(other, "memory", 2);
            assertEquals(0, f.service().session(f.player()).table);
            assertEquals(1, f.service().session(other).table);
            assertEquals(1, f.service().multiplayer().rooms().size());
            assertFalse(f.service().multiplayer().rooms().containsKey(f.service().session(f.player()).id));
            assertThrows(IllegalArgumentException.class, () -> f.service().create(f.player(), "chess", 2));
            assertEquals("2048", f.plugin().sessions().reservation(f.player().getUniqueId()));
            assertEquals(2, f.service().activeSessions().size());
        }
    }

    @Test void soloMemoryUsesSharedAutomaticRuleActions() {
        var f = fixture();
        var session = new GameSession(UUID.randomUUID(), "memory", 1, 42, 0);
        session.join(f.player().getUniqueId(), "Solo");
        session.board = session.newBoard();
        session.phase = GameSession.Phase.PLAYING;
        f.service().runtime().register(session);
        var memory = (GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame) session.board;
        try (var bukkit = mockStatic(Bukkit.class)) {
            for (int i = 0; i < 36 && !memory.waitingToHide(); i++) {
                f.service().apply(session, 0, new com.google.gson.JsonPrimitive(memory.legalActions(0).getFirst()), null);
            }
            assertTrue(memory.waitingToHide());
            int events = session.history.size();
            assertTrue(f.service().runtime().automatic(session, session.changed() + 1201));
            assertFalse(memory.waitingToHide());
            assertEquals(events + 1, session.history.size());
            assertEquals(GameSession.Phase.PLAYING, session.phase());
            verifyNoInteractions(f.money());
        }
    }
}

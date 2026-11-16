package GoldenEggOVO.TDGames.Multiplayer.integration;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.player.PlayerSessions;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Multiplayer.economy.RoomPaymentLedger;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StakeIntegrationTest {
    @TempDir Path directory;
    TDGamesPlugin plugin;
    OptionalIntegrations integrations;
    final Map<UUID, Double> balances = new HashMap<>();
    int transfers;

    @BeforeEach
    void setup() throws Exception {
        plugin = SessionTestSupport.plugin();
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        SessionTestSupport.module(plugin, "tableWorld", mock(GoldenEggOVO.TDGames.Shared.interaction.TableWorldController.class));
        SessionTestSupport.module(plugin, "playerSessions", mock(GoldenEggOVO.TDGames.Core.player.PlayerSessions.class));
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var config = new YamlConfiguration();
        config.set("economy.enabled", true);
        config.set("economy.entry-fee", 7);
        config.set("economy.winner-reward", 25);
        when(plugin.getConfig()).thenReturn(config);
        integrations = new OptionalIntegrations(plugin);
        EconomyAccess bank = new EconomyAccess() {
            public boolean available() { return true; }
            public OptionalDouble balance(UUID player) { return OptionalDouble.of(balances.getOrDefault(player, 0.0)); }
            public String format(double amount) { return Double.toString(amount); }
            public Transfer withdraw(UUID player, double amount) { transfers++; balances.merge(player, -amount, Double::sum); return Transfer.SUCCESS; }
            public Transfer deposit(UUID player, double amount) { transfers++; balances.merge(player, amount, Double::sum); return Transfer.SUCCESS; }
        };
        GameRulesTest.set(integrations, "economy", bank);
        GameRulesTest.set(integrations, "ledger", new RoomPaymentLedger(directory.resolve("economy.json"), bank));
    }

    GameSession room() {
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        for (int i = 0; i < 2; i++) {
            UUID id = UUID.randomUUID();
            room.join(id, "Player" + i);
            balances.put(id, 100.0);
        }
        return room;
    }

    @Test
    void missingOrUnreadableJournalsPauseRestoredStakeGames() throws Exception {
        for (boolean unreadable : new boolean[] {false, true}) {
            var room = room();
            SessionTestSupport.set(room, "stake", 10.0);
            SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
            SessionTestSupport.set(room, "board", room.newBoard());
            SessionTestSupport.mutableSessions(plugin).put(room.id, room);
            if (unreadable) GameRulesTest.set(integrations, "ledger", null);
            integrations.recover();
            assertEquals(GameSession.Phase.PAUSED, room.phase());
            assertEquals("error.economy.storage", room.result());
        }
        assertEquals(0, transfers);
    }

    @Test
    void recoveringTheForfeitJournalReleasesOnlyNewlyDepartedReservations() throws Exception {
        var room = room();
        SessionTestSupport.set(room, "stake", 10.0);
        SessionTestSupport.mutableReady(room).addAll(room.seats.stream().map(GameSession.Seat::id).toList());
        integrations.beforeStart(room);
        UUID loser = room.seats.getFirst().id();
        SessionTestSupport.mutableDeparted(room).add(loser);
        integrations.finish(room, "winner:1");
        SessionTestSupport.mutableDeparted(room).clear();
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        integrations.recover();
        assertEquals(-1, room.seat(loser));
        verify(plugin.sessions()).release(loser, room.kind);
        integrations.recover();
        verifyNoMoreInteractions(plugin.sessions());
        assertEquals(3, transfers);
    }

    @Test
    void newRoomsStartFreeAndChangingTheStakeInvalidatesEveryConfirmation() {
        var room = room();
        integrations.configureRoom(room);
        assertEquals(0.0, room.stake());
        SessionTestSupport.mutableReady(room).addAll(room.seats.stream().map(GameSession.Seat::id).toList());
        long revision = room.revision();
        integrations.changeStake(room, room.owner, revision, 10);
        assertEquals(10.0, room.stake());
        assertTrue(room.ready.isEmpty());
        assertTrue(room.revision() > revision);
        assertEquals(0, transfers);
    }

    @Test
    void onlyTheHostCanChooseAValidStakeBeforePlayAndStaleDialogsCannotChangeIt() {
        var room = room();
        integrations.configureRoom(room);
        assertThrows(IllegalArgumentException.class, () -> integrations.changeStake(room, room.seats.getLast().id(), room.revision(), 10));
        assertThrows(IllegalArgumentException.class, () -> integrations.changeStake(room, room.owner, room.revision() - 1, 10));
        for (double amount : new double[] {-1, 0.5, 1001, Double.NaN, Double.POSITIVE_INFINITY, 10.001})
            assertThrows(IllegalArgumentException.class, () -> integrations.changeStake(room, room.owner, room.revision(), amount));
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        assertThrows(IllegalArgumentException.class, () -> integrations.changeStake(room, room.owner, room.revision(), 10));
        assertEquals(0.0, room.stake());
    }

    @Test
    void aStakeNeedsEveryHumanConfirmationAndDoesNotAddLegacyFeesOrRewards() {
        var room = room();
        integrations.configureRoom(room);
        integrations.changeStake(room, room.owner, room.revision(), 10);
        SessionTestSupport.mutableReady(room).add(room.owner);
        assertThrows(IllegalArgumentException.class, () -> integrations.beforeStart(room));
        assertEquals(0, transfers);
        SessionTestSupport.mutableReady(room).add(room.seats.getLast().id());
        integrations.beforeStart(room);
        integrations.finish(room, "winner:0");
        assertEquals(110, balances.get(room.owner));
        assertEquals(90, balances.get(room.seats.getLast().id()));
        assertTrue(integrations.isStakeGame(room));
    }

    @Test
    void explicitFreeRoomsAndBotPracticeNeverUseLegacyFixedPayments() {
        var room = room();
        integrations.configureRoom(room);
        integrations.beforeStart(room);
        integrations.finish(room, "winner:0");
        assertEquals(0, transfers);
        SessionTestSupport.set(room, "stake", 10.0);
        SessionTestSupport.mutableSeats(room).set(1, new GameSession.Seat(room.seats.get(1).id(), "Bot", true));
        assertThrows(IllegalArgumentException.class, () -> integrations.beforeStart(room));
        assertEquals(0, transfers);
        SessionTestSupport.set(room, "stake", 0.0);
        assertDoesNotThrow(() -> integrations.beforeStart(room));
    }
}

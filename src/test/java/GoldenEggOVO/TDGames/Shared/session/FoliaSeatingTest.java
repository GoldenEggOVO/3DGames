package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaSeatingTest {
    private static final class Fixture implements AutoCloseable {
        final TDGamesPlugin plugin = SessionTestSupport.plugin();
        final PlatformScheduler scheduler = mock(PlatformScheduler.class);
        final TableWorldController tables = mock(TableWorldController.class);
        final GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 25, 0, Map.of("first", "opponent"));
        final Player host = mock(Player.class);
        final Location before = new Location(mock(World.class), 0, 80, 2);
        final Location target = new Location(before.getWorld(), 0, 80, -2);
        final List<Consumer<Boolean>> callbacks = new ArrayList<>();
        final List<Runnable> timeouts = new ArrayList<>();
        final MockedStatic<Bukkit> bukkit;
        Fixture() {
            when(host.getUniqueId()).thenReturn(UUID.randomUUID());
            room.join(host.getUniqueId(), "Host");
            room.fillBots();
            SessionTestSupport.install(plugin, Map.of(room.id, room));
            var service = plugin.games();
            doCallRealMethod().when(service).session(any(Player.class));
            doReturn(scheduler).when(plugin).scheduler();
            when(scheduler.folia()).thenReturn(true);
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Entity.class), any(Runnable.class));
            doAnswer(call -> { timeouts.add(call.getArgument(0)); return null; })
                    .when(scheduler).later(any(Runnable.class), anyLong());
            doAnswer(call -> { callbacks.add(call.getArgument(2)); return null; })
                    .when(scheduler).teleport(eq(host), any(Location.class), any());
            SessionTestSupport.module(plugin, "tableWorld", tables);
            when(plugin.tableWorld().atTableWorld(host, room)).thenReturn(true);
            when(plugin.tableWorld().seatLocation(room, 1)).thenReturn(target);
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(tables).atTable(eq(room), any(Runnable.class), any(Runnable.class));
            when(plugin.allowed(host)).thenReturn(true);
            when(host.getLocation()).thenReturn(before);
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(() -> Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
        }
        boolean prepare() { return plugin.multiplayer().prepareSeats(room); }
        @Override public void close() { bukkit.close(); }
    }

    @Test void waitsForTeleportBeforeStartingAndIgnoresDuplicateStart() {
        try (var f = new Fixture()) {
            assertFalse(f.prepare());
            assertFalse(f.prepare());
            verify(f.plugin.games(), never()).start(f.room);
            assertEquals(1, f.callbacks.size());
            f.callbacks.getFirst().accept(true);
            verify(f.plugin.games()).start(f.room);
            f.timeouts.getFirst().run();
            verify(f.plugin.games(), times(1)).start(f.room);
            verify(f.host, never()).teleport(any(Location.class));
        }
    }

    @Test void rejectedTeleportRestoresSeatsAndNeverStarts() {
        try (var f = new Fixture()) {
            var previous = List.copyOf(f.room.seats);
            f.prepare();
            f.callbacks.getFirst().accept(false);
            assertEquals(previous, f.room.seats);
            assertTrue(f.room.ready.isEmpty());
            verify(f.plugin.games(), never()).start(any());
            verify(f.scheduler).teleport(eq(f.host), eq(f.before), any());
        }
    }

    @Test void changedStakeOrMembershipCannotStartAnOldTransfer() {
        try (var f = new Fixture()) {
            f.prepare();
            SessionTestSupport.bump(f.room);
            f.callbacks.getFirst().accept(true);
            verify(f.plugin.games(), never()).start(any());
            assertEquals(0, f.room.seat(f.host.getUniqueId()));
        }
    }

    @Test void disconnectAndRetiredCallbackCannotLeaveSeatingPending() {
        try (var f = new Fixture()) {
            f.prepare();
            f.plugin.multiplayer().cancelSeating(f.host.getUniqueId());
            assertEquals(0, f.room.seat(f.host.getUniqueId()));
            f.timeouts.getFirst().run();
            verify(f.plugin.games(), never()).start(any());
            int completed = f.callbacks.size();
            assertFalse(f.prepare());
            assertEquals(completed + 1, f.callbacks.size());
        }
    }

    @Test void tablePreparationFailureDoesNotLeaveSeatingPending() {
        try (var f = new Fixture()) {
            doAnswer(call -> { call.<Runnable>getArgument(2).run(); return null; })
                    .when(f.tables).atTable(eq(f.room), any(Runnable.class), any(Runnable.class));
            f.prepare();
            assertEquals(0, f.room.seat(f.host.getUniqueId()));
            verify(f.plugin.games(), never()).start(any());
            assertFalse(f.prepare());
            assertEquals(2, f.timeouts.size());
        }
    }

    @Test void timedOutTeleportThatFinishesLateCannotStartAndReturnsToOriginalSeat() {
        try (var f = new Fixture()) {
            f.prepare();
            f.timeouts.getFirst().run();
            int before = f.callbacks.size();
            f.callbacks.getFirst().accept(true);
            assertEquals(0, f.room.seat(f.host.getUniqueId()));
            verify(f.plugin.games(), never()).start(any());
            assertEquals(before + 1, f.callbacks.size());
            verify(f.scheduler, times(2)).teleport(eq(f.host), eq(f.before), any());
        }
    }
}

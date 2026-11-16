package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableChunkOwnershipTest {
    @TempDir Path directory;
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void expiredPreparationCannotRemoveReplacementPendingTicket() {
        var fixture = new Fixture();
        var old = fixture.room(0);
        var oldLoads = fixture.prepare(old, () -> fail("Removed room must not render"), () -> {});
        oldLoads.getFirst().complete(fixture.chunks[0]);
        fixture.remove(old);
        var replacement = fixture.room(0);
        var rendered = new AtomicBoolean();
        var newLoads = fixture.prepare(replacement, () -> rendered.set(true), () -> fail("Replacement must survive"));
        newLoads.getFirst().complete(fixture.chunks[0]);
        assertTrue(fixture.tickets[0].get());

        oldLoads.getLast().complete(fixture.chunks[1]);

        assertTrue(fixture.tickets[0].get(), "Old completion removed the replacement's pending ticket");
        assertFalse(rendered.get());
        newLoads.getLast().complete(fixture.chunks[1]);
        assertTrue(rendered.get());
        fixture.remove(replacement);
        fixture.assertReleased();
    }

    @Test void expiredPreparationAndRepeatedRemovalCannotRemoveRegisteredReplacement() {
        var fixture = new Fixture();
        var old = fixture.room(0);
        var oldLoads = fixture.prepare(old, () -> fail("Removed room must not render"), () -> {});
        oldLoads.getFirst().complete(fixture.chunks[0]);
        fixture.remove(old);
        var replacement = fixture.room(0);
        var newLoads = fixture.prepare(replacement, () -> {}, () -> fail("Replacement must survive"));
        fixture.complete(newLoads);

        oldLoads.getLast().complete(fixture.chunks[1]);
        fixture.controller.remove(old);

        assertTrue(fixture.tickets[0].get());
        assertTrue(fixture.tickets[1].get());
        fixture.remove(replacement);
        fixture.assertReleased();
    }

    @Test void overlappingRoomsReleaseOnlyTheirLastChunkOwner() {
        var fixture = new Fixture();
        var first = fixture.room(0);
        fixture.complete(fixture.prepare(first, () -> {}, () -> fail("First room failed")));
        var second = fixture.room(1);
        fixture.complete(fixture.prepare(second, () -> {}, () -> fail("Second room failed")));

        fixture.remove(first);
        assertTrue(fixture.tickets[0].get());
        assertTrue(fixture.tickets[1].get());
        fixture.remove(second);
        fixture.assertReleased();
        verify(fixture.chunks[0], times(1)).removePluginChunkTicket(fixture.plugin);
        verify(fixture.chunks[1], times(1)).removePluginChunkTicket(fixture.plugin);
    }

    @Test void partialFailureReleasesSuccessfulTicketsAndRollsBackOnce() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var rolledBack = new AtomicBoolean();
        var loads = fixture.prepare(room, () -> fail("Partially loaded room must not render"), () -> rolledBack.set(true));
        loads.getFirst().complete(fixture.chunks[0]);
        loads.getLast().completeExceptionally(new IllegalStateException("load failed"));

        assertTrue(rolledBack.get());
        fixture.assertReleased();
        verify(fixture.chunks[0]).removePluginChunkTicket(fixture.plugin);
        verify(fixture.chunks[1], never()).addPluginChunkTicket(fixture.plugin);
        verify(fixture.plugin.games()).pause(room, "error.invalid");
    }

    @Test void removalReleasesPartialLoadImmediatelyAndLateCompletionCannotReacquire() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var failed = mock(Runnable.class);
        var loads = fixture.prepare(room, () -> fail("Removed room must not render"), failed);
        loads.getFirst().complete(fixture.chunks[0]);

        fixture.remove(room);
        assertFalse(fixture.tickets[0].get(), "Removal must release already acquired partial tickets");
        loads.getLast().complete(fixture.chunks[1]);

        fixture.assertReleased();
        verify(fixture.chunks[1], never()).addPluginChunkTicket(fixture.plugin);
        verify(failed, times(1)).run();
    }

    @Test void oldCompletionCannotReleaseNewRetentionOfTheSameRoomObject() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var oldLoads = fixture.prepare(room, () -> fail("Old preparation must not render"), () -> {});
        oldLoads.getFirst().complete(fixture.chunks[0]);
        fixture.controller.remove(room);
        var rendered = new AtomicBoolean();
        var newLoads = fixture.prepare(room, () -> rendered.set(true), () -> fail("New retention must survive"));
        newLoads.getFirst().complete(fixture.chunks[0]);

        oldLoads.getLast().complete(fixture.chunks[1]);
        assertTrue(fixture.tickets[0].get());
        newLoads.getLast().complete(fixture.chunks[1]);

        assertTrue(rendered.get());
        fixture.remove(room);
        fixture.assertReleased();
    }

    @Test void deferredRemovalRechecksNewPendingOwnerOnTheChunkThread() {
        var fixture = new Fixture();
        var old = fixture.room(0);
        var oldLoads = fixture.prepare(old, () -> fail("Removed room must not render"), () -> {});
        oldLoads.getFirst().complete(fixture.chunks[0]);
        when(fixture.scheduler.isOwned(any(Location.class))).thenReturn(false);
        List<Runnable> queued = new ArrayList<>();
        doAnswer(call -> { queued.add(call.getArgument(1)); return null; })
                .when(fixture.scheduler).execute(any(Location.class), any(Runnable.class));
        fixture.remove(old);
        var replacement = fixture.room(0);
        var newLoads = fixture.prepare(replacement, () -> {}, () -> fail("Replacement must survive"));
        newLoads.getFirst().complete(fixture.chunks[0]);
        assertEquals(2, queued.size());

        queued.getLast().run();
        queued.getFirst().run();

        assertTrue(fixture.tickets[0].get(), "Deferred removal ignored a newly acquired pending owner");
        when(fixture.scheduler.isOwned(any(Location.class))).thenReturn(true);
        fixture.executeImmediately();
        newLoads.getLast().complete(fixture.chunks[1]);
        oldLoads.getLast().complete(fixture.chunks[1]);
        fixture.remove(replacement);
        fixture.assertReleased();
    }

    @Test void queuedAcquisitionChecksCancellationBeforeAddingATicket() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var failed = mock(Runnable.class);
        var loads = fixture.prepare(room, () -> fail("Removed room must not render"), failed);
        List<Runnable> queued = new ArrayList<>();
        doAnswer(call -> { queued.add(call.getArgument(1)); return null; })
                .when(fixture.scheduler).execute(any(Location.class), any(Runnable.class));
        loads.getFirst().complete(fixture.chunks[0]);
        fixture.remove(room);
        fixture.executeImmediately();
        queued.getFirst().run();
        loads.getLast().complete(fixture.chunks[1]);

        fixture.assertReleased();
        for (var chunk : fixture.chunks) verify(chunk, never()).addPluginChunkTicket(fixture.plugin);
        verify(failed, times(1)).run();
    }

    @Test void addTicketFailureReleasesTheOtherSuccessfullyAcquiredChunk() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var failed = mock(Runnable.class);
        doThrow(new IllegalStateException("add failed")).when(fixture.chunks[1]).addPluginChunkTicket(fixture.plugin);
        var loads = fixture.prepare(room, () -> fail("Partially retained room must not render"), failed);

        fixture.complete(loads);

        fixture.assertReleased();
        verify(failed, times(1)).run();
        verify(fixture.chunks[0]).removePluginChunkTicket(fixture.plugin);
        verify(fixture.chunks[1], never()).removePluginChunkTicket(fixture.plugin);
    }

    @Test void enabledCloseReleasesPartialTicketsAndCancelsLateAcquisition() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var loads = fixture.prepare(room, () -> fail("Closed controller must not render"), () -> {});
        loads.getFirst().complete(fixture.chunks[0]);

        fixture.controller.close();
        fixture.assertReleased();
        loads.getLast().complete(fixture.chunks[1]);

        fixture.assertReleased();
        verify(fixture.chunks[1], never()).addPluginChunkTicket(fixture.plugin);
    }

    @Test void disabledCloseAndLateCompletionDoNotReadChunksOrScheduleTasks() {
        var fixture = new Fixture();
        var room = fixture.room(0);
        var loads = fixture.prepare(room, () -> fail("Disabled controller must not render"), () -> {});
        loads.getFirst().complete(fixture.chunks[0]);
        when(fixture.plugin.isEnabled()).thenReturn(false);
        clearInvocations(fixture.scheduler, fixture.chunks[0], fixture.chunks[1]);

        fixture.controller.close();
        loads.getLast().complete(fixture.chunks[1]);

        verify(fixture.scheduler, never()).execute(any(Location.class), any(Runnable.class));
        verifyNoInteractions(fixture.chunks[0], fixture.chunks[1]);
    }

    @Test void standardServerKeepsSynchronousRetentionAndSharedTicketRelease() {
        var fixture = new Fixture();
        when(fixture.scheduler.folia()).thenReturn(false);
        when(fixture.world.getChunkAt(0, 0)).thenReturn(fixture.chunks[0]);
        when(fixture.world.getChunkAt(1, 0)).thenReturn(fixture.chunks[1]);
        var first = fixture.room(0);
        fixture.controller.retainTableChunks(first.table);
        var second = fixture.room(1);
        fixture.controller.retainTableChunks(second.table);
        var rendered = new AtomicBoolean();
        fixture.controller.atTable(second, () -> rendered.set(true));

        assertTrue(rendered.get());
        verify(fixture.world, never()).getChunkAtAsync(anyInt(), anyInt());
        fixture.remove(first);
        assertTrue(fixture.tickets[0].get());
        assertTrue(fixture.tickets[1].get());
        fixture.remove(second);
        fixture.assertReleased();
    }

    private final class Fixture {
        final TDGamesPlugin plugin = SessionTestSupport.plugin();
        final PlatformScheduler scheduler = mock(PlatformScheduler.class);
        final World world = mock(World.class);
        final Chunk[] chunks = {mock(Chunk.class), mock(Chunk.class)};
        final AtomicBoolean[] tickets = {new AtomicBoolean(), new AtomicBoolean()};
        final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        final TableWorldController controller;

        Fixture() {
            var server = MockBukkit.getMock();
            when(plugin.getName()).thenReturn("3DGames");
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            doReturn(scheduler).when(plugin).scheduler();
            when(scheduler.folia()).thenReturn(true);
            when(scheduler.isOwned(any(Location.class))).thenReturn(true);
            executeImmediately();
            for (int index = 0; index < chunks.length; index++) {
                int chunkIndex = index;
                when(chunks[index].getWorld()).thenReturn(world);
                when(chunks[index].getX()).thenReturn(index);
                when(chunks[index].addPluginChunkTicket(plugin)).thenAnswer(call -> !tickets[chunkIndex].getAndSet(true));
                when(chunks[index].removePluginChunkTicket(plugin)).thenAnswer(call -> tickets[chunkIndex].getAndSet(false));
            }
            when(world.getChunkAtAsync(anyInt(), anyInt())).thenAnswer(call -> {
                var future = new CompletableFuture<Chunk>();
                loads.add(future);
                return future;
            });
            controller = spy(new TableWorldController(plugin));
            doReturn(new Location(world, 15, 80, 8)).when(controller).center(anyInt());
        }

        GameSession room(int table) {
            var room = new GameSession(UUID.randomUUID(), "chess", 2, 0, table);
            SessionTestSupport.mutableSessions(plugin).put(room.id, room);
            return room;
        }

        void executeImmediately() {
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Location.class), any(Runnable.class));
        }

        List<CompletableFuture<Chunk>> prepare(GameSession room, Runnable action, Runnable failed) {
            int before = loads.size();
            controller.atTable(room, action, failed);
            assertEquals(2, loads.size() - before);
            return List.copyOf(loads.subList(before, loads.size()));
        }

        void complete(List<CompletableFuture<Chunk>> pending) {
            for (int index = 0; index < chunks.length; index++) pending.get(index).complete(chunks[index]);
        }

        void remove(GameSession room) {
            SessionTestSupport.mutableSessions(plugin).remove(room.id);
            controller.remove(room);
        }

        void assertReleased() {
            for (var ticket : tickets) assertFalse(ticket.get(), "Chunk ticket leaked");
        }
    }
}

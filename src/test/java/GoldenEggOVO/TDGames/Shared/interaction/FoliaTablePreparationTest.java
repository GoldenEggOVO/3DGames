package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaTablePreparationTest {
    @TempDir Path directory;
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void waitsForEveryChunkAndRechecksOwnershipForAlreadyLoadedTables() {
        var plugin = SessionTestSupport.plugin();
        var server = MockBukkit.getMock();
        var world = mock(World.class);
        when(plugin.getName()).thenReturn("3DGames");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
        var scheduler = mock(PlatformScheduler.class);
        doReturn(scheduler).when(plugin).scheduler();
        when(scheduler.folia()).thenReturn(true);
        var owned = new AtomicBoolean(false);
        when(scheduler.isOwned(any(Location.class))).thenAnswer(call -> owned.get());
        doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).execute(any(Location.class), any(Runnable.class));
        List<Runnable> retries = new ArrayList<>();
        doAnswer(call -> { retries.add(call.getArgument(1)); return null; })
                .when(scheduler).later(any(Location.class), any(Runnable.class), eq(1L));
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 1, 0);
        room.anchorWorld = world.getUID(); room.anchorX = 15; room.anchorY = 80; room.anchorZ = 15;
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        var controller = spy(new TableWorldController(plugin));
        doReturn(new Location(world, 8, 80, 8)).when(controller).center(anyInt());
        var chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world); when(chunk.getX()).thenReturn(1); when(chunk.getZ()).thenReturn(1);
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
        var ran = new AtomicBoolean();
        controller.atTable(room, () -> ran.set(true));
        assertFalse(ran.get());
        owned.set(true); retries.removeFirst().run();
        assertTrue(ran.get());
        ran.set(false); owned.set(false);
        controller.atTable(room, () -> ran.set(true));
        assertFalse(ran.get());
        owned.set(true); retries.removeFirst().run();
        assertTrue(ran.get());
        verify(world, times(1)).getChunkAtAsync(anyInt(), anyInt());
    }

    @Test void failedPreparationReleasesSuccessfulPartialTicketsAndCallsRollback() {
        var plugin = SessionTestSupport.plugin();
        var server = MockBukkit.getMock();
        var world = mock(World.class);
        when(plugin.getName()).thenReturn("3DGames");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var scheduler = mock(PlatformScheduler.class);
        doReturn(scheduler).when(plugin).scheduler();
        when(scheduler.folia()).thenReturn(true);
        when(scheduler.isOwned(any(Location.class))).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).execute(any(Location.class), any(Runnable.class));
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 1, 0);
        room.anchorWorld = world.getUID(); room.anchorY = 80;
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        var controller = spy(new TableWorldController(plugin));
        doReturn(new Location(world, 15, 80, 8)).when(controller).center(anyInt());
        var chunk = mock(Chunk.class); when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAtAsync(0, 0)).thenReturn(CompletableFuture.completedFuture(chunk));
        when(world.getChunkAtAsync(1, 0)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("load failed")));
        var rollback = new AtomicBoolean();
        controller.atTable(room, () -> fail("Must not render a partially loaded table"), () -> rollback.set(true));
        assertTrue(rollback.get());
        verify(chunk).removePluginChunkTicket(plugin);
        verify(plugin.games()).pause(room, "error.invalid");
    }

    @Test void shutdownDoesNotRemoveEntitiesOrTouchChunksEvenWhenOwnershipReportsTrue() throws Exception {
        var plugin = SessionTestSupport.plugin();
        var server = MockBukkit.getMock();
        when(plugin.getName()).thenReturn("3DGames");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
        var scheduler = mock(PlatformScheduler.class);
        doReturn(scheduler).when(plugin).scheduler();
        when(scheduler.folia()).thenReturn(true);
        when(scheduler.isOwned(any(Location.class))).thenReturn(true);
        var controller = new TableWorldController(plugin);
        var view = mock(GoldenEggOVO.TDGames.Shared.render.TableView.class);
        var viewField = TableWorldController.class.getDeclaredField("views"); viewField.setAccessible(true);
        @SuppressWarnings("unchecked") var views = (Map<UUID, GoldenEggOVO.TDGames.Shared.render.TableView>) viewField.get(controller);
        views.put(UUID.randomUUID(), view);
        var chunk = mock(Chunk.class);
        var chunkField = TableWorldController.class.getDeclaredField("tableChunks"); chunkField.setAccessible(true);
        @SuppressWarnings("unchecked") var chunks = (Map<GameSession, Set<Chunk>>) chunkField.get(controller);
        chunks.put(new GameSession(UUID.randomUUID(), "chess", 2, 0, 0), Set.of(chunk));
        when(plugin.isEnabled()).thenReturn(false);
        assertDoesNotThrow(controller::close);
        verify(view, never()).close();
        verifyNoInteractions(chunk);
        assertTrue(views.isEmpty()); assertTrue(chunks.isEmpty());
    }
}

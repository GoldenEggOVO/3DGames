package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.SessionTestSupport;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObserveTeleportTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void foliaVisitRejectsFeetOrHeadBlockedWhileWaitingForTheTargetRegion(int blocked) throws Exception {
        var f = new FoliaVisit();
        f.clickTwice();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        assertEquals(1, f.targetTasks.size());
        when((blocked == 0 ? f.feet : f.head).isPassable()).thenReturn(false);
        f.targetTasks.getFirst().run();
        assertTrue(f.playerTasks.isEmpty());
        verify(f.scheduler, never()).teleport(any(), any(), any());
        verify(f.base.plugin).tell(f.base.player, GoldenEggOVO.TDGames.Core.text.Language.component("error.the-seat-is-blocked-choose-a-more"));
        f.assertNoJoinOrMenu();
    }

    @Test
    void foliaVisitRunsSpaceChecksBeforeDispatchingToThePlayerOwner() throws Exception {
        var f = new FoliaVisit();
        f.clickTwice();
        verify(f.feet, never()).isPassable();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        f.targetTasks.getFirst().run();
        verify(f.feet).isPassable();
        verify(f.head).isPassable();
        assertEquals(1, f.playerTasks.size());
        verify(f.scheduler, never()).teleport(any(), any(), any());
        f.playerTasks.getFirst().run();
        verify(f.scheduler).teleport(eq(f.base.player), eq(f.destination), any());
        f.assertNoJoinOrMenu();
    }

    @Test
    void foliaVisitRejectsARoomDeletedBeforeItsTargetRegionTask() throws Exception {
        var f = new FoliaVisit();
        f.clickTwice();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        SessionTestSupport.mutableSessions(f.base.plugin).remove(f.room.id);
        f.targetTasks.getFirst().run();
        assertTrue(f.playerTasks.isEmpty());
        verify(f.scheduler, never()).teleport(any(), any(), any());
        verify(f.feet, never()).isPassable();
        verify(f.base.plugin).tell(f.base.player, GoldenEggOVO.TDGames.Core.text.Language.component("error.page-changed"));
        f.assertNoJoinOrMenu();
    }

    @Test
    void foliaVisitRejectsARoomDeletedWhileWaitingForThePlayerOwner() throws Exception {
        var f = new FoliaVisit();
        f.clickTwice();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        f.targetTasks.getFirst().run();
        SessionTestSupport.mutableSessions(f.base.plugin).remove(f.room.id);
        f.playerTasks.getFirst().run();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        verify(f.base.plugin).tell(f.base.player, GoldenEggOVO.TDGames.Core.text.Language.component("error.page-changed"));
        f.assertNoJoinOrMenu();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foliaVisitDoesNotMoveAPlayerWhoJoinedAnotherRoomWhileWaiting(boolean waitingForPlayerOwner) throws Exception {
        var f = new FoliaVisit();
        f.clickTwice();
        if (waitingForPlayerOwner) f.targetTasks.getFirst().run();
        var own = f.base.addRoom(1);
        own.join(f.base.player.getUniqueId(), "Observer");
        assertSame(own, f.base.plugin.games().session(f.base.player));
        if (!waitingForPlayerOwner) f.targetTasks.getFirst().run();
        assertEquals(1, f.playerTasks.size());
        f.playerTasks.getFirst().run();
        verify(f.scheduler, never()).teleport(any(), any(), any());
        verify(f.base.plugin).tell(f.base.player, GoldenEggOVO.TDGames.Core.text.Language.component("chat.observe.own-game"));
        assertSame(own, f.base.plugin.games().session(f.base.player));
        f.assertNoJoinOrMenu();
    }

    private static final class FoliaVisit {
        final MenuFlowTest.Fixture base = new MenuFlowTest.Fixture();
        final PlatformScheduler scheduler = mock(PlatformScheduler.class);
        final GoldenEggOVO.TDGames.Shared.session.GameSession room = base.addRoom(0);
        final Location destination = new Location(base.player.getWorld(), 10, 80, 10);
        final Block feet = mock(Block.class), head = mock(Block.class);
        final List<Runnable> targetTasks = new ArrayList<>(), playerTasks = new ArrayList<>();

        FoliaVisit() throws Exception {
            SessionTestSupport.set(room, "board", GameFactory.create(room.kind, room.capacity, 7));
            when(base.plugin.scheduler()).thenReturn(scheduler);
            when(scheduler.folia()).thenReturn(true);
            var tables = mock(GoldenEggOVO.TDGames.Shared.interaction.TableWorldController.class);
            when(base.plugin.tableWorld()).thenReturn(tables);
            when(tables.seatLocation(room, 0)).thenReturn(destination);
            when(feet.isPassable()).thenReturn(true);
            when(head.isPassable()).thenReturn(true);
            when(base.player.getWorld().getBlockAt(any(Location.class)))
                    .thenAnswer(call -> ((Location) call.getArgument(0)).getBlockY() == 80 ? feet : head);
            doAnswer(call -> { targetTasks.add(call.getArgument(1)); return null; })
                    .when(scheduler).execute(any(Location.class), any(Runnable.class));
            doAnswer(call -> { playerTasks.add(call.getArgument(1)); return null; })
                    .when(scheduler).execute(eq(base.player), any(Runnable.class));
        }

        @SuppressWarnings("unchecked")
        void clickTwice() throws Exception {
            base.menus.observe(base.player, room);
            var field = GameMenus.class.getDeclaredField("sessions"); field.setAccessible(true);
            var sessions = (Map<UUID, GameMenus.Session>) field.get(base.menus);
            var session = sessions.get(base.player.getUniqueId());
            String visit = GoldenEggOVO.TDGames.Core.text.MessageText.plain(
                    GoldenEggOVO.TDGames.Core.text.Language.component("menu.observe.visit"));
            int index = java.util.stream.IntStream.range(0, session.buttons().size())
                    .filter(i -> session.buttons().get(i).label().equals(visit)).findFirst().orElseThrow();
            String action = "3dgames:" + session.token() + " " + index;
            base.menus.handle(base.player, action); base.menus.handle(base.player, action);
        }

        void assertNoJoinOrMenu() {
            assertFalse(base.menus.active(base.player));
            assertEquals(-1, room.seat(base.player.getUniqueId()));
            verify(base.plugin.games(), never()).join(any(), any());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void visitConsumesItsTokenBeforeTeleportAndFailureDoesNotJoinOrReopen() throws Exception {
        var f = new MenuFlowTest.Fixture();
        var scheduler = mock(PlatformScheduler.class);
        when(f.plugin.scheduler()).thenReturn(scheduler);
        var room = f.addRoom(0);
        SessionTestSupport.set(room, "board", GameFactory.create(room.kind, room.capacity, 7));
        Location destination = new Location(f.player.getWorld(), 10, 80, 10);
        var tables = mock(GoldenEggOVO.TDGames.Shared.interaction.TableWorldController.class);
        when(f.plugin.tableWorld()).thenReturn(tables);
        when(f.plugin.tableWorld().seatLocation(room, 0)).thenReturn(destination);
        f.menus.observe(f.player, room);
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        var sessions = (Map<UUID, GameMenus.Session>) field.get(f.menus);
        var session = sessions.get(f.player.getUniqueId());
        String visit = GoldenEggOVO.TDGames.Core.text.MessageText.plain(
                GoldenEggOVO.TDGames.Core.text.Language.component("menu.observe.visit"));
        int index = java.util.stream.IntStream.range(0, session.buttons().size())
                .filter(i -> session.buttons().get(i).label().equals(visit)).findFirst().orElseThrow();
        String action = "3dgames:" + session.token() + " " + index;
        f.menus.handle(f.player, action);
        f.menus.handle(f.player, action);
        assertFalse(f.menus.active(f.player));
        ArgumentCaptor<Consumer<Boolean>> completed = ArgumentCaptor.forClass(Consumer.class);
        verify(scheduler).teleport(eq(f.player), eq(destination), completed.capture());
        completed.getValue().accept(false);
        assertFalse(f.menus.active(f.player));
        assertEquals(-1, room.seat(f.player.getUniqueId()));
        verify(f.player, never()).teleport(any(Location.class));
        verify(f.plugin.games(), never()).join(any(), any());
    }
}

package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Core.render.PlayerViews;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaArenaTransferTest {
    private static final class Fixture {
        final TDGamesPlugin plugin = SessionTestSupport.plugin();
        final PlatformScheduler scheduler = mock(PlatformScheduler.class);
        final TableWorldController tables = mock(TableWorldController.class);
        final GameMenus menus = mock(GameMenus.class);
        final Player player = mock(Player.class);
        final GameSession room = new GameSession(UUID.randomUUID(), "chess", 2, 1, 0);
        final List<Consumer<Boolean>> callbacks = new ArrayList<>();
        final List<Runnable> timeouts = new ArrayList<>();
        Fixture() {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            room.join(UUID.randomUUID(), "Host"); room.join(player.getUniqueId(), "Guest");
            SessionTestSupport.install(plugin, Map.of(room.id, room));
            SessionTestSupport.module(plugin, "tableWorld", tables);
            SessionTestSupport.module(plugin, "menus", menus);
            SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
            doReturn(mock(PlayerViews.class)).when(plugin).playerViews();
            doReturn(scheduler).when(plugin).scheduler();
            when(scheduler.folia()).thenReturn(true);
            when(scheduler.isOwned(player)).thenReturn(true);
            when(plugin.allowed(player)).thenReturn(true);
            var world = mock(World.class); var block = mock(org.bukkit.block.Block.class);
            when(block.isPassable()).thenReturn(true); when(world.getBlockAt(any(Location.class))).thenReturn(block);
            var location = new Location(world, 0, 80, 0);
            when(player.getLocation()).thenReturn(location);
            when(tables.seatLocation(room, 1)).thenReturn(location);
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(tables).atTable(eq(room), any(Runnable.class), any(Runnable.class));
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Location.class), any(Runnable.class));
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                    .when(scheduler).execute(any(Entity.class), any(Runnable.class));
            doAnswer(call -> { timeouts.add(call.getArgument(0)); return null; })
                    .when(scheduler).later(any(Runnable.class), anyLong());
            doAnswer(call -> { callbacks.add(call.getArgument(2)); return null; })
                    .when(scheduler).teleport(eq(player), any(Location.class), any());
        }
        SessionRuntime runtime() { return plugin.games().runtime(); }
    }

    @Test void returningToGameDoesNotReopenTheMenuAfterAsyncTeleport() {
        var f = new Fixture();
        assertTrue(f.runtime().enterArena(f.player, f.room));
        assertTrue(f.runtime().transferring(f.player.getUniqueId()));
        f.callbacks.getFirst().accept(true);
        assertFalse(f.runtime().transferring(f.player.getUniqueId()));
        assertFalse(f.room.offline.containsKey(f.player.getUniqueId()));
        verify(f.plugin.playerViews()).capture(f.player);
        verifyNoInteractions(f.menus);
    }

    @Test void failedJoinReleasesOnlyTheNewSeatAndReservation() {
        var f = new Fixture();
        f.runtime().enterJoinedArena(f.player, f.room, true);
        f.callbacks.getFirst().accept(false);
        assertEquals(-1, f.room.seat(f.player.getUniqueId()));
        assertEquals(1, f.room.seats.size());
        verify(f.plugin.sessions()).release(f.player.getUniqueId(), f.room.kind);
        verifyNoInteractions(f.menus);
    }

    @Test void retiredPlayerCallbackTimesOutAndCannotRetainTheTransferLock() {
        var f = new Fixture();
        f.runtime().enterArena(f.player, f.room);
        f.timeouts.getFirst().run();
        assertFalse(f.runtime().transferring(f.player.getUniqueId()));
        f.callbacks.getFirst().accept(true);
        verifyNoInteractions(f.menus);
    }
}

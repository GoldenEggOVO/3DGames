package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDGames.SessionTestSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisplayMovementTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void crossRegionMovesKeepOnlyTheNewestTargetWhileAnEarlierMoveIsInFlight() {
        var plugin = SessionTestSupport.plugin();
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(scheduler.folia()).thenReturn(true);
        var entity = mock(Entity.class);
        var targets = new ArrayList<Location>();
        var completions = new ArrayList<Consumer<Boolean>>();
        doAnswer(call -> {
            targets.add(call.getArgument(1));
            completions.add(call.getArgument(2));
            return null;
        }).when(scheduler).teleport(eq(entity), any(Location.class), any());
        var world = mock(World.class);
        DisplayMovement.move(plugin, entity, new Location(world, 1, 80, 0));
        DisplayMovement.move(plugin, entity, new Location(world, 2, 80, 0));
        DisplayMovement.move(plugin, entity, new Location(world, 3, 80, 0));
        assertEquals(1, targets.size());
        completions.getFirst().accept(true);
        assertEquals(2, targets.size());
        assertEquals(3, targets.getLast().getX());
        completions.getLast().accept(true);
        verify(entity, never()).teleport(any(Location.class));
    }

    @Test void retiringADisplayDuringAnAsyncMoveRemovesItOnCompletionWithoutResending() {
        var plugin = SessionTestSupport.plugin();
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(scheduler.folia()).thenReturn(true);
        var entity = mock(Entity.class);
        var completions = new ArrayList<Consumer<Boolean>>();
        doAnswer(call -> { completions.add(call.getArgument(2)); return null; })
                .when(scheduler).teleport(eq(entity), any(Location.class), any());
        DisplayMovement.move(plugin, entity, new Location(mock(World.class), 1, 80, 0));
        DisplayAudience.retire(entity);
        verify(entity, never()).remove();
        completions.getFirst().accept(true);
        completions.getFirst().accept(true);
        verify(entity, times(1)).remove();
        verify(scheduler, times(1)).teleport(eq(entity), any(Location.class), any());
    }

    @Test void disablingDuringAnAsyncMoveClearsTheCallbackWithoutTouchingTheEntity() {
        var plugin = SessionTestSupport.plugin();
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(scheduler.folia()).thenReturn(true);
        var entity = mock(Entity.class);
        var completions = new ArrayList<Consumer<Boolean>>();
        doAnswer(call -> { completions.add(call.getArgument(2)); return null; })
                .when(scheduler).teleport(eq(entity), any(Location.class), any());
        var target = new Location(mock(World.class), 1, 80, 0);
        DisplayMovement.move(plugin, entity, target);
        when(plugin.isEnabled()).thenReturn(false);
        completions.getFirst().accept(true);
        DisplayMovement.move(plugin, entity, target);
        verify(entity, never()).remove();
        verify(scheduler, times(1)).teleport(eq(entity), any(Location.class), any());
    }
}

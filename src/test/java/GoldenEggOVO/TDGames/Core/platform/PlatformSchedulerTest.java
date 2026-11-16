package GoldenEggOVO.TDGames.Core.platform;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformSchedulerTest {
    @Test void ownedEntityRunsImmediatelyUnderTheStateLock() {
        Plugin plugin = mock(Plugin.class);
        Entity entity = mock(Entity.class);
        AtomicBoolean ran = new AtomicBoolean();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(entity)).thenReturn(true);
            new PlatformScheduler(plugin, true).execute(entity, () -> {
                assertTrue(Thread.holdsLock(plugin));
                ran.set(true);
            });
            assertTrue(ran.get());
            verify(entity, never()).getScheduler();
        }
    }

    @Test void entityWorkUsesEntitySchedulerAndCanBeCancelled() {
        Plugin plugin = mock(Plugin.class);
        Entity entity = mock(Entity.class);
        EntityScheduler owner = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(entity.getScheduler()).thenReturn(owner);
        List<Consumer<ScheduledTask>> callbacks = new ArrayList<>();
        when(owner.runDelayed(eq(plugin), any(), isNull(), eq(1L))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return task;
        });
        AtomicBoolean ran = new AtomicBoolean();
        try (var bukkit = mockStatic(Bukkit.class)) {
            PlatformScheduler.Task scheduled = new PlatformScheduler(plugin, true).execute(entity, () -> {
                assertTrue(Thread.holdsLock(plugin));
                ran.set(true);
            });
            assertFalse(ran.get());
            callbacks.getFirst().accept(task);
            assertTrue(ran.get());
            scheduled.cancel();
            verify(task).cancel();
            bukkit.verify(Bukkit::getGlobalRegionScheduler, never());
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }

    @Test void locationWorkUsesRegionSchedulerAndCopiesTheLocation() {
        Plugin plugin = mock(Plugin.class);
        Location location = new Location(mock(World.class), 12, 80, 25);
        RegionScheduler region = mock(RegionScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(region.runDelayed(eq(plugin), eq(location), any(), eq(2L))).thenReturn(task);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(region);
            new PlatformScheduler(plugin, true).later(location, () -> {}, 2).cancel();
            var captured = org.mockito.ArgumentCaptor.forClass(Location.class);
            verify(region).runDelayed(eq(plugin), captured.capture(), any(), eq(2L));
            assertEquals(location, captured.getValue());
            assertNotSame(location, captured.getValue());
            verify(task).cancel();
            bukkit.verify(Bukkit::getGlobalRegionScheduler, never());
        }
    }

    @Test void globalWorkUsesGlobalSchedulerRatherThanAWorldRegion() {
        Plugin plugin = mock(Plugin.class);
        GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(global.runAtFixedRate(eq(plugin), any(), eq(1L), eq(20L))).thenReturn(task);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            new PlatformScheduler(plugin, true).repeat(() -> {}, 0, 20).cancel();
            verify(global).runAtFixedRate(eq(plugin), any(), eq(1L), eq(20L));
            verify(task).cancel();
            bukkit.verify(Bukkit::getRegionScheduler, never());
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }

    @Test void retiredEntityDoesNotRunTheAction() {
        Plugin plugin = mock(Plugin.class);
        Entity entity = mock(Entity.class);
        when(entity.getScheduler()).thenReturn(mock(EntityScheduler.class));
        AtomicBoolean ran = new AtomicBoolean();
        try (var bukkit = mockStatic(Bukkit.class)) {
            new PlatformScheduler(plugin, true).execute(entity, () -> ran.set(true)).cancel();
            assertFalse(ran.get());
        }
    }

    @Test void asynchronousTeleportCompletionIsRoutedBackToTheEntityOwner() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        Entity entity = mock(Entity.class);
        EntityScheduler owner = mock(EntityScheduler.class);
        when(entity.getScheduler()).thenReturn(owner);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Location target = new Location(mock(World.class), 256, 80, 256);
        when(entity.teleportAsync(eq(target))).thenReturn(result);
        List<Consumer<ScheduledTask>> pending = new ArrayList<>();
        when(owner.runDelayed(eq(plugin), any(), isNull(), eq(1L))).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
        AtomicBoolean callback = new AtomicBoolean();
        try (var bukkit = mockStatic(Bukkit.class)) {
            new PlatformScheduler(plugin, true).teleport(entity, target, success -> {
                assertTrue(success);
                assertTrue(Thread.holdsLock(plugin));
                callback.set(true);
            });
            verify(entity, never()).teleportAsync(any());
            pending.removeFirst().accept(mock(ScheduledTask.class));
            result.complete(true);
            assertFalse(callback.get());
            pending.removeFirst().accept(mock(ScheduledTask.class));
            assertTrue(callback.get());
            verify(entity, never()).teleport(any(Location.class));
        }
    }

    @Test void ordinaryServersRetainSynchronousTeleportAndTheBukkitScheduler() {
        Plugin plugin = mock(Plugin.class);
        Entity entity = mock(Entity.class);
        Location target = new Location(mock(World.class), 0, 80, 0);
        when(entity.teleport(target)).thenReturn(true);
        AtomicBoolean callback = new AtomicBoolean();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlatformScheduler scheduler = new PlatformScheduler(plugin, false);
            scheduler.teleport(entity, target, callback::set);
            assertTrue(callback.get());
            verify(entity).teleport(target);
            verify(entity, never()).teleportAsync(any());
        }
    }
}

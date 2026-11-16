package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerViewsTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void foliaReadsOnlyCapturedPlayerStateAndReturnsDefensiveLocations() {
        var plugin = SessionTestSupport.plugin();
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(scheduler.folia()).thenReturn(true);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).execute(any(Entity.class), any(Runnable.class));
        var world = mock(World.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 1, 80, 2));
        when(player.getEyeHeight()).thenReturn(1.62);
        when(plugin.allowed(player)).thenReturn(true);
        var pack = mock(ResourcePackState.class);
        when(plugin.resourcePackState()).thenReturn(pack);
        when(pack.packed(player)).thenReturn(true);
        var views = new PlayerViews(plugin);
        views.capture(player);
        clearInvocations(player, world);
        var snapshot = views.nearby(new Location(world, 0, 80, 0), 24).getFirst();
        assertTrue(snapshot.allowed());
        assertTrue(snapshot.packed());
        snapshot.location().setX(999);
        snapshot.eyeLocation().setY(999);
        assertEquals(1, views.snapshot(id).orElseThrow().location().getX());
        assertEquals(81.62, views.snapshot(id).orElseThrow().eyeLocation().getY());
        verify(player, never()).getLocation();
        verify(world, never()).getPlayers();
        views.close();
        assertTrue(views.nearby(new Location(world, 0, 80, 0), 24).isEmpty());
    }
}

package GoldenEggOVO.TDGames.Core.render;


import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayAudienceTest {
    private TDGamesPlugin plugin;
    private ResourcePackState pack;
    private World world;
    private Player nativePlayer, packedPlayer;
    private Entity nativePart, packedPart, commonPart;
    private DisplayAudience audience;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        plugin = mock(TDGamesPlugin.class);
        pack = mock(ResourcePackState.class);
        when(pack.managed()).thenReturn(true);
        when(plugin.resourcePackState()).thenReturn(pack);
        world = mock(World.class);
        nativePlayer = player(false);
        packedPlayer = player(true);
        when(world.getPlayers()).thenReturn(List.of(nativePlayer, packedPlayer));
        audience = new DisplayAudience(plugin, new Location(world, 0, 80, 0));
        nativePart = mock(Entity.class);
        packedPart = mock(Entity.class);
        commonPart = mock(Entity.class);
        audience.add(nativePart, false);
        audience.add(packedPart, true);
        audience.common(commonPart);
        clearInvocations(nativePlayer, packedPlayer);
    }

    private Player player(boolean packed) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 0, 81, 0));
        when(plugin.allowed(player)).thenReturn(true);
        when(pack.packed(player)).thenReturn(packed);
        return player;
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void changingBackendUpdatesOnlyThatPlayersChangedLayers() {
        when(pack.packed(nativePlayer)).thenReturn(true);
        audience.refresh();
        assertFalse(audience.needed(false));
        assertTrue(audience.needed(true));
        verify(nativePlayer).hideEntity(plugin, nativePart);
        verify(nativePlayer).showEntity(plugin, packedPart);
        verify(nativePlayer, never()).showEntity(plugin, commonPart);
        verify(nativePlayer, never()).hideEntity(plugin, commonPart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, commonPart);
        verify(packedPlayer, never()).hideEntity(plugin, nativePart);
    }

    @Test
    void leavingRangeHidesVisibleLayersWithoutUpdatingOtherPlayers() {
        when(world.getPlayers()).thenReturn(List.of(packedPlayer));
        audience.refresh();
        assertEquals(java.util.Set.of(packedPlayer), audience.all());
        verify(nativePlayer).hideEntity(plugin, nativePart);
        verify(nativePlayer).hideEntity(plugin, commonPart);
        verify(nativePlayer, never()).hideEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, commonPart);
    }

    @Test
    void unchangedAudienceKeepsGenerationAndSendsNoEntityUpdates() {
        long generation = audience.generation;
        audience.refresh();
        assertEquals(generation, audience.generation);
        verify(nativePlayer, never()).showEntity(plugin, nativePart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(nativePlayer, never()).hideEntity(plugin, nativePart);
    }

    @Test
    void defaultAudienceStillExcludesUnloadedPlayersInRequiredPackMode() {
        when(pack.required()).thenReturn(true);
        audience.refresh();
        assertFalse(audience.needed(false));
        assertEquals(java.util.Set.of(packedPlayer), audience.all());
        verify(nativePlayer).hideEntity(plugin, nativePart);
        verify(nativePlayer).hideEntity(plugin, commonPart);
    }

    @Test
    void nativeFallbackKeepsUnloadedPlayersInRequiredPackMode() {
        when(pack.required()).thenReturn(true);
        var fallback = DisplayAudience.withNativeFallback(plugin, new Location(world, 0, 80, 0));
        assertEquals(java.util.Set.of(nativePlayer, packedPlayer), fallback.all());
        assertTrue(fallback.needed(false));
        assertTrue(fallback.needed(true));
    }

    @Test
    void fallbackTracksNearbyVanillaPlayersEvenWithoutAPackService() {
        when(plugin.resourcePackState()).thenReturn(null);
        when(packedPlayer.getLocation()).thenReturn(new Location(world, 25, 80, 0));
        var fallback = DisplayAudience.withNativeFallback(plugin, new Location(world, 0, 80, 0));
        assertEquals(java.util.Set.of(nativePlayer), fallback.viewers(false));
        assertTrue(fallback.viewers(true).isEmpty());
        when(world.getPlayers()).thenReturn(List.of());
        fallback.refresh();
        assertTrue(fallback.all().isEmpty());
    }

    @Test
    void absentOrVanillaPackUsesOnlyTheDefaultNativeLayer() {
        when(plugin.resourcePackState()).thenReturn(null);
        assertFalse(audience.managed());
        assertFalse(audience.packed(packedPlayer));
        assertTrue(audience.needed(false));
        assertFalse(audience.needed(true));
        when(plugin.resourcePackState()).thenReturn(pack);
        when(pack.managed()).thenReturn(false);
        assertFalse(audience.managed());
        assertTrue(audience.needed(false));
        assertFalse(audience.needed(true));
    }
}

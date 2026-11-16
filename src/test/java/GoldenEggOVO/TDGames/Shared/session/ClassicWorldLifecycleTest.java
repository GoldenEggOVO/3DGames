package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassicWorldLifecycleTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    private TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        var service = new GameService(plugin);
        when(plugin.games()).thenReturn(service);
        SessionTestSupport.set(service, "loaded", true);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.tableWorld()).thenReturn(mock(TableWorldController.class));
        when(plugin.sessions()).thenReturn(mock(PlayerSessions.class));
        return plugin;
    }

    private GameSession room(TDGamesPlugin plugin, UUID world) {
        var room = new GameSession(UUID.randomUUID(), "2048", 1, 42, 0);
        room.join(UUID.randomUUID(), "Player");
        room.anchorWorld = world; room.board = GameFactory.create("2048", 1, 42); room.phase = GameSession.Phase.PLAYING;
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        return room;
    }

    @Test void alreadyUnloadedWorldDoesNotEscapeTickAndSoloProgressIsReleased() {
        var plugin = plugin();
        GameSession room = room(plugin, UUID.randomUUID());
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(room.seats.getFirst().id());
        when(plugin.tableWorld().atTableWorld(player, room)).thenThrow(new IllegalStateException("Table world is not loaded"));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            assertDoesNotThrow(plugin.games()::tick);
        }
        assertTrue(plugin.games().activeSessions().isEmpty());
        assertTrue(plugin.games().hasSoloProgress(player.getUniqueId()));
        verify(plugin.sessions()).release(player.getUniqueId(), "2048");
    }

    @Test void worldUnloadSavesBeforeReleasingItsSoloRooms() {
        var plugin = plugin();
        var world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        GameSession room = room(plugin, world.getUID());
        var event = new WorldUnloadEvent(world);
        try (var bukkit = mockStatic(Bukkit.class)) { plugin.games().worldUnload(event); }
        assertFalse(event.isCancelled());
        assertTrue(plugin.games().activeSessions().isEmpty());
        assertTrue(plugin.games().hasSoloProgress(room.seats.getFirst().id()));
    }

    @Test void failedWorldUnloadSaveRetainsGameAndCancelsUnload() throws Exception {
        var plugin = plugin();
        var world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        GameSession room = room(plugin, world.getUID());
        Files.writeString(directory.resolve("solo"), "blocked");
        var event = new WorldUnloadEvent(world);
        plugin.games().worldUnload(event);
        assertTrue(event.isCancelled());
        assertSame(room, plugin.games().activeSessions().get(room.id));
        verify(plugin.sessions(), never()).release(any(), any());
    }
}

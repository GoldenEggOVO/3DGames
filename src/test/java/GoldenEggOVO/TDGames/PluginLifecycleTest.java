package GoldenEggOVO.TDGames;

import GoldenEggOVO.TDGames.Core.player.PlayerSessions;
import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginLifecycleTest {
    @TempDir Path directory;

    TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var config = new YamlConfiguration();
        config.set("economy.enabled", true);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    @Test void integrationShutdownFailureStillClosesTabletopResources() throws Exception {
        var plugin = plugin();
        SessionTestSupport.install(plugin);
        SessionTestSupport.module(plugin, "integrations", mock(OptionalIntegrations.class));
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        var integrations = plugin.integrations();
        doThrow(new IllegalStateException("Storage failed")).when(integrations).close();
        doCallRealMethod().when(plugin).onDisable();
        assertDoesNotThrow(plugin::onDisable);
        verify(plugin.menus()).close();
        verify(plugin.tableWorld()).close();
        verify(plugin.sessions()).close();
    }
}

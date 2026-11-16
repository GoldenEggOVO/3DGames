package GoldenEggOVO.TDGames.Core.command;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackReloadCommandTest {
    private TDGamesPlugin plugin() throws Exception {
        var plugin = SessionTestSupport.plugin();
        return plugin;
    }

    @Test
    void consoleCanDiscoverResourceReload() throws Exception {
        var plugin = plugin();
        var command = new GameCommand(plugin);
        assertEquals(java.util.List.of("reload-pack"), command.onTabComplete(
                mock(CommandSender.class), null, "3dgames", new String[]{"reload-p"}));
    }

    @Test
    void resourceReloadIsNotAdvertisedToOrdinaryPlayers() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        assertTrue(new GameCommand(plugin).onTabComplete(
                player, null, "3dgames", new String[]{"reload-p"}).isEmpty());
    }

    @Test
    void onlyAdministratorOrConsoleCanStartResourceReload() throws Exception {
        var plugin = plugin();
        SessionTestSupport.module(plugin, "pack", mock(ResourcePackService.class));
        var command = new GameCommand(plugin);
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        command.onCommand(player, null, "3dgames", new String[]{"reload-pack"});
        verify(plugin.pack(), never()).reload(any());
        when(player.hasPermission("3dgames.admin")).thenReturn(true);
        command.onCommand(player, null, "3dgames", new String[]{"RELOAD-PACK"});
        verify(plugin.pack()).reload(player);
        var console = mock(CommandSender.class);
        command.onCommand(console, null, "3dgames", new String[]{"reload-pack"});
        verify(plugin.pack()).reload(console);
    }
}

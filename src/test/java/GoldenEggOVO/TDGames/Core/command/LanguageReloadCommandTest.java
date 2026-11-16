package GoldenEggOVO.TDGames.Core.command;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.text.MessageText;

import GoldenEggOVO.TDGames.Core.text.Language;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageReloadCommandTest {
    @TempDir(factory=WorkspaceTempFactory.class) Path data;

    private TDGamesPlugin plugin() {
        var plugin=SessionTestSupport.plugin();
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageReloadTest"));
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        return plugin;
    }

    @Test void deniedPlayersCannotReadLanguageFiles() {
        var plugin=plugin();var player=mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        assertTrue(new GameCommand(plugin).onCommand(player,null,"3dgames",new String[]{"reload-language"}));
        verify(plugin,never()).getDataFolder();verify(plugin.menus(),never()).languageChanged();
        verify(plugin).tell(eq(player),any(net.kyori.adventure.text.Component.class));
    }

    @Test void consoleReloadReadsOnlyLanguageAndRejectsInvalidCandidateWithoutClosingMenus() throws Exception {
        var plugin=plugin();var console=mock(CommandSender.class);
        String config="language: custom\nmax-rooms: 23\n";Files.writeString(data.resolve("config.yml"),config);
        Language.load(data.resolve("languages"),"en_US",w->fail(w));
        Path custom=data.resolve("languages/custom.yml");Files.writeString(custom,"menu.create: 'Reloaded Create'\n");
        try {
            assertTrue(new GameCommand(plugin).onCommand(console,null,"3dgames",new String[]{"reload-language"}));
            assertEquals("Reloaded Create",GoldenEggOVO.TDGames.Core.text.MessageText.plain(Language.component("menu.create")));
            verify(plugin.menus()).languageChanged();clearInvocations(plugin.menus());
            Files.writeString(custom,"chat.joined: '{wrong}'\n");
            new GameCommand(plugin).onCommand(console,null,"3dgames",new String[]{"reload-language"});
            verify(plugin.menus(),never()).languageChanged();
            assertEquals("Reloaded Create",GoldenEggOVO.TDGames.Core.text.MessageText.plain(Language.component("menu.create")));
            assertEquals(config,Files.readString(data.resolve("config.yml")));verify(plugin,never()).reloadConfig();
        }finally{Language.reload(data.resolve("languages"),"en_US",w->fail(w));}
    }
}

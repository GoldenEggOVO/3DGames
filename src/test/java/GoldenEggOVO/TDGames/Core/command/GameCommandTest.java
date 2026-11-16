package GoldenEggOVO.TDGames.Core.command;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameCommandTest {
    @Test
    void mixedCaseRulesOpenTheRequestedGame() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        new GameCommand(plugin).onCommand(player, null, "3dgames",
                new String[] {"RULES", "CHESS"});
        verify(plugin.menus()).rules(player, "chess");
    }

    @Test
    void anUppercaseUniqueRoomPrefixFindsTheSameRoom() throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        Player player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        GameSession room = new GameSession(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), "chess", 2, 1, 0);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        new GameCommand(plugin).onCommand(player, null, "3dgames",
                new String[] {"join", "ABCDEF"});
        verify(plugin.games()).join(player, room);
    }

    @Test
    void ambiguousRoomPrefixNeverJoinsAnArbitraryRoom() throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        Player player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        GameSession first = new GameSession(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), "chess", 2, 1, 0);
        GameSession second = new GameSession(UUID.fromString("abcdef00-0000-0000-0000-000000000002"), "chess", 2, 1, 1);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(first.id, first, second.id, second)));
        new GameCommand(plugin).onCommand(player, null, "3dgames",
                new String[] {"join", "abcdef"});
        verify(plugin.games(), never()).join(any(), any());
        verify(plugin).tell(eq(player), any());
    }

    @Test
    void mixedCaseGameUsesItsCanonicalNameAndDefaultCapacity() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        var command = new GameCommand(plugin);

        assertTrue(command.onCommand(player, null, "3dgames", new String[] {"CREATE", "MAHJONG"}));

        verify(plugin.games()).create(player, "mahjong", 4);
    }

    @Test
    void deniedPlayerCannotCreateOrOpenAMenu() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        SessionTestSupport.module(plugin, "menus", mock(GameMenus.class));
        var command = new GameCommand(plugin);

        command.onCommand(player, null, "3dgames", new String[] {"create", "chess"});
        command.onCommand(player, null, "3dgames", new String[0]);

        verify(plugin.games(), never()).create(any(), anyString(), anyInt());
        verifyNoInteractions(plugin.menus());
        verify(plugin, times(2)).tell(eq(player), any());
    }

    @Test
    void privateCardSuggestionsAreVisibleOnlyToTheActingPlayer() throws Exception {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        var room = new GameSession(UUID.randomUUID(), "color-eight", 2, 17, 0);
        room.join(UUID.randomUUID(), "First");
        room.join(UUID.randomUUID(), "Second");
        SessionTestSupport.set(room, "board", room.newBoard());
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(java.util.Map.of(room.id, room)));
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.games().session(player)).thenReturn(room);
        var command = new GameCommand(plugin);
        String[] arguments = {"move", "play:"};

        when(player.getUniqueId()).thenReturn(room.seats.get(1 - room.turn()).id());
        assertEquals(List.of(), command.onTabComplete(player, null, "3dgames", arguments));

        when(player.getUniqueId()).thenReturn(room.seats.get(room.turn()).id());
        assertFalse(command.onTabComplete(player, null, "3dgames", arguments).isEmpty());
    }
    @Test
    void publicJoinSuggestionsAndLookupExcludeSoloSessions() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        var solo = new GameSession(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), "memory", 1, 1, 0);
        var multi = new GameSession(UUID.fromString("bcdef000-0000-0000-0000-000000000001"), "chess", 2, 1, 1);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(solo.id, solo, multi.id, multi)));
        var command = new GameCommand(plugin);
        assertEquals(List.of(), command.onTabComplete(player, null, "3dgames", new String[] {"join", "abcdef"}));
        command.onCommand(player, null, "3dgames", new String[] {"join", "abcdef"});
        verify(plugin.games(), never()).join(any(), eq(solo));
        verify(plugin).tell(eq(player), any());
        assertFalse(command.onTabComplete(player, null, "3dgames", new String[] {"join", "bcdef"}).isEmpty());
    }
}

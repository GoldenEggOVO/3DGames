package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import net.kyori.adventure.text.Component;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuFlowTest {
    @Test
    void soloSetupStartsAGameInsteadOfCreatingAMultiplayerRoom() throws Exception {
        var f = new Fixture();
        f.menus.singlePlayer(f.player);
        f.buttons.stream().filter(button -> button.id().equals("2048")).findFirst().orElseThrow().action().run();
        var start = f.buttons.stream().filter(button -> button.id().equals("start")).findFirst().orElseThrow();
        assertEquals(GoldenEggOVO.TDGames.Core.text.MessageText.plain(
                GoldenEggOVO.TDGames.Core.text.Language.component("solo.start")), start.label());
    }
    @Test
    void savedSoloGameIsNamedOnTheContinueButton() throws Exception {
        var f = new Fixture();
        when(f.plugin.games().hasSoloProgress(f.player.getUniqueId())).thenReturn(true);
        when(f.plugin.games().soloProgressKinds(f.player.getUniqueId())).thenReturn(List.of("2048"));
        f.menus.main(f.player);
        assertTrue(f.buttons.stream().noneMatch(button -> button.id().equals("solo-resume")));
        f.menus.singlePlayer(f.player);
        var resume = f.buttons.stream().filter(button -> button.id().equals("solo-resume")).findFirst().orElseThrow();
        assertTrue(resume.label().contains("2048"), resume.label());
    }

    @Test
    void multipleSavedSoloGamesCanBeResumedIndividually() throws Exception {
        var f = new Fixture();
        when(f.plugin.games().hasSoloProgress(f.player.getUniqueId())).thenReturn(true);
        when(f.plugin.games().soloProgressKinds(f.player.getUniqueId())).thenReturn(List.of("2048", "klondike"));
        f.menus.main(f.player);
        assertTrue(f.buttons.stream().noneMatch(button -> button.id().equals("solo-resume")));
        f.menus.singlePlayer(f.player);
        f.buttons.stream().filter(button -> button.id().equals("solo-resume")).findFirst().orElseThrow().action().run();
        f.buttons.stream().filter(button -> button.id().equals("back")).findFirst().orElseThrow().action().run();
        assertTrue(f.buttons.stream().anyMatch(button -> button.id().equals("2048")));
        f.buttons.stream().filter(button -> button.id().equals("solo-resume")).findFirst().orElseThrow().action().run();
        var choice = f.buttons.stream().filter(button -> button.label().contains("2048")).findFirst().orElseThrow();
        choice.action().run();
        verify(f.plugin.games()).resumeSolo(f.player, "2048");
    }
    @Test
    @SuppressWarnings("unchecked")
    void expiredMenuRefreshesOnlyItsOwnCurrentSession() throws Exception {
        var f = new Fixture();
        f.menus.main(f.player);
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        var sessions = (Map<UUID, GameMenus.Session>) field.get(f.menus);
        var original = sessions.get(f.player.getUniqueId());
        f.menus.refreshExpired(f.player, "3dgames:" + UUID.randomUUID());
        assertSame(original, sessions.get(f.player.getUniqueId()));
        f.menus.refreshExpired(f.player, "3dgames:" + original.token());
        var refreshed = sessions.get(f.player.getUniqueId());
        assertNotEquals(original.token(), refreshed.token());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(f.description).contains(
                GoldenEggOVO.TDGames.Core.text.MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("menu.expired"))));
        f.menus.refreshExpired(f.player, "3dgames:" + original.token());
        assertSame(refreshed, sessions.get(f.player.getUniqueId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredChatMenuClickRefreshesInsteadOfRunningItsOldAction() throws Exception {
        var f = new Fixture();
        f.menus.main(f.player);
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        var sessions = (Map<UUID, GameMenus.Session>) field.get(f.menus);
        var original = sessions.get(f.player.getUniqueId());
        sessions.put(f.player.getUniqueId(), new GameMenus.Session(original.token(), original.world(),
                System.currentTimeMillis() - 1, original.buttons(), original.refresh()));
        f.menus.handle(f.player, "3dgames:" + original.token() + " 0");
        verify(f.player).sendMessage(GoldenEggOVO.TDGames.Core.text.Language.component("menu.expired"));
        assertNotEquals(original.token(), sessions.get(f.player.getUniqueId()).token());
    }
    @Test
    void catalogueRoomBrowserAndResumeAreMutuallyExclusive() throws Exception {
        var f = new Fixture();
        GameSession room = f.addRoom(0);
        f.menus.main(f.player);
        assertTrue(f.buttons.stream().anyMatch(b -> b.id().equals("rooms")));
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("resume")));
        room.join(f.player.getUniqueId(), "Owner");
        f.menus.main(f.player);
        assertTrue(f.buttons.stream().anyMatch(b -> b.id().equals("resume")));
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("rooms")));
    }
    @Test
    void closeCallbackEndsTheSessionAndSynchronizesFocusInput() throws Exception {
        Fixture f = new Fixture();
        SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
        f.menus.main(f.player);
        assertTrue(f.menus.active(f.player));
        f.buttons.stream()
                .filter(b -> b.id().equals("close"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertFalse(f.menus.active(f.player));
        verify(f.plugin.tablePlayers()).menuClosed(f.player);
    }

    @Test
    void catalogShowsOneGlobalBrowserOnlyWhenAnyRoomExists() throws Exception {
        Fixture f = new Fixture();
        f.menus.main(f.player);
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("rooms")));
        GameSession chess = f.addRoom(0), cards = new GameSession(UUID.randomUUID(), "color-eight", 5, 1, 1);
        SessionTestSupport.mutableSessions(f.plugin).put(cards.id, cards);
        f.menus.main(f.player);
        var browse = f.buttons.stream().filter(b -> b.id().equals("rooms")).toList();
        assertEquals(1, browse.size());
        browse.getFirst().action().run();
        for (GameSession room : List.of(chess, cards))
            assertTrue(
                    f.buttons.stream()
                            .anyMatch(
                                    b ->
                                            b.id().equals("room-" + room.id)
                                                    && b.label()
                                                            .contains(
                                                                    MessageText.plain(
                                                                            RoomText.game(
                                                                                    room.kind)))
                                                    && b.label()
                                                            .contains(
                                                                    room.id
                                                                            .toString()
                                                                            .substring(0, 6))));
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("entry")));
        f.buttons.stream()
                .filter(b -> b.id().equals("back"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertTrue(f.buttons.stream().anyMatch(b -> b.id().equals("multiplayer")));
        SessionTestSupport.mutableSessions(f.plugin).clear();
        f.menus.main(f.player);
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("rooms")));
    }

    @Test
    void everyGameSetupOmitsItsOwnRoomBrowser() throws Exception {
        Fixture f = new Fixture();
        f.addRoom(0);
        for (String kind : GameCatalog.IDS) {
            f.menus.setup(f.player, kind);
            assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("rooms")), kind);
        }
    }

    @Test
    void catalogOpensSetupAndRulesReachCreationWithoutChangingTheDefaultDraft() throws Exception {
        Fixture f = new Fixture();
        f.menus.main(f.player);
        f.buttons.stream()
                .filter(b -> b.id().equals("multiplayer"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        f.buttons.stream()
                .filter(b -> b.id().equals("ludo"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("mode")));
        f.buttons.stream()
                .filter(b -> b.id().equals("details"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        f.buttons.stream()
                .filter(b -> b.id().equals("rule-blocking"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertTrue(
                f.buttons.stream()
                        .anyMatch(
                                b ->
                                        b.id().equals("rule-blocking")
                                                && b.label().contains("Any Pawn Blocks Passage")));
        f.buttons.stream()
                .filter(b -> b.id().equals("back"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        f.buttons.stream()
                .filter(b -> b.id().equals("start"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        verify(f.plugin.games()).create(f.player, "ludo", 4, Map.of("blocking", "on"));
        f.menus.setup(f.player, "ludo");
        f.buttons.stream()
                .filter(b -> b.id().equals("details"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertTrue(
                f.buttons.stream()
                        .anyMatch(
                                b ->
                                        b.id().equals("rule-blocking")
                                                && b.label().contains("Blocking: Off")));
    }

    @Test
    void multiplayerRoomIsCreatedBeforeTheHostChoosesToFillBots() throws Exception {
        Fixture f = new Fixture();
        f.menus.setup(f.player, "chess");
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("mode")));
        assertEquals("Create Room", f.buttons.stream().filter(b -> b.id().equals("start")).findFirst().orElseThrow().label());
        doAnswer(
                        i -> {
                            GameSession r = f.addRoom(0);
                            r.join(f.player.getUniqueId(), "Player");
                            f.menus.room(f.player, r);
                            return null;
                        })
                .when(SessionTestSupport.service(f.plugin))
                .create(f.player, "chess", 2, Map.of());
        f.buttons.stream()
                .filter(b -> b.id().equals("start"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        verify(f.plugin.games(), never()).startWithBots(any(), any());
        f.buttons.stream().filter(b -> b.id().equals("bots")).findFirst().orElseThrow().action().run();
        GameSession created = f.plugin.games().session(f.player);
        verify(f.plugin.games()).startWithBots(f.player, created);
    }

    @Test
    void roomBrowserReachesEveryRoomAndKeepsCreateOnEachPage() throws Exception {
        Fixture f = new Fixture();
        for (int i = 0; i < 25; i++) f.addRoom(i);
        f.menus.games(f.player, "chess");
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            assertTrue(f.buttons.stream().anyMatch(b -> b.label().equals("Create Room")));
            for (GameSession r : f.plugin.games().activeSessions().values())
                if (f.buttons.stream()
                        .anyMatch(b -> b.label().contains(r.id.toString().substring(0, 6))))
                    seen.add(r.id.toString());
            var next = f.buttons.stream().filter(b -> b.id().equals("next")).findFirst();
            if (next.isEmpty()) break;
            next.get().action().run();
        }
        assertEquals(25, seen.size(), "Room limit is configurable; browse beyond the first twelve");
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("previous") || b.id().equals("next")));
    }

    @Test
    void staleRoomListingCannotJoinOrVisitARemovedTable() throws Exception {
        Fixture f = new Fixture();
        GameSession r = f.addRoom(0);
        f.menus.games(f.player, "chess");
        var entry =
                f.buttons.stream()
                        .filter(b -> b.label().contains(r.id.toString().substring(0, 6)))
                        .findFirst()
                        .orElseThrow();
        SessionTestSupport.mutableSessions(f.plugin).remove(r.id);
        entry.action().run();
        verify(f.plugin.games(), never()).join(any(), any());
        assertFalse(
                f.buttons.stream()
                        .anyMatch(b -> b.label().contains(r.id.toString().substring(0, 6))));
    }

    @Test
    void roomReturnsToTheTableAndRulesRemainAvailableFromTheCommand() throws Exception {
        Fixture f = new Fixture();
        GameSession r = f.addRoom(0);
        r.join(f.player.getUniqueId(), "Player");
        r.fillBots();
        SessionTestSupport.set(r, "board", GameFactory.create("chess", 2, 0));
        SessionTestSupport.set(r, "phase", GameSession.Phase.PLAYING);
        f.menus.room(f.player, r);
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("controls")));
        f.buttons.stream()
                .filter(b -> b.id().equals("play"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        verify(f.plugin.games()).enterArena(f.player, r);
        f.menus.room(f.player, r);
        assertFalse(f.buttons.stream().anyMatch(b -> b.id().equals("rules")));
        f.menus.rules(f.player, "chess");
        assertTrue(MessageText.plain(f.description).contains("castling"));
    }

    @Test
    void leaveConfirmationCannotLeaveANewerRoom() throws Exception {
        Fixture f = new Fixture();
        GameSession old = f.addRoom(0);
        old.join(f.player.getUniqueId(), "Player");
        f.menus.confirmLeave(f.player);
        var confirm =
                f.buttons.stream().filter(b -> b.id().equals("entry")).findFirst().orElseThrow();
        SessionTestSupport.mutableSessions(f.plugin).remove(old.id);
        GameSession next = f.addRoom(1);
        next.join(f.player.getUniqueId(), "Player");
        confirm.action().run();
        verify(f.plugin.games(), never()).leave(f.player);
    }

    @Test
    void goRulesNeverAppendThePlayersCurrentGomokuRules() throws Exception {
        Fixture f = new Fixture();
        GameSession r = new GameSession(UUID.randomUUID(), "gomoku", 2, 0, 0);
        SessionTestSupport.mutableSessions(f.plugin).put(r.id, r);
        r.join(f.player.getUniqueId(), "Player");
        SessionTestSupport.set(r, "board", spy(GameFactory.create("gomoku", 2, 0)));
        when(r.board().publicInfo()).thenReturn(Map.of("rules", "GOMOKU_ONLY_RULES"));
        f.menus.rules(f.player, "go");
        assertFalse(MessageText.plain(f.description).contains("GOMOKU_ONLY_RULES"));
    }

    @Test
    void classicRulesUseTheExistingGenericDescription() throws Exception {
        Fixture f = new Fixture();
        for (String kind : GameCatalog.CLASSIC_IDS) {
            f.menus.rules(f.player, kind);
            assertTrue(MessageText.plain(f.description).startsWith("Choose a game to view its rules."), kind);
            assertEquals(List.of("back"), f.buttons.stream().map(GameMenus.Button::id).toList(), kind);
        }
    }

    @Test
    void roomButtonsUseTheSuppliedLocalizedLabels() {
        for (String id : List.of("bots", "play", "leave")) {
            var button = new GameMenus.Button(id, Component.text("Localized label"), () -> {});
            var menu = GameMenuWindow.layout("room", Component.text("Title"), Component.empty(),
                    List.of(button), UUID.randomUUID());
            assertEquals("Localized label", MessageText.plain(menu.controls().getFirst().caption()));
        }
    }

    static final class Fixture {
        final TDGamesPlugin plugin = SessionTestSupport.plugin();
        final Player player = mock(Player.class);
        final GameMenus menus;
        List<GameMenus.Button> buttons = List.of();
        Component description;

        Fixture() throws Exception {
            SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
            when(plugin.allowed(player)).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            World world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.games().session(player))
                    .thenAnswer(
                            i ->
                                    plugin.games().activeSessions().values().stream()
                                            .filter(r -> r.seat(player.getUniqueId()) >= 0)
                                            .findFirst()
                                            .orElse(null));
            var window = mock(GameMenuWindow.class);
            when(window.layoutMenu(
                            anyString(),
                            any(Component.class),
                            any(Component.class),
                            anyList(),
                            any()))
                    .thenAnswer(
                            i -> {
                                description = i.getArgument(2);
                                buttons = List.copyOf(i.getArgument(3));
                                return GameMenuWindow.layout(
                                        i.getArgument(0),
                                        i.getArgument(1, Component.class),
                                        description,
                                        buttons,
                                        i.getArgument(4));
                            });
            when(window.open(any(), any())).thenReturn(true);
            menus = new GameMenus(plugin, window);
            SessionTestSupport.module(plugin, "menus", menus);
        }

        GameSession addRoom(int n) {
            GameSession r = new GameSession(UUID.randomUUID(), "chess", 2, n, n);
            SessionTestSupport.mutableSessions(plugin).put(r.id, r);
            return r;
        }
    }
}

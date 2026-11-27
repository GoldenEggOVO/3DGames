package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Multiplayer.game.coloreight.ColorEightGame;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuExperienceTest {
    @Test
    void singlePlayerSetupDoesNotDescribeMultiplayerRoomSettings() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.singlePlayer(f.player);
        f.menus.setup(f.player, "2048");
        assertEquals(MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("setup.solo")),
                MessageText.plain(f.description));
        assertEquals(List.of("start", "back", "close"), ids(f));
    }

    @Test
    void fixedTwoPlayerSetupDoesNotPromiseUnavailableDetailedSettings() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.games(f.player);
        f.menus.setup(f.player, "xiangqi");
        String hint = MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("setup.basic-hint"));
        assertFalse(MessageText.plain(f.description).contains(hint));
        assertFalse(ids(f).contains("capacity"));
        assertFalse(ids(f).contains("details"));
    }

    @Test
    void disabledMenusDoNotBlockTableInputWithAnInvisibleSession() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        fixture.plugin.getConfig().set("menu-enabled", false);
        var menus = new GameMenus(fixture.plugin, new GameMenuWindow(fixture.plugin));

        menus.main(fixture.player);

        assertFalse(menus.active(fixture.player));
        verify(fixture.player, never()).showDialog(any());
        verify(fixture.player).sendMessage(
                GoldenEggOVO.TDGames.Core.text.Language.component("menu.disabled"));
    }

    @Test
    void setupHighlightsCreationAndKeepsDetailedRuleChangesWhenReturning() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.setup(f.player, "ludo");
        assertEquals("start", f.buttons.getFirst().id());
        assertFalse(ids(f).contains("rule-blocking"));
        click(f, "details");
        click(f, "rule-blocking");
        click(f, "back");
        assertTrue(ids(f).containsAll(List.of("start", "capacity", "details")));
        assertFalse(ids(f).contains("mode"));
        click(f, "start");
        verify(f.plugin.games()).create(f.player, "ludo", 4, Map.of("blocking", "on"));
    }

    @Test
    void mahjongProfileStaysOnBasicSetupWhileAllRegionalRulesRemainReachable() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.setup(f.player, "mahjong");
        click(f, "rule-profile");
        click(f, "rule-profile");
        assertFalse(ids(f).contains("rule-base-points"));
        click(f, "details");
        Set<String> seen = new HashSet<>();
        do {
            seen.addAll(ids(f));
            if (!ids(f).contains("next")) break;
            click(f, "next");
        } while (true);
        assertTrue(seen.containsAll(List.of("rule-base-points", "rule-refund-kong-on-exhaustion")));
        click(f, "back");
        click(f, "start");
        verify(f.plugin.games()).create(f.player, "mahjong", 4, Map.of("profile", "sichuan"));
    }

    @Test
    void playingRoomShowsLeaveDirectlyAndOmitsTheOptionsSubmenu() throws Exception {
        var f = new MenuFlowTest.Fixture();
        GameSession r = handRoom(f);
        f.menus.room(f.player, r);
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
        click(f, "leave");
        assertTrue(f.buttons.stream().anyMatch(b -> b.label().equals("Confirm Leave")));
    }

    @Test
    void roomAndBrowserBackReturnToAnExistingRoomOrCatalog() throws Exception {
        var f = new MenuFlowTest.Fixture();
        GameSession r = f.addRoom(0);
        r.join(f.player.getUniqueId(), "Owner");
        f.menus.room(f.player, r);
        click(f, "back");
        assertTrue(ids(f).contains("resume"));
        assertFalse(ids(f).contains("start"));
        f.menus.games(f.player, "chess");
        click(f, "back");
        assertTrue(ids(f).contains("ready"));
        SessionTestSupport.mutableSessions(f.plugin).clear();
        f.menus.games(f.player, "chess");
        click(f, "back");
        assertTrue(ids(f).contains("chess"));
        assertFalse(ids(f).contains("start"));
    }

    @Test
    void lobbyHasReadinessStartAndRoomOptionsAsItsPrimaryActions() throws Exception {
        var f = new MenuFlowTest.Fixture();
        GameSession r = f.addRoom(0);
        r.join(f.player.getUniqueId(), "Owner");
        f.menus.room(f.player, r);
        assertEquals(List.of("ready", "bots", "leave", "back", "close"), ids(f));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
        assertFalse(ids(f).contains("options"));
        assertFalse(
                MessageText.plain(f.description)
                        .contains(MessageText.plain(RoomText.options(r.kind, r.options))));
    }

    @Test
    void everyGameOmitsHelpAndDetailEntriesThroughoutItsMenus() throws Exception {
        var f = new MenuFlowTest.Fixture();
        for (String kind : GameCatalog.IDS) {
            f.menus.setup(f.player, kind);
            assertFalse(ids(f).contains("rules"), kind + " setup");
            f.menus.games(f.player, kind);
            assertFalse(ids(f).contains("rules"), kind + " browser");
            GameSession r = new GameSession(UUID.randomUUID(), kind, GameCatalog.defaultCapacity(kind), 0, 0);
            r.join(f.player.getUniqueId(), "Owner");
            SessionTestSupport.mutableSessions(f.plugin).put(r.id, r);
            for (GameSession.Phase phase : GameSession.Phase.values()) {
                SessionTestSupport.set(r, "phase", phase);
                f.menus.room(f.player, r);
                assertTrue(
                        f.buttons.stream()
                                .noneMatch(
                                        b ->
                                                Set.of("rules", "public-table").contains(b.id())
                                                        || b.label().equals("Room Details")),
                        kind + " " + phase);
                assertFalse(ids(f).contains("options"));
                assertTrue(
                        Collections.disjoint(ids(f), List.of("rules", "details", "public-table")),
                        kind + " options " + phase);
            }
            SessionTestSupport.mutableSessions(f.plugin).remove(r.id);
        }
    }

    @Test
    void everyPlayingGameReturnsToTheTableWithoutAFullMoveSelector() throws Exception {
        var f = new MenuFlowTest.Fixture();
        for (String kind : GameCatalog.IDS.stream().filter(id -> !GameCatalog.CLASSIC_IDS.contains(id)).toList()) {
            GameSession r = playingRoom(f, kind);
            f.menus.room(f.player, r);
            assertEquals(
                    Set.of("go", "go9", "go13").contains(kind)
                            ? List.of("play", "pass", "leave", "back", "close")
                            : List.of("play", "leave", "back", "close"),
                    ids(f),
                    kind);
            click(f, "play");
            verify(f.plugin.games()).enterArena(f.player, r);
            SessionTestSupport.mutableSessions(f.plugin).remove(r.id);
        }
    }

    @Test
    void physicalTargetChoiceReturnsToRoomInsteadOfTheFullMoveSelector() throws Exception {
        var f = new MenuFlowTest.Fixture();
        GameSession r = playingRoom(f, "chess");
        f.menus.boardChoices(f.player, r, List.of("move:a7:a8:q", "move:a7:a8:n"), 0);
        f.buttons.getFirst().action().run();
        verify(f.plugin.games())
                .action(f.player, r, r.revision(), new com.google.gson.JsonPrimitive("move:a7:a8:q"));
        click(f, "back");
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
    }

    @Test
    void goRoomOffersOnlyLegalStateActionsAndScoringKeepsBothPlayersChoices() throws Exception {
        for (String kind : List.of("go", "go9", "go13")) {
            var f = new MenuFlowTest.Fixture();
            GameSession r = playingRoom(f, kind);
            SessionTestSupport.set(r, "revision", 12);
            f.menus.room(f.player, r);
            assertEquals(List.of("play", "pass", "leave", "back", "close"), ids(f), kind);
            click(f, "pass");
            verify(f.plugin.games()).action(f.player, r, 12, new com.google.gson.JsonPrimitive("pass"));
            r.board().apply(0, "pass");
            SessionTestSupport.bump(r);
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "leave", "back", "close"),
                    ids(f),
                    "Only the current player can pass");
            r.board().apply(1, "pass");
            SessionTestSupport.bump(r);
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "accept", "resume", "leave", "back", "close"), ids(f), kind);
            click(f, "accept");
            verify(f.plugin.games()).action(f.player, r, 14, new com.google.gson.JsonPrimitive("accept"));
            r.board().apply(0, "accept");
            SessionTestSupport.bump(r);
            f.menus.room(f.player, r);
            assertEquals(
                    List.of("play", "resume", "leave", "back", "close"),
                    ids(f),
                    "A confirmed player can still dispute scoring");
            click(f, "resume");
            verify(f.plugin.games()).action(f.player, r, 15, new com.google.gson.JsonPrimitive("resume"));
            SessionTestSupport.set(r, "busy", true);
            f.menus.room(f.player, r);
            assertEquals(List.of("play", "leave", "back", "close"), ids(f));
            SessionTestSupport.set(r, "busy", false);
        }
    }

    @Test
    void colorEightRoomHasNoDeclarationOrHandMoveMenu() throws Exception {
        var f = new MenuFlowTest.Fixture();
        GameSession r = playingRoom(f, "color-eight");
        f.menus.room(f.player, r);
        assertEquals(List.of("play", "leave", "back", "close"), ids(f));
        assertFalse(r.board().legalActions(0).contains("declare"));
    }

    private static GameSession playingRoom(MenuFlowTest.Fixture f, String kind) {
        GameSession r = new GameSession(UUID.randomUUID(), kind, GameCatalog.defaultCapacity(kind), 0, 0);
        r.join(f.player.getUniqueId(), "Owner");
        r.fillBots();
        SessionTestSupport.set(r, "board", GameFactory.create(kind, r.capacity, 0));
        SessionTestSupport.set(r, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(f.plugin).put(r.id, r);
        return r;
    }

    private static GameSession handRoom(MenuFlowTest.Fixture f) {
        GameSession r = new GameSession(UUID.randomUUID(), "color-eight", 2, 0, 0);
        SessionTestSupport.set(r, "board", new ColorEightGame(2, 0));
        if (r.board().currentPlayer() == 1) r.join(UUID.randomUUID(), "Other player");
        r.join(f.player.getUniqueId(), "Owner");
        if (r.seats.size() == 1) r.join(UUID.randomUUID(), "Other player");
        SessionTestSupport.set(r, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(f.plugin).put(r.id, r);
        return r;
    }

    private static List<String> ids(MenuFlowTest.Fixture f) {
        return f.buttons.stream().map(GameMenus.Button::id).toList();
    }

    private static void click(MenuFlowTest.Fixture f, String id) {
        f.buttons.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow().action().run();
    }
}

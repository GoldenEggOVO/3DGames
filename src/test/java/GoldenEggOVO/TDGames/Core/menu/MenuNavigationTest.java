package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Core.text.MessageText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuNavigationTest {
    @Test void openingAnyPageReleasesDirectionalControlWithoutWaitingForAnotherInput() throws Exception {
        var f = new MenuFlowTest.Fixture();
        var controller = mock(GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController.class);
        when(f.plugin.tablePlayers()).thenReturn(controller);
        f.menus.main(f.player);
        verify(controller).releaseFocus(f.player);
    }

    @Test
    void classicRoomMenusOnlyManageTheRoomAndNeverOfferGameMoves() throws Exception {
        for (String kind : GoldenEggOVO.TDGames.Shared.game.GameCatalog.CLASSIC_IDS) {
            var f = new MenuFlowTest.Fixture();
            int players = java.util.Set.of("2048", "klondike").contains(kind) ? 1 : 2;
            var room = new GoldenEggOVO.TDGames.Shared.session.GameSession(UUID.randomUUID(), kind, players, 0, 0);
            room.join(f.player.getUniqueId(), "Owner");
            GoldenEggOVO.TDGames.SessionTestSupport.set(room, "board",
                    GoldenEggOVO.TDGames.Core.game.GameFactory.create(kind, players, 7));
            GoldenEggOVO.TDGames.SessionTestSupport.set(room, "phase", GoldenEggOVO.TDGames.Shared.session.GameSession.Phase.PLAYING);
            GoldenEggOVO.TDGames.SessionTestSupport.mutableSessions(f.plugin).put(room.id, room);
            f.menus.room(f.player, room);
            assertTrue(ids(f).contains("play"), kind);
            assertTrue(java.util.Set.of("play", "leave", "save-leave", "back").containsAll(ids(f)),
                    kind + " exposes game moves in the management menu: " + ids(f));
        }
    }

    @Test
    void categoriesSeparateSoloAndMultiplayerAndConstrainMixedGameCapacity() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.main(f.player);
        click(f, "single-player");
        assertEquals(List.of("mastermind", "2048", "klondike", "memory", "back"), ids(f));
        click(f, "memory");
        assertFalse(ids(f).contains("capacity"));
        assertFalse(ids(f).contains("mode"));
        click(f, "back");
        assertFalse(ids(f).contains("casino"));
        click(f, "back");
        click(f, "multiplayer");
        assertFalse(ids(f).contains("2048"));
        assertFalse(ids(f).contains("klondike"));
        assertFalse(ids(f).contains("casino"));
        assertTrue(ids(f).containsAll(List.of("mastermind", "memory", "shogi", "backgammon")));
        click(f, "memory");
        for (int i = 0; i < 4; i++) {
            assertTrue(f.buttons.stream().filter(button -> button.id().equals("capacity"))
                    .noneMatch(button -> button.label().contains("1")));
            click(f, "capacity");
        }
        click(f, "back");
        assertFalse(ids(f).contains("casino"));
    }

    @Test
    void homepageHasTwoGameEntrancesAndSettingsWithoutAnExternalPackToggle() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.main(f.player);
        assertEquals(List.of("single-player", "multiplayer", "settings", "close"), ids(f));
        var rendered = GameMenuWindow.layout("catalog", Component.empty(), Component.empty(), f.buttons, UUID.randomUUID());
        assertEquals(2, rendered.columns());
        assertEquals(List.of("Single Player", "Multiplayer"), rendered.controls().stream().map(control -> MessageText.plain(control.caption())).toList());
        assertFalse(ids(f).contains("resource-pack"));
    }

    @Test
    void tabletopKeepsEveryGameReachableWithOnlyPlayerCountsInTooltips() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.main(f.player); click(f, "multiplayer");
        assertEquals(List.of("xiangqi", "gomoku", "chess", "checkers", "draughts", "reversi", "go", "connectfour",
                "mancala", "nine-mens-morris", "dots-and-boxes", "hex", "battleship", "shogi", "mastermind",
                "color-eight", "mahjong", "doudizhu", "liars-bar", "texas-holdem", "memory", "ludo", "yacht", "backgammon", "monopoly"),
                ids(f).stream().filter(id -> !List.of("back").contains(id)).toList());
        for (var button : f.buttons) {
            if (List.of("back").contains(button.id())) continue;
            assertFalse(button.label().contains(" players"), button.id());
            assertNotNull(button.tooltip());
            assertTrue(MessageText.plain(button.tooltip()).matches("Players: [1-6](, [1-6])*"), button.id());
        }
        assertFalse(ids(f).contains("next"));
        click(f, "back"); assertTrue(ids(f).contains("multiplayer"));
    }

    @Test
    void soloGameTooltipUsesNaturalPlayerCountText() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.main(f.player); click(f, "single-player");
        var game = f.buttons.stream().filter(button -> button.id().equals("2048")).findFirst().orElseThrow();
        assertEquals("Players: 1", MessageText.plain(game.tooltip()));
    }

    @Test
    void shogiSetupSwitchesToMiniShogiWithoutLosingFirstPlayer() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.games(f.player);
        click(f, "shogi");
        assertTrue(f.buttons.stream().anyMatch(button -> button.id().equals("board") && button.label().equals("Board: 9×9 · Shogi")));
        click(f, "details");
        click(f, "rule-first");
        click(f, "back");
        click(f, "board");
        assertTrue(f.buttons.stream().anyMatch(button -> button.id().equals("board") && button.label().equals("Board: 5×5 · Mini Shogi")));
        assertFalse(ids(f).contains("mode"));
        click(f, "start");
        verify(f.plugin.games()).create(f.player, "mini-shogi", 2, Map.of("first", "host"));
    }

    @Test
    void miniShogiSetupCanSwitchBackToStandardShogi() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.setup(f.player, "mini-shogi");
        assertTrue(f.buttons.stream().anyMatch(button -> button.id().equals("board") && button.label().equals("Board: 5×5 · Mini Shogi")));
        click(f, "board");
        assertTrue(f.buttons.stream().anyMatch(button -> button.id().equals("board") && button.label().equals("Board: 9×9 · Shogi")));
        click(f, "start");
        verify(f.plugin.games()).create(f.player, "shogi", 2, Map.of("first", "random"));
    }

    @Test
    void setupReturnsToTabletopIncludingEveryGoBoardSize() throws Exception {
        var f = new MenuFlowTest.Fixture();
        for (String game : List.of("chess", "mahjong", "yacht", "go9", "go13", "shogi", "mini-shogi")) {
            f.menus.setup(f.player, game); click(f, "back");
            assertTrue(ids(f).contains("go")); assertTrue(ids(f).contains("yacht"));
            click(f, "back"); assertTrue(ids(f).contains("multiplayer"));
        }
    }

    @Test
    void dialogReceivesTheWholeTabletopListForNativeScrolling() throws Exception {
        var f = new MenuFlowTest.Fixture(); f.menus.games(f.player);
        var rendered = GameMenuWindow.layout("tabletop", Component.empty(), Component.empty(), f.buttons, UUID.randomUUID());
        assertEquals(2, rendered.columns());
        assertEquals(25, rendered.controls().size());
        assertEquals("Back", MessageText.plain(rendered.exit().caption()));
        assertNotEquals(rendered.controls().getLast().action(), rendered.exit().action());
    }

    @Test
    void roomPrimaryActionsPrecedeSettingsAndLeaveIsTheLastRoomAction() {
        var buttons = List.of(button("leave"), button("stake"), button("rematch"), button("refresh"), button("back"));
        var rendered = GameMenuWindow.layout("room", Component.empty(), Component.empty(), buttons, UUID.randomUUID());
        assertEquals(List.of("rematch", "stake", "refresh", "leave", "back"),
                rendered.buttons().stream().map(GameMenus.Button::id).toList());
        assertTrue(LegacyComponentSerializer.legacySection().serialize(rendered.controls().getFirst().caption()).contains("§a"));
    }

    @Test
    void returnAndDestructiveActionsUseDistinctColorsAndConsistentWidths() {
        var rendered = GameMenuWindow.layout("room", Component.empty(), Component.empty(),
                List.of(button("leave"), button("back")), UUID.randomUUID());
        assertEquals(330, rendered.exit().width());
        assertTrue(LegacyComponentSerializer.legacySection().serialize(rendered.exit().caption()).contains("§e"));
        assertFalse(LegacyComponentSerializer.legacySection().serialize(rendered.exit().caption()).contains("§c"));
        assertTrue(LegacyComponentSerializer.legacySection().serialize(rendered.controls().getFirst().caption()).contains("§c"));
    }

    private static List<String> ids(MenuFlowTest.Fixture f) {
        return f.buttons.stream().map(GameMenus.Button::id).toList();
    }

    private static void click(MenuFlowTest.Fixture f, String id) {
        var button = f.buttons.stream().filter(entry -> entry.id().equals(id)).findFirst();
        assertTrue(button.isPresent(), "Missing menu action: " + id);
        button.orElseThrow().action().run();
    }

    private static GameMenus.Button button(String id) {
        return new GameMenus.Button(id, id, () -> {});
    }
}

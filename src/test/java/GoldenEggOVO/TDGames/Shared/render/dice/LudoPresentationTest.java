package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.text.MessageText;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.render.BoardMapArtwork;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.game.GameFactory;

import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LudoPresentationTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test
    void catalogOffersLudoWithItsCurrentCapacity() {
        assertTrue(GameCatalog.IDS.contains("ludo"));
        assertFalse(GameCatalog.IDS.contains("aeroplane"));
        assertEquals(4, GameCatalog.defaultCapacity("ludo"));
    }

    @Test
    void ludoCoordinatesHaveUniqueClickTargetsAndAnExternalDie() {
        var board = GameFactory.create("ludo", 4, 0);
        var geometry = new TableGeometry("ludo", board.cells());
        for (var cell : board.cells())
            assertEquals(cell.id(), geometry.hit(geometry.x(cell), geometry.z(cell)));
        assertNull(geometry.hit(1.15, 0), "Empty space between the board and either tray");
        assertNull(geometry.hit(1.30, 0), "Only the tray itself owns the roll target");
        assertEquals(256, BoardMapArtwork.draw(geometry).getWidth());
    }

    @Test
    void hoveringALudoPawnShowsPrivateDestinationsAndKeepsPieceEntities() throws Exception {
        var f = new TableViewTest.Fixture("ludo");
        Map<?, ?> before = new HashMap<>((Map<?, ?>) TableViewTest.field(f.view, "tokens"));
        f.move("roll");
        for (int i = 0; i < DiceMotion.FRAMES; i++) f.view.tick();
        int count = f.entities.size();
        String source =
                f.room.board().cells().stream()
                        .filter(c -> !BoardSelection.sourceActions(f.room.board(), 0, c.id()).isEmpty())
                        .findFirst()
                        .orElseThrow()
                        .id();
        int history = f.room.history.size();
        f.view.cursor(f.player, null, source);
        assertTrue(f.entities.size() > count);
        List<Entity> hints = List.copyOf(f.entities.subList(count, f.entities.size()));
        for (Entity hint : hints) {
            verify((Display) hint).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, hint);
        }
        f.view.cursor(f.player, null, source);
        assertEquals(count + hints.size(), f.entities.size());
        assertEquals(history, f.room.history.size());
        f.move(BoardSelection.sourceActions(f.room.board(), 0, source).getFirst());
        for (int i = 0; i < 8; i++) f.view.tick();
        var after = (Map<?, ?>) TableViewTest.field(f.view, "tokens");
        for (var entry : before.entrySet()) assertSame(entry.getValue(), after.get(entry.getKey()));
        for (Entity hint : hints) verify(hint, atLeastOnce()).remove();
        f.view.close();
    }

    @Test
    void clickingAPawnCommitsItsDiceMoveWithoutAnExtraDestinationClick() throws Exception {
        var f = new TableViewTest.Fixture("ludo");
        f.move("roll");
        var tableWorld = mock(TableWorldController.class, CALLS_REAL_METHODS);
        GameRulesTest.set(tableWorld, "plugin", f.plugin);
        GameRulesTest.set(tableWorld, "selections", new HashMap<>());
        when(f.plugin.allowed(f.player)).thenReturn(true);
        String source =
                f.room.board().cells().stream()
                        .filter(c -> !BoardSelection.sourceActions(f.room.board(), 0, c.id()).isEmpty())
                        .findFirst()
                        .orElseThrow()
                        .id();
        String action = BoardSelection.sourceActions(f.room.board(), 0, source).getFirst();
        var click =
                TableWorldController.class.getDeclaredMethod(
                        "pickCell", Player.class, GameSession.class, int.class, String.class);
        click.setAccessible(true);
        click.invoke(tableWorld, f.player, f.room, 0, source);
        verify(f.plugin.games()).applyTableAction(eq(f.room), eq(0), eq(new JsonPrimitive(action)), eq(f.player));
    }

    @Test
    void stackedPawnsExplainTheChoiceAndKeepAllOptions() throws Exception {
        var f = new TableViewTest.Fixture("ludo");
        var field = f.room.board().getClass().getDeclaredField("progress");
        field.setAccessible(true);
        ((int[]) field.get(f.room.board()))[1] = 0;
        f.move("roll");
        for (int i = 0; i < DiceMotion.FRAMES; i++) f.view.tick();
        f.view.cursor(f.player, null, "sk0");
        var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player).sendActionBar(message.capture());
        assertTrue(
                GoldenEggOVO.TDGames.Core.text.MessageText.plain(message.getValue()).contains("choose a pawn"));
        var tableWorld = mock(TableWorldController.class, CALLS_REAL_METHODS);
        GameRulesTest.set(tableWorld, "plugin", f.plugin);
        GameRulesTest.set(tableWorld, "selections", new HashMap<>());
        GameRulesTest.set(tableWorld, "views", new HashMap<>(Map.of(f.room.id, f.view)));
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        var choices = BoardSelection.sourceActions(f.room.board(), 0, "sk0");
        assertEquals(2, choices.size());
        var click =
                TableWorldController.class.getDeclaredMethod(
                        "pickCell", Player.class, GameSession.class, int.class, String.class);
        click.setAccessible(true);
        click.invoke(tableWorld, f.player, f.room, 0, "sk0");
        verify(f.plugin.menus()).boardChoices(f.player, f.room, choices, 0);
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
    }
}

package GoldenEggOVO.TDGames;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.HandGame;

import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;

import GoldenEggOVO.TDGames.TDGamesPlugin;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController;
import GoldenEggOVO.TDGames.Shared.interaction.TableAccessController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.render.BoardMapArtwork;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.BoardPieceModels;
import GoldenEggOVO.TDGames.Shared.render.TableView;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GameRulesTest {
    static final List<String> KINDS =
            List.of(
                    "chess",
                    "xiangqi",
                    "gomoku",
                    "checkers",
                    "ludo",
                    "draughts",
                    "reversi",
                    "go9",
                    "go13",
                    "go");

    @Test
    void everyCellCenterMapsBackToItsOwnCellAndFitsTheNativeMap() {
        for (String kind : KINDS) {
            BoardGame board = GameFactory.create(kind, 2, 0);
            TableGeometry t = new TableGeometry(kind, board.cells());
            for (Cell c : board.cells()) {
                assertEquals(c.id(), t.hit(t.x(c), t.z(c)), kind + " " + c.id());
                assertTrue(t.px(c) > 10 && t.px(c) < 246);
                assertTrue(t.pz(c) > 10 && t.pz(c) < 246);
            }
            assertNull(t.hit(-1.5, 0));
            assertNull(t.hit(0, -1.5));
            assertNull(t.hit(Double.NaN, 0));
            assertNull(t.hit(20, 20));
            assertEquals("@menu", t.hit(0, 1.27));
        }
    }

    @Test
    void pointerRejectsUpwardParallelBeyondReachAndBehindEye() {
        assertEquals(-1, TableGeometry.intersection(2, 0, 1));
        assertEquals(-1, TableGeometry.intersection(2, 1, 1));
        assertEquals(-1, TableGeometry.intersection(1, -1, 2));
        assertEquals(-1, TableGeometry.intersection(20, -1, 1));
        assertEquals(2, TableGeometry.intersection(2, -.5, 1));
        assertEquals(-1, TableGeometry.intersection(Double.NaN, -1, 1));
    }

    @Test
    void chineseStarCampArtworkMatchesAllSixtyOriginalStartingHoles() {
        BoardGame game = GameFactory.create("checkers", 6, 0);
        int colored = 0;
        for (Cell c : game.cells()) {
            assertEquals(c.owner(), BoardMapArtwork.camp(c), c.id());
            if (BoardMapArtwork.camp(c) >= 0) colored++;
        }
        assertEquals(60, colored);
    }

    @Test
    void diceAlwaysFinishesWithTheAuthoritativeFaceUpAndOppositeFacesSumToSeven() {
        for (int face = 1; face <= 6; face++) {
            for (String model : List.of("tabletop/yacht/die", "tabletop/ludo/die", "tabletop/ludo/die_compact")) {
                String prefix = "face_" + face + "_dot_";
                assertEquals(face, ModelRepository.require(model).blocks().stream()
                        .filter(part -> part.name().startsWith(prefix)).count(), model);
            }
            Vector3f normal = new Vector3f(0, 1, 0).rotate(TableView.faceRotation(face));
            assertEquals(
                    -1,
                    normal.dot(new Vector3f(0, 1, 0).rotate(TableView.faceRotation(7 - face))),
                    1e-5);
            normal.rotate(TableView.faceRotation(face).invert());
            assertEquals(1, normal.y, 1e-5);
        }
    }

    @Test
    void originalModelsHaveBoundedEntityCountsAndFitTheirCells() throws Exception {
        for (String kind : KINDS) {
            BoardGame game = GameFactory.create(kind, kind.equals("checkers") ? 6 : 2, 0);
            if (kind.startsWith("go")) game.apply(0, "place:0,0");
            for (Cell c : game.cells())
                if (c.owner() >= 0) {
                    List<ModelDefinition.Block> parts = BoardPieceModels.piece(kind, c, game.publicInfo());
                    assertFalse(parts.isEmpty());
                    assertTrue(parts.size() <= 10, kind + " " + c.piece() + " " + parts.size());
                    for (var p : parts) {
                        double limit = .5;
                        assertTrue(Math.abs(p.x()) + p.w() / 2 < limit);
                        assertTrue(Math.abs(p.z()) + p.d() / 2 < limit);
                        assertTrue(p.bottom() >= 0 && p.h() > 0 && p.w() > 0 && p.d() > 0);
                    }
                }
        }
    }

    @Test
    void reversiFacesAndDeadStoneCrossHaveNoOverlappingTopSurfaces() {
        for (var parts : List.of(BoardPieceModels.reversi(), BoardPieceModels.deadStoneMarks()))
            for (int i = 0; i < parts.size(); i++)
                for (int j = i + 1; j < parts.size(); j++) {
                    var a = parts.get(i);
                    var b = parts.get(j);
                    if (Math.abs(a.bottom() + a.h() - b.bottom() - b.h()) > 1e-9) continue;
                    double x = Math.min(a.x() + a.w() / 2, b.x() + b.w() / 2)
                            - Math.max(a.x() - a.w() / 2, b.x() - b.w() / 2);
                    double z = Math.min(a.z() + a.d() / 2, b.z() + b.d() / 2)
                            - Math.max(a.z() - a.d() / 2, b.z() - b.d() / 2);
                    assertTrue(x <= 1e-9 || z <= 1e-9, "Coplanar native cross surfaces");
                }
    }

    @Test
    void stoneDiscsKeepThreePartsWithoutOverlappingVolumesOrTopFaces() {
        var parts = ModelRepository.require("tabletop/shared/stones/black").blocks();
        assertEquals(3, parts.size());
        for (int i = 0; i < parts.size(); i++)
            for (int j = i + 1; j < parts.size(); j++) {
                var a = parts.get(i);
                var b = parts.get(j);
                double x =
                        Math.min(a.x() + a.w() / 2, b.x() + b.w() / 2)
                                - Math.max(a.x() - a.w() / 2, b.x() - b.w() / 2);
                double z =
                        Math.min(a.z() + a.d() / 2, b.z() + b.d() / 2)
                                - Math.max(a.z() - a.d() / 2, b.z() - b.d() / 2);
                assertTrue(
                        x <= 1e-9 || z <= 1e-9,
                        "Disc strips must meet without overlapping top surfaces");
            }
    }

    @Test
    void nativeBoardArtDrawsEverySupportedBoard() {
        for (String kind : KINDS) {
            BoardGame game =
                    GameFactory.create(
                            kind,
                            kind.equals("checkers") ? 6 : kind.equals("ludo") ? 4 : 2,
                            0);
            TableGeometry t = new TableGeometry(kind, game.cells());
            var image = BoardMapArtwork.draw(t);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertNotEquals(image.getRGB(0, 0), image.getRGB(128, 128));

        }
    }

    @Test
    void pointerClickKeepsIdentityTurnAndDuplicatePacketGates() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.click());
        verify(f.plugin.games()).applyTableAction(eq(f.room), eq(0), eq(new JsonPrimitive("place:7,7")), eq(f.player));
        assertTrue(f.click());
        verify(f.plugin.games(), times(1)).applyTableAction(any(), anyInt(), any(), any());
        f.clicks.clear();
        when(f.plugin.allowed(f.player)).thenReturn(false);
        assertFalse(f.click());
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.games().session(f.player)).thenReturn(null);
        assertFalse(f.click());
        when(f.plugin.games().session(f.player)).thenReturn(f.room);
        f.room.board().apply(0, "place:7,7");
        f.clicks.clear();
        assertTrue(f.click());
        verify(f.plugin.games(), times(1)).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test
    void colorEightFeltMenuRequiresSneakingAndRightClick() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "color-eight");
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.set(f.room, "board", mock(HandGame.class));
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
        set(view, "geometry", new TableGeometry("color-eight", List.of()));
        when(f.player.getEyeLocation())
                .thenAnswer(
                        a ->
                                new Location(f.world, 0, 1.62, 2.25)
                                        .setDirection(new Vector(0, -.74, -2.25)));
        assertTrue(f.click());
        verify(f.plugin.menus(), never()).room(any(), any());
        f.clicks.clear();
        when(f.player.isSneaking()).thenReturn(true);
        assertTrue(f.click());
        verify(f.plugin.menus(), never()).room(any(), any());
        f.clicks.clear();
        Method method =
                TableWorldController.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(f.tableWorld, f.player, true));
        verify(f.plugin.menus()).room(f.player, f.room);
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test
    void publicTableMenusRequireSneakingAndRightClick() throws Exception {
        for (String kind : List.of("yacht", "chess")) {
            Fixture f = new Fixture();
            set(f.room, "kind", kind);
            SessionTestSupport.set(f.room, "board", GameFactory.create(kind, 2, 0));
            SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
            TableView view =
                    ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
            when(view.hitPiece(any(), any(), any())).thenReturn(new TableView.Hit("@menu", .4));
            Method method =
                    TableWorldController.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
            method.setAccessible(true);
            for (boolean sneak : new boolean[] {false, true}) {
                when(f.player.isSneaking()).thenReturn(sneak);
                for (boolean rightClick : new boolean[] {false, true}) {
                    f.clicks.clear();
                    clearInvocations(f.plugin.menus());
                    assertTrue((boolean) method.invoke(f.tableWorld, f.player, rightClick));
                    verify(f.plugin.menus(), times(sneak && rightClick ? 1 : 0)).room(f.player, f.room);
                }
            }
            verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        }
    }

    @Test
    void focusedMahjongRightClickOpensMenuAndReleasesFocusBeforeTileActions() throws Exception {
        for (boolean tile : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            set(f.room, "kind", "mahjong");
            SessionTestSupport.set(f.room, "board", mock(HandGame.class));
            SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
            SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
            when(f.plugin.tablePlayers().hasTableFocus(f.player)).thenReturn(true);
            when(f.player.isSneaking()).thenReturn(true);
            TableView view =
                    ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
            if (tile)
                when(view.handHit(eq(f.player), any(), any())).thenReturn("public:discard:1:a");
            Method method =
                    TableWorldController.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(f.tableWorld, f.player, true));
            var order = inOrder(f.plugin.tablePlayers(), f.plugin.menus());
            order.verify(f.plugin.tablePlayers()).releaseFocus(f.player);
            order.verify(f.plugin.menus()).room(f.player, f.room);
            verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        }
    }

    @Test
    void focusedMahjongMenuOpensOnceAcrossLobbyAndWorldListeners() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        SessionTestSupport.set(f.room, "board", mock(HandGame.class));
        SessionTestSupport.module(f.plugin, "tableWorld", f.tableWorld);
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
        when(f.plugin.tablePlayers().hasTableFocus(f.player)).thenReturn(true);
        var tablePlayers = f.plugin.tablePlayers();
        doAnswer(invocation -> {
            when(f.plugin.tablePlayers().hasTableFocus(f.player)).thenReturn(false);
            return null;
        }).when(tablePlayers).releaseFocus(f.player);
        when(f.player.isSneaking()).thenReturn(true);
        TableAccessController lobby = new TableAccessController(f.plugin);
        var event = new org.bukkit.event.player.PlayerInteractEvent(f.player,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null,
                org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        lobby.use(event);
        f.tableWorld.use(event);
        verify(f.plugin.menus(), times(1)).room(f.player, f.room);
        verify(f.plugin.tablePlayers(), times(1)).releaseFocus(f.player);
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test
    void mahjongOrdinaryFeltClicksDoNotOpenMenu() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        SessionTestSupport.set(f.room, "board", mock(HandGame.class));
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        for (boolean rightClick : new boolean[] {false, true}) {
            f.clicks.clear();
            Method method =
                    TableWorldController.class.getDeclaredMethod("worldClick", Player.class, boolean.class);
            method.setAccessible(true);
            assertTrue((boolean) method.invoke(f.tableWorld, f.player, rightClick));
        }
        verify(f.plugin.menus(), never()).room(any(), any());
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test
    void focusedMahjongLeftClickOnlyObservesAndNeverPlaysTheHandTile() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        HandGame game = mock(HandGame.class);
        SessionTestSupport.set(f.room, "board", game);
        when(game.hand(0)).thenReturn(List.of(new HandGame.Piece("a", "1m")));
        when(game.legalActions(0)).thenReturn(List.of("discard:a"));
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
        when(f.plugin.tablePlayers().hasTableFocus(f.player)).thenReturn(true);
        when(f.player.isSneaking()).thenReturn(true);
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
        when(view.handHit(eq(f.player), any(), any())).thenReturn("a");
        when(view.mahjongHandAction(f.player, "a")).thenReturn("discard:a");
        assertTrue(f.click());
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        verify(f.plugin.tablePlayers(), never()).releaseFocus(any());
        verify(f.plugin.menus(), never()).room(any(), any());
    }

    @Test
    void blockInteractionRoutesRightClickToTheColorEightMenuButNotLeftClick() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "color-eight");
        SessionTestSupport.set(f.room, "board", mock(HandGame.class));
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        when(f.player.isSneaking()).thenReturn(true);
        for (var action :
                List.of(
                        org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,
                        org.bukkit.event.block.Action.RIGHT_CLICK_AIR)) {
            f.clicks.clear();
            var event =
                    new org.bukkit.event.player.PlayerInteractEvent(
                            f.player,
                            action,
                            null,
                            null,
                            org.bukkit.block.BlockFace.SELF,
                            org.bukkit.inventory.EquipmentSlot.HAND);
            f.tableWorld.use(event);
            assertTrue(event.isCancelled());
            verify(f.plugin.menus(), times(action.isRightClick() ? 1 : 0)).room(f.player, f.room);
        }
    }

    @Test
    void anOccupiedPlacementReportsTheCellProblemWithoutChatSpam() throws Exception {
        Fixture f = new Fixture();
        f.room.board().apply(0, "place:7,7");
        f.room.board().apply(1, "place:8,7");
        assertTrue(f.click());
        verify(f.plugin, never()).tell(any(), any(net.kyori.adventure.text.Component.class));
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        verify(f.player)
                .sendActionBar(
                        Language.component("hint.position.unavailable")
                                .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @Test
    void continuousMahjongPressesDuringOtherTurnsMaintainTheReplacementGuard() throws Exception {
        Fixture f = new Fixture();
        set(f.room, "kind", "mahjong");
        HandGame game = mock(HandGame.class);
        when(game.currentPlayer()).thenReturn(1);
        SessionTestSupport.set(f.room, "board", game);
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
        when(view.handHit(eq(f.player), any(), any())).thenReturn("other-tile");
        assertTrue(f.click());
        verify(view).maintainMahjongPress(f.player);
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test
    void selectingAndCancellingAChessPieceChangesOnlyTheActingPlayersOverlay() throws Exception {
        Fixture f = new Fixture();
        when(f.plugin.getConfig()).thenReturn(mock(org.bukkit.configuration.file.FileConfiguration.class));
        set(f.room, "kind", "chess");
        SessionTestSupport.set(f.room, "board", GameFactory.create("chess", 2, 0));
        var view = ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
        var click = TableWorldController.class.getDeclaredMethod("pickCell", Player.class, GameSession.class, int.class, String.class);
        click.setAccessible(true);
        click.invoke(f.tableWorld, f.player, f.room, 0, "e2");
        verify(view).cursor(eq(f.player), argThat(selection -> selection != null && selection.source().equals("e2")), eq("e2"));
        click.invoke(f.tableWorld, f.player, f.room, 0, "e2");
        verify(view).cursor(f.player, null, "e2");
        verify(view, never()).sync();
        assertEquals(0, f.room.history.size());
    }

    @Test
    void suspendingTheViewAlsoClearsPrivateWorldSelection() throws Exception {
        Fixture f = new Fixture();
        SessionTestSupport.module(f.plugin, "tableWorld", f.tableWorld);
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        doCallRealMethod().when(f.plugin).closePlayerViews(f.player);
        var selections = (Map<UUID, BoardSelection>) TableViewTest.field(f.tableWorld, "selections");
        selections.put(
                f.player.getUniqueId(),
                new BoardSelection(f.room.id, f.room.revision(), "7,7", List.of()));
        SessionTestSupport.install(f.plugin, new HashMap<UUID, GameSession>());
        TableView view =
                ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
        f.plugin.closePlayerViews(f.player);
        assertTrue(selections.isEmpty());
        verify(view).clear(f.player);
        verify(f.plugin.menus()).forget(f.player);
    }

    @Test
    void pointerDoesNotClickThroughWallsOrOutsideItsTable() throws Exception {
        Fixture f = new Fixture();
        when(f.world.rayTraceBlocks(
                        any(), any(), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(new RayTraceResult(new Vector(0, 1, 1)));
        assertFalse(f.click());
        verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        when(f.world.rayTraceBlocks(
                        any(), any(), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
                .thenReturn(null);
        when(f.player.getEyeLocation())
                .thenReturn(new Location(f.world, 0, 1.62, 2.25).setDirection(new Vector(0, 1, 0)));
        assertFalse(f.click());
    }

    @Test
    void emptyMahjongDrawSlotKeepsRepeatedPressProtectionDuringOtherTurns() throws Exception {
        for (boolean overTable : new boolean[] {false, true}) {
            Fixture f = new Fixture();
            set(f.room, "kind", "mahjong");
            HandGame game = mock(HandGame.class);
            when(game.currentPlayer()).thenReturn(1);
            SessionTestSupport.set(f.room, "board", game);
            TableView view =
                    ((Map<UUID, TableView>) TableViewTest.field(f.tableWorld, "views")).get(f.room.id);
            SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
            when(f.player.getEyeLocation())
                    .thenReturn(
                            new Location(f.world, 0, 1.62, 2.25)
                                    .setDirection(new Vector(0, overTable ? -.74 : 1, -2.25)));
            assertEquals(overTable, f.click());
            verify(view).maintainMahjongPress(f.player);
            verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        }
    }

    public static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    static final class Fixture {
        final TDGamesPlugin plugin = SessionTestSupport.plugin();
        final TableWorldController tableWorld = mock(TableWorldController.class, CALLS_REAL_METHODS);
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final GameSession room = new GameSession(UUID.randomUUID(), "gomoku", 2, 0, 0);
        final Map<UUID, Long> clicks = new HashMap<>();

        Fixture() throws Exception {
            UUID id = UUID.randomUUID();
            room.join(id, "Test Player");
            room.fillBots();
            SessionTestSupport.set(room, "board", GameFactory.create("gomoku", 2, 0));
            SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
            TableView view = mock(TableView.class);
            set(view, "room", room);
            set(view, "origin", new Location(world, 0, .85, 0));
            set(view, "geometry", new TableGeometry("gomoku", room.board().cells()));
            doCallRealMethod().when(view).menuHit(any(), any());
            doCallRealMethod().when(view).boardPlaneY(any());
            set(tableWorld, "plugin", plugin);
            set(tableWorld, "views", new HashMap<>(Map.of(room.id, view)));
            set(tableWorld, "clicks", clicks);
            set(tableWorld, "selections", new HashMap<>());
            when(plugin.allowed(player)).thenReturn(true);
            when(plugin.games().session(player)).thenReturn(room);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getWorld()).thenReturn(world);
            when(player.getEyeLocation())
                    .thenAnswer(
                            a ->
                                    new Location(world, 0, 1.62, 2.25)
                                            .setDirection(new Vector(0, -.74, -2.25)));
        }

        boolean click() throws Exception {
            Method method = TableWorldController.class.getDeclaredMethod("worldClick", Player.class);
            method.setAccessible(true);
            return (boolean) method.invoke(tableWorld, player);
        }
    }
}

package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController;
import GoldenEggOVO.TDGames.Shared.interaction.TableAccessController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.Location;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableMenuHitTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private TableWorldController tableWorld(TableViewTest.Fixture f) throws Exception {
        var tableWorld = mock(TableWorldController.class, CALLS_REAL_METHODS);
        GameRulesTest.set(tableWorld, "plugin", f.plugin);
        GameRulesTest.set(tableWorld, "views", new HashMap<>(Map.of(f.room.id, f.view)));
        GameRulesTest.set(tableWorld, "clicks", new HashMap<>());
        GameRulesTest.set(tableWorld, "selections", new HashMap<>());
        SessionTestSupport.module(f.plugin, "tableWorld", tableWorld);
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.games().session(f.player)).thenReturn(f.room);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.player.isSneaking()).thenReturn(true);
        return tableWorld;
    }

    private void aim(TableViewTest.Fixture f, double x, double z) {
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(x, 2, z).setDirection(new Vector(0, -1, 0)));
    }

    @Test void seatedPlayerCanOpenFromTheEmptyBoardBorderWithoutSelectingACell() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var tableWorld = tableWorld(f);
        aim(f, 1.08, .60);
        assertTrue(tableWorld.worldClick(f.player, true));
        assertTrue(f.room.history.isEmpty());
        verify(f.plugin.menus()).room(f.player, f.room);
    }

    @Test void seatedPlayerCanOpenFromAGapBetweenGomokuCells() throws Exception {
        var f = new TableViewTest.Fixture("gomoku");
        var tableWorld = tableWorld(f);
        aim(f, .06, .06);
        assertTrue(tableWorld.worldClick(f.player, true));
        verify(f.plugin.menus()).room(f.player, f.room);
        assertTrue(f.room.history.isEmpty());
    }

    @Test void sneakingSwingCannotConsumeTheRightClickThatOpensTheMenu() throws Exception {
        var f = new TableViewTest.Fixture("gomoku");
        var tableWorld = tableWorld(f);
        aim(f, 0, 0);
        tableWorld.swing(new org.bukkit.event.player.PlayerAnimationEvent(f.player,
                org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
        assertTrue(tableWorld.worldClick(f.player, true));
        assertTrue(tableWorld.worldClick(f.player, true));
        verify(f.plugin.menus(), times(1)).room(f.player, f.room);
        assertTrue(f.room.history.isEmpty());
    }

    @Test void yachtPlainFeltAndLeftScoreTableOpenOnceAcrossBothListeners() throws Exception {
        for (double x : new double[] {1.1, -2.30}) {
            var f = new TableViewTest.Fixture("yacht");
            var tableWorld = tableWorld(f);
            aim(f, x, .90);
            var lobby = new TableAccessController(f.plugin);
            assertTrue(lobby.request(f.player));
            assertTrue(tableWorld.worldClick(f.player, true));
            verify(f.plugin.menus(), times(1)).room(f.player, f.room);
            assertTrue(f.room.history.isEmpty());
        }
    }

    @Test void connectFourTableAndTheUpperRackBothOpenTheirMenu() throws Exception {
        var table = new TableViewTest.Fixture("connectfour");
        var tableWorld = tableWorld(table);
        aim(table, .7, .8);
        assertTrue(tableWorld.worldClick(table.player, true));
        verify(table.plugin.menus()).room(table.player, table.room);
        var rack = new TableViewTest.Fixture("connectfour");
        tableWorld = tableWorld(rack);
        when(rack.player.getEyeLocation()).thenReturn(
                rack.view.origin.clone().add(0, 1.79, 2).setDirection(new Vector(0, 0, -1)));
        assertTrue(tableWorld.worldClick(rack.player, true));
        verify(rack.plugin.menus()).room(rack.player, rack.room);
    }

    @Test void solidWallAndExcessiveReachPreventTheEmptyTableMenu() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var tableWorld = tableWorld(f);
        aim(f, 1.08, .60);
        when(f.world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new RayTraceResult(new Vector(1.08, 82, .60)));
        assertFalse(tableWorld.worldClick(f.player, true));
        when(f.world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean())).thenReturn(null);
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(1.08, 6, .60).setDirection(new Vector(0, -1, 0)));
        assertFalse(tableWorld.worldClick(f.player, true));
        verifyNoInteractions(f.plugin.menus());
    }

    @Test void ordinaryRightClickAndSneakingLeftClickNeverOpenTheEmptyTableMenu() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var tableWorld = tableWorld(f);
        aim(f, 1.08, .60);
        when(f.player.isSneaking()).thenReturn(false);
        assertFalse(tableWorld.worldClick(f.player, true));
        when(f.player.isSneaking()).thenReturn(true);
        assertFalse(tableWorld.worldClick(f.player, false));
        verifyNoInteractions(f.plugin.menus());
    }

    @Test void focusedPlayerMustStillAimAtAReachableTableAndSneak() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var tableWorld = tableWorld(f);
        SessionTestSupport.module(f.plugin, "tablePlayers", mock(TablePlayerController.class));
        when(f.plugin.tablePlayers().hasTableFocus(f.player)).thenReturn(true);
        when(f.player.getEyeLocation()).thenReturn(
                f.view.origin.clone().add(0, 2, 0).setDirection(new Vector(0, 1, 0)));
        tableWorld.worldClick(f.player, true);
        aim(f, 1.08, .60);
        when(f.player.isSneaking()).thenReturn(false);
        tableWorld.worldClick(f.player, true);
        verifyNoInteractions(f.plugin.menus());
        when(f.player.isSneaking()).thenReturn(true);
        assertTrue(tableWorld.worldClick(f.player, true));
        verify(f.plugin.menus()).room(f.player, f.room);
        verify(f.plugin.tablePlayers()).releaseFocus(f.player);
    }

    @Test void outsiderCanAimAtYachtLeftTableMahjongBorderAndLudoSideTray() throws Exception {
        for (String kind : List.of("yacht", "mahjong", "ludo")) {
            var plugin = SessionTestSupport.plugin();
            SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
            var room = new GameSession(UUID.randomUUID(), kind, 2, 0, 0);
            room.sideTray = kind.equals("ludo");
            SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
            var world = mock(org.bukkit.World.class);
            when(plugin.tableWorld().center(0)).thenReturn(new Location(world, 0, 80, 0));
            var entry = new TableAccessController(plugin).collect().getFirst();
            double x = kind.equals("yacht") ? -2.30 : kind.equals("mahjong") ? 1.45 : 2.65;
            assertTrue(Double.isFinite(TableAccessController.hit(entry,
                    new Location(world, x, 83, .60), new Vector(0, -1, 0))), kind);
        }
    }

    @Test void lobbyCannotPickAirBesideTheRackOrTheGapBetweenYachtTables() throws Exception {
        for (String kind : List.of("connectfour", "yacht", "color-eight")) {
            var plugin = SessionTestSupport.plugin();
            SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
            var room = new GameSession(UUID.randomUUID(), kind, 2, 0, 0);
            SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
            var world = mock(org.bukkit.World.class);
            when(plugin.tableWorld().center(0)).thenReturn(new Location(world, 0, 80, 0));
            var entry = new TableAccessController(plugin).collect().getFirst();
            Location eye = kind.equals("connectfour") ? new Location(world, -2.5, 82.5, .9)
                    : kind.equals("yacht") ? new Location(world, -.765, 83, .5)
                    : new Location(world, 1.4, 83, 1.4);
            Vector direction = kind.equals("connectfour") ? new Vector(1, 0, 0) : new Vector(0, -1, 0);
            assertEquals(Double.POSITIVE_INFINITY, TableAccessController.hit(entry, eye, direction), kind);
        }
    }

    @Test void roundTableSideIsReachableWithoutExtendingTheUprightRackIntoAir() throws Exception {
        var plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        var world = mock(org.bukkit.World.class);
        when(plugin.tableWorld().center(0)).thenReturn(new Location(world, 0, 80, 0));
        var room = new GameSession(UUID.randomUUID(), "color-eight", 2, 0, 0);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        var entry = new TableAccessController(plugin).collect().getFirst();
        assertEquals(1, TableAccessController.hit(entry, new Location(world, 2.5, 81.03, 0),
                new Vector(-1, 0, 0)), 1e-6);
        assertEquals(Double.POSITIVE_INFINITY, TableAccessController.hit(entry,
                new Location(world, 7.1, 81.03, 0), new Vector(-1, 0, 0)));
        room = new GameSession(UUID.randomUUID(), "connectfour", 2, 0, 0);
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        entry = new TableAccessController(plugin).collect().getFirst();
        assertTrue(Double.isFinite(TableAccessController.hit(entry, new Location(world, 2.5, 82, .09),
                new Vector(-1, 0, 0))));
        assertTrue(Double.isFinite(TableAccessController.hit(entry, new Location(world, 1.15, 82, 2),
                new Vector(0, 0, -1))));
        assertEquals(Double.POSITIVE_INFINITY, TableAccessController.hit(entry,
                new Location(world, 2.5, 82, .15), new Vector(-1, 0, 0)));
    }
}

package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.text.MessageText;

import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.interaction.TableAccessController;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.TablePlacement;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.yacht.YachtGame;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YachtTableTest {
    @Test void rollButtonOutlinesBothModelLayersPrivatelyAtTheirActualHeights() {
        var f = new TableViewTest.Fixture("yacht");
        int count = f.entities.size();
        f.view.cursor(f.player, null, "@roll");
        assertEquals(count + 2, f.entities.size());
        var outlines = java.util.List.copyOf(f.entities.subList(count, f.entities.size()));
        for (var part : outlines) {
            var outline = assertInstanceOf(org.bukkit.entity.BlockDisplay.class, part);
            verify(outline).setGlowing(true);
            verify(outline).setGlowColorOverride(org.bukkit.Color.YELLOW);
            verify(outline).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, outline);
            assertTrue(f.transforms.get(outline).getTranslation().y < .02f);
        }
        for (int i = 0; i < 20; i++) f.view.cursor(f.player, null, "@roll");
        assertEquals(count + 2, f.entities.size());
        assertTrue(f.room.history.isEmpty());
        f.view.clear(f.player);
        for (var outline : outlines) verify(outline).remove();
        f.view.close();
    }
    @Test
    void scoreHintsDistinguishRollRequiredPreviewAndRecordedScore() {
        var f = new TableViewTest.Fixture("yacht");
        var category = Language.component("score.category.ones");
        f.view.cursor(f.player, null, "@score:ones");
        var hint = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player).sendActionBar(hint.capture());
        assertEquals(MessageText.plain(Language.component("table.yacht.score-roll-first", "category", category)),
                MessageText.plain(hint.getValue()));

        f.move("roll");
        for (int i = 0; i < 24; i++) f.view.tick();
        var board = (YachtGame) f.room.board();
        int preview = YachtGame.score(0, board.dice());
        clearInvocations(f.player);
        f.view.cursor(f.player, null, "@score:ones");
        verify(f.player).sendActionBar(hint.capture());
        assertEquals(MessageText.plain(Language.component("table.yacht.score-preview", "category", category, "score", preview)),
                MessageText.plain(hint.getValue()));

        f.move("score:ones");
        f.move("roll");
        for (int i = 0; i < 24; i++) f.view.tick();
        f.move("score:twos");
        clearInvocations(f.player);
        f.view.cursor(f.player, null, "@score:ones");
        verify(f.player).sendActionBar(hint.capture());
        assertEquals(MessageText.plain(Language.component("table.yacht.score-recorded", "category", category, "score", preview)),
                MessageText.plain(hint.getValue()));
        f.view.close();
    }

    @Test
    void botScoreHeadersFollowLanguageReloadWithoutASeatChange() throws Exception {
        var folder = temp.resolve("languages");
        Language.load(folder, "en_US", warning -> fail(warning));
        var f = new TableViewTest.Fixture("yacht");
        SessionTestSupport.mutableSeats(f.room).set(1, new GameSession.Seat(java.util.UUID.randomUUID(), "Bot 2", true));
        f.view.tick();
        var table = TableViewTest.field(f.view, "yachtTable");
        var headers = (org.bukkit.entity.TextDisplay[]) TableViewTest.field(table, "headers");
        clearInvocations(headers);
        java.nio.file.Files.writeString(folder.resolve("custom.yml"), "room.bot: 'CPU {number}'\n");
        try {
            assertTrue(Language.reload(folder, "custom", warning -> fail(warning)));
            f.view.tick();
            var text = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify(headers[1]).text(text.capture());
            assertEquals("2\nCPU 2", GoldenEggOVO.TDGames.Core.text.MessageText.plain(text.getValue()));
        } finally {
            f.view.close();
            assertTrue(Language.reload(folder, "en_US", warning -> fail(warning)));
        }
    }

    @Test void scoreSheetHasNoRaisedRailsAndDiceTrayKeepsItsRails() {
        var f = new TableViewTest.Fixture("yacht");
        var rails = f.entities.stream().filter(org.bukkit.entity.BlockDisplay.class::isInstance)
                .filter(e -> {
                    var pose = f.transforms.get(e);
                    return pose.getTranslation().y() + pose.getScale().y() > .04
                            && Math.min(pose.getScale().x(), pose.getScale().z()) < .10
                            && Math.max(pose.getScale().x(), pose.getScale().z()) > 1.7;
                }).toList();
        assertEquals(4, rails.size());
        assertTrue(rails.stream().allMatch(e -> f.transforms.get(e).getTranslation().x() > -.8));
        f.view.close();
    }

    @Test void cursorFramesFitScoreCellsArePrivateAndReuseIdleEntities() {
        var f = new TableViewTest.Fixture("yacht");
        int count = f.entities.size();
        f.view.cursor(f.player, null, "@score:ones");
        var edges = java.util.List.copyOf(f.entities.subList(count, f.entities.size()));
        assertEquals(4, edges.size());
        for (var edge : edges) {
            verify(edge).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, edge);
            var pose = f.transforms.get(edge);
            assertEquals(.0125, pose.getTranslation().y(), 1e-6);
            assertEquals(.003, pose.getScale().y(), 1e-6);
            assertTrue(pose.getTranslation().x() >= -1.621 - 1e-6);
            assertTrue(pose.getTranslation().x() + pose.getScale().x() <= -1.279 + 1e-6);
            assertTrue(pose.getTranslation().z() >= -.847 - 1e-6);
            assertTrue(pose.getTranslation().z() + pose.getScale().z() <= -.753 + 1e-6);
        }
        for (int i = 0; i < 100; i++) f.view.cursor(f.player, null, "@score:ones");
        assertEquals(count + 4, f.entities.size());
        f.view.clear(f.player);
        for (var edge : edges) verify(edge).remove();
        f.view.close();
    }

    @Test void dieHoverMovesToTheKeepSlotAndClearsDuringThrowsAndOnExit() {
        var f = new TableViewTest.Fixture("yacht", "roll");
        int count = f.entities.size();
        f.view.cursor(f.player, null, "die2");
        assertEquals(count + 1, f.entities.size());
        var edges = java.util.List.copyOf(f.entities.subList(count, f.entities.size()));
        verify(edges.getFirst()).setGlowing(true);
        verify((org.bukkit.entity.Display) edges.getFirst()).setGlowColorOverride(org.bukkit.Color.YELLOW);
        verify(edges.getFirst()).setVisibleByDefault(false);
        verify(f.player).showEntity(f.plugin, edges.getFirst());
        for (int i = 0; i < 20; i++) f.view.cursor(f.player, null, "die2");
        assertEquals(count + 1, f.entities.size(), "Idle hover must reuse its outline");
        f.move("hold:die2");
        f.view.cursor(f.player, null, "die2");
        for (var edge : edges) verify(edge).remove();
        var held = java.util.List.copyOf(f.entities.subList(count + 1, f.entities.size()));
        assertEquals(1, held.size());
        for (var edge : held) {
            var pose = f.transforms.get(edge);
            assertTrue(pose.getTranslation().z() >= -.778 - 1e-6);
            assertTrue(pose.getTranslation().z() + pose.getScale().z() <= -.582 + 1e-6);
        }
        f.move("roll");
        for (var edge : held) verify(edge).remove();
        for (int i = 0; i < 24; i++) f.view.tick();
        count = f.entities.size();
        f.view.cursor(f.player, null, "die2");
        var finalEdges = java.util.List.copyOf(f.entities.subList(count, f.entities.size()));
        f.view.cursor(f.player, null, null);
        for (var edge : finalEdges) verify(edge).remove();
        f.view.close();
    }

    @Test void activeScoreColumnFitsTheGridForEverySeatAndCapacity() throws Exception {
        for (int capacity : java.util.List.of(2, 4)) {
            var f = new TableViewTest.Fixture("yacht", capacity);
            var table = TableViewTest.field(f.view, "yachtTable");
            var highlight = (org.bukkit.entity.BlockDisplay) TableViewTest.field(table, "activeColumn");
            verify(highlight).setBlock(argThat(data -> data.getMaterial() == org.bukkit.Material.YELLOW_TERRACOTTA));
            for (int seat = 0; seat < capacity; seat++) {
                var transform = org.mockito.ArgumentCaptor.forClass(org.bukkit.util.Transformation.class);
                verify(highlight, atLeastOnce()).setTransformation(transform.capture());
                var pose = transform.getValue();
                double left = -1.625 + seat * .70 / capacity;
                assertEquals(left + .004, pose.getTranslation().x(), 1e-6);
                assertEquals(.70 / capacity - .008, pose.getScale().x(), 1e-6);
                assertEquals(-.85 + .004, pose.getTranslation().z(), 1e-6);
                assertEquals(1.50 - .008, pose.getScale().z(), 1e-6);
                f.move("roll");
                f.move("score:choice");
            }
            f.view.close();
        }
    }

    @Test void scoreHeadersFollowLateJoinsAndSeatReorderingWithoutBoardChanges() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        SessionTestSupport.mutableSeats(f.room).clear();
        SessionTestSupport.mutableSeats(f.room).add(new GameSession.Seat(java.util.UUID.randomUUID(), "Alpha", false));
        f.view.tick();
        Object table = TableViewTest.field(f.view, "yachtTable");
        var headers = (org.bukkit.entity.TextDisplay[]) TableViewTest.field(table, "headers");
        clearInvocations(headers);
        SessionTestSupport.mutableSeats(f.room).add(new GameSession.Seat(java.util.UUID.randomUUID(), "Bravo", false));
        f.view.tick();
        var text = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(headers[1]).text(text.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(text.getValue()).contains("Bravo"));
        java.util.Collections.swap(SessionTestSupport.mutableSeats(f.room), 0, 1);
        f.view.tick();
        verify(headers[0], atLeastOnce()).text(text.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(text.getValue()).contains("Bravo"));
        f.view.close();
    }
    @Test void packedDieFacesFollowTheSameWorldNormalsAsNativeDice() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        SessionTestSupport.module(f.plugin, "pack", new ResourcePackService(f.plugin, () -> true, id -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER)));
        var audience = new DisplayAudience(f.plugin, f.view.origin);
        var die = new YachtDie(f.plugin, f.room, f.view.origin, new org.bukkit.NamespacedKey("3dgames", "board-cell"),
                audience, true, .18, "die0");
        var item = (org.bukkit.entity.ItemDisplay) TableViewTest.field(die, "packed");
        for (int face = 1; face <= 6; face++) {
            clearInvocations(item);
            var pose = DiceMotion.rest(.18, face);
            die.pose(pose);
            var transform = org.mockito.ArgumentCaptor.forClass(org.bukkit.util.Transformation.class);
            verify(item).setTransformation(transform.capture());
            for (var axis : java.util.List.of(new org.joml.Vector3f(1,0,0),new org.joml.Vector3f(0,1,0),new org.joml.Vector3f(0,0,1))) {
                var expected = pose.rotation().transform(new org.joml.Vector3f(axis));
                var actual = transform.getValue().getLeftRotation().transform(new org.joml.Vector3f(axis));
                assertTrue(expected.distance(actual) < .001, "Face " + face + " has a reversed normal");
            }
        }
        die.close(); f.view.close();
    }
    @Test void scoreStandHasDirectRowsAndIsSeparateFromDiceControls() {
        var f = new TableViewTest.Fixture("yacht", "roll");
        assertHit(f, -1.65, -.80, "@score:ones");
        assertHit(f, -1.65, .50, "@score:yacht");
        assertHit(f, -1.65, .88, "@menu");
        assertHit(f, .45, .82, "@roll");
        assertTrue(TablePlacement.overlaps(0, 80, 0, false, 2.45, -3.3, 80, 0, false, 1.125));
        f.view.close();
    }
    @org.junit.jupiter.api.io.TempDir(factory = WorkspaceTempFactory.class) java.nio.file.Path temp;
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void yachtIsAvailableAndFinalRollStillAllowsMovingDiceBetweenSlots() {
        assertTrue(GameCatalog.IDS.contains("yacht"));
        var game = new YachtGame(2, 41);
        for (int i = 0; i < 3; i++) game.apply(0, "roll");
        int[] dice = game.dice();
        game.apply(0, "hold:die2");
        assertTrue(game.held(2));
        assertFalse(game.legalActions(0).contains("roll"));
        game.apply(0, "hold:die2");
        assertFalse(game.held(2));
        assertArrayEquals(dice, game.dice());
        assertTrue(game.legalActions(1).isEmpty());
        game.apply(0, "score:choice");
        assertEquals(1, game.currentPlayer());
        assertEquals(0, game.rolls());
        assertFalse(game.held(2));
        assertFalse(game.legalActions(0).contains("score:choice"));
    }

    @Test void fiveSmallDiceSettleInARowAndKeepSlotsAreReversible() {
        var f = new TableViewTest.Fixture("yacht");
        f.move("roll");
        assertTrue(f.view.rolling());
        for (int i = 0; i < 24; i++) f.view.tick();
        assertFalse(f.view.rolling());
        for (int i = 0; i < 5; i++) assertHit(f, .32 + (i - 2) * .36, .15, "die" + i);
        assertHit(f, .32, -.68, "@menu");
        f.move("hold:die2");
        assertHit(f, .32, -.68, "die2");
        assertHit(f, .32, .15, "@menu");
        int count = f.entities.size();
        f.move("roll");
        for (int i = 0; i < 24; i++) f.view.tick();
        assertHit(f, .32, -.68, "die2");
        assertEquals(count, f.entities.size());
        f.move("hold:die2");
        assertHit(f, .32, .15, "die2");
        assertHit(f, .32, -.68, "@menu");
        f.view.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    @Test void controlsHaveBoundedHitsAndIdleTicksDoNotResendDiceTransforms() {
        var f = new TableViewTest.Fixture("yacht", "roll");
        assertHit(f, .45, .82, "@roll");
        assertHit(f, -1.65, -.80, "@score:ones");
        assertHit(f, -1.65, .88, "@menu");
        assertNull(f.view.hitPiece(f.view.origin.clone().add(0, 8, 0), new Vector(0, -1, 0)));
        for (Entity entity : f.entities) clearInvocations(entity);
        for (int i = 0; i < 100; i++) f.view.tick();
        for (Entity entity : f.entities)
            if (entity instanceof org.bukkit.entity.Display display)
                verify(display, never()).setTransformation(any());
    }

    @Test void allHeldDiceDisableRollUntilOneReturnsAndScoresCannotBeReused() {
        var game = new YachtGame(2, 9);
        game.apply(0, "roll");
        for (int i = 0; i < 5; i++) game.apply(0, "hold:die" + i);
        assertFalse(game.legalActions(0).contains("roll"));
        game.apply(0, "hold:die4");
        assertTrue(game.legalActions(0).contains("roll"));
        game.apply(0, "score:yacht");
        game.apply(1, "roll");
        game.apply(1, "score:choice");
        game.apply(0, "roll");
        assertFalse(game.legalActions(0).contains("score:yacht"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "score:yacht"));
    }

    @Test void keepingOneDieDoesNotRestartTheOtherDiceInterpolation() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        f.move("roll");
        for (int i = 0; i < 24; i++) f.view.tick();
        var table = TableViewTest.field(f.view, "yachtTable");
        var dice = (java.util.List<YachtDie>) TableViewTest.field(table, "nativeDice");
        for (Entity entity : f.entities) clearInvocations(entity);
        f.move("hold:die2");
        for (int i = 0; i < dice.size(); i++) {
            var body = (org.bukkit.entity.BlockDisplay) TableViewTest.field(dice.get(i), "body");
            if (i == 2) verify(body).setTransformation(any());
            else verify(body, never()).setTransformation(any());
        }
        for (Entity entity : f.entities) clearInvocations(entity);
        f.move("hold:die2");
        for (int i = 0; i < dice.size(); i++) {
            var body = (org.bukkit.entity.BlockDisplay) TableViewTest.field(dice.get(i), "body");
            if (i == 2) verify(body).setTransformation(any());
            else verify(body, never()).setTransformation(any());
        }
        f.view.close();
    }

    @Test void seatedPlayerOpensEitherYachtTableThroughTheLobbyWithoutPickingTheGap() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        var tableWorld = mock(TableWorldController.class, CALLS_REAL_METHODS);
        var clicks = new java.util.HashMap<java.util.UUID, Long>();
        GameRulesTest.set(tableWorld, "plugin", f.plugin);
        GameRulesTest.set(tableWorld, "views", new java.util.HashMap<>(java.util.Map.of(f.room.id, f.view)));
        GameRulesTest.set(tableWorld, "clicks", clicks);
        SessionTestSupport.module(f.plugin, "tableWorld", tableWorld);
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        SessionTestSupport.module(f.plugin, "tableAccess", spy(new TableAccessController(f.plugin)));
        var playerId = java.util.UUID.randomUUID();
        when(f.player.getUniqueId()).thenReturn(playerId);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.player.isSneaking()).thenReturn(true);
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.games().session(f.player)).thenReturn(f.room);
        var entry = new TableAccessController.Entry("board:" + f.room.id,
                f.view.origin.clone().subtract(0, TableGeometry.SURFACE, 0), "yacht", false,
                GameSession.Phase.PLAYING, java.util.List.of("Player"), 2, java.util.Set.of(playerId),
                p -> fail("Seated player must not join again"), p -> f.plugin.menus().room(p, f.room));
        var lobby = f.plugin.tableAccess();
        doReturn(java.util.List.of(entry)).when(lobby).collect();
        for (double x : new double[] {-1.65, 1.1, -.765, 3}) {
            clearInvocations(f.plugin.menus());
            clicks.clear();
            when(f.player.getEyeLocation()).thenReturn(f.view.origin.clone().add(x, 2, .50)
                    .setDirection(new Vector(0, -1, 0)));
            boolean overTable = x == -1.65 || x == 1.1;
            assertEquals(overTable, f.plugin.tableAccess().request(f.player));
            var event = new org.bukkit.event.player.PlayerInteractEvent(f.player,
                    org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null,
                    org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
            event.setCancelled(false);
            tableWorld.use(event);
            assertEquals(overTable, event.isCancelled());
            verify(f.plugin.menus(), times(overTable ? 1 : 0)).room(f.player, f.room);
        }
        f.view.close();
    }

    @Test void languageReloadKeepsTheThrowLockedUntilItsFinalFrame() {
        var f = new TableViewTest.Fixture("yacht");
        try {
            f.move("roll");
            for (int i = 0; i < 5; i++) f.view.tick();
            assertTrue(Language.reload(temp, "zh_CN", warning -> fail(warning)));
            f.view.tick();
            assertTrue(f.view.rolling());
            for (int i = 0; i < 18; i++) f.view.tick();
            assertFalse(f.view.rolling());
        } finally {
            Language.reload(temp, "en_US", warning -> fail(warning));
            f.view.close();
        }
    }

    @Test void packedAndNativeLayersSwitchDuringARollWithoutChangingItsResult() {
        var f = new TableViewTest.Fixture("yacht");
        f.view.close(); f.entities.clear();
        var config = f.plugin.getConfig();
        config.set("rendering.mode", "mixed");
        config.set("rendering.resource-pack.source", "external");
        config.set("rendering.resource-pack.url", "https://example.org/tabletop.zip");
        var data = ((org.mockbukkit.mockbukkit.ServerMock)org.bukkit.Bukkit.getServer()).addPlayer().getPersistentDataContainer();
        when(f.player.getPersistentDataContainer()).thenReturn(data);
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getLocation()).thenReturn(f.view.origin);
        when(f.world.getPlayers()).thenReturn(java.util.List.of(f.player));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        SessionTestSupport.module(f.plugin, "pack", new ResourcePackService(f.plugin, () -> true, id -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER)));
        var table = new YachtTable(f.plugin, f.room, f.view.origin, new org.bukkit.NamespacedKey("3dgames", "board-cell"));
        f.room.board().apply(0, "roll"); f.room.event(0, new com.google.gson.JsonPrimitive("roll")); SessionTestSupport.bump(f.room);
        table.sync(); table.tick(); assertTrue(table.rolling());
        f.plugin.pack().toggle(f.player);
        f.plugin.pack().status(f.player, f.plugin.pack().requestId(f.player), org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        table.tick(); assertTrue(table.rolling());
        assertEquals(6, f.entities.stream().filter(org.bukkit.entity.ItemDisplay.class::isInstance).count());
        for (int i = 0; i < 24; i++) table.tick();
        assertFalse(table.rolling());
        for (int i = 0; i < 5; i++) assertEquals("die" + i, table.hit(f.view.origin.clone().add(.32 + (i-2)*.36,2,.15),new Vector(0,-1,0)).cell());
        int count = f.entities.size();
        table.cursor(f.player, "die1");
        var edges = java.util.List.copyOf(f.entities.subList(count, f.entities.size()));
        assertEquals(1, edges.size());
        assertInstanceOf(org.bukkit.entity.ItemDisplay.class, edges.getFirst());
        verify(edges.getFirst()).setGlowing(true);
        for (var edge : edges) {
            verify(edge).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, edge);
        }
        f.plugin.pack().toggle(f.player); table.tick();
        assertFalse(table.rolling());
        table.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    private static void assertHit(TableViewTest.Fixture f, double x, double z, String expected) {
        var hit = f.view.hitPiece(f.view.origin.clone().add(x, 2, z), new Vector(0, -1, 0));
        assertNotNull(hit);
        assertEquals(expected, hit.cell());
    }
}

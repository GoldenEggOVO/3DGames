package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.text.MessageText;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.render.dice.DiceMotion;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Multiplayer.game.go.GoGame;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TableViewTest {
    @org.junit.jupiter.api.io.TempDir(factory = WorkspaceTempFactory.class)
    java.nio.file.Path temp;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void close() {
        MockBukkit.unmock();
    }

    @Test void classicGamesUseTheExistingSharedTableTitleAndRemoveItOnClose() throws Exception {
        Fixture f = new Fixture("mancala");
        TextDisplay title = (TextDisplay) field(f.view, "title");
        assertNotNull(title);
        verify(title).setBillboard(Display.Billboard.CENTER);
        assertEquals(f.view.origin.getY() + 1.8, title.getLocation().getY(), 1e-6);
        var value = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(title, atLeastOnce()).text(value.capture());
        assertTrue(MessageText.plain(value.getValue()).contains("Owner"));
        f.view.close();
        verify(title).remove();
    }

    @Test void focused2048PlayerDoesNotSeeTheTitleAcrossTheBoard() throws Exception {
        Fixture f = new Fixture("2048", 1);
        var controller = mock(GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController.class);
        var observer = mock(Player.class);
        when(observer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(f.plugin.tablePlayers()).thenReturn(controller);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, observer));
        when(controller.hasTableFocus(f.player)).thenReturn(true);
        TextDisplay title = (TextDisplay) field(f.view, "title");
        f.view.tick();
        verify(f.player).hideEntity(f.plugin, title);
        verify(observer, never()).hideEntity(f.plugin, title);
        when(controller.hasTableFocus(f.player)).thenReturn(false);
        f.view.tick();
        verify(f.player).showEntity(f.plugin, title);
        f.view.close();
    }

    @Test
    void boardCursorStaysJustAboveTheSurfaceWhenMovingBetweenCells() throws Exception {
        Fixture f = new Fixture("chess");
        f.view.cursor(f.player, null, "e3");
        Map<?, ?> overlays = (Map<?, ?>) field(f.view, "overlays");
        Object overlay = overlays.get(f.player.getUniqueId());
        var edges = (List<?>) field(overlay, "hover");
        assertEquals(4, edges.size());
        for (String cell : List.of("e3", "e4")) {
            f.view.cursor(f.player, null, cell);
            for (Object edge : edges) {
                var display = (BlockDisplay) edge;
                var pose = f.transforms.get(display);
                assertEquals(-.020909375, display.getLocation().getY() - f.view.origin.getY() + pose.getTranslation().y(), 1e-6);
                assertEquals(.003, pose.getScale().y(), 1e-6);
            }
        }
        f.view.close();
    }

    @Test void selectedPieceAndLegalDestinationsUseConsistentPrivateColors() throws Exception {
        Fixture f = new Fixture("chess");
        try {
            var pick = new BoardSelection(f.room.id, f.room.revision(), "e2",
                    BoardSelection.sourceActions(f.room.board(), 0, "e2"));
            f.view.cursor(f.player, pick, null);
            Map<?, ?> overlays = (Map<?, ?>) field(f.view, "overlays");
            List<?> markers = (List<?>) field(overlays.get(f.player.getUniqueId()), "entities");
            assertTrue(markers.size() >= 5);
            for (int i = 0; i < markers.size(); i++) {
                var data = org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
                verify((BlockDisplay) markers.get(i), atLeastOnce()).setBlock(data.capture());
                assertEquals(i < 4 ? Material.CYAN_CONCRETE : Material.LIME_CONCRETE, data.getValue().getMaterial());
            }
        } finally { f.view.close(); }
    }

    @Test void stationaryChessPiecesRestOnTheBoardWithOnlyATinyGap() throws Exception {
        Fixture f = new Fixture("chess");
        Map<?, ?> tokens = (Map<?, ?>) field(f.view, "tokens");
        assertFalse(tokens.isEmpty());
        for (Object token : tokens.values()) {
            Location destination = (Location) field(token, "to");
            double y = destination.getY() - f.view.origin.getY();
            assertTrue(y >= .002 && y <= .006, "A settled piece must not float at the old 0.03 height");
        }
        f.view.close();
    }

    @Test
    void connectFourPreviewsTheLandingSlotPrivatelyAndThrottlesUnchangedHints() {
        Fixture f = new Fixture("connectfour");
        int initial = f.entities.size();
        f.view.cursor(f.player, null, "2,5");
        assertEquals(initial + 4, f.entities.size());
        var preview = List.copyOf(f.entities.subList(initial, f.entities.size()));
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Entity entity : preview) {
            verify((Display) entity).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, entity);
            assertTrue(
                    f.transforms.get(entity).getScale().z > .12f,
                    "Preview must project beyond both opaque rack faces");
            var pose = f.transforms.get(entity);
            minX = Math.min(minX, pose.getTranslation().x);
            maxX = Math.max(maxX, pose.getTranslation().x + pose.getScale().x);
            minY = Math.min(minY, pose.getTranslation().y);
            maxY = Math.max(maxY, pose.getTranslation().y + pose.getScale().y);
        }
        assertEquals(maxX - minX, maxY - minY, 1e-6, "Drop preview must be square");
        for (int i = 0; i < 20; i++) f.view.cursor(f.player, null, "2," + (i % 6));
        assertEquals(initial + 4, f.entities.size());
        verify(f.player, times(3)).sendActionBar(any(net.kyori.adventure.text.Component.class));
        assertTrue(f.room.history.isEmpty(), "A preview must never play a move");
        f.move("drop:2");
        f.move("drop:2");
        f.view.cursor(f.player, null, "2,0");
        List<Entity> next = f.entities.subList(f.entities.size() - 4, f.entities.size());
        for (Entity entity : next)
            assertEquals(
                    f.view.origin.getY() + .05 + 2 * .28 + .12, entity.getLocation().getY(), 1e-6);
        f.view.clear(f.player);
        for (Entity entity : next) verify(entity).remove();
    }

    @Test
    void fullColumnsAndWaitingRoomsNeverShowALegalDropPreview() {
        Fixture f =
                new Fixture(
                        "connectfour", "drop:2", "drop:2", "drop:2", "drop:2", "drop:2", "drop:2");
        int initial = f.entities.size();
        f.view.cursor(f.player, null, "2,4");
        assertEquals(initial, f.entities.size());
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player).sendActionBar(capture.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()).contains("full"));
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.LOBBY);
        f.view.cursor(f.player, null, "3,0");
        assertEquals(initial, f.entities.size());
        verify(f.player, atLeastOnce()).sendActionBar(capture.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()).contains("ready"));
    }

    @Test
    void hoverDescribesTheCurrentPieceAfterAPlacement() {
        Fixture f = new Fixture("gomoku");
        f.move("place:7,7");
        f.view.cursor(f.player, null, "7,7");
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player).sendActionBar(capture.capture());
        String text = GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue());
        assertTrue(text.contains("Black"), text);
        assertFalse(text.contains("Empty"), text);
    }

    @Test
    void hoveringTheDieStopsSayingRollingWhenTheAnimationFinishes() {
        Fixture f = new Fixture("ludo");
        f.move("roll");
        f.view.cursor(f.player, null, "@roll");
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player).sendActionBar(capture.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()).contains("rolling"));
        for (int i = 0; i < DiceMotion.FRAMES; i++) f.view.tick();
        f.view.cursor(f.player, null, "@roll");
        verify(f.player, atLeastOnce()).sendActionBar(capture.capture());
        assertFalse(GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()).contains("rolling"));
    }

    @Test
    void goDeadGroupMarkersReuseStonesAndRemoveOnlyTheMarkers() throws Exception {
        Fixture f = new Fixture("go9", "place:0,0", "place:8,8", "place:1,0", "pass", "pass");
        Map<?, ?> before = new HashMap<>((Map<?, ?>) field(f.view, "tokens"));
        List<Entity> stones = new ArrayList<>();
        for (Object token : before.values()) stones.addAll((List<Entity>) field(token, "parts"));
        f.move("dead:0,0");
        Map<?, ?> marked = (Map<?, ?>) field(f.view, "tokens");
        for (var entry : before.entrySet())
            assertSame(entry.getValue(), marked.get(entry.getKey()));
        for (Entity stone : stones) verify(stone, never()).remove();
        List<Entity> marks = new ArrayList<>();
        for (String id : List.of("0,0", "1,0")) {
            List<Entity> parts = (List<Entity>) field(marked.get(id), "parts");
            assertEquals(6, parts.size());
            for (Entity part : parts) if (!stones.contains(part)) marks.add(part);
        }
        assertEquals(6, marks.size());
        f.move("dead:0,0");
        for (Entity mark : marks) verify(mark).remove();
        for (Entity stone : stones) verify(stone, never()).remove();
        for (Object token : marked.values())
            assertEquals(3, ((List<?>) field(token, "parts")).size());
        f.move("dead:0,0");
        f.move("resume");
        for (Entity stone : stones) verify(stone, never()).remove();
        assertFalse(((GoldenEggOVO.TDGames.Multiplayer.game.go.GoGame) f.room.board()).scoring());
        f.view.close();
        for (Entity stone : stones) verify(stone).remove();
    }

    @Test
    void chessKnightsFaceTheOpponentAndKeepTheirHeadingAfterMoving() throws Exception {
        Fixture f = new Fixture("chess");
        Map<?, ?> tokens = (Map<?, ?>) field(f.view, "tokens");
        Object white = tokens.get("b1"), black = tokens.get("b8");
        for (Entity part : (List<Entity>) field(white, "parts"))
            assertEquals(180, part.getLocation().getYaw());
        for (Entity part : (List<Entity>) field(black, "parts"))
            assertEquals(0, part.getLocation().getYaw());
        f.move("move:b1:c3");
        for (int i = 0; i < 6; i++) f.view.tick();
        assertSame(white, ((Map<?, ?>) field(f.view, "tokens")).get("c3"));
        for (Entity part : (List<Entity>) field(white, "parts"))
            assertEquals(180, part.getLocation().getYaw());
    }

    @Test
    void idleTablesDoNotReadRuleStateOrResendDiceLabels() throws Exception {
        for (String kind : List.of("chess", "ludo")) {
            Fixture f = new Fixture(kind);
            SessionTestSupport.set(f.room, "board", spy(f.room.board()));
            f.view.sync();
            clearInvocations(f.room.board());
            for (Entity entity : f.entities) clearInvocations(entity);
            for (int i = 0; i < 100; i++) f.view.tick();
            verify(f.room.board(), never()).currentPlayer();
            verify(f.room.board(), never()).finished();
            verify(f.room.board(), never()).publicInfo();
            for (Entity entity : f.entities)
                if (entity instanceof TextDisplay label)
                    verify(label, never()).text(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void rosterKeepsHumanNamesLiteralEvenWhenTheyLookLikeBotNames() throws Exception {
        Fixture f = new Fixture("gomoku");
        String name = "<red>Ready&cBot";
        SessionTestSupport.mutableSeats(f.room).set(0, new GameSession.Seat(f.room.seats.getFirst().id(), name, false));
        SessionTestSupport.mutableSeats(f.room).set(1, new GameSession.Seat(f.room.seats.getLast().id(), "Bot99", false));
        f.view.tick();
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify((TextDisplay) field(f.view, "title"), atLeastOnce()).text(capture.capture());
        String actual = GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue());
        assertTrue(actual.contains(name), actual);
        assertTrue(actual.contains("Bot99"), actual);
    }

    @Test
    void cachedTitleStillReflectsPauseAndExplicitFinishWithoutARevisionChange() throws Exception {
        Fixture f = new Fixture("chess");
        long revision = f.room.revision();
        TextDisplay title = (TextDisplay) field(f.view, "title");
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.PAUSED);
        f.view.tick();
        verify(title, atLeastOnce()).text(capture.capture());
        assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()).contains("Paused"));
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.set(f.room, "result", "draw:50-move-rule");
        f.view.tick();
        verify(title, atLeastOnce()).text(capture.capture());
        String text = GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue());
        assertTrue(text.contains("Draw (50 moves without a capture or pawn move)"), text);
        assertEquals(revision, f.room.revision());
    }

    @Test
    void diceLabelUpdatesOnRollCompletionAndThenRemainsIdle() throws Exception {
        Fixture f = new Fixture("ludo");
        f.move("roll");
        TextDisplay label = (TextDisplay) field(field(f.view, "diceTray"), "label");
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(label, atLeastOnce()).text(capture.capture());
        assertEquals("Rolling…", GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()));
        for (int i = 0; i < DiceMotion.FRAMES; i++) f.view.tick();
        verify(label, atLeastOnce()).text(capture.capture());
        String next =
                f.room.board().publicInfo().get("pendingRoll").equals("0")
                        ? "Click to Roll"
                        : "Choose a highlighted pawn";
        assertEquals(next, GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue()));
        clearInvocations(label);
        for (int i = 0; i < 20; i++) f.view.tick();
        verify(label, never()).text(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void revisionChangesNeverReplayTheLastRollAndRestoreStartsSettled() throws Exception {
        Fixture f = new Fixture("ludo");
        f.move("roll");
        assertTrue(f.view.rolling());
        for (int i = 0; i < DiceMotion.FRAMES; i++) f.view.tick();
        assertFalse(f.view.rolling());
        Object tray = field(f.view, "diceTray");
        Object landed = ((Object[])field(tray, "poses"))[0];
        SessionTestSupport.bump(f.room);
        f.view.sync();
        assertFalse(f.view.rolling());
        assertSame(landed, ((Object[])field(tray, "poses"))[0]);
        Fixture restored = new Fixture("ludo", "roll");
        assertFalse(restored.view.rolling());
    }

    @Test
    void closingDuringAnAnimationStopsFurtherEntityUpdates() throws Exception {
        Fixture f = new Fixture("connectfour");
        f.move("drop:2");
        f.view.tick();
        f.view.close();
        f.entities.forEach(org.mockito.Mockito::clearInvocations);
        for (int i = 0; i < 20; i++) f.view.tick();
        for (Entity entity : f.entities) verify(entity, never()).teleport(any(Location.class));
    }

    @Test
    void loadingAnotherLanguageInvalidatesTheTitleWithoutAStateRevision() throws Exception {
        Fixture f = new Fixture("chess");
        long revision = f.room.revision();
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("language", "test");
        when(f.plugin.getConfig()).thenReturn(config);
        when(f.plugin.getDataFolder()).thenReturn(temp.toFile());
        when(f.plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageTest"));
        when(f.plugin.getResource("languages/en_US.yml"))
                .thenAnswer(
                        i ->
                                getClass()
                                        .getClassLoader()
                                        .getResourceAsStream("languages/en_US.yml"));
        java.nio.file.Files.createDirectories(temp.resolve("languages"));
        java.nio.file.Files.writeString(
                temp.resolve("languages/test.yml"),
                "table.title: '<gold>Custom table {number}</gold>'\n");
        try {
            Language.load(f.plugin);
            f.view.tick();
            var capture =
                    org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify((TextDisplay) field(f.view, "title"), atLeastOnce()).text(capture.capture());
            assertTrue(
                    GoldenEggOVO.TDGames.Core.text.MessageText.plain(capture.getValue())
                            .contains("Custom table 1"));
            assertEquals(revision, f.room.revision());
        } finally {
            config.set("language", "en_US");
            Language.load(f.plugin);
        }
    }

    @Test
    void tableTitleIsAboveTheCenterAndOldEdgeLabelIsAbsent() throws Exception {
        Fixture f = new Fixture("chess");
        TextDisplay title = (TextDisplay) field(f.view, "title");
        verify(title).setLineWidth(Integer.MAX_VALUE);
        verify(title).setShadowed(true);
        assertEquals(0, title.getLocation().getX(), 0.0001);
        assertEquals(0, title.getLocation().getZ(), 0.0001);
        assertEquals(
                ((Location) field(f.view, "origin")).getY() + 1.65,
                title.getLocation().getY(),
                0.0001);
        for (Entity e : f.entities)
            if (e instanceof TextDisplay text)
                verify(text, never())
                        .text(
                                net.kyori.adventure.text.Component.text(
                                        "Controls / Leave",
                                        net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    @Test
    void waitingTitleShowsCurrentRosterAndUpdatesWithoutSpawningEntities() throws Exception {
        Fixture f = new Fixture("gomoku");
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.LOBBY);
        SessionTestSupport.mutableSeats(f.room).removeLast();
        TextDisplay title = (TextDisplay) field(f.view, "title");
        int count = f.entities.size();
        f.view.tick();
        var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(title, atLeastOnce()).text(capture.capture());
        String initial =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(capture.getValue());
        assertTrue(initial.contains("Waiting for ready players"), initial);
        assertTrue(initial.contains("At table: Owner"), initial);
        assertTrue(initial.contains("Open seats: 1 / 2"), initial);
        f.room.join(UUID.randomUUID(), "Friend");
        f.view.tick();
        verify(title, atLeastOnce()).text(capture.capture());
        String full =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(capture.getValue());
        assertTrue(full.contains("Owner, Friend"));
        assertTrue(full.contains("Open seats: 0 / 2"));
        assertEquals(count, f.entities.size());
    }

    @Test
    void xiangqiRedLabelsFaceTheirOwnSideAndKeepRotationWhenMoving() throws Exception {
        Fixture f = new Fixture("xiangqi");
        Map<?, ?> tokens = (Map<?, ?>) field(f.view, "tokens");
        for (var entry : tokens.entrySet()) {
            var cell =
                    f.room.board().cells().stream()
                            .filter(c -> c.id().equals(entry.getKey()))
                            .findFirst()
                            .orElseThrow();
            for (Entity part : (List<Entity>) field(entry.getValue(), "parts"))
                if (part instanceof TextDisplay) {
                    assertEquals(cell.owner() == 0 ? 180 : 0, part.getLocation().getYaw());
                    assertEquals(-90, part.getLocation().getPitch());
                }
        }
        String action = f.room.board().legalActions(0).getFirst();
        String destination = action.split(":")[2];
        f.move(action);
        for (int i = 0; i < 6; i++) f.view.tick();
        Object moved = ((Map<?, ?>) field(f.view, "tokens")).get(destination);
        for (Entity part : (List<Entity>) field(moved, "parts"))
            if (part instanceof TextDisplay) assertEquals(180, part.getLocation().getYaw());
    }

    @Test
    void hoveringAcrossCellsReusesFourPrivateCursorEntitiesAndNeverChangesTheBoard() {
        Fixture f = new Fixture("chess");
        int publicCount = f.entities.size();
        var pick =
                new BoardSelection(
                        f.room.id,
                        f.room.revision(),
                        "e2",
                        BoardSelection.sourceActions(f.room.board(), 0, "e2"));
        f.view.cursor(f.player, pick, "e3");
        int initial = f.entities.size();
        assertEquals(10, initial - publicCount);
        for (int i = 0; i < 100; i++) f.view.cursor(f.player, pick, i % 2 == 0 ? "e4" : "e3");
        assertEquals(initial, f.entities.size());
        assertEquals(0, f.room.history.size());
        for (Entity e : f.entities.subList(publicCount, initial)) {
            verify((Display) e).setVisibleByDefault(false);
            verify(f.player).showEntity(f.plugin, e);
            verify(e, never()).remove();
        }
        f.view.clear(f.player);
        for (Entity e : f.entities.subList(publicCount, initial)) verify(e).remove();
        for (Entity e : f.entities.subList(0, publicCount)) verify(e, never()).remove();
    }

    @Test
    void legalMoveReusesTheMovingPieceAndNeverTeleportsStationaryPieces() throws Exception {
        Fixture f = new Fixture("chess");
        Map<?, ?> before = new HashMap<>((Map<?, ?>) field(f.view, "tokens"));
        Object moving = before.get("e2");
        for (Entity e : f.entities) clearInvocations(e);
        f.move("move:e2:e4");
        Map<?, ?> after = (Map<?, ?>) field(f.view, "tokens");
        assertSame(moving, after.get("e4"));
        for (int i = 0; i < 6; i++) f.view.tick();
        for (var e : before.entrySet())
            if (!e.getKey().equals("e2")) {
                assertSame(e.getValue(), after.get(e.getKey()));
                for (Entity part : (List<Entity>) field(e.getValue(), "parts"))
                    verify(part, never()).teleport(any(Location.class));
            }
        f.view.sync();
        assertEquals(after.size(), ((Map<?, ?>) field(f.view, "tokens")).size());
    }

    @Test
    void modelRayFindsTallKingBeforeTheTablePlaneAndCloseRemovesAllEntities() throws Exception {
        Fixture f = new Fixture("chess");
        Map<?, ?> tokens = (Map<?, ?>) field(f.view, "tokens");
        Object king = tokens.get("e1");
        Location at = (Location) field(king, "to");
        double h = (double) field(king, "height");
        Location target = at.clone().add(0, h * .85, 0), eye = target.clone().add(0, .35, -1.8);
        var hit = f.view.hitPiece(eye, target.toVector().subtract(eye.toVector()).normalize());
        assertNotNull(hit);
        assertEquals("e1", hit.cell());
        f.view.close();
        for (Entity e : f.entities) verify(e).remove();
    }

    @Test
    void connectFourHasVerticalPiecesAndRayRejectsMisses() throws Exception {
        Fixture f = new Fixture("connectfour");
        f.move("drop:3");
        f.move("drop:3");
        Map<?, ?> tokens = (Map<?, ?>) field(f.view, "tokens");
        Location low = (Location) field(tokens.get("3,0"), "to"),
                high = (Location) field(tokens.get("3,1"), "to");
        assertEquals(.28, high.getY() - low.getY(), 1e-6);
        assertEquals(low.getZ(), high.getZ(), 1e-6);
        Location eye = f.view.origin.clone().add(0, .15, -2);
        assertEquals("3,0", f.view.verticalHit(eye, new org.bukkit.util.Vector(0, 0, 1)));
        assertNull(f.view.verticalHit(eye, new org.bukkit.util.Vector(0, 1, 0)));
        assertNull(
                f.view.verticalHit(eye.clone().add(3, 0, 0), new org.bukkit.util.Vector(0, 0, 1)));
        assertNull(
                f.view.verticalHit(eye.clone().add(0, 0, -5), new org.bukkit.util.Vector(0, 0, 1)));
    }

    @Test
    void ordinarySquareBoardsHaveNoRaisedWoodenRails() throws Exception {
        for (String kind : List.of("connectfour", "xiangqi", "gomoku", "chess", "ludo",
                "checkers", "draughts", "reversi", "go9", "go13", "go")) {
            Fixture f = new Fixture(kind);
            var furniture = (List<Entity>) field(f.view, "nativeBoardFurniture");
            for (Entity entity : furniture) {
                if (!(entity instanceof BlockDisplay display)) continue;
                var data = org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
                verify(display).setBlock(data.capture());
                var pose = f.transforms.get(display);
                boolean tableRail = pose.getTranslation().y() >= 0 && pose.getTranslation().y() < .2
                        && pose.getScale().y() < .3 && Math.max(pose.getScale().x(), pose.getScale().z()) > 2.1;
                assertFalse(tableRail && data.getValue().getMaterial() == Material.STRIPPED_DARK_OAK_WOOD,
                        "Remove raised table perimeter rails from " + kind);
            }
            f.view.close();
        }
    }

    @Test
    void connectFourSupportsStayOutsideAllPlayingCells() throws Exception {
        Fixture f = new Fixture("connectfour");
        var furniture = (List<Entity>) field(f.view, "nativeBoardFurniture");
        for (Entity entity : furniture) {
            if (!(entity instanceof org.bukkit.entity.BlockDisplay display)) continue;
            var pose = f.transforms.get(display);
            if (pose == null) continue;
            double left = pose.getTranslation().x(), bottom = pose.getTranslation().y(), front = pose.getTranslation().z();
            double right = left + pose.getScale().x(), top = bottom + pose.getScale().y(), back = front + pose.getScale().z();
            for (int column = 0; column < 7; column++) {
                double center = (column - 3) * .28;
                assertFalse(left < center + .12 && right > center - .12
                        && bottom < .28 && top > .06 && front < .10 && back > -.10,
                        "Support must not overlap the bottom-row chip interior");
            }
        }
        f.view.close();
    }

    @Test
    void connectFourFallsFromAboveItsColumnAndSettlesAtTheRuleCell() throws Exception {
        Fixture f = new Fixture("connectfour");
        f.move("drop:2");
        Object token = ((Map<?, ?>) field(f.view, "tokens")).get("2,0");
        Location destination = (Location) field(token, "to");
        List<Entity> parts = (List<Entity>) field(token, "parts");
        assertTrue(parts.size() > 1, "A round disc needs more than the old single cube");
        assertTrue(parts.getFirst().getLocation().getY() > f.view.origin.getY() + 1.7);
        for (int i = 0; i < 16; i++) {
            f.view.tick();
            Location at = parts.getFirst().getLocation();
            assertEquals(destination.getX(), at.getX(), 1e-6);
            assertEquals(destination.getZ(), at.getZ(), 1e-6);
            assertTrue(at.getY() >= destination.getY() - 1e-6);
        }
        assertEquals(destination, parts.getFirst().getLocation());
        assertEquals(1, f.room.history.size());
    }

    @Test
    void reversiFlipsExistingEntitiesAndSettlesEvenWhenInterrupted() throws Exception {
        Fixture f = new Fixture("reversi");
        Map<?, ?> before = new HashMap<>((Map<?, ?>) field(f.view, "tokens"));
        var owners = new HashMap<String, Integer>();
        f.room.board().cells().forEach(c -> owners.put(c.id(), c.owner()));
        f.move(f.room.board().legalActions(0).getFirst());
        var flipped =
                f.room.board().cells().stream()
                        .filter(c -> owners.get(c.id()) >= 0 && owners.get(c.id()) != c.owner())
                        .findFirst()
                        .orElseThrow();
        Object original = before.get(flipped.id());
        assertSame(original, ((Map<?, ?>) field(f.view, "tokens")).get(flipped.id()));
        List<Entity> parts = (List<Entity>) field(original, "parts");
        f.view.tick();
        f.view.tick();
        int next = f.room.board().currentPlayer();
        f.move(f.room.board().legalActions(next).getFirst());
        for (int i = 0; i < 16; i++) f.view.tick();
        for (Entity part : parts) {
            verify(part, never()).remove();
            verify((BlockDisplay) part, atLeastOnce()).setTransformation(any());
        }
        var finalOwners = new HashMap<String, Integer>();
        f.room.board().cells().forEach(c -> finalOwners.put(c.id(), c.owner()));
        for (var entry : ((Map<?, ?>) field(f.view, "tokens")).entrySet())
            for (Entity part : (List<Entity>) field(entry.getValue(), "parts")) {
                float y = f.poses.get(part).m11();
                assertTrue(
                        finalOwners.get(entry.getKey()) == 0 ? y > 0 : y < 0,
                        "Final face must match authoritative owner");
            }
        assertEquals(2, f.room.history.size());
        f.view.close();
        for (Entity part : parts) verify(part).remove();
    }

    @Test
    void reversiFlipStaysAboveItsRestingBottomAtEveryFrameAndBetweenFrames() throws Exception {
        Fixture f = new Fixture("reversi");
        try {
            Map<?, ?> before = new HashMap<>((Map<?, ?>) field(f.view, "tokens"));
            var owners = new HashMap<String, Integer>();
            f.room.board().cells().forEach(c -> owners.put(c.id(), c.owner()));
            f.move(f.room.board().legalActions(0).getFirst());
            var flipped = f.room.board().cells().stream()
                    .filter(c -> owners.get(c.id()) >= 0 && owners.get(c.id()) != c.owner()).findFirst().orElseThrow();
            List<Entity> parts = (List<Entity>) field(before.get(flipped.id()), "parts");
            double restingBottom = bottom(f, parts);
            for (int frame = 1; frame <= 10; frame++) {
                var previous = new HashMap<Entity, org.bukkit.util.Transformation>();
                parts.forEach(part -> previous.put(part, f.transforms.get(part)));
                f.view.tick();
                assertTrue(bottom(f, parts) >= restingBottom - 1e-6, "Flip frame " + frame + " enters the board");
                for (Entity part : parts) {
                    var a = previous.get(part); var b = f.transforms.get(part);
                    for (int step = 1; step < 20; step++) {
                        float t = step / 20f;
                        var matrix = new org.joml.Matrix4f().translation(new org.joml.Vector3f(a.getTranslation()).lerp(b.getTranslation(), t))
                                .rotate(new org.joml.Quaternionf(a.getLeftRotation()).slerp(b.getLeftRotation(), t))
                                .scale(new org.joml.Vector3f(a.getScale()).lerp(b.getScale(), t))
                                .rotate(new org.joml.Quaternionf(a.getRightRotation()).slerp(b.getRightRotation(), t));
                        double actualBottom = bottom(f.positions.get(part), matrix);
                        assertTrue(actualBottom >= restingBottom - 1e-6,
                                "Interpolated flip frame " + frame + ":" + step + " enters the board by " + (restingBottom - actualBottom));
                    }
                }
            }
            assertEquals(restingBottom, bottom(f, parts), 1e-6);
        } finally { f.view.close(); }
    }

    private static double bottom(Fixture fixture, List<Entity> parts) {
        return parts.stream().mapToDouble(part -> bottom(fixture.positions.get(part), fixture.poses.get(part))).min().orElseThrow();
    }

    private static double bottom(Location location, org.joml.Matrix4f matrix) {
        double result = Double.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            var point = matrix.transformPosition(new org.joml.Vector3f((corner & 1) == 0 ? 0 : 1,
                    (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1));
            result = Math.min(result, location.getY() + point.y);
        }
        return result;
    }

    @Test
    void restoredConnectFourSpawnsAtRestAndWinKeepsRuleState() throws Exception {
        Fixture f =
                new Fixture(
                        "connectfour", "drop:0", "drop:1", "drop:0", "drop:1", "drop:0", "drop:1");
        for (Object token : ((Map<?, ?>) field(f.view, "tokens")).values())
            for (Entity part : (List<Entity>) field(token, "parts"))
                assertEquals(field(token, "to"), part.getLocation());
        f.move("drop:0");
        for (int i = 0; i < 16; i++) f.view.tick();
        assertTrue(f.room.board().finished());
        assertEquals(7, ((Map<?, ?>) field(f.view, "tokens")).size());
    }

    public static Object field(Object t, String n) throws Exception {
        var f = t.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(t);
    }

    public static final class Fixture {
        public final TDGamesPlugin plugin = SessionTestSupport.plugin();
        public final Player player = mock(Player.class);
        public final World world = mock(World.class);
        public final List<Entity> entities = new ArrayList<>();
        public final Map<Entity, Location> positions = new HashMap<>();
        public final Map<Entity, org.joml.Matrix4f> poses = new HashMap<>();
        public final Map<Entity, org.bukkit.util.Transformation> transforms = new HashMap<>();
        public final GameSession room;
        public final TableView view;

        public Fixture(String kind, String... initial) {
            this(kind,2,initial);
        }

        public Fixture(String kind,int capacity,String... initial) {
            when(plugin.resourcePackState()).thenAnswer(invocation -> plugin.pack());
            when(plugin.getConfig())
                    .thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class)))
                    .thenAnswer(
                            inv -> {
                                Entity e = mock((Class<? extends Entity>) inv.getArgument(1));
                                entities.add(e);
                                positions.put(e, ((Location) inv.getArgument(0)).clone());
                                when(e.getPersistentDataContainer())
                                        .thenReturn(mock(PersistentDataContainer.class));
                                when(e.isValid()).thenReturn(true);
                                when(e.getLocation()).thenAnswer(a -> positions.get(e).clone());
                                when(e.getWorld()).thenReturn(world);
                                if (e instanceof Display display)
                                    when(display.getTransformation()).thenAnswer(a -> transforms.get(e));
                                if (e instanceof BlockDisplay display) {
                                    var data = new java.util.concurrent.atomic.AtomicReference<org.bukkit.block.data.BlockData>();
                                    doAnswer(a -> { data.set(a.getArgument(0)); return null; }).when(display).setBlock(any());
                                    when(display.getBlock()).thenAnswer(a -> data.get());
                                }
                                if (e instanceof ItemDisplay display) {
                                    var item = new java.util.concurrent.atomic.AtomicReference<ItemStack>();
                                    doAnswer(a -> { item.set(a.getArgument(0)); return null; }).when(display).setItemStack(any());
                                    when(display.getItemStack()).thenAnswer(a -> item.get());
                                }
                                doAnswer(
                                                a -> {
                                                    positions.put(
                                                            e,
                                                            ((Location) a.getArgument(0)).clone());
                                                    return true;
                                                })
                                        .when(e)
                                        .teleport(any(Location.class));
                                doAnswer(
                                                a -> {
                                                    positions.get(e).setYaw(a.getArgument(0));
                                                    positions.get(e).setPitch(a.getArgument(1));
                                                    return null;
                                                })
                                        .when(e)
                                        .setRotation(anyFloat(), anyFloat());
                                if (e instanceof Display d)
                                    doAnswer(
                                                    a -> {
                                                        transforms.put(e, a.getArgument(0));
                                                        org.bukkit.util.Transformation pose = a.getArgument(0);
                                                        poses.put(e, new org.joml.Matrix4f().translation(pose.getTranslation())
                                                                .rotate(pose.getLeftRotation()).scale(pose.getScale()).rotate(pose.getRightRotation()));
                                                        return null;
                                                    })
                                            .when(d)
                                            .setTransformation(any());
                                if (e instanceof Display d)
                                    doAnswer(
                                                    a -> {
                                                        poses.put(
                                                                e,
                                                                new org.joml.Matrix4f(
                                                                        (org.joml.Matrix4f)
                                                                                a.getArgument(0)));
                                                        return null;
                                                    })
                                            .when(d)
                                            .setTransformationMatrix(any());
                                ((Consumer<Entity>) inv.getArgument(2)).accept(e);
                                return e;
                            });
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            room = new GameSession(UUID.randomUUID(), kind, capacity, 0, 0);
            room.join(id, "Owner");
            room.fillBots();
            SessionTestSupport.set(room, "board", GameFactory.create(kind, capacity, 0));
            SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
            for (String action : initial) {
                int seat = room.board().currentPlayer();
                room.board().apply(seat, action);
                room.event(seat, new JsonPrimitive(action));
                SessionTestSupport.bump(room);
            }
            BoardMapStore maps = mock(BoardMapStore.class);
            when(maps.get(eq(world), any()))
                    .thenReturn(Collections.nCopies(4, new ItemStack(Material.FILLED_MAP)));
            view =
                    new TableView(
                            plugin,
                            room,
                            new Location(world, 0, 80, 0),
                            new NamespacedKey("3dgames", "board-cell"),
                            maps);
        }

        public void move(String action) {
            int seat = room.board().currentPlayer();
            room.board().apply(seat, action);
            room.event(seat, new JsonPrimitive(action));
            SessionTestSupport.bump(room);
            view.sync();
        }
    }
}

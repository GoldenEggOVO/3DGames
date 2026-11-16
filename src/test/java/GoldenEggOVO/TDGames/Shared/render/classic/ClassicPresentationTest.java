package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.text.LabelLayout;
import GoldenEggOVO.TDGames.SessionTestSupport;
import com.google.gson.JsonPrimitive;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassicPresentationTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test void classicTablesHaveNoExtraStatusPlaqueEntities() {
        var f = new ClassicTableTest.Fixture(new MemoryGame(2, 1, Map.of()), "memory");
        Set<Entity> expected = new HashSet<>(ClassicPresentationTest.<List<Entity>>field(f.table, "nativeFurniture"));
        for (Object view : ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").values()) {
            for (Object visual : ClassicPresentationTest.<Map<String, ?>>field(view, "visuals").values())
                expected.addAll(ClassicPresentationTest.<List<Entity>>field(visual, "parts"));
            for (List<Entity> parts : ClassicPresentationTest.<Map<String, List<Entity>>>field(view, "controls").values())
                expected.addAll(parts);
        }
        assertEquals(expected, new HashSet<>(f.entities), "Information belongs in the existing table title or action bar");
        f.table.close();
    }

    @Test void shogiGlyphsFitInsideTheirTileAndStayJustAboveItsFace() {
        for (String kind : List.of("shogi", "mini-shogi")) {
            var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create(kind, 2, 1), kind);
            for (Object view : ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").values()) {
                for (Object visual : ClassicPresentationTest.<Map<String, ?>>field(view, "visuals").values()) {
                    ClassicLayout.Slot slot = field(visual, "slot");
                    for (Entity part : ClassicPresentationTest.<List<Entity>>field(visual, "parts")) if (part instanceof TextDisplay label) {
                        var text = ArgumentCaptor.forClass(Component.class);
                        verify(label).text(text.capture());
                        var pose = f.transforms.get(label);
                        assertTrue(LabelLayout.width(text.getValue()) * .025 * pose.getScale().x() <= slot.width() * .56,
                                "The character must not spill off the tapered tile");
                        assertTrue(.25 * pose.getScale().y() <= slot.depth() * .60);
                        assertTrue(Math.abs(pose.getTranslation().y()) > 0, "Center the flat text around its tile");
                        assertEquals(slot.height() + .0365, label.getLocation().getY() - f.origin.getY(), 1e-6);
                    }
                }
            }
            f.table.close();
        }
    }

    @Test void raisedTrayPiecesAndTheirHighlightsClearTheActualSurfaceInBothModes() {
        for (String kind : List.of("shogi", "mini-shogi", "backgammon")) for (boolean packed : new boolean[] {false, true}) {
            var game = (ClassicGame) GameFactory.create(kind, 2, 1);
            if (kind.equals("backgammon")) {
                int[] bar = field(game, "bar"); bar[0] = bar[1] = 1;
            } else {
                int[][] hands = field(game, "hands"); hands[0][1] = hands[1][1] = 1;
            }
            var f = new ClassicTableTest.Fixture(game, kind);
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            var model = GoldenEggOVO.TDGames.Core.render.model.ModelRepository.require("tabletop/" + kind.replace('-', '_') + "/table");
            var tray = model.blocks().stream().filter(part -> part.name().equals(kind.equals("backgammon") ? "center_bar" : "held_tray_1"))
                    .findFirst().orElseThrow();
            double surface = tray.bottom() + tray.h();
            for (String id : kind.equals("backgammon") ? List.of("bar0", "bar1") : List.of("h0P", "h1P")) {
                Object visual = ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get(id);
                Entity piece = ClassicPresentationTest.<List<Entity>>field(visual, "parts").getFirst();
                assertEquals(.0025, piece.getLocation().getY() - f.origin.getY()
                        + f.transforms.get(piece).getTranslation().y() - surface, 1e-6,
                        kind + " piece must sit above its raised surface");
                f.table.hover(f.owner, id);
                for (Entity edge : ClassicPresentationTest.<Map<String, List<Entity>>>field(own, "highlights").get(id))
                    assertEquals(.0025, edge.getLocation().getY() - f.origin.getY()
                            + f.transforms.get(edge).getTranslation().y() - surface, 1e-6,
                            kind + " frame must clear its raised surface");
            }
            String boardId = kind.equals("backgammon") ? "p1" : kind.equals("shogi") ? "h1" : "a1";
            f.table.hover(f.owner, boardId);
            for (Entity edge : ClassicPresentationTest.<Map<String, List<Entity>>>field(own, "highlights").get(boardId))
                assertEquals(.0115, edge.getLocation().getY() - f.origin.getY()
                        + f.transforms.get(edge).getTranslation().y(), 1e-6,
                        "A board square, including the h file, keeps its original frame height");
            f.table.close();
        }
    }

    @Test void unclaimedDotsAndBoxesLinesUseStoneInsteadOfDarkWood() {
        var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create("dots-and-boxes", 2, 1), "dots-and-boxes");
        Object view = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        for (var entry : ClassicPresentationTest.<Map<String, ?>>field(view, "visuals").entrySet()) {
            if (!(entry.getKey().startsWith("h") || entry.getKey().startsWith("v") || entry.getKey().startsWith("p"))) continue;
            for (Entity part : ClassicPresentationTest.<List<Entity>>field(entry.getValue(), "parts"))
                assertEquals(entry.getKey().startsWith("p") ? Material.POLISHED_DEEPSLATE : Material.LIGHT_GRAY_CONCRETE, f.materials.get(part));
        }
        f.table.close();
    }

    @Test void mancalaSowsOneSeedAtATimeAndFinishesAtTheConfirmedRuleState() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "mancala");
        game.apply(0, "sow:2"); f.room.event(0, new JsonPrimitive("sow:2")); SessionTestSupport.bump(f.room);
        List<?> result = game.cells();
        f.table.sync();
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        assertEquals("3", visibleCell(own, "2").piece(), "Only the first seed should have left the source well");
        for (int tick = 0; tick < 3; tick++) f.table.tick();
        assertEquals("5", visibleCell(own, "3").piece());
        assertEquals("2", visibleCell(own, "2").piece(), "The next seed should now be flying");
        for (int tick = 0; tick < 150; tick++) f.table.tick();
        for (var cell : game.cells()) assertEquals(cell, visibleCell(own, cell.id()));
        assertEquals(result, game.cells(), "Animation must not mutate the rules");
        for (Entity part : ClassicPresentationTest.<List<Entity>>field(f.table, "nativeFurniture")) verify(part, never()).teleport(any(Location.class));
        f.table.close();
        for (Entity part : f.entities) verify(part, atLeastOnce()).remove();
    }

    @Test void movingShogiPiecesLiftTogetherAndSettleAtTheirConfirmedSquare() {
        var game = (ClassicGame) GameFactory.create("shogi", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "shogi");
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        ClassicLayout before = field(own, "layout");
        String action = game.legalActions(0).stream().filter(a -> a.startsWith("move:")).findFirst().orElseThrow();
        String[] move = action.split(":");
        game.apply(0, action); f.room.event(0, new JsonPrimitive(action)); SessionTestSupport.bump(f.room); f.table.sync();
        Object target = ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get(move[2]);
        Entity part = ClassicPresentationTest.<List<Entity>>field(target, "parts").getFirst();
        var positions = ArgumentCaptor.forClass(Location.class);
        verify(part, atLeastOnce()).teleport(positions.capture());
        double partOffset = part.getLocation().getZ() - before.slot(move[2]).renderZ();
        assertEquals(before.slot(move[1]).renderZ() + partOffset, positions.getAllValues().getFirst().getZ(), 1e-6);
        for (int tick = 0; tick < 8; tick++) f.table.tick();
        positions = ArgumentCaptor.forClass(Location.class);
        verify(part, atLeastOnce()).teleport(positions.capture());
        assertTrue(positions.getAllValues().stream().anyMatch(at -> at.getY() > f.origin.getY() + .05));
        assertEquals(part.getLocation(), positions.getAllValues().getLast());
        f.table.close();
    }

    @Test void switchingPackDuringSowingShowsTheFinalStateAndRemovesAllFlights() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "mancala");
        game.apply(0, "sow:2"); f.room.event(0, new JsonPrimitive("sow:2")); SessionTestSupport.bump(f.room); f.table.sync();
        when(f.pack.packed(f.owner)).thenReturn(true);
        f.table.sync();
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        for (var cell : game.cells()) assertEquals(cell, visibleCell(own, cell.id()));
        for (int tick = 0; tick < 150; tick++) f.table.tick();
        for (var cell : game.cells()) assertEquals(cell, visibleCell(own, cell.id()));
        f.table.close();
        for (Entity part : f.entities) verify(part, atLeastOnce()).remove();
    }

    @Test void hexHoverFollowsTheSixEdgesOfItsCell() {
        var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create("hex", 2, 1), "hex");
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        ClassicLayout layout = field(own, "layout");
        String cell = layout.slots().stream().filter(slot -> slot.hex()).findFirst().orElseThrow().id();
        f.table.hover(f.owner, cell);
        assertEquals(6, ClassicPresentationTest.<Map<String, List<Entity>>>field(own, "highlights").get(cell).size());
        f.table.close();
    }

    @Test void memorySymbolsAppearImmediatelyWhileTheirBodiesTurnOver() {
        var game = new MemoryGame(2, 1, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        List<Entity> back = new ArrayList<>(ClassicPresentationTest.<List<Entity>>field(
                ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get("m0"), "parts"));
        game.apply(0, "flip:m0"); f.room.event(0, new JsonPrimitive("flip:m0")); SessionTestSupport.bump(f.room); f.table.sync();
        for (Entity part : back) verify(part, never()).remove();
        List<Entity> face = field(ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get("m0"), "parts");
        assertSame(back.getFirst(), face.getFirst());
        for (Entity part : face) verify(f.owner).showEntity(f.plugin, part);
        for (int tick = 0; tick < 10; tick++) f.table.tick();
        for (Entity part : back) {
            if (part == face.getFirst()) verify(part, never()).remove();
            else verify(part).remove();
        }
        for (Entity part : face) verify(f.owner).showEntity(f.plugin, part);
        for (Entity part : face) {
            verify(part, never()).teleport(any(Location.class));
            verify((org.bukkit.entity.Display)part, atLeast(10)).setTransformation(any());
        }
        f.table.close();
        for (Entity part : f.entities) verify(part, atLeastOnce()).remove();
    }

    @Test void numberedTilesSlideAcrossTheBoardInsteadOfOnlyPoppingUp() {
        var game = (ClassicGame) GameFactory.create("2048", 1, 1);
        var f = new ClassicTableTest.Fixture(game, "2048");
        String action = game.legalActions(0).stream().filter(a -> a.equals("slide:right")).findFirst().orElse(game.legalActions(0).getFirst());
        List<Entity> originals = List.copyOf(f.entities);
        game.apply(0, action); f.room.event(0, new JsonPrimitive(action)); SessionTestSupport.bump(f.room); f.table.sync();
        for (int frame = 0; frame < 6; frame++) f.table.tick();
        boolean slides = false;
        for (Entity part : originals) {
            var invocations = mockingDetails(part).getInvocations();
            for (var call : invocations) if (call.getMethod().getName().equals("teleport") && call.getArgument(0) instanceof Location at)
                slides |= Math.abs(at.getX() - part.getLocation().getX()) + Math.abs(at.getZ() - part.getLocation().getZ()) > .05;
        }
        assertTrue(slides, "A confirmed directional move needs a horizontal tile transition");
        f.table.close();
    }

    @Test void monopolyPawnsMoveBetweenPropertiesWithoutMovingThePropertyLabels() {
        var game = (ClassicGame) GameFactory.create("monopoly", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "monopoly");
        int start = f.entities.size();
        game.apply(0, "roll"); f.room.event(0, new JsonPrimitive("roll")); SessionTestSupport.bump(f.room); f.table.sync();
        boolean pawnMoves = false;
        for (Entity part : f.entities.subList(start, f.entities.size())) {
            for (var call : mockingDetails(part).getInvocations()) if (call.getMethod().getName().equals("teleport") && call.getArgument(0) instanceof Location at) {
                if (part instanceof TextDisplay) fail("Property labels must stay on their own property");
                pawnMoves |= Math.abs(at.getX() - part.getLocation().getX()) + Math.abs(at.getZ() - part.getLocation().getZ()) > .05;
            }
        }
        assertTrue(pawnMoves);
        f.table.close();
    }

    @Test void ordinaryCellHoverKeepsTheGameHintAndRestoresItWhenLookingAway() {
        var f = new ClassicTableTest.Fixture(new MemoryGame(2, 1, Map.of()), "memory");
        clearInvocations(f.owner);
        f.table.hover(f.owner, "m0");
        var hint = ArgumentCaptor.forClass(Component.class);
        verify(f.owner).sendActionBar(hint.capture());
        assertFalse(GoldenEggOVO.TDGames.Core.text.MessageText.plain(hint.getValue()).isBlank());
        f.table.hover(f.owner, null);
        verify(f.owner, times(2)).sendActionBar(any(Component.class));
        f.table.close();
    }

    @Test void adjacentTableStateDoesNotOverwriteUninvolvedPlayersHud() {
        var game = new MemoryGame(2, 1, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        verify(f.observer, never()).sendActionBar(any(Component.class));
        game.apply(0, "flip:m0"); SessionTestSupport.bump(f.room); f.table.sync();
        verify(f.observer, never()).sendActionBar(any(Component.class));
        f.table.close();
    }

    @Test void packedShogiUsesTheSelectedArtworkInsteadOfFloatingTextCharacters() {
        for (String kind : List.of("shogi", "mini-shogi")) {
            var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create(kind, 2, 1), kind);
            when(f.pack.packed(f.owner)).thenReturn(true); f.table.sync();
            verify(f.pack, atLeastOnce()).item("shogi_piece_pawn");
            verify(f.pack, atLeastOnce()).item("shogi_piece_king");
            verify(f.pack, atLeastOnce()).item("shogi_piece_jeweled_king");
            Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            for (Object visual : ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").values()) {
                for (Entity part : ClassicPresentationTest.<List<Entity>>field(visual, "parts"))
                    assertInstanceOf(org.bukkit.entity.ItemDisplay.class, part, "Each artwork piece should be a single packed display");
            }
            f.table.close();
        }
    }

    @Test void nativePromotedShogiPiecesHaveTheirOwnMark() throws Exception {
        var game = (ClassicGame) GameFactory.create("shogi", 2, 1);
        var board = game.getClass().getDeclaredField("board"); board.setAccessible(true);
        ((int[][])board.get(game))[2][0] = 9;
        var f = new ClassicTableTest.Fixture(game, "shogi");
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Object promoted = ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get("a3");
        var label = ClassicPresentationTest.<List<Entity>>field(promoted, "parts").stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast).findFirst().orElseThrow();
        var value = ArgumentCaptor.forClass(Component.class); verify(label).text(value.capture());
        assertEquals("\u3068", GoldenEggOVO.TDGames.Core.text.MessageText.plain(value.getValue()));
        f.table.close();
    }

    @Test void mismatchedMemoryCardsAlsoTurnBackInsteadOfBeingReplacedInstantly() {
        var game = new MemoryGame(2, 1, Map.of());
        String second = null;
        game.apply(0, "flip:m0");
        String face = game.cells().getFirst().piece();
        for (int index = 1; index < 36; index++) {
            var candidate = new MemoryGame(2, 1, Map.of());
            candidate.apply(0, "flip:m" + index);
            if (!candidate.cells().get(index).piece().equals(face)) { second = "m" + index; break; }
        }
        assertNotNull(second); game.apply(0, "flip:" + second);
        var f = new ClassicTableTest.Fixture(game, "memory");
        Object own = ClassicPresentationTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        List<Entity> front = field(ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get("m0"), "parts");
        game.apply(0, "hide"); f.room.event(0, new JsonPrimitive("hide")); SessionTestSupport.bump(f.room); f.table.sync();
        for (Entity part : front) verify(part, never()).remove();
        for (int tick = 0; tick < 9; tick++) f.table.tick();
        for (Entity part : front) {
            if (part instanceof org.bukkit.entity.ItemDisplay) verify(part).remove();
            else verify(part, never()).remove();
        }
        assertSame(front.getFirst(), ClassicPresentationTest.<List<Entity>>field(
                ClassicPresentationTest.<Map<String, ?>>field(own, "visuals").get("m0"), "parts").getFirst());
        f.table.close();
    }

    private static GoldenEggOVO.TDGames.Shared.game.Cell visibleCell(Object view, String id) {
        return field(ClassicPresentationTest.<Map<String, ?>>field(view, "visuals").get(id), "cell");
    }
}

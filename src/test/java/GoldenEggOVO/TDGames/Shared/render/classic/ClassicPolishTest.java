package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import GoldenEggOVO.TDGames.SessionTestSupport;
import com.google.gson.JsonPrimitive;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassicPolishTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(target); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test void monopolyUsesDeepPropertiesAndFourInwardFacingEdges() {
        var game = (ClassicGame) GameFactory.create("monopoly", 2, 1);
        var layout = ClassicLayout.of("monopoly", game.cells(), List.of());
        assertTrue(layout.slot("p0").width() >= .45);
        assertTrue(layout.slot("p1").depth() > layout.slot("p1").width() * 1.5);
        for (int i = 0; i < 40; i++) {
            var slot = layout.slot("p" + i);
            assertEquals("p" + i, layout.hit(slot.x(), slot.z()));
            assertEquals((i / 10) * 90, slot.yaw(), 1e-6);
        }
    }

    @Test void sowingRetainsExistingSeedsAndNeverDrawsTheFinalPitsFirst() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "mancala");
        Object own = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        var visuals = ClassicPolishTest.<Map<String, ?>>field(own, "visuals");
        List<Entity> source = field(visuals.get("2"), "parts"), target = field(visuals.get("3"), "parts");
        List<Entity> sourceSeeds = source.stream().filter(e -> f.materials.get(e) == Material.MUSHROOM_STEM).toList();
        game.apply(0, "sow:2"); f.room.event(0, new JsonPrimitive("sow:2")); SessionTestSupport.bump(f.room);
        f.table.sync();
        for (Entity seed : sourceSeeds.subList(0, 3)) verify(seed, never()).remove();
        for (Entity seed : target) verify(seed, never()).remove();
        Object after = ClassicPolishTest.<Map<String, ?>>field(own, "visuals").get("3");
        assertEquals("4", ClassicPolishTest.<Cell>field(after, "cell").piece());
        for (int tick = 0; tick < 2; tick++) f.table.tick();
        assertEquals("4", ClassicPolishTest.<Cell>field(ClassicPolishTest.<Map<String, ?>>field(own, "visuals").get("3"), "cell").piece());
        for (int tick = 0; tick < 350; tick++) f.table.tick();
        for (Cell cell : game.cells()) assertEquals(cell, ClassicPolishTest.<Cell>field(ClassicPolishTest.<Map<String, ?>>field(own, "visuals").get(cell.id()), "cell"));
        f.table.close();
    }

    @Test void dotsAndBoxesVisibleEdgesAreThinnerThanTheirHitArea() {
        var game = (ClassicGame) GameFactory.create("dots-and-boxes", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "dots-and-boxes");
        Object own = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Object visual = ClassicPolishTest.<Map<String, ?>>field(own, "visuals").get("h0,0");
        var parts = ClassicPolishTest.<List<Entity>>field(visual, "parts");
        assertEquals(1, parts.size());
        assertTrue(f.transforms.get(parts.getFirst()).getScale().z() <= .020);
        assertTrue(ClassicPolishTest.<ClassicLayout.Slot>field(visual, "slot").depth() >= .05);
        f.table.close();
    }

    @Test void dotsAndBoxesVisibleEdgesReachEveryEndpointDotWithoutEnlargingHitBounds() {
        for (boolean packed : new boolean[] {false, true}) {
            var game = (ClassicGame) GameFactory.create("dots-and-boxes", 2, 1);
            var f = new ClassicTableTest.Fixture(game, "dots-and-boxes");
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            Object own = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            ClassicLayout layout = field(own, "layout");
            Map<String, ?> visuals = field(own, "visuals");
            int checked = 0;
            for (var slot : layout.slots()) {
                if (!(slot.id().startsWith("h") || slot.id().startsWith("v"))) continue;
                boolean horizontal = slot.id().startsWith("h");
                String[] coordinates = slot.id().substring(1).split(",");
                int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]);
                var first = layout.slot("p" + x + "," + y);
                var second = layout.slot("p" + (x + (horizontal ? 1 : 0)) + "," + (y + (horizontal ? 0 : 1)));
                Entity edge = ClassicPolishTest.<List<Entity>>field(visuals.get(slot.id()), "parts").getFirst();
                var size = f.transforms.get(edge).getScale();
                double length = horizontal ? size.x() : size.z();
                double pitch = Math.hypot(second.x() - first.x(), second.z() - first.z());
                assertEquals(pitch, length + .032, 1e-6, slot.id() + " must meet both dot bodies");
                assertEquals(.016, horizontal ? size.z() : size.x(), 1e-6, "Keep the fine stroke");
                assertTrue((horizontal ? slot.width() : slot.depth()) < length, "Keep the existing narrower hit strip");
                checked++;
            }
            assertEquals(60, checked);
            f.table.close();
        }
    }

    @Test void dotsAndBoxesHighlightClearsTheVisibleStrokeInBothModes() {
        for (boolean packed : new boolean[] {false, true}) {
            var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create("dots-and-boxes", 2, 1), "dots-and-boxes");
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            Object own = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            for (String id : List.of("h0,0", "v0,0")) {
                f.table.hover(f.owner, id);
                for (Entity edge : ClassicPolishTest.<Map<String, List<Entity>>>field(own, "highlights").get(id))
                    assertEquals(.0025, edge.getLocation().getY() - f.origin.getY()
                            + f.transforms.get(edge).getTranslation().y() - .0125, 1e-6,
                            "The frame must clear the thin line's top surface");
            }
            f.table.close();
        }
    }

    @Test void simpleBoardPiecesRestJustAboveTheBoardInsteadOfAtTheOldGenericHeight() {
        for (String kind : List.of("hex", "nine-mens-morris", "shogi", "mini-shogi", "2048", "memory")) {
            var game = (ClassicGame) GameFactory.create(kind, Set.of("2048").contains(kind) ? 1 : 2, 1);
            var layout = ClassicLayout.of(kind, game.cells(), List.of());
            for (var slot : layout.slots()) assertTrue(slot.height() <= .012, kind + ":" + slot.id());
        }
    }

    @Test void keyboardAndHoleActionsDoNotCreateDirectionOrColorButtonBanks() {
        for (String kind : List.of("2048", "mastermind")) {
            var game = (ClassicGame) GameFactory.create(kind, 1, 1);
            var f = new ClassicTableTest.Fixture(game, kind);
            Object own = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            List<String> controls = field(own, "controlActions");
            assertTrue(controls.stream().noneMatch(a -> a.startsWith("slide:") || a.startsWith("slot:") || a.startsWith("color:")));
            f.table.close();
        }
    }

    @Test void mancalaCountsRemainFlatAndFaceEachPlayersOwnSeat() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "mancala");
        SessionTestSupport.mutableSeats(f.room).add(new GoldenEggOVO.TDGames.Shared.session.GameSession.Seat(f.observer.getUniqueId(), "Other", false));
        f.table.sync();
        for (var player : List.of(f.owner, f.observer)) {
            Object view = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(player.getUniqueId());
            Object pit = ClassicPolishTest.<Map<String, ?>>field(view, "visuals").get("0");
            var count = ClassicPolishTest.<org.bukkit.entity.TextDisplay>field(pit, "count");
            assertEquals(-90, count.getLocation().getPitch(), 1e-6);
            assertEquals(player == f.owner ? 1 : -1, Math.cos(Math.toRadians(count.getLocation().getYaw())), 1e-6);
        }
        f.table.close();
    }

    @Test void monopolyPawnStartsAtItsActualOldPositionWhenCrossingACorner() {
        var game = (ClassicGame) GameFactory.create("monopoly", 2, 1);
        game.apply(0, "roll");
        int[] positions = field(game, "positions"); positions[0] = 9;
        var f = new ClassicTableTest.Fixture(game, "monopoly");
        Object view = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Object before = ClassicPolishTest.<Map<String, ?>>field(view, "visuals").get("p9");
        var oldPawn = ClassicPolishTest.<Map<Integer, List<Entity>>>field(before, "pawns").get(0).getFirst().getLocation();
        positions[0] = 11;
        f.room.event(0, new JsonPrimitive("roll")); SessionTestSupport.bump(f.room); f.table.sync();
        Object after = ClassicPolishTest.<Map<String, ?>>field(view, "visuals").get("p11");
        Entity pawn = ClassicPolishTest.<Map<Integer, List<Entity>>>field(after, "pawns").get(0).getFirst();
        var locations = org.mockito.ArgumentCaptor.forClass(org.bukkit.Location.class);
        verify(pawn, atLeastOnce()).teleport(locations.capture());
        assertEquals(oldPawn.getX(), locations.getAllValues().getFirst().getX(), 1e-6);
        assertEquals(oldPawn.getY(), locations.getAllValues().getFirst().getY(), 1e-6);
        assertEquals(oldPawn.getZ(), locations.getAllValues().getFirst().getZ(), 1e-6);
        f.table.close();
    }
    @Test void seedFlightEndsAtTheExactNewSeedPositionInItsDestinationPit() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 1);
        var f = new ClassicTableTest.Fixture(game, "mancala");
        game.apply(0, "sow:2"); f.room.event(0, new JsonPrimitive("sow:2")); SessionTestSupport.bump(f.room); f.table.sync();
        Object view = ClassicPolishTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Object flight = ClassicPolishTest.<List<?>>field(view, "motions").getFirst();
        var target = ClassicPolishTest.<Map<Entity, org.bukkit.Location>>field(flight, "targets").values().iterator().next();
        var slot = ClassicPolishTest.<ClassicLayout>field(view, "layout").slot("3");
        assertEquals(slot.x() - .035, target.getX(), 1e-6);
        assertEquals(slot.z() + .0325, target.getZ(), 1e-6);
        f.table.close();
    }

}

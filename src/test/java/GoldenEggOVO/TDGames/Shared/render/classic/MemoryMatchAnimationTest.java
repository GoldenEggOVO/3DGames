package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame;
import GoldenEggOVO.TDGames.SessionTestSupport;
import com.google.gson.JsonPrimitive;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryMatchAnimationTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void symbolAndCardFaceTurnTogetherUsingSmoothDisplayPoses(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            Object previousView = ((Map<?, ?>)field(f.table, "views")).get(f.owner.getUniqueId());
            var originalBody = MemoryMatchAnimationTest.<List<Entity>>field(
                    ((Map<?, ?>)field(previousView, "visuals")).get("m0"), "parts").getFirst();
            apply(f, game, "flip:m0");
            Object view = ((Map<?, ?>)field(f.table, "views")).get(f.owner.getUniqueId());
            Object turn = MemoryMatchAnimationTest.<List<?>>field(view, "turns").getFirst();
            Map<Entity, ?> faces = field(turn, "faces");
            var body = (Display)faces.keySet().iterator().next();
            assertSame(originalBody, body, "One physical card body survives the whole turn");
            var symbol = (ItemDisplay)faces.keySet().stream().filter(ItemDisplay.class::isInstance).findFirst().orElseThrow();
            clearInvocations(body, symbol);
            for (int frame = 1; frame <= 9; frame++) {
                f.table.tick();
                var bodyPose = f.transforms.get(body);
                var symbolPose = f.transforms.get(symbol);
                var center = new Matrix4f().translate(bodyPose.getTranslation()).rotate(bodyPose.getLeftRotation())
                        .scale(bodyPose.getScale()).rotate(bodyPose.getRightRotation())
                        .transformPosition(new Vector3f(.5f)).add(body.getLocation().toVector().toVector3f());
                var picture = new Vector3f(symbolPose.getTranslation()).add(symbol.getLocation().toVector().toVector3f());
                var normal = bodyPose.getLeftRotation().transform(new Vector3f(0, 1, 0));
                var pictureNormal = symbolPose.getLeftRotation().transform(new Vector3f(0, 0, 1));
                assertEquals(.014, center.distance(picture), 2e-5, "Picture remains attached to its card");
                assertEquals(1, Math.abs(normal.dot(pictureNormal)), 1e-5, "Picture remains parallel to the card surface");
                assertEquals(1, picture.sub(center).normalize().dot(pictureNormal), 1e-4);
                if (frame == 4) assertTrue(Math.abs(normal.y) < .5, "The picture turns through an upright pose");
                if (frame == 9) assertTrue(pictureNormal.y > .999, "The picture ends face up");
            }
            verify(body, times(9)).setTransformation(any(Transformation.class));
            verify(symbol, times(9)).setTransformation(any(Transformation.class));
            verify(body, never()).teleport(any(Location.class));
            verify(symbol, never()).teleport(any(Location.class));
            verify(body, never()).remove();
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void clickedSymbolAppearsBeforeTheFirstAnimationTick(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            apply(f, game, "flip:m0");
            Object view = ((Map<?, ?>)field(f.table, "views")).get(f.owner.getUniqueId());
            Object turn = MemoryMatchAnimationTest.<List<?>>field(view, "turns").getFirst();
            var symbol = MemoryMatchAnimationTest.<Map<Entity, ?>>field(turn, "faces").keySet().stream()
                    .filter(org.bukkit.entity.ItemDisplay.class::isInstance).findFirst().orElseThrow();
            verify(f.owner).showEntity(f.plugin, symbol);
            for (int frame = 0; frame < 9; frame++) f.table.tick();
            verify(f.owner, times(1)).showEntity(f.plugin, symbol);
            verify(symbol, never()).remove();
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void symbolsAreRemovedOnlyAfterTheReturnAnimationEnds(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            apply(f, game, "flip:m0");
            for (int i = 0; i < 9; i++) f.table.tick();
            apply(f, game, "flip:m1");
            for (int i = 0; i < 9; i++) f.table.tick();
            Object view = ((Map<?, ?>)field(f.table, "views")).get(f.owner.getUniqueId());
            var visual = ((Map<?, ?>)field(view, "visuals")).get("m0");
            var symbol = MemoryMatchAnimationTest.<List<Entity>>field(visual, "parts").stream()
                    .filter(org.bukkit.entity.ItemDisplay.class::isInstance).findFirst().orElseThrow();
            apply(f, game, "hide");
            for (int i = 0; i < 8; i++) {
                f.table.tick();
                verify(symbol, never()).remove();
            }
            f.table.tick();
            verify(symbol).remove();
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void quickUnmatchedFlipsContinueTogetherInsteadOfFinishingTheFirstTurn(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of()); var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            apply(f, game, "flip:m0"); f.table.tick();
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            Object first = MemoryMatchAnimationTest.<List<?>>field(view, "turns").getFirst();
            Entity back = MemoryMatchAnimationTest.<Map<Entity, ?>>field(first, "backs").keySet().iterator().next();
            apply(f, game, "flip:m1");
            assertEquals(2, MemoryMatchAnimationTest.<List<?>>field(view, "turns").size());
            verify(back, never()).remove();
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void quickMatchingFlipsRevealTheFirstCardBeforeCollectingThePair(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of()); var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            apply(f, game, "flip:m0"); f.table.tick();
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            Object previous = MemoryMatchAnimationTest.<List<?>>field(view, "turns").getFirst();
            var firstFaces = MemoryMatchAnimationTest.<Map<Entity, ?>>field(previous, "faces");
            apply(f, game, "flip:m19");
            assertEquals(1, game.scores().getFirst());
            var turns = MemoryMatchAnimationTest.<List<?>>field(view, "turns");
            assertEquals(1, turns.size());
            assertEquals(firstFaces.keySet(), Set.copyOf(MemoryMatchAnimationTest.<List<Entity>>field(turns.getFirst(), "collection")));
            for (Entity face : firstFaces.keySet()) verify(f.owner).showEntity(f.plugin, face);
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void flippingCardLiftsItsLowestEdgeAboveTheTable(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of()); var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync(); apply(f, game, "flip:m0");
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            Object turn = MemoryMatchAnimationTest.<List<?>>field(view, "turns").getFirst();
            Entity body = MemoryMatchAnimationTest.<Map<Entity, ?>>field(turn, "faces").keySet().iterator().next();
            double surface = body.getLocation().getY(); Transformation previous = f.transforms.get(body);
            for (int frame = 1; frame <= 9; frame++) {
                f.table.tick();
                Transformation current = f.transforms.get(body);
                for (int step = 0; step <= 40; step++) {
                    float t = step / 40f;
                    var matrix = new Matrix4f().translate(new Vector3f(previous.getTranslation()).lerp(current.getTranslation(), t))
                            .rotate(new Quaternionf(previous.getLeftRotation()).slerp(current.getLeftRotation(), t))
                            .scale(new Vector3f(previous.getScale()).lerp(current.getScale(), t))
                            .rotate(new Quaternionf(previous.getRightRotation()).slerp(current.getRightRotation(), t));
                    for (int corner = 0; corner < 8; corner++) {
                        var point = matrix.transformPosition(new Vector3f((corner & 1) == 0 ? 0 : 1,
                                (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1));
                        assertTrue(body.getLocation().getY() + point.y >= surface - 1e-6, "Card edge enters the board at " + frame + ":" + step);
                    }
                }
                previous = current;
            }
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void matchedSecondCardFlipsThenBothFacesAreCollectedWithoutDelayingTheRuleResult(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
            apply(f, game, "flip:m0");
            for (int frame = 0; frame < 9; frame++) f.table.tick();
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            List<Entity> first = field(((Map<?, ?>) field(view, "visuals")).get("m0"), "parts");
            apply(f, game, "flip:m19");
            assertTrue(game.cells().stream().filter(cell -> cell.id().equals("m0") || cell.id().equals("m19")).allMatch(cell -> cell.piece().isEmpty()));
            assertEquals(1, game.scores().getFirst(), "The visual hold must not delay scoring");
            List<?> turns = field(view, "turns");
            assertEquals(1, turns.size(), "A matched second card must still be revealed");
            Map<Entity, ?> faces = field(turns.getFirst(), "faces");
            assertFalse(faces.isEmpty());
            Location firstPosition = first.getFirst().getLocation().clone();
            for (int frame = 1; frame <= 14; frame++) {
                f.table.tick();
                for (Entity part : first) verify(part, never()).remove();
                for (Entity part : faces.keySet()) verify(part, never()).remove();
            }
            var destination = ArgumentCaptor.forClass(Location.class);
            verify(first.getFirst(), atLeastOnce()).teleport(destination.capture());
            assertTrue(destination.getValue().getY() > firstPosition.getY(), "Matched cards should rise before collection");
            f.table.tick();
            for (Entity part : first) verify(part).remove();
            for (Entity part : faces.keySet()) verify(part).remove();
            assertTrue(MemoryMatchAnimationTest.<List<?>>field(view, "turns").isEmpty());
        } finally { f.table.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closingDuringAMatchRemovesAllRetainedPairParts(boolean packed) throws Exception {
        var game = new MemoryGame(2, 31, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
        apply(f, game, "flip:m0");
        for (int frame = 0; frame < 9; frame++) f.table.tick();
        apply(f, game, "flip:m19");
        Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
        List<?> turns = field(view, "turns"); assertEquals(1, turns.size());
        Map<Entity, ?> faces = field(turns.getFirst(), "faces");
        List<Entity> collected = field(turns.getFirst(), "collection");
        f.table.tick(); f.table.close();
        for (Entity part : faces.keySet()) verify(part).remove();
        for (Entity part : collected) verify(part).remove();
    }

    private static void apply(ClassicTableTest.Fixture f, MemoryGame game, String action) {
        int seat = game.currentPlayer(); game.apply(seat, action);
        f.room.event(seat, new JsonPrimitive(action)); SessionTestSupport.bump(f.room); f.table.sync();
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object);
    }
}

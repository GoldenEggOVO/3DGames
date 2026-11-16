package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.backgammon.BackgammonGame;
import GoldenEggOVO.TDGames.SessionTestSupport;
import com.google.gson.JsonPrimitive;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassicBackgammonMotionTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static List<Entity> blocks(Object visual) {
        return ClassicBackgammonMotionTest.<List<Entity>>field(visual, "parts").stream()
                .filter(BlockDisplay.class::isInstance).toList();
    }

    private static Vector center(ClassicTableTest.Fixture fixture, Entity entity, Location at) {
        var pose = fixture.transforms.get(entity);
        return at.toVector().add(new Vector(pose.getTranslation().x() + pose.getScale().x() / 2,
                pose.getTranslation().y() + pose.getScale().y() / 2,
                pose.getTranslation().z() + pose.getScale().z() / 2));
    }

    private static void sameCenter(Vector expected, Vector actual, String description) {
        assertEquals(expected.getX(), actual.getX(), 1e-6, description + " x");
        assertEquals(expected.getY(), actual.getY(), 1e-6, description + " y");
        assertEquals(expected.getZ(), actual.getZ(), 1e-6, description + " z");
    }

    @Test void backgammonFlightStartsAtTheRemovedSourceDiscAndLandsAtItsNewStackPosition() {
        for (boolean packed : new boolean[] {false, true}) {
            for (String action : List.of("move:p1:p5:4", "move:p19:p23:4")) {
                var game = new BackgammonGame(2, 1, Map.of());
                assertTrue(game.legalActions(game.currentPlayer()).contains(action));
                var fixture = new ClassicTableTest.Fixture(game, "backgammon");
                try {
                    when(fixture.pack.packed(fixture.owner)).thenReturn(packed);
                    fixture.table.sync();
                    Object view = ClassicBackgammonMotionTest.<Map<UUID, ?>>field(fixture.table, "views")
                            .get(fixture.owner.getUniqueId());
                    String[] move = action.split(":");
                    Map<String, ?> before = field(view, "visuals");
                    List<Entity> oldSource = blocks(before.get(move[1]));
                    int oldCount = oldSource.size() / 3;
                    List<Vector> sourceDisc = oldSource.subList(oldSource.size() - 3, oldSource.size()).stream()
                            .map(part -> center(fixture, part, part.getLocation())).toList();

                    int actor = game.currentPlayer();
                    game.apply(actor, action);
                    fixture.room.event(actor, new JsonPrimitive(action));
                    SessionTestSupport.bump(fixture.room);
                    fixture.table.sync();

                    Map<String, ?> after = field(view, "visuals");
                    List<Entity> target = blocks(after.get(move[2]));
                    List<Entity> movingDisc = target.subList(target.size() - 3, target.size());
                    List<Vector> finalDisc = movingDisc.stream()
                            .map(part -> center(fixture, part, part.getLocation())).toList();
                    for (int part = 0; part < 3; part++) {
                        var frames = ArgumentCaptor.forClass(Location.class);
                        verify(movingDisc.get(part), atLeastOnce()).teleport(frames.capture());
                        sameCenter(sourceDisc.get(part), center(fixture, movingDisc.get(part), frames.getAllValues().getFirst()),
                                action + " first frame part " + part + " packed=" + packed);
                    }

                    for (int tick = 0; tick < 6; tick++) fixture.table.tick();
                    for (int part = 0; part < 3; part++) {
                        var frames = ArgumentCaptor.forClass(Location.class);
                        verify(movingDisc.get(part), atLeastOnce()).teleport(frames.capture());
                        sameCenter(finalDisc.get(part), center(fixture, movingDisc.get(part), frames.getAllValues().getLast()),
                                action + " landed part " + part + " packed=" + packed);
                        verify(movingDisc.get(part), never()).remove();
                    }
                    assertEquals((oldCount - 1) * 3, blocks(after.get(move[1])).size(), "Keep all remaining source discs");
                    for (Entity remaining : blocks(after.get(move[1]))) verify(remaining, never()).teleport(any(Location.class));
                    assertEquals(3, target.size(), "The empty destination receives exactly one three-part disc");
                    assertTrue(ClassicBackgammonMotionTest.<List<?>>field(view, "motions").isEmpty());
                } finally { fixture.table.close(); }
            }
        }
    }
}

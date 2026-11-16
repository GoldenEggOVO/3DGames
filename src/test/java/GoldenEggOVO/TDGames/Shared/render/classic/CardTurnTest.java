package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CardTurnTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void onePhysicalBodySurvivesTheEntireTurnWithoutAMidpointSwap() {
        var f = new Fixture(0, 0);
        assertSame(f.backBody, f.after.getFirst());
        verify(f.faceBody).remove();
        for (int frame = 1; frame <= 9; frame++) {
            assertEquals(frame == 9, f.turn.tick());
            verify(f.backBody, never()).remove();
            verify(f.backBody, never()).teleport(any(Location.class));
        }
        verify(f.player, never()).showEntity(f.plugin, f.faceBody);
        verify(f.backSkin).remove();
    }

    @Test void rotationAcceleratesAndSettlesWithoutPausingAtTheMidpoint() {
        var f = new Fixture(0, 0);
        float[] angles = new float[10];
        angles[0] = f.angle();
        for (int frame = 1; frame <= 9; frame++) { f.turn.tick(); angles[frame] = f.angle(); }
        assertTrue(Math.abs(angles[1] - angles[0]) < Math.abs(angles[2] - angles[1]));
        assertTrue(Math.abs(angles[9] - angles[8]) < Math.abs(angles[8] - angles[7]));
        for (int frame = 1; frame <= 9; frame++) assertTrue(angles[frame] > angles[frame-1], "No midpoint stall");
    }

    @Test void reusedBodyStartsBothOpeningAndClosingWithoutAnInitializationJump() {
        var f = new Fixture(0, 0);
        assertSame(f.originals.get(f.backBody), f.poses.get(f.backBody));
        verify(f.backBody, never()).setTransformation(any());
        for (int tick = 0; tick < 9; tick++) f.turn.tick();
        var openPose = f.poses.get(f.backBody);
        var nextBody = f.part(BlockDisplay.class, 0, new Vector3f(-.15f, 0, -.20f),
                new Vector3f(.30f, .012f, .40f), new Quaternionf());
        var nextBack = f.part(BlockDisplay.class, .012, new Vector3f(-.13f, 0, -.176f),
                new Vector3f(.26f, .002f, .352f), new Quaternionf());
        clearInvocations(f.backBody);
        var closing = new CardTurn(f.plugin, f.player, f.after, new ArrayList<>(List.of(nextBody, nextBack)), .20);
        assertSame(openPose, f.poses.get(f.backBody));
        verify(f.backBody, never()).setTransformation(any());
        closing.tick();
        float firstStep = 2 * (float)Math.acos(Math.min(1, Math.abs(openPose.getLeftRotation()
                .dot(f.poses.get(f.backBody).getLeftRotation()))));
        assertTrue(firstStep > 0 && firstStep < Math.toRadians(7), "First closing step stays continuous");
        for (int tick = 1; tick <= 9; tick++) {
            if (tick > 1) closing.tick();
            var center = f.point(f.backBody, new Vector3f(.5f));
            var skin = f.point(nextBack, new Vector3f(.5f));
            assertEquals(.007, center.distance(skin), 2e-5, "Closing back stays attached after an earlier half-turn");
            if (tick < 9) {
                var picture = f.point(f.faceArt, new Vector3f());
                assertEquals(.014, center.distance(picture), 2e-5, "Old picture rotates until the closing ends");
                verify(f.faceArt, never()).remove();
            }
        }
        verify(f.faceArt).remove();
        assertEquals(1, f.normal().y, 1e-6, "Two consecutive half-turns finish without a reset");
    }

    @ParameterizedTest
    @CsvSource({"0,1", "90,1", "180,1", "-90,1", "45,1", "0,3", "90,3", "180,3", "-90,3", "45,3"})
    void backSkinStaysAttachedToTheOppositeSideOfTheSameCard(float yaw, int ticks) {
        var f = new Fixture(yaw, 0);
        for (int tick = 0; tick < ticks; tick++) assertFalse(f.turn.tick());
        Vector3f center = f.point(f.backBody, new Vector3f(.5f));
        Vector3f skin = f.point(f.backSkin, new Vector3f(.5f));
        assertEquals(.007, center.distance(skin), 2e-5);
        assertEquals(1, skin.sub(center).normalize().dot(f.normal()), 2e-4);
        verify(f.backBody, never()).remove();
        verify(f.backSkin, never()).remove();
    }

    @ParameterizedTest @ValueSource(floats = {0, 90, 180, -90, 45})
    void pictureIsVisibleBeforeTheFirstTickAndFollowsTheCardSurface(float yaw) {
        var f = new Fixture(yaw, 0);
        verify(f.player).showEntity(f.plugin, f.faceArt);
        for (int frame = 1; frame <= 9; frame++) {
            f.turn.tick();
            Vector3f center = f.point(f.backBody, new Vector3f(.5f));
            Vector3f picture = f.point(f.faceArt, new Vector3f());
            assertEquals(.014, center.distance(picture), 2e-5);
            assertEquals(-1, picture.sub(center).normalize().dot(f.normal()), 2e-4);
        }
        verify(f.player, times(1)).showEntity(f.plugin, f.faceArt);
        verify(f.faceArt, never()).teleport(any(Location.class));
    }

    @Test void ninthFrameRestoresTheFacePoseAndRemovesOnlyTheOldBack() {
        var f = new Fixture(90, 0);
        for (int tick = 0; tick < 8; tick++) assertFalse(f.turn.tick());
        verify(f.backSkin, never()).remove();
        assertTrue(f.turn.tick());
        f.assertBodyFinished();
        f.assertRestored(f.faceArt);
        verify(f.backSkin).remove();
        verify(f.backBody, never()).remove();
        verify(f.faceArt, never()).remove();
        verify(f.backBody).setInterpolationDuration(1);
        verify(f.faceArt).setInterpolationDuration(1);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 3, 4, 6, 8, 9})
    void cancelRestoresTheFaceAndDiscardsTheOldBackOnlyOnce(int ticks) {
        var f = new Fixture(37, 13);
        for (int tick = 0; tick < ticks; tick++) f.turn.tick();
        f.turn.cancel(); f.turn.cancel();
        f.assertBodyFinished();
        f.assertRestored(f.faceArt);
        verify(f.backSkin).remove();
        verify(f.backBody, never()).remove();
        verify(f.player, times(1)).showEntity(f.plugin, f.faceArt);
    }

    private static final class Fixture {
        final TDGamesPlugin plugin = mock(TDGamesPlugin.class);
        final Player player = mock(Player.class);
        final Map<Entity, Location> locations = new IdentityHashMap<>();
        final Map<Display, Transformation> poses = new IdentityHashMap<>(), originals = new IdentityHashMap<>();
        final Location origin;
        final BlockDisplay backBody, backSkin, faceBody;
        final ItemDisplay faceArt;
        final List<Entity> after;
        final CardTurn turn;

        Fixture(float yaw, float pitch) {
            origin = new Location(mock(World.class), 3, 80, -7, yaw, pitch);
            backBody = part(BlockDisplay.class, 0, new Vector3f(-.15f, 0, -.20f), new Vector3f(.30f, .012f, .40f), new Quaternionf());
            backSkin = part(BlockDisplay.class, .012, new Vector3f(-.13f, 0, -.176f), new Vector3f(.26f, .002f, .352f), new Quaternionf());
            faceBody = part(BlockDisplay.class, 0, new Vector3f(-.15f, 0, -.20f), new Vector3f(.30f, .012f, .40f), new Quaternionf());
            faceArt = part(ItemDisplay.class, .02, new Vector3f(), new Vector3f(.24f), new Quaternionf().rotateX((float)-Math.PI/2));
            after = new ArrayList<>(List.of(faceBody, faceArt));
            turn = new CardTurn(plugin, player, List.of(backBody, backSkin), after, .20);
        }

        float angle() {
            var normal = poses.get(backBody).getLeftRotation().transform(new Vector3f(0, 1, 0));
            return (float)Math.acos(Math.max(-1, Math.min(1, normal.y)));
        }

        Vector3f point(Display display, Vector3f point) {
            var pose = poses.get(display);
            var at = locations.get(display);
            return new Matrix4f().translate(at.toVector().toVector3f()).rotateY((float)Math.toRadians(-at.getYaw()))
                    .rotateX((float)Math.toRadians(at.getPitch())).translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation()).transformPosition(point);
        }

        Vector3f normal() {
            var at = locations.get(backBody);
            return new Quaternionf().rotateY((float)Math.toRadians(-at.getYaw())).rotateX((float)Math.toRadians(at.getPitch()))
                    .mul(poses.get(backBody).getLeftRotation()).transform(new Vector3f(0, 1, 0));
        }

        void assertRestored(Display display) {
            assertEquals(origin.getYaw(), display.getLocation().getYaw());
            assertEquals(origin.getPitch(), display.getLocation().getPitch());
            var expected = originals.get(display); var actual = poses.get(display);
            assertTrue(expected.getTranslation().distance(actual.getTranslation()) < 1e-6);
            assertEquals(1, Math.abs(expected.getLeftRotation().dot(actual.getLeftRotation())), 1e-6);
            assertEquals(expected.getScale(), actual.getScale());
        }

        void assertBodyFinished() {
            assertEquals(-1, poses.get(backBody).getLeftRotation().transform(new Vector3f(0, 1, 0)).y, 1e-6);
            assertTrue(point(backBody, new Vector3f(.5f)).distance(new Matrix4f()
                    .translate(origin.toVector().toVector3f()).rotateY((float)Math.toRadians(-origin.getYaw()))
                    .rotateX((float)Math.toRadians(origin.getPitch())).transformPosition(new Vector3f(0, .006f, 0))) < 1e-5);
        }

        private <T extends Display> T part(Class<T> type, double height, Vector3f translation, Vector3f size, Quaternionf rotation) {
            T part = mock(type);
            locations.put(part, origin.clone().add(0, height, 0));
            var pose = new Transformation(translation, rotation, size, new Quaternionf());
            poses.put(part, pose); originals.put(part, pose);
            when(part.getLocation()).thenAnswer(call -> locations.get(part).clone());
            when(part.getTransformation()).thenAnswer(call -> poses.get(part));
            doAnswer(call -> { poses.put(part, call.getArgument(0)); return null; }).when(part).setTransformation(any());
            return part;
        }
    }
}

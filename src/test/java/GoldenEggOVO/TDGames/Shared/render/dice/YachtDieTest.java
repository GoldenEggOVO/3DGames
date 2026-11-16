package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YachtDieTest {
    private static final Vector3f[] NORMALS = {
        new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1),
        new Vector3f(0, 0, -1), new Vector3f(-1, 0, 0), new Vector3f(0, -1, 0)
    };

    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void privateOutlineMatchesTheModelWithoutChangingTheSharedDie(boolean packed) {
        var f = new TableViewTest.Fixture("chess");
        f.view.close(); f.entities.clear();
        SessionTestSupport.module(f.plugin, "pack", new ResourcePackService(f.plugin, () -> true,
                id -> new ItemStack(Material.PAPER)));
        var origin = new Location(f.world, 0, 80, 0);
        var die = new YachtDie(f.plugin, f.room, origin, new NamespacedKey("3dgames", "board-cell"),
                new DisplayAudience(f.plugin, origin), packed, .18, "die0");
        var pose = new DiceMotion.Pose(.32, .10, .15, new Quaternionf().rotateXYZ(.2f, .7f, -.3f));
        die.pose(pose);
        var source = (Display)f.entities.getFirst();
        var original = f.transforms.get(source);
        var originalScale = new Vector3f(original.getScale());
        int count = f.entities.size();
        var highlight = die.highlight(f.plugin, f.player);
        assertEquals(count + 1, f.entities.size());
        assertEquals(originalScale, original.getScale(), "Highlight must not modify the shared model pose");
        var selected = f.transforms.get(highlight);
        assertCenter(pose, packed ? selected.getTranslation() : matrix(selected).transformPosition(new Vector3f(.5f)));
        assertTrue(selected.getScale().x > originalScale.x);
        assertTrue((selected.getScale().x - originalScale.x) / 2 < .004, "Native pips must remain exposed");
        assertEquals(original.getLeftRotation(), selected.getLeftRotation());
        verify(source, never()).setGlowing(anyBoolean());
        verify(highlight).setGlowing(true);
        verify(highlight).setVisibleByDefault(false);
        verify(f.player).showEntity(f.plugin, highlight);
        if (packed) {
            var item = assertInstanceOf(ItemDisplay.class, highlight);
            assertEquals(((ItemDisplay)source).getItemStack(), item.getItemStack());
            verify(item).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        } else assertEquals(((BlockDisplay)source).getBlock(), ((BlockDisplay)highlight).getBlock());
        highlight.remove(); die.close();
    }

    @ParameterizedTest
    @CsvSource({"0.18,tabletop/yacht/die", "0.28,tabletop/ludo/die", "0.17,tabletop/ludo/die_compact"})
    void nativePipsStayAttachedToTheirBodyBetweenEverySentPose(double size, String model) {
        var f = new TableViewTest.Fixture("chess");
        f.view.close();
        f.entities.clear();
        var origin = new Location(f.world, 0, 80, 0);
        var audience = new DisplayAudience(f.plugin, origin);
        var die = new YachtDie(f.plugin, f.room, origin, new NamespacedKey("3dgames", "board-cell"),
                audience, false, size, "test-die");
        var parts = f.entities.stream().map(BlockDisplay.class::cast).toList();
        var blocks = ModelRepository.require(model).blocks();
        assertEquals(blocks.size(), parts.size());
        int[] durations = parts.stream().mapToInt(YachtDieTest::duration).toArray();
        for (long seed : new long[]{0, 38, 91}) for (int start : new int[]{1, 4}) for (int face : new int[]{2, 6}) {
            var motion = new DiceMotion(size, size == .17 ? .21 : .61, DiceMotion.rest(size, start), face, seed);
            die.pose(motion.pose(0));
            var previous = snapshot(f, parts);
            for (int frame = 1; frame <= DiceMotion.FRAMES * 2; frame++) {
                die.pose(motion.pose(frame / 2.0));
                var current = snapshot(f, parts);
                for (float alpha : new float[]{.25f, .5f, .75f}) {
                    var bodyInverse = between(previous.getFirst(), current.getFirst(), durations[0], alpha).invert();
                    for (int index = 1; index < parts.size(); index++) {
                        var name = blocks.get(index).name();
                        var normal = NORMALS[Integer.parseInt(name.substring(5, 6)) - 1];
                        var pip = between(previous.get(index), current.get(index), durations[index], alpha);
                        double inner = Double.POSITIVE_INFINITY, outer = Double.NEGATIVE_INFINITY;
                        for (int corner = 0; corner < 8; corner++) {
                            var point = pip.transformPosition(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
                            bodyInverse.transformPosition(point).sub(.5f, .5f, .5f);
                            double distance = (point.dot(normal) - .5) * size;
                            inner = Math.min(inner, distance);
                            outer = Math.max(outer, distance);
                        }
                        String context = model + " seed=" + seed + " from=" + start + " to=" + face
                                + " frame=" + frame + " alpha=" + alpha + " " + name;
                        assertEquals(0, inner, 2e-6, "Pip inner surface must touch its body: " + context);
                        assertEquals(.004, outer, 2e-6, "Pip outer surface must remain visible: " + context);
                    }
                }
                previous = current;
            }
            var body = f.transforms.get(parts.getFirst());
            var center = matrix(body).transformPosition(new Vector3f(.5f));
            assertCenter(motion.pose(DiceMotion.FRAMES), center);
            assertEquals(1, new Vector3f(NORMALS[face - 1]).rotate(body.getLeftRotation()).y, 1e-6);
        }
        assertEquals(parts.size(), f.entities.size(), "Throws must reuse their entities");
        for (var part : parts) {
            verify(part).setTeleportDuration(2);
            verify(part, never()).teleport(any(Location.class));
            assertEquals(origin, part.getLocation(), "Transformation poses must keep a shared entity origin");
        }
        die.close();
        die.close();
        for (var part : parts) verify(part).remove();
    }

    @ParameterizedTest
    @CsvSource({"0.18", "0.28", "0.17"})
    void packedDieKeepsOneInterpolatedEntityAndItsAuthoritativeFinalPose(double size) {
        var f = new TableViewTest.Fixture("chess");
        f.view.close();
        f.entities.clear();
        SessionTestSupport.module(f.plugin, "pack", new ResourcePackService(f.plugin, () -> true,
                id -> new ItemStack(Material.PAPER)));
        var origin = new Location(f.world, 0, 80, 0);
        var die = new YachtDie(f.plugin, f.room, origin, new NamespacedKey("3dgames", "board-cell"),
                new DisplayAudience(f.plugin, origin), true, size, "test-die");
        assertEquals(1, f.entities.size(), "A packed die must stay a single rigid model");
        var display = assertInstanceOf(ItemDisplay.class, f.entities.getFirst());
        assertEquals(2, duration(display));
        verify(display).setTeleportDuration(2);
        for (int face = 1; face <= 6; face++) {
            var motion = new DiceMotion(size, .61, DiceMotion.rest(size, 4), face, 38);
            for (int frame = 0; frame <= DiceMotion.FRAMES; frame++) die.pose(motion.pose(frame));
            var finalPose = f.transforms.get(display);
            assertCenter(motion.pose(DiceMotion.FRAMES), finalPose.getTranslation());
            assertEquals(1, new Vector3f(NORMALS[face - 1]).rotate(finalPose.getLeftRotation()).y, 1e-6);
            assertEquals(new Vector3f((float) size), finalPose.getScale());
        }
        assertEquals(1, f.entities.size());
        die.close();
        die.close();
        verify(display).remove();
    }

    private static List<Transformation> snapshot(TableViewTest.Fixture f, List<? extends Entity> parts) {
        return parts.stream().map(f.transforms::get).toList();
    }

    private static int duration(Display display) {
        return mockingDetails(display).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("setInterpolationDuration"))
                .reduce((first, last) -> last).orElseThrow().getArgument(0);
    }

    private static Matrix4f between(Transformation previous, Transformation current, int duration, float alpha) {
        if (duration == 0) return matrix(current);
        return new Matrix4f().translation(new Vector3f(previous.getTranslation()).lerp(current.getTranslation(), alpha))
                .rotate(new Quaternionf(previous.getLeftRotation()).slerp(current.getLeftRotation(), alpha))
                .scale(new Vector3f(previous.getScale()).lerp(current.getScale(), alpha))
                .rotate(new Quaternionf(previous.getRightRotation()).slerp(current.getRightRotation(), alpha));
    }

    private static Matrix4f matrix(Transformation pose) {
        return new Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation());
    }

    private static void assertCenter(DiceMotion.Pose pose, Vector3f center) {
        assertEquals(pose.x(), center.x, 1e-6);
        assertEquals(pose.y(), center.y, 1e-6);
        assertEquals(pose.z(), center.z, 1e-6);
    }
}

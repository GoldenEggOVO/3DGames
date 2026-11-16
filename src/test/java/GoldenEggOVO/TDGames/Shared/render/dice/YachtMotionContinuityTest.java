package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import org.joml.Vector3f;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YachtMotionContinuityTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void midpointUpdatesOnlyNativeVisualsAndKeepsTheAuthoritativePose() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        try {
            var table = (YachtTable) TableViewTest.field(f.view, "yachtTable");
            f.move("roll"); f.view.tick();
            var motions = (DiceMotion[]) TableViewTest.field(table, "throwsByDie");
            var poses = (DiceMotion.Pose[]) TableViewTest.field(table, "poses");
            var previous = poses[0];
            var dice = (java.util.List<?>) TableViewTest.field(table, "nativeDice");
            var body = (org.bukkit.entity.BlockDisplay) TableViewTest.field(dice.getFirst(), "body");
            var next = motions[0].pose(1.5);
            table.renderNativeMidpoint();
            var transform = f.transforms.get(body);
            var center = new org.joml.Matrix4f().translation(transform.getTranslation())
                    .rotate(transform.getLeftRotation()).scale(transform.getScale())
                    .rotate(transform.getRightRotation()).transformPosition(new Vector3f(.5f));
            assertEquals(-.4 + next.x() * .25, center.x, 1e-6);
            assertEquals(next.y(), center.y, 1e-6);
            assertEquals(.15 + next.z(), center.z, 1e-6);
            assertEquals(1, Math.abs(next.rotation().dot(transform.getLeftRotation())), 1e-6);
            assertSame(previous, poses[0]);
            assertEquals(1, TableViewTest.field(table, "frame"));
            clearInvocations(body);
            f.room.pause("error.economy.review"); f.view.tick();
            clearInvocations(body); table.renderNativeMidpoint();
            verify(body, never()).setTransformation(any());
        } finally { f.view.close(); }
    }

    @Test void heldDiceStayCenteredInTheExistingModelSlotsInsteadOfUsingThrowLaneSpacing() throws Exception {
        var slots = ModelRepository.require("tabletop/yacht/dice_table").blocks().stream()
                .filter(block -> Math.abs(block.position().z() + .68) < 1e-5 && Math.abs(block.size().x() - .235) < 1e-5)
                .sorted(java.util.Comparator.comparingDouble(block -> block.position().x())).toList();
        assertEquals(5, slots.size(), "Exercise the actual model's five keep slots");
        var f = new TableViewTest.Fixture("yacht");
        try {
            var table = (YachtTable) TableViewTest.field(f.view, "yachtTable");
            double[] lanes = java.util.Arrays.stream((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))
                    .mapToDouble(DiceMotion.Pose::x).toArray();
            f.move("roll");
            for (int frame = 0; frame < DiceMotion.FRAMES; frame++) f.view.tick();
            for (int die = 0; die < 5; die++) {
                var before = ((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))[die];
                f.move("hold:die" + die);
                var held = ((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))[die];
                assertEquals(.32 + slots.get(die).position().x(), held.x(), 1e-6, "Keep-slot center " + die);
                assertEquals(slots.get(die).position().z(), held.z(), 1e-6);
                assertEquals(1, Math.abs(before.rotation().dot(held.rotation())), 1e-6);
                f.move("hold:die" + die);
                var released = ((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))[die];
                assertEquals(lanes[die], released.x(), 1e-8, "Release must return to the wider throw lane");
            }
        } finally { f.view.close(); }
    }

    @Test void pausingOrFinishingDuringAThrowSettlesEveryDieInsteadOfLeavingItInTheAir() throws Exception {
        for (boolean finish : new boolean[] {false, true}) {
            var f = new TableViewTest.Fixture("yacht");
            try {
                var table = (YachtTable) TableViewTest.field(f.view, "yachtTable"); f.move("roll");
                DiceMotion[] motions = ((DiceMotion[]) TableViewTest.field(table, "throwsByDie")).clone();
                for (int frame = 0; frame < 4; frame++) f.view.tick();
                if (finish) f.room.finish("winner:1"); else f.room.pause("error.economy.review");
                for (int frame = 0; frame < 30; frame++) f.view.tick();
                assertFalse(table.rolling());
                DiceMotion.Pose[] actual = (DiceMotion.Pose[]) TableViewTest.field(table, "poses");
                for (int die = 0; die < 5; die++) {
                    var end = motions[die].pose(DiceMotion.FRAMES);
                    assertEquals(end.y(), actual[die].y(), 1e-8, "An interrupted die must land on the table");
                    assertEquals(1, Math.abs(end.rotation().dot(actual[die].rotation())), 1e-6);
                }
            } finally { f.view.close(); }
        }
    }

    @Test void naturallyRotatedDieCornersRemainClickableAndInsideTheFocusFrame() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        try {
            var table = (YachtTable) TableViewTest.field(f.view, "yachtTable"); f.move("roll");
            for (int frame = 0; frame < DiceMotion.FRAMES; frame++) f.view.tick();
            DiceMotion.Pose[] poses = (DiceMotion.Pose[]) TableViewTest.field(table, "poses");
            for (int die = 0; die < 5; die++) {
                table.cursor(f.player, "die" + die);
                Object focus = ((java.util.Map<?, ?>) TableViewTest.field(table, "focuses")).get(f.player.getUniqueId());
                double width = (double) TableViewTest.field(focus, "width"), depth = (double) TableViewTest.field(focus, "depth");
                for (int corner = 0; corner < 8; corner++) {
                    Vector3f point = new Vector3f((corner & 1) == 0 ? -.088f : .088f,
                            (corner & 2) == 0 ? -.088f : .088f, (corner & 4) == 0 ? -.088f : .088f).rotate(poses[die].rotation());
                    var eye = f.view.origin.clone().add(poses[die].x() + point.x, 2, poses[die].z() + point.z);
                    assertEquals("die" + die, table.hit(eye, new Vector(0, -1, 0)).cell(), "Visible rotated corners must be clickable");
                    assertTrue(Math.abs(point.x) < width / 2 && Math.abs(point.z) < depth / 2,
                            "The highlight must enclose the current rotated footprint");
                }
            }
        } finally { f.view.close(); }
    }

    @Test void finalFrameKeepsTheActualThrowLandingInsteadOfSnappingToANewYaw() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        try {
            Object table = TableViewTest.field(f.view, "yachtTable");
            double center = ((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))[0].x();
            f.move("roll");
            DiceMotion motion = ((DiceMotion[]) TableViewTest.field(table, "throwsByDie"))[0];
            for (int frame = 0; frame < DiceMotion.FRAMES; frame++) f.view.tick();
            DiceMotion.Pose actual = ((DiceMotion.Pose[]) TableViewTest.field(table, "poses"))[0];
            DiceMotion.Pose expected = motion.pose(DiceMotion.FRAMES);
            assertEquals(center + expected.x() * .25, actual.x(), 1e-8);
            assertEquals(.15 + expected.z(), actual.z(), 1e-8);
            assertEquals(1, Math.abs(actual.rotation().dot(expected.rotation())), 1e-6, "The final face must not instantly swivel");
        } finally { f.view.close(); }
    }

    @Test void tumblingDiceRemainSeparatedAcrossAllNeighborLanes() throws Exception {
        var f = new TableViewTest.Fixture("yacht");
        try {
            Object table = TableViewTest.field(f.view, "yachtTable"); f.move("roll");
            for (int frame = 1; frame <= DiceMotion.FRAMES; frame++) {
                f.view.tick();
                DiceMotion.Pose[] poses = (DiceMotion.Pose[]) TableViewTest.field(table, "poses");
                for (int die = 0; die < 4; die++)
                    assertTrue(poses[die + 1].x() - poses[die].x() >= extent(poses[die]) + extent(poses[die + 1]) - 1e-6,
                            "Neighbor dice overlap at frame " + frame + ", lane " + die);
            }
        } finally { f.view.close(); }
    }

    private static double extent(DiceMotion.Pose pose) {
        var a = new Vector3f(1, 0, 0).rotate(pose.rotation());
        var b = new Vector3f(0, 1, 0).rotate(pose.rotation());
        var c = new Vector3f(0, 0, 1).rotate(pose.rotation());
        return .09 * (Math.abs(a.x) + Math.abs(b.x) + Math.abs(c.x));
    }
}

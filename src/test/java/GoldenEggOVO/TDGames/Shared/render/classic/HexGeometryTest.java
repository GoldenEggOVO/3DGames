package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.hex.HexGame;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HexGeometryTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static Matrix4f matrix(ClassicTableTest.Fixture f, Entity entity) {
        Matrix4f local = null;
        for (var invocation : mockingDetails(entity).getInvocations())
            if (invocation.getMethod().getName().equals("setTransformationMatrix"))
                local = new Matrix4f((Matrix4f) invocation.getArgument(0));
        if (local == null) {
            Transformation pose = f.transforms.get(entity);
            assertNotNull(pose);
            local = new Matrix4f().translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation());
        }
        var at = entity.getLocation();
        return new Matrix4f().translate((float)(at.getX() - f.origin.getX()),
                (float)(at.getY() - f.origin.getY()), (float)(at.getZ() - f.origin.getZ()))
                .rotateY((float)Math.toRadians(-at.getYaw())).rotateX((float)Math.toRadians(at.getPitch())).mul(local);
    }

    private static List<Vector3f> corners(ClassicLayout.Slot slot, double scale) {
        float x = (float)slot.x(), z = (float)slot.z(), w = (float)(slot.width() * scale / 2), d = (float)(slot.depth() * scale / 2);
        return List.of(new Vector3f(x + w, 0, z), new Vector3f(x + w / 2, 0, z + d),
                new Vector3f(x - w / 2, 0, z + d), new Vector3f(x - w, 0, z),
                new Vector3f(x - w / 2, 0, z - d), new Vector3f(x + w / 2, 0, z - d));
    }

    @Test void highlightEdgesMeetAtAllSixTileCornersInBothViews() throws Exception {
        var game = new HexGame(2, 0, Map.of("first", "host"));
        var f = new ClassicTableTest.Fixture(game, "hex");
        for (boolean packed : new boolean[]{false, true}) {
            when(f.pack.packed(f.owner)).thenReturn(packed);
            f.table.sync();
            Object view = HexGeometryTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
            ClassicLayout layout = field(view, "layout");
            for (String id : List.of("0,0", "10,0", "0,10", "10,10", "5,5")) {
                f.table.hover(f.owner, id);
                List<Entity> edges = HexGeometryTest.<Map<String, List<Entity>>>field(view, "highlights").get(id);
                assertEquals(6, edges.size());
                List<Vector3f> expected = corners(layout.slot(id), 1);
                int[] connections = new int[6];
                for (Entity edge : edges) for (float end : new float[]{0, 1}) {
                    Vector3f point = matrix(f, edge).transformPosition(new Vector3f(end, .5f, .5f));
                    point.y = 0;
                    int nearest = -1;
                    for (int i = 0; i < 6; i++) if (point.distance(expected.get(i)) < 2e-6) nearest = i;
                    assertTrue(nearest >= 0, id + " packed=" + packed + " edge end misses the hex corner: " + point);
                    connections[nearest]++;
                }
                assertArrayEquals(new int[]{2, 2, 2, 2, 2, 2}, connections);
            }
        }
        f.table.close();
    }

    @Test void nativeStoneUsesThreeNonOverlappingRhombiInsideTheHexagon() throws Exception {
        var game = new HexGame(2, 0, Map.of("first", "host"));
        game.apply(0, "place:5,5");
        var f = new ClassicTableTest.Fixture(game, "hex");
        Object view = HexGeometryTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Object visual = HexGeometryTest.<Map<String, ?>>field(view, "visuals").get("5,5");
        List<Entity> parts = field(visual, "parts");
        assertEquals(3, parts.size(), "Keep the existing per-stone entity budget");
        ClassicLayout.Slot slot = field(visual, "slot");
        List<Vector3f> expected = new ArrayList<>(corners(slot, .84));
        expected.add(new Vector3f((float)slot.x(), 0, (float)slot.z()));
        double area = 0;
        List<List<Vector3f>> polygons = new ArrayList<>();
        for (Entity part : parts) {
            assertFalse(part instanceof ItemDisplay);
            Matrix4f pose = matrix(f, part);
            List<Vector3f> polygon = new ArrayList<>();
            for (float[] point : new float[][]{{0,0},{1,0},{1,1},{0,1}}) {
                Vector3f position = pose.transformPosition(new Vector3f(point[0], 0, point[1]));
                assertEquals(slot.height(), position.y, 2e-6);
                position.y = 0;
                assertTrue(expected.stream().anyMatch(corner -> corner.distance(position) < 2e-6),
                        "The stone must use the center and the six exact hex vertices: " + position);
                polygon.add(position);
            }
            double twice = 0;
            for (int i = 0; i < 4; i++) {
                var a = polygon.get(i); var b = polygon.get((i + 1) % 4);
                twice += a.x * b.z - b.x * a.z;
            }
            area += Math.abs(twice) / 2;
            polygons.add(polygon);
        }
        assertEquals(.75 * slot.width() * slot.depth() * .84 * .84, area, 2e-6);
        for (int i = 0; i < 6; i++) {
            Vector3f sample = new Vector3f(expected.get(i)).add(expected.get((i + 1) % 6)).add(expected.get(6)).div(3);
            assertEquals(1, polygons.stream().filter(polygon -> contains(polygon, sample)).count(),
                    "Each of the six center triangles must be covered exactly once");
        }
        f.table.close();
    }

    private static boolean contains(List<Vector3f> polygon, Vector3f point) {
        boolean positive = false, negative = false;
        for (int i = 0; i < polygon.size(); i++) {
            var a = polygon.get(i); var b = polygon.get((i + 1) % polygon.size());
            float cross = (b.x - a.x) * (point.z - a.z) - (b.z - a.z) * (point.x - a.x);
            positive |= cross > 1e-7; negative |= cross < -1e-7;
        }
        return !(positive && negative);
    }

    @Test void packedStonesUseOneColoredModelEachAndKeepNativeViewsIndependent() throws Exception {
        var game = new HexGame(2, 0, Map.of("first", "host"));
        game.apply(0, "place:5,5"); game.apply(1, "place:6,5");
        var f = new ClassicTableTest.Fixture(game, "hex");
        when(f.pack.packed(f.owner)).thenReturn(true); f.table.sync();
        Object view = HexGeometryTest.<Map<UUID, ?>>field(f.table, "views").get(f.owner.getUniqueId());
        Map<String, ?> visuals = field(view, "visuals");
        for (String id : List.of("5,5", "6,5")) {
            List<Entity> parts = field(visuals.get(id), "parts");
            assertEquals(1, parts.size());
            assertInstanceOf(ItemDisplay.class, parts.getFirst());
        }
        verify(f.pack).item("classic_hex_piece_red");
        verify(f.pack).item("classic_hex_piece_blue");
        f.table.close();
    }
}

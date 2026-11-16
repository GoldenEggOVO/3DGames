package GoldenEggOVO.TDGames.Shared.render.cards;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;

import GoldenEggOVO.TDGames.Shared.render.TableViewTest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RoundCardTableTest {
    @Test void raisedThirtySixSidedRimUsesOneFeltLayerAndFiftyFourDisplays() {
        var parts = RoundCardTable.parts();
        assertEquals(54, parts.size());
        assertEquals(16, parts.stream().filter(p -> p.material() == Material.GREEN_TERRACOTTA).count());
        assertEquals(36, parts.stream().filter(p -> p.material() == Material.BROWN_CONCRETE).count());
        assertEquals(2, parts.stream().filter(p -> p.material() == Material.BROWN_TERRACOTTA).count());
    }

    @Test void feltAndRimCoverEveryPointInsideThePolygonWithoutExposingStripEnds() {
        var parts = RoundCardTable.parts();
        double apothem = 1.5 * Math.cos(Math.PI / 36);
        for (double x = -1.5; x <= 1.5; x += .015)
            for (double z = -1.5; z <= 1.5; z += .015) {
                boolean inside = true;
                for (int side = 0; side < 36; side++) {
                    double angle = Math.toRadians(side * 10);
                    if (-x * Math.sin(angle) + z * Math.cos(angle) > apothem) inside = false;
                }
                if (inside) {
                    boolean covered = false;
                    for (var part : parts) if (part.bottom() > -.2 && contains(part, x, z)) covered = true;
                    assertTrue(covered, "Open tabletop at " + x + ", " + z);
                }
            }
        var felt = parts.stream().filter(p -> p.material() == Material.GREEN_TERRACOTTA).toList();
        var rim = parts.stream().filter(p -> p.material() == Material.BROWN_CONCRETE).toList();
        for (var strip : felt)
            for (double x : new double[]{strip.x() - strip.w() / 2, strip.x() + strip.w() / 2})
                for (double z : new double[]{strip.z() - strip.d() / 2, strip.z(), strip.z() + strip.d() / 2})
                    assertTrue(rim.stream().anyMatch(p -> contains(p, x, z)), "Felt end must hide under rim");
    }

    @Test void rimJointsAvoidCoplanarFacesAndTheColumnReachesTheFeltUnderside() {
        var parts = RoundCardTable.parts();
        var rim = parts.stream().filter(p -> p.material() == Material.BROWN_CONCRETE).toList();
        double feltBottom = parts.stream().filter(p -> p.material() == Material.GREEN_TERRACOTTA).mapToDouble(ModelDefinition.Block::bottom).min().orElseThrow();
        double feltTop = feltBottom + .01;
        for (int i = 0; i < rim.size(); i++) {
            var current = rim.get(i);
            var next = rim.get((i + 1) % rim.size());
            assertTrue(current.bottom() + current.h() > feltTop);
            assertTrue(current.bottom() + current.h() <= feltTop + .0025, "Rim must be almost flush with the felt");
            assertTrue(Math.abs(current.bottom() - next.bottom()) >= .0002);
            assertTrue(Math.abs(current.bottom() + current.h() - next.bottom() - next.h()) >= .0002);
        }
        var column = parts.stream().filter(p -> p.name().equals("part_52")).findFirst().orElseThrow();
        assertEquals(Material.BROWN_TERRACOTTA, column.material());
        assertEquals(feltBottom, column.bottom() + column.h(), 1e-9);
    }

    @Test void nativeConsumersPlaceEveryRotatedRimPieceAtItsWorldCentre() throws Exception {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            for (String game : java.util.List.of("color-eight", "doudizhu", "liars-bar", "texas-holdem")) {
                var f = new TableViewTest.Fixture(game, game.equals("doudizhu") ? 3 : 2);
                Object table = TableViewTest.field(f.view, "playingTable");
                var entities = (java.util.List<org.bukkit.entity.Entity>) (table == null
                        ? TableViewTest.field(f.view, "handFurniture") : TableViewTest.field(table, "nativeFurniture"));
                var parts = RoundCardTable.parts();
                assertEquals(parts.size(), entities.size());
                for (int i = 0; i < parts.size(); i++) {
                    var part = parts.get(i);
                    var entity = entities.get(i);
                    var transform = f.transforms.get(entity);
                    var location = f.positions.get(entity);
                    var actual = new org.joml.Matrix4f()
                            .translation((float)(location.getX()-f.view.origin.getX()),
                                    (float)(location.getY()-f.view.origin.getY()),
                                    (float)(location.getZ()-f.view.origin.getZ()))
                            .rotateY((float)-Math.toRadians(location.getYaw()))
                            .translate(transform.getTranslation()).rotate(transform.getLeftRotation())
                            .scale(transform.getScale()).rotate(transform.getRightRotation());
                    var expected = GoldenEggOVO.TDGames.Core.render.model.ModelGeometry.matrix(part);
                    for (int corner = 0; corner < 8; corner++) {
                        var point = new org.joml.Vector3f(corner & 1, corner >> 1 & 1, corner >> 2 & 1);
                        assertTrue(actual.transformPosition(new org.joml.Vector3f(point))
                                .equals(expected.transformPosition(point), 1e-6f), game + " " + part.name());
                    }
                }
                f.view.close();
            }
        } finally {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
        }
    }

    public static boolean contains(ModelDefinition.Block part, double x, double z) {
        double angle = Math.toRadians(part.displayYaw()), dx = x - part.x(), dz = z - part.z();
        return Math.abs(dx * Math.cos(angle) + dz * Math.sin(angle)) <= part.w() / 2 + 1e-8
                && Math.abs(-dx * Math.sin(angle) + dz * Math.cos(angle)) <= part.d() / 2 + 1e-8;
    }
}

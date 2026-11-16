package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Shared.game.mastermind.MastermindGame;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ClassicPieceContactTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test void mastermindPegsRestInsideTheirWellsWithoutRaisingTheirFaces() {
        for (boolean packed : new boolean[] {false, true}) {
            var game = new MastermindGame(1, 7, Map.of());
            game.apply(0, "color:0");
            var fixture = new ClassicTableTest.Fixture(game, "mastermind");
            try {
                when(fixture.pack.packed(fixture.owner)).thenReturn(packed);
                fixture.table.sync();
                Object view = ClassicPieceContactTest.<Map<UUID, ?>>field(fixture.table, "views")
                        .get(fixture.owner.getUniqueId());
                Map<String, ?> visuals = field(view, "visuals");
                var parts = ClassicPieceContactTest.<List<Entity>>field(visuals.get("draft0"), "parts");
                assertEquals(packed ? 1 : 6, parts.size());
                if (!packed) {
                    double lowest = Double.POSITIVE_INFINITY, highest = Double.NEGATIVE_INFINITY;
                    for (Entity part : parts) {
                        var pose = fixture.transforms.get(part);
                        double bottom = part.getLocation().getY() - fixture.origin.getY() + pose.getTranslation().y();
                        lowest = Math.min(lowest, bottom);
                        highest = Math.max(highest, bottom + pose.getScale().y());
                    }
                    assertEquals(.0115, lowest, 1e-6, "The stem meets the well floor");
                    assertEquals(.046, highest, 1e-6, "The low cap clears the bezel");
                }
                assertEquals(2, ClassicPieceContactTest.<List<Entity>>field(visuals.get("secret0"), "parts").size());
                assertTrue(ClassicPieceContactTest.<List<Entity>>field(visuals.get("secret1"), "parts").isEmpty(),
                        "One cover spans the entire hidden answer");
            } finally { fixture.table.close(); }
        }
    }

    @Test void sixMonopolyPawnsStayClearOfThePropertyOwnershipStripOnEveryEdge() {
        for (boolean packed : new boolean[] {false, true}) {
            for (int square : new int[] {1, 11, 21, 31}) {
                var game = new MonopolyGame(6, 1, Map.of());
                Arrays.fill(ClassicPieceContactTest.<int[]>field(game, "positions"), square);
                ClassicPieceContactTest.<int[]>field(game, "owners")[square] = 0;
                var fixture = new ClassicTableTest.Fixture(game, "monopoly");
                try {
                    when(fixture.pack.packed(fixture.owner)).thenReturn(packed);
                    fixture.table.sync();
                    Object view = ClassicPieceContactTest.<Map<UUID, ?>>field(fixture.table, "views")
                            .get(fixture.owner.getUniqueId());
                    Object visual = ClassicPieceContactTest.<Map<String, ?>>field(view, "visuals").get("p" + square);
                    ClassicLayout.Slot slot = field(visual, "slot");
                    List<Entity> parts = field(visual, "parts");
                    Entity strip = parts.stream().filter(part -> part instanceof BlockDisplay
                            && Math.abs(fixture.transforms.get(part).getScale().y() - .002) < 1e-6)
                            .findFirst().orElseThrow();
                    Map<Integer, List<Entity>> pawns = field(visual, "pawns");
                    assertEquals(6, pawns.size());
                    double angle = Math.toRadians(slot.yaw());
                    double stripNear = localZ(fixture, slot, strip, angle)
                            - fixture.transforms.get(strip).getScale().z() / 2;
                    for (var pawn : pawns.values()) {
                        Entity base = pawn.getFirst();
                        double pawnFar = localZ(fixture, slot, base, angle)
                                + fixture.transforms.get(base).getScale().z() / 2;
                        assertTrue(pawnFar < stripNear - 1e-4,
                                "Keep pawn feet out of the ownership strip: " + square + "/" + packed);
                    }
                } finally { fixture.table.close(); }
            }
        }
    }

    private static double localZ(ClassicTableTest.Fixture fixture, ClassicLayout.Slot slot,
                                 Entity entity, double angle) {
        double x = entity.getLocation().getX() - fixture.origin.getX() - slot.x();
        double z = entity.getLocation().getZ() - fixture.origin.getZ() - slot.z();
        return -x * Math.sin(angle) + z * Math.cos(angle);
    }

    @Test void rectangularHighlightsHaveJoinedCornersInBothModes() {
        for (String kind : GameCatalog.CLASSIC_IDS) {
            if (kind.equals("hex")) continue;
            for (boolean packed : new boolean[] {false, true}) {
                var game = (ClassicGame) GameFactory.create(kind, GameCatalog.defaultCapacity(kind), 7);
                var fixture = new ClassicTableTest.Fixture(game, kind);
                try {
                    when(fixture.pack.packed(fixture.owner)).thenReturn(packed);
                    fixture.table.sync();
                    Object view = ClassicPieceContactTest.<Map<UUID, ?>>field(fixture.table, "views")
                            .get(fixture.owner.getUniqueId());
                    ClassicLayout layout = field(view, "layout");
                    var slots = kind.equals("monopoly")
                            ? List.of(layout.slot("p1"), layout.slot("p11"), layout.slot("p21"), layout.slot("p31"))
                            : List.of(layout.slots().stream().filter(slot -> !slot.id().startsWith("@")).findFirst().orElseThrow());
                    for (var slot : slots) {
                        fixture.table.hover(fixture.owner, slot.id());
                        List<Entity> edges = ClassicPieceContactTest.<Map<String, List<Entity>>>field(view, "highlights").get(slot.id());
                        assertEquals(4, edges.size(), kind);
                        double angle = Math.toRadians(slot.yaw());
                        var bounds = new ArrayList<double[]>();
                        for (Entity edge : edges) {
                            double x = edge.getLocation().getX() - fixture.origin.getX() - slot.x();
                            double z = edge.getLocation().getZ() - fixture.origin.getZ() - slot.z();
                            double cx = x * Math.cos(angle) + z * Math.sin(angle);
                            double cz = -x * Math.sin(angle) + z * Math.cos(angle);
                            var size = fixture.transforms.get(edge).getScale();
                            bounds.add(new double[] {cx - size.x() / 2, cz - size.z() / 2,
                                    cx + size.x() / 2, cz + size.z() / 2});
                        }
                        for (int a = 0; a < bounds.size(); a++) for (int b = a + 1; b < bounds.size(); b++) {
                            double[] first = bounds.get(a), second = bounds.get(b);
                            double overlapX = Math.min(first[2], second[2]) - Math.max(first[0], second[0]);
                            double overlapZ = Math.min(first[3], second[3]) - Math.max(first[1], second[1]);
                            assertTrue(overlapX <= 1e-7 || overlapZ <= 1e-7,
                                    kind + "/" + packed + "/" + slot.id() + ": overlapping highlight corners");
                        }
                        double thickness = Math.min(.012, slot.width() * .10);
                        assertEquals(slot.width() + thickness,
                                bounds.stream().mapToDouble(b -> b[2]).max().orElseThrow()
                                        - bounds.stream().mapToDouble(b -> b[0]).min().orElseThrow(), 1e-6, kind);
                        assertEquals(slot.depth() + thickness,
                                bounds.stream().mapToDouble(b -> b[3]).max().orElseThrow()
                                        - bounds.stream().mapToDouble(b -> b[1]).min().orElseThrow(), 1e-6, kind);
                    }
                } finally { fixture.table.close(); }
            }
        }
    }
}

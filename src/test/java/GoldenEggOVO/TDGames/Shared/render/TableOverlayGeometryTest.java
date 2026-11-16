package GoldenEggOVO.TDGames.Shared.render;

import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableOverlayGeometryTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void rectangularIndicatorsJoinAtCornersWithoutOverlappingFaces() throws Exception {
        var ring = TableView.class.getDeclaredMethod("ring", List.class, String.class,
                Material.class, Player.class, double.class, boolean.class);
        ring.setAccessible(true);
        for (String kind : List.of("chess", "xiangqi", "gomoku", "go", "reversi", "checkers")) {
            var fixture = new TableViewTest.Fixture(kind);
            try {
                String cell = fixture.view.geometry.byId.keySet().iterator().next();
                for (boolean packed : new boolean[] {false, true}) {
                    for (double fraction : new double[] {.82, .96, 1}) {
                        var edges = new ArrayList<Entity>();
                        ring.invoke(fixture.view, edges, cell, Material.YELLOW_CONCRETE,
                                fixture.player, fraction, packed);
                        assertEquals(4, edges.size());
                        var bounds = new ArrayList<double[]>();
                        for (Entity entity : edges) {
                            assertInstanceOf(BlockDisplay.class, entity);
                            var pose = fixture.transforms.get(entity);
                            var position = pose.getTranslation();
                            var scale = pose.getScale();
                            bounds.add(new double[] {position.x, position.z,
                                    position.x + scale.x, position.z + scale.z});
                        }
                        String context = kind + "/" + packed + "/" + fraction;
                        for (int a = 0; a < bounds.size(); a++) {
                            for (int b = a + 1; b < bounds.size(); b++) {
                                double[] first = bounds.get(a), second = bounds.get(b);
                                double x = Math.min(first[2], second[2]) - Math.max(first[0], second[0]);
                                double z = Math.min(first[3], second[3]) - Math.max(first[1], second[1]);
                                assertTrue(x <= 1e-7 || z <= 1e-7,
                                        context + ": indicator edges overlap at a visible corner");
                            }
                        }
                        double outer = fixture.view.geometry.spacing * (fraction + .065);
                        assertEquals(outer, bounds.stream().mapToDouble(b -> b[2]).max().orElseThrow()
                                - bounds.stream().mapToDouble(b -> b[0]).min().orElseThrow(), 1e-6, context);
                        assertEquals(outer, bounds.stream().mapToDouble(b -> b[3]).max().orElseThrow()
                                - bounds.stream().mapToDouble(b -> b[1]).min().orElseThrow(), 1e-6, context);
                    }
                }
            } finally { fixture.view.close(); }
        }
    }
}

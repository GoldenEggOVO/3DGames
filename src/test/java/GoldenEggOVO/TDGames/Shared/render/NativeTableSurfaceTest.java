package GoldenEggOVO.TDGames.Shared.render;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeTableSurfaceTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void mahjongRimCornersDoNotShareTexturedTopFaces() throws Exception {
        var f = new TableViewTest.Fixture("mahjong", 4);
        var furniture = (List<Entity>) TableViewTest.field(f.view,
                "handFurniture");
        var rim = furniture.stream().filter(e -> f.transforms.containsKey(e)
                && f.transforms.get(e).getScale().y >= .109999f
                && f.transforms.get(e).getScale().y < .112f).toList();
        assertEquals(8, rim.size(), "mahjong");
        assertEquals(4, rim.stream().filter(e -> f.transforms.get(e).getLeftRotation().angle() > .1f).count());
        for (int i = 0; i < rim.size(); i++) {
            var a = f.transforms.get(rim.get(i));
            var ap = rim.get(i).getLocation();
            for (int j = i + 1; j < rim.size(); j++) {
                var b = f.transforms.get(rim.get(j));
                var bp = rim.get(j).getLocation();
                double aTop = ap.getY() + a.getTranslation().y + a.getScale().y;
                double bTop = bp.getY() + b.getTranslation().y + b.getScale().y;
                if (Math.abs(aTop - bTop) > 1e-6) continue;
                double overlapX = Math.min(ap.getX() + a.getTranslation().x + a.getScale().x,
                        bp.getX() + b.getTranslation().x + b.getScale().x)
                        - Math.max(ap.getX() + a.getTranslation().x, bp.getX() + b.getTranslation().x);
                double overlapZ = Math.min(ap.getZ() + a.getTranslation().z + a.getScale().z,
                        bp.getZ() + b.getTranslation().z + b.getScale().z)
                        - Math.max(ap.getZ() + a.getTranslation().z, bp.getZ() + b.getTranslation().z);
                assertTrue(overlapX < 1e-6 || overlapZ < 1e-6, "Mahjong rim corners must meet without coplanar overlap");
            }
        }
        f.view.close();
    }
}

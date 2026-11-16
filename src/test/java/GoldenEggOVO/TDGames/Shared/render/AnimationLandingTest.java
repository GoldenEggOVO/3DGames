package GoldenEggOVO.TDGames.Shared.render;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimationLandingTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void chessHopSoftensItsVerticalDepartureAndLandingWithoutSpawningParts() throws Exception {
        var fixture = new TableViewTest.Fixture("chess");
        try {
            Object moving = ((Map<?, ?>) TableViewTest.field(fixture.view, "tokens")).get("e2");
            var parts = (List<Entity>) TableViewTest.field(moving, "parts");
            var position = moving.getClass().getDeclaredMethod("position");
            position.setAccessible(true);
            fixture.move("move:e2:e4");
            int entities = fixture.entities.size();
            Location target = (Location) TableViewTest.field(moving, "to");
            double[] height = new double[7];
            for (int tick = 1; tick <= 6; tick++) {
                fixture.view.tick();
                height[tick] = ((Location) position.invoke(moving)).getY() - target.getY();
            }
            assertTrue(height[1] < height[3] * .3);
            assertTrue(height[5] < height[3] * .3);
            assertEquals(0, height[6], 1e-9);
            assertEquals(entities, fixture.entities.size());
            assertSame(parts, TableViewTest.field(moving, "parts"));
        } finally { fixture.view.close(); }
    }

    @Test void deckDrawKeepsItsDelayAndTwelveTickDurationWithGentleLift() throws Exception {
        var fixture = new HandTableTest.Fixture("color-eight");
        try {
            Object piece = ((Map<?, ?>) TableViewTest.field(fixture.table, "publicPieces")).values().iterator().next();
            var draw = piece.getClass().getDeclaredMethod("drawFromDeck", int.class);
            var tick = piece.getClass().getDeclaredMethod("tick");
            draw.setAccessible(true); tick.setAccessible(true);
            draw.invoke(piece, 2);
            Vector start = ((Vector) TableViewTest.field(piece, "start")).clone();
            tick.invoke(piece); tick.invoke(piece);
            assertEquals(start, TableViewTest.field(piece, "displacement"));
            tick.invoke(piece);
            Vector first = (Vector) TableViewTest.field(piece, "displacement");
            double horizontalProgress = (first.getX() - start.getX()) / -start.getX();
            double lift = first.getY() - start.getY() * (1 - horizontalProgress);
            assertTrue(lift > 0 && lift < .014, "The first draw frame should not jerk upward");
            for (int frame = 2; frame <= 12; frame++) tick.invoke(piece);
            assertEquals(new Vector(), TableViewTest.field(piece, "displacement"));
            assertEquals(false, TableViewTest.field(piece, "drawing"));
        } finally { fixture.table.close(); }
    }
}

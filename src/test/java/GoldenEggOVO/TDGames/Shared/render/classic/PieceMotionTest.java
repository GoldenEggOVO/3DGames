package GoldenEggOVO.TDGames.Shared.render.classic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PieceMotionTest {
    @Test void temporarySeedRemainsVisibleAtItsDestinationForOneUpdateBeforeHandoff() {
        var position = new AtomicReference<>(new Location(mock(World.class), 4, 80, 6));
        Display seed = display(position);
        var destination = position.get().clone().add(1, 0, 0);
        var flight = new PieceMotion(seed, destination, .14, 2);
        assertFalse(flight.tick());
        assertFalse(flight.tick(), "The client needs an update interval to reach the sent destination");
        assertEquals(destination, position.get());
        verify(seed, never()).remove();
        assertTrue(flight.tick());
        verify(seed).remove();
    }
    @Test void hopEasesVerticallyAtBothEndsAndKeepsTheSamePeakAndDuration() {
        var position = new AtomicReference<>(new Location(mock(World.class), 4, 80, 6));
        Display part = display(position);
        var motion = new PieceMotion(List.of(part), new Vector(-1, 0, 0), .12, 6, false);
        assertEquals(3, position.get().getX(), 1e-9);
        double[] heights = new double[7];
        for (int tick = 1; tick <= 6; tick++) {
            assertEquals(tick == 6, motion.tick());
            heights[tick] = position.get().getY() - 80;
            assertTrue(position.get().getX() >= 3 && position.get().getX() <= 4);
        }
        assertTrue(heights[1] < heights[3] * .3, "Lift should start gently");
        assertTrue(heights[5] < heights[3] * .3, "Lift should settle gently");
        assertEquals(.12, heights[3], 1e-9);
        assertEquals(0, heights[6], 1e-9);
        assertEquals(4, position.get().getX(), 1e-9);
        verify(part, never()).remove();
    }

    @Test void multiSquareRouteLandsAtEveryWaypointAndMovesAllPartsRigidly() {
        World world = mock(World.class);
        var first = new AtomicReference<>(new Location(world, 4, 80, 6));
        var second = new AtomicReference<>(new Location(world, 4.02, 80.03, 6));
        Display body = display(first), trim = display(second);
        var motion = new PieceMotion(List.of(body, trim),
                List.of(new Vector(-1, 0, -1), new Vector(0, 0, -1), new Vector()), .045, 8, false);
        for (int tick = 1; tick <= 8; tick++) {
            assertEquals(tick == 8, motion.tick());
            assertEquals(.02, second.get().getX() - first.get().getX(), 1e-9);
            assertEquals(.03, second.get().getY() - first.get().getY(), 1e-9);
            if (tick == 4) assertEquals(new Vector(4, 80, 5), first.get().toVector());
        }
        assertEquals(new Vector(4, 80, 6), first.get().toVector());
    }

    @Test void cancellingLandsPersistentPartsAndRemovesTemporarySeeds() {
        var position = new AtomicReference<>(new Location(mock(World.class), 4, 80, 6));
        Display piece = display(position);
        var motion = new PieceMotion(List.of(piece), new Vector(-1, 0, 0), .12, 6, false);
        motion.tick();
        motion.cancel();
        assertEquals(new Vector(4, 80, 6), position.get().toVector());
        verify(piece, never()).remove();
        var seed = display(new AtomicReference<>(position.get().clone()));
        var flight = new PieceMotion(seed, position.get().clone().add(1, 0, 0), .14, 6);
        flight.tick();
        flight.cancel();
        verify(seed).remove();
    }

    private static Display display(AtomicReference<Location> position) {
        Display display = mock(Display.class);
        when(display.getLocation()).thenAnswer(call -> position.get().clone());
        doAnswer(call -> { position.set(((Location) call.getArgument(0)).clone()); return true; })
                .when(display).teleport(any(Location.class));
        return display;
    }
}

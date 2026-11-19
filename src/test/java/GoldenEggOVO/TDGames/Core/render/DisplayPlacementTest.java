package GoldenEggOVO.TDGames.Core.render;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DisplayPlacementTest {
    @Test void appliesTheAbsoluteSeatRotationBeforeConfiguringTheDisplay() {
        World world = mock(World.class);
        ItemDisplay entity = mock(ItemDisplay.class);
        Location seat = new Location(world, 5, 70, 8, -120, -90);
        when(world.spawn(eq(seat), eq(ItemDisplay.class), ArgumentMatchers.<Consumer<ItemDisplay>>any())).thenAnswer(call -> {
            Consumer<ItemDisplay> initializer = call.getArgument(2);
            initializer.accept(entity);
            return entity;
        });

        assertSame(entity, DisplayPlacement.spawn(seat, ItemDisplay.class, display -> display.setGravity(false)));
        var order = inOrder(entity);
        order.verify(entity).setRotation(-120, -90);
        order.verify(entity).setGravity(false);
    }
}

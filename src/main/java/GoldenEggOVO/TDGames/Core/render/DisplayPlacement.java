package GoldenEggOVO.TDGames.Core.render;

import org.bukkit.Location;
import org.bukkit.entity.Display;

import java.util.function.Consumer;

public final class DisplayPlacement {
    private DisplayPlacement() {}

    public static <D extends Display> D spawn(Location at, Class<D> type, Consumer<D> configure) {
        return at.getWorld().spawn(at, type, display -> {
            // Some Bukkit implementations only copy the coordinates when creating a display.
            display.setRotation(at.getYaw(), at.getPitch());
            configure.accept(display);
        });
    }
}

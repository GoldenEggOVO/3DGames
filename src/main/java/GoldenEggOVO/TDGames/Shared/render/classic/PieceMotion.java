package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.render.DisplayMovement;

import GoldenEggOVO.TDGames.Core.render.DisplayAudience;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PieceMotion {
    private final Map<Entity, Location> targets = new LinkedHashMap<>();
    private final List<Vector> route;
    private final double lift;
    private final int duration;
    private final boolean temporary;
    private int frame;

    PieceMotion(List<Entity> parts, Vector offset, double lift, int duration, boolean temporary) {
        this(parts, List.of(offset, new Vector()), lift, duration, temporary);
    }

    PieceMotion(List<Entity> parts, List<Vector> route, double lift, int duration, boolean temporary) {
        this.route = route.stream().map(Vector::clone).toList();
        this.lift = lift;
        this.duration = duration;
        this.temporary = temporary;
        for (Entity part : parts) {
            targets.put(part, part.getLocation().clone());
            if (part instanceof Display display) display.setTeleportDuration(2);
        }
        position(0);
    }

    PieceMotion(Entity part, Location destination, double lift, int duration) {
        this.route = List.of(part.getLocation().toVector().subtract(destination.toVector()), new Vector());
        this.lift = lift;
        this.duration = duration;
        this.temporary = true;
        targets.put(part, destination.clone());
        if (part instanceof Display display) display.setTeleportDuration(2);
        position(0);
    }

    boolean tick() {
        frame++;
        if (frame > duration) {
            if (temporary) targets.keySet().forEach(DisplayAudience::retire);
            return true;
        }
        position(Math.min(1, (double) frame / duration));
        if (frame < duration) return false;
        return !temporary;
    }

    void cancel() {
        if (temporary) targets.keySet().forEach(DisplayAudience::retire);
        else position(1);
    }

    private void position(double progress) {
        double segment = progress * (route.size() - 1);
        int index = Math.min(route.size() - 2, (int)segment);
        double step = segment - index, ease = step * step * (3 - 2 * step);
        Vector shift = route.get(index).clone().multiply(1 - ease).add(route.get(index + 1).clone().multiply(ease))
                .add(new Vector(0, Math.pow(Math.sin(Math.PI * step), 2) * lift, 0));
        targets.forEach((part, target) -> DisplayMovement.move(part, target.clone().add(shift)));
    }
}

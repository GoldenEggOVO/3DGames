package GoldenEggOVO.TDGames.Shared.render.cards;

import GoldenEggOVO.TDGames.Core.render.DisplayPlacement;
import GoldenEggOVO.TDGames.Core.render.DisplayMovement;

import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;

import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import java.util.*;

public final class TurnRing implements AutoCloseable {
    private final GameSession room;
    private final Location origin;
    private final List<Part> parts = new ArrayList<>();
    private int direction = 1;
    private double angle;
    private boolean closed;
    private DisplayAudience audience;

    private record Part(BlockDisplay entity, ModelDefinition.Block forward, ModelDefinition.Block reverse) {}

    TurnRing(TDGamesPlugin plugin, GameSession room, Location origin, NamespacedKey tag) {
        this(plugin, room, origin, tag, null);
    }

    public TurnRing(
            TDGamesPlugin plugin,
            GameSession room,
            Location origin,
            NamespacedKey tag,
            DisplayAudience audience) {
        this.audience = audience;
        this.room = room;
        this.origin = origin.clone();
        var forward = ModelRepository.require("tabletop/color_eight/turn_ring_clockwise").blocks();
        var reverse = ModelRepository.require("tabletop/color_eight/turn_ring_counterclockwise").blocks();
        for (int index = 0; index < forward.size(); index++) add(forward.get(index), reverse.get(index), tag);
    }

    private void add(ModelDefinition.Block forward, ModelDefinition.Block reverse, NamespacedKey tag) {
        BlockDisplay entity =
                DisplayPlacement.spawn(
                        at(forward),
                        BlockDisplay.class,
                        display -> {
                            display.setPersistent(false);
                            display.setGravity(false);
                            display.setInvulnerable(true);
                            display.getPersistentDataContainer()
                                    .set(
                                            tag,
                                            PersistentDataType.STRING,
                                            room.id + "|@board");
                            int light = forward.brightness() == null ? 15 : forward.brightness();
                            display.setBrightness(new Display.Brightness(light, light));
                            display.setViewRange(.35f);
                            display.setTeleportDuration(2);
                            display.setInterpolationDuration(2);
                            display.setBlock(forward.material().createBlockData());
                            if (audience != null) {
                                audience.add(display, false);
                            }
                            display.setTransformation(localPose(forward));
                        });
        parts.add(new Part(entity, forward, reverse));
    }

    private Location at(ModelDefinition.Block pose) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        Location at =
                origin.clone()
                        .add(
                                pose.x() * cos + pose.z() * sin,
                                pose.bottom(),
                                -pose.x() * sin + pose.z() * cos);
        at.setYaw(-(float) Math.toDegrees(angle));
        at.setPitch(0);
        return at;
    }

    private static Transformation localPose(ModelDefinition.Block part) {
        return ModelGeometry.relativePose(part);
    }

    private void position() {
        for (Part part : parts) {
            var selected = direction > 0 ? part.forward() : part.reverse();
            DisplayMovement.move(part.entity(), at(selected));
        }
    }

    public void direction(int direction) {
        int next = direction < 0 ? -1 : 1;
        if (closed || this.direction == next) {
            return;
        }
        this.direction = next;
        for (Part part : parts) {
            var selected = direction > 0 ? part.forward() : part.reverse();
            part.entity().setTransformation(localPose(selected));
            part.entity().setBlock(selected.material().createBlockData());
            int light = selected.brightness() == null ? 15 : selected.brightness();
            part.entity().setBrightness(new Display.Brightness(light, light));
        }
        position();
    }

    public void tick() {
        if (closed || room.phase() != GameSession.Phase.PLAYING) {
            return;
        }

        angle = (angle + direction * .024) % (2 * Math.PI);
        position();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        parts.forEach(
                part -> {
                    if (audience != null) {
                        audience.remove(part.entity());
                    } else {
                        DisplayAudience.retire(part.entity());
                    }
                });
        parts.clear();
    }
}

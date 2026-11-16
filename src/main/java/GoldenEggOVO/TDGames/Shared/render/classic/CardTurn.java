package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CardTurn {
    private final TDGamesPlugin plugin;
    private final Player player;
    private final Map<Entity, Location> backs = new LinkedHashMap<>(), faces = new LinkedHashMap<>();
    private final Map<Entity, Location> collectionOrigins = new LinkedHashMap<>();
    private final Map<Display, Transformation> originalPoses = new LinkedHashMap<>();
    private final List<Entity> collection;
    private final Display body;
    private final double halfDepth;
    private int frame;
    private boolean collected;
    private boolean previousRemoved;

    CardTurn(TDGamesPlugin plugin, Player player, List<Entity> before, List<Entity> after, double halfDepth) {
        this(plugin, player, before, after, List.of(), halfDepth);
    }

    CardTurn(TDGamesPlugin plugin, Player player, List<Entity> before, List<Entity> after, List<Entity> collection, double halfDepth) {
        this.plugin = plugin;
        this.player = player;
        this.collection = List.copyOf(collection);
        this.halfDepth = halfDepth;
        body = (Display)before.getFirst();
        after.set(0, body).remove();
        before.stream().filter(part -> part != body).forEach(part -> backs.put(part, part.getLocation().clone()));
        after.forEach(part -> faces.put(part, part.getLocation().clone()));
        collection.forEach(part -> collectionOrigins.put(part, part.getLocation().clone()));
        for (List<Entity> side : List.of(before, after)) for (Entity part : side) if (part instanceof Display display) {
            var pose = display.getTransformation();
            originalPoses.put(display, new Transformation(new Vector3f(pose.getTranslation()),
                    new Quaternionf(pose.getLeftRotation()), new Vector3f(pose.getScale()), new Quaternionf(pose.getRightRotation())));
            display.setTeleportDuration(2);
        }
        for (Entity part : collection) if (part instanceof Display display) display.setTeleportDuration(2);
        rotate(faces, -180);
        after.stream().filter(part -> part != body).forEach(part -> player.showEntity(plugin, part));
        originalPoses.keySet().forEach(display -> display.setInterpolationDuration(1));
    }

    boolean tick() {
        frame++;
        if (frame > 9) {
            if (!collection.isEmpty() && frame <= 13) {
                moveCollection((frame - 9) / 4.0);
                return false;
            }
            if (!collection.isEmpty() && frame < 15) return false;
            if (!collected && !collection.isEmpty())
                GoldenEggOVO.TDGames.Shared.audio.TableSounds.detailFor(plugin, player, collection.getFirst().getLocation(), "cards.collect", .15f);
            collect();
            return true;
        }
        float progress = frame / 9f;
        float angle = 180f * progress * progress * (3 - 2 * progress);
        rotate(backs, angle);
        rotate(faces, angle - 180f);
        rotate(body, faces.get(body), angle);
        if (frame >= 9) removePreviousFace();
        return frame >= 9 && collection.isEmpty();
    }

    void cancel() {
        faces.keySet().forEach(part -> { if (part instanceof Display display) display.setInterpolationDuration(0); });
        rotate(faces, 0); rotate(body, faces.get(body), 180); removePreviousFace(); collect();
    }

    boolean uses(List<Entity> parts) {
        return parts.stream().anyMatch(part -> backs.containsKey(part) || faces.containsKey(part) || collection.contains(part));
    }

    private void moveCollection(double progress) {
        if (faces.isEmpty() || collectionOrigins.isEmpty()) return;
        double ease = progress * progress * (3 - 2 * progress);
        Vector first = collectionOrigins.values().iterator().next().toVector();
        Vector second = faces.values().iterator().next().toVector();
        Vector offset = second.clone().subtract(first).multiply(.09 * ease);
        move(collectionOrigins, offset, .07 * ease);
        move(faces, offset.multiply(-1), .07 * ease);
    }

    private static void move(Map<Entity, Location> parts, Vector offset, double lift) {
        parts.forEach((part, at) -> part.teleport(at.clone().add(offset).add(0, lift, 0)));
    }

    private void collect() {
        if (collected || collection.isEmpty()) return;
        faces.keySet().forEach(Entity::remove);
        collection.forEach(Entity::remove);
        collected = true;
    }

    private void removePreviousFace() {
        if (previousRemoved) return;
        backs.keySet().forEach(Entity::remove);
        previousRemoved = true;
    }

    private void rotate(Map<Entity, Location> parts, float pitch) {
        parts.forEach((part, at) -> {
            if (part instanceof Display display && part != body) rotate(display, at, pitch);
        });
    }

    private void rotate(Display display, Location at, float pitch) {
        Location pivot = faces.get(body);
        double yaw = Math.toRadians(pivot.getYaw());
        double lift = halfDepth * 1.08 * Math.abs(Math.sin(Math.toRadians(pitch)));
        Quaternionf rotation = new Quaternionf().rotateX((float)Math.toRadians(pitch));
        Transformation base = originalPoses.get(display);
        Vector3f offset = at.toVector().subtract(pivot.toVector()).rotateAroundY(yaw).toVector3f();
        Vector3f translation = new Vector3f(base.getTranslation()).add(offset).sub(0, .006f, 0)
                .rotate(rotation).add(0, .006f + (float)lift, 0).sub(offset);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(translation, new Quaternionf(rotation).mul(base.getLeftRotation()),
                new Vector3f(base.getScale()), new Quaternionf(base.getRightRotation())));
    }
}

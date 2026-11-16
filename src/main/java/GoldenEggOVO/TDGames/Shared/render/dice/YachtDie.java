package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;

import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class YachtDie implements AutoCloseable {
    private record Part(BlockDisplay display, Vector3f center, Quaternionf face, Vector3f size) {}
    private final DisplayAudience audience;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Part> nativeParts = new ArrayList<>();
    private final double size;
    private final BlockDisplay body;
    private final ItemDisplay packed;

    YachtDie(TDGamesPlugin plugin, GameSession room, Location origin, NamespacedKey tag,
            DisplayAudience audience, boolean resourcePack, double size, String id) {
        this.audience = audience;
        this.size = size;
        if (resourcePack) {
            body = null;
            packed = origin.getWorld().spawn(origin, ItemDisplay.class, display -> {
                init(display, room, tag, id);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setItemStack(plugin.pack().item("yacht_die"));
            });
            entities.add(packed);
        } else {
            packed = null;
            String model = size == .18 ? "tabletop/yacht/die" : size == .17 ? "tabletop/ludo/die_compact" : size == .28 ? "tabletop/ludo/die" : null;
            if (model == null) throw new IllegalArgumentException("Unsupported die size: " + size);
            var blocks = ModelRepository.require(model).blocks();
            body = block(origin, blocks.getFirst().material(), room, tag, id);
            for (var part : blocks) {
                var at = part.position(); var dimensions = part.size();
                var display = part == blocks.getFirst() ? body : block(origin, part.material(), room, tag, id);
                if (part.brightness() != null) display.setBrightness(new Display.Brightness(part.brightness(), part.brightness()));
                nativeParts.add(new Part(display,
                        new Vector3f((float)at.x(), (float)at.y(), (float)at.z()),
                        ModelGeometry.rotation(part.rotation()),
                        new Vector3f((float)dimensions.x(), (float)dimensions.y(), (float)dimensions.z())));
            }
        }
        entities.forEach(entity -> audience.add(entity, resourcePack));
    }

    void pose(DiceMotion.Pose pose) {
        Quaternionf rotation = pose.rotation();
        Vector3f center = new Vector3f((float)pose.x(), (float)pose.y(), (float)pose.z());
        if (packed != null) {
            transform(packed, center, rotation, new Vector3f((float)size));
            return;
        }
        for (Part pip : nativeParts) {
            Quaternionf orientation = new Quaternionf(rotation).mul(pip.face());
            Vector3f corner = new Vector3f(pip.size()).mul(.5f).rotate(orientation);
            Vector3f at = new Vector3f(pip.center()).rotate(rotation).add(center).sub(corner);
            transform(pip.display(), at, orientation, new Vector3f(pip.size()));
        }
    }

    private BlockDisplay block(Location origin, Material material, GameSession room, NamespacedKey tag, String id) {
        BlockDisplay display = origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
            init(d, room, tag, id);
            d.setBlock(material.createBlockData());
        });
        entities.add(display);
        return display;
    }

    private void init(Display display, GameSession room, NamespacedKey tag, String id) {
        display.setVisibleByDefault(!audience.managed());
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15,15));
        display.setViewRange(.35f);
        display.setInterpolationDuration(display instanceof BlockDisplay ? 0 : 2);
        display.setTeleportDuration(2);
    }

    private static void transform(Display display, Vector3f at, Quaternionf rotation, Vector3f scale) {
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(at, new Quaternionf(rotation), scale, new Quaternionf()));
    }

    Display highlight(TDGamesPlugin plugin, Player viewer) {
        return GoldenEggOVO.TDGames.Shared.render.TableButtonStyle.privateOutline(plugin, viewer, packed != null ? packed : body);
    }

    @Override public void close() { entities.forEach(audience::remove); entities.clear(); }
}

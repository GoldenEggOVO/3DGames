package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Core.render.DisplayAudience;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Block;
import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Point;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class TableButtonStyle {
    public static final Material BODY = Material.GRAY_CONCRETE;
    public static final Material EDGE = Material.CYAN_CONCRETE;

    private TableButtonStyle() { }

    public static List<Block> layers(double width, double height, double depth, boolean flat) {
        return layers(width, height, depth, flat, BODY);
    }

    public static List<Block> layers(double width, double height, double depth, boolean flat, Material face) {
        double rim = Math.min(width, flat ? depth : height) * .06;
        if (flat) return List.of(
                new Block("edge", new Point(0, height / 6, 0), new Point(width, height / 3, depth), Point.ZERO, EDGE, 15),
                new Block("face", new Point(0, height * 2 / 3, 0), new Point(width - rim * 2, height * 2 / 3, depth - rim * 2), Point.ZERO, face, 15));
        return List.of(
                new Block("edge", new Point(0, height / 2, -depth / 3), new Point(width, height, depth / 3), Point.ZERO, EDGE, 15),
                new Block("face", new Point(0, height / 2, depth / 6), new Point(width - rim * 2, height - rim * 2, depth * 2 / 3), Point.ZERO, face, 15));
    }

    public static void hover(List<? extends Entity> parts, boolean selected) {
        for (Entity part : parts) {
            if (!(part instanceof BlockDisplay) && !(part instanceof ItemDisplay)) continue;
            Display display = (Display) part;
            if (selected) display.setGlowColorOverride(Color.YELLOW);
            display.setGlowing(selected);
        }
    }

    public static Display privateOutline(Plugin plugin, Player viewer, Display source) {
        var original = source.getTransformation();
        var scale = new Vector3f(original.getScale()).mul(1.0002f);
        var at = new Vector3f(original.getTranslation());
        if (source instanceof BlockDisplay) at.sub(new Vector3f(.5f).rotate(original.getRightRotation())
                .mul(new Vector3f(scale).sub(original.getScale())).rotate(original.getLeftRotation()));
        var pose = new Transformation(at, new Quaternionf(original.getLeftRotation()), scale,
                new Quaternionf(original.getRightRotation()));
        Display copy;
        if (source instanceof ItemDisplay item) copy = source.getWorld().spawn(source.getLocation(), ItemDisplay.class, display -> {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setItemStack(item.getItemStack());
            outlinePose(display, pose);
        });
        else copy = source.getWorld().spawn(source.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(((BlockDisplay)source).getBlock());
            outlinePose(display, pose);
        });
        DisplayAudience.show(plugin, viewer, copy);
        return copy;
    }

    private static void outlinePose(Display display, Transformation pose) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
        display.setTransformation(pose);
        display.setGlowColorOverride(Color.YELLOW);
        display.setGlowing(true);
    }
}

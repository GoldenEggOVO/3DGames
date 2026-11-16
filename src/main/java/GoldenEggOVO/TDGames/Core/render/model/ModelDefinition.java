package GoldenEggOVO.TDGames.Core.render.model;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.List;

public record ModelDefinition(List<Block> blocks, List<Label> labels) {
    public ModelDefinition {
        blocks = List.copyOf(blocks);
        labels = List.copyOf(labels);
        if (blocks.isEmpty() && labels.isEmpty()) throw new IllegalArgumentException("Empty model");
    }

    public record Point(double x, double y, double z) {
        public static final Point ZERO = new Point(0, 0, 0);
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite((float)x) || !Float.isFinite((float)y) || !Float.isFinite((float)z))
                throw new IllegalArgumentException("Coordinates must be finite");
        }
    }

    public record Block(String name, Point position, Point size, Point rotation, Material material, Integer brightness) {
        public Block {
            if (name == null || name.isBlank() || position == null || size == null || rotation == null)
                throw new IllegalArgumentException("Missing block fields");
            if (size.x <= 0 || size.y <= 0 || size.z <= 0)
                throw new IllegalArgumentException("Block dimensions must be positive: " + name);
            if (material == null || !material.isBlock()) throw new IllegalArgumentException("Invalid block material: " + name);
            if (brightness != null && (brightness < 0 || brightness > 15))
                throw new IllegalArgumentException("Invalid brightness: " + name);
        }

        public double x() { return position.x; }
        public double bottom() { return position.y - size.y / 2; }
        public double z() { return position.z; }
        public double w() { return size.x; }
        public double h() { return size.y; }
        public double d() { return size.z; }
        public float displayYaw() { return rotation.y == 0 ? 0 : (float)-rotation.y; }
    }

    public record Label(String name, String text, Point position, float width, float height, Integer color) {
        public Label {
            if (name == null || name.isBlank() || text == null || position == null
                    || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
                throw new IllegalArgumentException("Invalid label: " + name);
        }
    }

    public BoundingBox bounds() { return ModelGeometry.bounds(this); }
}

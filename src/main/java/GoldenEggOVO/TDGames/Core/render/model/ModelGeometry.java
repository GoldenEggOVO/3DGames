package GoldenEggOVO.TDGames.Core.render.model;

import org.bukkit.util.BoundingBox;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.util.Transformation;

public final class ModelGeometry {
    public static Transformation pose(ModelDefinition.Block block, float scale) {
        var pose = pose(block);
        pose.getTranslation().mul(scale);
        pose.getScale().mul(scale);
        return pose;
    }

    public static Transformation relativePose(ModelDefinition.Block block) {
        var pose = pose(block);
        pose.getTranslation().sub((float)block.x(), (float)block.bottom(), (float)block.z());
        return pose;
    }

    public static Transformation pose(ModelDefinition.Block block) {
        var size = block.size(); var position = block.position();
        var dimensions = new Vector3f((float)size.x(), (float)size.y(), (float)size.z());
        var orientation = rotation(block.rotation());
        var corner = new Vector3f(dimensions).mul(-.5f).rotate(orientation)
                .add((float)position.x(), (float)position.y(), (float)position.z());
        return new Transformation(corner, orientation, dimensions, new Quaternionf());
    }

    public static Quaternionf rotation(ModelDefinition.Point rotation) {
        return new Quaternionf().rotateY((float)Math.toRadians(rotation.y()))
                .rotateZ((float)Math.toRadians(rotation.z())).rotateX((float)Math.toRadians(rotation.x()));
    }

    public static Matrix4f matrix(ModelDefinition.Block block) {
        var p = block.position();
        var s = block.size();
        var r = block.rotation();
        return new Matrix4f().translation((float)p.x(), (float)p.y(), (float)p.z())
                .rotateY((float)Math.toRadians(r.y())).rotateZ((float)Math.toRadians(r.z()))
                .rotateX((float)Math.toRadians(r.x()))
                .translate((float)(-s.x()/2), (float)(-s.y()/2), (float)(-s.z()/2))
                .scale((float)s.x(), (float)s.y(), (float)s.z());
    }

    public static BoundingBox bounds(ModelDefinition model) {
        BoundingBox bounds = null;
        for (var block : model.blocks()) {
            var matrix = matrix(block);
            for (int corner = 0; corner < 8; corner++) {
                var p = matrix.transformPosition(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
                if (bounds == null) bounds = new BoundingBox(p.x, p.y, p.z, p.x, p.y, p.z);
                else bounds.union(p.x, p.y, p.z);
            }
        }
        return bounds;
    }

    private ModelGeometry() {}
}

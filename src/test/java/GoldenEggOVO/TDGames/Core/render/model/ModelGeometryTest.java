package GoldenEggOVO.TDGames.Core.render.model;

import org.bukkit.Material;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelGeometryTest {
    @Test void displayPosePreservesAllCornersWithoutMatrixDecomposition() {
        var block = new ModelDefinition.Block("beam", new ModelDefinition.Point(3, 2, 1),
                new ModelDefinition.Point(2, .1, .3), new ModelDefinition.Point(15, 123, -40), Material.OAK_PLANKS, null);
        var pose = ModelGeometry.pose(block);
        var actual = new Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation());
        assertTrue(actual.equals(ModelGeometry.matrix(block), 1e-6f));
    }

    @Test void blockCoordinatesUseTheSpecifiedCenterAndRotateAroundIt() {
        var block = new ModelDefinition.Block("beam", new ModelDefinition.Point(3,2,1),
                new ModelDefinition.Point(2,1,1), new ModelDefinition.Point(0,0,90), Material.OAK_PLANKS, null);
        var matrix = ModelGeometry.matrix(block);
        var center = matrix.transformPosition(new Vector3f(.5f));
        assertEquals(3, center.x, 1e-6);
        assertEquals(2, center.y, 1e-6);
        assertEquals(1, center.z, 1e-6);
        var model = new ModelDefinition(List.of(block), List.of());
        var bounds = ModelGeometry.bounds(model);
        assertEquals(1, bounds.getWidthX(), 1e-6);
        assertEquals(2, bounds.getHeight(), 1e-6);
        assertEquals(1, bounds.getWidthZ(), 1e-6);
    }
}

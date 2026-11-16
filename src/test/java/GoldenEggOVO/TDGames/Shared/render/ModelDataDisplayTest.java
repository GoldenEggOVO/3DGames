package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;
import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ModelDataDisplayTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void furnitureUsesTheRotationAndBrightnessFromItsDefinition() {
        var part = new ModelDefinition.Block("beam", new ModelDefinition.Point(.2, -.3, .4),
                new ModelDefinition.Point(.5, .1, .2), new ModelDefinition.Point(15, 35, 25),
                Material.DIAMOND_BLOCK, 9);
        try (var models = mockStatic(ModelRepository.class, CALLS_REAL_METHODS)) {
            models.when(() -> ModelRepository.require("tabletop/shared/square_table"))
                    .thenReturn(new ModelDefinition(List.of(part), List.of()));
            var fixture = new TableViewTest.Fixture("gomoku");
            try {
                var display = fixture.entities.stream().filter(BlockDisplay.class::isInstance)
                        .map(BlockDisplay.class::cast).filter(d -> mockingDetails(d).getInvocations().stream()
                                .anyMatch(call -> call.getMethod().getName().equals("setBlock")
                                        && ((org.bukkit.block.data.BlockData)call.getArgument(0)).getMaterial() == Material.DIAMOND_BLOCK))
                        .findFirst().orElseThrow();
                var pose = fixture.transforms.get(display);
                var actual = new Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                        .scale(pose.getScale()).rotate(pose.getRightRotation());
                assertTrue(actual.equals(ModelGeometry.matrix(part), 1e-6f));
                verify(display).setBrightness(new Display.Brightness(9, 9));
            } finally { fixture.view.close(); }
        }
    }
}

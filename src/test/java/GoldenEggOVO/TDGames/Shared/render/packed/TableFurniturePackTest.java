package GoldenEggOVO.TDGames.Shared.render.packed;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class TableFurniturePackTest {
    @Test void furniturePatchesUseSolidWarmColors() throws Exception {
        var root = Path.of("resource-pack/assets/3dgames/textures/item/shared/materials");
        for (String name : new String[] {"future_shell", "future_chassis", "future_accent"}) {
            var path = root.resolve(name + ".png");
            assertTrue(java.nio.file.Files.isRegularFile(path), name + " needs an independent furniture patch");
            var image = ImageIO.read(path.toFile());
            var colors = new HashSet<Integer>();
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                colors.add(image.getRGB(x, y));
            assertEquals(1, colors.size(), name + " must remain a flat color");
            var color = new Color(image.getRGB(0, 0));
            if (name.equals("future_shell")) assertTrue(color.getRed() > color.getGreen() && color.getGreen() > color.getBlue() && color.getRed() < 150);
            if (name.equals("future_chassis")) assertTrue(color.getRed() > color.getGreen() && color.getGreen() > color.getBlue() && color.getRed() < 75);
            if (name.equals("future_accent")) assertTrue(color.getRed() > color.getGreen() && color.getGreen() > color.getBlue());
        }
    }

    @Test void sharedWalnutAndIvoryRemainDistinctUnderTheSameLighting() throws Exception {
        var root = Path.of("resource-pack/assets/3dgames/textures/item/shared/materials");
        Color wood = average(root.resolve("wood.png"));
        Color ivory = average(root.resolve("ivory.png"));
        assertTrue(wood.getRed() < 90 && wood.getRed() > wood.getGreen()
                && wood.getGreen() > wood.getBlue(), "Furniture must have dark walnut color");
        assertTrue(ivory.getRed() > 210 && ivory.getGreen() > 195
                && ivory.getRed() - ivory.getBlue() >= 15, "Card edges must read as warm ivory");
        assertTrue(ivory.getGreen() - wood.getGreen() > 130, "Ivory cards must contrast with walnut");
        var material = ImageIO.read(root.resolve("wood.png").toFile());
        var colors = new HashSet<Integer>();
        for (int y = 0; y < material.getHeight(); y++) for (int x = 0; x < material.getWidth(); x++) {
            Color color = new Color(material.getRGB(x, y));
            colors.add(color.getRGB());
        }
        assertEquals(1, colors.size(), "Furniture must use solid color patches");
    }

    private static Color average(Path path) throws Exception {
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        long red = 0, green = 0, blue = 0, count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            Color color = new Color(image.getRGB(x, y), true);
            if (color.getAlpha() == 0) continue;
            red += color.getRed(); green += color.getGreen(); blue += color.getBlue(); count++;
        }
        assertTrue(count > 0, path.toString());
        return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
    }
}

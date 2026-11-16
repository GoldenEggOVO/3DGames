package GoldenEggOVO.TDGames.Core.pack;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PackedModelsTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void everyRuntimeModelUsesTheExistingItemDefinitionWithoutCraftEngine() throws Exception {
        var models = new PackedModels();
        assertTrue(models.ready());
        int count = 0;
        try (var files = Files.walk(Path.of("resource-pack/craftengine/resources/3dgames/configuration"))) {
            for (var path : files.filter(file -> file.toString().endsWith(".yml")).toList()) {
                var items = YamlConfiguration.loadConfiguration(path.toFile()).getConfigurationSection("items");
                for (String key : items.getKeys(false)) {
                    var item = models.item(key.substring("3dgames:".length()));
                    assertEquals(Material.PAPER, item.getType());
                    assertEquals(items.getString(key + ".item_model"), item.getItemMeta().getItemModel().toString());
                    item.setAmount(7);
                    assertEquals(1, models.item(key.substring("3dgames:".length())).getAmount());
                    count++;
                }
            }
        }
        assertEquals(CraftEngineModels.IDS.size(), count);
        assertThrows(IllegalArgumentException.class, () -> models.item("missing"));
    }
}

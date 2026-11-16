package GoldenEggOVO.TDGames.Core.pack;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackCatalogTest {
    @Test
    void bundledDefaultsMatchThePublishedChecksumAndEditableFiles() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/3DGames-resource-pack.zip")) {
            assertNotNull(input, "Editable defaults must be bundled with the plugin");
            bytes = input.readAllBytes();
        }
        try (var hash = getClass().getResourceAsStream("/resource-pack.sha1")) {
            assertEquals(new String(hash.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim(),
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes)));
        }
        int files = 0;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                assertTrue(name.equals("pack.mcmeta") || name.startsWith("assets/") || name.startsWith("licenses/"), name);
                String sourceName = name;
                if (name.startsWith("licenses/")) {
                    assertTrue(name.startsWith("licenses/3dgames/"), name);
                    sourceName = "licenses/" + name.substring("licenses/3dgames/".length());
                }
                assertArrayEquals(Files.readAllBytes(Path.of("resource-pack").resolve(sourceName)), zip.readAllBytes(), name);
                files++;
            }
        }
        assertTrue(files > 500, "The plugin needs the complete client pack");
    }

    @Test
    void staticItemsAndRegistrationsMatchEveryRuntimeModel() throws Exception {
        Path pack = Path.of("resource-pack");
        Path items = pack.resolve("assets/3dgames/items");
        Set<Path> definitions;
        try (var paths = Files.walk(items)) {
            definitions = paths.filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toSet());
        }
        Set<String> names = Set.copyOf(CraftEngineModels.IDS);
        var registration = new YamlConfiguration();
        Path configuration = pack.resolve("craftengine/resources/3dgames/configuration");
        try (var files = Files.walk(configuration)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".yml")).toList()) {
                var part = YamlConfiguration.loadConfiguration(path.toFile());
                for (String key : part.getConfigurationSection("items").getKeys(false)) {
                    assertFalse(registration.contains("items." + key), "Duplicate registration: " + key);
                    String itemModel = part.getString("items." + key + ".item_model");
                    assertTrue(itemModel.startsWith("3dgames:" + configuration.relativize(path).getName(0) + "/"), key);
                    registration.createSection("items." + key,
                            part.getConfigurationSection("items." + key).getValues(false));
                }
            }
        }
        assertEquals(names.stream().map(name -> "3dgames:" + name).collect(Collectors.toSet()),
                registration.getConfigurationSection("items").getKeys(false));
        var registeredPaths = new java.util.HashSet<Path>();
        for (String name : names) {
            String itemModel = registration.getString("items.3dgames:" + name + ".item_model");
            assertTrue(itemModel.startsWith("3dgames:"), name);
            Path path = items.resolve(itemModel.substring("3dgames:".length()) + ".json");
            assertTrue(Files.isRegularFile(path), name);
            assertTrue(registeredPaths.add(path), name);
            var item = JsonParser.parseString(Files.readString(path))
                    .getAsJsonObject().getAsJsonObject("model");
            assertEquals("minecraft:model", item.get("type").getAsString(), name);
            String model = item.get("model").getAsString();
            assertTrue(model.startsWith("3dgames:item/"), name);
            assertEquals(path.getFileName().toString().replace(".json", ""),
                    Path.of(model.split(":", 2)[1]).getFileName().toString(), name);
            assertTrue(Files.isRegularFile(pack.resolve("assets/" + model.replace(":", "/models/") + ".json")), name);
            assertEquals("paper", registration.getString("items.3dgames:" + name + ".material"), name);
            assertEquals(model.replace(":item/", ":"), itemModel, name);
        }
        assertEquals(definitions, registeredPaths);
    }
}

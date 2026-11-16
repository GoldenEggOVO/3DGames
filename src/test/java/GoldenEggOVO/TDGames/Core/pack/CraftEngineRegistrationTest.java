package GoldenEggOVO.TDGames.Core.pack;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftEngineRegistrationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path folder;

    private TDGamesPlugin plugin(boolean installed) {
        var server = mock(Server.class);
        var manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        if (installed) {
            var engine = mock(Plugin.class);
            when(engine.getDataFolder()).thenReturn(folder.resolve("engine-data").toFile());
            when(manager.getPlugin("CraftEngine")).thenReturn(engine);
        }
        var plugin = mock(TDGamesPlugin.class, CALLS_REAL_METHODS);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(folder.resolve("3DGames").toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CraftEngineRegistrationTest"));
        return plugin;
    }

    @Test
    void firstLoadInstallsMatchingRegistrationsIntoDetectedEngineFolder() throws Exception {
        plugin(true).onLoad();
        Path target = folder.resolve("engine-data/resources/3dgames");
        for (String name : new String[] {"pack.yml",
                "configuration/tabletop/items.yml", "configuration/tabletop/classic.yml",
                "configuration/tabletop/shogi_pieces.yml", "configuration/shared/items.yml"}) {
            assertTrue(Files.isRegularFile(target.resolve(name)), name);
            assertArrayEquals(Files.readAllBytes(Path.of("resource-pack/craftengine/resources/3dgames").resolve(name)),
                    Files.readAllBytes(target.resolve(name)), name);
        }
        var classic = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                target.resolve("configuration/tabletop/classic.yml").toFile());
        assertEquals(21, classic.getConfigurationSection("items").getKeys(false).size());
        for (int color = 0; color < 6; color++)
            assertNotNull(classic.getString("items.3dgames:classic_mastermind_peg_" + color + ".item_model"));
        assertNotNull(classic.getString("items.3dgames:classic_2048_board.item_model"));
        assertEquals("3dgames:tabletop/hex/hex_piece_red", classic.getString("items.3dgames:classic_hex_piece_red.item_model"));
        assertEquals("3dgames:tabletop/hex/hex_piece_blue", classic.getString("items.3dgames:classic_hex_piece_blue.item_model"));
        var registered = new java.util.HashSet<String>();
        try (var paths = Files.walk(target.resolve("configuration"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".yml")).toList()) {
                var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());
                for (String key : yaml.getConfigurationSection("items").getKeys(false))
                    assertTrue(registered.add(key), "Duplicate installed item: " + key);
            }
        }
        assertEquals(CraftEngineModels.IDS.stream().map(id -> "3dgames:" + id)
                .collect(java.util.stream.Collectors.toSet()), registered);
    }

    @Test
    void existingEntriesRemainUnchangedWhileMissingBuiltinsAreInstalled() throws Exception {
        Path target = folder.resolve("engine-data/resources/3dgames");
        Files.createDirectories(target.resolve("configuration"));
        Path items = target.resolve("configuration/tabletop/items.yml");
        Files.createDirectories(items.getParent());
        Files.writeString(items, "items:\n  custom:item:\n    material: stone\n"
                + "  3dgames:card_back:\n    material: stick\n    item_model: custom:back\n");
        plugin(true).onLoad();
        var installed = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(items.toFile());
        assertEquals("stone", installed.getString("items.custom:item.material"));
        assertEquals("stick", installed.getString("items.3dgames:card_back.material"));
        assertEquals("custom:back", installed.getString("items.3dgames:card_back.item_model"));
        assertNotNull(installed.getString("items.3dgames:card_r1.item_model"));
        assertFalse(Files.exists(target.resolve("configuration/casino")));
        assertTrue(Files.isRegularFile(target.resolve("pack.yml")));
        byte[] first = Files.readAllBytes(items);
        assertEquals(0, CraftEngineRegistration.install(folder.resolve("engine-data")));
        assertArrayEquals(first, Files.readAllBytes(items), "An unchanged complete registration should not be rewritten");
    }

    @Test
    void malformedExistingRegistrationIsRetained() throws Exception {
        Path target = folder.resolve("engine-data/resources/3dgames/configuration/tabletop/items.yml");
        Files.createDirectories(target.getParent());
        byte[] original = "items: [unterminated\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(target, original);
        assertThrows(java.io.IOException.class, () -> CraftEngineRegistration.install(folder.resolve("engine-data")));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void registrationsAlreadyDefinedElsewhereArePreservedWithoutDuplicates() throws Exception {
        Path pack = folder.resolve("engine-data/resources/3dgames");
        Path items = pack.resolve("configuration/custom/items.yml");
        Files.createDirectories(items.getParent());
        var contents = new StringBuilder("items:\n");
        for (String id : CraftEngineModels.IDS)
            contents.append("  3dgames:").append(id).append(":\n    material: stick\n");
        Files.writeString(items, contents);
        byte[] original = Files.readAllBytes(items);
        assertEquals(1, CraftEngineRegistration.install(folder.resolve("engine-data")));
        assertArrayEquals(original, Files.readAllBytes(items));
        try (var files = Files.walk(pack.resolve("configuration"))) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
        assertEquals(0, CraftEngineRegistration.install(folder.resolve("engine-data")));
    }

    @Test
    void duplicateExistingIdsFailBeforeWritingRegistrations() throws Exception {
        Path pack = folder.resolve("engine-data/resources/3dgames");
        Path configuration = pack.resolve("configuration");
        Files.createDirectories(configuration);
        String duplicate = "items:\n  3dgames:card_back:\n    material: stick\n";
        Files.writeString(configuration.resolve("first.yml"), duplicate);
        Files.writeString(configuration.resolve("second.yaml"), duplicate);
        assertThrows(java.io.IOException.class,
                () -> CraftEngineRegistration.install(folder.resolve("engine-data")));
        assertFalse(Files.exists(pack.resolve("pack.yml")));
        assertEquals(duplicate, Files.readString(configuration.resolve("first.yml")));
        assertEquals(duplicate, Files.readString(configuration.resolve("second.yaml")));
    }

    @Test
    void absenceOfCraftEngineDoesNotCreateResourceFolders() throws Exception {
        plugin(false).onLoad();
        assertFalse(Files.exists(folder.resolve("engine-data")));
    }
}

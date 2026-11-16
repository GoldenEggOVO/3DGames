package GoldenEggOVO.TDGames.Core.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CraftEngineRegistration {
    private CraftEngineRegistration() {}

    public static int install(Path dataFolder) throws IOException {
        Path root = dataFolder.toAbsolutePath().normalize();
        Path pack = root.resolve("resources/3dgames");
        Set<String> registered = registeredItems(root, pack.resolve("configuration"));
        int added = 0;
        for (String name : List.of("pack.yml",
                "configuration/tabletop/items.yml", "configuration/tabletop/classic.yml",
                "configuration/tabletop/shogi_pieces.yml", "configuration/shared/items.yml")) {
            Path target = pack.resolve(name);
            checkPath(root, target);
            try (var input = CraftEngineRegistration.class.getResourceAsStream("/craftengine/resources/3dgames/" + name)) {
                if (input == null) throw new IOException("Missing bundled CraftEngine registration: " + name);
                if (!name.equals("pack.yml")) {
                    if (mergeItems(target, new String(input.readAllBytes(), StandardCharsets.UTF_8), registered)) added++;
                    continue;
                }
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
                added++;
            }
        }
        return added;
    }

    private static void checkPath(Path root, Path target) throws IOException {
        for (Path parent = target; parent != null && parent.startsWith(root); parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw new IOException("CraftEngine resource path is a symbolic link: " + parent);
    }

    private static Set<String> registeredItems(Path root, Path configuration) throws IOException {
        checkPath(root, configuration);
        var registered = new HashSet<String>();
        if (!Files.exists(configuration)) return registered;
        try (var paths = Files.walk(configuration)) {
            for (Path file : paths.toList()) {
                checkPath(root, file);
                if (!Files.isRegularFile(file)
                        || !(file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))) continue;
                var yaml = parse(Files.readString(file));
                Object items = yaml.get("items");
                if (items == null) continue;
                if (!(items instanceof ConfigurationSection section))
                    throw new IOException("CraftEngine items must be a mapping: " + file);
                for (String key : section.getKeys(false))
                    if (!registered.add(key)) throw new IOException("Duplicate CraftEngine item ID: " + key);
            }
        }
        return registered;
    }

    private static boolean mergeItems(Path target, String bundled, Set<String> registered) throws IOException {
        boolean completeNewFile = !Files.exists(target);
        var existing = completeNewFile ? new YamlConfiguration() : parse(Files.readString(target));
        var defaults = parse(bundled);
        Object items = existing.get("items");
        if (items != null && !(items instanceof ConfigurationSection))
            throw new IOException("CraftEngine items must be a mapping: " + target);
        boolean changed = false;
        for (String key : defaults.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key;
            if (!registered.add(key)) {
                completeNewFile = false;
                continue;
            }
            existing.createSection(path, defaults.getConfigurationSection(path).getValues(false));
            changed = true;
        }
        if (!changed) return false;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "items-", ".tmp");
        try {
            Files.writeString(temporary, completeNewFile ? bundled : existing.saveToString());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    private static YamlConfiguration parse(String contents) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(contents);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid CraftEngine registration", exception);
        }
        return yaml;
    }
}

package GoldenEggOVO.TDGames.Multiplayer.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionalDependenciesTest {
    @Test
    void integrationsAreOptionalAndLoadAfterTheirProviders() {
        var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        var metadata = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(metadata.getStringList("softdepend").containsAll(List.of("CraftEngine", "Vault", "PlaceholderAPI")));
        assertTrue(metadata.getStringList("depend").isEmpty());
    }
}

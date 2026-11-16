package GoldenEggOVO.TDGames;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSeparationTest {
    @Test void pluginDoesNotExposeACasinoBridge() {
        assertThrows(NoSuchMethodException.class, () -> TDGamesPlugin.class.getMethod("companion"));
    }
}

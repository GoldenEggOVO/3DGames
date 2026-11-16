package GoldenEggOVO.TDGames;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleAccessTest {
    @Test
    void moduleReferencesCannotBeReplacedOutsideThePlugin() {
        for (var field : TDGamesPlugin.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()))
                assertTrue(Modifier.isPrivate(field.getModifiers()), field.getName());
        }
    }
}

package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MonopolyExperienceTextTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;
    @AfterEach void resetLanguage() { Language.reload(directory, "en_US", message -> fail(message)); }

    @Test void hoveringAStreetShowsItsCurrentRentAndBuildingLevelWithoutInternalIds() throws Exception {
        Language.reload(directory, "en_US", message -> fail(message));
        var game = new MonopolyGame(2, 17, Map.of());
        ((int[])field(game, "owners"))[1] = 0;
        ((int[])field(game, "buildings"))[1] = 2;
        String hint = MessageText.plain(ClassicText.cell(game, game.cells().get(1)));
        assertTrue(hint.contains("Rent 30"), hint);
        assertTrue(hint.contains("2 houses"), hint);
        assertTrue(hint.contains("Player 1"), hint);
        assertFalse(hint.contains("p1"), hint);
        assertFalse(hint.contains("["), hint);
    }

    @Test void theTurnHintExplainsHowToReachPropertyManagement() {
        Language.reload(directory, "en_US", message -> fail(message));
        var game = new MonopolyGame(2, 17, Map.of());
        String hint = MessageText.plain(ClassicText.status(game, 0));
        assertTrue(hint.contains("Player 1"), hint);
        assertTrue(hint.contains("1500"), hint);
        assertTrue(hint.contains("Click your properties"), hint);
        assertTrue(hint.length() < 180, hint);
    }

    private static Object field(MonopolyGame game, String name) throws Exception {
        var field = MonopolyGame.class.getDeclaredField(name); field.setAccessible(true); return field.get(game);
    }
}

package GoldenEggOVO.TDGames.Core.game;

import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassicCatalogTest {
    private static final List<String> NEW_GAMES = List.of("2048", "mancala", "nine-mens-morris",
            "dots-and-boxes", "mastermind", "hex", "memory", "backgammon", "battleship",
            "klondike", "shogi", "mini-shogi", "monopoly");

    @Test void allThirteenGamesHaveUsableCatalogEntries() {
        for (String id : NEW_GAMES) {
            assertTrue(GameCatalog.IDS.contains(id), id);
            assertTrue(GameCatalog.capacityValid(id, GameCatalog.defaultCapacity(id)), id);
            BoardGame game = assertDoesNotThrow(() -> GameFactory.create(id, GameCatalog.defaultCapacity(id), 42));
            assertEquals(id, game.id());
            assertFalse(game.finished(), id);
            assertFalse(game.cells().isEmpty(), id);
        }
    }

    @Test void trueSoloGamesAcceptOnePlayerWithoutInventingAnOpponent() {
        for (String id : List.of("2048", "klondike", "mastermind", "memory"))
            assertTrue(GameCatalog.capacityValid(id, 1), id);
        assertFalse(GameCatalog.capacityValid("shogi", 1));
    }
}

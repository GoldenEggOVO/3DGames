package GoldenEggOVO.TDGames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeBoundaryTest {
    @Test
    void pluginExposesSharedRuntimeAndRulesUseTheirModePackages() throws Exception {
        assertEquals("GoldenEggOVO.TDGames.Core.game.GameService",
                TDGamesPlugin.class.getMethod("games").getReturnType().getName());
        Class.forName("GoldenEggOVO.TDGames.Singleplayer.game.twentyfortyeight.TwentyFortyEightGame");
        Class.forName("GoldenEggOVO.TDGames.Multiplayer.game.chess.ChessGame");
        Class.forName("GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame");
    }
}

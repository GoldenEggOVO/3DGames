package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ClassicTextTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    @Test void shogiHoverIdentifiesPieceOwnerAndPromotionInBothVariants() {
        try {
            Language.reload(directory, "en_US", message -> fail(message));
            for (String kind : new String[] {"shogi", "mini-shogi"}) {
                var game = (ClassicGame) GameFactory.create(kind, 2, 1);
                String hint = MessageText.plain(ClassicText.cell(game, new Cell("a1", 0, 0, "+P", 1)));
                assertTrue(hint.contains("Gote"), hint);
                assertTrue(hint.contains("Promoted"), hint);
                assertTrue(hint.contains("Pawn"), hint);
                assertEquals("", MessageText.plain(ClassicText.cell(game, new Cell("a2", 0, 1, "", -1))));
            }
        } finally { Language.reload(directory, "en_US", message -> fail(message)); }
    }

    @Test void monopolyActionBarStaysConciseInsteadOfFlatteningTheWholeLedger() {
        try {
            Language.reload(directory, "en_US", message -> fail(message));
            var game = (ClassicGame) GameFactory.create("monopoly", 6, 1);
            String hint = MessageText.plain(ClassicText.status(game, 0));
            assertTrue(hint.length() < 180, hint);
            assertFalse(hint.contains("Seat 6:"), "Other players' accounts do not belong in a single-line hint");
        } finally { Language.reload(directory, "en_US", message -> fail(message)); }
    }

    @Test void controlsAndPrivateStatusesHaveBothLanguagesThroughRealGamePhases() {
        try {
            for (String language : new String[] {"en_US", "zh_CN"}) {
                assertTrue(Language.reload(directory, language, message -> fail(message)));
                for (String kind : GameCatalog.CLASSIC_IDS) {
                    var board = (ClassicGame) GameFactory.create(kind, GameCatalog.defaultCapacity(kind), 9123);
                    var random = new Random(20);
                    for (int step = 0; step < 100 && !board.finished(); step++) {
                        for (int seat = -1; seat < board.playerCount(); seat++) {
                            translated(ClassicText.status(board, seat), kind);
                            if (seat >= 0) for (String action : board.controlActions(seat))
                                translated(ClassicText.action(kind, action), kind + " " + action);
                        }
                        for (Cell cell : board.cells()) translated(ClassicText.cell(board, cell), kind + " " + cell.id());
                        String action = board.automaticAction();
                        if (action == null) action = board.botAction(board.currentPlayer(), random);
                        if (action == null) {
                            var legal = board.legalActions(board.currentPlayer());
                            if (legal.isEmpty()) break;
                            action = legal.get(random.nextInt(legal.size()));
                        }
                        board.apply(board.currentPlayer(), action);
                    }
                }
            }
        } finally { Language.reload(directory, "en_US", message -> fail(message)); }
    }

    private static void translated(net.kyori.adventure.text.Component text, String context) {
        String value = MessageText.plain(text);
        assertFalse(value.matches("(?s).*(?:classic\\.|monopoly\\.|mastermind\\.|error\\.)[a-z].*"), context + ": " + value);
    }
}

package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MonopolyTradeTextTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path directory;

    @AfterEach void resetLanguage() { Language.reload(directory, "en_US", message -> fail(message)); }

    @Test void cashForPropertyNamesBothPlayersAndOmitsEmptyAssetsAndInternalIds() throws Exception {
        var game = offer("350:0:-:p16:-:-");
        var before = game.publicInfo();
        assertEquals("\u4ea4\u6613\u63d0\u8bae\uff1a1\u53f7\u73a9\u5bb6\u54112\u53f7\u73a9\u5bb6\u652f\u4ed8350\uff1b2\u53f7\u73a9\u5bb6\u5c06\u5357\u74dc\u519c\u573a\u8f6c\u8ba9\u7ed91\u53f7\u73a9\u5bb6\u3002", summary(game, "zh_CN"));
        assertEquals("Trade offer: Player 1 pays 350 to Player 2; Player 2 transfers Pumpkin Farm to Player 1.", summary(game, "en_US"));
        assertEquals(before, game.publicInfo());
        assertNotNull(game.trade());
    }

    @Test void exchangesDescribeCashPropertiesAndNamedJailCardsInBothDirections() throws Exception {
        var game = offer("100:20:p1,p3:p16:0:1");
        String chinese = summary(game, "zh_CN");
        for (String term : List.of("1\u53f7\u73a9\u5bb6\u54112\u53f7\u73a9\u5bb6\u652f\u4ed8100", "2\u53f7\u73a9\u5bb6\u54111\u53f7\u73a9\u5bb6\u652f\u4ed820",
                "1\u53f7\u73a9\u5bb6\u5c06\u6a61\u6728\u6797\u3001\u767d\u6866\u6797\u8f6c\u8ba9\u7ed92\u53f7\u73a9\u5bb6", "2\u53f7\u73a9\u5bb6\u5c06\u5357\u74dc\u519c\u573a\u8f6c\u8ba9\u7ed91\u53f7\u73a9\u5bb6",
                "1\u53f7\u73a9\u5bb6\u5c06\u673a\u4f1a\u51fa\u72f1\u5361\u4ea4\u7ed92\u53f7\u73a9\u5bb6", "2\u53f7\u73a9\u5bb6\u5c06\u516c\u5171\u51fa\u72f1\u5361\u4ea4\u7ed91\u53f7\u73a9\u5bb6"))
            assertTrue(chinese.contains(term), chinese);
        String english = summary(game, "en_US");
        for (String term : List.of("Player 1 pays 100 to Player 2", "Player 2 pays 20 to Player 1",
                "Oak Grove, Birch Grove", "Chance jail card", "Community jail card"))
            assertTrue(english.contains(term), english);
        for (String text : List.of(chinese, english)) {
            assertFalse(text.contains("p1"), text);
            assertFalse(text.contains("["), text);
            assertFalse(text.contains("{"), text);
        }
    }

    @Test void propertyOnlyGiftsDoNotInventAPaymentOrAnEmptyOtherSide() throws Exception {
        var game = offer("0:0:p1:-:-:-");
        assertEquals("\u4ea4\u6613\u63d0\u8bae\uff1a1\u53f7\u73a9\u5bb6\u5c06\u6a61\u6728\u6797\u8f6c\u8ba9\u7ed92\u53f7\u73a9\u5bb6\u3002", summary(game, "zh_CN"));
        assertEquals("Trade offer: Player 1 transfers Oak Grove to Player 2.", summary(game, "en_US"));
    }

    @Test void unrelatedMortgagesDoNotTriggerATransferFeeReminder() throws Exception {
        var game = offer("350:0:-:p16:-:-");
        ((boolean[]) field(game, "mortgaged"))[1] = true;
        assertFalse(summary(game, "zh_CN").contains("\u62b5\u62bc"));
        assertFalse(summary(game, "en_US").contains("mortgag"));
    }

    @Test void transferredMortgageRemindersIdentifyEachRecipientAndOnlyTheirMortgagedProperties() throws Exception {
        var game = offer("100:20:p1,p3:p16:0:1");
        var mortgaged = (boolean[]) field(game, "mortgaged");
        mortgaged[1] = mortgaged[16] = true;
        String chinese = summary(game, "zh_CN");
        assertTrue(chinese.contains("2\u53f7\u73a9\u5bb6\u63a5\u6536\u7684\u6a61\u6728\u6797\u5df2\u62b5\u62bc"), chinese);
        assertTrue(chinese.contains("1\u53f7\u73a9\u5bb6\u63a5\u6536\u7684\u5357\u74dc\u519c\u573a\u5df2\u62b5\u62bc"), chinese);
        assertFalse(chinese.contains("\u6a61\u6728\u6797\u3001\u767d\u6866\u6797\u5df2\u62b5\u62bc"), chinese);
        assertTrue(chinese.contains("\u4e0d\u6536\u8f6c\u624b\u5229\u606f"), chinese);
        assertTrue(chinese.contains("\u8d4e\u56de\u8d39\u7528"), chinese);
        String english = summary(game, "en_US");
        assertTrue(english.contains("Player 2 receives mortgaged Oak Grove"), english);
        assertTrue(english.contains("Player 1 receives mortgaged Pumpkin Farm"), english);
    }

    @Test void noPendingTradeHasNoSummary() {
        assertEquals("", summary(new MonopolyGame(2, 31, Map.of()), "zh_CN"));
    }

    private String summary(MonopolyGame game, String locale) {
        assertTrue(Language.reload(directory, locale, message -> fail(message)));
        return MessageText.plain(ClassicText.tradeSummary(game));
    }

    @SuppressWarnings("unchecked")
    private static MonopolyGame offer(String assets) throws Exception {
        var game = new MonopolyGame(2, 31, Map.of());
        var owners = (int[]) field(game, "owners");
        owners[1] = owners[3] = 0;
        owners[16] = 1;
        var cards = (List<List<Integer>>) field(game, "jailCards");
        cards.get(0).add(0);
        cards.get(1).add(1);
        game.apply(0, "trade:1:" + assets + ":" + game.revision());
        return game;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

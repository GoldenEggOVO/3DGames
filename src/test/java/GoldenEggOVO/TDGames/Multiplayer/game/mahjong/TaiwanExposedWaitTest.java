package GoldenEggOVO.TDGames.Multiplayer.game.mahjong;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaiwanExposedWaitTest {
    @Test
    void fourCopiesInOwnKongDoNotCreateAnImpossibleSecondWait() {
        var hand = tiles("hand", "m2 m3 m4 p1 p1");
        var melds = List.of(
                new Meld(Meld.Kind.QUAD, tiles("kong", "m1 m1 m1 m1"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("first", "p4 p5 p6"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("second", "s1 s2 s3"), true, 2),
                new Meld(Meld.Kind.SEQUENCE, tiles("third", "s7 s8 s9"), true, 3));
        var context = new TaiwanRules.Context(
                Tiles.type("m4"), 27, false, false, false, false, false, false, List.of());

        var score = new TaiwanRules(Map.of()).score(hand, melds, context).orElseThrow();
        assertEquals(List.of("SINGLE_WAIT"), score.patterns());
        assertEquals(1, score.tai());
        assertTrue(new TaiwanRules(Map.of("minimum-tai", "1"))
                .score(hand, melds, context).isPresent());
    }

    @Test
    void threeCopiesInOwnPungStillLeaveTheFourthCopyAsASecondWait() {
        var hand = tiles("hand", "m2 m3 m4 p1 p1");
        var melds = List.of(
                new Meld(Meld.Kind.TRIPLET, tiles("pung", "m1 m1 m1"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("first", "p4 p5 p6"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("second", "s1 s2 s3"), true, 2),
                new Meld(Meld.Kind.SEQUENCE, tiles("third", "s7 s8 s9"), true, 3));
        var context = new TaiwanRules.Context(
                Tiles.type("m4"), 27, false, false, false, false, false, false, List.of());

        var score = new TaiwanRules(Map.of()).score(hand, melds, context).orElseThrow();
        assertFalse(score.patterns().contains("SINGLE_WAIT"));
        assertEquals(0, score.tai());
    }

    @Test
    void anImpossibleSequenceEndpointCannotScoreBothSingleWaitAndTwoSidedPingHu() {
        var hand = tiles("hand", "m2 m2 m3 m4 m5");
        var melds = List.of(
                new Meld(Meld.Kind.SEQUENCE, tiles("first", "m1 m2 m3"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("second", "m1 m2 m3"), true, 1),
                new Meld(Meld.Kind.SEQUENCE, tiles("third", "p4 p5 p6"), true, 2),
                new Meld(Meld.Kind.SEQUENCE, tiles("fourth", "s7 s8 s9"), true, 3));
        var context = new TaiwanRules.Context(
                Tiles.type("m5"), 27, false, false, false, false, false, false, List.of());

        var score = new TaiwanRules(Map.of()).score(hand, melds, context).orElseThrow();
        assertEquals(List.of("SINGLE_WAIT"), score.patterns());
        assertEquals(1, score.tai());
    }

    private static List<Tiles.Tile> tiles(String prefix, String faces) {
        List<Tiles.Tile> result = new ArrayList<>();
        for (String face : faces.split(" "))
            result.add(new Tiles.Tile(prefix + result.size(), Tiles.type(face), false));
        return List.copyOf(result);
    }
}

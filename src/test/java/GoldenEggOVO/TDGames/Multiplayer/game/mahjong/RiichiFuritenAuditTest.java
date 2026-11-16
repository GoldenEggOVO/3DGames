package GoldenEggOVO.TDGames.Multiplayer.game.mahjong;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiichiFuritenAuditTest {
    @Test
    void passingANoYakuCompletionBlocksALaterLastDiscardBeforeTheNextDraw() throws Exception {
        var game = game();
        setHand(game, 0, "z1");
        setHand(game, 1, "z1 m9");
        setHand(game, 2, "m1 m2 m3 m4 m5 m6 p2 p3 p4 s7 s8 s9 z1");
        setHand(game, 3, "");
        Wall wall = get(game, "wall");
        int front = get(wall, "front");
        set(wall, "back", front + 1);

        game.apply(0, "discard:s0_0");
        assertEquals(1, game.currentPlayer());
        assertEquals("TURN", game.publicInfo().get("phase"));
        assertEquals(0, game.deckSize());
        game.apply(1, "discard:s1_0");

        assertNotEquals("RON", game.publicInfo().get("phase"));
        assertTrue(game.legalActions(2).isEmpty());
    }

    @Test
    void claimingAPonClearsTemporaryFuritenBeforeTheNextDraw() throws Exception {
        assertCallClearsFuriten(
                "m1 m2 m2 m2 m3 m3 m4 m4 m6 m7 m8 m9 m9",
                "m3", "m2", "pon", "m1", "m5");
    }

    @Test
    void claimingAChiClearsTemporaryFuritenBeforeTheNextDraw() throws Exception {
        assertCallClearsFuriten(
                "m1 m1 m1 m2 m3 m4 m6 m6 m7 m8 m8 m8 m9",
                "m6", "m1", "chi", "m6", "m5");
    }

    private static void assertCallClearsFuriten(
            String hand, String passed, String called, String kind, String discard, String winning)
            throws Exception {
        var game = game();
        setHand(game, 0, called);
        setHand(game, 1, hand);
        setHand(game, 2, winning);
        setHand(game, 3, passed);
        set(game, "current", 3);

        game.apply(3, "discard:s3_0");
        assertEquals("RON", game.publicInfo().get("phase"));
        assertEquals(1, game.currentPlayer());
        game.apply(1, "pass");
        while (!game.publicInfo().get("phase").equals("TURN"))
            game.apply(game.currentPlayer(), "pass");
        assertEquals(0, game.currentPlayer());
        game.apply(0, "discard:s0_0");
        if (kind.equals("chi"))
            while (game.publicInfo().get("phase").equals("CALL"))
                game.apply(game.currentPlayer(), "pass");
        String claim = game.legalActions(1).stream()
                .filter(action -> action.startsWith(kind + ":")).findFirst().orElseThrow();
        game.apply(1, claim);
        String discardedId = game.hand(1).stream()
                .filter(tile -> tile.face().equals(discard)).findFirst().orElseThrow().id();
        game.apply(1, "discard:" + discardedId);
        assertEquals(2, game.currentPlayer());
        game.apply(2, "discard:s2_0");

        assertEquals("RON", game.publicInfo().get("phase"));
        assertEquals(1, game.currentPlayer());
        assertTrue(game.legalActions(1).contains("ron"));
    }

    private static MahjongGame game() throws Exception {
        var game = new MahjongGame(4, 0, Map.of("profile", "riichi", "rounds", "1"));
        set(game, "anyCalls", true);
        set(game, "discardCount", 8);
        return game;
    }

    private static void setHand(MahjongGame game, int seat, String faces) throws Exception {
        List<List<Tiles.Tile>> hands = get(game, "hands");
        hands.get(seat).clear();
        for (String face : faces.split(" "))
            if (!face.isEmpty()) hands.get(seat).add(new Tiles.Tile(
                    "s" + seat + "_" + hands.get(seat).size(), Tiles.type(face), false));
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}

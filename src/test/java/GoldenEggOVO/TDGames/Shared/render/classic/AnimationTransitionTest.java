package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.mancala.MancalaGame;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Multiplayer.game.shogi.ShogiGame;
import GoldenEggOVO.TDGames.Singleplayer.game.klondike.KlondikeGame;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import com.google.gson.JsonPrimitive;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimationTransitionTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void optionalShogiPromotionKeepsTheCommittedMovesOriginalSquare() throws Exception {
        for (boolean mini : List.of(false, true)) for (boolean packed : List.of(false, true)) {
            var game = new ShogiGame(2, 0, Map.of(), mini);
            int[][] board = field(game, "board"); for (int[] row : board) Arrays.fill(row, 0);
            int n = game.boardSize(); board[0][0] = 8; board[n - 1][n - 1] = -8;
            int y = mini ? 3 : 5; board[y][1] = mini ? 7 : 1;
            String source = "b" + (y + 1), target = "b" + (y + 2);
            var f = new ClassicTableTest.Fixture(game, game.id());
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
                ClassicLayout layout = field(view, "layout");
                String move = "move:" + source + ":" + target;
                game.apply(0, move); f.room.event(0, new JsonPrimitive(move)); SessionTestSupport.bump(f.room); f.table.sync();
                assertTrue(game.pendingPromotion());
                game.apply(0, "promote:yes"); f.room.event(0, new JsonPrimitive("promote:yes")); SessionTestSupport.bump(f.room); f.table.sync();
                List<?> motions = field(view, "motions"); assertEquals(1, motions.size());
                List<org.bukkit.util.Vector> route = field(motions.getFirst(), "route");
                assertEquals(layout.slot(source).renderZ() - layout.slot(target).renderZ(), route.getFirst().getZ(), 1e-8,
                        "Choosing promotion must not replace the board move with an in-place hop");
            } finally { f.table.close(); }
        }
    }

    @Test void monopolySpecialMovementDoesNotPretendTheNetDistanceWasTheDiceRoll() throws Exception {
        for (long seed : new long[] {11, 13, 16}) {
            var game = new MonopolyGame(2, seed, Map.of()); var f = new ClassicTableTest.Fixture(game, "monopoly");
            try {
                game.apply(0, "roll"); f.room.event(0, new JsonPrimitive("roll")); SessionTestSupport.bump(f.room); f.table.sync();
                int rolled = Arrays.stream(game.publicInfo().get("dice").split(",")).mapToInt(Integer::parseInt).sum();
                assertNotEquals(rolled, game.players().getFirst().position(), "Fixture must include a movement-card or jail effect");
                Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
                List<?> motions = field(view, "motions"); assertFalse(motions.isEmpty());
                assertEquals(2, AnimationTransitionTest.<List<?>>field(motions.getFirst(), "route").size(),
                        "A special relocation should travel directly to the authoritative square");
            } finally { f.table.close(); }
        }
    }

    @Test void drawingInKlondikeAnimatesOnlyTheNewWasteCardInEitherMode() throws Exception {
        for (boolean packed : List.of(false, true)) {
            var game = new KlondikeGame(1, 31, Map.of("draw", "1"));
            var f = new ClassicTableTest.Fixture(game, "klondike");
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
                var before = game.cells();
                game.apply(0, "draw"); f.room.event(0, new JsonPrimitive("draw")); SessionTestSupport.bump(f.room);
                f.table.sync();
                List<?> motions = field(view, "motions");
                assertEquals(1, motions.size(), "Only the freshly drawn card should leave stock");
                Map<String, ?> visuals = field(view, "visuals");
                Map<Entity, ?> targets = field(motions.getFirst(), "targets");
                for (var cell : before) if (!cell.piece().isEmpty() && !cell.piece().equals("back") && game.cells().contains(cell)) {
                    List<Entity> parts = field(visuals.get(cell.id()), "parts");
                    assertTrue(parts.stream().noneMatch(targets::containsKey), "Unchanged card " + cell.id() + " must remain still");
                }
            } finally { f.table.close(); }
        }
    }

    @Test void finalMancalaSowAndSweepSurviveTheSessionFinishNotification() throws Exception {
        var game = new MancalaGame(2, 0, Map.of());
        int[] pits = field(game, "pits"); Arrays.fill(pits, 0);
        pits[5] = 1; pits[6] = 23; pits[12] = 2; pits[13] = 22;
        var f = new ClassicTableTest.Fixture(game, "mancala");
        try {
            game.apply(0, "sow:5"); f.room.event(0, new JsonPrimitive("sow:5")); SessionTestSupport.bump(f.room);
            f.table.sync();
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            assertTrue(game.finished());
            var finalInfo = game.publicInfo();
            assertEquals(2, AnimationTransitionTest.<List<?>>field(view, "sowing").size());
            SessionTestSupport.set(f.room, "phase", GameSession.Phase.FINISHED); SessionTestSupport.bump(f.room);
            f.table.tick();
            assertFalse(AnimationTransitionTest.<List<?>>field(view, "sowing").isEmpty(), "Finalization must not cancel the last move before it plays");
            int frames = 1;
            while (!AnimationTransitionTest.<List<?>>field(view, "sowing").isEmpty()) {
                assertTrue(frames++ < 30, "The final sweep must finish promptly"); f.table.tick();
            }
            assertEquals(finalInfo, game.publicInfo(), "Animation must not change the rule result");
            assertTrue(AnimationTransitionTest.<List<?>>field(view, "motions").isEmpty());
        } finally { f.table.close(); }
    }

    @Test void fourSeedSowFinishesWithinTwelveFramesIncludingTheVisibleLandings() throws Exception {
        var game = new MancalaGame(2, 0, Map.of());
        var f = new ClassicTableTest.Fixture(game, "mancala");
        try {
            game.apply(0, "sow:0"); f.room.event(0, new JsonPrimitive("sow:0")); SessionTestSupport.bump(f.room);
            f.table.sync();
            Object view = ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId());
            int frames = 0;
            while (!AnimationTransitionTest.<List<?>>field(view, "sowing").isEmpty()) {
                assertTrue(++frames <= 12, "Ordinary four-seed sowing must not lock interaction for 2.4 seconds");
                f.table.tick();
            }
            assertEquals(12, frames);
            assertTrue(AnimationTransitionTest.<List<?>>field(view, "motions").isEmpty());
        } finally { f.table.close(); }
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}

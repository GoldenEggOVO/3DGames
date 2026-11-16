package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Singleplayer.game.twentyfortyeight.TwentyFortyEightGame;
import com.google.gson.JsonPrimitive;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TileSlideTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest @ValueSource(strings = {"left", "right", "up", "down"})
    void originalValuesReachBothMergeTargetsBeforeFinalValuesAndBirthAreShown(String direction) throws Exception {
        for (boolean packed : List.of(false, true)) {
            var game = game(direction);
            var f = new ClassicTableTest.Fixture(game, "2048");
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Object view = view(f);
                Map<String, ?> before = visuals(view);
                var donors = new ArrayList<List<Entity>>();
                var ids = new ArrayList<String>();
                for (Cell cell : game.cells()) if (cell.piece().equals("2")) {
                    ids.add(cell.id()); donors.add(parts(before.get(cell.id())));
                }
                clearInvocations(f.owner);
                commit(f, game, "slide:" + direction);
                assertEquals("8", game.publicInfo().get("score"), "The rule result must commit immediately");
                var info = game.publicInfo();
                List<Entity> finals = finalParts(view);
                for (var donor : donors) for (var part : donor) verify(part, never()).remove();
                for (var part : finals) verify(f.owner, never()).showEntity(f.plugin, part);
                for (int frame = 0; frame < 6; frame++) f.table.tick();
                ClassicLayout layout = field(view, "layout");
                for (int index = 0; index < ids.size(); index++) {
                    String[] coordinates = ids.get(index).split(",");
                    int source = Integer.parseInt(coordinates[direction.equals("left") || direction.equals("right") ? 0 : 1]);
                    int target = direction.equals("right") || direction.equals("down") ? 3 - (3 - source) / 2 : source / 2;
                    var slot = layout.slot(direction.equals("left") || direction.equals("right") ? target + ",0" : "0," + target);
                    Entity block = donors.get(index).stream().filter(BlockDisplay.class::isInstance).findFirst().orElseThrow();
                    var positions = ArgumentCaptor.forClass(Location.class); verify(block, atLeastOnce()).teleport(positions.capture());
                    Location end = positions.getAllValues().getLast();
                    assertEquals(f.origin.getX() + slot.x(), end.getX(), 1e-7);
                    assertEquals(f.origin.getZ() + slot.renderZ(), end.getZ(), 1e-7);
                    verify(block, never()).remove();
                }
                for (var part : finals) verify(f.owner, never()).showEntity(f.plugin, part);
                f.table.tick();
                for (var donor : donors) for (var part : donor) verify(part).remove();
                for (var part : finals) { verify(f.owner).showEntity(f.plugin, part); verify(part, never()).remove(); }
                assertEquals(info, game.publicInfo(), "Presentation must not resample or apply a second move");
            } finally { f.table.close(); }
        }
    }

    @Test void birthInAnOldDonorSquareIsHiddenUntilHandoffAndThenSurvives() throws Exception {
        var game = game("left"); var f = new ClassicTableTest.Fixture(game, "2048");
        try {
            Object view = view(f); List<Entity> source = parts(visuals(view).get("2,0"));
            clearInvocations(f.owner); commit(f, game, "slide:left");
            assertEquals("2", game.cells().stream().filter(c -> c.id().equals("2,0")).findFirst().orElseThrow().piece(),
                    "This fixed seed must reuse a departed donor square");
            List<Entity> birth = parts(visuals(view).get("2,0"));
            assertTrue(birth.stream().noneMatch(source::contains), "The new tile must not reuse the departing donor identity");
            for (int frame = 0; frame < 6; frame++) f.table.tick();
            for (Entity part : birth) verify(f.owner, never()).showEntity(f.plugin, part);
            f.table.tick();
            for (Entity part : source) verify(part).remove();
            for (Entity part : birth) { verify(f.owner).showEntity(f.plugin, part); verify(part, never()).remove(); }
        } finally { f.table.close(); }
    }

    @Test void finishingKeepsTheLastSlideButPauseAndCloseCleanUpItsDonors() throws Exception {
        for (String interruption : List.of("finish", "pause", "close")) for (int frame : List.of(0, 3, 6)) {
            var game = game("left"); var f = new ClassicTableTest.Fixture(game, "2048");
            try {
                Object view = view(f); List<Entity> old = new ArrayList<>();
                for (Cell cell : game.cells()) if (cell.piece().equals("2")) old.addAll(parts(visuals(view).get(cell.id())));
                commit(f, game, "slide:left");
                for (int i = 0; i < frame; i++) f.table.tick();
                if (interruption.equals("close")) f.table.close();
                else {
                    if (interruption.equals("finish")) f.room.finish("winner:0"); else f.room.pause("error.economy.review");
                    f.table.sync();
                }
                if (interruption.equals("finish")) for (Entity part : old) verify(part, never()).remove();
                for (int i = 0; i < 8; i++) f.table.tick();
                for (Entity part : old) verify(part).remove();
                if (!interruption.equals("close")) for (Entity part : finalParts(view)) verify(f.owner, atLeastOnce()).showEntity(f.plugin, part);
            } finally { f.table.close(); }
        }
    }

    @Test void aRapidSecondSlideCleansTheFirstAnimationAndRevealsTheLatestBoard() throws Exception {
        var game = game("left");
        var f = new ClassicTableTest.Fixture(game, "2048");
        try {
            Object view = view(f);
            List<Entity> original = new ArrayList<>();
            for (Cell cell : game.cells()) if (cell.piece().equals("2")) original.addAll(parts(visuals(view).get(cell.id())));
            commit(f, game, "slide:left");
            String next = game.legalActions(0).stream().filter(a -> a.startsWith("slide:") && !a.equals("slide:left")).findFirst().orElseThrow();
            commit(f, game, next);
            for (int i = 0; i < 9; i++) f.table.tick();
            assertFalse(f.table.moving());
            for (Entity part : original) verify(part, atLeastOnce()).remove();
            for (Entity part : finalParts(view)) verify(f.owner, atLeastOnce()).showEntity(f.plugin, part);
        } finally { f.table.close(); }
    }

    @Test void failedFinalTileCreationKeepsDonorsOwnedForCleanup() throws Exception {
        var game = game("left"); var f = new ClassicTableTest.Fixture(game, "2048");
        Object view = view(f); List<Entity> donors = new ArrayList<>();
        for (Cell cell : game.cells()) if (cell.piece().equals("2")) donors.addAll(parts(visuals(view).get(cell.id())));
        try {
            doThrow(new IllegalStateException("Injected spawn failure")).when(f.world)
                    .spawn(any(Location.class), any(Class.class), any(java.util.function.Consumer.class));
            assertThrows(IllegalStateException.class, () -> commit(f, game, "slide:left"));
            assertFalse(this.<Boolean>field(view, "hidingTiles"), "A failed build must restore normal visibility");
        } finally { f.table.close(); }
        for (Entity donor : donors) verify(donor).remove();
    }

    private TwentyFortyEightGame game(String direction) throws Exception {
        var game = new TwentyFortyEightGame(1, 1, Map.of()); int[] board = field(game, "board"); Arrays.fill(board, 0);
        for (int i = 0; i < 4; i++) board[direction.equals("left") || direction.equals("right") ? i : i * 4] = 2;
        return game;
    }
    private void commit(ClassicTableTest.Fixture f, TwentyFortyEightGame game, String action) {
        game.apply(0, action); f.room.event(0, new JsonPrimitive(action)); SessionTestSupport.bump(f.room); f.table.sync();
    }
    private Object view(ClassicTableTest.Fixture f) throws Exception { return ((Map<?, ?>) field(f.table, "views")).get(f.owner.getUniqueId()); }
    private Map<String, ?> visuals(Object view) throws Exception { return field(view, "visuals"); }
    private List<Entity> parts(Object visual) throws Exception { return field(visual, "parts"); }
    private List<Entity> finalParts(Object view) throws Exception {
        var parts = new ArrayList<Entity>(); for (Object visual : visuals(view).values()) parts.addAll(parts(visual)); return parts;
    }
    @SuppressWarnings("unchecked") private <T> T field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}

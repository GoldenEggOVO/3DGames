package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.mancala.MancalaGame;
import GoldenEggOVO.TDGames.SessionTestSupport;
import com.google.gson.JsonPrimitive;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SowingContactSoundTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void eachSeedLandingIsHeardOnceByEachViewerAndStopsOnInterruption() {
        for (boolean interrupt : List.of(false, true)) {
            var game = new MancalaGame(2, 1, Map.of());
            var f = new ClassicTableTest.Fixture(game, "mancala");
            when(f.plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(f.pack.packed(f.owner)).thenReturn(true); f.table.sync();
            var before = game.cells(); game.apply(0, "sow:0");
            var steps = MancalaSowing.plan(before, game.cells(), "sow:0");
            assertEquals(4, steps.size());
            f.room.event(0, new JsonPrimitive("sow:0")); SessionTestSupport.bump(f.room); f.table.sync();
            clearInvocations(f.owner, f.observer, f.world);
            try {
                for (int frame = 1; frame <= 3 * steps.size() + 6; frame++) {
                    if (interrupt && frame == 3) { f.room.pause("error.economy.review"); f.table.sync(); }
                    f.table.tick();
                    int expected = frame % 3 == 0 && frame <= 3 * steps.size() && !interrupt ? 1 : 0;
                    for (var player : List.of(f.owner, f.observer)) {
                        var calls = mockingDetails(player).getInvocations().stream().filter(call -> call.getMethod().getName().equals("playSound")).toList();
                        assertEquals(expected, calls.size(), "One personal cue per physical seed landing at frame " + frame);
                        if (expected == 1) {
                            var call = calls.getFirst();
                            assertEquals(player == f.owner
                                    ? frame == 3 * steps.size() ? "3dgames:board.sow.last" : "3dgames:board.glass"
                                    : Sound.BLOCK_GLASS_HIT, call.getArgument(1));
                            assertTrue((float) call.getArgument(3) <= .14f, "Repeated sowing must remain quiet");
                        }
                    }
                    assertTrue(mockingDetails(f.world).getInvocations().stream().noneMatch(call -> call.getMethod().getName().equals("playSound")));
                    clearInvocations(f.owner, f.observer, f.world);
                }
            } finally { f.table.close(); }
        }
    }
}

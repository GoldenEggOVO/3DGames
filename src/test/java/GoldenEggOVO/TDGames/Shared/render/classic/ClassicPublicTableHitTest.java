package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.interaction.TableAccessController;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.TablePlacement;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassicPublicTableHitTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void publicJoinAcceptsTheVisibleCornersOfEveryWideClassicTable() {
        for (String kind : List.of("monopoly", "battleship", "hex", "mancala", "backgammon")) {
            var f = fixture(kind);
            try {
                for (int sideX : List.of(-1, 1)) for (int sideZ : List.of(-1, 1)) {
                    double x = sideX * (ClassicLayout.tableHalfWidth(kind) - .02);
                    double z = sideZ * (ClassicLayout.tableHalfDepth(kind) - .02);
                    assertMenuParity(f, x, z, true);
                }
            } finally {
                f.table.close();
            }
        }
    }

    @Test
    void publicJoinAcceptsBothRealDiceSideTableEdges() {
        for (String kind : List.of("monopoly", "backgammon")) {
            var f = fixture(kind);
            try {
                double x = TablePlacement.sideTrayX(ClassicLayout.tableHalfWidth(kind));
                for (int sideX : List.of(-1, 1)) for (int sideZ : List.of(-1, 1))
                    assertMenuParity(f, x + sideX * .68, sideZ * .68, true);
                assertMenuParity(f, x + .73, 0, false);
                assertMenuParity(f, x, .73, false);
            } finally {
                f.table.close();
            }
        }
    }

    @Test
    void publicJoinRejectsAirBeyondTheActualTableAndTheMancalaShortEdge() {
        for (String kind : List.of("monopoly", "battleship", "hex", "mancala", "backgammon")) {
            var f = fixture(kind);
            try {
                assertMenuParity(f, 0, ClassicLayout.tableHalfDepth(kind) + .05, false);
                assertMenuParity(f, -ClassicLayout.tableHalfWidth(kind) - .05, 0, false);
                if (kind.equals("mancala")) assertMenuParity(f, 0, 1.10, false);
            } finally {
                f.table.close();
            }
        }
    }

    @Test
    void classicPublicTargetsStillRespectWorldReachAndSolidOcclusion() {
        var f = fixture("monopoly");
        try {
            var entry = entry(f);
            assertEquals(Double.POSITIVE_INFINITY, TableAccessController.hit(entry,
                    new Location(mock(World.class), 0, f.origin.getY() + 2, 0), new Vector(0, -1, 0)));
            assertEquals(Double.POSITIVE_INFINITY, TableAccessController.hit(entry,
                    f.origin.clone().add(0, 8, 0), new Vector(0, -1, 0)));
            when(f.world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
                    .thenReturn(new RayTraceResult(f.origin.clone().add(0, .5, 0).toVector()));
            assertMenuParity(f, 0, 0, false);
        } finally {
            f.table.close();
        }
    }

    private ClassicTableTest.Fixture fixture(String kind) {
        return new ClassicTableTest.Fixture((ClassicGame) GameFactory.create(kind, 2, 7L), kind);
    }

    private TableAccessController.Entry entry(ClassicTableTest.Fixture f) {
        return new TableAccessController.Entry("audit", f.origin.clone().subtract(0, TableGeometry.SURFACE, 0),
                f.room.kind, f.room.sideTray, GameSession.Phase.LOBBY, List.of(), f.room.capacity,
                Set.of(), player -> { }, player -> { });
    }

    private void assertMenuParity(ClassicTableTest.Fixture f, double x, double z, boolean expected) {
        Location eye = f.origin.clone().add(x, 2, z);
        Vector down = new Vector(0, -1, 0);
        assertEquals(expected, Double.isFinite(f.table.menuHit(eye, down)),
                f.room.kind + " seated reference at " + x + "," + z);
        assertEquals(expected, Double.isFinite(TableAccessController.hit(entry(f), eye, down)),
                f.room.kind + " public join must match the real table at " + x + "," + z);
    }
}

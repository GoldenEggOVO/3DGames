package GoldenEggOVO.TDGames.Shared.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicPlacementTest {
    @Test void wideBoardsReserveTheActualPositionOfTheirDiceSideTable() {
        assertFalse(TablePlacement.overlaps(0,80,0,false,1.875,1.875,4,80,0,false,1.125,1.125));
        assertTrue(TablePlacement.overlaps(0,80,0,true,1.875,1.875,4,80,0,false,1.125,1.125));
        assertTrue(TablePlacement.overlaps(4,80,0,false,1.125,1.125,0,80,0,true,1.875,1.875));
        assertFalse(TablePlacement.overlaps(0,80,0,true,1.875,1.875,5,80,0,false,1.125,1.125));
    }
    @Test void largeSquareTablesReserveTheirFullExtentOnBothAxes() {
        assertTrue(TablePlacement.overlaps(0, 80, 0, false, 1.875, 0, 80, 3, false, 1.875));
        assertTrue(TablePlacement.overlaps(0, 80, 0, false, 1.875, 3, 80, 0, false, 1.875));
        assertFalse(TablePlacement.overlaps(0, 80, 0, false, 1.875, 0, 80, 4, false, 1.875));
        assertFalse(TablePlacement.overlaps(0, 80, 0, false, 1.875, 4, 80, 0, false, 1.875));
    }
}

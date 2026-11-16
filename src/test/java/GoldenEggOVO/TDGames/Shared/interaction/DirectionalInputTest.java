package GoldenEggOVO.TDGames.Shared.interaction;

import org.bukkit.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectionalInputTest {
    private Input keys(boolean up, boolean down, boolean left, boolean right) {
        Input input = mock(Input.class);
        when(input.isForward()).thenReturn(up); when(input.isBackward()).thenReturn(down);
        when(input.isLeft()).thenReturn(left); when(input.isRight()).thenReturn(right);
        return input;
    }

    @Test void eachPressMovesOnceAndReleaseRearmsTheDirection() {
        var input = new DirectionalInput();
        assertEquals("up", input.press(keys(true, false, false, false)));
        assertNull(input.press(keys(true, false, false, false)));
        assertNull(input.press(keys(false, false, false, false)));
        assertEquals("up", input.press(keys(true, false, false, false)));
        assertEquals("left", input.press(keys(false, false, true, false)));
        assertEquals("down", input.press(keys(false, true, false, false)));
        assertEquals("right", input.press(keys(false, false, false, true)));
    }

    @Test void diagonalAndOppositeKeysNeverChooseAnArbitraryDirection() {
        var input = new DirectionalInput();
        assertNull(input.press(keys(true, false, true, false)));
        assertEquals("up", input.press(keys(true, false, false, false)));
        input.press(keys(false, false, false, false));
        assertNull(input.press(keys(true, true, false, false)));
        assertEquals("down", input.press(keys(false, true, false, false)));
        assertNull(input.press(keys(false, true, false, false)));
    }

}

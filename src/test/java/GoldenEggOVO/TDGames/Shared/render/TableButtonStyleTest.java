package GoldenEggOVO.TDGames.Shared.render;

import org.bukkit.Color;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class TableButtonStyleTest {
    @org.junit.jupiter.api.BeforeEach void setup() { org.mockbukkit.mockbukkit.MockBukkit.mock(); }
    @org.junit.jupiter.api.AfterEach void close() { org.mockbukkit.mockbukkit.MockBukkit.unmock(); }
    @Test void hoverOutlinesTheWholeButtonAndClearsEveryPartOnExit() {
        var edge = mock(BlockDisplay.class);
        var face = mock(BlockDisplay.class);
        var label = mock(TextDisplay.class);
        var model = mock(ItemDisplay.class);
        var parts = List.of(edge, face, model, label);
        TableButtonStyle.hover(parts, true);
        verify(edge).setGlowColorOverride(Color.YELLOW);
        verify(edge).setGlowing(true);
        verify(face).setGlowColorOverride(Color.YELLOW);
        verify(face).setGlowing(true);
        verify(model).setGlowColorOverride(Color.YELLOW);
        verify(model).setGlowing(true);
        verifyNoInteractions(label);
        TableButtonStyle.hover(parts, false);
        verify(edge).setGlowing(false);
        verify(face).setGlowing(false);
        verify(model).setGlowing(false);
    }
}

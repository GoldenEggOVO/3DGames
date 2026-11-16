package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import net.kyori.adventure.text.Component;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DialogSettingsTest {
    @Test
    void settingsWithoutResourceToggleExplainWhyNoOptionsAreAvailable() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.settings(f.player);
        assertEquals(List.of("back"), f.buttons.stream().map(GameMenus.Button::id).toList());
        assertFalse(MessageText.plain(f.description).isBlank());
    }

    @Test
    void settingsKeepResourceToggleWithAndWithoutThePack() throws Exception {
        var f = new MenuFlowTest.Fixture();
        var pack = mock(ResourcePackService.class);
        SessionTestSupport.module(f.plugin, "pack", pack);
        when(pack.hasToggle()).thenReturn(true);
        when(pack.button(f.player)).thenReturn(Component.text("Resource pack"));
        for (boolean packed : List.of(false, true)) {
            when(pack.packed(f.player)).thenReturn(packed);
            f.menus.settings(f.player);
            assertEquals(List.of("resource-pack", "back"), f.buttons.stream().map(GameMenus.Button::id).toList());
            assertTrue(MessageText.plain(f.description).isBlank());
        }
        f.buttons.getFirst().action().run();
        verify(pack).toggle(f.player);
    }
}

package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DialogMenusTest {
    @Test
    void resourcePackDoesNotChangeNativeDialogLayout() {
        MockBukkit.mock();
        try (var bodies = mockStatic(io.papermc.paper.registry.data.dialog.body.DialogBody.class);
             var bases = mockStatic(DialogBase.class);
             var actions = mockStatic(DialogAction.class);
             var buttons = mockStatic(ActionButton.class);
             var types = mockStatic(DialogType.class);
             var dialogs = mockStatic(Dialog.class)) {
            bodies.when(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(any(), anyInt()))
                    .thenReturn(mock(io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody.class));
            var nativeTitle = new AtomicReference<Component>();
            bases.when(() -> DialogBase.create(any(), isNull(), anyBoolean(), anyBoolean(),
                    any(), anyList(), anyList())).thenAnswer(call -> {
                        nativeTitle.set(call.getArgument(0));
                        return null;
                    });
            var plugin = mock(TDGamesPlugin.class);
            var pack = mock(GoldenEggOVO.TDGames.Core.pack.ResourcePackService.class);
            var player = mock(Player.class);
            var world = mock(World.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.pack()).thenReturn(pack);
            when(pack.packed(player)).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            var title = Component.text("Games");
            var exit = new MenuView.Button(Component.text("Close"), null, 170, "close");
            DialogMenus.open(plugin, player, new MenuView(title, List.of(), List.of(), List.of(), exit, 1),
                    (action, values) -> {});
            assertEquals(title, nativeTitle.get());
        } finally {
            MockBukkit.unmock();
        }
    }
    @Test
    void disabledMenusDoNotOpenADialog() {
        var plugin = mock(TDGamesPlugin.class);
        var config = new YamlConfiguration();
        config.set("menu-enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        var player = mock(Player.class);
        assertDoesNotThrow(() -> DialogMenus.open(plugin, player, null, (action, values) -> {}));
        verify(player, never()).showDialog(any());
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void exitButtonsCloseTheCurrentScreenBeforeServerNavigation() {
        MockBukkit.mock();
        try (var bases = mockStatic(DialogBase.class);
             var actions = mockStatic(DialogAction.class);
             var buttons = mockStatic(ActionButton.class);
             var types = mockStatic(DialogType.class);
             var dialogs = mockStatic(Dialog.class)) {
            var afterAction = new AtomicReference<DialogBase.DialogAfterAction>();
            bases.when(() -> DialogBase.create(any(), isNull(), anyBoolean(), anyBoolean(),
                    any(), anyList(), anyList())).thenAnswer(call -> {
                        afterAction.set(call.getArgument(4));
                        return null;
                    });
            var plugin = mock(TDGamesPlugin.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            var exit = new MenuView.Button(Component.text("Back"), null, 170, "back");
            DialogMenus.open(plugin, player,
                    new MenuView(Component.text("Casino"), List.of(), List.of(), List.of(), exit, 1),
                    (action, values) -> {});
            assertEquals(DialogBase.DialogAfterAction.CLOSE, afterAction.get());
        } finally {
            MockBukkit.unmock();
        }
    }
}

package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarmDialogTest {
    @Test
    void soloResumeAppearsAboveTheSingleplayerGames() {
        try (var f = new Fixture(false)) {
            f.open("tabletop", 2, List.of(choice("2048"), choice("klondike"), choice("solo-resume")), List.of());
            assertEquals("[solo-resume]", MessageText.plain(f.body.getFirst()));
            assertNotNull(f.body.getFirst().clickEvent());
            assertEquals(List.of("[2048]", "[klondike]", "[close]"),
                    f.nativeButtons.stream().map(button -> MessageText.plain(button.label())).toList());
            f.textCallbacks.getFirst().accept(f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertEquals(List.of("solo-resume"), f.dispatched);
        }
    }

    @Test
    void singleplayerLayoutKeepsSoloResumeAboveItsGames() {
        var choices = List.of(
                new GameMenus.Button("2048", "2048", () -> {}),
                new GameMenus.Button("klondike", "Klondike", () -> {}),
                new GameMenus.Button("solo-resume", "Continue 2048", () -> {}),
                new GameMenus.Button("close", "Close", () -> {}));
        var layout = GameMenuWindow.layout("tabletop", Component.text("Games"), Component.empty(), choices, UUID.randomUUID());
        assertEquals(List.of("Continue 2048"), layout.headers().stream()
                .map(control -> MessageText.plain(control.caption())).toList());
        assertEquals(List.of("2048", "Klondike"), layout.controls().stream()
                .map(control -> MessageText.plain(control.caption())).toList());
    }

    @Test
    void expiredDialogExplainsWhyTheActionWasNotExecuted() {
        try (var f = new Fixture(false)) {
            var view = new MenuView(Component.text("Menu"), List.of(), List.of(),
                    List.of(choice("save")), choice("close"), 1, "actions", System.currentTimeMillis() - 1);
            DialogMenus.open(f.plugin, f.player, view, (action, values) -> f.dispatched.add(action));
            f.callbacks.getFirst().accept(mock(io.papermc.paper.dialog.DialogResponseView.class), f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertTrue(f.dispatched.isEmpty());
            verify(f.player).sendMessage(GoldenEggOVO.TDGames.Core.text.Language.component("menu.expired"));
        }
    }

    @Test
    void closingAnExpiredDialogDoesNotReopenOrWarn() {
        try (var f = new Fixture(false)) {
            var view = new MenuView(Component.text("Menu"), List.of(), List.of(),
                    List.of(choice("save")), choice("close"), 1, "actions", System.currentTimeMillis() - 1);
            DialogMenus.open(f.plugin, f.player, view, (action, values) -> f.dispatched.add(action), f.dispatched::add);
            f.callbacks.getLast().accept(mock(io.papermc.paper.dialog.DialogResponseView.class), f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertTrue(f.dispatched.isEmpty());
            verify(f.player, never()).sendMessage(any(Component.class));
        }
    }
    @Test
    void amountPagesKeepReturnOnlyInTheFooter() {
        try (var f = new Fixture(false)) {
            var view = new MenuView(Component.text("Amount"),
                    List.of(new MenuView.Body(Component.text("Introduction"), 300)),
                    List.of(new MenuView.Input("amount", Component.text("Amount"), "1", 16)),
                    List.of(choice("save")), choice("back"), 1);
            DialogMenus.open(f.plugin, f.player, view, (action, values) -> f.dispatched.add(action));
            assertEquals(List.of("Introduction"), f.body.stream().map(MessageText::plain).toList());
            assertEquals(List.of("body", "input"), f.contentOrder);
            assertTrue(f.textCallbacks.isEmpty());
            assertEquals(List.of("[save]", "[Back]"), f.nativeButtons.stream().map(b -> MessageText.plain(b.label())).toList());
            f.callbacks.getLast().accept(mock(io.papermc.paper.dialog.DialogResponseView.class), f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertEquals(List.of("back"), f.dispatched);
        }
    }

    @Test
    void aSingleSubmitMustNotBecomeTheEscapeAction() {
        try (var f = new Fixture(true)) {
            f.open("actions", 1, List.of(choice("save")),
                    List.of(new MenuView.Input("amount", Component.text("Amount"), "1", 16)));
            assertEquals("multi-action", f.layout, "Notice would submit Save when Escape is pressed");
            assertEquals(2, f.nativeButtons.size(), "Submit remains in the body, with a safe Close exit");
            assertEquals(2, f.callbacks.size());
            f.callbacks.getLast().accept(mock(io.papermc.paper.dialog.DialogResponseView.class), f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertEquals(List.of("close"), f.dispatched);
        }
    }

    @Test
    void nativeCallbacksPreserveAmountAndRejectOtherPlayersAndRepeatedActions() {
        try (var f = new Fixture(true)) {
            f.open("actions", 1, List.of(choice("save")), List.of(new MenuView.Input("amount", Component.text("Amount"), "1", 16)));
            var response = mock(io.papermc.paper.dialog.DialogResponseView.class);
            when(response.getText("amount")).thenReturn("12.34");
            var other = mock(Player.class);
            when(other.getUniqueId()).thenReturn(UUID.randomUUID());
            f.callbacks.getFirst().accept(response, other);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertTrue(f.dispatched.isEmpty());
            f.callbacks.getFirst().accept(response, f.player);
            f.callbacks.getFirst().accept(response, f.player);
            MockBukkit.getMock().getScheduler().performOneTick();
            assertEquals(List.of("save"), f.dispatched);
            assertEquals(Map.of("amount", "12.34"), f.values);
        }
    }


    private static MenuView.Button choice(String key) {
        return new MenuView.Button(Component.text(key.equals("back") ? "Back" : key), Component.text("Rules for " + key), 330, key, key);
    }

    private record NativeButton(Component label, Component tooltip, int width, DialogAction action) {}

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<DialogBase> bases;
        final MockedStatic<DialogAction> actions;
        final MockedStatic<ActionButton> buttons;
        final MockedStatic<Dialog> dialogs;
        final MockedStatic<DialogType> types;
        final MockedStatic<ClickEvent> clicks;
        final MockedStatic<DialogBody> bodies;
        final MockedStatic<DialogInput> inputs;
        final TDGamesPlugin plugin = mock(TDGamesPlugin.class);
        final Player player = mock(Player.class);
        final List<NativeButton> nativeButtons = new ArrayList<>();
        final List<DialogActionCallback> callbacks = new ArrayList<>();
        final List<ClickCallback<net.kyori.adventure.audience.Audience>> textCallbacks = new ArrayList<>();
        final List<Component> body = new ArrayList<>();
        final List<Integer> bodyWidths = new ArrayList<>();
        final List<String> contentOrder = new ArrayList<>();
        final List<String> dispatched = new ArrayList<>();
        Map<String, String> values;
        String layout;
        boolean escapeEnabled;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Fixture(boolean packed) {
            MockBukkit.mock();
            bases = mockStatic(DialogBase.class);
            actions = mockStatic(DialogAction.class);
            buttons = mockStatic(ActionButton.class);
            dialogs = mockStatic(Dialog.class);
            types = mockStatic(DialogType.class);
            clicks = mockStatic(ClickEvent.class, CALLS_REAL_METHODS);
            bodies = mockStatic(DialogBody.class);
            inputs = mockStatic(DialogInput.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.isEnabled()).thenReturn(true);
            SessionTestSupport.module(plugin, "pack", mock(ResourcePackService.class));
            when(plugin.pack().packed(player)).thenReturn(packed);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            bodies.when(() -> DialogBody.plainMessage(any(), anyInt())).thenAnswer(call -> {
                body.add(call.getArgument(0)); bodyWidths.add(call.getArgument(1)); contentOrder.add("body"); return mock(io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody.class);
            });
            inputs.when(() -> DialogInput.text(anyString(), anyInt(), any(), anyBoolean(), anyString(), anyInt(), isNull()))
                    .thenAnswer(call -> { contentOrder.add("input"); return null; });
            actions.when(() -> DialogAction.customClick(any(DialogActionCallback.class), any(ClickCallback.Options.class)))
                    .thenAnswer(call -> { callbacks.add(call.getArgument(0)); return null; });
            buttons.when(() -> ActionButton.create(any(), nullable(Component.class), anyInt(), nullable(DialogAction.class)))
                    .thenAnswer(call -> { nativeButtons.add(new NativeButton(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3))); return null; });
            clicks.when(() -> ClickEvent.callback(any(), any(ClickCallback.Options.class)))
                    .thenAnswer(call -> { textCallbacks.add(call.getArgument(0)); return ClickEvent.runCommand(UUID.randomUUID().toString()); });
            bases.when(() -> DialogBase.create(any(), nullable(Component.class), anyBoolean(), anyBoolean(), any(), anyList(), anyList()))
                    .thenAnswer(call -> { escapeEnabled = call.getArgument(2); return null; });
            types.when(() -> DialogType.confirmation(any(), any())).thenAnswer(call -> { layout = "confirmation"; return null; });
            types.when(() -> DialogType.notice(any())).thenAnswer(call -> { layout = "notice"; return null; });
            types.when(() -> DialogType.multiAction(anyList(), any(), anyInt())).thenAnswer(call -> { layout = "multi-action"; return null; });
            var builder = mock(DialogRegistryEntry.Builder.class, RETURNS_SELF);
            RegistryBuilderFactory factory = mock(RegistryBuilderFactory.class);
            when(factory.empty()).thenReturn(builder);
            dialogs.when(() -> Dialog.create(any())).thenAnswer(call -> {
                ((Consumer) call.getArgument(0)).accept(factory); return null;
            });
        }

        void open(String page, int columns, List<MenuView.Button> choices, List<MenuView.Input> fields) {
            DialogMenus.open(plugin, player, new MenuView(Component.text("Menu"),
                    List.of(new MenuView.Body(Component.text("Introduction"), 350)), fields, choices,
                    choice("close"), columns, page), (action, response) -> { dispatched.add(action); values = response; });
        }

        @Override public void close() {
            inputs.close(); bodies.close(); clicks.close(); types.close(); dialogs.close(); buttons.close(); actions.close(); bases.close();
            MockBukkit.unmock();
        }
    }
}

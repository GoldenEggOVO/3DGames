package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowMenuTest {
    @Test
    void catalogKeepsSettingsAndResumeAboveTwoGameCards() {
        UUID token = UUID.randomUUID();
        var menu = layout("catalog", List.of(button("settings"), button("resume"), button("tabletop"), button("casino"), button("close")), token);
        assertEquals(2, menu.columns());
        assertEquals(2, menu.headers().size());
        assertEquals(List.of("tabletop", "casino"), menu.controls().stream().map(c -> MessageText.plain(c.caption())).toList());
        assertTrue(menu.controls().stream().allMatch(c -> c.width() == 174));
        assertEquals("3dgames:" + token + " 4", menu.exit().action());
    }

    @Test
    void roomBrowserUsesAnOwnedFullWidthLink() {
        UUID token = UUID.randomUUID();
        var menu =
                layout(
                        "catalog",
                        List.of(button("resource-pack"), button("rooms"), button("category-board")),
                        token);
        assertEquals(
                List.of("resource-pack", "rooms"),
                menu.headers().stream()
                        .map(control -> MessageText.plain(control.caption()))
                        .toList());
        var browser = menu.headers().get(1);
        assertEquals(350, browser.width());
        assertEquals(
                ClickEvent.runCommand("/3dgames click 3dgames:" + token + " 1"),
                browser.caption().clickEvent());
        assertEquals(1, menu.controls().size());
    }

    @Test
    void roomAndSetupKeepTheirActionOrderAndWidths() {
        var room =
                layout(
                        "room",
                        List.of(
                                button("leave"),
                                button("entry"),
                                button("play"),
                                button("bots"),
                                button("ready"),
                                button("back")),
                        UUID.randomUUID());
        assertEquals(
                List.of("ready", "bots", "play", "entry", "leave", "back"),
                room.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(1, room.columns());
        assertTrue(room.controls().stream().allMatch(control -> control.width() == 330));
        var setup =
                layout(
                        "setup",
                        List.of(
                                button("details"),
                                button("capacity"),
                                button("start"),
                                button("rule-profile")),
                        UUID.randomUUID());
        assertEquals(
                List.of("start", "capacity", "rule-profile", "details"),
                setup.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(1, setup.columns());
        assertEquals(330, setup.controls().getFirst().width());
    }

    @Test
    void submenusUseReturnAsTheirOnlyFooter() {
        for (String page : List.of("dialog", "category", "room", "setup", "settings", "tabletop")) {
            UUID token = UUID.randomUUID();
            var menu =
                    layout(
                            page,
                            List.of(new GameMenus.Button("back", "Return", () -> {})),
                            token);
            assertNotNull(menu.exit(), page);
            assertEquals("Return", MessageText.plain(menu.exit().caption()), page);
            assertEquals(330, menu.exit().width(), page);
            assertEquals("3dgames:" + token + " 0", menu.exit().action(), page);
            assertTrue(menu.controls().isEmpty(), page);
            assertTrue(menu.headers().isEmpty(), page);
        }
    }

    @Test void settingsHaveOneReturnAndTheRootKeepsClose() throws Exception {
        var f = new MenuFlowTest.Fixture();
        f.menus.settings(f.player);
        assertEquals(1, f.buttons.stream().filter(b -> b.id().equals("back")).count());
        assertTrue(f.buttons.stream().noneMatch(b -> b.id().equals("close")));
        f.buttons.stream().filter(b -> b.id().equals("back")).findFirst().orElseThrow().action().run();
        assertEquals(1, f.buttons.stream().filter(b -> b.id().equals("close")).count());
        assertTrue(f.buttons.stream().noneMatch(b -> b.id().equals("back")));
    }

    @Test
    void titlesDescriptionsAndButtonParametersKeepTheirStylesAndLiteralNames() {
        String name = "<red>Player&c";
        var title = Component.text(name, NamedTextColor.RED);
        var description = Component.text("Room details", NamedTextColor.GOLD);
        var button =
                new GameMenus.Button(
                        "entry", MessageText.render("Next: {name}", "name", name), () -> {});
        var menu =
                GameMenuWindow.layout(
                        "dialog", title, description, List.of(button), UUID.randomUUID());
        assertEquals(name, MessageText.plain(menu.title()));
        assertEquals("Room details", MessageText.plain(menu.description()));
        assertEquals("Next: " + name, MessageText.plain(menu.controls().getFirst().caption()));
        assertTrue(
                LegacyComponentSerializer.legacySection()
                        .serialize(menu.title())
                        .contains("§c" + name));
    }

    @Test
    void rootCloseInvalidatesTheSessionWithoutLeavingTheRoom() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        GameSession room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Owner");
        fixture.menus.main(fixture.player);
        fixture.buttons.stream()
                .filter(button -> button.id().equals("close"))
                .findFirst()
                .orElseThrow()
                .action()
                .run();
        assertFalse(fixture.menus.active(fixture.player));
        assertSame(room, fixture.plugin.games().session(fixture.player));
        verify(fixture.plugin.games(), never()).leave(fixture.player);
    }

    @Test
    void nativeMenuUsesRenderedCallbackOrderAndRejectsRepeatedClicks() throws Exception {
        var plugin = SessionTestSupport.plugin();
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(GameMenuWindow.class);
        when(view.layoutMenu(anyString(), any(Component.class), any(Component.class), anyList(), any()))
                .thenAnswer(
                        call ->
                                GameMenuWindow.layout(
                                        call.getArgument(0),
                                        call.getArgument(1),
                                        call.getArgument(2),
                                        call.getArgument(3),
                                        call.getArgument(4)));
        when(view.open(eq(player), any())).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        var clicked = new AtomicInteger();
        menus.show(
                player,
                "Test",
                "",
                List.of(
                        new GameMenus.Button("leave", "Leave", () -> clicked.set(1)),
                        new GameMenus.Button("play", "Play", () -> clicked.set(2))),
                null,
                "room");
        var rendered = ArgumentCaptor.forClass(GameMenuWindow.Menu.class);
        verify(view).open(eq(player), rendered.capture());
        String action = rendered.getValue().controls().getFirst().action();
        assertEquals(
                "Play", MessageText.plain(rendered.getValue().controls().getFirst().caption()));
        menus.handle(player, action);
        assertEquals(2, clicked.get());
        clicked.set(0);
        menus.handle(player, action);
        assertEquals(0, clicked.get());
        verify(player, never()).sendMessage(any(Component.class));
        menus.close();
    }

    private static GameMenuWindow.Menu layout(
            String page, List<GameMenus.Button> buttons, UUID token) {
        return GameMenuWindow.layout(page, Component.text("Title"), Component.empty(), buttons, token);
    }

    private static GameMenus.Button button(String id) {
        return new GameMenus.Button(id, id, () -> {});
    }
}

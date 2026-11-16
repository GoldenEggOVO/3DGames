package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


public final class GameMenuWindow {
    record Control(Component caption, int width, String action, Component tooltip) {}

    record Menu(
            String page,
            Component title,
            Component description,
            int descriptionWidth,
            int columns,
            List<Control> headers,
            List<Control> controls,
            Control exit,
            List<GameMenus.Button> buttons) {}

    private final TDGamesPlugin plugin;

    public GameMenuWindow(TDGamesPlugin plugin) {
        this.plugin = plugin;
    }

    Menu layoutMenu(
            String page,
            Component title,
            Component description,
            List<GameMenus.Button> buttons,
            UUID token) {
        return layout(page, title, description, buttons, token);
    }

    static Menu layout(
            String page,
            Component title,
            Component description,
            List<GameMenus.Button> supplied,
            UUID token) {
        List<String> order =
                switch (page) {
                    case "catalog" -> List.of("single-player", "multiplayer");
                    case "room" -> List.of("ready", "bots", "play", "rematch", "pass", "accept", "resume", "stake", "refresh", "entry", "leave", "save-leave");
                    case "setup" ->
                            List.of(
                                    "start",
                                    "capacity",
                                    "board",
                                    "rule-profile",
                                    "details",
                                    "entry");
                    default -> List.of("entry");
                };
        List<GameMenus.Button> buttons = new ArrayList<>(supplied);
        buttons.sort(Comparator.comparingInt(button -> buttonOrder(button.id(), order)));
        List<Control> headers = new ArrayList<>();
        List<Control> controls = new ArrayList<>();
        Control exit = null;
        for (int index = 0; index < buttons.size(); index++) {
            GameMenus.Button button = buttons.get(index);
            Component caption = caption(page, button);
            String action = "3dgames:" + token + " " + index;
            if (button.id().equals("close") || button.id().equals("back")) {
                exit = new Control(caption, page.equals("catalog") ? 350 : 330, action, button.tooltip());
            } else if (page.equals("catalog")
                    && List.of("resource-pack", "resume", "rooms", "settings").contains(button.id())
                    || page.equals("tabletop") && button.id().equals("solo-resume")) {
                headers.add(
                        new Control(
                                caption.clickEvent(
                                        ClickEvent.runCommand("/3dgames click " + action)),
                                350,
                                action, button.tooltip()));
            } else {
                int width =
                        switch (page) {
                            case "catalog", "tabletop" ->
                                    button.id().equals("main") ? 330 : 174;
                            default -> 330;
                        };
                controls.add(new Control(caption, width, action, button.tooltip()));
            }
        }
        return new Menu(
                page,
                MessageText.render("<white>{title}", "title", title),
                MessageText.render("<white>{description}", "description", description),
                370,
                List.of("catalog", "tabletop").contains(page) ? 2 : 1,
                List.copyOf(headers),
                List.copyOf(controls),
                exit,
                List.copyOf(buttons));
    }

    private static int buttonOrder(String id, List<String> order) {
        return switch (id) {
            case "resource-pack" -> -2;
            case "resume", "solo-resume", "rooms" -> order.contains(id) ? order.indexOf(id) : -1;
            case "back" -> order.size() + 1;
            case "main" -> order.size() + 2;
            case "close" -> order.size() + 3;
            default -> order.contains(id) ? order.indexOf(id) : order.size();
        };
    }

    private static Component caption(String page, GameMenus.Button button) {
        String style =
                switch (button.id()) {
                    case "close" -> "<gray>[ {label} ]";
                    case "main", "resume", "solo-resume" -> "<yellow>{label}";
                    case "back" -> "<yellow>{label}";
                    case "rooms" -> page.equals("catalog") ? "<aqua>{label}" : "<white>{label}";
                    case "ready", "play", "rematch" ->
                            page.equals("room") ? "<green><bold>{label}</bold>" : "<white>{label}";
                    case "bots" -> page.equals("room") ? "<aqua>{label}" : "<white>{label}";
                    case "leave" -> "<red>{label}";
                    case "start" ->
                            page.equals("setup") ? "<green><bold>{label}</bold>" : "<white>{label}";
                    case "capacity", "board", "rule-profile" ->
                            page.equals("setup") ? "<aqua>{label}" : "<white>{label}";
                    case "details" -> page.equals("setup") ? "<yellow>{label}" : "<white>{label}";
                    default -> "<white>{label}";
                };
        String label =
                button.id().equals("close") || button.id().equals("back")
                        ? button.label()
                        : button.label().replaceAll("(?i)[§&][0-9A-FK-OR]", "");
        Component content = button.component() != null ? button.component() : text(label);
        return MessageText.render(style, "label", content);
    }

    boolean open(Player player, Menu menu) {
        var controls = new ArrayList<Control>(menu.headers());
        controls.addAll(menu.controls());
        var buttons = controls.stream().map(control -> {
            var source = menu.buttons().get(Integer.parseInt(control.action().split(" ")[1]));
            return new GoldenEggOVO.TDGames.Core.menu.MenuView.Button(
                    control.caption().clickEvent(null), control.tooltip(), control.width(), control.action(), source.id());
        }).toList();
        var exit = menu.exit();
        String exitKey = menu.buttons().get(Integer.parseInt(exit.action().split(" ")[1])).id();
        GoldenEggOVO.TDGames.Core.menu.DialogMenus.open(plugin, player,
                new GoldenEggOVO.TDGames.Core.menu.MenuView(menu.title(),
                        List.of(new GoldenEggOVO.TDGames.Core.menu.MenuView.Body(menu.description(), menu.descriptionWidth())),
                        List.of(), buttons, new GoldenEggOVO.TDGames.Core.menu.MenuView.Button(
                                exit.caption(), exit.tooltip(), exit.width(), exit.action(), exitKey), menu.columns(), menu.page(), System.currentTimeMillis() + 120000),
                (action, values) -> plugin.menus().handle(player, action),
                action -> plugin.menus().refreshExpired(player, menu.exit().action().split(" ")[0]));
        return true;
    }

    static Component text(String value) {
        return MessageText.render(value);
    }
}

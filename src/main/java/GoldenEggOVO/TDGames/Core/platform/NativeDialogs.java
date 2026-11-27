package GoldenEggOVO.TDGames.Core.platform;

import GoldenEggOVO.TDGames.Core.menu.MenuView;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class NativeDialogs {
    private NativeDialogs() {}

    public static void open(Plugin plugin, Player player, MenuView view,
                            BiConsumer<String, Map<String, String>> dispatch) {
        if (paper()) NativeDialogsPaper.open(player, view, dispatch);
        else NativeDialogsBukkit.open(plugin, player, view, dispatch);
    }

    public static void clear(Player player) {
        if (paper()) NativeDialogsPaper.clear(player);
        else PlatformMessages.nativeCall(player, "clearDialog");
    }

    static void renderBody(MenuView view, Consumer<MenuView.Body> description, ObjIntConsumer<MenuView.Button> link) {
        int backIndex = -1;
        for (int i = 0; i < view.buttons().size(); i++) {
            var button = view.buttons().get(i);
            if (button.key().equals("back")) backIndex = i;
            else if (header(view, button)) link.accept(button, i);
        }
        for (var line : view.body())
            if (!MessageText.plain(line.text()).isBlank()) description.accept(line);
        if (backIndex >= 0) link.accept(view.buttons().get(backIndex), backIndex);
    }

    static boolean textLink(MenuView view, MenuView.Button button) {
        return button.key().equals("back") || header(view, button);
    }

    private static boolean header(MenuView view, MenuView.Button button) {
        return view.page().equals("catalog") && java.util.Set.of("settings", "rooms", "resume").contains(button.key())
                || view.page().equals("tabletop") && button.key().equals("solo-resume");
    }

    private static boolean paper() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog", false, NativeDialogs.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}

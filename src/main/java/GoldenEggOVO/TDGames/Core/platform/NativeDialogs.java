package GoldenEggOVO.TDGames.Core.platform;

import GoldenEggOVO.TDGames.Core.menu.MenuView;
import java.util.Map;
import java.util.function.BiConsumer;
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

    static boolean header(MenuView view, MenuView.Button button) {
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

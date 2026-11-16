package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Core.platform.NativeDialogs;
import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.Map;
import java.util.function.BiConsumer;

public final class DialogMenus {
    private DialogMenus() {}

    public static void open(Plugin plugin, Player player, MenuView view,
                              BiConsumer<String, Map<String, String>> handler) {
        open(plugin, player, view, handler, action -> {});
    }

    public static void open(Plugin plugin, Player player, MenuView view,
                              BiConsumer<String, Map<String, String>> handler, java.util.function.Consumer<String> expired) {
        if (!plugin.getConfig().getBoolean("menu-enabled", true)) {
            PlatformMessages.send(player, GoldenEggOVO.TDGames.Core.text.Language.component("menu.disabled"));
            return;
        }
        var world = player.getWorld().getUID();
        var used = new java.util.concurrent.atomic.AtomicBoolean();
        BiConsumer<String, Map<String, String>> dispatch = (action, values) -> {
            if (!plugin.isEnabled() || !used.compareAndSet(false, true)) return;
            new PlatformScheduler(plugin).later(player, () -> {
                if (plugin.isEnabled() && player.isOnline() && world.equals(player.getWorld().getUID())
                        && plugin.getConfig().getBoolean("menu-enabled", true)) {
                    if (System.currentTimeMillis() >= view.expiresAt()) {
                        if (action != null && !(view.exit().key().equals("close") && action.equals(view.exit().action()))) {
                            PlatformMessages.send(player, GoldenEggOVO.TDGames.Core.text.Language.component("menu.expired"));
                            expired.accept(action);
                        }
                    } else if (action != null) handler.accept(action, values);
                }
            }, 1);
        };
        NativeDialogs.open(plugin, player, view, dispatch);
    }
}

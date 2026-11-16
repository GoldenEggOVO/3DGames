package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.MenuView;
import GoldenEggOVO.TDGames.Core.menu.DialogMenus;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Core.text.Language;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public final class StakeWindow {
    private StakeWindow() {}

    public static void open(TDGamesPlugin plugin, Player player, GameSession room) {
        if (plugin.integrations() == null || !plugin.integrations().stakesEnabled()
                || !room.host(player.getUniqueId()) || room.seat(player.getUniqueId()) < 0
                || !room.departed.isEmpty()
                || room.phase() != GameSession.Phase.LOBBY && room.phase() != GameSession.Phase.FINISHED) return;
        long revision = room.revision();
        var economy = plugin.integrations().economy();
        var view = new MenuView(Language.component("stake.edit.title"),
                List.of(new MenuView.Body(Language.component("stake.edit.help",
                        "minimum", economy.format(plugin.getConfig().getDouble("economy.stakes.minimum", 1)),
                        "maximum", economy.format(plugin.getConfig().getDouble("economy.stakes.maximum", 1000)),
                        "seconds", plugin.getConfig().getLong("reconnect-seconds", 120)), 300)),
                List.of(new MenuView.Input("stake_amount",
                        Language.component("stake.edit.amount"), room.stake() == null ? "0"
                        : BigDecimal.valueOf(room.stake()).stripTrailingZeros().toPlainString(), 24)),
                List.of(new MenuView.Button(Language.component("stake.edit.save"), null, 300, "save")),
                new MenuView.Button(Language.component("menu.back"), null, 300, "back"),
                1, "stake", System.currentTimeMillis() + Duration.ofMinutes(2).toMillis());
        plugin.menus().forget(player);
        DialogMenus.open(plugin, player, view, (choice, values) -> {
            if (!plugin.allowed(player)) return;
            if (choice.equals("back")) plugin.menus().room(player, room);
            else if (choice.equals("save")) {
                try {
                    submit(plugin, player, room, revision, values.get("stake_amount"));
                } catch (IllegalArgumentException error) {
                    GoldenEggOVO.TDGames.Core.platform.PlatformMessages.actionBar(player, Language.error(error));
                    plugin.menus().room(player, room);
                }
            }
        });
    }

    static void submit(TDGamesPlugin plugin, Player player, GameSession room, long revision, String input) {
        if (input == null || input.trim().length() > 24 || !input.trim().matches("[0-9]+(?:\\.[0-9]+)?"))
            throw new RuleViolation("error.stake.input", "Enter a non-negative decimal stake");
        plugin.games().setStake(player, room, revision, new BigDecimal(input.trim()).doubleValue());
    }
}

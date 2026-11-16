package GoldenEggOVO.TDGames.Multiplayer.menu;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.MenuView;
import GoldenEggOVO.TDGames.Core.menu.DialogMenus;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Multiplayer.game.texasholdem.TexasHoldemGame;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

public final class PokerRaiseWindow {
    private PokerRaiseWindow() {}

    public static void open(TDGamesPlugin plugin, Player player, GameSession room, long revision) {
        int seat = room.seat(player.getUniqueId());
        if (seat < 0
                || !room.kind.equals("texas-holdem")
                || !room.board().legalActions(seat).stream().anyMatch(a -> a.startsWith("raise:")))
            return;
        var info = room.board().publicInfo();
        var view = new MenuView(Language.component("cards.control.raise"),
                List.of(new MenuView.Body(Language.component("cards.poker.raise-help",
                        "minimum", info.get("minimumRaise"), "already", info.get("bet." + seat),
                        "chips", info.get("chips." + seat)), 300)),
                List.of(new MenuView.Input("street_total", Language.component("cards.poker.raise-total"),
                        info.get("minimumRaise"), 6)),
                List.of(new MenuView.Button(Language.component("cards.control.raise"), null, 300, "save")),
                new MenuView.Button(Language.component("menu.back"), null, 300, "back"),
                1, "raise", System.currentTimeMillis() + Duration.ofMinutes(2).toMillis());
        plugin.menus().forget(player);
        DialogMenus.open(plugin, player, view, (choice, values) -> {
            if (!plugin.allowed(player)) return;
            if (choice.equals("back")) plugin.menus().room(player, room);
            else if (choice.equals("save")) {
                try {
                    submit(plugin, player, room, revision, seat, values.get("street_total"));
                } catch (IllegalArgumentException error) {
                    GoldenEggOVO.TDGames.Core.platform.PlatformMessages.actionBar(player, Language.error(error));
                }
            }
        });
    }

    static void submit(TDGamesPlugin plugin, Player actor, GameSession room, long revision, int seat, String total) {
        if (plugin.games().activeSessions().get(room.id) != room)
            throw new RuleViolation("error.room-closed", "Room closed");
        if (!plugin.tableWorld().atTableWorld(actor, room))
            throw new RuleViolation(
                    "error.use-3dgames-resume-to-return-to-the",
                    "Use /3dgames resume to return to the table world first");
        if (plugin.pack() != null) plugin.pack().require(actor, room.kind);
        room.requireAction(actor.getUniqueId(), revision);
        int value = Integer.parseInt(total);
        ((TexasHoldemGame) room.board()).validateRaise(seat, value);
        plugin.games().apply(room, seat, new JsonPrimitive("raise:" + value), null);
    }

}

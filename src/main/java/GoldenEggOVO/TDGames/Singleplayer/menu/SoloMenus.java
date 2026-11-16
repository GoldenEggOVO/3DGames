package GoldenEggOVO.TDGames.Singleplayer.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.menu.GameMenus.Button;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import org.bukkit.entity.Player;
import java.util.*;

public final class SoloMenus {
    private final TDGamesPlugin plugin;
    private final GameMenus menus;

    public SoloMenus(TDGamesPlugin plugin, GameMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    public void create(Player player, String kind, Map<String, String> options, Runnable back) {
        Runnable start = () -> plugin.games().create(player, kind, 1, options, true);
        if (plugin.games().hasSoloProgress(player.getUniqueId(), kind))
            menus.show(player, Language.component("solo.overwrite.title"), Language.component("solo.overwrite.description"),
                    List.of(new Button("confirm", Language.component("solo.overwrite.confirm"), start)), back);
        else start.run();
    }

    public void progress(Player player) {
        List<String> kinds = plugin.games().soloProgressKinds(player.getUniqueId());
        if (kinds.isEmpty()) { plugin.games().resumeSolo(player); return; }
        if (kinds.size() == 1) { plugin.games().resumeSolo(player, kinds.getFirst()); return; }
        List<Button> buttons = new ArrayList<>();
        for (String kind : kinds)
            buttons.add(new Button("entry", RoomText.game(kind), () -> plugin.games().resumeSolo(player, kind)));
        menus.show(player, Language.component("solo.resume"), Language.component("solo.choose-progress"), buttons,
                () -> menus.singlePlayer(player));
    }

    public void open(Player player, GameSession session) {
        if (plugin.games().activeSessions().get(session.id) != session) {
            menus.main(player);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        if (session.phase() == GameSession.Phase.PLAYING)
            buttons.add(new Button("play", Language.component("menu.play"), () -> {
                menus.forget(player);
                plugin.games().enterArena(player, session);
            }));
        if (session.phase() == GameSession.Phase.FINISHED)
            buttons.add(new Button("rematch", Language.component(session.ready.contains(player.getUniqueId())
                    ? "menu.rematch.waiting" : "menu.rematch"), () -> plugin.games().rematch(player, session, session.stake())));
        buttons.add(new Button("save-leave", Language.component("solo.save-leave"), () -> plugin.games().leave(player)));
        menus.show(player, RoomText.name(session), menus.summary(session, player.getUniqueId()), buttons,
                () -> menus.main(player), "room");
    }
}

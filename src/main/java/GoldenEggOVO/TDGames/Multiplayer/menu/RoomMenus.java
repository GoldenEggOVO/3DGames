package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.menu.GameMenus.Button;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;

public final class RoomMenus {
    private final TDGamesPlugin plugin;
    private final GameMenus menus;
    private final RoomEconomyText economyText;

    public RoomMenus(TDGamesPlugin plugin, GameMenus menus) {
        this.plugin = plugin;
        this.menus = menus;
        economyText = new RoomEconomyText(plugin);
    }

    public void open(Player p, GameSession r) {
        if (plugin.games().activeSessions().get(r.id) != r) {
            menus.main(p);
            return;
        }
        int seat = r.seat(p.getUniqueId());
        if (seat < 0) {
            menus.observe(p, r);
            return;
        }
        List<Button> b = new ArrayList<>();
        Double confirmedStake = r.stake();
        if (r.capacity > 1 && (r.phase() == GameSession.Phase.LOBBY || r.phase() == GameSession.Phase.FINISHED) && r.departed.isEmpty()
                && r.host(p.getUniqueId()) && plugin.integrations() != null && plugin.integrations().stakesEnabled() && plugin.integrations().economy().available())
            b.add(new Button("stake", Language.component("stake.edit.button"), () -> StakeWindow.open(plugin, p, r)));
        if (r.phase() == GameSession.Phase.LOBBY) {
            b.add(
                    new Button(
                            "ready",
                            readyLabel(r, p,
                                Language.component(
                                    r.ready.contains(p.getUniqueId())
                                            ? "menu.unready"
                                            : "menu.ready")),
                            () -> plugin.games().ready(p, r, confirmedStake)));
            if (r.host(p.getUniqueId()) && (r.stake() == null || r.stake() == 0))
                b.add(
                        new Button(
                                "bots",
                                Language.component("menu.bots"),
                                () -> plugin.games().startWithBots(p, r)));
        }
        if (r.phase() == GameSession.Phase.PLAYING) {
            b.add(
                    new Button(
                            "play",
                            Language.component("menu.play"),
                            () -> {
                                menus.forget(p);
                                plugin.games().enterArena(p, r);
                            }));
            if (r.board() != null && !r.busy()) {
                List<String> actions =
                        Set.of("go", "go9", "go13").contains(r.kind)
                                ? List.of("pass", "accept", "resume")
                                : List.of();
                if (!actions.isEmpty()) {
                    long revision = r.revision();
                    List<String> legal = r.board().legalActions(seat);
                    for (String action : actions)
                        if (legal.contains(action))
                            b.add(
                                    new Button(
                                            action,
                                            GameMenus.actionLabel(r, action),
                                            () -> menus.performAction(p, r, revision, action)));
                }
            }
        }
        if (r.phase() == GameSession.Phase.FINISHED && r.departed.isEmpty())
            b.add(
                    new Button(
                            "rematch",
                            readyLabel(r, p,
                                Language.component(
                                    r.ready.contains(p.getUniqueId())
                                            ? "menu.rematch.waiting"
                                            : "menu.rematch")),
                            () -> plugin.games().rematch(p, r, confirmedStake)));
        if (plugin.integrations() != null && plugin.integrations().settlementPending(r, p.getUniqueId()))
            b.add(new Button("refresh", Language.component("menu.refresh"), () -> menus.room(p, r)));
        b.add(new Button("leave", Language.component("menu.leave.title"), () -> menus.confirmLeave(p)));
        menus.show(p, RoomText.name(r), menus.summary(r, p.getUniqueId()), b, () -> menus.main(p), "room");
    }

    private Component readyLabel(GameSession room, Player player, Component label) {
        return plugin.integrations() == null || room.ready.contains(player.getUniqueId())
                ? label : economyText.readyLabel(room, label);
    }

    public void confirmLeave(Player p) {
        GameSession r = plugin.games().session(p);
        if (r == null) {
            menus.main(p);
            return;
        }
        menus.show(
                p,
                Language.component("menu.leave.title"),
                plugin.integrations() == null
                        ? Language.component(r.phase() == GameSession.Phase.LOBBY ? "menu.leave.lobby"
                                : r.phase() == GameSession.Phase.FINISHED ? "menu.leave.finished" : "menu.leave.active")
                        : economyText.leaveDetails(r),
                List.of(
                        new Button(
                                "entry",
                                Language.component("menu.leave.confirm"),
                                () -> {
                                    if (plugin.games().session(p) == r) plugin.games().leave(p);
                                    else menus.main(p);
                                })),
                () -> menus.room(p, r));
    }

    public void list(Player p, String kind, int requestedPage) {
        List<GameSession> rooms =
                plugin.multiplayer().rooms().values().stream()
                        .filter(
                                r ->
                                        kind == null
                                                || r.kind.equals(kind)
                                                || kind.equals("go")
                                                        && Set.of("go9", "go13").contains(r.kind))
                        .sorted(
                                Comparator.comparingInt(
                                        r ->
                                                r.seat(p.getUniqueId()) >= 0
                                                        ? 0
                                                        : r.phase() == GameSession.Phase.LOBBY
                                                                        && r.seats.size()
                                                                                < r.capacity
                                                                ? 1
                                                                : 2))
                        .toList();
        int page = 0;
        List<Button> b = new ArrayList<>();
        if (kind != null)
            b.add(new Button("entry", Language.component("menu.create"), () -> menus.setup(p, kind)));
        for (GameSession r :
                rooms) {
            String action =
                    r.seat(p.getUniqueId()) >= 0
                            ? "menu.rooms.resume"
                            : r.phase() == GameSession.Phase.LOBBY && r.seats.size() < r.capacity
                                    ? "menu.rooms.join"
                                    : "menu.rooms.view";
            Component label =
                    Language.component(
                            "room.list",
                            "room",
                            RoomText.name(r),
                            "occupied",
                            r.occupied(),
                            "capacity",
                            r.capacity,
                            "phase",
                            RoomText.phase(r));
            Component badge = plugin.integrations() == null ? Component.empty() : economyText.priceBadge(r);
            if (!MessageText.plain(badge).isBlank()) label = label.append(Component.text(" · ")).append(badge);
            Component details = Language.component("room.browser.details", "room", RoomText.name(r),
                    "occupied", r.occupied(), "capacity", r.capacity, "phase", RoomText.phase(r),
                    "mode", Language.component(r.seats.stream().anyMatch(GameSession.Seat::bot) ? "room.mode.bots" : "room.mode.humans"));
            if (plugin.integrations() != null) {
                Component fees = economyText.fees(r);
                if (!MessageText.plain(fees).isBlank()) details = details.append(Component.newline()).append(fees);
            }
            b.add(
                    new Button(
                            "room-" + r.id,
                            Language.component(
                                    "menu.rooms.entry",
                                    "action",
                                    Language.component(action),
                                    "room",
                                    label),
                            () -> {
                                if (plugin.games().activeSessions().get(r.id) != r) {
                                    plugin.tell(p, Language.component("error.page-changed"));
                                    list(p, kind, page);
                                    return;
                                }
                                if (r.seat(p.getUniqueId()) >= 0) plugin.games().resume(p, r);
                                else if (r.phase() == GameSession.Phase.LOBBY && r.seats.size() < r.capacity)
                                    plugin.games().join(p, r);
                                else menus.observe(p, r);
                            }, details));
        }
        Component description =
                rooms.isEmpty()
                        ? Language.component("menu.rooms.empty")
                        : Language.component(
                                "menu.rooms.count", "count", rooms.size());
        menus.show(
                p,
                kind == null ? Language.component("setup.rooms") : RoomText.game(kind),
                description,
                b,
                () -> {
                    GameSession current = plugin.games().session(p);
                    if (kind != null && current != null) menus.room(p, current);
                    else if (kind != null) menus.categoryFor(p, kind);
                    else menus.main(p);
                });
    }
}

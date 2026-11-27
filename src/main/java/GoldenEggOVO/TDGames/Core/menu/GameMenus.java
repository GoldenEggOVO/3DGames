package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Singleplayer.menu.SoloMenus;
import GoldenEggOVO.TDGames.Multiplayer.menu.RoomMenus;
import GoldenEggOVO.TDGames.Multiplayer.menu.PokerRaiseWindow;
import GoldenEggOVO.TDGames.Multiplayer.menu.RoomEconomyText;

import com.google.gson.*;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.game.GameOptions;
import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Shared.text.BoardMessages;
import GoldenEggOVO.TDGames.Shared.text.HandText;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Core.platform.NativeDialogs;
import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class GameMenus implements AutoCloseable {
    private static final Map<String, List<String>> CATEGORIES = Map.of(
            "board", List.of("xiangqi", "gomoku", "chess", "checkers", "draughts", "reversi", "go", "connectfour",
                    "mancala", "nine-mens-morris", "dots-and-boxes", "hex", "battleship", "shogi", "mastermind", "2048"),
            "card", List.of("color-eight", "mahjong", "doudizhu", "liars-bar", "texas-holdem", "klondike", "memory"),
            "dice", List.of("ludo", "yacht", "backgammon", "monopoly"));

    public record Button(String id, String label, Runnable action, Component component, Component tooltip) {
        public Button(String id, String label, Runnable action) {
            this(id, label, action, null, null);
        }

        Button(String label, Runnable action) {
            this("entry", label, action);
        }

        public Button(String id, Component label, Runnable action) {
            this(id, label, action, null);
        }

        public Button(String id, Component label, Runnable action, Component tooltip) {
            this(id, MessageText.plain(label), action, label, tooltip);
        }

        public Button(Component label, Runnable action) {
            this("entry", label, action);
        }
    }

    public record Session(UUID token, UUID world, long expires, List<Button> buttons, Runnable refresh) {}

    record Setup(String kind, int capacity, Map<String, String> options) {
        Setup {
            if (capacity == 1 && options.containsKey("first")) {
                options = new LinkedHashMap<>(options);
                options.remove("first");
            }
            options = GameOptions.validate(kind, options);
        }
    }

    private final TDGamesPlugin plugin;
    private final RoomEconomyText economyText;
    private final SoloMenus soloMenus;
    private final RoomMenus roomMenus;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, String> categories = new HashMap<>();
    private final Set<UUID> expiredNotices = new HashSet<>();
    private final GameMenuWindow window;

    public GameMenus(TDGamesPlugin p) {
        this(p, null);
    }

    public GameMenus(TDGamesPlugin p, GameMenuWindow window) {
        plugin = p;
        economyText = new RoomEconomyText(p);
        soloMenus = new SoloMenus(p, this);
        roomMenus = new RoomMenus(p, this);
        this.window = window;
    }

    public void show(Player p, String title, String description, List<Button> buttons, Runnable back) {
        show(p, title, description, buttons, back, "dialog");
    }

    public void show(
            Player p,
            String title,
            String description,
            List<Button> buttons,
            Runnable back,
            String page) {
        show(p, GameMenuWindow.text(title), GameMenuWindow.text(description), buttons, back, page);
    }

    public void show(
            Player p, Component title, Component description, List<Button> buttons, Runnable back) {
        show(p, title, description, buttons, back, "dialog");
    }

    public void show(
            Player p,
            Component title,
            Component description,
            List<Button> buttons,
            Runnable back,
            String page) {
        if (!plugin.allowed(p)) return;
        if (!plugin.getConfig().getBoolean("menu-enabled", true)) {
            forget(p);
            PlatformMessages.send(p, Language.component("menu.disabled"));
            return;
        }
        if (plugin.tablePlayers() != null) plugin.tablePlayers().releaseFocus(p);
        Component displayedDescription = expiredNotices.remove(p.getUniqueId())
                ? Language.component("menu.expired").append(Component.newline()).append(description) : description;
        List<Button> entries = new ArrayList<>(buttons);
        if (back != null) entries.add(new Button("back", Language.component("menu.back"), back));
        else if (plugin.serverMenuAvailable())
            entries.add(
                    new Button(
                            "main",
                            Language.component("menu.main"),
                            () -> {
                                forget(p);
                                Bukkit.dispatchCommand(p, "servermenu:servermenu main");
                            }));
        entries.add(new Button("close", Language.component("menu.close"), () -> forget(p)));
        UUID token = UUID.randomUUID();
        Runnable refresh = page.equals("settings") ? () -> settings(p)
                : () -> show(p, title, description, buttons, back, page);
        if (window != null) {
            var rendered = window.layoutMenu(page, title, displayedDescription, entries, token);
            entries = rendered.buttons();
            sessions.put(
                    p.getUniqueId(),
                    new Session(
                            token,
                            p.getWorld().getUID(),
                            System.currentTimeMillis() + 120000,
                            List.copyOf(entries),
                            refresh));
            if (window.open(p, rendered)) return;
        }
        sessions.put(
                p.getUniqueId(),
                new Session(
                        token,
                        p.getWorld().getUID(),
                        System.currentTimeMillis() + 120000,
                        List.copyOf(entries),
                        refresh));
        PlatformMessages.send(p, title.append(Component.newline()).append(displayedDescription));
        for (int i = 0; i < entries.size(); i++) {
            var b = entries.get(i);
            Component caption = b.component() != null ? b.component() : GameMenuWindow.text(b.label());
            if (b.tooltip() != null) caption = caption.hoverEvent(b.tooltip());
            PlatformMessages.send(p,
                    Component.text("[ ")
                            .append(caption)
                            .append(Component.text(" ] "))
                            .clickEvent(
                                    net.kyori.adventure.text.event.ClickEvent.runCommand(
                                            "/3dgames click 3dgames:" + token + " " + i)));
        }
    }

    public void handle(Player p, String action) {
        String[] a = action.split(" ");
        if (a.length != 2 || !a[0].startsWith("3dgames:")) return;
        try {
            UUID token = UUID.fromString(a[0].substring("3dgames:".length()));
            int index = Integer.parseInt(a[1]);
            Session s = sessions.get(p.getUniqueId());
            if (s == null
                    || !s.token().equals(token)
                    || !s.world().equals(p.getWorld().getUID())
                    || index < 0
                    || index >= s.buttons().size()) return;
            if (System.currentTimeMillis() >= s.expires()) {
                if (s.buttons().get(index).id().equals("close")) { forget(p); return; }
                if (plugin.allowed(p)) {
                    PlatformMessages.send(p, Language.component("menu.expired"));
                    refreshExpired(p, a[0]);
                }
                return;
            }
            sessions.remove(p.getUniqueId());
            if (plugin.allowed(p)) s.buttons().get(index).action().run();
        } catch (IllegalArgumentException ex) {
            plugin.tell(p, Language.error(ex));
        }
    }

    public void forget(Player p) {
        sessions.remove(p.getUniqueId());
        categories.remove(p.getUniqueId());
        expiredNotices.remove(p.getUniqueId());
        if (plugin.tablePlayers() != null) plugin.tablePlayers().menuClosed(p);
    }

    public boolean active(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s != null
                && s.world().equals(p.getWorld().getUID())
                && System.currentTimeMillis() < s.expires();
    }

    public void refresh(Player p) {
        if (active(p) && p.isOnline()) sessions.get(p.getUniqueId()).refresh().run();
    }

    void refreshExpired(Player p, String token) {
        Session session = sessions.get(p.getUniqueId());
        if (session == null || !token.equals("3dgames:" + session.token())
                || !session.world().equals(p.getWorld().getUID()) || !plugin.allowed(p)) return;
        expiredNotices.add(p.getUniqueId());
        try { session.refresh().run(); }
        finally { expiredNotices.remove(p.getUniqueId()); }
    }

    public void main(Player p) {
        List<Button> b = new ArrayList<>();
        GameSession current = plugin.games().session(p);
        if (current != null)
            b.add(
                    new Button(
                            "resume",
                            Language.component("menu.resume"),
                            () -> plugin.games().resume(p, current)));
        else if (!plugin.multiplayer().rooms().isEmpty())
            b.add(new Button("rooms", Language.component("setup.rooms"), () -> rooms(p)));
        b.add(new Button("single-player", Language.component("menu.category.single-player"), () -> singlePlayer(p)));
        b.add(new Button("multiplayer", Language.component("menu.category.multiplayer"), () -> games(p)));
        b.add(new Button("settings", Language.component("menu.settings.title"), () -> settings(p)));
        show(p, Language.component("menu.title"), Component.empty(), b, null, "catalog");
    }

    public void games(Player p) { category(p, "multiplayer"); }

    public void singlePlayer(Player p) { category(p, "single-player"); }

    private void category(Player p, String category) {
        List<Button> buttons = new ArrayList<>();
        categories.put(p.getUniqueId(), category);
        boolean solo = category.equals("single-player");
        if (solo && plugin.games().session(p) == null && plugin.games().hasSoloProgress(p.getUniqueId())) {
            var savedKinds = plugin.games().soloProgressKinds(p.getUniqueId());
            Component label = savedKinds.size() > 1 ? Language.component("solo.resume-count", "count", savedKinds.size())
                    : savedKinds.isEmpty() ? Language.component("solo.resume")
                    : Language.component("solo.resume-game", "game", RoomText.game(savedKinds.getFirst()));
            buttons.add(new Button("solo-resume", label, () -> soloMenus.progress(p)));
        }
        var kinds = java.util.stream.Stream.of("board", "card", "dice")
                .flatMap(key -> CATEGORIES.get(key).stream())
                .filter(kind -> GameCatalog.playerCounts(kind).stream().anyMatch(count -> solo ? count == 1 : count > 1)).toList();
        for (String kind : kinds) {
            String players = String.join(", ", GameCatalog.playerCounts(kind).stream()
                    .filter(count -> solo ? count == 1 : count > 1).map(String::valueOf).toList());
            var detail = Language.component("menu.game-players", "players", players);
            buttons.add(new Button(kind,
                    Language.component("menu.game-entry", "game", RoomText.game(kind), "players", players),
                    () -> setup(p, kind), detail));
        }
        show(p, Language.component("menu.category." + category), Component.empty(), buttons, () -> main(p), "tabletop");
    }

    public void settings(Player p) {
        var b = new ArrayList<Button>();
        if (plugin.pack() != null && plugin.pack().hasToggle()) b.add(new Button("resource-pack", plugin.pack().button(p), () -> {
            plugin.pack().toggle(p); settings(p);
        }));
        show(p, Language.component("menu.settings.title"),
                b.isEmpty() ? Language.component("menu.settings.empty") : Component.empty(), b, () -> main(p), "settings");
    }

    public void categoryFor(Player p, String kind) {
        category(p, categories.getOrDefault(p.getUniqueId(), GameCatalog.defaultCapacity(kind) == 1 ? "single-player" : "multiplayer"));
    }

    public void setup(Player p, String kind) {
        boolean solo = categories.getOrDefault(p.getUniqueId(), "").equals("single-player");
        int capacity = solo && GameCatalog.capacityValid(kind, 1) ? 1 : GameCatalog.defaultCapacity(kind);
        if (!solo && capacity == 1 && GameCatalog.playerCounts(kind).stream().anyMatch(count -> count > 1))
            capacity = GameCatalog.playerCounts(kind).stream().filter(count -> count > 1).findFirst().orElseThrow();
        setup(p, new Setup(kind, capacity, Map.of()));
    }

    void setup(Player p, Setup draft) {
        String kind = draft.kind();
        List<Button> buttons = new ArrayList<>();
        List<GameOptions.Option> settings = GameOptions.forGame(kind, draft.options()).stream()
                .filter(option -> draft.capacity() > 1 || !option.key().equals("first")).toList();
        buttons.add(
                new Button(
                        "start",
                        Language.component(draft.capacity() == 1 ? "solo.start" : "menu.create"),
                        () -> startSetup(p, draft)));
        int[] sizes = GameCatalog.playerCounts(kind).stream().filter(count -> draft.capacity() == 1 ? count == 1 : count > 1)
                .mapToInt(Integer::intValue).toArray();
        if (sizes.length > 1)
            buttons.add(
                    new Button(
                            "capacity",
                            Language.component("setup.capacity", "count", draft.capacity()),
                            () -> {
                                int index = 0;
                                while (index < sizes.length && sizes[index] != draft.capacity())
                                    index++;
                                setup(
                                        p,
                                        new Setup(
                                                kind,
                                                sizes[(index + 1) % sizes.length],
                                                draft.options()));
                            }));
        if (Set.of("go", "go9", "go13").contains(kind))
            buttons.add(
                    new Button(
                            "board",
                            Language.component(
                                    "setup.board",
                                    "size",
                                    kind.equals("go9") ? 9 : kind.equals("go13") ? 13 : 19),
                            () ->
                                    setup(
                                            p,
                                            new Setup(
                                                    kind.equals("go9")
                                                            ? "go13"
                                                            : kind.equals("go13") ? "go" : "go9",
                                                    2,
                                                    draft.options()))));
        if (Set.of("shogi", "mini-shogi").contains(kind))
            buttons.add(
                    new Button(
                            "board",
                            Language.component("setup.board", "size", kind.equals("shogi") ? 9 : 5)
                                    .append(Component.text(" · ")).append(RoomText.game(kind)),
                            () ->
                                    setup(
                                            p,
                                            new Setup(
                                                    kind.equals("shogi") ? "mini-shogi" : "shogi",
                                                    2,
                                                    draft.options()))));
        for (var option : settings)
            if (option.key().equals("profile"))
                buttons.add(
                        new Button(
                                "rule-profile",
                                RoomText.option(kind, option, draft.options()),
                                () ->
                                        setup(
                                                p,
                                                new Setup(
                                                        kind,
                                                        draft.capacity(),
                                                        GameOptions.change(
                                                                kind, draft.options(), option)))));
        if (settings.stream().anyMatch(option -> !option.key().equals("profile")))
            buttons.add(
                    new Button(
                            "details",
                            Language.component("setup.details"),
                            () -> setupRules(p, draft, 0)));
        Component description = draft.capacity() == 1
                ? Language.component("setup.solo")
                : Language.component(
                        "setup.room-summary",
                        "count",
                        draft.capacity());
        show(
                p,
                Language.component("setup.title", "game", RoomText.game(kind)),
                description,
                buttons,
                () -> categoryFor(p, kind),
                "setup");
    }

    private void startSetup(Player player, Setup draft) {
        if (draft.capacity() == 1) {
            soloMenus.create(player, draft.kind(), draft.options(), () -> setup(player, draft));
            return;
        }
        plugin.games().create(player, draft.kind(), draft.capacity(), draft.options());
    }

    private void setupRules(Player p, Setup draft, int requestedPage) {
        String kind = draft.kind();
        List<Button> buttons = new ArrayList<>();
        List<GameOptions.Option> settings =
                GameOptions.forGame(kind, draft.options()).stream()
                        .filter(option -> draft.capacity() > 1 || !option.key().equals("first"))
                        .filter(option -> !option.key().equals("profile"))
                        .toList();
        int page = 0;
        for (var option : settings)
            buttons.add(
                    new Button(
                            "rule-" + option.key(),
                            RoomText.option(kind, option, draft.options()),
                            () ->
                                    setupRules(
                                            p,
                                            new Setup(
                                                    kind,
                                                    draft.capacity(),
                                                    GameOptions.change(
                                                            kind, draft.options(), option)),
                                            page)));
        Component description = Language.component("setup.rules-hint");
        show(
                p,
                Language.component("setup.details-title", "game", RoomText.game(kind)),
                description,
                buttons,
                () -> setup(p, draft),
                "setup");
    }

    void games(Player p, String kind) {
        games(p, kind, 0);
    }

    public void rooms(Player p) {
        games(p, null, 0);
    }

    private void games(Player player, String kind, int requestedPage) {
        roomMenus.list(player, kind, requestedPage);
    }

    Component status(GameSession r) {
        Component text = RoomText.phase(r).append(Component.newline());
        int turn = r.turn();
        for (int i = 0; i < r.seats.size(); i++) {
            GameSession.Seat seat = r.seats.get(i);
            text =
                    text.append(
                                    Language.component(
                                            "room.seat",
                                            "number",
                                            i + 1,
                                            "player",
                                            RoomText.player(seat, i + 1),
                                            "ready",
                                            r.ready.contains(seat.id()) ? " ✓" : "",
                                            "turn",
                                            turn == i
                                                    ? Component.space()
                                                            .append(Language.component("room.turn"))
                                                    : Component.empty()))
                            .append(Component.newline());
        }
        if (r.board() instanceof HandGame hand)
            text =
                    text.append(HandText.status(r.kind, hand))
                            .append(RoomText.scores(r))
                            .append(Component.newline());
        else if (r.board() != null)
            for (var message : r.board().messages())
                text = text.append(BoardMessages.render(message)).append(Component.newline());
        if (r.phase() == GameSession.Phase.FINISHED || r.phase() == GameSession.Phase.PAUSED)
            text = text.append(RoomText.outcome(r, r.result()));
        return text;
    }

    public void pokerRaise(Player player,GameSession room,long revision) {
        PokerRaiseWindow.open(plugin,player,room,revision);
    }

    public void room(Player player, GameSession session) {
        if (GameCatalog.solo(session.kind, session.capacity)) soloMenus.open(player, session);
        else roomMenus.open(player, session);
    }

    public Component summary(GameSession r, UUID viewer) {
        boolean solo = GameCatalog.solo(r.kind, r.capacity);
        Component text =
                solo ? RoomText.phase(r) : Language.component(
                        "room.summary",
                        "phase",
                        RoomText.phase(r),
                        "occupied",
                        r.occupied(),
                        "capacity",
                        r.capacity);
        if (!GameCatalog.solo(r.kind, r.capacity) && plugin.integrations() != null) {
            Component fees = economyText.fees(r);
            if (!fees.equals(Component.empty())) text = text.append(Component.newline()).append(fees);
            Component balance = economyText.balanceHint(r, viewer);
            if (!balance.equals(Component.empty())) text = text.append(Component.newline()).append(balance);
        }
        if (!solo && (r.phase() == GameSession.Phase.LOBBY || r.phase() == GameSession.Phase.FINISHED))
            text =
                    text.append(Component.newline())
                            .append(
                                    Language.component(
                                            "room.ready-count",
                                            "ready",
                                            r.ready.size(),
                                            "occupied",
                                            r.occupied()));
        else if (!solo && r.turn() >= 0 && r.turn() < r.seats.size())
            text =
                    text.append(Component.newline())
                            .append(
                                    Language.component(
                                            "room.current-turn",
                                            "player",
                                            RoomText.player(r.seats.get(r.turn()), r.turn() + 1)));
        if (!solo && r.phase() == GameSession.Phase.LOBBY)
            for (int i = 0; i < r.seats.size(); i++) {
                GameSession.Seat seat = r.seats.get(i);
                text =
                        text.append(Component.newline())
                                .append(
                                        Language.component(
                                                "room.member",
                                                "player",
                                                RoomText.player(seat, i + 1),
                                                "state",
                                                Language.component(
                                                        r.ready.contains(seat.id())
                                                                ? "room.ready"
                                                                : "room.unready")));
            }
        if (r.phase() == GameSession.Phase.FINISHED) text = text.append(RoomText.scores(r));
        if (!r.departed.isEmpty()) text = text.append(Component.newline()).append(Language.component("stake.recreate"));
        if (r.phase() == GameSession.Phase.FINISHED || r.phase() == GameSession.Phase.PAUSED)
            text =
                    text.append(Component.newline())
                            .append(RoomText.outcome(r, r.result()))
                            .append(RoomText.ranking(r));
        if (!GameCatalog.solo(r.kind, r.capacity) && plugin.integrations() != null) {
            Component settlement = economyText.settlement(r, viewer);
            if (!settlement.equals(Component.empty())) text = text.append(Component.newline()).append(settlement);
        }
        return text;
    }

    public void observe(Player p, GameSession r) {
        if (plugin.games().activeSessions().get(r.id) != r) {
            plugin.tell(p, Language.component("error.page-changed"));
            games(p, r.kind);
            return;
        }
        if (plugin.games().session(p) != null) {
            plugin.tell(p, Language.component("chat.observe.own-game"));
            return;
        }
        List<Button> b = new ArrayList<>();
        b.add(new Button(Language.component("menu.observe.refresh"), () -> observe(p, r)));
        if (r.board() != null)
            b.add(
                    new Button(
                            Language.component("menu.observe.visit"),
                            () -> {
                                if (plugin.games().activeSessions().get(r.id) != r) {
                                    plugin.tell(p, Language.component("error.page-changed"));
                                    games(p, r.kind);
                                    return;
                                }
                                Location target = plugin.tableWorld().seatLocation(r, 0);
                                if (!plugin.scheduler().folia()) {
                                    plugin.scheduler().teleport(p, target, success -> {});
                                    return;
                                }
                                plugin.scheduler().execute(target, () -> {
                                    if (plugin.games().activeSessions().get(r.id) != r) {
                                        plugin.tell(p, Language.component("error.page-changed"));
                                        return;
                                    }
                                    if (!target.getBlock().isPassable()
                                            || !target.clone().add(0, 1, 0).getBlock().isPassable()) {
                                        plugin.tell(p, Language.component("error.the-seat-is-blocked-choose-a-more"));
                                        return;
                                    }
                                    plugin.scheduler().execute(p, () -> {
                                        if (plugin.games().activeSessions().get(r.id) != r) {
                                            plugin.tell(p, Language.component("error.page-changed"));
                                            return;
                                        }
                                        if (plugin.games().session(p) != null) {
                                            plugin.tell(p, Language.component("chat.observe.own-game"));
                                            return;
                                        }
                                        plugin.scheduler().teleport(p, target, success -> {});
                                    });
                                });
                            }));
        show(
                p,
                Language.component("menu.observe.title", "room", RoomText.name(r)),
                status(r).append(Language.component("menu.observe.description")),
                b,
                () -> games(p, r.kind));
    }

    public void boardChoices(Player p, GameSession r, List<String> choices, int page) {
        if (plugin.games().activeSessions().get(r.id) != r) {
            main(p);
            return;
        }
        long revision = r.revision();
        List<Button> b = new ArrayList<>();
        for (String action : choices)
            b.add(
                    new Button(
                            r.board() instanceof HandGame
                                    ? HandText.action(r, r.seat(p.getUniqueId()), action)
                                    : actionLabel(r, action),
                            () -> performAction(p, r, revision, action)));
        show(
                p,
                Language.component("menu.moves.title"),
                Language.component(
                        r.kind.equals("chess") ? "menu.moves.chess" : "menu.moves.description"),
                b,
                () -> room(p, r));
    }

    public static Component actionLabel(GameSession r, String action) {
        String[] s = action.split(":");
        if (r.kind.equals("ludo") && s.length == 3 && s[0].equals("move")) {
            var cell =
                    r.board().cells().stream()
                            .filter(c -> c.id().equals(s[2]))
                            .findFirst()
                            .orElseThrow();
            return Language.component(
                    "action.ludo.move",
                    "pawn",
                    Integer.parseInt(s[1]) % 4 + 1,
                    "destination",
                    BoardMessages.coordinate("ludo", cell));
        }
        if (Set.of("roll", "pass", "accept", "resume").contains(action))
            return Language.component("action." + action);
        if (s.length == 2 && s[0].equals("drop"))
            return Language.component("action.drop", "column", Integer.parseInt(s[1]) + 1);
        if (s.length == 2)
            return Language.component(
                    s[0].equals("dead") ? "action.dead" : "action.place", "cell", s[1]);
        if (s.length >= 3)
            return s.length == 4
                    ? Language.component(
                            "action.promote",
                            "from",
                            s[1],
                            "to",
                            s[2],
                            "piece",
                            Set.of("q", "r", "b", "n").contains(s[3])
                                    ? Language.component("promotion." + s[3])
                                    : Component.text(s[3]))
                    : Language.component("action.move", "from", s[1], "to", s[2]);
        return Component.text(action);
    }

    public void performAction(Player player, GameSession room, long revision, String action) {
        room.requireAction(player.getUniqueId(), revision);
        plugin.games().action(player, room, revision, new JsonPrimitive(action));
    }

    public void confirmLeave(Player player) {
        GameSession session = plugin.games().session(player);
        if (session == null) main(player);
        else if (GameCatalog.solo(session.kind, session.capacity)) plugin.games().leave(player);
        else roomMenus.confirmLeave(player);
    }

    public void rules(Player p, String kind) {
        GameSession r = plugin.games().session(p);
        rules(
                p,
                kind,
                () -> {
                    if (r != null) room(p, r);
                    else main(p);
                });
    }

    private void rules(Player p, String kind, Runnable back) {
        String key =
                Set.of("go9", "go13").contains(kind)
                        ? "go"
                        : GameCatalog.IDS.contains(kind) && !GameCatalog.CLASSIC_IDS.contains(kind)
                                ? kind
                                : "default";
        Component text =
                Language.component("rules." + key);
        text =
                text.append(Component.newline())
                        .append(Component.newline())
                        .append(
                                Language.component(
                                        "rules.footer",
                                        "seconds",
                                        plugin.getConfig().getLong("reconnect-seconds", 120)));
        show(
                p,
                Language.component("rules.title", "game", RoomText.game(kind)),
                text,
                List.of(),
                back);
    }

    public void languageChanged() {
        Map.copyOf(sessions).forEach((id, session) -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                sessions.remove(id, session);
                return;
            }
            plugin.scheduler().execute(player, () -> {
                if (sessions.get(id) != session) return;
                NativeDialogs.clear(player);
                forget(player);
            });
        });
    }

    @Override
    public void close() {
        sessions.clear();
        categories.clear();
    }
}

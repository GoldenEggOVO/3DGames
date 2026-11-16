package GoldenEggOVO.TDGames;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.game.GameService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

public final class SessionTestSupport {
    public static TDGamesPlugin plugin() {
        var plugin = mock(TDGamesPlugin.class);
        install(plugin);
        return plugin;
    }

    public static GameService install(TDGamesPlugin plugin) {
        var service = plugin.games();
        if (service == null) {
            service = mock(GameService.class, withSettings().useConstructor(plugin)
                    .defaultAnswer(mockingDetails(plugin).getMockCreationSettings().getDefaultAnswer()));
            set(plugin, "games", service);
            doReturn(service).when(plugin).games();
            doCallRealMethod().when(service).activeSessions();
            doCallRealMethod().when(service).singleplayer();
            doCallRealMethod().when(service).multiplayer();
            doCallRealMethod().when(service).runtime();
            doReturn(field(service, "singleplayer")).when(plugin).singleplayer();
            doReturn(field(service, "multiplayer")).when(plugin).multiplayer();
        }
        return service;
    }

    public static GameService install(TDGamesPlugin plugin, Map<UUID, GameSession> rooms) {
        var service = install(plugin);
        var target = mutableSessions(plugin);
        target.clear();
        target.putAll(rooms);
        return service;
    }

    public static Map<UUID, GameSession> mutableSessions(TDGamesPlugin plugin) {
        return field(field(plugin.games(), "runtime"), "sessions");
    }

    public static GameService service(TDGamesPlugin plugin) { return field(plugin, "games"); }

    public static List<GameSession.Seat> mutableSeats(GameSession room) { return field(room, "members"); }
    public static Set<UUID> mutableReady(GameSession room) { return field(room, "confirmations"); }
    public static Map<UUID, Long> mutableOffline(GameSession room) { return field(room, "absences"); }
    public static Set<UUID> mutableDeparted(GameSession room) { return field(room, "departures"); }

    public static void bump(GameSession room) { set(room, "revision", room.revision() + 1); }

    public static void module(TDGamesPlugin plugin, String name, Object value) {
        set(plugin, name, value);
        switch (name) {
            case "menus" -> doCallRealMethod().when(plugin).menus();
            case "tableWorld" -> doCallRealMethod().when(plugin).tableWorld();
            case "tablePlayers" -> doCallRealMethod().when(plugin).tablePlayers();
            case "tableAccess" -> doCallRealMethod().when(plugin).tableAccess();
            case "pack" -> doCallRealMethod().when(plugin).pack();
            case "playerSessions" -> doCallRealMethod().when(plugin).sessions();
            case "integrations" -> doCallRealMethod().when(plugin).integrations();
            default -> throw new IllegalArgumentException(name);
        }
    }

    public static void set(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private SessionTestSupport() {}
}

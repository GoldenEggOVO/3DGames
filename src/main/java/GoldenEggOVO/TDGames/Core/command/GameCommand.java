package GoldenEggOVO.TDGames.Core.command;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameCommand implements Listener, CommandExecutor, TabCompleter {
    private final TDGamesPlugin plugin;

    public GameCommand(TDGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload-pack")) {
            if (sender instanceof Player player
                    && (!plugin.allowed(player) || !GoldenEggOVO.TDGames.Core.player.GamePermissions.admin(player))) {
                plugin.tell(player, Language.component("chat.permission"));
                return true;
            }
            plugin.pack().reload(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload-language")) {
            if (sender instanceof Player player
                    && (!plugin.allowed(player) || !GoldenEggOVO.TDGames.Core.player.GamePermissions.admin(player))) {
                plugin.tell(player, Language.component("chat.permission"));
                return true;
            }
            try {
                var config = new org.bukkit.configuration.file.YamlConfiguration();
                config.load(new File(plugin.getDataFolder(), "config.yml"));
                boolean changed =
                        Language.reload(
                                plugin.getDataFolder().toPath().resolve("languages"),
                                config.getString("language", "en_US"),
                                plugin.getLogger()::warning);
                if (changed && plugin.menus() != null) plugin.menus().languageChanged();
                sender.sendMessage(
                        Language.component(changed ? "language.reloaded" : "language.failed"));
            } catch (Exception ex) {
                plugin.getLogger().warning("Cannot reload languages: " + ex.getMessage());
                sender.sendMessage(Language.component("language.failed"));
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Language.component("chat.console"));
            if (args.length > 0 && args[0].equals("status"))
                sender.sendMessage(
                        Language.component(
                                "chat.status",
                                "rooms",
                                plugin.games().activeSessions().size()));
            return true;
        }
        if (!plugin.allowed(p)) {
            plugin.tell(p, Language.component("chat.permission"));
            return true;
        }
        try {
            String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "menu" -> plugin.menus().main(p);
                case "click" -> {
                    if (args.length == 3) plugin.menus().handle(p, args[1] + " " + args[2]);
                }
                case "move" -> {
                    if (args.length == 2) {
                        GameSession r = requireRoom(p);
                        plugin.games().action(p, r, r.revision(), new JsonPrimitive(args[1]));
                    }
                }
                case "resume" -> {
                    GameSession r = plugin.games().session(p);
                    if (r == null) {
                        if (plugin.games().hasSoloProgress(p.getUniqueId())) plugin.games().resumeSolo(p);
                        else plugin.menus().main(p);
                    } else plugin.games().resume(p, r);
                }
                case "create" -> {
                    if (args.length < 2) plugin.menus().main(p);
                    else {
                        String game = args[1].toLowerCase(Locale.ROOT);
                        plugin.games().create(
                                p,
                                game,
                                args.length > 2
                                        ? Integer.parseInt(args[2])
                                        : GameCatalog.defaultCapacity(game));
                    }
                }
                case "join" -> {
                    if (args.length < 2) plugin.menus().main(p);
                    else plugin.games().join(p, find(args[1]));
                }
                case "ready" -> {
                    GameSession r = requireRoom(p);
                    plugin.games().ready(p, r);
                }
                case "bots" -> {
                    GameSession r = requireRoom(p);
                    plugin.games().startWithBots(p, r);
                }
                case "leave" -> {
                    plugin.menus().confirmLeave(p);
                }
                case "rematch" -> plugin.games().rematch(p, requireRoom(p));
                case "rules" -> {
                    GameSession r = plugin.games().session(p);
                    String game = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                    plugin.menus().rules(
                            p,
                            GameCatalog.IDS.contains(game)
                                    ? game
                                    : r == null ? "gomoku" : r.kind);
                }
                default -> plugin.menus().main(p);
            }
        } catch (IllegalArgumentException ex) {
            plugin.tell(p, Language.error(ex));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1
                && List.of("reload-language", "reload-pack").stream().anyMatch(
                        value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                && (!(sender instanceof Player player)
                        || plugin.allowed(player) && GoldenEggOVO.TDGames.Core.player.GamePermissions.admin(player))) {
            var suggestions =
                    new ArrayList<>(
                            sender instanceof Player player
                                    ? CommandSuggestions.complete(
                                            args, plugin.multiplayer().rooms().values(), plugin.games().session(player), false, true)
                                    : CommandSuggestions.complete(
                                            args, plugin.multiplayer().rooms().values(), null, true, true));
            List.of("reload-language", "reload-pack").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .forEach(suggestions::add);
            return suggestions;
        }
        if (sender instanceof Player player) {
            GameSession own = plugin.games().session(player);
            if (args.length == 2 && args[0].equalsIgnoreCase("move") && own != null
                    && own.board() instanceof GoldenEggOVO.TDGames.Shared.game.ClassicGame classic)
                return classic.legalActions(own.seat(player.getUniqueId())).stream()
                        .filter(action -> action.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 2
                    && args[0].equalsIgnoreCase("move")
                    && own != null
                    && own.board() instanceof HandGame
                    && own.seat(player.getUniqueId()) != own.turn()) return List.of();
            return CommandSuggestions.complete(args, plugin.multiplayer().rooms().values(), own, false, plugin.allowed(player));
        }
        return CommandSuggestions.complete(args, plugin.multiplayer().rooms().values(), null, true, true);
    }

    @EventHandler
    public void commandSuggestions(PlayerCommandSendEvent event) {
        CommandSuggestions.hideDuplicateRoot(event.getCommands());
    }

    private GameSession requireRoom(Player p) {
        GameSession r = plugin.games().session(p);
        if (r == null) throw new RuleViolation("error.you-have-not-joined-a-room", "You have not joined a room");
        return r;
    }

    private GameSession find(String text) {
        String prefix = text.toLowerCase(Locale.ROOT);
        List<GameSession> matches = plugin.multiplayer().rooms().values().stream()
                .filter(room -> room.id.toString().startsWith(prefix)).limit(2).toList();
        if (matches.isEmpty()) throw new RuleViolation("error.room-closed", "Room closed");
        if (matches.size() > 1)
            throw new RuleViolation("error.room-prefix-ambiguous",
                    "Room code matches several rooms; use a longer code");
        return matches.getFirst();
    }

}

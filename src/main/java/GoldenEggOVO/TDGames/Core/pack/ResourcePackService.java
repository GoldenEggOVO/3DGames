package GoldenEggOVO.TDGames.Core.pack;

import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;
import GoldenEggOVO.TDGames.Core.platform.PlatformEvents;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;

import GoldenEggOVO.TDGames.Shared.render.packed.BoardModelKeys;

import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Core.render.ResourcePackState;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Core.text.Language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public final class ResourcePackService implements Listener, AutoCloseable, ResourcePackState {
    private static final byte[] RESOURCE_PACK_HASH = bundledHash();
    private static final Method VIRTUAL_HOST = virtualHostMethod();

    private static Method virtualHostMethod() {
        try {
            return Player.class.getMethod("getVirtualHost");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static byte[] bundledHash() {
        try (var input = ResourcePackService.class.getResourceAsStream("/resource-pack.sha1")) {
            if (input == null) throw new IOException("Missing generated resource-pack.sha1");
            byte[] hash =
                    HexFormat.of()
                            .parseHex(
                                    new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                                            .trim());
            if (hash.length != 20) throw new IOException("Invalid generated resource-pack SHA-1");
            return hash;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public enum Mode {
        VANILLA,
        RESOURCE_PACK,
        MIXED
    }

    private enum Stage {
        LOADING,
        READY,
        FAILED
    }

    private record Request(UUID id, Stage stage) {}

    private final TDGamesPlugin plugin;
    private final BooleanSupplier available;
    private final Function<String, ItemStack> models;
    private final Map<UUID, Request> requests = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> virtualHosts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> optedIn = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final NamespacedKey preference = new NamespacedKey("3dgames", "resource_pack");
    private final String source;
    private final Path localFolder;
    private final InetSocketAddress localAddress;
    private final String publicUrl;
    private volatile LocalResourcePack local;
    private volatile CraftEngineResourcePack engine;
    private volatile boolean closed;
    private boolean reloading;
    private PlatformScheduler.Task startup;
    public final Mode mode;

    public ResourcePackService(TDGamesPlugin plugin) {
        this(plugin, providers(plugin));
    }

    private record Providers(BooleanSupplier available, Function<String, ItemStack> models) {}

    private static Providers providers(TDGamesPlugin plugin) {
        if (plugin.getConfig().getString("rendering.resource-pack.source", "craftengine").equals("craftengine")) {
            var models = new CraftEngineModels();
            return new Providers(models::ready, models::item);
        }
        var models = new PackedModels();
        return new Providers(models::ready, models::item);
    }

    private ResourcePackService(TDGamesPlugin plugin, Providers providers) {
        this(plugin, providers.available(), providers.models());
    }

    public ResourcePackService(TDGamesPlugin plugin, BooleanSupplier available, Function<String, ItemStack> models) {
        this.plugin = plugin;
        this.available = available;
        this.models = models;
        mode =
                switch (plugin.getConfig().getString("rendering.mode", "auto")) {
                    case "auto" -> org.bukkit.Bukkit.getPluginManager().isPluginEnabled("CraftEngine")
                            ? Mode.MIXED : Mode.VANILLA;
                    case "resource-pack" -> Mode.RESOURCE_PACK;
                    case "mixed" -> Mode.MIXED;
                    case "vanilla" -> Mode.VANILLA;
                    default ->
                            throw new IllegalArgumentException(
                                    "rendering.mode must be auto, vanilla, resource-pack or mixed");
                };
        var config = plugin.getConfig();
        source = config.getString("rendering.resource-pack.source", "craftengine");
        if (!Set.of("craftengine", "local", "external").contains(source))
            throw new IllegalArgumentException("rendering.resource-pack.source must be craftengine, local or external");
        publicUrl = config.getString("rendering.resource-pack.local.public-url", "");
        if (mode != Mode.VANILLA && !source.equals("external")) {
            localFolder = plugin.getDataFolder().toPath().resolve("resource-pack");
            localAddress = source.equals("craftengine") ? null : new InetSocketAddress(
                    config.getString("rendering.resource-pack.local.bind-address", "0.0.0.0"),
                    config.getInt("rendering.resource-pack.local.port", 8137));
            try {
                local = createLocal();
                if (source.equals("craftengine"))
                    engine = new CraftEngineResourcePack(plugin, localFolder.resolveSibling("resource-pack.zip"));
            } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("Cannot initialize tabletop resource pack: " + exception.getMessage());
            }
        } else {
            localFolder = null;
            localAddress = null;
        }
    }

    private LocalResourcePack createLocal() throws IOException {
        LocalResourcePack.extractDefaults(localFolder,
                ResourcePackService.class.getResourceAsStream("/3DGames-resource-pack.zip"));
        return new LocalResourcePack(localFolder, localAddress);
    }

    public void start() {
        if (mode != Mode.VANILLA && source.equals("local") && publicUrl.isBlank() && VIRTUAL_HOST == null) {
            PlatformEvents.register(plugin, new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void login(PlayerLoginEvent event) {
                    if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
                    try {
                        String host = URI.create("http://" + event.getHostname()).getHost();
                        if (host != null) virtualHosts.put(event.getPlayer().getUniqueId(), host);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            });
        }
        if (engine == null) return;
        int[] attempts = {0};
        startup = plugin.scheduler().repeat(() -> {
                if (closed) { startup.cancel(); return; }
                try {
                    if (engine.ready()) {
                        startup.cancel();
                        reload(org.bukkit.Bukkit.getConsoleSender());
                    } else if (++attempts[0] >= 120) {
                        startup.cancel();
                        plugin.getLogger().warning("CraftEngine startup timed out; use 3dgames reload-pack after it is ready.");
                    }
                } catch (ReflectiveOperationException exception) {
                    startup.cancel();
                    plugin.getLogger().warning("Cannot prepare unified resource pack: " + exception.getMessage());
                }
        }, 20, 20);
    }

    public void reload(org.bukkit.command.CommandSender sender) {
        if (closed || localFolder == null || source.equals("craftengine")
                && !org.bukkit.Bukkit.getPluginManager().isPluginEnabled("CraftEngine") && engine == null) {
            PlatformMessages.send(sender, Language.component("pack.reload-unavailable"));
            return;
        }
        if (reloading) {
            PlatformMessages.send(sender, Language.component("pack.reload-busy"));
            return;
        }
        reloading = true;
        String previous = engine != null ? engine.hash() : local == null ? "" : local.current().sha1();
        PlatformMessages.send(sender, Language.component("pack.reloading"));
        plugin.scheduler().async(() -> {
            try {
                LocalResourcePack next = local;
                if (next == null) {
                    next = createLocal();
                    local = next;
                } else next.reload();
                if (closed) {
                    next.close();
                    return;
                }
                if (source.equals("craftengine") && engine == null)
                    engine = new CraftEngineResourcePack(plugin, localFolder.resolveSibling("resource-pack.zip"));
                if (closed) {
                    if (engine != null) engine.close();
                    next.close();
                    return;
                }
                String hash = engine == null ? next.current().sha1() : engine.rebuild(next.current());
                plugin.scheduler().execute(() -> {
                    reloading = false;
                    if (closed) return;
                    boolean changed = !previous.equals(hash);
                    if (changed) {
                        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                            plugin.scheduler().execute(player, () -> {
                                if (closed || !preferred(player)) return;
                                remove(player);
                                request(player);
                                if (plugin.menus() != null) plugin.menus().refresh(player);
                            });
                        }
                    }
                    reply(sender, changed ? "pack.reloaded" : "pack.reload-unchanged");
                });
            } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
                if (closed) return;
                plugin.getLogger().warning("Cannot reload tabletop resource pack: " + exception.getMessage());
                plugin.scheduler().execute(() -> {
                    reloading = false;
                    if (!closed) reply(sender, "pack.reload-failed");
                });
            }
        });
    }

    private String localUrl(Player player, LocalResourcePack resources, LocalResourcePack.Archive archive) {
        String base = publicUrl;
        if (base.isBlank()) {
            String host = virtualHosts.get(player.getUniqueId());
            try {
                Object address = VIRTUAL_HOST == null ? null : VIRTUAL_HOST.invoke(player);
                if (address instanceof InetSocketAddress socket) host = socket.getHostString();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Cannot read local resource-pack host", exception);
            }
            if (host == null || host.isBlank()) throw new IllegalArgumentException("Set local resource-pack public-url");
            try {
                base = new URI("http", null, host, resources.port(), "", null, null).toString();
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Invalid local resource-pack host", exception);
            }
        }
        return resources.url(base, archive);
    }

    public boolean hasToggle() {
        return mode == Mode.MIXED;
    }

    boolean preferred(Player player) {
        boolean preferred = mode == Mode.RESOURCE_PACK || mode == Mode.MIXED
                && player.getPersistentDataContainer().getOrDefault(preference, PersistentDataType.BYTE, (byte) 0) == 1;
        if (preferred) optedIn.add(player.getUniqueId());
        else optedIn.remove(player.getUniqueId());
        return preferred;
    }

    @Override
    public boolean managed() {
        return mode != Mode.VANILLA;
    }

    @Override
    public boolean required() {
        return mode == Mode.RESOURCE_PACK;
    }

    @Override
    public boolean packed(Player player) {
        Request r = requests.get(player.getUniqueId());
        return mode != Mode.VANILLA
                && (mode == Mode.RESOURCE_PACK || optedIn.contains(player.getUniqueId()))
                && r != null
                && r.stage() == Stage.READY
                && available.getAsBoolean();
    }

    public boolean canPlay(Player player, String kind) {
        return mode != Mode.RESOURCE_PACK || !supported(kind) || packed(player);
    }

    public static boolean supported(String kind) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.CLASSIC_IDS.contains(kind)) return true;
        return kind.equals("mahjong") || kind.equals("color-eight") || kind.equals("yacht") || BoardModelKeys.supported(kind)
                || Set.of("doudizhu", "liars-bar", "texas-holdem").contains(kind);
    }

    public ItemStack item(String id) {
        return models.apply(id);
    }

    public UUID requestId(Player player) {
        Request r = requests.get(player.getUniqueId());
        return r == null ? null : r.id();
    }

    public void require(Player player, String kind) {
        if (canPlay(player, kind)) return;
        Request r = requests.get(player.getUniqueId());
        if (r == null || r.stage() == Stage.FAILED) request(player);
        throw new RuleViolation("pack.required", "Load the tabletop resource pack before playing");
    }

    public Component button(Player player) {
        Request r = requests.get(player.getUniqueId());
        return Language.component(
                        r != null && r.stage() == Stage.LOADING
                                ? "pack.loading"
                                : packed(player) ? "pack.disable" : "pack.enable")
                .color(
                        r != null && r.stage() == Stage.LOADING
                                ? NamedTextColor.AQUA
                                : packed(player) ? NamedTextColor.GOLD : NamedTextColor.GREEN);
    }

    public void toggle(Player player) {
        if (!plugin.scheduler().isOwned(player)) {
            plugin.scheduler().execute(player, () -> toggle(player));
            return;
        }
        if (!hasToggle()) return;
        Request r = requests.get(player.getUniqueId());
        if (r != null && r.stage() != Stage.FAILED) {
            player.getPersistentDataContainer().set(preference, PersistentDataType.BYTE, (byte) 0);
            optedIn.remove(player.getUniqueId());
            remove(player);
        } else {
            player.getPersistentDataContainer().set(preference, PersistentDataType.BYTE, (byte) 1);
            optedIn.add(player.getUniqueId());
            request(player);
        }
        capture(player);
    }

    void request(Player player) {
        if (!plugin.scheduler().isOwned(player)) {
            plugin.scheduler().execute(player, () -> request(player));
            return;
        }
        if (closed || mode == Mode.VANILLA || !preferred(player)) return;
        Request existing = requests.get(player.getUniqueId());
        if (existing != null && existing.stage() != Stage.FAILED) return;
        if (!available.getAsBoolean()) {
            plugin.tell(player, Language.component("pack.unavailable"));
            return;
        }
        if (source.equals("craftengine")) {
            if (engine == null || engine.hash().isEmpty()) {
                plugin.tell(player, Language.component("pack.unavailable"));
                return;
            }
            remove(player);
            UUID id = UUID.randomUUID();
            requests.put(player.getUniqueId(), new Request(id, Stage.LOADING));
            engine.download(player.getUniqueId()).whenComplete((download, failure) -> {
                if (closed) return;
                plugin.scheduler().execute(player, () -> {
                    Request current = requests.get(player.getUniqueId());
                    if (closed || !player.isOnline() || !preferred(player)
                            || current == null || !current.id().equals(id)) return;
                    if (failure != null) {
                        failed(player, id);
                    } else {
                        try {
                            send(player, id, download.url(), HexFormat.of().parseHex(download.sha1()));
                        } catch (IllegalArgumentException exception) {
                            failed(player, id);
                        }
                    }
                    if (plugin.menus() != null) plugin.menus().refresh(player);
                });
            });
            return;
        }
        String url;
        byte[] hash;
        try {
            if (localFolder == null) {
                url = plugin.getConfig().getString("rendering.resource-pack.url", "");
                hash = RESOURCE_PACK_HASH.clone();
            } else {
                LocalResourcePack resources = local;
                if (resources == null) throw new IllegalArgumentException("Local resource pack is unavailable");
                LocalResourcePack.Archive archive = resources.current();
                url = localUrl(player, resources, archive);
                hash = HexFormat.of().parseHex(archive.sha1());
            }
            URI uri = URI.create(url);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            plugin.tell(player, Language.component("pack.configuration"));
            return;
        }
        remove(player);

        UUID id = UUID.randomUUID();
        requests.put(player.getUniqueId(), new Request(id, Stage.LOADING));
        send(player, id, url, hash);
    }

    private void send(Player player, UUID id, String url, byte[] hash) {
        try {
            URI uri = URI.create(url);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getHost() == null || hash.length != 20) throw new IllegalArgumentException();
            player.addResourcePack(
                    id,
                    url,
                    hash,
                    GoldenEggOVO.TDGames.Core.text.MessageText.plain(Language.component("pack.prompt")),
                    false);
        } catch (IllegalArgumentException ex) {
            failed(player, id);
        }
    }

    private void failed(Player player, UUID id) {
        requests.put(player.getUniqueId(), new Request(id, Stage.FAILED));
        plugin.tell(player, Language.component("pack.failed"));
        capture(player);
    }

    public void status(Player player, UUID id, PlayerResourcePackStatusEvent.Status status) {
        if (!plugin.scheduler().isOwned(player)) {
            plugin.scheduler().execute(player, () -> status(player, id, status));
            return;
        }
        Request r = requests.get(player.getUniqueId());
        if (r == null || !r.id().equals(id) || r.stage() == Stage.FAILED || !preferred(player))
            return;
        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                requests.put(player.getUniqueId(), new Request(id, Stage.READY));
                plugin.tell(player, Language.component("pack.ready"));
            }
            case ACCEPTED, DOWNLOADED -> {}
            default -> {
                requests.put(player.getUniqueId(), new Request(id, Stage.FAILED));
                plugin.tell(player, Language.component("pack.failed"));
            }
        }
        if (status != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && status != PlayerResourcePackStatusEvent.Status.DOWNLOADED
                && plugin.menus() != null) plugin.menus().refresh(player);
        capture(player);
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        status(event.getPlayer(), event.getID(), event.getStatus());
    }

    @EventHandler
    public void joined(PlayerJoinEvent event) {
        plugin.scheduler()
                .later(
                        event.getPlayer(),
                        () -> {
                            if (event.getPlayer().isOnline()) request(event.getPlayer());
                        },
                        20);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
        optedIn.remove(event.getPlayer().getUniqueId());
        virtualHosts.remove(event.getPlayer().getUniqueId());
    }

    private void remove(Player player) {
        Request r = requests.remove(player.getUniqueId());
        if (r != null) player.removeResourcePack(r.id());
        capture(player);
    }

    private void capture(Player player) {
        if (plugin.playerViews() != null) plugin.playerViews().capture(player);
    }

    private void reply(org.bukkit.command.CommandSender sender, String key) {
        if (sender instanceof Player player)
            plugin.scheduler().execute(player, () -> PlatformMessages.send(sender, Language.component(key)));
        else PlatformMessages.send(sender, Language.component(key));
    }

    @Override
    public void close() {
        closed = true;
        if (startup != null) startup.cancel();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            Request request = requests.remove(player.getUniqueId());
            if (request == null) continue;
            // Disabled plugins cannot submit region tasks; popping this owned pack only sends a packet.
            if (!plugin.isEnabled()) player.removeResourcePack(request.id());
            else plugin.scheduler().execute(player, () -> player.removeResourcePack(request.id()));
        }
        requests.clear();
        optedIn.clear();
        virtualHosts.clear();
        if (local != null) local.close();
        if (engine != null) engine.close();
    }
}

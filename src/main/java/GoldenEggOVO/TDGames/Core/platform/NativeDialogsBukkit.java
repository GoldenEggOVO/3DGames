package GoldenEggOVO.TDGames.Core.platform;

import GoldenEggOVO.TDGames.Core.menu.MenuLabels;
import GoldenEggOVO.TDGames.Core.menu.MenuView;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/** Uses the vanilla data format on Bukkit servers that expose registry-key dialogs only. */
final class NativeDialogsBukkit implements Listener {
    private static final Map<Plugin, NativeDialogsBukkit> INSTANCES = new WeakHashMap<>();
    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private record Session(UUID token, MenuView view, BiConsumer<String, Map<String, String>> dispatch) {}

    private NativeDialogsBukkit(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class<? extends Event> click = PlatformMessages.serverClass("org.bukkit.event.player.PlayerCustomClickEvent")
                    .asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(click, this, EventPriority.NORMAL, (listener, event) -> {
                synchronized (plugin) {
                    click((PlayerEvent) event);
                }
            }, plugin);
            Bukkit.getPluginManager().registerEvents(this, plugin);
        } catch (ReflectiveOperationException failure) {
            throw PlatformMessages.failure("This server does not expose native dialog click events", failure);
        }
    }

    static void open(Plugin plugin, Player player, MenuView view, BiConsumer<String, Map<String, String>> dispatch) {
        NativeDialogsBukkit bridge;
        synchronized (INSTANCES) {
            bridge = INSTANCES.computeIfAbsent(plugin, NativeDialogsBukkit::new);
        }
        UUID token = UUID.randomUUID();
        bridge.sessions.put(player.getUniqueId(), new Session(token, view, dispatch));
        try {
            Object dialog = decode(json(view, token).toString());
            Object holder = PlatformMessages.serverClass("net.minecraft.core.Holder").getMethod("direct", Object.class)
                    .invoke(null, dialog);
            PlatformMessages.nativeCall(PlatformMessages.handle(player), "openDialog", holder);
        } catch (ReflectiveOperationException failure) {
            bridge.sessions.remove(player.getUniqueId());
            throw PlatformMessages.failure("Could not open the native dialog", failure);
        } catch (RuntimeException failure) {
            bridge.sessions.remove(player.getUniqueId());
            throw failure;
        }
    }

    static Object decode(String json) throws ReflectiveOperationException {
        Class<?> ops = PlatformMessages.serverClass("com.mojang.serialization.DynamicOps");
        Object jsonOps = PlatformMessages.serverClass("com.mojang.serialization.JsonOps").getField("INSTANCE").get(null);
        // The server owns its Gson classes; exchange JSON text across the shaded dependency boundary.
        Object tree = PlatformMessages.serverClass(String.join(".", "com", "google", "gson", "JsonParser"))
                .getMethod("parseString", String.class).invoke(null, json);
        Object codec = PlatformMessages.serverClass("net.minecraft.server.dialog.Dialog").getField("DIRECT_CODEC").get(null);
        Object result = PlatformMessages.serverClass("com.mojang.serialization.Decoder")
                .getMethod("parse", ops, Object.class).invoke(codec, jsonOps, tree);
        return PlatformMessages.serverClass("com.mojang.serialization.DataResult").getMethod("getOrThrow").invoke(result);
    }

    static JsonObject json(MenuView view, UUID token) {
        JsonObject root = new JsonObject();
        root.add("title", component(view.title()));
        root.addProperty("can_close_with_escape", true);
        root.addProperty("pause", false);
        root.addProperty("after_action", "close");
        JsonArray body = new JsonArray();
        JsonArray actions = new JsonArray();
        for (int i = 0; i < view.buttons().size(); i++) {
            MenuView.Button button = view.buttons().get(i);
            if (NativeDialogs.header(view, button)) {
                JsonObject label = component(MenuLabels.caption(button)
                        .hoverEvent(button.tooltip() == null ? button.text() : button.tooltip()));
                if (button.action() != null) {
                    JsonObject click = new JsonObject();
                    click.addProperty("action", "custom");
                    click.addProperty("id", id(token, i));
                    label.add("click_event", click);
                }
                body.add(body(label, 300));
            } else actions.add(button(button, token, i, Math.min(view.columns() > 1 ? 144 : 300, button.width())));
        }
        for (var line : view.body())
            if (!MessageText.plain(line.text()).isBlank()) body.add(body(component(line.text()), Math.min(300, line.width())));
        root.add("body", body);
        JsonArray inputs = new JsonArray();
        for (var input : view.inputs()) {
            JsonObject value = new JsonObject();
            value.addProperty("type", "minecraft:text");
            value.addProperty("key", input.id());
            value.addProperty("width", 300);
            value.add("label", component(input.label()));
            value.addProperty("label_visible", true);
            value.addProperty("initial", input.value());
            value.addProperty("max_length", input.maxLength());
            inputs.add(value);
        }
        root.add("inputs", inputs);
        JsonObject exit = button(view.exit(), token, view.buttons().size(), Math.min(300, view.exit().width()));
        if (actions.isEmpty()) {
            root.addProperty("type", "minecraft:notice");
            root.add("action", exit);
        } else {
            root.addProperty("type", "minecraft:multi_action");
            root.add("actions", actions);
            root.add("exit_action", exit);
            root.addProperty("columns", view.columns());
        }
        return root;
    }

    private static JsonObject body(JsonObject text, int width) {
        JsonObject body = new JsonObject();
        body.addProperty("type", "minecraft:plain_message");
        body.add("contents", text);
        body.addProperty("width", width);
        return body;
    }

    private static JsonObject button(MenuView.Button button, UUID token, int index, int width) {
        JsonObject value = new JsonObject();
        value.add("label", component(MenuLabels.caption(button)));
        if (button.tooltip() != null) value.add("tooltip", component(button.tooltip()));
        value.addProperty("width", width);
        if (button.action() != null) {
            JsonObject action = new JsonObject();
            action.addProperty("type", "minecraft:dynamic/custom");
            action.addProperty("id", id(token, index));
            value.add("action", action);
        }
        return value;
    }

    private static JsonObject component(Component component) {
        var value = JsonParser.parseString(PlatformMessages.json(component));
        if (value.isJsonObject()) return value.getAsJsonObject();
        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        JsonArray extra = new JsonArray();
        extra.add(value);
        root.add("extra", extra);
        return root;
    }

    private static String id(UUID token, int index) {
        return "3dgames:dialog/" + token + "/" + index;
    }

    private void click(PlayerEvent event) {
        try {
            NamespacedKey id = (NamespacedKey) event.getClass().getMethod("getId").invoke(event);
            if (!id.getNamespace().equals("3dgames")) return;
            Session session = sessions.get(event.getPlayer().getUniqueId());
            if (session == null) return;
            String prefix = "dialog/" + session.token() + "/";
            if (!id.getKey().startsWith(prefix)) return;
            int index;
            try {
                index = Integer.parseInt(id.getKey().substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                return;
            }
            MenuView view = session.view();
            if (index < 0 || index > view.buttons().size()) return;
            MenuView.Button button = index == view.buttons().size() ? view.exit() : view.buttons().get(index);
            if (button.action() == null) return;
            Map<String, String> values = new HashMap<>();
            if (!NativeDialogs.header(view, button)) {
                Object raw = event.getClass().getMethod("getData").invoke(event);
                if (raw != null) {
                    var payload = JsonParser.parseString(raw.toString());
                    if (!payload.isJsonObject()) return;
                    for (var input : view.inputs()) {
                        var value = payload.getAsJsonObject().get(input.id());
                        if (value == null) continue;
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return;
                        String text = value.getAsString();
                        if (text.length() > input.maxLength()) return;
                        values.put(input.id(), text);
                    }
                }
            }
            sessions.remove(event.getPlayer().getUniqueId());
            session.dispatch().accept(button.action(), Map.copyOf(values));
        } catch (ReflectiveOperationException failure) {
            throw PlatformMessages.failure("Could not read the native dialog response", failure);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        synchronized (plugin) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void disable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        synchronized (plugin) {
            sessions.clear();
            HandlerList.unregisterAll(this);
            synchronized (INSTANCES) {
                INSTANCES.remove(plugin);
            }
        }
    }
}

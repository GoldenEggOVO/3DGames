package GoldenEggOVO.TDGames.Core.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

public final class PlatformMessages {
    private record MethodKey(String name, List<Class<?>> arguments) {}
    private static final ClassValue<Map<MethodKey, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<MethodKey, Method> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private PlatformMessages() {}

    public static void send(CommandSender sender, Component message) {
        if (sender instanceof Audience audience) audience.sendMessage(message);
        else if (sender instanceof Player player) nativeCall(handle(player), "sendSystemMessage", nativeText(message));
        else sender.sendMessage(legacy(message));
    }

    public static void actionBar(Player player, Component message) {
        if (player instanceof Audience audience) audience.sendActionBar(message);
        else nativeCall(handle(player), "sendOverlayMessage", nativeText(message));
    }

    public static void text(TextDisplay display, Component message) {
        if (display instanceof Audience) display.text(message);
        else nativeCall(handle(display), "setText", nativeText(message));
    }

    public static Component text(TextDisplay display) {
        if (display instanceof Audience) return display.text();
        return component(nativeCall(handle(display), "getText"));
    }

    public static Component displayName(Player player) {
        if (player instanceof Audience) return player.displayName();
        return LegacyComponentSerializer.legacySection().deserialize(player.getDisplayName());
    }

    public static void customName(Entity entity, Component message) {
        if (entity instanceof Audience) entity.customName(message);
        else nativeCall(handle(entity), "setCustomName", message == null ? null : nativeText(message));
    }

    static String json(Component message) {
        return GsonComponentSerializer.gson().serialize(message);
    }

    private static String legacy(Component message) {
        return LegacyComponentSerializer.legacySection().serialize(message);
    }

    static Object handle(Object craftObject) {
        return nativeCall(craftObject, "getHandle");
    }

    private static Object nativeText(Component message) {
        try {
            return Chat.FROM_JSON.invoke(null, json(message));
        } catch (ReflectiveOperationException failure) {
            throw failure("Could not convert a component for this server", failure);
        }
    }

    private static Component component(Object nativeText) {
        try {
            String json = (String) Chat.TO_JSON.invoke(null, nativeText);
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (ReflectiveOperationException failure) {
            throw failure("Could not read a component from this server", failure);
        }
    }

    private static final class Chat {
        private static final Method FROM_JSON;
        private static final Method TO_JSON;

        static {
            try {
                Class<?> chat = serverClass(Bukkit.getServer().getClass().getPackageName() + ".util.CraftChatMessage");
                FROM_JSON = chat.getMethod("fromJSON", String.class);
                TO_JSON = chat.getMethod("toJSON", serverClass("net.minecraft.network.chat.Component"));
            } catch (ReflectiveOperationException failure) {
                throw failure("Could not locate the server component converter", failure);
            }
        }
    }

    static Class<?> serverClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, Bukkit.getServer().getClass().getClassLoader());
    }

    static Object nativeCall(Object target, String name, Object... arguments) {
        var key = new MethodKey(name, Arrays.stream(arguments).map(value -> value == null ? Void.class : value.getClass()).toList());
        Method method = METHODS.get(target.getClass()).computeIfAbsent(key, ignored -> method(target.getClass(), name, arguments));
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw failure("Could not invoke native method " + name, failure);
        }
    }

    private static Method method(Class<?> type, String name, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            boolean compatible = true;
            for (int i = 0; i < arguments.length; i++)
                if (arguments[i] != null && !method.getParameterTypes()[i].isInstance(arguments[i])) compatible = false;
            if (!compatible) continue;
            return method;
        }
        throw new IllegalStateException("Missing native method " + type.getName() + "." + name);
    }

    static IllegalStateException failure(String message, ReflectiveOperationException failure) {
        return new IllegalStateException(message,
                failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
    }
}

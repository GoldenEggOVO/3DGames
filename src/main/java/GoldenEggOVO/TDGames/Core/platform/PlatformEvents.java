package GoldenEggOVO.TDGames.Core.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class PlatformEvents {
    private PlatformEvents() {}

    public static void register(Plugin plugin, Listener listener) {
        for (Method method : listener.getClass().getMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            Class<? extends Event> type = method.getParameterTypes()[0].asSubclass(Event.class);
            method.trySetAccessible();
            Bukkit.getPluginManager().registerEvent(type, listener, handler.priority(), (registered, event) -> {
                if (!type.isInstance(event)) return;
                synchronized (plugin) {
                    try {
                        method.invoke(listener, event);
                    } catch (InvocationTargetException failure) {
                        throw new EventException(failure.getCause());
                    } catch (ReflectiveOperationException failure) {
                        throw new EventException(failure);
                    }
                }
            }, plugin, handler.ignoreCancelled());
        }
    }
}

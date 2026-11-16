package GoldenEggOVO.TDGames.Core.render;

import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.*;

public final class DisplayAudience {
    private final TDGamesPlugin plugin;
    private final Location origin;
    private final boolean enabled;
    private final boolean nativeFallback;
    private Set<Player> nativeViewers = Set.of(), packViewers = Set.of(), allViewers = Set.of();
    private final Map<Entity, Boolean> layers = new HashMap<>();
    public long generation;

    public DisplayAudience(TDGamesPlugin plugin, Location origin) {
        this(plugin, origin, true);
    }

    public DisplayAudience(TDGamesPlugin plugin, Location origin, boolean enabled) {
        this(plugin, origin, enabled, false);
    }

    public static DisplayAudience withNativeFallback(TDGamesPlugin plugin, Location origin) {
        return new DisplayAudience(plugin, origin, true, true);
    }

    private DisplayAudience(TDGamesPlugin plugin, Location origin, boolean enabled, boolean nativeFallback) {
        this.plugin = plugin;
        this.origin = origin;
        this.enabled = enabled;
        this.nativeFallback = nativeFallback;
        refresh();
    }

    public boolean managed() {
        ResourcePackState pack = plugin.resourcePackState();
        return enabled && pack != null && pack.managed();
    }

    public boolean packed(Player player) {
        ResourcePackState pack = plugin.resourcePackState();
        return pack != null && pack.packed(player);
    }

    public boolean needed(boolean packed) {
        return !managed() ? !packed : !viewers(packed).isEmpty();
    }

    public Set<Player> viewers(boolean packed) {
        return packed ? packViewers : nativeViewers;
    }

    public Set<Player> all() {
        return allViewers;
    }

    public void refresh() {
        boolean managed = managed();
        if (!managed && !nativeFallback) return;
        Set<Player> natives = new HashSet<>(), packs = new HashSet<>();

        for (Player player : origin.getWorld().getPlayers()) {
            if (!player.isOnline() || !plugin.allowed(player)
                    || player.getLocation().distanceSquared(origin) > 24 * 24) continue;
            if (managed && packed(player)) packs.add(player);
            else if (nativeFallback || !plugin.resourcePackState().required()) natives.add(player);
        }
        if (natives.equals(nativeViewers) && packs.equals(packViewers)) return;
        Set<Player> oldNative = nativeViewers, oldPacked = packViewers, oldAll = allViewers;
        nativeViewers = Set.copyOf(natives);
        packViewers = Set.copyOf(packs);
        natives.addAll(packs);
        allViewers = Set.copyOf(natives);
        generation++;
        for (var entry : layers.entrySet()) {
            Boolean packed = entry.getValue();
            Set<Player> before = packed == null ? oldAll : packed ? oldPacked : oldNative;
            Set<Player> after = packed == null ? allViewers : viewers(packed);
            for (Player player : before)
                if (!after.contains(player)) player.hideEntity(plugin, entry.getKey());
            for (Player player : after)
                if (!before.contains(player)) player.showEntity(plugin, entry.getKey());
        }
    }

    public void add(Entity entity, boolean packed) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, packed);
        for (Player player : viewers(packed)) player.showEntity(plugin, entity);
    }

    public void common(Entity entity) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, null);
        for (Player player : allViewers) player.showEntity(plugin, entity);
    }

    public void remove(Entity entity) {
        layers.remove(entity);
        entity.remove();
    }
}

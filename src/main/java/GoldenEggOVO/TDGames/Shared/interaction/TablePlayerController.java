package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

public final class TablePlayerController implements Listener, AutoCloseable {
    private final TDGamesPlugin plugin;
    private final Map<UUID, Boolean> previousCollidableState = new HashMap<>();

    private record Focus(
            GameSession room,
            int seat,
            Location original,
            Location anchor,
            boolean gravity,
            boolean invisible,
            GameMode mode,
            boolean allowFlight,
            boolean flying,
            ArmorStand camera) {}

    private record DirectionalControl(GameSession room, DirectionalInput keys) { }
    private final Map<UUID, DirectionalControl> directionalPlayers = new HashMap<>();

    private final Map<UUID, Focus> focusedPlayers = new HashMap<>();
    private final Map<UUID, Location> internalTeleports = new HashMap<>();
    private final Set<UUID> sprintHeld = new HashSet<>();
    private final Set<UUID> closingMenus = new HashSet<>();
    private final Set<UUID> internalModeChanges = new HashSet<>();
    private static final NamespacedKey FOCUS_RETURN_KEY =
            new NamespacedKey("3dgames", "focus-return");

    public TablePlayerController(TDGamesPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            recoverFocusReturnState(player);
        }
    }

    private boolean isNearJoinedTable(Player player) {
        GameSession room = plugin.games().session(player);
        return room != null
                && plugin.allowed(player)
                && plugin.tableWorld().atTableWorld(player, room)
                && player.getLocation().distanceSquared(plugin.tableWorld().center(room.table)) <= 36;
    }

    public void syncPlayerState() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerState(player);
        }
    }

    private void syncPlayerState(Player player) {
        DirectionalControl directional = directionalPlayers.get(player.getUniqueId());
        if (directional != null && !validDirectional(player, directional)) releaseFocus(player);
        Focus current = focusedPlayers.get(player.getUniqueId());
        if (current != null
                && ((!current.room().kind.equals("2048") && !sprintHeld.contains(player.getUniqueId()))
                        || (current.room().kind.equals("2048") && !directionalPlayers.containsKey(player.getUniqueId()))
                        || !isFocusValid(player, current)
                        || !atAnchor(player, current))) {
            finishFocus(player, atAnchor(player, current));
        }
        if (hasTableFocus(player)) {
            hideFocusedPlayer(player);
        }
        if (!isNearJoinedTable(player)) {
            restoreCollision(player);
            return;
        }
        if (!previousCollidableState.containsKey(player.getUniqueId())) {
            boolean collidable = player.isCollidable();
            previousCollidableState.put(player.getUniqueId(), collidable);
            if (collidable) {
                player.setCollidable(false);
            }
        }
    }

    public boolean hasTableFocus(Player player) {
        return focusedPlayers.containsKey(player.getUniqueId());
    }

    public void releaseFocus(Player player) {
        directionalPlayers.remove(player.getUniqueId());
        finishFocus(player, true);
    }

    public boolean startDirectionalControl(Player player) {
        GameSession room = plugin.games().session(player);
        if (room == null || !room.kind.equals("2048") || room.phase() != GameSession.Phase.PLAYING
                || room.seat(player.getUniqueId()) != 0 || !isNearJoinedTable(player)
                || player.isDead() || player.isInsideVehicle() || player.getGameMode() == GameMode.SPECTATOR
                || plugin.menus() != null && plugin.menus().active(player)) return false;
        beginFocus(player);
        if (!hasTableFocus(player)) return false;
        var keys = new DirectionalInput();
        if (player.getCurrentInput() != null) keys.press(player.getCurrentInput());
        directionalPlayers.put(player.getUniqueId(), new DirectionalControl(room, keys));
        player.setVelocity(new Vector());
        player.sendActionBar(GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.controls"));
        return true;
    }

    private boolean validDirectional(Player player, DirectionalControl state) {
        Focus focus = focusedPlayers.get(player.getUniqueId());
        return focus != null && focus.room() == state.room() && isFocusValid(player, focus) && atAnchor(player, focus);
    }

    public void menuClosed(Player player) {
        if (sprintHeld.contains(player.getUniqueId())) {
            closingMenus.add(player.getUniqueId());
        }
    }

    private boolean isFocusValid(Player player, Focus state) {
        return !player.isDead()
                && !player.isInsideVehicle()
                && player.getGameMode() == GameMode.SPECTATOR
                && state.camera().isValid()
                && player.getSpectatorTarget() == state.camera()
                && (plugin.menus() == null || !plugin.menus().active(player))
                && (!state.room().kind.equals("2048") || state.room().phase() == GameSession.Phase.PLAYING)
                && plugin.games().session(player) == state.room()
                && state.room().seat(player.getUniqueId()) == state.seat()
                && isNearJoinedTable(player);
    }

    private static boolean atAnchor(Player player, Focus state) {
        Location now = player.getLocation();
        return now.getWorld().equals(state.anchor().getWorld())
                && now.distanceSquared(state.anchor()) < .01;
    }

    private void beginFocus(Player player) {
        GameSession room = plugin.games().session(player);
        if (room == null
                || !(room.kind.equals("mahjong") || room.kind.equals("2048") && room.phase() == GameSession.Phase.PLAYING)
                || room.seat(player.getUniqueId()) < 0
                || !isNearJoinedTable(player)
                || player.isDead()
                || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int seat = room.seat(player.getUniqueId());
        double angle = 2 * Math.PI * seat / room.capacity;
        Location anchor =
                plugin.tableWorld()
                        .center(room.table)
                        .clone()
                        .add(Math.sin(angle) * .65, .88 + 1.27, Math.cos(angle) * .65);

        anchor.setDirection(
                new Vector(
                        -Math.sin(angle) * .65,
                        TableGeometry.SURFACE + .05 - .88 - 1.27,
                        -Math.cos(angle) * .65));
        if (room.kind.equals("2048")) {
            anchor = plugin.tableWorld().center(room.table).clone().add(0, 3.2, 0);
            anchor.setYaw(180);
            anchor.setPitch(90);
        }
        if (!clearCamera(anchor)) {
            return;
        }
        Location cameraAnchor = anchor;
        ArmorStand camera =
                anchor.getWorld()
                        .spawn(
                                anchor,
                                ArmorStand.class,
                                cameraEntity -> {
                                    cameraEntity.setVisibleByDefault(false);
                                    cameraEntity.setPersistent(false);
                                    cameraEntity.setGravity(false);
                                    cameraEntity.setInvulnerable(true);
                                    cameraEntity.setMarker(true);
                                    cameraEntity.setVisible(false);
                                    cameraEntity.setSilent(true);
                                    cameraEntity.setRotation(cameraAnchor.getYaw(), cameraAnchor.getPitch());
                                });
        Focus state =
                new Focus(
                        room,
                        seat,
                        player.getLocation().clone(),
                        anchor,
                        player.hasGravity(),
                        player.isInvisible(),
                        player.getGameMode(),
                        player.getAllowFlight(),
                        player.isFlying(),
                        camera);
        saveFocusReturnState(player, state);
        syncPlayerState(player);
        focusedPlayers.put(player.getUniqueId(), state);
        player.setGravity(false);
        player.setInvisible(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        hideFocusedPlayer(player);
        try {
            changeMode(player, GameMode.SPECTATOR);
            if (player.getGameMode() != GameMode.SPECTATOR) {
                finishFocus(player, false);
                return;
            }
            if (!teleport(player, anchor) || !atAnchor(player, state)) {
                finishFocus(player, false);
                return;
            }
            player.showEntity(plugin, camera);
            internalTeleports.put(player.getUniqueId(), anchor);
            try {
                player.setSpectatorTarget(camera);
            } finally {
                internalTeleports.remove(player.getUniqueId());
            }
            if (player.getSpectatorTarget() != camera) {
                finishFocus(player, true);
            }
        } catch (RuntimeException exception) {
            finishFocus(player, true);
            throw exception;
        }
    }

    private static boolean clearCamera(Location target) {
        if (!target.getBlock().isPassable()) {
            return false;
        }
        for (double x : new double[] {-.3, .3}) {
            for (double z : new double[] {-.3, .3}) {
                if (!target.clone().add(x, 0, z).getBlock().isPassable()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean clear(Location target) {
        for (double x : new double[] {-.3, .3}) {
            for (double z : new double[] {-.3, .3}) {
                for (double y : new double[] {0, .9, 1.8}) {
                    if (!target.clone().add(x, y, z).getBlock().isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean teleport(Player player, Location target) {
        internalTeleports.put(player.getUniqueId(), target);
        try {
            return player.teleport(target);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private void finishFocus(Player player, boolean returnToSeat) {
        finishFocus(player, returnToSeat, true, returnToSeat && !player.isDead());
    }

    private void finishFocus(Player player, boolean returnToSeat, boolean restoreMode) {
        finishFocus(player, returnToSeat, restoreMode, false);
    }

    private void finishFocus(Player player, boolean returnToSeat, boolean restoreMode, boolean retry) {
        directionalPlayers.remove(player.getUniqueId());
        Focus state = focusedPlayers.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
            if (restoreMode) {
                changeMode(player, state.mode());
                if (player.getGameMode() == GameMode.SPECTATOR && retry) {
                    focusedPlayers.put(player.getUniqueId(), state);
                    internalTeleports.put(player.getUniqueId(), state.anchor());
                    try {
                        player.setSpectatorTarget(state.camera());
                    } finally {
                        internalTeleports.remove(player.getUniqueId());
                    }
                    return;
                }
                if (player.getGameMode() == state.mode()) {
                    player.setAllowFlight(state.allowFlight());
                    player.setFlying(state.flying());
                }
            }
        }
        state.camera().remove();
        player.setGravity(state.gravity());
        player.setInvisible(state.invisible());
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.showPlayer(plugin, player);
            }
        }
        if (returnToSeat
                && !player.isDead()
                && atAnchor(player, state)
                && clear(state.original())) {
            teleport(player, state.original());
        }
        if (!restoreMode || player.getGameMode() != GameMode.SPECTATOR) {
            player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
        }
    }

    private void saveFocusReturnState(Player player, Focus state) {
        var data = new com.google.gson.JsonObject();
        Location originalLocation = state.original();
        data.addProperty("mode", state.mode().name());
        data.addProperty("gravity", state.gravity());
        data.addProperty("invisible", state.invisible());
        data.addProperty("allowFlight", state.allowFlight());
        data.addProperty("flying", state.flying());
        data.addProperty(
                "collidable",
                previousCollidableState.getOrDefault(player.getUniqueId(), player.isCollidable()));
        data.addProperty("world", originalLocation.getWorld().getUID().toString());
        data.addProperty("x", originalLocation.getX());
        data.addProperty("y", originalLocation.getY());
        data.addProperty("z", originalLocation.getZ());
        data.addProperty("yaw", originalLocation.getYaw());
        data.addProperty("pitch", originalLocation.getPitch());
        player.getPersistentDataContainer()
                .set(FOCUS_RETURN_KEY, PersistentDataType.STRING, data.toString());
    }

    private void recoverFocusReturnState(Player player) {
        if (hasTableFocus(player)) {
            return;
        }
        String saved =
                player.getPersistentDataContainer()
                        .get(FOCUS_RETURN_KEY, PersistentDataType.STRING);
        if (saved == null) {
            return;
        }
        try {
            var data = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGravity(data.get("gravity").getAsBoolean());
                player.setInvisible(data.get("invisible").getAsBoolean());
                player.setCollidable(data.get("collidable").getAsBoolean());
                player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
                return;
            }
            GameMode mode = GameMode.valueOf(data.get("mode").getAsString());
            var world = Bukkit.getWorld(UUID.fromString(data.get("world").getAsString()));
            Location back =
                    new Location(
                            world,
                            data.get("x").getAsDouble(),
                            data.get("y").getAsDouble(),
                            data.get("z").getAsDouble(),
                            data.get("yaw").getAsFloat(),
                            data.get("pitch").getAsFloat());
            player.setSpectatorTarget(null);
            changeMode(player, mode);
            if (player.getGameMode() != mode) {
                return;
            }
            player.setAllowFlight(data.get("allowFlight").getAsBoolean());
            player.setFlying(data.get("flying").getAsBoolean());
            player.setGravity(data.get("gravity").getAsBoolean());
            player.setInvisible(data.get("invisible").getAsBoolean());
            player.setCollidable(data.get("collidable").getAsBoolean());
            if (world != null && clear(back)) {
                teleport(player, back);
            }
            player.getPersistentDataContainer().remove(FOCUS_RETURN_KEY);
        } catch (IllegalArgumentException
                | IllegalStateException
                | NullPointerException exception) {
            plugin.getLogger()
                    .warning(
                            "Could not restore saved table camera state for "
                                    + player.getUniqueId());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        recoverFocusReturnState(event.getPlayer());
    }

    private void changeMode(Player player, GameMode mode) {
        internalModeChanges.add(player.getUniqueId());
        try {
            player.setGameMode(mode);
        } finally {
            internalModeChanges.remove(player.getUniqueId());
        }
    }

    private void hideFocusedPlayer(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    @EventHandler
    public void input(PlayerInputEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (plugin.menus() != null && plugin.menus().active(player)) {
            releaseFocus(player);
            return;
        }
        boolean sprintPressed = event.getInput().isSprint() && sprintHeld.add(id);
        if (!event.getInput().isSprint()) sprintHeld.remove(id);
        DirectionalControl directional = directionalPlayers.get(id);
        if (directional != null) {
            if (!validDirectional(player, directional) || sprintPressed) {
                releaseFocus(player);
                player.sendActionBar(GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.released"));
            } else {
                String direction = directional.keys().press(event.getInput());
                if (direction != null) {
                    String action = "slide:" + direction;
                    if (directional.room().board().legalActions(0).contains(action)) {
                        try {
                            directional.room().requireAction(player.getUniqueId(), directional.room().revision());
                            plugin.games().applyTableAction(directional.room(), 0, new com.google.gson.JsonPrimitive(action), player);
                        } catch (IllegalArgumentException exception) {
                            plugin.tell(player, GoldenEggOVO.TDGames.Core.text.Language.component("chat.changed"));
                        }
                    } else player.sendActionBar(GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.blocked"));
                }
            }
            return;
        }
        if (sprintPressed) {
            GameSession room = plugin.games().session(player);
            if (room != null && room.kind.equals("2048")) {
                if (startDirectionalControl(player)) directionalPlayers.get(id).keys().press(event.getInput());
            } else beginFocus(player);
        } else {
            if (!event.getInput().isSprint()) releaseFocus(player);
        }
    }

    @EventHandler
    public void clientTick(io.papermc.paper.event.packet.ClientTickEndEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!closingMenus.remove(id) || plugin.menus() != null && plugin.menus().active(player)) {
            return;
        }
        if (!player.getCurrentInput().isSprint()) {
            sprintHeld.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Player player = event.getPlayer();
        Focus state = focusedPlayers.get(player.getUniqueId());
        if (state == null || event.getTo() == null) {
            return;
        }
        if (!isFocusValid(player, state)) {
            releaseFocus(player);
            event.setTo(player.getLocation());
            return;
        }

        event.setCancelled(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void stopCamera(
            com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
        if (hasTableFocus(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void switchCamera(
            com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        Focus state = focusedPlayers.get(event.getPlayer().getUniqueId());
        if (state != null && !event.getNewSpectatorTarget().equals(state.camera())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        if (internalModeChanges.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        finishFocus(event.getPlayer(), false, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spectatorTeleport(PlayerTeleportEvent event) {
        if (!hasTableFocus(event.getPlayer())
                || event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Location expected = internalTeleports.get(event.getPlayer().getUniqueId());
        if (expected == null || !expected.equals(event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void externalTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location expected = internalTeleports.get(player.getUniqueId());
        if (expected != null && expected.equals(event.getTo())) {
            return;
        }
        directionalPlayers.remove(player.getUniqueId());
        finishFocus(player, false);
        restoreCollision(player);
    }

    private void restoreCollision(Player player) {
        Boolean value = previousCollidableState.remove(player.getUniqueId());
        if (value != null) {
            player.setCollidable(value);
        }
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isNearJoinedTable(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent event) {
        directionalPlayers.remove(event.getPlayer().getUniqueId());
        finishFocus(event.getPlayer(), false);
        restoreCollision(event.getPlayer());
        syncPlayerState(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        directionalPlayers.remove(event.getPlayer().getUniqueId());
        finishFocus(event.getPlayer(), true, true, false);
        restoreCollision(event.getPlayer());
        sprintHeld.remove(event.getPlayer().getUniqueId());
        closingMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        directionalPlayers.remove(event.getEntity().getUniqueId());
        finishFocus(event.getEntity(), false);
        restoreCollision(event.getEntity());
        sprintHeld.remove(event.getEntity().getUniqueId());
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            finishFocus(player, true, true, false);
            restoreCollision(player);
        }
        directionalPlayers.clear();
        focusedPlayers.clear();
        sprintHeld.clear();
        closingMenus.clear();
        previousCollidableState.clear();
    }
}

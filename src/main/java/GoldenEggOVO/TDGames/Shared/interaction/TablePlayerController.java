package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.platform.PlatformEvents;
import GoldenEggOVO.TDGames.Core.platform.PlatformScheduler;
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
    private final PlatformScheduler scheduler;
    private final PlatformScheduler.Task fallbackTick;
    private final boolean paperCameraEvents;
    private boolean closed;
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
    private final Map<UUID, Focus> startingFocus = new HashMap<>();
    private static final NamespacedKey FOCUS_RETURN_KEY =
            new NamespacedKey("3dgames", "focus-return");

    public TablePlayerController(TDGamesPlugin plugin) {
        this(plugin, true);
    }

    TablePlayerController(TDGamesPlugin plugin, boolean paperEventsAvailable) {
        this.plugin = plugin;
        this.scheduler = plugin.scheduler() == null ? new PlatformScheduler(plugin) : plugin.scheduler();
        PlatformEvents.register(plugin, this);
        paperCameraEvents = paperEventsAvailable && registerPaperCameraEvents();
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.execute(player, () -> recoverFocusReturnState(player));
        }
        fallbackTick = paperCameraEvents ? () -> {} : scheduler.repeat(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.execute(player, () -> {
                    if (closed) return;
                    clientTick(player);
                    Focus focus = focusedPlayers.get(player.getUniqueId());
                    if (focus != null && !startingFocus.containsKey(player.getUniqueId())
                            && player.getGameMode() == GameMode.SPECTATOR && atAnchor(player, focus)
                            && player.getSpectatorTarget() != focus.camera()) attachCamera(player, focus);
                });
            }
        }, 1, 1);
    }

    private boolean registerPaperCameraEvents() {
        try {
            Class.forName("io.papermc.paper.event.packet.ClientTickEndEvent");
            Class.forName("com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent");
            Class.forName("com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent");
            PlatformEvents.register(plugin, new PaperCameraEvents(this));
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
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
            scheduler.execute(player, () -> { if (!closed) syncPlayerState(player); });
        }
    }

    private void syncPlayerState(Player player) {
        DirectionalControl directional = directionalPlayers.get(player.getUniqueId());
        if (directional != null && !validDirectional(player, directional)) releaseFocus(player);
        Focus current = focusedPlayers.get(player.getUniqueId());
        if (current != null && !startingFocus.containsKey(player.getUniqueId())
                && ((!current.room().kind.equals("2048") && !sprintHeld.contains(player.getUniqueId()))
                        || (current.room().kind.equals("2048") && !directionalPlayers.containsKey(player.getUniqueId()))
                        || !atAnchor(player, current)
                        || !isFocusValid(player, current))) {
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
        if (!scheduler.isOwned(player)) {
            scheduler.execute(player, () -> releaseFocus(player));
            return;
        }
        directionalPlayers.remove(player.getUniqueId());
        finishFocus(player, true);
    }

    public boolean startDirectionalControl(Player player) {
        if (!scheduler.isOwned(player)) {
            scheduler.execute(player, () -> startDirectionalControl(player));
            return true;
        }
        GameSession room = plugin.games().session(player);
        if (room == null || !room.kind.equals("2048") || room.phase() != GameSession.Phase.PLAYING
                || room.seat(player.getUniqueId()) != 0 || !isNearJoinedTable(player)
                || player.isDead() || player.isInsideVehicle() || player.getGameMode() == GameMode.SPECTATOR
                || plugin.menus() != null && plugin.menus().active(player)) return false;
        return beginFocus(player, () -> {
            var keys = new DirectionalInput();
            if (player.getCurrentInput() != null) keys.press(player.getCurrentInput());
            directionalPlayers.put(player.getUniqueId(), new DirectionalControl(room, keys));
            player.setVelocity(new Vector());
            GoldenEggOVO.TDGames.Core.platform.PlatformMessages.actionBar(player,
                    GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.controls"));
        });
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
                && scheduler.isOwned(state.camera())
                && state.camera().isValid()
                && (player.getSpectatorTarget() == state.camera() || !paperCameraEvents)
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
        beginFocus(player, () -> {});
    }

    private boolean beginFocus(Player player, Runnable ready) {
        if (closed || startingFocus.containsKey(player.getUniqueId())) return false;
        GameSession room = plugin.games().session(player);
        if (room == null
                || !(room.kind.equals("mahjong") || room.kind.equals("2048") && room.phase() == GameSession.Phase.PLAYING)
                || room.seat(player.getUniqueId()) < 0
                || !isNearJoinedTable(player)
                || player.isDead()
                || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
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
        Focus original = new Focus(room, seat, player.getLocation().clone(), anchor,
                player.hasGravity(), player.isInvisible(), player.getGameMode(),
                player.getAllowFlight(), player.isFlying(), null);
        startingFocus.put(player.getUniqueId(), original);
        scheduler.execute(anchor, () -> createFocusCamera(player, original, ready));
        return true;
    }

    private void createFocusCamera(Player player, Focus original, Runnable ready) {
        if (closed || startingFocus.get(player.getUniqueId()) != original) return;
        Location anchor = original.anchor();
        if (!clearCamera(anchor)) {
            startingFocus.remove(player.getUniqueId(), original);
            return;
        }
        ArmorStand camera = anchor.getWorld().spawn(anchor, ArmorStand.class, cameraEntity -> {
            cameraEntity.setVisibleByDefault(false);
            cameraEntity.setPersistent(false);
            cameraEntity.setGravity(false);
            cameraEntity.setInvulnerable(true);
            cameraEntity.setMarker(true);
            cameraEntity.setVisible(false);
            cameraEntity.setSilent(true);
            cameraEntity.setRotation(anchor.getYaw(), anchor.getPitch());
        });
        Focus state = new Focus(original.room(), original.seat(), original.original(), anchor,
                original.gravity(), original.invisible(), original.mode(), original.allowFlight(),
                original.flying(), camera);
        scheduler.later(camera, () -> {
            if (focusedPlayers.get(player.getUniqueId()) != state) {
                startingFocus.remove(player.getUniqueId(), original);
                camera.remove();
            }
        }, 40);
        scheduler.execute(player, () -> {
            if (closed || startingFocus.get(player.getUniqueId()) != original
                    || plugin.games().session(player) != state.room() || !isNearJoinedTable(player)) {
                scheduler.execute(camera, camera::remove);
                startingFocus.remove(player.getUniqueId(), original);
                return;
            }
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
                teleport(player, anchor, success -> {
                    if (focusedPlayers.get(player.getUniqueId()) != state) return;
                    if (closed || !success || !atAnchor(player, state)
                            || !scheduler.isOwned(camera) || !camera.isValid()) {
                        finishFocus(player, false);
                        return;
                    }
                    startingFocus.remove(player.getUniqueId(), original);
                    player.showEntity(plugin, camera);
                    attachCamera(player, state);
                    if (player.getSpectatorTarget() != camera) finishFocus(player, true);
                    else ready.run();
                });
            } catch (RuntimeException exception) {
                finishFocus(player, true);
                throw exception;
            }
        });
    }

    private void attachCamera(Player player, Focus state) {
        internalTeleports.put(player.getUniqueId(), state.anchor());
        try {
            player.setSpectatorTarget(state.camera());
        } finally {
            internalTeleports.remove(player.getUniqueId());
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

    private void teleport(Player player, Location target, java.util.function.Consumer<Boolean> completion) {
        internalTeleports.put(player.getUniqueId(), target);
        scheduler.teleport(player, target, success -> {
            internalTeleports.remove(player.getUniqueId(), target);
            completion.accept(success);
        });
    }

    private void returnToLocation(Player player, Location target) {
        Location from = player.getLocation().clone();
        scheduler.execute(target, () -> {
            if (clear(target)) scheduler.execute(player, () -> {
                if (!player.isDead() && player.getLocation().equals(from)) teleport(player, target, success -> {});
            });
        });
    }

    private void finishFocus(Player player, boolean returnToSeat) {
        finishFocus(player, returnToSeat, true, returnToSeat && !player.isDead());
    }

    private void finishFocus(Player player, boolean returnToSeat, boolean restoreMode) {
        finishFocus(player, returnToSeat, restoreMode, false);
    }

    private void finishFocus(Player player, boolean returnToSeat, boolean restoreMode, boolean retry) {
        startingFocus.remove(player.getUniqueId());
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
        if (!scheduler.folia() || plugin.isEnabled() || scheduler.isOwned(state.camera())) {
            scheduler.execute(state.camera(), state.camera()::remove);
        }
        player.setGravity(state.gravity());
        player.setInvisible(state.invisible());
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                if (!scheduler.folia() || plugin.isEnabled() || scheduler.isOwned(viewer)) {
                    scheduler.execute(viewer, () -> viewer.showPlayer(plugin, player));
                }
            }
        }
        if (returnToSeat
                && !player.isDead()
                && atAnchor(player, state)
                && (!scheduler.folia() || plugin.isEnabled())) {
            returnToLocation(player, state.original());
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
            if (world != null) returnToLocation(player, back);
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
                scheduler.execute(viewer, () -> viewer.hidePlayer(plugin, player));
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
                GoldenEggOVO.TDGames.Core.platform.PlatformMessages.actionBar(player,
                        GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.released"));
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
                    } else GoldenEggOVO.TDGames.Core.platform.PlatformMessages.actionBar(player,
                            GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.blocked"));
                }
            }
            return;
        }
        if (sprintPressed) {
            GameSession room = plugin.games().session(player);
            if (room != null && room.kind.equals("2048")) {
                if (startDirectionalControl(player)) {
                    DirectionalControl started = directionalPlayers.get(id);
                    if (started != null) started.keys().press(event.getInput());
                }
            } else beginFocus(player);
        } else {
            GameSession room = plugin.games().session(player);
            if (!event.getInput().isSprint() && !(startingFocus.containsKey(id)
                    && room != null && room.kind.equals("2048"))) releaseFocus(player);
        }
    }

    public void clientTick(Event event) {
        clientTick(((PlayerEvent) event).getPlayer());
    }

    private void clientTick(Player player) {
        UUID id = player.getUniqueId();
        if (!closingMenus.remove(id) || plugin.menus() != null && plugin.menus().active(player)) {
            return;
        }
        if (player.getCurrentInput() != null && !player.getCurrentInput().isSprint()) {
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
        if (!startingFocus.containsKey(player.getUniqueId()) && !isFocusValid(player, state)) {
            releaseFocus(player);
            event.setTo(player.getLocation());
            return;
        }

        event.setCancelled(true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    public void stopCamera(Event event) {
        if (hasTableFocus(((PlayerEvent) event).getPlayer())) ((Cancellable) event).setCancelled(true);
    }

    public void switchCamera(Event event) {
        Focus state = focusedPlayers.get(((PlayerEvent) event).getPlayer().getUniqueId());
        try {
            if (state != null && !event.getClass().getMethod("getNewSpectatorTarget").invoke(event).equals(state.camera())) {
                ((Cancellable) event).setCancelled(true);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read spectator target", exception);
        }
    }

    public static final class PaperCameraEvents implements Listener {
        private final TablePlayerController controller;
        PaperCameraEvents(TablePlayerController controller) { this.controller = controller; }

        @EventHandler
        public void clientTick(io.papermc.paper.event.packet.ClientTickEndEvent event) {
            controller.clientTick(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void stopCamera(com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
            controller.stopCamera(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void switchCamera(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
            controller.switchCamera(event);
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
        closed = true;
        fallbackTick.cancel();
        if (scheduler.folia() && !plugin.isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.execute(player, () -> {
                finishFocus(player, true, true, false);
                restoreCollision(player);
            });
        }
    }
}

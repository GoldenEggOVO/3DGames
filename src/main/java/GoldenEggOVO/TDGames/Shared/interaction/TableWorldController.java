package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.BoardMapStore;
import GoldenEggOVO.TDGames.Shared.render.TablePlacement;
import GoldenEggOVO.TDGames.Shared.render.TableView;
import GoldenEggOVO.TDGames.Shared.render.mahjong.MahjongAssist;
import GoldenEggOVO.TDGames.Shared.render.mahjong.MahjongControls;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.SelectedHandGame;
import GoldenEggOVO.TDGames.Multiplayer.game.go.GoGame;
import GoldenEggOVO.TDGames.Core.text.Language;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;

public final class TableWorldController implements Listener, AutoCloseable {
    private final TDGamesPlugin plugin;
    private final NamespacedKey tag;
    private final Map<UUID, TableView> views = new HashMap<>();
    private final BoardMapStore maps;
    private final Map<UUID, Long> clicks = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask pointerTask;
    private org.bukkit.scheduler.BukkitTask nativeDiceTask;
    private final Map<UUID, BoardSelection> selections = new HashMap<>();
    private final Map<Integer, Set<Chunk>> tableChunks = new HashMap<>();
    private final Map<UUID, PendingSounds> pendingSounds = new HashMap<>();

    private record PendingSounds(GameSession room, TableView view, BoardGame board, int history,
            long revision, GameSession.Phase phase, List<TableSounds.Cue> cues, boolean settled) {
        PendingSounds waiting(boolean value) { return new PendingSounds(room, view, board, history, revision, phase, cues, value); }
    }

    public TableWorldController(TDGamesPlugin plugin) {
        this.plugin = plugin;
        maps = new BoardMapStore(plugin);
        tag = new NamespacedKey("3dgames", "board-cell");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void initialize() {

        pointerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pointers, 2, 2);
        nativeDiceTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> views.values().forEach(TableView::renderNativeDiceMidpoint), 3, 2);
    }

    public Location center(int index) {
        GameSession room =
                plugin.games().activeSessions().values().stream()
                        .filter(v -> v.table == index)
                        .findFirst()
                        .orElse(null);
        if (room != null && room.anchorWorld != null) {
            World world = Bukkit.getWorld(room.anchorWorld);
            if (world == null) {
                throw new IllegalStateException("Table world is not loaded");
            }
            return TablePlacement.snap(
                    new Location(world, room.anchorX, room.anchorY, room.anchorZ), 2);
        }
        throw new IllegalStateException("Room has no usable world coordinates");
    }

    public boolean atTableWorld(Player player, GameSession room) {
        return player.getWorld().equals(center(room.table).getWorld());
    }

    public void anchor(GameSession room, Location location) {
        Location snapped = TablePlacement.snap(location, 2);
        UUID worldId = location.getWorld().getUID();
        for (GameSession existing : plugin.games().activeSessions().values()) {
            if (existing != room
                    && worldId.equals(existing.anchorWorld)
                    && TablePlacement.overlaps(
                            snapped.getX(),
                            snapped.getY(),
                            snapped.getZ(),
                            room.sideTray,
                            tableHalfWidth(room),
                            tableHalfDepth(room),
                            existing.anchorX,
                            existing.anchorY,
                            existing.anchorZ,
                            existing.sideTray,
                            tableHalfWidth(existing), tableHalfDepth(existing))) {
                throw new RuleViolation("error.table-overlap", "Another table is too close");
            }
        }
        if (room.sideTray) {
            double trayX = TablePlacement.sideTrayX(tableHalfWidth(room));
            for (int x = (int) Math.floor(snapped.getX() + trayX - .70);
                    x <= Math.floor(snapped.getX() + trayX + .70);
                    x++) {
                for (int y = snapped.getBlockY(); y <= Math.floor(snapped.getY() + 1.8); y++) {
                    for (int z = (int) Math.floor(snapped.getZ() - .70);
                            z <= Math.floor(snapped.getZ() + .70);
                            z++) {
                        if (!snapped.getWorld().getBlockAt(x, y, z).isPassable()) {
                            throw new RuleViolation("error.tray-space", "The dice tray needs clear space");
                        }
                    }
                }
            }
        }
        room.anchorWorld = worldId;
        room.anchorX = snapped.getX();
        room.anchorY = snapped.getY();
        room.anchorZ = snapped.getZ();
    }

    private static double tableHalfWidth(GameSession room) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.CLASSIC_IDS.contains(room.kind))
            return GoldenEggOVO.TDGames.Shared.render.classic.ClassicLayout.tableHalfWidth(room.kind);
        return switch (room.kind) {
            case "monopoly" -> 1.875;
            case "yacht" -> 2.45;
            case "mahjong", "color-eight", "doudizhu", "liars-bar", "texas-holdem" -> 1.5;
            default -> 1.125;
        };
    }

    private static double tableHalfDepth(GameSession room) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.CLASSIC_IDS.contains(room.kind))
            return GoldenEggOVO.TDGames.Shared.render.classic.ClassicLayout.tableHalfDepth(room.kind);
        return room.kind.equals("yacht") ? 1.125 : tableHalfWidth(room);
    }

    public void retainTableChunks(int index) {

        if (index < 0 || index > 127) {
            throw new RuleViolation("error.invalid-table-number", "Invalid table number");
        }
        Location c = center(index);
        Set<Chunk> chunks = new HashSet<>();
        for (int x = (c.getBlockX() - 4) >> 4; x <= ((c.getBlockX() + 4) >> 4); x++) {
            for (int z = (c.getBlockZ() - 4) >> 4; z <= ((c.getBlockZ() + 4) >> 4); z++) {
                c.getWorld().getChunkAt(x, z).addPluginChunkTicket(plugin);
                chunks.add(c.getWorld().getChunkAt(x, z));
            }
        }
        tableChunks.put(index, chunks);
    }

    public Location seatLocation(GameSession room, int seat) {
        Location c = center(room.table);
        double angle = 2 * Math.PI * Math.max(0, seat) / Math.max(2, room.capacity);
        double dx = Math.sin(angle) * 2.25, dz = Math.cos(angle) * 2.25;
        if (room.kind.equals("monopoly")) {
            dx = Math.sin(angle) * 3.0;
            dz = Math.cos(angle) * 3.0;
            if ((room.capacity == 4 || room.capacity == 5) && seat == 1) {
                dx = 2.8;
                dz = 1.15;
            }
        }
        if (room.kind.equals("chess") || room.kind.equals("xiangqi")) {
            dx = -dx;
            dz = -dz;
        }
        if (room.kind.equals("yacht")) {
            c.add(-.55, 0, 0);
            dx = Math.sin(angle) * 3.0;
        }
        if (room.kind.equals("ludo")) {
            int[] colors =
                    room.capacity == 2
                            ? new int[] {0, 2}
                            : room.capacity == 3 ? new int[] {0, 1, 2} : new int[] {0, 1, 2, 3};
            double a = -3 * Math.PI / 4 - colors[Math.floorMod(seat, colors.length)] * Math.PI / 2;
            dx = Math.sin(a) * 2.25;
            dz = Math.cos(a) * 2.25;
        }
        double lookHeight = room.kind.equals("connectfour") ? .6 : TableGeometry.SURFACE + .05 - 1.62;
        Location location =
                c.add(dx, 0, dz)
                        .setDirection(new Vector(-dx, lookHeight, -dz));
        if (!location.getBlock().isPassable()
                || !location.clone().add(0, 1, 0).getBlock().isPassable()) {
            throw new RuleViolation(
                    "error.the-seat-is-blocked-choose-a-more",
                    "The seat is blocked. Choose a more open position.");
        }
        return location;
    }

    public void render(GameSession room) {
        if (room.board() == null) {
            removeView(room.id);
            return;
        }
        selections
                .values()
                .removeIf(pick -> pick.room().equals(room.id) && pick.revision() != room.revision());
        TableView view = views.get(room.id);
        if (view == null) {
            view = new TableView(plugin, room, center(room.table), tag, maps);
            views.put(room.id, view);
        } else {
            view.sync();
        }
        if (room.restoring() || room.phase() != GameSession.Phase.PLAYING && room.phase() != GameSession.Phase.FINISHED)
            TableSounds.stopResults(view.origin);
    }

    public void countdown(GameSession room, long now) {
        TableView view = views.get(room.id);
        if (view != null) {
            TableSounds.countdown(plugin, room, view.origin, now);
        }
    }

    public void sound(GameSession room, TableSounds.Cue cue) {
        TableView view = views.get(room.id);
        if (view == null) return;
        PendingSounds pending = pendingSounds.get(room.id);
        if (pending != null && !current(pending)) {
            pendingSounds.remove(room.id);
            pending = null;
        }
        if (cue.delayTicks() > 0) {
            var state = new PendingSounds(room, view, room.board(), room.history.size(), room.revision(), room.phase(), List.of(), false);
            var immediate = new TableSounds.Cue(cue.sound(), cue.volume(), cue.pitch(), cue.resource(), 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (current(state)) sound(room, immediate);
            }, cue.delayTicks());
            return;
        }
        if (TableSounds.isLanding(cue) && (view.moving() || pending != null)) {
            if (pending == null) {
                pending = new PendingSounds(room, view, room.board(), room.history.size(), room.revision(), room.phase(), new ArrayList<>(), false);
                pendingSounds.put(room.id, pending);
            }
            pending.cues().add(cue);
            return;
        }
        TableSounds.play(plugin, view.origin, cue, room.kind);
    }

    public void detail(GameSession room, String resource, float volume) {
        TableView view = views.get(room.id);
        if (view == null || room.restoring()) return;
        for (Player player : view.origin.getWorld().getPlayers())
            if (plugin.allowed(player) && player.getLocation().distanceSquared(view.origin) <= 24 * 24)
                TableSounds.detailFor(plugin, player, view.origin, resource, volume);
    }

    private boolean current(PendingSounds state) {
        GameSession room = state.room();
        return views.get(room.id) == state.view() && room.board() == state.board()
                && room.history.size() == state.history() && !room.restoring()
                && (room.revision() == state.revision() || state.phase() == GameSession.Phase.PLAYING
                && room.phase() == GameSession.Phase.FINISHED && room.revision() == state.revision() + 1)
                && (room.phase() == GameSession.Phase.PLAYING || room.phase() == GameSession.Phase.FINISHED);
    }

    public boolean rolling(GameSession room) {
        TableView view = views.get(room.id);
        return view != null && view.rolling();
    }

    public MahjongAssist mahjongAssistance(GameSession room, int seat) {
        TableView view = views.get(room.id);
        return view == null ? null : view.mahjongAssistance(seat);
    }

    private String aimed(Player player, TableView view) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (view.room.board() instanceof ClassicGame) return view.classicHit(player, eye, direction);
        String call = view.handCallHit(player, eye, direction);
        if (call != null) {
            return "@call:" + call;
        }
        String hand = view.handHit(player, eye, direction);
        if (hand != null) {
            return (hand.startsWith("public:") ? "@tile:" : "@hand:") + hand;
        }
        if (view.deckHit(eye, direction)) {
            return "@draw";
        }
        if (view.room.board() instanceof HandGame) {
            double distance =
                    TableGeometry.intersection(
                            eye.getY(), direction.getY(), view.origin.getY() + .03);
            if (distance < 0
                    || eye.getWorld()
                                    .rayTraceBlocks(
                                            eye,
                                            direction,
                                            Math.max(.001, distance - .035),
                                            FluidCollisionMode.NEVER,
                                            true)
                            != null) {
                return null;
            }
            Vector point =
                    eye.toVector()
                            .add(direction.clone().multiply(distance))
                            .subtract(view.origin.toVector());
            double half = 1.5;
            return Math.abs(point.getX()) < half && Math.abs(point.getZ()) < half ? "@menu" : null;
        }
        if (view.geometry.kind.equals("connectfour")) {
            return view.verticalHit(eye, direction);
        }
        double distance =
                TableGeometry.intersection(eye.getY(), direction.getY(), view.boardPlaneY(player));
        TableView.Hit piece = view.hitPiece(player, eye, direction);
        String cell = null;
        if (piece != null
                && (view.room.kind.equals("yacht")
                        || distance < 0
                        || piece.distance() < distance)) {
            distance = piece.distance();
            cell = piece.cell();
        }
        if (distance < 0) {
            return null;
        }
        var obstacle =
                eye.getWorld()
                        .rayTraceBlocks(
                                eye,
                                direction,
                                Math.max(.001, distance - .035),
                                FluidCollisionMode.NEVER,
                                true);
        if (obstacle != null) {
            return null;
        }
        if (cell != null) {
            return cell;
        }
        if (view.room.kind.equals("yacht")) {
            return null;
        }
        Vector hit = eye.toVector().add(direction.multiply(distance));
        return view.geometry.hit(hit.getX() - view.origin.getX(), hit.getZ() - view.origin.getZ());
    }

    private void pointers() {
        TableSounds.expireResults();
        views.values().forEach(view -> {
            var room = view.room;
            if (room.restoring() || room.phase() != GameSession.Phase.PLAYING && room.phase() != GameSession.Phase.FINISHED)
                TableSounds.stopResults(view.origin);
            view.tick();
        });
        for (var iterator = pendingSounds.values().iterator(); iterator.hasNext();) {
            PendingSounds pending = iterator.next();
            if (!current(pending)) iterator.remove();
            else if (pending.view().moving()) {
                if (pending.settled()) pendingSounds.put(pending.room().id, pending.waiting(false));
            } else if (!pending.settled()) pendingSounds.put(pending.room().id, pending.waiting(true));
            else {
                iterator.remove();
                for (var cue : pending.cues())
                    TableSounds.play(plugin, pending.view().origin, cue, pending.room().kind);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameSession room = plugin.games().session(player);
            TableView view = room == null ? null : views.get(room.id);
            if (view == null || !player.getWorld().equals(view.origin.getWorld())) {
                continue;
            }
            if (!plugin.allowed(player)) {
                view.clear(player);
                continue;
            }
            if (player.getEyeLocation().distanceSquared(view.origin) > 36) {
                view.clearInteraction(player);
                continue;
            }
            BoardSelection pick = selections.get(player.getUniqueId());
            if (pick != null && !pick.current(room)) {
                selections.remove(player.getUniqueId());
                pick = null;
            }
            if (plugin.tablePlayers() != null && plugin.tablePlayers().hasTableFocus(player)) {
                view.cursor(player, null, null);
            } else {
                view.cursor(player, pick, aimed(player, view));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void click(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
            worldClick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (worldClick(event.getPlayer(), event.getAction().isRightClick())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void swing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            worldClick(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player) {
                worldClick(player);
            }
        }
    }

    @EventHandler
    public void hanging(org.bukkit.event.hanging.HangingBreakEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
        }
    }

    private boolean worldClick(Player player) {
        return worldClick(player, false);
    }

    public boolean worldClick(Player player, boolean rightClick) {
        if (!plugin.allowed(player)) {
            return false;
        }
        boolean hasTableFocus = plugin.tablePlayers() != null && plugin.tablePlayers().hasTableFocus(player);
        GameSession room = plugin.games().session(player);
        TableView view = room == null ? null : views.get(room.id);
        if (view == null
                || room.board() == null
                || !player.getWorld().equals(view.origin.getWorld())) {
            return hasTableFocus;
        }
        if (player.isSneaking() && rightClick) {
            Location eye = player.getEyeLocation();
            if (!Double.isFinite(view.menuHit(eye, eye.getDirection()))) {
                return hasTableFocus;
            }
            long now = System.nanoTime(), last = clicks.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < 180_000_000L) {
                return true;
            }
            clicks.put(player.getUniqueId(), now);
            if (plugin.tablePlayers() != null && (hasTableFocus || room.kind.equals("mahjong"))) {
                plugin.tablePlayers().releaseFocus(player);
            }
            plugin.menus().room(player, room);
            return true;
        }
        if (hasTableFocus) {
            return true;
        }
        String cell = aimed(player, view);
        if (room.kind.equals("mahjong")
                && room.phase() == GameSession.Phase.PLAYING
                && (cell == null
                        || !cell.startsWith("@hand:")
                        || room.seat(player.getUniqueId()) != room.board().currentPlayer())) {
            view.maintainMahjongPress(player);
        }
        if (cell == null) {
            return false;
        }
        if (player.isSneaking()) {
            return true;
        }
        long now = System.nanoTime(), last = clicks.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 180_000_000L) {
            return true;
        }
        clicks.put(player.getUniqueId(), now);
        if (cell.equals("@menu") || cell.startsWith("@tile:")) {
            return true;
        }
        if (room.phase() != GameSession.Phase.PLAYING) {
            return true;
        }
        if (cell.startsWith("@call:assist:")) {
            view.toggleMahjongAssistance(player, cell.substring("@call:assist:".length()));
            return true;
        }
        int seat = room.seat(player.getUniqueId());
        if (room.board() instanceof ClassicGame ? seat != room.board().currentPlayer() && room.board().legalActions(seat).isEmpty()
                : seat != room.board().currentPlayer() && !(room.board() instanceof GoGame go && go.scoring())) {
            player.sendActionBar(
                    Language.component("hint.not-turn").colorIfAbsent(NamedTextColor.GRAY));
            return true;
        }
        if (view.rolling()) {
            player.sendActionBar(
                    Language.component("hint.roll.wait").colorIfAbsent(NamedTextColor.GOLD));
            return true;
        }
        if (cell.equals("@call:dismiss")) {
            view.dismissHandCalls(player);
            return true;
        }
        if (rightClick && room.board() instanceof GoldenEggOVO.TDGames.Multiplayer.game.battleship.BattleshipGame sea
                && sea.setup()) {
            if (cell.matches("b" + seat + "_[0-9]{1,2}") && sea.legalActions(seat).contains("rotate")) {
                execute(player, room, List.of("rotate")); return true;
            }
            if (cell.matches("@ship/[0-4]") && sea.infoFor(seat).get("ship").equals(cell.substring(6))
                    && sea.legalActions(seat).contains("clear")) {
                execute(player, room, List.of("clear")); return true;
            }
        }
        if (rightClick && room.kind.equals("mastermind") && cell.matches("draft[0-3]")) {
            cycleColor(player, room, cell, 1); return true;
        }
        pickCell(player, room, seat, cell);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void changeColor(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        GameSession room = plugin.games().session(player);
        if (room == null || !room.kind.equals("mastermind") || room.phase() != GameSession.Phase.PLAYING
                || !plugin.allowed(player) || room.seat(player.getUniqueId()) != room.board().currentPlayer()) return;
        TableView view = views.get(room.id);
        if (view == null || !view.origin.getWorld().equals(player.getWorld())) return;
        String cell = aimed(player, view);
        if (cell == null || !cell.matches("draft[0-3]")) return;
        event.setCancelled(true);
        int delta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot() + 4, 9) - 4;
        if (delta != 0) cycleColor(player, room, cell, delta);
    }

    private void cycleColor(Player player, GameSession room, String cell, int direction) {
        if (room.seat(player.getUniqueId()) != room.board().currentPlayer()) return;
        execute(player, room, List.of((direction > 0 ? "next-color:" : "previous-color:") + cell.charAt(5)));
    }

    private void pickCell(Player player, GameSession room, int seat, String cell) {
        if (room.board() instanceof ClassicGame) {
            TableView view = views.get(room.id);
            if (view == null) return;
            String action = view.classicAction(player, cell);
            if (action != null) execute(player, room, List.of(action));
            return;
        }
        if (room.kind.equals("yacht")) {
            pickYachtControl(player, room, seat, cell);
            return;
        }
        if (cell.startsWith("@call:playing:")) {
            TableView view = views.get(room.id);
            String action = view == null ? null : view.playingAction(player, cell.substring(14));
            if (action != null) {
                execute(player, room, List.of(action));
            }
            return;
        }
        if (cell.startsWith("@call:card:") && room.kind.equals("color-eight")) {
            String action = cell.substring(11);
            if (room.board().legalActions(seat).contains(action)) {
                execute(player, room, List.of(action));
            }
            return;
        }
        if (cell.startsWith("@call:") && room.kind.equals("mahjong")) {
            pickMahjongControl(player, room, seat, cell);
            return;
        }
        if (cell.equals("@draw")) {
            if (room.board().legalActions(seat).contains("draw")) {
                execute(player, room, List.of("draw"));
            } else {
                player.sendActionBar(
                        Language.component("hint.hand.draw-unavailable")
                                .colorIfAbsent(NamedTextColor.GRAY));
            }
            return;
        }
        if (cell.startsWith("@hand:") && room.board() instanceof HandGame hand) {
            pickHandCard(player, room, seat, cell, hand);
            return;
        }
        if (cell.equals("@roll")) {
            if (room.board().legalActions(seat).contains("roll")) {
                execute(player, room, List.of("roll"));
            } else {
                player.sendActionBar(
                        Language.component("hint.roll.unavailable")
                                .colorIfAbsent(NamedTextColor.GOLD));
            }
            return;
        }
        List<String> related = room.board().actionsForCell(seat, cell);
        if (related.size() == 1
                && (related.getFirst().startsWith("drop:")
                        || related.getFirst().startsWith("place:")
                        || related.getFirst().startsWith("dead:")
                        || related.getFirst().startsWith("hold:"))) {
            execute(player, room, related);
            return;
        }
        BoardSelection pick = selections.get(player.getUniqueId());
        if (pick != null && !pick.current(room)) {
            selections.remove(player.getUniqueId());
            pick = null;
        }
        List<String> destinations = BoardSelection.destinationActions(pick, cell);
        if (!destinations.isEmpty()) {
            execute(player, room, destinations);
            return;
        }
        List<String> sources = BoardSelection.sourceActions(room.board(), seat, cell);
        if (room.kind.equals("ludo") && !sources.isEmpty()) {
            execute(player, room, sources);
            return;
        }
        if (!sources.isEmpty()) {
            TableView view = views.get(room.id);
            if (pick != null && pick.source().equals(cell)) {
                selections.remove(player.getUniqueId());
                if (view != null) view.cursor(player, null, cell);
                player.sendActionBar(
                        Language.component("hint.cancelled").colorIfAbsent(NamedTextColor.GRAY));
                return;
            }
            BoardSelection selected = new BoardSelection(room.id, room.revision(), cell, sources);
            selections.put(player.getUniqueId(), selected);
            if (view != null) view.cursor(player, selected, cell);
            TableSounds.select(plugin, player);
            player.sendActionBar(
                    Language.component("hint.selected").colorIfAbsent(NamedTextColor.GREEN));
        } else if (room.kind.equals("ludo") && room.board().legalActions(seat).contains("roll")) {
            plugin.tell(player, Language.component("chat.roll-first"));
        } else if (Set.of("gomoku", "go", "go9", "go13", "reversi", "connectfour")
                .contains(room.kind)) {
            player.sendActionBar(
                    Language.component(
                                    room.kind.equals("connectfour")
                                            ? "hint.column.unavailable"
                                            : "hint.position.unavailable")
                            .colorIfAbsent(NamedTextColor.RED));
        } else {
            player.sendActionBar(
                    Language.component(pick == null ? "chat.select-first" : "chat.destination")
                            .colorIfAbsent(NamedTextColor.GRAY));
        }
    }

    private void pickYachtControl(Player player, GameSession room, int seat, String cell) {
        if (cell.startsWith("@score:")) {
            String action = "score:" + cell.substring(7);
            if (room.board().legalActions(seat).contains(action)) {
                execute(player, room, List.of(action));
            }
        } else if (cell.startsWith("die")) {
            String action = "hold:" + cell;
            if (room.board().legalActions(seat).contains(action)) {
                execute(player, room, List.of(action));
            }
        } else if (cell.equals("@roll") && room.board().legalActions(seat).contains("roll")) {
            execute(player, room, List.of("roll"));
        }
        return;
    }

    private void pickMahjongControl(Player player, GameSession room, int seat, String cell) {
        String group = cell.substring(6);
        TableView view = views.get(room.id);
        MahjongAssist assistance = view == null ? null : view.mahjongAssistance(seat);
        List<String> legal = room.board().legalActions(seat);
        if (assistance != null) {
            legal = assistance.visibleActions(legal);
        }
        if (view != null && view.expandHandCall(player, group)) {
            return;
        }
        if (group.startsWith("choice:")) {
            String action = group.substring(7);
            if (legal.contains(action)) {
                execute(player, room, List.of(action));
            }
            return;
        }
        List<String> choices = MahjongControls.groups(legal).getOrDefault(group, List.of());
        if (!choices.isEmpty()) {
            execute(player, room, choices);
        }
        return;
    }

    private void pickHandCard(
            Player player, GameSession room, int seat, String cell, HandGame hand) {
        String id = cell.substring(6);
        if (hand.hand(seat).stream().noneMatch(piece -> piece.id().equals(id))) {
            return;
        }
        if (room.kind.equals("mahjong")) {
            TableView view = views.get(room.id);
            String action = view == null ? null : view.mahjongHandAction(player, id);
            if (action != null) {
                execute(player, room, List.of(action));
            } else {
                player.sendActionBar(
                        Language.component("hint.unavailable").colorIfAbsent(NamedTextColor.GRAY));
            }
            return;
        }
        TableView view = views.get(room.id);
        String action = view == null ? null : view.cardHandAction(player, id);
        if (room.board() instanceof SelectedHandGame) {
            return;
        }
        if (action != null) {
            execute(player, room, List.of(action));
        } else {
            player.sendActionBar(
                    Language.component("hint.unavailable").colorIfAbsent(NamedTextColor.GRAY));
        }
        return;
    }

    private void execute(Player player, GameSession room, List<String> choices) {
        selections.remove(player.getUniqueId());
        if (choices.size() > 1) {
            render(room);
            plugin.menus().boardChoices(player, room, choices, 0);
            return;
        }
        try {
            if (!plugin.allowed(player)) {
                return;
            }
            room.requireAction(player.getUniqueId(), room.revision());
            plugin.games().applyTableAction(
                    room,
                    room.seat(player.getUniqueId()),
                    new com.google.gson.JsonPrimitive(choices.getFirst()),
                    player);
        } catch (IllegalArgumentException ex) {
            plugin.tell(player, Language.component("chat.changed"));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        clearSelection(e.getPlayer());
    }

    @EventHandler
    public void changeWorld(PlayerChangedWorldEvent e) {
        clearSelection(e.getPlayer());
    }

    public void clearSelection(Player player) {
        clicks.remove(player.getUniqueId());
        views.values().forEach(view -> view.clear(player));
        selections.remove(player.getUniqueId());
    }

    private void removeView(UUID room) {
        pendingSounds.remove(room);
        TableView old = views.remove(room);
        if (old != null) {
            TableSounds.stopResults(old.origin);
            old.close();
        }
    }

    public void remove(GameSession room) {
        removeView(room.id);
        selections.values().removeIf(pick -> pick.room().equals(room.id));
        Set<Chunk> chunks = tableChunks.remove(room.table);
        if (chunks != null) {
            for (Chunk chunk : chunks) {
                if (tableChunks.values().stream().noneMatch(set -> set.contains(chunk))) {
                    chunk.removePluginChunkTicket(plugin);
                }
            }
        }
    }

    @Override
    public void close() {
        if (pointerTask != null) {
            pointerTask.cancel();
        }
        if (nativeDiceTask != null) nativeDiceTask.cancel();
        views.values().forEach(view -> { TableSounds.stopResults(view.origin); view.close(); });
        views.clear();
        pendingSounds.clear();
        selections.clear();
        clicks.clear();
        tableChunks.clear();
        for (World world : Bukkit.getWorlds()) {
            world.removePluginChunkTickets(plugin);
        }
    }
}

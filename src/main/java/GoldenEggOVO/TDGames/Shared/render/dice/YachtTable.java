package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;

import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.TableButtonStyle;
import GoldenEggOVO.TDGames.Shared.render.TableView;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.yacht.YachtGame;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class YachtTable implements AutoCloseable {
    private static final double SIZE = .18;
    private final TDGamesPlugin plugin;
    private final GameSession room;
    private final Location origin;
    private final NamespacedKey tag;
    public final DisplayAudience audience;
    private final List<Entity> common = new ArrayList<>(),
            nativeTable = new ArrayList<>(),
            packedTable = new ArrayList<>();
    private final List<YachtDie> nativeDice = new ArrayList<>(), packedDice = new ArrayList<>();
    private final DiceMotion[] throwsByDie = new DiceMotion[5];
    private final DiceMotion.Pose[] poses = new DiceMotion.Pose[5];
    private final boolean[] held = new boolean[5];
    private final int[] faces = new int[5];
    private final TextDisplay[] categories = new TextDisplay[12];
    private final TextDisplay rollLabel, headerLabel;
    private final TextDisplay[][] scores;
    private final TextDisplay[] headers;
    private List<GameSession.Seat> roster = List.of();
    private final BlockDisplay activeColumn;
    private final List<BlockDisplay> rollButton = new ArrayList<>();
    private final TextDisplay[] summaryLabels = new TextDisplay[3];
    static final double SCORE_X = -1.65;
    private static final double SCORE_TOP = -.85, SCORE_HEIGHT = 1.50;
    private static final double COLUMNS_LEFT = -1.625, COLUMNS_WIDTH = .70;

    static double scoreZ(int category) {
        return -.80 + (category < 6 ? category : category + 2) * .10;
    }

    private double columnX(int seat) {
        return COLUMNS_LEFT + (seat + .5) * COLUMNS_WIDTH / room.capacity;
    }

    private YachtGame board;
    private long revision = -1, language = -1;
    private int history, frame;
    private boolean closed;

    private record Focus(
            String target,
            double x,
            double y,
            double z,
            double width,
            double depth,
            boolean packed,
            List<Entity> parts) {}

    private final Map<UUID, Focus> focuses = new HashMap<>();

    public YachtTable(TDGamesPlugin plugin, GameSession room, Location origin, NamespacedKey tag) {
        this.plugin = plugin;
        this.room = room;
        this.origin = origin;
        this.tag = tag;
        audience = new DisplayAudience(plugin, origin);
        for (int i = 0; i < 5; i++) {
            poses[i] = rest(i, false, 1);
        }
        for (int i = 0; i < categories.length; i++) {
            categories[i] = label(-1.99, .034, scoreZ(i), .135f);
            PlatformMessages.text(categories[i],
                    (i < 6
                                    ? Component.text(String.valueOf((char) (0x2680 + i)))
                                            .append(Component.space())
                                    : Component.empty())
                            .append(
                                    Language.component(
                                            "score.category." + YachtGame.CATEGORIES.get(i)))
                            .color(NamedTextColor.BLACK));
        }
        scores = new TextDisplay[room.capacity][15];
        headers = new TextDisplay[room.capacity];
        for (int seat = 0; seat < room.capacity; seat++) {
            headers[seat] = label(columnX(seat), .034, -.94, .09f);
            for (int row = 0; row < 15; row++) {
                scores[seat][row] = label(columnX(seat), .034, -.80 + row * .10, .14f);
            }
        }
        summaryLabels[0] = label(-1.99, .034, -.20, .12f);
        summaryLabels[1] = label(-1.99, .034, -.10, .12f);
        summaryLabels[2] = label(-1.99, .034, .60, .13f);
        for (int seat = 1; seat < room.capacity; seat++) {
            common.add(
                    block(
                            COLUMNS_LEFT + seat * COLUMNS_WIDTH / room.capacity,
                            .017,
                            -.10,
                            .006,
                            .002,
                            SCORE_HEIGHT,
                            Material.BLACK_CONCRETE));
        }
        activeColumn =
                block(
                        columnX(0),
                        .007,
                        -.10,
                        COLUMNS_WIDTH / room.capacity - .008,
                        .004,
                        SCORE_HEIGHT - .008,
                        Material.YELLOW_TERRACOTTA);
        common.add(activeColumn);
        for (var layer : TableButtonStyle.layers(.95, .016, .23, true))
            rollButton.add(block(.45 + layer.x(), .007 + layer.bottom(), .82 + layer.z(), layer.w(), layer.h(), layer.d(), layer.material()));
        common.addAll(rollButton);
        rollLabel = label(.45, .038, .82, .16f);
        headerLabel = label(SCORE_X, .038, -1.025, .11f);
        for (Entity entity : common) {
            audience.common(entity);
        }
        sync();
    }

    public void sync() {
        if (closed) {
            return;
        }
        layers();
        if (!roster.equals(room.seats) || language != Language.generation()) {
            roster = List.copyOf(room.seats);
            for (int seat = 0; seat < headers.length; seat++) {
                String name = seat < roster.size()
                        ? MessageText.plain(RoomText.player(roster.get(seat), seat + 1)) : "";
                String shortName =
                        name.substring(
                                0,
                                name.offsetByCodePoints(
                                        0, Math.min(5, name.codePointCount(0, name.length()))));
                PlatformMessages.text(headers[seat],
                        Component.text((seat + 1) + "\n" + shortName).color(NamedTextColor.BLACK));
            }
        }
        if (board == room.board() && revision == room.revision()) {
            if (language != Language.generation()) {
                language = Language.generation();
                refreshLabels();
            }
            return;
        }
        YachtGame next = (YachtGame) room.board();
        String action =
                room.history.isEmpty()
                        ? ""
                        : room.history
                                .get(room.history.size() - 1)
                                .getAsJsonObject()
                                .get("action")
                                .getAsString();
        boolean sameBoard = board == next;
        boolean roll = sameBoard && room.history.size() == history + 1 && action.equals("roll");
        board = next;
        revision = room.revision();
        history = room.history.size();
        language = Language.generation();
        int[] values = board.dice();
        for (int i = 0; i < 5; i++) {
            int face = Math.max(1, values[i]);
            if (roll && !board.held(i)) {
                DiceMotion.Pose local =
                        new DiceMotion.Pose((poses[i].x() - x(i)) / .25, poses[i].y(), poses[i].z() - .15, poses[i].rotation());
                throwsByDie[i] =
                        new DiceMotion(SIZE, .24, local, face, room.seed() ^ (history * 31L + i));
            } else {
                DiceMotion interrupted = throwsByDie[i];
                throwsByDie[i] = null;
                if (!sameBoard || held[i] != board.held(i) || faces[i] != face || values[i] == 0) {
                    DiceMotion.Pose resting = rest(i, board.held(i), face);
                    if (sameBoard && faces[i] == face && values[i] != 0)
                        resting = new DiceMotion.Pose(resting.x(), resting.y(), resting.z(), poses[i].rotation());
                    poses[i] = resting;
                    render(i);
                } else if (interrupted != null) {
                    DiceMotion.Pose end = interrupted.pose(DiceMotion.FRAMES);
                    poses[i] = new DiceMotion.Pose(x(i) + end.x() * .25, end.y(), .15 + end.z(), end.rotation());
                    render(i);
                }
            }
            held[i] = board.held(i);
            faces[i] = face;
        }
        frame = 0;
        if (rolling()) {
            clearFocuses();
        }
        refreshLabels();
    }

    public void tick() {
        sync();
        if (!rolling()) {
            return;
        }
        frame++;
        for (int i = 0; i < 5; i++) {
            if (throwsByDie[i] != null) {
                DiceMotion.Pose p = throwsByDie[i].pose(frame);

                poses[i] = new DiceMotion.Pose(
                                        x(i) + p.x() * .25, p.y(), .15 + p.z(), p.rotation());
                render(i);
                if (frame >= DiceMotion.FRAMES) {
                    throwsByDie[i] = null;
                }
            }
        }
        if (Arrays.stream(throwsByDie).anyMatch(motion -> motion != null && motion.impact(frame))) {
            TableSounds.play(
                    plugin,
                    origin,
                    TableSounds.diceImpact(frame));
        }
        if (!rolling()) {
            if (room.phase() == GameSession.Phase.PLAYING && !room.restoring())
                for (var snapshot : plugin.playerViews().nearby(origin, 24))
                    if (snapshot.allowed())
                        TableSounds.detailFor(plugin, snapshot.player(), origin, "dice.settle", .12f);
            refreshLabels();
        }
    }

    public boolean rolling() {
        for (DiceMotion motion : throwsByDie) {
            if (motion != null) {
                return true;
            }
        }
        return false;
    }

    public void renderNativeMidpoint() {
        if (nativeDice.isEmpty() || !rolling()) return;
        for (int i = 0; i < throwsByDie.length; i++) {
            if (throwsByDie[i] == null) continue;
            DiceMotion.Pose p = throwsByDie[i].pose(frame + .5);
            nativeDice.get(i).pose(new DiceMotion.Pose(x(i) + p.x() * .25, p.y(), .15 + p.z(), p.rotation()));
        }
    }

    private static double x(int index) {
        return .32 + (index - 2) * .36;
    }

    private static DiceMotion.Pose rest(int index, boolean held, int face) {
        DiceMotion.Pose rest = DiceMotion.rest(SIZE, face);
        return new DiceMotion.Pose(
                held ? .32 + (index - 2) * .26 : x(index), rest.y() + (held ? .02 : 0), held ? -.68 : .15, rest.rotation());
    }

    private void render(int index) {
        if (!nativeDice.isEmpty()) {
            nativeDice.get(index).pose(poses[index]);
        }
        if (!packedDice.isEmpty()) {
            packedDice.get(index).pose(poses[index]);
        }
    }

    private void layers() {
        audience.refresh();
        if (audience.needed(false) && nativeTable.isEmpty()) {
            for (String model : List.of("dice_table", "score_table")) {
                double center = model.equals("score_table") ? SCORE_X : .32;
                for (var part : ModelRepository.require("tabletop/yacht/" + model).blocks()) {
                    var display = origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
                        init(d, "@menu");
                        d.setBlock(part.material().createBlockData());
                        var pose = ModelGeometry.pose(part);
                        pose.getTranslation().add((float)center, 0, 0);
                        d.setTransformation(pose);
                        if (part.brightness() != null) d.setBrightness(new Display.Brightness(part.brightness(), part.brightness()));
                    });
                    nativeTable.add(display);
                }
            }
            nativeTable.forEach(entity -> audience.add(entity, false));
        } else if (!audience.needed(false) && !nativeTable.isEmpty()) {
            nativeTable.forEach(audience::remove);
            nativeTable.clear();
        }
        if (audience.needed(true) && packedTable.isEmpty()) {
            ItemDisplay item =
                    origin.getWorld()
                            .spawn(
                                    origin,
                                    ItemDisplay.class,
                                    d -> {
                                        init(d, "@menu");
                                        d.setItemDisplayTransform(
                                                ItemDisplay.ItemDisplayTransform.FIXED);
                                        d.setItemStack(plugin.pack().item("yacht_table"));
                                        d.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        new Quaternionf(),
                                                        new Vector3f(2),
                                                        new Quaternionf()));
                                    });
            packedTable.add(item);
            audience.add(item, true);
        } else if (!audience.needed(true) && !packedTable.isEmpty()) {
            packedTable.forEach(audience::remove);
            packedTable.clear();
        }
        diceLayer(nativeDice, false);
        diceLayer(packedDice, true);
    }

    private void diceLayer(List<YachtDie> dice, boolean packed) {
        if (audience.needed(packed) && dice.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                var die =
                        new YachtDie(plugin, room, origin, tag, audience, packed, SIZE, "die" + i);
                dice.add(die);
                die.pose(poses[i]);
            }
        } else if (!audience.needed(packed) && !dice.isEmpty()) {
            dice.forEach(YachtDie::close);
            dice.clear();
        }
    }

    private void refreshLabels() {
        int seat = board.currentPlayer();
        for (int row = 0; row < summaryLabels.length; row++) {
            PlatformMessages.text(summaryLabels[row],
                    Language.component(
                                    "table.yacht."
                                            + List.of("subtotal", "bonus", "total-label").get(row))
                            .color(NamedTextColor.WHITE));
        }
        int completed = 0;
        for (int i = 0; i < 12; i++) {
            if (board.written(seat, i) >= 0) {
                completed++;
            }
        }
        PlatformMessages.text(headerLabel,
                Language.component("table.yacht.round", "round", Math.min(12, completed + 1))
                        .color(NamedTextColor.BLACK));
        activeColumn.setTransformation(
                new Transformation(
                        new Vector3f(
                                (float)
                                        (columnX(seat)
                                                - (COLUMNS_WIDTH / room.capacity - .008) / 2),
                                .007f,
                                (float) (SCORE_TOP + .004)),
                        new Quaternionf(),
                        new Vector3f(
                                (float) (COLUMNS_WIDTH / room.capacity - .008),
                                .004f,
                                (float) (SCORE_HEIGHT - .008)),
                        new Quaternionf()));
        for (int player = 0; player < board.playerCount(); player++) {
            int upper = 0;
            for (int category = 0; category < 12; category++) {
                int written = board.written(player, category);
                if (category < 6) {
                    upper += Math.max(0, written);
                }
                String value =
                        written >= 0
                                ? Integer.toString(written)
                                : player == seat && board.rolls() > 0
                                        ? Integer.toString(YachtGame.score(category, board.dice()))
                                        : "—";
                PlatformMessages.text(scores[player][category < 6 ? category : category + 2],
                        Component.text(value)
                                .color(
                                        written < 0
                                                ? NamedTextColor.DARK_GRAY
                                                : NamedTextColor.BLACK));
            }
            PlatformMessages.text(scores[player][6], Component.text(upper + "/63").color(NamedTextColor.BLACK));
            PlatformMessages.text(scores[player][7],
                    Component.text(upper >= 63 ? "+35" : "+0").color(NamedTextColor.BLACK));
            PlatformMessages.text(scores[player][14],
                    Component.text(board.total(player)).color(NamedTextColor.BLACK));
        }
        PlatformMessages.text(rollLabel,
                Language.component(
                        rolling()
                                ? "hint.rolling"
                                : board.legalActions(seat).contains("roll")
                                        ? "menu.yacht.roll"
                                        : "menu.yacht.choose-score",
                        "count",
                        3 - board.rolls()));
        for (int i = 0; i < categories.length; i++) {
            PlatformMessages.text(categories[i],
                    (i < 6
                                    ? Component.text(String.valueOf((char) (0x2680 + i)))
                                            .append(Component.space())
                                    : Component.empty())
                            .append(
                                    Language.component(
                                            "score.category." + YachtGame.CATEGORIES.get(i)))
                            .color(
                                    board.written(seat, i) >= 0
                                            ? NamedTextColor.DARK_GRAY
                                            : NamedTextColor.BLACK));
        }
    }

    public TableView.Hit hit(Location eye, Vector direction) {
        if (closed || !origin.getWorld().equals(eye.getWorld())) {
            return null;
        }
        TableView.Hit nearest = null;
        for (int i = 0; i < 5; i++) {
            if (board.rolls() > 0) {
                DiceMotion.Pose p = poses[i];
                Quaternionf inverse = new Quaternionf(p.rotation()).conjugate();
                Vector3f localEye = new Vector3f((float) (eye.getX() - origin.getX() - p.x()),
                        (float) (eye.getY() - origin.getY() - p.y()), (float) (eye.getZ() - origin.getZ() - p.z())).rotate(inverse);
                Vector3f localRay = new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).rotate(inverse);
                var ray = new Vector(localRay.x, localRay.y, localRay.z);
                var from = new Vector(localEye.x, localEye.y, localEye.z);
                var intersection = new BoundingBox(-SIZE / 2, -SIZE / 2, -SIZE / 2, SIZE / 2, SIZE / 2, SIZE / 2)
                        .rayTrace(from, ray, TableGeometry.REACH);
                if (intersection != null) {
                    double distance = intersection.getHitPosition().distance(from);
                    if (nearest == null || distance < nearest.distance()) nearest = new TableView.Hit("die" + i, distance);
                }
            }
        }
        nearest = hit(nearest, eye, direction, "@roll", .45, .007, .82, .95, .035, .23);
        for (int i = 0; i < 12; i++) {
            nearest =
                    hit(
                            nearest,
                            eye,
                            direction,
                            "@score:" + YachtGame.CATEGORIES.get(i),
                            SCORE_X,
                            .007,
                            scoreZ(i),
                            1.45,
                            .035,
                            .094);
        }
        nearest = hit(nearest, eye, direction, "@menu", SCORE_X, -.01, 0, 1.60, .015, 2.25);
        return hit(nearest, eye, direction, "@menu", .32, -.01, 0, 2.00, .015, 2.25);
    }

    private TableView.Hit hit(
            TableView.Hit nearest,
            Location eye,
            Vector direction,
            String id,
            double x,
            double y,
            double z,
            double w,
            double h,
            double d) {
        var box =
                new BoundingBox(
                        origin.getX() + x - w / 2,
                        origin.getY() + y,
                        origin.getZ() + z - d / 2,
                        origin.getX() + x + w / 2,
                        origin.getY() + y + h,
                        origin.getZ() + z + d / 2);
        var hit = box.rayTrace(eye.toVector(), direction, TableGeometry.REACH);
        if (hit == null) {
            return nearest;
        }
        double distance = hit.getHitPosition().distance(eye.toVector());
        return nearest == null || distance < nearest.distance()
                ? new TableView.Hit(id, distance)
                : nearest;
    }

    public void cursor(Player player, String hover) {
        focus(player, rolling() ? null : hover);
        Component hint = Component.empty();
        if (hover != null) {
            if (rolling()) {
                hint = Language.component("hint.rolling");
            } else if (hover.startsWith("die")) {
                int index = Integer.parseInt(hover.substring(3));
                hint =
                        Language.component(
                                board.held(index) ? "table.yacht.unkeep" : "table.yacht.keep");
            } else if (hover.startsWith("@score:")) {
                int category = YachtGame.CATEGORIES.indexOf(hover.substring(7));
                int written = board.written(board.currentPlayer(), category);
                String key = written >= 0 ? "table.yacht.score-recorded"
                        : board.rolls() == 0 ? "table.yacht.score-roll-first" : "table.yacht.score-preview";
                int score = written >= 0 ? written : board.rolls() == 0 ? 0 : YachtGame.score(category, board.dice());
                hint = Language.component(key, "category",
                        Language.component("score.category." + YachtGame.CATEGORIES.get(category)), "score", score);
            } else {
                hint = Language.component(hover.equals("@roll") ? "hint.roll" : "hint.menu");
            }
        }
        PlatformMessages.actionBar(player, hint.colorIfAbsent(NamedTextColor.GOLD));
    }

    private void focus(Player player, String target) {
        if (closed
                || target == null
                || (!target.startsWith("die") && !target.startsWith("@score:") && !target.equals("@roll"))) {
            clear(player);
            return;
        }
        double x, y, z, width, depth;
        if (target.equals("@roll")) {
            x = .45; y = .007; z = .82; width = .95; depth = .23;
        } else if (target.startsWith("die")) {
            var pose = poses[Integer.parseInt(target.substring(3))];
            Vector3f extent = new Vector3f((float) SIZE / 2, 0, 0).rotate(pose.rotation()).absolute()
                    .add(new Vector3f(0, (float) SIZE / 2, 0).rotate(pose.rotation()).absolute())
                    .add(new Vector3f(0, 0, (float) SIZE / 2).rotate(pose.rotation()).absolute());
            x = pose.x();
            y = pose.y() + extent.y + TableGeometry.SELECTION_GAP;
            z = pose.z();
            width = extent.x * 2 + .016;
            depth = extent.z * 2 + .016;
        } else {
            x = columnX(board.currentPlayer());
            y = .011 + TableGeometry.SELECTION_GAP;
            z = scoreZ(YachtGame.CATEGORIES.indexOf(target.substring(7)));
            width = COLUMNS_WIDTH / room.capacity - .008;
            depth = .094;
        }
        boolean packed = audience.packed(player) && !packedDice.isEmpty();
        Focus old = focuses.get(player.getUniqueId());
        if (old != null
                && old.target().equals(target)
                && old.x() == x
                && old.y() == y
                && old.z() == z
                && old.width() == width
                && old.depth() == depth
                && old.packed() == packed) {
            return;
        }
        clear(player);
        List<Entity> parts = new ArrayList<>(4);
        if (target.startsWith("die")) {
            var dice = packed ? packedDice : nativeDice;
            if (dice.isEmpty()) return;
            parts.add(dice.get(Integer.parseInt(target.substring(3))).highlight(plugin, player));
            focuses.put(player.getUniqueId(), new Focus(target, x, y, z, width, depth, packed, parts));
            return;
        }
        if (target.equals("@roll")) {
            for (BlockDisplay part : rollButton)
                parts.add(TableButtonStyle.privateOutline(plugin, player, part));
            focuses.put(player.getUniqueId(), new Focus(target, x, y, z, width, depth, packed, parts));
            return;
        }
        double stroke = .006;
        for (double side : new double[] {-1, 1}) {
            parts.add(
                    block(
                            x,
                            y,
                            z + side * (depth - stroke) / 2,
                            width,
                            .003,
                            stroke,
                            Material.LIME_CONCRETE,
                            player));
            parts.add(
                    block(
                            x + side * (width - stroke) / 2,
                            y,
                            z,
                            stroke,
                            .003,
                            depth - stroke * 2,
                            Material.LIME_CONCRETE,
                            player));
        }
        focuses.put(player.getUniqueId(), new Focus(target, x, y, z, width, depth, packed, parts));
    }

    public void clear(Player player) {
        Focus old = focuses.remove(player.getUniqueId());
        if (old != null) {
            old.parts().forEach(DisplayAudience::retire);
        }
    }

    private void clearFocuses() {
        focuses.values().forEach(focus -> focus.parts().forEach(DisplayAudience::retire));
        focuses.clear();
    }

    private void init(Display display, String id) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.getPersistentDataContainer()
                .set(tag, PersistentDataType.STRING, room.id + "|" + id);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(.35f);
    }

    private BlockDisplay block(
            double x, double y, double z, double w, double h, double d, Material material) {
        return block(x, y, z, w, h, d, material, null);
    }

    private BlockDisplay block(
            double x,
            double y,
            double z,
            double w,
            double h,
            double d,
            Material material,
            Player viewer) {
        BlockDisplay entity =
                origin.getWorld()
                        .spawn(
                                origin,
                                BlockDisplay.class,
                                display -> {
                                    init(display, "@menu");
                                    display.setBlock(material.createBlockData());
                                    if (viewer != null) {
                                        display.setVisibleByDefault(false);
                                    }
                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(
                                                            (float) (x - w / 2),
                                                            (float) y,
                                                            (float) (z - d / 2)),
                                                    new Quaternionf(),
                                                    new Vector3f((float) w, (float) h, (float) d),
                                                    new Quaternionf()));
                                });
        if (viewer != null) {
            DisplayAudience.show(plugin, viewer, entity);
        }
        return entity;
    }

    private TextDisplay label(double x, double y, double z, float scale) {
        TextDisplay label =
                origin.getWorld()
                        .spawn(
                                origin.clone().add(x, y, z),
                                TextDisplay.class,
                                display -> {
                                    init(display, "@menu");
                                    display.setRotation(0, -90);
                                    display.setBillboard(Display.Billboard.FIXED);
                                    display.setDefaultBackground(false);
                                    display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                    display.setShadowed(false);
                                    display.setLineWidth(Integer.MAX_VALUE);
                                    display.setTransformation(
                                            new Transformation(
                                                    new Vector3f(),
                                                    new Quaternionf(),
                                                    new Vector3f(scale),
                                                    new Quaternionf()));
                                });
        common.add(label);
        return label;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearFocuses();
        nativeDice.forEach(YachtDie::close);
        packedDice.forEach(YachtDie::close);
        nativeTable.forEach(audience::remove);
        packedTable.forEach(audience::remove);
        common.forEach(audience::remove);
    }
}

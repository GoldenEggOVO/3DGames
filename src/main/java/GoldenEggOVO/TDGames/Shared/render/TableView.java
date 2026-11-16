package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;

import GoldenEggOVO.TDGames.Shared.text.BoardMessages;

import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;

import GoldenEggOVO.TDGames.Core.render.DisplayAudience;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.render.cards.PlayingCardTable;
import GoldenEggOVO.TDGames.Shared.render.cards.RoundCardTable;
import GoldenEggOVO.TDGames.Shared.render.dice.DiceTray;
import GoldenEggOVO.TDGames.Shared.render.dice.YachtTable;
import GoldenEggOVO.TDGames.Shared.render.mahjong.MahjongAssist;
import GoldenEggOVO.TDGames.Shared.render.mahjong.MahjongTableFurniture;
import GoldenEggOVO.TDGames.Shared.render.packed.BoardModelKeys;
import GoldenEggOVO.TDGames.Shared.render.packed.PackedDisplay;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Shared.game.SelectedHandGame;
import GoldenEggOVO.TDGames.Multiplayer.game.go.GoGame;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.GameSymbols;
import GoldenEggOVO.TDGames.Core.text.LabelLayout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public final class TableView implements AutoCloseable {
    private static final double BOARD_CURSOR_Y = .004;
    private static final double NATIVE_MAP_SURFACE_Y = -.023359375;
    private static final double PACKED_MAP_SURFACE_Y = .00155;
    private static final double NATIVE_MAP_OFFSET = NATIVE_MAP_SURFACE_Y - PACKED_MAP_SURFACE_Y;
    final TDGamesPlugin plugin;
    public final GameSession room;
    public final TableGeometry geometry;
    public final Location origin;
    private final NamespacedKey tag;
    private final List<Entity> furniture = new ArrayList<>();
    private final Map<String, TokenView> tokens = new LinkedHashMap<>();
    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private BoardGame renderedBoard;
    private final Set<TokenView> animating = new LinkedHashSet<>();
    private final Map<UUID, Overlay> overlays = new HashMap<>();
    private final List<Entity> lastMove = new ArrayList<>();
    private final Map<Boolean, List<Entity>> lastMoveLayers = new HashMap<>();
    private final Map<Boolean, TextDisplay> riverLayers = new HashMap<>();
    private TextDisplay title;
    private final Map<UUID, Player> focusedTitleViewers = new HashMap<>();
    private long titleAudienceGeneration = Long.MIN_VALUE;
    private DiceTray diceTray;
    private YachtTable yachtTable;
    private GoldenEggOVO.TDGames.Shared.render.classic.ClassicTable classicTable;
    private HandTable handTable;
    private PlayingCardTable playingTable;
    private final List<Entity> handFurniture = new ArrayList<>();
    private ItemDisplay packedTable;
    private boolean buildingHandFurniture;
    private final BoardMapStore maps;
    private DisplayAudience boardAudience;
    private final List<Entity> nativeBoardFurniture = new ArrayList<>();
    private ItemDisplay packedBoard;
    private int boardLayers = -1;
    private long revision = -1;
    private int renderedHistory;
    private Component lastTitle;
    private TitleState titleState;
    private boolean wasRolling;
    private String lastDiceKey;

    private record TitleState(
            BoardGame board,
            long revision,
            GameSession.Phase phase,
            List<GameSession.Seat> seats,
            int capacity,
            int table,
            String result,
            long language) {
        TitleState(GameSession room) {
            this(
                    room.board(),
                    room.revision(),
                    room.phase(),
                    List.copyOf(room.seats),
                    room.capacity,
                    room.table,
                    room.result(),
                    Language.generation());
        }

        boolean matches(GameSession room) {
            return board == room.board()
                    && revision == room.revision()
                    && phase == room.phase()
                    && capacity == room.capacity
                    && table == room.table
                    && result.equals(room.result())
                    && language == Language.generation()
                    && seats.equals(room.seats);
        }
    }

    private String lastDestination;

    private record Token(String id, Cell cell, double stack) {}

    private final class TokenView {
        Token token;
        final List<Entity> parts = new ArrayList<>(), deadMarks = new ArrayList<>();
        Location from, to;
        int frame = 6;
        List<Location> path = List.of();
        int pathDuration = 6;
        double height, radius;
        final Map<Display, Matrix4f> flipParts = new LinkedHashMap<>();
        int flipFrame = 10;
        float flipFrom, flipTo;

        TokenView(Token token, Location at) {
            this.token = token;
            this.from = at;
            this.to = at;
            build(from);
        }

        void build(Location at) {
            Cell cell = token.cell;
            float scale = (float) geometry.spacing;
            if (boardAudience.needed(true) && BoardModelKeys.supported(room.kind)) {
                ItemDisplay item =
                        packedItem(
                                at,
                                BoardModelKeys.piece(room.kind, cell, room.board().publicInfo()),
                                room.kind.equals("connectfour") ? 1 : scale);
                parts.add(item);
                if (room.kind.equals("reversi")) {
                    flipParts.put(item, new Matrix4f().scale(scale));
                }
                if ((room.kind.equals("chess") && cell.piece().equals(GameSymbols.HORSE))
                        || room.kind.equals("xiangqi")) {
                    item.setRotation(cell.owner() == 0 ? 180 : 0, 0);
                }
            }
            if (room.kind.equals("connectfour")) {
                if (boardAudience.needed(false)) {
                    for (var part : BoardPieceModels.connectFour(cell.owner())) {
                        parts.add(modelBlock(nativeMapPosition(at), part, 1, null));
                    }
                }
                height = .24;
                radius = .12;
                frame = 12;
                return;
            }
            boolean reversi = room.kind.equals("reversi");
            flipTo = cell.owner() == 0 ? 0 : (float) Math.PI;
            for (ModelDefinition.Block part :
                    reversi
                            ? BoardPieceModels.reversi()
                            : BoardPieceModels.piece(room.kind, cell, room.board().publicInfo())) {
                height = Math.max(height, (part.bottom() + part.h()) * scale);
                radius =
                        Math.max(
                                radius,
                                Math.max(
                                                Math.abs(part.x()) + part.w() / 2,
                                                Math.abs(part.z()) + part.d() / 2)
                                        * scale);
                if (!boardAudience.needed(false)) {
                    continue;
                }
                BlockDisplay d = modelBlock(nativeMapPosition(at), part, scale, null);
                if (reversi) {
                    flipParts.put(d, new Matrix4f().scale(scale).mul(ModelGeometry.matrix(part)));
                }
                if (room.kind.equals("chess") && cell.piece().equals(GameSymbols.HORSE)) {
                    d.setRotation(cell.owner() == 0 ? 180 : 0, 0);
                }
                if (Set.of("go", "go9", "go13").contains(room.kind)
                        && part.material() == Material.RED_CONCRETE) {
                    deadMarks.add(d);
                }
                if (room.kind.equals("ludo")) {
                    d.setRotation(
                            PieceColors.seatIndex(room.board().publicInfo(), cell.owner()) * 90, 0);
                }
                parts.add(d);
            }
            if (reversi) {
                poseFlip(flipTo);
            }
            if (boardAudience.needed(false) && room.kind.equals("xiangqi")) {
                String glyph = cell.piece();
                TextDisplay label =
                        text(
                                nativeMapPosition(at).add(0, geometry.spacing * .235, 0),
                                Component.text(glyph),
                                geometry.spacing * 1.50,
                                true,
                                cell.owner() == 0 ? NamedTextColor.DARK_RED : NamedTextColor.BLACK);
                if (cell.owner() == 0) {
                    label.setRotation(180, -90);
                }
                parts.add(label);
            }
        }

        void rebuildLayers() {
            int savedFrame = frame;
            float savedFlipFrom = flipFrom, savedFlipTo = flipTo;
            int savedFlipFrame = flipFrame;
            parts.forEach(boardAudience::remove);
            parts.clear();
            deadMarks.clear();
            flipParts.clear();
            height = radius = 0;
            build(room.kind.equals("ludo") ? position() : from);
            frame = savedFrame;
            flipFrom = savedFlipFrom;
            flipTo = savedFlipTo;
            flipFrame = savedFlipFrame;
            positionParts(position());
            if (room.kind.equals("reversi")) {
                poseFlip(flipAngle());
            }
        }

        void mark(Token next) {
            boolean dead = next.cell.piece().contains("×");
            if (token.cell.piece().contains("×") != dead) {
                for (Entity part : parts) {
                    if (part instanceof ItemDisplay item) {
                        item.setItemStack(
                                plugin.pack().item(
                                        BoardModelKeys.piece(
                                                room.kind, next.cell, room.board().publicInfo())));
                    }
                }
            }
            if (dead && deadMarks.isEmpty() && boardAudience.needed(false)) {
                for (var part : BoardPieceModels.deadStoneMarks()) {
                    double scale = geometry.spacing;
                    BlockDisplay display = modelBlock(nativeMapPosition(position()), part, (float)scale, null);
                    deadMarks.add(display);
                    parts.add(display);
                }
            } else if (!dead && !deadMarks.isEmpty()) {
                deadMarks.forEach(boardAudience::remove);
                parts.removeAll(deadMarks);
                deadMarks.clear();
            }
            height = geometry.spacing * (dead ? .195 : .13);
            token = next;
        }

        void move(Token next, Location at, boolean animate) {
            Location start = position();
            List<Location> route = animate ? ludoPath(token, next, start, at) : List.of();
            if (!route.isEmpty() && !path.isEmpty() && frame < duration()) {
                List<Location> remaining = new ArrayList<>();
                remaining.add(start);
                int segment = (int) ((double) frame / duration() * (path.size() - 1));
                for (int index = segment + 1; index < path.size(); index++) remaining.add(path.get(index));
                remaining.addAll(route.subList(1, route.size()));
                route = List.copyOf(remaining);
            }
            from = start;
            to = at;
            path = route;
            pathDuration = route.isEmpty() ? 6 : Math.min(12, Math.max(6, (route.size() - 1) * 2));
            token = next;
            frame = animate ? 0 : duration();
            if (animate) {
                animating.add(this);
            }
            if (!animate) {
                positionParts(at);
            }
        }

        int duration() {
            return room.kind.equals("connectfour") ? 12 : pathDuration;
        }

        Location position() {
            if (room.kind.equals("connectfour")) {
                double u = Math.min(1, frame / 10.0);
                double y =
                        frame <= 10
                                ? from.getY() + (to.getY() - from.getY()) * u * u
                                : to.getY() + (frame < 12 ? .025 : 0);
                return to.clone().set(to.getX(), y, to.getZ());
            }
            if (!path.isEmpty()) {
                double segment = Math.min(1, (double) frame / duration()) * (path.size() - 1);
                int index = Math.min(path.size() - 2, (int) segment);
                double step = segment - index, ease = step * step * (3 - 2 * step);
                Location start = path.get(index), end = path.get(index + 1);
                return start.clone().add(end.toVector().subtract(start.toVector()).multiply(ease))
                        .add(0, Math.pow(Math.sin(Math.PI * step), 2) * geometry.spacing * .65, 0);
            }
            double u = Math.min(1, frame / 6.0), ease = u * u * (3 - 2 * u);
            return from.clone()
                    .add(to.toVector().subtract(from.toVector()).multiply(ease))
                    .add(0, Math.pow(Math.sin(Math.PI * u), 2) * geometry.spacing * .65, 0);
        }

        float flipAngle() {
            float u = Math.min(1, flipFrame / 10f);
            return flipFrom + (flipTo - flipFrom) * u * u * (3 - 2 * u);
        }

        void flip(Token next) {
            if (token.cell.owner() != next.cell.owner()) {
                flipFrom = flipAngle();
                flipTo = next.cell.owner() == 0 ? 0 : (float) Math.PI;
                flipFrame = 0;
                animating.add(this);
            }
            token = next;
        }

        void poseFlip(float angle) {
            float pivot = (float) (geometry.spacing * .065);
            double sine = Math.abs(Math.sin(angle));
            float lift = (float) (Math.max(0, radius * sine + pivot * Math.abs(Math.cos(angle)) - pivot)
                    + geometry.spacing * .03 * sine);
            Quaternionf rotation = new Quaternionf((float) Math.sin(angle / 2), 0, 0, (float) Math.cos(angle / 2));
            flipParts.forEach(
                    (part, base) -> {
                        part.setInterpolationDelay(0);
                        Vector3f translation = base.getTranslation(new Vector3f()).sub(0, pivot, 0)
                                .rotate(rotation).add(0, pivot + lift, 0);
                        part.setTransformation(new Transformation(translation,
                                new Quaternionf(rotation).mul(base.getUnnormalizedRotation(new Quaternionf())),
                                base.getScale(new Vector3f()), new Quaternionf()));
                    });
        }

        void tick() {
            if (frame < duration()) {
                frame++;
                positionParts(position());
            }
            if (flipFrame < 10) {
                flipFrame++;
                poseFlip(flipAngle());
            }
        }

        boolean moving() {
            return frame < duration() || flipFrame < 10;
        }

        void positionParts(Location at) {
            for (Entity part : parts) {
                Location dest = at.clone();
                if (!(part instanceof ItemDisplay)) dest.add(0, nativeMapOffset(), 0);
                if (part instanceof TextDisplay) {
                    dest.add(0, geometry.spacing * (room.kind.equals("xiangqi") ? .235 : .39), 0);
                }
                dest.setYaw(part.getLocation().getYaw());
                dest.setPitch(part.getLocation().getPitch());
                part.teleport(dest);
            }
        }

        boolean valid() {
            for (int index = 0; index < parts.size(); index++)
                if (!parts.get(index).isValid()) return false;
            return true;
        }

        void remove() {
            animating.remove(this);
            parts.forEach(boardAudience::remove);
        }
    }

    private static final class Overlay {
        final String signature;
        final List<Entity> entities = new ArrayList<>();
        final List<BlockDisplay> hover = new ArrayList<>();
        String cell;
        Component feedback = Component.empty();
        int ticks;

        Overlay(String signature) {
            this.signature = signature;
        }

        void remove() {
            entities.forEach(Entity::remove);
            hover.forEach(Entity::remove);
        }
    }

    public TableView(TDGamesPlugin plugin, GameSession room, Location center, NamespacedKey tag, BoardMapStore maps) {
        this.plugin = plugin;
        this.room = room;
        this.tag = tag;
        this.maps = maps;
        geometry = new TableGeometry(room.kind, room.board().cells());
        origin = center.clone().add(0, TableGeometry.SURFACE, 0);
        if (room.board() instanceof GoldenEggOVO.TDGames.Shared.game.ClassicGame) {
            classicTable = new GoldenEggOVO.TDGames.Shared.render.classic.ClassicTable(plugin, room, origin, tag);
            title = tableTitle(1.8, .4);
            classicTable.audience.common(title);
            sync();
            return;
        }
        if (room.kind.equals("yacht")) {
            yachtTable = new YachtTable(plugin, room, origin, tag);
            title = tableTitle(1.8, .35);
            furniture.add(title);
            yachtTable.audience.common(title);
            sync();
            return;
        }
        if (room.board() instanceof SelectedHandGame) {
            playingTable = new PlayingCardTable(plugin, room, origin, tag);
            title = tableTitle(1.8, .4);
            furniture.add(title);
            playingTable.audience.common(title);
            sync();
            return;
        }
        if (room.board() instanceof HandGame) {
            handTable = new HandTable(plugin, room, origin, tag);
            syncHandFurniture();
            title = tableTitle(1.8, .4);
            furniture.add(title);
            if (handTable.audience.managed()) {
                title.setVisibleByDefault(false);
                handTable.audience.common(title);
            }
            sync();
            return;
        }
        boardAudience = new DisplayAudience(plugin, origin, BoardModelKeys.supported(room.kind));
        if (!room.kind.equals("connectfour")) {
            Interaction hit =
                    origin.getWorld()
                            .spawn(
                                    origin.clone().add(0, .012, 0),
                                    Interaction.class,
                                    e -> {
                                        tag(e, "@board");
                                        e.setInteractionWidth(2.25f);
                                        e.setInteractionHeight(.025f);
                                        e.setResponsive(true);
                                    });
            furniture.add(hit);
            boardAudience.common(hit);
        }
        boolean vertical = room.kind.equals("connectfour");
        title = tableTitle(vertical ? 2.05 : 1.65, vertical ? .38 : .48);
        title.setLineWidth(500);
        furniture.add(title);
        boardAudience.common(title);
        if (room.kind.equals("ludo")) {
            diceTray = new DiceTray(plugin, room, center, tag, !room.sideTray, boardAudience);
        }
        syncBoardFurniture();
        sync();
    }

    private void buildNativeBoardFurniture() {
        buildBoardFrame();
        if (room.kind.equals("connectfour")) {
            buildConnectFourRack();
            return;
        }
        buildBoardMapSurface();
    }

    private void buildBoardFrame() {
        buildFurnitureModel("tabletop/shared/square_table");
    }

    private void buildConnectFourRack() {
        buildFurnitureModel("tabletop/connect_four/rack");
    }

    private void buildFurnitureModel(String id) {
        for (var part : ModelRepository.require(id).blocks())
            furniture.add(modelBlock(origin, part, 1, null));
    }

    private void buildBoardMapSurface() {
        List<org.bukkit.inventory.ItemStack> images = maps.get(origin.getWorld(), geometry);
        Class<? extends ItemFrame> frameType = room.board() instanceof GoGame ? GlowItemFrame.class : ItemFrame.class;
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 2; x++) {
                Location at = origin.clone().add(x - .5, 0, z - .5);
                final int tile = z * 2 + x;
                ItemFrame mapFrame =
                        origin.getWorld()
                                .spawn(
                                        at,
                                        frameType,
                                        e -> {
                                            tag(e, "@board");
                                            boardAudience.add(e, false);
                                            e.setFacingDirection(BlockFace.UP, true);
                                            e.setFixed(true);
                                            e.setVisible(false);
                                            e.setRotation(Rotation.NONE);
                                            e.setItem(images.get(tile), false);
                                            e.setItemDropChance(0);
                                        });

                mapFrame.teleport(at);
                mapFrame.setFacingDirection(BlockFace.UP, true);
                furniture.add(mapFrame);
            }
        }
    }

    private void syncHandFurniture() {
        DisplayAudience audience = handTable.audience;
        if (audience.needed(false) && handFurniture.isEmpty()) {
            buildingHandFurniture = true;
            try {
                if (room.kind.equals("color-eight")) {
                    for (var part : RoundCardTable.parts()) {
                        Location at = origin.clone();
                        at.setYaw(0);
                        handFurniture.add(modelBlock(at, part, 1, null));
                    }
                } else {
                    for (var part : MahjongTableFurniture.parts()) {
                        handFurniture.add(modelBlock(origin, part, 1, null));
                    }
                }
            } finally {
                buildingHandFurniture = false;
            }
        } else if (!audience.needed(false) && !handFurniture.isEmpty()) {
            handFurniture.forEach(audience::remove);
            handFurniture.clear();
        }
        if (audience.needed(true) && packedTable == null) {
            packedTable =
                    PackedDisplay.spawn(
                            plugin,
                            room,
                            audience,
                            origin,
                            tag,
                            room.kind.equals("mahjong") ? "mahjong_table" : "card_table",
                            new Vector3f(1),
                            new Quaternionf());
        } else if (!audience.needed(true) && packedTable != null) {
            audience.remove(packedTable);
            packedTable = null;
        }
    }

    private void tag(Entity entity, String id) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|" + id);
    }

    private void display(Display d, Player viewer) {
        display(d, viewer, false);
    }

    private void display(Display d, Player viewer, boolean packed) {
        tag(d, "@model");
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(.35f);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        if (viewer != null) {
            d.setVisibleByDefault(false);
        } else if (boardAudience != null) {
            boardAudience.add(d, packed);
        }
        if (buildingHandFurniture) {
            handTable.audience.add(d, false);
        }
    }

    private BlockDisplay modelBlock(Location at, ModelDefinition.Block part, float scale, Player viewer) {
        var display = block(at, part.material(), ModelGeometry.pose(part, scale), viewer);
        if (part.brightness() != null) display.setBrightness(new Display.Brightness(part.brightness(), part.brightness()));
        return display;
    }

    private BlockDisplay block(
            Location at,
            Material material,
            double x,
            double y,
            double z,
            double w,
            double h,
            double depth,
            Player viewer) {
        return block(at, material, x, y, z, w, h, depth, viewer, false);
    }

    private BlockDisplay block(Location at, Material material, double x, double y, double z,
            double w, double h, double depth, Player viewer, boolean packed) {
        return block(at, material, new Transformation(
                new Vector3f((float)(x-w/2), (float)y, (float)(z-depth/2)), new Quaternionf(),
                new Vector3f((float)w, (float)h, (float)depth), new Quaternionf()), viewer, packed);
    }

    private BlockDisplay block(Location at, Material material, Transformation pose, Player viewer) {
        return block(at, material, pose, viewer, false);
    }

    private BlockDisplay block(Location at, Material material, Transformation pose, Player viewer, boolean packed) {
        var result = origin.getWorld().spawn(at, BlockDisplay.class, entity -> {
            display(entity, viewer, packed);
            entity.setBlock(material.createBlockData());
            entity.setTransformation(pose);
        });
        if (viewer != null) viewer.showEntity(plugin, result);
        return result;
    }

    private TextDisplay text(
            Location at, String value, double scale, boolean flat, NamedTextColor color) {
        return text(at, Component.text(value), scale, flat, color);
    }

    private TextDisplay text(
            Location at, Component value, double scale, boolean flat, NamedTextColor color) {
        return text(at, value, scale, flat, color, false);
    }

    private TextDisplay text(Location at, Component value, double scale, boolean flat, NamedTextColor color, boolean packed) {
        return origin.getWorld()
                .spawn(
                        at,
                        TextDisplay.class,
                        d -> {
                            display(d, null, packed);
                            d.setBillboard(Display.Billboard.FIXED);
                            if (flat) {
                                d.setRotation(0, -90);
                            }
                            d.text(value.colorIfAbsent(color));
                            d.setLineWidth(200);
                            d.setAlignment(TextDisplay.TextAlignment.CENTER);
                            d.setDefaultBackground(false);
                            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                            d.setShadowed(false);
                            d.setSeeThrough(false);
                            d.setTransformation(
                                    new Transformation(
                                            new Vector3f(0, flat ? (float) (-.125 * scale) : 0, 0),
                                            new Quaternionf(),
                                            new Vector3f((float) scale),
                                            new Quaternionf()));
                        });
    }

    private List<Token> desired() {
        List<Token> list = new ArrayList<>();
        for (Cell cell : cells.values()) {
            if (cell.owner() >= 0) {
                if (room.kind.equals("ludo")) {
                    int stack = 0;
                    for (char digit : cell.piece().toCharArray()) {
                        if (digit >= '1' && digit <= '4') {
                            list.add(
                                    new Token(
                                            cell.owner() + ":" + digit,
                                            cell,
                                            stack++ * .82));
                        }
                    }
                } else {
                    list.add(new Token(cell.id(), cell, 0));
                }
            }
        }
        return list;
    }

    private Location at(Token token) {
        if (room.kind.equals("connectfour")) {
            return origin.clone().add((token.cell.x() - 3) * .28, .05 + token.cell.y() * .28, 0);
        }
        return origin.clone()
                .add(
                        geometry.x(token.cell),
                        .003 + token.stack * geometry.spacing - restingBottom(token.cell) * geometry.spacing,
                        geometry.z(token.cell));
    }

    private List<Location> ludoPath(Token before, Token next, Location start, Location destination) {
        if (!room.kind.equals("ludo") || renderedBoard != room.board()
                || room.history.size() != renderedHistory + 1 || before.cell.id().equals(next.cell.id())) return List.of();
        int owner = next.cell.owner();
        int pawn = owner * 4 + next.id.charAt(next.id.length() - 1) - '1';
        if (!lastAction().equals("move:" + pawn + ":" + next.cell.id())) return List.of();
        int color = PieceColors.seatIndex(room.board().publicInfo(), owner);
        int fromStep = ludoStep(before.cell.id(), color), toStep = ludoStep(next.cell.id(), color);
        if (fromStep == -1) return toStep == 0 ? List.of(start, destination) : List.of();
        if (fromStep < 0 || toStep <= fromStep || toStep - fromStep > 6) return List.of();
        List<Location> route = new ArrayList<>();
        route.add(start);
        for (int step = fromStep + 1; step < toStep; step++) {
            String id = step >= 51 ? "ld" + color + "_" + (step - 51) : "sk" + ((color * 13 + step) % 52);
            Cell cell = cells.get(id);
            route.add(at(new Token(next.id, new Cell(id, cell.x(), cell.y(), next.cell.piece(), owner), 0)));
        }
        route.add(destination);
        return List.copyOf(route);
    }

    private int ludoStep(String id, int color) {
        if (id.startsWith("sk")) return Math.floorMod(Integer.parseInt(id.substring(2)) - color * 13, 52);
        if (id.startsWith("ld" + color + "_")) return 51 + Integer.parseInt(id.substring(4));
        if (id.equals("go" + color)) return 56;
        return id.startsWith("ba" + color + "_") ? -1 : -2;
    }

    private double nativeMapOffset() {
        return boardAudience != null && !room.kind.equals("connectfour") ? NATIVE_MAP_OFFSET : 0;
    }

    private Location nativeMapPosition(Location at) {
        return at.clone().add(0, nativeMapOffset(), 0);
    }

    private boolean packedMapViewer(Player viewer) {
        return viewer != null && boardAudience != null && boardAudience.managed() && boardAudience.packed(viewer);
    }

    public double boardPlaneY(Player viewer) {
        return origin.getY() + (nativeMapOffset() == 0 ? .03
                : packedMapViewer(viewer) ? PACKED_MAP_SURFACE_Y : NATIVE_MAP_SURFACE_Y);
    }

    private double restingBottom(Cell cell) {
        var parts = room.kind.equals("reversi") ? BoardPieceModels.reversi()
                : BoardPieceModels.piece(room.kind, cell, room.board().publicInfo());
        return parts.stream().mapToDouble(part -> part.bottom()).min().orElse(0);
    }

    private boolean same(Token a, Token b) {
        return a.cell.owner() == b.cell.owner() && a.cell.piece().equals(b.cell.piece());
    }

    public void sync() {
        if (classicTable != null) { classicTable.sync(); updateTitle(); syncFocusedTitle(); return; }
        if (yachtTable != null) {
            yachtTable.sync();
            renderedBoard = room.board();
            revision = room.revision();
            updateTitle();
            return;
        }
        if (boardAudience != null) {
            syncBoardFurniture();
        }
        if (playingTable != null) {
            playingTable.sync();
            renderedBoard = room.board();
            revision = room.revision();
            updateTitle();
            return;
        }
        if (handTable != null) {
            handTable.sync();
            renderedBoard = room.board();
            revision = room.revision();
            updateTitle();
            return;
        }
        boolean changed =
                revision >= 0 && (revision != room.revision() || renderedBoard != room.board());
        if (revision == room.revision()
                && renderedBoard == room.board()
                && tokens.values().stream().allMatch(TokenView::valid)) {
            updateTitle();
            return;
        }
        cells.clear();
        room.board().cells().forEach(cell -> cells.put(cell.id(), cell));
        if (changed) {
            overlays.values().forEach(Overlay::remove);
            overlays.clear();
        }
        Map<String, TokenView> remainingTokens = new LinkedHashMap<>(tokens),
                updatedTokens = new LinkedHashMap<>();
        List<Token> pending = reuseTokens(remainingTokens, updatedTokens, changed);
        String action = lastAction();
        String[] move = action.split(":");
        moveOrCreateTokens(pending, remainingTokens, updatedTokens, move, changed);
        remainingTokens.values().forEach(TokenView::remove);
        tokens.clear();
        tokens.putAll(updatedTokens);
        if (changed) {
            lastMove.forEach(boardAudience::remove);
            lastMove.clear();
            lastMoveLayers.clear();
            lastDestination = move.length >= 3 ? move[2] : move.length == 2 ? move[1] : null;
            syncSurfaceLayers();
        }
        if (diceTray != null) {
            int value =
                    Math.max(
                            1, Integer.parseInt(room.board().publicInfo().getOrDefault("dice", "1")));
            if (renderedBoard == room.board()
                    && room.history.size() == renderedHistory + 1
                    && action.equals("roll")) {
                diceTray.roll(value, room.seed() ^ room.history.size());
            } else if (renderedBoard != room.board()) {
                diceTray.settle(value);
            }
        }
        renderedHistory = room.history.size();
        renderedBoard = room.board();
        revision = room.revision();
        updateTitle();
    }

    private void syncFocusedTitle() {
        if (!room.kind.equals("2048")) return;
        var controller = plugin.tablePlayers();
        Map<UUID, Player> focused = new HashMap<>();
        if (controller != null) for (Player player : origin.getWorld().getPlayers())
            if (room.seat(player.getUniqueId()) >= 0 && controller.hasTableFocus(player))
                focused.put(player.getUniqueId(), player);
        for (var entry : focusedTitleViewers.entrySet())
            if (!focused.containsKey(entry.getKey())) entry.getValue().showEntity(plugin, title);
        boolean audienceChanged = titleAudienceGeneration != classicTable.audience.generation;
        for (var entry : focused.entrySet())
            if (audienceChanged || !focusedTitleViewers.containsKey(entry.getKey()))
                entry.getValue().hideEntity(plugin, title);
        focusedTitleViewers.clear();
        focusedTitleViewers.putAll(focused);
        titleAudienceGeneration = classicTable.audience.generation;
    }

    private List<Token> reuseTokens(
            Map<String, TokenView> remainingTokens,
            Map<String, TokenView> updatedTokens,
            boolean changed) {
        List<Token> pending = new ArrayList<>();
        for (Token desiredToken : desired()) {
            TokenView existing = remainingTokens.get(desiredToken.id);
            if (existing != null
                    && existing.valid()
                    && Set.of("go", "go9", "go13").contains(room.kind)
                    && existing.token.cell.owner() == desiredToken.cell.owner()) {
                remainingTokens.remove(desiredToken.id);
                existing.mark(desiredToken);
                updatedTokens.put(desiredToken.id, existing);
                continue;
            }
            if (existing != null && existing.valid() && room.kind.equals("reversi")) {
                remainingTokens.remove(desiredToken.id);
                existing.flip(desiredToken);
                updatedTokens.put(desiredToken.id, existing);
                continue;
            }
            if (existing != null
                    && existing.valid()
                    && (room.kind.equals("ludo") || same(existing.token, desiredToken))) {
                remainingTokens.remove(desiredToken.id);
                boolean moved =
                        !existing.token.cell.id().equals(desiredToken.cell.id())
                                || existing.token.stack != desiredToken.stack;
                if (moved) {
                    existing.move(desiredToken, at(desiredToken), changed);
                } else {
                    existing.token = desiredToken;
                }
                updatedTokens.put(desiredToken.id, existing);
            } else {
                pending.add(desiredToken);
            }
        }
        return pending;
    }

    private void moveOrCreateTokens(
            List<Token> pending,
            Map<String, TokenView> remainingTokens,
            Map<String, TokenView> updatedTokens,
            String[] move,
            boolean changed) {
        for (Token desiredToken : pending) {
            TokenView source = null;
            String sourceId = null;
            if (changed
                    && move.length >= 3
                    && move[0].equals("move")
                    && move[2].equals(desiredToken.cell.id())) {
                source = remainingTokens.get(move[1]);
                sourceId = move[1];
                if (source != null && (!source.valid() || !same(source.token, desiredToken))) {
                    source = null;
                    sourceId = null;
                }
            }
            if (source == null
                    && changed
                    && !Set.of("ludo", "reversi", "connectfour").contains(room.kind)) {
                for (var entry : remainingTokens.entrySet()) {
                    if (entry.getValue().valid()
                            && same(entry.getValue().token, desiredToken)
                            && !entry.getValue().token.cell.id().equals(desiredToken.cell.id())) {
                        source = entry.getValue();
                        sourceId = entry.getKey();
                        break;
                    }
                }
            }
            if (source != null) {
                remainingTokens.remove(sourceId);
                source.move(desiredToken, at(desiredToken), true);
                updatedTokens.put(desiredToken.id, source);
            } else {
                Location destination = at(desiredToken);
                Location animationStart = destination.clone();
                if (changed) {
                    animationStart.setY(
                            room.kind.equals("connectfour")
                                    ? origin.getY() + 1.9
                                    : destination.getY() + geometry.spacing * .8);
                }
                TokenView created = new TokenView(desiredToken, animationStart);
                if (changed) {
                    created.move(desiredToken, destination, true);
                }
                updatedTokens.put(desiredToken.id, created);
            }
        }
    }

    private void syncBoardFurniture() {
        boardAudience.refresh();
        if (boardAudience.needed(false) && nativeBoardFurniture.isEmpty()) {
            Set<Entity> before = new HashSet<>(furniture);
            buildNativeBoardFurniture();
            for (Entity entity : furniture) {
                if (!before.contains(entity)) {
                    if (entity instanceof TextDisplay || entity instanceof Interaction) {
                        boardAudience.common(entity);
                    } else {
                        nativeBoardFurniture.add(entity);
                    }
                }
            }
        } else if (!boardAudience.needed(false) && !nativeBoardFurniture.isEmpty()) {
            nativeBoardFurniture.forEach(boardAudience::remove);
            furniture.removeAll(nativeBoardFurniture);
            nativeBoardFurniture.clear();
        }
        if (BoardModelKeys.supported(room.kind)
                && boardAudience.needed(true)
                && packedBoard == null) {
            packedBoard =
                    packedItem(
                            origin,
                            BoardModelKeys.table(room.kind),
                            room.kind.equals("connectfour") ? 2 : 1);
        } else if (!boardAudience.needed(true) && packedBoard != null) {
            boardAudience.remove(packedBoard);
            packedBoard = null;
        }
        int neededLayers =
                (boardAudience.needed(false) ? 1 : 0) | (boardAudience.needed(true) ? 2 : 0);
        if (boardLayers != neededLayers) {
            for (TokenView token : tokens.values()) {
                token.rebuildLayers();
            }
            boardLayers = neededLayers;
            lastMove.forEach(boardAudience::remove); lastMove.clear(); lastMoveLayers.clear();
            for (TextDisplay river : riverLayers.values()) { boardAudience.remove(river); furniture.remove(river); }
            riverLayers.clear();
        }
        syncSurfaceLayers();
    }

    private void syncSurfaceLayers() {
        if (nativeMapOffset() == 0) return;
        for (boolean packed : new boolean[] {false, true}) {
            if (!boardAudience.needed(packed)) continue;
            if (room.kind.equals("xiangqi") && !riverLayers.containsKey(packed)) {
                var river = text(origin.clone().add(0, PACKED_MAP_SURFACE_Y + .0015 + (packed ? 0 : nativeMapOffset()), 0),
                        Component.text(GameSymbols.XIANGQI_RIVER), .26, true, NamedTextColor.DARK_GRAY, packed);
                riverLayers.put(packed, river); furniture.add(river);
            }
            if (lastDestination != null && geometry.byId.containsKey(lastDestination) && !lastMoveLayers.containsKey(packed)) {
                List<Entity> parts = new ArrayList<>();
                ring(parts, lastDestination, Material.GOLD_BLOCK, null, .90, packed);
                lastMoveLayers.put(packed, parts); lastMove.addAll(parts);
            }
        }
    }

    private ItemDisplay packedItem(Location at, String model, float scale) {
        return origin.getWorld()
                .spawn(
                        at,
                        ItemDisplay.class,
                        entity -> {
                            tag(entity, "@model");
                            entity.setBrightness(new Display.Brightness(15, 15));
                            entity.setViewRange(.35f);
                            entity.setTeleportDuration(2);
                            entity.setInterpolationDuration(2);
                            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            entity.setItemStack(plugin.pack().item(model));
                            entity.setTransformationMatrix(new Matrix4f().scale(scale));
                            boardAudience.add(entity, true);
                        });
    }

    private String lastAction() {
        if (room.history.isEmpty()) {
            return "";
        }
        var e = room.history.get(room.history.size() - 1).getAsJsonObject().get("action");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private TextDisplay tableTitle(double height, double scale) {
        TextDisplay display = text(origin.clone().add(0, height, 0), "", scale, false, NamedTextColor.GOLD);
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        return display;
    }

    private void updateTitle() {
        if (room.kind.equals("mahjong")
                && room.phase() != GameSession.Phase.LOBBY
                && room.phase() != GameSession.Phase.STARTING) {
            if (!Component.empty().equals(lastTitle)) {
                title.text(Component.empty());
                lastTitle = Component.empty();
            }
            titleState = null;
            return;
        }
        boolean changed = titleState == null || !titleState.matches(room);
        if (changed) {
            int turn = room.board().currentPlayer();
            Component status =
                    room.board().finished()
                            ? RoomText.outcome(room, room.board().outcome())
                            : Language.component(
                                    "table.turn",
                                    "player",
                                    turn >= 0 && turn < room.seats.size()
                                            ? RoomText.player(room.seats.get(turn), turn + 1)
                                            : Language.component("room.player"));
            if (!room.board().finished() && room.board() instanceof GoldenEggOVO.TDGames.Shared.game.ClassicGame classic
                    && java.util.stream.IntStream.range(0, classic.playerCount()).filter(seat -> !classic.legalActions(seat).isEmpty()).count() > 1)
                status = Language.component("room.simultaneous-turn");
            if (room.phase() == GameSession.Phase.LOBBY) {
                status = Language.component("table.waiting");
            } else if (room.phase() == GameSession.Phase.FINISHED && !room.result().isEmpty()) {
                status = RoomText.outcome(room, room.result());
            } else if (room.phase() == GameSession.Phase.PAUSED || room.phase() == GameSession.Phase.ABORTED) {
                status = RoomText.phase(room);
            }
            Component value =
                    Language.component(
                                    "table.title",
                                    "game",
                                    RoomText.game(room.kind),
                                    "number",
                                    room.table + 1)
                            .append(Component.newline())
                            .append(status.colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.newline())
                            .append(
                                    RoomText.roster(room.seats, room.capacity)
                                            .colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.newline())
                            .append(
                                    Language.component(
                                                    handTable != null
                                                            ? "table.hand.hint"
                                                            : room.kind.equals("yacht")
                                                                    ? "table.yacht.hint"
                                                                    : room.kind.equals("ludo")
                                                                            ? "table.ludo.hint"
                                                                            : "table.hint")
                                            .colorIfAbsent(NamedTextColor.GRAY))
                            .append(Component.newline())
                            .append(
                                    Language.component("table.join")
                                            .colorIfAbsent(NamedTextColor.GRAY));
            if (!value.equals(lastTitle)) {
                var fit = LabelLayout.fit(value, 2.8f, .48f);
                title.text(fit.text());

                title.setLineWidth(Integer.MAX_VALUE);
                title.setTransformation(
                        new Transformation(
                                new Vector3f(),
                                new Quaternionf(),
                                new Vector3f(fit.scale()),
                                new Quaternionf()));
                lastTitle = value;
            }
            titleState = new TitleState(room);
        }
        if (diceTray != null && (changed || wasRolling != rolling())) {
            String key =
                    rolling()
                            ? "table.dice.rolling"
                            : room.board().publicInfo().getOrDefault("pendingRoll", "0").equals("0")
                                    ? "table.dice.roll"
                                    : room.kind.equals("ludo")
                                            ? "table.ludo.choose"
                                            : "table.dice.choose";
            if (changed || !key.equals(lastDiceKey)) {
                diceTray.label(Language.component(key).colorIfAbsent(NamedTextColor.GOLD));
                lastDiceKey = key;
            }
            wasRolling = rolling();
        }
    }

    public void tick() {
        if (classicTable != null) { classicTable.tick(); updateTitle(); syncFocusedTitle(); return; }
        if (yachtTable != null) {
            yachtTable.tick();
        }
        if (boardAudience != null) {
            syncBoardFurniture();
        }
        for (var iterator = animating.iterator(); iterator.hasNext(); ) {
            TokenView token = iterator.next();
            token.tick();
            if (!token.moving()) {
                iterator.remove();
            }
        }
        if (diceTray != null) {
            diceTray.tick();
        }
        if (playingTable != null) {
            playingTable.sync();
        }
        if (handTable != null) {
            handTable.tick();
            syncHandFurniture();
        }
        updateTitle();
    }

    public static Quaternionf faceRotation(int face) {
        float half = (float) (Math.PI / 2);
        return switch (face) {
            case 1 -> new Quaternionf();
            case 2 -> new Quaternionf().rotateZ(-half);
            case 3 -> new Quaternionf().rotateX(half);
            case 4 -> new Quaternionf().rotateX(-half);
            case 5 -> new Quaternionf().rotateZ(half);
            case 6 -> new Quaternionf().rotateX(half * 2);
            default -> throw new IllegalArgumentException("dice face");
        };
    }

    public boolean moving() {
        return !animating.isEmpty() || classicTable != null && classicTable.moving();
    }

    public void renderNativeDiceMidpoint() {
        if (classicTable != null) classicTable.renderNativeDiceMidpoint();
        if (yachtTable != null) yachtTable.renderNativeMidpoint();
        if (diceTray != null) diceTray.renderNativeMidpoint();
    }

    public boolean rolling() {
        if (classicTable != null) return classicTable.rolling();
        return yachtTable != null ? yachtTable.rolling() : diceTray != null && diceTray.rolling();
    }

    public String handHit(Player player, Location eye, org.bukkit.util.Vector direction) {
        return playingTable != null
                ? playingTable.handHit(player, eye, direction)
                : handTable == null ? null : handTable.hit(player, eye, direction);
    }

    public boolean deckHit(Location eye, org.bukkit.util.Vector direction) {
        return handTable != null && handTable.deckHit(eye, direction);
    }

    public String handCallHit(Player player, Location eye, org.bukkit.util.Vector direction) {
        return playingTable != null
                ? playingTable.callHit(player, eye, direction)
                : handTable == null ? null : handTable.callHit(player, eye, direction);
    }

    public void dismissHandCalls(Player player) {
        if (handTable != null) {
            handTable.dismissCalls(player);
        }
    }

    public boolean toggleMahjongAssistance(Player player, String option) {
        return handTable != null && handTable.toggleAssistance(player, option);
    }

    public MahjongAssist mahjongAssistance(int seat) {
        return handTable == null ? null : handTable.assistance(seat);
    }

    public boolean expandHandCall(Player player, String group) {
        return handTable != null && handTable.expandCall(player, group);
    }

    public String mahjongHandAction(Player player, String id) {
        return handTable == null ? null : handTable.handAction(player, id);
    }

    public String cardHandAction(Player player, String id) {
        return playingTable != null
                ? playingTable.cardAction(player, id)
                : handTable == null ? null : handTable.cardAction(player, id);
    }

    public String playingAction(Player player, String control) {
        return playingTable == null ? null : playingTable.action(player, control);
    }

    public void maintainMahjongPress(Player player) {
        if (handTable != null) {
            handTable.keepHandPress(player);
        }
    }

    public record Hit(String cell, double distance) {}

    public double menuHit(Location eye, org.bukkit.util.Vector direction) {
        if (classicTable != null) return classicTable.menuHit(eye, direction);
        return TableGeometry.menuHit(room.kind, room.sideTray, origin, eye, direction);
    }

    public String classicHit(Player player, Location eye, org.bukkit.util.Vector direction) {
        if (classicTable == null) return null;
        var hit = classicTable.hit(player, eye, direction);
        return hit == null ? null : hit.id();
    }

    public String classicAction(Player player, String hit) {
        return classicTable == null ? null : classicTable.action(player, hit);
    }

    public Hit hitPiece(Location eye, org.bukkit.util.Vector direction) {
        return hitPiece(null, eye, direction);
    }

    public Hit hitPiece(Player viewer, Location eye, org.bukkit.util.Vector direction) {
        if (yachtTable != null) {
            return yachtTable.hit(eye, direction);
        }
        Hit nearest = null;
        var from = eye.toVector();
        double surfaceOffset = packedMapViewer(viewer) ? 0 : nativeMapOffset();
        for (TokenView token : tokens.values()) {
            Location position = token.position();
            double bottom = position.getY() + surfaceOffset;
            var box =
                    new org.bukkit.util.BoundingBox(
                            position.getX() - token.radius,
                            bottom,
                            position.getZ() - token.radius,
                            position.getX() + token.radius,
                            bottom + token.height,
                            position.getZ() + token.radius);
            var hit = box.rayTrace(from, direction, TableGeometry.REACH);
            if (hit != null) {
                double distance = hit.getHitPosition().distance(from);
                if (nearest == null || distance < nearest.distance) {
                    nearest = new Hit(token.token.cell.id(), distance);
                }
            }
        }
        if (diceTray != null) {
            double distance = diceTray.hit(eye, direction);
            if (distance >= 0 && (nearest == null || distance < nearest.distance)) {
                nearest = new Hit("@roll", distance);
            }
        }
        return nearest;
    }

    public String verticalHit(Location eye, org.bukkit.util.Vector direction) {
        if (Math.abs(direction.getZ()) < 1e-6) {
            return null;
        }
        double distance = (origin.getZ() - eye.getZ()) / direction.getZ();
        if (distance < 0 || distance > TableGeometry.REACH) {
            return null;
        }
        if (eye.getWorld()
                        .rayTraceBlocks(
                                eye,
                                direction,
                                Math.max(.001, distance - .035),
                                FluidCollisionMode.NEVER,
                                true)
                != null) {
            return null;
        }
        var point = eye.toVector().add(direction.clone().multiply(distance));
        double x = point.getX() - origin.getX(), y = point.getY() - origin.getY();
        int col = (int) Math.floor(x / .28 + 3.5), row = (int) Math.floor((y - .02) / .28);
        return col >= 0 && col < 7 && row >= 0 && row < 6 ? col + "," + row : null;
    }

    public void cursor(Player player, BoardSelection pick, String hover) {
        if (classicTable != null) { classicTable.hover(player, hover); return; }
        if (yachtTable != null) {
            yachtTable.cursor(player, hover);
            return;
        }
        if (playingTable != null) {
            playingTable.hover(
                    player,
                    hover != null && (hover.startsWith("@hand:") || hover.startsWith("@call:")) ? hover.substring(6) : null);
            return;
        }
        if (handTable != null) {
            handTable.hoverButtons(player, hover != null && hover.startsWith("@call:") ? hover.substring(6) : null);
            handTable.hover(
                    player,
                    hover != null && (hover.startsWith("@hand:") || hover.startsWith("@tile:"))
                            ? hover.substring(6)
                            : null);
            handTable.assistanceHint(player, hover);
            return;
        }
        boolean turn =
                room.phase() == GameSession.Phase.PLAYING
                        && !room.busy()
                        && room.seat(player.getUniqueId()) >= 0
                        && (room.seat(player.getUniqueId()) == room.board().currentPlayer()
                                || room.board() instanceof GoGame go
                                        && go.scoring());
        String signature =
                room.revision()
                        + "/"
                        + room.phase()
                        + "/"
                        + room.busy()
                        + "/"
                        + turn
                        + "/"
                        + rolling()
                        + "/"
                        + Language.generation()
                        + "/" + (nativeMapOffset() != 0 && packedMapViewer(player))
                        + "/"
                        + (pick == null ? "" : pick.source());
        Overlay old = overlays.get(player.getUniqueId());
        boolean reset = old == null || !old.signature.equals(signature);
        if (reset) {
            if (old != null) {
                old.remove();
            }
            old = new Overlay(signature);
            overlays.put(player.getUniqueId(), old);
        }
        if (room.kind.equals("connectfour")) {
            columnCursor(player, old, reset, turn, hover);
            return;
        }
        if (room.kind.equals("ludo")) {
            ludoCursor(player, old, reset, turn && !rolling(), hover);
            return;
        }
        if (reset && turn && pick != null) {
            ring(old.entities, pick.source(), Material.CYAN_CONCRETE, player, 1.0);
            Set<String> destinations = new HashSet<>();
            for (String action : pick.actions()) {
                String[] parts = action.split(":");
                if (parts.length >= 3) {
                    destinations.add(parts[2]);
                }
            }
            for (String id : destinations) {
                Cell cell = cells.get(id);
                if (cell != null) {
                    if (cell.owner() >= 0) {
                        ring(old.entities, id, Material.LIME_CONCRETE, player, .96);
                    } else {
                        old.entities.add(
                                block(
                                        origin.clone()
                                                .add(geometry.x(cell), .023 + (packedMapViewer(player) ? 0 : nativeMapOffset()), geometry.z(cell)),
                                        Material.LIME_CONCRETE,
                                        0,
                                        0,
                                        0,
                                        geometry.spacing * .20,
                                        .018,
                                        geometry.spacing * .20,
                                        player));
                    }
                }
            }
        }
        if (!reset && Objects.equals(old.cell, hover)) {
            if (++old.ticks % 10 == 0) {
                player.sendActionBar(old.feedback);
            }
            return;
        }
        old.cell = hover;
        old.ticks = 0;
        if (hover != null && cells.containsKey(hover)) {
            Cell cell = cells.get(hover);
            boolean direct =
                    turn
                            && room
                                    .board()
                                    .actionsForCell(room.seat(player.getUniqueId()), hover)
                                    .stream()
                                    .anyMatch(
                                            a ->
                                                    a.startsWith("place:")
                                                            || a.startsWith("dead:")
                                                            || a.startsWith("hold:"));
            boolean legal =
                    turn
                            && (direct
                                    || pick != null
                                            && !BoardSelection.destinationActions(pick, hover).isEmpty()
                                    || !BoardSelection.sourceActions(
                                                    room.board(),
                                                    room.seat(player.getUniqueId()),
                                                    hover)
                                            .isEmpty());
            Material material =
                    !turn
                            ? Material.GRAY_CONCRETE
                            : legal ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE;
            if (old.hover.isEmpty()) {
                List<Entity> list = new ArrayList<>();
                ring(list, hover, material, player, .82);
                for (Entity entity : list) {
                    old.hover.add((BlockDisplay) entity);
                }
            } else {
                Location at = origin.clone().add(geometry.x(cell), BOARD_CURSOR_Y + (packedMapViewer(player) ? 0 : nativeMapOffset()), geometry.z(cell));
                for (BlockDisplay d : old.hover) {
                    d.teleport(at);
                    d.setBlock(material.createBlockData());
                }
            }
            String hint =
                    !turn
                            ? blockedHint()
                            : legal
                                    ? direct
                                            ? cell.piece().contains("×")
                                                    ? "hint.restore"
                                                    : "hint.act"
                                            : pick == null ? "hint.select" : "hint.place"
                                    : pick == null ? "hint.unavailable" : "hint.invalid";
            old.feedback =
                    Language.component(
                                    "hint.cell",
                                    "coordinate",
                                    Component.text(BoardMessages.coordinate(room.kind, cell)),
                                    "piece",
                                    cell.owner() < 0
                                            ? Language.component("hint.empty")
                                            : Component.text(Language.glyph(cell.piece())),
                                    "action",
                                    Language.component(hint))
                            .colorIfAbsent(legal ? NamedTextColor.YELLOW : NamedTextColor.GRAY);
        } else {
            old.hover.forEach(Entity::remove);
            old.hover.clear();
            old.feedback =
                    ("@roll".equals(hover)
                                    ? Language.component(
                                            rolling()
                                                    ? "hint.rolling"
                                                    : turn ? "hint.roll" : blockedHint())
                                    : "@menu".equals(hover)
                                            ? Language.component("hint.menu")
                                            : Component.empty())
                            .colorIfAbsent(NamedTextColor.GOLD);
        }
        player.sendActionBar(old.feedback);
    }

    private String blockedHint() {
        return switch (room.phase()) {
            case LOBBY -> "hint.lobby";
            case STARTING -> "hint.starting";
            case FINISHED, ABORTED -> "hint.finished";
            case PAUSED -> "hint.paused";
            case PLAYING -> room.busy() ? "hint.busy" : "hint.wait";
        };
    }

    private void ludoCursor(
            Player player, Overlay overlay, boolean reset, boolean turn, String hover) {
        int seat = room.seat(player.getUniqueId());
        if (reset && turn) {
            for (Cell cell : cells.values()) {
                if (cell.owner() == seat
                        && !BoardSelection.sourceActions(room.board(), seat, cell.id()).isEmpty()) {
                    ring(overlay.entities, cell.id(), Material.YELLOW_CONCRETE, player, .92);
                }
            }
        }
        if (!reset && Objects.equals(overlay.cell, hover)) {
            if (++overlay.ticks % 10 == 0) {
                player.sendActionBar(overlay.feedback);
            }
            return;
        }
        overlay.cell = hover;
        overlay.ticks = 0;
        overlay.hover.forEach(Entity::remove);
        overlay.hover.clear();
        List<String> choices = turn ? BoardSelection.sourceActions(room.board(), seat, hover) : List.of();
        if (!choices.isEmpty()) {
            String[] action = choices.getFirst().split(":");
            Cell destination = cells.get(action[2]);
            List<Entity> preview = new ArrayList<>();
            ring(preview, destination.id(), Material.LIME_CONCRETE, player, .96);
            preview.forEach(entity -> overlay.hover.add((BlockDisplay) entity));
            String effect =
                    destination.owner() >= 0 && destination.owner() != seat
                            ? "hint.ludo.capture"
                            : destination.id().startsWith("go")
                                    ? "hint.ludo.finish"
                                    : "hint.ludo.step";
            overlay.feedback =
                    Language.component(
                                    choices.size() > 1 ? "hint.ludo.stack" : "hint.ludo.move",
                                    "count",
                                    choices.size(),
                                    "pawn",
                                    Integer.parseInt(action[1]) % 4 + 1,
                                    "destination",
                                    BoardMessages.coordinate("ludo", destination),
                                    "effect",
                                    Language.component(effect))
                            .colorIfAbsent(NamedTextColor.GREEN);
        } else {
            String key =
                    rolling()
                            ? "hint.rolling"
                            : !turn
                                    ? blockedHint()
                                    : room.board().legalActions(seat).contains("roll")
                                            ? "hint.roll"
                                            : "table.ludo.choose";
            if ("@menu".equals(hover)) {
                key = "hint.menu";
            }
            overlay.feedback = Language.component(key).colorIfAbsent(NamedTextColor.GOLD);
        }
        player.sendActionBar(overlay.feedback);
    }

    private void columnCursor(
            Player player, Overlay overlay, boolean reset, boolean turn, String hover) {
        Cell aimed = hover == null ? null : cells.get(hover);
        String column = aimed == null ? null : Integer.toString(aimed.x());
        if (!reset && Objects.equals(overlay.cell, column)) {
            if (++overlay.ticks % 10 == 0) {
                player.sendActionBar(overlay.feedback);
            }
            return;
        }
        overlay.cell = column;
        overlay.ticks = 0;
        Cell landing =
                aimed == null
                        ? null
                        : cells.values().stream()
                                .filter(cell -> cell.x() == aimed.x() && cell.owner() < 0)
                                .min(Comparator.comparingInt(Cell::y))
                                .orElse(null);
        boolean legal =
                turn
                        && landing != null
                        && room.board()
                                .legalActions(room.seat(player.getUniqueId()))
                                .contains("drop:" + column);
        if (legal) {
            Location at =
                    origin.clone().add((landing.x() - 3) * .28, .05 + landing.y() * .28 + .12, 0);
            if (overlay.hover.isEmpty()) {

                for (double side : new double[] {-.132, .132}) {
                    overlay.hover.add(
                            block(
                                    at,
                                    Material.LIME_CONCRETE,
                                    side,
                                    -.125,
                                    0,
                                    .014,
                                    .25,
                                    .16,
                                    player));
                    overlay.hover.add(
                            block(
                                    at,
                                    Material.LIME_CONCRETE,
                                    0,
                                    side - .007,
                                    0,
                                    .278,
                                    .014,
                                    .16,
                                    player));
                }
            } else {
                overlay.hover.forEach(entity -> entity.teleport(at));
            }
        } else {
            overlay.hover.forEach(Entity::remove);
            overlay.hover.clear();
        }
        overlay.feedback =
                (!turn
                                ? Language.component(blockedHint())
                                : aimed == null
                                        ? Language.component("hint.column.aim")
                                        : Language.component(
                                                legal ? "hint.column" : "hint.column.full",
                                                "column",
                                                aimed.x() + 1))
                        .colorIfAbsent(legal ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        player.sendActionBar(overlay.feedback);
    }

    private void ring(
            List<Entity> list, String id, Material material, Player viewer, double fraction) {
        ring(list, id, material, viewer, fraction, packedMapViewer(viewer));
    }

    private void ring(List<Entity> list, String id, Material material, Player viewer, double fraction, boolean packed) {
        Cell cell = geometry.byId.get(id);
        if (cell == null) {
            return;
        }
        double width = geometry.spacing * fraction, stroke = geometry.spacing * .065;
        Location at = origin.clone().add(geometry.x(cell), BOARD_CURSOR_Y + (packed ? 0 : nativeMapOffset()), geometry.z(cell));
        for (double side : new double[] {-width / 2, width / 2}) {
            list.add(block(at, material, side, 0, 0, stroke, TableGeometry.SELECTION_THICKNESS, width - stroke, viewer, packed));
            list.add(block(at, material, 0, 0, side, width + stroke, TableGeometry.SELECTION_THICKNESS, stroke, viewer, packed));
        }
    }

    public void clearInteraction(Player player) {
        if (playingTable != null) playingTable.clearInteraction(player);
        else clear(player);
    }

    public void clear(Player player) {
        if (classicTable != null) { classicTable.clear(player); return; }
        if (yachtTable != null) {
            yachtTable.clear(player);
        }
        if (handTable != null) {
            handTable.clear(player);
        }
        if (playingTable != null) {
            playingTable.clear(player);
        }
        Overlay old = overlays.remove(player.getUniqueId());
        if (old != null) {
            old.remove();
        }
    }

    @Override
    public void close() {
        if (classicTable != null) { classicTable.close(); classicTable.audience.remove(title); return; }
        if (handTable != null) {
            handTable.close();
        }
        if (playingTable != null) {
            playingTable.close();
        }
        if (diceTray != null) {
            diceTray.close();
        }
        if (yachtTable != null) {
            yachtTable.close();
        }
        tokens.values().forEach(TokenView::remove);
        tokens.clear();
        handFurniture.forEach(Entity::remove);
        if (packedTable != null) {
            packedTable.remove();
        }
        if (packedBoard != null) {
            boardAudience.remove(packedBoard);
        }
        furniture.forEach(
                entity -> {
                    if (playingTable != null) {
                        playingTable.audience.remove(entity);
                    } else if (boardAudience == null) {
                        entity.remove();
                    } else {
                        boardAudience.remove(entity);
                    }
                });
        overlays.values().forEach(Overlay::remove);
        overlays.clear();
        if (boardAudience != null) {
            lastMove.forEach(boardAudience::remove);
        } else {
            lastMove.forEach(Entity::remove);
        }
    }
}

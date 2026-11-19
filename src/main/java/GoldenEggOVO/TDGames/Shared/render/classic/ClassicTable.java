package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.render.DisplayPlacement;
import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Core.render.PlayingCardHeads;
import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.LabelLayout;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.TableButtonStyle;
import GoldenEggOVO.TDGames.Shared.render.TablePlacement;
import GoldenEggOVO.TDGames.Shared.render.dice.DiceTray;
import GoldenEggOVO.TDGames.Multiplayer.game.backgammon.BackgammonGame;
import GoldenEggOVO.TDGames.Multiplayer.game.battleship.BattleshipGame;
import GoldenEggOVO.TDGames.Shared.render.packed.ShogiPieceModels;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData;
import GoldenEggOVO.TDGames.Singleplayer.game.klondike.KlondikeGame;
import GoldenEggOVO.TDGames.Shared.text.ClassicText;
import GoldenEggOVO.TDGames.Shared.text.MonopolyText;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Dedicated classic-game displays; every dynamic view belongs to exactly one viewer. */
public final class ClassicTable implements AutoCloseable {
    public record Hit(String id, double distance) { }
    private static final double MONOPOLY_PAWN_ROW_STEP = .040;
    private static final Material[] COLORS = {Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.LIME_CONCRETE,
            Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE};
    private static final Material[] PEG_COLORS = {Material.BLUE_CONCRETE, Material.RED_CONCRETE, Material.LIME_CONCRETE,
            Material.YELLOW_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_CONCRETE};
    private static final Map<String, String> SHOGI = Map.ofEntries(
            Map.entry("P", "\u6b69"), Map.entry("L", "\u9999"), Map.entry("N", "\u6842"), Map.entry("S", "\u9280"),
            Map.entry("G", "\u91d1"), Map.entry("B", "\u89d2"), Map.entry("R", "\u98db"), Map.entry("K", "\u738b"),
            Map.entry("+P", "\u3068"), Map.entry("+L", "\u674f"), Map.entry("+N", "\u572d"),
            Map.entry("+S", "\u5168"), Map.entry("+B", "\u99ac"), Map.entry("+R", "\u9f8d"));
    private final TDGamesPlugin plugin;
    private final GameSession room;
    private final Location origin;
    private final NamespacedKey tag;
    public final DisplayAudience audience;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private final List<Entity> nativeFurniture = new ArrayList<>(), packedFurniture = new ArrayList<>();
    private final List<Interaction> clickTargets = new ArrayList<>(2);
    private boolean closed;
    private final DiceTray diceTray;
    private ClassicGame diceBoard;
    private int diceHistory;
    private record MonopolyState(MonopolyGame.PropertyView property, List<MonopolyGame.PlayerView> players) { }

    private final class Visual {
        Cell cell;
        final ClassicLayout.Slot slot;
        final boolean expanded;
        final MonopolyState monopoly;
        final List<Entity> parts = new ArrayList<>();
        final List<Entity> seeds = new ArrayList<>();
        TextDisplay count;
        boolean localized;
        final Map<Integer, List<Entity>> pawns = new HashMap<>();
        Visual(View view, Cell cell, ClassicLayout.Slot slot, MonopolyState monopoly) {
            this.cell = cell;
            this.slot = slot;
            expanded = cell.id().equals(view.selected);
            this.monopoly = monopoly;
            drawCell(view, this);
        }
        void close() { parts.forEach(DisplayAudience::retire); }
    }

    private final class View {
        final Player player;
        final Map<String, Visual> visuals = new LinkedHashMap<>();
        final Map<String, List<Entity>> highlights = new LinkedHashMap<>();
        final Map<String, Material> highlightColors = new LinkedHashMap<>();
        final Map<String, List<Entity>> controls = new LinkedHashMap<>();
        List<String> actions = List.of(), controlActions = List.of(), choices = List.of();
        ClassicLayout layout;
        ClassicGame board;
        GameSession.Phase phase;
        long revision = -1, language = -1;
        int seat, page, hoverPulses;
        boolean packed;
        String selected, hover;
        MonopolyTableInput input;
        Component feedbackText;
        Component lastFeedback;
        MonopolyGame.TradeView shownTrade;
        List<MonopolyGame.PlayerView> accounts;
        List<MonopolyGame.PropertyView> deeds;
        List<Cell> snapshot = List.of();
        int historySize;
        final List<PieceMotion> motions = new ArrayList<>();
        final List<CardTurn> turns = new ArrayList<>();
        boolean animateCards, revealingCard;
        boolean hidingTiles;
        TileSlide tileSlide;
        Map<Integer, String> pawnSquares = Map.of();
        List<MancalaSowing.Step> sowing = List.of();
        int sowStep, sowFrame;
        int hintTicks;
        Location hintAt;

        View(Player player, int seat, boolean packed) { this.player = player; this.seat = seat; this.packed = packed; }
        void clearVisuals() {
            stopMotion();
            visuals.values().forEach(Visual::close); visuals.clear();
            controls.values().forEach(parts -> parts.forEach(DisplayAudience::retire)); controls.clear();
            highlights.values().forEach(parts -> parts.forEach(DisplayAudience::retire)); highlights.clear(); highlightColors.clear();
            feedbackText = null;
            lastFeedback = null;
            selected = hover = null; choices = List.of(); input = null;
            accounts = null; deeds = null;
        }
        void stopMotion() {
            stopMotion(false);
        }
        void stopMotion(boolean keepCardTurns) {
            if (tileSlide != null) { tileSlide.cancel(); tileSlide = null; }
            motions.forEach(PieceMotion::cancel); motions.clear();
            if (!keepCardTurns) { turns.forEach(CardTurn::cancel); turns.clear(); }
            sowing = List.of(); sowStep = sowFrame = 0;
            hintTicks = 0; hintAt = null;
        }
        void finishTurnsFor(List<Entity> parts) {
            for (var iterator = turns.iterator(); iterator.hasNext();) {
                CardTurn turn = iterator.next();
                if (turn.uses(parts)) { turn.cancel(); iterator.remove(); }
            }
        }
    }

    private final class TileSlide {
        final View view;
        final List<PieceMotion> donors;

        TileSlide(View view, List<PieceMotion> donors) { this.view = view; this.donors = donors; }

        boolean tick() {
            donors.removeIf(PieceMotion::tick);
            if (!donors.isEmpty()) return false;
            reveal();
            return true;
        }

        void cancel() { donors.forEach(PieceMotion::cancel); reveal(); }

        private void reveal() {
            for (Visual visual : view.visuals.values())
                for (Entity part : visual.parts) DisplayAudience.show(plugin, view.player, part);
        }
    }

    public ClassicTable(TDGamesPlugin plugin, GameSession room, Location origin, NamespacedKey tag) {
        this.plugin = plugin;
        this.room = room;
        this.origin = origin.clone();
        this.tag = tag;
        audience = DisplayAudience.withNativeFallback(plugin, origin);
        if (room.kind.equals("mastermind")) addClickTarget(0, 2.25f);
        if (room.kind.equals("battleship")) {
            addClickTarget(-ClassicLayout.FLEET_CENTER_X, 1.9f);
            addClickTarget(ClassicLayout.FLEET_CENTER_X, 1.9f);
        }
        diceTray = Set.of("backgammon", "monopoly").contains(room.kind)
                ? new DiceTray(plugin, room, origin.clone().subtract(0, TableGeometry.SURFACE, 0), tag, audience, 2,
                        TablePlacement.sideTrayX(ClassicLayout.tableHalfWidth(room.kind))) : null;
        sync();
    }

    private ClassicGame game() { return (ClassicGame) room.board(); }

    private void addClickTarget(double x, float width) {
        var target = origin.getWorld().spawn(origin.clone().add(x, .005, 0), Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setInteractionWidth(width);
            entity.setInteractionHeight(.03f);
            entity.setResponsive(true);
            entity.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|@board");
        });
        audience.common(target);
        clickTargets.add(target);
    }

    public void sync() {
        if (closed) return;
        audience.refresh();
        Set<UUID> present = new HashSet<>();
        Set<Player> packedViewers = audience.viewers(true);
        syncFurniture(!audience.viewers(false).isEmpty(), !packedViewers.isEmpty());
        syncDice();
        for (Player player : audience.all()) {
            UUID id = player.getUniqueId(); present.add(id);
            int seat = room.seat(id);
            boolean usePack = packedViewers.contains(player);
            View view = views.get(id);
            boolean fresh = view == null;
            if (fresh) { view = new View(player, seat, usePack); views.put(id, view); }
            boolean modeChanged = view.packed != usePack;
            if (modeChanged || view.seat != seat) {
                view.clearVisuals();
                view.revision = -1;
                view.seat = seat;
                view.packed = usePack;
            }
            if (fresh || modeChanged) {
                for (Entity entity : usePack ? nativeFurniture : packedFurniture) DisplayAudience.hide(plugin, player, entity);
                for (Entity entity : usePack ? packedFurniture : nativeFurniture) DisplayAudience.show(plugin, player, entity);
            }
            syncView(view);
        }
        for (UUID id : List.copyOf(views.keySet())) if (!present.contains(id)) {
            View old = views.remove(id); old.clearVisuals();
            for (Entity entity : nativeFurniture) DisplayAudience.hide(plugin, old.player, entity);
            for (Entity entity : packedFurniture) DisplayAudience.hide(plugin, old.player, entity);
        }
    }

    public void tick() {
        sync();
        if (diceTray != null) diceTray.tick();
        for (View view : views.values()) {
            if (view.tileSlide != null && view.tileSlide.tick()) view.tileSlide = null;
            view.motions.removeIf(PieceMotion::tick);
            view.turns.removeIf(CardTurn::tick);
            if (view.hintTicks > 0 && --view.hintTicks == 0) {
                TableSounds.playFor(plugin, view.player, view.hintAt, TableSounds.FEEDBACK, room.kind);
                view.hintAt = null;
            }
            if (!view.sowing.isEmpty() && ++view.sowFrame >= 3) {
                var step = view.sowing.get(view.sowStep);
                syncCells(view, step.after(), Map.of());
                TableSounds.playFor(plugin, view.player, at(view.layout.slot(step.toId())),
                        new TableSounds.Cue(TableSounds.GLASS.sound(), .12f, 1.15f,
                                view.sowStep == view.sowing.size() - 1 ? "board.sow.last" : TableSounds.GLASS.resource(), 0), room.kind);
                view.sowStep++; view.sowFrame = 0;
                if (view.sowStep < view.sowing.size()) startSeedFlight(view);
                else { view.sowing = List.of(); syncHighlights(view); }
            }
            if (view.hover != null && ++view.hoverPulses % 10 == 0) describeHover(view);
        }
    }

    private void syncDice() {
        if (diceTray == null) return;
        ClassicGame board = game();
        if (diceBoard != board || diceHistory != room.history.size()) {
            int[] dice;
            if (board instanceof BackgammonGame backgammon) dice = backgammon.rolledDice();
            else dice = Arrays.stream(board.publicInfo().get("dice").split(",")).mapToInt(Integer::parseInt).toArray();
            if (diceBoard != board) for (int i = 0; i < dice.length; i++) dice[i] = Math.max(1, dice[i]);
            if (diceBoard == board && room.history.size() > diceHistory && lastAction().equals("roll"))
                diceTray.roll(dice, room.seed() ^ room.history.size());
            else if (diceBoard != board) diceTray.settle(dice);
            diceBoard = board; diceHistory = room.history.size();
        }
        diceTray.label((rolling() ? Language.component("hint.rolling") : ClassicText.action(room.kind, "roll")).color(NamedTextColor.WHITE));
    }

    public boolean rolling() { return diceTray != null && diceTray.rolling(); }

    public void renderNativeDiceMidpoint() {
        if (diceTray != null) diceTray.renderNativeMidpoint();
    }

    public boolean moving() {
        return views.values().stream().anyMatch(view -> view.tileSlide != null || !view.motions.isEmpty()
                || !view.turns.isEmpty() || !view.sowing.isEmpty());
    }

    private void syncFurniture(boolean natives, boolean packed) {
        if (!natives && !nativeFurniture.isEmpty()) { nativeFurniture.forEach(DisplayAudience::retire); nativeFurniture.clear(); }
        if (!packed && !packedFurniture.isEmpty()) { packedFurniture.forEach(DisplayAudience::retire); packedFurniture.clear(); }
        if (natives && nativeFurniture.isEmpty()) {
            String model = "tabletop/" + room.kind.replace('-', '_') + "/table";
            for (var part : ModelRepository.require(model).blocks())
                nativeFurniture.add(block(origin, part.material(), ModelGeometry.pose(part), null));
            showFurniture(nativeFurniture, false);
        }
        if (packed && packedFurniture.isEmpty()) {
            packedFurniture.add(packed(origin, "classic_" + room.kind.replace('-', '_') + "_board",
                    new Vector3f(1), new Quaternionf(), null));
            showFurniture(packedFurniture, true);
        }
    }

    private void showFurniture(List<Entity> parts, boolean packed) {
        for (View view : views.values()) if (view.packed == packed) for (Entity entity : parts) DisplayAudience.show(plugin, view.player, entity);
    }

    private List<Cell> displayCells(ClassicGame game, int seat) {
        List<Cell> cells = new ArrayList<>(game.cellsFor(seat));
        if (room.kind.equals("mastermind")) {
            int round = Integer.parseInt(game.publicInfo().getOrDefault("round", "1")) - 1;
            cells.removeIf(cell -> cell.id().startsWith("g") && !cell.id().startsWith("g" + round + "_")
                    || cell.id().startsWith("feedback") && !cell.id().startsWith("feedback" + round + "_"));
        }
        if (room.kind.equals("shogi") || room.kind.equals("mini-shogi")) {
            for (int owner = 0; owner < 2; owner++) {
                String hand = game.publicInfo().getOrDefault("hand" + owner, "{}").replace("{", "").replace("}", "");
                for (String part : hand.split(",")) {
                    String[] pair = part.trim().split("=");
                    if (pair.length != 2 || !pair[0].matches("[PLNSGBRK]")) continue;
                    cells.add(new Cell("h" + owner + pair[0], 0, 0, pair[0] + "_" + pair[1], owner));
                }
            }
        }
        return List.copyOf(cells);
    }

    private void syncView(View view) {
        ClassicGame board = game();
        if (view.board == board && view.revision == room.revision() && view.language == Language.generation() && view.phase == room.phase()) return;
        boolean animate = view.board == board && view.revision >= 0 && view.historySize + 1 == room.history.size()
                && view.language == Language.generation() && view.phase == GameSession.Phase.PLAYING
                && (room.phase() == GameSession.Phase.PLAYING || room.phase() == GameSession.Phase.FINISHED);
        List<Cell> before = view.snapshot;
        Map<Integer, String> oldPawnSquares = view.pawnSquares;
        ClassicLayout previousLayout = view.layout;
        boolean finishing = view.board == board && view.historySize == room.history.size()
                && view.language == Language.generation() && view.phase == GameSession.Phase.PLAYING
                && room.phase() == GameSession.Phase.FINISHED;
        if (!finishing) view.stopMotion(animate && room.kind.equals("memory"));
        Map<Integer, Location> oldPawnPositions = new HashMap<>();
        if (room.kind.equals("monopoly")) for (Visual visual : view.visuals.values())
            visual.pawns.forEach((seat, parts) -> oldPawnPositions.put(seat, parts.getFirst().getLocation().clone()));
        if (view.board != board || view.revision != room.revision() || room.phase() != GameSession.Phase.PLAYING) {
            view.selected = view.hover = null; view.choices = List.of(); view.input = null;
            view.highlights.values().forEach(parts -> parts.forEach(DisplayAudience::retire));
            view.highlights.clear(); view.highlightColors.clear();
        }
        boolean languageChanged = view.language != Language.generation();
        if (languageChanged) view.visuals.values().removeIf(visual -> {
            if (!visual.localized) return false;
            visual.close();
            return true;
        });
        view.board = board; view.revision = room.revision(); view.language = Language.generation(); view.phase = room.phase();
        List<Cell> cells = displayCells(board, view.seat);
        view.snapshot = cells;
        view.historySize = room.history.size();
        view.actions = view.seat >= 0 && room.phase() == GameSession.Phase.PLAYING ? board.legalActions(view.seat) : List.of();
        List<String> controls = controls(view);
        boolean changedControls = !controls.equals(view.controlActions);
        view.controlActions = controls;
        view.page = Math.min(view.page, Math.max(0, (controls.size() - 1) / ClassicLayout.CONTROLS_PER_PAGE));
        view.layout = ClassicLayout.of(room.kind, cells, controls, view.page).controlsForSeat(view.seat, room.capacity);
        Map<String, MonopolyState> monopoly = new HashMap<>();
        Map<Integer, String> pawnSquares = new HashMap<>();
        if (board instanceof MonopolyGame estate) {
            List<MonopolyGame.PlayerView> players = estate.players();
            players.stream().filter(player -> !player.eliminated()).forEach(player -> pawnSquares.put(player.seat(), "p" + player.position()));
            for (var property : estate.properties()) monopoly.put("p" + property.index(), new MonopolyState(property,
                    players.stream().filter(player -> !player.eliminated() && player.position() == property.index()).toList()));
        }
        view.pawnSquares = Map.copyOf(pawnSquares);
        view.animateCards = animate && room.kind.equals("memory");
        if (animate && room.kind.equals("mancala")) {
            view.sowing = MancalaSowing.plan(before, cells, lastAction());
            if (view.sowing.isEmpty()) syncCells(view, cells, monopoly);
            else {
                syncCells(view, before, monopoly);
                TableSounds.detailFor(plugin, view.player, at(view.layout.slot(view.sowing.getFirst().fromId())), "board.sow.pickup", .12f);
                startSeedFlight(view);
            }
        } else if (animate && room.kind.equals("2048") && lastAction().startsWith("slide:")) {
            startTileSlide(view, before, cells, lastAction().substring(6));
        } else if (!finishing || view.sowing.isEmpty() && view.tileSlide == null) {
            syncCells(view, cells, monopoly);
            if (animate) animateMove(view, before, previousLayout, oldPawnSquares, oldPawnPositions, lastAction());
        }
        view.animateCards = false;
        if (board instanceof MonopolyGame estate) {
            if (animate && !room.restoring() && view.seat >= 0 && view.accounts != null) {
                Component notice = MonopolyText.changes(estate, view.accounts, view.deeds, view.seat);
                if (!notice.equals(Component.empty())) PlatformMessages.send(view.player, notice);
            }
            view.accounts = estate.players();
            view.deeds = estate.properties();
        }
        if (changedControls || languageChanged || view.controls.isEmpty()) syncControls(view);
        updateStatus(view);
        syncHighlights(view);
    }

    private void syncCells(View view, List<Cell> cells, Map<String, MonopolyState> monopoly) {
        if (view.animateCards) revealMatchedPair(view, cells);
        Set<String> ids = new HashSet<>(); cells.forEach(cell -> ids.add(cell.id()));
        for (String id : List.copyOf(view.visuals.keySet())) if (!ids.contains(id) || view.layout.slot(id) == null) view.visuals.remove(id).close();
        for (Cell cell : cells) {
            var slot = view.layout.slot(cell.id());
            if (slot == null) continue;
            Visual existing = view.visuals.get(cell.id());
            MonopolyState detail = monopoly.get(cell.id());
            if (existing != null && existing.cell.equals(cell) && existing.slot.equals(slot) && existing.expanded == cell.id().equals(view.selected)
                    && Objects.equals(existing.monopoly, detail)) continue;
            if (view.animateCards && existing != null) view.finishTurnsFor(existing.parts);
            if (existing != null && room.kind.equals("mancala") && existing.slot.equals(slot)) {
                syncSeeds(view, existing, cell);
                continue;
            }
            boolean turnCard = view.animateCards && existing != null && !existing.cell.piece().isEmpty()
                    && !cell.piece().isEmpty() && !existing.cell.piece().equals(cell.piece());
            if (room.kind.equals("mastermind") && cell.id().startsWith("draft") && existing != null
                    && existing.cell.piece().startsWith("color_") && cell.piece().startsWith("color_")
                    && !existing.cell.piece().equals(cell.piece()))
                TableSounds.detailFor(plugin, view.player, at(slot), "peg.remove", .12f);
            boolean uncover = room.kind.equals("mastermind") && cell.id().equals("secret0")
                    && existing != null && existing.cell.piece().equals("back") && cell.piece().startsWith("color_");
            if (uncover && !existing.parts.isEmpty()) {
                view.motions.add(new PieceMotion(existing.parts, List.of(new Vector(), new Vector(.17, .10, 0)), 0, 8, true));
                TableSounds.playFor(plugin, view.player, at(slot), TableSounds.REVEAL, room.kind);
            } else if (existing != null && !turnCard) existing.close();
            view.revealingCard = turnCard;
            Visual next = new Visual(view, cell, slot, detail);
            view.revealingCard = false;
            view.visuals.put(cell.id(), next);
            if (turnCard) view.turns.add(new CardTurn(plugin, view.player, existing.parts, next.parts, slot.depth() / 2));
        }
    }

    private void revealMatchedPair(View view, List<Cell> cells) {
        String action = lastAction();
        if (!action.startsWith("flip:")) return;
        String id = action.substring(5);
        Cell matched = cells.stream().filter(cell -> cell.id().equals(id) && cell.piece().isEmpty() && cell.owner() >= 0)
                .findFirst().orElse(null);
        Visual back = view.visuals.get(id);
        if (matched == null || back == null || !back.cell.piece().equals("back")) return;
        Visual first = view.visuals.values().stream().filter(visual -> !visual.cell.piece().isEmpty()
                && !visual.cell.piece().equals("back") && cells.stream().anyMatch(cell -> cell.id().equals(visual.cell.id())
                && cell.piece().isEmpty() && cell.owner() == matched.owner())).findFirst().orElse(null);
        if (first == null) return;
        view.finishTurnsFor(first.parts);
        view.finishTurnsFor(back.parts);
        view.revealingCard = true;
        Visual face = new Visual(view, new Cell(matched.id(), matched.x(), matched.y(), first.cell.piece(), matched.owner()), back.slot, null);
        view.revealingCard = false;
        view.visuals.remove(id);
        view.visuals.remove(first.cell.id());
        view.turns.add(new CardTurn(plugin, view.player, back.parts, face.parts, first.parts, back.slot.depth() / 2));
    }

    private String lastAction() {
        if (room.history.isEmpty()) return "";
        var action = room.history.get(room.history.size() - 1).getAsJsonObject().get("action");
        return action != null && action.isJsonPrimitive() && action.getAsJsonPrimitive().isString() ? action.getAsString() : "";
    }

    private void animateMove(View view, List<Cell> before, ClassicLayout previous, Map<Integer, String> oldPawnSquares, Map<Integer, Location> oldPawnPositions, String action) {
        Map<String, Cell> old = new HashMap<>(); before.forEach(cell -> old.put(cell.id(), cell));
        String[] move = action.split(":");
        if (action.startsWith("promote:") && Set.of("shogi", "mini-shogi").contains(room.kind))
            move = view.board.publicInfo().getOrDefault("lastMove", "").split(":");
        Set<String> animated = new HashSet<>();
        if (room.kind.equals("memory")) return;
        if (room.kind.equals("mastermind")) {
            for (Visual visual : view.visuals.values()) {
                Cell prior = old.get(visual.cell.id());
                if (visual.parts.isEmpty() || visual.cell.piece().equals("back")
                        || prior != null && prior.piece().equals(visual.cell.piece())) continue;
                if (visual.cell.id().startsWith("g")) {
                    String peg = "draft" + visual.cell.x();
                    if (old.containsKey(peg) && old.get(peg).piece().equals(visual.cell.piece())) continue;
                }
                view.motions.add(new PieceMotion(visual.parts, new Vector(0, .065, 0), 0,
                        visual.cell.id().startsWith("feedback") ? 6 : 4, false));
                if (visual.cell.id().startsWith("feedback")) { view.hintTicks = 8; view.hintAt = at(visual.slot); }
            }
            return;
        }
        if (room.kind.equals("monopoly")) {
            for (Visual visual : view.visuals.values()) for (var pawn : visual.pawns.entrySet()) {
                String from = oldPawnSquares.get(pawn.getKey());
                var source = from == null ? null : previous.slot(from);
                if (source == null || from.equals(visual.cell.id())) continue;
                int start = Integer.parseInt(from.substring(1)), end = Integer.parseInt(visual.cell.id().substring(1));
                int distance = Math.floorMod(end - start, 40);
                Location destination = pawn.getValue().getFirst().getLocation();
                Location startPosition = oldPawnPositions.get(pawn.getKey());
                if (startPosition == null) continue;
                List<Vector> route = new ArrayList<>();
                route.add(startPosition.toVector().subtract(destination.toVector()));
                int rolled = Arrays.stream(view.board.publicInfo().getOrDefault("dice", "0,0").split(","))
                        .mapToInt(Integer::parseInt).sum();
                if (action.equals("roll") && distance <= 12 && distance == rolled) {
                    int lane = 0;
                    while (visual.monopoly.players().get(lane).seat() != pawn.getKey()) lane++;
                    for (int index = 1; index < distance; index++) {
                        var square = view.layout.slot("p" + ((start + index) % 40));
                        route.add(onCell(square, (lane % 3 - 1) * .065, .0015, .145 + (lane / 3) * MONOPOLY_PAWN_ROW_STEP)
                                .toVector().subtract(destination.toVector()));
                    }
                }
                route.add(new Vector());
                view.motions.add(new PieceMotion(pawn.getValue(), route, .045, Math.max(6, (route.size() - 1) * 2), false));
            }
            return;
        }
        if (move.length >= 3 && (move[0].equals("move") || move[0].equals("drop"))) {
            String sourceId = move[0].equals("drop") ? "h" + room.history.get(room.history.size() - 1).getAsJsonObject().get("seat").getAsInt() + move[1] : move[1];
            var source = previous.slot(sourceId);
            var target = view.visuals.get(move[2]);
            if (source != null && target != null && !target.parts.isEmpty()) {
                List<Entity> parts = target.parts;
                Vector offset = new Vector(source.x() - target.slot.x(), source.height() - target.slot.height(),
                        source.renderZ() - target.slot.renderZ());
                if (room.kind.equals("backgammon")) {
                    int count = Integer.parseInt(target.cell.piece());
                    int targetIndex = Math.max(0, Math.min(count, 5) - 1);
                    int sourceIndex = Math.max(0, Math.min(Integer.parseInt(old.get(sourceId).piece()), 5) - 1);
                    int start = targetIndex * 3;
                    parts = parts.subList(start, Math.min(start + 3, parts.size()));
                    offset = backgammonTokenPosition(source, sourceIndex).toVector()
                            .subtract(backgammonTokenPosition(target.slot, targetIndex).toVector());
                    animated.add(sourceId);
                }
                view.motions.add(new PieceMotion(parts, offset, .12, 6, false));
                animated.add(target.cell.id());
            }
        }
        if (room.kind.equals("klondike")) {
            for (Visual visual : view.visuals.values()) {
                if (visual.cell.piece().isEmpty() || visual.cell.piece().equals("back") || visual.parts.isEmpty()) continue;
                if (visual.cell.equals(old.get(visual.cell.id()))) continue;
                Cell source = before.stream().filter(cell -> cell.piece().equals(visual.cell.piece()) && !cell.id().equals(visual.cell.id())).findFirst().orElse(null);
                var slot = source == null ? action.equals("draw") ? previous.slot("stock") : null : previous.slot(source.id());
                if (slot != null && !animated.contains(visual.cell.id())) {
                    view.motions.add(new PieceMotion(visual.parts, new Vector(slot.x() - visual.slot.x(), slot.height() - visual.slot.height(),
                            slot.renderZ() - visual.slot.renderZ()), .08, 6, false));
                    animated.add(visual.cell.id());
                }
            }
        }
        for (Visual visual : view.visuals.values()) {
            Cell prior = old.get(visual.cell.id());
            if (animated.contains(visual.cell.id()) || visual.parts.isEmpty() || prior == null
                    || prior.piece().equals(visual.cell.piece()) && prior.owner() == visual.cell.owner()
                    || visual.cell.piece().isEmpty() || visual.cell.piece().equals("0") || visual.cell.piece().equals("back")) continue;
            view.motions.add(new PieceMotion(visual.parts, new Vector(0, .10, 0), .025, 5, false));
        }
    }

    private void startTileSlide(View view, List<Cell> before, List<Cell> after, String direction) {
        boolean horizontal = direction.equals("left") || direction.equals("right");
        boolean reverse = direction.equals("right") || direction.equals("down");
        List<PieceMotion> donors = new ArrayList<>();
        for (int line = 0; line < 4; line++) {
            int axis = line;
            List<Cell> tiles = before.stream().filter(cell -> !cell.piece().equals("0") && (horizontal ? cell.y() : cell.x()) == axis)
                    .sorted(Comparator.comparingInt(cell -> (reverse ? -1 : 1) * (horizontal ? cell.x() : cell.y()))).toList();
            int destination = 0;
            for (int index = 0; index < tiles.size(); index++) {
                int position = reverse ? 3 - destination++ : destination++;
                String id = horizontal ? position + "," + line : line + "," + position;
                Cell source = tiles.get(index);
                boolean merged = index + 1 < tiles.size() && source.piece().equals(tiles.get(index + 1).piece());
                for (int member = 0; member < (merged ? 2 : 1); member++) {
                    Visual donor = view.visuals.remove(tiles.get(index + member).id());
                    Vector shift = at(view.layout.slot(id)).toVector().subtract(at(donor.slot).toVector());
                    donors.add(new PieceMotion(donor.parts, List.of(new Vector(), shift), 0, 6, true));
                }
                if (merged) index++;
            }
        }
        view.tileSlide = new TileSlide(view, donors);
        view.hidingTiles = true;
        try { syncCells(view, after, Map.of()); }
        finally { view.hidingTiles = false; }
    }

    private void startSeedFlight(View view) {
        var step = view.sowing.get(view.sowStep);
        int sourceCount = Integer.parseInt(step.before().stream().filter(c -> c.id().equals(step.fromId())).findFirst().orElseThrow().piece());
        int targetCount = Integer.parseInt(step.before().stream().filter(c -> c.id().equals(step.toId())).findFirst().orElseThrow().piece());
        List<Cell> airborne = step.before().stream().map(cell -> cell.id().equals(step.fromId())
                ? new Cell(cell.id(), cell.x(), cell.y(), Integer.toString(sourceCount - step.seeds()), cell.owner()) : cell).toList();
        syncCells(view, airborne, Map.of());
        var source = view.layout.slot(step.fromId());
        var target = view.layout.slot(step.toId());
        for (int index = 0; index < Math.min(3, step.seeds()); index++) {
            Location from = seedPoint(source, Math.clamp(sourceCount - index - 1, 0, 7));
            Location to = seedPoint(target, Math.min(targetCount + index, 7));
            Entity seed = block(from, Material.MUSHROOM_STEM, .048, .026, .048, view.player);
            view.motions.add(new PieceMotion(seed, to, .14, 2));
        }
    }

    private void updateStatus(View view) {
        Component state = ClassicText.status(view.board, view.seat);
        if (room.kind.equals("2048")) state = state.append(Component.text(" | ")).append(Language.component(
                plugin.tablePlayers() != null && plugin.tablePlayers().hasTableFocus(view.player)
                        ? "hint.2048.controls" : "hint.2048.enter"));
        if (room.kind.equals("mastermind")) state = state.append(Component.text(" | ")).append(Language.component("hint.mastermind.colors"));
        if (view.board instanceof BattleshipGame sea && view.seat >= 0 && room.phase() == GameSession.Phase.PLAYING) {
            String hint = sea.setup() ? sea.confirmed(view.seat) ? "hint.battleship.setup-wait" : "hint.battleship.controls"
                    : sea.currentPlayer() == view.seat ? "hint.battleship.fire" : "hint.battleship.wait";
            state = state.append(Component.text(" | ")).append(Language.component(hint));
        }
        if (view.input != null) {
            state = view.input.status();
            if (view.input.propertyField()) state = state.append(Component.text(" | ")).append(Language.component("hint.monopoly.property"));
        }
        view.feedbackText = state.replaceText(config -> config.matchLiteral("\n").replacement(" \u00b7 "));
        if (view.seat >= 0 && view.hover == null && !view.feedbackText.equals(view.lastFeedback)) feedback(view, view.feedbackText);
        if (view.board instanceof MonopolyGame monopoly) {
            var trade = monopoly.trade();
            if (trade != null && !trade.equals(view.shownTrade) && (view.seat == trade.proposer() || view.seat == trade.recipient()))
                PlatformMessages.send(view.player, ClassicText.tradeSummary(monopoly));
            view.shownTrade = trade;
        }
    }

    private void feedback(View view, Component message) {
        PlatformMessages.actionBar(view.player, message);
        view.lastFeedback = message;
    }

    private List<String> controls(View view) {
        if (view.seat < 0 || room.phase() != GameSession.Phase.PLAYING) return List.of();
        if (view.input != null) return view.input.actions().stream()
                .filter(action -> !action.startsWith("@edit:property:"))
                .toList();
        if (view.choices.isEmpty()) {
            List<String> actions = new ArrayList<>(view.board.controlActions(view.seat).stream()
                .filter(action -> diceTray == null || !action.equals("roll"))
                .filter(action -> !room.kind.equals("2048") || !action.startsWith("slide:"))
                .filter(action -> !room.kind.equals("battleship") || !action.startsWith("ship:")
                        && !action.equals("rotate") && !action.equals("clear"))
                .filter(action -> !room.kind.equals("monopoly") || !action.matches("(?:build|sell|mortgage|redeem|auction-target):p[0-9]+"))
                .toList());
            if (room.kind.equals("monopoly")) actions.add("@assets");
            return List.copyOf(actions);
        }
        List<String> controls = new ArrayList<>(view.choices);
        controls.add("@cancel");
        return List.copyOf(controls);
    }

    private void refreshControls(View view) {
        view.controlActions = controls(view);
        view.page = Math.clamp(view.page, 0, Math.max(0, (view.controlActions.size() - 1) / ClassicLayout.CONTROLS_PER_PAGE));
        view.layout = ClassicLayout.of(room.kind, view.snapshot, view.controlActions, view.page).controlsForSeat(view.seat, room.capacity);
        syncControls(view); updateStatus(view); syncHighlights(view);
    }

    private Component controlLabel(View view, String action) {
        if (view.input != null) return view.input.label(action);
        if (action.equals("@assets")) return Language.component("monopoly.assets");
        return action.equals("@cancel") ? Language.component("classic.action.cancel") : ClassicText.action(room.kind, action);
    }

    private void syncControls(View view) {
        view.controls.values().forEach(parts -> parts.forEach(DisplayAudience::retire)); view.controls.clear();
        for (var slot : view.layout.slots()) {
            if (!slot.id().startsWith("@")) continue;
            List<Entity> parts = new ArrayList<>();
            if (slot.id().startsWith("@palette/")) {
                peg(view, parts, at(slot), Integer.parseInt(slot.id().substring(9)));
                view.controls.put(slot.id(), parts);
                continue;
            }
            if (slot.id().startsWith("@ship/")) {
                int ship = Integer.parseInt(slot.id().substring(6));
                int length = BattleshipGame.shipLength(ship);
                for (int segment = 0; segment < length; segment++) {
                    double x = (segment - (length - 1) / 2.0) * .05;
                    double angle = Math.toRadians(-slot.yaw());
                    Location center = at(slot).add(x * Math.cos(angle), 0, x * Math.sin(angle));
                    parts.add(block(center, Material.GRAY_CONCRETE, .047, .018, .075, view.player));
                    parts.add(block(center.clone().add(0, .018, 0), Material.LIGHT_GRAY_CONCRETE, .025, .012, .045, view.player));
                }
                view.controls.put(slot.id(), parts);
                continue;
            }
            for (var layer : TableButtonStyle.layers(slot.width(), .012, slot.depth(), true))
                parts.add(block(at(slot), layer.material(), ModelGeometry.pose(layer), view.player));
            Component label = slot.id().startsWith("@control/")
                    ? controlLabel(view, view.controlActions.get(Integer.parseInt(slot.id().substring(9))))
                    : Component.text(slot.id().endsWith("next") ? "\u2192" : "\u2190");
            var fit = LabelLayout.fit(label, (float)slot.width() * .82f, (float)slot.depth() * .70f);
            parts.add(text(at(slot).add(0, .016, 0), fit.text(), fit.scale(), NamedTextColor.WHITE, view.player, true));
            view.controls.put(slot.id(), parts);
        }
    }

    private Location at(ClassicLayout.Slot slot) {
        Location at = origin.clone().add(slot.x(), slot.height(), slot.renderZ());
        at.setYaw((float)slot.yaw());
        return at;
    }

    private Material ownerColor(int owner) { return owner < 0 ? Material.SMOOTH_SANDSTONE : COLORS[Math.floorMod(owner, COLORS.length)]; }

    private void peg(View view, List<Entity> parts, Location at, int color) {
        if (view.packed) {
            parts.add(packed(at, "classic_mastermind_peg_" + color, new Vector3f(1), new Quaternionf(), view.player));
            return;
        }
        Material material = PEG_COLORS[color];
        parts.add(block(at.clone().add(0, -.0005, 0), material, .032, .010, .032, view.player));
        double[] widths = {.06, .09, .104, .09, .06}, tops = {.026, .031, .034, .031, .026};
        for (int slice = 0; slice < 5; slice++)
            parts.add(block(at.clone().add(0, .0095, (slice - 2) * .02), material,
                    widths[slice], tops[slice] - .0095, .02, view.player));
    }

    private void drawCell(View view, Visual visual) {
        Cell cell = visual.cell;
        var slot = visual.slot;
        Location at = at(slot);
        String piece = cell.piece();
        if (visual.monopoly != null) { drawMonopoly(view, visual); return; }
        if (room.kind.equals("klondike")) { drawCard(view, visual); return; }
        if (room.kind.equals("memory")) {
            if (piece.isEmpty()) return;
            Player viewer = view.revealingCard ? null : view.player;
            visual.parts.add(block(at, Material.SMOOTH_SANDSTONE, slot.width(), .012, slot.depth(), viewer));
            if (piece.equals("back")) visual.parts.add(block(at.clone().add(0, .012, 0), Material.BLUE_TERRACOTTA,
                    slot.width() * .86, .002, slot.depth() * .88, viewer));
            if (!piece.equals("back")) {
                Material material = Material.matchMaterial(piece.toUpperCase(Locale.ROOT));
                if (material != null) visual.parts.add(item(at.clone().add(0, .02, 0), new ItemStack(material),
                        new Vector3f((float)slot.width() * .80f), new Quaternionf().rotateX((float)-Math.PI/2), viewer));
            }
            return;
        }
        if (room.kind.equals("mastermind")) {
            if (cell.id().startsWith("feedback")) {
                String[] counts = piece.split("_");
                if (counts.length == 2) {
                    int exact = Integer.parseInt(counts[0]), misplaced = Integer.parseInt(counts[1]);
                    for (int i = 0; i < exact + misplaced; i++) disc(visual.parts,
                            at.clone().add((i % 2 - .5) * .055, -.0005, (i / 2 - .5) * .055),
                            i < exact ? Material.ORANGE_CONCRETE : Material.WHITE_CONCRETE, .035, .018, view.player);
                }
            } else if (piece.startsWith("color_")) {
                int color = Integer.parseInt(piece.substring(6));
                peg(view, visual.parts, at, color);
            } else if (piece.equals("back") && cell.id().equals("secret0")) {
                visual.parts.add(block(at.clone().add(0, .006, .30), Material.GRAY_CONCRETE, .17, .048, .78, view.player));
                visual.parts.add(block(at.clone().add(0, .054, .30), Material.LIGHT_GRAY_CONCRETE, .055, .004, .17, view.player));
            }
            return;
        }
        if (room.kind.equals("battleship")) {
            if (piece.equals("water")) return;
            if (piece.startsWith("ship_")) {
                visual.parts.add(block(at, Material.GRAY_CONCRETE, slot.width() * .90, .024, slot.depth() * .90, view.player));
                visual.parts.add(block(at.clone().add(0, .024, 0), Material.LIGHT_GRAY_CONCRETE,
                        slot.width() * .48, .016, slot.depth() * .48, view.player));
            } else {
                Material marker = piece.equals("miss") ? Material.WHITE_CONCRETE : piece.equals("sunk") ? Material.BLACK_CONCRETE : Material.RED_CONCRETE;
                disc(visual.parts, at, marker, slot.width() * .38, .025, view.player);
            }
            return;
        }
        if (room.kind.equals("dots-and-boxes")) {
            if (cell.id().startsWith("b") && cell.owner() < 0) return;
            if (cell.id().startsWith("p")) disc(visual.parts, at, Material.POLISHED_DEEPSLATE, .032, .002, view.player);
            else {
                double lineLength = (cell.id().startsWith("h") ? slot.width() : slot.depth()) * 2 / 1.55 - .032;
                visual.parts.add(block(at, cell.owner() < 0 ? Material.LIGHT_GRAY_CONCRETE : ownerColor(cell.owner()),
                        cell.id().startsWith("v") ? .016 : cell.id().startsWith("h") ? lineLength : slot.width(), .002,
                        cell.id().startsWith("h") ? .016 : cell.id().startsWith("v") ? lineLength : slot.depth(), view.player));
            }
            return;
        }
        if (room.kind.equals("nine-mens-morris") || room.kind.equals("hex")) {
            if (piece.isEmpty()) return;
            Material color = room.kind.equals("hex") ? piece.equals("NS") ? COLORS[0] : COLORS[1]
                    : cell.owner() == 0 ? Material.WHITE_CONCRETE : Material.BROWN_CONCRETE;
            if (room.kind.equals("hex")) {
                if (view.packed) visual.parts.add(packed(at, "classic_hex_piece_" + (piece.equals("NS") ? "red" : "blue"),
                        new Vector3f((float)(slot.width() * .84), .045f, (float)(slot.depth() * .84)), new Quaternionf(), view.player));
                else hexStone(visual.parts, at, color, slot.width() * .84, .045, slot.depth() * .84, view.player);
            } else disc(visual.parts, at, color, slot.width() * .84, .045, view.player);
            return;
        }
        if (room.kind.equals("mancala")) { syncSeeds(view, visual, cell); return; }
        if (room.kind.equals("backgammon")) {
            int count;
            try { count = piece.isEmpty() ? 0 : Integer.parseInt(piece); } catch (NumberFormatException exception) { count = 0; }
            for (int i = 0; i < Math.min(count, 5); i++) {
                Location token = backgammonTokenPosition(slot, i);
                disc(visual.parts, token, cell.owner() == 0 ? Material.WHITE_CONCRETE : Material.RED_TERRACOTTA,
                        Math.min(slot.width() * .72, .11), .018, view.player);
            }
            if (count > 0) visual.parts.add(text(origin.clone().add(slot.x(), .015,
                    slot.z() + (slot.z() < 0 ? .37 : -.37)), Component.text(count), .28f,
                    NamedTextColor.BLACK, view.player, true));
            return;
        }
        if (room.kind.equals("shogi") || room.kind.equals("mini-shogi")) {
            if (piece.isEmpty()) return;
            double facing = cell.owner() == 1 ? -1 : 1;
            double depth = slot.depth() * .85;
            if (view.packed) {
                visual.parts.add(packed(at, ShogiPieceModels.piece(cell),
                        new Vector3f((float)slot.width() * .9f, .175f, (float)slot.depth() * .95f),
                        new Quaternionf().rotateY(cell.owner() == 0 ? (float)Math.PI : 0), view.player));
                if (piece.contains("_")) {
                    Location count = at.clone().add(slot.width() * .32, .039, facing * slot.depth() * .30);
                    count.setYaw(cell.owner() == 1 ? 180 : 0);
                    var fit = LabelLayout.fit(Component.text(piece.substring(piece.indexOf('_') + 1)), (float)slot.width() * .23f, (float)slot.depth() * .20f);
                    visual.parts.add(text(count, fit.text(), fit.scale(), NamedTextColor.WHITE, view.player, true));
                }
                return;
            }
            for (int row = 0; row < 4; row++) visual.parts.add(block(at.clone().add(0, 0, facing * (row - 1.5) * depth / 4),
                    Material.BIRCH_PLANKS, slot.width() * .80 * (row == 0 ? .45 : row == 1 ? .78 : 1), .035, depth / 4, view.player));
            String code = piece.split("_")[0];
            String glyph = code.equals("K") && cell.owner() == 1 ? "\u7389" : SHOGI.getOrDefault(code, code);
            if (piece.contains("_")) glyph += " " + piece.substring(piece.indexOf('_') + 1);
            Location face = at.clone().add(0, .0365, 0);
            face.setYaw(cell.owner() == 1 ? 180 : 0);
            var fit = LabelLayout.fit(Component.text(glyph), (float)slot.width() * .55f, (float)depth * .52f);
            visual.parts.add(text(face, fit.text(), fit.scale(),
                    piece.startsWith("+") ? NamedTextColor.DARK_RED : NamedTextColor.BLACK, view.player, true));
            return;
        }
        Material background = piece.equals("0") || piece.isEmpty() ? Material.BIRCH_PLANKS : ownerColor(cell.owner());
        if (room.kind.equals("2048") && !piece.equals("0")) {
            int exponent = Integer.numberOfTrailingZeros(Integer.parseInt(piece));
            Material[] tiles = {Material.SMOOTH_SANDSTONE, Material.WHITE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
                    Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.RED_TERRACOTTA, Material.BROWN_TERRACOTTA};
            background = tiles[Math.min(exponent - 1, tiles.length - 1)];
        }
        if (room.kind.equals("2048") && piece.equals("0")) return;
        Player viewer = view.hidingTiles ? null : view.player;
        if (!view.packed || !piece.isEmpty()) visual.parts.add(block(at, background, slot.width(), .025, slot.depth(), viewer));
        if (!piece.isEmpty() && !piece.equals("0")) {
            boolean number = piece.matches("[0-9]+");
            String key = "classic.cell." + room.kind + "." + piece;
            visual.localized = !number && Language.contains(key);
            if (number || visual.localized) {
                Component label = number ? Component.text(piece) : Language.component(key);
                boolean tile = room.kind.equals("2048") && number;
                float scale = (float) slot.width() * (tile ? Math.min(1.35f, 4f / piece.length()) : .55f);
                NamedTextColor color = tile && Integer.parseInt(piece) >= 64 ? NamedTextColor.WHITE : NamedTextColor.BLACK;
                visual.parts.add(text(at.clone().add(0, .032, 0), label, scale, color, viewer, true));
            }
        }
    }

    private Location seedPoint(ClassicLayout.Slot slot, int index) {
        return at(slot).add((index % 2 - .5) * .070, -.012, (index / 2 - 1.5) * .065);
    }

    private void syncSeeds(View view, Visual visual, Cell cell) {
        int count = Integer.parseInt(cell.piece());
        int shown = Math.min(count, 8);
        var slot = visual.slot;
        while (visual.seeds.size() > shown) {
            Entity removed = visual.seeds.removeLast(); visual.parts.remove(removed); DisplayAudience.retire(removed);
        }
        while (visual.seeds.size() < shown) {
            int index = visual.seeds.size();
            Entity seed = block(seedPoint(slot, index),
                    Material.MUSHROOM_STEM, .048, .026, .048, view.player);
            visual.seeds.add(seed); visual.parts.add(seed);
        }
        if (visual.count == null) {
            double angle = view.seat >= 0 ? 2 * Math.PI * view.seat / room.capacity : 0;
            Location counter = origin.clone().add(slot.x() + Math.sin(angle) * (slot.width()/2 + .025),
                    .0105, slot.z() + Math.cos(angle) * (slot.depth()/2 + .025));
            counter.setYaw((float)-Math.toDegrees(angle));
            visual.count = text(counter, Component.text(count), .28f, NamedTextColor.BLACK, view.player, true);
            visual.parts.add(visual.count);
        } else if (!visual.cell.piece().equals(cell.piece())) PlatformMessages.text(visual.count, Component.text(count, NamedTextColor.BLACK));
        visual.cell = cell;
    }

    private void disc(List<Entity> parts, Location at, Material material, double width, double height, Player player) {
        parts.add(block(at, material, width * .6, height, width, player));
        for (double side : new double[] {-1, 1})
            parts.add(block(at.clone().add(side * width * .4, 0, 0), material, width * .2, height, width * .6, player));
    }

    private void hexStone(List<Entity> parts, Location at, Material material, double width, double height, double depth, Player player) {
        double[][] vertices = {{width / 2, 0}, {-width / 4, depth / 2}, {-width / 4, -depth / 2}};
        for (int index = 0; index < vertices.length; index++) {
            double[] a = vertices[index], b = vertices[(index + 1) % vertices.length];
            Matrix4f pose = new Matrix4f().m00((float)a[0]).m02((float)a[1]).m11((float)height)
                    .m20((float)b[0]).m22((float)b[1]);
            var entity = DisplayPlacement.spawn(at, BlockDisplay.class, display -> {
                configure(display); display.setBlock(material.createBlockData()); display.setTransformationMatrix(pose);
            });
            if (player != null) DisplayAudience.show(plugin, player, entity);
            parts.add(entity);
        }
    }

    private Location onCell(ClassicLayout.Slot slot, double x, double y, double z) {
        double angle = Math.toRadians(slot.yaw()), c = Math.cos(angle), sn = Math.sin(angle);
        return at(slot).add(x * c - z * sn, y, x * sn + z * c);
    }

    private Location backgammonTokenPosition(ClassicLayout.Slot slot, int index) {
        boolean point = slot.id().startsWith("p");
        return at(slot).add(0, point ? 0 : index * .019,
                point ? (slot.z() < 0 ? -1 : 1) * (.25 - index * .12) : 0);
    }

    private void drawMonopoly(View view, Visual visual) {
        var slot = visual.slot;
        var property = visual.monopoly.property();
        var space = MonopolyData.spaces().get(property.index());
        if (property.owner() >= 0) visual.parts.add(block(onCell(slot, 0, .0015, .217), ownerColor(property.owner()),
                slot.width() * .90, .002, .025, view.player));
        String key = "classic.cell.monopoly." + visual.cell.piece();
        if (Language.contains(key)) {
            visual.localized = true;
            String name = GoldenEggOVO.TDGames.Core.text.MessageText.plain(Language.component(key));
            if (name.length() > 3) {
                int split = name.indexOf(' ');
                if (split < 0) split = (name.length() + 1) / 2;
                name = name.substring(0, split) + "\n" + name.substring(split).stripLeading();
            }
            var fit = LabelLayout.fit(Component.text(name), (float)slot.width() * .88f, .15f);
            visual.parts.add(text(onCell(slot, 0, .003, -.035), fit.text(), Math.min(.36f, fit.scale()),
                    NamedTextColor.BLACK, view.player, true));
        }
        if (space.price() > 0) visual.parts.add(text(onCell(slot, 0, .003, .085),
                Component.text(space.price()), .20f, NamedTextColor.DARK_GRAY, view.player, true));
        if (property.mortgaged()) visual.parts.add(block(onCell(slot, 0, .004, .115), Material.BLACK_CONCRETE,
                slot.width() * .80, .002, .010, view.player));
        if (property.buildings() == 5) {
            visual.parts.add(block(onCell(slot, 0, .003, -.18), Material.RED_CONCRETE, .135, .105, .080, view.player));
            visual.parts.add(block(onCell(slot, 0, .108, -.18), Material.RED_TERRACOTTA, .15, .024, .095, view.player));
        } else for (int i = 0; i < property.buildings(); i++) {
            double x = (i - (property.buildings() - 1) / 2.0) * .066;
            visual.parts.add(block(onCell(slot, x, .003, -.18), Material.GREEN_CONCRETE, .055, .070, .065, view.player));
            visual.parts.add(block(onCell(slot, x, .073, -.18), Material.GREEN_TERRACOTTA, .064, .018, .076, view.player));
        }
        int number = 0;
        for (var player : visual.monopoly.players()) {
            int start = visual.parts.size();
            Location pawn = onCell(slot, (number % 3 - 1) * .065, .0015, .145 + (number / 3) * MONOPOLY_PAWN_ROW_STEP);
            Material color = ownerColor(player.seat());
            visual.parts.add(block(pawn, color, .048, .05, .038, view.player));
            visual.parts.add(block(pawn.clone().add(0, .05, 0), color, .030, .028, .030, view.player));
            if (player.jailed()) visual.parts.add(block(pawn.clone().add(0, .083, 0), Material.IRON_BLOCK, .055, .010, .055, view.player));
            visual.pawns.put(player.seat(), List.copyOf(visual.parts.subList(start, visual.parts.size())));
            number++;
        }
    }

    private void drawCard(View view, Visual visual) {
        var slot = visual.expanded ? visual.slot.expanded() : visual.slot;
        String face = visual.cell.piece();
        if (face.isEmpty()) {
            visual.parts.add(block(at(slot), Material.DEEPSLATE_TILES, slot.width(), .008, slot.fullDepth(), view.player));
            return;
        }
        if (visual.cell.id().equals("w") && game() instanceof KlondikeGame cards && cards.wasteCards().size() > 1) {
            var waste = cards.wasteCards();
            for (int i = 0; i < waste.size(); i++) drawSingleCard(view, visual, waste.get(i).face(),
                    at(slot).add((waste.size() - 1 - i) * .045, i * .003025, 0), false, i < waste.size() - 1);
        } else drawSingleCard(view, visual, face, at(slot), slot.covered() && !visual.expanded, false);
    }

    private void drawSingleCard(View view, Visual visual, String face, Location at, boolean stripTop, boolean stripRight) {
        var slot = visual.slot;
        if (view.packed) {
            visual.parts.add(packed(at.clone().add(0, .006, 0), "playing_" + face,
                    new Vector3f((float)slot.width(), (float)slot.fullDepth(), .008f), new Quaternionf().rotateX((float)-Math.PI/2), view.player));
        } else {
            visual.parts.add(block(at, Material.WHITE_CONCRETE, slot.width(), .012, slot.fullDepth(), view.player));
            if (face.equals("back")) {
                visual.parts.add(block(at.clone().add(0, .012, 0), Material.BLUE_CONCRETE, slot.width() * .88, .002, slot.fullDepth() * .92, view.player));
            } else {
                List<PlayingCardHeads.Texture> tiles = PlayingCardHeads.tiles(face);
                int count = stripRight ? 6 : stripTop ? 4 : tiles.size();
                for (int tile = 0; tile < count; tile++) {
                    int index = stripRight ? tile * 4 + 3 : tile;
                    var head = DisplayPlacement.spawn(at.clone().add(0, .004, 0), ItemDisplay.class, display -> {
                        configure(display);
                        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        display.setItemStack(PlayingCardHeads.item(tiles.get(index)));
                        display.setTransformation(PlayingCardHeads.pose(slot.width(), slot.fullDepth(), false, index, 4, 6));
                    });
                    DisplayAudience.show(plugin, view.player, head); visual.parts.add(head);
                }
            }
        }
    }

    public String action(Player player, String hitId) {
        sync();
        View view = views.get(player.getUniqueId());
        if (view == null || view.seat < 0 || hitId == null || room.phase() != GameSession.Phase.PLAYING) return null;
        if (rolling()) return null;
        if (room.kind.equals("mastermind") && hitId.matches("@palette/[0-5]")) {
            String action = "color:" + hitId.charAt(9);
            return view.actions.contains(action) ? action : null;
        }
        if (room.kind.equals("battleship") && hitId.matches("@ship/[0-4]")) {
            String action = "ship:" + hitId.charAt(6);
            return view.actions.contains(action) && view.layout.slot(hitId) != null ? action : null;
        }
        if (hitId.equals("@roll")) return diceTray != null && view.actions.contains("roll") ? "roll" : null;
        if (!view.sowing.isEmpty()) return null;
        if (hitId.startsWith("@page/")) {
            view.page += hitId.endsWith("next") ? 1 : -1;
            view.hover = null;
            view.layout = ClassicLayout.of(room.kind, view.snapshot, view.controlActions, view.page).controlsForSeat(view.seat, room.capacity);
            syncControls(view); syncHighlights(view); return null;
        }
        if (hitId.startsWith("@control/")) {
            if (view.layout.slot(hitId) == null) return null;
            int index;
            try { index = Integer.parseInt(hitId.substring(9)); } catch (NumberFormatException exception) { return null; }
            if (index < 0 || index >= view.controlActions.size()) return null;
            String action = view.controlActions.get(index);
            if (action.equals("@assets") && game() instanceof MonopolyGame monopoly) {
                PlatformMessages.send(view.player, MonopolyText.ledger(monopoly));
                return null;
            }
            if (view.input != null) {
                String submitted = view.input.select(action);
                if (view.input.closed()) view.input = null;
                refreshControls(view);
                return submitted;
            }
            view.choices = List.of();
            if (game() instanceof MonopolyGame monopoly) view.input = MonopolyTableInput.open(monopoly, view.seat, action);
            refreshControls(view);
            return action.equals("@cancel") || view.input != null ? null : action;
        }
        if (view.layout.slot(hitId) == null) return null;
        if (view.input != null) {
            if (hitId.matches("p[0-9]+")) view.input.property(Integer.parseInt(hitId.substring(1)));
            refreshControls(view);
            if (Objects.equals(view.hover, hitId)) describeHover(view);
            return null;
        }
        if (!view.choices.isEmpty()) { view.choices = List.of(); refreshControls(view); }
        if (hitId.equals(view.selected)) { view.selected = null; refreshSelection(view); return null; }
        List<String> related = game().actionsForCell(view.seat, hitId);
        if (room.kind.equals("monopoly")) {
            PlatformMessages.send(view.player, MonopolyText.details((MonopolyGame) game(), Integer.parseInt(hitId.substring(1)), view.seat));
            view.selected = hitId;
            view.choices = List.copyOf(related);
            refreshControls(view); return null;
        }
        if (view.selected != null) {
            String target = room.kind.equals("klondike") && hitId.matches("t[0-6]_[0-9]+") ? hitId.substring(0, 2) : hitId;
            List<String> moves = view.actions.stream().filter(action -> {
                String[] p = action.split(":");
                return p.length >= 3 && p[2].equals(target) && (p[0].equals("move") && p[1].equals(view.selected)
                        || p[0].equals("drop") && view.selected.equals("h" + view.seat + p[1]));
            }).toList();
            if (!moves.isEmpty()) return choose(view, moves);
        }
        boolean source = view.actions.stream().anyMatch(action -> {
            String[] p = action.split(":");
            return p.length >= 3 && (p[0].equals("move") && p[1].equals(hitId)
                    || p[0].equals("drop") && hitId.equals("h" + view.seat + p[1]));
        });
        if (source) { view.selected = hitId; refreshSelection(view); TableSounds.select(plugin, view.player, room.kind); return null; }
        return choose(view, related.stream().filter(action -> !action.startsWith("move:") && !action.startsWith("drop:")).toList());
    }

    private String choose(View view, List<String> choices) {
        if (choices.isEmpty()) {
            feedback(view, Language.component("hint.unavailable"));
            return null;
        }
        if (choices.size() == 1) return choices.getFirst();
        view.choices = choices; view.page = 0; refreshControls(view); return null;
    }

    public void hover(Player player, String hitId) {
        View view = views.get(player.getUniqueId());
        if (view == null || Objects.equals(view.hover, hitId)) return;
        view.hover = hitId;
        view.hoverPulses = 0;
        syncHighlights(view);
        if (hitId == null) feedback(view, view.seat >= 0 && view.feedbackText != null ? view.feedbackText : Component.empty());
        else describeHover(view);
    }

    private void describeHover(View view) {
        if (view.hover == null) return;
        if (view.hover.equals("@roll")) {
            feedback(view, rolling() ? Language.component("hint.rolling")
                    : view.actions.contains("roll") ? ClassicText.action(room.kind, "roll") : Language.component("hint.roll.unavailable"));
            return;
        }
        if (view.hover.startsWith("@palette/")) {
            feedback(view, ClassicText.action(room.kind, "color:" + view.hover.substring(9))); return;
        }
        if (view.hover.startsWith("@ship/")) {
            feedback(view, ClassicText.action(room.kind, "ship:" + view.hover.substring(6))); return;
        }
        Visual visual = view.visuals.get(view.hover);
        if (visual != null) {
            Component message = ClassicText.cell(game(), visual.cell);
            if (view.input != null && view.input.propertyField() && view.hover.matches("p[0-9]+")) {
                int property = Integer.parseInt(view.hover.substring(1));
                String hint = !view.input.selectableProperty(property) ? "hint.monopoly.property-unavailable"
                        : view.input.selectedProperties().contains(property) ? "hint.monopoly.property-selected" : "hint.monopoly.property-select";
                message = ClassicText.property(property).append(Component.text(" | ")).append(Language.component(hint));
            } else if (game() instanceof BattleshipGame sea && sea.setup() && !sea.confirmed(view.seat)
                    && view.hover.startsWith("b" + view.seat + "_")) {
                String hint = view.actions.contains("place:" + view.hover) ? "hint.battleship.place-valid" : "hint.battleship.place-invalid";
                message = message.append(Component.text(" | ")).append(Language.component(hint));
            }
            feedback(view, message.equals(Component.empty()) && view.feedbackText != null ? view.feedbackText : message);
        }
        else if (view.hover.startsWith("@control/") && view.layout.slot(view.hover) != null) {
            int index = Integer.parseInt(view.hover.substring(9));
            feedback(view, controlLabel(view, view.controlActions.get(index)));
        }
    }

    private void refreshSelection(View view) {
        for (var entry : List.copyOf(view.visuals.entrySet())) {
            Visual old = entry.getValue();
            if (old.expanded != old.cell.id().equals(view.selected) && room.kind.equals("klondike")) {
                old.close(); view.visuals.put(entry.getKey(), new Visual(view, old.cell, old.slot, old.monopoly));
                List<Entity> frame = view.highlights.remove(entry.getKey());
                if (frame != null) frame.forEach(DisplayAudience::retire);
                view.highlightColors.remove(entry.getKey());
            }
        }
        syncHighlights(view);
    }

    private void syncHighlights(View view) {
        String selectedShip = game() instanceof BattleshipGame sea && view.actions.contains("auto")
                ? "@ship/" + sea.infoFor(view.seat).get("ship") : null;
        view.controls.forEach((id, parts) -> {
            boolean hovered = id.equals(view.hover);
            if (id.startsWith("@control/") || id.startsWith("@page/")) TableButtonStyle.hover(parts, hovered);
            else for (Entity part : parts) if (part instanceof BlockDisplay display) {
                boolean selected = id.equals(selectedShip);
                display.setGlowing(hovered || selected);
                if (hovered || selected) display.setGlowColorOverride(hovered ? Color.YELLOW : Color.AQUA);
            }
        });
        Map<String, Material> desired = new LinkedHashMap<>();
        if (view.input != null) {
            for (int property : view.input.selectableProperties()) desired.put("p" + property, Material.LIME_CONCRETE);
            for (int property : view.input.selectedProperties()) desired.put("p" + property, Material.CYAN_CONCRETE);
        }
        if (room.kind.equals("mastermind")) {
            String slot = game().infoFor(view.seat).get("slot");
            if (slot != null) desired.put("draft" + slot, Material.CYAN_CONCRETE);
        }
        String last = lastPublicCell(view);
        if (last != null) desired.put(last, Material.ORANGE_CONCRETE);
        if (view.selected != null) {
            for (String action : view.actions) {
                String[] p = action.split(":");
                if (p.length < 3 || !(p[0].equals("move") && p[1].equals(view.selected)
                        || p[0].equals("drop") && view.selected.equals("h" + view.seat + p[1]))) continue;
                String target = p[2];
                if (room.kind.equals("klondike") && target.matches("t[0-6]") && view.layout.slot(target) == null) {
                    String prefix = target + "_";
                    target = view.visuals.keySet().stream().filter(id -> id.startsWith(prefix))
                            .max(Comparator.comparingInt(id -> Integer.parseInt(id.substring(3)))).orElse(target);
                }
                desired.put(target, Material.LIME_CONCRETE);
            }
            desired.put(view.selected, Material.CYAN_CONCRETE);
        }
        if (view.hover != null && !view.controls.containsKey(view.hover)) {
            Material hover = Material.YELLOW_CONCRETE;
            if (view.hover.equals(view.selected)) hover = Material.CYAN_CONCRETE;
            if (view.input != null && view.input.propertyField() && view.hover.matches("p[0-9]+")) {
                int property = Integer.parseInt(view.hover.substring(1));
                if (!view.input.selectableProperty(property)) hover = Material.RED_CONCRETE;
                else if (view.input.selectedProperties().contains(property)) hover = Material.CYAN_CONCRETE;
            }
            desired.put(view.hover, hover);
        }
        if (game() instanceof BattleshipGame sea) {
            List<String> footprint = sea.placementPreview(view.seat, view.hover);
            Material color = view.actions.contains("place:" + view.hover) ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            footprint.forEach(id -> desired.put(id, color));
        }
        for (String id : List.copyOf(view.highlights.keySet())) if (!Objects.equals(desired.get(id), view.highlightColors.get(id))) {
            view.highlights.remove(id).forEach(DisplayAudience::retire); view.highlightColors.remove(id);
        }
        for (var entry : desired.entrySet()) {
            if (view.highlights.containsKey(entry.getKey())) continue;
            var slot = view.layout.slot(entry.getKey());
            if (slot == null) continue;
            Visual visual = view.visuals.get(entry.getKey());
            if (room.kind.equals("klondike") && visual != null && visual.expanded) slot = slot.expanded();
            List<Entity> parts = new ArrayList<>();
            double halfW = slot.width()/2, halfD = slot.depth()/2, thickness = Math.min(.012, slot.width() * .10);
            boolean raisedTray = Set.of("shogi", "mini-shogi").contains(room.kind) && slot.id().matches("h[01][PLNSGBRK]")
                    || room.kind.equals("backgammon") && slot.id().startsWith("bar");
            double surface = room.kind.equals("klondike") ? slot.height() + .014 : raisedTray ? .0140
                    : room.kind.equals("dots-and-boxes") ? .0135 : room.kind.equals("mastermind") ? .018 : .010;
            Location center = origin.clone().add(slot.x(), surface + TableGeometry.SELECTION_GAP, slot.z());
            center.setYaw((float)slot.yaw());
            double angle = Math.toRadians(-slot.yaw()), c = Math.cos(angle), s = Math.sin(angle);
            if (slot.hex()) {
                double[][] corners = {{halfW, 0}, {halfW / 2, halfD}, {-halfW / 2, halfD}, {-halfW, 0}, {-halfW / 2, -halfD}, {halfW / 2, -halfD}};
                for (int index = 0; index < corners.length; index++) {
                    double[] a = corners[index], b = corners[(index + 1) % corners.length];
                    double midX = (a[0] + b[0]) / 2, midZ = (a[1] + b[1]) / 2;
                    double dx = (b[0] - a[0]) * c + (b[1] - a[1]) * s, dz = -(b[0] - a[0]) * s + (b[1] - a[1]) * c;
                    Location edge = center.clone().add(midX * c + midZ * s, 0, -midX * s + midZ * c);
                    edge.setYaw((float)Math.toDegrees(Math.atan2(dz, dx)));
                    parts.add(block(edge, entry.getValue(), Math.hypot(dx, dz), TableGeometry.SELECTION_THICKNESS, thickness, view.player));
                }
            } else for (double sign : new double[] {-1, 1}) {
                    parts.add(block(center.clone().add(sign * halfW * c, 0, -sign * halfW * s), entry.getValue(), thickness, TableGeometry.SELECTION_THICKNESS, slot.depth() - thickness, view.player));
                    parts.add(block(center.clone().add(sign * halfD * s, 0, sign * halfD * c), entry.getValue(), slot.width() + thickness, TableGeometry.SELECTION_THICKNESS, thickness, view.player));
            }
            view.highlights.put(entry.getKey(), parts); view.highlightColors.put(entry.getKey(), entry.getValue());
        }
    }

    private String lastPublicCell(View view) {
        if (room.kind.equals("mastermind") || room.history.isEmpty()) return null;
        var event = room.history.get(room.history.size() - 1).getAsJsonObject().get("action");
        if (!event.isJsonPrimitive() || !event.getAsJsonPrimitive().isString()) return null;
        String[] parts = event.getAsString().split(":");
        if (parts.length < 2 || room.kind.equals("battleship") && !parts[0].equals("shoot")) return null;
        if (!Set.of("move", "flip", "shoot", "place", "take", "sow", "draw", "drop").contains(parts[0])) return null;
        String target = parts.length >= 3 && (parts[0].equals("move") || parts[0].equals("drop")) ? parts[2] : parts[1];
        if (room.kind.equals("klondike") && target.matches("t[0-6]") && view.layout.slot(target) == null) {
            String prefix = target + "_";
            target = view.visuals.keySet().stream().filter(id -> id.startsWith(prefix))
                    .max(Comparator.comparingInt(id -> Integer.parseInt(id.substring(3)))).orElse(target);
        }
        return view.layout.slot(target) == null ? null : target;
    }

    public void clear(Player player) {
        View view = views.get(player.getUniqueId());
        if (view == null) return;
        boolean editing = view.input != null || !view.choices.isEmpty();
        view.hover = view.selected = null; view.choices = List.of(); view.input = null;
        if (editing) refreshControls(view);
        refreshSelection(view);
    }

    public Hit hit(Player player, Location eye, Vector direction) {
        View view = views.get(player.getUniqueId());
        if (view == null || !Objects.equals(eye.getWorld(), origin.getWorld()) || !finite(direction) || direction.lengthSquared() < 1e-10) return null;
        Vector ray = direction.clone().normalize();
        if (ray.getY() >= -1e-6) return null;
        Hit best = null;
        if (diceTray != null) {
            double distance = diceTray.hit(eye, ray);
            if (distance >= 0) best = new Hit("@roll", distance);
        }
        for (var baseSlot : view.layout.slots()) {
            Visual visual = view.visuals.get(baseSlot.id());
            var slot = room.kind.equals("klondike") && visual != null && visual.expanded ? baseSlot.expanded() : baseSlot;
            double offset = .04;
            if (visual != null && !room.kind.equals("mancala")
                    && (visual.cell.piece().isEmpty() || visual.cell.piece().equals("0")
                            || room.kind.equals("battleship") && visual.cell.piece().equals("water")))
                offset = room.kind.equals("klondike") ? .008 : 0;
            if (visual != null && room.kind.equals("dots-and-boxes")) offset = .002;
            if (visual != null && room.kind.equals("mastermind") && slot.id().startsWith("draft"))
                offset = visual.cell.piece().startsWith("color_") ? .034 : 0;
            double distance = (origin.getY() + slot.height() + offset - eye.getY()) / ray.getY();
            if (distance < 0 || distance > 5.5) continue;
            double x = eye.getX() + distance * ray.getX() - origin.getX(), z = eye.getZ() + distance * ray.getZ() - origin.getZ();
            boolean inside = slot.contains(x, z);
            if (!inside && room.kind.equals("battleship") && slot.id().matches("b[01]_[0-9]{1,2}"))
                inside = Math.abs(x - slot.x()) < .07 && Math.abs(z - slot.z()) < .07;
            if (!inside || best != null && distance >= best.distance()) continue;
            best = new Hit(slot.id(), distance);
        }
        return best != null && unblocked(eye, ray, best.distance()) ? best : null;
    }

    public double menuHit(Location eye, Vector direction) {
        if (!Objects.equals(eye.getWorld(), origin.getWorld()) || !finite(direction) || direction.lengthSquared() < 1e-10) return Double.POSITIVE_INFINITY;
        double halfX = ClassicLayout.tableHalfWidth(room.kind), halfZ = ClassicLayout.tableHalfDepth(room.kind);
        Vector ray = direction.clone().normalize();
        BoundingBox table = new BoundingBox(origin.getX() - halfX, origin.getY() - .21, origin.getZ() - halfZ,
                origin.getX() + halfX, origin.getY() + .10, origin.getZ() + halfZ);
        var result = table.rayTrace(eye.toVector(), ray, 5.5);
        double distance = result == null ? Double.POSITIVE_INFINITY : result.getHitPosition().distance(eye.toVector());
        if (diceTray != null) {
            double trayHit = diceTray.hit(eye, ray);
            if (trayHit >= 0) distance = Math.min(distance, trayHit);
        }
        if (!Double.isFinite(distance)) return distance;
        return unblocked(eye, ray, distance) ? distance : Double.POSITIVE_INFINITY;
    }

    private boolean finite(Vector vector) { return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ()); }
    private boolean unblocked(Location eye, Vector ray, double distance) {
        return origin.getWorld().rayTraceBlocks(eye, ray, Math.max(.001, distance - .035), FluidCollisionMode.NEVER, true) == null;
    }

    private void configure(Display display) {
        display.setVisibleByDefault(false);
        display.setPersistent(false); display.setGravity(false); display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15)); display.setViewRange(.35f);
        display.setTeleportDuration(0); display.setInterpolationDuration(0);
        display.getPersistentDataContainer().set(tag, PersistentDataType.STRING, room.id + "|@board");
    }

    private BlockDisplay block(Location at, Material material, double width, double height, double depth, Player player) {
        return block(at, material, new Transformation(new Vector3f((float)-width/2, 0, (float)-depth/2), new Quaternionf(),
                new Vector3f((float)width, (float)height, (float)depth), new Quaternionf()), player);
    }

    private BlockDisplay block(Location at, Material material, Transformation pose, Player player) {
        var entity = DisplayPlacement.spawn(at, BlockDisplay.class, display -> {
            configure(display); display.setBlock(material.createBlockData()); display.setTransformation(pose);
        });
        if (player != null) DisplayAudience.show(plugin, player, entity);
        return entity;
    }

    private ItemDisplay packed(Location at, String model, Vector3f scale, Quaternionf rotation, Player player) {
        return item(at, plugin.pack().item(model), scale, rotation, player);
    }

    private ItemDisplay item(Location at, ItemStack item, Vector3f scale, Quaternionf rotation, Player player) {
        var entity = DisplayPlacement.spawn(at, ItemDisplay.class, display -> {
            configure(display); display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); display.setItemStack(item);
            display.setTransformation(new Transformation(new Vector3f(), rotation, scale, new Quaternionf()));
        });
        if (player != null) DisplayAudience.show(plugin, player, entity);
        return entity;
    }

    private TextDisplay text(Location at, Component value, float scale, NamedTextColor color, Player player, boolean flat) {
        if (flat) at.setPitch(-90);
        var entity = DisplayPlacement.spawn(at, TextDisplay.class, display -> {
            configure(display); PlatformMessages.text(display, value.color(color)); display.setShadowed(false); display.setBackgroundColor(Color.fromARGB(0));
            display.setLineWidth(500); display.setTransformation(new Transformation(new Vector3f(0, flat ? -.125f * scale : 0, 0), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
        });
        if (player != null) DisplayAudience.show(plugin, player, entity);
        return entity;
    }

    public void close() {
        if (closed) return;
        closed = true;
        clickTargets.forEach(audience::remove);
        clickTargets.clear();
        if (diceTray != null) diceTray.close();
        views.values().forEach(View::clearVisuals); views.clear();
        nativeFurniture.forEach(DisplayAudience::retire); nativeFurniture.clear();
        packedFurniture.forEach(DisplayAudience::retire); packedFurniture.clear();
    }
}

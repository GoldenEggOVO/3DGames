package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Shared.game.Cell;

import java.util.*;
import java.util.regex.Pattern;

/** Table-local coordinates shared by display construction and pointer tests. */
public final class ClassicLayout {
    private static final Pattern TABLEAU_CARD = Pattern.compile("t[0-6]_[0-9]+");
    private static final Pattern TABLEAU_COLUMN = Pattern.compile("t[0-6]");
    static final double FLEET_CENTER_X = .82;
    public static final int CONTROLS_PER_PAGE = 10;

    public record Slot(String id, double x, double z, double width, double depth, double height, double fullDepth, boolean hex, double yaw) {
        public Slot(String id, double x, double z, double width, double depth, double height, double fullDepth, boolean hex) {
            this(id, x, z, width, depth, height, fullDepth, hex, 0);
        }
        public Slot(String id, double x, double z, double width, double depth, double height) {
            this(id, x, z, width, depth, height, depth, false);
        }
        public Slot(String id, double x, double z, double width, double depth, double height, double fullDepth) {
            this(id, x, z, width, depth, height, fullDepth, false);
        }
        public double renderZ() { return z + (fullDepth - depth) / 2; }
        public boolean covered() { return depth < fullDepth - .00001; }
        public Slot expanded() { return new Slot(id, x, renderZ(), width, fullDepth, height + .20, fullDepth, hex, yaw); }
        public boolean contains(double px, double pz) {
            double angle = Math.toRadians(-yaw), c = Math.cos(angle), s = Math.sin(angle);
            double dx = Math.abs((px - x) * c - (pz - z) * s), dz = Math.abs((px - x) * s + (pz - z) * c);
            return dx <= width / 2 && dz <= depth / 2 && (!hex || 2 * dx / width + dz / depth <= 1);
        }
    }

    private final String kind;
    private final List<Slot> slots;
    private final Map<String, Slot> byId;

    private ClassicLayout(String kind, List<Slot> slots) {
        this.kind = kind;
        this.slots = List.copyOf(slots);
        Map<String, Slot> index = new LinkedHashMap<>();
        for (Slot slot : slots) index.put(slot.id(), slot);
        byId = Map.copyOf(index);
    }

    public static ClassicLayout of(String kind, List<Cell> cells, List<String> controls) { return of(kind, cells, controls, 0); }

    public static ClassicLayout of(String kind, List<Cell> cells, List<String> controls, int page) {
        List<Slot> slots = new ArrayList<>();
        switch (kind) {
            case "klondike" -> addKlondikeSlots(slots, cells);
            case "mastermind" -> addMastermindSlots(slots, cells);
            default -> addBoardSlots(slots, kind, cells);
        }
        if (kind.equals("mastermind")) for (int color = 0; color < 6; color++)
            slots.add(new Slot("@palette/" + color, (color - 2.5) * .27, .77, .19, .18, .012));
        if (kind.equals("battleship") && controls.contains("auto")) for (int ship = 0; ship < 5; ship++)
            slots.add(new Slot("@ship/" + ship, (ship - 2) * .39, .84, .34, .18, .014));
        int pageCount = Math.max(1, (controls.size() + CONTROLS_PER_PAGE - 1) / CONTROLS_PER_PAGE);
        int current = Math.clamp(page, 0, pageCount - 1);
        int visible = Math.min(CONTROLS_PER_PAGE, controls.size() - current * CONTROLS_PER_PAGE);
        double z = kind.equals("monopoly") ? .35 : kind.equals("mancala") ? .78 : 1.03;
        for (int i = 0; i < visible; i++) slots.add(new Slot("@control/" + (current * CONTROLS_PER_PAGE + i),
                (i % 5 - 2) * .33, z + (i / 5) * .105, .305, .09, .014));
        if (pageCount > 1) {
            if (current > 0) slots.add(new Slot("@page/prev", -.96, z, .15, .09, .014));
            if (current + 1 < pageCount) slots.add(new Slot("@page/next", .96, z, .15, .09, .014));
        }
        return new ClassicLayout(kind, slots);
    }

    private static void addKlondikeSlots(List<Slot> slots, List<Cell> cells) {
        Map<String, Integer> cardIndices = new HashMap<>();
        Set<String> occupiedColumns = new HashSet<>();
        int longest = 1;
        for (Cell cell : cells) {
            String id = cell.id();
            if (id.indexOf('_') == 2) occupiedColumns.add(id.substring(0, 2));
            if (!TABLEAU_CARD.matcher(id).matches()) continue;
            int index = Integer.parseInt(id, 3, id.length(), 10);
            cardIndices.put(id, index);
            longest = Math.max(longest, index + 1);
        }
        double step = Math.min(.055, .94 / Math.max(1, longest - 1));
        for (Cell cell : cells) {
            String id = cell.id();
            Integer index = cardIndices.get(id);
            if (index != null) {
                int column = id.charAt(1) - '0';
                double depth = cardIndices.containsKey("t" + column + "_" + (index + 1)) ? step : .32;
                double top = -.48 + index * step;
                slots.add(new Slot(id, (column - 3) * .26, top + depth / 2, .23, depth, .025 + index * .003025, .32));
            } else if (TABLEAU_COLUMN.matcher(id).matches()) {
                if (!occupiedColumns.contains(id)) {
                    int column = id.charAt(1) - '0';
                    slots.add(new Slot(id, (column - 3) * .26, -.32, .23, .32, .018));
                }
            } else {
                slots.add(new Slot(id, (cell.x() - 3) * .26, -.70, .23, .32, .025));
            }
        }
    }

    private static void addMastermindSlots(List<Slot> slots, List<Cell> cells) {
        int attempt = 0;
        for (Cell cell : cells) {
            String id = cell.id();
            if (id.startsWith("g")) {
                attempt = Math.max(attempt, guessRow(id) + 1);
            }
        }
        for (Cell cell : cells) {
            String id = cell.id();
            if (id.startsWith("g")) {
                int column = Integer.parseInt(id, id.lastIndexOf('_') + 1, id.length(), 10);
                slots.add(new Slot(id, -.85 + guessRow(id) * .165,
                        (column - 1.5) * .20 + .08, .138, .138, .012));
            } else if (id.startsWith("feedback")) {
                int row = Integer.parseInt(id.substring(id.indexOf('_') + 1));
                slots.add(new Slot(id, -.85 + row * .165, -.50, .12, .12, .012));
            } else {
                if (id.startsWith("draft") && attempt >= 10) continue;
                slots.add(new Slot(id, id.startsWith("secret") ? .94 : -.85 + attempt * .165,
                        (cell.x() - 1.5) * .20 + .08, .138, .138, .012));
            }
        }
    }

    private static int guessRow(String id) {
        return Integer.parseInt(id, id.indexOf('_') + 1, id.lastIndexOf('_'), 10);
    }

    private static void addBoardSlots(List<Slot> slots, String kind, List<Cell> cells) {
        boolean shogi = kind.equals("shogi") || kind.equals("mini-shogi");
        int minX = cells.stream().filter(c -> !c.id().startsWith("h")).mapToInt(Cell::x).min().orElse(0);
        int maxX = cells.stream().filter(c -> !c.id().startsWith("h")).mapToInt(Cell::x).max().orElse(0);
        int minY = cells.stream().filter(c -> !c.id().startsWith("h")).mapToInt(Cell::y).min().orElse(0);
        int maxY = cells.stream().filter(c -> !c.id().startsWith("h")).mapToInt(Cell::y).max().orElse(0);
        double half = kind.equals("monopoly") ? 1.74 : shogi ? .72 : .90;
        double unit = 2 * half / Math.max(maxX - minX + 1, maxY - minY + 1);
        for (Cell cell : cells) {
            String id = cell.id();
            if (kind.equals("monopoly") && id.matches("p[0-9]+")) {
                slots.add(monopoly(Integer.parseInt(id.substring(1))));
            } else if (kind.equals("mancala")) {
                boolean store = id.equals("6") || id.equals("13");
                slots.add(new Slot(id, store ? id.equals("6") ? 1.24 : -1.24 : (cell.x() - 2.5) * .345,
                        store ? 0 : cell.y() == 0 ? -.285 : .285, store ? .23 : .295, store ? .95 : .40, -.036));
            } else if (kind.equals("backgammon")) {
                if (id.startsWith("p")) {
                    double x = (cell.x() - 5.5) * .18 + (cell.x() < 6 ? -.055 : .055);
                    slots.add(new Slot(id, x, cell.y() == 0 ? -.49 : .49, .16, .66, .012));
                } else slots.add(new Slot(id, id.startsWith("bar") ? 0 : 1.225, cell.y() == 0 ? -.30 : .30, .12, .30, id.startsWith("bar") ? .0155 : .012));
            } else if (kind.equals("battleship")) {
                int board = cell.x() / 11, col = cell.x() % 11;
                slots.add(new Slot(id, (board == 0 ? -FLEET_CENTER_X : FLEET_CENTER_X) + (col - 4.5) * .14,
                        (cell.y() - 4.5) * .14, .131, .131, .012));
            } else if (shogi && id.matches("h[01][PLNSGBRK]")) {
                int index = "PLNSGBRK".indexOf(id.charAt(2));
                slots.add(new Slot(id, -.76 + index * .215, id.charAt(1) == '0' ? .85 : -.85, .17, .12, .0155));
            } else if (kind.equals("hex")) {
                double hexUnit = 2.24 / (maxX - minX + 1 + (maxY - minY) * .5);
                double x = cell.x() - (minX + maxX) / 2.0, y = cell.y() - (minY + maxY) / 2.0;
                slots.add(new Slot(id, (x + y) * hexUnit * Math.sqrt(3) / 2,
                        (y - x) * hexUnit / 2, hexUnit * 2 / Math.sqrt(3), hexUnit, .0105, hexUnit, true, 0));
            } else {
                double width = unit * .9, depth = width;
                if (kind.equals("nine-mens-morris")) width = depth = .14;
                if (kind.equals("dots-and-boxes")) {
                    if (id.startsWith("h")) { width = unit * 1.55; depth = unit * .38; }
                    else if (id.startsWith("v")) { width = unit * .38; depth = unit * 1.55; }
                    else if (id.startsWith("p")) width = depth = unit * .28;
                }
                slots.add(new Slot(id, (cell.x() - (minX + maxX) / 2.0) * unit,
                        (cell.y() - (minY + maxY) / 2.0) * unit * (shogi ? -1 : 1), width, depth, .0105));
            }
        }
    }

    private static Slot monopoly(int index) {
        int edge = index / 10, step = index % 10;
        double corner = .48, pitch = .28, center = 1.50;
        double x = step == 0 ? center : center - corner / 2 - (step - .5) * pitch;
        double z = center, angle = edge * Math.PI / 2;
        return new Slot("p" + index, x * Math.cos(angle) - z * Math.sin(angle),
                x * Math.sin(angle) + z * Math.cos(angle), step == 0 ? corner : pitch - .006,
                corner, .0105, corner, false, edge * 90);
    }

    public List<Slot> slots() { return slots; }
    public Slot slot(String id) { return byId.get(id); }
    public ClassicLayout controlsForSeat(int seat, int capacity) {
        if (seat <= 0) return this;
        double angle = 2 * Math.PI * seat / Math.max(2, capacity), c = Math.cos(angle), s = Math.sin(angle);
        return new ClassicLayout(kind, slots.stream().map(slot -> !slot.id().startsWith("@") || slot.id().startsWith("@palette/") ? slot
                : new Slot(slot.id(), slot.x() * c + slot.z() * s, -slot.x() * s + slot.z() * c,
                        slot.width(), slot.depth(), slot.height(), slot.fullDepth(), slot.hex(), -Math.toDegrees(angle))).toList());
    }
    public static double tableHalfWidth(String kind) {
        return switch (kind) {
            case "monopoly" -> 1.875;
            case "hex" -> 1.6;
            case "battleship" -> 1.75;
            case "mancala" -> 1.6;
            case "backgammon" -> 1.4;
            default -> 1.125;
        };
    }
    public static double tableHalfDepth(String kind) {
        return kind.equals("monopoly") ? 1.875 : kind.equals("mancala") ? 1.0 : 1.225;
    }
    public double tableHalf() { return Math.max(tableHalfWidth(kind), tableHalfDepth(kind)); }
    public double boardHalf() { return kind.equals("monopoly") ? 1.74 : kind.equals("mancala") ? 1.44 : .90; }

    public String hit(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return null;
        for (int i = slots.size() - 1; i >= 0; i--) if (slots.get(i).contains(x, z)) return slots.get(i).id();
        return null;
    }
}

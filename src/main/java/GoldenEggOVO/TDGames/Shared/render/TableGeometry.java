package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;
import GoldenEggOVO.TDGames.Shared.render.classic.ClassicLayout;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public final class TableGeometry {

    record Layout(double xUnit, double zUnit, double midX, double midY) {
        public double x(Cell cell) {
            return (cell.x() - midX) * xUnit;
        }

        public double z(Cell cell) {
            return (cell.y() - midY) * zUnit;
        }
    }

    private static Layout createLayout(String kind, List<Cell> cells) {
        int minX = cells.stream().mapToInt(Cell::x).min().orElse(0),
                maxX = cells.stream().mapToInt(Cell::x).max().orElse(0);
        int minY = cells.stream().mapToInt(Cell::y).min().orElse(0),
                maxY = cells.stream().mapToInt(Cell::y).max().orElse(0);
        double unit = 1.80 / Math.max(maxX - minX + 1, maxY - minY + 1);
        if (kind.equals("yacht")) {
            unit = .17;
        }
        if (kind.equals("checkers")) {
            double scale =
                    Math.min(
                            1.80 / ((maxX - minX + 2) * .5), 1.80 / ((maxY - minY + 1) * .8660254));
            return new Layout(
                    .5 * scale, .8660254 * scale, (minX + maxX) / 2.0, (minY + maxY) / 2.0);
        }
        return new Layout(unit, unit, (minX + maxX) / 2.0, (minY + maxY) / 2.0);
    }


    public static final double SURFACE = 1.03125, REACH = 5.5;
    public static final double SELECTION_GAP = .0015, SELECTION_THICKNESS = .003;
    public final String kind;
    final Layout layout;
    final List<Cell> cells;
    final Map<String, Cell> byId;
    public final double spacing;
    private final boolean squareCells;
    private final Map<Long, Cell> regularGrid = new HashMap<>();

    public TableGeometry(String kind, List<Cell> cells) {
        this.kind = kind;
        this.cells = List.copyOf(cells);
        layout = createLayout(kind, cells);
        byId = new LinkedHashMap<>();
        cells.forEach(c -> byId.put(c.id(), c));
        squareCells = Set.of("chess", "draughts", "reversi").contains(kind);
        if (squareCells || Set.of("gomoku", "xiangqi", "go", "go9", "go13").contains(kind)) {
            for (Cell cell : cells) regularGrid.put(gridKey(cell.x(), cell.y()), cell);
        }
        spacing =
                kind.equals("checkers")
                        ? layout.xUnit() * 2
                        : Math.min(layout.xUnit(), layout.zUnit());
    }

    public double x(Cell c) {
        return layout.x(c);
    }

    public double z(Cell c) {
        return layout.z(c);
    }

    private static long gridKey(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    private double cellDistance(Cell cell, double x, double z) {
        double dx = x - x(cell), dz = z - z(cell);
        return squareCells ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.hypot(dx, dz);
    }

    public String hit(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return null;
        }
        if (Math.abs(x) < .45 && Math.abs(z - 1.27) < .15) {
            return "@menu";
        }
        double radius = spacing * (squareCells ? .499 : .46);
        if (!regularGrid.isEmpty()) {
            int col = (int) Math.round(x / layout.xUnit() + layout.midX());
            int row = (int) Math.round(z / layout.zUnit() + layout.midY());
            Cell candidate = regularGrid.get(gridKey(col, row));
            return candidate != null && cellDistance(candidate, x, z) <= radius ? candidate.id() : null;
        }
        Cell best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Cell c : cells) {
            double d = cellDistance(c, x, z);
            if (d < distance) {
                distance = d;
                best = c;
            }
        }
        return best != null && distance <= radius ? best.id() : null;
    }

    public static double intersection(double eyeY, double dy, double planeY) {
        if (!Double.isFinite(eyeY)
                || !Double.isFinite(dy)
                || !Double.isFinite(planeY)
                || dy >= -1e-5) {
            return -1;
        }
        double t = (planeY - eyeY) / dy;
        return t >= 0 && t <= REACH ? t : -1;
    }

    public static double menuHit(
            String kind, boolean sideTray, Location origin, Location eye, Vector direction) {
        if (!Objects.equals(origin.getWorld(), eye.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        double length = direction.lengthSquared();
        if (!Double.isFinite(length) || length < 1e-10) return Double.POSITIVE_INFINITY;
        Vector from = eye.toVector().subtract(origin.toVector()),
                ray = direction.clone().normalize();
        double nearest = Double.POSITIVE_INFINITY;
        if (Set.of("color-eight", "doudizhu", "liars-bar", "texas-holdem").contains(kind)) {
            nearest = roundHit(from, ray);
        } else {
            List<BoundingBox> bounds = new ArrayList<>();
            if (GameCatalog.CLASSIC_IDS.contains(kind)) {
                double halfX = ClassicLayout.tableHalfWidth(kind), halfZ = ClassicLayout.tableHalfDepth(kind);
                bounds.add(new BoundingBox(-halfX, -.21, -halfZ, halfX, .10, halfZ));
                if (Set.of("backgammon", "monopoly").contains(kind)) {
                    double x = TablePlacement.sideTrayX(halfX);
                    bounds.add(new BoundingBox(x - .7, -.03, -.7, x + .7, .11, .7));
                }
            } else if (kind.equals("yacht")) {
                bounds.add(new BoundingBox(-2.45, -.19, -1.125, -.85, .055, 1.125));
                bounds.add(new BoundingBox(-.68, -.19, -1.125, 1.32, .055, 1.125));
            } else {
                double half = kind.equals("mahjong") ? 1.5 : 1.125;
                bounds.add(
                        new BoundingBox(
                                -half,
                                -.19,
                                -half,
                                half,
                                kind.equals("mahjong") ? .065 : .06,
                                half));
                if (kind.equals("connectfour")) {
                    bounds.add(new BoundingBox(-1.125, -.04, -.10, 1.125, 1.83, .10));
                    for (double x : new double[] {-1.06, 1.06}) {
                        bounds.add(new BoundingBox(x - .10, -.04, -.10, x + .10, 1.79, .10));
                        bounds.add(new BoundingBox(x - .13, -.04, -.325, x + .13, .06, .325));
                    }
                }
                if (kind.equals("ludo")) {
                    double x = sideTray ? 2 : 1.42, halfTray = sideTray ? .7 : .25;
                    bounds.add(
                            new BoundingBox(
                                    x - halfTray, -.16, -halfTray, x + halfTray, .11, halfTray));
                }
            }
            for (BoundingBox box : bounds) {
                var hit = box.rayTrace(from, ray, REACH);
                if (hit != null) {
                    nearest = Math.min(nearest, hit.getHitPosition().distance(from));
                }
            }
        }
        if (!Double.isFinite(nearest) || nearest > REACH) {
            return Double.POSITIVE_INFINITY;
        }
        return eye.getWorld()
                                .rayTraceBlocks(
                                        eye,
                                        ray,
                                        Math.max(.001, nearest - .035),
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null
                ? nearest
                : Double.POSITIVE_INFINITY;
    }

    private static double roundHit(Vector from, Vector ray) {
        double nearest = Double.POSITIVE_INFINITY;

        if (Math.abs(ray.getY()) > 1e-6) {
            for (double y : new double[] {-.20, .025}) {
                double t = (y - from.getY()) / ray.getY();
                double x = from.getX() + t * ray.getX(), z = from.getZ() + t * ray.getZ();
                if (t >= 0 && x * x + z * z <= 2.25) {
                    nearest = Math.min(nearest, t);
                }
            }
        }
        double a = ray.getX() * ray.getX() + ray.getZ() * ray.getZ();
        double b = 2 * (from.getX() * ray.getX() + from.getZ() * ray.getZ());
        double c = from.getX() * from.getX() + from.getZ() * from.getZ() - 2.25;
        double discriminant = b * b - 4 * a * c;
        if (a > 1e-6 && discriminant >= 0) {
            for (double sign : new double[] {-1, 1}) {
                double t = (-b + sign * Math.sqrt(discriminant)) / (2 * a),
                        y = from.getY() + t * ray.getY();
                if (t >= 0 && y >= -.20 && y <= .025) {
                    nearest = Math.min(nearest, t);
                }
            }
        }
        return nearest;
    }

    public int px(Cell c) {
        return pixel(x(c));
    }

    public int pz(Cell c) {
        return pixel(z(c));
    }

    static int pixel(double coordinate) {
        return (int) Math.round((coordinate + 1) * 128);
    }
}

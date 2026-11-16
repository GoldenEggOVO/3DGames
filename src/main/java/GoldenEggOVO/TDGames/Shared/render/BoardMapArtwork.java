package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.game.Cell;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class BoardMapArtwork {
    static final int[] COLORS = {0xc83931, 0x2d63b4, 0x3a944b, 0xeabd31, 0x814fa9, 0xdb719b};

    public static BufferedImage draw(TableGeometry geometry) {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0x413932));
        graphics.fillRect(0, 0, 256, 256);
        graphics.setColor(new Color(0x7a8588));
        graphics.drawRect(3, 3, 249, 249);
        graphics.drawRect(6, 6, 243, 243);
        graphics.setColor(new Color(switch (geometry.kind) {
            case "reversi" -> 0x23854a;
            case "ludo" -> 0xf4f0df;
            default -> 0xe3be81;
        }));
        graphics.fillRect(10, 10, 236, 236);
        graphics.setStroke(new BasicStroke(1.25f));
        switch (geometry.kind) {
            case "chess", "draughts" -> chess(graphics, geometry);
            case "reversi" -> reversi(graphics, geometry);
            case "gomoku", "go", "go9", "go13" -> grid(graphics, geometry, false);
            case "xiangqi" -> grid(graphics, geometry, true);
            case "checkers" -> checkers(graphics, geometry);
            case "ludo" -> ludo(graphics, geometry);
            default -> throw new IllegalArgumentException(geometry.kind);
        }
        graphics.setColor(new Color(0x7a8588));
        for (int x : new int[] {3, 247}) for (int y : new int[] {3, 247}) {
            graphics.fillRect(x, y, 6, 6);
            graphics.setColor(new Color(0xb6b0a0));
            graphics.drawLine(x + 1, y + 1, x + 4, y + 1);
            graphics.setColor(new Color(0x7a8588));
        }
        graphics.dispose();
        return image;
    }

    static void grid(Graphics2D graphics, TableGeometry geometry, boolean river) {
        int maxX = geometry.cells.stream().mapToInt(Cell::x).max().orElse(14),
                maxY = geometry.cells.stream().mapToInt(Cell::y).max().orElse(14);
        int left = geometry.px(geometry.cells.getFirst()),
                top = geometry.pz(geometry.cells.getFirst());
        int right = TableGeometry.pixel((maxX - geometry.layout.midX()) * geometry.layout.xUnit());
        int bottom = TableGeometry.pixel((maxY - geometry.layout.midY()) * geometry.layout.zUnit());
        graphics.setColor(new Color(0x30271c));
        for (int y = 0; y <= maxY; y++) {
            int py = TableGeometry.pixel((y - geometry.layout.midY()) * geometry.layout.zUnit());
            graphics.drawLine(left, py, right, py);
        }
        for (int x = 0; x <= maxX; x++) {
            int px = TableGeometry.pixel((x - geometry.layout.midX()) * geometry.layout.xUnit());
            if (river && x > 0 && x < 8) {
                graphics.drawLine(
                        px,
                        top,
                        px,
                        TableGeometry.pixel(
                                (4 - geometry.layout.midY()) * geometry.layout.zUnit()));
                graphics.drawLine(
                        px,
                        TableGeometry.pixel((5 - geometry.layout.midY()) * geometry.layout.zUnit()),
                        px,
                        bottom);
            } else {
                graphics.drawLine(px, top, px, bottom);
            }
        }
        if (river) {
            for (int row : new int[] {0, 7}) {
                for (int diagonal = 0; diagonal < 2; diagonal++) {
                    int x1 = diagonal == 0 ? 3 : 5, x2 = diagonal == 0 ? 5 : 3;
                    graphics.drawLine(
                            TableGeometry.pixel(
                                    (x1 - geometry.layout.midX()) * geometry.layout.xUnit()),
                            TableGeometry.pixel(
                                    (row - geometry.layout.midY()) * geometry.layout.zUnit()),
                            TableGeometry.pixel(
                                    (x2 - geometry.layout.midX()) * geometry.layout.xUnit()),
                            TableGeometry.pixel(
                                    (row + 2 - geometry.layout.midY()) * geometry.layout.zUnit()));
                }
            }

        } else {
            int edge = maxX == 8 ? 2 : 3, mid = maxX / 2;
            for (int x : new int[] {edge, mid, maxX - edge}) {
                for (int y : new int[] {edge, mid, maxY - edge}) {
                    dot(
                            graphics,
                            TableGeometry.pixel((x - mid) * geometry.layout.xUnit()),
                            TableGeometry.pixel((y - mid) * geometry.layout.zUnit()),
                            maxX > 14 ? 1.7 : 2.5,
                            0x30271c);
                }
            }
        }
    }

    static void reversi(Graphics2D graphics, TableGeometry geometry) {
        graphics.setColor(new Color(0x23854a));
        graphics.fillRect(11, 11, 234, 234);
        graphics.setColor(new Color(0x153e27));
        double halfCellPixels = geometry.spacing * 64;
        for (Cell cell : geometry.cells) {
            graphics.drawRect(
                    (int) Math.round(geometry.px(cell) - halfCellPixels),
                    (int) Math.round(geometry.pz(cell) - halfCellPixels),
                    (int) Math.round(halfCellPixels * 2),
                    (int) Math.round(halfCellPixels * 2));
        }
        for (int x : new int[] {2, 6}) {
            for (int y : new int[] {2, 6}) {
                dot(
                        graphics,
                        TableGeometry.pixel((x - 4) * geometry.spacing),
                        TableGeometry.pixel((y - 4) * geometry.spacing),
                        2,
                        0xe1ead8);
            }
        }
    }

    static void chess(Graphics2D graphics, TableGeometry geometry) {
        double halfCellPixels = geometry.spacing * 128 / 2;
        for (Cell cell : geometry.cells) {
            graphics.setColor(new Color(((cell.x() + cell.y()) & 1) == 0 ? 0xb58863 : 0xf0d9b5));
            int x = (int) Math.round(geometry.px(cell) - halfCellPixels),
                    z = (int) Math.round(geometry.pz(cell) - halfCellPixels),
                    cellWidth = (int) Math.ceil(halfCellPixels * 2) + 1;
            graphics.fillRect(x, z, cellWidth, cellWidth);
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        for (Cell cell : geometry.cells) {
            graphics.setColor(new Color(0xd1d5ce));
            if (cell.y() == 0) {
                graphics.drawString(
                        String.valueOf((char) ('A' + cell.x())), geometry.px(cell) - 3, 10);
            }
            if (cell.x() == 0) {
                graphics.drawString("" + (cell.y() + 1), 3, geometry.pz(cell) + 3);
            }
        }
    }

    static void checkers(Graphics2D graphics, TableGeometry geometry) {
        double edge = geometry.spacing * 128;
        graphics.setColor(new Color(0x5b4229));
        graphics.setStroke(new BasicStroke(.8f));
        for (int firstIndex = 0; firstIndex < geometry.cells.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1;
                    secondIndex < geometry.cells.size();
                    secondIndex++) {
                Cell firstCell = geometry.cells.get(firstIndex),
                        secondCell = geometry.cells.get(secondIndex);
                if (Math.hypot(
                                geometry.px(firstCell) - geometry.px(secondCell),
                                geometry.pz(firstCell) - geometry.pz(secondCell))
                        < edge * 1.1) {
                    graphics.drawLine(
                            geometry.px(firstCell),
                            geometry.pz(firstCell),
                            geometry.px(secondCell),
                            geometry.pz(secondCell));
                }
            }
        }
        for (Cell cell : geometry.cells) {
            int color = camp(cell);
            dot(
                    graphics,
                    geometry.px(cell),
                    geometry.pz(cell),
                    edge * .35,
                    color < 0 ? 0xcfad76 : COLORS[color]);
            dot(graphics, geometry.px(cell), geometry.pz(cell), edge * .20, 0x5b4229);
        }
    }

    public static int camp(Cell cell) {
        if (cell.y() >= 13) {
            return 0;
        }
        if (cell.y() <= 3) {
            return 3;
        }
        if (cell.y() >= 9 && cell.x() <= cell.y() - 6) {
            return 1;
        }
        if (cell.y() <= 7 && cell.x() <= 10 - cell.y()) {
            return 2;
        }
        if (cell.y() >= 9 && cell.x() >= 30 - cell.y()) {
            return 5;
        }
        if (cell.y() <= 7 && cell.x() >= 14 + cell.y()) {
            return 4;
        }
        return -1;
    }

    static void ludo(Graphics2D graphics, TableGeometry geometry) {
        double cellPixels = geometry.spacing * 128;
        graphics.setColor(new Color(0xf4f0df));
        graphics.fillRect(11, 11, 234, 234);
        for (int color = 0; color < 4; color++) {
            final int baseColor = color;
            List<Cell> base =
                    geometry.cells.stream()
                            .filter(p -> p.id().startsWith("ba" + baseColor))
                            .toList();
            int x = base.stream().mapToInt(geometry::px).min().orElseThrow(),
                    y = base.stream().mapToInt(geometry::pz).min().orElseThrow();
            graphics.setColor(new Color(COLORS[color]));
            graphics.fillRoundRect(
                    (int) (x - cellPixels * 1.2),
                    (int) (y - cellPixels * 1.2),
                    (int) (cellPixels * 4.4),
                    (int) (cellPixels * 4.4),
                    14,
                    14);
            graphics.setColor(new Color(0xf4f0df));
            graphics.fillRoundRect(
                    (int) (x - cellPixels * .6),
                    (int) (y - cellPixels * .6),
                    (int) (cellPixels * 3.2),
                    (int) (cellPixels * 3.2),
                    12,
                    12);
        }
        for (Cell cell : geometry.cells) {
            String id = cell.id();
            int color = ludoCellColor(id);
            int x = geometry.px(cell), y = geometry.pz(cell);
            if (id.startsWith("ba")) {
                dot(graphics, x, y, cellPixels * .38, COLORS[color]);
                dot(graphics, x, y, cellPixels * .25, 0xf4f0df);
                continue;
            }
            int half = (int) Math.round(cellPixels * .48);
            graphics.setColor(new Color(color < 0 ? 0xf4f0df : COLORS[color]));
            graphics.fillRect(x - half, y - half, half * 2, half * 2);
            graphics.setColor(new Color(0x3f4439));
            graphics.setStroke(new BasicStroke(.7f));
            graphics.drawRect(x - half, y - half, half * 2, half * 2);
            if (id.startsWith("go")) {
                dot(graphics, x, y, cellPixels * .18, 0xf4f0df);
            }
            if (id.startsWith("sk") && color >= 0) {
                graphics.setColor(new Color(0xf4f0df));
                graphics.drawOval(x - 3, y - 3, 6, 6);
            }
        }
        graphics.setColor(new Color(0xe8d5a0));
        graphics.fillOval(122, 122, 12, 12);
    }

    private static int ludoCellColor(String id) {
        if (!id.startsWith("sk")) {
            return Character.digit(id.charAt(2), 10);
        }
        int trackIndex = Integer.parseInt(id.substring(2));
        return trackIndex % 13 == 0 ? trackIndex / 13 : -1;
    }

    static void dot(Graphics2D graphics, int x, int y, double radius, int color) {
        graphics.setColor(new Color(color));
        graphics.fillOval(
                (int) Math.round(x - radius),
                (int) Math.round(y - radius),
                (int) Math.round(radius * 2),
                (int) Math.round(radius * 2));
    }
}

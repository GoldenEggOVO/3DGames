package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Shared.game.Cell;

import java.util.ArrayList;
import java.util.List;

/** Expands a confirmed rule transition into visual seed transfers. */
public final class MancalaSowing {
    private MancalaSowing() { }

    /** Both snapshots include all seeds; the flight temporarily removes seeds from before. */
    public record Step(List<Cell> before, List<Cell> after, String fromId, String toId, int seeds) {
        public Step {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    public static List<Step> plan(List<Cell> before, List<Cell> after, String action) {
        int[] values = counts(before);
        if (values == null || counts(after) == null || action == null || !action.startsWith("sow:")) return List.of();
        int from;
        try { from = Integer.parseInt(action.substring(4)); }
        catch (NumberFormatException ignored) { return List.of(); }
        if (from < 0 || from >= 14 || from == 6 || from == 13 || !action.equals("sow:" + from)
                || values[from] == 0 || empty(values, 0) || empty(values, 1)) return List.of();

        List<Cell> template = List.copyOf(before);
        List<Step> steps = new ArrayList<>();
        int seat = from / 7, store = seat * 7 + 6, opposingStore = (1 - seat) * 7 + 6;
        int remaining = values[from], last = from;
        while (remaining > 0) {
            last = (last + 1) % 14;
            if (last == opposingStore) continue;
            transfer(template, values, steps, from, last, 1);
            remaining--;
        }

        // Build only a candidate visual route; the supplied rule result remains authoritative.
        if (last >= seat * 7 && last < store && values[last] == 1 && values[12 - last] > 0) {
            transfer(template, values, steps, last, store, 1);
            transfer(template, values, steps, 12 - last, store, values[12 - last]);
        }
        if (empty(values, 0) || empty(values, 1)) {
            for (int owner = 0; owner < 2; owner++) for (int pit = owner * 7; pit < owner * 7 + 6; pit++)
                if (values[pit] > 0) transfer(template, values, steps, pit, owner * 7 + 6, values[pit]);
        }
        return steps.size() <= 50 && snapshot(template, values).equals(after) ? List.copyOf(steps) : List.of();
    }

    private static int[] counts(List<Cell> cells) {
        if (cells == null || cells.size() != 14) return null;
        int[] values = new int[14];
        int total = 0;
        for (int i = 0; i < 14; i++) {
            Cell cell = cells.get(i);
            if (cell == null || !Integer.toString(i).equals(cell.id())) return null;
            try { values[i] = Integer.parseInt(cell.piece()); }
            catch (NumberFormatException ignored) { return null; }
            if (values[i] < 0 || values[i] > 48 || !Integer.toString(values[i]).equals(cell.piece())) return null;
            total += values[i];
        }
        return total == 48 ? values : null;
    }

    private static boolean empty(int[] values, int seat) {
        for (int pit = seat * 7; pit < seat * 7 + 6; pit++) if (values[pit] > 0) return false;
        return true;
    }

    private static void transfer(List<Cell> template, int[] values, List<Step> steps, int from, int to, int seeds) {
        List<Cell> before = snapshot(template, values);
        values[from] -= seeds;
        values[to] += seeds;
        steps.add(new Step(before, snapshot(template, values), Integer.toString(from), Integer.toString(to), seeds));
    }

    private static List<Cell> snapshot(List<Cell> template, int[] values) {
        List<Cell> cells = new ArrayList<>(14);
        for (int i = 0; i < 14; i++) {
            Cell cell = template.get(i);
            cells.add(new Cell(cell.id(), cell.x(), cell.y(), Integer.toString(values[i]), cell.owner()));
        }
        return List.copyOf(cells);
    }
}

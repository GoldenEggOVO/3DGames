package GoldenEggOVO.TDGames.Singleplayer.game.twentyfortyeight;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class TwentyFortyEightGame implements ClassicGame {
    private static final List<String> DIRECTIONS = List.of("left", "right", "up", "down");
    private final int[] board = new int[16];
    private final Random random;
    private long score;
    private boolean reachedGoal;
    private boolean continued;
    private String result = "";

    public TwentyFortyEightGame(int players, long seed, Map<String, String> options) {
        if (players != 1) throw new IllegalArgumentException("2048 requires one player");
        random = new Random(seed);
        spawn();
        spawn();
    }

    @Override public String id() { return "2048"; }
    @Override public int playerCount() { return 1; }
    @Override public int currentPlayer() { return 0; }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            cells.add(new Cell(x + "," + y, x, y, Integer.toString(board[y * 4 + x]), board[y * 4 + x] == 0 ? -1 : 0));
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (seat != 0 || finished()) return List.of();
        if (reachedGoal && !continued) return List.of("continue", "finish");
        List<String> actions = new ArrayList<>();
        for (String direction : DIRECTIONS)
            if (!Arrays.equals(board, slide(direction).values())) actions.add("slide:" + direction);
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) { return List.of(); }

    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal 2048 action");
        if (action.equals("finish")) {
            result = "winner:0";
        } else if (action.equals("continue")) {
            continued = true;
            finishIfBlocked();
        } else {
            Slide moved = slide(action.substring(6));
            System.arraycopy(moved.values(), 0, board, 0, board.length);
            score += moved.score();
            spawn();
            reachedGoal |= Arrays.stream(board).max().orElse(0) >= 2048;
            if (!reachedGoal || continued) finishIfBlocked();
        }
    }

    private void finishIfBlocked() {
        if (DIRECTIONS.stream().allMatch(direction -> Arrays.equals(board, slide(direction).values())))
            result = reachedGoal ? "winner:0" : "draw:no-moves";
    }

    private Slide slide(String direction) {
        int[] values = new int[16];
        long gained = 0;
        for (int line = 0; line < 4; line++) {
            int[] compact = new int[4];
            int count = 0;
            for (int position = 0; position < 4; position++) {
                int value = board[index(direction, line, position)];
                if (value != 0) compact[count++] = value;
            }
            int destination = 0;
            for (int source = 0; source < count; source++) {
                int value = compact[source];
                if (source + 1 < count && value == compact[source + 1]) {
                    value *= 2;
                    gained += value;
                    source++;
                }
                values[index(direction, line, destination++)] = value;
            }
        }
        return new Slide(values, gained);
    }

    private int index(String direction, int line, int position) {
        return switch (direction) {
            case "left" -> line * 4 + position;
            case "right" -> line * 4 + 3 - position;
            case "up" -> position * 4 + line;
            case "down" -> (3 - position) * 4 + line;
            default -> throw new IllegalArgumentException("Unknown direction");
        };
    }

    private void spawn() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < board.length; i++) if (board[i] == 0) empty.add(i);
        if (!empty.isEmpty()) board[empty.get(random.nextInt(empty.size()))] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    @Override public Map<String, String> publicInfo() {
        return Map.of("score", Long.toString(score), "max", Integer.toString(Arrays.stream(board).max().orElse(0)),
                "goal", Boolean.toString(reachedGoal), "continued", Boolean.toString(continued));
    }

    @Override public String botAction(int seat, Random random) { return null; }

    private record Slide(int[] values, long score) { }
}

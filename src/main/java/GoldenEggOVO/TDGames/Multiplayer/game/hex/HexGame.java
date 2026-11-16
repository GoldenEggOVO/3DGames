package GoldenEggOVO.TDGames.Multiplayer.game.hex;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

public final class HexGame implements ClassicGame {
    private static final int SIZE = 11;
    private static final int[][] NEIGHBOURS = {{1,0},{-1,0},{0,1},{0,-1},{1,-1},{-1,1}};
    private final int[] board = new int[SIZE * SIZE];
    private final int[] colours = {0,1};
    private final boolean pieRule;
    private int turn;
    private int moves;
    private String result = "";

    public HexGame(int players, long seed, Map<String, String> options) {
        if (players != 2) throw new IllegalArgumentException("Hex requires two players");
        Arrays.fill(board, -1);
        pieRule = !"false".equalsIgnoreCase(options.getOrDefault("pie-rule", "true"));
    }

    @Override public String id() { return "hex"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return turn; }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int colour = board[y * SIZE + x];
            cells.add(new Cell(x + "," + y, x, y, colour < 0 ? "" : colour == 0 ? "NS" : "EW", colour < 0 ? -1 : seatFor(colour)));
        }
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (seat != turn || finished()) return List.of();
        List<String> actions = new ArrayList<>();
        if (pieRule && moves == 1 && seat == 1) actions.add("swap");
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (board[y * SIZE + x] < 0) actions.add("place:" + x + "," + y);
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        return cellId == null ? List.of() : legalActions(seat).stream().filter(action -> action.equals("place:" + cellId)).toList();
    }

    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal Hex action");
        if (action.equals("swap")) {
            colours[0] = 1;
            colours[1] = 0;
        } else {
            String[] coordinates = action.substring(6).split(",");
            int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]);
            board[y * SIZE + x] = colours[seat];
            if (connected(colours[seat])) result = "winner:" + seat;
        }
        moves++;
        turn = 1 - turn;
    }

    private int seatFor(int colour) { return colours[0] == colour ? 0 : 1; }

    private boolean connected(int colour) {
        boolean[] visited = new boolean[board.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < SIZE; i++) {
            int position = colour == 0 ? i : i * SIZE;
            if (board[position] == colour) { visited[position] = true; queue.add(position); }
        }
        while (!queue.isEmpty()) {
            int position = queue.removeFirst(), x = position % SIZE, y = position / SIZE;
            if (colour == 0 && y == SIZE - 1 || colour == 1 && x == SIZE - 1) return true;
            for (int[] delta : NEIGHBOURS) {
                int nx = x + delta[0], ny = y + delta[1];
                if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) continue;
                int next = ny * SIZE + nx;
                if (!visited[next] && board[next] == colour) { visited[next] = true; queue.add(next); }
            }
        }
        return false;
    }

    @Override public String botAction(int seat, Random random) {
        List<String> actions = legalActions(seat);
        if (actions.isEmpty()) return null;
        if (actions.contains("swap")) {
            for (int i = 0; i < board.length; i++) if (board[i] >= 0 && Math.abs(i % SIZE - 5) <= 2 && Math.abs(i / SIZE - 5) <= 2) return "swap";
        }
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (String action : actions) {
            if (action.equals("swap")) continue;
            String[] coordinates = action.substring(6).split(",");
            int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]), position = y * SIZE + x;
            board[position] = colours[seat];
            if (connected(colours[seat])) { board[position] = -1; return action; }
            int value = 20 * (distance(colours[1 - seat]) - distance(colours[seat])) - Math.abs(x - 5) - Math.abs(y - 5);
            board[position] = colours[1 - seat];
            if (connected(colours[1 - seat])) value += 10000;
            board[position] = -1;
            if (value > bestValue) { bestValue = value; best = action; }
        }
        return best;
    }

    private int distance(int colour) {
        int[] distances = new int[board.length];
        Arrays.fill(distances, 1000);
        PriorityQueue<Path> queue = new PriorityQueue<>((a,b) -> Integer.compare(a.cost(), b.cost()));
        for (int i = 0; i < SIZE; i++) {
            int position = colour == 0 ? i : i * SIZE;
            int cost = cost(position, colour);
            if (cost < 1000) { distances[position] = cost; queue.add(new Path(position, cost)); }
        }
        while (!queue.isEmpty()) {
            Path path = queue.remove();
            int position = path.position(), x = position % SIZE, y = position / SIZE;
            if (path.cost() != distances[position]) continue;
            if (colour == 0 && y == SIZE - 1 || colour == 1 && x == SIZE - 1) return path.cost();
            for (int[] delta : NEIGHBOURS) {
                int nx = x + delta[0], ny = y + delta[1];
                if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) continue;
                int next = ny * SIZE + nx, cost = path.cost() + cost(next, colour);
                if (cost < distances[next]) { distances[next] = cost; queue.add(new Path(next, cost)); }
            }
        }
        return 1000;
    }

    private int cost(int position, int colour) { return board[position] == colour ? 0 : board[position] < 0 ? 1 : 1000; }

    @Override public Map<String, String> publicInfo() {
        return Map.of("colour.0", colours[0] == 0 ? "NS" : "EW", "colour.1", colours[1] == 0 ? "NS" : "EW",
                "moves", Integer.toString(moves), "swap", Boolean.toString(pieRule && moves == 1));
    }

    private record Path(int position, int cost) { }
}

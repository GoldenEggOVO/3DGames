package GoldenEggOVO.TDGames.Multiplayer.game.dotsandboxes;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public final class DotsAndBoxesGame implements ClassicGame {
    private static final Map<String, Edge> EDGES = createEdges();
    private final int players;
    private final int[] horizontal = new int[30];
    private final int[] vertical = new int[30];
    private final int[] boxes = new int[25];
    private final int[] scores;
    private int turn;
    private String result = "";

    public DotsAndBoxesGame(int players, long seed, Map<String, String> options) {
        if (players < 2 || players > 4) throw new IllegalArgumentException("Dots and boxes requires two to four players");
        this.players = players;
        scores = new int[players];
        Arrays.fill(horizontal, -1);
        Arrays.fill(vertical, -1);
        Arrays.fill(boxes, -1);
    }

    @Override public String id() { return "dots-and-boxes"; }
    @Override public int playerCount() { return players; }
    @Override public int currentPlayer() { return turn; }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < 6; y++) for (int x = 0; x < 6; x++) {
            cells.add(new Cell("p" + x + "," + y, x * 2, y * 2, "DOT", -1));
            if (x < 5) cells.add(new Cell("h" + x + "," + y, x * 2 + 1, y * 2, horizontal[y * 5 + x] < 0 ? "" : "H", horizontal[y * 5 + x]));
            if (y < 5) cells.add(new Cell("v" + x + "," + y, x * 2, y * 2 + 1, vertical[y * 6 + x] < 0 ? "" : "V", vertical[y * 6 + x]));
            if (x < 5 && y < 5) cells.add(new Cell("b" + x + "," + y, x * 2 + 1, y * 2 + 1, boxes[y * 5 + x] < 0 ? "" : "B", boxes[y * 5 + x]));
        }
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (seat != turn || finished()) return List.of();
        List<String> actions = new ArrayList<>();
        EDGES.forEach((action, edge) -> { if (available(edge)) actions.add(action); });
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null || seat != turn || finished()) return List.of();
        String action = "draw:" + cellId;
        return available(EDGES.get(action)) ? List.of(action) : List.of();
    }

    @Override public void apply(int seat, String action) {
        Edge edge = EDGES.get(action);
        if (seat != turn || finished() || !available(edge)) throw new IllegalArgumentException("Illegal dots and boxes action");
        values(edge)[index(edge)] = seat;
        int captured = claimBox(edge.x(), edge.y(), seat) + claimBox(edge.otherBoxX(), edge.otherBoxY(), seat);
        scores[seat] += captured;
        if (Arrays.stream(scores).sum() == 25) {
            int maximum = Arrays.stream(scores).max().orElse(0);
            List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < players; i++) if (scores[i] == maximum) winners.add(i);
            result = winners.size() == 1 ? "winner:" + winners.getFirst() : "winners:" + winners.stream().map(String::valueOf).collect(Collectors.joining(","));
        } else if (captured == 0) turn = (turn + 1) % players;
    }

    private int claimBox(int x, int y, int seat) {
        if (drawnSidesOfOpenBox(x, y) != 4) return 0;
        boxes[y * 5 + x] = seat;
        return 1;
    }

    private int drawnSidesOfOpenBox(int x, int y) {
        if (x < 0 || x >= 5 || y < 0 || y >= 5 || boxes[y * 5 + x] >= 0) return -1;
        return (horizontal[y * 5 + x] >= 0 ? 1 : 0) + (horizontal[(y + 1) * 5 + x] >= 0 ? 1 : 0)
                + (vertical[y * 6 + x] >= 0 ? 1 : 0) + (vertical[y * 6 + x + 1] >= 0 ? 1 : 0);
    }

    private static Map<String, Edge> createEdges() {
        Map<String, Edge> edges = new LinkedHashMap<>();
        for (int y = 0; y < 6; y++) for (int x = 0; x < 5; x++) edges.put("draw:h" + x + "," + y, new Edge(true, x, y));
        for (int y = 0; y < 5; y++) for (int x = 0; x < 6; x++) edges.put("draw:v" + x + "," + y, new Edge(false, x, y));
        return Collections.unmodifiableMap(edges);
    }

    private int[] values(Edge edge) { return edge.horizontal() ? horizontal : vertical; }
    private int index(Edge edge) { return edge.y() * (edge.horizontal() ? 5 : 6) + edge.x(); }
    private boolean available(Edge edge) { return edge != null && values(edge)[index(edge)] < 0; }

    @Override public String botAction(int seat, Random random) {
        if (seat != turn || finished()) return null;
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (var candidate : EDGES.entrySet()) {
            Edge edge = candidate.getValue();
            if (!available(edge)) continue;
            int value = drawValue(edge.x(), edge.y()) + drawValue(edge.otherBoxX(), edge.otherBoxY());
            if (value > bestValue) { bestValue = value; best = candidate.getKey(); }
        }
        return best;
    }

    private int drawValue(int x, int y) {
        // Compare the change in score: closing a box also removes its three-side penalty.
        return switch (drawnSidesOfOpenBox(x, y)) {
            case 3 -> 100 + 10;
            case 2 -> -10;
            default -> 0;
        };
    }

    @Override public Map<String, String> publicInfo() {
        Map<String,String> info = new LinkedHashMap<>();
        for (int seat = 0; seat < players; seat++) info.put("score." + seat, Integer.toString(scores[seat]));
        info.put("remaining", Integer.toString(25 - Arrays.stream(scores).sum()));
        return Map.copyOf(info);
    }

    private record Edge(boolean horizontal, int x, int y) {
        int otherBoxX() { return horizontal ? x : x - 1; }
        int otherBoxY() { return horizontal ? y - 1 : y; }
    }
}

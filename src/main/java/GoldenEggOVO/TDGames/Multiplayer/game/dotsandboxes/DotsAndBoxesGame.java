package GoldenEggOVO.TDGames.Multiplayer.game.dotsandboxes;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public final class DotsAndBoxesGame implements ClassicGame {
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
        for (int y = 0; y < 6; y++) for (int x = 0; x < 5; x++) if (horizontal[y * 5 + x] < 0) actions.add("draw:h" + x + "," + y);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 6; x++) if (vertical[y * 6 + x] < 0) actions.add("draw:v" + x + "," + y);
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        return cellId == null ? List.of() : legalActions(seat).stream().filter(action -> action.equals("draw:" + cellId)).toList();
    }

    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal dots and boxes action");
        Edge edge = edge(action);
        values(edge)[index(edge)] = seat;
        int captured = 0;
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) if (boxes[y * 5 + x] < 0 && sides(x, y) == 4) {
            boxes[y * 5 + x] = seat;
            scores[seat]++;
            captured++;
        }
        if (Arrays.stream(scores).sum() == 25) {
            int maximum = Arrays.stream(scores).max().orElse(0);
            List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < players; i++) if (scores[i] == maximum) winners.add(i);
            result = winners.size() == 1 ? "winner:" + winners.getFirst() : "winners:" + winners.stream().map(String::valueOf).collect(Collectors.joining(","));
        } else if (captured == 0) turn = (turn + 1) % players;
    }

    private int sides(int x, int y) {
        return (horizontal[y * 5 + x] >= 0 ? 1 : 0) + (horizontal[(y + 1) * 5 + x] >= 0 ? 1 : 0)
                + (vertical[y * 6 + x] >= 0 ? 1 : 0) + (vertical[y * 6 + x + 1] >= 0 ? 1 : 0);
    }

    private Edge edge(String action) {
        String[] coordinates = action.substring(6).split(",");
        return new Edge(action.charAt(5) == 'h', Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
    }

    private int[] values(Edge edge) { return edge.horizontal() ? horizontal : vertical; }
    private int index(Edge edge) { return edge.y() * (edge.horizontal() ? 5 : 6) + edge.x(); }

    @Override public String botAction(int seat, Random random) {
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (String action : legalActions(seat)) {
            Edge edge = edge(action);
            values(edge)[index(edge)] = seat;
            int value = 0;
            for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) if (boxes[y * 5 + x] < 0) {
                int sides = sides(x, y);
                if (sides == 4) value += 100;
                else if (sides == 3) value -= 10;
            }
            values(edge)[index(edge)] = -1;
            if (value > bestValue) { bestValue = value; best = action; }
        }
        return best;
    }

    @Override public Map<String, String> publicInfo() {
        Map<String,String> info = new LinkedHashMap<>();
        for (int seat = 0; seat < players; seat++) info.put("score." + seat, Integer.toString(scores[seat]));
        info.put("remaining", Integer.toString(25 - Arrays.stream(scores).sum()));
        return Map.copyOf(info);
    }

    private record Edge(boolean horizontal, int x, int y) { }
}

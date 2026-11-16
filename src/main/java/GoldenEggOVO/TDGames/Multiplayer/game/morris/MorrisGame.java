package GoldenEggOVO.TDGames.Multiplayer.game.morris;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MorrisGame implements ClassicGame {
    private static final int[][] MILLS = {
        {0,1,2}, {2,3,4}, {4,5,6}, {6,7,0}, {8,9,10}, {10,11,12}, {12,13,14}, {14,15,8},
        {16,17,18}, {18,19,20}, {20,21,22}, {22,23,16}, {1,9,17}, {3,11,19}, {5,13,21}, {7,15,23}
    };
    private static final int[][] COORDINATES = {
        {0,0},{3,0},{6,0},{6,3},{6,6},{3,6},{0,6},{0,3},
        {1,1},{3,1},{5,1},{5,3},{5,5},{3,5},{1,5},{1,3},
        {2,2},{3,2},{4,2},{4,3},{4,4},{3,4},{2,4},{2,3}
    };
    private final int[] board = new int[24];
    private final int[] unplaced = {9,9};
    private final Map<String,Integer> repetitions = new HashMap<>();
    private int turn;
    private boolean taking;
    private String result = "";

    public MorrisGame(int players, long seed, Map<String, String> options) {
        if (players != 2) throw new IllegalArgumentException("Nine men's morris requires two players");
        Arrays.fill(board, -1);
    }

    @Override public String id() { return "nine-mens-morris"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return turn; }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < board.length; i++) cells.add(new Cell(Integer.toString(i), COORDINATES[i][0], COORDINATES[i][1], board[i] < 0 ? "" : "M", board[i]));
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (seat != turn || finished()) return List.of();
        List<String> actions = new ArrayList<>();
        if (taking) {
            boolean unprotected = false;
            for (int i = 0; i < board.length; i++) if (board[i] == 1 - seat && !inMill(i)) unprotected = true;
            for (int i = 0; i < board.length; i++)
                if (board[i] == 1 - seat && (!unprotected || !inMill(i))) actions.add("take:" + i);
        } else if (unplaced[seat] > 0) {
            for (int i = 0; i < board.length; i++) if (board[i] < 0) actions.add("place:" + i);
        } else {
            boolean flying = count(seat) == 3;
            if (count(seat) < 3) return List.of();
            for (int from = 0; from < board.length; from++) if (board[from] == seat)
                for (int to = 0; to < board.length; to++) if (board[to] < 0 && (flying || adjacent(from, to))) actions.add("move:" + from + ":" + to);
        }
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        return ClassicGame.super.actionsForCell(seat, cellId);
    }

    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal morris action");
        if (moving() && repetitions.isEmpty()) remember();
        String[] parts = action.split(":");
        if (parts[0].equals("take")) {
            board[Integer.parseInt(parts[1])] = -1;
            taking = false;
            endTurn(seat);
            return;
        }
        int destination;
        if (parts[0].equals("place")) {
            destination = Integer.parseInt(parts[1]);
            unplaced[seat]--;
        } else {
            board[Integer.parseInt(parts[1])] = -1;
            destination = Integer.parseInt(parts[2]);
        }
        board[destination] = seat;
        if (inMill(destination) && count(1 - seat) > 0) taking = true;
        else endTurn(seat);
    }

    private void endTurn(int actor) {
        turn = 1 - actor;
        if (moving()) {
            if (count(actor) < 3) result = "winner:" + turn;
            else if (count(turn) < 3 || legalActions(turn).isEmpty()) result = "winner:" + actor;
            else if (remember() >= 3) result = "draw:threefold-repetition";
        }
    }

    private boolean moving() { return unplaced[0] == 0 && unplaced[1] == 0; }

    private int remember() { return repetitions.merge(Arrays.toString(board) + "/" + turn, 1, Integer::sum); }

    private int count(int seat) {
        int count = 0;
        for (int owner : board) if (owner == seat) count++;
        return count;
    }

    private boolean inMill(int point) {
        if (board[point] < 0) return false;
        for (int[] mill : MILLS) if ((mill[0] == point || mill[1] == point || mill[2] == point)
                && board[mill[0]] == board[point] && board[mill[1]] == board[point] && board[mill[2]] == board[point]) return true;
        return false;
    }

    private boolean adjacent(int from, int to) {
        if (from / 8 == to / 8) {
            int distance = Math.abs(from % 8 - to % 8);
            if (distance == 1 || distance == 7) return true;
        }
        return from % 2 == 1 && from % 8 == to % 8 && Math.abs(from / 8 - to / 8) == 1;
    }

    @Override public String botAction(int seat, Random random) {
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (String action : legalActions(seat)) {
            String[] parts = action.split(":");
            int destination = Integer.parseInt(parts[parts.length - 1]);
            int value;
            if (taking) {
                value = potential(destination, 1 - seat) + (destination % 2 == 1 ? 2 : 0);
            } else {
                int from = parts.length == 3 ? Integer.parseInt(parts[1]) : -1;
                if (from >= 0) board[from] = -1;
                value = potential(destination, 1 - seat);
                board[destination] = seat;
                value += inMill(destination) ? 100 : potential(destination, seat);
                board[destination] = -1;
                if (from >= 0) board[from] = seat;
            }
            if (value > bestValue) { bestValue = value; best = action; }
        }
        return best;
    }

    private int potential(int point, int seat) {
        int value = 0;
        for (int[] mill : MILLS) if (mill[0] == point || mill[1] == point || mill[2] == point) {
            int own = 0;
            boolean blocked = false;
            for (int position : mill) if (position != point) {
                if (board[position] == seat) own++;
                else if (board[position] >= 0) blocked = true;
            }
            if (!blocked) value += own == 2 ? 20 : own;
        }
        return value;
    }

    @Override public Map<String, String> publicInfo() {
        return Map.of("phase", finished() ? "finished" : taking ? "take" : moving() ? "move" : "place",
                "unplaced.0", Integer.toString(unplaced[0]), "unplaced.1", Integer.toString(unplaced[1]),
                "pieces.0", Integer.toString(count(0)), "pieces.1", Integer.toString(count(1)));
    }
}

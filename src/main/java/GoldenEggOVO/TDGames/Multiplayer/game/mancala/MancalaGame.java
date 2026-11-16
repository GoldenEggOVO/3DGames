package GoldenEggOVO.TDGames.Multiplayer.game.mancala;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MancalaGame implements ClassicGame {
    private final int[] pits = new int[14];
    private int turn;
    private String result = "";

    public MancalaGame(int players, long seed, Map<String, String> options) {
        if (players != 2) throw new IllegalArgumentException("Kalah requires two players");
        Arrays.fill(pits, 4);
        pits[6] = pits[13] = 0;
    }

    @Override public String id() { return "mancala"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return turn; }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            int x = i == 6 ? 6 : i == 13 ? -1 : i < 6 ? i : 12 - i;
            int y = i < 7 ? 1 : 0;
            cells.add(new Cell(Integer.toString(i), x, y, Integer.toString(pits[i]), i < 7 ? 0 : 1));
        }
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (seat != turn || finished()) return List.of();
        List<String> actions = new ArrayList<>();
        for (int i = seat * 7; i < seat * 7 + 6; i++) if (pits[i] > 0) actions.add("sow:" + i);
        return List.copyOf(actions);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        return cellId == null ? List.of() : legalActions(seat).stream().filter(action -> action.equals("sow:" + cellId)).toList();
    }

    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal Kalah action");
        int last = sow(pits, seat, Integer.parseInt(action.substring(4)));
        if (empty(pits, 0) || empty(pits, 1)) {
            sweep(pits);
            result = pits[6] == pits[13] ? "draw:equal-stores" : "winner:" + (pits[6] > pits[13] ? 0 : 1);
        } else if (last != seat * 7 + 6) turn = 1 - turn;
    }

    private int sow(int[] values, int seat, int from) {
        int remaining = values[from];
        values[from] = 0;
        int position = from;
        while (remaining > 0) {
            position = (position + 1) % 14;
            if (position == (1 - seat) * 7 + 6) continue;
            values[position]++;
            remaining--;
        }
        if (position >= seat * 7 && position < seat * 7 + 6 && values[position] == 1 && values[12 - position] > 0) {
            values[seat * 7 + 6] += values[position] + values[12 - position];
            values[position] = values[12 - position] = 0;
        }
        return position;
    }

    private boolean empty(int[] values, int seat) {
        for (int i = seat * 7; i < seat * 7 + 6; i++) if (values[i] > 0) return false;
        return true;
    }

    private void sweep(int[] values) {
        for (int seat = 0; seat < 2; seat++) for (int i = seat * 7; i < seat * 7 + 6; i++) {
            values[seat * 7 + 6] += values[i];
            values[i] = 0;
        }
    }

    @Override public String botAction(int seat, Random random) {
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (String action : legalActions(seat)) {
            int[] after = pits.clone();
            int last = sow(after, seat, Integer.parseInt(action.substring(4)));
            boolean end = empty(after, 0) || empty(after, 1);
            if (end) sweep(after);
            int value = 10 * (after[seat * 7 + 6] - after[(1 - seat) * 7 + 6]) + (last == seat * 7 + 6 ? 5 : 0);
            if (end) value += Integer.compare(after[seat * 7 + 6], after[(1 - seat) * 7 + 6]) * 1000;
            if (value > bestValue) { bestValue = value; best = action; }
        }
        return best;
    }

    @Override public Map<String, String> publicInfo() {
        return Map.of("score.0", Integer.toString(pits[6]), "score.1", Integer.toString(pits[13]), "phase", finished() ? "finished" : "sow");
    }
}

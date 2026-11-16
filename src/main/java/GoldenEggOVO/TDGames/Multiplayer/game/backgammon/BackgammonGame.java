package GoldenEggOVO.TDGames.Multiplayer.game.backgammon;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Standard single-game backgammon with complete, at most four-die turn search. */
public final class BackgammonGame implements ClassicGame {
    private final int[] points = new int[24];
    private final int[] bar = new int[2];
    private final int[] off = new int[2];
    private final List<Integer> remaining = new ArrayList<>();
    private final int[] rolledDice = new int[2];
    private final Random diceRandom;
    private final boolean doubling;
    private int turn;
    private String phase = "move";
    private String result = "ongoing";
    private int cube = 1;
    private int cubeOwner = -1;
    private int winPoints;
    private String winType = "";

    private record Move(int from, int to, int die) {
        String action(int seat) { return "move:" + (from == -1 ? "bar" + seat : "p" + (from + 1))
                + ":" + (to == -1 ? "off" + seat : "p" + (to + 1)) + ":" + die; }
    }

    public BackgammonGame(int players, long seed, Map<String, String> options) {
        if (players != 2) throw new IllegalArgumentException("Backgammon requires two players");
        doubling = "on".equals(options.getOrDefault("doubling", "off"));
        diceRandom = new Random(seed);
        points[23] = 2; points[12] = 5; points[7] = 3; points[5] = 5;
        points[0] = -2; points[11] = -5; points[16] = -3; points[18] = -5;
        int first, second;
        do { first = die(); second = die(); } while (first == second);
        turn = first > second ? 0 : 1;
        remaining.add(first); remaining.add(second);
        rolledDice[0] = first; rolledDice[1] = second;
    }

    @Override public String id() { return "backgammon"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return phase.equals("double") ? 1 - turn : turn; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    public List<Integer> dice() { return List.copyOf(remaining); }
    public int[] rolledDice() { return rolledDice.clone(); }
    public int bar(int seat) { return bar[seat]; }
    public int borneOff(int seat) { return off[seat]; }
    public int cube() { return cube; }
    public int cubeOwner() { return cubeOwner; }
    public int winPoints() { return winPoints; }

    @Override public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(28);
        for (int i = 0; i < 24; i++) out.add(new Cell("p" + (i + 1), i < 12 ? i : 23 - i,
                i < 12 ? 0 : 1, points[i] == 0 ? "" : Integer.toString(Math.abs(points[i])), owner(points[i])));
        for (int seat = 0; seat < 2; seat++) {
            out.add(new Cell("bar" + seat, 12, seat, bar[seat] == 0 ? "" : Integer.toString(bar[seat]), bar[seat] == 0 ? -1 : seat));
            out.add(new Cell("off" + seat, 13, seat, off[seat] == 0 ? "" : Integer.toString(off[seat]), off[seat] == 0 ? -1 : seat));
        }
        return List.copyOf(out);
    }

    @Override public Map<String, String> publicInfo() {
        return Map.of("phase", finished() ? "finished" : phase, "dice", remaining.toString(),
                "cube", Integer.toString(cube), "cubeOwner", Integer.toString(cubeOwner),
                "turn", Integer.toString(currentPlayer()), "winPoints", Integer.toString(winPoints),
                "winType", winType, "doubling", doubling ? "on" : "off", "outcome", result);
    }

    @Override public List<String> legalActions(int seat) {
        if (finished() || seat != currentPlayer()) return List.of();
        if (phase.equals("double")) return List.of("double:accept", "double:reject");
        if (phase.equals("roll")) return canDouble() ? List.of("roll", "double:offer") : List.of("roll");
        List<Move> moves = legalMoves();
        return moves.isEmpty() ? List.of("pass") : moves.stream().map(m -> m.action(turn)).toList();
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream().filter(a -> {
            String[] p = a.split(":");
            return p.length == 4 && (p[1].equals(cellId) || p[2].equals(cellId));
        }).toList();
    }

    @Override public List<String> controlActions(int seat) {
        return legalActions(seat).stream().filter(a -> !a.startsWith("move:")).toList();
    }

    @Override public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null)
            throw new IllegalArgumentException("Invalid backgammon actor or phase");
        if (phase.equals("double")) {
            if (action.equals("double:accept")) {
                cube *= 2;
                cubeOwner = seat;
                phase = "roll";
                return;
            }
            if (action.equals("double:reject")) {
                result = "winner:" + turn;
                winPoints = cube;
                winType = "double-refused";
                return;
            }
            throw new IllegalArgumentException("Doubling response required");
        }
        if (phase.equals("roll")) {
            if (action.equals("double:offer") && canDouble()) { phase = "double"; return; }
            if (!action.equals("roll")) throw new IllegalArgumentException("Roll required");
            int first = die(), second = die();
            rolledDice[0] = first; rolledDice[1] = second;
            remaining.add(first); remaining.add(second);
            if (first == second) { remaining.add(first); remaining.add(first); }
            phase = "move";
            return;
        }
        List<Move> legal = legalMoves();
        if (action.equals("pass") && legal.isEmpty()) { endTurn(); return; }
        Move selected = null;
        for (Move move : legal) if (move.action(turn).equals(action)) { selected = move; break; }
        if (selected == null) throw new IllegalArgumentException("Illegal backgammon move or die selection");
        make(selected);
        remaining.remove(Integer.valueOf(selected.die));
        if (off[turn] == 15) {
            result = "winner:" + turn;
            int other = 1 - turn;
            boolean inHome = false;
            for (int i = 0; i < 24; i++) if (owner(points[i]) == other && home(turn, i)) inHome = true;
            int multiplier = off[other] > 0 ? 1 : bar[other] > 0 || inHome ? 3 : 2;
            winType = multiplier == 1 ? "normal" : multiplier == 2 ? "gammon" : "backgammon";
            winPoints = cube * multiplier;
        } else if (remaining.isEmpty()) endTurn();
    }

    private int die() { return diceRandom.nextInt(6) + 1; }
    private boolean canDouble() { return doubling && cube < 64 && (cubeOwner == -1 || cubeOwner == turn); }
    private void endTurn() { remaining.clear(); turn = 1 - turn; phase = "roll"; }
    private static int sign(int seat) { return seat == 0 ? 1 : -1; }
    private static int owner(int count) { return count == 0 ? -1 : count > 0 ? 0 : 1; }
    private static boolean home(int seat, int point) { return seat == 0 ? point < 6 : point >= 18; }

    private List<Move> localMoves(int die) {
        List<Move> out = new ArrayList<>();
        if (bar[turn] > 0) {
            int to = turn == 0 ? 24 - die : die - 1;
            if (points[to] * sign(turn) >= -1) out.add(new Move(-1, to, die));
            return out;
        }
        boolean allHome = true;
        for (int p = 0; p < 24; p++) if (owner(points[p]) == turn && !home(turn, p)) allHome = false;
        for (int from = 0; from < 24; from++) {
            if (owner(points[from]) != turn) continue;
            int to = from - die * sign(turn);
            if (to >= 0 && to < 24) {
                if (points[to] * sign(turn) >= -1) out.add(new Move(from, to, die));
            } else if (allHome) {
                int exact = turn == 0 ? from + 1 : 24 - from;
                boolean higher = false;
                for (int p = 0; p < 24; p++)
                    if (owner(points[p]) == turn && (turn == 0 ? p > from : p < from)) higher = true;
                if (die == exact || die > exact && !higher) out.add(new Move(from, -1, die));
            }
        }
        return out;
    }

    private List<Move> legalMoves() {
        Map<String, Integer> memo = new HashMap<>();
        int max = maxPlayable(remaining, memo);
        if (max == 0) return List.of();
        List<Move> legal = new ArrayList<>();
        for (int die : remaining.stream().distinct().toList()) for (Move move : localMoves(die)) {
            Snapshot before = snapshot();
            make(move);
            List<Integer> rest = new ArrayList<>(remaining);
            rest.remove(Integer.valueOf(die));
            int played = 1 + maxPlayable(rest, memo);
            restore(before);
            if (played == max) legal.add(move);
        }
        if (max == 1) {
            int highest = legal.stream().mapToInt(Move::die).max().orElse(0);
            legal.removeIf(m -> m.die != highest);
        }
        return List.copyOf(legal);
    }

    private int maxPlayable(List<Integer> dice, Map<String, Integer> memo) {
        if (dice.isEmpty() || off[turn] == 15) return 0;
        String key = Arrays.toString(points) + Arrays.toString(bar) + Arrays.toString(off) + dice;
        Integer known = memo.get(key);
        if (known != null) return known;
        int max = 0;
        for (int die : dice.stream().distinct().toList()) for (Move move : localMoves(die)) {
            Snapshot before = snapshot();
            make(move);
            List<Integer> rest = new ArrayList<>(dice);
            rest.remove(Integer.valueOf(die));
            max = Math.max(max, 1 + maxPlayable(rest, memo));
            restore(before);
            if (max == dice.size()) { memo.put(key, max); return max; }
        }
        memo.put(key, max);
        return max;
    }

    private record Snapshot(int[] points, int[] bar, int[] off) { }
    private Snapshot snapshot() { return new Snapshot(points.clone(), bar.clone(), off.clone()); }
    private void restore(Snapshot before) {
        System.arraycopy(before.points, 0, points, 0, 24);
        System.arraycopy(before.bar, 0, bar, 0, 2);
        System.arraycopy(before.off, 0, off, 0, 2);
    }

    private void make(Move move) {
        int sign = sign(turn);
        if (move.from == -1) bar[turn]--; else points[move.from] -= sign;
        if (move.to == -1) off[turn]++;
        else {
            if (points[move.to] == -sign) { points[move.to] = 0; bar[1 - turn]++; }
            points[move.to] += sign;
        }
    }

    @Override public String botAction(int seat, Random random) {
        if (finished() || seat != currentPlayer()) return null;
        if (phase.equals("double")) return "double:accept";
        if (phase.equals("roll")) return "roll";
        List<Move> moves = legalMoves();
        if (moves.isEmpty()) return "pass";
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : moves) {
            Snapshot before = snapshot();
            make(move);
            int score = 100 * off[turn] + 30 * bar[1 - turn] + random.nextInt(8);
            for (int p = 0; p < 24; p++) if (owner(points[p]) == turn) {
                int count = Math.abs(points[p]);
                score -= (turn == 0 ? p + 1 : 24 - p) * count;
                score += count == 1 ? -12 : 8;
            }
            restore(before);
            if (score > bestScore) { bestScore = score; best = move; }
        }
        return best.action(turn);
    }
}

package GoldenEggOVO.TDGames.Shared.game.memory;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.*;

public final class MemoryGame implements ClassicGame {
    private static final List<String> FACES = List.of("apple", "diamond", "book", "compass", "emerald", "gold_ingot",
            "iron_ingot", "ender_pearl", "carrot", "cake", "clock", "feather", "slime_ball", "redstone", "amethyst_shard",
            "honey_bottle", "nautilus_shell", "totem_of_undying");
    private final int players;
    private final int[] cards = new int[36], owners = new int[36], seen = new int[36], scores;
    private int turn, first = -1, second = -1, rounds, mistakes, pairs;
    private String result = "ongoing";

    public MemoryGame(int players, long seed, Map<String, String> options) {
        if (players < 1 || players > 4) throw new IllegalArgumentException("Memory requires one to four players");
        this.players = players;
        scores = new int[players];
        Arrays.fill(owners, -1);
        Arrays.fill(seen, -1);
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 18; i++) { deck.add(i); deck.add(i); }
        Collections.shuffle(deck, new Random(seed));
        for (int i = 0; i < 36; i++) cards[i] = deck.get(i);
    }

    public String id() { return "memory"; }
    public int playerCount() { return players; }
    public int currentPlayer() { return turn; }
    public boolean finished() { return !result.equals("ongoing"); }
    public String outcome() { return result; }
    public List<Integer> scores() { return Arrays.stream(scores).boxed().toList(); }
    public boolean waitingToHide() { return second >= 0; }

    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 36; i++) cells.add(new Cell("m" + i, i % 6, i / 6,
                owners[i] >= 0 ? "" : i == first || i == second ? FACES.get(cards[i]) : "back", owners[i]));
        return List.copyOf(cells);
    }

    public List<String> legalActions(int seat) {
        if (finished() || seat < 0 || seat != turn || waitingToHide()) return List.of();
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (owners[i] < 0 && i != first) actions.add("flip:m" + i);
        return List.copyOf(actions);
    }

    public void apply(int seat, String action) {
        if ("hide".equals(action) && waitingToHide() && seat == turn && !finished()) {
            first = second = -1;
            turn = (turn + 1) % players;
            return;
        }
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.classic.action", "Invalid Memory action");
        int index = Integer.parseInt(action.substring(6));
        seen[index] = cards[index];
        if (first < 0) first = index;
        else {
            rounds++;
            if (cards[first] == cards[index]) {
                owners[first] = owners[index] = turn;
                scores[turn]++;
                first = -1;
                if (++pairs == 18) {
                    int maximum = Arrays.stream(scores).max().orElseThrow();
                    List<String> winners = new ArrayList<>();
                    for (int i = 0; i < players; i++) if (scores[i] == maximum) winners.add(Integer.toString(i));
                    result = winners.size() == 1 ? "winner:" + winners.getFirst() : "winners:" + String.join(",", winners);
                }
            } else { second = index; mistakes++; }
        }
    }

    public String automaticAction() { return waitingToHide() ? "hide" : null; }
    public long automaticDelayMillis() { return waitingToHide() ? 1200 : 0; }

    public Map<String, String> publicInfo() {
        return Map.of("turn", Integer.toString(turn), "scores", scores().toString(), "rounds", Integer.toString(rounds),
                "mistakes", Integer.toString(mistakes), "pairs", Integer.toString(pairs),
                "phase", waitingToHide() ? "waiting" : first < 0 ? "first" : "second");
    }

    public String botAction(int seat, Random random) {
        List<String> actions = legalActions(seat);
        if (actions.isEmpty()) return null;
        if (first >= 0) {
            for (int i = 0; i < 36; i++) if (i != first && owners[i] < 0 && seen[i] == seen[first]) return "flip:m" + i;
        } else {
            for (int i = 0; i < 36; i++) if (owners[i] < 0 && seen[i] >= 0)
                for (int j = i + 1; j < 36; j++) if (owners[j] < 0 && seen[j] == seen[i]) return "flip:m" + i;
        }
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (owners[i] < 0 && i != first && seen[i] < 0) unknown.add("flip:m" + i);
        List<String> choices = unknown.isEmpty() ? actions : unknown;
        return choices.get(random.nextInt(choices.size()));
    }
}

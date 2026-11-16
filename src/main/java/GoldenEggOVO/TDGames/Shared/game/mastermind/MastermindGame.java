package GoldenEggOVO.TDGames.Shared.game.mastermind;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.*;

public final class MastermindGame implements ClassicGame {
    public record Guess(int round, List<Integer> colors, int exact, int misplaced) {
        public Guess { colors = List.copyOf(colors); }
    }

    private final int players;
    private final int[] draft = {-1, -1, -1, -1};
    private final int[] scores = new int[2];
    private final List<Guess> guesses = new ArrayList<>();
    private int[] secret;
    private int round, selectedSlot;
    private boolean setting;
    private String result = "ongoing";

    public MastermindGame(int players, long seed, Map<String, String> options) {
        if (players < 1 || players > 2) throw new IllegalArgumentException("Mastermind requires one or two players");
        this.players = players;
        setting = players == 2;
        if (!setting) secret = new Random(seed).ints(4, 0, 6).toArray();
    }

    public String id() { return "mastermind"; }
    public int playerCount() { return players; }
    public int currentPlayer() { return setting ? round : players == 1 ? 0 : 1 - round; }
    public boolean finished() { return !result.equals("ongoing"); }
    public String outcome() { return result; }
    public int round() { return round; }
    public boolean setting() { return setting; }
    public List<Guess> guesses() { return List.copyOf(guesses); }
    public List<Integer> scores() { return Arrays.stream(scores).limit(players).boxed().toList(); }

    public List<Cell> cells() { return cellsFor(-1); }

    public List<Cell> cellsFor(int seat) {
        List<Cell> cells = new ArrayList<>();
        for (int r = 0; r < (players == 1 ? 1 : 2); r++) {
            int row = 0;
            for (Guess guess : guesses) {
                if (guess.round() != r) continue;
                for (int i = 0; i < 4; i++) cells.add(new Cell("g" + r + "_" + row + "_" + i,
                        i, r * 11 + row, "color_" + guess.colors().get(i), players == 1 ? 0 : 1 - r));
                cells.add(new Cell("feedback" + r + "_" + row, 5, r * 11 + row,
                        guess.exact() + "_" + guess.misplaced(), -1));
                row++;
            }
        }
        for (int i = 0; i < 4; i++) {
            String edit = seat == currentPlayer() && !finished() && draft[i] >= 0 ? "color_" + draft[i] : "back";
            cells.add(new Cell("draft" + i, i, 23, edit, currentPlayer()));
            String answer = secret != null && (finished() || players == 2 && seat == round)
                    ? "color_" + secret[i] : "back";
            cells.add(new Cell("secret" + i, i, 25, answer, players == 2 ? round : -1));
        }
        return List.copyOf(cells);
    }

    public List<String> legalActions(int seat) {
        if (finished() || seat < 0 || seat != currentPlayer()) return List.of();
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < draft.length; i++) {
            actions.add("slot:" + i);
            actions.add("next-color:" + i);
            actions.add("previous-color:" + i);
        }
        for (int i = 0; i < 6; i++) actions.add("color:" + i);
        if (Arrays.stream(draft).allMatch(color -> color >= 0)) actions.add("submit");
        return List.copyOf(actions);
    }

    public List<String> actionsForCell(int seat, String id) {
        if (id != null && id.matches("draft[0-3]")) {
            String action = "slot:" + id.charAt(5);
            return legalActions(seat).contains(action) ? List.of(action) : List.of();
        }
        return List.of();
    }

    @Override
    public List<String> controlActions(int seat) {
        return !finished() && currentPlayer() == seat && Arrays.stream(draft).allMatch(color -> color >= 0)
                ? List.of("submit") : List.of();
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.classic.action", "Invalid Mastermind action");
        if (action.startsWith("slot:")) selectedSlot = Integer.parseInt(action.substring(5));
        else if (action.startsWith("color:")) draft[selectedSlot] = Integer.parseInt(action.substring(6));
        else if (action.startsWith("next-color:") || action.startsWith("previous-color:")) {
            selectedSlot = Integer.parseInt(action.substring(action.indexOf(':') + 1));
            int current = draft[selectedSlot];
            draft[selectedSlot] = action.startsWith("next-color:") ? (current + 1) % 6
                    : Math.floorMod(Math.max(0, current) - 1, 6);
        } else if (setting) {
            secret = draft.clone();
            setting = false;
            clearDraft();
        } else {
            int[] feedback = feedback(secret, draft);
            guesses.add(new Guess(round, Arrays.stream(draft).boxed().toList(), feedback[0], feedback[1]));
            int attempts = (int) guesses.stream().filter(guess -> guess.round() == round).count();
            clearDraft();
            if (feedback[0] == 4 || attempts == 10) {
                if (players == 1) result = feedback[0] == 4 ? "winner:0" : "draw:unsolved";
                else {
                    scores[round] = feedback[0] == 4 ? attempts : 11;
                    if (round == 0) {
                        round = 1;
                        setting = true;
                        secret = null;
                    } else result = scores[0] == scores[1] ? "draw:equal-score"
                            : "winner:" + (scores[0] > scores[1] ? 0 : 1);
                }
            }
        }
    }

    private void clearDraft() { Arrays.fill(draft, -1); selectedSlot = 0; }

    private static int[] feedback(int[] code, int[] guess) {
        int exact = 0;
        int[] remainingCode = new int[6], remainingGuess = new int[6];
        for (int i = 0; i < 4; i++) {
            if (code[i] == guess[i]) exact++;
            else { remainingCode[code[i]]++; remainingGuess[guess[i]]++; }
        }
        int misplaced = 0;
        for (int i = 0; i < 6; i++) misplaced += Math.min(remainingCode[i], remainingGuess[i]);
        return new int[] {exact, misplaced};
    }

    public Map<String, String> publicInfo() {
        return Map.of("phase", setting ? "setting" : "guessing", "round", Integer.toString(round + 1),
                "turn", Integer.toString(currentPlayer()), "attempts", Long.toString(guesses.stream().filter(g -> g.round() == round).count()),
                "scores", scores().toString());
    }

    public Map<String, String> infoFor(int seat) {
        if (seat != currentPlayer() || seat < 0) return publicInfo();
        Map<String, String> info = new LinkedHashMap<>(publicInfo());
        info.put("slot", Integer.toString(selectedSlot));
        return Map.copyOf(info);
    }

    public String botAction(int seat, Random random) {
        if (legalActions(seat).isEmpty()) return null;
        int[] target = new int[4];
        if (setting) {
            for (int i = 0; i < 4; i++) if (draft[i] < 0)
                return selectedSlot == i ? "color:" + random.nextInt(6) : "slot:" + i;
            return "submit";
        }
        for (int candidate = 0; candidate < 1296; candidate++) {
            int value = candidate;
            for (int i = 0; i < 4; i++) { target[i] = value % 6; value /= 6; }
            boolean consistent = true;
            for (Guess previous : guesses) {
                if (previous.round() != round) continue;
                int[] f = feedback(target, previous.colors().stream().mapToInt(Integer::intValue).toArray());
                if (f[0] != previous.exact() || f[1] != previous.misplaced()) { consistent = false; break; }
            }
            if (consistent) break;
        }
        for (int i = 0; i < 4; i++) if (draft[i] != target[i])
            return selectedSlot == i ? "color:" + target[i] : "slot:" + i;
        return "submit";
    }
}

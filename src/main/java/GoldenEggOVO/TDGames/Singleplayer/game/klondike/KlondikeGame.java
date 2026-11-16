package GoldenEggOVO.TDGames.Singleplayer.game.klondike;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.cards.PlayingCard;

import java.util.*;

public final class KlondikeGame implements ClassicGame {
    public record VisibleCard(String id, String face, boolean faceUp) { }

    private final List<PlayingCard> stock, waste = new ArrayList<>();
    private final List<List<PlayingCard>> columns = new ArrayList<>(), foundations = new ArrayList<>();
    private final int[] hidden = new int[7];
    private final int drawCount;
    private int recycles, moves;

    public KlondikeGame(int players, long seed, Map<String, String> options) {
        if (players != 1) throw new IllegalArgumentException("Klondike requires one player");
        String draw = options.getOrDefault("draw", "1");
        if (!draw.equals("1") && !draw.equals("3")) throw new IllegalArgumentException("Klondike draw must be one or three");
        drawCount = Integer.parseInt(draw);
        stock = new ArrayList<>(PlayingCard.deck(new Random(seed), false));
        for (int col = 0; col < 7; col++) {
            List<PlayingCard> pile = new ArrayList<>();
            for (int i = 0; i <= col; i++) pile.add(stock.removeLast());
            columns.add(pile);
            hidden[col] = col;
        }
        for (int i = 0; i < 4; i++) foundations.add(new ArrayList<>());
    }

    public String id() { return "klondike"; }
    public int playerCount() { return 1; }
    public int currentPlayer() { return 0; }
    public boolean finished() { return foundations.stream().mapToInt(List::size).sum() == 52; }
    public String outcome() { return finished() ? "winner:0" : "ongoing"; }
    public int drawCount() { return drawCount; }

    public List<List<VisibleCard>> columns() {
        List<List<VisibleCard>> view = new ArrayList<>();
        for (int col = 0; col < 7; col++) {
            List<VisibleCard> cards = new ArrayList<>();
            for (int i = 0; i < columns.get(col).size(); i++) cards.add(new VisibleCard("t" + col + "_" + i,
                    i < hidden[col] ? "back" : columns.get(col).get(i).face(), i >= hidden[col]));
            view.add(List.copyOf(cards));
        }
        return List.copyOf(view);
    }

    public List<VisibleCard> wasteCards() {
        List<VisibleCard> cards = new ArrayList<>();
        for (int i = Math.max(0, waste.size() - drawCount); i < waste.size(); i++)
            cards.add(new VisibleCard(i == waste.size() - 1 ? "w" : "w" + i, waste.get(i).face(), true));
        return List.copyOf(cards);
    }

    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell("stock", 0, 0, stock.isEmpty() ? "" : "back", -1));
        cells.add(new Cell("w", 1, 0, waste.isEmpty() ? "" : waste.getLast().face(), 0));
        for (int i = 0; i < 4; i++) cells.add(new Cell("f" + i, i + 3, 0,
                foundations.get(i).isEmpty() ? "" : foundations.get(i).getLast().face(), 0));
        List<List<VisibleCard>> view = columns();
        for (int col = 0; col < 7; col++) {
            cells.add(new Cell("t" + col, col, 2, "", -1));
            for (int i = 0; i < view.get(col).size(); i++) {
                VisibleCard card = view.get(col).get(i);
                cells.add(new Cell(card.id(), col, i + 3, card.face(), card.faceUp() ? 0 : -1));
            }
        }
        return List.copyOf(cells);
    }

    private static int rank(PlayingCard card) { return card.rank() == 14 ? 1 : card.rank(); }
    private static boolean red(PlayingCard card) { return card.suit().equals("hearts") || card.suit().equals("diamonds"); }
    private static boolean follows(PlayingCard lower, PlayingCard upper) {
        return rank(lower) == rank(upper) + 1 && red(lower) != red(upper);
    }

    private List<PlayingCard> source(String id) {
        if (id.equals("w")) return waste.isEmpty() ? List.of() : List.of(waste.getLast());
        if (id.matches("f[0-3]")) {
            List<PlayingCard> pile = foundations.get(id.charAt(1) - '0');
            return pile.isEmpty() ? List.of() : List.of(pile.getLast());
        }
        if (!id.matches("t[0-6]_[0-9]+")) return List.of();
        int col = id.charAt(1) - '0', start;
        try { start = Integer.parseInt(id.substring(3)); } catch (NumberFormatException e) { return List.of(); }
        List<PlayingCard> pile = columns.get(col);
        if (start < hidden[col] || start >= pile.size()) return List.of();
        for (int i = start + 1; i < pile.size(); i++) if (!follows(pile.get(i - 1), pile.get(i))) return List.of();
        return List.copyOf(pile.subList(start, pile.size()));
    }

    private boolean canMove(String sourceId, String target, List<PlayingCard> cards) {
        if (cards.isEmpty() || sourceId.equals(target)) return false;
        PlayingCard bottom = cards.getFirst();
        if (target.matches("f[0-3]")) {
            if (cards.size() != 1 || sourceId.startsWith("f")) return false;
            int foundation = target.charAt(1) - '0';
            return bottom.suit().equals(PlayingCard.SUITS.get(foundation)) && rank(bottom) == foundations.get(foundation).size() + 1;
        }
        if (!target.matches("t[0-6]")) return false;
        int col = target.charAt(1) - '0';
        if (sourceId.startsWith("t" + col + "_")) return false;
        List<PlayingCard> pile = columns.get(col);
        return pile.isEmpty() ? rank(bottom) == 13 : follows(pile.getLast(), bottom);
    }

    public List<String> legalActions(int seat) {
        if (seat != 0 || finished()) return List.of();
        List<String> actions = new ArrayList<>();
        if (!stock.isEmpty()) actions.add("draw");
        else if (!waste.isEmpty()) actions.add("recycle");
        List<String> sources = new ArrayList<>();
        if (!waste.isEmpty()) sources.add("w");
        for (int i = 0; i < 4; i++) if (!foundations.get(i).isEmpty()) sources.add("f" + i);
        for (int col = 0; col < 7; col++) for (int i = hidden[col]; i < columns.get(col).size(); i++) sources.add("t" + col + "_" + i);
        for (String source : sources) {
            List<PlayingCard> cards = source(source);
            for (int i = 0; i < 7; i++) if (canMove(source, "t" + i, cards)) actions.add("move:" + source + ":t" + i);
            for (int i = 0; i < 4; i++) if (canMove(source, "f" + i, cards)) actions.add("move:" + source + ":f" + i);
        }
        return List.copyOf(actions);
    }

    public List<String> actionsForCell(int seat, String cellId) {
        List<String> actions = legalActions(seat);
        if ("stock".equals(cellId)) return actions.stream().filter(a -> a.equals("draw") || a.equals("recycle")).toList();
        if (cellId == null) return List.of();
        String column = cellId.matches("t[0-6]_[0-9]+") ? cellId.substring(0, 2) : cellId;
        return actions.stream().filter(a -> a.startsWith("move:" + cellId + ":") || a.endsWith(":" + column)).toList();
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.classic.action", "Invalid Klondike action");
        if (action.equals("draw")) {
            for (int i = 0; i < drawCount && !stock.isEmpty(); i++) waste.add(stock.removeLast());
        } else if (action.equals("recycle")) {
            while (!waste.isEmpty()) stock.add(waste.removeLast());
            recycles++;
        } else {
            String[] parts = action.split(":");
            List<PlayingCard> cards = source(parts[1]);
            if (parts[1].equals("w")) waste.removeLast();
            else if (parts[1].startsWith("f")) foundations.get(parts[1].charAt(1) - '0').removeLast();
            else {
                int col = parts[1].charAt(1) - '0';
                List<PlayingCard> pile = columns.get(col);
                pile.subList(pile.size() - cards.size(), pile.size()).clear();
                if (!pile.isEmpty() && hidden[col] == pile.size()) hidden[col]--;
            }
            if (parts[2].startsWith("f")) foundations.get(parts[2].charAt(1) - '0').addAll(cards);
            else columns.get(parts[2].charAt(1) - '0').addAll(cards);
        }
        moves++;
    }

    public Map<String, String> publicInfo() {
        return Map.of("stock", Integer.toString(stock.size()), "waste", Integer.toString(waste.size()),
                "foundations", Integer.toString(foundations.stream().mapToInt(List::size).sum()),
                "draw", Integer.toString(drawCount), "recycles", Integer.toString(recycles), "moves", Integer.toString(moves));
    }

    public String botAction(int seat, Random random) { return null; }
}

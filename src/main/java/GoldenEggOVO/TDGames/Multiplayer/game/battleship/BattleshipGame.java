package GoldenEggOVO.TDGames.Multiplayer.game.battleship;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.*;

public final class BattleshipGame implements ClassicGame {
    private static final int[] LENGTHS = {5, 4, 3, 3, 2};
    private static final String[] SHIPS = {"ship_carrier", "ship_battleship", "ship_cruiser", "ship_submarine", "ship_destroyer"};
    private final List<List<List<Integer>>> fleets = new ArrayList<>();
    private final boolean[][] shots = new boolean[2][100];
    private final boolean[] confirmed = new boolean[2], vertical = new boolean[2];
    private final int[] selected = new int[2];
    private final Random random;
    private int turn;
    private String result = "ongoing";

    public BattleshipGame(int players, long seed, Map<String, String> options) {
        if (players != 2) throw new IllegalArgumentException("Battleship requires two players");
        random = new Random(seed);
        for (int seat = 0; seat < 2; seat++) {
            List<List<Integer>> fleet = new ArrayList<>();
            for (int ship = 0; ship < 5; ship++) fleet.add(List.of());
            fleets.add(fleet);
        }
    }

    public String id() { return "battleship"; }
    public int playerCount() { return 2; }
    public int currentPlayer() { return setup() ? confirmed[0] ? 1 : 0 : turn; }
    public boolean finished() { return !result.equals("ongoing"); }
    public String outcome() { return result; }
    public boolean setup() { return !confirmed[0] || !confirmed[1]; }
    public boolean confirmed(int seat) { return seat >= 0 && seat < 2 && confirmed[seat]; }
    public List<Cell> cells() { return cellsFor(-1); }

    public List<Cell> cellsFor(int seat) {
        List<Cell> cells = new ArrayList<>(200);
        for (int board = 0; board < 2; board++) {
            int attacker = 1 - board;
            int[] shipsByCell = new int[100];
            Arrays.fill(shipsByCell, -1);
            boolean[] sunkShips = new boolean[LENGTHS.length];
            for (int ship = 0; ship < LENGTHS.length; ship++) {
                for (int index : fleets.get(board).get(ship)) shipsByCell[index] = ship;
                sunkShips[ship] = sunk(board, ship);
            }
            for (int i = 0; i < 100; i++) {
                int ship = shipsByCell[i];
                String piece = "water";
                if (shots[attacker][i]) piece = ship < 0 ? "miss" : sunkShips[ship] ? "sunk" : "hit";
                else if (seat == board && ship >= 0) piece = SHIPS[ship];
                cells.add(new Cell("b" + board + "_" + i, board * 11 + i % 10, i / 10, piece, board));
            }
        }
        return List.copyOf(cells);
    }

    private int shipAt(int seat, int index) {
        for (int ship = 0; ship < 5; ship++) if (fleets.get(seat).get(ship).contains(index)) return ship;
        return -1;
    }

    private boolean sunk(int seat, int ship) {
        List<Integer> positions = fleets.get(seat).get(ship);
        return !positions.isEmpty() && positions.stream().allMatch(index -> shots[1 - seat][index]);
    }

    private int remaining(int seat) {
        int count = 5;
        for (int ship = 0; ship < 5; ship++) if (sunk(seat, ship)) count--;
        return count;
    }

    private List<Integer> placement(int seat, int ship, int start, boolean isVertical) {
        int length = LENGTHS[ship];
        if (start < 0 || start >= 100 || (!isVertical && start % 10 + length > 10)
                || (isVertical && start / 10 + length > 10)) return List.of();
        List<Integer> cells = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            int index = start + i * (isVertical ? 10 : 1);
            int other = shipAt(seat, index);
            if (other >= 0 && other != ship) return List.of();
            cells.add(index);
        }
        return List.copyOf(cells);
    }

    public List<String> placementPreview(int seat, String cell) {
        if (seat < 0 || seat >= 2 || !setup() || confirmed[seat] || cell == null
                || !cell.matches("b" + seat + "_[0-9]{1,2}")) return List.of();
        int start = Integer.parseInt(cell.substring(3));
        List<String> preview = new ArrayList<>();
        for (int step = 0; step < LENGTHS[selected[seat]]; step++) {
            int x = start % 10 + (vertical[seat] ? 0 : step);
            int y = start / 10 + (vertical[seat] ? step : 0);
            if (x < 10 && y < 10) preview.add("b" + seat + "_" + (y * 10 + x));
        }
        return List.copyOf(preview);
    }

    public static int shipLength(int ship) { return LENGTHS[ship]; }

    public List<String> legalActions(int seat) {
        if (finished() || seat < 0 || seat >= 2) return List.of();
        List<String> actions = new ArrayList<>();
        if (setup()) {
            if (confirmed[seat]) return List.of();
            for (int ship = 0; ship < 5; ship++) actions.add("ship:" + ship);
            actions.add("rotate");
            actions.add("auto");
            if (!fleets.get(seat).get(selected[seat]).isEmpty()) actions.add("clear");
            for (int i = 0; i < 100; i++) if (!placement(seat, selected[seat], i, vertical[seat]).isEmpty())
                actions.add("place:b" + seat + "_" + i);
            if (fleets.get(seat).stream().allMatch(ship -> !ship.isEmpty())) actions.add("confirm");
        } else if (seat == turn) {
            for (int i = 0; i < 100; i++) if (!shots[seat][i]) actions.add("shoot:b" + (1 - seat) + "_" + i);
        }
        return List.copyOf(actions);
    }

    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.classic.action", "Invalid Battleship action");
        if (action.startsWith("ship:")) selected[seat] = Integer.parseInt(action.substring(5));
        else if (action.equals("rotate")) vertical[seat] = !vertical[seat];
        else if (action.equals("clear")) fleets.get(seat).set(selected[seat], List.of());
        else if (action.equals("auto")) autoPlace(seat);
        else if (action.equals("confirm")) confirmed[seat] = true;
        else if (action.startsWith("place:")) {
            int index = Integer.parseInt(action.substring(action.indexOf('_') + 1));
            fleets.get(seat).set(selected[seat], placement(seat, selected[seat], index, vertical[seat]));
        } else {
            int index = Integer.parseInt(action.substring(action.indexOf('_') + 1));
            shots[seat][index] = true;
            if (remaining(1 - seat) == 0) result = "winner:" + seat;
            else turn = 1 - turn;
        }
    }

    private void autoPlace(int seat) {
        List<List<Integer>> fleet = fleets.get(seat);
        for (int ship = 0; ship < 5; ship++) fleet.set(ship, List.of());
        for (int ship = 0; ship < 5; ship++) {
            List<List<Integer>> choices = new ArrayList<>();
            for (int i = 0; i < 100; i++) for (boolean direction : new boolean[] {false, true}) {
                List<Integer> positions = placement(seat, ship, i, direction);
                if (!positions.isEmpty()) choices.add(positions);
            }
            fleet.set(ship, choices.get(random.nextInt(choices.size())));
        }
    }

    public Map<String, String> publicInfo() {
        return Map.of("phase", setup() ? "setup" : "shooting", "turn", Integer.toString(currentPlayer()),
                "confirmed_0", Boolean.toString(confirmed[0]), "confirmed_1", Boolean.toString(confirmed[1]),
                "remaining_0", Integer.toString(remaining(0)), "remaining_1", Integer.toString(remaining(1)));
    }

    public Map<String, String> infoFor(int seat) {
        if (seat < 0 || seat > 1) return publicInfo();
        Map<String, String> info = new LinkedHashMap<>(publicInfo());
        info.put("ship", Integer.toString(selected[seat]));
        info.put("orientation", vertical[seat] ? "vertical" : "horizontal");
        info.put("placed", Long.toString(fleets.get(seat).stream().filter(ship -> !ship.isEmpty()).count()));
        return Map.copyOf(info);
    }

    public String botAction(int seat, Random random) {
        List<String> actions = legalActions(seat);
        if (actions.isEmpty()) return null;
        if (setup()) return actions.contains("confirm") ? "confirm" : "auto";
        Map<Integer, String> exposed = new HashMap<>();
        String prefix = "b" + (1 - seat) + "_";
        for (Cell cell : cellsFor(seat)) if (cell.id().startsWith(prefix))
            exposed.put(Integer.parseInt(cell.id().substring(prefix.length())), cell.piece());
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            if (!"hit".equals(exposed.get(i))) continue;
            for (int delta : new int[] {-10, 10, -1, 1}) {
                int next = i + delta;
                if (next < 0 || next >= 100 || Math.abs(delta) == 1 && i / 10 != next / 10) continue;
                String shot = "shoot:" + prefix + next;
                if (actions.contains(shot)) targets.add(shot);
            }
        }
        List<String> choices = targets.isEmpty() ? actions : targets;
        return choices.get(random.nextInt(choices.size()));
    }
}

package GoldenEggOVO.TDGames.Multiplayer.game.shogi;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Shared standard shogi and mini-shogi rules. Coordinates start at sente's left corner. */
public final class ShogiGame implements ClassicGame {
    private static final String[] SYMBOLS = {"", "P", "L", "N", "S", "G", "B", "R", "K"};
    private static final int[] VALUES = {0, 100, 250, 280, 400, 500, 700, 850, 0};
    private final boolean mini;
    private final int size;
    private final int[][] board;
    private final int[][] hands = new int[2][8];
    private final List<String> positions = new ArrayList<>();
    private final List<Ply> plies = new ArrayList<>();
    private int turn;
    private String result = "ongoing";
    private String reason = "";
    private Move pending;
    private boolean impasseOffered;
    private String lastMove = "";

    private record Move(int fromX, int fromY, int x, int y, int drop) {
        String action() {
            return drop == 0 ? "move:" + square(fromX, fromY) + ":" + square(x, y)
                    : "drop:" + SYMBOLS[drop] + ":" + square(x, y);
        }
    }
    private record Ply(int seat, boolean check) { }

    public ShogiGame(int players, long seed, Map<String, String> options) {
        this(players, seed, options, false);
    }

    public ShogiGame(int players, long seed, Map<String, String> options, boolean mini) {
        if (players != 2) throw new IllegalArgumentException("Shogi requires two players");
        this.mini = mini;
        size = mini ? 5 : 9;
        board = new int[size][size];
        int[] back = mini ? new int[]{8, 5, 4, 6, 7} : new int[]{2, 3, 4, 5, 8, 5, 4, 3, 2};
        for (int x = 0; x < size; x++) {
            board[0][x] = back[x];
            board[size - 1][size - 1 - x] = -back[x];
            if (!mini) {
                board[2][x] = 1;
                board[size - 3][x] = -1;
            }
        }
        if (mini) {
            board[1][0] = 1;
            board[size - 2][size - 1] = -1;
        } else {
            board[1][1] = 6;
            board[1][7] = 7;
            board[7][7] = -6;
            board[7][1] = -7;
        }
        positions.add(positionKey());
    }

    @Override public String id() { return mini ? "mini-shogi" : "shogi"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return impasseOffered ? 1 - turn : turn; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    public int boardSize() { return size; }
    public boolean pendingPromotion() { return pending != null; }

    public Map<String, Integer> hand(int seat) {
        if (seat < 0 || seat > 1) return Map.of();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int p = 1; p < 8; p++) if (hands[seat][p] > 0) out.put(SYMBOLS[p], hands[seat][p]);
        return Map.copyOf(out);
    }

    @Override public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(size * size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int piece = board[y][x];
            out.add(new Cell(square(x, y), x, y, symbol(piece), owner(piece)));
        }
        return List.copyOf(out);
    }

    @Override public Map<String, String> publicInfo() {
        return Map.of("phase", finished() ? "finished" : impasseOffered ? "impasse" : pending != null ? "promotion" : "move",
                "turn", Integer.toString(currentPlayer()), "hand0", hand(0).toString(), "hand1", hand(1).toString(),
                "check", Boolean.toString(inCheck(turn)), "lastMove", lastMove, "reason", reason,
                "boardSize", Integer.toString(size), "outcome", result);
    }

    @Override public List<String> legalActions(int seat) {
        if (finished() || seat != currentPlayer()) return List.of();
        if (impasseOffered) return List.of("impasse:accept", "impasse:reject");
        if (pending != null) return List.of("promote:yes", "promote:no");
        List<String> out = new ArrayList<>();
        for (Move move : moves(turn, true)) out.add(move.action());
        if (canImpasse()) out.add("impasse:offer");
        return List.copyOf(out);
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream().filter(action -> {
            String[] parts = action.split(":");
            return parts.length == 3 && (parts[2].equals(cellId)
                    || parts[0].equals("move") && parts[1].equals(cellId)
                    || parts[0].equals("drop") && cellId.equals("h" + seat + parts[1]));
        }).toList();
    }

    @Override public List<String> controlActions(int seat) {
        return legalActions(seat).stream().filter(a -> a.startsWith("promote:") || a.startsWith("impasse:")).toList();
    }

    @Override public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null)
            throw new RuleViolation("error.shogi.actor", "Invalid shogi actor or phase");
        if (impasseOffered) {
            if (action.equals("impasse:reject")) {
                impasseOffered = false;
                return;
            }
            if (!action.equals("impasse:accept")) throw new RuleViolation("error.shogi.impasse-response", "Impasse response required");
            impasseOffered = false;
            int first = impassePoints(0), second = impassePoints(1);
            result = first < 24 ? "winner:1" : second < 24 ? "winner:0" : "draw:impasse";
            reason = "impasse";
            return;
        }
        if (pending != null) {
            if (!action.equals("promote:yes") && !action.equals("promote:no"))
                throw new RuleViolation("error.shogi.promotion-required", "Promotion response required");
            Move move = pending;
            pending = null;
            commit(move, action.equals("promote:yes"));
            return;
        }
        if (action.equals("impasse:offer")) {
            if (!canImpasse()) throw new RuleViolation("error.shogi.impasse-unavailable", "Both safe kings must enter the opposing camp");
            impasseOffered = true;
            return;
        }
        Move selected = null;
        for (Move move : moves(turn, true)) if (move.action().equals(action)) { selected = move; break; }
        if (selected == null) throw rejected(action);
        if (selected.drop == 0) {
            int piece = Math.abs(board[selected.fromY][selected.fromX]);
            if (canPromote(piece) && (inZone(turn, selected.fromY) || inZone(turn, selected.y))) {
                if (!mustPromote(piece, turn, selected.y)) {
                    pending = selected;
                    return;
                }
                commit(selected, true);
                return;
            }
        }
        commit(selected, false);
    }

    private RuleViolation rejected(String action) {
        String[] parts = action.split(":", -1);
        if (parts.length == 3 && validSquare(parts[2])) {
            int x = parts[2].charAt(0) - 'a', y = parts[2].charAt(1) - '1';
            if (parts[0].equals("drop") && board[y][x] == 0) {
                int p = Arrays.asList(SYMBOLS).indexOf(parts[1]);
                if (p > 0 && p < 8 && hands[turn][p] > 0) {
                    if (mustPromote(p, turn, y)) return new RuleViolation("error.shogi.dead-drop", "The dropped piece would have no future move");
                    if (p == 1 && hasPawn(turn, x)) return new RuleViolation("error.shogi.double-pawn", "An unpromoted pawn already occupies this file");
                    Move drop = new Move(-1, -1, x, y, p);
                    if (!safe(drop, turn, false)) return new RuleViolation("error.shogi.king-unsafe", "The king would remain in check");
                    if (p == 1 && !safe(drop, turn, true)) return new RuleViolation("error.shogi.pawn-drop-mate", "A pawn drop cannot give immediate checkmate");
                }
            } else if (parts[0].equals("move") && validSquare(parts[1])) {
                int fx = parts[1].charAt(0) - 'a', fy = parts[1].charAt(1) - '1';
                if (owner(board[fy][fx]) == turn && owner(board[y][x]) != turn
                        && Math.abs(board[y][x]) != 8 && reaches(fx, fy, x, y, board[fy][fx])
                        && !safe(new Move(fx, fy, x, y, 0), turn, true))
                    return new RuleViolation("error.shogi.king-unsafe", "The king would remain in check");
            }
        }
        return new RuleViolation("error.shogi.invalid-move", "Illegal shogi move or drop");
    }

    private boolean validSquare(String cell) {
        return cell.length() == 2 && cell.charAt(0) >= 'a' && cell.charAt(0) < 'a' + size
                && cell.charAt(1) >= '1' && cell.charAt(1) < '1' + size;
    }

    private void commit(Move move, boolean promote) {
        int seat = turn;
        int captured = Math.abs(board[move.y][move.x]);
        int piece;
        if (move.drop != 0) {
            hands[seat][move.drop]--;
            piece = move.drop * sign(seat);
        } else {
            piece = board[move.fromY][move.fromX];
            board[move.fromY][move.fromX] = 0;
            if (promote) piece = (Math.abs(piece) + 8) * sign(seat);
        }
        if (captured != 0) hands[seat][base(captured)]++;
        board[move.y][move.x] = piece;
        lastMove = move.action() + (promote ? ":promoted" : "");
        turn = 1 - turn;
        plies.add(new Ply(seat, inCheck(turn)));
        positions.add(positionKey());
        adjudicateRepetition();
        if (!finished() && moves(turn, true).isEmpty()) {
            result = "winner:" + seat;
            reason = inCheck(turn) ? "checkmate" : "no-legal-move";
        }
    }

    private List<Move> moves(int seat, boolean enforcePawnMate) {
        List<Move> out = new ArrayList<>();
        for (int fy = 0; fy < size; fy++) for (int fx = 0; fx < size; fx++) {
            int piece = board[fy][fx];
            if (owner(piece) != seat) continue;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                if (owner(board[y][x]) == seat || Math.abs(board[y][x]) == 8 || !reaches(fx, fy, x, y, piece)) continue;
                Move move = new Move(fx, fy, x, y, 0);
                if (safe(move, seat, enforcePawnMate)) out.add(move);
            }
        }
        for (int p = 1; p < 8; p++) {
            if (hands[seat][p] == 0) continue;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                if (board[y][x] != 0 || mustPromote(p, seat, y) || p == 1 && hasPawn(seat, x)) continue;
                Move move = new Move(-1, -1, x, y, p);
                if (safe(move, seat, enforcePawnMate)) out.add(move);
            }
        }
        return out;
    }

    private boolean safe(Move move, int seat, boolean enforcePawnMate) {
        int captured = board[move.y][move.x];
        int piece = move.drop != 0 ? move.drop * sign(seat) : board[move.fromY][move.fromX];
        if (move.drop == 0) board[move.fromY][move.fromX] = 0;
        board[move.y][move.x] = piece;
        boolean valid = !inCheck(seat);
        if (valid && enforcePawnMate && move.drop == 1 && pawnChecksKing(move.x, move.y, seat))
            valid = !moves(1 - seat, false).isEmpty();
        board[move.y][move.x] = captured;
        if (move.drop == 0) board[move.fromY][move.fromX] = piece;
        return valid;
    }

    private boolean pawnChecksKing(int x, int y, int seat) {
        int ahead = y + sign(seat);
        return ahead >= 0 && ahead < size && board[ahead][x] == -8 * sign(seat);
    }

    public boolean inCheck(int seat) {
        if (seat < 0 || seat > 1) return false;
        int kx = -1, ky = -1;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            if (board[y][x] == 8 * sign(seat)) { kx = x; ky = y; }
        if (kx == -1) return true;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            if (owner(board[y][x]) == 1 - seat && reaches(x, y, kx, ky, board[y][x])) return true;
        return false;
    }

    private boolean reaches(int fx, int fy, int x, int y, int piece) {
        int dx = x - fx, dy = y - fy;
        if (dx == 0 && dy == 0) return false;
        int ax = Math.abs(dx), ay = Math.abs(dy), forward = dy * Integer.signum(piece), p = Math.abs(piece);
        if (p == 8) return ax <= 1 && ay <= 1;
        if (p == 5 || p >= 9 && p <= 12)
            return forward == 1 && ax <= 1 || forward == 0 && ax == 1 || forward == -1 && dx == 0;
        if (p == 1) return dx == 0 && forward == 1;
        if (p == 3) return ax == 1 && forward == 2;
        if (p == 4) return forward == 1 && ax <= 1 || forward == -1 && ax == 1;
        if (p == 14 && ax + ay == 1 || p == 15 && ax == 1 && ay == 1) return true;
        boolean sliding = p == 2 && dx == 0 && forward > 0
                || (p == 6 || p == 14) && ax == ay
                || (p == 7 || p == 15) && (dx == 0 || dy == 0);
        if (!sliding) return false;
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        for (int bx = fx + sx, by = fy + sy; bx != x || by != y; bx += sx, by += sy)
            if (board[by][bx] != 0) return false;
        return true;
    }

    private boolean hasPawn(int seat, int x) {
        for (int y = 0; y < size; y++) if (board[y][x] == sign(seat)) return true;
        return false;
    }

    private boolean inZone(int seat, int y) { return seat == 0 ? y >= size - (mini ? 1 : 3) : y < (mini ? 1 : 3); }
    private boolean mustPromote(int piece, int seat, int y) {
        int distance = seat == 0 ? size - 1 - y : y;
        return (piece == 1 || piece == 2) && distance == 0 || piece == 3 && distance <= 1;
    }
    private static boolean canPromote(int p) { return p >= 1 && p <= 4 || p == 6 || p == 7; }
    private static int sign(int seat) { return seat == 0 ? 1 : -1; }
    private static int owner(int piece) { return piece == 0 ? -1 : piece > 0 ? 0 : 1; }
    private static int base(int p) { return p > 8 ? p - 8 : p; }
    private static String square(int x, int y) { return "" + (char) ('a' + x) + (y + 1); }
    private static String symbol(int piece) {
        int p = Math.abs(piece);
        return p == 0 ? "" : (p > 8 ? "+" : "") + SYMBOLS[base(p)];
    }

    private String positionKey() { return Arrays.deepToString(board) + Arrays.deepToString(hands) + turn; }

    private void adjudicateRepetition() {
        String current = positions.getLast();
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) if (positions.get(i).equals(current)) matching.add(i);
        if (matching.size() < 4) return;
        int start = matching.get(matching.size() - 4);
        for (int seat = 0; seat < 2; seat++) {
            boolean checkedEveryTime = true, moved = false;
            for (int i = start; i < plies.size(); i++) if (plies.get(i).seat == seat) {
                moved = true;
                if (!plies.get(i).check) { checkedEveryTime = false; break; }
            }
            if (moved && checkedEveryTime) {
                result = "winner:" + (1 - seat);
                reason = "perpetual-check";
                return;
            }
        }
        result = mini ? "winner:1" : "draw:repetition";
        reason = "repetition";
    }

    private boolean canImpasse() {
        if (mini || inCheck(0) || inCheck(1)) return false;
        boolean first = false, second = false;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (board[y][x] == 8 && inZone(0, y)) first = true;
            if (board[y][x] == -8 && inZone(1, y)) second = true;
        }
        return first && second;
    }

    private int impassePoints(int seat) {
        int value = 0;
        for (int p = 1; p < 8; p++) value += hands[seat][p] * (p == 6 || p == 7 ? 5 : 1);
        for (int[] row : board) for (int piece : row) if (owner(piece) == seat && Math.abs(piece) != 8) {
            int p = base(Math.abs(piece));
            value += p == 6 || p == 7 ? 5 : 1;
        }
        return value;
    }

    @Override public String botAction(int seat, Random random) {
        if (finished() || seat != currentPlayer()) return null;
        if (pending != null) return "promote:yes";
        if (impasseOffered) return impassePoints(seat) >= 24 ? "impasse:accept" : "impasse:reject";
        List<Move> legal = moves(seat, true);
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move move : legal) {
            int piece = move.drop != 0 ? move.drop * sign(seat) : board[move.fromY][move.fromX];
            int captured = board[move.y][move.x];
            int score = VALUES[base(Math.abs(captured))] * 2 + random.nextInt(20);
            if (move.drop == 0) {
                score += (move.y - move.fromY) * sign(seat);
                if (canPromote(Math.abs(piece)) && (inZone(seat, move.y) || inZone(seat, move.fromY))) score += 50;
                board[move.fromY][move.fromX] = 0;
            } else score -= 10;
            board[move.y][move.x] = piece;
            if (inCheck(1 - seat)) score += 30;
            board[move.y][move.x] = captured;
            if (move.drop == 0) board[move.fromY][move.fromX] = piece;
            if (score > bestScore) { bestScore = score; best = move; }
        }
        return best == null ? null : best.action();
    }
}

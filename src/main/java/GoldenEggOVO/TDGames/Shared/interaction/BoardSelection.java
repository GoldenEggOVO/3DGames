package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import java.util.List;
import java.util.UUID;

public record BoardSelection(UUID room, long revision, String source, List<String> actions) {
    public BoardSelection {
        actions = List.copyOf(actions);
    }

    public boolean current(GameSession session) {
        return room.equals(session.id) && revision == session.revision();
    }

    public static List<String> sourceActions(BoardGame board, int seat, String source) {
        if (seat < 0 || source == null) {
            return List.of();
        }
        Cell cell =
                board.cells().stream()
                        .filter(c -> c.id().equals(source) && c.owner() == seat)
                        .findFirst()
                        .orElse(null);
        if (cell == null) {
            return List.of();
        }
        return board.actionsForCell(seat, source).stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            if (parts.length < 3 || !parts[0].equals("move")) {
                                return false;
                            }
                            if (!board.id().equals("ludo")) {
                                return parts[1].equals(source);
                            }
                            try {
                                int pawn = Integer.parseInt(parts[1]);
                                return pawn / 4 == seat
                                        && cell.piece().contains(String.valueOf(pawn % 4 + 1));
                            } catch (NumberFormatException ex) {
                                return false;
                            }
                        })
                .toList();
    }

    public static List<String> destinationActions(BoardSelection pick, String destination) {
        if (pick == null || destination == null) {
            return List.of();
        }
        return pick.actions().stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            return parts.length >= 3 && parts[2].equals(destination);
                        })
                .toList();
    }
}

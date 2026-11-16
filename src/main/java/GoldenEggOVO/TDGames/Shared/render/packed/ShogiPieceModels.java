package GoldenEggOVO.TDGames.Shared.render.packed;

import GoldenEggOVO.TDGames.Shared.game.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShogiPieceModels {
    private static final Map<String, String> PIECES = Map.ofEntries(
            Map.entry("P", "pawn"), Map.entry("L", "lance"), Map.entry("N", "knight"),
            Map.entry("S", "silver"), Map.entry("G", "gold"), Map.entry("B", "bishop"),
            Map.entry("R", "rook"), Map.entry("K", "king"), Map.entry("+P", "promoted_pawn"),
            Map.entry("+L", "promoted_lance"), Map.entry("+N", "promoted_knight"),
            Map.entry("+S", "promoted_silver"), Map.entry("+B", "promoted_bishop"), Map.entry("+R", "promoted_rook"));

    private ShogiPieceModels() { }

    public static String piece(Cell cell) {
        String code = cell.piece().split("_", 2)[0];
        String name = code.equals("K") && cell.owner() == 1 ? "jeweled_king" : PIECES.get(code);
        if (name == null) throw new IllegalArgumentException("Unknown shogi piece: " + code);
        return "shogi_piece_" + name;
    }

    public static List<String> ids() {
        List<String> names = new ArrayList<>(PIECES.values());
        names.add("jeweled_king");
        return names.stream().sorted().map(name -> "shogi_piece_" + name).toList();
    }
}

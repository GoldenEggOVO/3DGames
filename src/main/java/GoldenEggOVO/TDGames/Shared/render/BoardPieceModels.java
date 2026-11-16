package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Core.text.GameSymbols;
import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Block;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BoardPieceModels {
    public static List<Block> piece(String kind, Cell cell, Map<String, String> publicInfo) {
        int owner = cell.owner();
        String side = owner == 0 ? "black" : "white";
        String id = switch (kind) {
            case "gomoku", "go", "go9", "go13", "reversi" -> "shared/stones/" + side;
            case "draughts" -> "draughts/" + side + (cell.piece().equals(GameSymbols.KING) ? "_king" : "_piece");
            case "xiangqi" -> "xiangqi/piece";
            case "checkers", "ludo" -> (kind.equals("checkers") ? "chinese_checkers" : kind) + "/"
                    + PieceColors.name(publicInfo, owner) + "_piece";
            case "chess" -> "chess/pieces/" + (owner == 0 ? "white" : "black") + "_" + switch (cell.piece()) {
                case GameSymbols.PAWN -> "pawn";
                case GameSymbols.ROOK -> "rook";
                case GameSymbols.HORSE -> "knight";
                case GameSymbols.ELEPHANT -> "bishop";
                case GameSymbols.QUEEN -> "queen";
                case GameSymbols.KING -> "king";
                default -> throw new IllegalArgumentException("Unknown chess piece " + cell.piece());
            };
            default -> throw new IllegalArgumentException(kind);
        };
        var blocks = ModelRepository.require("tabletop/" + id).blocks();
        if (List.of("gomoku", "go", "go9", "go13", "reversi").contains(kind) && cell.piece().contains("×")) {
            var marked = new ArrayList<>(blocks);
            marked.addAll(deadStoneMarks());
            return List.copyOf(marked);
        }
        return blocks;
    }

    public static List<Block> deadStoneMarks() { return ModelRepository.require("tabletop/go/dead_marks").blocks(); }
    static List<Block> connectFour(int owner) {
        return ModelRepository.require("tabletop/connect_four/" + (owner == 0 ? "red" : "yellow") + "_piece").blocks();
    }
    public static List<Block> reversi() { return ModelRepository.require("tabletop/reversi/piece").blocks(); }
    private BoardPieceModels() {}
}

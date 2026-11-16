package GoldenEggOVO.TDGames.Shared.render.packed;

import GoldenEggOVO.TDGames.Shared.render.PieceColors;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Core.text.GameSymbols;

import java.util.*;

public final class BoardModelKeys {
    static final List<String> KINDS =
            List.of(
                    "chess", "connectfour", "xiangqi", "gomoku", "go",
                    "go9", "go13", "reversi", "draughts", "checkers", "ludo");
    private static final Map<String, String> CHESS =
            Map.of(
                    GameSymbols.PAWN, "pawn",
                    GameSymbols.HORSE, "knight",
                    GameSymbols.ELEPHANT, "bishop",
                    GameSymbols.ROOK, "rook",
                    GameSymbols.QUEEN, "queen",
                    GameSymbols.KING, "king");

    private BoardModelKeys() {}

    public static boolean supported(String kind) {
        return KINDS.contains(kind);
    }

    public static String table(String kind) {
        if (!supported(kind)) throw new IllegalArgumentException("Unsupported packed board: " + kind);
        return "board_" + kind;
    }

    public static String piece(String kind, Cell cell, Map<String, String> info) {
        String side = cell.owner() == 0 ? "white" : "black";
        return switch (kind) {
            case "chess" -> "chess_" + side + "_" + Objects.requireNonNull(CHESS.get(cell.piece()));
            case "connectfour" -> "connectfour_" + (cell.owner() == 0 ? "red" : "yellow");
            case "xiangqi" ->
                    "xiangqi_" + (cell.owner() == 0 ? "red" : "black") + "_" + xiangqi(cell.piece());
            case "gomoku", "go", "go9", "go13" ->
                    "stone_" + (cell.owner() == 0 ? "black" : "white")
                            + (cell.piece().contains("×") ? "_dead" : "");
            case "reversi" -> "reversi_disc";
            case "draughts" -> "draught_" + (cell.owner() == 0 ? "black" : "white")
                    + (cell.piece().equals(GameSymbols.KING) ? "_king" : "");
            case "checkers", "ludo" ->
                    (kind.equals("ludo") ? "ludo_" : "peg_")
                            + PieceColors.name(info, cell.owner());
            default -> throw new IllegalArgumentException("Unsupported packed piece: " + kind);
        };
    }

    private static String xiangqi(String glyph) {
        if (glyph.equals(GameSymbols.RED_GENERAL) || glyph.equals(GameSymbols.BLACK_GENERAL))
            return "general";
        if (glyph.equals(GameSymbols.RED_ADVISOR) || glyph.equals(GameSymbols.BLACK_ADVISOR))
            return "advisor";
        if (glyph.equals(GameSymbols.RED_ELEPHANT) || glyph.equals(GameSymbols.ELEPHANT))
            return "elephant";
        if (glyph.equals(GameSymbols.HORSE) || glyph.equals(GameSymbols.TRADITIONAL_HORSE))
            return "horse";
        if (glyph.equals(GameSymbols.ROOK)) return "rook";
        if (glyph.equals(GameSymbols.RED_CANNON) || glyph.equals(GameSymbols.BLACK_CANNON))
            return "cannon";
        if (glyph.equals(GameSymbols.PAWN) || glyph.equals(GameSymbols.BLACK_PAWN)) return "pawn";
        throw new IllegalArgumentException("Unknown Xiangqi piece: " + glyph);
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.addAll(ShogiPieceModels.ids());
        GoldenEggOVO.TDGames.Shared.game.GameCatalog.CLASSIC_IDS
                .forEach(kind -> ids.add("classic_" + kind.replace('-', '_') + "_board"));
        ids.addAll(List.of("classic_hex_piece_red", "classic_hex_piece_blue"));
        for (int color = 0; color < 6; color++) ids.add("classic_mastermind_peg_" + color);
        KINDS.forEach(kind -> ids.add(table(kind)));
        for (String side : List.of("white", "black")) {
            CHESS.values().forEach(piece -> ids.add("chess_" + side + "_" + piece));
            ids.add("stone_" + side);
            ids.add("stone_" + side + "_dead");
            ids.add("draught_" + side);
            ids.add("draught_" + side + "_king");
        }
        for (String side : List.of("red", "black"))
            for (String piece :
                    List.of("general", "advisor", "elephant", "horse", "rook", "cannon", "pawn"))
                ids.add("xiangqi_" + side + "_" + piece);
        for (String color : PieceColors.NAMES) {
            ids.add("peg_" + color);
            ids.add("ludo_" + color);
        }
        ids.addAll(List.of("connectfour_red", "connectfour_yellow", "reversi_disc"));
        for (String suit : List.of("spades", "hearts", "diamonds", "clubs"))
            for (String rank :
                    List.of(
                            "ace", "2", "3", "4", "5", "6", "7",
                            "8", "9", "10", "jack", "queen", "king"))
                ids.add("playing_" + suit + "_" + rank);
        ids.addAll(
                List.of(
                        "playing_joker_small", "playing_joker_big", "playing_back",
                        "doudizhu_table", "liars_bar_table", "texas_holdem_table",
                        "poker_dealer"));
        return List.copyOf(ids);
    }
}

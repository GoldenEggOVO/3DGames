package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import net.kyori.adventure.text.Component;

import java.util.*;

public final class ClassicText {
    private ClassicText() {}

    public static Component action(String kind, String action) {
        String[] parts = action.split(":");
        if (kind.equals("monopoly") && parts[0].equals("trade"))
            return Language.component("monopoly.trade.with", "seat", Integer.parseInt(parts[1]) + 1);
        if (parts.length == 1 && Language.contains("classic.action." + kind + "." + action))
            return Language.component("classic.action." + kind + "." + action);
        Component value = Component.empty();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) value = value.append(Component.text(" → "));
            String part = parts[i];
            Component displayed = Language.contains("classic.value." + part) ? Language.component("classic.value." + part) : Component.text(part);
            if (parts[0].equals("color")) displayed = Language.component("classic.color." + part);
            if (parts[0].equals("slot") || parts[0].equals("next-color") || parts[0].equals("previous-color"))
                displayed = Component.text(Integer.parseInt(part) + 1);
            if (parts[0].equals("ship")) displayed = Language.component("classic.cell.battleship.ship_"
                    + List.of("carrier", "battleship", "cruiser", "submarine", "destroyer").get(Integer.parseInt(part)));
            if (kind.equals("monopoly") && part.matches("p[0-9]+")) displayed = property(Integer.parseInt(part.substring(1)));
            value = value.append(displayed);
        }
        String key = "classic.action." + parts[0];
        return Language.component(key, "value", value);
    }

    public static Component status(ClassicGame game, int seat) {
        if (game instanceof MonopolyGame monopoly) return MonopolyText.status(monopoly, seat);
        Map<String, String> info = game.infoFor(seat);
        Component result = Component.empty();
        for (String key : List.of("phase", "round", "attempts", "score", "scores", "moves", "pairs", "mistakes",
                "stock", "foundations", "dice", "cube", "balance", "debt")) {
            String value = info.get(key);
            if (value == null || value.isBlank()) continue;
            if (!result.equals(Component.empty())) result = result.append(Component.newline());
            Component displayed = key.equals("phase") ? Language.component("classic.phase." + value) : Component.text(value);
            result = result.append(Language.component("classic.info." + key, "value", displayed));
        }
        for (int player = 0; player < game.playerCount(); player++) {
            String score = info.get("score." + player);
            if (score == null) continue;
            if (!result.equals(Component.empty())) result = result.append(Component.newline());
            result = result.append(Language.component("classic.info.seat-score", "seat", player + 1, "value", score));
        }
        if (game.id().equals("mastermind") && info.containsKey("slot"))
            result = result.append(Component.newline()).append(Language.component("mastermind.selected-slot", "slot", Integer.parseInt(info.get("slot")) + 1));
        if (game.id().equals("battleship") && "setup".equals(info.get("phase")) && info.containsKey("ship"))
            result = result.append(Component.newline()).append(Language.component("battleship.setup-status", "ship",
                    Language.component("classic.cell.battleship.ship_" + List.of("carrier", "battleship", "cruiser", "submarine", "destroyer").get(Integer.parseInt(info.get("ship")))),
                    "orientation", Language.component("battleship.orientation." + info.get("orientation")), "placed", info.get("placed")));
        return result;
    }

    public static Component property(int index) { return MonopolyText.property(index); }

    public static Component cell(ClassicGame game, Cell cell) {
        if ((game.id().equals("shogi") || game.id().equals("mini-shogi"))
                && cell.owner() >= 0 && cell.piece().matches("\\+?[PLNSGBRK]")) {
            boolean promoted = cell.piece().startsWith("+");
            String piece = promoted ? cell.piece().substring(1) : cell.piece();
            return Language.component("shogi.hover", "side", Language.component("shogi.side." + cell.owner()),
                    "piece", Language.component("shogi.piece." + piece.toLowerCase(java.util.Locale.ROOT)),
                    "state", promoted ? Language.component("shogi.promoted") : Component.empty());
        }
        if (game instanceof MonopolyGame monopoly) return MonopolyText.hover(monopoly, Integer.parseInt(cell.id().substring(1)));
        if (game.id().equals("mastermind") && cell.id().startsWith("feedback")) {
            String[] feedback = cell.piece().split("_");
            if (feedback.length == 2) return Language.component("mastermind.feedback", "exact", feedback[0], "misplaced", feedback[1]);
        }
        if (game.id().equals("battleship")) {
            int index = Integer.parseInt(cell.id().substring(cell.id().indexOf('_') + 1));
            return Language.component("battleship.cell", "board", Character.digit(cell.id().charAt(1), 10) + 1,
                    "coordinate", String.valueOf((char) ('A' + index % 10)) + (index / 10 + 1),
                    "state", Language.component("classic.cell.battleship." + cell.piece()));
        }
        return Component.empty();
    }

    public static Component tradeSummary(MonopolyGame game) { return MonopolyText.tradeSummary(game); }
}

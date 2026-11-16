package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Shared.game.Cell;

import GoldenEggOVO.TDGames.Core.text.RuleMessage;

import net.kyori.adventure.text.Component;

import java.util.*;

public final class BoardMessages {
    public static Component render(RuleMessage message) {
        List<Object> parameters = new ArrayList<>();
        message.parameters()
                .forEach(
                        (key, value) -> {
                            parameters.add(key);
                            if (key.equals("piece"))
                                value = Component.text(Language.glyph(String.valueOf(value)));
                            if (value instanceof Boolean confirmed)
                                value =
                                        Language.component(
                                                confirmed
                                                        ? "board.confirmed"
                                                        : "board.unconfirmed");
                            parameters.add(value);
                        });
        return Language.component(message.key(), parameters.toArray());
    }

    public static Component opening(String kind) {
        String key = switch (kind) {
            case "2048" -> "hint.2048.enter";
            case "mastermind" -> "chat.start.mastermind";
            case "battleship" -> "hint.battleship.controls";
            case "monopoly" -> "monopoly.hint.roll";
            case "memory" -> "chat.start.memory";
            default -> "chat.start";
        };
        return Language.component(key);
    }

    public static String coordinate(String kind, Cell cell) {
        if (kind.equals("ludo")) {
            if (cell.id().startsWith("sk")) {
                return MessageText.plain(
                        Language.component(
                                "board.track",
                                "number",
                                Integer.parseInt(cell.id().substring(2)) + 1));
            }
            if (cell.id().startsWith("ld")) {
                return MessageText.plain(
                        Language.component(
                                "board.home-lane",
                                "number",
                                Character.digit(cell.id().charAt(4), 10) + 1));
            }
            return MessageText.plain(
                    Language.component(cell.id().startsWith("go") ? "board.finish" : "board.yard"));
        }
        if (kind.equals("chess")) {
            return cell.id().toUpperCase(Locale.ROOT);
        }
        if (Set.of("gomoku", "xiangqi", "draughts", "reversi", "go", "go9", "go13")
                .contains(kind)) {
            return String.valueOf((char) ('A' + cell.x())) + (cell.y() + 1);
        }
        if (kind.equals("yacht")) {
            return MessageText.plain(
                    Language.component("board.die", "number", cell.x() / 2 + 1));
        }
        return cell.id();
    }

    private BoardMessages() {}
}

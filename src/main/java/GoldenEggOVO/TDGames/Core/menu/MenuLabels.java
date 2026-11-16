package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Core.text.MessageText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MenuLabels {
    private MenuLabels() {}

    public static Component caption(MenuView.Button button) {
        var result = Component.text("[" + name(button) + "]");
        if (button.key().equals("back")) return result.color(NamedTextColor.YELLOW);
        if (button.key().equals("close")) return result.color(NamedTextColor.GRAY);
        return result.style(button.text().style());
    }

    private static String name(MenuView.Button button) {
        String text = MessageText.plain(button.text()).strip();
        return text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1).strip() : text;
    }

}

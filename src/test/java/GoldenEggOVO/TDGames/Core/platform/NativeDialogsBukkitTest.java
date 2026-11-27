package GoldenEggOVO.TDGames.Core.platform;

import GoldenEggOVO.TDGames.Core.menu.MenuView;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeDialogsBukkitTest {
    @Test
    void nativeFormatPreservesHeaderInputActionsAndExit() {
        var header = new MenuView.Button(Component.text("Settings", NamedTextColor.AQUA),
                Component.text("Configure games"), 350, "settings-action", "settings");
        var choice = new MenuView.Button(Component.text("Play"), null, 330, "play-action", "play");
        var back = new MenuView.Button(Component.text("Back"), null, 330, "back-action", "back");
        var exit = new MenuView.Button(Component.text("Close"), null, 330, "close-action", "close");
        var view = new MenuView(Component.text("Games"),
                List.of(new MenuView.Body(Component.empty(), 370), new MenuView.Body(Component.text("Choose"), 370)),
                List.of(new MenuView.Input("amount", Component.text("Amount"), "10", 16)),
                List.of(header, choice, back), exit, 2, "catalog");
        UUID token = UUID.randomUUID();
        var json = NativeDialogsBukkit.json(view, token);
        assertEquals("minecraft:multi_action", json.get("type").getAsString());
        assertEquals("close", json.get("after_action").getAsString());
        assertFalse(json.get("pause").getAsBoolean());
        assertEquals(2, json.get("columns").getAsInt());
        var body = json.getAsJsonArray("body");
        assertEquals(3, body.size());
        var caption = body.get(0).getAsJsonObject().getAsJsonObject("contents");
        assertEquals("3dgames:dialog/" + token + "/0", caption.getAsJsonObject("click_event").get("id").getAsString());
        assertTrue(caption.has("hover_event"));
        assertEquals(300, body.get(1).getAsJsonObject().get("width").getAsInt());
        var input = json.getAsJsonArray("inputs").get(0).getAsJsonObject();
        assertEquals("amount", input.get("key").getAsString());
        assertEquals("10", input.get("initial").getAsString());
        assertEquals(16, input.get("max_length").getAsInt());
        var action = json.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals(144, action.get("width").getAsInt());
        assertEquals("minecraft:dynamic/custom", action.getAsJsonObject("action").get("type").getAsString());
        assertEquals("3dgames:dialog/" + token + "/1", action.getAsJsonObject("action").get("id").getAsString());
        assertTrue(json.get("can_close_with_escape").getAsBoolean());
        assertEquals(1, json.getAsJsonArray("actions").size());
        var returnLink = body.get(2).getAsJsonObject().getAsJsonObject("contents");
        assertEquals("[Back]", returnLink.get("text").getAsString());
        assertEquals("yellow", returnLink.get("color").getAsString());
        assertEquals("3dgames:dialog/" + token + "/2", returnLink.getAsJsonObject("click_event").get("id").getAsString());
        assertEquals("3dgames:dialog/" + token + "/3", json.getAsJsonObject("exit_action").getAsJsonObject("action").get("id").getAsString());
    }

    @Test
    void emptyChoiceListUsesTheSameNoticeExitAndWidth() {
        var exit = new MenuView.Button(Component.text("Close"), null, 170, null, "close");
        var json = NativeDialogsBukkit.json(new MenuView(Component.text("Rules"), List.of(), List.of(),
                List.of(), exit, 1), UUID.randomUUID());
        assertEquals("minecraft:notice", json.get("type").getAsString());
        assertEquals(170, json.getAsJsonObject("action").get("width").getAsInt());
        assertFalse(json.getAsJsonObject("action").has("action"));
        assertFalse(json.has("exit_action"));
    }
}

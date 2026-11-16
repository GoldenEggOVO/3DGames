package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Multiplayer.game.battleship.BattleshipGame;
import GoldenEggOVO.TDGames.Shared.game.mastermind.MastermindGame;
import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalTableInputTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void sixPhysicalPalettePegsSelectTheExistingColorActionWithoutMenuChoices() {
        var f = new ClassicTableTest.Fixture(new MastermindGame(1, 7, Map.of()), "mastermind");
        try {
            for (int color = 0; color < 6; color++)
                assertEquals("color:" + color, f.table.action(f.owner, "@palette/" + color));
            assertNull(f.table.action(f.observer, "@palette/0"));
            assertNull(f.table.action(f.owner, "@palette/6"));
        } finally { f.table.close(); }
    }

    @Test void clickingAnUnavailableMemoryCardExplainsItCannotBeUsed() {
        var game = new GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame(2, 7, Map.of());
        var f = new ClassicTableTest.Fixture(game, "memory");
        try {
            game.apply(0, "flip:m0");
            GoldenEggOVO.TDGames.SessionTestSupport.bump(f.room);
            f.table.sync();
            org.mockito.Mockito.clearInvocations(f.owner);
            assertNull(f.table.action(f.owner, "m0"));
            var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            org.mockito.Mockito.verify(f.owner).sendActionBar(message.capture());
            assertEquals(GoldenEggOVO.TDGames.Core.text.Language.component("hint.unavailable"), message.getValue());
        } finally { f.table.close(); }
    }

    @Test void hoveringPhysicalTraysNeverRecolorsThePegsOrShipHulls() {
        for (String kind : List.of("mastermind", "battleship")) {
            var game = kind.equals("mastermind") ? new MastermindGame(1, 7, Map.of()) : new BattleshipGame(2, 1, Map.of());
            var f = new ClassicTableTest.Fixture(game, kind);
            try {
                var views = PhysicalTableInputTest.<Map<UUID, ?>>field(f.table, "views");
                var controls = PhysicalTableInputTest.<Map<String, List<org.bukkit.entity.Entity>>>field(views.get(f.owner.getUniqueId()), "controls");
                var pieces = controls.entrySet().stream().filter(e -> e.getKey().startsWith("@palette/") || e.getKey().startsWith("@ship/"))
                        .flatMap(e -> e.getValue().stream()).toList();
                var materials = new HashMap<org.bukkit.entity.Entity, Material>();
                pieces.forEach(piece -> materials.put(piece, f.materials.get(piece)));
                assertFalse(materials.isEmpty());
                f.table.hover(f.owner, kind.equals("mastermind") ? "@palette/0" : "@ship/0");
                pieces.forEach(piece -> assertEquals(materials.get(piece), f.materials.get(piece)));
                f.table.clear(f.owner);
                pieces.forEach(piece -> assertEquals(materials.get(piece), f.materials.get(piece)));
            } finally { f.table.close(); }
        }
    }

    @Test void shipRackUsesExistingShipActionsAndHoverShowsTheWholeLegalFootprint() {
        var game = new BattleshipGame(2, 1, Map.of());
        var f = new ClassicTableTest.Fixture(game, "battleship");
        try {
            assertEquals("ship:4", f.table.action(f.owner, "@ship/4"));
            assertEquals("place:b0_0", f.table.action(f.owner, "b0_0"));
            f.table.hover(f.owner, "b0_0");
            var views = PhysicalTableInputTest.<Map<UUID, ?>>field(f.table, "views");
            var colors = PhysicalTableInputTest.<Map<String, Material>>field(views.get(f.owner.getUniqueId()), "highlightColors");
            for (int i = 0; i < 5; i++) assertEquals(Material.LIME_CONCRETE, colors.get("b0_" + i));
            assertFalse(colors.containsKey("b1_0"));
            game.apply(0, "rotate");
            assertEquals(List.of("b0_0", "b0_10", "b0_20", "b0_30", "b0_40"), game.placementPreview(0, "b0_0"));
            assertTrue(game.placementPreview(0, "b1_0").isEmpty());
            assertTrue(game.placementPreview(-1, "b0_0").isEmpty());
        } finally { f.table.close(); }
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

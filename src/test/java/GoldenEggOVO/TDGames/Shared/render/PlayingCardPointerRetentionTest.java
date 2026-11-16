package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.render.cards.PlayingCardTable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayingCardPointerRetentionTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void distantSeatedPlayersKeepTheirActualPrivateDisplaysAcrossPointerPulses() throws Exception {
        for (boolean packed : List.of(false, true)) {
            var f = new TableViewTest.Fixture("doudizhu", 3);
            prepareOwner(f, packed);
            while (f.room.board().currentPlayer() != 0) f.move("bid:0");
            f.move("bid:3");
            var controller = controller(f);
            Method pointers = TableWorldController.class.getDeclaredMethod("pointers");
            pointers.setAccessible(true);
            try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
                position(f, 2);
                pointers.invoke(controller);
                Map<String, Object> originalCards = new HashMap<>(cards(f));
                assertEquals(20, originalCards.size());
                List<Entity> privateFaces = parts(originalCards);
                assertEquals(20 * (packed ? 1 : 24), privateFaces.size());
                int created = f.entities.size();

                position(f, 8);
                for (int pulse = 0; pulse < 10; pulse++) pointers.invoke(controller);
                assertEquals(created, f.entities.size(),
                        "Ten unchanged distant pointer pulses must not create new Displays");
                for (Entity face : privateFaces) verify(face, never()).remove();
                Map<String, Object> currentCards = cards(f);
                assertEquals(originalCards.keySet(), currentCards.keySet());
                originalCards.forEach((id, card) -> assertSame(card, currentCards.get(id)));

                position(f, 25);
                pointers.invoke(controller);
                assertTrue(privateViews(f).isEmpty());
                for (Entity face : privateFaces) verify(face).remove();
                assertEquals(created, f.entities.size(), "Leaving display range must only remove Displays");
            } finally {
                f.view.close();
            }
        }
    }

    @Test
    void explicitPlayerCleanupStillRemovesPrivateDisplays() throws Exception {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        prepareOwner(f, false);
        position(f, 2);
        f.view.tick();
        List<Entity> privateFaces = parts(cards(f));
        assertFalse(privateFaces.isEmpty());
        var controller = controller(f);
        controller.clearSelection(f.player);
        assertTrue(privateViews(f).isEmpty());
        for (Entity face : privateFaces) verify(face).remove();
        f.view.close();
    }

    private void prepareOwner(TableViewTest.Fixture f, boolean packed) {
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.plugin.games().session(f.player)).thenReturn(f.room);
        when(f.world.getPlayers()).thenReturn(List.of(f.player));
        position(f, 2);
        if (packed) {
            SessionTestSupport.module(f.plugin, "pack", mock(ResourcePackService.class));
            when(f.plugin.pack().managed()).thenCallRealMethod();
            when(f.plugin.pack().required()).thenCallRealMethod();
            when(f.plugin.pack().packed(f.player)).thenReturn(true);
            when(f.plugin.pack().item(anyString())).thenReturn(new ItemStack(Material.PAPER));
        }
        f.view.tick();
    }

    private void position(TableViewTest.Fixture f, double horizontalDistance) {
        Location feet = f.view.origin.clone().add(0, -TableGeometry.SURFACE, horizontalDistance);
        when(f.player.getLocation()).thenReturn(feet);
        when(f.player.getEyeLocation()).thenReturn(feet.clone().add(0, 1.62, 0));
    }

    private TableWorldController controller(TableViewTest.Fixture f) throws Exception {
        var controller = mock(TableWorldController.class, CALLS_REAL_METHODS);
        SessionTestSupport.set(controller, "pendingSounds", new HashMap<>());
        GameRulesTest.set(controller, "plugin", f.plugin);
        GameRulesTest.set(controller, "views", new HashMap<>(Map.of(f.room.id, f.view)));
        GameRulesTest.set(controller, "clicks", new HashMap<>());
        GameRulesTest.set(controller, "selections", new HashMap<>());
        SessionTestSupport.module(f.plugin, "tableWorld", controller);
        SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
        return controller;
    }

    private PlayingCardTable table(TableViewTest.Fixture f) throws Exception {
        return (PlayingCardTable) TableViewTest.field(f.view, "playingTable");
    }

    private Map<?, ?> privateViews(TableViewTest.Fixture f) throws Exception {
        return (Map<?, ?>) TableViewTest.field(table(f), "privateViews");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cards(TableViewTest.Fixture f) throws Exception {
        Object view = privateViews(f).get(f.player.getUniqueId());
        assertNotNull(view, "The 24-block display policy must retain the owner's hand at eight blocks");
        return (Map<String, Object>) TableViewTest.field(view, "cards");
    }

    @SuppressWarnings("unchecked")
    private List<Entity> parts(Map<String, Object> cards) throws Exception {
        List<Entity> parts = new ArrayList<>();
        for (Object card : cards.values())
            parts.addAll((List<Entity>) TableViewTest.field(card, "parts"));
        return parts;
    }
}

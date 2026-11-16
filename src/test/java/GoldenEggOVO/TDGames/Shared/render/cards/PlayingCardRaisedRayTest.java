package GoldenEggOVO.TDGames.Shared.render.cards;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayingCardRaisedRayTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void raisedPackedCardKeepsItsVisibleTopAndItsOriginalStickyBottomClickable() throws Exception {
        var f = new TableViewTest.Fixture("liars-bar", 4);
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getWorld()).thenReturn(f.world);
        when(f.player.getLocation()).thenReturn(f.view.origin.clone().add(0, 0, 2));
        when(f.plugin.allowed(f.player)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player));
        SessionTestSupport.module(f.plugin, "pack", mock(ResourcePackService.class));
        when(f.plugin.pack().managed()).thenCallRealMethod();
        when(f.plugin.pack().required()).thenCallRealMethod();
        when(f.plugin.pack().packed(f.player)).thenReturn(true);
        when(f.plugin.pack().item(anyString())).thenReturn(new ItemStack(Material.PAPER));
        f.view.tick();
        try {
            var table = (PlayingCardTable) TableViewTest.field(f.view, "playingTable");
            var views = (Map<?, ?>) TableViewTest.field(table, "privateViews");
            Object view = views.get(f.player.getUniqueId());
            var cards = (Map<String, Object>) TableViewTest.field(view, "cards");
            var first = cards.entrySet().iterator().next();
            Entity face = ((List<Entity>) TableViewTest.field(first.getValue(), "parts")).getFirst();
            Location originalBase = face.getLocation().clone();
            Vector towardHand = new Vector(0, 0, -1);
            double packedHeight = .168 * 84 / 60;
            Location topRay = originalBase.clone().add(0, packedHeight + .085 - .005, 1.5);

            table.hover(f.player, first.getKey());
            assertEquals(.085, face.getLocation().getY() - originalBase.getY(), 1e-8);
            assertEquals(first.getKey(), table.handHit(f.player, topRay, towardHand),
                    "The top of the raised, visible face must be a ray target");
            assertEquals(first.getKey(), table.handHit(f.player,
                    originalBase.clone().add(0, .02, 1.5), towardHand),
                    "Retain the deliberate sticky hit near the original bottom edge");

            table.hover(f.player, null);
            assertNull(table.handHit(f.player, topRay, towardHand),
                    "A resting card must not capture empty space above its real top");
        } finally {
            f.view.close();
        }
    }
}

package GoldenEggOVO.TDGames.Shared.audio;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import org.junit.jupiter.api.Test;
import java.util.List;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ApprovedSoundRoutingTest {
    @Test void closingAnOlderTableDoesNotStopAnotherTablesLatestResult() {
        var plugin = SessionTestSupport.plugin();
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        SessionTestSupport.module(plugin, "pack", mock(ResourcePackService.class));
        var player = mock(Player.class);
        when(plugin.pack().packed(player)).thenReturn(true);
        var world = mock(World.class);
        var a = new Location(world, 0, 64, 0);
        var b = new Location(world, 4, 64, 0);
        TableSounds.playFor(plugin, player, a, TableSounds.WIN, "chess");
        TableSounds.playFor(plugin, player, b, TableSounds.WIN, "chess");
        clearInvocations(player);
        TableSounds.stopResults(a);
        verifyNoInteractions(player);
        TableSounds.stopResults(b);
        verify(player).stopSound("3dgames:table.win", SoundCategory.BLOCKS);
    }

    @Test void cardFlippingAndRecyclingUseTheirApprovedActions() {
        assertEquals("cards.flip", TableSounds.move("memory", 0, "flip:m0", List.of(), List.of()).resource());
        assertEquals("cards.flip", TableSounds.move("memory", 0, "hide", List.of(), List.of()).resource());
        assertEquals("cards.shuffle", TableSounds.move("klondike", 0, "recycle", List.of(), List.of()).resource());
    }

    @Test void promotionUsesTheWoodenTurnoverOnlyWhenAccepted() {
        var before = List.of(new Cell("a3", 0, 2, "P", 0));
        var after = List.of(new Cell("a2", 0, 1, "+P", 0));
        var cue = TableSounds.move("shogi", 0, "promote:yes", before, after);
        assertEquals("board.promote", cue.resource());
        assertTrue(TableSounds.isLanding(cue));
        assertEquals("board.wood", TableSounds.move("shogi", 0, "promote:no", before, after).resource());
    }
}

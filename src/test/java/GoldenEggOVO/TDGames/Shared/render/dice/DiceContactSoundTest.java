package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceContactSoundTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void bothTraysUseFourShortContactsWithDecayAndPerListenerPackFallback() throws Exception {
        for (String kind : List.of("chess", "yacht")) for (boolean packed : List.of(false, true)) {
            var f = new TableViewTest.Fixture(kind);
            var pack = mock(ResourcePackService.class);
            when(f.plugin.pack()).thenReturn(pack);
            when(pack.packed(f.player)).thenReturn(packed);
            when(f.world.getPlayers()).thenReturn(List.of(f.player));
            when(f.plugin.allowed(f.player)).thenReturn(true);
            when(f.player.getLocation()).thenReturn(f.view.origin.clone());
            DiceTray tray = kind.equals("chess") ? new DiceTray(f.plugin, f.room,
                    new Location(f.world, 0, 80, 0), new NamespacedKey("3dgames", "board-cell"), false) : null;
            try {
                if (tray != null) tray.roll(6, 91); else f.move("roll");
                clearInvocations(f.player, f.world);
                float lastVolume = Float.POSITIVE_INFINITY;
                for (int frame = 1; frame <= DiceMotion.FRAMES; frame++) {
                    if (tray != null) tray.tick(); else f.view.tick();
                    var sounds = mockingDetails(f.player).getInvocations().stream()
                            .filter(call -> call.getMethod().getName().equals("playSound")).toList();
                    boolean contact = List.of(8, 13, 17, 21).contains(frame);
                    boolean settled = packed && frame == DiceMotion.FRAMES;
                    assertEquals(contact || settled ? 1 : 0, sounds.size(), kind + " contact frame " + frame);
                    if (settled) {
                        assertEquals("3dgames:dice.settle", sounds.getFirst().getArgument(1));
                        assertEquals(SoundCategory.BLOCKS, sounds.getFirst().getArgument(2));
                    }
                    if (contact) {
                        var sound = sounds.getFirst();
                        assertEquals(packed ? "3dgames:dice.contact" : Sound.BLOCK_BONE_BLOCK_HIT, sound.getArgument(1));
                        assertEquals(SoundCategory.BLOCKS, sound.getArgument(2));
                        float volume = sound.getArgument(3);
                        assertTrue(volume < lastVolume && volume > 0, "Each bounce must become quieter");
                        lastVolume = volume;
                    }
                    assertTrue(mockingDetails(f.world).getInvocations().stream()
                            .noneMatch(call -> call.getMethod().getName().equals("playSound")), "A contact must not double broadcast");
                    clearInvocations(f.player, f.world);
                }
                if (tray != null) tray.close(); else f.view.close();
                for (int frame = 0; frame < 30; frame++) { if (tray != null) tray.tick(); else f.view.tick(); }
                assertTrue(mockingDetails(f.player).getInvocations().stream().noneMatch(call -> call.getMethod().getName().equals("playSound")));
            } finally { if (tray != null) tray.close(); f.view.close(); }
        }
    }

    @Test void pausedAndRestoringTraysDoNotAnnounceSettlement() throws Exception {
        for (String kind : List.of("chess", "yacht")) for (boolean restoring : List.of(false, true)) {
            var f = new TableViewTest.Fixture(kind);
            var pack = mock(ResourcePackService.class);
            when(f.plugin.pack()).thenReturn(pack);
            when(pack.packed(f.player)).thenReturn(true);
            when(f.world.getPlayers()).thenReturn(List.of(f.player));
            when(f.plugin.allowed(f.player)).thenReturn(true);
            when(f.player.getLocation()).thenReturn(f.view.origin.clone());
            DiceTray tray = kind.equals("chess") ? new DiceTray(f.plugin, f.room,
                    new Location(f.world, 0, 80, 0), new NamespacedKey("3dgames", "board-cell"), false) : null;
            try {
                if (tray != null) tray.roll(6, 91); else f.move("roll");
                if (restoring) f.room.beginRestore(); else f.room.pause("test");
                clearInvocations(f.player);
                for (int frame = 1; frame <= DiceMotion.FRAMES; frame++) {
                    if (tray != null) tray.tick(); else f.view.tick();
                }
                assertTrue(mockingDetails(f.player).getInvocations().stream().noneMatch(call ->
                        call.getMethod().getName().equals("playSound")
                                && "3dgames:dice.settle".equals(call.getArgument(1))));
            } finally { if (tray != null) tray.close(); f.view.close(); }
        }
    }
}

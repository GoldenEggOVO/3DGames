package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Core.menu.GameMenus;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import com.google.gson.JsonPrimitive;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TablePlayer2048FocusTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void sprintPressTogglesFixedTopDownCameraAndReleaseKeepsItActive() {
        var f = fixture();
        Location original = f.location.get().clone();
        f.input(true);
        assertTrue(f.tablePlayers.hasTableFocus(f.player));
        assertEquals(GameMode.SPECTATOR, f.mode.get());
        Location camera = f.location.get().clone();
        assertEquals(0, camera.getX(), .0001);
        assertEquals(0, camera.getZ(), .0001);
        assertEquals(90, camera.getPitch(), .0001);
        assertTrue(camera.getY() > original.getY() + 2);
        f.input(false);
        f.sync();
        assertTrue(f.tablePlayers.hasTableFocus(f.player));
        f.input(true);
        assertFalse(f.tablePlayers.hasTableFocus(f.player));
        assertEquals(GameMode.SURVIVAL, f.mode.get());
        assertEquals(original, f.location.get());
        f.input(true);
        assertFalse(f.tablePlayers.hasTableFocus(f.player));
        assertTrue(f.saved.isEmpty());
    }

    @Test void boardDirectionsIgnoreStartingYawAndLongPressOrDiagonalDoNotRepeat() {
        for (float yaw : new float[] {0, 90, 180, 270}) {
            var f = fixture();
            f.location.get().setYaw(yaw);
            f.input(true);
            assertTrue(f.tablePlayers.hasTableFocus(f.player));
            f.input(false);
            for (String direction : List.of("up", "left", "down", "right")) {
                String action = "slide:" + direction;
                assertTrue(f.room.board().legalActions(0).contains(action));
                Input key = keys(direction);
                f.tablePlayers.input(new PlayerInputEvent(f.player, key));
                f.tablePlayers.input(new PlayerInputEvent(f.player, key));
                verify(f.plugin.games(), times(1)).applyTableAction(eq(f.room), eq(0), eq(new JsonPrimitive(action)), eq(f.player));
            }
            Input diagonal = mock(Input.class);
            when(diagonal.isForward()).thenReturn(true);
            when(diagonal.isLeft()).thenReturn(true);
            f.tablePlayers.input(new PlayerInputEvent(f.player, diagonal));
            verify(f.plugin.games(), times(4)).applyTableAction(any(), anyInt(), any(), any());
            Location attempted = f.location.get().clone().add(1, 0, 1);
            attempted.setYaw(77);
            var move = new PlayerMoveEvent(f.player, f.location.get(), attempted);
            f.tablePlayers.move(move);
            assertTrue(move.isCancelled());
            assertEquals(180, f.location.get().getYaw(), .0001);
            f.tablePlayers.releaseFocus(f.player);
        }
    }

    @Test void changingDirectionThroughAnOverlappingKeyPressDoesNotLoseTheSecondMove() {
        var f = fixture();
        f.input(true); f.input(false);
        f.tablePlayers.input(new PlayerInputEvent(f.player, keys("up")));
        Input both = mock(Input.class);
        when(both.isForward()).thenReturn(true);
        when(both.isLeft()).thenReturn(true);
        f.tablePlayers.input(new PlayerInputEvent(f.player, both));
        f.tablePlayers.input(new PlayerInputEvent(f.player, keys("left")));
        f.tablePlayers.input(new PlayerInputEvent(f.player, keys("left")));
        verify(f.plugin.games()).applyTableAction(eq(f.room), eq(0), eq(new JsonPrimitive("slide:up")), eq(f.player));
        verify(f.plugin.games()).applyTableAction(eq(f.room), eq(0), eq(new JsonPrimitive("slide:left")), eq(f.player));
        verify(f.plugin.games(), times(2)).applyTableAction(any(), anyInt(), any(), any());
    }

    @Test void blockedDirectionShowsFeedbackOnlyOncePerPress() {
        var f = fixture();
        SessionTestSupport.set(f.room.board(), "board", new int[] {
                2, 4, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0});
        f.input(true);
        f.input(false);
        clearInvocations(f.player);
        var up = keys("up");
        f.tablePlayers.input(new PlayerInputEvent(f.player, up));
        f.tablePlayers.input(new PlayerInputEvent(f.player, up));
        var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(f.player, times(1)).sendActionBar(message.capture());
        assertEquals(GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.blocked"), message.getValue());
    }

    @Test void menuPauseAndRoomLeaveRestoreCameraAndCannotSendStaleSlides() {
        for (int reason = 0; reason < 3; reason++) {
            var f = fixture();
            Location original = f.location.get().clone();
            f.input(true);
            assertTrue(f.tablePlayers.hasTableFocus(f.player));
            if (reason == 0) {
                SessionTestSupport.module(f.plugin, "menus", mock(GameMenus.class));
                when(f.plugin.menus().active(f.player)).thenReturn(true);
            } else if (reason == 1) SessionTestSupport.set(f.room, "phase", GameSession.Phase.PAUSED);
            else when(f.plugin.games().session(f.player)).thenReturn(null);
            f.sync();
            assertFalse(f.tablePlayers.hasTableFocus(f.player));
            assertEquals(original, f.location.get());
            assertEquals(GameMode.SURVIVAL, f.mode.get());
            f.tablePlayers.input(new PlayerInputEvent(f.player, keys("up")));
            verify(f.plugin.games(), never()).applyTableAction(any(), anyInt(), any(), any());
        }
    }

    @Test void deathDisconnectAndPluginStopClear2048FocusAndRestoreFlags() {
        for (int reason = 0; reason < 3; reason++) {
            var f = fixture();
            f.input(true);
            assertTrue(f.tablePlayers.hasTableFocus(f.player));
            if (reason == 0) {
                var event = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
                when(event.getEntity()).thenReturn(f.player);
                when(f.player.isDead()).thenReturn(true);
                f.tablePlayers.death(event);
            } else if (reason == 1) f.tablePlayers.quit(new PlayerQuitEvent(f.player,
                    net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            else try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
                f.tablePlayers.close();
            }
            assertFalse(f.tablePlayers.hasTableFocus(f.player));
            assertEquals(GameMode.SURVIVAL, f.mode.get());
            assertTrue(f.saved.isEmpty());
            assertNull(f.target.get());
            verify(f.camera).remove();
            verify(f.player).setGravity(true);
        }
    }

    @Test void cameraHidesPlayerAndBlocksOtherSpectatorTargetsAndRemoteTeleport() {
        var f = fixture();
        Player peer = mock(Player.class);
        when(peer.getUniqueId()).thenReturn(UUID.randomUUID());
        try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player, peer));
            f.input(true);
            assertTrue(f.tablePlayers.hasTableFocus(f.player));
            verify(peer).hidePlayer(f.plugin, f.player);
            var switchCamera = new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(f.player, f.camera, peer);
            f.tablePlayers.switchCamera(switchCamera);
            assertTrue(switchCamera.isCancelled());
            var teleport = new PlayerTeleportEvent(f.player, f.location.get(),
                    new Location(f.world, 1000, 90, 1000), PlayerTeleportEvent.TeleportCause.SPECTATE);
            f.tablePlayers.spectatorTeleport(teleport);
            assertTrue(teleport.isCancelled());
            f.tablePlayers.releaseFocus(f.player);
            verify(peer).showPlayer(f.plugin, f.player);
        }
    }

    @Test void spectatorUnseatedPausedAndDistantPlayersCannotEnter() {
        for (int reason = 0; reason < 4; reason++) {
            var f = fixture();
            if (reason == 0) f.mode.set(GameMode.SPECTATOR);
            else if (reason == 1) SessionTestSupport.mutableSeats(f.room).clear();
            else if (reason == 2) SessionTestSupport.set(f.room, "phase", GameSession.Phase.PAUSED);
            else f.location.set(new Location(f.world, 100, 80, 100));
            f.input(true);
            assertFalse(f.tablePlayers.hasTableFocus(f.player));
            verify(f.player, never()).setGravity(false);
        }
    }

    private static TablePlayerFocusTest.Fixture fixture() {
        var f = new TablePlayerFocusTest.Fixture(0, "2048");
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.set(f.room, "board", GameFactory.create("2048", 1, 1));
        SessionTestSupport.set(f.room.board(), "board", new int[] {
                0, 0, 0, 0,
                0, 2, 0, 0,
                0, 0, 4, 0,
                0, 0, 0, 0});
        return f;
    }

    private static Input keys(String direction) {
        Input keys = mock(Input.class);
        when(keys.isForward()).thenReturn(direction.equals("up"));
        when(keys.isBackward()).thenReturn(direction.equals("down"));
        when(keys.isLeft()).thenReturn(direction.equals("left"));
        when(keys.isRight()).thenReturn(direction.equals("right"));
        return keys;
    }

}

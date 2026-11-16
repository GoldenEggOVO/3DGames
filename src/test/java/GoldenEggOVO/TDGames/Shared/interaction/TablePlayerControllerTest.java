package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TablePlayerControllerTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    @Test void protectionOnlyAppliesNearAnAuthorizedPlayersOwnTable() throws Exception {
        Fixture f=new Fixture();
        assertTrue(f.hunger());
        when(f.plugin.tableWorld().atTableWorld(f.player,f.room)).thenReturn(false);assertFalse(f.hunger());
        when(f.plugin.tableWorld().atTableWorld(f.player,f.room)).thenReturn(true);
        when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));assertFalse(f.hunger());
        when(f.player.getLocation()).thenReturn(new Location(f.world,0,80,2.25));
        when(f.plugin.allowed(f.player)).thenReturn(false);assertFalse(f.hunger());
    }
    @Test void walkingAwayRestoresCollisionAndRepeatedSyncDoesNotResendIt() throws Exception {
        Fixture f=new Fixture();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
            f.tablePlayers.syncPlayerState();f.tablePlayers.syncPlayerState();verify(f.player,times(1)).setCollidable(false);
            when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));f.tablePlayers.syncPlayerState();
            verify(f.player).setCollidable(true);f.tablePlayers.syncPlayerState();verify(f.player,times(1)).setCollidable(true);
        }
    }
    @Test void quitAndWorldChangeRestoreCollisionImmediately() throws Exception {
        Fixture f=new Fixture();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));f.tablePlayers.syncPlayerState();
            when(f.plugin.tableWorld().atTableWorld(f.player,f.room)).thenReturn(false);
            f.tablePlayers.world(new org.bukkit.event.player.PlayerChangedWorldEvent(f.player,f.world));verify(f.player).setCollidable(true);
            when(f.plugin.tableWorld().atTableWorld(f.player,f.room)).thenReturn(true);f.tablePlayers.syncPlayerState();
            f.tablePlayers.quit(new org.bukkit.event.player.PlayerQuitEvent(f.player,net.kyori.adventure.text.Component.empty(),org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));verify(f.player,times(2)).setCollidable(true);
        }
    }
    @Test void theRegularRoomTickUpdatesComfortWithoutAnotherMenuAction() throws Exception {
        Fixture f=new Fixture();SessionTestSupport.module(f.plugin, "tablePlayers", f.tablePlayers);
        GameRulesTest.set(f.plugin.games(), "loaded",true);SessionTestSupport.install(f.plugin, new LinkedHashMap<UUID,GameSession>());
        doCallRealMethod().when(SessionTestSupport.service(f.plugin)).tick();
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));f.plugin.games().tick();verify(f.player).setCollidable(false);
            when(f.player.getLocation()).thenReturn(new Location(f.world,100,80,0));f.plugin.games().tick();verify(f.player).setCollidable(true);
        }
    }
    @Test void preexistingNonCollidableStateSurvivesLeavingAndClosing() throws Exception {
        Fixture f=new Fixture();when(f.player.isCollidable()).thenReturn(false);
        try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(f.player));
            f.tablePlayers.syncPlayerState();when(f.plugin.games().session(f.player)).thenReturn(null);f.tablePlayers.syncPlayerState();f.tablePlayers.close();
            verify(f.player,never()).setCollidable(true);
        }
    }

    @Test void spectatorsAndOtherGamesCannotCaptureMovementKeys() {
        Fixture f = new Fixture();
        assertFalse(f.tablePlayers.startDirectionalControl(f.player));
        f.tablePlayers.close();
        f = new Fixture("2048");
        SessionTestSupport.set(f.room, "phase", GameSession.Phase.PLAYING);
        assertFalse(f.tablePlayers.startDirectionalControl(f.player));
        f.tablePlayers.close();
    }
    static final class Fixture {
        final TDGamesPlugin plugin=SessionTestSupport.plugin();final Player player=mock(Player.class);final World world=mock(World.class);
        final GameSession room;final TablePlayerController tablePlayers;
        Fixture(){this("chess");}
        Fixture(String kind){
            room=new GameSession(UUID.randomUUID(),kind,kind.equals("2048") ? 1 : 2,0,0);
            when(plugin.getName()).thenReturn("3DGames");when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(MockBukkit.getMock());
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
            SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));when(plugin.games().session(player)).thenReturn(room);when(plugin.allowed(player)).thenReturn(true);
            when(plugin.tableWorld().atTableWorld(player,room)).thenReturn(true);when(plugin.tableWorld().center(0)).thenReturn(new Location(world,0,80,0));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getLocation()).thenReturn(new Location(world,0,80,2.25));when(player.isCollidable()).thenReturn(true);
            tablePlayers=new TablePlayerController(plugin);
        }
        boolean hunger(){FoodLevelChangeEvent event=new FoodLevelChangeEvent(player,19);tablePlayers.hunger(event);return event.isCancelled();}
    }
}

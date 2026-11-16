package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Multiplayer.room.RoundActions;
import GoldenEggOVO.TDGames.SessionTestSupport;

import com.google.gson.*;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Multiplayer.game.ludo.LudoGame;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomOptionsTest {
    @Test void monopolyStoresTheModernEditionAndRejectsOlderUnfinishedRules() {
        var room = new GameSession(UUID.randomUUID(), "monopoly", 3, 17, 0);
        var record = SessionRecordCodec.record(room);
        record.add("history", new JsonArray());
        assertEquals(2, record.get("rulesVersion").getAsInt());
        assertDoesNotThrow(() -> SessionRecordCodec.session(record));
        record.addProperty("rulesVersion", 1);
        assertThrows(IllegalArgumentException.class, () -> SessionRecordCodec.session(record));
    }
    @Test void startingSideMovesThePlayerAndPreservesHostAuthority() throws Exception {
        GameSession room=new GameSession(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        Player host=mock(Player.class);when(host.getUniqueId()).thenReturn(UUID.randomUUID());room.join(host.getUniqueId(),"Host");room.fillBots();
        TDGamesPlugin plugin=SessionTestSupport.plugin();SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));doCallRealMethod().when(SessionTestSupport.service(plugin)).prepareSeats(room);
        when(plugin.allowed(host)).thenReturn(true);when(plugin.tableWorld().atTableWorld(host,room)).thenReturn(true);
        World world=mock(World.class);Location previous=new Location(world,0,80,1.7),target=new Location(world,0,80,-1.7);
        when(host.getLocation()).thenReturn(previous);when(plugin.tableWorld().seatLocation(room,1)).thenReturn(target);when(host.teleport(target)).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(()->Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);
            assertTrue(plugin.games().prepareSeats(room));verify(host).teleport(target);assertEquals(1,room.seat(host.getUniqueId()));assertTrue(room.host(host.getUniqueId()));
            assertTrue(plugin.games().prepareSeats(room));verify(host,times(1)).teleport(target);
        }
    }
    @Test void cancelledSideChangeDoesNotStartTheGameOrReassignItsHost() throws Exception {
        GameSession room=new GameSession(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        Player host=mock(Player.class);when(host.getUniqueId()).thenReturn(UUID.randomUUID());room.join(host.getUniqueId(),"Host");room.fillBots();SessionTestSupport.mutableReady(room).add(host.getUniqueId());
        List<GameSession.Seat> before=List.copyOf(room.seats);TDGamesPlugin plugin=SessionTestSupport.plugin();SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        doCallRealMethod().when(SessionTestSupport.service(plugin)).start(room);doCallRealMethod().when(SessionTestSupport.service(plugin)).prepareSeats(room);when(plugin.allowed(host)).thenReturn(true);
        when(plugin.tableWorld().atTableWorld(host,room)).thenReturn(true);when(host.getLocation()).thenReturn(new Location(mock(World.class),0,80,0));
        when(plugin.tableWorld().seatLocation(room,1)).thenReturn(new Location(mock(World.class),0,80,1));
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(()->Bukkit.getPlayer(host.getUniqueId())).thenReturn(host);plugin.games().start(room);
            assertEquals(before,room.seats);assertEquals(GameSession.Phase.LOBBY,room.phase);assertNotNull(room.board);assertTrue(room.ready.isEmpty());assertTrue(room.host(host.getUniqueId()));
            verify(plugin.tableWorld()).render(room);
        }
    }
    @Test void choosingSecondChangesSeatsWithoutChangingChessColorsOrTheOpeningRule(){
        GameSession room=new GameSession(UUID.randomUUID(),"chess",2,25,0,Map.of("first","opponent"));
        UUID host=UUID.randomUUID();room.join(host,"Host");room.fillBots();room.prepareSeats();
        assertEquals(1,room.seat(host));assertEquals(0,room.newBoard().currentPlayer());
        assertTrue(room.newBoard().legalActions(0).contains("move:e2:e4"));
        room.prepareSeats();assertEquals(1,room.seat(host),"Empty-history restart must not swap twice");
    }
    @Test void missingOptionsRetainLegacyLudoDeployment(){
        assertEquals(Map.of(),GameSession.readOptions(new JsonObject()));
        GameSession room=new GameSession(UUID.randomUUID(),"ludo",2,12,0);
        assertEquals(new LudoGame(2,12).cells(),room.newBoard().cells());
    }
    @Test void configuredRulesAreImmutableAndSurviveJsonAndFreshRounds(){
        Map<String,String> draft=new HashMap<>(Map.of("start","six","blocking","on","goal","over","finish","all"));
        GameSession room=new GameSession(UUID.randomUUID(),"ludo",2,12,0,draft);draft.clear();
        assertThrows(UnsupportedOperationException.class,()->room.options.put("start","automatic"));
        JsonObject record=new JsonObject();record.add("options",new Gson().toJsonTree(room.options));
        GameSession restored=new GameSession(room.id,room.kind,2,12,0,GameSession.readOptions(record));
        assertEquals(room.options,restored.options);assertTrue(restored.newBoard().cells().stream().filter(c->c.owner()>=0).allMatch(c->c.id().startsWith("ba")));
        room.phase=GameSession.Phase.FINISHED;RoundActions.fresh(room,13);
        assertEquals(restored.options,room.options);assertEquals(restored.newBoard().cells(),room.newBoard().cells());
    }
    @Test void malformedOrUnknownOptionsAreRejectedInsteadOfChangingSavedRules(){
        for(String json:List.of("{\"options\":null}","{\"options\":[]}","{\"options\":{\"blocking\":true}}"))
            assertThrows(RuntimeException.class,()->GameSession.readOptions(JsonParser.parseString(json).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->new GameSession(UUID.randomUUID(),"ludo",2,0,0,Map.of("blocking","double")));
        assertThrows(IllegalArgumentException.class,()->new GameSession(UUID.randomUUID(),"chess",2,0,0,Map.of("blocking","on")));
    }
}

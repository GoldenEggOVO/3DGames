package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.SessionTestSupport;

import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceRollGateTest {
    @Test void yachtRejectsKeepAndScoreThroughTheSharedPathWhileDiceAreRolling() throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID,GameSession>());
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        GameSession room = new GameSession(UUID.randomUUID(), "yacht", 2, 0, 0);
        SessionTestSupport.set(room, "board", spy(room.newBoard())); SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(plugin).put(room.id, room);
        when(plugin.tableWorld().rolling(room)).thenReturn(true);
        doCallRealMethod().when(SessionTestSupport.service(plugin)).apply(any(), anyInt(), any(), any());
        for (String action : List.of("roll", "hold:die0", "score:ones"))
            plugin.games().apply(room, 0, new JsonPrimitive(action), null);
        assertTrue(room.history.isEmpty());
        verify(room.board(), never()).apply(anyInt(), anyString());
        verify(plugin.tableWorld(), never()).render(any());
    }
    @Test void animationBlocksTheSharedActionPathForBotsCommandsAndMenus() throws Exception {
        TDGamesPlugin plugin=SessionTestSupport.plugin();SessionTestSupport.install(plugin, new LinkedHashMap<UUID,GameSession>());
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));GameSession room=new GameSession(UUID.randomUUID(),"ludo",2,0,0);SessionTestSupport.set(room, "board", spy(room.newBoard()));SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        SessionTestSupport.mutableSessions(plugin).put(room.id,room);when(plugin.tableWorld().rolling(room)).thenReturn(true);
        doCallRealMethod().when(SessionTestSupport.service(plugin)).apply(any(),anyInt(),any(),any());plugin.games().apply(room,0,new JsonPrimitive("roll"),null);
        assertTrue(room.history.isEmpty());verify(room.board(),never()).apply(anyInt(),anyString());verify(plugin.tableWorld(),never()).render(any());
    }
}

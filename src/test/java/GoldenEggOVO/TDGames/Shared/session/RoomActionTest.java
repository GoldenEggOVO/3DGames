package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.SessionTestSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomActionTest {
    static Stream<JsonElement> malformedActions() {
        return Stream.of(null, JsonNull.INSTANCE, new JsonObject(), new JsonArray(),
                new JsonPrimitive(42), new JsonPrimitive(true));
    }

    @ParameterizedTest
    @MethodSource("malformedActions")
    void malformedActionsCannotFinishTheRoomOrChangeItsHistory(JsonElement action) throws Exception {
        var plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.board = room.newBoard();
        room.phase = GameSession.Phase.PLAYING;
        var cells = room.board.cells();
        SessionTestSupport.install(plugin, new LinkedHashMap<>(Map.of(room.id, room)));
        doCallRealMethod().when(SessionTestSupport.service(plugin)).apply(any(), anyInt(), any(), any());

        assertDoesNotThrow(() -> plugin.games().apply(room, 0, action, null));

        assertEquals(GameSession.Phase.PLAYING, room.phase);
        assertEquals(cells, room.board.cells());
        assertTrue(room.history.isEmpty());
        verify(plugin.games(), never()).finish(any(), anyString());
        verify(plugin.games(), never()).save();
        verify(plugin.games(), never()).save(any(GameSession.class));
        verify(plugin.tableWorld(), never()).render(any());
    }
}

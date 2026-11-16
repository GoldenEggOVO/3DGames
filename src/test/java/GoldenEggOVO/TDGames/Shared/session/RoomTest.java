package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import com.google.gson.*;


import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    GameSession room(){return new GameSession(UUID.randomUUID(),"checkers",3,1L,0);}
    @Test void uniqueMembershipPreservesExistingReadyPlayers(){GameSession r=room();UUID a=UUID.randomUUID();r.join(a,"a");SessionTestSupport.mutableReady(r).add(a);r.join(a,"a");assertEquals(1,r.seats.size());assertTrue(r.ready.contains(a));r.join(UUID.randomUUID(),"b");assertEquals(Set.of(a),r.ready);r.join(UUID.randomUUID(),"c");assertThrows(IllegalArgumentException.class,()->r.join(UUID.randomUUID(),"d"));}
    @Test void startLocksMembershipAndOldCallbacks(){GameSession r=room();UUID a=UUID.randomUUID();r.join(a,"a");r.phase=GameSession.Phase.PLAYING;assertThrows(IllegalArgumentException.class,()->r.join(UUID.randomUUID(),"x"));assertThrows(IllegalArgumentException.class,()->r.requireAction(a,0));assertDoesNotThrow(()->r.requireAction(a,r.revision));r.busy=true;assertThrows(IllegalArgumentException.class,()->r.requireAction(a,r.revision));r.busy=false;assertThrows(IllegalArgumentException.class,()->r.requireAction(UUID.randomUUID(),r.revision));}
    @Test void botIdentityStableAndNeverDuplicatesHuman(){GameSession r=room();UUID a=UUID.randomUUID();r.join(a,"a");r.fillBots();r.fillBots();assertEquals(3,r.seats.size());assertEquals(3,r.seats.stream().map(GameSession.Seat::id).distinct().count());assertFalse(r.seats.getFirst().bot());assertTrue(r.seats.getLast().bot());}
    @Test
    void refillingBotsAfterAPlayerLeavesKeepsEverySeatUnique() {
        GameSession room = room();
        UUID host = UUID.randomUUID();
        room.join(host, "Host");
        room.join(UUID.randomUUID(), "Guest");
        room.fillBots();
        GameSession.Seat existingBot = room.seats.getLast();
        SessionTestSupport.mutableSeats(room).removeIf(seat -> seat.id().equals(host));
        room.fillBots();
        assertEquals(3, room.seats.size());
        assertEquals(3, room.seats.stream().map(GameSession.Seat::id).distinct().count());
        assertTrue(room.seats.contains(existingBot));
    }
    @Test void actionHistoryIsDetached(){GameSession r=room();JsonObject action=new JsonObject();action.addProperty("type","bid");action.addProperty("value",3);r.event(0,action);action.addProperty("value",0);assertEquals(3,r.history.get(0).getAsJsonObject().getAsJsonObject("action").get("value").getAsInt());}
    @Test void supportedSeatCountsAreExplicit(){assertTrue(GameCatalog.capacityValid("connectfour",2));assertFalse(GameCatalog.capacityValid("connectfour",3));assertFalse(GameCatalog.capacityValid("checkers",5));assertTrue(GameCatalog.capacityValid("checkers",6));assertFalse(GameCatalog.capacityValid("chess",3));assertFalse(GameCatalog.capacityValid("unknown",2));}
}

package GoldenEggOVO.TDGames.Core.player;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import GoldenEggOVO.TDGames.TDGamesPlugin;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerSessionsTest {
    private TDGamesPlugin plugin() throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        SessionTestSupport.install(plugin, new java.util.LinkedHashMap<UUID, GameSession>());
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        when(plugin.allowed(any())).thenReturn(true);
        var service = SessionTestSupport.service(plugin);
        doCallRealMethod().when(service).create(any(), anyString(), anyInt());
        doCallRealMethod().when(service).create(any(), anyString(), anyInt(), anyMap());
        doCallRealMethod().when(service).create(any(), anyString(), anyInt(), anyMap(), anyBoolean());
        doCallRealMethod().when(service).createReservedForMode(any(), anyString(), anyInt(), anyMap());
        doCallRealMethod().when(service).join(any(), any());
        var multiplayer = spy(plugin.multiplayer());
        SessionTestSupport.set(service, "multiplayer", multiplayer);
        when(plugin.multiplayer()).thenReturn(multiplayer);
        return plugin;
    }

    private Player player(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());return p;}
    @Test void reservationRefusalPreventsRoomCreation() throws Exception{var plugin=plugin();var p=player();assertThrows(IllegalArgumentException.class,()->plugin.games().create(p,"chess",2));verify(plugin.games(),never()).createReserved(any(),anyString(),anyInt(),anyMap());verify(plugin.sessions(),never()).release(any(),any());}
    @Test void creationFailureReleasesReservation() throws Exception{var plugin=plugin();var p=player();when(plugin.sessions().reserve(p.getUniqueId(),"chess")).thenReturn(true);doThrow(new IllegalArgumentException("blocked terrain")).when(SessionTestSupport.service(plugin)).createReserved(p,"chess",2,java.util.Map.of());assertThrows(IllegalArgumentException.class,()->plugin.games().create(p,"chess",2));verify(plugin.sessions()).release(p.getUniqueId(),"chess");}
    @Test void joinFailureReleasesReservationAndExistingSeatDoesNotReserveTwice() throws Exception{var plugin=plugin();var multiplayer=plugin.multiplayer();var p=player();GameSession room=new GameSession(UUID.randomUUID(),"chess",2,1,0);SessionTestSupport.mutableSessions(plugin).put(room.id,room);when(plugin.sessions().reserve(p.getUniqueId(),"chess")).thenReturn(true);doThrow(new IllegalArgumentException("full")).when(multiplayer).joinReserved(p,room);assertThrows(IllegalArgumentException.class,()->plugin.games().join(p,room));verify(plugin.sessions()).release(p.getUniqueId(),"chess");clearInvocations(plugin.sessions());room.join(p.getUniqueId(),"P");plugin.games().join(p,room);verify(plugin.sessions(),never()).reserve(any(),any());verify(plugin.games()).resume(p,room);}
    @Test void exceptionAfterCreationRemovesPartialRoom() throws Exception{var plugin=plugin();var p=player();GameSession room=new GameSession(UUID.randomUUID(),"chess",2,1,0);SessionTestSupport.mutableSessions(plugin).put(room.id,room);when(plugin.sessions().reserve(p.getUniqueId(),"chess")).thenReturn(true);doAnswer(call->{when(plugin.games().session(p)).thenReturn(room);throw new IllegalStateException("menu failed");}).when(SessionTestSupport.service(plugin)).createReserved(p,"chess",2,java.util.Map.of());assertThrows(IllegalStateException.class,()->plugin.games().create(p,"chess",2));verify(plugin.games()).remove(room);}
    @Test void exceptionAfterJoinRemovesPartialSeat() throws Exception{var plugin=plugin();var multiplayer=plugin.multiplayer();var p=player();GameSession room=new GameSession(UUID.randomUUID(),"chess",2,1,0);SessionTestSupport.mutableSessions(plugin).put(room.id,room);when(plugin.sessions().reserve(p.getUniqueId(),"chess")).thenReturn(true);doAnswer(call->{room.join(p.getUniqueId(),"P");throw new IllegalStateException("teleport failed");}).when(multiplayer).joinReserved(p,room);assertThrows(IllegalStateException.class,()->plugin.games().join(p,room));assertEquals(-1,room.seat(p.getUniqueId()));}
}

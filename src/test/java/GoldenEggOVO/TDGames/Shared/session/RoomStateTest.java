package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.game.GameService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomStateTest {
    @Test
    void publicSeatsCannotBypassAdmissionAndRevisionTracking() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        UUID host = UUID.randomUUID();
        room.join(host, "Host");
        long revision = room.revision;

        assertThrows(UnsupportedOperationException.class,
                () -> room.seats.clear());

        assertEquals(1, room.seats.size());
        assertEquals(0, room.seat(host));
        assertEquals(revision, room.revision);
    }

    @Test
    void readyStateCannotBeChangedWithoutRoomValidation() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);

        assertThrows(UnsupportedOperationException.class,
                () -> room.ready.add(UUID.randomUUID()));
    }

    @Test
    void presenceAndDepartureViewsCannotBeMutatedByConsumers() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);

        assertThrows(UnsupportedOperationException.class,
                () -> room.offline.put(UUID.randomUUID(), 1L));
        assertThrows(UnsupportedOperationException.class,
                () -> room.departed.add(UUID.randomUUID()));
    }

    @Test
    void startingAfterBotAdmissionRejectsActionsFromTheLobbyRevision() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        UUID host = UUID.randomUUID();
        room.join(host, "Host");
        long lobbyRevision = room.revision();
        room.fillBots();
        room.beginStart();
        room.board = room.newBoard();
        room.started();

        assertThrows(IllegalArgumentException.class, () -> room.requireAction(host, lobbyRevision));
        assertDoesNotThrow(() -> room.requireAction(host, room.revision()));
    }

    @Test
    void removingTheHostTransfersAuthorityAndClearsTheirPresenceAndReadiness() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        UUID host = UUID.randomUUID(), guest = UUID.randomUUID();
        room.join(host, "Host");
        room.join(guest, "Guest");
        room.toggleReady(host);
        room.markOffline(host, 1);
        long revision = room.revision();

        assertTrue(room.removeSeat(host));

        assertTrue(room.host(guest));
        assertEquals(-1, room.seat(host));
        assertTrue(room.ready.isEmpty());
        assertTrue(room.offline.isEmpty());
        assertTrue(room.revision() > revision);
        assertFalse(room.removeSeat(host));
    }

    @Test
    void changingStakeInvalidatesConfirmationsAndOldRevisions() {
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);
        UUID host = UUID.randomUUID();
        room.join(host, "Host");
        room.toggleReady(host);
        long revision = room.revision();

        room.changeStake(10);

        assertEquals(10, room.stake());
        assertTrue(room.ready.isEmpty());
        assertTrue(room.revision() > revision);
    }

    @Test
    void consumersCannotRegisterOrRemoveRoomsWithoutTheService() {
        var service = new GameService(org.mockito.Mockito.mock(GoldenEggOVO.TDGames.TDGamesPlugin.class));
        var room = new GameSession(UUID.randomUUID(), "chess", 2, 42, 0);

        assertThrows(UnsupportedOperationException.class, () -> service.activeSessions().put(room.id, room));
        assertTrue(service.activeSessions().isEmpty());
    }
}

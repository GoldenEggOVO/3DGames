package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.Core.player.PlayerSessions;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalOccupancyTest {
    @Test void onePlayerCanReserveOnlyOneBoardUntilMatchingRelease() {
        var seats = new PlayerSessions();
        UUID player = UUID.randomUUID();
        assertTrue(seats.reserve(player, "chess"));
        assertFalse(seats.reserve(player, "chess"));
        assertFalse(seats.reserve(player, "gomoku"));
        seats.release(player, "gomoku");
        assertFalse(seats.reserve(player, "gomoku"));
        seats.release(player, "chess");
        assertTrue(seats.reserve(player, "gomoku"));
    }

    @Test void restoredOfflineSeatRejectsDuplicateWithoutOnlineAdmission() {
        var seats = new PlayerSessions();
        UUID player = UUID.randomUUID();
        assertTrue(seats.restoreReservation(player, "connectfour"));
        assertFalse(seats.restoreReservation(player, "connectfour"));
        assertFalse(seats.reserve(player, "chess"));
    }

    @Test void closingOccupancyClearsAllLocalSeats() {
        var seats = new PlayerSessions();
        UUID player = UUID.randomUUID();
        assertTrue(seats.reserve(player, "chess"));
        seats.close();
        assertTrue(seats.reserve(player, "gomoku"));
    }
}

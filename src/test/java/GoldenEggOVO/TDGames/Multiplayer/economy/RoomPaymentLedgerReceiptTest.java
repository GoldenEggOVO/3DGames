package GoldenEggOVO.TDGames.Multiplayer.economy;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomPaymentLedgerReceiptTest {
    @TempDir Path directory;
    final RoomPaymentLedgerTest.FakeEconomy bank = new RoomPaymentLedgerTest.FakeEconomy();

    GameSession room() {
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(UUID.randomUUID(), "Host");
        room.join(UUID.randomUUID(), "Guest");
        room.seats.forEach(seat -> bank.balances.put(seat.id(), 100.0));
        return room;
    }

    RoomPaymentLedger ledger() throws Exception {
        return new RoomPaymentLedger(directory.resolve("economy.json"), bank);
    }

    @Test
    void receiptUsesConfirmedPaymentsAndOriginalTermsWithoutWritingOrTransferring() throws Exception {
        var room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "winner:0");
        byte[] stored = Files.readAllBytes(directory.resolve("economy.json"));
        int calls = bank.calls;
        var winner = ledger.receipt(room, room.seats.getFirst().id());
        var loser = ledger.receipt(room, room.seats.getLast().id());
        assertEquals(10, ledger.terms(room).entryFee());
        assertEquals(25, ledger.terms(room).winnerReward());
        assertEquals(10, winner.charged());
        assertEquals(25, winner.rewarded());
        assertEquals(15, winner.net());
        assertEquals(-10, loser.net());
        assertFalse(winner.pending());
        assertFalse(winner.review());
        assertNull(ledger.receipt(room, UUID.randomUUID()));
        assertArrayEquals(stored, Files.readAllBytes(directory.resolve("economy.json")));
        assertEquals(calls, bank.calls);
        SessionTestSupport.set(room, "seed", room.seed() + 1);
        assertNull(ledger.terms(room));
        assertNull(ledger.receipt(room, room.seats.getFirst().id()));
    }

    @Test
    void rejectedRewardStaysPendingUntilConfirmedAfterRecovery() throws Exception {
        var room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        UUID host = room.seats.getFirst().id();
        bank.rejectCredit = host;
        ledger.finish(room, "winner:0");
        var pending = ledger.receipt(room, host);
        assertEquals(0, pending.rewarded());
        assertEquals(25, pending.rewardPending());
        assertEquals(-10, pending.net());
        assertTrue(pending.pending());
        assertFalse(pending.review());
        bank.rejectCredit = null;
        ledger = ledger();
        ledger.recover(List.of(room));
        assertEquals(25, ledger.receipt(room, host).rewarded());
        assertFalse(ledger.receipt(room, host).pending());
    }

    @Test
    void uncertainRewardNeverAppearsAsReceivedOrSafeToRetry() throws Exception {
        var room = room();
        EconomyAccess uncertain = new EconomyAccess() {
            public boolean available() { return true; }
            public java.util.OptionalDouble balance(UUID player) { return bank.balance(player); }
            public String format(double amount) { return bank.format(amount); }
            public Transfer withdraw(UUID player, double amount) { return bank.withdraw(player, amount); }
            public Transfer deposit(UUID player, double amount) { return Transfer.UNCERTAIN; }
        };
        var ledger = new RoomPaymentLedger(directory.resolve("economy.json"), uncertain);
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "winner:0");
        var receipt = ledger.receipt(room, room.seats.getFirst().id());
        assertEquals(0, receipt.rewarded());
        assertEquals(0, receipt.rewardPending());
        assertTrue(receipt.review());
        assertFalse(receipt.pending());
    }

    @Test
    void drawReceiptTracksPendingAndCompletedRefunds() throws Exception {
        var room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        UUID host = room.seats.getFirst().id();
        bank.rejectCredit = host;
        ledger.finish(room, "draw");
        assertEquals(10, ledger.receipt(room, host).refundPending());
        assertEquals(0, ledger.receipt(room, host).refunded());
        assertEquals(0, ledger.receipt(room, host).rewardPending());
        bank.rejectCredit = null;
        ledger.recover(List.of(room));
        assertEquals(10, ledger.receipt(room, host).refunded());
        assertEquals(0, ledger.receipt(room, host).net());
        assertFalse(ledger.receipt(room, host).pending());
    }

    @Test
    void aFailedStartDoesNotDiscloseAnotherPlayersBalanceToTheCaller() throws Exception {
        var room = room();
        bank.balances.put(room.seats.getLast().id(), 5.0);
        var error = assertThrows(RuleViolation.class, () -> ledger().begin(room, true, 10, 25));
        assertFalse(error.message().parameters().containsKey("shortfall"));
        assertFalse(error.message().parameters().containsKey("balance"));
        assertEquals("Guest", error.message().parameters().get("player"));
        assertEquals("10.0", error.message().parameters().get("amount"));
        assertEquals(0, bank.calls);
    }
}

package GoldenEggOVO.TDGames.Multiplayer.economy;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoomPaymentLedgerTest {
    @TempDir Path data;
    final FakeEconomy bank = new FakeEconomy();

    GameSession room() {
        var room = new GameSession(UUID.randomUUID(), "connectfour", 2, 42, 0);
        room.join(UUID.randomUUID(), "Host");
        room.join(UUID.randomUUID(), "Guest");
        room.seats.forEach(seat -> bank.balances.put(seat.id(), 100.0));
        return room;
    }

    RoomPaymentLedger ledger() throws Exception {
        return new RoomPaymentLedger(data.resolve("economy.json"), bank);
    }

    @Test
    void disabledEconomyAndBotPracticeNeitherChargeNorCreateRecords() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, false, 10, 25);
        SessionTestSupport.mutableSeats(room).set(1, new GameSession.Seat(room.seats.get(1).id(), "Bot", true));
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "winner:0");
        assertEquals(0, bank.calls);
        assertFalse(Files.exists(data.resolve("economy.json")));
    }

    @Test
    void startAndFinishChargeAndRewardOnlyOnceAcrossRestart() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        ledger.begin(room, true, 10, 25);
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        var restored = ledger();
        restored.recover(List.of(room));
        restored.begin(room, true, 99, 999);
        restored.finish(room, "winner:0");
        restored.finish(room, "winner:1");
        ledger().recover(List.of(room));
        assertEquals(115, bank.balances.get(room.seats.get(0).id()));
        assertEquals(90, bank.balances.get(room.seats.get(1).id()));
        assertEquals(3, bank.calls);
    }

    @Test
    void insufficientFundsRejectBeforeChargingAnyPlayer() throws Exception {
        GameSession room = room();
        bank.balances.put(room.seats.get(1).id(), 5.0);
        assertThrows(IllegalArgumentException.class, () -> ledger().begin(room, true, 10, 25));
        assertEquals(100, bank.balances.get(room.seats.get(0).id()));
        assertEquals(0, bank.calls);
    }

    @Test
    void rejectedSecondChargeRefundsTheFirstPlayer() throws Exception {
        GameSession room = room();
        bank.rejectDebit = room.seats.get(1).id();
        assertThrows(IllegalArgumentException.class, () -> ledger().begin(room, true, 10, 25));
        assertEquals(100, bank.balances.get(room.seats.get(0).id()));
        assertEquals(100, bank.balances.get(room.seats.get(1).id()));
        assertEquals(3, bank.calls);
    }

    @Test
    void aRefundedStartFailureCanBeRetriedWithTheSameRoom() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        bank.rejectDebit = room.seats.getLast().id();
        assertThrows(IllegalArgumentException.class, () -> ledger.begin(room, true, 10, 25));
        bank.rejectDebit = null;
        assertDoesNotThrow(() -> ledger.begin(room, true, 10, 25));
        room.seats.forEach(seat -> assertEquals(90, bank.balances.get(seat.id())));
    }

    @Test
    void anInterruptedChargeCannotBeTreatedAsAnActivePaidRound() throws Exception {
        GameSession room = room();
        ledger().begin(room, true, 10, 25);
        Path file = data.resolve("economy.json");
        var record = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        record.getAsJsonArray("rounds").get(0).getAsJsonObject().getAsJsonObject("payments")
                .remove("CHARGE:" + room.seats.getLast().id());
        Files.writeString(file, record.toString());
        var restored = ledger();
        assertThrows(IllegalArgumentException.class, () -> restored.begin(room, true, 10, 25));
        assertThrows(IllegalArgumentException.class, () -> restored.finish(room, "winner:0"));
        assertTrue(bank.balances.values().stream().allMatch(balance -> balance <= 100));
    }

    @Test
    void invalidPaymentRelationshipsCannotBeRestored() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "winner:0");
        Path file = data.resolve("economy.json");
        var original = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (String state : List.of("refund-without-cancel", "reward-to-loser", "reward-on-cancelled-round")) {
            var root = original.deepCopy();
            var round = root.getAsJsonArray("rounds").get(0).getAsJsonObject();
            var payments = round.getAsJsonObject("payments");
            if (state.equals("refund-without-cancel")) {
                var refund = payments.getAsJsonObject("CHARGE:" + room.seats.getFirst().id()).deepCopy();
                refund.addProperty("kind", "REFUND");
                payments.add("REFUND:" + room.seats.getFirst().id(), refund);
            } else if (state.equals("reward-to-loser")) {
                var reward = payments.remove("REWARD:" + room.seats.getFirst().id()).getAsJsonObject();
                reward.addProperty("player", room.seats.getLast().id().toString());
                payments.add("REWARD:" + room.seats.getLast().id(), reward);
            } else {
                round.addProperty("cancelled", true);
            }
            Files.writeString(file, root.toString());
            assertThrows(java.io.IOException.class, this::ledger, state);
        }
        assertEquals(3, bank.calls);
    }

    @Test
    void cancellationAndDrawRefundOnceAndNeverReward() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        ledger.cancel(room);
        ledger.cancel(room);
        ledger.finish(room, "winner:0");
        room.seats.forEach(seat -> assertEquals(100, bank.balances.get(seat.id())));
        assertEquals(4, bank.calls);
        SessionTestSupport.set(room, "seed", room.seed() + 1);
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "draw");
        ledger.finish(room, "draw");
        room.seats.forEach(seat -> assertEquals(100, bank.balances.get(seat.id())));
        assertEquals(8, bank.calls);
    }

    @Test
    void absentRoomsRefundKnownChargesAfterRestart() throws Exception {
        GameSession room = room();
        ledger().begin(room, true, 10, 25);
        ledger().recover(List.of());
        ledger().recover(List.of());
        room.seats.forEach(seat -> assertEquals(100, bank.balances.get(seat.id())));
        assertEquals(4, bank.calls);
    }

    @Test
    void missingProviderRejectsPaidGamesButLeavesFreeGamesAvailable() throws Exception {
        bank.enabled = false;
        GameSession room = room();
        var ledger = ledger();
        assertDoesNotThrow(() -> ledger.begin(room, false, 10, 25));
        assertThrows(IllegalArgumentException.class, () -> ledger.begin(room, true, 10, 25));
        assertEquals(0, bank.calls);
    }

    @Test
    void multipleWinnersEachReceiveOneFixedReward() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        ledger.finish(room, "winners:0,1,0");
        room.seats.forEach(seat -> assertEquals(115, bank.balances.get(seat.id())));
        assertEquals(4, bank.calls);
    }

    @Test
    void rejectedRewardsCanBeRetriedWithoutRepeatingSuccessfulTransfers() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        ledger.begin(room, true, 10, 25);
        bank.rejectCredit = room.seats.getFirst().id();
        ledger.finish(room, "winner:0");
        assertEquals(90, bank.balances.get(room.seats.getFirst().id()));
        bank.rejectCredit = null;
        ledger().recover(List.of(room));
        ledger().recover(List.of(room));
        assertEquals(115, bank.balances.get(room.seats.getFirst().id()));
        assertEquals(4, bank.calls);
    }

    @Test
    void uncertainTransfersArePreservedAndNeverAutomaticallyRepeated() throws Exception {
        GameSession room = room();
        bank.uncertainDebit = room.seats.getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> ledger().begin(room, true, 10, 25));
        ledger().recover(List.of());
        ledger().recover(List.of());
        assertEquals(1, bank.calls);
        assertTrue(Files.readString(data.resolve("economy.json")).contains("UNCERTAIN"));
    }

    @Test
    void corruptedLedgerAndUnwritableStorageCannotCauseMoneyTransfers() throws Exception {
        Files.writeString(data.resolve("economy.json"), "broken");
        assertThrows(Exception.class, this::ledger);
        Files.delete(data.resolve("economy.json"));
        Files.createDirectory(data.resolve("economy.json"));
        assertThrows(Exception.class, () -> ledger().begin(room(), true, 10, 25));
        assertEquals(0, bank.calls);
    }

    @Test
    void invalidAmountsAreRejectedWithoutTouchingBalances() throws Exception {
        GameSession room = room();
        for (double amount : List.of(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class, () -> ledger().begin(room, true, amount, 25));
            assertThrows(IllegalArgumentException.class, () -> ledger().begin(room, true, 10, amount));
        }
        assertEquals(0, bank.calls);
    }

    @Test
    void failedWriteCannotLeaveAnUnchargedRoundThatLooksActive() throws Exception {
        GameSession room = room();
        var ledger = ledger();
        Files.createDirectory(data.resolve("economy.json.tmp"));
        assertThrows(IllegalArgumentException.class, () -> ledger.begin(room, true, 10, 25));
        Files.delete(data.resolve("economy.json.tmp"));
        assertThrows(IllegalArgumentException.class, () -> ledger.begin(room, true, 10, 25));
        assertThrows(IllegalArgumentException.class, () -> ledger.finish(room, "winner:0"));
        assertEquals(0, bank.calls);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CHARGE", "REFUND", "REWARD"})
    void aSuccessfulTransferWithAFailedResultWriteIsNotRepeatedAfterRestart(String kind) throws Exception {
        GameSession room = room();
        var ledger = ledger();
        if (!kind.equals("CHARGE")) ledger.begin(room, true, 10, 25);
        bank.afterTransfer = () -> {
            try { Files.createDirectory(data.resolve("economy.json.tmp")); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        };
        assertThrows(IllegalArgumentException.class, () -> {
            switch (kind) {
                case "CHARGE" -> ledger.begin(room, true, 10, 25);
                case "REFUND" -> ledger.cancel(room);
                case "REWARD" -> ledger.finish(room, "winner:0");
            }
        });
        bank.afterTransfer = () -> {};
        Files.delete(data.resolve("economy.json.tmp"));
        var restored = ledger();
        assertTrue(restored.needsReview());
        restored.recover(List.of());
        int calls = bank.calls;
        ledger().recover(List.of());
        assertEquals(calls, bank.calls);
        assertEquals(kind.equals("CHARGE") ? 1 : kind.equals("REFUND") ? 4 : 3, calls);
        assertTrue(Files.readString(data.resolve("economy.json")).contains("UNCERTAIN"));
    }

    static final class FakeEconomy implements EconomyAccess {
        final Map<UUID, Double> balances = new HashMap<>();
        boolean enabled = true;
        UUID rejectDebit, rejectCredit, uncertainDebit;
        int calls;
        Runnable afterTransfer = () -> {};

        public boolean available() { return enabled; }
        public OptionalDouble balance(UUID player) { return OptionalDouble.of(balances.getOrDefault(player, 0.0)); }
        public String format(double amount) { return Double.toString(amount); }
        public Transfer withdraw(UUID player, double amount) {
            calls++;
            if (player.equals(uncertainDebit)) return Transfer.UNCERTAIN;
            if (player.equals(rejectDebit)) return Transfer.REJECTED;
            balances.merge(player, -amount, Double::sum);
            afterTransfer.run();
            return Transfer.SUCCESS;
        }
        public Transfer deposit(UUID player, double amount) {
            calls++;
            if (player.equals(rejectCredit)) return Transfer.REJECTED;
            balances.merge(player, amount, Double::sum);
            afterTransfer.run();
            return Transfer.SUCCESS;
        }
    }
}

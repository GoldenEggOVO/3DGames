package GoldenEggOVO.TDGames.Multiplayer.economy;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RoomPaymentLedgerPoolTest {
    @TempDir Path directory;
    final RoomPaymentLedgerTest.FakeEconomy bank = new RoomPaymentLedgerTest.FakeEconomy();

    GameSession room(int players) {
        var room = new GameSession(UUID.randomUUID(), "yacht", players, 42, 0);
        for (int seat = 0; seat < players; seat++) {
            UUID id = UUID.randomUUID();
            room.join(id, "Player" + seat);
            bank.balances.put(id, 100.0);
        }
        return room;
    }

    RoomPaymentLedger ledger() throws Exception { return new RoomPaymentLedger(directory.resolve("economy.json"), bank); }

    @Test
    void playersFundThePotAndTheWinnerReceivesOnlyThatPotOnce() throws Exception {
        var room = room(2);
        var ledger = ledger();
        ledger.beginPool(room, 10);
        ledger.beginPool(room, 20);
        assertEquals(90, bank.balances.get(room.seats.getFirst().id()));
        ledger.finish(room, "winner:0");
        assertEquals(110, bank.balances.get(room.seats.getFirst().id()));
        assertEquals(90, bank.balances.get(room.seats.getLast().id()));
        assertEquals(20, ledger.receipt(room, room.seats.getFirst().id()).rewarded());
        assertTrue(ledger.isPool(room));
        assertEquals(20, ledger.terms(room).pot());
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        ledger().recover(List.of(room));
        assertEquals(3, bank.calls);
        assertEquals(200, bank.balances.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void multipleWinnersSplitEveryCurrencyUnitInStableSeatOrder() throws Exception {
        var room = room(4);
        var ledger = ledger();
        ledger.beginPool(room, 1.01);
        ledger.finish(room, "winners:2,1,0,2");
        assertEquals(1.35, ledger.receipt(room, room.seats.get(0).id()).rewarded());
        assertEquals(1.35, ledger.receipt(room, room.seats.get(1).id()).rewarded());
        assertEquals(1.34, ledger.receipt(room, room.seats.get(2).id()).rewarded());
        assertEquals(0, ledger.receipt(room, room.seats.get(3).id()).rewarded());
        assertEquals(400, bank.balances.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void rejectedPoolAwardsRetryWithoutReplayingOtherWinnersPayments() throws Exception {
        var room = room(3);
        var ledger = ledger();
        ledger.beginPool(room, 10);
        UUID winner = room.seats.get(1).id();
        bank.rejectCredit = winner;
        ledger.finish(room, "winners:0,1");
        assertEquals(15, ledger.receipt(room, winner).rewardPending());
        bank.rejectCredit = null;
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        ledger().recover(List.of(room));
        ledger().recover(List.of(room));
        assertEquals(105, bank.balances.get(winner));
        assertEquals(6, bank.calls);
        assertEquals(300, bank.balances.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void drawsAndFailedStartsRefundTheWholeStake() throws Exception {
        var room = room(2);
        var ledger = ledger();
        bank.rejectDebit = room.seats.getLast().id();
        assertThrows(IllegalArgumentException.class, () -> ledger.beginPool(room, 10));
        assertEquals(100, bank.balances.get(room.seats.getFirst().id()));
        bank.rejectDebit = null;
        ledger.beginPool(room, 10);
        ledger.finish(room, "draw");
        assertEquals(100, bank.balances.get(room.seats.getFirst().id()));
        assertEquals(100, bank.balances.get(room.seats.getLast().id()));
        assertEquals(10, ledger.receipt(room, room.seats.getFirst().id()).refunded());
    }

    @Test
    void recoveryRestoresForfeitedMembershipAlongWithTheAwardedResult() throws Exception {
        var room = room(2);
        var ledger = ledger();
        ledger.beginPool(room, 10);
        UUID loser = room.seats.getFirst().id();
        SessionTestSupport.mutableDeparted(room).add(loser);
        ledger.finish(room, "winner:1");
        SessionTestSupport.mutableDeparted(room).clear();
        SessionTestSupport.set(room, "result", "");
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        ledger().recover(List.of(room));
        assertEquals(GameSession.Phase.FINISHED, room.phase());
        assertEquals(Set.of(loser), room.departed);
        assertEquals(-1, room.seat(loser));
        assertEquals(3, bank.calls);
    }

    @Test
    void newJournalsRequireExplicitPoolAndCurrencyPrecision() throws Exception {
        var room = room(2);
        ledger().beginPool(room, 10);
        var file = directory.resolve("economy.json");
        String original = Files.readString(file);
        for (String field : List.of("pool", "precision")) {
            for (String replacement : List.of("missing", "null", "\"invalid\"", "1.5")) {
                var json = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
                var round = json.getAsJsonArray("rounds").get(0).getAsJsonObject();
                if (replacement.equals("missing")) round.remove(field);
                else round.add(field, com.google.gson.JsonParser.parseString(replacement));
                Files.writeString(file, json.toString());
                assertThrows(java.io.IOException.class, this::ledger, field + ":" + replacement);
            }
        }
        assertEquals(2, bank.calls);
    }

    @Test
    void poolPrecisionIsValidatedBeforeChargingAndTamperedAwardsFailClosed() throws Exception {
        var room = room(2);
        var ledger = ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.beginPool(room, 0.001));
        assertEquals(0, bank.calls);
        ledger.beginPool(room, 10);
        ledger.finish(room, "winner:0");
        var file = directory.resolve("economy.json");
        var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2, json.get("schema").getAsInt());
        json.getAsJsonArray("rounds").get(0).getAsJsonObject().getAsJsonObject("payments")
                .getAsJsonObject("REWARD:" + room.seats.getFirst().id()).addProperty("amount", 200);
        Files.writeString(file, json.toString());
        assertThrows(java.io.IOException.class, this::ledger);
    }
}

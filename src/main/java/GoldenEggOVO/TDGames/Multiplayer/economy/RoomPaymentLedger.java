package GoldenEggOVO.TDGames.Multiplayer.economy;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RoomPaymentLedger {
    public record Terms(double entryFee, double winnerReward, boolean pool, double pot) {
        public Terms(double entryFee, double winnerReward) { this(entryFee, winnerReward, false, 0); }
    }
    public record Receipt(double charged, double refunded, double rewarded, double refundPending,
                          double rewardPending, boolean review) {
        public double net() {
            return java.math.BigDecimal.valueOf(refunded).add(java.math.BigDecimal.valueOf(rewarded))
                    .subtract(java.math.BigDecimal.valueOf(charged)).doubleValue();
        }
        public boolean pending() { return refundPending > 0 || rewardPending > 0; }
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private enum Kind { CHARGE, REFUND, REWARD }
    private enum State { IN_FLIGHT, SUCCESS, REJECTED, UNCERTAIN }

    private static final class Payment {
        UUID player;
        Kind kind;
        State state;
        double amount;

        Payment(UUID player, Kind kind, double amount) {
            this.player = player;
            this.kind = kind;
            this.amount = amount;
            state = State.IN_FLIGHT;
        }
    }

    private static final class Round {
        UUID room;
        long seed;
        double fee, reward;
        boolean pool;
        int precision;
        List<UUID> players;
        Set<UUID> departed = new LinkedHashSet<>();
        boolean cancelled;
        String result;
        Map<String, Payment> payments = new LinkedHashMap<>();
    }

    private final Path file;
    private final EconomyAccess economy;
    private final Map<String, Round> rounds = new LinkedHashMap<>();
    private boolean storageFailed;

    public RoomPaymentLedger(Path file, EconomyAccess economy) throws IOException {
        this.file = file;
        this.economy = economy;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int schema = root.get("schema").getAsInt();
            if (!Set.of(1, 2).contains(schema)) throw new IllegalArgumentException("Unsupported economy schema");
            boolean recovered = false;
            for (var element : root.getAsJsonArray("rounds")) {
                var object = element.getAsJsonObject();
                if (schema == 2) {
                    if (!object.has("pool") || !object.get("pool").isJsonPrimitive()
                            || !object.getAsJsonPrimitive("pool").isBoolean()
                            || !object.has("precision") || !object.get("precision").isJsonPrimitive()
                            || !object.getAsJsonPrimitive("precision").isNumber())
                        throw new IllegalArgumentException("Missing economy round terms");
                    object.get("precision").getAsBigDecimal().intValueExact();
                } else if (object.has("pool") && object.get("pool").getAsBoolean()) {
                    throw new IllegalArgumentException("Pool requires economy schema 2");
                }
                Round round = JSON.fromJson(element, Round.class);
                recovered |= validate(round);
                if (rounds.put(key(round.room, round.seed), round) != null)
                    throw new IllegalArgumentException("Duplicate economy round");
            }
            if (recovered) store();
        } catch (RuntimeException exception) {
            throw new IOException("Cannot read economy records; original file retained", exception);
        }
    }

    public void begin(GameSession room, boolean enabled, double fee, double reward) {
        begin(room, enabled, fee, reward, false);
    }

    public void beginPool(GameSession room, double stake) {
        begin(room, true, stake, 0, true);
    }

    private void begin(GameSession room, boolean enabled, double fee, double reward, boolean pool) {
        Round existing = rounds.get(key(room.id, room.seed()));
        if (existing != null) {
            if (storageFailed) throw failure("error.economy.storage");
            if (existing.cancelled || existing.result != null || uncertain(existing) || !charged(existing))
                throw failure("error.economy.review");
            return;
        }
        if (!enabled || room.restoring() || !room.history.isEmpty()
                || room.seats.stream().anyMatch(GameSession.Seat::bot)) return;
        amount(fee);
        amount(reward);
        if (fee == 0 && reward == 0) return;
        if (storageFailed) throw failure("error.economy.storage");
        if (!economy.available()) throw failure("error.economy.unavailable");
        if (!economy.supports(fee) || !economy.supports(reward)) throw failure("error.economy.amount");
        if (fee > 0) {
            for (GameSession.Seat seat : room.seats) {
                var balance = economy.balance(seat.id());
                if (balance.isEmpty()) throw failure("error.economy.unavailable");
                if (balance.getAsDouble() < fee)
                    throw new RuleViolation("error.economy.funds", "Insufficient balance", "player", seat.name(), "amount", economy.format(fee));
            }
        }
        Round round = new Round();
        round.room = room.id;
        round.seed = room.seed();
        round.fee = fee;
        round.reward = reward;
        round.pool = pool;
        if (pool) {
            int precision = economy.fractionalDigits();
            round.precision = precision < 0 ? 2 : precision;
            if (round.precision > 8 || java.math.BigDecimal.valueOf(fee).stripTrailingZeros().scale() > round.precision)
                throw failure("error.economy.amount");
        }
        round.players = room.seats.stream().map(GameSession.Seat::id).toList();
        validate(round);
        rounds.put(key(round.room, round.seed), round);
        store();
        for (UUID player : round.players) {
            if (fee > 0 && !transfer(round, player, Kind.CHARGE, fee)) {
                cancel(round);
                throw failure(uncertain(round) ? "error.economy.review" : "error.economy.transaction");
            }
        }
    }

    public void finish(GameSession room, String result) {
        Round round = rounds.get(key(room.id, room.seed()));
        if (round == null || round.cancelled) return;
        if (!charged(round)) {
            cancel(round);
            throw failure("error.economy.review");
        }
        if (round.result == null) {
            winners(round, result);
            round.result = result;
            round.departed.addAll(room.departed);
            store();
        }
        settle(round);
    }

    public void cancel(GameSession room) {
        Round round = rounds.get(key(room.id, room.seed()));
        if (round != null && round.result == null) cancel(round);
    }

    public List<GameSession> recover(Collection<GameSession> rooms) {
        Map<String, GameSession> live = new HashMap<>();
        rooms.forEach(room -> live.put(key(room.id, room.seed()), room));
        List<GameSession> changed = new ArrayList<>();
        for (Round round : List.copyOf(rounds.values())) {
            GameSession room = live.get(key(round.room, round.seed));
            if (round.result != null) {
                if (room != null && (room.phase() != GameSession.Phase.FINISHED || !round.result.equals(room.result())
                        || !room.departed.containsAll(round.departed))) {
                    room.markDeparted(round.departed);
                    room.finish(round.result);
                    changed.add(room);
                }
                settle(round);
            } else if (round.cancelled || room == null || room.phase() == GameSession.Phase.LOBBY || !charged(round)) {
                cancel(round);
                if (room != null && room.phase() != GameSession.Phase.LOBBY) {
                    room.pause("error.economy.review");
                    changed.add(room);
                }
            } else if (room.phase() == GameSession.Phase.FINISHED) {
                finish(room, room.result());
            }
            if (room == null && resolved(round) && rounds.containsKey(key(round.room, round.seed))) {
                rounds.remove(key(round.room, round.seed));
                store();
            }
        }
        return List.copyOf(changed);
    }

    public boolean needsReview() {
        return rounds.values().stream().anyMatch(RoomPaymentLedger::uncertain);
    }

    public Terms terms(GameSession room) {
        Round round = rounds.get(key(room.id, room.seed()));
        return round == null ? null : new Terms(round.fee, round.reward, round.pool, round.pool ? pot(round) : 0);
    }

    public boolean isPool(GameSession room) {
        Round round = rounds.get(key(room.id, room.seed()));
        return round != null && round.pool && !round.cancelled;
    }

    public Receipt receipt(GameSession room, UUID player) {
        Round round = rounds.get(key(room.id, room.seed()));
        return round == null ? null : receipt(round, player);
    }

    public boolean blocked(UUID player) {
        if (storageFailed) return true;
        return rounds.values().stream().anyMatch(round -> {
            var receipt = receipt(round, player);
            return receipt != null && (receipt.review() || receipt.pending());
        });
    }

    private Receipt receipt(Round round, UUID player) {
        if (round == null || !round.players.contains(player)) return null;
        Payment charge = round.payments.get(paymentKey(player, Kind.CHARGE));
        Payment refund = round.payments.get(paymentKey(player, Kind.REFUND));
        Payment reward = round.payments.get(paymentKey(player, Kind.REWARD));
        double charged = confirmed(charge), refunded = confirmed(refund), rewarded = confirmed(reward);
        double refundPending = round.cancelled && charged > 0 && retryable(refund) ? round.fee : 0;
        double rewardPending = !round.cancelled && round.result != null && winners(round, round.result).contains(player)
                && retryable(reward) ? award(round, player) : 0;
        boolean review = storageFailed || round.payments.values().stream().anyMatch(payment -> payment.player.equals(player)
                && (payment.state == State.IN_FLIGHT || payment.state == State.UNCERTAIN));
        return new Receipt(charged, refunded, rewarded, refundPending, rewardPending, review);
    }

    private static double confirmed(Payment payment) {
        return payment != null && payment.state == State.SUCCESS ? payment.amount : 0;
    }

    private static boolean retryable(Payment payment) {
        return payment == null || payment.state == State.REJECTED;
    }

    private void cancel(Round round) {
        round.cancelled = true;
        store();
        for (UUID player : round.players) {
            Payment charge = round.payments.get(paymentKey(player, Kind.CHARGE));
            if (charge != null && charge.state == State.SUCCESS)
                transfer(round, player, Kind.REFUND, charge.amount);
        }
        if (round.result == null && resolved(round)) {
            rounds.remove(key(round.room, round.seed));
            store();
        }
    }

    private void settle(Round round) {
        if (round.cancelled) {
            cancel(round);
            return;
        }
        List<UUID> winners = winners(round, round.result);
        if (winners.isEmpty()) {
            cancel(round);
        } else {
            for (UUID winner : winners) {
                double amount = award(round, winner);
                if (amount > 0) transfer(round, winner, Kind.REWARD, amount);
            }
        }
    }

    private static double pot(Round round) {
        return java.math.BigDecimal.valueOf(round.fee).multiply(java.math.BigDecimal.valueOf(round.players.size())).doubleValue();
    }

    private static double award(Round round, UUID player) {
        if (!round.pool) return round.reward;
        if (round.result == null) return 0;
        var eligible = winners(round, round.result);
        var ordered = round.players.stream().filter(eligible::contains).toList();
        int place = ordered.indexOf(player);
        if (place < 0) return 0;
        var total = java.math.BigDecimal.valueOf(round.fee).multiply(java.math.BigDecimal.valueOf(round.players.size()));
        var count = java.math.BigDecimal.valueOf(ordered.size());
        var share = total.divide(count, round.precision, java.math.RoundingMode.DOWN);
        int remainder = total.subtract(share.multiply(count)).movePointRight(round.precision).intValueExact();
        if (place < remainder) share = share.add(java.math.BigDecimal.ONE.movePointLeft(round.precision));
        return share.doubleValue();
    }

    private boolean transfer(Round round, UUID player, Kind kind, double amount) {
        String key = paymentKey(player, kind);
        Payment previous = round.payments.get(key);
        if (previous != null && previous.state != State.REJECTED) return previous.state == State.SUCCESS;
        if (!economy.available()) return false;
        Payment payment = new Payment(player, kind, amount);
        round.payments.put(key, payment);
        store();
        EconomyAccess.Transfer result;
        try {
            result = kind == Kind.CHARGE ? economy.withdraw(player, amount) : economy.deposit(player, amount);
        } catch (RuntimeException exception) {
            result = EconomyAccess.Transfer.UNCERTAIN;
        }
        payment.state = result == EconomyAccess.Transfer.SUCCESS ? State.SUCCESS
                : result == EconomyAccess.Transfer.REJECTED ? State.REJECTED : State.UNCERTAIN;
        store();
        return payment.state == State.SUCCESS;
    }

    private static List<UUID> winners(Round round, String result) {
        String prefix = result.startsWith("winners:") ? "winners:" : "winner:";
        if (!result.startsWith(prefix)) return List.of();
        Set<UUID> winners = new LinkedHashSet<>();
        for (String token : result.substring(prefix.length()).split(",", -1)) {
            int seat = Integer.parseInt(token);
            if (seat < 0 || seat >= round.players.size()) throw failure("error.economy.transaction");
            winners.add(round.players.get(seat));
        }
        return List.copyOf(winners);
    }

    private static boolean uncertain(Round round) {
        return round.payments.values().stream().anyMatch(p -> p.state == State.IN_FLIGHT || p.state == State.UNCERTAIN);
    }

    private static boolean charged(Round round) {
        return round.fee == 0 || round.players.stream().allMatch(player -> {
            Payment charge = round.payments.get(paymentKey(player, Kind.CHARGE));
            return charge != null && charge.state == State.SUCCESS;
        });
    }

    private static boolean resolved(Round round) {
        if (uncertain(round)) return false;
        if (round.cancelled) {
            return round.payments.values().stream().filter(p -> p.kind == Kind.CHARGE && p.state == State.SUCCESS)
                    .allMatch(p -> {
                        Payment refund = round.payments.get(paymentKey(p.player, Kind.REFUND));
                        return refund != null && refund.state == State.SUCCESS;
                    });
        }
        return round.result != null && winners(round, round.result).stream().allMatch(player -> {
            if (award(round, player) == 0) return true;
            Payment reward = round.payments.get(paymentKey(player, Kind.REWARD));
            return reward != null && reward.state == State.SUCCESS;
        });
    }

    private static boolean validate(Round round) {
        boolean recovered = false;
        Objects.requireNonNull(round.room);
        amount(round.fee);
        amount(round.reward);
        if (round.players == null || round.players.size() < 2 || round.players.size() > 6
                || new HashSet<>(round.players).size() != round.players.size() || round.players.contains(null))
            throw new IllegalArgumentException("Invalid economy participants");
        if (round.pool && (round.fee <= 0 || round.reward != 0 || round.precision < 0 || round.precision > 8
                || java.math.BigDecimal.valueOf(round.fee).stripTrailingZeros().scale() > round.precision))
            throw new IllegalArgumentException("Invalid stake currency precision");
        amount(pot(round));
        Objects.requireNonNull(round.payments);
        for (var entry : round.payments.entrySet()) {
            Payment payment = entry.getValue();
            if (payment.kind == null || payment.state == null || !round.players.contains(payment.player)
                    || !entry.getKey().equals(paymentKey(payment.player, payment.kind))
                    || payment.amount != (payment.kind == Kind.REWARD ? award(round, payment.player) : round.fee))
                throw new IllegalArgumentException("Invalid economy payment");
            amount(payment.amount);
            if (payment.amount == 0) throw new IllegalArgumentException("Zero-value economy payment");
            if (payment.state == State.IN_FLIGHT) {
                payment.state = State.UNCERTAIN;
                recovered = true;
            }
        }
        List<UUID> winners = round.result == null ? List.of() : winners(round, round.result);
        if (round.departed == null || !round.players.containsAll(round.departed)
                || !Collections.disjoint(winners, round.departed)
                || !round.departed.isEmpty() && round.result == null)
            throw new IllegalArgumentException("Invalid forfeited participants");
        if (round.result != null && !charged(round)) throw new IllegalArgumentException("Uncharged completed economy round");
        if (round.cancelled && !winners.isEmpty()) throw new IllegalArgumentException("Cancelled winning economy round");
        for (Payment payment : round.payments.values()) {
            if (payment.kind == Kind.REFUND) {
                Payment charge = round.payments.get(paymentKey(payment.player, Kind.CHARGE));
                if (!round.cancelled || charge == null || charge.state != State.SUCCESS)
                    throw new IllegalArgumentException("Economy refund has no successful charge");
            } else if (payment.kind == Kind.REWARD && (round.cancelled || !winners.contains(payment.player))) {
                throw new IllegalArgumentException("Economy reward has no winning result");
            }
        }
        return recovered;
    }

    private void store() {
        if (storageFailed) throw failure("error.economy.storage");
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.add("rounds", JSON.toJsonTree(rounds.values()));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temporary, JSON.toJson(root) + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            storageFailed = true;
            throw failure("error.economy.storage");
        }
    }

    private static void amount(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw failure("error.economy.amount");
    }

    private static RuleViolation failure(String key) { return new RuleViolation(key, "Economy operation failed"); }
    private static String key(UUID room, long seed) { return room + ":" + seed; }
    private static String paymentKey(UUID player, Kind kind) { return kind + ":" + player; }
}

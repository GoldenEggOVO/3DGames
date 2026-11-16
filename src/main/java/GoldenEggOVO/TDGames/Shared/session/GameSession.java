package GoldenEggOVO.TDGames.Shared.session;

import com.google.gson.*;

import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.GameOptions;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;

import java.util.*;

public final class GameSession {
    public enum Phase {
        LOBBY,
        STARTING,
        PLAYING,
        PAUSED,
        FINISHED,
        ABORTED
    }

    public record Seat(UUID id, String name, boolean bot) {}

    public final UUID id;
    public final String kind;
    public final int capacity;
    long seed;
    public final int table;
    public final Map<String, String> options;
    public UUID owner;
    public boolean sideTray;
    public UUID anchorWorld;
    public double anchorX, anchorY, anchorZ;
    private final List<Seat> members = new ArrayList<>();
    public final List<Seat> seats = Collections.unmodifiableList(members);
    private final Set<UUID> confirmations = new HashSet<>();
    public final Set<UUID> ready = Collections.unmodifiableSet(confirmations);
    private final Map<UUID, Long> absences = new HashMap<>();
    public final Map<UUID, Long> offline = Collections.unmodifiableMap(absences);
    Double stake;
    private final Set<UUID> departures = new HashSet<>();
    public final Set<UUID> departed = Collections.unmodifiableSet(departures);
    public final SessionHistory history = new SessionHistory();
    Phase phase = Phase.LOBBY;
    long revision = 0;
    long changed = System.currentTimeMillis();
    public long countdownChanged = -1;
    public int countdownSecond = -1;
    boolean busy = false;
    boolean restoring = false;
    boolean completed = false;
    BoardGame board;
    String result = "";

    public GameSession(UUID id, String kind, int capacity, long seed, int table) {
        this(id, kind, capacity, seed, table, Map.of());
    }

    public GameSession(UUID id, String kind, int capacity, long seed, int table, Map<String, String> options) {
        this.id = id;
        this.kind = kind;
        this.capacity = capacity;
        this.seed = seed;
        this.table = table;
        this.options = GameOptions.validate(kind, options);
    }

    public void beginRestore() { restoring = true; }
    public void endRestore() { restoring = false; }
    public void refreshTurnClock(long now) { changed = now; }
    public void resetRound(long seed) {
        history.clear();
        this.seed = seed;
        board = null;
        result = "";
        completed = false;
        restoring = false;
        clearReady();
        phase = Phase.LOBBY;
        touch();
    }

    public long seed() { return seed; }
    public Double stake() { return stake; }
    public Phase phase() { return phase; }
    public long revision() { return revision; }
    public long changed() { return changed; }
    public boolean busy() { return busy; }
    public boolean restoring() { return restoring; }
    public boolean completed() { return completed; }
    public BoardGame board() { return board; }
    public String result() { return result; }

    void touch() {
        revision++;
        changed = System.currentTimeMillis();
    }

    public boolean removeSeat(UUID player) {
        if (!members.removeIf(seat -> seat.id().equals(player))) return false;
        if (player.equals(owner)) owner = members.isEmpty() ? null : members.getFirst().id();
        absences.remove(player);
        confirmations.remove(player);
        touch();
        return true;
    }

    void restoreSeat(Seat seat) { members.add(seat); }

    public void restoreSeating(List<Seat> previous) {
        members.clear();
        members.addAll(previous);
        confirmations.clear();
        touch();
    }

    public void markOffline(UUID player, long since) { absences.putIfAbsent(player, since); }
    public void markOnline(UUID player) { absences.remove(player); }

    public void markDeparted(Collection<UUID> players) {
        boolean changed = departures.addAll(players);
        players.forEach(absences::remove);
        players.forEach(confirmations::remove);
        if (changed) touch();
    }

    void restoreDeparture(UUID player) { departures.add(player); }

    public void toggleReady(UUID player) {
        if (phase != Phase.LOBBY || seat(player) < 0)
            throw new RuleViolation("error.game-changed", "Room changed");
        if (!confirmations.add(player)) confirmations.remove(player);
        touch();
    }

    public void confirmReady(UUID player) {
        if (seat(player) < 0) throw new RuleViolation("error.game-changed", "Player has no seat");
        confirmations.add(player);
        touch();
    }

    public void restoreReady(Collection<UUID> players) {
        confirmations.addAll(players);
        touch();
    }

    void clearReady() { confirmations.clear(); }

    public void configureStake() { stake = 0.0; }

    public void changeStake(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid room stake");
        if (Objects.equals(stake, amount)) return;
        stake = amount;
        confirmations.clear();
        touch();
    }

    void beginStart() {
        phase = Phase.STARTING;
        busy = true;
        touch();
    }

    void started() {
        phase = completed || board.finished() ? Phase.FINISHED : Phase.PLAYING;
        busy = false;
        touch();
    }

    public boolean finish(String outcome) {
        boolean first = phase != Phase.FINISHED;
        phase = Phase.FINISHED;
        completed = true;
        result = outcome;
        confirmations.clear();
        if (first) touch();
        return first;
    }

    public void pause(String reason) {
        phase = Phase.PAUSED;
        busy = false;
        result = reason;
        touch();
    }

    public void abort(String reason) {
        phase = Phase.ABORTED;
        result = reason;
        touch();
    }

    public BoardGame newBoard() {
        return GameFactory.create(kind, capacity, seed, options);
    }

    public void prepareSeats() {
        if (owner == null && !seats.isEmpty()) owner = seats.getFirst().id();
        if (capacity <= 1) return;
        if (restoring || !history.isEmpty() || !options.containsKey("first") || seats.size() != capacity) return;
        int host = seat(owner);
        if (host < 0) return;
        int first =
                options.get("first").equals("random")
                        ? new SplittableRandom(seed).nextInt(capacity)
                        : options.get("first").equals("host") ? 0 : 1;
        int shift = Math.floorMod(host + first, capacity);
        if (shift != 0) {
            Collections.rotate(members, -shift);
            touch();
        }
    }

    public static Map<String, String> readOptions(JsonObject record) {
        boolean revisedRules = record.has("kind")
                && Set.of("color-eight", "monopoly").contains(record.get("kind").getAsString());
        int version = record.has("rulesVersion") ? record.get("rulesVersion").getAsInt() : 1;
        if (revisedRules ? version != 2 : version != 1)
            throw new IllegalArgumentException(
                    "Unsupported room rules version; convert saved data before upgrading");
        if (!record.has("options")) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : record.getAsJsonObject("options").entrySet()) {
            if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Room rule options must be strings");
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    public int seat(UUID player) {
        if (departed.contains(player)) return -1;
        for (int i = 0; i < seats.size(); i++) if (seats.get(i).id().equals(player)) return i;
        return -1;
    }

    public int occupied() { return seats.size() - departed.size(); }

    public boolean host(UUID player) {
        return player.equals(
                owner != null ? owner : seats.isEmpty() ? null : seats.getFirst().id());
    }

    public void join(UUID player, String name) {
        if (seat(player) >= 0) return;
        if (phase != Phase.LOBBY || seats.size() >= capacity)
            throw new RuleViolation("error.this-room-is-full-or-has-started", "This room is full or has started");
        if (owner == null) owner = player;
        members.add(new Seat(player, name, false));
        changed = System.currentTimeMillis();
        revision++;
    }

    public void fillBots() {
        int previous = seats.size();
        int index = previous;
        while (seats.size() < capacity) {
            UUID botId = UUID.nameUUIDFromBytes(
                    (id + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (seat(botId) < 0) members.add(new Seat(botId, "Bot " + (index + 1), true));
            index++;
        }
        if (seats.size() != previous) touch();
    }

    public void requireAction(UUID player, long expected) {
        if (phase != Phase.PLAYING
                || busy
                || revision != expected
                || seat(player) < 0)
            throw new RuleViolation(
                    "error.game-changed", "Game changed; reopen the table");
    }

    public int turn() {
        return board != null ? board.currentPlayer() : -1;
    }

    public void event(int seat, JsonElement action) {
        history.record(seat, action);
    }
}

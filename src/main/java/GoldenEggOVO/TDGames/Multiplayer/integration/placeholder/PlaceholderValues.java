package GoldenEggOVO.TDGames.Multiplayer.integration.placeholder;

import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.text.RoomText;
import GoldenEggOVO.TDGames.Core.text.MessageText;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class PlaceholderValues {
    private record Balance(String raw, String formatted, long updated) {}
    private record Snapshot(int rooms, int players, boolean economy,
                            Map<UUID, Map<String, String>> members, Map<UUID, Balance> balances) {}

    private final LongSupplier clock;
    private final Map<UUID, Long> balanceRequests = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = new Snapshot(0, 0, false, Map.of(), Map.of());

    public PlaceholderValues(LongSupplier clock) { this.clock = clock; }

    public String resolve(UUID player, String parameter) {
        Snapshot data = snapshot;
        String key = parameter.toLowerCase(Locale.ROOT);
        Map<String, String> member = player == null ? Map.of() : data.members().getOrDefault(player, Map.of());
        return switch (key) {
            case "rooms" -> Integer.toString(data.rooms());
            case "players" -> Integer.toString(data.players());
            case "economy_available" -> Boolean.toString(data.economy());
            case "balance", "balance_formatted" -> {
                if (player == null) yield "";
                balanceRequests.put(player, clock.getAsLong() + 60000);
                Balance balance = data.balances().get(player);
                yield balance == null ? "" : key.equals("balance") ? balance.raw() : balance.formatted();
            }
            case "in_room", "ready", "your_turn" -> member.getOrDefault(key, "false");
            case "room_players", "room_humans", "room_bots", "room_capacity" -> member.getOrDefault(key, "0");
            case "room_id", "room_name", "game", "game_id", "state", "state_id", "turn_player" ->
                    member.getOrDefault(key, "");
            default -> null;
        };
    }

    public void refresh(Collection<GameSession> rooms, EconomyAccess economy) {
        Map<UUID, Map<String, String>> members = new HashMap<>();
        int count = 0, players = 0;
        for (GameSession room : rooms) {
            if (room.phase() == GameSession.Phase.ABORTED) continue;
            count++;
            int humans = (int) room.seats.stream().filter(seat -> !seat.bot() && !room.departed.contains(seat.id())).count();
            players += humans;
            Map<String, String> common = new HashMap<>();
            common.put("in_room", "true");
            common.put("room_id", room.id.toString());
            common.put("room_name", MessageText.plain(RoomText.name(room)));
            common.put("game", MessageText.plain(RoomText.game(room.kind)));
            common.put("game_id", room.kind);
            common.put("state", MessageText.plain(RoomText.phase(room)));
            common.put("state_id", room.phase().name().toLowerCase(Locale.ROOT));
            common.put("room_players", Integer.toString(room.occupied()));
            common.put("room_humans", Integer.toString(humans));
            common.put("room_bots", Integer.toString(room.occupied() - humans));
            common.put("room_capacity", Integer.toString(room.capacity));
            int turn = room.phase() == GameSession.Phase.PLAYING ? room.turn() : -1;
            GameSession.Seat current = turn >= 0 && turn < room.seats.size() ? room.seats.get(turn) : null;
            common.put("turn_player", current == null ? "" : MessageText.plain(RoomText.player(current, turn + 1)));
            for (GameSession.Seat seat : room.seats) {
                if (seat.bot() || room.departed.contains(seat.id())) continue;
                Map<String, String> values = new HashMap<>(common);
                values.put("ready", Boolean.toString(room.ready.contains(seat.id())));
                values.put("your_turn", Boolean.toString(current != null && current.id().equals(seat.id())));
                members.put(seat.id(), Map.copyOf(values));
            }
        }
        long now = clock.getAsLong();
        balanceRequests.entrySet().removeIf(entry -> entry.getValue() < now);
        Map<UUID, Balance> balances = new HashMap<>();
        boolean available = economy.available();
        for (UUID player : balanceRequests.keySet()) {
            Balance cached = snapshot.balances().get(player);
            if (available && cached != null && now - cached.updated() < 5000) {
                balances.put(player, cached);
            } else {
                var balance = available ? economy.balance(player) : OptionalDouble.empty();
                double amount = balance.orElse(Double.NaN);
                boolean valid = Double.isFinite(amount) && amount >= 0;
                balances.put(player, new Balance(valid ? BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() : "",
                        valid ? economy.format(amount) : "", now));
            }
        }
        snapshot = new Snapshot(count, players, available, Map.copyOf(members), Map.copyOf(balances));
    }

    public void clear() {
        balanceRequests.clear();
        snapshot = new Snapshot(0, 0, false, Map.of(), Map.of());
    }
}

package GoldenEggOVO.TDGames.Shared.session;

import com.google.gson.*;
import java.util.*;

public final class SessionRecordCodec {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static JsonObject record(GameSession room) {
        JsonObject record = new JsonObject();
        record.addProperty("id", room.id.toString());
        record.addProperty("kind", room.kind);
        record.addProperty("capacity", room.capacity);
        record.addProperty("seed", room.seed);
        record.addProperty("table", room.table);
        record.addProperty("rulesVersion", Set.of("color-eight", "monopoly").contains(room.kind) ? 2 : 1);
        record.add("options", JSON.toJsonTree(room.options));
        if (room.owner != null) record.addProperty("owner", room.owner.toString());
        record.addProperty("sideTray", room.sideTray);
        if (room.anchorWorld != null) {
            record.addProperty("anchorWorld", room.anchorWorld.toString());
            record.addProperty("anchorX", room.anchorX);
            record.addProperty("anchorY", room.anchorY);
            record.addProperty("anchorZ", room.anchorZ);
        }
        record.addProperty("phase", room.phase.name());
        record.addProperty("completed", room.completed);
        record.addProperty("result", room.result);
        record.addProperty("revision", room.revision);
        if (room.stake != null) record.addProperty("stake", room.stake);
        if (!room.departed.isEmpty()) record.add("departed", JSON.toJsonTree(room.departed));
        record.add("seats", JSON.toJsonTree(room.seats));
        return record;
    }

    public static GameSession session(JsonObject record) {
        GameSession room =
                new GameSession(
                        UUID.fromString(record.get("id").getAsString()),
                        record.get("kind").getAsString(),
                        record.get("capacity").getAsInt(),
                        record.get("seed").getAsLong(),
                        record.get("table").getAsInt(),
                        GameSession.readOptions(record));
        room.owner =
                record.has("owner") ? UUID.fromString(record.get("owner").getAsString()) : null;
        room.sideTray = Set.of("backgammon", "monopoly").contains(room.kind)
                || record.has("sideTray") && record.get("sideTray").getAsBoolean();
        if (record.has("anchorWorld")) {
            room.anchorWorld = UUID.fromString(record.get("anchorWorld").getAsString());
            room.anchorX = record.get("anchorX").getAsDouble();
            room.anchorY = record.get("anchorY").getAsDouble();
            room.anchorZ = record.get("anchorZ").getAsDouble();
        }
        for (JsonElement seat : record.getAsJsonArray("seats"))
            room.restoreSeat(JSON.fromJson(seat, GameSession.Seat.class));
        if (room.owner == null && !room.seats.isEmpty()) room.owner = room.seats.getFirst().id();
        room.history.restore(record.getAsJsonArray("history"));
        room.revision = record.get("revision").getAsLong();
        room.phase = GameSession.Phase.valueOf(record.get("phase").getAsString());
        room.completed = record.has("completed") && record.get("completed").getAsBoolean();
        room.result = record.has("result") ? record.get("result").getAsString() : "";
        if (record.has("stake")) {
            JsonElement stake = record.get("stake");
            if (!stake.isJsonPrimitive() || !stake.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Invalid saved room stake");
            double amount = stake.getAsDouble();
            if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid saved room stake");
            room.stake = amount;
        }
        if (record.has("departed")) {
            for (var player : record.getAsJsonArray("departed")) {
                UUID id = UUID.fromString(player.getAsString());
                if (room.seats.stream().noneMatch(seat -> !seat.bot() && seat.id().equals(id)))
                    throw new IllegalArgumentException("Departed player has no human seat");
                room.restoreDeparture(id);
            }
            if (!room.departed.isEmpty() && room.phase != GameSession.Phase.FINISHED && !room.kind.equals("monopoly"))
                throw new IllegalArgumentException("Departed players require a finished room");
        }
        return room;
    }

    private SessionRecordCodec() {}
}

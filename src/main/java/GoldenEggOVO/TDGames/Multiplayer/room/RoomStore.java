package GoldenEggOVO.TDGames.Multiplayer.room;

import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Shared.session.SessionRecordCodec;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class RoomStore {
    private static final int SCHEMA = 1;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static void write(Path file, Collection<GameSession> rooms)
            throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (JsonWriter output = JSON.newJsonWriter(Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))) {
            output.beginObject();
            output.name("schema").value(SCHEMA);
            output.name("rooms").beginArray();
            for (GameSession room : rooms) {
                if (room.phase() == GameSession.Phase.ABORTED) continue;
                output.beginObject();
                for (var entry : SessionRecordCodec.record(room).entrySet()) {
                    output.name(entry.getKey());
                    JSON.toJson(entry.getValue(), output);
                }
                output.name("history");
                room.history.writeTo(JSON, output);
                output.endObject();
            }
            output.endArray();
            output.endObject();
        } catch (JsonIOException exception) {
            if (exception.getCause() instanceof IOException failure) throw failure;
            throw exception;
        }
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static List<GameSession> read(Path file) throws IOException {
        JsonObject root =
                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
        requireSchema(root);
        List<GameSession> rooms = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rooms"))
            rooms.add(SessionRecordCodec.session(element.getAsJsonObject()));
        return List.copyOf(rooms);
    }

    static void requireSchema(JsonObject root) {
        if (root.has("schema") && root.get("schema").getAsInt() != SCHEMA)
            throw new IllegalArgumentException("Unsupported room file schema");
    }

    private RoomStore() {}
}

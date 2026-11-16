package GoldenEggOVO.TDGames.Singleplayer.progress;

import GoldenEggOVO.TDGames.Shared.session.SessionHistory;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SoloProgressStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> KINDS = List.of("2048", "klondike", "mastermind", "memory");
    private final Path directory;

    public record Progress(String kind, long seed, Map<String, String> options, JsonArray history) {
        public Progress {
            if (!KINDS.contains(kind))
                throw new IllegalArgumentException("Unsupported solo game");
            options = Map.copyOf(options);
            history = history.deepCopy();
            var budget = new SessionHistory();
            for (JsonElement element : history) {
                JsonObject event = element.getAsJsonObject();
                JsonElement action = event.get("action");
                if (event.get("seat").getAsInt() != 0 || !action.isJsonPrimitive()
                        || !action.getAsJsonPrimitive().isString() || !budget.allows(action))
                    throw new IllegalArgumentException("Invalid solo action history");
                budget.record(0, action);
            }
        }

        @Override public JsonArray history() { return history.deepCopy(); }
    }

    public SoloProgressStore(Path directory) { this.directory = directory; }

    public boolean exists(UUID player) {
        return Files.exists(legacyFile(player)) || KINDS.stream().anyMatch(kind -> Files.exists(file(player, kind)));
    }

    public boolean exists(UUID player, String kind) {
        requireKind(kind);
        if (Files.exists(file(player, kind))) return true;
        if (!Files.exists(legacyFile(player))) return false;
        return readableLegacyKind(player).map(kind::equals).orElse(true);
    }

    public List<String> kinds(UUID player) throws IOException {
        Set<String> available = new LinkedHashSet<>();
        readableLegacyKind(player).ifPresent(available::add);
        for (String kind : KINDS) if (Files.exists(file(player, kind))) available.add(kind);
        return KINDS.stream().filter(available::contains).toList();
    }

    public Progress read(UUID player) throws IOException {
        List<Path> candidates = new ArrayList<>();
        readableLegacyKind(player).filter(kind -> !Files.exists(file(player, kind)))
                .ifPresent(kind -> candidates.add(legacyFile(player)));
        for (String kind : KINDS) if (Files.exists(file(player, kind))) candidates.add(file(player, kind));
        if (candidates.isEmpty()) return readFile(legacyFile(player));
        Path latest = candidates.getFirst();
        for (Path candidate : candidates)
            if (Files.getLastModifiedTime(candidate).compareTo(Files.getLastModifiedTime(latest)) >= 0) latest = candidate;
        Progress progress = readFile(latest);
        if (!latest.equals(legacyFile(player)) && !latest.equals(file(player, progress.kind())))
            throw new IllegalArgumentException("Solo progress does not match its game");
        return progress;
    }

    public Progress read(UUID player, String kind) throws IOException {
        requireKind(kind);
        Path target = file(player, kind);
        if (Files.exists(target)) {
            Progress progress = readFile(target);
            if (!progress.kind().equals(kind)) throw new IllegalArgumentException("Solo progress does not match its game");
            return progress;
        }
        Progress legacy = readFile(legacyFile(player));
        if (!legacy.kind().equals(kind)) throw new NoSuchFileException(target.toString());
        return legacy;
    }

    private Progress readFile(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("schema").getAsInt() != 1 || root.get("rulesVersion").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported solo progress version");
        Map<String, String> options = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("options").entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Invalid solo options");
            options.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new Progress(root.get("kind").getAsString(), root.get("seed").getAsLong(), options, root.getAsJsonArray("history"));
    }

    public void write(UUID player, Progress progress) throws IOException {
        Files.createDirectories(directory);
        boolean replaceLegacy = readableLegacyKind(player).filter(progress.kind()::equals).isPresent();
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("rulesVersion", 1);
        root.addProperty("kind", progress.kind());
        root.addProperty("seed", progress.seed());
        root.add("options", JSON.toJsonTree(progress.options()));
        root.add("history", progress.history());
        Path target = file(player, progress.kind());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        if (replaceLegacy) backupLegacy(player);
        try {
            Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (replaceLegacy) Files.delete(legacyFile(player));
    }

    public void delete(UUID player) throws IOException {
        if (exists(player)) delete(player, read(player).kind());
    }

    public void delete(UUID player, String kind) throws IOException {
        requireKind(kind);
        if (readableLegacyKind(player).filter(kind::equals).isPresent()) {
            backupLegacy(player);
            Files.delete(legacyFile(player));
        }
        Files.deleteIfExists(file(player, kind));
    }

    private void backupLegacy(UUID player) throws IOException {
        Path backups = directory.resolve("legacy-backups");
        Files.createDirectories(backups);
        Files.copy(legacyFile(player), backups.resolve(player + "-" + UUID.randomUUID() + ".json"));
    }

    private Optional<String> readableLegacyKind(UUID player) {
        Path path = legacyFile(player);
        if (!Files.exists(path)) return Optional.empty();
        try { return Optional.of(readFile(path).kind()); }
        catch (IOException | RuntimeException ex) { return Optional.empty(); }
    }

    private static void requireKind(String kind) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported solo game");
    }
    private Path legacyFile(UUID player) { return directory.resolve(player + ".json"); }
    private Path file(UUID player, String kind) { return directory.resolve(player + "." + kind + ".json"); }
}

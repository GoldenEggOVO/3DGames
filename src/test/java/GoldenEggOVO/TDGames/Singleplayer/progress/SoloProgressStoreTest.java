package GoldenEggOVO.TDGames.Singleplayer.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoloProgressStoreTest {
    @TempDir Path directory;

    @Test void savingAnotherGameKeepsTheFirstGamesProgress() throws Exception {
        var store = new SoloProgressStore(directory);
        var player = UUID.randomUUID();
        store.write(player, new SoloProgressStore.Progress("2048", 11, Map.of(), new JsonArray()));
        store.write(player, new SoloProgressStore.Progress("klondike", 22, Map.of(), new JsonArray()));
        assertEquals(java.util.List.of("2048", "klondike"), store.kinds(player));
        assertEquals(11, store.read(player, "2048").seed());
        assertEquals(22, store.read(player, "klondike").seed());
        try (var files = Files.walk(directory)) {
            var contents = files.filter(Files::isRegularFile).map(path -> {
                try { return Files.readString(path); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertTrue(contents.stream().anyMatch(s -> s.contains("2048") && s.contains("11")));
            assertTrue(contents.stream().anyMatch(s -> s.contains("klondike") && s.contains("22")));
        }
        store.delete(player, "2048");
        assertFalse(store.exists(player, "2048"));
        assertEquals(22, store.read(player, "klondike").seed());
    }

    @Test void writingAnotherGameDoesNotOverwriteLegacyProgress() throws Exception {
        var player = UUID.randomUUID();
        var legacy = directory.resolve(player + ".json");
        String original = "{\"schema\":1,\"rulesVersion\":1,\"kind\":\"2048\",\"seed\":11,\"options\":{},\"history\":[]}";
        Files.writeString(legacy, original);
        var store = new SoloProgressStore(directory);
        store.write(player, new SoloProgressStore.Progress("klondike", 22, Map.of(), new JsonArray()));
        assertEquals(original, Files.readString(legacy));
        assertEquals(11, store.read(player, "2048").seed());
        assertEquals(22, store.read(player, "klondike").seed());
    }

    @Test void replacingLegacyProgressBacksItUpBeforeUpdatingTheSameGame() throws Exception {
        var player = UUID.randomUUID();
        var legacy = directory.resolve(player + ".json");
        String original = "{\"schema\":1,\"rulesVersion\":1,\"kind\":\"2048\",\"seed\":11,\"options\":{},\"history\":[]}";
        Files.writeString(legacy, original);
        var store = new SoloProgressStore(directory);
        store.write(player, new SoloProgressStore.Progress("2048", 33, Map.of(), new JsonArray()));
        assertEquals(33, store.read(player, "2048").seed());
        try (var backups = Files.list(directory.resolve("legacy-backups"))) {
            assertTrue(backups.anyMatch(path -> {
                try { return Files.readString(path).equals(original); } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        store.delete(player, "2048");
        assertFalse(store.exists(player, "2048"), "Retired legacy saves must not reappear");
    }

    @Test void backupFailureLeavesTheLegacyProgressReadable() throws Exception {
        var player = UUID.randomUUID();
        var legacy = directory.resolve(player + ".json");
        String original = "{\"schema\":1,\"rulesVersion\":1,\"kind\":\"2048\",\"seed\":11,\"options\":{},\"history\":[]}";
        Files.writeString(legacy, original);
        Files.writeString(directory.resolve("legacy-backups"), "blocked");
        var store = new SoloProgressStore(directory);
        assertThrows(java.io.IOException.class,
                () -> store.write(player, new SoloProgressStore.Progress("2048", 33, Map.of(), new JsonArray())));
        assertFalse(Files.exists(directory.resolve(player + ".2048.json.tmp")));
        assertEquals(original, Files.readString(legacy));
        assertEquals(11, store.read(player, "2048").seed());
    }

    @Test void keepsOneIndependentProgressPerPlayerAndCopiesHistory() throws Exception {
        var store = new SoloProgressStore(directory);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var actions = new JsonArray();
        var event = new JsonObject();
        event.addProperty("seat", 0);
        event.addProperty("action", "slide:left");
        actions.add(event);
        var progress = new SoloProgressStore.Progress("2048", 42, Map.of(), actions);
        actions.asList().clear();
        store.write(first, progress);
        store.write(second, new SoloProgressStore.Progress("klondike", 7, Map.of(), new JsonArray()));
        assertEquals("2048", store.read(first).kind());
        assertEquals(42, store.read(first).seed());
        var loaded = store.read(first);
        loaded.history().asList().clear();
        assertEquals(1, loaded.history().size());
        assertEquals("klondike", store.read(second).kind());
        store.delete(first);
        assertFalse(store.exists(first));
        assertTrue(store.exists(second));
    }

    @Test void malformedAndUnsupportedDataIsRetained() throws Exception {
        var store = new SoloProgressStore(directory);
        var player = UUID.randomUUID();
        Path file = directory.resolve(player + ".json");
        Files.writeString(file, "{broken");
        assertThrows(Exception.class, () -> store.read(player));
        assertEquals("{broken", Files.readString(file));
        Files.writeString(file, "{\"schema\":99}");
        assertThrows(IllegalArgumentException.class, () -> store.read(player));
        assertTrue(Files.exists(file));
    }

    @Test void damagedLegacyFileCannotHideOrBlockIndependentGameSaves() throws Exception {
        var store = new SoloProgressStore(directory);
        var player = UUID.randomUUID();
        store.write(player, new SoloProgressStore.Progress("2048", 11, Map.of(), new JsonArray()));
        Path legacy = directory.resolve(player + ".json");
        Files.writeString(legacy, "{broken");

        assertEquals(java.util.List.of("2048"), store.kinds(player));
        assertEquals(11, store.read(player).seed());
        store.write(player, new SoloProgressStore.Progress("klondike", 22, Map.of(), new JsonArray()));
        store.delete(player, "2048");
        assertEquals(java.util.List.of("klondike"), store.kinds(player));
        assertEquals(22, store.read(player, "klondike").seed());
        assertEquals("{broken", Files.readString(legacy));
    }

    @Test void rejectsMultiplayerAndNonStringActionsBeforeReplacingSavedData() throws Exception {
        var store = new SoloProgressStore(directory);
        var player = UUID.randomUUID();
        store.write(player, new SoloProgressStore.Progress("2048", 1, Map.of(), new JsonArray()));
        var actions = new JsonArray();
        var invalid = new JsonObject();
        invalid.addProperty("seat", 1);
        invalid.addProperty("action", "slide:left");
        actions.add(invalid);
        assertThrows(IllegalArgumentException.class, () -> new SoloProgressStore.Progress("2048", 2, Map.of(), actions));
        assertThrows(IllegalArgumentException.class, () -> new SoloProgressStore.Progress("shogi", 2, Map.of(), new JsonArray()));
        assertEquals(1, store.read(player).seed());
    }
}

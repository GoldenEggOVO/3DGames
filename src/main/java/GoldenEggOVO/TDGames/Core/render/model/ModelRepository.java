package GoldenEggOVO.TDGames.Core.render.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.bukkit.Material;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ModelRepository {
    private static final String ROOT = "/vanilla-models/";
    private static final class Builtin {
        static final Map<String, ModelDefinition> MODELS = read(ModelRepository.class::getResourceAsStream);
    }

    public static ModelDefinition get(String id) { return Builtin.MODELS.get(id); }

    public static ModelDefinition require(String id) {
        var model = get(id);
        if (model == null) throw new IllegalArgumentException("Unknown native model: " + id);
        return model;
    }

    public static List<String> ids(String module) {
        String prefix = module + "/";
        return Builtin.MODELS.keySet().stream().filter(id -> id.startsWith(prefix))
                .map(id -> id.substring(prefix.length())).sorted().toList();
    }

    public static Map<String, ModelDefinition> read(Function<String, InputStream> resources) {
        Map<String, String> index = new LinkedHashMap<>();
        try (var input = resources.apply(ROOT + "index.json")) {
            if (input == null) throw new IllegalArgumentException("Missing index");
            try (var reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String id = reader.nextName(), path = reader.nextString();
                    if (!id.matches("(?:tabletop|shared)/[a-z0-9_/-]+")
                            || path.startsWith("/") || path.contains("..") || path.contains("\\") || !path.endsWith(".json"))
                        throw new IllegalArgumentException("Invalid entry: " + id);
                    if (index.putIfAbsent(id, path) != null) throw new IllegalArgumentException("Duplicate model: " + id);
                }
                reader.endObject();
                if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                    throw new IllegalArgumentException("Trailing index data");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load " + ROOT + "index.json", exception);
        }
        Map<String, ModelDefinition> files = new HashMap<>(), result = new LinkedHashMap<>();
        index.forEach((id, path) -> result.put(id, files.computeIfAbsent(path, file -> load(resources, file))));
        return Map.copyOf(result);
    }

    private static ModelDefinition load(Function<String, InputStream> resources, String path) {
        try (var input = resources.apply(ROOT + path)) {
            if (input == null) throw new IllegalArgumentException("Missing model file");
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            fields(root, "blocks", "labels");
            List<ModelDefinition.Block> blocks = new ArrayList<>();
            Set<String> names = new HashSet<>();
            if (root.has("blocks")) for (var element : root.getAsJsonArray("blocks")) {
                var b = element.getAsJsonObject();
                fields(b, "name", "position", "size", "rotation", "material", "brightness");
                String name = b.get("name").getAsString();
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate part: " + name);
                blocks.add(new ModelDefinition.Block(name, point(b.getAsJsonArray("position")), point(b.getAsJsonArray("size")),
                        b.has("rotation") ? point(b.getAsJsonArray("rotation")) : ModelDefinition.Point.ZERO,
                        Material.valueOf(b.get("material").getAsString()), b.has("brightness") ? b.get("brightness").getAsInt() : null));
            }
            List<ModelDefinition.Label> labels = new ArrayList<>();
            if (root.has("labels")) for (var element : root.getAsJsonArray("labels")) {
                var l = element.getAsJsonObject();
                fields(l, "name", "text", "position", "width", "height", "color");
                String name = l.get("name").getAsString();
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate part: " + name);
                labels.add(new ModelDefinition.Label(name, l.get("text").getAsString(), point(l.getAsJsonArray("position")),
                        l.get("width").getAsFloat(), l.get("height").getAsFloat(), l.has("color") ? l.get("color").getAsInt() : null));
            }
            return new ModelDefinition(blocks, labels);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load " + ROOT + path + ": " + exception.getMessage(), exception);
        }
    }

    private static ModelDefinition.Point point(JsonArray values) {
        if (values == null || values.size() != 3) throw new IllegalArgumentException("Expected three coordinates");
        return new ModelDefinition.Point(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }

    private static void fields(JsonObject object, String... allowed) {
        var keys = Set.of(allowed);
        for (String field : object.keySet())
            if (!keys.contains(field)) throw new IllegalArgumentException("Unknown field: " + field);
    }

    private ModelRepository() {}
}

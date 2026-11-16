package GoldenEggOVO.TDGames.Core.render;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayingCardHeads {
    public record Texture(String value, String signature) {}

    private static final class Catalog {
        static final Map<String, List<Texture>> FACES = load("/vanilla-models/shared/cards/head_textures.json");
    }
    private static final Map<Texture, ItemStack> ITEMS = new ConcurrentHashMap<>();

    private PlayingCardHeads() {}

    public static List<Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    public static ItemStack item(Texture texture) {
        return ITEMS.computeIfAbsent(texture, key -> {
            var item = new ItemStack(Material.PLAYER_HEAD);
            var meta = (SkullMeta) item.getItemMeta();
            var profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(key.value.getBytes(StandardCharsets.UTF_8)), "TabletopCard");
            var textures = profile.getTextures();
            textures.setSkin(skin(key));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            item.setItemMeta(meta);
            return item;
        }).clone();
    }

    static java.net.URL skin(Texture texture) {
        String json = new String(java.util.Base64.getDecoder().decode(texture.value()), StandardCharsets.UTF_8);
        String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                .getAsJsonObject("SKIN").get("url").getAsString();
        try {
            return URI.create(url).toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid native card skin URL", exception);
        }
    }

    public static Transformation pose(double width, boolean standing, int tile) {
        return pose(width, width * 4 / 3, standing, tile, 4, 6);
    }

    public static Transformation pose(double width, double height, boolean standing, int tile, int columns, int rows) {
        double cellWidth = width / columns, cellHeight = height / rows;
        var rotation = standing
                ? new Quaternionf()
                : new Quaternionf().rotateX((float) -Math.PI / 2);
        var translation = new Vector3f((float) ((tile % columns - (columns - 1) / 2.0) * cellWidth),
                (float) ((standing ? height : height / 2) - (tile / columns) * cellHeight), 0);
        rotation.transform(translation);
        if (!standing) translation.y += .006f;

        return new Transformation(translation, rotation,
                new Vector3f((float) (cellWidth * 2), (float) (cellHeight * 2), .016f),
                new Quaternionf().rotateY((float) Math.PI));
    }

    public static Map<String, List<Texture>> load(String resource) {
        try (var input = PlayingCardHeads.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing native card head textures: " + resource);
            return Map.copyOf(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Texture>>>() {}.getType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load native playing-card head textures", exception);
        }
    }
}

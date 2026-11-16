package GoldenEggOVO.TDGames.Core.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayingCardHeadsTest {
    @BeforeEach
    void setup() { MockBukkit.mock(); }

    @AfterEach
    void close() { MockBukkit.unmock(); }

    @Test
    void everyStandardCardAndBothJokersHaveTwentyFourSignedMojangTiles() {
        var faces = new java.util.ArrayList<String>();
        for (String suit : List.of("clubs", "diamonds", "hearts", "spades")) {
            for (String rank : List.of("ace", "2", "3", "4", "5", "6", "7",
                    "8", "9", "10", "jack", "queen", "king")) {
                faces.add(suit + "_" + rank);
            }
        }
        faces.addAll(List.of("joker_small", "joker_big"));
        for (String face : faces) {
            var tiles = PlayingCardHeads.tiles(face);
            assertNotNull(tiles, face);
            assertEquals(24, tiles.size(), face);
            for (var tile : tiles) {
                assertTrue(java.util.Base64.getDecoder().decode(tile.signature()).length > 0);
                String json = new String(java.util.Base64.getDecoder().decode(tile.value()),
                        java.nio.charset.StandardCharsets.UTF_8);
                var data = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                String url = data.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                assertTrue(url.matches("https?://textures\\.minecraft\\.net/texture/[a-f0-9]{1,64}"), face);
            }
        }
        assertNull(PlayingCardHeads.tiles("joker_red"));
        assertNull(PlayingCardHeads.tiles("back"), "Public backs use two blocks, not head skins");
    }

    @Test
    void headItemsRetainTheGeneratedMojangSkinAndDoNotShareMutableStacks() throws Exception {
        try (var input = getClass().getResourceAsStream("/native-card-head-sample.json")) {
            var sample = new com.google.gson.Gson().fromJson(
                    new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8),
                    PlayingCardHeads.Texture.class);
            var json = com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(sample.value()),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            json.addProperty("test", "public-profile");
            var texture = new PlayingCardHeads.Texture(java.util.Base64.getEncoder().encodeToString(
                    json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), sample.signature());
            var id = java.util.UUID.nameUUIDFromBytes(texture.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            org.bukkit.profile.PlayerProfile profile = spy(MockBukkit.getMock().createPlayerProfile(id, "TabletopCard"));
            try (var bukkit = mockStatic(org.bukkit.Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> org.bukkit.Bukkit.createPlayerProfile(id, "TabletopCard")).thenReturn(profile);
            var first = PlayingCardHeads.item(texture);
            var second = PlayingCardHeads.item(texture);
            assertEquals(org.bukkit.Material.PLAYER_HEAD, first.getType());
            assertNotSame(first, second);
            first.setAmount(2);
            assertEquals(1, second.getAmount());
            var meta = (org.bukkit.inventory.meta.SkullMeta) second.getItemMeta();
            assertEquals(PlayingCardHeads.skin(sample), profile.getTextures().getSkin());
            assertEquals(id, meta.getOwnerProfile().getUniqueId());
            verify(profile, never()).update();
            }
        }
    }

    @Test
    void twentyFourHeadFacesTileTheCardEnvelopeWithoutGapsAndPointTowardTheOwnerOrUp() {

        var client = new Matrix4f().rotateY((float) Math.PI)
                .translate(0, -.5f, 0).rotateX((float) Math.PI);
        for (boolean standing : List.of(true, false)) {
                for (int index = 0; index < 24; index++) {
                    var pose = PlayingCardHeads.pose(.168, standing, index);
                    var matrix = new Matrix4f().translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                            .scale(pose.getScale()).rotate(pose.getRightRotation()).mul(client);
                    double x = -.084 + (index % 4) * .042;
                    double top = .224 - (index / 4) * (.224 / 6);
                    Vector3f leftTop = matrix.transformPosition(new Vector3f(-.25f, -.5f, -.25f));
                    Vector3f rightBottom = matrix.transformPosition(new Vector3f(.25f, 0, -.25f));
                    assertEquals(x, leftTop.x, 1e-6);
                    assertEquals(x + .042, rightBottom.x, 1e-6);
                    assertEquals(standing ? top : .010, leftTop.y, 1e-6);
                    assertEquals(standing ? top - .224 / 6 : .010, rightBottom.y, 1e-6);
                    assertEquals(standing ? .004 : .112 - top, leftTop.z, 1e-6);
                    assertEquals(standing ? .004 : .112 - top + .224 / 6, rightBottom.z, 1e-6);
                    Vector3f normal = matrix.transformDirection(new Vector3f(0, 0, -1)).normalize();
                    assertEquals(standing ? 0 : 1, normal.y, 1e-6);
                    assertEquals(standing ? 1 : 0, normal.z, 1e-6);
                }
        }
    }

}

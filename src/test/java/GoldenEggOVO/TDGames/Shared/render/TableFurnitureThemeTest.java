package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.render.cards.RoundCardTable;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableFurnitureThemeTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void everyNativeCardGameUsesIvoryBordersAndBlueBacksWithSeparatedFaces() throws Exception {
        for (String game : List.of("color-eight", "doudizhu", "liars-bar", "texas-holdem")) {
            var fixture = new TableViewTest.Fixture(game, game.equals("doudizhu") ? 3 : 2);
            Object table = TableViewTest.field(fixture.view,
                    game.equals("color-eight") ? "handTable" : "playingTable");
            Map<?, ?> cards = (Map<?, ?>) TableViewTest.field(table,
                    game.equals("color-eight") ? "publicPieces" : "publicCards");
            int backs = 0;
            for (Object card : cards.values()) {
                List<Entity> parts = (List<Entity>) TableViewTest.field(card, "parts");
                if (parts.size() != 2 || !(parts.getFirst() instanceof BlockDisplay border)
                        || !(parts.getLast() instanceof BlockDisplay panel)) continue;
                backs++;
                assertEquals(Material.SMOOTH_QUARTZ, material(border), game + " ivory card border");
                assertEquals(Material.BLUE_CONCRETE, material(panel), game + " blue card back");
                var body = fixture.transforms.get(border);
                var inset = fixture.transforms.get(panel);
                assertTrue(inset.getScale().x() < body.getScale().x(), game + " visible ivory margin");
                assertTrue(inset.getScale().y() < body.getScale().y()
                        || inset.getScale().z() < body.getScale().z(), game + " inset remains thin");
            }
            assertTrue(backs > 0, game + " must expose at least one public card back");
            fixture.view.close();
        }
    }

    @Test void mapBoardsKeepTheirTraditionalPlayingColors() {
        for (String game : List.of("chess", "draughts", "reversi", "gomoku", "go", "go9",
                "go13", "xiangqi", "checkers", "ludo")) {
            var board = GameFactory.create(game, game.equals("checkers") ? 6 : 2, 0);
            var image = BoardMapArtwork.draw(new TableGeometry(game, board.cells()));
            Color frame = new Color(image.getRGB(1, 1));
            assertTrue(frame.getRed() <= 75 && frame.getRed() > frame.getGreen()
                    && frame.getGreen() > frame.getBlue(), game + " dark walnut frame");
            long recognizable = 0;
            for (int y = 12; y < 244; y++) for (int x = 12; x < 244; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int r = pixel.getRed(), g = pixel.getGreen(), b = pixel.getBlue();
                boolean matches = switch (game) {
                    case "reversi" -> g > r * 1.5 && g > b * 1.3;
                    case "ludo" -> r > 220 && g > 220 && b > 200;
                    default -> r > g && g > b && r > 150;
                };
                if (matches) recognizable++;
            }
            assertTrue(recognizable > 5000, game + " must retain its recognizable playing surface");
        }
    }

    @Test void roundFurnitureKeepsItsBrownRimFlush() {
        var parts = RoundCardTable.parts();
        assertEquals(54, parts.size(), "The trim must reuse the existing furniture budget");
        var accents = parts.stream().filter(p -> p.material() == Material.BROWN_CONCRETE).toList();
        assertEquals(36, accents.size(), "The brown rim keeps all existing segments");
        for (var part : accents) {
            assertEquals(.20, part.d(), 1e-9);
            assertTrue(Math.hypot(part.x(), part.z()) > 1.3, "Accents must stay on the outer rim");
            assertTrue(part.bottom() + part.h() <= .01725 + 1e-9, "Accents must remain almost flush");
        }
    }

    @Test void tableFramesUseWarmMaterialsWithoutRecoloringThePlayingSurface() {
        for (String id : List.of("shared/square_table", "mahjong/table", "ludo/dice_tray_compact",
                "ludo/dice_tray_large", "ludo/tray_legs_compact", "ludo/tray_legs_large",
                "yacht/dice_table", "yacht/score_table")) {
            var parts = ModelRepository.require("tabletop/" + id).blocks();
            assertTrue(parts.stream().anyMatch(p -> p.material() == Material.BROWN_CONCRETE
                    || p.material() == Material.BROWN_TERRACOTTA), id + " needs a brown frame");
            assertTrue(parts.stream().noneMatch(p -> p.material().name().contains("DARK_OAK")),
                    id + " must not retain the wooden furniture skin");
        }
        for (String game : List.of("2048", "backgammon", "battleship", "dots_and_boxes", "hex",
                "klondike", "mancala", "mastermind", "memory", "mini_shogi", "monopoly",
                "nine_mens_morris", "shogi")) {
            var parts = ModelRepository.require("tabletop/" + game + "/table").blocks();
            assertTrue(parts.stream().filter(p -> p.name().startsWith("rim_"))
                    .allMatch(p -> p.material() == Material.BROWN_CONCRETE), game + " brown frame");
            assertTrue(parts.stream().noneMatch(p -> p.name().startsWith("corner_")),
                    game + " smooth frame corners");
        }
    }

    @Test void connectFourRackCrossingsHaveSeparateFrontAndBackPlanes() {
        var fixture = new TableViewTest.Fixture("connectfour");
        var vertical = fixture.entities.stream().filter(BlockDisplay.class::isInstance)
                .filter(e -> Math.abs(fixture.transforms.get(e).getScale().x() - .035) < 1e-6)
                .toList();
        var horizontal = fixture.entities.stream().filter(BlockDisplay.class::isInstance)
                .filter(e -> Math.abs(fixture.transforms.get(e).getScale().x() - 2) < 1e-6
                        && Math.abs(fixture.transforms.get(e).getScale().y() - .035) < 1e-6)
                .toList();
        assertEquals(8, vertical.size());
        assertEquals(7, horizontal.size());
        for (var upright : vertical) for (var crossbar : horizontal) {
            var a = fixture.transforms.get(upright);
            var b = fixture.transforms.get(crossbar);
            assertTrue(Math.abs(a.getTranslation().z() - b.getTranslation().z()) >= .0004,
                    "Rack crossings must not share a textured front plane");
            assertTrue(Math.abs(a.getTranslation().z() + a.getScale().z()
                    - b.getTranslation().z() - b.getScale().z()) >= .0004,
                    "Rack crossings must not share a textured rear plane");
        }
        fixture.view.close();
    }

    private static Material material(BlockDisplay display) {
        var capture = ArgumentCaptor.forClass(BlockData.class);
        verify(display).setBlock(capture.capture());
        return capture.getValue().getMaterial();
    }
}

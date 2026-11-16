package GoldenEggOVO.TDGames.Shared.render;

import GoldenEggOVO.TDGames.Shared.interaction.BoardSelection;

import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapBoardSurfaceTest {
    private static final double MAP_Y = -.023359375, OFFSET = -.024909375;
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        return (T) TableViewTest.field(target, name);
    }

    private static ResourcePackService mixed(TableViewTest.Fixture f, Player other) {
        var pack = mock(ResourcePackService.class);
        SessionTestSupport.module(f.plugin, "pack", pack);
        when(pack.managed()).thenReturn(true);
        when(pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
        for (Player player : List.of(f.player, other)) {
            when(player.isOnline()).thenReturn(true);
            when(player.getLocation()).thenReturn(f.view.origin.clone().add(0, 1, 2));
            when(f.plugin.allowed(player)).thenReturn(true);
        }
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(pack.packed(other)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, other));
        f.view.tick();
        return pack;
    }

    private static double bottom(TableViewTest.Fixture f, Entity part) {
        Matrix4f matrix = f.poses.get(part);
        if (matrix == null) {
            var pose = f.transforms.get(part);
            matrix = new Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation());
        }
        double min = Double.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++)
            min = Math.min(min, matrix.transformPosition(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1)).y());
        return part.getLocation().getY() - f.view.origin.getY() + min;
    }

    private static double textPlane(TableViewTest.Fixture f, Entity part) {
        Location at = part.getLocation();
        var pose = f.transforms.get(part);
        var matrix = new Matrix4f().rotateY((float) Math.toRadians(-at.getYaw()))
                .rotateX((float) Math.toRadians(at.getPitch())).translate(pose.getTranslation())
                .rotate(pose.getLeftRotation()).scale(pose.getScale()).rotate(pose.getRightRotation());
        return at.getY() - f.view.origin.getY() + matrix.transformPosition(new Vector3f()).y();
    }

    @Test void allTenNativeMapBoardsPlaceActualPieceBoundsJustAboveTheRenderedMap() throws Exception {
        for (String kind : List.of("chess", "xiangqi", "gomoku", "go", "go9", "go13", "reversi", "draughts", "checkers", "ludo")) {
            var f = Set.of("gomoku", "go", "go9", "go13").contains(kind)
                    ? new TableViewTest.Fixture(kind, "place:0,0") : new TableViewTest.Fixture(kind);
            Map<?, ?> tokens = field(f.view, "tokens");
            assertFalse(tokens.isEmpty(), kind);
            for (Object token : tokens.values()) {
                List<Entity> parts = field(token, "parts");
                double low = parts.stream().filter(BlockDisplay.class::isInstance).mapToDouble(part -> bottom(f, part)).min().orElseThrow();
                double stack = ((Number) field(field(token, "token"), "stack")).doubleValue();
                assertEquals(.00145 + stack * f.view.geometry.spacing, low - MAP_Y, 2e-6, kind);
                if (kind.equals("xiangqi")) {
                    Location logical = field(token, "to");
                    for (Entity part : parts) if (part instanceof TextDisplay)
                        assertEquals(logical.getY() + OFFSET + f.view.geometry.spacing * .235, part.getLocation().getY(), 1e-6);
                }
            }
            for (Entity part : f.entities) if (part instanceof ItemFrame)
                assertEquals(0, part.getLocation().getY() - f.view.origin.getY(), 1e-6, "Do not move frames");
            long litFrames = f.entities.stream().filter(GlowItemFrame.class::isInstance).count();
            assertEquals(Set.of("go", "go9", "go13").contains(kind) ? 4 : 0, litFrames, kind);
            f.view.close();
        }
    }

    @Test void mixedTokensAndPrivateHoverKeepPackedCoordinatesAndReuseTheirExistingParts() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        Player packed = mock(Player.class);
        mixed(f, packed);
        Map<?, ?> tokens = field(f.view, "tokens");
        for (Object token : tokens.values()) {
            Location logical = field(token, "to");
            for (Entity part : MapBoardSurfaceTest.<List<Entity>>field(token, "parts"))
                assertEquals(logical.getY() + (part instanceof ItemDisplay ? 0 : OFFSET), part.getLocation().getY(), 1e-6);
        }
        for (Player viewer : List.of(f.player, packed)) {
            f.view.cursor(viewer, null, "e3");
            Object overlay = MapBoardSurfaceTest.<Map<UUID, ?>>field(f.view, "overlays").get(viewer.getUniqueId());
            List<BlockDisplay> edges = field(overlay, "hover");
            int count = f.entities.size();
            f.view.cursor(viewer, null, "e4");
            assertEquals(count, f.entities.size(), "Moving hover reuses four entities");
            for (Entity edge : edges) assertEquals(.004 + (viewer == packed ? 0 : OFFSET), bottom(f, edge), 1e-6);
        }
        f.view.close();
    }

    @Test void switchingViewerModeAtTheSameHoverRepositionsThePrivateFrame() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var pack = mixed(f, mock(Player.class));
        f.view.cursor(f.player, null, "e3");
        Object before = MapBoardSurfaceTest.<Map<UUID, ?>>field(f.view, "overlays").get(f.player.getUniqueId());
        when(pack.packed(f.player)).thenReturn(true); f.view.tick();
        f.view.cursor(f.player, null, "e3");
        Object after = MapBoardSurfaceTest.<Map<UUID, ?>>field(f.view, "overlays").get(f.player.getUniqueId());
        for (Entity edge : MapBoardSurfaceTest.<List<Entity>>field(after, "hover")) assertEquals(.004, bottom(f, edge), 1e-6);
        for (Entity edge : MapBoardSurfaceTest.<List<Entity>>field(before, "hover")) verify(edge).remove();
        when(pack.packed(f.player)).thenReturn(false); f.view.tick();
        f.view.cursor(f.player, null, "e3");
        Object restored = MapBoardSurfaceTest.<Map<UUID, ?>>field(f.view, "overlays").get(f.player.getUniqueId());
        for (Entity edge : MapBoardSurfaceTest.<List<Entity>>field(restored, "hover")) assertEquals(.004 + OFFSET, bottom(f, edge), 1e-6);
        f.view.close();
    }

    @Test void selectedDestinationHintsFollowTheViewerSurface() throws Exception {
        for (boolean packed : new boolean[] {false, true}) {
            var f = new TableViewTest.Fixture("chess");
            var pack = mixed(f, mock(Player.class));
            when(pack.packed(f.player)).thenReturn(packed); f.view.tick();
            var pick = new BoardSelection(f.room.id, f.room.revision(), "e2",
                    BoardSelection.sourceActions(f.room.board(), 0, "e2"));
            f.view.cursor(f.player, pick, "e3");
            Object overlay = MapBoardSurfaceTest.<Map<UUID, ?>>field(f.view, "overlays").get(f.player.getUniqueId());
            int hints = 0;
            for (Entity entity : MapBoardSurfaceTest.<List<Entity>>field(overlay, "entities"))
                if (Math.abs(f.transforms.get(entity).getScale().y() - .018) < 1e-6) {
                    assertEquals(.023 + (packed ? 0 : OFFSET), bottom(f, entity), 1e-6); hints++;
                }
            assertEquals(2, hints);
            f.view.close();
        }
    }

    @Test void mixedSharedLastMoveAndRiverUseOnlyTheirOwnAudienceAndCleanUp() throws Exception {
        var f = new TableViewTest.Fixture("xiangqi");
        Player packed = mock(Player.class);
        var pack = mixed(f, packed);
        f.move(f.room.board().legalActions(0).stream().filter(a -> a.startsWith("move:")).findFirst().orElseThrow());
        for (int i = 0; i < 8; i++) f.view.tick();
        Map<Entity, Boolean> layers = field(field(f.view, "boardAudience"), "layers");
        List<Entity> last = field(f.view, "lastMove");
        assertEquals(8, last.size(), "Mixed viewers need one four-edge frame per surface");
        for (Entity part : last) {
            Boolean layer = layers.get(part);
            assertNotNull(layer);
            assertEquals(.004 + (layer ? 0 : OFFSET), bottom(f, part), 1e-6);
            verify(layer ? f.player : packed, never()).showEntity(f.plugin, part);
        }
        List<Entity> rivers = new ArrayList<>();
        for (Entity part : MapBoardSurfaceTest.<List<Entity>>field(f.view, "furniture"))
            if (part instanceof TextDisplay && part != field(f.view, "title")) rivers.add(part);
        assertEquals(2, rivers.size());
        for (Entity river : rivers) {
            Boolean layer = layers.get(river);
            assertNotNull(layer);
            assertEquals(.0015, textPlane(f, river) - (layer ? .00155 : MAP_Y), 1e-6,
                    "The flat river glyph plane clears its own board surface");
            verify(layer ? f.player : packed, never()).showEntity(f.plugin, river);
        }
        when(pack.packed(f.player)).thenReturn(true); f.view.tick();
        assertEquals(4, last.size());
        for (Entity part : rivers) if (!Boolean.TRUE.equals(layers.get(part))) verify(part).remove();
        f.view.close();
        assertTrue(layers.isEmpty());
        for (Entity part : last) verify(part).remove();
    }

    @Test void changingLayersDuringMovementKeepsTheLogicalAnimationAndNativeOffset() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        f.move("move:e2:e4"); f.view.tick();
        Object token = MapBoardSurfaceTest.<Map<String, ?>>field(f.view, "tokens").get("e4");
        int frame = field(token, "frame");
        Player other = mock(Player.class);
        var pack = mixed(f, other);
        assertEquals(frame + 1, ((Number) field(token, "frame")).intValue());
        when(pack.packed(f.player)).thenReturn(true); f.view.tick();
        assertEquals(frame + 2, ((Number) field(token, "frame")).intValue());
        assertTrue(MapBoardSurfaceTest.<List<Entity>>field(token, "parts").stream().allMatch(ItemDisplay.class::isInstance));
        when(pack.packed(other)).thenReturn(false); f.view.tick();
        when(pack.packed(f.player)).thenReturn(false); f.view.tick();
        for (int tick = 0; tick < 8; tick++) f.view.tick();
        Location logical = field(token, "to");
        for (Entity part : MapBoardSurfaceTest.<List<Entity>>field(token, "parts"))
            assertEquals(logical.getY() + (part instanceof ItemDisplay ? 0 : OFFSET), part.getLocation().getY(), 1e-6);
        f.view.close();
    }

    @Test void goDeadMarksAndReversiFlipKeepTheNativeSurfaceAfterLayerChanges() throws Exception {
        var go = new TableViewTest.Fixture("go9", "place:0,0", "place:8,8", "place:1,0", "pass", "pass");
        mixed(go, mock(Player.class)); go.move("dead:0,0");
        Object stone = MapBoardSurfaceTest.<Map<String, ?>>field(go.view, "tokens").get("0,0");
        Location logical = field(stone, "to");
        for (Entity mark : MapBoardSurfaceTest.<List<Entity>>field(stone, "deadMarks"))
            assertEquals(logical.getY() + OFFSET, mark.getLocation().getY(), 1e-6);
        go.view.close();
        var f = new TableViewTest.Fixture("reversi");
        f.move(f.room.board().legalActions(0).getFirst()); f.view.tick();
        Object flipping = MapBoardSurfaceTest.<Map<?, ?>>field(f.view, "tokens").values().stream()
                .filter(token -> { try { return ((Number) field(token, "flipFrame")).intValue() < 10; } catch (Exception ex) { throw new AssertionError(ex); } })
                .findFirst().orElseThrow();
        int flipFrame = field(flipping, "flipFrame");
        var pack = mixed(f, mock(Player.class));
        assertEquals(flipFrame + 1, ((Number) field(flipping, "flipFrame")).intValue());
        when(pack.packed(f.player)).thenReturn(true); f.view.tick();
        when(pack.packed(f.player)).thenReturn(false); f.view.tick();
        for (int tick = 0; tick < 12; tick++) f.view.tick();
        for (Object token : MapBoardSurfaceTest.<Map<?, ?>>field(f.view, "tokens").values()) {
            double low = MapBoardSurfaceTest.<List<Entity>>field(token, "parts").stream()
                    .filter(BlockDisplay.class::isInstance).mapToDouble(part -> bottom(f, part)).min().orElseThrow();
            assertEquals(.00145, low - MAP_Y, 2e-6);
        }
        f.view.close();
    }

    @Test void controllerUsesEachViewersRenderedBoardAndPieceCollisionPlane() throws Exception {
        for (boolean packed : new boolean[] {false, true}) for (String kind : List.of("chess", "reversi")) {
            var f = new TableViewTest.Fixture(kind);
            var pack = mixed(f, mock(Player.class));
            when(pack.packed(f.player)).thenReturn(packed); f.view.tick();
            String id = kind.equals("chess") ? "e3" : "3,3";
            var cell = f.room.board().cells().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
            double y = kind.equals("chess") ? (packed ? .00155 : MAP_Y) : .020 + (packed ? 0 : OFFSET);
            Location target = f.view.origin.clone().add(f.view.geometry.x(cell), y, f.view.geometry.z(cell));
            Location eye = target.clone().add(-1, kind.equals("chess") ? .25 : 0, 0);
            eye.setDirection(target.toVector().subtract(eye.toVector()));
            when(f.player.getEyeLocation()).thenReturn(eye);
            var controller = mock(TableWorldController.class, CALLS_REAL_METHODS);
            SessionTestSupport.set(controller, "plugin", f.plugin);
            var aimed = TableWorldController.class.getDeclaredMethod("aimed", Player.class, TableView.class);
            aimed.setAccessible(true);
            assertEquals(id, aimed.invoke(controller, f.player, f.view), kind + " packed=" + packed);
            f.view.close();
        }
    }
}

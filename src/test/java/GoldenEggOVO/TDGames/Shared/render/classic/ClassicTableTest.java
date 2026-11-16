package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.game.GameCatalog;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Multiplayer.game.battleship.BattleshipGame;
import GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame;
import GoldenEggOVO.TDGames.Singleplayer.game.klondike.KlondikeGame;
import GoldenEggOVO.TDGames.Shared.game.cards.PlayingCard;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Multiplayer.game.hex.HexGame;
import net.kyori.adventure.text.Component;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassicTableTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    static final class Fixture {
        final TDGamesPlugin plugin = GoldenEggOVO.TDGames.SessionTestSupport.plugin();
        final World world = mock(World.class);
        final Player owner = mock(Player.class), observer = mock(Player.class);
        final ResourcePackService pack = mock(ResourcePackService.class);
        final List<Entity> entities = new ArrayList<>();
        final Map<Entity, Transformation> transforms = new HashMap<>();
        final Map<Entity, Material> materials = new HashMap<>();
        final GameSession room;
        final ClassicTable table;
        final Location origin = new Location(world, 0, 80, 0);

        @SuppressWarnings("unchecked") Fixture(ClassicGame game, String kind) {
            when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
            when(plugin.resourcePackState()).thenReturn(pack);
            when(plugin.pack()).thenReturn(pack);
            when(pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
            when(pack.managed()).thenReturn(true);
            UUID id = UUID.randomUUID();
            when(owner.getUniqueId()).thenReturn(id);
            when(observer.getUniqueId()).thenReturn(UUID.randomUUID());
            for (Player player : List.of(owner, observer)) {
                when(player.isOnline()).thenReturn(true);
                when(player.getWorld()).thenReturn(world);
                when(player.getLocation()).thenReturn(origin.clone().add(0, 1, 2));
                when(plugin.allowed(player)).thenReturn(true);
            }
            when(world.getPlayers()).thenReturn(List.of(owner, observer));
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(inv -> {
                Entity entity = mock((Class<? extends Entity>) inv.getArgument(1));
                entities.add(entity);
                when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
                when(entity.isValid()).thenReturn(true);
                when(entity.getLocation()).thenReturn(((Location) inv.getArgument(0)).clone());
                if (entity instanceof Display display) {
                    doAnswer(call -> { transforms.put(entity, call.getArgument(0)); return null; })
                            .when(display).setTransformation(any(Transformation.class));
                    when(display.getTransformation()).thenAnswer(call -> transforms.get(entity));
                }
                if (entity instanceof BlockDisplay display) doAnswer(call -> { materials.put(entity, ((BlockData)call.getArgument(0)).getMaterial()); return null; })
                        .when(display).setBlock(any(BlockData.class));
                ((Consumer<Entity>) inv.getArgument(2)).accept(entity);
                if (entity instanceof Display) verify(entity).setVisibleByDefault(false);
                return entity;
            });
            room = new GameSession(UUID.randomUUID(), kind, game.playerCount(), 0, 0);
            SessionTestSupport.mutableSeats(room).add(new GameSession.Seat(id, "Owner", false));
            SessionTestSupport.set(room, "board", game);
            SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
            table = new ClassicTable(plugin, room, origin, new NamespacedKey("3dgames", "classic"));
        }
    }

    @SuppressWarnings("unchecked") private <T> T field(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test void classicCursorTracksTheBoardOrCardSurfaceForEveryGame() {
        for (String kind : GameCatalog.CLASSIC_IDS) {
            int players = Set.of("2048", "klondike").contains(kind) ? 1 : 2;
            var f = new Fixture((ClassicGame) GameFactory.create(kind, players, 7L), kind);
            Map<?, ?> views = field(f.table, "views");
            Object own = views.get(f.owner.getUniqueId());
            ClassicLayout layout = field(own, "layout");
            var slot = layout.slots().stream().filter(s -> !s.id().startsWith("@")).findFirst().orElseThrow();
            f.table.hover(f.owner, slot.id());
            Map<?, ?> highlights = field(own, "highlights");
            var edges = (List<?>) highlights.get(slot.id());
            assertNotNull(edges, kind);
            assertEquals(slot.hex() ? 6 : 4, edges.size(), kind);
            double surface = kind.equals("klondike") ? slot.height() + .014 : kind.equals("dots-and-boxes") ? .0135
                    : kind.equals("mastermind") ? .018 : .010;
            for (Object edge : edges) {
                var display = (BlockDisplay) edge;
                var pose = f.transforms.get(display);
                assertEquals(surface + .0015, display.getLocation().getY() - f.origin.getY() + pose.getTranslation().y(), 1e-6, kind);
                assertEquals(.003, pose.getScale().y(), 1e-6, kind);
            }
            f.table.close();
        }
    }

    @Test void controlsUseSpectralOutlinesWithoutRaisedSelectionFrames() {
        Set<String> checked = new HashSet<>();
        for (String kind : GameCatalog.CLASSIC_IDS) {
            int players = Set.of("2048", "klondike").contains(kind) ? 1 : 2;
            var f = new Fixture((ClassicGame)GameFactory.create(kind, players, 7L), kind);
            try {
                Map<UUID, ?> views = field(f.table, "views");
                Object own = views.get(f.owner.getUniqueId());
                Map<String, List<Entity>> controls = field(own, "controls");
                for (var entry : controls.entrySet()) {
                    f.table.hover(f.owner, entry.getKey());
                    Map<String, ?> highlights = field(own, "highlights");
                    assertFalse(highlights.containsKey(entry.getKey()), kind + " " + entry.getKey());
                    verify(entry.getValue().getFirst(), atLeastOnce()).setGlowing(true);
                    checked.add(entry.getKey().split("/")[0]);
                }
            } finally { f.table.close(); }
        }
        assertTrue(checked.containsAll(Set.of("@control", "@palette", "@ship")), checked.toString());
    }

    @Test void privateShipsAreInvisibleAtSpawnAndOnlyShownToTheirOwner() {
        var game = new BattleshipGame(2, 1, Map.of());
        game.apply(0, "auto");
        var f = new Fixture(game, "battleship");
        Map<UUID, ?> viewers = field(f.table, "views");
        Object own = viewers.get(f.owner.getUniqueId()), observer = viewers.get(f.observer.getUniqueId());
        Map<String, ?> ownCells = field(own, "visuals"), publicCells = field(observer, "visuals");
        String ship = game.cellsFor(0).stream().filter(c -> c.piece().startsWith("ship_")).findFirst().orElseThrow().id();
        Cell publicCell = field(publicCells.get(ship), "cell");
        assertEquals("water", publicCell.piece());
        for (Entity entity : this.<List<Entity>>field(ownCells.get(ship), "parts")) {
            verify(f.owner).showEntity(f.plugin, entity);
            verify(f.observer, never()).showEntity(f.plugin, entity);
        }
        f.table.close();
        for (Entity entity : f.entities) verify(entity).remove();
    }

    @Test void rightClickTargetsCoverTheColorBoardAndBothFleetsAndAreReleasedWithTheTable() {
        for (String kind : List.of("mastermind", "battleship")) {
            var f = new Fixture((ClassicGame) GameFactory.create(kind, 2, 7L), kind);
            var targets = f.entities.stream().filter(Interaction.class::isInstance).map(Interaction.class::cast).toList();
            assertEquals(kind.equals("mastermind") ? 1 : 2, targets.size());
            for (var target : targets) {
                verify(target).setResponsive(true);
                verify(target).setPersistent(false);
                assertEquals(f.origin.getY(), target.getLocation().getY(), .012);
                verify(f.owner).showEntity(f.plugin, target);
                verify(f.observer).showEntity(f.plugin, target);
            }
            if (targets.size() == 2) {
                assertTrue(targets.getFirst().getLocation().getX() < f.origin.getX());
                assertTrue(targets.getLast().getLocation().getX() > f.origin.getX());
            }
            f.table.sync();
            f.table.close();
            targets.forEach(target -> verify(target).remove());
        }
    }

    @Test void unchangedStateAndUnchangedCellsKeepTheirEntities() {
        var game = new MemoryGame(2, 1, Map.of());
        var f = new Fixture(game, "memory");
        int initial = f.entities.size();
        Map<UUID, ?> views = field(f.table, "views");
        Object own = views.get(f.owner.getUniqueId());
        Map<String, ?> visuals = field(own, "visuals");
        Object untouched = visuals.get("m1");
        for (int i = 0; i < 20; i++) f.table.sync();
        assertEquals(initial, f.entities.size());
        game.apply(0, "flip:m0"); SessionTestSupport.bump(f.room); f.table.sync();
        assertSame(untouched, visuals.get("m1"));
        for (Entity entity : this.<List<Entity>>field(untouched, "parts")) verify(entity, never()).remove();
        List<Entity> furniture = field(f.table, "nativeFurniture");
        for (Entity entity : furniture) verify(entity, never()).remove();
        f.table.close();
    }

    @Test void resourcePackSwitchRebuildsOnlyThatViewAndNeverChangesRules() {
        var game = new MemoryGame(2, 2, Map.of());
        var f = new Fixture(game, "memory");
        List<Cell> before = game.cells();
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> observerVisuals = field(views.get(f.observer.getUniqueId()), "visuals");
        Object kept = observerVisuals.get("m0");
        when(f.pack.packed(f.owner)).thenReturn(true);
        f.table.sync();
        assertSame(kept, observerVisuals.get("m0"));
        assertEquals(before, game.cells());
        verify(f.pack).item("classic_memory_board");
        f.table.close();
    }

    @Test void packedChessBoxUsesOneCompleteFurnitureModelAndItsLongEdgeOpensMenu() {
        var game = (ClassicGame) GameFactory.create("mancala", 2, 7L);
        var f = new Fixture(game, "mancala");
        when(f.pack.packed(f.owner)).thenReturn(true);
        f.table.sync();
        List<Entity> furniture = field(f.table, "packedFurniture");
        assertEquals(1, furniture.size(), "The packed chess box includes its frame and legs");
        assertInstanceOf(ItemDisplay.class, furniture.getFirst());
        assertEquals(f.origin, furniture.getFirst().getLocation());
        assertTrue(Double.isFinite(f.table.menuHit(f.origin.clone().add(1.48, 2, 0), new Vector(0, -1, 0))),
                "The complete long tabletop must support sneak + right click");
        f.table.close();
    }

    @Test void privateControlsFaceTheOppositeSeat() {
        var f = new Fixture((ClassicGame) GameFactory.create("battleship", 2, 7L), "battleship");
        SessionTestSupport.mutableSeats(f.room).add(new GameSession.Seat(f.observer.getUniqueId(), "Opponent", false));
        f.table.sync();
        Map<UUID, ?> views = field(f.table, "views");
        Object other = views.get(f.observer.getUniqueId());
        ClassicLayout layout = field(other, "layout");
        var button = layout.slot("@control/0");
        assertNotNull(button);
        assertTrue(button.z() < 0, "Controls must move to the second player's edge with their hit boxes");
        assertEquals(button.id(), layout.hit(button.x(), button.z()));
        f.table.close();
    }

    @Test void backgammonCountersNeverShareOverlappingTopFaces() {
        var f = new Fixture((ClassicGame) GameFactory.create("backgammon", 2, 7L), "backgammon");
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
        for (Object visual : visuals.values()) {
            List<Entity> parts = field(visual, "parts");
            var blocks = parts.stream().filter(BlockDisplay.class::isInstance).toList();
            for (int i = 0; i < blocks.size(); i++) for (int j = i + 1; j < blocks.size(); j++) {
                var a = blocks.get(i); var b = blocks.get(j);
                var pa = f.transforms.get(a); var pb = f.transforms.get(b);
                double topA = a.getLocation().getY() + pa.getTranslation().y() + pa.getScale().y();
                double topB = b.getLocation().getY() + pb.getTranslation().y() + pb.getScale().y();
                if (Math.abs(topA - topB) > 1e-6) continue;
                double ax = a.getLocation().getX() + pa.getTranslation().x(), bx = b.getLocation().getX() + pb.getTranslation().x();
                double az = a.getLocation().getZ() + pa.getTranslation().z(), bz = b.getLocation().getZ() + pb.getTranslation().z();
                assertFalse(Math.min(ax + pa.getScale().x(), bx + pb.getScale().x()) - Math.max(ax, bx) > 1e-6
                        && Math.min(az + pa.getScale().z(), bz + pb.getScale().z()) - Math.max(az, bz) > 1e-6);
            }
        }
        f.table.close();
    }

    @Test void pieceSelectionAndAmbiguousDiceDoNotCommitAnyMove() {
        ClassicGame game = new ClassicGame() {
            public String id() { return "backgammon"; }
            public int playerCount() { return 2; }
            public int currentPlayer() { return 0; }
            public boolean finished() { return false; }
            public String outcome() { return "ongoing"; }
            public List<Cell> cells() { return List.of(new Cell("p1", 0, 0, "2", 0), new Cell("p2", 1, 0, "", -1)); }
            public List<String> legalActions(int seat) { return seat == 0 ? List.of("move:p1:p2:1", "move:p1:p2:2") : List.of(); }
            public void apply(int seat, String action) { fail("The view must return actions without committing them"); }
            public Map<String, String> publicInfo() { return Map.of("dice", "1,2"); }
        };
        var f = new Fixture(game, "backgammon");
        assertNull(f.table.action(f.owner, "p1"));
        assertNull(f.table.action(f.owner, "p2"));
        Map<UUID, ?> views = field(f.table, "views");
        Object own = views.get(f.owner.getUniqueId());
        List<String> controls = field(own, "controlActions");
        assertEquals(List.of("move:p1:p2:1", "move:p1:p2:2", "@cancel"), controls);
        assertEquals("move:p1:p2:2", f.table.action(f.owner, "@control/1"));
        assertNull(f.table.action(f.observer, "p1"));
        f.table.close();
    }

    @Test void monopolyTradeIsEditedOnTheTableAndDiscardedWhenTheRoomChanges() {
        var game = new MonopolyGame(2, 12, Map.of());
        var f = new Fixture(game, "monopoly");
        Map<UUID, ?> views = field(f.table, "views");
        Object own = views.get(f.owner.getUniqueId());
        List<String> controls = field(own, "controlActions");
        int trade = java.util.stream.IntStream.range(0, controls.size()).filter(i -> controls.get(i).startsWith("trade:")).findFirst().orElseThrow();
        assertNull(f.table.action(f.owner, "@control/" + trade), "Opening a trade must not submit a preset offer");
        assertNotNull(field(own, "input"));
        assertTrue(this.<List<String>>field(own, "controlActions").contains("@edit:give-cash"));
        SessionTestSupport.bump(f.room);
        f.table.sync();
        assertNull(field(own, "input"));
        assertTrue(this.<List<String>>field(own, "controlActions").stream().noneMatch(a -> a.startsWith("@edit:")));
        f.table.close();
    }

    @Test void classicTableKeepsPrivateGameInformationInTheActionBar() {
        var f = new Fixture((ClassicGame) GameFactory.create("mancala", 2, 7), "mancala");
        Map<UUID, ?> views = field(f.table, "views");
        Component state = field(views.get(f.owner.getUniqueId()), "feedbackText");
        assertFalse(Component.empty().equals(state));
        verify(f.owner, atLeastOnce()).sendActionBar(any(Component.class));
        f.table.close();
    }

    @Test void largeTableMenuUsesItsActualBoundsAndCellsUseSameRayCoordinates() {
        var f = new Fixture(new MonopolyGame(2, 2, Map.of()), "monopoly");
        assertTrue(Double.isFinite(f.table.menuHit(f.origin.clone().add(1.7, 2, 0), new Vector(0, -1, 0))));
        assertEquals(Double.POSITIVE_INFINITY, f.table.menuHit(f.origin.clone().add(2, 2, 0), new Vector(0, -1, 0)));
        Map<UUID, ?> views = field(f.table, "views");
        ClassicLayout layout = field(views.get(f.owner.getUniqueId()), "layout");
        var slot = layout.slot("p0");
        var hit = f.table.hit(f.owner, f.origin.clone().add(slot.x(), 2, slot.z()), new Vector(0, -1, 0));
        assertNotNull(hit);
        assertEquals("p0", hit.id());
        f.table.close();
    }

    @Test void klondikeCoveredCardsUseOnlyOneRowOfHeadsAndSelectionExpandsTheFace() {
        var game = new KlondikeGame(1, 7, Map.of());
        List<List<PlayingCard>> columns = field(game, "columns");
        columns.forEach(List::clear);
        Arrays.fill(this.<int[]>field(game, "hidden"), 0);
        columns.get(0).addAll(List.of(new PlayingCard("king", "spades", 13), new PlayingCard("queen", "hearts", 12)));
        var f = new Fixture(game, "klondike");
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
        assertEquals(5, this.<List<Entity>>field(visuals.get("t0_0"), "parts").size());
        assertEquals(25, this.<List<Entity>>field(visuals.get("t0_1"), "parts").size());
        List<Entity> covered = field(visuals.get("t0_0"), "parts"), top = field(visuals.get("t0_1"), "parts");
        var client = new Matrix4f().rotateY((float)Math.PI).translate(0, -.5f, 0).rotateX((float)Math.PI);
        ClassicLayout layout = field(views.get(f.owner.getUniqueId()), "layout");
        var strip = layout.slot("t0_0");
        for (Entity head : covered.subList(1, covered.size())) {
            Transformation pose = f.transforms.get(head);
            var matrix = new Matrix4f().translate(pose.getTranslation()).rotate(pose.getLeftRotation())
                    .scale(pose.getScale()).rotate(pose.getRightRotation()).mul(client);
            var left = matrix.transformPosition(new Vector3f(-.25f, -.5f, -.25f));
            var right = matrix.transformPosition(new Vector3f(.25f, 0, -.25f));
            double frontZ = head.getLocation().getZ() - f.origin.getZ() + left.z;
            assertEquals(strip.z() - strip.depth()/2, frontZ, .00001);
            assertTrue(right.z > left.z);
            double frontY = head.getLocation().getY() + left.y;
            double nextBodyTop = top.getFirst().getLocation().getY() + f.transforms.get(top.getFirst()).getScale().y;
            assertTrue(frontY < nextBodyTop, "Covered face must be below the next card's opaque body");
        }
        f.table.hover(f.owner, "t0_0");
        assertNull(f.table.action(f.owner, "t0_0"));
        Map<String, List<Entity>> frames = field(views.get(f.owner.getUniqueId()), "highlights");
        for (Entity edge : frames.get("t0_0"))
            assertEquals(strip.expanded().height() + .0155, edge.getLocation().getY() - f.origin.getY(), 1e-6);
        List<Entity> selectedParts = field(visuals.get("t0_0"), "parts");
        assertEquals(25, selectedParts.size());
        assertTrue(selectedParts.get(1).getLocation().getY() > top.getFirst().getLocation().getY() + .05,
                "The selected complete face must be raised above the covering card");
        var selectedHit = f.table.hit(f.owner, f.origin.clone().add(strip.x(), 2, strip.renderZ() + .12), new Vector(0, -1, 0));
        assertNotNull(selectedHit);
        assertEquals("t0_0", selectedHit.id());
        assertEquals("move:t0_0:t1", f.table.action(f.owner, "t1"));
        assertEquals(2, game.columns().get(0).size());
        f.table.close();
    }

    @Test void diceGamesExposeTheirSideTableForRollingAndOpeningTheRoomMenu() {
        for (String kind : List.of("backgammon", "monopoly")) {
            var game = (ClassicGame) GameFactory.create(kind, 2, 7L);
            var f = new Fixture(game, kind);
            double x = kind.equals("monopoly") ? 2.775 : 2.3;
            Location eye = f.origin.clone().add(x, 2, 0);
            var hit = f.table.hit(f.owner, eye, new Vector(0, -1, 0));
            assertNotNull(hit, kind);
            assertEquals("@roll", hit.id());
            assertTrue(Double.isFinite(f.table.menuHit(eye, new Vector(0, -1, 0))), kind);
            if (game.currentPlayer() == 0 && game.legalActions(0).contains("roll"))
                assertEquals("roll", f.table.action(f.owner, "@roll"));
            assertNull(f.table.action(f.observer, "@roll"));
            f.table.close();
        }
    }

    @Test void shogiPieceTipsFollowTheirForwardMovesInBothRenderModes() {
        for (String kind : List.of("shogi", "mini-shogi")) for (boolean packed : List.of(false, true)) {
            var game = (ClassicGame) GameFactory.create(kind, 2, 7L);
            var f = new Fixture(game, kind);
            when(f.pack.packed(f.owner)).thenReturn(packed);
            f.table.sync();
            Map<UUID, ?> views = field(f.table, "views");
            Object view = views.get(f.owner.getUniqueId());
            ClassicLayout layout = field(view, "layout");
            Map<String, ?> visuals = field(view, "visuals");
            for (int seat = 0; seat < 2; seat++) {
                int owner = seat;
                var pawn = game.cells().stream().filter(cell -> cell.owner() == owner && cell.piece().equals("P"))
                        .findFirst().orElseThrow();
                int nextY = pawn.y() + (seat == 0 ? 1 : -1);
                var destination = layout.slot(game.cells().stream().filter(cell -> cell.x() == pawn.x() && cell.y() == nextY)
                        .findFirst().orElseThrow().id());
                double forward = destination.z() - layout.slot(pawn.id()).z();
                List<Entity> parts = field(visuals.get(pawn.id()), "parts");
                double tipDirection;
                if (packed) {
                    var pose = f.transforms.get(parts.getFirst());
                    var matrix = new Matrix4f().rotate(pose.getLeftRotation()).scale(pose.getScale())
                            .rotate(pose.getRightRotation()).rotateY((float)Math.PI);
                    tipDirection = matrix.transformDirection(new Vector3f(0, 0, -1)).z();
                } else {
                    tipDirection = parts.get(0).getLocation().getZ() - parts.get(3).getLocation().getZ();
                }
                assertTrue(tipDirection * forward > 0, kind + " packed=" + packed + " owner=" + owner);
            }
            f.table.close();
        }
    }

    @Test void shogiHeldPieceSelectionReturnsDropWithoutExposingOtherSeatsControls() {
        ClassicGame game = new ClassicGame() {
            public String id() { return "shogi"; }
            public int playerCount() { return 2; }
            public int currentPlayer() { return 0; }
            public boolean finished() { return false; }
            public String outcome() { return "ongoing"; }
            public List<Cell> cells() { return List.of(new Cell("0,0", 0, 0, "", -1)); }
            public List<String> legalActions(int seat) { return seat == 0 ? List.of("drop:P:0,0") : List.of(); }
            public List<String> controlActions(int seat) { return List.of(); }
            public void apply(int seat, String action) { fail("No view-side mutation"); }
            public Map<String, String> publicInfo() { return Map.of("hand0", "{P=1}", "hand1", "{S=1}"); }
        };
        var f = new Fixture(game, "shogi");
        assertNull(f.table.action(f.owner, "h0P"));
        assertEquals("drop:P:0,0", f.table.action(f.owner, "0,0"));
        assertNull(f.table.action(f.observer, "h0P"));
        f.table.close();
    }

    @Test void hexSwapKeepsThePlacedStoneColorBoundToItsTargetEdges() {
        var game = new HexGame(2, 0, Map.of());
        game.apply(0, "place:5,5");
        var f = new Fixture(game, "hex");
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
        List<Entity> before = field(visuals.get("5,5"), "parts");
        assertEquals(Material.RED_CONCRETE, f.materials.get(before.getFirst()));
        game.apply(1, "swap"); SessionTestSupport.bump(f.room); f.table.sync();
        List<Entity> after = field(visuals.get("5,5"), "parts");
        assertEquals(Material.RED_CONCRETE, f.materials.get(after.getFirst()));
        f.table.close();
    }

    @Test void monopolyAssetsAndDeedsExplainTheGameWithoutSubmittingMoves() {
        var game = new MonopolyGame(2, 0, Map.of());
        var f = new Fixture(game, "monopoly");
        Map<UUID, ?> views = field(f.table, "views");
        Object own = views.get(f.owner.getUniqueId());
        List<String> controls = field(own, "controlActions");
        int assets = controls.indexOf("@assets");
        assertTrue(assets >= 0);
        clearInvocations(f.owner);
        assertNull(f.table.action(f.owner, "@control/" + assets));
        var message = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(f.owner).sendMessage(message.capture());
        String ledger = GoldenEggOVO.TDGames.Core.text.MessageText.plain(message.getValue());
        assertTrue(ledger.contains("1500"));
        assertTrue(ledger.contains("32"));
        assertTrue(ledger.contains("12"));
        clearInvocations(f.owner);
        assertNull(f.table.action(f.owner, "p1"));
        verify(f.owner).sendMessage(message.capture());
        String deed = GoldenEggOVO.TDGames.Core.text.MessageText.plain(message.getValue());
        assertTrue(deed.contains("60"));
        assertFalse(deed.contains("p1"));
        assertTrue(f.room.history.isEmpty());
        assertEquals(0, game.revision());
        f.table.close();
    }

    @Test void monopolyCashAndBuildingNoticesAreSentOncePerRecordedAction() {
        var game = new MonopolyGame(2, 0, Map.of());
        this.<int[]>field(game, "owners")[1] = 0;
        this.<int[]>field(game, "owners")[3] = 0;
        var f = new Fixture(game, "monopoly");
        clearInvocations(f.owner);
        game.apply(0, "build:p1");
        f.room.event(0, new com.google.gson.JsonPrimitive("build:p1"));
        SessionTestSupport.bump(f.room); f.table.sync(); f.table.sync();
        var message = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(f.owner).sendMessage(message.capture());
        String notice = GoldenEggOVO.TDGames.Core.text.MessageText.plain(message.getValue());
        assertTrue(notice.contains("-50"));
        assertTrue(notice.contains("1450"));
        assertTrue(notice.contains("1 house"));
        f.table.close();
    }

    @Test void monopolyBuildingsMortgageAndPawnLocationsInvalidateOnlyAffectedCells() {
        var game = new MonopolyGame(2, 0, Map.of());
        this.<int[]>field(game, "owners")[1] = 0;
        var f = new Fixture(game, "monopoly");
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
        Object before = visuals.get("p1"), untouched = visuals.get("p3"), start = visuals.get("p0");
        this.<int[]>field(game, "buildings")[1] = 4;
        SessionTestSupport.bump(f.room); f.table.sync();
        assertNotSame(before, visuals.get("p1"));
        assertSame(untouched, visuals.get("p3"));
        List<Entity> houseParts = field(visuals.get("p1"), "parts");
        assertEquals(4, houseParts.stream().filter(entity -> f.materials.get(entity) == Material.GREEN_CONCRETE).count());
        Object houses = visuals.get("p1");
        this.<int[]>field(game, "buildings")[1] = 5;
        SessionTestSupport.bump(f.room); f.table.sync();
        assertNotSame(houses, visuals.get("p1"));
        this.<int[]>field(game, "buildings")[1] = 0;
        this.<boolean[]>field(game, "mortgaged")[1] = true;
        this.<int[]>field(game, "positions")[0] = 1;
        SessionTestSupport.bump(f.room); f.table.sync();
        assertNotSame(start, visuals.get("p0"));
        List<Entity> mortgageParts = field(visuals.get("p1"), "parts");
        assertTrue(mortgageParts.stream().anyMatch(entity -> f.materials.get(entity) == Material.BLACK_CONCRETE));
        assertSame(untouched, visuals.get("p3"));
        f.table.close();
    }

    @Test void lastActionHighlightsPublicShotsAndNeverSecretShipPlacement() {
        var game = new BattleshipGame(2, 0, Map.of());
        game.apply(0, "place:b0_0");
        var f = new Fixture(game, "battleship");
        f.room.event(0, new com.google.gson.JsonPrimitive("place:b0_0"));
        SessionTestSupport.bump(f.room); f.table.sync();
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> markers = field(views.get(f.observer.getUniqueId()), "highlights");
        assertFalse(markers.containsKey("b0_0"));
        game.apply(0, "auto"); game.apply(1, "auto"); game.apply(0, "confirm"); game.apply(1, "confirm");
        game.apply(0, "shoot:b1_0");
        f.room.event(0, new com.google.gson.JsonPrimitive("shoot:b1_0"));
        SessionTestSupport.bump(f.room); f.table.sync();
        assertTrue(markers.containsKey("b1_0"));
        f.table.close();
    }

    @Test void drawThreeWasteRendersTwoExposedSideStripsAndOneWholeTopCard() {
        var game = new KlondikeGame(1, 31, Map.of("draw", "3"));
        game.apply(0, "draw");
        var f = new Fixture(game, "klondike");
        Map<UUID, ?> views = field(f.table, "views");
        Map<String, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
        List<Entity> parts = field(visuals.get("w"), "parts");
        assertEquals(39, parts.size());
        f.table.close();
    }

    @Test void hoverDescriptionsRefreshOnChangeAndOncePerTenPulses() {
        var f = new Fixture(new MemoryGame(2, 31, Map.of()), "memory");
        clearInvocations(f.owner);
        f.table.hover(f.owner, "m0");
        verify(f.owner).sendActionBar(any(net.kyori.adventure.text.Component.class));
        for (int i = 0; i < 9; i++) { f.table.hover(f.owner, "m0"); f.table.tick(); }
        verify(f.owner).sendActionBar(any(net.kyori.adventure.text.Component.class));
        f.table.tick();
        verify(f.owner, times(2)).sendActionBar(any(net.kyori.adventure.text.Component.class));
        f.table.close();
    }
    @Test void monopolyTradeHighlightsSelectablePropertiesAndUsesTheBoardInsteadOfExtraButtons() throws Exception {
        var game = new MonopolyGame(2, 12, Map.of());
        var owners = MonopolyGame.class.getDeclaredField("owners"); owners.setAccessible(true);
        ((int[]) owners.get(game))[1] = 0;
        ((int[]) owners.get(game))[3] = 0;
        var f = new Fixture(game, "monopoly");
        try {
            Map<UUID, ?> views = field(f.table, "views");
            Object own = views.get(f.owner.getUniqueId());
            List<String> controls = field(own, "controlActions");
            int trade = controls.indexOf(controls.stream().filter(a -> a.startsWith("trade:")).findFirst().orElseThrow());
            f.table.action(f.owner, "@control/" + trade);
            controls = field(own, "controlActions");
            f.table.action(f.owner, "@control/" + controls.indexOf("@edit:give-properties"));
            controls = field(own, "controlActions");
            assertTrue(controls.stream().noneMatch(a -> a.startsWith("@edit:property:")), "Select properties on the board");
            Map<String, Material> colors = field(own, "highlightColors");
            assertEquals(Material.LIME_CONCRETE, colors.get("p1"));
            assertEquals(Material.LIME_CONCRETE, colors.get("p3"));
            f.table.hover(f.owner, "p1");
            f.table.action(f.owner, "p1");
            assertEquals(Material.CYAN_CONCRETE, colors.get("p1"), "The selected property stays distinct while hovered");
            assertEquals(Material.LIME_CONCRETE, colors.get("p3"));
            assertNull(this.<Map<String, Material>>field(views.get(f.observer.getUniqueId()), "highlightColors").get("p1"));
            f.table.action(f.owner, "p1");
            assertEquals(Material.YELLOW_CONCRETE, colors.get("p1"));
            assertEquals(0, game.revision());
        } finally { f.table.close(); }
    }

    @Test void battleshipPreviewExplainsWhetherPlacementIsValid() {
        var game = new BattleshipGame(2, 3, Map.of());
        var f = new Fixture(game, "battleship");
        try {
            Map<UUID, ?> views = field(f.table, "views");
            Object own = views.get(f.owner.getUniqueId());
            f.table.hover(f.owner, "b0_0");
            Component valid = field(own, "lastFeedback");
            assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(valid).contains(GoldenEggOVO.TDGames.Core.text.MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("hint.battleship.place-valid"))), valid.toString());
            f.table.hover(f.owner, "b0_9");
            Component invalid = field(own, "lastFeedback");
            assertTrue(GoldenEggOVO.TDGames.Core.text.MessageText.plain(invalid).contains(GoldenEggOVO.TDGames.Core.text.MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("hint.battleship.place-invalid"))), invalid.toString());
        } finally { f.table.close(); }
    }

    @Test void twoThousandFortyEightStatusMatchesTheCurrentCameraMode() {
        var f = new Fixture((ClassicGame) GameFactory.create("2048", 1, 7L), "2048");
        try {
            var controls = mock(GoldenEggOVO.TDGames.Shared.interaction.TablePlayerController.class);
            when(f.plugin.tablePlayers()).thenReturn(controls);
            when(controls.hasTableFocus(f.owner)).thenReturn(true);
            SessionTestSupport.bump(f.room); f.table.sync();
            Map<UUID, ?> views = field(f.table, "views");
            Component status = field(views.get(f.owner.getUniqueId()), "feedbackText");
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(status);
            String controlsText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                    GoldenEggOVO.TDGames.Core.text.Language.component("hint.2048.controls"));
            assertTrue(text.contains(controlsText), text);
        } finally { f.table.close(); }
    }

    @Test void twoThousandFortyEightNumbersContrastWithTheirTilesAndFitByLength() {
        var game = (ClassicGame) GameFactory.create("2048", 1, 7L);
        int[] samples = {2, 64, 128, 2048, 16384, 1048576, 1073741824};
        SessionTestSupport.set(game, "board", new int[] {2, 64, 128, 2048, 16384, 1048576, 1073741824, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        var f = new Fixture(game, "2048");
        try {
            Map<?, ?> views = field(f.table, "views");
            Map<?, ?> visuals = field(views.get(f.owner.getUniqueId()), "visuals");
            float[] scales = new float[samples.length];
            for (int i = 0; i < samples.length; i++) {
                List<Entity> parts = field(visuals.get(i % 4 + "," + i / 4), "parts");
                TextDisplay label = (TextDisplay) parts.stream().filter(TextDisplay.class::isInstance).findFirst().orElseThrow();
                var value = org.mockito.ArgumentCaptor.forClass(Component.class);
                verify(label).text(value.capture());
                assertEquals(i == 0 ? net.kyori.adventure.text.format.NamedTextColor.BLACK
                        : net.kyori.adventure.text.format.NamedTextColor.WHITE, value.getValue().color());
                scales[i] = f.transforms.get(label).getScale().x();
                assertTrue(scales[i] * Integer.toString(samples[i]).length() <= 1.65f,
                        "Long labels must keep a bounded width");
            }
            assertTrue(scales[0] > .45f, "Single-digit labels should be legible on the tile");
            assertTrue(scales[0] > scales[3]);
        } finally { f.table.close(); }
    }

    @Test void slantedRaysTargetVisibleEmptyCellsAndThinLines() {
        record Target(String kind, String cell, double eyeX, double eyeZ, double top) {}
        var targets = List.of(
                new Target("mastermind", "draft0", 0, 2.25, 0),
                new Target("nine-mens-morris", "1", 0, 2.25, 0),
                new Target("dots-and-boxes", "h0,0", 0, 2.25, .002),
                new Target("battleship", "b0_0", 0, 2.25, 0),
                new Target("backgammon", "p2", -2.25, 0, 0),
                new Target("shogi", "a2", -2.25, 0, 0),
                new Target("mini-shogi", "b2", 0, 2.25, 0),
                new Target("klondike", "w", 0, 2.25, .008));
        for (var target : targets) for (boolean packed : List.of(false, true)) {
            var game = (ClassicGame) GameFactory.create(target.kind(), GameCatalog.defaultCapacity(target.kind()), 7L);
            var f = new Fixture(game, target.kind());
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Map<UUID, ?> views = field(f.table, "views");
                ClassicLayout layout = field(views.get(f.owner.getUniqueId()), "layout");
                var slot = layout.slot(target.cell());
                Location eye = f.origin.clone().add(target.eyeX(), .589, target.eyeZ());
                Vector ray = f.origin.clone().add(slot.x(), slot.height() + target.top(), slot.z())
                        .toVector().subtract(eye.toVector());
                var hit = f.table.hit(f.owner, eye, ray);
                assertNotNull(hit, target.kind());
                assertEquals(target.cell(), hit.id(), target.kind());
            } finally { f.table.close(); }
        }
    }

    @Test void hexRayTargetsTheVisibleEmptyCellAndTheTopOfAnOccupiedPiece() {
        for (boolean packed : List.of(false, true)) {
            var game = (ClassicGame) GameFactory.create("hex", 2, 7L);
            game.apply(0, "place:5,5");
            var f = new Fixture(game, "hex");
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Map<UUID, ?> views = field(f.table, "views");
                ClassicLayout layout = field(views.get(f.owner.getUniqueId()), "layout");
                Location eye = f.origin.clone().add(0, .589, -2.25);
                for (String id : List.of("5,4", "5,5")) {
                    var slot = layout.slot(id);
                    double top = slot.height() + (id.equals("5,5") ? .045 : 0);
                    Vector ray = f.origin.clone().add(slot.x(), top, slot.z()).toVector().subtract(eye.toVector());
                    var hit = f.table.hit(f.owner, eye, ray);
                    assertNotNull(hit, id);
                    assertEquals(id, hit.id(), "Aiming at the visible " + id + " surface should select that cell");
                }
            } finally { f.table.close(); }
        }
    }

    @Test void battleshipGridGapsPickTheNearestCellInBothRenderingModes() {
        for (boolean packed : List.of(false, true)) {
            var f = new Fixture(new BattleshipGame(2, 1, Map.of()), "battleship");
            try {
                when(f.pack.packed(f.owner)).thenReturn(packed); f.table.sync();
                Map<UUID, ?> views = field(f.table, "views");
                ClassicLayout layout = field(views.get(f.owner.getUniqueId()), "layout");
                var slot = layout.slot("b0_0");
                var insideGap = f.table.hit(f.owner, f.origin.clone().add(slot.x() + .068, 2, slot.z()), new Vector(0, -1, 0));
                assertNotNull(insideGap, "Narrow gaps should not miss");
                assertEquals("b0_0", insideGap.id());
                var nextCell = f.table.hit(f.owner, f.origin.clone().add(slot.x() + .072, 2, slot.z()), new Vector(0, -1, 0));
                assertNotNull(nextCell);
                assertEquals("b0_1", nextCell.id());
            } finally { f.table.close(); }
        }
    }

    @Test void battleshipStatusSwitchesFromSetupToShootingForEachSeat() {
        var game = new BattleshipGame(2, 3, Map.of());
        game.apply(0, "auto"); game.apply(1, "auto"); game.apply(0, "confirm"); game.apply(1, "confirm");
        var f = new Fixture(game, "battleship");
        try {
            SessionTestSupport.mutableSeats(f.room).add(new GameSession.Seat(f.observer.getUniqueId(), "Opponent", false));
            f.table.sync();
            Map<UUID, ?> views = field(f.table, "views");
            String own = GoldenEggOVO.TDGames.Core.text.MessageText.plain(field(views.get(f.owner.getUniqueId()), "feedbackText"));
            String other = GoldenEggOVO.TDGames.Core.text.MessageText.plain(field(views.get(f.observer.getUniqueId()), "feedbackText"));
            assertTrue(own.contains(GoldenEggOVO.TDGames.Core.text.MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("hint.battleship.fire"))), own);
            assertTrue(other.contains(GoldenEggOVO.TDGames.Core.text.MessageText.plain(GoldenEggOVO.TDGames.Core.text.Language.component("hint.battleship.wait"))), other);
            assertFalse(own.contains("Left-click a ship"), own);
            assertFalse(own.contains("Carrier"), own);
        } finally { f.table.close(); }
    }

}

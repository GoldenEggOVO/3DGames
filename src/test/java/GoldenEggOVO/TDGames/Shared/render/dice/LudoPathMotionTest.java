package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Multiplayer.game.ludo.LudoGame;
import GoldenEggOVO.TDGames.Multiplayer.game.ludo.LudoOptions;
import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LudoPathMotionTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest
    @CsvSource({"4,0,sk5,sk6,sk7", "4,1,sk18,sk19,sk20", "4,2,sk31,sk32,sk33",
            "4,3,sk44,sk45,sk46", "2,1,sk31,sk32,sk33"})
    void movingPawnVisitsEachCornerInsteadOfCuttingAcrossTheBoard(int players, int seat,
            String first, String second, String destination) throws Exception {
        var f = prepared(players, seat, 4, 3);
        try {
            Object pawn = pawn(f, seat + ":1"); List<Entity> originalParts = List.copyOf(parts(pawn));
            f.move("move:" + seat * 4 + ":" + destination);
            assertSame(pawn, pawn(f, seat + ":1"));
            for (String waypoint : List.of(first, second, destination)) {
                tick(f, 2); assertAtCell(f, pawn, waypoint);
            }
            assertEquals(originalParts, parts(pawn), "Movement must retain the same physical pawn parts");
            assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @ParameterizedTest
    @CsvSource({"4,0,sk50,ld0_0,ld0_1,ld0_2", "2,1,sk24,ld2_0,ld2_1,ld2_2"})
    void homeEntryUsesTheActualColorLane(int players, int seat, String ring,
            String home0, String home1, String destination) throws Exception {
        var f = prepared(players, seat, 49, 4);
        try {
            Object pawn = pawn(f, seat + ":1");
            f.move("move:" + seat * 4 + ":" + destination);
            for (String waypoint : List.of(ring, home0, home1, destination)) {
                tick(f, 2); assertAtCell(f, pawn, waypoint);
            }
            assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @Test void sixStepsFinishWithinTwelveUpdatesWithoutDelayingTheRules() throws Exception {
        var f = prepared(4, 0, 4, 6);
        try {
            Object pawn = pawn(f, "0:1"); f.move("move:0:sk10");
            assertEquals(10, ((int[]) TableViewTest.field(f.room.board(), "progress"))[0]);
            for (String waypoint : List.of("sk5", "sk6", "sk7", "sk8", "sk9", "sk10")) {
                assertTrue(f.view.moving()); tick(f, 2); assertAtCell(f, pawn, waypoint);
            }
            assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @ParameterizedTest @CsvSource({"0,sk0", "1,sk26"})
    void deployingOnSixGoesStraightFromItsOwnYardToItsStart(int seat, String destination) throws Exception {
        var f = prepared(2, seat, -1, 6);
        try {
            Object pawn = pawn(f, seat + ":1");
            f.move("move:" + seat * 4 + ":" + destination); tick(f, 6);
            assertAtCell(f, pawn, destination); assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @Test void overshootingTheFinishOnlyVisitsTheRemainingHomeCells() throws Exception {
        var f = new TableViewTest.Fixture("ludo");
        try {
            SessionTestSupport.set(f.room, "board", new LudoGame(2, 0, new LudoOptions(true, false, false, false)));
            prepare(f, 0, 54, 6); Object pawn = pawn(f, "0:1");
            f.move("move:0:go0"); tick(f, 3); assertAtCell(f, pawn, "ld0_4");
            tick(f, 3); assertAtCell(f, pawn, "go0"); assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @Test void capturedPawnReturnsDirectlyToItsYardWhileTheActorFollowsTheRing() throws Exception {
        var f = prepared(4, 0, 4, 6);
        try {
            ((int[]) TableViewTest.field(f.room.board(), "progress"))[4] = 49;
            SessionTestSupport.bump(f.room); f.view.sync(); tick(f, 12);
            Object victim = pawn(f, "1:1"); f.move("move:0:sk10"); tick(f, 6);
            assertSame(victim, pawn(f, "1:1")); assertAtCell(f, victim, "ba1_0");
            assertTrue(f.view.moving(), "The actor still has three ring steps to finish");
            tick(f, 6); assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @Test void stackingOnlyRepositionsTheOtherPawnWithoutMovingItAroundTheRing() throws Exception {
        var f = prepared(4, 0, 4, 6);
        try {
            ((int[]) TableViewTest.field(f.room.board(), "progress"))[1] = 10;
            SessionTestSupport.bump(f.room); f.view.sync(); tick(f, 12);
            Object stacked = pawn(f, "0:2"); f.move("move:0:sk10"); tick(f, 6);
            assertSame(stacked, pawn(f, "0:2")); assertAtCell(f, stacked, "sk10");
            Location expected = (Location) TableViewTest.field(stacked, "to");
            assertEquals(expected.getY(), position(stacked).getY(), 1e-9);
            tick(f, 6); assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @Test void redirectingHalfwayPreservesTheCurrentPhysicalPositionAndTheBoundedBudget() throws Exception {
        var f = prepared(4, 0, 4, 6);
        try {
            Object pawn = pawn(f, "0:1"); f.move("move:0:sk10"); tick(f, 3);
            Location before = position(pawn);
            SessionTestSupport.set(f.room.board(), "pendingRoll", 3);
            SessionTestSupport.set(f.room.board(), "lastRoll", 3);
            f.move("move:0:sk13");
            assertEquals(before, position(pawn), "A second move must start from the currently presented pose");
            tick(f, 12); assertAtCell(f, pawn, "sk13"); assertFalse(f.view.moving());
        } finally { f.view.close(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changingDisplayLayersKeepsTheCurrentRoutePoseAndFinalOrientation(boolean captured) throws Exception {
        var f = prepared(2, 1, 4, 6);
        try {
            if (captured) {
                ((int[]) TableViewTest.field(f.room.board(), "progress"))[0] = 36;
                SessionTestSupport.bump(f.room); f.view.sync(); tick(f, 12);
            }
            var pack = mock(ResourcePackService.class);
            when(f.plugin.pack()).thenReturn(pack); when(pack.managed()).thenReturn(true);
            when(pack.item(anyString())).thenReturn(new ItemStack(Material.PAPER));
            when(f.world.getPlayers()).thenReturn(List.of(f.player));
            when(f.player.isOnline()).thenReturn(true); when(f.plugin.allowed(f.player)).thenReturn(true);
            when(f.player.getLocation()).thenReturn(f.view.origin.clone().add(0, 1, 2));
            f.view.sync(); Object pawn = pawn(f, captured ? "0:1" : "1:1");
            f.move("move:4:sk36"); tick(f, captured ? 2 : 3);
            var spawn = mockingDetails(f.world).getStubbings().stream()
                    .filter(stub -> stub.getInvocation().getMethod().getName().equals("spawn")).findFirst().orElseThrow();
            Map<Entity, Location> createdAt = new IdentityHashMap<>();
            doAnswer(call -> {
                Entity entity = (Entity) spawn.answer(call);
                createdAt.put(entity, ((Location) call.getArgument(0)).clone());
                return entity;
            }).when(f.world).spawn(any(Location.class), any(Class.class), any(java.util.function.Consumer.class));
            Location before = position(pawn); when(pack.packed(f.player)).thenReturn(true); f.view.sync();
            assertEquals(before, position(pawn));
            List<Entity> parts = parts(pawn); assertTrue(parts.stream().allMatch(ItemDisplay.class::isInstance));
            for (Entity part : parts) assertEquals(before.toVector(), createdAt.get(part).toVector(),
                    "A replacement must spawn at the current pose before it becomes visible");
            tick(f, 1); before = position(pawn); when(pack.packed(f.player)).thenReturn(false); f.view.sync();
            assertEquals(before, position(pawn));
            for (Entity part : parts(pawn)) assertEquals(part.getLocation().toVector(), createdAt.get(part).toVector(),
                    "Native replacements must also spawn at their current layer position");
            tick(f, captured ? 9 : 8); assertAtCell(f, pawn, captured ? "ba0_0" : "sk36"); assertFalse(f.view.moving());
            for (Entity part : parts(pawn)) {
                assertEquals(0, part.getLocation().getPitch(), 1e-9);
                assertEquals(captured ? 0 : 180, part.getLocation().getYaw(), 1e-9);
            }
        } finally { f.view.close(); }
    }

    @Test void closingDuringTheRouteStopsAllFutureEntityUpdates() throws Exception {
        var f = prepared(4, 0, 4, 6); f.move("move:0:sk10"); tick(f, 3); f.view.close();
        f.entities.forEach(org.mockito.Mockito::clearInvocations); tick(f, 20);
        assertFalse(f.view.moving());
        for (Entity entity : f.entities) verify(entity, never()).teleport(any(Location.class));
    }

    private static TableViewTest.Fixture prepared(int players, int seat, int progress, int roll) throws Exception {
        var f = new TableViewTest.Fixture("ludo", players); prepare(f, seat, progress, roll); return f;
    }

    private static void prepare(TableViewTest.Fixture f, int seat, int step, int roll) throws Exception {
        int[] progress = (int[]) TableViewTest.field(f.room.board(), "progress"); Arrays.fill(progress, -1); progress[seat * 4] = step;
        SessionTestSupport.set(f.room.board(), "current", seat);
        SessionTestSupport.set(f.room.board(), "pendingRoll", roll); SessionTestSupport.set(f.room.board(), "lastRoll", roll);
        SessionTestSupport.bump(f.room); f.view.sync(); tick(f, 12);
    }

    private static Object pawn(TableViewTest.Fixture f, String id) throws Exception {
        return ((Map<?, ?>) TableViewTest.field(f.view, "tokens")).get(id);
    }

    @SuppressWarnings("unchecked") private static List<Entity> parts(Object pawn) throws Exception {
        return (List<Entity>) TableViewTest.field(pawn, "parts");
    }

    private static Location position(Object pawn) throws Exception {
        var method = pawn.getClass().getDeclaredMethod("position"); method.setAccessible(true); return (Location) method.invoke(pawn);
    }

    private static void assertAtCell(TableViewTest.Fixture f, Object pawn, String id) throws Exception {
        Cell cell = f.room.board().cells().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        Location actual = position(pawn);
        assertEquals(f.view.origin.getX() + f.view.geometry.x(cell), actual.getX(), 1e-9, id + " x");
        assertEquals(f.view.origin.getZ() + f.view.geometry.z(cell), actual.getZ(), 1e-9, id + " z");
    }

    private static void tick(TableViewTest.Fixture f, int frames) { for (int i = 0; i < frames; i++) f.view.tick(); }
}

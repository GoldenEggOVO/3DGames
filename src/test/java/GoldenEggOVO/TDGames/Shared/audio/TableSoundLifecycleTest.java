package GoldenEggOVO.TDGames.Shared.audio;

import GoldenEggOVO.TDGames.SessionTestSupport;
import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;
import GoldenEggOVO.TDGames.Shared.render.TableView;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TableSoundLifecycleTest {
    @TempDir Path temporary;
    private TDGamesPlugin plugin;
    private World world;
    private Location origin;
    private GameSession room;
    private TableView view;
    private TableWorldController controller;
    private MockedStatic<Bukkit> bukkit;
    private final List<Scheduled> scheduled = new ArrayList<>();
    private boolean moving, landOnTick;
    private long now;

    private static final class Scheduled {
        long at;
        Runnable callback;
        boolean cancelled;
    }

    @BeforeEach void setup() throws Exception {
        MockBukkit.mock();
        plugin = mock(TDGamesPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(temporary.toFile());
        world = mock(World.class);
        origin = new Location(world, 0, 80, 0);
        room = new GameSession(UUID.randomUUID(), "gomoku", 2, 0, 0);
        SessionTestSupport.set(room, "board", GameFactory.create("gomoku", 2, 0));
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        room.event(0, new JsonPrimitive("place:0,0"));
        view = mockView();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            var task = new Scheduled();
            task.at = now + invocation.getArgument(2, Long.class);
            task.callback = invocation.getArgument(1);
            scheduled.add(task);
            var handle = mock(BukkitTask.class);
            doAnswer(ignored -> { task.cancelled = true; return null; }).when(handle).cancel();
            return handle;
        });
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        controller = new TableWorldController(plugin);
        views().put(room.id, view);
    }

    @AfterEach void close() {
        bukkit.close();
        MockBukkit.unmock();
    }

    @Test void landingIsReleasedOnlyAfterTheAnimationTickSettlesIt() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        silent();
        pointers();
        silent();
        landOnTick = true;
        pointers();
        silent();
        pointers();
        heard(TableSounds.WOOD.sound(), 1);
        pointers();
        heard(TableSounds.WOOD.sound(), 1);
    }

    @Test void aWinningFinishRetainsTheMoveAndOneResultCue() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        room.finish("winner:0");
        controller.sound(room, TableSounds.WIN);
        silent();
        landOnTick = true;
        pointers();
        silent();
        pointers();
        heard(TableSounds.WOOD.sound(), 1);
        heard(TableSounds.WIN.sound(), 1);
        pointers();
        heard(TableSounds.WIN.sound(), 1);
    }

    @Test void nextActionCancelsThePreviousLanding() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        room.event(1, new JsonPrimitive("place:1,0"));
        controller.sound(room, TableSounds.SELECT);
        landOnTick = true;
        pointers();
        heard(TableSounds.WOOD.sound(), 0);
        heard(TableSounds.SELECT.sound(), 1);
    }

    @Test void cancellationDuringTheFinalClientInterpolationCannotEmitLanding() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        landOnTick = true;
        pointers();
        silent();
        room.pause("test");
        pointers();
        silent();
    }

    @Test void motionResumingBeforeTheFinalInterpolationRestartsTheWait() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        landOnTick = true;
        pointers();
        silent();
        moving = true; landOnTick = false;
        pointers();
        silent();
        landOnTick = true;
        pointers();
        silent();
        pointers();
        heard(TableSounds.WOOD.sound(), 1);
    }

    @Test void aMoveWithoutAnyAnimationStillSoundsImmediately() {
        controller.sound(room, TableSounds.WOOD);
        heard(TableSounds.WOOD.sound(), 1);
    }

    @ParameterizedTest @ValueSource(ints = {2, 30})
    void aPauseFollowedByForfeitCannotReviveAnEarlierDelayedCue(int ticks) {
        controller.sound(room, delayed(ticks));
        room.pause("test");
        room.finish("winner:1");
        advance(ticks);
        silent();
    }

    @Test void aPauseFollowedByForfeitBetweenPointerTicksCannotReviveAnEarlierLanding() throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        room.pause("test");
        room.finish("winner:1");
        landOnTick = true;
        pointers(); pointers(); pointers();
        silent();
    }

    @ParameterizedTest @ValueSource(strings = {"pause", "abort", "remove", "replace", "board", "close"})
    void cancelledTableWorkCannotEmitLanding(String cancellation) throws Exception {
        moving = true;
        controller.sound(room, TableSounds.WOOD);
        cancel(cancellation);
        landOnTick = true;
        pointers();
        silent();
    }

    @ParameterizedTest @ValueSource(ints = {2, 30})
    void delayedImmediateCuesKeepTheirServerTickDeadline(int ticks) {
        var cue = delayed(ticks);
        controller.sound(room, cue);
        advance(ticks - 1);
        silent();
        advance(1);
        heard(cue.sound(), 1);
    }

    @ParameterizedTest @ValueSource(strings = {"pause", "abort", "remove", "replace", "board", "history", "close"})
    void cancelledTableWorkCannotEmitDelayedSound(String cancellation) throws Exception {
        controller.sound(room, delayed(30));
        cancel(cancellation);
        advance(30);
        silent();
    }

    @Test void completedMoveCanKeepItsDelayedOutcome() {
        var cue = delayed(30);
        controller.sound(room, cue);
        room.finish("winner:0");
        advance(30);
        heard(cue.sound(), 1);
    }

    @Test void selectionAndRollingRemainImmediateWhilePiecesMove() {
        moving = true;
        controller.sound(room, TableSounds.SELECT);
        controller.sound(room, TableSounds.ROLL);
        heard(TableSounds.SELECT.sound(), 1);
        heard(TableSounds.ROLL.sound(), 1);
    }

    @Test void pointerTickStopsAnAlreadyPlayingResultAfterPause() throws Exception {
        var pack = mock(GoldenEggOVO.TDGames.Core.pack.ResourcePackService.class);
        when(plugin.pack()).thenReturn(pack);
        var player = mock(org.bukkit.entity.Player.class);
        when(pack.packed(player)).thenReturn(true);
        TableSounds.playFor(plugin, player, origin, TableSounds.WIN, room.kind);
        room.pause("test");
        pointers();
        verify(player).stopSound("3dgames:table.win", SoundCategory.BLOCKS);
    }

    private TableView mockView() {
        var result = mock(TableView.class, invocation -> {
            if (invocation.getMethod().getName().equals("moving")) return moving;
            if (invocation.getMethod().getName().equals("tick") && landOnTick) moving = false;
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        SessionTestSupport.set(result, "origin", origin);
        SessionTestSupport.set(result, "room", room);
        return result;
    }

    private void cancel(String kind) throws Exception {
        switch (kind) {
            case "pause" -> room.pause("test");
            case "abort" -> room.abort("test");
            case "remove" -> controller.remove(room);
            case "replace" -> views().put(room.id, mockView());
            case "board" -> SessionTestSupport.set(room, "board", GameFactory.create("gomoku", 2, 1));
            case "history" -> room.event(1, new JsonPrimitive("place:1,0"));
            case "close" -> controller.close();
            default -> throw new AssertionError(kind);
        }
    }

    private void advance(int ticks) {
        now += ticks;
        for (var task : List.copyOf(scheduled)) if (task.at <= now) {
            scheduled.remove(task);
            if (!task.cancelled) task.callback.run();
        }
    }

    private void pointers() throws Exception {
        Method method = TableWorldController.class.getDeclaredMethod("pointers");
        method.setAccessible(true);
        method.invoke(controller);
    }

    @SuppressWarnings("unchecked") private Map<UUID, TableView> views() throws Exception {
        Field field = TableWorldController.class.getDeclaredField("views");
        field.setAccessible(true);
        return (Map<UUID, TableView>) field.get(controller);
    }

    private static TableSounds.Cue delayed(int ticks) {
        return new TableSounds.Cue(Sound.BLOCK_LEVER_CLICK, .2f, .8f, null, ticks);
    }

    private void heard(Sound sound, int times) {
        verify(world, times(times)).playSound(eq(origin), eq(sound), eq(SoundCategory.BLOCKS), anyFloat(), anyFloat());
    }

    private void silent() {
        verify(world, never()).playSound(any(Location.class), any(Sound.class), any(SoundCategory.class), anyFloat(), anyFloat());
    }
}

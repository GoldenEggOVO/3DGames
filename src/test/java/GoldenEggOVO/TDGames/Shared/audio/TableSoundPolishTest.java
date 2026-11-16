package GoldenEggOVO.TDGames.Shared.audio;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.game.Cell;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableSoundPolishTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void close() { MockBukkit.unmock(); }

    @Test void pokerBetsDoNotPlayCardsUntilTheStreetActuallyDeals() {
        for (String action : List.of("call", "raise:20", "all-in")) {
            var game = GameFactory.create("texas-holdem", 2, 0);
            var before = game.publicInfo();
            var cells = game.cells();
            game.apply(game.currentPlayer(), action);
            assertEquals(cells, game.cells(), action);
            assertEquals(List.of("chips.bet"), resources(TableSounds.cards(
                    "texas-holdem", action, before, game.publicInfo(), true)), action);
        }
        var game = GameFactory.create("texas-holdem", 2, 0);
        game.apply(game.currentPlayer(), "call");
        var before = game.publicInfo();
        game.apply(game.currentPlayer(), "check");
        assertEquals("flop", game.publicInfo().get("phase"));
        assertEquals(List.of("table.select", "cards.draw"), resources(TableSounds.cards(
                "texas-holdem", "check", before, game.publicInfo(), true)));
    }

    @Test void pokerNextHandDealsWithoutInventingAnExtraBet() {
        var game = GameFactory.create("texas-holdem", 2, 0);
        game.apply(game.currentPlayer(), "fold");
        var before = game.publicInfo();
        game.apply(game.currentPlayer(), "continue");
        assertEquals(List.of("cards.draw"), resources(TableSounds.cards(
                "texas-holdem", "continue", before, game.publicInfo(), true)));
        assertEquals(List.of("table.select"), resources(TableSounds.cards("texas-holdem", "check",
                Map.of("phase", "flop", "hand", "1"), Map.of("phase", "flop", "hand", "1"), true)));
    }

    @Test void aFoldThatCompletesTheStreetStillPlaysTheActualDeal() {
        var game = GameFactory.create("texas-holdem", 3, 0);
        game.apply(game.currentPlayer(), "call");
        game.apply(game.currentPlayer(), "call");
        var before = game.publicInfo();
        game.apply(game.currentPlayer(), "fold");
        assertEquals("preflop", before.get("phase"));
        assertEquals("flop", game.publicInfo().get("phase"));
        assertEquals(List.of("table.pass", "cards.draw"), resources(TableSounds.cards(
                "texas-holdem", "fold", before, game.publicInfo(), true)));
        var headsUp = GameFactory.create("texas-holdem", 2, 0);
        before = headsUp.publicInfo();
        headsUp.apply(headsUp.currentPlayer(), "fold");
        assertEquals(List.of("table.pass"), resources(TableSounds.cards(
                "texas-holdem", "fold", before, headsUp.publicInfo(), true)), "Winning an uncontested pot does not deal cards");
    }

    @Test void foldingTheThirdPlayerCanRunOutTheTwoRemainingAllInHands() {
        var game = GameFactory.create("texas-holdem", 3, 0);
        game.apply(game.currentPlayer(), "all-in");
        game.apply(game.currentPlayer(), "call");
        var before = game.publicInfo();
        assertEquals(0, game.cells().size());
        game.apply(game.currentPlayer(), "fold");
        assertEquals("showdown", game.publicInfo().get("phase"));
        assertEquals(5, game.cells().size(), "Both surviving all-in hands need the actual board runout");
        assertEquals(List.of("table.pass", "cards.draw"), resources(TableSounds.cards(
                "texas-holdem", "fold", before, game.publicInfo(), true)));
    }

    @Test void classicCardAndControlActionsUseTheirActualMaterial() {
        record Expected(String kind, String action, String resource) {}
        for (var expected : List.of(
                new Expected("memory", "flip:m0", "cards.flip"),
                new Expected("memory", "hide", "cards.flip"),
                new Expected("klondike", "draw", "cards.draw"),
                new Expected("klondike", "recycle", "cards.shuffle"),
                new Expected("klondike", "move:w:t0", "cards.play"),
                new Expected("mancala", "sow:0", "table.select"),
                new Expected("mastermind", "slot:1", "table.select"),
                new Expected("mastermind", "color:2", "peg.place"),
                new Expected("mastermind", "submit", "table.confirm"),
                new Expected("backgammon", "double:offer", "table.select"),
                new Expected("backgammon", "double:accept", "table.confirm"),
                new Expected("backgammon", "double:reject", "table.pass"),
                new Expected("shogi", "impasse:offer", "table.select"),
                new Expected("shogi", "impasse:accept", "table.confirm"),
                new Expected("shogi", "impasse:reject", "table.pass"))) {
            assertEquals(expected.resource(), TableSounds.move(expected.kind(), 0, expected.action(),
                    List.of(), List.of()).resource(), expected.kind() + ":" + expected.action());
        }
    }

    @Test void mastermindOnlyPlaysAnInsertionWhenTheDraftChanges() {
        var game = new GoldenEggOVO.TDGames.Shared.game.mastermind.MastermindGame(1, 7, Map.of());
        var before = game.cellsFor(0);
        game.apply(0, "slot:1");
        assertEquals(before, game.cellsFor(0), "Selecting a hole does not insert a peg");
        assertEquals("table.select", TableSounds.move("mastermind", 0, "slot:1", before, game.cellsFor(0)).resource());
        before = game.cellsFor(0);
        game.apply(0, "color:2");
        assertNotEquals(before, game.cellsFor(0), "Selecting a color fills the chosen hole");
        assertEquals("peg.place", TableSounds.move("mastermind", 0, "color:2", before, game.cellsFor(0)).resource());
    }

    @Test void shogiPromotionPromptStaysDistinctFromTheCommittedMove() {
        var before = List.of(new Cell("a3", 0, 2, "P", 0), new Cell("a2", 0, 1, "", -1));
        var after = List.of(new Cell("a3", 0, 2, "", -1), new Cell("a2", 0, 1, "+P", 0));
        assertEquals("table.select", TableSounds.move("shogi", 0, "move:a3:a2", before, before).resource());
        assertEquals("board.promote", TableSounds.move("shogi", 0, "promote:yes", before, after).resource());
    }

    @Test void softTileMovementStillWaitsForItsContact() {
        var cue = TableSounds.move("2048", 0, "slide:left", List.of(), List.of());
        assertEquals("board.slide", cue.resource());
        assertTrue(cue.volume() > 0 && cue.volume() <= .16f);
        assertTrue(landing(cue));
    }

    @Test void onlyPhysicalAndResultCuesWaitForLanding() {
        for (var cue : List.of(TableSounds.WOOD, TableSounds.XIANGQI, TableSounds.STONE,
                TableSounds.HOP, TableSounds.DROP, TableSounds.FLIP, TableSounds.PAWN,
                TableSounds.CAPTURE, TableSounds.HOME, TableSounds.WIN, TableSounds.DRAW))
            assertTrue(landing(cue), cue.toString());
        assertTrue(landing(new TableSounds.Cue(TableSounds.WOOD.sound(), .14f, 1.15f, "board.wood", 0)));
        for (var cue : List.of(TableSounds.SELECT, TableSounds.PASS, TableSounds.CONFIRM,
                TableSounds.CARD, TableSounds.CARD_DRAW, TableSounds.ROLL, TableSounds.SINGLE_ROLL))
            assertFalse(landing(cue), cue.toString());
    }

    @Test void diceContactsUseShortMaterialSamplesAndLoseEnergy() {
        float previous = 1;
        for (int frame : new int[] {8, 13, 17, 21}) {
            var cue = TableSounds.diceImpact(frame);
            assertEquals("dice.contact", cue.resource());
            assertEquals(org.bukkit.Sound.BLOCK_BONE_BLOCK_HIT, cue.sound());
            assertTrue(cue.volume() > 0 && cue.volume() <= .24f && cue.volume() < previous);
            previous = cue.volume();
        }
    }

    @Test void perViewerPlaybackKeepsRoutingAndVolumeWithoutBroadcasting() {
        var plugin = mock(TDGamesPlugin.class);
        var config = new YamlConfiguration();
        config.set("sounds.volume", .5);
        when(plugin.getConfig()).thenReturn(config);
        var pack = mock(ResourcePackService.class);
        when(plugin.pack()).thenReturn(pack);
        var world = mock(World.class);
        var at = new Location(world, 0, 80, 0);
        var packed = mock(Player.class);
        var nativeViewer = mock(Player.class);
        var unrelated = mock(Player.class);
        when(pack.packed(packed)).thenReturn(true);
        playFor(plugin, packed, at, TableSounds.WOOD);
        playFor(plugin, nativeViewer, at, TableSounds.WOOD);
        verify(packed).playSound(at, "3dgames:board.wood", SoundCategory.BLOCKS, .14f, 1f);
        verify(nativeViewer).playSound(at, TableSounds.WOOD.sound(), SoundCategory.BLOCKS, .14f, TableSounds.WOOD.pitch());
        verifyNoInteractions(world, unrelated);
        clearInvocations(packed, nativeViewer);
        config.set("sounds.enabled", false);
        playFor(plugin, packed, at, TableSounds.WOOD);
        playFor(plugin, nativeViewer, at, TableSounds.WOOD);
        verifyNoInteractions(packed, nativeViewer);
    }

    private static List<String> resources(List<TableSounds.Cue> cues) {
        return cues.stream().map(TableSounds.Cue::resource).toList();
    }

    private static boolean landing(TableSounds.Cue cue) {
        return TableSounds.isLanding(cue);
    }

    private static void playFor(TDGamesPlugin plugin, Player player, Location at, TableSounds.Cue cue) {
        TableSounds.playFor(plugin, player, at, cue, "mancala");
    }
}

package GoldenEggOVO.TDGames.Core.game;

import GoldenEggOVO.TDGames.Shared.game.BoardGame;
import GoldenEggOVO.TDGames.Shared.game.GameOptions;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;

import GoldenEggOVO.TDGames.Multiplayer.game.chess.ChessGame;
import GoldenEggOVO.TDGames.Multiplayer.game.chinesecheckers.ChineseCheckersGame;
import GoldenEggOVO.TDGames.Multiplayer.game.chinesecheckers.ChineseCheckersOptions;
import GoldenEggOVO.TDGames.Multiplayer.game.coloreight.ColorEightGame;
import GoldenEggOVO.TDGames.Multiplayer.game.connectfour.ConnectFourGame;
import GoldenEggOVO.TDGames.Multiplayer.game.doudizhu.DoudizhuGame;
import GoldenEggOVO.TDGames.Multiplayer.game.draughts.DraughtsGame;
import GoldenEggOVO.TDGames.Multiplayer.game.go.GoGame;
import GoldenEggOVO.TDGames.Multiplayer.game.gomoku.GomokuGame;
import GoldenEggOVO.TDGames.Multiplayer.game.gomoku.GomokuOptions;
import GoldenEggOVO.TDGames.Multiplayer.game.liarsbar.LiarsBarGame;
import GoldenEggOVO.TDGames.Multiplayer.game.ludo.LudoGame;
import GoldenEggOVO.TDGames.Multiplayer.game.mahjong.MahjongGame;
import GoldenEggOVO.TDGames.Multiplayer.game.reversi.ReversiGame;
import GoldenEggOVO.TDGames.Multiplayer.game.texasholdem.TexasHoldemGame;
import GoldenEggOVO.TDGames.Multiplayer.game.xiangqi.XiangqiGame;
import GoldenEggOVO.TDGames.Multiplayer.game.yacht.YachtGame;

import java.util.Locale;
import java.util.Map;

public final class GameFactory {
    private GameFactory() {}

    public static BoardGame create(String game, int players, long seed) {
        return create(game, players, seed, Map.of());
    }

    public static BoardGame create(
            String game, int players, long seed, Map<String, String> options) {
        if (game == null) throw new RuleViolation("error.game-type-cannot-be-empty", "Game type cannot be empty");
        game = game.toLowerCase(Locale.ROOT);
        options = GameOptions.validate(game, options);
        return switch (game) {
            case "2048" -> new GoldenEggOVO.TDGames.Singleplayer.game.twentyfortyeight.TwentyFortyEightGame(players, seed, options);
            case "mancala" -> new GoldenEggOVO.TDGames.Multiplayer.game.mancala.MancalaGame(players, seed, options);
            case "nine-mens-morris" -> new GoldenEggOVO.TDGames.Multiplayer.game.morris.MorrisGame(players, seed, options);
            case "dots-and-boxes" -> new GoldenEggOVO.TDGames.Multiplayer.game.dotsandboxes.DotsAndBoxesGame(players, seed, options);
            case "mastermind" -> new GoldenEggOVO.TDGames.Shared.game.mastermind.MastermindGame(players, seed, options);
            case "hex" -> new GoldenEggOVO.TDGames.Multiplayer.game.hex.HexGame(players, seed, options);
            case "memory" -> new GoldenEggOVO.TDGames.Shared.game.memory.MemoryGame(players, seed, options);
            case "backgammon" -> new GoldenEggOVO.TDGames.Multiplayer.game.backgammon.BackgammonGame(players, seed, options);
            case "battleship" -> new GoldenEggOVO.TDGames.Multiplayer.game.battleship.BattleshipGame(players, seed, options);
            case "klondike" -> new GoldenEggOVO.TDGames.Singleplayer.game.klondike.KlondikeGame(players, seed, options);
            case "shogi", "mini-shogi" -> new GoldenEggOVO.TDGames.Multiplayer.game.shogi.ShogiGame(players, seed, options, game.equals("mini-shogi"));
            case "monopoly" -> new GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame(players, seed, options);
            case "mahjong" -> new MahjongGame(players, seed, options);
            case "doudizhu" -> new DoudizhuGame(players, seed);
            case "liars-bar" -> new LiarsBarGame(players, seed);
            case "texas-holdem" -> new TexasHoldemGame(players, seed);
            case "color-eight" -> new ColorEightGame(players, seed);
            case "ludo" -> new LudoGame(players, seed, GameOptions.ludo(options));
            case "connectfour" -> {
                requireTwo(players);
                yield new ConnectFourGame();
            }
            case "gomoku" -> {
                requireTwo(players);
                yield new GomokuGame(
                        new GomokuOptions(
                                options.getOrDefault("double-three", "allow").equals("forbid"),
                                options.getOrDefault("double-four", "allow").equals("forbid"),
                                options.getOrDefault("overline", "allow").equals("forbid")));
            }
            case "xiangqi" -> {
                requireTwo(players);
                yield new XiangqiGame();
            }
            case "chess" -> {
                requireTwo(players);
                yield new ChessGame();
            }
            case "draughts" -> {
                requireTwo(players);
                yield new DraughtsGame();
            }
            case "reversi" -> {
                requireTwo(players);
                yield new ReversiGame();
            }
            case "go" -> {
                requireTwo(players);
                yield new GoGame(19);
            }
            case "go9" -> {
                requireTwo(players);
                yield new GoGame(9);
            }
            case "go13" -> {
                requireTwo(players);
                yield new GoGame(13);
            }
            case "yacht" -> new YachtGame(players, seed);
            case "checkers" ->
                    new ChineseCheckersGame(
                            players,
                            new ChineseCheckersOptions(
                                    !options.getOrDefault("jump-own", "allow").equals("forbid"),
                                    !options.getOrDefault("other-camps", "allow").equals("forbid"),
                                    options.getOrDefault("finish", "first").equals("all")));
            default ->
                    throw new RuleViolation(
                            "error.game.unknown", "Unknown game: " + game, "game", game);
        };
    }

    private static void requireTwo(int players) {
        if (players != 2)
            throw new RuleViolation("error.this-game-requires-two-players", "This game requires two players");
    }
}

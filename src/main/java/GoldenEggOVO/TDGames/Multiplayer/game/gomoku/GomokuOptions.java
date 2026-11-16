package GoldenEggOVO.TDGames.Multiplayer.game.gomoku;

public record GomokuOptions(boolean forbidDoubleThree, boolean forbidDoubleFour, boolean forbidOverline) {
    public static final GomokuOptions DEFAULT = new GomokuOptions(false, false, false);
}

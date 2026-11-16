package GoldenEggOVO.TDGames.Multiplayer.game.chinesecheckers;

public record ChineseCheckersOptions(boolean jumpOwn, boolean enterOtherCamps, boolean allPlaces) {
    public static final ChineseCheckersOptions DEFAULT = new ChineseCheckersOptions(true, true, false);
}

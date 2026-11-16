package GoldenEggOVO.TDGames.Shared.render.mahjong;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Block;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import java.util.List;

public final class MahjongTilePatterns {
    public static List<Block> of(String face) {
        return ModelRepository.require("tabletop/mahjong/tiles/" + face).blocks();
    }
    private MahjongTilePatterns() {}
}

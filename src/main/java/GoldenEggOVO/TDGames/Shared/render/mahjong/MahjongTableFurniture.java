package GoldenEggOVO.TDGames.Shared.render.mahjong;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Block;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import java.util.List;

public final class MahjongTableFurniture {
    public static List<Block> parts() { return ModelRepository.require("tabletop/mahjong/table").blocks(); }
    private MahjongTableFurniture() {}
}

package GoldenEggOVO.TDGames.Shared.render.cards;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition.Block;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;
import java.util.List;

public final class RoundCardTable {
    public static final double RADIUS = 1.5, RIM_TOP = .017;
    public static List<Block> parts() { return ModelRepository.require("tabletop/shared/round_table").blocks(); }
    private RoundCardTable() {}
}

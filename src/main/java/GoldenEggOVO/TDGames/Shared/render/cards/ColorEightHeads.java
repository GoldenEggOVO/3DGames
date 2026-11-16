package GoldenEggOVO.TDGames.Shared.render.cards;

import GoldenEggOVO.TDGames.Core.render.PlayingCardHeads;

import java.util.List;
import java.util.Map;

public final class ColorEightHeads {
    private static final class Catalog {
        static final Map<String, List<PlayingCardHeads.Texture>> FACES =
                PlayingCardHeads.load("/vanilla-models/tabletop/color_eight/head_textures.json");
    }

    public static List<PlayingCardHeads.Texture> tiles(String face) {
        return Catalog.FACES.get(face);
    }

    private ColorEightHeads() {}
}

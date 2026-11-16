package GoldenEggOVO.TDGames.Shared.render.mahjong;

import GoldenEggOVO.TDGames.Shared.game.HandGame;
import GoldenEggOVO.TDGames.Multiplayer.game.mahjong.MahjongGame;
import GoldenEggOVO.TDGames.Multiplayer.game.mahjong.Meld;
import GoldenEggOVO.TDGames.Multiplayer.game.mahjong.Tiles;

import java.util.*;

public final class MahjongLayout {
    public record Tile(HandGame.Piece piece, double offset, int row, boolean sideways, double lift) {}

    public record Placement(Tile tile, String prefix, double tangent, double radius) {}

    private static final double TILE_WIDTH = .101;
    private static final double SIDEWAYS_WIDTH = .153;

    public static List<Placement> publicRows(MahjongGame game, int seat) {
        List<Placement> result = new ArrayList<>();
        for (var tile : river(game.discards(seat), game.riichiTile(seat)))
            result.add(new Placement(tile, "discard:" + seat, tile.offset(), .65 + tile.row() * .16));
        double edge = 0;
        int group = 0;
        for (var meld : game.melds(seat)) {
            if (group % 2 == 0) edge = 0;
            var tiles = meld(meld, seat, game.concealedMeldFacesVisible());
            for (var tile : tiles)
                result.add(new Placement(tile, "exposed:" + seat,
                        1.22 - edge - tile.offset(), 1.10 - (group / 2) * .17));
            edge +=
                    tiles.stream()
                                    .filter(tile -> tile.lift() == 0)
                                    .mapToDouble(
                                            tile ->
                                                    tile.offset()
                                                            + (tile.sideways() ? .153 : .101) / 2)
                                    .max()
                                    .orElse(0)
                            + .04;
            group++;
        }
        var flowers = game.flowers(seat);
        int flowerRow = (group + 1) / 2;
        for (int index = 0; index < flowers.size(); index++) {
            var tile = new Tile(flowers.get(index), 0, 0, false, 0);
            result.add(new Placement(tile, "exposed:" + seat,
                    1.22 - index * .101, 1.10 - flowerRow * .17));
        }
        return List.copyOf(result);
    }

    public static List<Tile> river(List<HandGame.Piece> pieces, String riichiTile) {
        List<Tile> result = new ArrayList<>();
        int start = Math.max(0, pieces.size() - 18);
        for (int rowStart = start; rowStart < pieces.size(); rowStart += 6) {
            double left = -TILE_WIDTH * 3;
            for (int index = rowStart; index < Math.min(rowStart + 6, pieces.size()); index++) {
                var piece = pieces.get(index);
                boolean sideways = piece.id().equals(riichiTile);
                double width = sideways ? SIDEWAYS_WIDTH : TILE_WIDTH;
                result.add(new Tile(piece, left + width / 2, (rowStart - start) / 6, sideways, 0));
                left += width;
            }
        }
        return List.copyOf(result);
    }

    static List<Tile> meld(Meld meld, int seat) {
        return meld(meld, seat, true);
    }

    public static List<Tile> meld(Meld meld, int seat, boolean revealConcealedFaces) {
        List<Tiles.Tile> pieces = new ArrayList<>(meld.tiles());
        Tiles.Tile called =
                pieces.stream()
                        .filter(tile -> tile.id().equals(meld.calledTileId()))
                        .findFirst()
                        .orElse(null);
        Tiles.Tile added =
                pieces.stream()
                        .filter(tile -> tile.id().equals(meld.addedTileId()))
                        .findFirst()
                        .orElse(null);
        pieces.remove(called);
        pieces.remove(added);
        pieces.sort(
                Comparator.comparingInt(Tiles.Tile::type).reversed().thenComparing(Tiles.Tile::id));
        if (called != null) {
            int direction = (meld.fromSeat() - seat + 4) % 4;
            int index = direction == 1 ? 0 : direction == 2 ? 1 : pieces.size();
            pieces.add(index, called);
        }
        List<Tile> result = new ArrayList<>();
        double edge = 0;
        Tile calledPlacement = null;
        for (int index = 0; index < pieces.size(); index++) {
            var piece = pieces.get(index);
            boolean sideways = piece.equals(called);
            double width = sideways ? SIDEWAYS_WIDTH : TILE_WIDTH;
            boolean hidden =
                    !meld.open()
                            && (!revealConcealedFaces || index == 0 || index == pieces.size() - 1);
            Tile tile =
                    new Tile(
                            new HandGame.Piece(piece.id(), hidden ? "back" : piece.face()),
                            edge + width / 2,
                            0,
                            sideways,
                            0);
            result.add(tile);
            if (sideways) calledPlacement = tile;
            edge += width;
        }
        if (added != null)
            result.add(
                    new Tile(
                            new HandGame.Piece(added.id(), added.face()),
                            calledPlacement.offset(),
                            0,
                            true,
                            .055));
        return List.copyOf(result);
    }

    private MahjongLayout() {}
}

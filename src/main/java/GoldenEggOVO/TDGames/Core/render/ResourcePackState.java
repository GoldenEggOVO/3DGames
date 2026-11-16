package GoldenEggOVO.TDGames.Core.render;

import org.bukkit.entity.Player;

public interface ResourcePackState {
    boolean managed();

    boolean required();

    boolean packed(Player player);
}

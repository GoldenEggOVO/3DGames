package GoldenEggOVO.TDGames.Core.player;

import org.bukkit.entity.Player;

public final class GamePermissions {
    private GamePermissions() {}

    public static boolean use(Player player) { return player.hasPermission("3dgames.use"); }
    public static boolean admin(Player player) { return player.hasPermission("3dgames.admin"); }
}

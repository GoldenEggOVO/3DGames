package GoldenEggOVO.TDGames.Multiplayer.integration.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

public final class TabletopExpansion extends PlaceholderExpansion {
    private final String version;
    private final PlaceholderValues values;

    public TabletopExpansion(String version, PlaceholderValues values) {
        this.version = version;
        this.values = values;
    }

    @Override
    public String getIdentifier() { return "3dgames"; }

    @Override
    public String getAuthor() { return "GoldenEggOVO"; }

    @Override
    public String getVersion() { return version; }

    @Override
    public boolean persist() { return true; }

    @Override
    public List<String> getPlaceholders() {
        return List.of("rooms", "players", "economy_available", "balance", "balance_formatted", "in_room",
                "room_id", "room_name", "game", "game_id", "state", "state_id", "room_players", "room_humans",
                "room_bots", "room_capacity", "ready", "your_turn", "turn_player").stream()
                .map(parameter -> "%" + getIdentifier() + "_" + parameter + "%").toList();
    }

    @Override
    public String onRequest(OfflinePlayer player, String parameter) {
        return values.resolve(player == null ? null : player.getUniqueId(), parameter);
    }
}

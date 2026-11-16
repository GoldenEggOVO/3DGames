package GoldenEggOVO.TDGames.Shared.render;

import java.util.List;
import java.util.Map;

public final class PieceColors {
    public static final List<String> NAMES = List.of("red", "blue", "green", "yellow", "purple", "pink");

    private PieceColors() {}

    public static int seatIndex(Map<String, String> info, int seat) {
        if (seat < 0) return -1;
        String[] colors = info.getOrDefault("colors", "").replace("[", "").replace("]", "").split(",");
        if (seat < colors.length && !colors[seat].isBlank()) {
            try {
                return Integer.parseInt(colors[seat].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return seat;
    }

    public static String name(Map<String, String> info, int seat) {
        return NAMES.get(Math.floorMod(seatIndex(info, seat), NAMES.size()));
    }
}

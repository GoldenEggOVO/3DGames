package GoldenEggOVO.TDGames.Shared.game;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public interface ClassicGame extends BoardGame {
    default List<Cell> cellsFor(int seat) { return cells(); }

    default Map<String, String> infoFor(int seat) { return publicInfo(); }

    default List<String> controlActions(int seat) {
        Set<String> ids = cellsFor(seat).stream().map(Cell::id).collect(Collectors.toSet());
        return legalActions(seat).stream().filter(action -> {
            String[] parts = action.split(":");
            return !parts[0].equals("move") && !(parts.length == 2 && ids.contains(parts[1]));
        }).toList();
    }

    default String botAction(int seat, Random random) {
        List<String> actions = legalActions(seat);
        return actions.isEmpty() ? null : actions.get(random.nextInt(actions.size()));
    }

    default boolean eliminated(int seat) { return false; }

    default String automaticAction() { return null; }

    default long automaticDelayMillis() { return 0; }

    default String timeoutAction(int seat) { return null; }
}

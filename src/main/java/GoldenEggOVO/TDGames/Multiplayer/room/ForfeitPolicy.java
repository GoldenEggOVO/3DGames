package GoldenEggOVO.TDGames.Multiplayer.room;

import GoldenEggOVO.TDGames.Shared.session.GameSession;

import java.util.*;
import java.util.stream.Collectors;

public final class ForfeitPolicy {
    private ForfeitPolicy() {}

    public static String outcome(GameSession room, Set<UUID> losers) {
        Set<Integer> lost = new HashSet<>();
        for (int seat = 0; seat < room.seats.size(); seat++)
            if (losers.contains(room.seats.get(seat).id()) || room.departed.contains(room.seats.get(seat).id())) lost.add(seat);
        if (lost.isEmpty()) throw new IllegalArgumentException("No seated player forfeited");
        if (room.kind.equals("doudizhu")) {
            int landlord = Integer.parseInt(room.board().publicInfo().getOrDefault("landlord", "-1"));
            if (landlord >= 0) {
                boolean farmerLost = lost.stream().anyMatch(seat -> seat != landlord);
                if (farmerLost) for (int seat = 0; seat < room.seats.size(); seat++) if (seat != landlord) lost.add(seat);
            }
        }
        List<Integer> winners = new ArrayList<>();
        for (int seat = 0; seat < room.seats.size(); seat++)
            if (!lost.contains(seat)
                    && !(room.board() instanceof GoldenEggOVO.TDGames.Shared.game.ClassicGame classic && classic.eliminated(seat)))
                winners.add(seat);
        if (winners.isEmpty()) return "draw";
        return (winners.size() == 1 ? "winner:" : "winners:") + winners.stream().map(Object::toString).collect(Collectors.joining(","));
    }
}

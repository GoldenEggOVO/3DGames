package GoldenEggOVO.TDGames.Multiplayer.room;

import GoldenEggOVO.TDGames.Shared.session.GameSession;

import GoldenEggOVO.TDGames.Core.text.RuleViolation;

import java.util.*;

public final class RoundActions {
    public static boolean rematchReady(GameSession r, UUID player) {
        if (r.phase() != GameSession.Phase.FINISHED || r.busy() || r.seat(player) < 0 || !r.departed.isEmpty())
            throw new RuleViolation(
                    "error.finish-this-game-before-starting-a-rematch", "Finish this game before starting a rematch");
        r.confirmReady(player);
        return r.seats.stream().allMatch(s -> s.bot() || r.ready.contains(s.id()));
    }

    public static void fresh(GameSession r, long seed) {
        if (r.phase() != GameSession.Phase.FINISHED
                || r.seats.stream().anyMatch(s -> !s.bot() && !r.ready.contains(s.id())))
            throw new RuleViolation("error.waiting-for-tablemates-to-ready-for-a", "Waiting for tablemates to ready for a rematch");
        r.resetRound(seed);
    }
}

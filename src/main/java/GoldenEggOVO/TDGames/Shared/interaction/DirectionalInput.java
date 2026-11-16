package GoldenEggOVO.TDGames.Shared.interaction;

import org.bukkit.Input;

final class DirectionalInput {
    private int held;

    String press(Input input) {
        int next = (input.isForward() ? 1 : 0) | (input.isBackward() ? 2 : 0)
                | (input.isLeft() ? 4 : 0) | (input.isRight() ? 8 : 0);
        int previous = held;
        int pressed = next & ~previous;
        held = next;
        if (Integer.bitCount(next) != 1 || pressed == 0 && Integer.bitCount(previous) <= 1) return null;
        return switch (next) { case 1 -> "up"; case 2 -> "down"; case 4 -> "left"; default -> "right"; };
    }

}

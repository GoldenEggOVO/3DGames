package GoldenEggOVO.TDGames.Core.economy;

import java.util.OptionalDouble;
import java.util.UUID;

public interface EconomyAccess {
    enum Transfer { SUCCESS, REJECTED, UNCERTAIN }

    boolean available();
    OptionalDouble balance(UUID player);
    String format(double amount);
    default boolean supports(double amount) { return Double.isFinite(amount) && amount >= 0; }
    default int fractionalDigits() { return 2; }
    Transfer withdraw(UUID player, double amount);
    Transfer deposit(UUID player, double amount);
}

package GoldenEggOVO.TDGames.Core.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class VaultEconomy implements EconomyAccess {
    private final Supplier<Economy> providers;
    private final Function<UUID, OfflinePlayer> players;

    public VaultEconomy(Supplier<Economy> providers, Function<UUID, OfflinePlayer> players) {
        this.providers = providers;
        this.players = players;
    }

    public static EconomyAccess connect(org.bukkit.plugin.java.JavaPlugin plugin) {
        return new VaultEconomy(() -> {
            var service = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            return service == null ? null : service.getProvider();
        }, org.bukkit.Bukkit::getOfflinePlayer);
    }

    private Economy provider() {
        try {
            Economy provider = providers.get();
            return provider != null && provider.isEnabled() ? provider : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean available() { return provider() != null; }

    @Override
    public int fractionalDigits() {
        Economy provider = provider();
        return provider == null ? 2 : provider.fractionalDigits();
    }

    @Override
    public OptionalDouble balance(UUID player) {
        Economy provider = provider();
        if (provider == null) return OptionalDouble.empty();
        try {
            double balance = provider.getBalance(players.apply(player));
            return Double.isFinite(balance) && balance >= 0 ? OptionalDouble.of(balance) : OptionalDouble.empty();
        } catch (RuntimeException exception) {
            return OptionalDouble.empty();
        }
    }

    @Override
    public String format(double amount) {
        Economy provider = provider();
        if (provider != null) {
            try {
                String result = provider.format(amount);
                if (result != null) return result;
            } catch (RuntimeException ignored) {}
        }
        return Double.isFinite(amount) ? BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() : "";
    }

    @Override
    public boolean supports(double amount) {
        Economy provider = provider();
        if (provider == null || !EconomyAccess.super.supports(amount)) return false;
        try {
            int decimals = provider.fractionalDigits();
            return decimals < 0 || BigDecimal.valueOf(amount).stripTrailingZeros().scale() <= decimals;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public Transfer withdraw(UUID player, double amount) { return transfer(player, amount, true); }

    @Override
    public Transfer deposit(UUID player, double amount) { return transfer(player, amount, false); }

    private Transfer transfer(UUID player, double amount, boolean withdraw) {
        Economy provider = provider();
        if (provider == null || amount <= 0 || !supports(amount)) return Transfer.REJECTED;
        try {
            var response = withdraw ? provider.withdrawPlayer(players.apply(player), amount)
                    : provider.depositPlayer(players.apply(player), amount);
            if (response == null) return Transfer.UNCERTAIN;
            return response.transactionSuccess() ? Transfer.SUCCESS : Transfer.REJECTED;
        } catch (RuntimeException exception) {
            return Transfer.UNCERTAIN;
        }
    }
}

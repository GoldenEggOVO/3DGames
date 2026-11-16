package GoldenEggOVO.TDGames.Multiplayer.integration;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Multiplayer.economy.RoomPaymentLedger;
import GoldenEggOVO.TDGames.Core.economy.VaultEconomy;
import GoldenEggOVO.TDGames.Multiplayer.integration.placeholder.PlaceholderValues;
import GoldenEggOVO.TDGames.Multiplayer.integration.placeholder.TabletopExpansion;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.RuleViolation;

import java.io.IOException;
import java.util.OptionalDouble;
import java.util.UUID;

public final class OptionalIntegrations implements AutoCloseable {
    private static final EconomyAccess NONE = new EconomyAccess() {
        public boolean available() { return false; }
        public OptionalDouble balance(UUID player) { return OptionalDouble.empty(); }
        public String format(double amount) { return Double.toString(amount); }
        public Transfer withdraw(UUID player, double amount) { return Transfer.REJECTED; }
        public Transfer deposit(UUID player, double amount) { return Transfer.REJECTED; }
    };

    private final TDGamesPlugin plugin;
    private final EconomyAccess economy;
    private final PlaceholderValues values = new PlaceholderValues(System::currentTimeMillis);
    private RoomPaymentLedger ledger;
    private Runnable unregister;
    private int ticks;
    private boolean reviewWarning;
    private boolean storageWarning;

    public OptionalIntegrations(TDGamesPlugin plugin) {
        this.plugin = plugin;
        EconomyAccess access = NONE;
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                access = VaultEconomy.connect(plugin);
                plugin.getLogger().info("Optional Vault economy API connected.");
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Cannot connect optional Vault API: " + exception.getClass().getSimpleName());
            }
        }
        economy = access;
        try {
            ledger = new RoomPaymentLedger(plugin.getDataFolder().toPath().resolve("economy.json"), economy);
        } catch (IOException exception) {
            plugin.getLogger().warning("Cannot read economy records; paid games blocked and original file retained.");
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                registerPlaceholders();
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Cannot register optional PlaceholderAPI expansion: " + exception.getClass().getSimpleName());
            }
        }
    }

    private void registerPlaceholders() {
        var expansion = new TabletopExpansion(plugin.getDescription().getVersion(), values);
        if (expansion.register()) {
            unregister = expansion::unregister;
            plugin.getLogger().info("Registered optional PlaceholderAPI expansion: " + expansion.getIdentifier());
        } else {
            plugin.getLogger().warning("PlaceholderAPI identifier " + expansion.getIdentifier() + " is already registered; existing expansion retained.");
        }
    }

    public EconomyAccess economy() { return economy; }

    public RoomPaymentLedger.Terms terms(GameSession room) {
        return ledger == null ? null : ledger.terms(room);
    }

    public boolean stakesEnabled() {
        return plugin.getConfig().getBoolean("economy.enabled", false)
                && plugin.getConfig().getBoolean("economy.stakes.enabled", true);
    }

    public void configureRoom(GameSession room) {
        room.configureStake();
    }

    public void changeStake(GameSession room, UUID actor, long revision, double amount) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.solo(room.kind, room.capacity))
            throw new RuleViolation("error.solo.economy", "Solo progress does not use economy");
        if (!room.host(actor) || room.seat(actor) < 0) throw new RuleViolation("error.stake.host", "Only the host can set stakes");
        if (room.revision() != revision || room.phase() != GameSession.Phase.LOBBY && room.phase() != GameSession.Phase.FINISHED
                || !room.departed.isEmpty()) throw new RuleViolation("error.game-changed", "Room changed");
        if (room.phase() == GameSession.Phase.LOBBY && ledger != null && ledger.terms(room) != null)
            throw new RuleViolation("error.economy.review", "Unresolved payments block stake changes");
        validateStake(room, amount);
        if (java.util.Objects.equals(room.stake(), amount)) return;
        room.changeStake(amount);
    }

    private void validateStake(GameSession room, double amount) {
        if (!stakesEnabled()) throw new RuleViolation("error.stake.disabled", "Player stakes are disabled");
        double minimum = plugin.getConfig().getDouble("economy.stakes.minimum", 1);
        double maximum = plugin.getConfig().getDouble("economy.stakes.maximum", 1000);
        if (!Double.isFinite(amount) || amount < 0 || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum < 0 || maximum < minimum || amount != 0 && (amount < minimum || amount > maximum))
            throw new RuleViolation("error.stake.limits", "Stake outside configured limits", "minimum", economy.format(minimum), "maximum", economy.format(maximum));
        if (amount == 0) return;
        if (room.seats.stream().anyMatch(GameSession.Seat::bot)) throw new RuleViolation("error.stake.bots", "Stakes require human players");
        if (!economy.available()) throw new RuleViolation("error.economy.unavailable", "Economy unavailable");
        int precision = economy.fractionalDigits();
        if (precision < 0) precision = 2;
        if (precision > 8 || java.math.BigDecimal.valueOf(amount).stripTrailingZeros().scale() > precision || !economy.supports(amount))
            throw new RuleViolation("error.economy.amount", "Invalid stake precision");
    }

    public boolean isStakeGame(GameSession room) { return ledger != null && ledger.isPool(room); }

    public void beforeStart(GameSession room) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.solo(room.kind, room.capacity)) return;
        boolean charging = room.stake() != null ? room.stake() > 0
                : plugin.getConfig().getBoolean("economy.enabled", false)
                    && plugin.getConfig().getDouble("economy.entry-fee", 0) > 0;
        if (!room.restoring() && charging && room.seats.stream().filter(seat -> !seat.bot()).anyMatch(seat ->
                paymentBlocked(seat.id())))
            throw new RuleViolation("error.economy.review", "Unresolved payments block a new paid round");
        if (room.stake() != null) {
            if (room.stake() > 0) {
                validateStake(room, room.stake());
                if (room.seats.size() != room.capacity || room.seats.stream().anyMatch(seat -> seat.bot() || !room.ready.contains(seat.id())))
                    throw new RuleViolation("error.stake.confirm", "Every player must confirm the stake");
                if (ledger == null) throw new RuleViolation("error.economy.storage", "Economy records unavailable");
                ledger.beginPool(room, room.stake());
            } else {
                if (room.stake() != 0) throw new RuleViolation("error.economy.amount", "Invalid room stake");
                if (ledger != null) ledger.begin(room, false, 0, 0);
            }
            warnReview();
            return;
        }
        boolean enabled = plugin.getConfig().getBoolean("economy.enabled", false);
        double fee = plugin.getConfig().getDouble("economy.entry-fee", 0);
        double reward = plugin.getConfig().getDouble("economy.winner-reward", 0);
        if (ledger == null) {
            if (enabled && (fee != 0 || reward != 0) && room.seats.stream().noneMatch(GameSession.Seat::bot))
                throw new RuleViolation("error.economy.storage", "Economy records unavailable");
            return;
        }
        ledger.begin(room, enabled, fee, reward);
        warnReview();
    }

    public void finish(GameSession room, String result) {
        if (ledger != null) safely(() -> ledger.finish(room, result));
    }

    public void cancel(GameSession room) {
        if (ledger != null) safely(() -> ledger.cancel(room));
    }

    public void recover() {
        if (ledger == null) {
            pauseUnrecordedStakes();
            refresh();
            return;
        }
        safely(() -> {
            var previousDepartures = new java.util.HashMap<UUID, java.util.Set<UUID>>();
            plugin.multiplayer().rooms().values().forEach(room -> previousDepartures.put(room.id, java.util.Set.copyOf(room.departed)));
            var changed = ledger.recover(plugin.multiplayer().rooms().values());
            for (GameSession room : changed) {
                for (UUID player : room.departed)
                    if (!previousDepartures.get(room.id).contains(player)) plugin.sessions().release(player, room.kind);
                plugin.tableWorld().render(room);
            }
            if (!changed.isEmpty()) plugin.games().save();
        });
        pauseUnrecordedStakes();
        refresh();
    }

    private void pauseUnrecordedStakes() {
        boolean changed = false;
        for (GameSession room : plugin.multiplayer().rooms().values()) {
            if (room.stake() == null || room.stake() <= 0 || room.phase() != GameSession.Phase.PLAYING && room.phase() != GameSession.Phase.STARTING
                    || ledger != null && ledger.isPool(room)) continue;
            room.pause("error.economy.storage");
            plugin.tableWorld().render(room);
            changed = true;
        }
        if (changed) plugin.games().save();
    }

    public void tick() {
        if (++ticks % 30 == 0) recover();
        else refresh();
    }

    private void refresh() {
        if (unregister != null) values.refresh(plugin.games().activeSessions().values(), economy);
    }

    public RoomPaymentLedger.Receipt receipt(GameSession room, UUID viewer) {
        return ledger == null || room.phase() != GameSession.Phase.FINISHED && room.phase() != GameSession.Phase.PAUSED
                ? null : ledger.receipt(room, viewer);
    }

    public boolean settlementPending(GameSession room, UUID viewer) {
        var receipt = receipt(room, viewer);
        return receipt != null && (receipt.pending() || receipt.review());
    }

    public boolean paymentBlocked(UUID player) { return ledger == null || ledger.blocked(player); }

    private void safely(Runnable action) {
        try {
            action.run();
            storageWarning = false;
        } catch (RuntimeException exception) {
            if (!storageWarning) {
                plugin.getLogger().warning("Economy processing stopped; inspect economy.json and provider balances: " + exception.getMessage());
                storageWarning = true;
            }
        }
        warnReview();
    }

    private void warnReview() {
        if (ledger != null && ledger.needsReview() && !reviewWarning) {
            plugin.getLogger().warning("Uncertain economy transactions require manual balance review; they will not be automatically retried. Inspect economy.json.");
            reviewWarning = true;
        }
    }

    @Override
    public void close() {
        if (unregister != null) {
            try { unregister.run(); }
            catch (RuntimeException | LinkageError exception) { plugin.getLogger().warning("Cannot unregister tabletop placeholders."); }
        }
        unregister = null;
        values.clear();
    }
}

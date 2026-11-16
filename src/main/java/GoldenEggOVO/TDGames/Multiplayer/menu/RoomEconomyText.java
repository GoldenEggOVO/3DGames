package GoldenEggOVO.TDGames.Multiplayer.menu;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Multiplayer.economy.RoomPaymentLedger;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.Language;
import net.kyori.adventure.text.Component;

import java.util.OptionalDouble;
import java.util.UUID;

public final class RoomEconomyText {
    private final TDGamesPlugin plugin;

    public RoomEconomyText(TDGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public Component fees(GameSession room) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.solo(room.kind, room.capacity)) return Component.empty();
        var economy = plugin.integrations().economy();
        if ((room.phase() != GameSession.Phase.LOBBY && room.phase() != GameSession.Phase.FINISHED)
                || !plugin.getConfig().getBoolean("economy.enabled", false))
            return Component.empty();
        if (room.seats.stream().anyMatch(GameSession.Seat::bot)) return Language.component("room.economy.bots");
        var terms = nextTerms(room);
        Component text = terms.pool()
                ? Language.component("stake.fees", "stake", economy.format(terms.entryFee()), "pot", economy.format(terms.pot()),
                        "seconds", plugin.getConfig().getLong("reconnect-seconds", 120))
                : terms.entryFee() == 0 && terms.winnerReward() == 0 ? Language.component("stake.zero")
                : Language.component("room.economy.fees", "fee", economy.format(terms.entryFee()), "reward", economy.format(terms.winnerReward()));
        if (room.phase() == GameSession.Phase.FINISHED) text = Language.component("room.economy.next", "fees", text);
        return text.append(Component.newline()).append(Language.component("room.economy.charge-timing"));
    }

    private RoomPaymentLedger.Terms nextTerms(GameSession room) {
        if (GoldenEggOVO.TDGames.Shared.game.GameCatalog.solo(room.kind, room.capacity)) return null;
        if (!plugin.getConfig().getBoolean("economy.enabled", false) || room.seats.stream().anyMatch(GameSession.Seat::bot)) return null;
        if (room.stake() != null) return new RoomPaymentLedger.Terms(room.stake(), 0, room.stake() > 0,
                java.math.BigDecimal.valueOf(room.stake()).multiply(java.math.BigDecimal.valueOf(room.capacity)).doubleValue());
        return new RoomPaymentLedger.Terms(plugin.getConfig().getDouble("economy.entry-fee", 0),
                plugin.getConfig().getDouble("economy.winner-reward", 0));
    }

    public Component priceBadge(GameSession room) {
        var economy = plugin.integrations().economy();
        if (room.phase() == GameSession.Phase.PAUSED && room.result().equals("error.economy.storage"))
            return Language.component("error.economy.storage");
        var terms = room.phase() == GameSession.Phase.LOBBY || room.phase() == GameSession.Phase.FINISHED
                ? nextTerms(room) : plugin.integrations().terms(room);
        if (terms == null && !plugin.getConfig().getBoolean("economy.enabled", false)) return Component.empty();
        if (room.seats.stream().anyMatch(GameSession.Seat::bot)) return Language.component("room.economy.bots-short");
        Component badge = terms == null || terms.entryFee() == 0 && terms.winnerReward() == 0
                ? Language.component("room.economy.zero")
                : terms.pool() ? Language.component("stake.price", "stake", economy.format(terms.entryFee()), "pot", economy.format(terms.pot()))
                : Language.component("room.economy.price", "fee", economy.format(terms.entryFee()), "reward", economy.format(terms.winnerReward()));
        return room.phase() == GameSession.Phase.FINISHED ? Language.component("room.economy.next-short", "fees", badge) : badge;
    }

    public Component readyLabel(GameSession room, Component label) {
        var economy = plugin.integrations().economy();
        var terms = nextTerms(room);
        return terms == null || terms.entryFee() == 0 ? label
                : terms.pool() ? Language.component("stake.ready", "stake", economy.format(terms.entryFee()))
                : Language.component("room.economy.ready", "label", label, "fee", economy.format(terms.entryFee()));
    }

    public Component balanceHint(GameSession room, UUID viewer) {
        var economy = plugin.integrations().economy();
        if (room.phase() != GameSession.Phase.LOBBY && room.phase() != GameSession.Phase.FINISHED) return Component.empty();
        var terms = nextTerms(room);
        if (terms == null || terms.entryFee() <= 0) return Component.empty();
        var balance = economy.available() ? economy.balance(viewer) : OptionalDouble.empty();
        if (balance.isEmpty()) return Language.component("error.economy.unavailable");
        double amount = balance.getAsDouble();
        if (amount < terms.entryFee()) {
            double shortfall = java.math.BigDecimal.valueOf(terms.entryFee()).subtract(java.math.BigDecimal.valueOf(amount)).doubleValue();
            return Language.component("room.economy.shortfall", "fee", economy.format(terms.entryFee()),
                    "balance", economy.format(amount), "shortfall", economy.format(shortfall));
        }
        return Language.component("room.economy.balance", "balance", economy.format(amount));
    }

    public Component settlement(GameSession room, UUID viewer) {
        var economy = plugin.integrations().economy();
        var receipt = plugin.integrations().receipt(room, viewer);
        if (receipt == null) return Component.empty();
        var terms = plugin.integrations().terms(room);
        Component text = Language.component(terms != null && terms.pool() ? "stake.settlement" : "room.economy.settlement", "fee", economy.format(receipt.charged()),
                "refund", economy.format(receipt.refunded()), "reward", economy.format(receipt.rewarded()),
                "net", (receipt.net() < 0 ? "-" : "+") + economy.format(Math.abs(receipt.net())));
        if (receipt.refundPending() > 0) text = text.append(Component.newline()).append(
                Language.component("room.economy.refund-pending", "amount", economy.format(receipt.refundPending())));
        if (receipt.rewardPending() > 0) text = text.append(Component.newline()).append(
                Language.component("room.economy.reward-pending", "amount", economy.format(receipt.rewardPending())));
        if (receipt.review()) text = text.append(Component.newline()).append(Language.component("room.economy.receipt-review"));
        return text;
    }

    public Component leaveDetails(GameSession room) {
        if (room.phase() == GameSession.Phase.PAUSED && room.result().equals("error.economy.storage"))
            return Language.component("error.economy.storage");
        if (plugin.integrations().isStakeGame(room) && (room.phase() == GameSession.Phase.PLAYING || room.phase() == GameSession.Phase.PAUSED))
            return Language.component("menu.leave.stake", "seconds", plugin.getConfig().getLong("reconnect-seconds", 120));
        String key = room.phase() == GameSession.Phase.LOBBY ? "menu.leave.lobby"
                : room.phase() == GameSession.Phase.FINISHED ? "menu.leave.finished" : "menu.leave.active";
        Component text = Language.component(key);
        var terms = plugin.integrations().terms(room);
        if (room.phase() != GameSession.Phase.LOBBY && room.phase() != GameSession.Phase.FINISHED && terms != null && terms.entryFee() > 0)
            text = text.append(Component.newline()).append(Language.component("menu.leave.refund"));
        return text;
    }

}

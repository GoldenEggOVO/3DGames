package GoldenEggOVO.TDGames.Shared.text;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import java.util.ArrayList;
import java.util.List;

public final class MonopolyText {
    private MonopolyText() {}

    public static Component property(int index) {
        return Language.component("classic.cell.monopoly." + MonopolyData.spaces().get(index).code());
    }

    private static Component owner(int seat) {
        return seat < 0 ? Language.component("monopoly.bank") : Language.component("monopoly.player", "seat", seat + 1);
    }

    private static Component buildings(MonopolyGame.PropertyView property) {
        return Language.component(property.buildings() == 5 ? "monopoly.buildings.hotel" : "monopoly.buildings.houses",
                "count", property.buildings());
    }

    private static Component rent(MonopolyGame game, int index) {
        var property = game.properties().get(index);
        if (property.owner() >= 0 && !property.mortgaged() && MonopolyData.spaces().get(index).kind() == MonopolyData.Kind.UTILITY)
            return Language.component("monopoly.rent.dice", "multiplier", game.utilityRentMultiplier(index));
        return Component.text(game.propertyRent(index));
    }

    public static Component hover(MonopolyGame game, int index) {
        var property = game.properties().get(index);
        var space = MonopolyData.spaces().get(index);
        if (space.price() == 0) return property(index);
        return Language.component("monopoly.property.hover", "property", property(index), "owner", owner(property.owner()),
                "rent", rent(game, index), "buildings", space.kind() == MonopolyData.Kind.PROPERTY ? buildings(property)
                        : Language.component("monopoly.property.price", "price", space.price()),
                "state", property.mortgaged() ? Language.component("monopoly.mortgaged") : Component.empty());
    }

    public static Component details(MonopolyGame game, int index, int seat) {
        var space = MonopolyData.spaces().get(index);
        var property = game.properties().get(index);
        if (space.price() == 0) return property(index);
        Component result = Language.component("monopoly.property.details", "property", property(index), "owner", owner(property.owner()),
                "price", space.price(), "rent", rent(game, index), "buildings", space.kind() == MonopolyData.Kind.PROPERTY
                        ? buildings(property) : Language.component("monopoly.type." + space.kind().name().toLowerCase(java.util.Locale.ROOT)),
                "mortgage", space.mortgage(), "redemption", space.mortgage() + (space.mortgage() + 9) / 10,
                "state", Language.component(property.mortgaged() ? "monopoly.mortgaged" : "monopoly.unmortgaged"));
        if (space.group() >= 0) {
            int count = (int) MonopolyData.spaces().stream().filter(other -> other.group() == space.group()).count();
            result = result.append(Component.newline()).append(Language.component("monopoly.property.development",
                    "group", Language.component("monopoly.group." + space.group()), "owned", seat < 0 ? 0 : game.groupCount(seat, space.group()),
                    "total", count, "cost", space.buildCost(),
                    "reason", Language.component("monopoly.build." + game.buildBlocker(seat, index).name().toLowerCase(java.util.Locale.ROOT))));
            var rents = space.rents();
            result = result.append(Component.newline()).append(Language.component("monopoly.property.rents", "base", rents.get(0),
                    "set", rents.get(0) * 2, "one", rents.get(1), "two", rents.get(2), "three", rents.get(3),
                    "four", rents.get(4), "hotel", rents.get(5)));
        }
        return result;
    }

    public static Component status(MonopolyGame game, int seat) {
        var info = game.publicInfo();
        String phase = info.get("phase");
        Component hint = Language.component("monopoly.hint." + phase, "property", info.containsKey("property")
                        ? property(Integer.parseInt(info.get("property"))) : Component.empty(),
                "amount", info.getOrDefault("debt", "0"), "multiplier", info.getOrDefault("rent-multiplier", "4"),
                "recipient", game.trade() == null ? 0 : game.trade().recipient() + 1,
                "creditor", owner(Integer.parseInt(info.getOrDefault("creditor", "-1"))),
                "bid", info.getOrDefault("bid", "0"), "item", "property".equals(info.get("auction-type"))
                        ? property(Integer.parseInt(info.get("property"))) : Language.component("monopoly.auction." + info.getOrDefault("auction-type", "property")));
        Component result = Language.component("monopoly.turn", "player", owner(game.currentPlayer()));
        if (seat >= 0 && seat < game.playerCount()) result = result.append(Component.text(" · "))
                .append(Language.component("classic.info.balance", "value", game.players().get(seat).cash()));
        result = result.append(Component.text(" | ")).append(hint);
        if (info.containsKey("auction-target." + seat) && Integer.parseInt(info.get("auction-target." + seat)) >= 0)
            result = result.append(Component.text(" | ")).append(Language.component("monopoly.auction.target",
                    "property", property(Integer.parseInt(info.get("auction-target." + seat)))));
        return result;
    }

    public static Component ledger(MonopolyGame game) {
        var properties = game.properties();
        Component result = Language.component("monopoly.assets");
        for (var player : game.players()) {
            var owned = properties.stream().filter(property -> property.owner() == player.seat()).toList();
            result = result.append(Component.newline()).append(Language.component("monopoly.assets.player",
                    "player", owner(player.seat()), "cash", player.cash(), "properties", owned.size(),
                    "houses", owned.stream().filter(property -> property.buildings() < 5).mapToInt(MonopolyGame.PropertyView::buildings).sum(),
                    "hotels", owned.stream().filter(property -> property.buildings() == 5).count(),
                    "mortgages", owned.stream().filter(MonopolyGame.PropertyView::mortgaged).count(),
                    "state", player.eliminated() ? Language.component("monopoly.player.eliminated")
                            : player.jailed() ? Language.component("monopoly.player.jailed") : Component.empty()));
        }
        return result.append(Component.newline()).append(Language.component("monopoly.inventory", "houses", game.bankHouses(), "hotels", game.bankHotels()))
                .append(Component.newline()).append(Language.component("monopoly.assets.help"));
    }

    public static Component changes(MonopolyGame game, List<MonopolyGame.PlayerView> players,
                                    List<MonopolyGame.PropertyView> properties, int seat) {
        List<Component> changes = new ArrayList<>();
        long cash = game.players().get(seat).cash(), delta = cash - players.get(seat).cash();
        if (delta != 0) changes.add(Language.component("monopoly.change.cash", "change", delta > 0 ? "+" + delta : delta, "balance", cash));
        for (var after : game.properties()) {
            var before = properties.get(after.index());
            if (after.owner() == seat && before.owner() != seat) changes.add(Language.component("monopoly.change.received", "property", property(after.index())));
            else if (before.owner() == seat && after.owner() != seat) changes.add(Language.component("monopoly.change.transferred", "property", property(after.index())));
            if (after.owner() == seat && before.owner() == seat && after.buildings() != before.buildings())
                changes.add(Language.component("monopoly.change.building", "property", property(after.index()), "buildings", buildings(after)));
        }
        return join(changes, "monopoly.trade.separator");
    }

    private static Component join(List<Component> parts, String separator) {
        return Component.join(JoinConfiguration.separator(Language.component(separator)), parts);
    }

    public static Component tradeSummary(MonopolyGame game) {
        var trade = game.trade();
        if (trade == null) return Component.empty();
        List<Component> terms = new ArrayList<>();
        appendTradeTerms(terms, trade.proposer(), trade.recipient(), trade.giveCash(), trade.giveProperties(), trade.giveCards());
        appendTradeTerms(terms, trade.recipient(), trade.proposer(), trade.takeCash(), trade.takeProperties(), trade.takeCards());
        return Language.component("monopoly.trade.summary", "terms", join(terms, "monopoly.trade.separator"))
                .append(mortgageReminder(game, trade.recipient(), trade.giveProperties()))
                .append(mortgageReminder(game, trade.proposer(), trade.takeProperties()));
    }

    private static void appendTradeTerms(List<Component> terms, int from, int to, long cash,
                                         List<Integer> properties, List<Integer> cards) {
        if (cash > 0) terms.add(Language.component("monopoly.trade.payment", "from", from + 1, "to", to + 1, "amount", cash));
        if (!properties.isEmpty()) terms.add(Language.component("monopoly.trade.properties", "from", from + 1, "to", to + 1, "properties", propertyNames(properties)));
        if (!cards.isEmpty()) terms.add(Language.component("monopoly.trade.cards", "from", from + 1, "to", to + 1,
                "cards", join(cards.stream().map(id -> Language.component("monopoly.table.card." + id)).toList(), "monopoly.trade.asset-separator")));
    }

    private static Component propertyNames(List<Integer> ids) {
        return join(ids.stream().map(MonopolyText::property).toList(), "monopoly.trade.asset-separator");
    }

    private static Component mortgageReminder(MonopolyGame game, int recipient, List<Integer> ids) {
        var properties = game.properties();
        List<Integer> mortgages = ids.stream().filter(id -> properties.get(id).mortgaged()).toList();
        return mortgages.isEmpty() ? Component.empty() : Component.newline().append(Language.component("monopoly.trade.mortgage",
                "seat", recipient + 1, "properties", propertyNames(mortgages)));
    }
}

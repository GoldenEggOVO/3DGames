package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyGame;
import GoldenEggOVO.TDGames.Shared.text.ClassicText;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.stream.Collectors;

final class MonopolyTableInput {
    private static final List<String> FIELDS = List.of("give-cash", "take-cash", "give-properties", "take-properties", "give-cards", "take-cards");
    private final MonopolyGame game;
    private final int seat, recipient;
    private final long revision, minimum;
    private final Map<String, Long> cash = new HashMap<>();
    private final Map<String, Set<Integer>> assets = new HashMap<>();
    private String field;
    private boolean closed;

    private MonopolyTableInput(MonopolyGame game, int seat, String action) {
        this.game = game; this.seat = seat; revision = game.revision();
        String[] parts = action.split(":");
        boolean bid = parts[0].equals("bid");
        recipient = bid ? -1 : Integer.parseInt(parts[1]);
        minimum = bid ? Long.parseLong(parts[1]) : 0;
        field = bid ? "bid" : "";
        cash.put("bid", minimum);
        cash.put("give-cash", bid ? 0 : Long.parseLong(parts[2]));
        cash.put("take-cash", bid ? 0 : Long.parseLong(parts[3]));
        for (int i = 2; i < FIELDS.size(); i++) {
            Set<Integer> selected = new TreeSet<>();
            if (!bid && !parts[i + 2].equals("-")) for (String value : parts[i + 2].split(","))
                selected.add(Integer.parseInt(value.replace("p", "")));
            assets.put(FIELDS.get(i), selected);
        }
    }

    static MonopolyTableInput open(MonopolyGame game, int seat, String action) {
        return (action.startsWith("bid:") || action.startsWith("trade:")) && game.legalActions(seat).contains(action)
                ? new MonopolyTableInput(game, seat, action) : null;
    }

    boolean valid() { return !closed && game.revision() == revision && !game.finished() && !game.legalActions(seat).isEmpty(); }
    boolean closed() { return closed; }

    boolean propertyField() { return valid() && field.endsWith("properties"); }

    boolean selectableProperty(int index) {
        return propertyField() && index >= 0 && index < game.properties().size() && canTrade(index);
    }

    Set<Integer> selectableProperties() {
        if (!propertyField()) return Set.of();
        Set<Integer> result = new HashSet<>();
        for (var property : game.properties()) if (selectableProperty(property.index())) result.add(property.index());
        return Set.copyOf(result);
    }

    Set<Integer> selectedProperties() {
        Set<Integer> selected = new HashSet<>(assets.get("give-properties"));
        selected.addAll(assets.get("take-properties"));
        return Set.copyOf(selected);
    }

    List<String> actions() {
        if (!valid()) return List.of();
        List<String> actions = new ArrayList<>();
        if (field.isEmpty()) {
            FIELDS.forEach(key -> actions.add("@edit:" + key));
            if (cash.values().stream().anyMatch(value -> value > 0) || assets.values().stream().anyMatch(value -> !value.isEmpty()))
                actions.add("@edit:confirm");
        } else if (cash.containsKey(field)) {
            for (int delta : new int[] {-100, -10, -1, 1, 10, 100}) actions.add("@edit:change:" + delta);
            actions.add("@edit:max"); actions.add("@edit:reset");
            if (field.equals("bid")) actions.add("@edit:confirm");
        } else if (field.endsWith("properties")) {
            for (var property : game.properties()) if (canTrade(property.index())) actions.add("@edit:property:" + property.index());
        } else for (int card : game.players().get(assetOwner()).jailCards()) actions.add("@edit:card:" + card);
        if (!field.isEmpty() && !field.equals("bid")) actions.add("@edit:back");
        actions.add("@edit:cancel");
        return List.copyOf(actions);
    }

    String select(String action) {
        if (!actions().contains(action)) return null;
        String[] parts = action.split(":");
        switch (parts[1]) {
            case "cancel" -> closed = true;
            case "back" -> field = "";
            case "confirm" -> {
                closed = true;
                return recipient < 0 ? "bid:" + cash.get("bid") : "trade:" + recipient + ":" + cash.get("give-cash") + ":" + cash.get("take-cash")
                        + ":" + list("give-properties", "p") + ":" + list("take-properties", "p")
                        + ":" + list("give-cards", "") + ":" + list("take-cards", "") + ":" + revision;
            }
            case "change" -> {
                long current = cash.get(field), delta = Long.parseLong(parts[2]);
                cash.put(field, delta > 0 ? current + Math.min(delta, limit() - current) : Math.max(floor(), current + delta));
            }
            case "max" -> cash.put(field, limit());
            case "reset" -> cash.put(field, floor());
            case "property" -> property(Integer.parseInt(parts[2]));
            case "card" -> toggle(assets.get(field), Integer.parseInt(parts[2]));
            default -> { if (FIELDS.contains(parts[1])) field = parts[1]; }
        }
        return null;
    }

    void property(int index) {
        if (selectableProperty(index)) toggle(assets.get(field), index);
    }

    Component status() {
        if (!field.isEmpty()) return Language.component("monopoly.table.field." + field, "value",
                cash.containsKey(field) ? cash.get(field) : assets.get(field).size());
        return Language.component("monopoly.trade.with", "seat", recipient + 1);
    }

    Component label(String action) {
        String[] parts = action.split(":");
        String key = parts[1];
        if (FIELDS.contains(key)) return Language.component("monopoly.table.field." + key,
                "value", cash.containsKey(key) ? cash.get(key) : assets.get(key).size());
        if (key.equals("change")) return Component.text(Long.parseLong(parts[2]) > 0 ? "+" + parts[2] : parts[2]);
        if (key.equals("property") || key.equals("card")) {
            int value = Integer.parseInt(parts[2]);
            Component name = key.equals("property") ? ClassicText.property(value) : Language.component("monopoly.table.card." + value);
            return Component.text(assets.get(field).contains(value) ? "[x] " : "[ ] ").append(name);
        }
        return Language.component(key.equals("cancel") ? "classic.action.cancel" : "monopoly.table." + key);
    }

    private long floor() { return field.equals("bid") ? minimum : 0; }
    private long limit() { return game.players().get(field.equals("take-cash") ? recipient : seat).cash(); }
    private int assetOwner() { return field.startsWith("take-") ? recipient : seat; }
    private boolean canTrade(int index) {
        var property = game.properties().get(index);
        int group = MonopolyData.spaces().get(index).group();
        return property.owner() == assetOwner() && (group < 0 || game.properties().stream().noneMatch(other ->
                MonopolyData.spaces().get(other.index()).group() == group && other.buildings() > 0));
    }
    private String list(String key, String prefix) {
        String value = assets.get(key).stream().map(id -> prefix + id).collect(Collectors.joining(","));
        return value.isEmpty() ? "-" : value;
    }
    private static void toggle(Set<Integer> values, int value) { if (!values.remove(value)) values.add(value); }
}

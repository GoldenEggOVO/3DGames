package GoldenEggOVO.TDGames.Multiplayer.game.monopoly;

import GoldenEggOVO.TDGames.Shared.game.Cell;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData.Card;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData.Kind;
import GoldenEggOVO.TDGames.Multiplayer.game.monopoly.MonopolyData.Space;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class MonopolyGame implements ClassicGame {
    public enum BuildBlocker { NONE, NOT_STREET, NOT_OWNER, COLOR_SET, MORTGAGED, HOTEL, UNEVEN, FUNDS, HOUSES, HOTELS, PHASE }
    private enum Phase { ROLL, MANAGE, PURCHASE, UTILITY_RENT, AUCTION, TRADE, DEBT, INHERIT }
    private sealed interface Resolution permits Payment, Landing, Inheritance, AuctionLot, Release { }
    private record Payment(int debtor, int creditor, long amount) implements Resolution { }
    private record Landing(int seat, int destination, boolean collectGo, int rentMode) implements Resolution { }
    private record Inheritance(int seat, int property) implements Resolution { }
    private record AuctionLot(int property) implements Resolution { }
    private record Release(int seat, int distance) implements Resolution { }

    public record PlayerView(int seat, int position, long cash, boolean jailed, int jailAttempts, boolean eliminated, List<Integer> jailCards) {
        public PlayerView { jailCards = List.copyOf(jailCards); }
    }
    public record PropertyView(int index, int owner, int buildings, boolean mortgaged) { }
    public record TradeView(int proposer, int recipient, long giveCash, long takeCash, List<Integer> giveProperties,
                            List<Integer> takeProperties, List<Integer> giveCards, List<Integer> takeCards, long revision) {
        public TradeView {
            giveProperties = List.copyOf(giveProperties); takeProperties = List.copyOf(takeProperties);
            giveCards = List.copyOf(giveCards); takeCards = List.copyOf(takeCards);
        }
    }

    private final int playerCount;
    private final Random random;
    private final int[] positions;
    private final long[] cash;
    private final boolean[] jailed;
    private final int[] jailAttempts;
    private final boolean[] eliminated;
    private final List<List<Integer>> jailCards = new ArrayList<>();
    private final int[] owners = new int[40];
    private final int[] buildings = new int[40];
    private final boolean[] mortgaged = new boolean[40];
    private final ArrayDeque<Card> chance;
    private final ArrayDeque<Card> community;
    private final ArrayDeque<Resolution> pending = new ArrayDeque<>();
    private Phase phase = Phase.ROLL;
    private Phase resting = Phase.ROLL;
    private int turn;
    private int doubles;
    private boolean extraRoll;
    private int dieOne;
    private int dieTwo;
    private int houses = 32;
    private int hotels = 12;
    private int purchase = -1;
    private int utilityProperty = -1, utilityMultiplier;
    private int auctionProperty = -1;
    private boolean buildingAuction;
    private int[] auctionTargets;
    private int auctionActor;
    private int highestBidder = -1;
    private long highestBid;
    private boolean[] passed;
    private TradeView trade;
    private Phase tradeReturnPhase;
    private final Set<Integer> declinedPurchases = new HashSet<>();
    private long revision;
    private String lastEvent = "";
    private String result = "";

    public MonopolyGame(int players, long seed, Map<String, String> options) {
        if (players < 2 || players > 6) throw new IllegalArgumentException("Monopoly requires two to six players");
        playerCount = players;
        random = new Random(seed);
        positions = new int[players]; cash = new long[players]; jailed = new boolean[players];
        jailAttempts = new int[players]; eliminated = new boolean[players];
        Arrays.fill(cash, 1500); Arrays.fill(owners, -1);
        for (int i = 0; i < players; i++) jailCards.add(new ArrayList<>());
        List<Card> chanceCards = new ArrayList<>(MonopolyData.chanceCards());
        List<Card> communityCards = new ArrayList<>(MonopolyData.communityCards());
        Collections.shuffle(chanceCards, random); Collections.shuffle(communityCards, random);
        chance = new ArrayDeque<>(chanceCards); community = new ArrayDeque<>(communityCards);
    }

    @Override public String id() { return "monopoly"; }
    @Override public int playerCount() { return playerCount; }
    @Override public int currentPlayer() {
        return switch (phase) {
            case AUCTION -> auctionActor;
            case TRADE -> trade.recipient();
            case DEBT -> ((Payment) pending.getFirst()).debtor();
            case INHERIT -> ((Inheritance) pending.getFirst()).seat();
            default -> turn;
        };
    }
    @Override public boolean finished() { return !result.isEmpty(); }
    @Override public String outcome() { return result; }
    @Override public boolean eliminated(int seat) { return seat >= 0 && seat < playerCount && eliminated[seat]; }
    public long revision() { return revision; }
    public int bankHouses() { return houses; }
    public int bankHotels() { return hotels; }
    public TradeView trade() { return trade; }

    public List<PlayerView> players() {
        List<PlayerView> views = new ArrayList<>();
        for (int seat = 0; seat < playerCount; seat++) views.add(new PlayerView(seat,positions[seat],cash[seat],jailed[seat],jailAttempts[seat],eliminated[seat],jailCards.get(seat)));
        return List.copyOf(views);
    }

    public List<PropertyView> properties() {
        List<PropertyView> views = new ArrayList<>();
        for (int i = 0; i < 40; i++) views.add(new PropertyView(i,owners[i],buildings[i],mortgaged[i]));
        return List.copyOf(views);
    }

    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (Space space : MonopolyData.spaces()) {
            int i = space.index();
            int x = i <= 10 ? 10 - i : i <= 20 ? 0 : i <= 30 ? i - 20 : 10;
            int y = i <= 10 ? 10 : i <= 20 ? 20 - i : i <= 30 ? 0 : i - 30;
            cells.add(new Cell("p" + i,x,y,space.code(),owners[i]));
        }
        return List.copyOf(cells);
    }

    @Override public List<String> legalActions(int seat) {
        if (finished() || seat < 0 || seat >= playerCount || eliminated[seat]) return List.of();
        List<String> actions = new ArrayList<>();
        boolean acting = seat == currentPlayer();
        if (acting) switch (phase) {
            case ROLL -> {
                actions.add("roll");
                if (jailed[seat]) {
                    actions.add("jail-pay");
                    for (int card : jailCards.get(seat)) actions.add("jail-card:" + card);
                }
            }
            case MANAGE -> actions.add(extraRoll ? "roll" : "end");
            case PURCHASE -> { if (cash[seat] >= space(purchase).price()) actions.add("buy"); actions.add("decline"); }
            case UTILITY_RENT -> actions.add("roll");
            case AUCTION -> { }
            case TRADE -> { actions.add("accept:" + trade.revision()); actions.add("reject:" + trade.revision()); }
            case DEBT -> {
                Payment debt = (Payment) pending.getFirst();
                if (cash[seat] >= debt.amount()) actions.add("pay");
                if (liquidationValue(seat) < debt.amount()) actions.add("bankrupt");
            }
            case INHERIT -> {
                Inheritance inheritance = (Inheritance) pending.getFirst();
                actions.add("inherit:keep");
                if (cash[seat] >= redemption(inheritance.property())) actions.add("inherit:redeem");
            }
        }
        if (phase == Phase.AUCTION && (!buildingAuction || auctionTargets[seat] >= 0)) {
            if (cash[seat] >= minimumBid()) actions.add("bid:" + minimumBid());
            if (seat != highestBidder && !passed[seat]) actions.add("pass");
            if (buildingAuction) for (int i = 0; i < 40; i++)
                if (buildingTarget(seat, i, buildings[auctionProperty] == 4)) actions.add("auction-target:p" + i);
        }
        if (phase == Phase.ROLL || phase == Phase.MANAGE || acting && (phase == Phase.DEBT || phase == Phase.INHERIT)) {
            for (int i = 0; i < 40; i++) if (owners[i] == seat) {
                if (canSell(seat,i)) actions.add("sell:p" + i);
                if (canMortgage(seat,i)) actions.add("mortgage:p" + i);
                if (phase == Phase.ROLL || phase == Phase.MANAGE) {
                    if (canBuild(seat,i)) actions.add("build:p" + i);
                    if (mortgaged[i] && cash[seat] >= redemption(i)) actions.add("redeem:p" + i);
                }
            }
            for (int group = 0; group < 8; group++) if (canSellGroup(seat,group)) actions.add("sell-group:" + group);
        }
        if (phase == Phase.ROLL || phase == Phase.MANAGE || phase == Phase.DEBT) for (int other = 0; other < playerCount; other++) if (other != seat && !eliminated[other]) {
            String entry = tradeEntry(seat, other);
            if (entry != null) actions.add(entry);
        }
        return List.copyOf(actions);
    }

    private String tradeEntry(int seat, int other) {
        String assets;
        if (cash[seat] > 0) assets = "1:0:-:-:-:-";
        else if (cash[other] > 0) assets = "0:1:-:-:-:-";
        else {
            assets = null;
            for (int i = 0; i < owners.length; i++) {
                if (space(i).price() == 0 || space(i).group() >= 0 && !clearGroup(space(i).group())) continue;
                if (owners[i] == seat) { assets = "0:0:p" + i + ":-:-:-"; break; }
                if (owners[i] == other) { assets = "0:0:-:p" + i + ":-:-"; break; }
            }
            if (assets == null && !jailCards.get(seat).isEmpty()) assets = "0:0:-:-:" + jailCards.get(seat).getFirst() + ":-";
            if (assets == null && !jailCards.get(other).isEmpty()) assets = "0:0:-:-:-:" + jailCards.get(other).getFirst();
        }
        return assets == null ? null : "trade:" + other + ":" + assets + ":" + revision;
    }

    @Override public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream().filter(action -> action.endsWith(":" + cellId)).toList();
    }

    @Override public List<String> controlActions(int seat) { return legalActions(seat); }

    @Override public void apply(int seat, String action) {
        if (action == null || finished() || seat < 0 || seat >= playerCount || eliminated[seat])
            throw new IllegalArgumentException("Illegal Monopoly actor or action");
        String[] parts = action.split(":",-1);
        TradeView proposal = null;
        long bid = 0;
        if (parts[0].equals("trade")) {
            proposal = parseTrade(seat,parts);
        } else if (parts[0].equals("bid")) {
            if (phase != Phase.AUCTION || parts.length != 2 || buildingAuction && auctionTargets[seat] < 0) throw new IllegalArgumentException("Not an auction bid");
            bid = amount(parts[1]);
            if (bid < minimumBid() || bid > cash[seat]) throw new IllegalArgumentException("Bid exceeds cash or does not raise");
        } else if (!legalActions(seat).contains(action)) throw new IllegalArgumentException("Illegal Monopoly action");

        switch (parts[0]) {
            case "roll" -> roll(seat);
            case "end" -> nextTurn();
            case "buy" -> { cash[seat] -= space(purchase).price(); owners[purchase] = seat; purchase = -1; resolve(); }
            case "decline" -> { pending.addFirst(new AuctionLot(purchase)); purchase = -1; resolve(); }
            case "bid" -> { highestBid = bid; highestBidder = seat; passed[seat] = false; advanceAuction(seat); }
            case "pass" -> { passed[seat] = true; advanceAuction(seat); }
            case "auction-target" -> auctionTargets[seat] = property(parts[1]);
            case "build" -> buyBuilding(seat,property(parts[1]));
            case "sell" -> { sell(seat,property(parts[1])); resolveAfterLiquidation(); }
            case "sell-group" -> { sellGroup(seat,Integer.parseInt(parts[1])); resolveAfterLiquidation(); }
            case "mortgage" -> { int property = property(parts[1]); mortgaged[property] = true; cash[seat] += space(property).mortgage(); resolveAfterLiquidation(); }
            case "redeem" -> { int property = property(parts[1]); cash[seat] -= redemption(property); mortgaged[property] = false; }
            case "jail-pay" -> {
                resting = Phase.ROLL;
                pending.addLast(new Payment(seat,-1,50)); pending.addLast(new Release(seat,0)); resolve();
            }
            case "jail-card" -> { returnCard(seat,Integer.parseInt(parts[1])); jailed[seat] = false; jailAttempts[seat] = 0; }
            case "pay" -> resolve();
            case "bankrupt" -> { bankrupt((Payment) pending.removeFirst()); resolve(); }
            case "trade" -> { tradeReturnPhase = phase; trade = proposal; phase = Phase.TRADE; }
            case "accept" -> acceptTrade();
            case "reject" -> { declinedPurchases.addAll(trade.takeProperties()); trade = null; phase = tradeReturnPhase; }
            case "inherit" -> inherit(parts[1].equals("redeem"));
            default -> throw new IllegalArgumentException("Unknown Monopoly action");
        }
        revision++;
    }

    private void roll(int seat) {
        dieOne = random.nextInt(6) + 1; dieTwo = random.nextInt(6) + 1;
        if (phase == Phase.UTILITY_RENT) {
            pending.addFirst(new Payment(seat, owners[utilityProperty], (long) utilityMultiplier * (dieOne + dieTwo)));
            utilityProperty = -1;
            resolve();
            return;
        }
        boolean same = dieOne == dieTwo;
        resting = Phase.MANAGE;
        extraRoll = false;
        if (jailed[seat]) {
            if (same) { pending.addLast(new Release(seat,dieOne + dieTwo)); resolve(); }
            else if (++jailAttempts[seat] == 3) {
                pending.addLast(new Payment(seat,-1,50)); pending.addLast(new Release(seat,dieOne + dieTwo)); resolve();
            } else phase = Phase.MANAGE;
            return;
        }
        doubles = same ? doubles + 1 : 0;
        if (doubles == 3) { goToJail(seat); phase = Phase.MANAGE; return; }
        extraRoll = same;
        int destination = (positions[seat] + dieOne + dieTwo) % 40;
        pending.addLast(new Landing(seat,destination,destination < positions[seat],0));
        resolve();
    }

    private void nextTurn() {
        turn = nextLiving(turn);
        declinedPurchases.clear();
        doubles = 0; extraRoll = false; phase = resting = Phase.ROLL;
    }

    private int nextLiving(int after) {
        for (int step = 1; step <= playerCount; step++) if (!eliminated[(after + step) % playerCount]) return (after + step) % playerCount;
        return after;
    }

    private void resolveAfterLiquidation() { if (phase == Phase.DEBT || phase == Phase.INHERIT) resolve(); }

    private void resolve() {
        while (!pending.isEmpty()) {
            Resolution resolution = pending.getFirst();
            if (resolution instanceof Payment payment) {
                if (eliminated[payment.debtor()]) { pending.removeFirst(); continue; }
                if (cash[payment.debtor()] < payment.amount()) { phase = Phase.DEBT; return; }
                cash[payment.debtor()] -= payment.amount();
                if (payment.creditor() >= 0 && !eliminated[payment.creditor()]) cash[payment.creditor()] += payment.amount();
                pending.removeFirst();
            } else if (resolution instanceof Inheritance inheritance) {
                if (eliminated[inheritance.seat()] || owners[inheritance.property()] != inheritance.seat()) { pending.removeFirst(); continue; }
                phase = Phase.INHERIT;
                return;
            } else if (resolution instanceof AuctionLot lot) {
                pending.removeFirst();
                startAuction(lot.property());
                if (phase == Phase.AUCTION) return;
            } else if (resolution instanceof Release release) {
                pending.removeFirst();
                if (eliminated[release.seat()]) continue;
                jailed[release.seat()] = false; jailAttempts[release.seat()] = 0;
                if (release.distance() > 0) pending.addFirst(new Landing(release.seat(),(positions[release.seat()] + release.distance()) % 40,false,0));
            } else if (resolution instanceof Landing landing) {
                pending.removeFirst();
                if (eliminated[landing.seat()]) continue;
                positions[landing.seat()] = landing.destination();
                if (landing.collectGo()) cash[landing.seat()] += 200;
                if (land(landing)) return;
            }
        }
        phase = resting;
        if (eliminated[turn]) nextTurn();
        int survivor = -1, alive = 0;
        for (int seat = 0; seat < playerCount; seat++) if (!eliminated[seat]) { survivor = seat; alive++; }
        if (alive < 2) result = alive == 1 ? "winner:" + survivor : "draw:no-survivors";
    }

    private boolean land(Landing landing) {
        int seat = landing.seat(), property = landing.destination();
        Space space = space(property);
        if (space.price() > 0) {
            if (owners[property] < 0) { purchase = property; phase = Phase.PURCHASE; return true; }
            if (owners[property] != seat && !mortgaged[property]) {
                if (space.kind() == Kind.UTILITY) {
                    utilityProperty = property;
                    long owned = MonopolyData.spaces().stream().filter(s -> s.kind() == Kind.UTILITY && owners[s.index()] == owners[property]).count();
                    utilityMultiplier = landing.rentMode() == 2 || owned == 2 ? 10 : 4;
                    phase = Phase.UTILITY_RENT;
                    return true;
                }
                pending.addFirst(new Payment(seat,owners[property],rent(property,landing.rentMode())));
            }
        } else switch (space.kind()) {
            case CHANCE -> draw(seat,0);
            case COMMUNITY -> draw(seat,1);
            case INCOME_TAX -> pending.addFirst(new Payment(seat,-1,200));
            case LUXURY_TAX -> pending.addFirst(new Payment(seat,-1,100));
            case GO_TO_JAIL -> goToJail(seat);
            default -> { }
        }
        return false;
    }

    private long rent(int property,int mode) {
        Space space = space(property);
        int owner = owners[property];
        if (space.kind() == Kind.STATION) {
            int count = 0;
            for (Space other : MonopolyData.spaces()) if (other.kind() == Kind.STATION && owners[other.index()] == owner) count++;
            return (25L << (count - 1)) * (mode == 1 ? 2 : 1);
        }
        if (space.kind() == Kind.UTILITY) {
            int count = 0;
            for (Space other : MonopolyData.spaces()) if (other.kind() == Kind.UTILITY && owners[other.index()] == owner) count++;
            return (mode == 2 || count == 2 ? 10L : 4L) * (dieOne + dieTwo);
        }
        int rent = space.rents().get(buildings[property]);
        return buildings[property] == 0 && ownsGroup(owner,space.group()) ? rent * 2L : rent;
    }

    private void goToJail(int seat) {
        positions[seat] = 10; jailed[seat] = true; jailAttempts[seat] = 0; doubles = 0; extraRoll = false;
    }

    private void draw(int seat,int deckIndex) {
        ArrayDeque<Card> deck = deckIndex == 0 ? chance : community;
        Card card = deck.removeFirst();
        lastEvent = (deckIndex == 0 ? "chance." : "community.") + card.code();
        if (card.effect() == MonopolyData.Effect.JAIL_CARD) { jailCards.get(seat).add(deckIndex); return; }
        deck.addLast(card);
        switch (card.effect()) {
            case ADVANCE -> pending.addFirst(new Landing(seat,card.amount(),card.amount() <= positions[seat],0));
            case MONEY -> { if (card.amount() >= 0) cash[seat] += card.amount(); else pending.addFirst(new Payment(seat,-1,-card.amount())); }
            case JAIL -> goToJail(seat);
            case NEAREST_STATION -> {
                int target = nextOfKind(positions[seat],Kind.STATION);
                pending.addFirst(new Landing(seat,target,target < positions[seat],1));
            }
            case NEAREST_UTILITY -> {
                int target = nextOfKind(positions[seat],Kind.UTILITY);
                pending.addFirst(new Landing(seat,target,target < positions[seat],2));
            }
            case BACK_THREE -> pending.addFirst(new Landing(seat,(positions[seat] + 37) % 40,false,0));
            case REPAIRS -> {
                long cost = 0;
                for (int i = 0; i < 40; i++) if (owners[i] == seat) cost += buildings[i] == 5 ? card.secondary() : (long) buildings[i] * card.amount();
                pending.addFirst(new Payment(seat,-1,cost));
            }
            case PAY_EACH, COLLECT_EACH -> {
                for (int other = playerCount - 1; other >= 0; other--) if (other != seat && !eliminated[other]) {
                    boolean collect = card.effect() == MonopolyData.Effect.COLLECT_EACH;
                    pending.addFirst(new Payment(collect ? other : seat,collect ? seat : other,card.amount()));
                }
            }
            default -> { }
        }
    }

    private int nextOfKind(int position,Kind kind) {
        for (int distance = 1; distance < 40; distance++) if (space((position + distance) % 40).kind() == kind) return (position + distance) % 40;
        throw new IllegalStateException("Missing board destination");
    }

    private void returnCard(int seat,int deckIndex) {
        jailCards.get(seat).remove(Integer.valueOf(deckIndex));
        List<Card> cards = deckIndex == 0 ? MonopolyData.chanceCards() : MonopolyData.communityCards();
        (deckIndex == 0 ? chance : community).addLast(cards.stream().filter(card -> card.effect() == MonopolyData.Effect.JAIL_CARD).findFirst().orElseThrow());
    }

    private void startAuction(int property) {
        buildingAuction = false;
        auctionTargets = null;
        auctionProperty = property; highestBidder = -1; highestBid = 0; passed = eliminated.clone();
        auctionActor = eliminated[turn] ? nextLiving(turn) : turn;
        phase = Phase.AUCTION;
        if (Arrays.stream(cash).sum() == 0) { auctionProperty = -1; phase = resting; }
    }

    private void advanceAuction(int after) {
        int remaining = 0, last = -1;
        for (int seat = 0; seat < playerCount; seat++) if (!passed[seat]) { remaining++; last = seat; }
        if (remaining == 0 || remaining == 1 && highestBidder == last) {
            if (highestBidder >= 0) {
                if (buildingAuction) placeBuilding(auctionTargets[highestBidder]);
                else owners[auctionProperty] = highestBidder;
                cash[highestBidder] -= highestBid;
            }
            auctionProperty = -1;
            buildingAuction = false;
            auctionTargets = null;
            resolve();
            return;
        }
        for (int distance = 1; distance <= playerCount; distance++) {
            int seat = (after + distance) % playerCount;
            if (!passed[seat] && seat != highestBidder) { auctionActor = seat; return; }
        }
    }

    private long minimumBid() { return Math.max(10, highestBid + 1); }

    private boolean buildingTarget(int seat, int property, boolean hotel) {
        return structuralBuildBlocker(seat, property) == BuildBlocker.NONE && (buildings[property] == 4) == hotel;
    }

    private void buyBuilding(int seat, int property) {
        boolean hotel = buildings[property] == 4;
        if ((hotel ? hotels : houses) == 1) {
            int[] targets = new int[playerCount];
            Arrays.fill(targets, -1);
            int bidders = 0;
            for (int player = 0; player < playerCount; player++) if (!eliminated[player] && cash[player] >= 10) {
                for (int i = 0; i < 40; i++) if (buildingTarget(player, i, hotel)) { targets[player] = i; break; }
                if (targets[player] >= 0) bidders++;
            }
            if (bidders > 1) {
                targets[seat] = property;
                startAuction(property);
                buildingAuction = true;
                auctionTargets = targets;
                for (int player = 0; player < playerCount; player++) passed[player] = targets[player] < 0;
                auctionActor = seat;
                return;
            }
        }
        cash[seat] -= space(property).buildCost();
        placeBuilding(property);
    }

    private boolean ownsGroup(int seat,int group) {
        for (Space space : MonopolyData.spaces()) if (space.group() == group && owners[space.index()] != seat) return false;
        return group >= 0;
    }

    private boolean clearGroup(int group) {
        for (Space space : MonopolyData.spaces()) if (space.group() == group && buildings[space.index()] > 0) return false;
        return true;
    }

    private boolean canBuild(int seat,int property) {
        return buildBlocker(seat, property) == BuildBlocker.NONE;
    }

    public BuildBlocker buildBlocker(int seat, int property) {
        BuildBlocker structural = structuralBuildBlocker(seat, property);
        if (structural != BuildBlocker.NONE) return structural;
        if (phase != Phase.ROLL && phase != Phase.MANAGE) return BuildBlocker.PHASE;
        if (cash[seat] < space(property).buildCost()) return BuildBlocker.FUNDS;
        if (buildings[property] == 4 && hotels == 0) return BuildBlocker.HOTELS;
        if (buildings[property] < 4 && houses == 0) return BuildBlocker.HOUSES;
        return BuildBlocker.NONE;
    }

    private BuildBlocker structuralBuildBlocker(int seat, int property) {
        Space space = space(property);
        if (space.kind() != Kind.PROPERTY) return BuildBlocker.NOT_STREET;
        if (seat < 0 || seat >= playerCount || owners[property] != seat) return BuildBlocker.NOT_OWNER;
        if (!ownsGroup(seat,space.group())) return BuildBlocker.COLOR_SET;
        if (buildings[property] == 5) return BuildBlocker.HOTEL;
        for (Space other : MonopolyData.spaces()) if (other.group() == space.group() && mortgaged[other.index()]) return BuildBlocker.MORTGAGED;
        for (Space other : MonopolyData.spaces()) if (other.group() == space.group() && buildings[other.index()] < buildings[property]) return BuildBlocker.UNEVEN;
        return BuildBlocker.NONE;
    }

    public long propertyRent(int property) {
        return owners[property] < 0 || mortgaged[property] ? 0 : rent(property, 0);
    }

    public int utilityRentMultiplier(int property) {
        int count = 0;
        for (Space space : MonopolyData.spaces()) if (space.kind() == Kind.UTILITY && owners[space.index()] == owners[property]) count++;
        return count == 2 ? 10 : 4;
    }

    private void placeBuilding(int property) {
        if (buildings[property] == 4) { houses += 4; hotels--; } else houses--;
        buildings[property]++;
    }

    private boolean canSell(int seat,int property) {
        if (owners[property] != seat || buildings[property] == 0) return false;
        for (Space other : MonopolyData.spaces()) if (other.group() == space(property).group() && buildings[other.index()] > buildings[property]) return false;
        return buildings[property] != 5 || houses >= 4;
    }

    private void sell(int seat,int property) {
        cash[seat] += space(property).buildCost() / 2;
        if (buildings[property] == 5) { houses -= 4; hotels++; } else houses++;
        buildings[property]--;
    }

    private boolean canSellGroup(int seat,int group) {
        return ownsGroup(seat,group) && !clearGroup(group);
    }

    private void sellGroup(int seat,int group) {
        for (Space space : MonopolyData.spaces()) if (space.group() == group && owners[space.index()] == seat) {
            int property = space.index();
            cash[seat] += (long) buildings[property] * space.buildCost() / 2;
            if (buildings[property] == 5) hotels++; else houses += buildings[property];
            buildings[property] = 0;
        }
    }

    private boolean canMortgage(int seat,int property) {
        return owners[property] == seat && !mortgaged[property] && (space(property).group() < 0 || clearGroup(space(property).group()));
    }

    private long interest(int property) { return (space(property).mortgage() + 9L) / 10; }
    private long redemption(int property) { return space(property).mortgage() + interest(property); }

    private long liquidationValue(int seat) {
        long value = cash[seat];
        for (int i = 0; i < 40; i++) if (owners[i] == seat) {
            if (!mortgaged[i]) value += space(i).mortgage();
            value += (long) buildings[i] * space(i).buildCost() / 2;
        }
        return value;
    }

    private void bankrupt(Payment debt) {
        int debtor = debt.debtor(), creditor = debt.creditor();
        if (creditor >= 0 && eliminated[creditor]) creditor = -1;
        for (int group = 0; group < 8; group++) if (ownsGroup(debtor,group)) sellGroup(debtor,group);
        eliminated[debtor] = true;
        if (creditor >= 0) cash[creditor] += cash[debtor];
        cash[debtor] = 0;
        List<Resolution> transfers = new ArrayList<>();
        for (int i = 0; i < 40; i++) if (owners[i] == debtor) {
            owners[i] = creditor;
            if (creditor < 0) { mortgaged[i] = false; transfers.add(new AuctionLot(i)); }
            else if (mortgaged[i]) transfers.add(new Inheritance(creditor,i));
        }
        if (creditor >= 0) { jailCards.get(creditor).addAll(jailCards.get(debtor)); jailCards.get(debtor).clear(); }
        else for (int card : List.copyOf(jailCards.get(debtor))) returnCard(debtor,card);
        for (int i = transfers.size() - 1; i >= 0; i--) pending.addFirst(transfers.get(i));
    }

    private TradeView parseTrade(int seat,String[] parts) {
        if (parts.length != 9 || phase != Phase.ROLL && phase != Phase.MANAGE && phase != Phase.DEBT) throw new IllegalArgumentException("Trade requires a management phase");
        int recipient = number(parts[1]);
        if (recipient < 0 || recipient >= playerCount || recipient == seat || eliminated[recipient]) throw new IllegalArgumentException("Invalid trade recipient");
        long give = amount(parts[2]), take = amount(parts[3]);
        List<Integer> giveProperties = properties(parts[4]), takeProperties = properties(parts[5]);
        List<Integer> giveCards = cards(parts[6]), takeCards = cards(parts[7]);
        if (amount(parts[8]) != revision) throw new IllegalArgumentException("Stale trade revision");
        if (give == 0 && take == 0 && giveProperties.isEmpty() && takeProperties.isEmpty() && giveCards.isEmpty() && takeCards.isEmpty())
            throw new IllegalArgumentException("Empty trade");
        TradeView proposal = new TradeView(seat,recipient,give,take,giveProperties,takeProperties,giveCards,takeCards,revision + 1);
        validateAssets(proposal);
        return proposal;
    }

    private void validateAssets(TradeView proposal) {
        if (cash[proposal.proposer()] < proposal.giveCash() || cash[proposal.recipient()] < proposal.takeCash()) throw new IllegalArgumentException("Trade cash is unavailable");
        validateTransfer(proposal.proposer(),proposal.giveProperties(),proposal.giveCards());
        validateTransfer(proposal.recipient(),proposal.takeProperties(),proposal.takeCards());
    }

    private void validateTransfer(int seat,List<Integer> properties,List<Integer> cards) {
        for (int property : properties) if (owners[property] != seat || space(property).group() >= 0 && !clearGroup(space(property).group())) throw new IllegalArgumentException("Deed cannot be traded");
        if (!jailCards.get(seat).containsAll(cards)) throw new IllegalArgumentException("Jail card is unavailable");
    }

    private void acceptTrade() {
        validateAssets(trade);
        cash[trade.proposer()] += trade.takeCash() - trade.giveCash();
        cash[trade.recipient()] += trade.giveCash() - trade.takeCash();
        transfer(trade.proposer(),trade.recipient(),trade.giveProperties(),trade.giveCards());
        transfer(trade.recipient(),trade.proposer(),trade.takeProperties(),trade.takeCards());
        trade = null;
        phase = tradeReturnPhase;
        resolve();
    }

    private void transfer(int from,int to,List<Integer> properties,List<Integer> cards) {
        for (int property : properties) {
            owners[property] = to;
            if (mortgaged[property]) pending.addLast(new Inheritance(to,property));
        }
        jailCards.get(from).removeAll(cards); jailCards.get(to).addAll(cards);
    }

    private void inherit(boolean redeem) {
        Inheritance inheritance = (Inheritance) pending.removeFirst();
        int property = inheritance.property(), seat = inheritance.seat();
        if (redeem) { cash[seat] -= redemption(property); mortgaged[property] = false; }
        resolve();
    }

    private List<Integer> properties(String text) {
        if (text.equals("-")) return List.of();
        List<Integer> values = Arrays.stream(text.split(",",-1)).map(this::property).toList();
        if (new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Duplicate deed");
        return values;
    }

    private List<Integer> cards(String text) {
        if (text.equals("-")) return List.of();
        List<Integer> values = Arrays.stream(text.split(",",-1)).map(this::number).toList();
        if (new HashSet<>(values).size() != values.size() || values.stream().anyMatch(value -> value < 0 || value > 1)) throw new IllegalArgumentException("Invalid jail cards");
        return values;
    }

    private int property(String text) {
        if (!text.startsWith("p")) throw new IllegalArgumentException("Invalid property id");
        int value = number(text.substring(1));
        if (value < 0 || value >= 40 || space(value).price() == 0 || !text.equals("p" + value)) throw new IllegalArgumentException("Invalid property id");
        return value;
    }

    private int number(String text) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid number",exception); }
    }

    private long amount(String text) {
        try {
            long value = Long.parseLong(text);
            if (value < 0 || !text.equals(Long.toString(value))) throw new IllegalArgumentException("Invalid amount");
            return value;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid amount",exception); }
    }

    private Space space(int property) { return MonopolyData.spaces().get(property); }

    @Override public String timeoutAction(int seat) {
        return phase == Phase.AUCTION && seat == currentPlayer() && !finished() ? "pass" : null;
    }

    @Override public String botAction(int seat, Random random) {
        List<String> actions = legalActions(seat);
        if (actions.isEmpty()) return null;
        switch (phase) {
            case PURCHASE -> { return cash[seat] >= space(purchase).price() + 100 ? "buy" : "decline"; }
            case AUCTION -> {
                long limit = Math.min(buildingAuction ? space(auctionProperty).buildCost() : space(auctionProperty).price(),Math.max(0,cash[seat] - 150));
                return limit >= minimumBid() ? "bid:" + Math.min(limit,highestBid + 10) : "pass";
            }
            case UTILITY_RENT -> { return "roll"; }
            case TRADE -> {
                long value = trade.giveCash() - trade.takeCash() + 50L * (trade.giveCards().size() - trade.takeCards().size());
                for (int property : trade.giveProperties()) value += deedValue(seat,property);
                for (int property : trade.takeProperties()) value -= deedValue(seat,property);
                return (value >= 0 && cash[seat] + trade.giveCash() - trade.takeCash() >= 100 ? "accept:" : "reject:") + trade.revision();
            }
            case INHERIT -> { return "inherit:keep"; }
            case DEBT -> {
                if (actions.contains("pay")) return "pay";
                for (String action : actions) if (action.startsWith("sell:") || action.startsWith("sell-group:")) return action;
                for (String action : actions) if (action.startsWith("mortgage:")) return action;
                return "bankrupt";
            }
            case ROLL -> {
                for (String action : actions) if (action.startsWith("jail-card:")) return action;
                if (jailed[seat] && cash[seat] > 250) return "jail-pay";
            }
            default -> { }
        }
        for (String action : actions) if (action.startsWith("build:") && cash[seat] >= space(property(action.substring(6))).buildCost() + 200) return action;
        for (String action : actions) if (action.startsWith("redeem:") && cash[seat] >= redemption(property(action.substring(7))) + 300) return action;
        for (Space space : MonopolyData.spaces()) {
            int property = space.index(), owner = owners[property];
            if (space.group() >= 0 && owner >= 0 && owner != seat && clearGroup(space.group()) && !ownsGroup(owner,space.group())
                    && !declinedPurchases.contains(property) && groupCount(seat,space.group()) > 0
                    && groupCount(seat,space.group()) >= groupCount(owner,space.group()) && cash[seat] >= space.price() * 2L + 200) {
                return "trade:" + owner + ":" + space.price() * 2L + ":0:-:p" + property + ":-:-:" + revision;
            }
        }
        return actions.contains("roll") ? "roll" : "end";
    }

    public int groupCount(int seat,int group) {
        int count = 0;
        for (Space space : MonopolyData.spaces()) if (space.group() == group && owners[space.index()] == seat) count++;
        return count;
    }

    private long deedValue(int seat,int property) {
        long value = space(property).price();
        if (space(property).group() >= 0 && ownsGroup(seat,space(property).group())) value *= 3;
        if (mortgaged[property]) value -= redemption(property);
        return value;
    }

    @Override public Map<String, String> publicInfo() {
        Map<String,String> info = new LinkedHashMap<>();
        info.put("rules",MonopolyData.VERSION); info.put("phase",finished() ? "finished" : phase.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        info.put("revision",Long.toString(revision)); info.put("turn",Integer.toString(turn));
        info.put("dice",dieOne + "," + dieTwo); info.put("houses",Integer.toString(houses)); info.put("hotels",Integer.toString(hotels));
        info.put("event",lastEvent);
        for (int seat = 0; seat < playerCount; seat++) {
            info.put("cash." + seat,Long.toString(cash[seat])); info.put("position." + seat,Integer.toString(positions[seat]));
            info.put("eliminated." + seat,Boolean.toString(eliminated[seat]));
        }
        if (phase == Phase.PURCHASE) info.put("property",Integer.toString(purchase));
        if (phase == Phase.AUCTION) {
            info.put("property",Integer.toString(auctionProperty)); info.put("bid",Long.toString(highestBid)); info.put("bidder",Integer.toString(highestBidder));
            info.put("auction-type", buildingAuction ? buildings[auctionProperty] == 4 ? "hotel" : "house" : "property");
            if (buildingAuction) for (int seat = 0; seat < playerCount; seat++) info.put("auction-target." + seat, Integer.toString(auctionTargets[seat]));
        }
        if (phase == Phase.UTILITY_RENT) {
            info.put("property", Integer.toString(utilityProperty));
            info.put("rent-multiplier", Integer.toString(utilityMultiplier));
        }
        if (phase == Phase.DEBT) {
            Payment debt = (Payment) pending.getFirst(); info.put("debt",Long.toString(debt.amount())); info.put("creditor",Integer.toString(debt.creditor()));
        }
        if (phase == Phase.INHERIT) info.put("property",Integer.toString(((Inheritance)pending.getFirst()).property()));
        return Map.copyOf(info);
    }
}

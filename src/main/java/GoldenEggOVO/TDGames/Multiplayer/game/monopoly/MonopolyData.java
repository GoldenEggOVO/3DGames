package GoldenEggOVO.TDGames.Multiplayer.game.monopoly;

import java.util.List;

public final class MonopolyData {
    public static final String VERSION = "classic_g0009_2025";
    public static final String RULE_SOURCE = "https://instructions.hasbro.com/en-us/instruction/monopoly-board-game-classic-game-with-storage-tray-and-larger-tokens-family-games-8";

    public enum Kind { GO, PROPERTY, STATION, UTILITY, CHANCE, COMMUNITY, INCOME_TAX, LUXURY_TAX, JAIL, PARKING, GO_TO_JAIL }
    public enum Effect { ADVANCE, MONEY, JAIL_CARD, JAIL, NEAREST_STATION, NEAREST_UTILITY, BACK_THREE, REPAIRS, PAY_EACH, COLLECT_EACH }

    public record Space(int index, String code, Kind kind, int group, int price, int buildCost, List<Integer> rents) {
        public Space { rents = List.copyOf(rents); }
        public int mortgage() { return price / 2; }
    }

    public record Card(String code, Effect effect, int amount, int secondary) { }

    private static final List<Space> SPACES = List.of(
        square(0,"spawn",Kind.GO),
        property(1,"oak_grove",0,60,50,2,10,30,90,160,250),
        square(2,"village_chest",Kind.COMMUNITY),
        property(3,"birch_grove",0,60,50,4,20,60,180,320,450),
        square(4,"village_tax",Kind.INCOME_TAX), station(5,"oak_station"),
        property(6,"meadow_path",1,100,50,6,30,90,270,400,550),
        square(7,"adventure",Kind.CHANCE),
        property(8,"sunflower_path",1,100,50,6,30,90,270,400,550),
        property(9,"flower_forest",1,120,50,8,40,100,300,450,600),
        square(10,"guardhouse",Kind.JAIL),
        property(11,"copper_mine",2,140,100,10,50,150,450,625,750),
        utility(12,"redstone_power"),
        property(13,"iron_mine",2,140,100,10,50,150,450,625,750),
        property(14,"gold_mine",2,160,100,12,60,180,500,700,900), station(15,"spruce_station"),
        property(16,"pumpkin_farm",3,180,100,14,70,200,550,750,950),
        square(17,"village_chest",Kind.COMMUNITY),
        property(18,"melon_farm",3,180,100,14,70,200,550,750,950),
        property(19,"honey_farm",3,200,100,16,80,220,600,800,1000),
        square(20,"campfire_rest",Kind.PARKING),
        property(21,"desert_temple",4,220,150,18,90,250,700,875,1050),
        square(22,"adventure",Kind.CHANCE),
        property(23,"jungle_temple",4,220,150,18,90,250,700,875,1050),
        property(24,"ocean_monument",4,240,150,20,100,300,750,925,1100), station(25,"jungle_station"),
        property(26,"amethyst_cave",5,260,150,22,110,330,800,975,1150),
        property(27,"lush_cave",5,260,150,22,110,330,800,975,1150), utility(28,"waterworks"),
        property(29,"dripstone_cave",5,280,150,24,120,360,850,1025,1200),
        square(30,"guard_patrol",Kind.GO_TO_JAIL),
        property(31,"nether_fortress",6,300,200,26,130,390,900,1100,1275),
        property(32,"bastion",6,300,200,26,130,390,900,1100,1275),
        square(33,"village_chest",Kind.COMMUNITY),
        property(34,"warped_forest",6,320,200,28,150,450,1000,1200,1400), station(35,"nether_station"),
        square(36,"adventure",Kind.CHANCE),
        property(37,"end_city",7,350,200,35,175,500,1100,1300,1500),
        square(38,"treasure_tax",Kind.LUXURY_TAX),
        property(39,"dragon_island",7,400,200,50,200,600,1400,1700,2000)
    );

    private MonopolyData() { }

    public static List<Space> spaces() { return SPACES; }

    public static List<Card> chanceCards() {
        return List.of(card("return_to_spawn",Effect.ADVANCE,0),card("monument_expedition",Effect.ADVANCE,24),
                card("copper_delivery",Effect.ADVANCE,11),card("power_inspection",Effect.NEAREST_UTILITY,0),
                card("rail_express_a",Effect.NEAREST_STATION,0),card("rail_express_b",Effect.NEAREST_STATION,0),
                card("merchant_dividend",Effect.MONEY,50),card("guard_pass",Effect.JAIL_CARD,0),
                card("lost_trail",Effect.BACK_THREE,0),card("guard_detention",Effect.JAIL,0),
                new Card("workshop_repairs",Effect.REPAIRS,25,100),card("speed_fine",Effect.MONEY,-15),
                card("oak_train",Effect.ADVANCE,5),card("dragon_expedition",Effect.ADVANCE,39),
                card("village_chair",Effect.PAY_EACH,50),card("building_fund",Effect.MONEY,150));
    }

    public static List<Card> communityCards() {
        return List.of(card("return_to_spawn",Effect.ADVANCE,0),card("bank_correction",Effect.MONEY,200),
                card("healer_visit",Effect.MONEY,-50),card("ore_sale",Effect.MONEY,45),
                card("guard_pass",Effect.JAIL_CARD,0),card("guard_detention",Effect.JAIL,0),
                card("village_concert",Effect.COLLECT_EACH,50),card("harvest_fund",Effect.MONEY,100),
                card("tax_refund",Effect.MONEY,20),card("insurance",Effect.MONEY,100),
                card("healer_bill",Effect.MONEY,-100),card("school_tax",Effect.MONEY,-150),
                card("crafting_services",Effect.MONEY,25),new Card("road_repairs",Effect.REPAIRS,40,115),
                card("building_contest",Effect.MONEY,10),card("inheritance",Effect.MONEY,100));
    }

    private static Card card(String code, Effect effect, int amount) { return new Card(code,effect,amount,0); }
    private static Space square(int index,String code,Kind kind) { return new Space(index,code,kind,-1,0,0,List.of()); }
    private static Space station(int index,String code) { return new Space(index,code,Kind.STATION,-1,200,0,List.of(25,50,100,200)); }
    private static Space utility(int index,String code) { return new Space(index,code,Kind.UTILITY,-1,150,0,List.of(4,10)); }
    private static Space property(int index,String code,int group,int price,int buildCost,Integer... rents) {
        return new Space(index,code,Kind.PROPERTY,group,price,buildCost,List.of(rents));
    }
}

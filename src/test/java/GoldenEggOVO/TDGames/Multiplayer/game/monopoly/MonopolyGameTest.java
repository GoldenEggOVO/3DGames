package GoldenEggOVO.TDGames.Multiplayer.game.monopoly;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonopolyGameTest {
    private MonopolyGame game(int players) { return new MonopolyGame(players,17,Map.of()); }
    private Object field(MonopolyGame game,String name)throws Exception {
        Field field=MonopolyGame.class.getDeclaredField(name);field.setAccessible(true);return field.get(game);
    }
    private void set(MonopolyGame game,String name,Object value)throws Exception {
        Field field=MonopolyGame.class.getDeclaredField(name);field.setAccessible(true);field.set(game,value);
    }
    private int[] ints(MonopolyGame game,String name)throws Exception{return(int[])field(game,name);}
    private long[] cash(MonopolyGame game)throws Exception{return(long[])field(game,"cash");}
    private void dice(MonopolyGame game,int... values)throws Exception {
        set(game,"random",new Random(1){int cursor;@Override public int nextInt(int bound){return values[cursor++]-1;}});
    }
    private void own(MonopolyGame game,int seat,int... properties)throws Exception {for(int property:properties)ints(game,"owners")[property]=seat;}
    private void manage(MonopolyGame game)throws Exception {
        Field field=MonopolyGame.class.getDeclaredField("phase");field.setAccessible(true);
        @SuppressWarnings({"unchecked","rawtypes"}) Object value=Enum.valueOf((Class)field.getType(),"MANAGE");field.set(game,value);
    }
    @Test void classicBoardHasFixedDeedsAndFortySquares() {
        var game=game(4);assertEquals(40,game.cells().size());assertEquals(28,MonopolyData.spaces().stream().filter(s->s.price()>0).count());
        var first=MonopolyData.spaces().get(1);assertEquals(60,first.price());assertEquals(List.of(2,10,30,90,160,250),first.rents());
        assertEquals(50,first.buildCost());assertEquals(400,MonopolyData.spaces().get(39).price());
        assertEquals(List.of(50,200,600,1400,1700,2000),MonopolyData.spaces().get(39).rents());
        assertTrue(game.players().stream().allMatch(p->p.cash()==1500&&p.position()==0&&!p.eliminated()));
    }
    @Test void thirdConsecutiveDoubleGoesDirectlyToJailWithoutGoSalary()throws Exception {
        var game=game(2);dice(game,1,1,2,2,6,6);
        game.apply(0,"roll"); if(game.legalActions(0).contains("buy"))game.apply(0,"buy");
        game.apply(0,"roll");if(game.legalActions(0).contains("buy"))game.apply(0,"buy");
        ints(game,"positions")[0]=39;long before=cash(game)[0];game.apply(0,"roll");
        assertTrue(game.players().get(0).jailed());assertEquals(10,game.players().get(0).position());assertEquals(before,cash(game)[0]);
        assertFalse(game.legalActions(0).contains("roll"));
    }
    @Test void ordinaryMovementCollectsGoAndUnownedPropertyCanBeBought()throws Exception {
        var game=game(2);dice(game,1,2);ints(game,"positions")[0]=38;game.apply(0,"roll");
        assertEquals(1700,cash(game)[0]);assertEquals(1,game.players().get(0).position());game.apply(0,"buy");
        assertEquals(1640,cash(game)[0]);assertEquals(0,game.properties().get(1).owner());
    }
    @Test void decliningPurchaseAuctionsToAllAndAllowsRejoiningBeforeTheAuctionEnds()throws Exception {
        var game=game(3);dice(game,1,2);game.apply(0,"roll");game.apply(0,"decline");
        assertEquals(0,game.currentPlayer());game.apply(0,"bid:20");game.apply(1,"pass");
        assertEquals(2,game.currentPlayer());game.apply(2,"bid:30");assertEquals(0,game.currentPlayer());
        game.apply(1,"bid:40");game.apply(0,"pass");game.apply(2,"pass");
        assertEquals(1,game.properties().get(3).owner());assertEquals(1460,cash(game)[1]);assertEquals(0,game.currentPlayer());
    }
    @Test void noBidsReturnsLotToBankAndAuctionTimeoutIsOnlyPass()throws Exception {
        var game=game(2);dice(game,1,2);game.apply(0,"roll");game.apply(0,"decline");assertEquals("pass",game.timeoutAction(0));
        game.apply(0,"pass");game.apply(1,"pass");assertEquals(-1,game.properties().get(3).owner());assertFalse(game.finished());
    }
    @Test void monopolyBaseRentWorksEvenWithAnotherGroupDeedMortgaged()throws Exception {
        var game=game(2);own(game,1,1,3);((boolean[])field(game,"mortgaged"))[3]=true;dice(game,1,2);ints(game,"positions")[0]=38;
        game.apply(0,"roll");assertEquals(1696,cash(game)[0]);assertEquals(1504,cash(game)[1]);
        var noRent=game(2);own(noRent,1,3);((boolean[])field(noRent,"mortgaged"))[3]=true;dice(noRent,1,2);
        noRent.apply(0,"roll");assertEquals(1500,cash(noRent)[0]);
    }
    @Test void evenConstructionUsesRealHouseAndHotelInventory()throws Exception {
        var game=game(2);own(game,0,1,3);cash(game)[0]=10000;manage(game);
        game.apply(0,"build:p1");assertThrows(IllegalArgumentException.class,()->game.apply(0,"build:p1"));game.apply(0,"build:p3");
        for(int i=1;i<4;i++){game.apply(0,"build:p1");game.apply(0,"build:p3");}
        assertEquals(24,game.bankHouses());game.apply(0,"build:p1");assertEquals(28,game.bankHouses());assertEquals(11,game.bankHotels());
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"mortgage:p3"));
        game.apply(0,"sell:p1");assertEquals(24,game.bankHouses());assertEquals(12,game.bankHotels());
    }
    @Test void cannotInventHousesWhenDowngradingHotelButWholeGroupMayBeSold()throws Exception {
        var game=game(2);own(game,0,1,3);manage(game);ints(game,"buildings")[1]=5;ints(game,"buildings")[3]=5;
        set(game,"houses",0);set(game,"hotels",10);
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"sell:p1"));game.apply(0,"sell-group:0");
        assertEquals(0,game.properties().get(1).buildings());assertEquals(12,game.bankHotels());assertEquals(1750,cash(game)[0]);
    }
    @Test void mortgagesAndRedemptionChargePrincipalAndTenPercentInterest()throws Exception {
        var game=game(2);own(game,0,1);manage(game);game.apply(0,"mortgage:p1");assertEquals(1530,cash(game)[0]);
        game.apply(0,"redeem:p1");assertEquals(1497,cash(game)[0]);assertFalse(game.properties().get(1).mortgaged());
    }
    @Test void jailThirdFailureCollectsFineBeforeMovingAndNoDoubleBonusAfterRelease()throws Exception {
        var game=game(2);((boolean[])field(game,"jailed"))[0]=true;ints(game,"positions")[0]=10;ints(game,"jailAttempts")[0]=2;
        dice(game,1,2);game.apply(0,"roll");assertFalse(game.players().get(0).jailed());assertEquals(1450,cash(game)[0]);
        assertEquals(13,game.players().get(0).position());game.apply(0,"buy");assertFalse(game.legalActions(0).contains("roll"));
        var doubles=game(2);((boolean[])field(doubles,"jailed"))[0]=true;ints(doubles,"positions")[0]=10;
        dice(doubles,2,2);doubles.apply(0,"roll");doubles.apply(0,"buy");assertFalse(doubles.legalActions(0).contains("roll"));
    }
    @Test void unpaidRentEntersDebtAndDoesNotImmediatelyEliminatePlayer()throws Exception {
        var game=game(3);own(game,1,3);ints(game,"buildings")[3]=5;own(game,0,5);cash(game)[0]=10;dice(game,1,2);
        game.apply(0,"roll");assertFalse(game.eliminated(0));assertEquals("debt",game.publicInfo().get("phase"));
        assertFalse(game.legalActions(0).contains("roll"));assertTrue(game.legalActions(0).contains("mortgage:p5"));
        assertTrue(game.legalActions(0).contains("bankrupt"));game.apply(0,"bankrupt");assertTrue(game.eliminated(0));
        assertEquals(1,game.properties().get(5).owner());assertFalse(game.finished());
    }
    @Test void cannotDeclareBankruptcyWhenLiquidationCanPay()throws Exception {
        var game=game(3);own(game,1,3);own(game,0,5);cash(game)[0]=0;dice(game,1,2);game.apply(0,"roll");
        assertFalse(game.legalActions(0).contains("bankrupt"));assertThrows(IllegalArgumentException.class,()->game.apply(0,"bankrupt"));
        game.apply(0,"mortgage:p5");assertEquals(96,cash(game)[0]);assertEquals("manage",game.publicInfo().get("phase"));
    }
    @Test void tradeNeedsBothConfirmationAndCurrentRevisionAndIsAtomic()throws Exception {
        var game=game(3);own(game,0,1);own(game,1,3);manage(game);long revision=game.revision();
        game.apply(0,"trade:1:100:20:p1:p3:-:-:"+revision);
        assertEquals(1,game.currentPlayer());assertEquals(0,game.properties().get(1).owner());
        var before=game.publicInfo();assertThrows(IllegalArgumentException.class,()->game.apply(1,"accept:"+revision));assertEquals(before,game.publicInfo());
        game.apply(1,"accept:"+game.revision());assertEquals(1,game.properties().get(1).owner());assertEquals(0,game.properties().get(3).owner());
        assertEquals(1420,cash(game)[0]);assertEquals(1580,cash(game)[1]);
    }
    @Test void transferredMortgagesOfferImmediateRedemptionOrKeepingWithoutATransferFee()throws Exception {
        var game=game(3);own(game,0,1);((boolean[])field(game,"mortgaged"))[1]=true;manage(game);
        game.apply(0,"trade:1:0:0:p1:-:-:-:"+game.revision());game.apply(1,"accept:"+game.revision());
        assertEquals(1,game.currentPlayer());game.apply(1,"inherit:keep");assertEquals(1500,cash(game)[1]);
        assertTrue(game.properties().get(1).mortgaged());
        var redeem=game(3);own(redeem,0,1);((boolean[])field(redeem,"mortgaged"))[1]=true;manage(redeem);
        redeem.apply(0,"trade:1:0:0:p1:-:-:-:"+redeem.revision());redeem.apply(1,"accept:"+redeem.revision());
        redeem.apply(1,"inherit:redeem");assertEquals(1467,cash(redeem)[1]);assertFalse(redeem.properties().get(1).mortgaged());
    }
    @Test void invalidInputAndUnaffordableBidsDoNotConsumeRandomOrMutateState()throws Exception {
        var first=game(3);var replay=game(3);var before=first.publicInfo();
        for(String action:Arrays.asList(null,"","roll:1","bid:-1","buy","trade:999:0:0:-:-:-:-:0")) {
            assertThrows(IllegalArgumentException.class,()->first.apply(0,action));assertEquals(before,first.publicInfo());
        }
        assertThrows(IllegalArgumentException.class,()->first.apply(1,"roll"));
        first.apply(0,"roll");replay.apply(0,"roll");assertEquals(first.players(),replay.players());assertEquals(first.publicInfo(),replay.publicInfo());
    }
    @SuppressWarnings("unchecked") private ArrayDeque<MonopolyData.Card> deck(MonopolyGame game,String name)throws Exception {
        return(ArrayDeque<MonopolyData.Card>)field(game,name);
    }
    private void top(MonopolyGame game,String deck,MonopolyData.Card card)throws Exception {
        var cards=deck(game,deck);cards.removeIf(existing->existing.code().equals(card.code()));cards.addFirst(card);
    }
    @Test void eventMovementCollectsGoAndBackThreeCanTriggerASecondEvent()throws Exception {
        var game=game(3);top(game,"community",new MonopolyData.Card("return_to_spawn",MonopolyData.Effect.ADVANCE,0,0));
        ints(game,"positions")[0]=39;dice(game,1,2);game.apply(0,"roll");assertEquals(1900,cash(game)[0]);assertEquals(0,game.players().get(0).position());
        var back=game(3);top(back,"chance",new MonopolyData.Card("lost_trail",MonopolyData.Effect.BACK_THREE,0,0));
        top(back,"community",new MonopolyData.Card("harvest_fund",MonopolyData.Effect.MONEY,100,0));
        ints(back,"positions")[0]=33;dice(back,1,2);back.apply(0,"roll");assertEquals(33,back.players().get(0).position());assertEquals(1600,cash(back)[0]);
    }
    @Test void utilityEventUsesAFreshRollAndStationEventDoublesRent()throws Exception {
        var utility=game(3);own(utility,1,12);top(utility,"chance",new MonopolyData.Card("power_inspection",MonopolyData.Effect.NEAREST_UTILITY,0,0));
        dice(utility,1,6,2,3);utility.apply(0,"roll");utility.apply(0,"roll");assertEquals(1450,cash(utility)[0]);assertEquals(1550,cash(utility)[1]);
        var rail=game(3);own(rail,1,5,15);top(rail,"chance",new MonopolyData.Card("rail_express_a",MonopolyData.Effect.NEAREST_STATION,0,0));
        ints(rail,"positions")[0]=33;dice(rail,1,2);rail.apply(0,"roll");assertEquals(1600,cash(rail)[0]);assertEquals(1600,cash(rail)[1]);
    }
    @Test void jailCardLeavesDeckWhileHeldAndReturnsAtBottomWhenUsed()throws Exception {
        var game=game(3);top(game,"chance",new MonopolyData.Card("guard_pass",MonopolyData.Effect.JAIL_CARD,0,0));
        dice(game,1,6);game.apply(0,"roll");assertEquals(List.of(0),game.players().get(0).jailCards());assertEquals(15,deck(game,"chance").size());
        ((boolean[])field(game,"jailed"))[0]=true;
        Field phase=MonopolyGame.class.getDeclaredField("phase");phase.setAccessible(true);
        @SuppressWarnings({"unchecked","rawtypes"}) Object roll=Enum.valueOf((Class)phase.getType(),"ROLL");phase.set(game,roll);
        game.apply(0,"jail-card:0");assertFalse(game.players().get(0).jailed());assertTrue(game.players().get(0).jailCards().isEmpty());
        assertEquals(16,deck(game,"chance").size());assertEquals(MonopolyData.Effect.JAIL_CARD,deck(game,"chance").getLast().effect());
    }
    @Test void printedTaxesDoNotDependOnMortgagedDeedPrices()throws Exception {
        var game=game(3);own(game,0,1,3);cash(game)[0]=1000;((boolean[])field(game,"mortgaged"))[3]=true;
        ints(game,"positions")[0]=1;dice(game,1,2);game.apply(0,"roll");assertEquals("manage",game.publicInfo().get("phase"));
        assertEquals(800,cash(game)[0]);
        var luxury=game(3);ints(luxury,"positions")[0]=35;dice(luxury,1,2);luxury.apply(0,"roll");assertEquals(1400,cash(luxury)[0]);
    }
    @Test void jailFineDebtMustResolveBeforeThirdFailedRollMovesToken()throws Exception {
        var game=game(3);own(game,0,5);cash(game)[0]=0;((boolean[])field(game,"jailed"))[0]=true;
        ints(game,"positions")[0]=10;ints(game,"jailAttempts")[0]=2;dice(game,1,2);game.apply(0,"roll");
        assertEquals(10,game.players().get(0).position());assertTrue(game.players().get(0).jailed());
        game.apply(0,"mortgage:p5");assertEquals(13,game.players().get(0).position());assertFalse(game.players().get(0).jailed());
        assertEquals(50,cash(game)[0]);assertFalse(game.legalActions(0).contains("roll"));
    }
    @Test void bankBankruptcyClearsMortgagesAndAuctionsEachRecoveredDeed()throws Exception {
        var game=game(3);own(game,0,1,3);cash(game)[0]=0;((boolean[])field(game,"mortgaged"))[1]=true;
        ints(game,"positions")[0]=1;dice(game,1,2);game.apply(0,"roll");game.apply(0,"bankrupt");
        assertTrue(game.eliminated(0));assertEquals("auction",game.publicInfo().get("phase"));assertEquals("1",game.publicInfo().get("property"));
        assertFalse(game.properties().get(1).mortgaged());game.apply(1,"bid:10");game.apply(2,"pass");
        assertEquals("3",game.publicInfo().get("property"));game.apply(1,"pass");game.apply(2,"pass");
        assertEquals(1,game.properties().get(1).owner());assertEquals(-1,game.properties().get(3).owner());assertEquals(1,game.currentPlayer());
    }
    @Test void keepingInheritedMortgagesDoesNotCreateAnotherDebt()throws Exception {
        var game=game(4);own(game,0,39);((boolean[])field(game,"mortgaged"))[39]=true;cash(game)[0]=0;cash(game)[1]=0;set(game,"turn",1);
        top(game,"community",new MonopolyData.Card("village_concert",MonopolyData.Effect.COLLECT_EACH,50,0));dice(game,1,1);game.apply(1,"roll");
        assertEquals(0,game.currentPlayer());game.apply(0,"bankrupt");assertEquals(1,game.currentPlayer());game.apply(1,"inherit:keep");
        assertEquals("manage",game.publicInfo().get("phase"));assertTrue(game.eliminated(0));assertFalse(game.eliminated(1));
        assertEquals(100,cash(game)[1]);assertEquals(1,game.properties().get(39).owner());
        assertEquals(1,game.currentPlayer());assertFalse(game.finished());assertFalse(game.eliminated(2));
    }
    @Test void lastSolventPlayerWinsAfterNaturalBankruptcy()throws Exception {
        var game=game(2);own(game,1,3);cash(game)[0]=0;dice(game,1,2);game.apply(0,"roll");game.apply(0,"bankrupt");
        assertEquals("winner:1",game.outcome());assertTrue(game.eliminated(0));assertFalse(game.eliminated(1));assertTrue(game.legalActions(1).isEmpty());
    }
    @Test void expiredTradeAndMalformedCollectionsNeverPartiallyTransferAssets()throws Exception {
        var game=game(3);own(game,0,1);manage(game);var before=game.players();
        for(String action:List.of("trade:1:1:0:p1,p1:-:-:-:0","trade:1:1:0:p0:-:-:-:0","trade:1:1:0:p1:-:2:-:0","trade:1:1501:0:p1:-:-:-:0"))
            assertThrows(IllegalArgumentException.class,()->game.apply(0,action));
        assertEquals(before,game.players());game.apply(0,"mortgage:p1");
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"trade:1:0:0:p1:-:-:-:0"));assertEquals(0,game.properties().get(1).owner());
    }
    @Test void emptyHouseOrHotelSupplyAndUnevenSellingAreRejected()throws Exception {
        var game=game(3);own(game,0,1,3);manage(game);set(game,"houses",0);
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"build:p1"));
        ints(game,"buildings")[1]=4;ints(game,"buildings")[3]=4;set(game,"hotels",0);
        assertThrows(IllegalArgumentException.class,()->game.apply(0,"build:p1"));
        set(game,"hotels",12);game.apply(0,"build:p1");assertThrows(IllegalArgumentException.class,()->game.apply(0,"sell:p3"));
    }
    @Test void inheritedMortgagesDoNotEliminateTheLastSolventPlayer()throws Exception {
        var game=game(2);own(game,0,39);((boolean[])field(game,"mortgaged"))[39]=true;cash(game)[0]=0;cash(game)[1]=0;set(game,"turn",1);
        top(game,"community",new MonopolyData.Card("village_concert",MonopolyData.Effect.COLLECT_EACH,50,0));dice(game,1,1);game.apply(1,"roll");
        game.apply(0,"bankrupt");game.apply(1,"inherit:keep");
        assertEquals("winner:1",game.outcome());assertTrue(game.eliminated(0));assertFalse(game.eliminated(1));
    }
    @Test void fullGamesConserveBuildingInventoryAndQueriesNeverMutate() {
        for(int count=2;count<=6;count++) {
            var game=new MonopolyGame(count,77+count,Map.of());Random random=new Random(1);
            for(int step=0;step<10000&&!game.finished();step++) {
                int seat=game.currentPlayer();var players=game.players();var properties=game.properties();var info=game.publicInfo();
                String action=game.botAction(seat,random);game.botAction(seat,random);game.controlActions(seat);game.cells();
                assertEquals(players,game.players());assertEquals(properties,game.properties());assertEquals(info,game.publicInfo());
                assertThrows(IllegalArgumentException.class,()->game.apply(seat,"roll:invalid"));
                assertEquals(info,game.publicInfo());game.apply(seat,action);
                int houseCount=game.bankHouses(),hotelCount=game.bankHotels();
                for(var property:game.properties()) {
                    if(property.buildings()==5)hotelCount++;else houseCount+=property.buildings();
                    assertFalse(property.mortgaged()&&property.buildings()>0);
                }
                assertEquals(32,houseCount);assertEquals(12,hotelCount);
                assertTrue(game.players().stream().allMatch(player->player.cash()>=0));
            }
            assertTrue(game.finished(),"players="+count);
        }
    }
    @Test void botCanTradeForMissingGroupAndAcceptsAFavourableOffer()throws Exception {
        var game=game(3);own(game,0,1);own(game,1,3);manage(game);
        String offer=game.botAction(0,new Random(1));assertTrue(offer.startsWith("trade:1:"));game.apply(0,offer);
        assertEquals("accept:"+game.revision(),game.botAction(1,new Random(1)));game.apply(1,game.botAction(1,new Random(1)));
        assertEquals(0,game.properties().get(1).owner());assertEquals(0,game.properties().get(3).owner());
    }
    @Test void boundedBotActionsReplayAnEntireGame() {
        var game=game(3);var replay=game(3);Random random=new Random(91);
        for(int step=0;step<15000&&!game.finished();step++) {
            int seat=game.currentPlayer();String action=game.botAction(seat,random);assertNotNull(action,"phase="+game.publicInfo());
            game.apply(seat,action);replay.apply(seat,action);assertEquals(game.players(),replay.players());
            assertEquals(game.properties(),replay.properties());assertEquals(game.publicInfo(),replay.publicInfo());
        }
        assertTrue(game.finished());assertTrue(game.outcome().startsWith("winner:"));assertEquals(game.outcome(),replay.outcome());
    }

    @Test void bankruptcyOnThirdJailFailureDoesNotReleaseOrMoveEliminatedPlayer() throws Exception {
        var game = game(3);
        own(game, 0, 1);
        ((boolean[])field(game, "mortgaged"))[1] = true;
        ((boolean[])field(game, "jailed"))[0] = true;
        ints(game, "positions")[0] = 10;
        ints(game, "jailAttempts")[0] = 2;
        cash(game)[0] = 0;
        dice(game, 1, 2);
        game.apply(0, "roll");
        assertEquals("debt", game.publicInfo().get("phase"));
        game.apply(0, "bankrupt");
        assertEquals("auction", game.publicInfo().get("phase"));
        game.apply(1, "pass");
        game.apply(2, "pass");
        assertTrue(game.eliminated(0));
        assertTrue(game.players().get(0).jailed());
        assertEquals(10, game.players().get(0).position());
        assertEquals(1, game.currentPlayer());
        assertEquals(List.of("roll", "trade:2:1:0:-:-:-:-:" + game.revision()), game.legalActions(1));
        var before = game.players();
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "roll"));
        assertEquals(before, game.players());
    }
}

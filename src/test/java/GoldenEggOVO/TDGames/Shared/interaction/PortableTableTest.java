package GoldenEggOVO.TDGames.Shared.interaction;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.GameRulesTest;
import GoldenEggOVO.TDGames.Shared.render.TablePlacement;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.Language;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortableTableTest {
 @Test void sideTrayHasClearanceAgainstNeighborTablesWithoutMovingLegacyAnchors(){
  assertFalse(TablePlacement.overlaps(0,80,0,false,3,80,0,false));
  assertTrue(TablePlacement.overlaps(0,80,0,true,3,80,0,false));
  assertTrue(TablePlacement.overlaps(3,80,0,false,0,80,0,true));
  assertFalse(TablePlacement.overlaps(0,80,0,true,5,80,0,false));
  assertFalse(TablePlacement.overlaps(0,80,0,true,3,83,0,false));
 }
 @TempDir Path dir;
 @Test void enlargedMahjongEdgeCannotIntersectAnAdjacentSideTray(){
  assertTrue(TablePlacement.overlaps(0,80,0,true,1.125,4.2,80,0,false,1.5));
  assertTrue(TablePlacement.overlaps(4.2,80,0,false,1.5,0,80,0,true,1.125));
  assertFalse(TablePlacement.overlaps(0,80,0,true,1.125,4.5,80,0,false,1.5));
 }
 @BeforeEach void setup(){MockBukkit.mock();}

 @Test void nativeDiceMidpointRunsBetweenPointerTicksAndStopsOnClose() throws Exception {
  var server=MockBukkit.getMock();
  var fixture=new GoldenEggOVO.TDGames.Shared.render.TableViewTest.Fixture("yacht");
  var plugin=fixture.plugin;
  when(plugin.getName()).thenReturn("3DGames");when(plugin.namespace()).thenReturn("3DGames");
  when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
  when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDataFolder()).thenReturn(dir.toFile());
  var controller=new TableWorldController(plugin);
  var view=spy(fixture.view);
  doNothing().when(view).tick();doNothing().when(view).renderNativeDiceMidpoint();doNothing().when(view).close();
  var viewsField=TableWorldController.class.getDeclaredField("views");viewsField.setAccessible(true);
  @SuppressWarnings("unchecked") var views=(Map<UUID,GoldenEggOVO.TDGames.Shared.render.TableView>)viewsField.get(controller);
  views.put(fixture.room.id,view);
  try {
   controller.initialize();server.getScheduler().performTicks(3);
   verify(view).tick();verify(view).renderNativeDiceMidpoint();
   server.getScheduler().performTicks(2);
   verify(view,times(2)).tick();verify(view,times(2)).renderNativeDiceMidpoint();
   controller.close();server.getScheduler().performTicks(3);
   verify(view,times(2)).renderNativeDiceMidpoint();
  } finally {controller.close();fixture.view.close();}
 }
 @AfterEach void cleanup(){MockBukkit.unmock();}
 @Test void connectFourSeatsInitiallyLookTowardTheUpperBoard() throws Exception {
  var plugin=SessionTestSupport.plugin();
  var tableWorld=mock(TableWorldController.class,CALLS_REAL_METHODS);GameRulesTest.set(tableWorld,"plugin",plugin);
  var world=mock(World.class);var block=mock(org.bukkit.block.Block.class);
  when(world.getBlockAt(any(Location.class))).thenReturn(block);when(block.isPassable()).thenReturn(true);
  doAnswer(call->new Location(world,0,80,0)).when(tableWorld).center(anyInt());
  var room=new GameSession(UUID.randomUUID(),"connectfour",2,1,0);
  for(int seat=0;seat<2;seat++) assertTrue(tableWorld.seatLocation(room,seat).getPitch() < -10);
 }
 @Test void monopolySeatsAvoidTheDiceTableAndKeepTheWholeBoardInReach() throws Exception {
  var plugin=SessionTestSupport.plugin();SessionTestSupport.install(plugin,new LinkedHashMap<UUID,GameSession>());
  var tableWorld=mock(TableWorldController.class,CALLS_REAL_METHODS);GameRulesTest.set(tableWorld,"plugin",plugin);
  var world=mock(World.class);var block=mock(org.bukkit.block.Block.class);
  when(world.getBlockAt(any(Location.class))).thenReturn(block);when(block.isPassable()).thenReturn(true);
  doAnswer(call->new Location(world,0,80,0)).when(tableWorld).center(anyInt());
  for(int players=2;players<=6;players++) {
   var room=new GameSession(UUID.randomUUID(),"monopoly",players,1,0);
   room.anchorWorld=world.getUID();room.anchorY=80;room.sideTray=true;
   SessionTestSupport.mutableSessions(plugin).clear();SessionTestSupport.mutableSessions(plugin).put(room.id,room);
   for(int seat=0;seat<players;seat++) {
    var at=tableWorld.seatLocation(room,seat);
    assertTrue(at.getX()+.3<2.075||at.getX()-.3>3.475||Math.abs(at.getZ())-.3>.7,"Player body intersects dice side table: "+players+"/"+seat);
    for(double x:new double[]{-1.74,1.74})for(double z:new double[]{-1.74,1.74})
     assertTrue(at.clone().add(0,1.62,0).distance(new Location(world,x,80+GoldenEggOVO.TDGames.Shared.render.TableGeometry.SURFACE,z))<5.5,"Board corner out of reach: "+players+"/"+seat);
   }
  }
 }
 @Test void newTablesCannotIntersectExistingModelsButOtherFloorsAndWorldsRemainAvailable()throws Exception{
  var plugin=SessionTestSupport.plugin();SessionTestSupport.install(plugin, new LinkedHashMap<UUID,GameSession>());
  var tableWorld=mock(TableWorldController.class,CALLS_REAL_METHODS);GameRulesTest.set(tableWorld,"plugin",plugin);
  World world=MockBukkit.getMock().addSimpleWorld("tables"),other=MockBukkit.getMock().addSimpleWorld("other");
  GameSession first=new GameSession(UUID.randomUUID(),"connectfour",2,0,0);tableWorld.anchor(first,new Location(world,0,80,0));SessionTestSupport.mutableSessions(plugin).put(first.id,first);
  GameSession next=new GameSession(UUID.randomUUID(),"chess",2,0,1);
  var overlap = assertThrows(IllegalArgumentException.class,()->tableWorld.anchor(next,new Location(world,0,80,0)));
  assertEquals(Language.component("error.table-overlap"), Language.error(overlap));
  assertThrows(IllegalArgumentException.class,()->tableWorld.anchor(next,new Location(world,2,80,2)));
  assertNull(next.anchorWorld,"A rejected placement must not set an anchor");
  assertDoesNotThrow(()->tableWorld.anchor(next,new Location(world,3,80,0)));
  assertDoesNotThrow(()->tableWorld.anchor(next,new Location(world,0,83,0)));
  assertDoesNotThrow(()->tableWorld.anchor(next,new Location(other,0,80,0)));
 }
 @Test void arbitraryWorldAndGridCenterArePreservedAndSeatChecksDoNotEditTerrain() throws Exception {
  var server=MockBukkit.getMock();var w=server.addSimpleWorld("survival");var other=server.addSimpleWorld("creative");
  var plugin=SessionTestSupport.plugin();when(plugin.getName()).thenReturn("3DGames");when(plugin.namespace()).thenReturn("3DGames");when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDataFolder()).thenReturn(dir.toFile());
  var tableWorld=new TableWorldController(plugin);int worlds=server.getWorlds().size();tableWorld.initialize();assertEquals(worlds,server.getWorlds().size());
  var r=new GameSession(UUID.randomUUID(),"gomoku",2,1,0);var c=new Location(w,-12.25,83.5,17.75);tableWorld.anchor(r,c);SessionTestSupport.mutableSessions(plugin).put(r.id,r);
  assertEquals(new Location(w,-12,83,18),tableWorld.center(0));
  var p=server.addPlayer();p.teleport(c);assertTrue(tableWorld.atTableWorld(p,r));p.teleport(other.getSpawnLocation());assertFalse(tableWorld.atTableWorld(p,r));

  var collisionWorld=spy(w);var block=mock(org.mockbukkit.mockbukkit.block.BlockMock.class);when(block.isPassable()).thenReturn(true);
  doReturn(block).when(collisionWorld).getBlockAt(org.mockito.ArgumentMatchers.any(Location.class));
  try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
   bukkit.when(()->Bukkit.getWorld(r.anchorWorld)).thenReturn(collisionWorld);
   var seat=tableWorld.seatLocation(r,0);assertEquals(w.getUID(),seat.getWorld().getUID());assertEquals(83,seat.getY());
   when(block.isPassable()).thenReturn(false);assertThrows(IllegalArgumentException.class,()->tableWorld.seatLocation(r,0));
   verify(block,never()).setType(org.mockito.ArgumentMatchers.any(Material.class));
  }
  assertEquals(Material.AIR,w.getBlockAt(c).getType());

 }
}


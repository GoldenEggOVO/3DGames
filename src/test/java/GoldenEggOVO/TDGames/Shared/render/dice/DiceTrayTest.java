package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.render.BoardMapStore;
import GoldenEggOVO.TDGames.Shared.render.TableView;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.Core.pack.ResourcePackService;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.game.GameFactory;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiceTrayTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}

    @Test void fullAndCompactTraysStayOutsideTheMainBoardAndHaveReachLimitedHits() {
        var f=new TableViewTest.Fixture("chess");
        for(boolean compact:new boolean[]{false,true}) {
            var tray=tray(f,compact);Location center=tray.center();double width=compact?.5:1.4;
            assertTrue(center.getX()-width/2>1.125,"The side table must clear the main board");
            assertEquals(80+TableGeometry.SURFACE,center.getY(),1e-6);
            assertTrue(tray.hit(center.clone().add(0,2,0),new Vector(0,-1,0))>=0);
            assertEquals(-1,tray.hit(new Location(f.world,1.15,center.getY()+2,0),new Vector(0,-1,0)));
            assertEquals(-1,tray.hit(center.clone().add(width,2,0),new Vector(0,-1,0)));
            assertEquals(-1,tray.hit(center.clone().add(0,8,0),new Vector(0,-1,0)));
            assertEquals(-1,tray.hit(center.clone().add(0,2,0),new Vector(0,1,0)));
            center.add(9,9,9);assertNotEquals(center,tray.center(),"Callers cannot move the tray anchor");tray.close();
        }
    }

    @Test void unmanagedVanillaDiceRemainVisibleWithoutPackAudienceTracking() {
        var f=new TableViewTest.Fixture("chess");
        int before=f.entities.size();
        var tray=tray(f,false);
        for(Entity part:f.entities.subList(before,f.entities.size()))verify(part).setVisibleByDefault(true);
        tray.close();
    }

    @Test void throwsReuseTheirEntitiesSettleAndBecomeQuietUntilNextAction() {
        var f=new TableViewTest.Fixture("chess");when(f.plugin.getConfig()).thenReturn(new YamlConfiguration());
        int initial=f.entities.size();var tray=tray(f,false);List<Entity> parts=List.copyOf(f.entities.subList(initial,f.entities.size()));
        int count=f.entities.size();tray.roll(6,91);assertTrue(tray.rolling());
        for(int i=0;i<DiceMotion.FRAMES;i++)tray.tick();assertFalse(tray.rolling());assertEquals(count,f.entities.size());
        for(Entity part:parts)clearInvocations(part);
        for(int i=0;i<30;i++)tray.tick();
        for(Entity part:parts)if(part instanceof Display display)verify(display,never()).setTransformation(any());
        tray.roll(2,7);tray.tick();tray.settle(4);assertFalse(tray.rolling());assertEquals(count,f.entities.size());
        tray.close();tray.close();for(Entity part:parts)verify(part).remove();
    }

    @Test void nativeMidpointRendersAnExtraPoseWithoutAdvancingTheThrow() throws Exception {
        var f = new TableViewTest.Fixture("chess");
        var tray = tray(f, false);
        try {
            tray.roll(6, 91);
            tray.tick();
            var motions = (DiceMotion[]) TableViewTest.field(tray, "motions");
            var dice = (List<?>) TableViewTest.field(tray, "nativeDice");
            var body = (BlockDisplay) TableViewTest.field(dice.getFirst(), "body");
            var poses = (DiceMotion.Pose[]) TableViewTest.field(tray, "poses");
            var authoritative = poses[0];
            var expected = motions[0].pose(1.5);
            tray.renderNativeMidpoint();
            var transform = f.transforms.get(body);
            var center = new org.joml.Matrix4f().translation(transform.getTranslation())
                    .rotate(transform.getLeftRotation()).scale(transform.getScale())
                    .rotate(transform.getRightRotation()).transformPosition(new org.joml.Vector3f(.5f));
            assertEquals(expected.x(), center.x, 1e-6);
            assertEquals(expected.y() + .015, center.y, 1e-6);
            assertEquals(expected.z(), center.z, 1e-6);
            assertEquals(1, Math.abs(expected.rotation().dot(transform.getLeftRotation())), 1e-6);
            assertSame(authoritative, poses[0], "Intermediate visuals must not advance game state");
            assertEquals(1, TableViewTest.field(tray, "frame"));
        } finally { tray.close(); f.view.close(); }
    }

    @Test void repeatedLabelsDoNotResendUnchangedText() {
        var f=new TableViewTest.Fixture("chess");int initial=f.entities.size();var tray=tray(f,false);
        TextDisplay label=(TextDisplay)f.entities.subList(initial,f.entities.size()).stream().filter(TextDisplay.class::isInstance).findFirst().orElseThrow();
        clearInvocations(label);Component text=Component.text("Roll the dice");tray.label(text);tray.label(text);
        verify(label).text(text);tray.close();
    }

    @Test void mixedPlayersSeeTheirTrayAndDieLayerWithSharedLegsAndControls() {
        for(boolean compact:new boolean[]{false,true}) {
            var f=mixedFixture();Player nativePlayer=nativePlayer(f);
            when(f.world.getPlayers()).thenReturn(List.of(f.player,nativePlayer));
            loadPack(f);
            var tray=tray(f,compact);
            List<ItemDisplay> packed=f.entities.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
            assertEquals(2,packed.size(),"A packed tray needs one tabletop and one die");
            var table=f.transforms.get(packed.getFirst());
            assertEquals(compact?.5:1.4,table.getScale().x,1e-6);
            assertEquals(1,table.getScale().y,1e-6,"Rim height must not change with tray width");
            assertEquals(compact?.5:1.4,table.getScale().z,1e-6);
            for(ItemDisplay part:packed) {
                verify(f.player).showEntity(f.plugin,part);
                verify(nativePlayer,never()).showEntity(f.plugin,part);
            }
            var layers=groupParts(f);
            assertEquals(6,layers.common().size(),"Four legs, label and click target must remain common");
            assertTrue(layers.nativeOnly().size()>6,"The native tabletop and six-face die must remain available");
            for(Entity part:layers.common()) {
                verify(f.player).showEntity(f.plugin,part);
                verify(nativePlayer).showEntity(f.plugin,part);
            }
            for(Entity part:layers.nativeOnly()) {
                verify(nativePlayer).showEntity(f.plugin,part);
                verify(f.player,never()).showEntity(f.plugin,part);
            }
            long legs=layers.common().stream().filter(BlockDisplay.class::isInstance)
                .filter(e->Math.abs(f.transforms.get(e).getTranslation().y+TableGeometry.SURFACE)<1e-5).count();
            assertEquals(4,legs,"Both tray widths must keep legs reaching the original ground");
            tray.close();for(Entity part:f.entities)verify(part).remove();
        }
    }

    @Test void midpointDoesNotChangeThePackedDieInMixedMode() {
        var f = mixedFixture(); Player nativePlayer = nativePlayer(f);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, nativePlayer));
        loadPack(f); var tray = tray(f, false);
        try {
            tray.roll(6, 91); tray.tick();
            var packed = f.entities.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
            assertEquals(2, packed.size());
            packed.forEach(org.mockito.Mockito::clearInvocations);
            tray.renderNativeMidpoint();
            for (var display : packed) verify(display, never()).setTransformation(any());
        } finally { tray.close(); }
    }

    @Test void dualDiceKeepTheirSeparateResultsAcrossPackChangesWithoutLeavingTheTray() {
        var f=mixedFixture();
        var audience=new GoldenEggOVO.TDGames.Core.render.DisplayAudience(f.plugin,new Location(f.world,0,80,0));
        var tray=new DiceTray(f.plugin,f.room,new Location(f.world,0,80,0),new NamespacedKey("3dgames","board-cell"),audience,2,2.775);
        audience.refresh();tray.tick();tray.roll(new int[]{2,6},91);
        for(int frame=0;frame<5;frame++)tray.tick();
        loadPack(f);audience.refresh();tray.tick();
        var packed=f.entities.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
        assertEquals(3,packed.size(),"One side table and two dice must share the packed layer");
        double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(int frame=6;frame<DiceMotion.FRAMES;frame++) {
            tray.tick();
            double zPosition=f.transforms.get(packed.get(1)).getTranslation().z;
            minZ=Math.min(minZ,zPosition);maxZ=Math.max(maxZ,zPosition);
            for(var die:packed.subList(1,3)) {
                var pose=f.transforms.get(die);
                for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                    var corner=new org.joml.Vector3f(x*.14f,y*.14f,z*.14f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
                    assertTrue(Math.abs(corner.x)<=.61+1e-6);
                    assertTrue(Math.abs(corner.z)<=.61+1e-6);
                    assertTrue(corner.y>=.015);
                }
            }
        }
        assertTrue(maxZ-minZ>.12,"Two dice need room to travel along the tray, not only spin in place");
        assertFalse(tray.rolling());
        assertEquals(1,f.transforms.get(packed.get(1)).getLeftRotation().transform(new org.joml.Vector3f(1,0,0)).y,1e-6);
        assertEquals(1,f.transforms.get(packed.get(2)).getLeftRotation().transform(new org.joml.Vector3f(0,-1,0)).y,1e-6);
        for(var die:packed)clearInvocations(die);
        for(int i=0;i<20;i++)tray.tick();
        for(var die:packed)verify(die,never()).setTransformation(any());
        tray.close();for(var die:packed)verify(die).remove();
    }

    @Test void changingPackChoiceDuringThrowKeepsMotionResultAndIdleTransformsQuiet() {
        var f=mixedFixture();var tray=tray(f,false);tray.roll(6,91);
        for(int i=0;i<5;i++)tray.tick();assertTrue(tray.rolling());
        loadPack(f);tray.tick();assertTrue(tray.rolling());
        List<ItemDisplay> packed=f.entities.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
        assertEquals(2,packed.size(),"Loading the pack during a throw must create its current frame");
        ItemDisplay die=packed.getLast();
        for(int i=6;i<DiceMotion.FRAMES;i++)tray.tick();assertFalse(tray.rolling());
        var transform=f.transforms.get(die);
        assertEquals(.161,transform.getTranslation().y,1e-6,"The .28 die must keep its .006 clearance above the .015 felt");
        var sixNormal=transform.getLeftRotation().transform(new org.joml.Vector3f(0,-1,0));
        assertEquals(1,sixNormal.y,1e-6,"Face six must remain up after switching render layers");
        clearInvocations(die);for(int i=0;i<30;i++)tray.tick();verify(die,never()).setTransformation(any());
        f.plugin.pack().toggle(f.player);tray.tick();for(ItemDisplay part:packed)verify(part).remove();
        assertFalse(tray.rolling());assertTrue(tray.hit(tray.center().add(0,2,0),new Vector(0,-1,0))>=0);
        tray.close();for(Entity part:f.entities)verify(part).remove();
    }

    @Test void ludoViewUsesTheSamePackChoiceForItsBoardAndSideTray() {
        var f=mixedFixture();loadPack(f);
        var room=new GameSession(java.util.UUID.randomUUID(),"ludo",2,0,0);
        room.join(f.player.getUniqueId(),"Owner");room.fillBots();
        SessionTestSupport.set(room, "board", GoldenEggOVO.TDGames.Core.game.GameFactory.create("ludo",2,0));SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        BoardMapStore maps=mock(BoardMapStore.class);
        when(maps.get(eq(f.world),any())).thenReturn(java.util.Collections.nCopies(4,new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP)));
        var view=new TableView(f.plugin,room,new Location(f.world,0,80,0),new NamespacedKey("3dgames","board-cell"),maps);
        long trayModels=f.entities.stream().filter(ItemDisplay.class::isInstance)
            .filter(entity->entity.getLocation().getX()>1.125).count();
        assertEquals(2,trayModels,"The Ludo view must layer the side tray together with its packed board");
        view.close();for(Entity part:f.entities)verify(part).remove();
    }

    private static TableViewTest.Fixture mixedFixture() {
        var f=new TableViewTest.Fixture("chess");f.view.close();f.entities.clear();
        f.plugin.getConfig().set("rendering.mode","mixed");
        f.plugin.getConfig().set("rendering.resource-pack.source","external");
        f.plugin.getConfig().set("rendering.resource-pack.url","https://example.org/tabletop.zip");
        var data=((org.mockbukkit.mockbukkit.ServerMock)org.bukkit.Bukkit.getServer()).addPlayer().getPersistentDataContainer();
        when(f.player.getPersistentDataContainer()).thenReturn(data);
        when(f.player.isOnline()).thenReturn(true);when(f.player.getLocation()).thenReturn(f.view.origin);
        when(f.world.getPlayers()).thenReturn(List.of(f.player));when(f.plugin.allowed(f.player)).thenReturn(true);
        SessionTestSupport.module(f.plugin, "pack", new ResourcePackService(f.plugin,()->true,id->new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER)));
        return f;
    }

    private static Player nativePlayer(TableViewTest.Fixture f) {
        Player player=mock(Player.class);when(player.isOnline()).thenReturn(true);when(player.getLocation()).thenReturn(f.view.origin);
        when(f.plugin.allowed(player)).thenReturn(true);when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        var data=((org.mockbukkit.mockbukkit.ServerMock)org.bukkit.Bukkit.getServer()).addPlayer().getPersistentDataContainer();
        when(player.getPersistentDataContainer()).thenReturn(data);
        return player;
    }

    private static void loadPack(TableViewTest.Fixture f) {
        f.plugin.pack().toggle(f.player);
        f.plugin.pack().status(f.player,f.plugin.pack().requestId(f.player),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    private record Parts(List<Entity> common,List<Entity> nativeOnly) {}
    private static Parts groupParts(TableViewTest.Fixture f) {
        List<Entity> common=new java.util.ArrayList<>(),nativeOnly=new java.util.ArrayList<>();
        for(Entity entity:f.entities)if(!(entity instanceof ItemDisplay)) {
            boolean shown=mockingDetails(f.player).getInvocations().stream().anyMatch(invocation->invocation.getMethod().getName().equals("showEntity")&&invocation.getArgument(1)==entity);
            (shown?common:nativeOnly).add(entity);
        }
        return new Parts(common,nativeOnly);
    }

    private static DiceTray tray(TableViewTest.Fixture f,boolean compact) {
        return new DiceTray(f.plugin,f.room,new Location(f.world,0,80,0),new NamespacedKey("3dgames","board-cell"),compact);
    }
}

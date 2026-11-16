package GoldenEggOVO.TDGames.Shared.render.dice;

import GoldenEggOVO.TDGames.Core.platform.PlatformMessages;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;
import GoldenEggOVO.TDGames.Core.render.model.ModelGeometry;
import GoldenEggOVO.TDGames.Core.render.model.ModelRepository;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.audio.TableSounds;
import GoldenEggOVO.TDGames.Core.render.DisplayAudience;
import GoldenEggOVO.TDGames.Shared.render.TableGeometry;
import GoldenEggOVO.TDGames.Shared.session.GameSession;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class DiceTray implements AutoCloseable {
    private static final double FELT=.015;
    private final TDGamesPlugin plugin;
    private final GameSession room;
    private final NamespacedKey tag;
    private final Location center;
    private final double width,size,innerHalf;
    private final DisplayAudience audience;
    private final boolean refreshAudience;
    private final List<Entity> entities=new ArrayList<>();
    private final List<Entity> nativeTable=new ArrayList<>();
    private ItemDisplay packedTable;
    private final List<YachtDie> nativeDice=new ArrayList<>(),packedDice=new ArrayList<>();
    private final TextDisplay label;
    private Component lastLabel=Component.empty();
    private final DiceMotion[] motions;
    private final DiceMotion.Pose[] poses;
    private int frame;
    private boolean closed;

    public DiceTray(TDGamesPlugin plugin,GameSession room,Location tableOrigin,NamespacedKey tag,boolean compact) {
        this(plugin,room,tableOrigin,tag,compact,new DisplayAudience(plugin,tableOrigin),true,1,compact?1.42:2);
    }

    public DiceTray(TDGamesPlugin plugin,GameSession room,Location tableOrigin,NamespacedKey tag,boolean compact,DisplayAudience audience) {
        this(plugin,room,tableOrigin,tag,compact,audience,false,1,compact?1.42:2);
    }

    public DiceTray(TDGamesPlugin plugin,GameSession room,Location tableOrigin,NamespacedKey tag,
            DisplayAudience audience,int diceCount,double offset) {
        this(plugin,room,tableOrigin,tag,false,audience,false,diceCount,offset);
    }

    private DiceTray(TDGamesPlugin plugin,GameSession room,Location tableOrigin,NamespacedKey tag,boolean compact,
            DisplayAudience audience,boolean refreshAudience,int diceCount,double offset) {
        this.plugin=plugin;this.room=room;this.tag=tag;
        this.audience=audience;this.refreshAudience=refreshAudience;
        center=tableOrigin.clone().add(offset,TableGeometry.SURFACE,0);
        motions=new DiceMotion[diceCount];poses=new DiceMotion.Pose[diceCount];
        width=compact?.5:1.4;size=compact?.17:.28;innerHalf=compact?.21:.61;
        for (var part : ModelRepository.require(
                "tabletop/ludo/tray_legs_" + (compact ? "compact" : "large")).blocks())
            entities.add(block(part));
        label=center.getWorld().spawn(center.clone().add(0,.125,width/2+.015),TextDisplay.class,d->{
            display(d);d.setRotation(0,-90);d.setBillboard(Display.Billboard.FIXED);PlatformMessages.text(d, lastLabel);
            d.setLineWidth(Integer.MAX_VALUE);d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);d.setBackgroundColor(Color.fromARGB(0,0,0,0));d.setShadowed(false);d.setSeeThrough(false);
            d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(compact?.13f:.22f),new Quaternionf()));
        });entities.add(label);
        Interaction interaction=center.getWorld().spawn(center.clone().add(0,-.03,0),Interaction.class,e->{
            tag(e);e.setInteractionWidth((float)width);e.setInteractionHeight(.14f);e.setResponsive(true);
        });entities.add(interaction);
        entities.forEach(audience::common);
        settle(1);
        layers();
    }

    public void settle(int... faces) {
        java.util.Arrays.fill(motions,null);frame=0;
        for(int i=0;i<poses.length;i++)poses[i]=DiceMotion.rest(size,faces[Math.min(i,faces.length-1)]);
        render();
    }
    public void roll(int face,long variationSeed){roll(new int[]{face},variationSeed);}
    public void roll(int[] faces,long variationSeed) {
        if(closed)return;
        for(int i=0;i<poses.length;i++)motions[i]=new DiceMotion(size,poses.length>1?.29:innerHalf,innerHalf,
                poses[i],faces[i],variationSeed+i*7919L);
        frame=0;
    }
    public void tick() {
        if(closed)return;
        if(refreshAudience)audience.refresh();
        layers();
        if(!rolling())return;
        frame++;
        for(int i=0;i<poses.length;i++)poses[i]=motions[i].pose(frame);
        render();
        if(motions[0].impact(frame)) {
            TableSounds.play(plugin,center.clone().add(poses[0].x(),FELT,poses[0].z()),
                TableSounds.diceImpact(frame));
        }
        if(frame>=DiceMotion.FRAMES) {
            for (var snapshot : plugin.playerViews().nearby(center, 24))
                if (room.phase() == GameSession.Phase.PLAYING && !room.restoring()
                        && snapshot.allowed())
                    TableSounds.detailFor(plugin, snapshot.player(), center, "dice.settle", .12f);
            java.util.Arrays.fill(motions,null);
        }
    }
    public boolean rolling(){return !closed&&motions[0]!=null;}
    public void renderNativeMidpoint() {
        if(closed||nativeDice.isEmpty()||!rolling())return;
        for(int i=0;i<motions.length;i++) {
            if(motions[i]==null)continue;
            var pose=motions[i].pose(frame+.5);
            nativeDice.get(i).pose(new DiceMotion.Pose(pose.x()+lane(i),FELT+pose.y(),pose.z(),pose.rotation()));
        }
    }
    public void label(Component text){if(!closed&&!text.equals(lastLabel)){lastLabel=text;PlatformMessages.text(label, text);}}
    Location center(){return center.clone();}

    public double hit(Location eye,Vector normalizedDirection) {
        if(closed||!center.getWorld().equals(eye.getWorld()))return -1;
        Vector from=eye.toVector();double nearest=-1;
        BoundingBox games=new BoundingBox(center.getX()-width/2,center.getY()-.03,center.getZ()-width/2,
            center.getX()+width/2,center.getY()+.11,center.getZ()+width/2);
        double radius=size*Math.sqrt(3)/2;
        List<BoundingBox> boxes=new ArrayList<>();boxes.add(games);
        for(int i=0;i<poses.length;i++) {
            var pose=poses[i];double x=pose.x()+lane(i);
            boxes.add(new BoundingBox(center.getX()+x-radius,center.getY()+FELT+pose.y()-radius,center.getZ()+pose.z()-radius,
                    center.getX()+x+radius,center.getY()+FELT+pose.y()+radius,center.getZ()+pose.z()+radius));
        }
        for(BoundingBox box:boxes) {
            var hit=box.rayTrace(from,normalizedDirection,TableGeometry.REACH);
            if(hit!=null){double distance=hit.getHitPosition().distance(from);if(nearest<0||distance<nearest)nearest=distance;}
        }
        return nearest;
    }

    private double lane(int index){return poses.length==1?0:(index-.5)*.62;}

    private void render() {
        if(closed)return;
        for(int i=0;i<poses.length;i++) {
            var pose=poses[i];
            DiceMotion.Pose at=new DiceMotion.Pose(pose.x()+lane(i),FELT+pose.y(),pose.z(),pose.rotation());
            if(!nativeDice.isEmpty())nativeDice.get(i).pose(at);
            if(!packedDice.isEmpty())packedDice.get(i).pose(at);
        }
    }

    private void layers() {
        if(audience.needed(false)&&nativeTable.isEmpty()) {
            for (var part : ModelRepository.require(
                    "tabletop/ludo/dice_tray_" + (width < 1 ? "compact" : "large")).blocks())
                nativeTable.add(block(part));
            nativeTable.forEach(entity->audience.add(entity,false));
            for(int i=0;i<poses.length;i++)nativeDice.add(new YachtDie(plugin,room,center,tag,audience,false,size,"@roll"));
            render();
        } else if(!audience.needed(false)&&!nativeTable.isEmpty()) {
            nativeTable.forEach(audience::remove);nativeTable.clear();nativeDice.forEach(YachtDie::close);nativeDice.clear();
        }
        if(audience.needed(true)&&packedTable==null) {
            packedTable=center.getWorld().spawn(center,ItemDisplay.class,d->{
                display(d);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setItemStack(plugin.pack().item("ludo_dice_tray"));
                d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f((float)width,1,(float)width),new Quaternionf()));
            });
            audience.add(packedTable,true);
            for(int i=0;i<poses.length;i++)packedDice.add(new YachtDie(plugin,room,center,tag,audience,true,size,"@roll"));
            render();
        } else if(!audience.needed(true)&&packedTable!=null) {
            audience.remove(packedTable);packedTable=null;packedDice.forEach(YachtDie::close);packedDice.clear();
        }
    }

    private void tag(Entity entity) {
        entity.setVisibleByDefault(!audience.managed());
        entity.setPersistent(false);entity.setGravity(false);entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|@roll");
    }
    private void display(Display display) {
        tag(display);display.setBrightness(new Display.Brightness(15,15));display.setViewRange(.35f);
        display.setTeleportDuration(2);display.setInterpolationDuration(2);
    }
    private BlockDisplay block(ModelDefinition.Block part) {
        return center.getWorld().spawn(center, BlockDisplay.class, d -> {
            display(d);
            d.setBlock(part.material().createBlockData());
            d.setTransformation(ModelGeometry.pose(part));
            if (part.brightness() != null) d.setBrightness(new Display.Brightness(part.brightness(), part.brightness()));
        });
    }
    @Override public void close(){
        if(closed)return;closed=true;java.util.Arrays.fill(motions,null);
        entities.forEach(audience::remove);entities.clear();nativeTable.forEach(audience::remove);nativeTable.clear();
        if(packedTable!=null){audience.remove(packedTable);packedTable=null;}
        nativeDice.forEach(YachtDie::close);nativeDice.clear();
        packedDice.forEach(YachtDie::close);packedDice.clear();
    }
}

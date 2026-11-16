package GoldenEggOVO.TDGames.Shared.render.mahjong;

import GoldenEggOVO.TDGames.Core.render.model.ModelDefinition;

import GoldenEggOVO.TDGames.Shared.render.cards.RoundCardTable;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.awt.geom.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MahjongTilePatternsTest {
    @Test void overlappingNativePartsNeverShareTheSameSurfacePlane(){
        List<List<ModelDefinition.Block>> meshes=new ArrayList<>();
        for(char suit:new char[]{'m','p','s'})for(int n=0;n<=9;n++)meshes.add(MahjongTilePatterns.of(""+suit+n));
        for(int n=1;n<=7;n++)meshes.add(MahjongTilePatterns.of("z"+n));
        for(int n=1;n<=8;n++)meshes.add(MahjongTilePatterns.of("f"+n));
        for(var parts:meshes)for(var part:parts){
            assertTrue(part.position().z() >= 0 && part.position().z() <= .0042 + 1e-9,"Native relief must remain bounded: "+part);

        }
        for(var parts:meshes)for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++){
            var a=parts.get(i);var b=parts.get(j);
            if(Math.abs(a.position().z()-b.position().z())>1e-9)continue;
            Area overlap=area(a);overlap.intersect(area(b));
            assertTrue(overlap.isEmpty()||overlap.getBounds2D().getWidth()<.001||overlap.getBounds2D().getHeight()<.001,
                    "Overlapping native surfaces: "+a+" / "+b);
        }
    }
    @Test void detailedPatternsUseFewerNativeEntitiesWithoutShrinkingFaces(){
        assertTrue(MahjongTilePatterns.of("p9").size()<=140);
        assertTrue(RoundCardTable.parts().size()<=94);
    }
    @Test void overlappingColorsHaveDifferentDepths(){
        for(String face:List.of("s1","p1","f1","f2","f3","f4","f5","f6","f7","f8")){
            var parts=MahjongTilePatterns.of(face);
            for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++){
                var a=parts.get(i);var b=parts.get(j);
                if(Math.abs(a.position().z()-b.position().z())>1e-9||a.material()==b.material())continue;
                Area overlap=area(a);overlap.intersect(area(b));
                assertTrue(overlap.isEmpty()||overlap.getBounds2D().getWidth()<.001||overlap.getBounds2D().getHeight()<.001,face+" coplanar colors: "+a+" / "+b);
            }
        }
    }
    private static Area area(ModelDefinition.Block part){
        AffineTransform transform=AffineTransform.getTranslateInstance((part.x()+.5)*32,(1-part.position().y())*48);transform.rotate((-Math.toRadians(part.rotation().z())));
        return new Area(transform.createTransformedShape(new Rectangle2D.Double(-(part.w()*32)/2,-(part.h()*48)/2,(part.w()*32),(part.h()*48))));
    }
    @Test void nativePatternsStayOnTheTileFaceWithBoundedEntityCounts(){
        List<String> faces=new ArrayList<>();
        for(char suit:new char[]{'m','p','s'})for(int n=0;n<=9;n++)faces.add(""+suit+n);
        for(int n=1;n<=7;n++)faces.add("z"+n);
        for(int n=1;n<=8;n++)faces.add("f"+n);
        for(String face:faces){
            var parts=MahjongTilePatterns.of(face);assertFalse(parts.isEmpty(),face);assertTrue(parts.size()<=225,face);
            for(var part:parts){
                double rx=Math.abs(Math.cos((-Math.toRadians(part.rotation().z()))))*(part.w()*32)/2+Math.abs(Math.sin((-Math.toRadians(part.rotation().z()))))*(part.h()*48)/2;
                double ry=Math.abs(Math.sin((-Math.toRadians(part.rotation().z()))))*(part.w()*32)/2+Math.abs(Math.cos((-Math.toRadians(part.rotation().z()))))*(part.h()*48)/2;
                double x=(part.x()+.5)*32,y=(1-part.position().y())*48;
                assertTrue(x-rx>=-1e-9&&x+rx<=32+1e-9&&y-ry>=-1e-9&&y+ry<=48+1e-9,face+" "+part);
            }
        }
    }
    @Test void redFivesRemainRecognizableAndHonorGlyphsKeepTheirInk(){
        for(String face:List.of("p0","s0")){
            assertTrue(MahjongTilePatterns.of(face).stream().anyMatch(p->p.material()==Material.RED_CONCRETE));
            assertTrue(MahjongTilePatterns.of(face).stream().noneMatch(p->p.material()==Material.BLUE_CONCRETE||p.material()==Material.GREEN_CONCRETE));
            assertNotEquals(MahjongTilePatterns.of(face),MahjongTilePatterns.of(face.charAt(0)+"5"));
        }
        for(String face:List.of("m1","m9","z1","z2","z3","z4","z6","z7")){
            var parts=MahjongTilePatterns.of(face);
            if(!face.equals("z7"))assertTrue(parts.stream().anyMatch(p->Math.abs(Math.sin((-Math.toRadians(p.rotation().z()))*2))>.1),face+" needs angled character strokes");
            assertTrue(parts.stream().anyMatch(p->p.material()==(face.equals("z6")?Material.GREEN_CONCRETE:face.equals("z7")?Material.RED_CONCRETE:Material.BLACK_CONCRETE)),face);
            if(face.charAt(0)=='m')assertTrue(parts.stream().anyMatch(p->p.material()==Material.RED_CONCRETE),"Wan glyph stays red");
        }
    }
}

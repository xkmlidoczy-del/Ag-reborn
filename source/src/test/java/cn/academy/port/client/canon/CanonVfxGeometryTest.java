package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign;
import java.util.HashSet;
import java.util.Set;

/** Pure shape contract, independent of a running client or game renderer. */
public final class CanonVfxGeometryTest {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var shapes=new HashSet<Object>();
        for(var motif:CanonVfxDesign.Motif.values())for(double length:new double[]{0,2,16,32,100}){
            var points=CanonVfxGeometry.sample(motif,4,length);
            require(!points.isEmpty()&&points.size()<=96,"bounded nonempty "+motif);
            for(var p:points)require(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z())
                    && p.x()*p.x()+p.y()*p.y()+p.z()*p.z()<40*40,"finite spatial bound "+motif);
            if(length==2)shapes.add(points);
        }
        require(shapes.size()==CanonVfxDesign.Motif.values().length,"all current motifs have distinct geometry");
        for(var motif:CanonVfxDesign.Motif.values()){
            var start=CanonVfxGeometry.stage(motif,0,1,4,20);
            var active=CanonVfxGeometry.stage(motif,1,1,4,20);
            var hit=CanonVfxGeometry.stage(motif,2,1,4,20);
            var end=CanonVfxGeometry.stage(motif,3,10,4,20);
            require(!start.equals(active)&&!active.equals(hit)&&!active.equals(end),"phase-specific geometry "+motif);
            require(hit.size()<=96,"impact remains bounded "+motif);
        }
        require(CanonVfxGeometry.sample(null,0,2).isEmpty(),"null motif refused");
        require(CanonVfxGeometry.sample(CanonVfxDesign.Motif.REMOTE,Double.NaN,2).isEmpty(),"non-finite time refused");
        require(CanonVfxGeometry.sample(CanonVfxDesign.Motif.REMOTE,0,Double.POSITIVE_INFINITY).isEmpty(),"non-finite range refused");
        Set<String> categories=new HashSet<>();
        for(var entry:CanonVfxDesign.all().entrySet()){
            require(!entry.getKey().isBlank()&&entry.getValue().motif()!=null,"mapped skill has shape");categories.add(entry.getValue().category());
        }
        require(categories.equals(Set.of("mental_out","dark_matter","nitrogen_armor","aero_hand","hydro_hand","float_dial","liquid_shadow","paper_psychokinesis")),"all eight categories covered");
        require(CanonVfxDesign.find("unknown_skill")==null,"unknown skill is not rendered");
        require(CanonVfxDesign.all().size()>=192,"design matrix covers at least 24 IDs per category");
        for(String c:categories)require(CanonVfxDesign.all().values().stream().filter(d->d.category().equals(c)).count()>=24,"at least 24 designs: "+c);
        require(CanonVfxDesign.find("float_item_push")!=null,"actual added object push has a visual design");
        require(!CanonVfxGeometry.skillStage("float_item_lift",CanonVfxDesign.Motif.FLOAT_LIFT,1,4,3,20)
                .equals(CanonVfxGeometry.skillStage("float_item_lower",CanonVfxDesign.Motif.FLOAT_LIFT,1,4,3,20)),"lift and descent arrows differ");
        System.out.println("PASS canon VFX geometry: "+checks+" checks; "+shapes.size()+" distinct motifs; "+CanonVfxDesign.all().size()+" mapped IDs");
    }
}

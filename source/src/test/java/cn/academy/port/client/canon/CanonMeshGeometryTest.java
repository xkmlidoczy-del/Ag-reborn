package cn.academy.port.client.canon;
import cn.academy.port.canon.CanonVfxDesign.Motif;
import java.util.List;
public final class CanonMeshGeometryTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(var motif:List.of(Motif.DARK_WINGS,Motif.NITROGEN_SHELL,Motif.NITROGEN_PUNCH))for(int phase=0;phase<4;phase++){
            var quads=CanonMeshGeometry.shape(motif,phase,3,20,4);
            ok(!quads.isEmpty()&&quads.size()<=40,"bounded mesh "+motif);
            for(var q:quads){ok(q.alpha()>0&&q.alpha()<256,"translucent alpha");for(var p:List.of(q.a(),q.b(),q.c(),q.d()))
                ok(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z())&&Math.abs(p.x())<5&&Math.abs(p.y())<5&&Math.abs(p.z())<5,"finite local point");}
        }
        ok(CanonMeshGeometry.shape(Motif.DARK_WINGS,1,3,20,2).size()==18,"six wings with two facets and a rib each");
        ok(CanonMeshGeometry.shape(Motif.NITROGEN_SHELL,1,3,20,2).size()==32,"eight-sided shell with plate accents");
        ok(CanonMeshGeometry.shape(Motif.NITROGEN_PUNCH,1,3,20,2).size()==6,"bounded six-sided pressure fist");
        ok(CanonMeshGeometry.shape(Motif.DARK_WINGS,1,Double.NaN,20,2).isEmpty(),"invalid time refused");
        for(var motif:List.of(Motif.REMOTE,Motif.AERO_JET,Motif.WATER_TRANSFER,Motif.FLOAT_LIFT,Motif.LIQUID_SCOUT,Motif.PAPER_CONSTRUCT)){
            var quads=CanonMeshGeometry.shape(motif,1,2,20,2);ok(!quads.isEmpty()&&quads.size()<=40,"dedicated category mesh "+motif);
            for(var quad:quads)for(var point:List.of(quad.a(),quad.b(),quad.c(),quad.d()))
                ok(Double.isFinite(point.x())&&Double.isFinite(point.y())&&Double.isFinite(point.z()),"finite mesh point "+motif);
        }
        ok(CanonMeshGeometry.shape(Motif.DARK_CONSTRUCT,1,2,20,2).isEmpty(),"unimplemented optional construct mesh is not fabricated");
        ok(!CanonMeshGeometry.shape(Motif.DARK_WINGS,0,0,20,2).equals(CanonMeshGeometry.shape(Motif.DARK_WINGS,1,5,20,2)),"emergence differs from sustain");
        var paperShapes=new java.util.HashSet<List<CanonMeshGeometry.Quad>>();
        for(String skill:List.of("paper_ramp","paper_lift_bridge","paper_cover","paper_platform","paper_catwalk","paper_emergency_stairs"))
            paperShapes.add(CanonMeshGeometry.shapeFor(skill,Motif.PAPER_CONSTRUCT,1,4,20,3));
        ok(paperShapes.size()==6,"six physical paper structures have distinct mesh plans");
        var contacts=new java.util.HashSet<List<CanonMeshGeometry.Quad>>();
        for(String skill:List.of("liquid_grapple","liquid_contact_cut","liquid_contact_sweep","liquid_contact_crush"))
            contacts.add(CanonMeshGeometry.shapeFor(skill,Motif.LIQUID_STRIKE,1,4,20,2));
        ok(contacts.size()==4,"four physical close-contact liquid actions differ");
        System.out.println("PASS canon mesh geometry: "+checks+" checks; 6 wings, 8-sided shell, 6-sided pressure fist");
    }
}

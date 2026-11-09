package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry.Plate;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry.Snapshot;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** NEW252 six authentic finite slabs, original mdshield artwork and the existing scoped NEW221 material. */
public final class ZeroGapArmorMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 768, SPAWNED_PARTICLES = 0;
    public static final int EDGE_PARTS = 8, GLINT_PARTS = 12;
    public static final double CULL_DISTANCE = 64, CONTACT_PLANE_DISTANCE = .08, MAX_GLINT_RADIUS = .09;
    private static final double EPS = 1e-7, RIM_WIDTH = .012;
    private static final int DORMANT_COLOR = 0x537765, GAP_COLOR = 0xCEE1B2;
    private ZeroGapArmorMesh() {}
    public static boolean validPoint(Vector p) {
        return p != null && p.finite() && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 4096;
    }
    public static Snapshot snapshot(ZeroGapArmorState.Frame f) {
        return ZeroGapArmorState.valid(f) ? ZeroGapArmorGeometry.capture(f.feet(),f.yaw(),f.phaseTick()) : null;
    }
    private static Vector point(Plate plate, double x, double y, double depth) {
        var basis = ZeroGapArmorGeometry.frame(plate);
        return plate.center().add(basis.right().scale(x)).add(basis.up().scale(y)).add(basis.normal().scale(depth));
    }
    /** Render geometry, including inward thickness, never grows beyond its own real rectangle. */
    public static boolean contains(Plate plate, Vector p) {
        if (plate == null || !validPoint(p)) return false;
        var basis = ZeroGapArmorGeometry.frame(plate); Vector delta = p.subtract(plate.center());
        double depth = delta.dot(basis.normal());
        return depth <= EPS && depth >= -ZeroGapArmorGeometry.THICKNESS-EPS
                && Math.abs(delta.dot(basis.right())) <= ZeroGapArmorGeometry.HALF_WIDTH+EPS
                && Math.abs(delta.dot(basis.up())) <= ZeroGapArmorGeometry.HALF_HEIGHT+EPS;
    }
    private static Face face(Plate plate,double ax,double ay,double bx,double by,double cx,double cy,double dx,double dy,
                             double depth,int color,int alpha,int kind,int texture,Material material) {
        return new Face(point(plate,ax,ay,depth),point(plate,bx,by,depth),point(plate,cx,cy,depth),point(plate,dx,dy,depth),
                color,alpha,kind,texture,new Uv(0,1),new Uv(1,1),new Uv(1,0),new Uv(0,0),material);
    }
    public static List<Face> plate(ZeroGapArmorState.Frame current,int index,int fadeAge,float materialPhase) {
        return plate(current,index,fadeAge,materialPhase,null,ZeroGapArmorState.HIT_TICKS);
    }
    /** Selected live panes alone carry the textured body and four inward slab sides. */
    public static List<Face> plate(ZeroGapArmorState.Frame current,int index,int fadeAge,float materialPhase,
                                    ZeroGapArmorState.Frame contact,float contactAge) {
        Snapshot snapshot = snapshot(current);
        if (snapshot == null || index < 0 || index >= ZeroGapArmorGeometry.PLATE_COUNT || !Float.isFinite(materialPhase)
                || fadeAge < -1 || fadeAge >= ZeroGapArmorState.FADE_TICKS) return List.of();
        Plate plate = snapshot.plates().get(index);
        boolean fading = fadeAge >= 0 || current.phase() == ZeroGapArmorState.ENDED;
        boolean selected = (current.selectedMask() & 1 << index) != 0;
        boolean startup = current.phase() == ZeroGapArmorState.STARTUP;
        boolean gap = current.breakTicks() > 0;
        boolean live = !fading && !startup && !gap && (current.liveMask() & 1 << index) != 0;
        double fade = fading ? (ZeroGapArmorState.FADE_TICKS-Math.max(0,fadeAge))/(double)ZeroGapArmorState.FADE_TICKS : 1;
        double w = ZeroGapArmorGeometry.HALF_WIDTH, h = ZeroGapArmorGeometry.HALF_HEIGHT;
        Material material = Material.phase(materialPhase);
        Vector glint = contact != null && contact.plate() == index ? currentContact(current,contact,contactAge) : null;
        if (live && glint != null) {
            Vector local = glint.subtract(plate.center()); var basis = ZeroGapArmorGeometry.frame(plate);
            material = new Material(materialPhase,(float)(.5+local.dot(basis.right())/ZeroGapArmorGeometry.WIDTH),
                    (float)(.5-local.dot(basis.up())/ZeroGapArmorGeometry.HEIGHT),contactPower(contactAge));
        }
        List<Face> out = new ArrayList<>(37);
        if (live) {
            // Match NEW242's original artwork/alpha and inward .04m volume. No texture copies or generated assets.
            out.add(face(plate,-w,-h,w,-h,w,h,-w,h,-.001,0xFFFFFF,210,FILL,SHIELD_TEXTURE,material));
            var vertices = ZeroGapArmorGeometry.vertices(plate);
            Vector back = plate.normal().scale(-ZeroGapArmorGeometry.THICKNESS);
            for (int edge=0;edge<4;edge++) {
                Vector a=vertices.get(edge),b=vertices.get((edge+1)%4);
                out.add(new Face(a,b,b.add(back),a.add(back),EDGE,45,SIDE,WHITE_TEXTURE).withMaterial(material));
            }
        }
        double[] xs={-w,w,w,-w},ys={-h,-h,h,h};
        for (int edge=0;edge<4;edge++) {
            int next=(edge+1)%4;
            double width=selected?RIM_WIDTH:.007;
            double ix=edge==1?-width:edge==3?width:0;
            double iy=edge==0?width:edge==2?-width:0;
            int parts=selected?EDGE_PARTS:1;
            for(int part=0;part<parts;part++) {
                if (fading && part%2!=0) continue;
                if (selected && !live && !fading) {
                    // Startup has regular dashes; a break leaves open corner brackets; spent shares are dim dashes.
                    if (gap ? part>1 && part<EDGE_PARTS-2 : part%2!=0) continue;
                }
                double a=part/(double)parts,b=(part+1)/(double)parts;
                double ax=xs[edge]+(xs[next]-xs[edge])*a,ay=ys[edge]+(ys[next]-ys[edge])*a;
                double bx=xs[edge]+(xs[next]-xs[edge])*b,by=ys[edge]+(ys[next]-ys[edge])*b;
                int color=fading?PREVIEW:!selected?DORMANT_COLOR:gap?GAP_COLOR:startup?GREEN:live?CORE:PREVIEW;
                int alpha=(int)((fading?32:!selected?35:gap?116:startup?105:live?88:52)*fade);
                out.add(face(plate,ax,ay,bx,by,bx+ix,by+iy,ax+ix,ay+iy,0,color,alpha,live?RIM:GHOST,WHITE_TEXTURE,material));
            }
        }
        return List.copyOf(out);
    }
    /** Never move a historical hit to a remote new pose. A tiny normal projection is allowed only inside the current numbered pane. */
    public static Vector currentContact(ZeroGapArmorState.Frame current,ZeroGapArmorState.Frame contact,float age) {
        if (!ZeroGapArmorState.valid(current) || !ZeroGapArmorState.valid(contact)
                || current.phase()!=ZeroGapArmorState.ACTIVE && current.phase()!=ZeroGapArmorState.HIT
                || contact.phase()!=ZeroGapArmorState.HIT || !current.id().equals(contact.id())
                || current.sequence()<contact.sequence() || !Float.isFinite(age) || age<0 || age>=ZeroGapArmorState.HIT_TICKS
                || (current.selectedMask() & 1 << contact.plate()) == 0) return null;
        Plate plate=ZeroGapArmorGeometry.capture(current.feet(),current.yaw(),current.phaseTick()).plates().get(contact.plate());
        var basis=ZeroGapArmorGeometry.frame(plate); Vector delta=contact.point().subtract(plate.center());
        double depth=delta.dot(basis.normal()),x=delta.dot(basis.right()),y=delta.dot(basis.up());
        if (Math.abs(depth)>CONTACT_PLANE_DISTANCE || Math.abs(x)>ZeroGapArmorGeometry.HALF_WIDTH+EPS
                || Math.abs(y)>ZeroGapArmorGeometry.HALF_HEIGHT+EPS) return null;
        return point(plate,Math.clamp(x,-ZeroGapArmorGeometry.HALF_WIDTH,ZeroGapArmorGeometry.HALF_WIDTH),
                Math.clamp(y,-ZeroGapArmorGeometry.HALF_HEIGHT,ZeroGapArmorGeometry.HALF_HEIGHT),0);
    }
    private static float contactPower(float age) { return Math.max(0,1-age/ZeroGapArmorState.HIT_TICKS); }
    /** Short broken caustic at a confirmed local crossing; never a beam, remote spark, full-pane flash or expanding ring. */
    public static List<Face> glint(ZeroGapArmorState.Frame current,ZeroGapArmorState.Frame contact,float age,float materialPhase) {
        Vector center=currentContact(current,contact,age);
        if (center==null || !Float.isFinite(materialPhase)) return List.of();
        Plate plate=ZeroGapArmorGeometry.capture(current.feet(),current.yaw(),current.phaseTick()).plates().get(contact.plate());
        var basis=ZeroGapArmorGeometry.frame(plate); Vector local=center.subtract(plate.center());
        double x=local.dot(basis.right()),y=local.dot(basis.up());
        double radius=.037+.016*(1-contactPower(age));
        double halfWidth=.005*contactPower(age);
        int alpha=(int)(175*contactPower(age));
        List<Face> out=new ArrayList<>(GLINT_PARTS*2);
        for(int i=0;i<GLINT_PARTS;i++) {
            if(i%4>=2)continue;
            double a=i*Math.PI*2/GLINT_PARTS,b=(i+1)*Math.PI*2/GLINT_PARTS;
            List<Local> polygon=List.of(new Local(x+Math.cos(a)*(radius-halfWidth),y+Math.sin(a)*(radius-halfWidth)*.7),
                    new Local(x+Math.cos(b)*(radius-halfWidth),y+Math.sin(b)*(radius-halfWidth)*.7),
                    new Local(x+Math.cos(b)*(radius+halfWidth),y+Math.sin(b)*(radius+halfWidth)*.7),
                    new Local(x+Math.cos(a)*(radius+halfWidth),y+Math.sin(a)*(radius+halfWidth)*.7));
            polygon=clip(polygon,0,-ZeroGapArmorGeometry.HALF_WIDTH,true);polygon=clip(polygon,0,ZeroGapArmorGeometry.HALF_WIDTH,false);
            polygon=clip(polygon,1,-ZeroGapArmorGeometry.HALF_HEIGHT,true);polygon=clip(polygon,1,ZeroGapArmorGeometry.HALF_HEIGHT,false);
            if(polygon.size()<3)continue;
            Local first=polygon.getFirst(); Material material=Material.phase(materialPhase);
            if(polygon.size()==4) {
                Local s=polygon.get(1),t=polygon.get(2),u=polygon.get(3);
                out.add(face(plate,first.x,first.y,s.x,s.y,t.x,t.y,u.x,u.y,0,CORE,alpha,REFRACTION,WHITE_TEXTURE,material));
            } else for(int j=1;j<polygon.size()-1;j++) {
                Local s=polygon.get(j),t=polygon.get(j+1);
                out.add(face(plate,first.x,first.y,s.x,s.y,t.x,t.y,first.x,first.y,0,CORE,alpha,REFRACTION,WHITE_TEXTURE,material));
            }
        }
        return List.copyOf(out);
    }
    private record Local(double x,double y) { double axis(int axis) { return axis==0?x:y; } }
    private static List<Local> clip(List<Local> input,int axis,double bound,boolean minimum) {
        if(input.isEmpty())return input; List<Local> result=new ArrayList<>(6); Local prior=input.getLast();
        boolean priorInside=minimum?prior.axis(axis)>=bound:prior.axis(axis)<=bound;
        for(Local current:input) {
            boolean inside=minimum?current.axis(axis)>=bound:current.axis(axis)<=bound;
            if(inside!=priorInside) {
                double t=(bound-prior.axis(axis))/(current.axis(axis)-prior.axis(axis));
                result.add(new Local(prior.x+(current.x-prior.x)*t,prior.y+(current.y-prior.y)*t));
            }
            if(inside)result.add(current);prior=current;priorInside=inside;
        }
        return result;
    }
    /** Global post-near-clipping cap across all owners, slabs and contact accents. */
    public static List<Face> ordered(List<Face> input,Vector eye,Vector look,Budget budget) {
        List<Face> result=new ArrayList<>();
        outer: for(Face face:input) if(face.alpha()>0) for(Face part:FoldedMirrorMesh.nearClip(face,eye,look)) {
            if(!budget.take())break outer;result.add(part);
        }
        result.sort(Comparator.comparingInt(FoldedMirrorMesh::layer).thenComparing(Comparator.comparingDouble(
                (Face f)->f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(eye).length()).reversed()));
        return List.copyOf(result);
    }
    public static final class Budget {
        private int used;
        public boolean take() { if(used>=MAX_MESH_QUADS)return false;used++;return true; }
        public int used() { return used; }
    }
}

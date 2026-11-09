package cn.academy.port.client;

import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Pose;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** NEW242 exact three finite rectangular facets. Real four-degree gaps contain no visual geometry. */
public final class DissipationCurtainMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 768, SPAWNED_PARTICLES = 0;
    public static final int EDGE_PARTS = 8, RIPPLE_PARTS = 48;
    public static final double CULL_DISTANCE = 64;
    private static final double EPS = 1e-7, RIM_WIDTH = .012;
    private DissipationCurtainMesh() {}
    public static boolean validPoint(Vector p) {
        return p != null && p.finite() && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 4096;
    }
    public static boolean validDirection(Vector p) { return p != null && p.finite() && Math.abs(p.length() - 1) <= 1e-7; }
    public static cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector materialPoint(Vector p) {
        return new cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector(p.x(), p.y(), p.z());
    }
    private static Vector point(Pose pose, int facet, double x, double y, double depth) {
        return DissipationCurtainGeometry.facetCenter(pose, facet)
                .add(DissipationCurtainGeometry.facetRight(pose, facet).scale(x)).add(pose.up().scale(y))
                .add(DissipationCurtainGeometry.facetNormal(pose, facet).scale(depth));
    }
    /** Only real front-face contact can seed the receipt ripple; gaps and rear contacts cannot. */
    public static boolean onFacet(Pose pose, int facet, Vector contact) {
        if (pose == null || facet < 0 || facet >= 3 || !validPoint(contact)) return false;
        Vector delta = contact.subtract(DissipationCurtainGeometry.facetCenter(pose, facet));
        return Math.abs(delta.dot(DissipationCurtainGeometry.facetNormal(pose, facet))) <= EPS
                && Math.abs(delta.dot(DissipationCurtainGeometry.facetRight(pose, facet))) <= DissipationCurtainGeometry.HALF_WIDTH + EPS
                && Math.abs(delta.dot(pose.up())) <= DissipationCurtainGeometry.HEIGHT / 2 + EPS;
    }
    private static Face face(Pose pose, int facet, double ax, double ay, double bx, double by,
                             double cx, double cy, double dx, double dy, double depth, int color, int alpha, int kind, int texture, Material material) {
        return new Face(materialPoint(point(pose,facet,ax,ay,depth)), materialPoint(point(pose,facet,bx,by,depth)),
                materialPoint(point(pose,facet,cx,cy,depth)), materialPoint(point(pose,facet,dx,dy,depth)), color, alpha, kind, texture,
                new Uv(0,1),new Uv(1,1),new Uv(1,0),new Uv(0,0),material);
    }
    /** Startup is a dashed warning. Ending retains only faint broken edges, never a live-filled wall. */
    public static List<Face> facet(Pose pose, int facet, boolean warning, int fadeAge, float phase) {
        if (pose == null || facet < 0 || facet >= 3 || !Float.isFinite(phase) || fadeAge >= DissipationCurtainState.FADE_TICKS) return List.of();
        boolean fading = fadeAge >= 0;
        double fade = fading ? (DissipationCurtainState.FADE_TICKS - fadeAge) / (double)DissipationCurtainState.FADE_TICKS : 1;
        double w = DissipationCurtainGeometry.HALF_WIDTH, h = DissipationCurtainGeometry.HEIGHT / 2;
        Material material = Material.phase(phase);
        List<Face> out = new ArrayList<>(37);
        if (!warning && !fading) {
            out.add(face(pose,facet,-w,-h,w,-h,w,h,-w,h,-.001,0xFFFFFF,210,FILL,SHIELD_TEXTURE,material));
            List<Vector> v = DissipationCurtainGeometry.vertices(pose,facet);
            Vector back = DissipationCurtainGeometry.facetNormal(pose,facet).scale(-DissipationCurtainGeometry.THICKNESS);
            for (int edge = 0; edge < 4; edge++) {
                Vector a = v.get(edge), b = v.get((edge+1)%4);
                out.add(new Face(materialPoint(a),materialPoint(b),materialPoint(b.add(back)),materialPoint(a.add(back)),EDGE,45,SIDE,WHITE_TEXTURE).withMaterial(material));
            }
        }
        double[] xs = {-w,w,w,-w}, ys = {-h,-h,h,h};
        for (int edge = 0; edge < 4; edge++) {
            int next=(edge+1)%4;
            double ix = edge==1 ? -RIM_WIDTH : edge==3 ? RIM_WIDTH : 0;
            double iy = edge==0 ? RIM_WIDTH : edge==2 ? -RIM_WIDTH : 0;
            for (int part=0; part<EDGE_PARTS; part++) {
                if ((warning || fading) && part%2!=0) continue;
                double a=part/(double)EDGE_PARTS, b=(part+1)/(double)EDGE_PARTS;
                double ax=xs[edge]+(xs[next]-xs[edge])*a, ay=ys[edge]+(ys[next]-ys[edge])*a;
                double bx=xs[edge]+(xs[next]-xs[edge])*b, by=ys[edge]+(ys[next]-ys[edge])*b;
                int alpha=(int)((fading?64:warning?105:88)*fade);
                out.add(face(pose,facet,ax,ay,bx,by,bx+ix,by+iy,ax+ix,ay+iy,0,
                        fading?PREVIEW:warning?GREEN:CORE,alpha,warning||fading?GHOST:RIM,WHITE_TEXTURE,material));
            }
        }
        return List.copyOf(out);
    }
    /** A committed point expands to this facet's edges. Rectangle clipping never spills into neighboring gaps. */
    public static List<Face> ripple(DissipationCurtainState.Frame receipt, float age, float phase) {
        if (!DissipationCurtainState.valid(receipt) || receipt.phase()!=DissipationCurtainState.HIT
                || !Float.isFinite(age) || age<0 || age>=DissipationCurtainState.HIT_TICKS || !Float.isFinite(phase)) return List.of();
        Pose pose=DissipationCurtainGeometry.pose(receipt.center(),receipt.axis()); int facet=receipt.plate();
        Vector delta=receipt.point().subtract(DissipationCurtainGeometry.facetCenter(pose,facet));
        double x=delta.dot(DissipationCurtainGeometry.facetRight(pose,facet)), y=delta.dot(pose.up());
        double w=DissipationCurtainGeometry.HALF_WIDTH, h=DissipationCurtainGeometry.HEIGHT/2;
        double progress=age/(DissipationCurtainState.HIT_TICKS-1.0);
        double radius=.045+Math.hypot(w+Math.abs(x),h+Math.abs(y))*progress;
        double halfWidth=.012*(1-progress*.5);
        int alpha=(int)(190*(1-progress*.85));
        List<Face> result=new ArrayList<>(RIPPLE_PARTS*2);
        for(int i=0;i<RIPPLE_PARTS;i++) {
            double a=i*Math.PI*2/RIPPLE_PARTS, b=(i+1)*Math.PI*2/RIPPLE_PARTS;
            List<Local> polygon=List.of(new Local(x+Math.cos(a)*(radius-halfWidth),y+Math.sin(a)*(radius-halfWidth)),
                    new Local(x+Math.cos(b)*(radius-halfWidth),y+Math.sin(b)*(radius-halfWidth)),
                    new Local(x+Math.cos(b)*(radius+halfWidth),y+Math.sin(b)*(radius+halfWidth)),
                    new Local(x+Math.cos(a)*(radius+halfWidth),y+Math.sin(a)*(radius+halfWidth)));
            polygon=clip(polygon,0,-w,true); polygon=clip(polygon,0,w,false);
            polygon=clip(polygon,1,-h,true); polygon=clip(polygon,1,h,false);
            if(polygon.size()<3)continue;
            Local first=polygon.getFirst();
            if(polygon.size()==4) {
                Local s=polygon.get(1),t=polygon.get(2),u=polygon.get(3);
                result.add(face(pose,facet,first.x,first.y,s.x,s.y,t.x,t.y,u.x,u.y,0,CORE,alpha,REFRACTION,WHITE_TEXTURE,Material.phase(phase)));
            } else for(int j=1;j<polygon.size()-1;j++) {
                Local s=polygon.get(j),t=polygon.get(j+1);
                result.add(face(pose,facet,first.x,first.y,s.x,s.y,t.x,t.y,first.x,first.y,0,CORE,alpha,REFRACTION,WHITE_TEXTURE,Material.phase(phase)));
            }
        }
        return List.copyOf(result);
    }
    /** A historical absorption may decorate only the presently visible facet containing its real world point. */
    public static List<Face> ripple(DissipationCurtainState.Frame current, DissipationCurtainState.Frame contact, float age, float phase) {
        if (!DissipationCurtainState.valid(current) || !DissipationCurtainState.valid(contact)
                || current.phase() != DissipationCurtainState.ACTIVE && current.phase() != DissipationCurtainState.HIT
                || contact.phase() != DissipationCurtainState.HIT || !current.id().equals(contact.id())) return List.of();
        Pose now = DissipationCurtainGeometry.pose(current.center(),current.axis());
        Pose then = DissipationCurtainGeometry.pose(contact.center(),contact.axis());
        if (!onFacet(now,contact.plate(),contact.point()) || DissipationCurtainGeometry.facetNormal(now,contact.plate())
                .dot(DissipationCurtainGeometry.facetNormal(then,contact.plate())) < 1 - EPS) return List.of();
        return ripple(new DissipationCurtainState.Frame(contact.id(),contact.sequence(),DissipationCurtainState.HIT,
                current.center(),current.axis(),7,contact.plate(),contact.point()),age,phase);
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
            if(inside)result.add(current); prior=current; priorInside=inside;
        }
        return result;
    }
    /** One post-near-clipping cap shared across owners, panes, contacts and material batches. */
    public static List<Face> ordered(List<Face> input, cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector eye,
                                     cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector look, Budget budget) {
        List<Face> result=new ArrayList<>();
        for(Face face:input) if(face.alpha()>0) for(Face part:FoldedMirrorMesh.nearClip(face,eye,look)) {
            if(!budget.take())break; result.add(part);
        }
        result.sort(Comparator.comparingInt(FoldedMirrorMesh::layer).thenComparing(Comparator.comparingDouble(
                (Face f)->f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(eye).length()).reversed()));
        return List.copyOf(result);
    }
    public static final class Budget {
        private int used;
        public boolean take() { if(used>=MAX_MESH_QUADS)return false; used++; return true; }
        public int used() { return used; }
    }
}

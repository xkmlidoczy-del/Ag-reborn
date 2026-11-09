package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.List;
import java.util.Optional;

/** NEW241: two immutable thin rectangles and three real crossed rays, never a filled corridor. */
public final class ParticleLaminateGeometry {
    public static final double HALF_WIDTH=.6, HALF_HEIGHT=1, THICKNESS=.04;
    public static final double RADIUS=.035, RANGE=64, MIN_SEPARATION=2;
    public record Panel(int index,Vector center,Vector normal) {
        public Panel {
            if(index<0||index>1||!finite(center)||!unit(normal))throw new IllegalArgumentException("Invalid NEW241 panel");
            Vector extent=extent(frameFor(normal));
            if(!center.subtract(extent).finite()||!center.add(extent).finite())throw new IllegalArgumentException("NEW241 panel outside world");
        }
    }
    public record Frame(Vector normal,Vector right,Vector up) {}
    public record Segment(Vector start,Vector end) {
        public Segment {
            if(!finite(start)||!finite(end)||start.subtract(end).length()>RANGE)throw new IllegalArgumentException("Invalid closed NEW241 segment");
        }
    }
    private ParticleLaminateGeometry() {}
    public static Frame frame(Panel panel){return frameFor(panel.normal);}
    public static Vector point(Panel panel,double right,double up){
        if(!Double.isFinite(right)||!Double.isFinite(up)||Math.abs(right)>HALF_WIDTH||Math.abs(up)>HALF_HEIGHT)throw new IllegalArgumentException("NEW241 panel point");
        Frame frame=frame(panel);return panel.center.add(frame.right.scale(right)).add(frame.up.scale(up));
    }
    public static Bounds volumeBounds(Panel panel){Vector e=extent(frame(panel));return new Bounds(panel.center.subtract(e),panel.center.add(e));}
    /** Conservatively refuses configurations whose actual outer crossed ray would exceed 64m. */
    public static Optional<List<Segment>> segments(Panel first,Panel second){
        if(first==null||second==null||first.index!=0||second.index!=1)return Optional.empty();
        double separation=first.center.subtract(second.center).length();
        if(separation<MIN_SEPARATION||separation>RANGE)return Optional.empty();
        try{return Optional.of(List.of(new Segment(point(first,-.48,.8),point(second,.48,-.8)),
                new Segment(first.center,second.center),new Segment(point(first,.48,-.8),point(second,-.48,.8))));}
        catch(IllegalArgumentException outside){return Optional.empty();}
    }
    /** Closed slab clip: includes initial containment, tangencies and exact terminal contacts. */
    public static Optional<Vector> clip(Bounds bounds,Vector start,Vector end,double radius){
        if(bounds==null||!finite(bounds.min())||!finite(bounds.max())||!finite(start)||!finite(end)||!Double.isFinite(radius)||radius<0||radius>RADIUS)return Optional.empty();
        double near=0,far=1;double[] origin={start.x(),start.y(),start.z()},delta={end.x()-start.x(),end.y()-start.y(),end.z()-start.z()};
        double[] min={bounds.min().x()-radius,bounds.min().y()-radius,bounds.min().z()-radius},max={bounds.max().x()+radius,bounds.max().y()+radius,bounds.max().z()+radius};
        for(int i=0;i<3;i++){
            if(min[i]>max[i])return Optional.empty();
            if(delta[i]==0){if(origin[i]<min[i]||origin[i]>max[i])return Optional.empty();continue;}
            double a=(min[i]-origin[i])/delta[i],b=(max[i]-origin[i])/delta[i];
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return Optional.empty();
        }
        return near>=0&&near<=1?Optional.of(start.add(end.subtract(start).scale(near))):Optional.empty();
    }
    private static boolean finite(Vector v){return v!=null&&v.finite();}
    private static boolean unit(Vector v){return finite(v)&&Math.abs(v.length()-1)<=1e-10;}
    private static Frame frameFor(Vector normal){
        double horizontal=Math.hypot(normal.x(),normal.z());
        Vector right=horizontal>0?new Vector(normal.z()/horizontal,0,-normal.x()/horizontal):new Vector(1,0,0);
        return new Frame(normal,right,normal.cross(right));
    }
    private static Vector extent(Frame f){return new Vector(axis(f.right.x(),f.up.x(),f.normal.x()),axis(f.right.y(),f.up.y(),f.normal.y()),axis(f.right.z(),f.up.z(),f.normal.z()));}
    private static double axis(double right,double up,double normal){return HALF_WIDTH*Math.abs(right)+HALF_HEIGHT*Math.abs(up)+THICKNESS/2*Math.abs(normal);}
}

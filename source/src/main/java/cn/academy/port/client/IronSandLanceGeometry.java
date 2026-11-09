package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Engine-free bounded solid geometry. There is deliberately no predicted flight path or target query. */
public final class IronSandLanceGeometry {
    public static final int LANCES=3,CHARGE_GRAINS=48,HIT_GRAINS_PER_LANCE=4,MAX_DECORATIVE_GRAINS=60;
    public static final double RADIUS=.085;
    /** Cosmetic opaque-body safety distance; server muzzle/flight and the existing thin axis are untouched. */
    public static final double SOLID_NEAR_DEPTH=.6;
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public Point plus(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point minus(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public Point scale(double s){return new Point(x*s,y*s,z*s);}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}
        public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(dot(this));}
    }
    public record Triangle(Point a,Point b,Point c,int shade) {}
    public record Grain(Point point,double size) {}
    public record Basis(Point forward,Point side,Point up) {}
    public record Segment(Point start,Point end,double halfWidth) {}
    private IronSandLanceGeometry() {}
    public static Basis basis(Point direction){
        if(direction==null||!direction.finite()||direction.length()<1e-9)return null;
        Point forward=direction.scale(1/direction.length());
        Point side=forward.cross(Math.abs(forward.y)<.95?new Point(0,1,0):new Point(1,0,0));side=side.scale(1/side.length());
        return new Basis(forward,side,side.cross(forward));
    }
    /** Camera up, rather than world up, preserves the offhand side at steep pitch. */
    public static Basis cameraBasis(Point direction,Point cameraUp){
        if(direction==null||cameraUp==null||!direction.finite()||!cameraUp.finite()||direction.length()<1e-9)return null;
        Point forward=direction.scale(1/direction.length()),side=forward.cross(cameraUp);
        if(side.length()<1e-9)return null;side=side.scale(1/side.length());return new Basis(forward,side,side.cross(forward));
    }
    /** Clip ribbons at the actual near plane;48m projectiles must not inherit the classic32m tether cap. */
    public static Segment clip(Point start,Point end,Point forward,double width){
        if(start==null||end==null||!start.finite()||!end.finite()||start.length()>128||end.length()>128
                ||!Double.isFinite(width)||width<=0||width>.1)return null;
        Basis b=basis(forward);if(b==null)return null;double a=start.dot(b.forward),z=end.dot(b.forward),depth=.20+width;
        if(a<depth&&z<depth)return null;
        if(a<depth)start=lerp(start,end,(depth-a)/(z-a));else if(z<depth)end=lerp(start,end,(depth-a)/(z-a));
        return new Segment(start,end,Math.min(width,Math.min(start.dot(b.forward),end.dot(b.forward))*.02));
    }
    public static Point lerp(Point from,Point to,double alpha){return from.plus(to.minus(from).scale(Math.max(0,Math.min(1,alpha))));}
    /** Three broad, opaque grain-textured facets and a triangular rear cap. */
    public static List<Triangle> lance(Point tail,Point head,double scale){
        if(tail==null||head==null||!tail.finite()||!head.finite()||!Double.isFinite(scale)||scale<=0||scale>1)return List.of();
        Basis b=basis(head.minus(tail));if(b==null||head.minus(tail).length()>3)return List.of();
        Point[] rear=new Point[3];
        for(int i=0;i<3;i++){double a=2*Math.PI*i/3;rear[i]=tail.plus(b.side.scale(Math.cos(a)*RADIUS*scale)).plus(b.up.scale(Math.sin(a)*RADIUS*scale));}
        List<Triangle> result=new ArrayList<>(4);
        for(int i=0;i<3;i++)result.add(new Triangle(rear[i],rear[(i+1)%3],head,165+i*33));
        result.add(new Triangle(rear[2],rear[1],rear[0],130));return List.copyOf(result);
    }
    /**
     * Clip actual camera-relative facets, not their authoritative head/tail. The .2m server muzzle
     * leaves a 1.2m waiting shaft behind the eye; projecting that opaque cone uncut fills the screen.
     * Each original triangle becomes zero, one or two triangles. Far bodies remain byte-for-byte
     * the same vertices/shades, and no artificial cap is drawn across the clipped opening.
     */
    public static List<Triangle> visibleLance(Point tail,Point head,double scale,Point cameraForward){
        Basis camera=basis(cameraForward);if(camera==null)return List.of();
        var original=lance(tail,head,scale);if(original.isEmpty())return original;
        List<Triangle> result=new ArrayList<>(8);
        for(var face:original){
            List<Point> polygon=new ArrayList<>(4);Point previous=face.c();double previousDepth=previous.dot(camera.forward);
            for(Point current:List.of(face.a(),face.b(),face.c())){
                double depth=current.dot(camera.forward);boolean inside=depth>=SOLID_NEAR_DEPTH,previousInside=previousDepth>=SOLID_NEAR_DEPTH;
                if(inside!=previousInside){
                    Point crossing=lerp(previous,current,(SOLID_NEAR_DEPTH-previousDepth)/(depth-previousDepth));
                    double roundedDepth=crossing.dot(camera.forward);
                    if(roundedDepth<SOLID_NEAR_DEPTH)crossing=crossing.plus(camera.forward.scale(SOLID_NEAR_DEPTH-roundedDepth));
                    polygon.add(crossing);
                }
                if(inside)polygon.add(current);
                previous=current;previousDepth=depth;
            }
            for(int i=1;i+1<polygon.size();i++){
                Point a=polygon.get(0),b=polygon.get(i),c=polygon.get(i+1);
                if(b.minus(a).cross(c.minus(a)).length()>1e-14)result.add(new Triangle(a,b,c,face.shade()));
            }
        }
        return List.copyOf(result);
    }
    /** Exactly the same sixteen grains per lance are reused each frame; no particle/entity spawning. */
    public static List<Grain> gather(Point source,Point tail,Point head,int index,double charge){
        if(source==null||!source.finite()||tail==null||head==null||!tail.finite()||!head.finite()||index<0||index>=LANCES||!Double.isFinite(charge))return List.of();
        Basis b=basis(head.minus(tail));if(b==null)return List.of();
        double progress=Math.max(0,Math.min(1,charge));List<Grain> result=new ArrayList<>(16);
        for(int i=0;i<16;i++){
            double seed=i*2.399963229728653+index*1.7;
            double t=Math.max(0,Math.min(1,progress*1.5-i*.025));
            Point destination=lerp(tail,head,.12+.82*(i/15.0));
            Point start=source.plus(b.side.scale(Math.cos(seed)*(.12+i*.012))).plus(b.up.scale(Math.sin(seed)*.14));
            Point p=lerp(start,destination,t).plus(b.side.scale(Math.sin(seed+t*5)*.11*Math.sin(Math.PI*t)))
                    .plus(b.up.scale(Math.cos(seed+t*5)*.09*Math.sin(Math.PI*t)));
            result.add(new Grain(p,.012+(i%3)*.003));
        }
        return List.copyOf(result);
    }
    /** Four short flecks only at an authoritative HIT/DEFLECTED point; never a predicted endpoint. */
    public static List<Grain> impact(Point head,Point axis,int index,double age){
        Basis b=basis(axis);if(head==null||!head.finite()||b==null||index<0||index>=LANCES||!Double.isFinite(age)||age<0||age>=4)return List.of();
        List<Grain> grains=new ArrayList<>(4);
        for(int i=0;i<4;i++){double angle=i*Math.PI*.5+index;double distance=.035+age*.035;
            grains.add(new Grain(head.plus(b.side.scale(Math.cos(angle)*distance)).plus(b.up.scale(Math.sin(angle)*distance)),.014));}
        return List.copyOf(grains);
    }
}

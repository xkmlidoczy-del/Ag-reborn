package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Bounded, engine-free geometry. Every effect is an open guide; no projectile is fabricated. */
public final class MagneticArrowSeparationGeometry {
    public static final double RANGE=24,RADIUS=2,HALF_WIDTH=.005;
    public static final int RING_SEGMENTS=16,RINGS=3,RAILS=4,CURVE_SEGMENTS=6;
    public static final int CORRIDOR_SEGMENTS=RING_SEGMENTS*RINGS+RAILS+2;
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public Point plus(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point minus(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public Point scale(double s){return new Point(x*s,y*s,z*s);}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}
        public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(dot(this));}
    }
    public record Segment(Point from,Point to) {}
    private MagneticArrowSeparationGeometry() {}
    private static Point unit(Point p){if(p==null||!p.finite())return null;double length=p.length();return length<1e-9||!Double.isFinite(length)?null:p.scale(1/length);}
    /** Three open, thin rings plus four rails. The origin and axis are accepted server state only. */
    public static List<Segment> corridor(Point origin,Point axis){return corridor(origin,axis,RANGE);}
    public static List<Segment> corridor(Point origin,Point axis,double range){
        Point forward=unit(axis);if(origin==null||!origin.finite()||forward==null||!Double.isFinite(range)||range<=0||range>RANGE)return List.of();
        Point side=unit(forward.cross(Math.abs(forward.y)<.95?new Point(0,1,0):new Point(1,0,0)));
        Point up=side.cross(forward);List<Segment> lines=new ArrayList<>(CORRIDOR_SEGMENTS);
        for(int ring=0;ring<RINGS;ring++){
            Point center=origin.plus(forward.scale(ring*range/(RINGS-1)));
            for(int i=0;i<RING_SEGMENTS;i++)lines.add(new Segment(ring(center,side,up,i),ring(center,side,up,i+1)));
        }
        for(int i=0;i<RAILS;i++){
            Point start=ring(origin,side,up,i*RING_SEGMENTS/RAILS);
            lines.add(new Segment(start,start.plus(forward.scale(range))));
        }
        // A small far-cap cross remains visible even when a nearby wall truncates the full-radius ring outside the FOV.
        Point end=origin.plus(forward.scale(range));double cap=Math.min(.12,range*.1);
        lines.add(new Segment(end.plus(side.scale(-cap)),end.plus(side.scale(cap))));
        lines.add(new Segment(end.plus(up.scale(-cap)),end.plus(up.scale(cap))));
        return List.copyOf(lines);
    }
    private static Point ring(Point center,Point side,Point up,int segment){double angle=2*Math.PI*segment/RING_SEGMENTS;return center.plus(side.scale(Math.cos(angle)*RADIUS)).plus(up.scale(Math.sin(angle)*RADIUS));}
    /** Outward curve rooted at a currently rendered real arrow, at most 1.5 blocks long. */
    public static List<Segment> outward(Point position,Point origin,Point axis){
        Point forward=unit(axis);if(position==null||origin==null||!position.finite()||!origin.finite()||forward==null)return List.of();
        Point relative=position.minus(origin);double along=relative.dot(forward);Point radial=relative.minus(forward.scale(along));
        if(along<-.5||along>RANGE+2||radial.length()>RADIUS+3)return List.of();
        Point outward=unit(radial);if(outward==null)return List.of();
        List<Segment> lines=new ArrayList<>(CURVE_SEGMENTS);Point previous=position;
        for(int i=1;i<=CURVE_SEGMENTS;i++){double t=i/(double)CURVE_SEGMENTS;Point next=position.plus(outward.scale(1.25*t*t)).plus(forward.scale(.35*t));lines.add(new Segment(previous,next));previous=next;}
        return List.copyOf(lines);
    }
}

package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW141's own 64m, non-planar blue-white arc geometry. Never used to authorize damage. */
public final class ThunderNetGeometry {
    public static final double MAX_RANGE=cn.academy.port.highpower.core.ThunderNetRules.RANGE,ANCHOR_RADIUS=12,NEAR_DEPTH=.20,MAX_HALF_WIDTH=.018,CENTER_CLEAR=.020;
    public static final int MAX_NODES=6,MAX_ARC_SEGMENTS=64,MAX_DECORATIVE_GRAINS=96,GRAINS_PER_HIT=8;
    private static final double EPS=1e-8;
    public record Point(double x,double y,double z){
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public Point plus(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point minus(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public Point scale(double n){return new Point(x*n,y*n,z*n);}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}
        public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public double length(){return Math.sqrt(dot(this));}
        public Point lerp(Point p,double t){return plus(p.minus(this).scale(t));}
    }
    public record Segment(Point start,Point end){}
    public record Basis(Point forward,Point side,Point up){}
    public record Ribbon(Point start,Point end,Point side,double halfWidth){}
    public record Grain(Point point,double size){}
    private ThunderNetGeometry(){}
    public static boolean validPoint(Point p){return p!=null&&p.finite()&&Math.abs(p.x)<=30_000_000&&Math.abs(p.z)<=30_000_000&&Math.abs(p.y)<=4096;}
    public static boolean validEndpoints(Point a,Point b){return validPoint(a)&&validPoint(b)&&a.minus(b).length()<=MAX_RANGE+1e-6;}
    public static Basis basis(Point axis){
        if(axis==null||!axis.finite()||axis.length()<EPS)return null;
        Point forward=axis.scale(1/axis.length());
        Point side=forward.cross(Math.abs(forward.y)>.9?new Point(1,0,0):new Point(0,1,0));side=side.scale(1/side.length());
        return new Basis(forward,side,side.cross(forward));
    }
    /** Exact endpoints with small irregular offsets on both orthogonal axes, including vertical arcs. */
    public static List<Segment> arc(Point a,Point b,long token,int frame,boolean fine){
        if(!validEndpoints(a,b))return List.of();Basis basis=basis(b.minus(a));if(basis==null)return List.of();
        double length=b.minus(a).length();int count=Math.max(2,Math.min(MAX_ARC_SEGMENTS,(int)Math.ceil(length/.6)));
        double amplitude=Math.min(fine?.035:.10,length*.05);List<Segment> out=new ArrayList<>(count);Point previous=a;
        for(int i=1;i<=count;i++){
            double t=(double)i/count,envelope=Math.sin(Math.PI*t);
            Point next=i==count?b:a.lerp(b,t).plus(basis.side.scale(noise(token,frame,i)*amplitude*envelope))
                    .plus(basis.up.scale(noise(token^0x3A659BL,frame,i)*amplitude*.8*envelope));
            out.add(new Segment(previous,next));previous=next;
        }
        return List.copyOf(out);
    }
    /** Four disconnected target ticks. An unpaid server lock never gets a hit spark or caster beam. */
    public static List<Segment> lock(Point p,Point look){
        Basis basis=basis(look);if(!validPoint(p)||basis==null)return List.of();List<Segment> out=new ArrayList<>(4);
        for(int i=0;i<4;i++){double angle=i*Math.PI*.5+.25;Point direction=basis.side.scale(Math.cos(angle)).plus(basis.up.scale(Math.sin(angle)));
            out.add(new Segment(p.plus(direction.scale(.22)),p.plus(direction.scale(.34))));}
        return List.copyOf(out);
    }
    /** One bounded eight-fleck set at a server-confirmed hit, never at locked/refused targets. */
    public static List<Grain> hit(Point p,Point axis,long token,int age){
        Basis basis=basis(axis);if(!validPoint(p)||basis==null||age<0||age>=4)return List.of();List<Grain> out=new ArrayList<>(GRAINS_PER_HIT);
        for(int i=0;i<GRAINS_PER_HIT;i++){
            double angle=i*2.3999632297+noise(token,0,i),radius=.035+age*.035;
            out.add(new Grain(p.plus(basis.side.scale(Math.cos(angle)*radius)).plus(basis.up.scale(Math.sin(angle)*radius)),.009+(i%3)*.002));
        }
        return List.copyOf(out);
    }
    /** Upper-triangle bit indexing shared by the bounded server transport. */
    public static int linkBit(int size,int a,int b){
        if(size<0||size>MAX_NODES||a<0||a>=b||b>=size)return 0;int bit=0;
        for(int i=0;i<size;i++)for(int j=i+1;j<size;j++,bit++)if(i==a&&j==b)return 1<<bit;
        return 0;
    }
    public static int linkMask(int size){return size<0||size>MAX_NODES?0:(1<<(size*(size-1)/2))-1;}
    /** Low-duty restraint web: two visible ticks followed by ten genuinely empty ticks. */
    public static boolean restraintVisible(int age){return age>=0&&age%12<2;}
    public static double noise(long token,int frame,int index){
        long v=token^frame*0x9E3779B97F4A7C15L^index*0xBF58476D1CE4E5B9L;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;v^=v>>>31;
        return (v>>>11)*0x1.0p-53*2-1;
    }
    /** Camera-relative near clipping, with no inherited planar or legacy32m restriction. */
    public static Segment nearClip(Point a,Point b,Point forward,double width){
        Basis basis=basis(forward);
        if(a==null||b==null||!a.finite()||!b.finite()||a.length()>160||b.length()>160||basis==null
                ||!Double.isFinite(width)||width<=0||width>MAX_HALF_WIDTH||a.minus(b).length()>MAX_RANGE+.3)return null;
        double za=a.dot(basis.forward),zb=b.dot(basis.forward),near=NEAR_DEPTH+width;
        if(za<near&&zb<near)return null;
        if(za<near)a=a.lerp(b,(near-za)/(zb-za));else if(zb<near)b=a.lerp(b,(near-za)/(zb-za));
        return a.minus(b).length()<EPS?null:new Segment(a,b);
    }
    /** Remove the small center aiming cone without shifting any surviving world geometry. */
    public static List<Segment> clearCenter(Segment segment,Point forward,Point cameraUp){
        if(segment==null||forward==null||cameraUp==null||!forward.finite()||!cameraUp.finite())return List.of();
        Basis fallback=basis(forward);if(fallback==null)return List.of();Point f=fallback.forward,side=f.cross(cameraUp);
        if(side.length()<EPS)side=fallback.side;else side=side.scale(1/side.length());Point up=side.cross(f);
        Point a=segment.start,b=segment.end;double enter=0,exit=1;
        for(Point plane:List.of(side.minus(f.scale(CENTER_CLEAR)),side.scale(-1).minus(f.scale(CENTER_CLEAR)),up.minus(f.scale(CENTER_CLEAR)),up.scale(-1).minus(f.scale(CENTER_CLEAR)))){
            double va=a.dot(plane),vb=b.dot(plane),delta=vb-va;
            if(Math.abs(delta)<EPS){if(va>0)return List.of(segment);continue;}
            double crossing=-va/delta;if(delta>0)exit=Math.min(exit,crossing);else enter=Math.max(enter,crossing);
            if(enter>=exit)return List.of(segment);
        }
        List<Segment> result=new ArrayList<>(2);
        if(enter>EPS)result.add(new Segment(a,a.lerp(b,Math.min(1,enter))));
        if(exit<1-EPS)result.add(new Segment(a.lerp(b,Math.max(0,exit)),b));
        return List.copyOf(result);
    }
    public static Ribbon ribbon(Segment s,Point forward,double width){
        if(s==null)return null;Basis basis=basis(forward);if(basis==null)return null;
        Point direction=s.end.minus(s.start);if(direction.length()<EPS)return null;
        Point side=direction.cross(s.start.scale(-1));if(side.length()<EPS)side=direction.cross(basis.up);if(side.length()<EPS)side=basis.side;
        double actual=Math.min(width,Math.min(s.start.dot(basis.forward),s.end.dot(basis.forward))*.012);
        return new Ribbon(s.start,s.end,side.scale(actual/side.length()),actual);
    }
}

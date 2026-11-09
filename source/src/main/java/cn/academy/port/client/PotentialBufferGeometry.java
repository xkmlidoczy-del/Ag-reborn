package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW142's sparse frontal surface and exactly 6m pulse. Cosmetic geometry grants no protection. */
public final class PotentialBufferGeometry {
    public static final double PULSE_RANGE=cn.academy.port.highpower.core.PotentialBufferRules.RANGE, HALF_ANGLE=Math.PI/3, SHELL_RADIUS=.75;
    public static final double NEAR_DEPTH=.20, MAX_HALF_WIDTH=.012, CENTER_CLEAR=.025;
    public static final int MAX_DECORATIVE_GRAINS=96, GRAINS_PER_HIT=12;
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
    private PotentialBufferGeometry(){}
    public static boolean validPoint(Point p){return p!=null&&p.finite()&&Math.abs(p.x)<=30_000_000&&Math.abs(p.z)<=30_000_000&&Math.abs(p.y)<=4096;}
    public static boolean validDirection(Point p){return p!=null&&p.finite()&&p.length()>=.999&&p.length()<=1.001;}
    public static Basis basis(Point axis){
        if(axis==null||!axis.finite()||axis.length()<EPS)return null;
        Point forward=axis.scale(1/axis.length());
        Point side=forward.cross(Math.abs(forward.y)>.9?new Point(1,0,0):new Point(0,1,0));side=side.scale(1/side.length());
        return new Basis(forward,side,side.cross(forward));
    }
    private static Point surface(Point center,Basis basis,double polar,double around,double radius){
        return center.plus(basis.forward.scale(Math.cos(polar)*radius))
                .plus(basis.side.scale(Math.sin(polar)*Math.cos(around)*radius))
                .plus(basis.up.scale(Math.sin(polar)*Math.sin(around)*radius));
    }
    /** Three broken horizontal rails match the server's yaw-only 120-degree front test. */
    public static List<Segment> layer(Point center,Point facing,long token,int frame){
        if(!validPoint(center)||facing==null||!facing.finite())return List.of();
        if(Math.hypot(facing.x,facing.z)<=1e-6)return List.of();
        Basis basis=basis(new Point(facing.x,0,facing.z));if(basis==null)return List.of();
        List<Segment> result=new ArrayList<>(24);
        for(int rail=0;rail<3;rail++)for(int dash=0;dash<4;dash++){
            double start=-HALF_ANGLE+dash*Math.PI/6+.025;
            double height=(rail-1)*.44;
            Point a=cylinder(center,basis,start,height);
            Point bend=cylinder(center,basis,start+.11,height+noise(token,frame/4,rail*4+dash)*.018);
            Point b=cylinder(center,basis,start+.23,height);
            result.add(new Segment(a,bend));result.add(new Segment(bend,b));
        }
        return List.copyOf(result);
    }
    private static Point cylinder(Point center,Basis basis,double angle,double height){
        return center.plus(basis.forward.scale(Math.cos(angle)*SHELL_RADIUS))
                .plus(basis.side.scale(Math.sin(angle)*SHELL_RADIUS)).plus(new Point(0,height,0));
    }
    /** A single positive absorbed hit branches only around its authoritative surface contact. */
    public static List<Segment> hit(Point impact,Point outward,long token,int age){
        Basis basis=basis(outward);if(!validPoint(impact)||basis==null||age<0||age>=PotentialBufferState.HIT_TICKS)return List.of();
        List<Segment> result=new ArrayList<>(18);
        for(int arm=0;arm<6;arm++){
            double angle=arm*2.3999632297+noise(token,0,arm)*.25;
            Point tangent=basis.side.scale(Math.cos(angle)).plus(basis.up.scale(Math.sin(angle)));
            Point across=basis.side.scale(-Math.sin(angle)).plus(basis.up.scale(Math.cos(angle)));
            double length=.15+(noise(token,0,arm+10)+1)*.045;
            Point bend=impact.plus(tangent.scale(length*.45)).plus(across.scale(noise(token,0,arm+20)*.035));
            Point tip=impact.plus(tangent.scale(length));
            Point branch=bend.plus(tangent.scale(length*.25)).plus(across.scale((arm%2==0?1:-1)*.07));
            result.add(new Segment(impact,bend));result.add(new Segment(bend,tip));result.add(new Segment(bend,branch));
        }
        return List.copyOf(result);
    }
    public static List<Grain> grains(Point impact,Point outward,long token,int age){
        Basis basis=basis(outward);if(!validPoint(impact)||basis==null||age<0||age>=PotentialBufferState.HIT_TICKS)return List.of();
        List<Grain> result=new ArrayList<>(GRAINS_PER_HIT);
        for(int i=0;i<GRAINS_PER_HIT;i++){
            double angle=i*2.3999632297+noise(token,0,i),radius=.025+age*.027;
            result.add(new Grain(impact.plus(basis.side.scale(Math.cos(angle)*radius)).plus(basis.up.scale(Math.sin(angle)*radius)),.005+(i%3)*.001));
        }
        return List.copyOf(result);
    }
    public static double pulseRadius(int age){
        if(age<0||age>=PotentialBufferState.PULSE_TICKS)return 0;
        return SHELL_RADIUS+(PULSE_RANGE-SHELL_RADIUS)*age/(PotentialBufferState.PULSE_TICKS-1.0);
    }
    /** Six short outward-moving meridian fragments, ending at the real server range, never a filled sphere. */
    public static List<Segment> pulse(Point origin,Point facing,int age){
        Basis basis=basis(facing);double radius=pulseRadius(age);
        if(!validPoint(origin)||basis==null||radius==0)return List.of();
        List<Segment> result=new ArrayList<>(24);
        for(int band=0;band<2;band++)for(int dash=0;dash<6;dash++){
            double polar=band==0?Math.PI*.42:Math.PI*.67,around=dash*Math.PI/3+band*.25;
            Point a=surface(origin,basis,polar,around,radius);
            Point b=surface(origin,basis,polar,around+.08,radius);
            Point c=surface(origin,basis,polar,around+.16,radius);
            result.add(new Segment(a,b));result.add(new Segment(b,c));
        }
        return List.copyOf(result);
    }
    public static double noise(long token,int frame,int index){
        long v=token^frame*0x9E3779B97F4A7C15L^index*0xBF58476D1CE4E5B9L;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;v^=v>>>31;
        return (v>>>11)*0x1.0p-53*2-1;
    }
    /** Camera-relative near clipping, for NEW142's short, thin surface fragments. */
    public static Segment nearClip(Point a,Point b,Point forward,double width){
        Basis basis=basis(forward);
        if(a==null||b==null||!a.finite()||!b.finite()||a.length()>160||b.length()>160||basis==null
                ||!Double.isFinite(width)||width<=0||width>MAX_HALF_WIDTH||a.minus(b).length()>PULSE_RANGE+.3)return null;
        double za=a.dot(basis.forward),zb=b.dot(basis.forward),near=NEAR_DEPTH+width;
        if(za<near&&zb<near)return null;
        if(za<near)a=a.lerp(b,(near-za)/(zb-za));else if(zb<near)b=a.lerp(b,(near-za)/(zb-za));
        return a.minus(b).length()<EPS?null:new Segment(a,b);
    }
    /** Remove the small center aiming cone without shifting any surviving world geometry. */
    public static List<Segment> clearCenter(Segment segment,Point forward,Point cameraUp,double width){
        if(segment==null||forward==null||cameraUp==null||!forward.finite()||!cameraUp.finite()
                ||!Double.isFinite(width)||width<=0||width>MAX_HALF_WIDTH)return List.of();
        Basis fallback=basis(forward);if(fallback==null)return List.of();Point f=fallback.forward,side=f.cross(cameraUp);
        if(side.length()<EPS)side=fallback.side;else side=side.scale(1/side.length());Point up=side.cross(f);
        Point a=segment.start,b=segment.end;double enter=0,exit=1;
        // Widen each clip plane by the entire ribbon half-width, preserving the empty aiming cone.
        double margin=width*Math.sqrt(1+CENTER_CLEAR*CENTER_CLEAR);
        for(Point plane:List.of(side.minus(f.scale(CENTER_CLEAR)),side.scale(-1).minus(f.scale(CENTER_CLEAR)),up.minus(f.scale(CENTER_CLEAR)),up.scale(-1).minus(f.scale(CENTER_CLEAR)))){
            double va=a.dot(plane)-margin,vb=b.dot(plane)-margin,delta=vb-va;
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

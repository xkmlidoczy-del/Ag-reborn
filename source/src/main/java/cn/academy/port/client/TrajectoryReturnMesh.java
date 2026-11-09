package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW431 gray vector pressure: open convergence, opposed direction arrows, real receipt trails. */
public final class TrajectoryReturnMesh {
    public static final int MAX_CONTROLLERS=4, MAX_PARTICLES=0, MATERIAL_LAYERS=2, MAX_STROKES=64;
    public static final int MAX_VERTICES=2048, MAX_CONTROLLER_VERTICES=512;
    public static final double CULL_DISTANCE=64, NEAR_PLANE=.08, DECORATIVE_MARGIN=1.15;
    public enum Role { INCOMING, REVERSED, PRESSURE, TRAIL, HAMMER, DAMAGE, ABSORBED, FALLBACK }
    public record Stroke(Point from,Point to,Point anchor,double width,int alpha,Role role) {}
    public record Profile(double width,int alpha,int gray,float minV,float maxV) {}
    public record Bounds(Point min,Point max) {
        public boolean contains(Point p){return p.x()>=min.x()&&p.y()>=min.y()&&p.z()>=min.z()&&p.x()<=max.x()&&p.y()<=max.y()&&p.z()<=max.z();}
    }
    public record Shape(List<Stroke> strokes) {
        public Shape {strokes=List.copyOf(strokes);}
        public int vertexCount(){return strokes.size()*MATERIAL_LAYERS*4;}
    }
    /** Production billboard corners in double camera-relative coordinates, before the float boundary. */
    public record Ribbon(List<Point> corners,int startAlpha,int endAlpha) {
        public Ribbon { corners=List.copyOf(corners); }
    }
    public static Ribbon ribbon(Stroke stroke,Profile profile,Point camera,Point forward,Point fallback){
        if(stroke==null||profile==null||camera==null||forward==null||fallback==null
                ||!camera.finite()||!forward.finite()||!fallback.finite()||length(forward)<.5||length(fallback)<.5
                ||!Double.isFinite(profile.width)||profile.width<=0||profile.alpha<=0)return null;
        Stroke clipped=clipNear(new Stroke(relative(stroke.from,camera),relative(stroke.to,camera),stroke.anchor,
                profile.width,profile.alpha,stroke.role),normalized(forward),profile.width/2);
        if(clipped==null)return null;
        Point a=clipped.from,b=clipped.to,side=normalized(cross(subtract(b,a),scale(add(a,b),-.5)));
        if(length(side)<.5)side=normalized(fallback);side=scale(side,profile.width/2);
        var corners=List.of(subtract(a,side),add(a,side),add(b,side),subtract(b,side));
        if(corners.stream().anyMatch(p->!p.finite()))return null;
        return new Ribbon(corners,(int)Math.round(profile.alpha*nearEyeFade(length(a))),(int)Math.round(profile.alpha*nearEyeFade(length(b))));
    }
    private TrajectoryReturnMesh() {}
    /** Admits a complete decoration, otherwise a complete minimal direction arrow per active slot. */
    public static Shape budgeted(TrajectoryReturnState.Controller c,double age,int remaining){
        if(remaining<0)return new Shape(List.of());
        Shape full=shape(c,age);if(full.vertexCount()<=remaining)return full;
        Shape minimal=fallback(c,age);return minimal.vertexCount()<=remaining?minimal:new Shape(List.of());
    }
    public static Shape shape(TrajectoryReturnState.Controller c,double age){
        var strokes=new ArrayList<Stroke>();
        if(!valid(c,age))return new Shape(strokes);
        for(var s:c.slots()) {
            double since=age-s.receivedAt();
            if(s.hammer()) {
                hammer(strokes,s,age-s.reversedAt());
            } else {
                double reverseAge=age-s.reversedAt();
                if(s.kind()!=TrajectoryReturnState.CONTACT&&reverseAge<6)reverse(strokes,s,reverseAge);
                // Never synthesize, smooth across, or extend past any received native segment.
                for(var segment:s.trail()) {
                    double tailAge=age-segment.tick();
                    if(tailAge>=0&&tailAge<TrajectoryReturnState.TRAIL_LIFE&&since<=TrajectoryReturnState.GAP_TICKS)
                        add(strokes,segment.from(),segment.to(),segment.from(),.034,(int)(170*(1-tailAge/TrajectoryReturnState.TRAIL_LIFE)),Role.TRAIL);
                }
            }
            if((s.kind()==TrajectoryReturnState.CONTACT||s.hammer())&&since>=0&&since<8)contact(strokes,s,since);
        }
        // The design is bounded by construction, never by slicing an incomplete glyph.
        if(strokes.size()>MAX_STROKES)throw new IllegalStateException("trajectory decoration exceeds complete mesh budget");
        return new Shape(strokes);
    }
    private static boolean valid(TrajectoryReturnState.Controller c,double age){
        return c!=null&&Double.isFinite(age)&&age>=0&&age<TrajectoryReturnState.LIFE_TICKS
                &&c.slots().size()<=TrajectoryReturnState.MAX_SLOTS;
    }
    private static void reverse(List<Stroke> out,TrajectoryReturnState.Slot s,double age){
        Point d=normalized(s.reversalVelocity());if(length(d)<.5)return;
        Point right=right(d),up=normalized(cross(right,d)),p=s.reversal();
        int alpha=(int)(238*Math.min(1,(6-age)/2));
        // Separated before/after arrows have visibly opposed tips at the real reversal point.
        Point incomingEnd=add(p,scale(right,.15));
        arrow(out,add(incomingEnd,scale(d,.68)),incomingEnd,p,right,.050,(int)(alpha*.68),Role.INCOMING);
        Point start=add(p,scale(right,-.15));
        arrow(out,start,add(start,scale(d,.82)),p,right,.065,alpha,Role.REVERSED);
        // Open convergence chevrons form a broad pressure shoulder, not a ring or a closed orb.
        for(int side:new int[]{-1,1}) {
            Point a=add(p,add(scale(right,side*.43),scale(d,-.15)));
            Point b=add(p,add(scale(right,side*.24),scale(d,.04)));
            Point e=add(p,add(scale(right,side*.15),scale(d,.27)));
            add(out,a,b,p,.058,(int)(alpha*.73),Role.PRESSURE);
            add(out,b,e,p,.052,(int)(alpha*.73),Role.PRESSURE);
            add(out,add(p,add(scale(up,side*.20),scale(d,-.04))),add(p,add(scale(up,side*.13),scale(d,.25))),p,.043,(int)(alpha*.48),Role.PRESSURE);
        }
    }
    private static void hammer(List<Stroke> out,TrajectoryReturnState.Slot s,double age){
        if(age<0||age>=7)return;
        Point delta=subtract(s.position(),s.origin());double distance=length(delta);if(distance<.05||distance>40.00001)return;
        Point d=scale(delta,1/distance),r=right(d),u=normalized(cross(r,d));
        // A short compression column sweeps only inside the already-authoritative clipped path.
        double head=Math.min(distance,.7+age*Math.min(5,distance/3));
        double tail=Math.max(0,head-2.4), inset=Math.min(.08,(head-tail)*.12);
        Point a=add(s.origin(),scale(d,tail+inset)),b=add(s.origin(),scale(d,head-inset));
        int alpha=(int)(214*Math.min(1,(7-age)/3));
        for(int axis=0;axis<4;axis++) {
            double angle=axis*Math.PI/2;Point offset=add(scale(r,.13*Math.cos(angle)),scale(u,.13*Math.sin(angle)));
            add(out,add(a,offset),add(b,offset),s.origin(),.052,(int)(alpha*.70),Role.HAMMER);
        }
        for(int ring=0;ring<2;ring++) {
            Point center=ring==0?a:b;double radius=ring==0?.19:.25;
            for(int j=0;j<4;j++) {
                double angle=-.9+j*.45,next=angle+.45;
                Point p=add(center,add(scale(r,radius*Math.sin(angle)),scale(u,radius*Math.cos(angle))));
                Point q=add(center,add(scale(r,radius*Math.sin(next)),scale(u,radius*Math.cos(next))));
                add(out,p,q,s.origin(),.060,alpha,Role.HAMMER);
            }
        }
        add(out,a,b,s.origin(),.030,(int)(alpha*.65),Role.HAMMER);
    }
    private static void contact(List<Stroke> out,TrajectoryReturnState.Slot s,double age){
        if(s.status()!=TrajectoryReturnState.DAMAGED&&s.status()!=TrajectoryReturnState.ABSORBED)return;
        Point d=normalized(s.hammer()?subtract(s.position(),s.origin()):s.velocity());
        if(length(d)<.5)d=normalized(s.reversalVelocity());if(length(d)<.5)d=new Point(0,0,1);
        Point r=right(d),u=normalized(cross(r,d)),p=add(s.position(),scale(d,-.055));
        int alpha=(int)(225*Math.min(1,(8-age)/4));
        if(s.status()==TrajectoryReturnState.DAMAGED)for(int i=0;i<4;i++) {
            double a=Math.PI*(.25+i*.5);Point axis=add(scale(r,Math.cos(a)),scale(u,Math.sin(a)));
            add(out,add(p,scale(axis,.045)),add(p,scale(axis,.18)),p,.048,alpha,Role.DAMAGE);
        } else for(int i:new int[]{-1,1}) {
            Point center=add(p,scale(u,i*.085));add(out,add(center,scale(r,-.18)),add(center,scale(r,.18)),p,.052,(int)(alpha*.75),Role.ABSORBED);
        }
    }
    public static Shape fallback(TrajectoryReturnState.Controller c,double age){
        var out=new ArrayList<Stroke>();if(!valid(c,age))return new Shape(out);
        for(var s:c.slots()) {
            if(s.kind()==TrajectoryReturnState.CONTACT||age-s.receivedAt()>6)continue;
            Point d=normalized(s.hammer()?subtract(s.position(),s.origin()):s.velocity());if(length(d)<.5)continue;
            Point tip=s.hammer()?add(s.origin(),scale(d,Math.min(.75,length(subtract(s.position(),s.origin()))))):s.position();
            double n=s.hammer()?Math.min(.4,length(subtract(s.position(),s.origin()))*.5):.4;
            arrow(out,add(tip,scale(d,-n)),tip,tip,right(d),.038,180,Role.FALLBACK);
        }
        return new Shape(out);
    }
    private static void arrow(List<Stroke> out,Point from,Point tip,Point anchor,Point right,double width,int alpha,Role role){
        Point d=normalized(subtract(tip,from));double wing=Math.min(.14,length(subtract(tip,from))*.34);
        add(out,from,tip,anchor,width,alpha,role);
        for(int side:new int[]{-1,1})add(out,tip,add(tip,add(scale(d,-wing),scale(right,side*wing*.62))),anchor,width*.90,alpha,role);
    }
    private static void add(List<Stroke> out,Point a,Point b,Point anchor,double width,int alpha,Role role){
        if(a.finite()&&b.finite()&&anchor.finite()&&alpha>0&&a.distanceSquared(b)>1e-12)out.add(new Stroke(a,b,anchor,width,alpha,role));
    }
    public static Profile profile(Stroke s,boolean core){
        int gray=s.role==Role.INCOMING?216:244;
        return core?new Profile(s.width,s.alpha,gray,11f/32,21f/32):new Profile(s.width*2.45,(int)(s.alpha*.43),170,0,1);
    }
    public static Bounds bounds(TrajectoryReturnState.Controller c){
        Point min=null,max=null;
        for(var s:c.slots()) {
            var points=new ArrayList<Point>(List.of(s.origin(),s.position(),s.reversal()));
            for(var t:s.trail()){points.add(t.from());points.add(t.to());}
            for(var p:points){if(min==null){min=max=p;}else{min=new Point(Math.min(min.x(),p.x()),Math.min(min.y(),p.y()),Math.min(min.z(),p.z()));max=new Point(Math.max(max.x(),p.x()),Math.max(max.y(),p.y()),Math.max(max.z(),p.z()));}}
        }
        if(min==null)min=max=TrajectoryReturnState.ZERO;
        Point margin=new Point(DECORATIVE_MARGIN,DECORATIVE_MARGIN,DECORATIVE_MARGIN);return new Bounds(subtract(min,margin),add(max,margin));
    }
    public static Point relative(Point world,Point camera){return subtract(world,camera);}
    public static Stroke clipNear(Stroke s,Point forward,double halfWidth){
        if(s==null||!s.from.finite()||!s.to.finite()||!forward.finite()||!Double.isFinite(halfWidth))return null;
        double plane=NEAR_PLANE+Math.max(0,halfWidth),a=dot(s.from,forward),b=dot(s.to,forward);
        if(a<=plane&&b<=plane)return null;Point from=s.from,to=s.to;
        if(a<plane)from=lerp(from,to,(plane-a)/(b-a));else if(b<plane)to=lerp(from,to,(plane-a)/(b-a));
        return new Stroke(from,to,s.anchor,s.width,s.alpha,s.role);
    }
    public static double nearEyeFade(double distance){double t=Math.max(0,Math.min(1,(distance-.23)/.55));return t*t*(3-2*t);}
    private static Point right(Point d){return normalized(cross(d,Math.abs(d.y())>.98?new Point(0,0,1):new Point(0,1,0)));}
    private static double length(Point p){return Math.sqrt(dot(p,p));}
    private static Point normalized(Point p){double n=length(p);return n<1e-9?TrajectoryReturnState.ZERO:scale(p,1/n);}
    private static Point add(Point a,Point b){return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    private static Point subtract(Point a,Point b){return new Point(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    private static Point scale(Point p,double s){return new Point(p.x()*s,p.y()*s,p.z()*s);}
    private static Point lerp(Point a,Point b,double t){return add(a,scale(subtract(b,a),t));}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Point cross(Point a,Point b){return new Point(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}

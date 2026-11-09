package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static cn.academy.port.client.TrajectoryReturnMesh.*;
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Optional NEW431 presentation only. Every trail is an independent authoritative receipt. */
public final class TrajectoryReturnEnhancedMesh {
    public static final int REVERSAL_LIFE=14, HISTORY_LIFE=8, CONTACT_LIFE=12, HAMMER_LIFE=16;
    private static final double SHOULDER_SCALE=3.2, CORE_SCALE=.80;
    private static final double CAMERA_FADE_START=.35, CAMERA_FADE_END=1.65;
    private TrajectoryReturnEnhancedMesh() {}

    /**
     * Retained histories belong to the caller and must also be included in its culling bounds.
     * Four arrow slots use at most 4 * (6 direction + 6 pressure + 4 history) strokes.
     * A hammer is exclusive, as required by the existing authenticated state machine.
     */
    public static Shape shape(Controller c,double age,Map<Integer,List<Segment>> histories) {
        var out=new ArrayList<Stroke>();
        if(c==null||!Double.isFinite(age)||age<0||age>=LIFE_TICKS||c.slots().size()>MAX_SLOTS)
            return new Shape(out);
        if(c.slots().stream().anyMatch(Slot::hammer)&&c.slots().size()!=1)return new Shape(out);
        int occupied=0;
        for(var s:c.slots()) {
            if(!valid(s)||s.number()<0||s.number()>=MAX_SLOTS||(occupied&(1<<s.number()))!=0)
                return new Shape(List.of());
            occupied|=1<<s.number();
            double since=age-s.receivedAt(),reverseAge=age-s.reversedAt();
            if(since<0||reverseAge<0)continue;
            if(s.hammer()) {
                hammer(out,s,reverseAge);
                contact(out,s,since);
            } else if(s.kind()==CONTACT) {
                // A terminal receipt replaces the reversal, including REFUSED without a hit cue.
                contact(out,s,since);
            } else if(s.kind()==REVERSE||s.kind()==FLIGHT) {
                if(s.status()!=COMMITTED)continue;
                reverse(out,s,reverseAge);
                var history=histories==null?null:histories.get(s.number());
                history(out,history==null?s.trail():history,age);
            }
        }
        if(out.size()>MAX_STROKES)throw new IllegalStateException("enhanced trajectory decoration exceeds complete mesh budget");
        return new Shape(out);
    }

    private static boolean valid(Slot s) {
        return s!=null&&finite(s.origin())&&finite(s.position())&&finite(s.velocity())
                &&finite(s.reversal())&&finite(s.reversalVelocity())&&s.reversedAt()>=0
                &&s.receivedAt()>=s.reversedAt()&&s.status()>=REFUSED&&s.status()<=COMMITTED
                &&(!s.hammer()||(s.kind()==AIR_HAMMER||s.kind()==CONTACT)&&s.status()!=COMMITTED);
    }

    private static void reverse(List<Stroke> out,Slot s,double age) {
        if(age>=REVERSAL_LIFE)return;
        Point d=unit(s.reversalVelocity());if(length(d)<.5)return;
        Point r=right(d),u=unit(cross(r,d)),p=s.reversal();
        int alpha=fade(244,age,REVERSAL_LIFE,5);
        // These opposed symbols describe direction at the real reversal, never a future path.
        Point incoming=plus(p,times(r,.17)),outgoing=plus(p,times(r,-.17));
        arrow(out,plus(incoming,times(d,.68)),incoming,p,r,.065,(int)(alpha*.76),Role.INCOMING);
        arrow(out,outgoing,plus(outgoing,times(d,.80)),p,r,.077,alpha,Role.REVERSED);
        // Two disconnected 106-degree arcs remain broad even viewed along the projectile axis.
        // Their changing radius is local pressure decoration, not sampled or predicted movement.
        double radius=.64+.055*Math.sin(Math.PI*Math.min(1,age/REVERSAL_LIFE));
        openPair(out,p,d,r,u,radius,.83,-.035,.10,.082,(int)(alpha*.90),Role.PRESSURE,3);
    }

    private static void history(List<Stroke> out,List<Segment> segments,double age) {
        for(int i=Math.max(0,segments.size()-MAX_SEGMENTS);i<segments.size();i++) {
            var segment=segments.get(i);if(segment==null||!finite(segment.from())||!finite(segment.to()))continue;
            double since=age-segment.tick(),lengthSquared=segment.from().distanceSquared(segment.to());
            if(segment.tick()<0||since<0||since>=HISTORY_LIFE||lengthSquared>64.000001)continue;
            // Do not connect adjacent receipts, fill a gap, or animate either endpoint.
            add(out,segment.from(),segment.to(),segment.from(),.046,
                    (int)Math.round(196*(1-since/HISTORY_LIFE)),Role.TRAIL);
        }
    }

    private static void hammer(List<Stroke> out,Slot s,double age) {
        if(age>=HAMMER_LIFE)return;
        Point delta=minus(s.position(),s.origin());double distance=length(delta);
        if(distance<1e-6||distance>40.00001)return;
        Point d=times(delta,1/distance),r=right(d),u=unit(cross(r,d)),origin=s.origin();
        double size=Math.min(1,distance/.8),width=.087*size;
        // Reserve half the broadest ribbon width at both axial ends, even for oblique cameras.
        double inset=width*SHOULDER_SCALE*.5+Math.min(.01,distance*.01);
        double palm=Math.min(.58,distance*.31);
        int alpha=fade(237,age,HAMMER_LIFE,6);
        Point palmCenter=plus(origin,times(d,palm));
        openPair(out,palmCenter,d,r,u,.46*size,.80,0,0,width,(int)(alpha*.87),Role.HAMMER,3);
        // The leading transverse shoulder traverses only this receipt's eye-to-contact interval.
        double start=Math.min(.76,distance*.54),progress=Math.min(1,age/10);
        double head=start+(distance-inset-start)*progress;
        Point headCenter=plus(origin,times(d,head));
        openPair(out,headCenter,d,r,u,.68*size,.82,0,0,width,alpha,Role.HAMMER,4);
        double tail=Math.max(inset,head-Math.min(1.20,distance*.28));
        Point tailCenter=plus(origin,times(d,tail));
        openPair(out,tailCenter,d,r,u,.39*size,.80,0,0,width*.80,(int)(alpha*.66),Role.HAMMER,3);
        // Two short, separated pressure struts, with no projectile arrow or invented palm offset.
        for(int side:new int[]{-1,1}) {
            Point offset=times(r,side*.16*size);
            add(out,plus(tailCenter,offset),plus(headCenter,offset),origin,width*.62,(int)(alpha*.58),Role.HAMMER);
        }
    }

    private static void contact(List<Stroke> out,Slot s,double age) {
        if(age>=CONTACT_LIFE||(s.status()!=DAMAGED&&s.status()!=ABSORBED))return;
        Point delta=minus(s.position(),s.origin());double distance=length(delta);
        if(s.hammer()&&(distance<1e-6||distance>40.00001))return;
        Point d=unit(s.hammer()?delta:s.velocity());
        if(length(d)<.5)d=unit(s.reversalVelocity());if(length(d)<.5)return;
        Point r=right(d),u=unit(cross(r,d));double size=s.hammer()?Math.min(1,distance/.8):1;
        Point p=plus(s.position(),times(d,s.hammer()?-Math.min(.18,distance*.24):-.07));
        int alpha=fade(244,age,CONTACT_LIFE,5);
        if(s.status()==DAMAGED) {
            double spread=(.33+.07*Math.min(1,age/5))*size;
            for(int i=0;i<4;i++) {
                double angle=Math.PI*(.25+i*.5);Point axis=plus(times(r,Math.cos(angle)),times(u,Math.sin(angle)));
                add(out,plus(p,times(axis,.095*size)),plus(p,times(axis,spread)),s.position(),.070*size,alpha,Role.DAMAGE);
            }
        } else {
            // Absorption is a pair of open, flattened brackets, never a damage star or shield.
            openPair(out,p,d,r,u,.33*size,.55,0,0,.071*size,(int)(alpha*.87),Role.ABSORBED,3);
        }
    }

    private static void openPair(List<Stroke> out,Point center,Point d,Point r,Point u,double radius,
                                 double aspect,double axial,double bow,double width,int alpha,Role role,int steps) {
        for(int side:new int[]{-1,1})for(int i=0;i<steps;i++) {
            double a=-.93+1.86*i/steps,b=-.93+1.86*(i+1)/steps;
            add(out,arc(center,d,r,u,side,radius,aspect,axial,bow,a),
                    arc(center,d,r,u,side,radius,aspect,axial,bow,b),center,width,alpha,role);
        }
    }

    private static Point arc(Point center,Point d,Point r,Point u,int side,double radius,
                             double aspect,double axial,double bow,double angle) {
        return plus(center,plus(times(r,side*radius*Math.cos(angle)),
                plus(times(u,radius*aspect*Math.sin(angle)),times(d,axial+bow*Math.cos(angle)))));
    }

    private static void arrow(List<Stroke> out,Point from,Point tip,Point anchor,Point right,
                              double width,int alpha,Role role) {
        Point d=unit(minus(tip,from));double wing=Math.min(.19,length(minus(tip,from))*.32);
        add(out,from,tip,anchor,width,alpha,role);
        for(int side:new int[]{-1,1})add(out,tip,
                plus(tip,plus(times(d,-wing),times(right,side*wing*.72))),anchor,width*.90,alpha,role);
    }

    /** Uses the existing texture and native alpha cutoff; does not need a new shader or raster asset. */
    public static Profile profile(Stroke s,boolean core) {
        // Shoulder samples the useful gradient (rows 8..23), rather than mostly transparent rows.
        // Gray and opacity stay lower than the crisp center; extra area comes from transverse geometry.
        return core?new Profile(s.width()*CORE_SCALE,s.alpha(),s.role()==Role.INCOMING?224:249,12f/32,20f/32)
                :new Profile(s.width()*SHOULDER_SCALE,(int)Math.round(s.alpha()*.56),153,8f/32,24f/32);
    }

    /** Camera-local opacity only: receipt geometry, shoulder extent and distant material are unchanged. */
    public static Profile profile(Stroke s,boolean core,Point camera) {
        var profile=profile(s,core);
        return new Profile(profile.width(),(int)Math.round(profile.alpha()*cameraFade(s,camera)),
                profile.gray(),profile.minV(),profile.maxV());
    }

    /**
     * Use the closest point of each segment, not its receipt anchor or just its endpoints.
     * This also attenuates a long chord crossing the eye while leaving distant hammer arcs readable.
     * Subtract the camera in double precision before projecting, including near the world border.
     */
    public static double cameraFade(Stroke s,Point camera) {
        if(s==null||!finite(s.from())||!finite(s.to())||!finite(camera))return 0;
        Point a=minus(s.from(),camera),b=minus(s.to(),camera),delta=minus(b,a);
        double lengthSquared=dot(delta,delta);
        double along=lengthSquared>1e-18?Math.max(0,Math.min(1,-dot(a,delta)/lengthSquared)):0;
        double distance=length(plus(a,times(delta,along)));
        if(!Double.isFinite(distance))return 0;
        double t=Math.max(0,Math.min(1,(distance-CAMERA_FADE_START)/(CAMERA_FADE_END-CAMERA_FADE_START)));
        return t*t*(3-2*t);
    }

    private static int fade(int alpha,double age,double life,double fadeTicks) {
        return (int)Math.round(alpha*Math.min(1,Math.max(0,(life-age)/fadeTicks)));
    }
    private static void add(List<Stroke> out,Point a,Point b,Point anchor,double width,int alpha,Role role) {
        if(finite(a)&&finite(b)&&finite(anchor)&&Double.isFinite(width)&&width>0&&alpha>0
                &&a.distanceSquared(b)>1e-12)out.add(new Stroke(a,b,anchor,width,Math.min(255,alpha),role));
    }
    private static boolean finite(Point p){return p!=null&&p.finite();}
    private static Point right(Point d){return unit(cross(d,Math.abs(d.y())>.98?new Point(0,0,1):new Point(0,1,0)));}
    private static double length(Point p){return Math.sqrt(dot(p,p));}
    private static Point unit(Point p){double n=length(p);return n<1e-9?ZERO:times(p,1/n);}
    private static Point plus(Point a,Point b){return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    private static Point minus(Point a,Point b){return new Point(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    private static Point times(Point p,double n){return new Point(p.x()*n,p.y()*n,p.z()*n);}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static Point cross(Point a,Point b){return new Point(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}

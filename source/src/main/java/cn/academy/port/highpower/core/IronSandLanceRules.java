package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.UUID;

/** NEW131 finite geometry and numerics. These rules never touch an inventory or native entity. */
public final class IronSandLanceRules {
    public static final int CHARGE_TICKS = 12, MAX_CHARGE_TICKS = 200, LANCES = 3, ORBIT_TICKS = SkillTuning.integer("iron_sand_lance", "duration_ticks"), SCAN_LIMIT = 64;
    public static final double RANGE = SkillTuning.number("iron_sand_lance", "reach"), SPEED = SkillTuning.number("iron_sand_lance", "speed"), LENGTH = 1.2, RADIUS = .25, ORBIT_RADIUS = 1.6, ORBIT_STEP = .12;
    public static final double TURN = Math.PI / 4, MAX_ARROW_SPEED = 3, RANGE_EPSILON = 1e-7;
    private static final ContinuousHighPowerGrowth.Curve COST = ContinuousHighPowerGrowth.curve("12", "12", "12", "4", "1");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("60", "60", "60", "15", "2");
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vector add(Vector b) { return new Vector(x+b.x,y+b.y,z+b.z); }
        public Vector subtract(Vector b) { return new Vector(x-b.x,y-b.y,z-b.z); }
        public Vector scale(double s) { return new Vector(x*s,y*s,z*s); }
        public double dot(Vector b) { return x*b.x+y*b.y+z*b.z; }
        public double lengthSquared() { return dot(this); }
    }
    public record Segment(Vector tail, Vector head) {}
    private IronSandLanceRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q)); }
    public static BigDecimal damage(BigDecimal currentDamage) {
        if (currentDamage == null || currentDamage.signum() <= 0 || currentDamage.compareTo(new BigDecimal("1e20")) > 0) return null;
        return currentDamage.multiply(new BigDecimal("0.6")).stripTrailingZeros();
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MAX_CHARGE_TICKS - ORBIT_TICKS; }
    public static boolean charged(long start, long now) { return clock(start) && clock(now) && now >= start && now-start >= CHARGE_TICKS && now-start < MAX_CHARGE_TICKS; }
    public static boolean chargeExpired(long start, long now) { return !clock(start) || !clock(now) || now < start || now-start >= MAX_CHARGE_TICKS; }
    public static boolean orbitExpired(long start, long now) { return !clock(start) || !clock(now) || now < start || now-start >= ORBIT_TICKS; }
    public static boolean unit(Vector v) { return v != null && v.finite() && Math.abs(v.lengthSquared()-1) <= 1e-8; }
    public static Vector direction(Vector look, int index) {
        if (!unit(look) || index < 0 || index >= LANCES) return null;
        double angle = Math.toRadians((index-1)*4), s = Math.sin(angle), c = Math.cos(angle);
        return new Vector(look.x*c-look.z*s, look.y, look.x*s+look.z*c);
    }
    /** A world-coordinate ULP may otherwise round a 2m step above its budget or leave a zero-motion tail.
     * Move only inward by bounded representable increments; never extend the actual traveled range. */
    public static Vector step(Vector start,Vector direction,double remaining) {
        if(start==null||!start.finite()||!unit(direction)||!Double.isFinite(remaining)||remaining<=RANGE_EPSILON||remaining>RANGE)return null;
        double limit=Math.min(SPEED,remaining);Vector next=start.add(direction.scale(limit));
        for(int i=0;i<=8;i++){
            double distance=next.subtract(start).lengthSquared();
            if(distance>0&&distance<=limit*limit)return next;
            next=new Vector(Math.nextAfter(next.x,start.x),Math.nextAfter(next.y,start.y),Math.nextAfter(next.z,start.z));
        }
        return null;
    }
    public static Segment orbit(Vector center, int index, long age) {
        if (center == null || !center.finite() || index < 0 || index >= LANCES || age < 0 || age >= ORBIT_TICKS) return null;
        double a = index*Math.PI*2/LANCES + age*ORBIT_STEP;
        Vector middle = center.add(new Vector(Math.cos(a)*ORBIT_RADIUS,0,Math.sin(a)*ORBIT_RADIUS));
        Vector half = new Vector(-Math.sin(a),0,Math.cos(a)).scale(LENGTH/2);
        return new Segment(middle.subtract(half),middle.add(half));
    }
    public static double pointSegmentSquared(Vector p, Vector a, Vector b) {
        Vector axis=b.subtract(a);double n=axis.lengthSquared(),t=n<1e-20?0:Math.clamp(p.subtract(a).dot(axis)/n,0,1);
        return p.subtract(a.add(axis.scale(t))).lengthSquared();
    }
    private static Vector lerp(Vector a,Vector b,double t) { return a.add(b.subtract(a).scale(t)); }
    private static double distanceAt(Vector p0,Vector p1,Segment old,Segment next,double t) {
        return pointSegmentSquared(lerp(p0,p1,t),lerp(old.tail,next.tail,t),lerp(old.head,next.head,t));
    }
    /** Same-time contact against the actually observed arrow and lance sweeps, never an upcoming arrow ray.
     * A bounded numerical minimization can conservatively miss a grazing contact, but never invent one:
     * the returned time itself must witness distance <= the physical capsule radius. */
    public static double contact(Vector p0,Vector p1,Segment old,Segment next) {
        if (p0==null||p1==null||old==null||next==null||!p0.finite()||!p1.finite()
                || !old.tail.finite()||!old.head.finite()||!next.tail.finite()||!next.head.finite()) return Double.NaN;
        int best=0;double min=Double.POSITIVE_INFINITY;
        for(int i=0;i<=32;i++){double d=distanceAt(p0,p1,old,next,i/32.0);if(d<min){min=d;best=i;}}
        double low=Math.max(0,(best-1)/32.0),high=Math.min(1,(best+1)/32.0);
        for(int i=0;i<32;i++){double a=low+(high-low)/3,b=high-(high-low)/3;if(distanceAt(p0,p1,old,next,a)<distanceAt(p0,p1,old,next,b))high=b;else low=a;}
        double t=(low+high)/2;
        if(distanceAt(p0,p1,old,next,t)<=RADIUS*RADIUS)return t;
        return min<=RADIUS*RADIUS?best/32.0:Double.NaN;
    }
    /** Read-only diagnostic minimum using the same bounded same-time geometry as contact(). */
    public static double diagnosticMinimumDistance(Vector p0,Vector p1,Segment old,Segment next) {
        if(p0==null||p1==null||old==null||next==null||!p0.finite()||!p1.finite()
                ||!old.tail.finite()||!old.head.finite()||!next.tail.finite()||!next.head.finite())return Double.NaN;
        int best=0;double min=Double.POSITIVE_INFINITY;
        for(int i=0;i<=32;i++){double d=distanceAt(p0,p1,old,next,i/32.0);if(d<min){min=d;best=i;}}
        double low=Math.max(0,(best-1)/32.0),high=Math.min(1,(best+1)/32.0);
        for(int i=0;i<32;i++){double a=low+(high-low)/3,b=high-(high-low)/3;if(distanceAt(p0,p1,old,next,a)<distanceAt(p0,p1,old,next,b))high=b;else low=a;}
        return Math.sqrt(Math.min(min,distanceAt(p0,p1,old,next,(low+high)/2)));
    }
    /** One at-most45degree horizontal outward deflection, never reflected, accelerated, or aimed at the shooter. */
    public static Vector deflect(Vector velocity,Vector at,Vector center,Vector shooter,UUID identity) {
        if(velocity==null||at==null||center==null||shooter==null||identity==null||!velocity.finite()||!at.finite()||!center.finite()||!shooter.finite()
                ||velocity.lengthSquared()>MAX_ARROW_SPEED*MAX_ARROW_SPEED||Math.hypot(velocity.x,velocity.z)<.03
                ||velocity.dot(center.subtract(at))<=0||velocity.dot(shooter.subtract(at))>1e-10)return null;
        Vector left=rotate(velocity,TURN),right=rotate(velocity,-TURN);
        if(left==null||right==null)return null;
        double before=horizontal(at.add(velocity).subtract(center)),a=horizontal(at.add(left).subtract(center)),b=horizontal(at.add(right).subtract(center));
        Vector result=Math.abs(a-b)>1e-12?(a>b?left:right):((identity.getLeastSignificantBits()&1)==0?left:right);
        if(horizontal(at.add(result).subtract(center))<=before+1e-12||result.dot(shooter.subtract(at))>1e-10||result.dot(velocity)<=0)return null;
        return result;
    }
    private static double horizontal(Vector v) { return v.x*v.x+v.z*v.z; }
    private static Vector rotate(Vector v,double angle) {
        double s=Math.sin(angle),c=Math.cos(angle);Vector r=new Vector(v.x*c-v.z*s,v.y,v.x*s+v.z*c);
        for(int i=0;i<=8;i++) {if(horizontal(r)<=horizontal(v)&&r.lengthSquared()<=v.lengthSquared())return r;r=new Vector(Math.nextAfter(r.x,0),v.y,Math.nextAfter(r.z,0));}
        return null;
    }
}

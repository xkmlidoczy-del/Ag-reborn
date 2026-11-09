package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW141 reviewed prototype bounds. No world, input, target or payment authority lives here. */
public final class ThunderNetRules {
    public static final int MAX_TARGETS = SkillTuning.integer("thunder_net", "max_targets"), SCAN_LIMIT=64, HOLD_THRESHOLD=8, HOLD_TICKS = SkillTuning.integer("thunder_net", "duration_ticks"), PULSE_INTERVAL=20, RECEIPT_TICKS=100;
    public static final double RANGE = SkillTuning.number("thunder_net", "reach"), ANCHOR_RADIUS=12, AIM_DEGREES=20;
    private static final BigDecimal CAP=new BigDecimal("1e20");
    private static final ContinuousHighPowerGrowth.Curve TAP_COST=ContinuousHighPowerGrowth.curve("10","10","10","10","1");
    private static final ContinuousHighPowerGrowth.Curve HOLD_COST=ContinuousHighPowerGrowth.curve("3","3","3","3","0.1");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN=ContinuousHighPowerGrowth.curve("40","40","40","40","4");
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public Point subtract(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public double squared(){return x*x+y*y+z*z;}
        public double dot(Point p){return x*p.x+y*p.y+z*p.z;}
    }
    private ThunderNetRules(){}
    public static double tapCost(BigDecimal q){return TAP_COST.value(q).doubleValue();}
    public static double pulseCost(BigDecimal q){return HOLD_COST.value(q).doubleValue();}
    public static BigDecimal theoreticalCooldown(BigDecimal q){return COOLDOWN.value(q);}
    public static int cooldownTicks(BigDecimal q){return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));}
    /** Raw per-target amounts are decimal and capped after multiplication, before native witness conversion. */
    public static BigDecimal damage(BigDecimal d,boolean hold){
        if(d==null||d.precision()>64||Math.abs((long)d.scale())>256||d.signum()<=0||d.compareTo(CAP)>0)return null;
        return d.multiply(new BigDecimal(hold?"0.25":"2.5")).min(CAP).stripTrailingZeros();
    }
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-RECEIPT_TICKS-HOLD_TICKS-HOLD_THRESHOLD;}
    public static boolean held(long start,long now){return clock(start)&&clock(now)&&now>=start&&now-start>=HOLD_THRESHOLD;}
    public static boolean expired(long start,long now){return !clock(start)||!clock(now)||now<start||now-start>=HOLD_THRESHOLD+HOLD_TICKS;}
    public static boolean receiptLive(long issued,long now){return clock(issued)&&clock(now)&&now>=issued&&now-issued<RECEIPT_TICKS;}
    public static boolean pulseReady(long last,long now){return clock(now)&&(last<0||clock(last)&&now>=last&&now-last>=PULSE_INTERVAL);}
    public static boolean inside(Point p,Point center,double radius){return p!=null&&center!=null&&p.finite()&&center.finite()&&Double.isFinite(radius)&&radius>=0&&p.subtract(center).squared()<=radius*radius;}
    /** Aim-cone half-angle is explicitly20deg; server chooses closest visible eligible target within it. */
    public static boolean aimed(Point origin,Point look,Point target){
        if(origin==null||look==null||target==null||!origin.finite()||!look.finite()||!target.finite()||Math.abs(look.squared()-1)>1e-6)return false;
        Point to=target.subtract(origin);double distance=to.squared();
        return distance>1e-12&&distance<=RANGE*RANGE&&to.dot(look)>=Math.sqrt(distance)*Math.cos(Math.toRadians(AIM_DEGREES));
    }
}

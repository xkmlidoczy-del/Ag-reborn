package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW311 provisional economics; physical limits do not disappear at LV5. */
public final class DisplacedProjectionRules {
    public static final double RANGE = SkillTuning.number("displaced_projection", "reach"), SIDE_OFFSET=1.5, SPEED = SkillTuning.number("displaced_projection", "speed");
    public static final int FLIGHT_TICKS = SkillTuning.integer("displaced_projection", "duration_ticks"), CAST_FENCE=8, REQUEST_FENCE=4;
    private static final ContinuousHighPowerGrowth.Curve CP=
            ContinuousHighPowerGrowth.curve("8","4","2",".5",".05");
    private static final ContinuousHighPowerGrowth.Curve CD=
            ContinuousHighPowerGrowth.curve("40","20","10","4","1");
    private DisplacedProjectionRules(){}
    public static double cpCost(BigDecimal q){return CP.value(q).doubleValue();}
    public static int cooldownTicks(BigDecimal q){return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q));}
    public static BigDecimal damage(BigDecimal d){
        if(d==null||d.signum()<=0)return BigDecimal.ZERO;
        return d.min(new BigDecimal("1e20"));
    }
    public record Point(double x,double y,double z){
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
                &&Math.abs(x)<=30_000_000&&Math.abs(z)<=30_000_000&&Math.abs(y)<=30_000_000;}
        public double distance(Point b){return Math.hypot(Math.hypot(x-b.x,y-b.y),z-b.z);}
    }
    /** One fixed caster-right side, no enemy tracking, rotation search or alternate landing. */
    public static Point side(Point target,double yaw){
        if(target==null||!target.finite()||!Double.isFinite(yaw))return null;
        double radians=Math.toRadians(Math.IEEEremainder(yaw,360));
        Point result=new Point(target.x-Math.cos(radians)*SIDE_OFFSET,target.y,
                target.z-Math.sin(radians)*SIDE_OFFSET);
        return result.finite()?result:null;
    }
    /** The advertised bound includes caster-to-emitter distance and the remaining initial flight. */
    public static boolean path(Point caster,Point emitter,Point endpoint){
        return caster!=null&&emitter!=null&&endpoint!=null&&caster.finite()&&emitter.finite()&&endpoint.finite()
                &&caster.distance(emitter)+emitter.distance(endpoint)<=RANGE;
    }
}

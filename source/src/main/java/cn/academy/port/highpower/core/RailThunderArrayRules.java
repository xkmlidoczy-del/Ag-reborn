package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW151 bounded, deterministic geometry, clocks and a single immutable whole-cast budget. */
public final class RailThunderArrayRules {
    public static final int AXES=3, ROUNDS=3, RAYS=9, SCAN_LIMIT=Integer.MAX_VALUE, MAX_TARGETS = SkillTuning.integer("rail_thunder_array", "max_targets");
    public static final int MARK_INTERVAL=4, PREPARATION_TICKS=200, HOLD_THRESHOLD=8, HOLD_TICKS = SkillTuning.integer("rail_thunder_array", "duration_ticks"),
            PULSE_INTERVAL=20, ROUND_INTERVAL=22, ENTITY_INTERVAL=20, POSTCAST_TICKS=4;
    public static final double RANGE = SkillTuning.number("rail_thunder_array", "reach"), CORRIDOR_RADIUS=2, MOVEMENT_LIMIT=1;
    private static final BigDecimal CAP=new BigDecimal("1e20");
    private static final int[] CP_NUMERATORS={349525,349525,349526};
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public Point subtract(Point b){return new Point(x-b.x,y-b.y,z-b.z);}
        public double dot(Point b){return x*b.x+y*b.y+z*b.z;}
        public double squared(){return dot(this);}
    }
    private RailThunderArrayRules(){}
    /** The cast retains exactly4D. Individual emissions are admitted by the closed native amount cap. */
    public static BigDecimal budget(BigDecimal damage){
        if(damage==null||damage.precision()>64||Math.abs((long)damage.scale())>256||damage.signum()<=0||damage.compareTo(CAP)>0)return null;
        return damage.multiply(BigDecimal.valueOf(4));
    }
    /** Fixed original slots, floor each preceding share, exact remainder in the final slot. */
    public static List<BigDecimal> split(BigDecimal total,int slots){
        if(total==null||total.precision()>68||Math.abs((long)total.scale())>256||total.signum()<0||total.compareTo(CAP.multiply(BigDecimal.valueOf(4)))>0||slots<1||slots>MAX_TARGETS)throw new IllegalArgumentException("NEW151 bounded allocation");
        BigDecimal share=total.divide(BigDecimal.valueOf(slots),Math.max(6,total.scale()),RoundingMode.DOWN);
        List<BigDecimal> result=new ArrayList<>(slots);
        for(int i=0;i<slots-1;i++)result.add(share);
        result.add(total.subtract(share.multiply(BigDecimal.valueOf(slots-1))));
        return List.copyOf(result);
    }
    public static double pulseCost(int pulse){if(pulse<0||pulse>=ROUNDS)throw new IllegalArgumentException("NEW151 pulse");return Math.scalb((double)CP_NUMERATORS[pulse],-20);}
    public static int rayTick(int slot){if(slot<0||slot>=RAYS)throw new IllegalArgumentException("NEW151 ray");return slot/AXES*ROUND_INTERVAL+slot%AXES;}
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-PREPARATION_TICKS-HOLD_TICKS-HOLD_THRESHOLD-POSTCAST_TICKS;}
    public static boolean elapsed(long start,long now,int interval){return clock(start)&&clock(now)&&now>=start&&now-start>=interval;}
    public static boolean preparationExpired(long start,long now){return !clock(start)||!clock(now)||now<start||elapsed(start,now,PREPARATION_TICKS);}
    public static boolean unit(Point v){return v!=null&&v.finite()&&Math.abs(v.squared()-1)<=1e-6;}
    /** Only virtually identical directions are duplicates: no broad minimum-angle gameplay constraint. */
    public static boolean distinct(Point direction,List<Point> existing){
        if(!unit(direction)||existing==null||existing.size()>=AXES)return false;
        for(Point old:existing)if(!unit(old)||direction.subtract(old).squared()<1e-8)return false;
        return true;
    }
    /** World-coordinate rounding may overshoot a nominal96m endpoint. Only move inward by bounded ULPs. */
    public static Point endpoint(Point origin,Point axis){
        if(origin==null||!origin.finite()||!unit(axis))return null;
        Point end=new Point(origin.x+axis.x*RANGE,origin.y+axis.y*RANGE,origin.z+axis.z*RANGE);
        for(int i=0;i<=8;i++){
            double distance=end.subtract(origin).squared();
            if(end.finite()&&distance>0&&distance<=RANGE*RANGE)return end;
            end=new Point(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));
        }
        return null;
    }
    public static boolean inside(Point p,Point origin,double radius){return p!=null&&origin!=null&&p.finite()&&origin.finite()&&Double.isFinite(radius)&&radius>=0&&p.subtract(origin).squared()<=radius*radius;}
    /** Finite corridor is a radius2 capsule around the committed96m axis, also bounded by the96m sphere. */
    public static boolean corridor(Point p,Point origin,Point axis){
        if(!unit(axis)||!inside(p,origin,RANGE))return false;
        Point offset=p.subtract(origin);double projection=offset.dot(axis);
        if(projection<0||projection>RANGE)return false;
        return Math.max(0,offset.squared()-projection*projection)<=CORRIDOR_RADIUS*CORRIDOR_RADIUS;
    }
}

package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW211 has two separately payable native emissions, never an aggregated damage transaction. */
public final class DualPointRules {
    public static final int MARK_TICKS = SkillTuning.integer("dual_point", "duration_ticks"), MARK_INTERVAL=4, SCAN_LIMIT=64, EMISSIONS=2;
    public static final double RANGE = SkillTuning.number("dual_point", "reach"), RADIUS=.035, HAND_SIDE=.32, HAND_FORWARD=.35, HAND_DOWN=.28;
    private static final ContinuousHighPowerGrowth.Curve CP=ContinuousHighPowerGrowth.curve("10","5","2",".5",".1");
    private DualPointRules() {}
    public static BigDecimal damage(BigDecimal d){return CombatAmounts.requirePositiveAmount(d,"NEW211 D").multiply(new BigDecimal(".65"));}
    public static double cost(BigDecimal position,int emissions){
        if(emissions<1||emissions>EMISSIONS)throw new IllegalArgumentException("NEW211 emission count");
        return CP.value(position).multiply(BigDecimal.valueOf(emissions)).doubleValue();
    }
    public static int cooldown(int arcCooldown){return Math.max(MARK_INTERVAL,arcCooldown);}
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-MARK_TICKS-4;}
    public static boolean expired(long start,long now){return !clock(start)||!clock(now)||now<start||now-start>=MARK_TICKS;}
    public static boolean markReady(long last,long now){return clock(now)&&(last<0||clock(last)&&now>=last&&now-last>=MARK_INTERVAL);}
    public record Point(double x,double y,double z){
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public double distanceSquared(Point p){return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z);}
    }
    public static boolean within(Point origin,Point endpoint){return origin!=null&&endpoint!=null&&origin.finite()&&endpoint.finite()&&origin.distanceSquared(endpoint)<=RANGE*RANGE;}
    /** World-coordinate addition can round a nominal 28 m endpoint outwards. Never widen the range. */
    public static Point endpoint(Point origin,Point direction){
        if(origin==null||direction==null||!origin.finite()||!direction.finite())return null;
        double squared=direction.x*direction.x+direction.y*direction.y+direction.z*direction.z;
        if(Math.abs(squared-1)>1e-6)return null;
        Point end=new Point(origin.x+direction.x*RANGE,origin.y+direction.y*RANGE,origin.z+direction.z*RANGE);
        for(int i=0;i<=8;i++){
            if(within(origin,end)&&end.distanceSquared(origin)>0)return end;
            end=new Point(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));
        }
        return null;
    }
    /** Real focusing points remain beside the two hands, including vertical-look singularities. */
    public static Point origin(Point eye,Point look,int slot){
        if(eye==null||look==null||!eye.finite()||!look.finite()||slot<0||slot>=EMISSIONS)return null;
        double length=Math.sqrt(look.x*look.x+look.y*look.y+look.z*look.z);
        if(!Double.isFinite(length)||length<.5)return null;
        double x=look.x/length,y=look.y/length,z=look.z/length,flat=Math.hypot(x,z);
        double rightX=flat<1e-8?1:z/flat,rightZ=flat<1e-8?0:-x/flat,side=(slot==0?-1:1)*HAND_SIDE;
        return new Point(eye.x+x*HAND_FORWARD+rightX*side,eye.y+y*HAND_FORWARD-HAND_DOWN,eye.z+z*HAND_FORWARD+rightZ*side);
    }
}

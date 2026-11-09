package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Pure NEW431 economics and closed geometry; native identity and collisions use GameTest. */
public final class TrajectoryReturnRulesRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-12,message+": "+actual);}
    private static void rejects(Runnable call,String message){boolean rejected=false;try{call.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);}
    public static void main(String[] args){
        double[] cp={18,9,4.5,1.125,.1125};int[] cd={60,30,16,6,1};
        for(int i=0;i<5;i++){BigDecimal q=BigDecimal.valueOf(i+1);near(TrajectoryReturnRules.cpCost(q),cp[i],"one whole-cast CP anchor");check(TrajectoryReturnRules.cooldownTicks(q)==cd[i],"independent native CD anchor");}
        double previous=cp[0];int previousCd=cd[0];
        for(int i=101;i<=500;i++){
            BigDecimal q=BigDecimal.valueOf(i,2);double cost=TrajectoryReturnRules.cpCost(q);int cooldown=TrajectoryReturnRules.cooldownTicks(q);
            check(Double.isFinite(cost)&&cost>0&&cost<previous,"positive continuous decreasing cast cost");
            check(cooldown>=1&&cooldown<=previousCd,"only final cooldown rounds upward");previous=cost;previousCd=cooldown;
        }
        check(TrajectoryReturnRules.cpCost(new BigDecimal("3.10001"))<TrajectoryReturnRules.cpCost(new BigDecimal("3.10000")),"growth is not percentage quantized");
        for(String value:new String[]{"1e-30",".1","1","1e7","1e13","5e18"}){
            BigDecimal d=new BigDecimal(value),perArrow=TrajectoryReturnRules.damage(d);
            check(perArrow.compareTo(d.multiply(BigDecimal.TWO))==0,"one actual arrow owns whole2D");
            check(perArrow.multiply(BigDecimal.valueOf(4)).compareTo(d.multiply(BigDecimal.valueOf(8)))==0,"four arrows reach8D without redistribution");
        }
        check(TrajectoryReturnRules.damage(new BigDecimal("1e20")).compareTo(CombatAmounts.CAP)==0,"final per-arrow cap");
        check(TrajectoryReturnRules.damage(null).signum()==0&&TrajectoryReturnRules.damage(BigDecimal.ZERO).signum()==0&&TrajectoryReturnRules.damage(BigDecimal.ONE.negate()).signum()==0,"nonpositive source produces no amount");
        rejects(()->TrajectoryReturnRules.damage(new BigDecimal("1e1000")),"unbounded scale fails closed before multiplication");
        rejects(()->TrajectoryReturnRules.damage(new BigDecimal("1."+"1".repeat(513))),"unbounded precision fails closed");
        var origin=new TrajectoryReturnRules.Point(0,0,0);var forward=new TrajectoryReturnRules.Point(0,0,1);
        check(TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(.75,0,1)),"inclusive provisional .75 corridor");
        check(!TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(Math.nextUp(.75),0,1)),"radius does not silently expand");
        check(TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(0,0,TrajectoryReturnRules.RANGE)),"exact configured endpoint");
        check(!TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(.1,0,TrajectoryReturnRules.RANGE)),"lateral width cannot extend actual configured range");
        check(!TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(0,0,Math.nextUp(TrajectoryReturnRules.RANGE))),"outside range refused");
        check(!TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(0,0,-.00001)),"behind the server look ray refused");
        check(!TrajectoryReturnRules.corridor(origin,new TrajectoryReturnRules.Point(0,0,0),origin),"zero look cannot create a corridor");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            check(!TrajectoryReturnRules.flight(invalid),"nonfinite actual flight budget refuses");
            check(!TrajectoryReturnRules.corridor(origin,forward,new TrajectoryReturnRules.Point(invalid,0,0)),"nonfinite candidate refuses");
        }
        check(TrajectoryReturnRules.flight(0)&&TrajectoryReturnRules.flight(TrajectoryReturnRules.RANGE)&&!TrajectoryReturnRules.flight(Math.nextUp(TrajectoryReturnRules.RANGE))&&!TrajectoryReturnRules.flight(-1),"cumulative actual flight budget includes the configured boundary");
        for(double coordinate:new double[]{0,1e6,-1e6,29999900,-29999900}){
            var start=new TrajectoryReturnRules.Point(coordinate,100,coordinate);
            for(double angle:new double[]{0,.1,.5,1,2,3,4,5,6}){
                var axis=new TrajectoryReturnRules.Point(Math.sin(angle),0,Math.cos(angle));
                var end=TrajectoryReturnRules.endpoint(start,axis);
                check(end!=null&&TrajectoryReturnRules.within(start,end),"world coordinate rounding keeps40m endpoint within budget");
            }
        }
        check(TrajectoryReturnRules.MAX_ARROWS==4&&TrajectoryReturnRules.SCAN_LIMIT==64,"distinct admission and total broadphase bounds");
        check(TrajectoryReturnRules.FLIGHT_TICKS==20&&TrajectoryReturnRules.QUOTA_TICKS==20&&TrajectoryReturnRules.CAST_FENCE==4,"physical fences independent of q5 one-tick cooldown");
        check(TrajectoryReturnRules.AIR_HAMMER_RADIUS==.035&&TrajectoryReturnRules.MAX_SPEED==8,"thin air hammer and bounded ordinary native velocity");
        System.out.println("TrajectoryReturnRulesRegressionTest passed "+checks+" checks");
    }
}

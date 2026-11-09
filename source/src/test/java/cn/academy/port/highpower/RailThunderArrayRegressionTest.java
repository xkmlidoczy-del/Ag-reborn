package cn.academy.port.highpower;

import cn.academy.port.highpower.core.RailThunderArrayRules;
import cn.academy.port.highpower.core.RailThunderArrayRules.Point;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Standalone pure specification. Uses exact decimal/double witnesses rather than a rounded epsilon sum. */
public final class RailThunderArrayRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void exact(BigDecimal a,BigDecimal b,String why){check(a.compareTo(b)==0,why+": "+a+" != "+b);}
    private static BigDecimal sum(List<BigDecimal> values){return values.stream().reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static void fails(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        check(RailThunderArrayRules.RANGE==96&&RailThunderArrayRules.MAX_TARGETS==16&&RailThunderArrayRules.SCAN_LIMIT==64,"finite query and geometry bounds");
        for(String input:List.of("1","5e18","1e20","1.000000000000001","0.000000000000000000001","99999999999999999999.999")){
            BigDecimal d=new BigDecimal(input),total=RailThunderArrayRules.budget(d);
            exact(total,d.multiply(BigDecimal.valueOf(4)),"whole cast equals4D including synthetic cumulative total above final-hit cap");
            List<BigDecimal> rays=RailThunderArrayRules.split(total,9);exact(sum(rays),total,"nine shares conserve every digit");
            for(int i=0;i<8;i++)exact(rays.get(i),rays.get(0),"first eight identical floored allocations");
            check(rays.get(8).compareTo(rays.get(0))>=0,"last ray owns exact rounding remainder");
            for(int count=1;count<=16;count++){
                BigDecimal allocated=BigDecimal.ZERO;
                for(BigDecimal pulse:RailThunderArrayRules.split(total,3)){
                    List<BigDecimal> targets=RailThunderArrayRules.split(pulse,count);exact(sum(targets),pulse,"original frozen target slots conserve pulse");
                    allocated=allocated.add(sum(targets));
                    if(count>1)check(sum(targets.subList(1,count)).compareTo(pulse)<0||targets.getFirst().signum()==0,"dropping original slot never redistributes it");
                }
                exact(allocated,total,"all three target matrices exactly conserve whole budget");
            }
            boolean immutable=false;try{rays.set(0,BigDecimal.ZERO);}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"shares immutable");
        }
        check(RailThunderArrayRules.budget(null)==null&&RailThunderArrayRules.budget(BigDecimal.ZERO)==null&&RailThunderArrayRules.budget(BigDecimal.ONE.negate())==null,"invalid damage rejected");
        check(RailThunderArrayRules.budget(new BigDecimal("1e21"))==null&&RailThunderArrayRules.budget(new BigDecimal("1e-1000000000"))==null,"unbounded input representation rejected");
        fails(()->RailThunderArrayRules.split(BigDecimal.ONE,0),"zero slots rejected");fails(()->RailThunderArrayRules.split(BigDecimal.ONE,17),"17 targets rejected");
        exact(sum(RailThunderArrayRules.split(BigDecimal.ZERO,16)),BigDecimal.ZERO,"zero allocations conserved without emission");
        double cost=0;for(int pulse=0;pulse<3;pulse++)cost+=RailThunderArrayRules.pulseCost(pulse);check(cost==1,"dyadic costs sum exactly one");
        exact(new BigDecimal(RailThunderArrayRules.pulseCost(0)).add(new BigDecimal(RailThunderArrayRules.pulseCost(1))).add(new BigDecimal(RailThunderArrayRules.pulseCost(2))),BigDecimal.ONE,"binary64 values themselves sum exactly one");
        Random random=new Random(151);
        for(int i=0;i<20000;i++){
            double pool=i==0?100000:i==1?Math.nextUp(1):1+random.nextDouble()*99999;
            double left=pool;BigDecimal precise=new BigDecimal(pool);
            for(int pulse=0;pulse<3;pulse++){
                double debit=RailThunderArrayRules.pulseCost(pulse);left-=debit;precise=precise.subtract(new BigDecimal(debit));
                exact(new BigDecimal(left),precise,"each arbitrary valid pool subtraction is exact");
            }
            exact(new BigDecimal(left),new BigDecimal(pool).subtract(BigDecimal.ONE),"finite three pulse pool debit exactly one");
        }
        int[] ticks={0,1,2,22,23,24,44,45,46};for(int i=0;i<9;i++)check(RailThunderArrayRules.rayTick(i)==ticks[i],"actual per-axis schedule");
        check(!RailThunderArrayRules.elapsed(100,107,8)&&RailThunderArrayRules.elapsed(100,108,8),"server hold threshold eight");
        check(!RailThunderArrayRules.preparationExpired(100,299)&&RailThunderArrayRules.preparationExpired(100,300),"200tick preparation expiry");
        check(RailThunderArrayRules.preparationExpired(100,99)&&!RailThunderArrayRules.clock(Long.MAX_VALUE)&&!RailThunderArrayRules.clock(-1),"rollback and overflow fail closed");
        Point origin=new Point(0,0,0),axis=new Point(0,0,1);
        check(RailThunderArrayRules.distinct(axis,List.of()),"first finite unit axis");
        check(!RailThunderArrayRules.distinct(axis,List.of(axis)),"duplicate axis rejected");
        double a=.001;check(RailThunderArrayRules.distinct(new Point(Math.sin(a),0,Math.cos(a)),List.of(axis)),"tiny meaningful angle accepted");
        check(!RailThunderArrayRules.distinct(new Point(Double.NaN,0,1),List.of())&&!RailThunderArrayRules.distinct(new Point(0,0,0),List.of()),"invalid directions rejected");
        check(RailThunderArrayRules.corridor(new Point(2,0,10),origin,axis)&&!RailThunderArrayRules.corridor(new Point(2.00001,0,10),origin,axis),"radius2 boundary");
        check(RailThunderArrayRules.corridor(new Point(0,0,96),origin,axis)&&!RailThunderArrayRules.corridor(new Point(0,0,96.001),origin,axis),"range96 boundary");
        check(!RailThunderArrayRules.corridor(new Point(0,0,-1),origin,axis),"behind axis not selected");
        check(RailThunderArrayRules.inside(new Point(1,0,0),origin,1)&&!RailThunderArrayRules.inside(new Point(1.000001,0,0),origin,1),"one metre movement limit");
        for(double coordinate:new double[]{0,8902083.5,-10460878.5,29999900,-29999900})for(int yaw=-180;yaw<=180;yaw++)for(int pitch:new int[]{-89,-45,0,17,75,89}){
            double y=Math.toRadians(yaw),x=Math.toRadians(pitch);Point direction=new Point(-Math.sin(y)*Math.cos(x),-Math.sin(x),Math.cos(y)*Math.cos(x));
            double length=Math.sqrt(direction.squared());direction=new Point(direction.x()/length,direction.y()/length,direction.z()/length);
            Point start=new Point(coordinate,63.62,-coordinate),end=RailThunderArrayRules.endpoint(start,direction);
            check(end!=null&&end.subtract(start).squared()<=96*96,"large-coordinate endpoint strictly inside96m without tolerance widening");
        }
        check(RailThunderArrayRules.endpoint(origin,new Point(Double.NaN,0,1))==null,"invalid endpoint direction rejected");
        System.out.println("RailThunderArrayRegressionTest: "+checks+" checks passed");
    }
}

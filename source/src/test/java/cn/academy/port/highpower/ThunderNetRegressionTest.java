package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.highpower.core.ThunderNetRules;
import cn.academy.port.highpower.core.ThunderNetRules.Point;
import java.math.BigDecimal;

/** Standalone NEW141 decimal, bounded clocks, fixed sphere and server-aim specification. */
public final class ThunderNetRegressionTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void exact(BigDecimal expected,BigDecimal actual,String why){check(expected.compareTo(actual)==0,why+": "+actual);}
    public static void main(String[] args){
        check(ThunderNetRules.MAX_TARGETS==6&&ThunderNetRules.SCAN_LIMIT==64,"bounded target work");
        check(ThunderNetRules.tapCost(new BigDecimal("4"))==10&&ThunderNetRules.tapCost(new BigDecimal("5"))==1,"tap anchors10/1");
        check(ThunderNetRules.pulseCost(new BigDecimal("4"))==3&&ThunderNetRules.pulseCost(new BigDecimal("5"))==.1,"pulse anchors3/.1");
        check(ThunderNetRules.cooldownTicks(new BigDecimal("4"))==40&&ThunderNetRules.cooldownTicks(new BigDecimal("5"))==4,"cooldown40/4");
        for(int i=0;i<=1000;i++){
            BigDecimal q=new BigDecimal("4").add(BigDecimal.valueOf(i,3)),d=ContinuousHighPowerGrowth.sample(q).damage();
            exact(d.multiply(new BigDecimal("2.5")),ThunderNetRules.damage(d,false),"exacttap");exact(d.multiply(new BigDecimal(".25")),ThunderNetRules.damage(d,true),"exacthold");
            check(ThunderNetRules.tapCost(q)>=1&&ThunderNetRules.tapCost(q)<=10,"tapbound");check(ThunderNetRules.pulseCost(q)>=.1&&ThunderNetRules.pulseCost(q)<=3,"pulsebound");
            check(ThunderNetRules.cooldownTicks(q)>=4&&ThunderNetRules.cooldownTicks(q)<=40,"cooldownbound");
            if(i>0){BigDecimal previous=q.subtract(new BigDecimal(".001"));check(ThunderNetRules.tapCost(q)<=ThunderNetRules.tapCost(previous),"continuouslyfalling cost");}
        }
        exact(new BigDecimal("1e20"),ThunderNetRules.damage(new BigDecimal("1e20"),false),"postmultiplycap");
        check(ThunderNetRules.damage(new BigDecimal("1e21"),false)==null&&ThunderNetRules.damage(BigDecimal.ZERO,false)==null&&ThunderNetRules.damage(null,true)==null,"invalidamountsrefused");
        check(ThunderNetRules.damage(new BigDecimal("1e-1000000000"),true)==null,"maliciousrepresentationbounded");
        check(!ThunderNetRules.held(100,107)&&ThunderNetRules.held(100,108),"serverthreshold8");
        check(!ThunderNetRules.expired(100,167)&&ThunderNetRules.expired(100,168),"hold60afterthreshold");
        check(!ThunderNetRules.pulseReady(100,119)&&ThunderNetRules.pulseReady(100,120),"minimum20");
        check(ThunderNetRules.receiptLive(100,199)&&!ThunderNetRules.receiptLive(100,200)&&!ThunderNetRules.receiptLive(100,99),"receipt100exclusiveandrollback");
        check(!ThunderNetRules.clock(Long.MAX_VALUE)&&ThunderNetRules.expired(0,-1),"overflowfailclosed");
        Point zero=new Point(0,0,0),look=new Point(0,0,1);
        check(ThunderNetRules.aimed(zero,look,new Point(0,0,ThunderNetRules.RANGE)),"configured boundary");check(!ThunderNetRules.aimed(zero,look,new Point(0,0,Math.nextUp(ThunderNetRules.RANGE))),"beyondrange");
        check(!ThunderNetRules.aimed(zero,look,new Point(10,0,10)),"45degoffaxis");check(!ThunderNetRules.aimed(zero,look,new Point(0,0,-1)),"behind");
        check(!ThunderNetRules.aimed(zero,new Point(0,0,2),new Point(0,0,1)),"unnormalizedlook");
        check(ThunderNetRules.inside(new Point(0,12,0),zero,12)&&!ThunderNetRules.inside(new Point(0,12.001,0),zero,12),"actual3dsphere");
        check(!ThunderNetRules.inside(new Point(Double.NaN,0,0),zero,12),"finitegeometry");
        System.out.println("ThunderNetRegressionTest: "+checks+" checks passed");
    }
}

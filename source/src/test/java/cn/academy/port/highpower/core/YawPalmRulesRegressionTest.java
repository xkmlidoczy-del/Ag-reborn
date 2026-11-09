package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Independent conservation, control-domain and native-physics limits for NEW423. */
public final class YawPalmRulesRegressionTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static YawPalmRules.Point p(double x, double y, double z) { return new YawPalmRules.Point(x,y,z); }
    public static void main(String[] args) {
        double[] costs = {10,5,2,.5,.1}; int[] cooldowns = {10,5,4,4,4};
        for (int i=0;i<5;i++) {
            BigDecimal q = BigDecimal.valueOf(i+1);
            check(YawPalmRules.cpCost(q)==costs[i], "exact CP anchor");
            check(YawPalmRules.cooldownTicks(q)==cooldowns[i], "native cadence plus four-tick fence");
        }
        double prior = 10; int priorCd = 10;
        for (int i=101;i<=500;i++) {
            BigDecimal q = BigDecimal.valueOf(i,2);
            double cp = YawPalmRules.cpCost(q); int cd = YawPalmRules.cooldownTicks(q);
            check(cp>0 && cp<prior && cd>=4 && cd<=priorCd, "continuous economics never erase physical fence");
            prior=cp; priorCd=cd;
        }
        for (String text : new String[]{"0","2e-256","4e-256",".1","1","3","10000000","123456789012345.6789012345678901234","1e20","1e21"}) {
            BigDecimal d=new BigDecimal(text), whole=d.min(CombatAmounts.CAP).multiply(new BigDecimal(".5"));
            for(int count=1;count<=3;count++) {
                List<BigDecimal> shares=YawPalmRules.shares(d,count);
                check(shares.size()==count && shares.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(whole)==0,
                        "hostile shares sum exactly to half D: "+text+"/"+count);
                for(BigDecimal share:shares) check(CombatAmounts.requireAmount(share,"share").compareTo(share)==0,"representable share");
                check(shares.getLast().subtract(shares.getFirst()).compareTo(BigDecimal.valueOf(count-1).scaleByPowerOfTen(-256))<=0,
                        "only final hostile owns remainder");
                if(count>1) {
                    BigDecimal frozen=shares.getLast();
                    check(frozen.compareTo(whole)<=0 && frozen.equals(shares.getLast()),"discarding another frozen share never redistributes");
                }
            }
        }
        check(YawPalmRules.shares(new BigDecimal("2e-256"),3).equals(List.of(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1e-256"))),"last frozen hostile retains quantum");
        check(YawPalmRules.shares(BigDecimal.TEN,0).isEmpty(),"item-only cast has no damage allocations");
        check(YawPalmRules.damage(null).signum()==0 && YawPalmRules.damage(BigDecimal.ONE.negate()).signum()==0,"no negative damage");
        rejects(()->YawPalmRules.shares(BigDecimal.ONE,4),"fourth object cannot create a share");
        rejects(()->YawPalmRules.shares(BigDecimal.ONE,-1),"negative count refuses");
        rejects(()->YawPalmRules.damage(new BigDecimal("1e-256")),"unrepresentable half refuses instead of rounding budget");
        rejects(()->YawPalmRules.damage(new BigDecimal("1e-257")),"subquantum input refuses");
        rejects(()->YawPalmRules.damage(new BigDecimal("1e257")),"huge exponent refuses before expansion");
        try { YawPalmRules.shares(BigDecimal.TEN,3).set(0,BigDecimal.TEN); throw new AssertionError("mutable shares"); }
        catch(UnsupportedOperationException expected) { checks++; }

        var right=YawPalmRules.side(0,false); var left=YawPalmRules.side(0,true);
        check(right.x()==-1 && right.y()==0 && right.z()==0 && left.x()==1 && left.y()==0 && left.z()==0,"yaw-zero right is west, shift-left east");
        check(YawPalmRules.side(Float.NaN,false)==null,"nonfinite yaw refuses");
        for(int yaw=-720;yaw<=720;yaw++) {
            var r=YawPalmRules.side(yaw,false);var l=YawPalmRules.side(yaw,true);
            check(r.y()==0 && l.y()==0 && r.x()==-l.x() && r.z()==-l.z(),"horizontal mirrored side independent of pitch");
        }
        var itemBefore=p(.1,17,.1);var itemAfter=YawPalmRules.itemResult(itemBefore,right);
        check(itemAfter!=null && itemAfter.x()==.1-.25 && itemAfter.y()==17 && itemAfter.z()==.1,"item additive side preserves other components and Y exactly");
        check(YawPalmRules.itemResult(p(-.25,-99,0),right).equals(p(-.5,-99,0)),"inclusive item cap preserves Y");
        check(YawPalmRules.itemResult(p(-.250001,0,0),right)==null,"over-cap impulse refuses instead of clamping momentum");
        check(YawPalmRules.itemResult(p(0,0,0),p(0,1,0))==null,"vertical side never supported");
        check(YawPalmRules.itemResult(p(Double.NaN,0,0),right)==null,"nonfinite velocity refuses");
        double strength=YawPalmRules.KNOCKBACK;
        var before=p(.1,-.6,.2);
        var airborne=YawPalmRules.nativeResult(before,right,strength,.25,false);
        check(airborne!=null && airborne.x()==.1/2-strength*.75 && airborne.y()==-.6 && airborne.z()==.2/2,"airborne native result retains native existing-axis damping and unchanged Y");
        var grounded=YawPalmRules.nativeResult(before,right,strength,.25,true);
        check(grounded!=null && grounded.y()==Math.min(.4,-.6/2+strength*.75),"grounded native Y uses reviewed formula only");
        check(YawPalmRules.nativeResult(before,right,strength,1,true).equals(before),"full native resistance preserves all motion");
        check(YawPalmRules.nativeResult(before,right,0,0,true).equals(before),"listener-zero strength cannot fabricate motion");
        check(YawPalmRules.nativeResult(p(-1,0,0),right,strength,0,false)==null,"unaffordable native result refuses without clamp");
        check(YawPalmRules.nativeResult(p(0,Math.nextUp(1.0),0),right,strength,0,false)==null,"living vertical control domain is conservative");
        check(YawPalmRules.nativeResult(before,right,Math.nextUp(strength),0,false)==null,"amplified strength refuses");
        check(YawPalmRules.nativeResult(before,right,strength,-.1,false)==null && YawPalmRules.nativeResult(before,right,strength,Double.NaN,false)==null,"invalid resistance refuses");

        check(YawPalmRules.within(p(0,0,0),p(0,0,YawPalmRules.RANGE)),"inclusive configured boundary");
        check(!YawPalmRules.within(p(0,0,0),p(0,0,Math.nextUp(YawPalmRules.RANGE))),"no outward range epsilon");
        Random random=new Random(423);
        for(int i=0;i<2000;i++) {
            var start=p(random.nextDouble()*59_999_000-29_999_500,random.nextDouble()*1000-500,random.nextDouble()*59_999_000-29_999_500);
            double x=random.nextDouble()*2-1,y=random.nextDouble()*2-1,z=random.nextDouble()*2-1,n=Math.sqrt(x*x+y*y+z*z);
            var end=YawPalmRules.endpoint(start,p(x/n,y/n,z/n));
            check(end!=null && YawPalmRules.within(start,end) && start.distanceSquared(end)>35.999,"rounded world endpoint never extends range");
        }
        check(YawPalmRules.clock(0) && !YawPalmRules.clock(-1) && !YawPalmRules.clock(Long.MAX_VALUE-11999),"clock bounds");
        check(YawPalmRules.MAX_TARGETS==3 && YawPalmRules.SCAN_LIMIT==64 && YawPalmRules.RADIUS==.035 && YawPalmRules.VISUAL_TICKS==12,"bounded mixed selection contract");
        System.out.println("YawPalmRulesRegressionTest passed "+checks+" checks");
    }
}

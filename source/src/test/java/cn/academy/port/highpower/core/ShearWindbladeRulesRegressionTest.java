package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Random;

/** Independent whole-cast conservation and continuous economic boundary checks. */
public final class ShearWindbladeRulesRegressionTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        BigDecimal[] amounts = {BigDecimal.ONE, new BigDecimal("0.1"), new BigDecimal("1E-256"),
                new BigDecimal("123456789.01234567890123456789"), new BigDecimal("5E18"), CombatAmounts.CAP};
        for (BigDecimal d : amounts) for (int n=1;n<=4;n++) verify(d,n);
        var random = new Random(421);
        for(int i=0;i<2000;i++) verify(BigDecimal.valueOf(1+random.nextInt(1000000000),random.nextInt(24)),1+random.nextInt(4));
        check(ShearWindbladeRules.shares(BigDecimal.ONE,0).isEmpty(),"empty allocation");
        for(int n:new int[]{-1,5,Integer.MAX_VALUE}) {
            boolean rejected=false;try {ShearWindbladeRules.shares(BigDecimal.ONE,n);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid slot count refuses");
        }
        String[] cp={"8","8","3",".5",".05"};int[] cd={30,30,15,5,1};
        double last=Double.POSITIVE_INFINITY;
        for(int i=0;i<=4000;i++) {
            BigDecimal q=BigDecimal.ONE.add(BigDecimal.valueOf(i,3));double cost=ShearWindbladeRules.cpCost(q);
            check(Double.isFinite(cost)&&cost>0&&cost<=last+1E-12,"finite monotone continuous CP");last=cost;
            check(ShearWindbladeRules.cooldownTicks(q)>=1,"native tick cooldown finite");
        }
        for(int i=0;i<5;i++) {
            check(Math.abs(ShearWindbladeRules.cpCost(BigDecimal.valueOf(i+1))-Double.parseDouble(cp[i]))<1E-12,"same-tier CP anchor");
            check(ShearWindbladeRules.cooldownTicks(BigDecimal.valueOf(i+1))==cd[i],"cooldown anchors are ticks, not seconds");
        }
        check(ShearWindbladeRules.CAST_FENCE==4&&ShearWindbladeRules.SECOND_DELAY==2&&ShearWindbladeRules.LIFETIME==16,"separate finite physical budgets");
        check(!ShearWindbladeRules.ready(20,19,4)&&!ShearWindbladeRules.ready(20,23,4)&&ShearWindbladeRules.ready(20,24,4),"clock rollback and fence");
        System.out.println("PASS "+checks+" NEW421 exact allocation and economic checks");
    }
    private static void verify(BigDecimal d,int count) {
        BigDecimal whole=ShearWindbladeRules.damage(d),sum=BigDecimal.ZERO;
        check(whole.signum()>=0&&whole.compareTo(d.multiply(new BigDecimal("1.5")))<=0&&whole.compareTo(CombatAmounts.CAP)<=0,"whole conserved before partition");
        var shares=ShearWindbladeRules.shares(d,count);check(shares.size()==count,"fixed cardinality");
        for(BigDecimal share:shares){CombatAmounts.requireAmount(share,"test share");check(share.signum()>=0,"nonnegative share");sum=sum.add(share);}
        check(sum.compareTo(whole)==0,"exact whole sum incl last remainder");
        if(count>1){BigDecimal without=whole.subtract(shares.getFirst());check(without.compareTo(whole)<=0,"lost opportunity cannot enlarge remainder");}
    }
}

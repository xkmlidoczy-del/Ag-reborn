package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Exact attack allocation and physical bounds; not native combat or visual acceptance. */
public final class CrossSubstitutionRulesRegressionTest {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        double[] cp={12,6,3,.75,.075}; int[] cd={60,30,16,6,1};
        for(int i=0;i<5;i++){
            BigDecimal q=BigDecimal.valueOf(i+1);
            check(Math.abs(CrossSubstitutionRules.cpCost(q)-cp[i])<1e-12,"continuous CP anchor "+q);
            check(CrossSubstitutionRules.cooldownTicks(q)==cd[i],"native tick cooldown anchor "+q);
        }
        double previous=Double.POSITIVE_INFINITY;
        for(int i=0;i<=4000;i++){
            BigDecimal q=BigDecimal.ONE.add(BigDecimal.valueOf(i,3));
            double cost=CrossSubstitutionRules.cpCost(q);
            check(Double.isFinite(cost)&&cost>0&&cost<=previous+1e-12,"CP remains positive and monotone "+q);
            double first=CrossSubstitutionRules.cpShare(cost,0), second=CrossSubstitutionRules.cpShare(cost,1), last=CrossSubstitutionRules.cpShare(cost,2);
            check(first>0&&first==second&&last>0,"all committed shares positive");
            check(Double.doubleToLongBits((first+second)+last)==Double.doubleToLongBits(cost),"frozen CP shares sum exactly to representable total");
            previous=cost;
        }
        for(String raw:new String[]{".00000000000000000000001","1","10000000","5e18","1e20"}){
            BigDecimal d=new BigDecimal(raw), share=CrossSubstitutionRules.damage(d);
            check(share.compareTo(d.multiply(new BigDecimal(".5")))==0,"one exact .5D share "+raw);
            check(share.multiply(BigDecimal.valueOf(3)).compareTo(d.multiply(new BigDecimal("1.5")))==0,"three fixed shares conserve1.5D "+raw);
        }
        check(CrossSubstitutionRules.damage(new BigDecimal("3e20")).compareTo(new BigDecimal("1e20"))==0,"per-emission final cap discards excess");
        check(CrossSubstitutionRules.damage(null).signum()==0&&CrossSubstitutionRules.damage(BigDecimal.ONE.negate()).signum()==0,"invalid negative source cannot mint damage");
        for(int slot=0;slot<3;slot++){
            check(CrossSubstitutionRules.launchTick(slot)==slot*4,"native0/4/8 schedule");
            check(CrossSubstitutionRules.usesA(slot)==(slot!=1),"fixed A/B/A allocation");
        }
        for(int bad:new int[]{-1,3,Integer.MAX_VALUE}){
            boolean rejected=false;try{CrossSubstitutionRules.launchTick(bad);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"no fourth or negative share");
        }
        check(CrossSubstitutionRules.path(12,CrossSubstitutionRules.RANGE-12)&&CrossSubstitutionRules.path(0,CrossSubstitutionRules.RANGE),"exact cumulative boundary");
        check(!CrossSubstitutionRules.path(0,Math.nextUp(CrossSubstitutionRules.RANGE))&&!CrossSubstitutionRules.path(-1,1),"outside or negative path refused");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            check(!CrossSubstitutionRules.path(bad,1)&&!CrossSubstitutionRules.path(1,bad),"nonfinite path refused");
        }
        check(CrossSubstitutionRules.PREPARATION_TICKS==cn.academy.port.core.SkillTuning.number("cross_substitution", "duration_ticks")&&CrossSubstitutionRules.FLIGHT_TICKS==20&&CrossSubstitutionRules.CAST_FENCE==8,"physical fences independent of q");
        System.out.println("CrossSubstitutionRulesRegressionTest: "+checks+" checks passed");
    }
}

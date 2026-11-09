package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Random;

/** Benefit versus the same ordinary category shield, plus legacy/quantum/overkill conservation. */
public final class SpecialistShieldEfficiencyRegressionTest {
    private static int checks;
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void eq(BigDecimal actual,BigDecimal expected,String why){check(actual.compareTo(expected)==0,why+" actual="+actual+" expected="+expected);}
    private static ExactCombatState state(String h,String s){return new ExactCombatState(n(h),n(h),n(s),n(s),false);}
    private static void directional(ExactCombatState before,BigDecimal damage,boolean relief){
        var legacy=relief?CirculationReliefRules.absorb(before,damage):VectorParryRules.absorb(before,damage);
        var old=relief?CirculationReliefRules.absorb(before,damage,false):VectorParryRules.absorb(before,damage,false);
        check(old.equals(legacy),"explicit legacy mode remains byte-for-byte same result");
        var plan=relief?CirculationReliefRules.absorb(before,damage,true):VectorParryRules.absorb(before,damage,true);
        BigDecimal residual=damage.subtract(plan.prevented()).subtract(plan.absorbed());
        var finalState=CategoryShieldRules.finish(before,plan.state(),residual);
        check(finalState.equals(plan.state()),"base fallback does not reapply saved damage or double-spend S");
        eq(plan.absorbed(),before.shield().subtract(finalState.shield()),"absorbed still means actual existing S debit");
        eq(plan.absorbed().add(residual).add(plan.prevented()),damage,"shield + HP-side remainder + prevention conserve incoming damage before HP clipping");
        check(finalState.health().compareTo(legacy.state().health())>=0&&finalState.shield().compareTo(legacy.state().shield())>=0,"casting cannot cost extra HP/S compared with no reduction");
        check(plan.prevented().compareTo(damage.multiply(n(".25")))<=0,"quantization never exceeds stated twenty-five percent");
    }
    public static void main(String[] args){
        var full=state("100","20");
        var p=VectorParryRules.absorb(full,n("8"),true);eq(p.state().shield(),n("14"),"front parry spends six S instead of ordinary eight");eq(p.prevented(),n("2"),"front parry prevents exactly25%");
        var r=CirculationReliefRules.absorb(full,n("8"),true);check(r.equals(p),"side relief gets the same benefit through its independent admission");
        var a=InertialAnchorRules.absorbFall(full,n("8"),true);eq(a.state().shield(),n("16"),"anchor landing spends four S instead of ordinary eight");eq(a.prevented(),n("4"),"anchor landing prevents exactly50%");
        var broken=VectorParryRules.absorb(state("100","1"),n("8"),true);eq(broken.state().health(),n("95"),"breaking hit discounts before one S then five HP");
        var landing=InertialAnchorRules.absorbFall(state("100","2"),n("8"),true);eq(landing.state().health(),n("98"),"landing discounts before two S then two HP");
        check(InertialAnchorRules.absorbFall(full,n("8"),false).equals(InertialAnchorRules.absorbFall(full,n("8"))),"legacy anchor unchanged");
        check(VectorParryRules.absorb(state("100","0"),n("8"),true).prevented().signum()==0,"no shield cannot gain unadmitted parry reduction");
        check(InertialAnchorRules.absorbFall(state("100","0"),n("8"),true).prevented().signum()==0,"no shield cannot gain unadmitted landing reduction");
        for(BigDecimal damage:new BigDecimal[]{BigDecimal.ZERO,n("1e-256"),n("3e-256"),n("1.01"),CombatAmounts.CAP}){
            directional(full,damage,false);directional(full,damage,true);
            var plan=InertialAnchorRules.absorbFall(full,damage,true);var after=CategoryShieldRules.finish(full,plan.state(),damage.subtract(plan.prevented()).subtract(plan.absorbed()));
            check(after.equals(plan.state()),"anchor fallback preserves exact reduced final state, even lethal overkill");
            check(plan.prevented().compareTo(damage.multiply(n(".5")))<=0,"landing quantization never overstates fifty percent");
        }
        Random random=new Random(343422);
        for(int i=0;i<12000;i++){
            var before=state("1000",Integer.toString(1+random.nextInt(1000)));BigDecimal damage=BigDecimal.valueOf(random.nextInt(1500000),3);
            directional(before,damage,false);directional(before,damage,true);
        }
        System.out.println("PASS SpecialistShieldEfficiencyRegressionTest "+checks);
    }
}

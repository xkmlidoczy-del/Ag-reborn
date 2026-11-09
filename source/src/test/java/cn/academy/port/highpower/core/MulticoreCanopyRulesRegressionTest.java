package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import static cn.academy.port.highpower.core.MulticoreCanopyRules.*;

/** Exact conserved plans, immutable lifetime, physical cadence and irreversible bounded work. */
public final class MulticoreCanopyRulesRegressionTest {
    private static int checks;
    public static void main(String[] args){
        String[] deploy={"4","2","1",".2",".01"},fire={"80","40","20","4",".2"},cd={"80","50","30","15","1"};
        for(int q=1;q<=5;q++){
            BigDecimal position=BigDecimal.valueOf(q);equal(theoreticalDeploymentCost(position),d(deploy[q-1]),"deployment endpoint");
            equal(theoreticalFireCost(position),d(fire[q-1]),"fire endpoint");equal(theoreticalCooldown(position),d(cd[q-1]),"cooldown endpoint");
        }
        check(theoreticalFireCost(d("3.5")).compareTo(d("20"))<0&&theoreticalFireCost(d("3.5")).compareTo(d("4"))>0,"continuous intermediate cost");
        check(cooldownTicks(d("3.5"))==ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(d("3.5"))),"only engine boundary rounds");
        equal(damage(d("1234567890123456789.123456789")),d("4938271560493827156.493827156"),"exact4D");
        equal(damage(CombatAmounts.CAP),CombatAmounts.CAP,"cap before dividing");
        for(BigDecimal total:new BigDecimal[]{d("1"),d(".000000000000000000000000001"),CombatAmounts.CAP})for(int slots=1;slots<=16;slots++){
            var shares=shares(total,slots);equal(shares.stream().reduce(BigDecimal.ZERO,BigDecimal::add),total,"entire frozen plan conserved");
            check(shares.getLast().compareTo(shares.getFirst())>=0,"last deterministic slot owns remainder");
            if(slots>1)check(shares.getFirst().compareTo(total)<0,"lost slot cannot give another full budget");
        }
        check(shares(d("1"),0).isEmpty(),"empty free opportunity");reject(()->shares(d("1"),17),"slot bound");
        check(remaining(100,100)==LIFETIME&&remaining(100,99+LIFETIME)==1&&remaining(100,100+LIFETIME)==0,"immutable first-press lifetime");
        check(remaining(100,99)==0&&remaining(Long.MAX_VALUE,Long.MAX_VALUE)==0,"corrupt clocks refuse");
        check(!ready(100,104,5)&&ready(100,105,5)&&!ready(105,105,5),"one held emission every physical5ticks");
        check(ready(100,199,5)&&!ready(199,199,5),"lag allows one opportunity only when timestamp advances");
        check(!ready(100,119,20)&&ready(100,120,20),"physical20tick fire fence");
        check(!expired(100,109,10)&&expired(100,110,10),"ten invisible ticks retire irreversibly");
        check(!expired(100,111,12)&&expired(100,112,12),"twelve tick warning");
        Work work=new Work();for(int i=0;i<MAX_PREFIXES;i++)check(work.prefix(),"finite ray prefixes");
        check(!work.prefix()&&!work.valid()&&!work.cell(),"exhaustion permanently refuses later work");
        Work entities=new Work();check(entities.entities(MAX_ENTITY_VISITS)&&!entities.entities(1)&&!entities.valid(),"entity visits bounded before filtering");
        Work invalid=new Work();check(!invalid.entities(-1)&&!invalid.valid(),"corrupt work refuses");
        System.out.println("MulticoreCanopyRulesRegressionTest: "+checks+" checks passed");
    }
    private static BigDecimal d(String value){return new BigDecimal(value);}
    private static void equal(BigDecimal actual,BigDecimal expected,String why){check(actual.compareTo(expected)==0,why);}
    private static void reject(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
}

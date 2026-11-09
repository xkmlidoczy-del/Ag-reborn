package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import static cn.academy.port.highpower.core.ParticleLaminateRules.*;

/** Standalone exact budget, irreversible slot assignment, work limits and physical-clock tests. */
public final class ParticleLaminateRulesRegressionTest {
    private static int checks;
    public static void main(String[] args){
        String[] placement={"8","4","2",".5",".05"},pulse={"24","12","6","2",".1"};
        for(int q=1;q<=5;q++){
            BigDecimal position=BigDecimal.valueOf(q);equal(theoreticalPlacementCost(position),d(placement[q-1]),"placement endpoint");
            equal(theoreticalPulseCost(position,false),d(pulse[q-1]),"pulse endpoint");equal(theoreticalPulseCost(position,true),d(pulse[q-1]).multiply(d(".32")),"low mode cost endpoint");
        }
        check(theoreticalPlacementCost(d("3.5")).compareTo(d("2"))<0&&theoreticalPlacementCost(d("3.5")).compareTo(d(".5"))>0,"smooth intermediate q");
        equal(damage(d("1234567890123456789.123456789"),false),d("3086419725308641972.8086419725"),"exact 2.5D");
        equal(damage(d("1234567890123456789.123456789"),true),d("987654312098765431.2987654312"),"exact .8D");
        equal(damage(CombatAmounts.CAP,false),CombatAmounts.CAP,"discard cap excess before partition");
        for(BigDecimal amount:new BigDecimal[]{d("1"),d(".000000000000000000000000001"),CombatAmounts.CAP})for(int count=1;count<=3;count++){
            var shares=shares(amount,count);equal(shares.stream().reduce(BigDecimal.ZERO,BigDecimal::add),amount,"whole pulse conserved exactly");
            check(shares.getLast().compareTo(shares.getFirst())>=0,"deterministic remainder belongs to last frozen identity");
            if(count>1){BigDecimal surviving=shares.getFirst();check(surviving.compareTo(amount)<0,"canceled neighbor share cannot be redistributed");}
        }
        check(shares(d("1"),0).isEmpty(),"empty opportunity has no allocations");reject(()->shares(d("1"),4),"four first-body targets impossible");
        check(opportunity(100,105)==-1,"six ticks of advance warning");
        for(int pulseAt=0;pulseAt<5;pulseAt++)check(opportunity(100,106+20*pulseAt)==pulseAt,"fixed five windows");
        check(opportunity(100,199)==4&&opportunity(100,200)==-1,"no sixth opportunity at expiry");
        check(opportunity(100,167)==3,"lag jumps directly to current window, never catch-up");
        check(!ready(106,125,20)&&ready(106,126,20),"persisted fence is independent of input identity");
        check(!ready(106,105,20)&&!ready(Long.MAX_VALUE,120,20),"clock rollback and malformed future fences refuse");
        check(remaining(100,-1,299)==1&&remaining(100,-1,300)==0,"first anchor exact immutable expiry");
        check(remaining(100,290,299)==1,"late arming cannot refresh anchor lifetime");
        check(expired(100,140,HOLD_TICKS)&&!expired(100,139,HOLD_TICKS),"unpaid holds finite");
        Work work=new Work();for(int i=0;i<MAX_PREFIXES;i++)check(work.prefix(),"bounded guard prefix");check(!work.prefix()&&!work.valid()&&!work.cell(),"prefix exhaustion irrevocable");
        Work entities=new Work();check(entities.entities(MAX_ENTITY_VISITS)&&!entities.entities(1)&&!entities.valid(),"whole pulse entity visits bounded");
        Work corrupt=new Work();check(!corrupt.entities(-1)&&!corrupt.valid(),"malformed accounting refuses");
        System.out.println("Particle laminate rules: "+checks+" checks passed");
    }
    private static BigDecimal d(String value){return new BigDecimal(value);}
    private static void equal(BigDecimal a,BigDecimal b,String why){check(a.compareTo(b)==0,why+": "+a+" / "+b);}
    private static void reject(Runnable call,String why){try{call.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}

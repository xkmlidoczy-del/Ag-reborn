package cn.academy.port.highpower.core;
import java.math.BigDecimal;
/** Pure physical/economic policy; native player integration is covered separately. */
public final class BeamStepRulesRegressionTest {
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result)throw new AssertionError(message); }
    public static void main(String[] args) {
        double[] costs={8,4,2,.5,.05}; int[] cooldowns={60,30,16,10,1};
        for(int i=0;i<5;i++) {
            check(BeamStepRules.cpCost(BigDecimal.valueOf(i+1))==costs[i],"independent CP anchor");
            check(BeamStepRules.cooldownTicks(BigDecimal.valueOf(i+1))==cooldowns[i],"independent cooldown anchor");
        }
        double previous=8;
        for(int i=101;i<=500;i++) { double cost=BeamStepRules.cpCost(BigDecimal.valueOf(i,2)); check(cost<previous,"smooth economic progression"); previous=cost; }
        for(double y=-20;y<.36;y+=.001) { double result=BeamStepRules.assistedY(y); check(result<=.36&&result>y&&result-y<=.720000000000001,"bounded additive Y"); }
        check(Math.abs(BeamStepRules.assistedY(-2)+1.28)<1e-12,"severe descent remains descending");
        check(Double.isNaN(BeamStepRules.assistedY(.36))&&Double.isNaN(BeamStepRules.assistedY(Double.NaN)),"zero/nonfinite addition rejected");
        long[] grants={}; grants=BeamStepRules.append(grants,100);
        check(!BeamStepRules.quotaReady(grants,107)&&BeamStepRules.quotaReady(grants,108),"eight-tick physical fence");
        grants=BeamStepRules.append(grants,108); grants=BeamStepRules.append(grants,116);
        check(!BeamStepRules.quotaReady(grants,124)&&!BeamStepRules.quotaReady(grants,131),"three receipts in rolling32 window");
        check(BeamStepRules.quotaReady(grants,132),"first receipt expires exactly at32");
        grants=BeamStepRules.append(grants,132); check(grants.length==3&&grants[0]==108,"bounded persistent record");
        check(!BeamStepRules.quotaReady(new long[]{3,3},40)&&!BeamStepRules.quotaReady(new long[]{-1},40)&&!BeamStepRules.quotaReady(null,40),"malformed records rejected");
        check(!BeamStepRules.quotaReady(new long[]{41},40),"clock rewind fails closed");
        check(BeamStepRules.fresh(10,12,10)&&BeamStepRules.fresh(10,12,11)&&!BeamStepRules.fresh(10,12,12),"strict two-tick grant TTL");
        System.out.println("BeamStepRulesRegressionTest passed "+checks+" checks");
    }
}

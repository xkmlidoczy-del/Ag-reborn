package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Independent JVM checks for continuous balance, real-tick quotas and hostile saved clocks. */
public final class BeamEtchingRulesRegressionTest {
    private static int assertions;
    public static void main(String[] args) {
        balance(); cadence(); quotaAcrossCasts(); corruptClocks(); geometry();
        System.out.println("PASS " + assertions + " NEW213 beam-etching rule assertions");
    }
    private static void balance() {
        double[] cp={1,.5,.2,.05,.005}; int[] cooldown={10,8,6,4,1};
        for (int level=1;level<=5;level++) {
            BigDecimal q=BigDecimal.valueOf(level);
            truth(BeamEtchingRules.cost(q)==cp[level-1],"exact per-block CP anchor");
            truth(BeamEtchingRules.cooldown(q)==cooldown[level-1],"exact finish cooldown anchor");
        }
        double previous=1;
        for(int i=1;i<=400;i++) {
            BigDecimal q=BigDecimal.ONE.add(BigDecimal.valueOf(i,2)); double current=BeamEtchingRules.cost(q);
            truth(current<previous,"CP is continuously decreasing inside levels"); previous=current;
            truth(BeamEtchingRules.cooldown(q)==ContinuousHighPowerGrowth.ceilCooldownTicks(BeamEtchingRules.theoreticalCooldown(q)),"only scheduled cooldown rounds");
        }
        truth(BeamEtchingRules.MAX_MARKS==16&&BeamEtchingRules.ATTEMPTS_PER_TICK==2&&BeamEtchingRules.HOLD_TICKS==100,"bounded work constants");
    }
    private static void cadence() {
        var timing=BeamEtchingRules.Timing.initial(40);
        truth(timing.previewReady(40)&&timing.acquireReady(40),"first preparation is available");
        timing=timing.acquire(40).preview(40);
        truth(!timing.acquireReady(40)&&timing.acquireReady(41),"one mark per native tick");
        truth(!timing.previewReady(43)&&timing.previewReady(44),"free preview maximum four-tick cadence");
        var reloaded=new BeamEtchingRules.Timing(timing.observed(),timing.previewAt(),timing.acquireAt(),timing.attemptAt(),timing.attempts(),timing.stopUntil());
        truth(!reloaded.previewReady(40)&&!reloaded.acquireReady(40),"abort and reload cannot erase preparation cadence");
        truth(!BeamEtchingRules.expired(40,139)&&BeamEtchingRules.expired(40,140),"hold ends at exact hundredth tick");
        truth(!BeamEtchingRules.ready(40,59,20)&&BeamEtchingRules.ready(40,60,20),"productive XP twenty-tick fence");
    }
    private static void quotaAcrossCasts() {
        var timing=BeamEtchingRules.Timing.initial(100).attempt(100).attempt(100);
        truth(!timing.attemptReady(100),"third attempt refused even when first two failed");
        rejects(()->BeamEtchingRules.Timing.initial(100).attempt(100).attempt(100).attempt(100));
        var recast=timing.finish(100,1);
        truth(!recast.attemptReady(100),"finish/recast cannot reset global attempts");
        truth(recast.attemptReady(101)&&recast.stopUntil()==101,"new actual tick grants quota at cooldown boundary");
        recast=recast.attempt(101);
        truth(recast.attempts()==1&&recast.attemptReady(101),"only current actual frame count carries forward");
        var advanced=recast.attempt(400);
        truth(advanced.attempts()==1,"missed ticks never accumulate burst allowance");
        truth(!advanced.current(399)&&!advanced.attemptReady(399),"clock rollback cannot refill allowance");
    }
    private static void corruptClocks() {
        rejects(()->new BeamEtchingRules.Timing(-1,-1,-1,-1,0,0));
        rejects(()->new BeamEtchingRules.Timing(5,6,-1,-1,0,0));
        rejects(()->new BeamEtchingRules.Timing(5,-2,-1,-1,0,0));
        rejects(()->new BeamEtchingRules.Timing(5,-1,-1,5,3,0));
        rejects(()->new BeamEtchingRules.Timing(5,-1,-1,-1,1,0));
        rejects(()->new BeamEtchingRules.Timing(5,-1,-1,5,0,0));
        rejects(()->new BeamEtchingRules.Timing(5,-1,-1,-1,0,16));
        truth(!BeamEtchingRules.clock(Long.MAX_VALUE)&&BeamEtchingRules.expired(5,4),"overflow and rollback fail closed");
    }
    private static void geometry() {
        var zero=new BeamEtchingRules.Point(0,0,0);
        truth(BeamEtchingRules.within(zero,new BeamEtchingRules.Point(0,0,BeamEtchingRules.MAX_RANGE)),"exact configured distance admitted");
        truth(!BeamEtchingRules.within(zero,new BeamEtchingRules.Point(0,0,Math.nextUp(BeamEtchingRules.MAX_RANGE))),"no range epsilon benefit");
        truth(!BeamEtchingRules.within(zero,new BeamEtchingRules.Point(Double.NaN,0,0)),"invalid coordinates refused");
        for (double base:new double[]{0,1234567.1,-29999983.75}) {
            var origin=new BeamEtchingRules.Point(base,100,base);
            var endpoint=BeamEtchingRules.endpoint(origin,new BeamEtchingRules.Point(Math.sqrt(.5),0,Math.sqrt(.5)));
            truth(endpoint!=null&&BeamEtchingRules.within(origin,endpoint),"rounded world endpoint stays inside range");
        }
        truth(BeamEtchingRules.endpoint(zero,zero)==null,"zero direction refused");
    }
    private static void truth(boolean yes,String why) { assertions++; if(!yes)throw new AssertionError(why); }
    private static void rejects(Runnable action) { assertions++; try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("invalid operation accepted"); }
}

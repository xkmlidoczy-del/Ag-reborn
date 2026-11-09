package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Independent JVM checks for NEW233's true range, actual-tick focus and inherited balance. */
public final class PointMeltdownRulesRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        balance(); preview(); focus(); corruptClocks(); range(); roundedEndpoints();
        System.out.println("PASS " + assertions + " NEW233 point-meltdown rule assertions");
    }

    private static void balance() {
        double[] costs = {1,.5,.2,.05,.005};
        int[] cooldowns = {10,8,6,4,1};
        for (int level=1; level<=5; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            truth(PointMeltdownRules.cost(q)==costs[level-1], "exact inherited CP anchor");
            truth(PointMeltdownRules.cooldown(q)==cooldowns[level-1], "exact inherited cooldown anchor");
        }
        double previousCost = PointMeltdownRules.cost(BigDecimal.ONE);
        BigDecimal previousCooldown = PointMeltdownRules.theoreticalCooldown(BigDecimal.ONE);
        for (int i=1; i<=400; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i,2));
            double cost = PointMeltdownRules.cost(q);
            BigDecimal cooldown = PointMeltdownRules.theoreticalCooldown(q);
            truth(cost==BeamEtchingRules.cost(q), "CP agrees with NEW213 at every sampled fractional q");
            truth(cooldown.compareTo(BeamEtchingRules.theoreticalCooldown(q))==0, "theoretical cooldown reuses NEW213 continuously");
            truth(PointMeltdownRules.cooldown(q)==BeamEtchingRules.cooldown(q), "scheduled cooldown agrees with NEW213");
            truth(cost<previousCost && cooldown.compareTo(previousCooldown)<0, "both balance curves decrease between levels");
            truth(PointMeltdownRules.cooldown(q)==ContinuousHighPowerGrowth.ceilCooldownTicks(cooldown), "only engine cooldown scheduling rounds");
            previousCost = cost; previousCooldown = cooldown;
        }
        var q = new BigDecimal("3.1234567");
        var nearby = new BigDecimal("3.1234568");
        truth(PointMeltdownRules.cost(q)>PointMeltdownRules.cost(nearby), "CP retains sub-percentage progress");
        truth(PointMeltdownRules.theoreticalCooldown(q).compareTo(PointMeltdownRules.theoreticalCooldown(nearby))>0,
                "theoretical cooldown retains sub-percentage progress");
    }

    private static void preview() {
        truth(PointMeltdownRules.MAX_RANGE==cn.academy.port.core.SkillTuning.number("point_meltdown", "reach") && PointMeltdownRules.PREVIEW_TICKS==100
                && PointMeltdownRules.FOCUS_TICKS==10 && PointMeltdownRules.PREVIEW_INTERVAL==2, "approved bounded constants");
        truth(!PointMeltdownRules.expired(40,40) && !PointMeltdownRules.expired(40,139), "preview admits ticks zero through ninety-nine");
        truth(PointMeltdownRules.expired(40,140) && PointMeltdownRules.expired(40,1000), "preview ends at its actual hundredth tick");
        truth(PointMeltdownRules.ready(-1,40,2), "first preview is immediately available");
        truth(!PointMeltdownRules.ready(40,40,2) && !PointMeltdownRules.ready(40,41,2)
                && PointMeltdownRules.ready(40,42,2), "preview cannot exceed two-tick cadence");
        truth(PointMeltdownRules.ready(40,1000,2) && !PointMeltdownRules.ready(1000,1000,2), "missed preview ticks do not allow duplicate sends");
        truth(!PointMeltdownRules.ready(40,39,2) && !PointMeltdownRules.ready(40,42,0), "preview rollback and invalid intervals fail closed");
    }

    private static void focus() {
        var focus = PointMeltdownRules.Focus.initial(500);
        truth(focus.ticks()==0 && !focus.complete(), "release itself grants no focus");
        truth(focus.advance(500)==focus, "release-tick duplicates grant no focus");
        for (int i=1; i<=PointMeltdownRules.FOCUS_TICKS; i++) {
            truth(focus.current(500+i), "next actual server tick is admitted");
            focus = focus.advance(500+i);
            truth(focus.ticks()==i && focus.lastTick()==500+i, "each observed native tick grants exactly one focus");
            truth(focus.complete()==(i==10), "only the tenth consecutive focus tick completes");
            truth(focus.advance(500+i)==focus, "duplicate callbacks cannot accelerate focus");
        }
        var complete = focus;
        rejects(()->complete.advance(511));
        var partial = PointMeltdownRules.Focus.initial(100).advance(101).advance(102);
        truth(!partial.current(101) && !partial.current(104), "rollback and missing ticks invalidate focus");
        rejects(()->partial.advance(101));
        rejects(()->partial.advance(104));
        rejects(()->partial.advance(1000));
        rejects(()->PointMeltdownRules.Focus.initial(100).advance(110));
        truth(partial.ticks()==2 && !partial.complete(), "rejected callbacks never synthesize accumulated focus");
        var reloaded = new PointMeltdownRules.Focus(partial.started(),partial.lastTick(),partial.ticks());
        truth(reloaded.advance(102).ticks()==2 && reloaded.advance(103).ticks()==3, "saved focus cannot duplicate a tick on reload");
        var lateRelease = PointMeltdownRules.Focus.initial(139);
        for (int tick=140; tick<=149; tick++) lateRelease = lateRelease.advance(tick);
        truth(lateRelease.complete(), "a valid late preview release receives its own ten focus ticks");
    }

    private static void corruptClocks() {
        truth(!PointMeltdownRules.clock(-1) && !PointMeltdownRules.clock(Long.MAX_VALUE), "negative and overflow clocks fail closed");
        truth(PointMeltdownRules.expired(-1,0) && PointMeltdownRules.expired(40,39)
                && PointMeltdownRules.expired(0,Long.MAX_VALUE), "corrupt or reversed preview clocks expire");
        truth(!PointMeltdownRules.ready(-2,40,2) && !PointMeltdownRules.ready(Long.MAX_VALUE,40,2), "invalid previous preview clocks reject");
        rejects(()->PointMeltdownRules.Focus.initial(-1));
        rejects(()->PointMeltdownRules.Focus.initial(Long.MAX_VALUE));
        rejects(()->new PointMeltdownRules.Focus(100,99,0));
        rejects(()->new PointMeltdownRules.Focus(100,100,-1));
        rejects(()->new PointMeltdownRules.Focus(100,111,11));
        rejects(()->new PointMeltdownRules.Focus(100,101,0));
        rejects(()->new PointMeltdownRules.Focus(100,110,1));
        rejects(()->PointMeltdownRules.Focus.initial(100).advance(Long.MAX_VALUE));
    }

    private static void range() {
        var origin = new PointMeltdownRules.Point(0,0,0);
        for (double range : new double[]{Math.nextDown(32.0),32,Math.nextUp(32.0),35.999,Math.nextDown(PointMeltdownRules.MAX_RANGE),PointMeltdownRules.MAX_RANGE}) {
            truth(PointMeltdownRules.within(origin,new PointMeltdownRules.Point(0,0,range)), "32 m is not a hidden cutoff; the exact configured boundary is admitted");
        }
        truth(!PointMeltdownRules.within(origin,new PointMeltdownRules.Point(0,0,Math.nextUp(PointMeltdownRules.MAX_RANGE))), "one ULP beyond the configured range receives no epsilon");
        truth(!PointMeltdownRules.within(origin,new PointMeltdownRules.Point(PointMeltdownRules.MAX_RANGE,1,0)), "range is a sphere rather than per-axis bounds");
        truth(!PointMeltdownRules.within(origin,new PointMeltdownRules.Point(Double.NaN,0,0))
                && !PointMeltdownRules.within(origin,new PointMeltdownRules.Point(0,Double.POSITIVE_INFINITY,0)), "nonfinite points are refused");
        truth(!PointMeltdownRules.within(null,origin) && !PointMeltdownRules.within(origin,null), "missing points fail closed");
        var large = new PointMeltdownRules.Point(29_999_983.75,100,-29_999_983.75);
        var boundary = new PointMeltdownRules.Point(large.x()+PointMeltdownRules.MAX_RANGE,large.y(),large.z());
        truth(PointMeltdownRules.within(large,boundary), "large-coordinate exact range boundary is admitted");
        truth(!PointMeltdownRules.within(large,new PointMeltdownRules.Point(Math.nextUp(boundary.x()),boundary.y(),boundary.z())),
                "large-coordinate one-ULP overshoot is refused");
    }

    private static void roundedEndpoints() {
        var roundedOrigin = new PointMeltdownRules.Point(29_999_983.75,100,-29_999_983.75);
        var diagonal = new PointMeltdownRules.Point(1/Math.sqrt(2),1/Math.sqrt(2),0);
        var rawEnd = new PointMeltdownRules.Point(roundedOrigin.x()+diagonal.x()*36,
                roundedOrigin.y()+diagonal.y()*36,roundedOrigin.z());
        truth(!PointMeltdownRules.within(roundedOrigin,rawEnd), "world-coordinate addition can genuinely round outside the boundary");
        var inwardEnd = PointMeltdownRules.endpoint(roundedOrigin,diagonal);
        truth(inwardEnd!=null && !rawEnd.equals(inwardEnd) && PointMeltdownRules.within(roundedOrigin,inwardEnd),
                "endpoint corrects a real large-coordinate overshoot inward");
        double[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,0,-1},{1,1,0},{1,1,1},{-2,3,-5},{.1,-.7,.2}};
        for (double base : new double[]{0,1_234_567.1,-29_999_983.75,29_999_983.75}) {
            var origin = new PointMeltdownRules.Point(base,100,-base);
            for (double[] direction : directions) {
                double norm = Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);
                var unit = new PointMeltdownRules.Point(direction[0]/norm,direction[1]/norm,direction[2]/norm);
                var end = PointMeltdownRules.endpoint(origin,unit);
                truth(end!=null && end.finite() && PointMeltdownRules.within(origin,end), "rounded endpoint remains inside the true 36 m limit");
                truth(origin.distanceSquared(end)>35.99999*35.99999, "inward rounding preserves the intended long range");
            }
        }
        var zero = new PointMeltdownRules.Point(0,0,0);
        truth(PointMeltdownRules.endpoint(zero,zero)==null, "zero direction is refused");
        truth(PointMeltdownRules.endpoint(zero,new PointMeltdownRules.Point(2,0,0))==null, "nonunit direction is refused");
        truth(PointMeltdownRules.endpoint(zero,new PointMeltdownRules.Point(Double.NaN,0,0))==null, "invalid direction is refused");
        truth(PointMeltdownRules.endpoint(null,new PointMeltdownRules.Point(1,0,0))==null, "missing origin is refused");
        truth(PointMeltdownRules.endpoint(new PointMeltdownRules.Point(Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE),
                new PointMeltdownRules.Point(1,0,0))==null, "unrepresentable nonzero ray fails closed");
    }

    private static void truth(boolean yes,String why) { assertions++; if(!yes)throw new AssertionError(why); }
    private static void rejects(Runnable action) { assertions++; try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("invalid operation accepted"); }
}

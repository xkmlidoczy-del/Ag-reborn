package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** NEW323 continuous resource curves and exact lifetime/request boundaries. */
public final class RecoveryMarkRulesRegressionTest {
    private static int checks;
    private static void check(boolean valid, String why) {
        checks++; if (!valid) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        double[] cp = {6, 3, 1.5, .375, .025};
        int[] cd = {40, 20, 10, 4, 1};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(RecoveryMarkRules.cpCost(q) == cp[i], "exact CP anchor " + q);
            check(RecoveryMarkRules.theoreticalCooldown(q).compareTo(BigDecimal.valueOf(cd[i])) == 0,
                    "exact theoretical cooldown anchor " + q);
            check(RecoveryMarkRules.cooldownTicks(q) == cd[i], "native cooldown anchor " + q);
        }
        double previousCost = cp[0];
        BigDecimal previousCooldown = BigDecimal.valueOf(cd[0]);
        for (int i = 101; i <= 500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2), cooldown = RecoveryMarkRules.theoreticalCooldown(q);
            double cost = RecoveryMarkRules.cpCost(q);
            check(cost < previousCost && cooldown.compareTo(previousCooldown) < 0, "continuous decreasing economics");
            int ticks = RecoveryMarkRules.cooldownTicks(q);
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0
                    && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0, "single final engine ceiling");
            previousCost = cost; previousCooldown = cooldown;
        }
        BigDecimal step = new BigDecimal(".00001");
        for (int i = 2; i <= 4; i++) {
            BigDecimal q = BigDecimal.valueOf(i);
            double left = (RecoveryMarkRules.cpCost(q) - RecoveryMarkRules.cpCost(q.subtract(step))) / step.doubleValue();
            double right = (RecoveryMarkRules.cpCost(q.add(step)) - RecoveryMarkRules.cpCost(q)) / step.doubleValue();
            check(Math.abs(left - right) < .001, "smooth cost slope through level " + i);
            double cdLeft = RecoveryMarkRules.theoreticalCooldown(q).subtract(RecoveryMarkRules.theoreticalCooldown(q.subtract(step))).divide(step).doubleValue();
            double cdRight = RecoveryMarkRules.theoreticalCooldown(q.add(step)).subtract(RecoveryMarkRules.theoreticalCooldown(q)).divide(step).doubleValue();
            check(Math.abs(cdLeft - cdRight) < .001, "smooth cooldown slope through level " + i);
        }
        check(RecoveryMarkRules.cpCost(new BigDecimal("2.1001")) < RecoveryMarkRules.cpCost(new BigDecimal("2.1000")), "progress never quantizes to percent");
        check(RecoveryMarkRules.cpCost(BigDecimal.ZERO) == 6 && RecoveryMarkRules.cpCost(BigDecimal.TEN) == .025, "bounded clamp");
        check(RecoveryMarkRules.RANGE == cn.academy.port.core.SkillTuning.number("recovery_mark", "reach") && RecoveryMarkRules.MARK_LIFE_TICKS == 1200
                && RecoveryMarkRules.REQUEST_INTERVAL_TICKS == 8, "fixed physical and timing limits");
        check(RecoveryMarkRules.fresh(50, 50) && RecoveryMarkRules.fresh(50, 1249), "live before exact expiry");
        check(!RecoveryMarkRules.fresh(50, 1250) && !RecoveryMarkRules.fresh(50, 49), "expiry and future issue refuse");
        check(!RecoveryMarkRules.fresh(-1, 0) && !RecoveryMarkRules.fresh(0, -1), "invalid clocks refuse");
        check(RecoveryMarkRules.ready(-1, 0, 8) && !RecoveryMarkRules.ready(-2, 100, 8), "only -1 means empty receipt");
        check(!RecoveryMarkRules.ready(100, 107, 8) && RecoveryMarkRules.ready(100, 108, 8), "eight tick fence");
        check(!RecoveryMarkRules.ready(101, 100, 8), "future receipt refuses");
        check(!RecoveryMarkRules.ready(-1, 100, 0) && !RecoveryMarkRules.ready(-1, 100, 1201), "bad fence refuses");
        check(RecoveryMarkRules.clock(Long.MAX_VALUE - 1200) && !RecoveryMarkRules.clock(Long.MAX_VALUE - 1199)
                && !RecoveryMarkRules.clock(-1), "overflow-risk clocks refuse");
        System.out.println("RecoveryMarkRulesRegressionTest passed " + checks + " checks");
    }
}

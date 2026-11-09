package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Standalone policy tests: previews cannot borrow confirm costs or physical fences. */
public final class SafeLandingProbeRulesRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        double[] costs = {8, 4, 2, .5, .05};
        int[] cooldowns = {40, 20, 10, 4, 1};
        for (int i = 0; i < costs.length; i++) {
            var q = BigDecimal.valueOf(i + 1);
            check(SafeLandingProbeRules.cpCost(q) == costs[i], "exact CP anchor at " + q);
            check(SafeLandingProbeRules.theoreticalCooldown(q).compareTo(BigDecimal.valueOf(cooldowns[i])) == 0,
                    "exact theoretical cooldown anchor at " + q);
            check(SafeLandingProbeRules.cooldownTicks(q) == cooldowns[i], "native cooldown anchor at " + q);
        }
        double previousCost = costs[0];
        BigDecimal previousCooldown = BigDecimal.valueOf(cooldowns[0]);
        for (int percent = 101; percent <= 500; percent++) {
            var q = BigDecimal.valueOf(percent, 2);
            double cost = SafeLandingProbeRules.cpCost(q);
            BigDecimal cooldown = SafeLandingProbeRules.theoreticalCooldown(q);
            check(cost < previousCost, "continuous CP descent");
            check(cooldown.compareTo(previousCooldown) < 0, "continuous theoretical cooldown descent");
            check(BigDecimal.valueOf(SafeLandingProbeRules.cooldownTicks(q)).compareTo(cooldown) >= 0,
                    "one native tick ceiling only at commit boundary");
            previousCost = cost; previousCooldown = cooldown;
        }
        check(SafeLandingProbeRules.fresh(50, 50) && SafeLandingProbeRules.fresh(50, 49 + SafeLandingProbeRules.PREVIEW_TICKS), "configured preview lifetime");
        check(!SafeLandingProbeRules.fresh(50, 50 + SafeLandingProbeRules.PREVIEW_TICKS) && !SafeLandingProbeRules.fresh(50, 49), "expired or future preview refuses");
        check(!SafeLandingProbeRules.fresh(-1, 0) && !SafeLandingProbeRules.clock(Long.MAX_VALUE), "clock corruption and overflow refuse");
        check(SafeLandingProbeRules.ready(-1, 0, 4), "empty receipt available");
        check(!SafeLandingProbeRules.ready(-2, 100, 4) && !SafeLandingProbeRules.ready(101, 100, 4), "malformed receipt refuses");
        check(!SafeLandingProbeRules.ready(50, 53, 4) && SafeLandingProbeRules.ready(50, 54, 4), "four-tick preview fence");
        check(!SafeLandingProbeRules.ready(50, 57, 8) && SafeLandingProbeRules.ready(50, 58, 8), "eight-tick movement fence");
        check(!SafeLandingProbeRules.productiveMastery(2.999999, -1, 50), "tiny movement earns no mastery");
        check(SafeLandingProbeRules.productiveMastery(3, -1, 50), "three-meter movement can earn mastery");
        check(!SafeLandingProbeRules.productiveMastery(3, 50, 69)
                && SafeLandingProbeRules.productiveMastery(3, 50, 70), "at most one productive receipt per 20 ticks");
        check(!SafeLandingProbeRules.productiveMastery(Math.nextUp(SafeLandingProbeRules.RANGE), -1, 50)
                && !SafeLandingProbeRules.productiveMastery(Double.NaN, -1, 50), "out-of-range or nonfinite movement refuses");
        check(SafeLandingProbeRules.groundedVerticalMotion(-.0784)
                && SafeLandingProbeRules.groundedVerticalMotion(0), "ordinary native grounded gravity residue remains usable");
        check(!SafeLandingProbeRules.groundedVerticalMotion(-1)
                && !SafeLandingProbeRules.groundedVerticalMotion(.42)
                && !SafeLandingProbeRules.groundedVerticalMotion(Double.NaN), "falls, jumps and malformed velocity refuse");
        System.out.println("SafeLandingProbeRulesRegressionTest passed " + checks + " checks");
    }
}

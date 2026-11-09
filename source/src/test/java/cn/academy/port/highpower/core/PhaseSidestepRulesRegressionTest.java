package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** NEW312 keeps the requested continuous economics and independent fixed physical fences. */
public final class PhaseSidestepRulesRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        double[] costs = {8, 4, 2, .5, .05};
        int[] cooldowns = {40, 20, 10, 4, 1};
        for (int i = 0; i < costs.length; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(PhaseSidestepRules.cpCost(q) == costs[i], "exact CP anchor " + q);
            check(PhaseSidestepRules.theoreticalCooldown(q).compareTo(BigDecimal.valueOf(cooldowns[i])) == 0,
                    "exact theoretical cooldown anchor " + q);
            check(PhaseSidestepRules.cooldownTicks(q) == cooldowns[i], "native cooldown anchor " + q);
        }
        double previousCost = costs[0];
        BigDecimal previousCooldown = BigDecimal.valueOf(cooldowns[0]);
        for (int percent = 101; percent <= 500; percent++) {
            BigDecimal q = BigDecimal.valueOf(percent, 2);
            double cost = PhaseSidestepRules.cpCost(q);
            BigDecimal cooldown = PhaseSidestepRules.theoreticalCooldown(q);
            check(cost < previousCost && cooldown.compareTo(previousCooldown) < 0, "continuous q descent");
            int ticks = PhaseSidestepRules.cooldownTicks(q);
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0
                    && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0, "one final native ceiling");
            previousCost = cost; previousCooldown = cooldown;
        }
        check(PhaseSidestepRules.DISTANCE == 3, "three-meter distance never depends on q");
        check(PhaseSidestepRules.REQUEST_INTERVAL_TICKS == 4, "request work has four-tick bound");
        check(PhaseSidestepRules.MOVEMENT_FENCE_TICKS == 8, "physical movement has eight-tick bound");
        check(PhaseSidestepRules.ready(-1, 0, 4), "empty request receipt admits");
        check(!PhaseSidestepRules.ready(100, 103, 4) && PhaseSidestepRules.ready(100, 104, 4), "request boundary");
        check(!PhaseSidestepRules.ready(100, 107, 8) && PhaseSidestepRules.ready(100, 108, 8), "physical boundary");
        check(!PhaseSidestepRules.ready(-2, 100, 4) && !PhaseSidestepRules.ready(101, 100, 4), "corrupt/future receipt refuses");
        check(!PhaseSidestepRules.clock(-1) && !PhaseSidestepRules.clock(Long.MAX_VALUE), "clock bounds");
        check(!PhaseSidestepRules.ready(-1, 100, 0), "invalid fence refuses");
        System.out.println("PhaseSidestepRulesRegressionTest passed " + checks + " checks");
    }
}

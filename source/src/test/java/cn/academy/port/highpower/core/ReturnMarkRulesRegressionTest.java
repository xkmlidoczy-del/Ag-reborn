package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** NEW322 smooth economics, exact timing boundaries and corrupt/overflow clock refusal. */
public final class ReturnMarkRulesRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        double[] costs = {12, 6, 3, .75, .05};
        int[] cooldowns = {80, 40, 20, 8, 1};
        for (int i = 0; i < costs.length; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(ReturnMarkRules.cpCost(q) == costs[i], "exact CP anchor " + q);
            check(ReturnMarkRules.theoreticalCooldown(q).compareTo(BigDecimal.valueOf(cooldowns[i])) == 0,
                    "exact theoretical cooldown anchor " + q);
            check(ReturnMarkRules.cooldownTicks(q) == cooldowns[i], "native cooldown anchor " + q);
        }
        double previousCost = costs[0];
        BigDecimal previousCooldown = BigDecimal.valueOf(cooldowns[0]);
        for (int percent = 101; percent <= 500; percent++) {
            BigDecimal q = BigDecimal.valueOf(percent, 2);
            double cost = ReturnMarkRules.cpCost(q);
            BigDecimal cooldown = ReturnMarkRules.theoreticalCooldown(q);
            check(cost < previousCost && cooldown.compareTo(previousCooldown) < 0, "strict continuous q descent");
            int ticks = ReturnMarkRules.cooldownTicks(q);
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0
                    && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0, "one final engine cooldown ceiling");
            previousCost = cost; previousCooldown = cooldown;
        }
        BigDecimal tiny = new BigDecimal("0.00001");
        for (int level = 2; level <= 4; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            double leftCost = (ReturnMarkRules.cpCost(q) - ReturnMarkRules.cpCost(q.subtract(tiny))) / tiny.doubleValue();
            double rightCost = (ReturnMarkRules.cpCost(q.add(tiny)) - ReturnMarkRules.cpCost(q)) / tiny.doubleValue();
            check(Math.abs(leftCost - rightCost) < .001, "cost slope remains smooth across integer q");
            double leftCooldown = ReturnMarkRules.theoreticalCooldown(q)
                    .subtract(ReturnMarkRules.theoreticalCooldown(q.subtract(tiny))).divide(tiny).doubleValue();
            double rightCooldown = ReturnMarkRules.theoreticalCooldown(q.add(tiny))
                    .subtract(ReturnMarkRules.theoreticalCooldown(q)).divide(tiny).doubleValue();
            check(Math.abs(leftCooldown - rightCooldown) < .001, "theoretical cooldown slope remains smooth across integer q");
        }
        BigDecimal a = new BigDecimal("2.1000"), b = new BigDecimal("2.1001");
        check(ReturnMarkRules.cpCost(a) > ReturnMarkRules.cpCost(b), "CP does not quantize classic progress to percent");
        check(ReturnMarkRules.theoreticalCooldown(a).compareTo(ReturnMarkRules.theoreticalCooldown(b)) > 0,
                "theoretical cooldown remains continuous inside an engine tick");
        check(ReturnMarkRules.cpCost(BigDecimal.ZERO) == 12 && ReturnMarkRules.cpCost(BigDecimal.TEN) == .05,
                "existing curve clamps retain bounded economics");

        check(ReturnMarkRules.RANGE == cn.academy.port.core.SkillTuning.number("return_mark", "reach"), "physical range uses the table");
        check(ReturnMarkRules.MARK_LIFE_TICKS == 1200, "mark lifetime is fixed at 1200 ticks");
        check(ReturnMarkRules.WARNING_TICKS == 4, "return warning lasts four ticks");
        check(ReturnMarkRules.REQUEST_INTERVAL_TICKS == 8, "request work has an eight-tick fence");
        check(ReturnMarkRules.MOVEMENT_FENCE_TICKS == PhaseSidestepRules.MOVEMENT_FENCE_TICKS
                && ReturnMarkRules.MOVEMENT_FENCE_TICKS == SafeLandingProbeRules.MOVEMENT_FENCE_TICKS,
                "return shares existing physical movement-fence duration");
        check(ReturnMarkRules.fresh(50, 50) && ReturnMarkRules.fresh(50, 1249), "mark alive until final pre-expiry tick");
        check(!ReturnMarkRules.fresh(50, 1250) && !ReturnMarkRules.fresh(50, 49), "exact expiry and future mark refuse");
        check(!ReturnMarkRules.fresh(-1, 0) && !ReturnMarkRules.fresh(0, -1), "corrupt mark clock refuses");
        check(ReturnMarkRules.ready(-1, 0, 8), "only empty receipt admits without elapsed fence");
        check(!ReturnMarkRules.ready(-2, 100, 8) && !ReturnMarkRules.ready(101, 100, 8), "malformed and future receipts refuse");
        check(!ReturnMarkRules.ready(100, 107, 8) && ReturnMarkRules.ready(100, 108, 8), "exact eight-tick request and movement fence");
        check(!ReturnMarkRules.ready(100, 103, 4) && ReturnMarkRules.ready(100, 104, 4), "warning boundary cannot complete early");
        check(!ReturnMarkRules.ready(-1, 100, 0) && !ReturnMarkRules.ready(-1, 100, -1)
                && !ReturnMarkRules.ready(-1, 100, 1201), "invalid fence sizes refuse");
        check(ReturnMarkRules.clock(0) && ReturnMarkRules.clock(Long.MAX_VALUE - 1200), "valid clock limits");
        check(!ReturnMarkRules.clock(-1) && !ReturnMarkRules.clock(Long.MAX_VALUE - 1199)
                && !ReturnMarkRules.clock(Long.MAX_VALUE), "overflow-risk clock refuses before adding lifetime");
        check(!ReturnMarkRules.ready(-1, Long.MAX_VALUE, 8)
                && !ReturnMarkRules.fresh(Long.MAX_VALUE - 1200, Long.MAX_VALUE), "timing queries share strict clock bounds");
        check(ReturnMarkRules.groundedVerticalMotion(-.0784) && ReturnMarkRules.groundedVerticalMotion(0)
                && ReturnMarkRules.groundedVerticalMotion(-.15), "ordinary grounded native gravity residue admits");
        check(!ReturnMarkRules.groundedVerticalMotion(Math.nextDown(-.15))
                && !ReturnMarkRules.groundedVerticalMotion(.42)
                && !ReturnMarkRules.groundedVerticalMotion(Double.NaN)
                && !ReturnMarkRules.groundedVerticalMotion(Double.POSITIVE_INFINITY), "fall, jump and invalid motion refuse");
        System.out.println("ReturnMarkRulesRegressionTest passed " + checks + " checks");
    }
}

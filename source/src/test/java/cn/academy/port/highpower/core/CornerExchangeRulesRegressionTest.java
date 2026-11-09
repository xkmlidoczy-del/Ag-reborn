package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** NEW333 economics and exact physical/input boundaries, independent of Minecraft state. */
public final class CornerExchangeRulesRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        double[] costs = {8, 4, 2, .5, .05};
        int[] cooldowns = {40, 20, 10, 4, 1};
        for (int i = 0; i < costs.length; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(CornerExchangeRules.cpCost(q) == costs[i], "exact CP anchor " + q);
            check(CornerExchangeRules.theoreticalCooldown(q).compareTo(BigDecimal.valueOf(cooldowns[i])) == 0,
                    "exact theoretical cooldown anchor " + q);
            check(CornerExchangeRules.cooldownTicks(q) == cooldowns[i], "native cooldown anchor " + q);
        }
        double lastCost = costs[0]; BigDecimal lastCooldown = BigDecimal.valueOf(cooldowns[0]);
        for (int percent = 101; percent <= 500; percent++) {
            BigDecimal q = BigDecimal.valueOf(percent, 2);
            double cost = CornerExchangeRules.cpCost(q); BigDecimal cooldown = CornerExchangeRules.theoreticalCooldown(q);
            check(cost < lastCost && cooldown.compareTo(lastCooldown) < 0, "economics descend continuously");
            int ticks = CornerExchangeRules.cooldownTicks(q);
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0 && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0,
                    "one final native cooldown ceiling");
            lastCost = cost; lastCooldown = cooldown;
        }
        BigDecimal tiny = new BigDecimal(".00001");
        for (int level = 2; level <= 4; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            double left = (CornerExchangeRules.cpCost(q) - CornerExchangeRules.cpCost(q.subtract(tiny))) / tiny.doubleValue();
            double right = (CornerExchangeRules.cpCost(q.add(tiny)) - CornerExchangeRules.cpCost(q)) / tiny.doubleValue();
            check(Math.abs(left - right) < .001, "CP derivative smooth at integer boundary");
            double leftCd = CornerExchangeRules.theoreticalCooldown(q).subtract(CornerExchangeRules.theoreticalCooldown(q.subtract(tiny))).divide(tiny).doubleValue();
            double rightCd = CornerExchangeRules.theoreticalCooldown(q.add(tiny)).subtract(CornerExchangeRules.theoreticalCooldown(q)).divide(tiny).doubleValue();
            check(Math.abs(leftCd - rightCd) < .001, "cooldown derivative smooth at integer boundary");
        }
        check(CornerExchangeRules.cpCost(new BigDecimal("2.1000")) > CornerExchangeRules.cpCost(new BigDecimal("2.1001")),
                "classic progress is not quantized to percent");
        check(CornerExchangeRules.cpCost(BigDecimal.ZERO) == 8 && CornerExchangeRules.cpCost(BigDecimal.TEN) == .05,
                "curve clamps remain bounded");
        check(CornerExchangeRules.MARK_LIFE_TICKS == cn.academy.port.core.SkillTuning.number("corner_exchange", "duration_ticks") && CornerExchangeRules.WARNING_TICKS == 3,
                "fixed first-mark lifetime and warning duration");
        check(CornerExchangeRules.HOLD_CLEAR_TICKS == 6 && CornerExchangeRules.REQUEST_INTERVAL_TICKS == 4,
                "fixed server hold classification and work fence");
        check(CornerExchangeRules.MOVEMENT_FENCE_TICKS == 8
                && CornerExchangeRules.MOVEMENT_FENCE_TICKS == SafeLandingProbeRules.MOVEMENT_FENCE_TICKS,
                "shared movement fence remains eight physical ticks");
        for (int held = 0; held < 6; held++)
            check(CornerExchangeRules.tapRelease(held) && !CornerExchangeRules.holdClears(held), "release before six is a tap");
        check(!CornerExchangeRules.tapRelease(6) && CornerExchangeRules.holdClears(6), "sixth tick exclusively clears");
        check(!CornerExchangeRules.tapRelease(100) && CornerExchangeRules.holdClears(100), "late release never turns a hold into a tap");
        check(!CornerExchangeRules.tapRelease(-1) && !CornerExchangeRules.holdClears(-1), "corrupt held counter fails closed");
        check(CornerExchangeRules.withinRange(CornerExchangeRules.RANGE) && CornerExchangeRules.withinRange(0), "inclusive configured boundary");
        check(!CornerExchangeRules.withinRange(Math.nextUp(CornerExchangeRules.RANGE)) && !CornerExchangeRules.withinRange(-1)
                && !CornerExchangeRules.withinRange(Double.NaN) && !CornerExchangeRules.withinRange(Double.POSITIVE_INFINITY),
                "range refuses invalid and just-outside values");
        check(CornerExchangeRules.near(.75, .125) && CornerExchangeRules.near(.75, -.125), "inclusive near-source cylinder");
        check(!CornerExchangeRules.near(Math.nextUp(.75), 0) && !CornerExchangeRules.near(0, Math.nextUp(.125))
                && !CornerExchangeRules.near(0, Math.nextDown(-.125)) && !CornerExchangeRules.near(-1, 0)
                && !CornerExchangeRules.near(Double.NaN, 0), "source proximity never expands beyond exact boundary");
        float[] headings = {-720, -181, -180, -179, -1, 0, 1, 179, 180, 181, 720, Float.MAX_VALUE};
        for (float yaw : headings) {
            check(Float.compare(CornerExchangeRules.commitYaw(yaw, false), yaw) == 0, "keep-facing is exact");
            float reversed = CornerExchangeRules.commitYaw(yaw, true);
            check(Float.isFinite(reversed) && reversed >= -180 && reversed <= 180, "reverse-facing is bounded and finite");
            check(Math.abs(Math.IEEEremainder(reversed - Math.IEEEremainder((double) yaw, 360), 360)) == 180,
                    "finite extreme facing cannot round away the half-turn");
        }
        check(CornerExchangeRules.commitYaw(0, true) == 180 && CornerExchangeRules.commitYaw(180, true) == 0
                && CornerExchangeRules.commitYaw(-180, true) == 0 && CornerExchangeRules.commitYaw(90, true) == -90,
                "reverse means one half-turn, including signed180 boundaries");
        boolean refused = false;
        try { CornerExchangeRules.commitYaw(Float.NaN, true); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "invalid facing cannot become native teleport input");
        check(CornerExchangeRules.groundedVerticalMotion(-.0784) && CornerExchangeRules.groundedVerticalMotion(0)
                && CornerExchangeRules.groundedVerticalMotion(-.15), "ordinary grounded native gravity residue admits");
        check(!CornerExchangeRules.groundedVerticalMotion(Math.nextDown(-.15)) && !CornerExchangeRules.groundedVerticalMotion(.42)
                && !CornerExchangeRules.groundedVerticalMotion(Double.NaN), "falling jumping invalid source motion refuses");
        System.out.println("CornerExchangeRulesRegressionTest passed " + checks + " checks");
    }
}

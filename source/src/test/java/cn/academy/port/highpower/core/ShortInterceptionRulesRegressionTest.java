package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** NEW331 workbook invariants only; native collision and transaction tests live in GameTest. */
public final class ShortInterceptionRulesRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-12, message + ": " + actual + " != " + expected);
    }
    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) {
        double[] cp = {18, 9, 4.5, 1.125, .1125};
        int[] cd = {60, 30, 16, 6, 1};
        for (int index = 0; index < 5; index++) {
            BigDecimal q = BigDecimal.valueOf(index + 1);
            near(ShortInterceptionRules.cpCost(q), cp[index], "exact per-projectile CP anchor " + q);
            check(ShortInterceptionRules.cooldownTicks(q) == cd[index], "exact native cooldown anchor " + q);
        }
        double previousCp = cp[0]; int previousCd = cd[0];
        for (int step = 101; step <= 500; step++) {
            BigDecimal q = BigDecimal.valueOf(step, 2);
            double cost = ShortInterceptionRules.cpCost(q); int cooldown = ShortInterceptionRules.cooldownTicks(q);
            check(Double.isFinite(cost) && cost > 0 && cost < previousCp, "every fractional step has decreasing positive per-shot cost " + q);
            check(cooldown >= 1 && cooldown <= previousCd, "native cooldown stays positive and monotone " + q);
            previousCp = cost; previousCd = cooldown;
        }
        check(ShortInterceptionRules.cpCost(new BigDecimal("3.1001")) < ShortInterceptionRules.cpCost(new BigDecimal("3.1000")),
                "actual growth is never quantized to a percentage or integer level");
        BigDecimal epsilon = new BigDecimal(".00001");
        for (int level = 2; level <= 4; level++) {
            BigDecimal q = BigDecimal.valueOf(level); double at = ShortInterceptionRules.cpCost(q);
            double before = ShortInterceptionRules.cpCost(q.subtract(epsilon)), after = ShortInterceptionRules.cpCost(q.add(epsilon));
            check(Math.abs((at - before) / epsilon.doubleValue() - (after - at) / epsilon.doubleValue()) < .002,
                    "per-shot CP has no slope jump at integer growth boundary " + level);
            check(ShortInterceptionRules.cooldownTicks(q.subtract(epsilon)) == cd[level - 1] + 1
                    && ShortInterceptionRules.cooldownTicks(q.add(epsilon)) == cd[level - 1],
                    "native cooldown applies one final upward tick rounding across anchor " + level);
        }
        near(ShortInterceptionRules.cpCost(BigDecimal.ZERO), 18, "finite low coordinate clamps to first anchor");
        near(ShortInterceptionRules.cpCost(BigDecimal.TEN), .1125, "finite high coordinate clamps to final anchor");
        check(ShortInterceptionRules.cooldownTicks(BigDecimal.ZERO) == 60 && ShortInterceptionRules.cooldownTicks(BigDecimal.TEN) == 1,
                "cooldown clamps are bounded independently of the physical fences");

        for (String raw : new String[] {"1e-30", ".00001", "1", "10000000", "10000000000000", "5000000000000000000"}) {
            BigDecimal d = new BigDecimal(raw), actual = ShortInterceptionRules.damage(d);
            check(actual.compareTo(d.multiply(new BigDecimal("1.8"))) == 0, "one physical projectile retains exact1.8D " + raw);
            check(actual.multiply(BigDecimal.valueOf(4)).compareTo(d.multiply(new BigDecimal("7.2"))) == 0,
                    "four separately admitted arrows are not assigned shares of a single1.8D pool " + raw);
        }
        check(ShortInterceptionRules.damage(null).signum() == 0 && ShortInterceptionRules.damage(BigDecimal.ZERO).signum() == 0
                && ShortInterceptionRules.damage(BigDecimal.ONE.negate()).signum() == 0, "absent and nonpositive base amounts never mint damage");
        check(ShortInterceptionRules.damage(new BigDecimal("1e20")).compareTo(new BigDecimal("1e20")) == 0,
                "final per-projectile amount respects the shared exact ledger cap");

        double[][] offsets = {{0}, {-.15, .15}, {-.3, 0, .3}, {-.45, -.15, .15, .45}};
        for (int count = 1; count <= 4; count++) {
            double sum = 0;
            for (int slot = 0; slot < count; slot++) {
                double actual = ShortInterceptionRules.offset(slot, count); near(actual, offsets[count - 1][slot], "fixed exit offset " + slot + "/" + count);
                near(actual, -ShortInterceptionRules.offset(count - 1 - slot, count), "fixed exit set remains centered"); sum += actual;
            }
            near(sum, 0, "whole fixed exit set has no lateral drift");
        }
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 5, Integer.MAX_VALUE})
            rejects(() -> ShortInterceptionRules.offset(0, count), "invalid snapshot size cannot define an exit");
        for (int count = 1; count <= 4; count++) {
            int size = count;
            rejects(() -> ShortInterceptionRules.offset(-1, size), "negative slot never defines an exit");
            rejects(() -> ShortInterceptionRules.offset(size, size), "slot beyond frozen snapshot never defines an exit");
        }

        check(ShortInterceptionRules.path(0, 46, 2) && ShortInterceptionRules.path(10, 30, 8)
                && ShortInterceptionRules.path(0, 0, 48), "all three physical segments share inclusive48m total budget");
        check(!ShortInterceptionRules.path(0, 0, Math.nextUp(48.0)) && !ShortInterceptionRules.path(47, 1, .000001)
                && !ShortInterceptionRules.path(48, 48, 48), "each individually short segment cannot exceed the cumulative48m limit");
        check(!ShortInterceptionRules.path(-1, 1, 1) && !ShortInterceptionRules.path(1, -1, 1)
                && !ShortInterceptionRules.path(1, 1, -1) && !ShortInterceptionRules.path(1, 1, 0), "negative paths and a zero native flight refuse");
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(!ShortInterceptionRules.path(bad, 1, 1) && !ShortInterceptionRules.path(1, bad, 1)
                    && !ShortInterceptionRules.path(1, 1, bad), "every nonfinite segment fails closed");
        check(!ShortInterceptionRules.path(Double.MAX_VALUE, Double.MAX_VALUE, 1), "overflowing finite path sum cannot become a valid cast");

        check(ShortInterceptionRules.RANGE == 48 && ShortInterceptionRules.SELECTION_RADIUS == .75
                && ShortInterceptionRules.EXIT_DISTANCE == 2, "approved target/corridor/fixed-exit geometry");
        check(ShortInterceptionRules.MAX_ARROWS == 4 && ShortInterceptionRules.SCAN_LIMIT == 64,
                "separate frozen admission and bounded-world-scan limits");
        check(ShortInterceptionRules.FLIGHT_TICKS == 20 && ShortInterceptionRules.QUOTA_TICKS == 20
                && ShortInterceptionRules.CAST_FENCE == 8 && ShortInterceptionRules.REQUEST_FENCE == 4,
                "flight permit and rolling quota remain physical limits independent of one-tick LV5 economics");
        check(ShortInterceptionRules.MARK_TICKS == cn.academy.port.core.SkillTuning.number("short_interception", "duration_ticks") && ShortInterceptionRules.HOLD_CLEAR == 6
                && ShortInterceptionRules.NEEDLE_SPEED == 1.5, "frozen provisional mark/hold/fallback contracts");
        System.out.println("ShortInterceptionRulesRegressionTest passed " + checks + " checks");
    }
}

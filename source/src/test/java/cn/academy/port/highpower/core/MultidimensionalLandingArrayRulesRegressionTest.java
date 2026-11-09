package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Exact whole-round partition and continuous economics, without a game process. */
public final class MultidimensionalLandingArrayRulesRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void rejected(Runnable action, String reason) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { refused = true; }
        check(refused, reason);
    }
    public static void main(String[] args) {
        double[] cp = {24, 12, 6, 1.5, .15}; int[] cd = {120, 60, 30, 10, 1};
        for (int level = 1; level <= 5; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            check(MultidimensionalLandingArrayRules.cpCost(q) == cp[level - 1], "exact CP anchor");
            check(MultidimensionalLandingArrayRules.cooldownTicks(q) == cd[level - 1], "exact CD anchor");
        }
        double previousCp = Double.POSITIVE_INFINITY; int previousCd = Integer.MAX_VALUE;
        for (int step = 0; step <= 400; step++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(step, 2));
            double cost = MultidimensionalLandingArrayRules.cpCost(q);
            int cooldown = MultidimensionalLandingArrayRules.cooldownTicks(q);
            check(Double.isFinite(cost) && cost >= .15 && cost <= 24 && cost <= previousCp, "bounded continuous monotone CP");
            check(cooldown >= 1 && cooldown <= previousCd, "only engine cooldown is ceiled");
            for (int targets = 1; targets <= 16; targets++) {
                int needles = MultidimensionalLandingArrayRules.needleCount(targets); double sum = 0;
                for (int slot = 0; slot < needles; slot++) {
                    double share = MultidimensionalLandingArrayRules.cpShare(cost, slot, needles);
                    check(Double.isFinite(share) && share > 0, "every CP share positive and finite");
                    sum += share;
                }
                check(Double.doubleToLongBits(sum) == Double.doubleToLongBits(cost), "last residual preserves exact representable CP sum");
            }
            previousCp = cost; previousCd = cooldown;
        }
        for (int boundary = 2; boundary <= 4; boundary++) {
            BigDecimal q = BigDecimal.valueOf(boundary), epsilon = new BigDecimal(".000001");
            double left = MultidimensionalLandingArrayRules.cpCost(q.subtract(epsilon));
            double exact = MultidimensionalLandingArrayRules.cpCost(q), right = MultidimensionalLandingArrayRules.cpCost(q.add(epsilon));
            check(left > exact && exact > right && left - right < .0002, "no level boundary CP jump");
        }
        check(MultidimensionalLandingArrayRules.cpCost(new BigDecimal("4.123456789"))
                > MultidimensionalLandingArrayRules.cpCost(new BigDecimal("4.123456790")), "fractional q is not integer quantized");
        check(MultidimensionalLandingArrayRules.cpCost(BigDecimal.ZERO) == 24
                && MultidimensionalLandingArrayRules.cpCost(BigDecimal.TEN) == .15, "finite q overshoots clamp");
        rejected(() -> MultidimensionalLandingArrayRules.cpCost(null), "null q refused");
        rejected(() -> MultidimensionalLandingArrayRules.cpCost(new BigDecimal("1e-257")), "unbounded q scale refused");
        rejected(() -> MultidimensionalLandingArrayRules.cpCost(new BigDecimal("1e257")), "unbounded q exponent refused");
        check(ContinuousHighPowerGrowth.position(4, Double.NaN).compareTo(BigDecimal.valueOf(4)) == 0
                && ContinuousHighPowerGrowth.position(4, Double.POSITIVE_INFINITY).compareTo(BigDecimal.valueOf(4)) == 0,
                "nonfinite native progress cannot grant advancement");

        for (int targets = 1; targets <= 16; targets++) {
            int needles = MultidimensionalLandingArrayRules.needleCount(targets);
            check(needles == Math.max(6, targets), "six mandatory real needles and every frozen target represented");
            boolean[] assigned = new boolean[targets], anchors = new boolean[6];
            for (int slot = 0; slot < needles; slot++) {
                check(MultidimensionalLandingArrayRules.sourceSlot(slot, targets) == slot % 6, "source index is always i modulo six");
                check(MultidimensionalLandingArrayRules.targetSlot(slot, targets) == slot % targets, "frozen target index i modulo T");
                check(MultidimensionalLandingArrayRules.launchTick(slot, needles) == slot * 4, "physical four tick emission spacing");
                assigned[slot % targets] = true; anchors[slot % 6] = true;
            }
            for (boolean target : assigned) check(target, "every target owns an original");
            for (boolean anchor : anchors) check(anchor, "all six anchors participate");
            for (String raw : new String[]{"1e-250", ".0000000000000001", ".1", "1", "1.234567890123456789", "1e7", "5e18", "2.5e19", "4e19", "1e20"}) {
                BigDecimal d = new BigDecimal(raw), whole = MultidimensionalLandingArrayRules.damageTotal(d), sum = BigDecimal.ZERO;
                check(whole.compareTo(d.multiply(BigDecimal.valueOf(4)).min(CombatAmounts.CAP)) == 0, "whole min(4D,1e20), capped exactly once");
                BigDecimal first = MultidimensionalLandingArrayRules.damageShare(whole, 0, needles);
                for (int slot = 0; slot < needles; slot++) {
                    BigDecimal share = MultidimensionalLandingArrayRules.damageShare(whole, slot, needles);
                    check(share.signum() > 0 && CombatAmounts.requireAmount(share, "share").compareTo(share) == 0, "native legal positive exact decimal share");
                    check(slot == needles - 1 || share.compareTo(first) == 0, "only final original owns remainder");
                    sum = sum.add(share);
                }
                check(sum.compareTo(whole) == 0, "exact decimal sum for every N six through sixteen");
                check(sum.subtract(first).compareTo(whole) < 0, "missed share is lost rather than reassigned");
            }
        }
        for (BigDecimal bad : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate(), CombatAmounts.CAP.add(BigDecimal.ONE), new BigDecimal("1e-257")})
            rejected(() -> MultidimensionalLandingArrayRules.damageTotal(bad), "invalid D refused");
        rejected(() -> MultidimensionalLandingArrayRules.damageShare(BigDecimal.ZERO, 0, 6), "zero whole budget refused");
        rejected(() -> MultidimensionalLandingArrayRules.damageShare(new BigDecimal("4e-256"), 0, 16), "decimal underflow refuses whole plan, never authorizes zero-value share");
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE})
            rejected(() -> MultidimensionalLandingArrayRules.cpShare(bad, 0, 6), "invalid or underflowing CP refused");
        for (int bad : new int[]{-1, 0, 17, Integer.MAX_VALUE}) rejected(() -> MultidimensionalLandingArrayRules.needleCount(bad), "invalid T refused");
        for (int bad : new int[]{0, 5, 17}) {
            rejected(() -> MultidimensionalLandingArrayRules.damageShare(BigDecimal.ONE, 0, bad), "invalid N refuses damage");
            rejected(() -> MultidimensionalLandingArrayRules.cpShare(1, 0, bad), "invalid N refuses CP");
        }
        rejected(() -> MultidimensionalLandingArrayRules.launchTick(16, 16), "no seventeenth original");
        rejected(() -> MultidimensionalLandingArrayRules.launchTick(-1, 6), "no negative original");
        check(MultidimensionalLandingArrayRules.PREPARATION_TICKS == 400 && MultidimensionalLandingArrayRules.MAX_IN_FLIGHT == 6
                && MultidimensionalLandingArrayRules.ROUND_TICKS == 160 && MultidimensionalLandingArrayRules.CAST_FENCE == 20
                && MultidimensionalLandingArrayRules.FLIGHT_TICKS == 40 && MultidimensionalLandingArrayRules.CANDIDATE_LIMIT == 64,
                "immutable physical resource fences");
        check(MultidimensionalLandingArrayRules.emissionDue(100, 100, Long.MIN_VALUE, 0), "first original can emit at admitted start");
        check(!MultidimensionalLandingArrayRules.emissionDue(103, 100, 100, 0)
                && MultidimensionalLandingArrayRules.emissionDue(104, 100, 100, 0), "same-target minimum four ticks");
        check(!MultidimensionalLandingArrayRules.emissionDue(104, 100, 100, 6), "six in flight exhaust capacity");
        check(MultidimensionalLandingArrayRules.emissionDue(220, 100, 100, 0)
                && !MultidimensionalLandingArrayRules.emissionDue(220, 100, 220, 0), "stall may emit one original, never catch up in same tick");
        check(!MultidimensionalLandingArrayRules.emissionDue(260, 100, 100, 0)
                && !MultidimensionalLandingArrayRules.emissionDue(99, 100, Long.MIN_VALUE, 0), "absolute160 deadline and start boundary");
        System.out.println("MultidimensionalLandingArrayRulesRegressionTest: " + checks + " checks passed");
    }
}

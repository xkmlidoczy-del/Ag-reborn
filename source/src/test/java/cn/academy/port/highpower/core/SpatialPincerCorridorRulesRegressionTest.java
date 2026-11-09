package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Exact NEW341 budgets and continuous prices; native contact is checked separately. */
public final class SpatialPincerCorridorRulesRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void rejected(Runnable action, String reason) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { refused = true; }
        check(refused, reason);
    }
    public static void main(String[] args) {
        double[] cp = {16, 8, 4, 1, .1}, transferCp = {8, 4, 2, .5, .05};
        int[] cd = {80, 40, 20, 8, 1}, transferCd = {60, 30, 16, 10, 1};
        for (int i = 0; i < 5; i++) {
            var q = BigDecimal.valueOf(i + 1);
            check(SpatialPincerCorridorRules.cpCost(q) == cp[i], "exact cast CP anchor " + q);
            check(SpatialPincerCorridorRules.transferCpCost(q) == transferCp[i], "exact transfer CP anchor " + q);
            check(SpatialPincerCorridorRules.cooldownTicks(q) == cd[i], "cast cooldown anchor " + q);
            check(SpatialPincerCorridorRules.transferCooldownTicks(q) == transferCd[i], "transfer cooldown anchor " + q);
        }
        double previous = Double.POSITIVE_INFINITY, previousTransfer = Double.POSITIVE_INFINITY;
        int previousCd = Integer.MAX_VALUE, previousTransferCd = Integer.MAX_VALUE;
        for (int i = 0; i <= 4000; i++) {
            var q = BigDecimal.ONE.add(BigDecimal.valueOf(i, 3));
            double cost = SpatialPincerCorridorRules.cpCost(q), transfer = SpatialPincerCorridorRules.transferCpCost(q);
            int cooldown = SpatialPincerCorridorRules.cooldownTicks(q), transferCooldown = SpatialPincerCorridorRules.transferCooldownTicks(q);
            check(Double.isFinite(cost) && cost >= .1 && cost <= 16 && cost <= previous, "continuous bounded decreasing cast cost " + q);
            check(Double.isFinite(transfer) && transfer >= .05 && transfer <= 8 && transfer <= previousTransfer,
                    "continuous bounded decreasing transfer cost " + q);
            check(cooldown >= 1 && cooldown <= previousCd && transferCooldown >= 1 && transferCooldown <= previousTransferCd,
                    "only scheduler cooldowns are integer-ceiled " + q);
            double first = SpatialPincerCorridorRules.cpShare(cost, 0), second = SpatialPincerCorridorRules.cpShare(cost, 1), last = SpatialPincerCorridorRules.cpShare(cost, 2);
            check(first > 0 && first == second && last > 0, "all three committed CP shares stay positive");
            check(Double.doubleToLongBits((first + second) + last) == Double.doubleToLongBits(cost),
                    "last emission conserves exact representable frozen CP residual");
            previous = cost; previousTransfer = transfer; previousCd = cooldown; previousTransferCd = transferCooldown;
        }
        for (int boundary = 2; boundary <= 4; boundary++) {
            var center = BigDecimal.valueOf(boundary); var epsilon = new BigDecimal(".000001");
            double left = SpatialPincerCorridorRules.cpCost(center.subtract(epsilon));
            double exact = SpatialPincerCorridorRules.cpCost(center);
            double right = SpatialPincerCorridorRules.cpCost(center.add(epsilon));
            check(left > exact && exact > right && left - right < .0001, "no price jump or integer-q quantization at " + boundary);
        }
        var q1 = new BigDecimal("4.123456789"); var q2 = new BigDecimal("4.123456790");
        check(SpatialPincerCorridorRules.cpCost(q1) > SpatialPincerCorridorRules.cpCost(q2)
                && SpatialPincerCorridorRules.transferCpCost(q1) > SpatialPincerCorridorRules.transferCpCost(q2),
                "tiny fractional progress changes both continuous prices");
        check(SpatialPincerCorridorRules.cpCost(BigDecimal.ZERO) == 16
                && SpatialPincerCorridorRules.transferCpCost(BigDecimal.TEN) == .05, "finite q overshoots clamp to endpoints");
        rejected(() -> SpatialPincerCorridorRules.cpCost(null), "null q is invalid");
        rejected(() -> SpatialPincerCorridorRules.transferCpCost(new BigDecimal("1e-257")), "unbounded q representation is rejected");

        BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);
        for (String raw : new String[]{"1e-250", ".00000000000000000000001", ".1", "1", "1.234567890123456789", "10000000", "5e18", "4e19", "1e20"}) {
            var d = new BigDecimal(raw);
            var total = SpatialPincerCorridorRules.damageTotal(d);
            var expected = d.multiply(new BigDecimal("2.8")).min(CombatAmounts.CAP);
            check(total.compareTo(expected) == 0, "exact capped 2.8D total for fractional D " + raw);
            var first = SpatialPincerCorridorRules.damageShare(total, 0);
            var second = SpatialPincerCorridorRules.damageShare(total, 1);
            var last = SpatialPincerCorridorRules.damageShare(total, 2);
            check(first.signum() > 0 && second.signum() > 0 && last.signum() > 0 && first.compareTo(second) == 0,
                    "three noninteger fixed damage shares " + raw);
            check(first.add(second).add(last).compareTo(total) == 0, "all three shares conserve the frozen total exactly " + raw);
            check(last.compareTo(first) >= 0 && last.subtract(first).compareTo(quantum.multiply(BigDecimal.valueOf(3))) < 0,
                    "only last share owns bounded exact decimal residual " + raw);
            check(first.add(last).compareTo(total) < 0 && second.compareTo(SpatialPincerCorridorRules.damageShare(total, 1)) == 0,
                    "a missing slot discards its damage instead of redistributing to later originals " + raw);
            for (var share : new BigDecimal[]{first, second, last})
                check(CombatAmounts.requireAmount(share, "share").compareTo(share) == 0, "share remains legal in native decimal combat ledger");
        }
        check(SpatialPincerCorridorRules.damageTotal(CombatAmounts.CAP).compareTo(CombatAmounts.CAP) == 0,
                "2.8D overflow is capped once across entire volley");
        for (BigDecimal bad : new BigDecimal[]{null, BigDecimal.ZERO, BigDecimal.ONE.negate(), CombatAmounts.CAP.add(BigDecimal.ONE)})
            rejected(() -> SpatialPincerCorridorRules.damageTotal(bad), "invalid D cannot produce attack shares");
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            rejected(() -> SpatialPincerCorridorRules.cpShare(bad, 0), "nonpositive or nonfinite CP budget is rejected");
        for (int bad : new int[]{-1, 3, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            rejected(() -> SpatialPincerCorridorRules.launchTick(bad), "exactly three launch slots");
            rejected(() -> SpatialPincerCorridorRules.damageShare(BigDecimal.ONE, bad), "invalid slot cannot mint damage");
            rejected(() -> SpatialPincerCorridorRules.cpShare(1, bad), "invalid slot cannot debit CP");
        }
        for (int slot = 0; slot < 3; slot++) check(SpatialPincerCorridorRules.launchTick(slot) == slot * 4, "fixed emission schedule 0/4/8");
        check(SpatialPincerCorridorRules.NEEDLES == 3 && SpatialPincerCorridorRules.PREPARATION_TICKS == cn.academy.port.core.SkillTuning.number("spatial_pincer_corridor", "duration_ticks")
                && SpatialPincerCorridorRules.FLIGHT_TICKS == 40 && SpatialPincerCorridorRules.CAST_FENCE == 8
                && SpatialPincerCorridorRules.REQUEST_FENCE == 4 && SpatialPincerCorridorRules.CLEAR_HOLD_TICKS == 6
                && SpatialPincerCorridorRules.INVITATION_TICKS == 200, "physical timings do not shrink with continuous q");
        for (int targets = 1; targets <= 8; targets++) {
            int needles = SpatialPincerCorridorRules.needleCount(targets);
            check(needles == Math.max(3, targets), "every frozen target owns a deterministic original assignment");
            for (String raw : new String[]{"1e-250", ".1", "1.234567890123456789", "4e19", "1e20"}) {
                BigDecimal whole = SpatialPincerCorridorRules.damageTotal(new BigDecimal(raw)), sum = BigDecimal.ZERO;
                for (int slot = 0; slot < needles; slot++) {
                    var share = SpatialPincerCorridorRules.damageShare(whole, slot, needles); sum = sum.add(share);
                    check(share.signum() > 0 && CombatAmounts.requireAmount(share, "assigned share").compareTo(share) == 0, "legal positive variable-count share");
                    check(SpatialPincerCorridorRules.launchTick(slot, needles) == 4 * slot, "absolute per-original publication timing");
                    check(SpatialPincerCorridorRules.sourceSlot(slot, targets) == slot % 3
                            && SpatialPincerCorridorRules.targetSlot(slot, targets) == slot % targets, "immutable cyclic assignments");
                }
                check(sum.compareTo(whole) == 0, "one capped damage budget conserved across N=" + needles);
                BigDecimal first = SpatialPincerCorridorRules.damageShare(whole, 0, needles);
                check(sum.subtract(first).compareTo(whole) < 0, "skipped assignment permanently discards only its fixed share");
            }
            for (int progress = 0; progress <= 4000; progress++) {
                double whole = SpatialPincerCorridorRules.cpCost(BigDecimal.ONE.add(BigDecimal.valueOf(progress, 3))), sum = 0;
                for (int slot = 0; slot < needles; slot++) {
                    double share = SpatialPincerCorridorRules.cpShare(whole, slot, needles);
                    check(Double.isFinite(share) && share > 0, "each original owns positive finite CP"); sum += share;
                }
                check(Double.doubleToLongBits(sum) == Double.doubleToLongBits(whole), "final CP residual exactly conserves N=" + needles);
            }
        }
        check(SpatialPincerCorridorRules.POINTS == 3 && SpatialPincerCorridorRules.MAX_TARGETS == 8
                && SpatialPincerCorridorRules.MAX_NEEDLES == 8 && SpatialPincerCorridorRules.CANDIDATE_LIMIT == 64,
                "three geometry marks, eight frozen hostiles, eight custody slots, and entire scan bound are distinct");
        for (int invalid : new int[]{-1, 0, 9, Integer.MAX_VALUE}) {
            rejected(() -> SpatialPincerCorridorRules.needleCount(invalid), "invalid target count cannot create assignments");
            rejected(() -> SpatialPincerCorridorRules.damageShare(BigDecimal.ONE, 0, invalid), "invalid count cannot mint damage");
            rejected(() -> SpatialPincerCorridorRules.cpShare(1, 0, invalid), "invalid count cannot debit CP");
        }
        rejected(() -> SpatialPincerCorridorRules.launchTick(8, 8), "ninth original cannot be scheduled");
        System.out.println("SpatialPincerCorridorRulesRegressionTest: " + checks + " checks passed");
    }
}

package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

/** NEW342 smooth manual economics, fixed timing and exact base-only automatic charge. */
public final class EmergencyReturnRulesRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal actual, BigDecimal expected, String message) {
        check(actual.compareTo(expected) == 0, message + ": " + actual + " != " + expected);
    }
    private static void refused(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        double[] costs = {8, 4, 2, .5, .05};
        int[] cooldowns = {60, 30, 16, 10, 1};
        for (int i = 0; i < costs.length; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(EmergencyReturnRules.cpCost(q) == costs[i], "exact CP anchor " + q);
            equal(EmergencyReturnRules.theoreticalCooldown(q), BigDecimal.valueOf(cooldowns[i]), "cooldown anchor " + q);
            check(EmergencyReturnRules.cooldownTicks(q) == cooldowns[i], "engine cooldown anchor " + q);
        }
        double previousCost = costs[0]; BigDecimal previousCooldown = BigDecimal.valueOf(cooldowns[0]);
        for (int percent = 101; percent <= 500; percent++) {
            BigDecimal q = BigDecimal.valueOf(percent, 2), cooldown = EmergencyReturnRules.theoreticalCooldown(q);
            double cost = EmergencyReturnRules.cpCost(q); int ticks = EmergencyReturnRules.cooldownTicks(q);
            check(cost < previousCost && cooldown.compareTo(previousCooldown) < 0, "strict continuous q descent");
            check(BigDecimal.valueOf(ticks).compareTo(cooldown) >= 0
                    && BigDecimal.valueOf(ticks - 1).compareTo(cooldown) < 0, "one final engine ceiling");
            previousCost = cost; previousCooldown = cooldown;
        }
        BigDecimal tiny = n(".00001");
        for (int level = 2; level <= 4; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            double costLeft = (EmergencyReturnRules.cpCost(q) - EmergencyReturnRules.cpCost(q.subtract(tiny))) / tiny.doubleValue();
            double costRight = (EmergencyReturnRules.cpCost(q.add(tiny)) - EmergencyReturnRules.cpCost(q)) / tiny.doubleValue();
            check(Math.abs(costLeft - costRight) < .001, "smooth CP slope at integer q");
            double cdLeft = EmergencyReturnRules.theoreticalCooldown(q).subtract(EmergencyReturnRules.theoreticalCooldown(q.subtract(tiny))).divide(tiny).doubleValue();
            double cdRight = EmergencyReturnRules.theoreticalCooldown(q.add(tiny)).subtract(EmergencyReturnRules.theoreticalCooldown(q)).divide(tiny).doubleValue();
            check(Math.abs(cdLeft - cdRight) < .001, "smooth cooldown slope at integer q");
        }
        check(EmergencyReturnRules.cpCost(n("2.1000")) > EmergencyReturnRules.cpCost(n("2.1001")), "CP never quantizes progress");
        check(EmergencyReturnRules.theoreticalCooldown(n("2.1000")).compareTo(EmergencyReturnRules.theoreticalCooldown(n("2.1001"))) > 0,
                "theoretical cooldown remains continuous within one engine tick");
        check(EmergencyReturnRules.cpCost(BigDecimal.ZERO) == 8 && EmergencyReturnRules.cpCost(BigDecimal.TEN) == .05,
                "existing growth curve retains its bounded endpoints");
        check(EmergencyReturnRules.RANGE == cn.academy.port.core.SkillTuning.number("emergency_return", "reach") && EmergencyReturnRules.MARK_LIFE_TICKS == 1200
                && EmergencyReturnRules.AUTOMATIC_FENCE_TICKS == 1200, "fixed physical/lifetime/automatic boundaries");
        check(EmergencyReturnRules.REQUEST_INTERVAL_TICKS == 8 && EmergencyReturnRules.MOVEMENT_FENCE_TICKS == 8,
                "request and existing shared movement fence remain eight ticks");
        check(EmergencyReturnRules.fresh(50, 50) && EmergencyReturnRules.fresh(50, 1249), "mark survives final pre-expiry tick");
        check(!EmergencyReturnRules.fresh(50, 1250) && !EmergencyReturnRules.fresh(50, 49), "exact expiry and future mark refuse");
        check(EmergencyReturnRules.ready(-1, 0, 1200) && !EmergencyReturnRules.ready(-2, 100, 1200), "only absent receipt admits first use");
        check(!EmergencyReturnRules.ready(50, 1249, 1200) && EmergencyReturnRules.ready(50, 1250, 1200), "automatic fence exact boundary");
        check(!EmergencyReturnRules.ready(50, 57, 8) && EmergencyReturnRules.ready(50, 58, 8), "input and movement exact boundary");
        check(!EmergencyReturnRules.ready(101, 100, 8) && !EmergencyReturnRules.ready(-1, 100, 0)
                && !EmergencyReturnRules.ready(-1, 100, 1201), "future and invalid fences refuse");
        check(EmergencyReturnRules.clock(0) && EmergencyReturnRules.clock(Long.MAX_VALUE - 1200), "bounded clock endpoints");
        check(!EmergencyReturnRules.clock(-1) && !EmergencyReturnRules.clock(Long.MAX_VALUE - 1199)
                && !EmergencyReturnRules.fresh(0, Long.MAX_VALUE), "negative and overflow clocks refuse");
        check(EmergencyReturnRules.groundedVerticalMotion(-.0784) && EmergencyReturnRules.groundedVerticalMotion(0)
                && !EmergencyReturnRules.groundedVerticalMotion(.42) && !EmergencyReturnRules.groundedVerticalMotion(Double.NaN),
                "grounded native residue only");

        automaticBaseOnly();
        System.out.println("EmergencyReturnRulesRegressionTest passed " + checks + " checks");
    }

    private static void automaticBaseOnly() {
        var base = new ExactCombatState(n("75"), n("100"), n("60"), n("120"), false);
        var after = EmergencyReturnRules.automaticSpend(base);
        equal(EmergencyReturnRules.automaticCost(base.maxShield()), n("12"), "fixed 10% of certified base S");
        equal(after.shield(), n("48"), "automatic payment debits base exactly once");
        equal(after.health(), base.health(), "payment cannot heal or absorb the already settled HP loss");
        equal(after.maxShield(), base.maxShield(), "payment cannot expand capacity");
        check(!after.shieldEnabled(), "payment does not enable old defenses");
        var poor = new ExactCombatState(n("75"), n("100"), n("11.999999"), n("120"), false);
        check(EmergencyReturnRules.automaticSpend(poor) == null, "insufficient base refuses even if independent reserves could pay");
        check(EmergencyReturnRules.automaticSpend(new ExactCombatState(n("75"), n("100"), n("12"), n("120"), false)).shield().signum() == 0,
                "exact affordability reaches zero without underflow");
        check(EmergencyReturnRules.automaticSpend(new ExactCombatState(n("75"), n("100"), BigDecimal.ZERO, BigDecimal.ZERO, false)) == null,
                "zero certified capacity cannot create a free automatic escape");
        check(EmergencyReturnRules.automaticSpend(new ExactCombatState(BigDecimal.ZERO, n("100"), n("60"), n("120"), false)) == null,
                "dead state cannot pay");
        check(EmergencyReturnRules.automaticSpend(base.withShieldEnabled(true)) == null && EmergencyReturnRules.automaticSpend(null) == null,
                "unsupported defense/null states fail closed");
        refused(() -> EmergencyReturnRules.automaticCost(BigDecimal.ZERO), "zero capacity refuses");
        refused(() -> EmergencyReturnRules.automaticCost(n("-1")), "negative capacity refuses");
        refused(() -> EmergencyReturnRules.automaticCost(CombatAmounts.CAP.add(BigDecimal.ONE)), "out-of-bound capacity refuses");
        var corrupt = new HashMap<>(base.toDecimalMap()); corrupt.put("shield", "NaN");
        refused(() -> ExactCombatState.fromDecimalMap(corrupt), "corrupted balances cannot initialize a replacement full pool");

        var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(n("20"), n("20"), n("20")));
        var reserves = new SharedShieldReserveShares(n("15"), n("21"));
        var armor = ZeroGapArmorShares.restore(base, scatter, reserves,
                List.of(n("3"), n("17"), n("5"), n("15"), n("7"), n("13")),
                List.of(n("1"), n("2"), n("5"), n("7"), n("9"), n("12")));
        var nextScatter = scatter.afterBaseSpend(after.shield());
        var next = armor.reconcileParents(after, nextScatter, reserves).after();
        equal(armor.total().subtract(next.total()), n("12"), "all twelve child deltas sum to the sole base debit");
        check(next.reserves().equals(reserves), "lower and upper reserve identities remain unchanged");
        for (int i = 0; i < 6; i++) equal(next.plate(i).reserveCurrent(), armor.plate(i).reserveCurrent(), "reserve child preserved " + i);
        for (int i = 0; i < 3; i++) equal(next.plate(2 * i).baseCurrent().add(next.plate(2 * i + 1).baseCurrent()), nextScatter.plate(i).current(), "base child sum " + i);
        equal(next.plate(0).baseCurrent(), BigDecimal.ZERO, "ordered existing parent debit exhausts first asymmetric child");
        equal(next.plate(1).baseCurrent(), n("8"), "remaining parent debit retains second asymmetric child");
        check(next.reconcileParents(after, nextScatter, reserves).after().toDecimalMap().equals(next.toDecimalMap()), "zero delta/relearn reconciliation cannot refill");
        var recovered = next.base().regenerateShield(n("1.2"));
        var recoveredChildren = next.reconcileParents(recovered, nextScatter.afterBaseRecovery(recovered.shield()), reserves).after();
        equal(recoveredChildren.total().subtract(next.total()), n("1.2"), "existing one-percent recovery creates only one budget");
        for (int i = 0; i < 6; i++) equal(recoveredChildren.plate(i).reserveCurrent(), next.plate(i).reserveCurrent(), "base recovery never credits reserve child " + i);
    }
}

package cn.academy.port.highpower;

import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Independent exact-budget, shared-resource, boundary and continuous-cost acceptance witnesses. */
public final class ThermalShellRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) { check(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static ThermalShellRules.Point p(double x, double y, double z) { return new ThermalShellRules.Point(x, y, z); }
    private static BigDecimal sum(List<BigDecimal> parts) { return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static void refuses(Runnable operation, String why) { try { operation.run(); throw new AssertionError(why); } catch (IllegalArgumentException expected) { checks++; } }
    public static void main(String[] args) {
        var before = new ExactCombatState(n("100"), n("200"), n("40"), n("100"), false);
        var full = ThermalShellRules.absorb(before, n("12"));
        eq(full.absorbed(), n("12"), "fully shielded hit spends only actual mitigated damage");
        eq(full.state().shield(), n("28"), "one existing pool debit"); eq(full.state().health(), n("100"), "absorption never heals");
        var partial = ThermalShellRules.absorb(before, n("55"));
        eq(partial.absorbed(), n("40"), "partial exhausts only existing balance");
        eq(partial.state().health(), n("85"), "partial remainder harms HP"); eq(partial.state().shield(), BigDecimal.ZERO, "no secondary pool");
        var zero = ThermalShellRules.absorb(before, BigDecimal.ZERO);
        check(zero.state().equals(before) && zero.absorbed().signum() == 0, "zero produces no productive receipt");
        var lethal = ThermalShellRules.absorb(before, n("200"));
        check(lethal.state().isDead() && lethal.state().shield().signum() == 0, "shield never revives lethal remainder");
        eq(ThermalShellRules.absorb(lethal.state(), n("1")).absorbed(), BigDecimal.ZERO, "dead ledger never absorbs");
        var tiny = ThermalShellRules.absorb(ExactCombatState.full(HighPowerLevel.LV5), n("1e-200"));
        eq(tiny.absorbed(), n("1e-200"), "tiny actual absorption retains exact decimal precision");
        eq(ThermalShellRules.pulseBudget(n("100"), n("2")), n("2"), "total capped at real absorbed amount");
        eq(ThermalShellRules.pulseBudget(n("100"), n("20")), n("5"), "large hit caps total at five percent D");
        eq(ThermalShellRules.pulseBudget(n("100"), BigDecimal.ZERO), BigDecimal.ZERO, "no absorption no pulse");
        Random random = new Random(222);
        for (int i = 0; i < 12000; i++) {
            BigDecimal hp = BigDecimal.valueOf(1 + random.nextInt(1000000)), shield = BigDecimal.valueOf(random.nextInt(1000000));
            BigDecimal incoming = BigDecimal.valueOf(random.nextInt(2000000));
            var initial = new ExactCombatState(hp, hp, shield, n("1000000"), random.nextBoolean());
            var result = ThermalShellRules.absorb(initial, incoming);
            eq(result.absorbed(), shield.min(incoming), "one capped spend");
            eq(result.state().shield().add(result.absorbed()), shield, "existing S conserved");
            eq(result.state().health(), hp.subtract(incoming.subtract(result.absorbed()).min(hp)), "exact HP remainder");
            check(!result.state().shieldEnabled() && result.state().maxHealth().equals(initial.maxHealth()) && result.state().maxShield().equals(initial.maxShield()), "no generic forcefield or capacity change");
            BigDecimal total = ThermalShellRules.pulseBudget(n("1000000"), result.absorbed());
            for (int targets = 1; targets <= 4; targets++) {
                List<BigDecimal> shares = ThermalShellRules.shares(total, targets);
                eq(sum(shares), total, "one total budget at target count " + targets);
                for (BigDecimal share : shares) check(share.signum() >= 0 && share.compareTo(total) <= 0, "nonnegative capped share");
                if (targets > 1) eq(sum(shares.subList(0, targets - 1)), total.subtract(shares.get(targets - 1)), "rejected final target keeps its assigned remainder unspent");
            }
        }
        for (String amount : List.of("1e-256", "0.000000000000000001", "1", "5000000000000000000", "100000000000000000000")) {
            for (int targets = 1; targets <= 4; targets++) {
                var shares = ThermalShellRules.shares(n(amount), targets);
                eq(sum(shares), n(amount), "extreme exact conservation");
                for (BigDecimal share : shares) CombatAmounts.requireAmount(share, "admissible share");
            }
        }
        check(ThermalShellRules.shares(n("1"), 3).get(2).compareTo(ThermalShellRules.shares(n("1"), 3).get(0)) > 0, "final frozen slot owns finite-decimal remainder deterministically");
        refuses(() -> ThermalShellRules.shares(BigDecimal.ONE, 0), "empty target array rejected");
        refuses(() -> ThermalShellRules.shares(BigDecimal.ONE, 5), "target cap enforced before allocation");
        refuses(() -> ThermalShellRules.absorb(before, n("-1")), "negative mitigated damage cannot mint S");
        check(ThermalShellRules.inside(p(ThermalShellRules.RANGE,0,0), p(0,0,0)) && ThermalShellRules.inside(p(-ThermalShellRules.RANGE,0,0), p(0,0,0)), "omnidirectional exact configured radius");
        check(!ThermalShellRules.inside(p(Math.nextUp(ThermalShellRules.RANGE),0,0), p(0,0,0)) && !ThermalShellRules.inside(p(0,Math.nextUp(ThermalShellRules.RANGE),0), p(0,0,0)), "spherical range rejects horizontal and vertical overflow");
        check(!ThermalShellRules.inside(p(Double.NaN,0,0),p(0,0,0)) && !ThermalShellRules.inside(null,p(0,0,0)), "invalid geometry fails closed");
        check(ThermalShellRules.PULSE_INTERVAL == 15 && ThermalShellRules.PAYMENT_PERIOD == 20 && ThermalShellRules.MAX_TARGETS == 4 && ThermalShellRules.SCAN_LIMIT == 64, "separate physical rate, sustain period, targets and scan bounds");
        check(ThermalShellRules.ready(15,15) && !ThermalShellRules.ready(15,14) && !ThermalShellRules.ready(-1,15)
                && !ThermalShellRules.clock(Long.MAX_VALUE - 19), "exclusive persistent until and overflow bound");
        double[] activation = {2,1,.2,.02}, sustain = {.25,.1,.02,.002}, pulse = {1,.5,.1,.01};
        int[] cooldown = {10,6,3,1};
        for (int level = 2; level <= 5; level++) {
            BigDecimal q = BigDecimal.valueOf(level); int i = level - 2;
            check(ThermalShellRules.activationCost(q) == activation[i] && ThermalShellRules.sustainCost(q) == sustain[i]
                    && ThermalShellRules.pulseCost(q) == pulse[i] && ThermalShellRules.cooldownTicks(q) == cooldown[i], "approved LV" + level + " anchors");
        }
        double lastA = 2, lastS = .25, lastP = 1; BigDecimal lastCd = n("10");
        for (int i = 1; i <= 300; i++) {
            BigDecimal q = n("2").add(BigDecimal.valueOf(i).divide(n("100")));
            double a = ThermalShellRules.activationCost(q), s = ThermalShellRules.sustainCost(q), pulseCost = ThermalShellRules.pulseCost(q);
            BigDecimal cd = ThermalShellRules.cooldown(q);
            check(a < lastA && s < lastS && pulseCost < lastP && cd.compareTo(lastCd) < 0, "actual earned q smoothly improves all cost schedules");
            check(ThermalShellRules.cooldownTicks(q) >= 1 && ThermalShellRules.cooldownTicks(q) <= 10, "only final engine cooldown is tick-rounded");
            lastA = a; lastS = s; lastP = pulseCost; lastCd = cd;
        }
        System.out.println("PASS " + checks + " NEW222 exact shared-S, conserved pulse budget and continuous cost checks");
    }
}

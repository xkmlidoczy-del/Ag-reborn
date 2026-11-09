package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Pure NEW441 whole-cast conservation, real-tick schedule, and economic checks. */
public final class VectorCageRulesRegressionTest {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void rejected(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    public static void main(String[] args) {
        allocation();
        economicCurves();
        timing();
        System.out.println("PASS " + checks + " NEW441 exact allocation, curves, and real-tick checks");
    }

    private static void allocation() {
        for (BigDecimal d : List.of(BigDecimal.ONE, new BigDecimal(".1"), new BigDecimal("1E-256"),
                new BigDecimal("9E-255"), new BigDecimal("123456789.01234567890123456789"),
                new BigDecimal("4E19"), new BigDecimal("5E19"), CombatAmounts.CAP)) {
            for (boolean low : new boolean[]{false, true})
                for (int n = 1; n <= 8; n++) verifyPartition(d, low, n);
        }
        Random random = new Random(441);
        for (int i = 0; i < 1000; i++) verifyPartition(
                BigDecimal.valueOf(1 + random.nextInt(1_000_000_000), random.nextInt(32)),
                random.nextBoolean(), 1 + random.nextInt(8));
        check(VectorCageRules.damage(new BigDecimal("8"), false).compareTo(new BigDecimal("20")) == 0,
                "normal whole cast is 2.5D, not 2.5D per wall or pulse");
        check(VectorCageRules.damage(new BigDecimal("8"), true).compareTo(new BigDecimal("4")) == 0,
                "crouching whole cast is .5D");
        check(VectorCageRules.damage(CombatAmounts.CAP, false).equals(CombatAmounts.requireAmount(CombatAmounts.CAP, "cap")),
                "normal cast caps once at 1E20 before partition");
        check(VectorCageRules.damage(CombatAmounts.CAP, true).compareTo(new BigDecimal("5E19")) == 0,
                "low mode multiplies D before cap");
        for (int n : new int[]{Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE})
            rejected(() -> VectorCageRules.shares(BigDecimal.ONE, false, n), "target count outside 1..8 rejected");
        for (BigDecimal d : List.of(BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1E21"),
                new BigDecimal("1E-257")))
            rejected(() -> VectorCageRules.damage(d, false), "invalid damage fails closed");
        rejected(() -> VectorCageRules.damageShare(BigDecimal.ONE, -1, 0, 1), "negative pulse rejected");
        rejected(() -> VectorCageRules.damageShare(BigDecimal.ONE, 4, 0, 1), "fifth pulse rejected");
        rejected(() -> VectorCageRules.damageShare(BigDecimal.ONE, 0, 1, 1), "extra target rejected");
        rejected(() -> VectorCageRules.damageShare(BigDecimal.ONE, 0, -1, 1), "negative target rejected");
        List<BigDecimal> immutable = VectorCageRules.shares(BigDecimal.ONE, false, 3);
        try { immutable.set(0, BigDecimal.TEN); throw new AssertionError("shares must be immutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void verifyPartition(BigDecimal d, boolean low, int n) {
        BigDecimal whole = VectorCageRules.damage(d, low);
        BigDecimal ideal = d.multiply(new BigDecimal(low ? ".5" : "2.5")).min(CombatAmounts.CAP);
        check(whole.compareTo(ideal) <= 0 && ideal.subtract(whole).compareTo(new BigDecimal("1E-256")) < 0,
                "only unsupported final decimal sub-unit may be discarded");
        List<BigDecimal> shares = VectorCageRules.shares(d, low, n);
        check(shares.size() == 4 * n, "exactly four times frozen target count slots");
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal share = CombatAmounts.requireAmount(shares.get(i), "share");
            sum = sum.add(share);
            check(share.compareTo(VectorCageRules.damageShare(whole, i / n, i % n, n)) == 0,
                    "pulse-major access reproduces frozen allocation");
            if (i < shares.size() - 1) check(share.compareTo(shares.getFirst()) == 0,
                    "no early pulse or target can own decimal remainder");
        }
        check(sum.compareTo(whole) == 0, "all fixed shares sum exactly to whole damage");
        check(shares.getLast().compareTo(shares.getFirst()) >= 0, "only last slot receives nonnegative remainder");
        BigDecimal missed = shares.get(0);
        check(sum.subtract(missed).compareTo(whole.subtract(missed)) == 0,
                "discarding a missed slot never redistributes into later slots");
    }

    private static void economicCurves() {
        String[] cp = {"24", "12", "6", "2", ".1"};
        int[] cd = {40, 40, 40, 40, 4};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(VectorCageRules.cpCost(q) == Double.parseDouble(cp[i]), "whole CP anchor q=" + q);
            check(VectorCageRules.cooldownTicks(q) == cd[i], "native cooldown tick anchor q=" + q);
        }
        double previous = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 400; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i, 2));
            double cost = VectorCageRules.cpCost(q);
            check(Double.isFinite(cost) && cost > 0 && cost <= previous, "continuous CP is finite and monotone");
            previous = cost;
            int cooldown = VectorCageRules.cooldownTicks(q);
            check(cooldown >= 4 && cooldown <= 40, "cooldown remains within native tick anchors");
            check(BigDecimal.valueOf(cooldown).compareTo(VectorCageRules.theoreticalCooldown(q)) >= 0,
                    "cooldown rounds once upwards at native scheduling boundary");
        }
        check(VectorCageRules.cpCost(new BigDecimal("4.000001")) < 2,
                "economic growth does not quantize at integer levels");
    }

    private static void timing() {
        check(VectorCageRules.PULSES == 4 && VectorCageRules.LIFETIME == 80
                && VectorCageRules.SHRINK_TICKS == 6 && VectorCageRules.TARGET_FENCE == 20,
                "physical duration and target attempt fences are separate immutable budgets");
        check(VectorCageRules.PREVIEW_TICKS == cn.academy.port.core.SkillTuning.number("vector_cage", "duration_ticks") && VectorCageRules.REQUEST_FENCE == 5
                && VectorCageRules.SCAN_LIMIT == 64, "bounded preview, request rate, and candidate scan");
        int active = 0;
        for (int age = -20; age <= 100; age++) {
            int expected = -1, step = -1;
            for (int pulse = 0; pulse < 4; pulse++) if (age >= 6 + pulse * 20 && age <= 11 + pulse * 20) {
                expected = pulse; step = age - 6 - pulse * 20;
            }
            check(VectorCageRules.sweepIndex(age) == expected, "only explicit six-tick compression windows are active");
            check(VectorCageRules.sweepStep(age) == step, "real age determines one step, never catch-up work");
            if (expected >= 0) {
                active++;
                check(VectorCageRules.fromHalfWidth(age) == 4 - step * .625, "linear previous half-width");
                check(VectorCageRules.toHalfWidth(age) == 4 - (step + 1) * .625, "linear next half-width");
            }
            check(VectorCageRules.pulseIndex(age) == (step == 0 ? expected : -1), "exact pulse starts only");
        }
        check(active == 24, "four pulses have only twenty-four physical sweep ticks");
        check(VectorCageRules.sweepIndex(Long.MAX_VALUE) == -1
                && VectorCageRules.sweepIndex(Long.MIN_VALUE) == -1, "extreme ages cannot wrap into live phases");
        for (long age : new long[]{-1, 0, 5, 12, 25, 32, 79, 80, Long.MAX_VALUE})
            rejected(() -> VectorCageRules.fromHalfWidth(age), "inactive age cannot request a sweep");
        check(VectorCageRules.halfWidth(0) == 4 && VectorCageRules.halfWidth(5) == 4
                && VectorCageRules.halfWidth(6) == 3.375 && VectorCageRules.halfWidth(11) == .25
                && VectorCageRules.halfWidth(25) == .25 && VectorCageRules.halfWidth(26) == 3.375,
                "warmup, endpoints, idle and next pulse visual timing agree");
        rejected(() -> VectorCageRules.halfWidth(80), "visual geometry cannot extend immutable lifetime");
        check(VectorCageRules.ready(-1, 0, 20) && !VectorCageRules.ready(10, 29, 20)
                && VectorCageRules.ready(10, 30, 20) && !VectorCageRules.ready(10, 9, 20),
                "target real-tick attempt fence rejects rollback and premature retry");
        check(!VectorCageRules.ready(20, 20, 20) && !VectorCageRules.ready(-2, 30, 20)
                && !VectorCageRules.ready(-1, Long.MAX_VALUE, 20) && !VectorCageRules.ready(-1, 0, 0),
                "same-tick retry and invalid clocks or intervals fail closed");
    }
}

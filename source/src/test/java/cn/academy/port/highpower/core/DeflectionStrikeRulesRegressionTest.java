package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Independent exact-budget, native cadence and actual world-coordinate range invariants for NEW411. */
public final class DeflectionStrikeRulesRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) {
        double[] costs = {10, 5, 2, .5, .1}; int[] cooldowns = {10, 5, 4, 4, 4};
        for (int i=0; i<5; i++) {
            BigDecimal q = BigDecimal.valueOf(i+1);
            check(DeflectionStrikeRules.cpCost(q) == costs[i], "exact CP anchor " + q);
            check(DeflectionStrikeRules.cooldownTicks(q) == cooldowns[i], "native cooldown + physical fence " + q);
        }
        double previous = costs[0]; int previousCooldown = cooldowns[0];
        for (int i=101; i<=500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2); double cp = DeflectionStrikeRules.cpCost(q); int cd = DeflectionStrikeRules.cooldownTicks(q);
            check(Double.isFinite(cp) && cp > 0 && cp < previous, "fractional cost is continuously decreasing " + q);
            check(cd >= 4 && cd <= previousCooldown, "four native ticks never disappear at high growth " + q);
            previous = cp; previousCooldown = cd;
        }
        check(DeflectionStrikeRules.cpCost(new BigDecimal("3.00001")) < DeflectionStrikeRules.cpCost(new BigDecimal("3.00000")),
                "growth never quantizes to integer levels or UI percentages");
        check(DeflectionStrikeRules.cpCost(BigDecimal.ZERO) == 10 && DeflectionStrikeRules.cpCost(BigDecimal.TEN) == .1,
                "finite growth coordinates clamp");
        check(DeflectionStrikeRules.cooldownTicks(BigDecimal.ZERO) == 10 && DeflectionStrikeRules.cooldownTicks(BigDecimal.TEN) == 4,
                "clamped economics never bypass the independent physical fence");

        for (String text : new String[] {"0", "1e-256", "3e-256", "1e-30", ".3", "1", "10000000", "123456789012345.678912345678912345",
                "5000000000000000000", "1e20", "1e21"}) {
            BigDecimal d = new BigDecimal(text), whole = d.min(CombatAmounts.CAP);
            for (int count=1; count<=2; count++) {
                List<BigDecimal> shares = DeflectionStrikeRules.shares(d, count, false);
                check(shares.size() == count && shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(whole) == 0,
                        "one frozen whole budget is exactly conserved " + text + "/" + count);
                for (BigDecimal share : shares) check(CombatAmounts.requireAmount(share, "test share").compareTo(share) == 0,
                        "every share remains representable in the exact ledger");
                if (count == 2) {
                    check(shares.get(0).compareTo(shares.get(1)) <= 0, "last target owns any remainder");
                    BigDecimal remaining = shares.get(1);
                    check(remaining.compareTo(whole) <= 0 && shares.get(1).equals(remaining), "discarding first share cannot increase or reassign second share");
                    check(shares.get(1).subtract(shares.get(0)).compareTo(BigDecimal.ONE.scaleByPowerOfTen(-256)) <= 0,
                            "division differs by at most one supported decimal quantum");
                }
                check(DeflectionStrikeRules.shares(d, count, true).stream().allMatch(s -> s.signum() == 0),
                        "crouch creates no damage budget regardless of level");
            }
        }
        check(DeflectionStrikeRules.shares(new BigDecimal("1e-256"), 2, false).equals(List.of(BigDecimal.ZERO, new BigDecimal("1e-256"))),
                "minimum-quantum remainder is never rounded upward or destroyed");
        check(DeflectionStrikeRules.shares(BigDecimal.ONE, 0, false).isEmpty(), "no targets have no shares");
        check(DeflectionStrikeRules.damage(null).signum() == 0 && DeflectionStrikeRules.damage(BigDecimal.ONE.negate()).signum() == 0,
                "missing or negative amounts cannot mint damage");
        rejects(() -> DeflectionStrikeRules.shares(BigDecimal.ONE, -1, false), "negative target count rejects");
        rejects(() -> DeflectionStrikeRules.shares(BigDecimal.ONE, 3, false), "third enemy never creates another share");
        rejects(() -> DeflectionStrikeRules.damage(new BigDecimal("1e-257")), "unsupported subquantum amount rejects");
        rejects(() -> DeflectionStrikeRules.damage(new BigDecimal("1e257")), "unbounded exponent rejects before expansion");
        try { DeflectionStrikeRules.shares(BigDecimal.ONE, 2, false).set(1, BigDecimal.TEN); throw new AssertionError("mutable shares"); }
        catch (UnsupportedOperationException expected) { checks++; }

        var origin = new DeflectionStrikeRules.Point(0, 0, 0);
        check(DeflectionStrikeRules.within(origin, new DeflectionStrikeRules.Point(0, 0, DeflectionStrikeRules.RANGE)), "inclusive actual endpoint");
        check(!DeflectionStrikeRules.within(origin, new DeflectionStrikeRules.Point(0, 0, Math.nextUp(DeflectionStrikeRules.RANGE))), "no outward range epsilon");
        check(!DeflectionStrikeRules.within(origin, new DeflectionStrikeRules.Point(Double.NaN, 0, 1)), "nonfinite geometry refuses");
        check(DeflectionStrikeRules.endpoint(origin, new DeflectionStrikeRules.Point(0, 0, 0)) == null, "zero look cannot create a ray");
        Random random = new Random(411);
        for (int i=0; i<2000; i++) {
            var start = new DeflectionStrikeRules.Point(random.nextDouble()*59_999_000-29_999_500,
                    random.nextDouble()*1000-500, random.nextDouble()*59_999_000-29_999_500);
            double x = random.nextDouble()*2-1, y = random.nextDouble()*2-1, z = random.nextDouble()*2-1;
            double length = Math.sqrt(x*x+y*y+z*z);
            var end = DeflectionStrikeRules.endpoint(start, new DeflectionStrikeRules.Point(x/length, y/length, z/length));
            check(end != null && DeflectionStrikeRules.within(start, end) && start.distanceSquared(end) > 63.999,
                    "far-coordinate endpoint never overstates the actual path");
        }
        check(DeflectionStrikeRules.clock(0) && DeflectionStrikeRules.clock(Long.MAX_VALUE-12000), "bounded persistent clock accepted");
        check(!DeflectionStrikeRules.clock(-1) && !DeflectionStrikeRules.clock(Long.MAX_VALUE-11999), "rollback/overflow clocks refuse");
        check(DeflectionStrikeRules.RANGE == cn.academy.port.core.SkillTuning.number("deflection_strike", "reach") && DeflectionStrikeRules.RADIUS == .035 && DeflectionStrikeRules.KNOCKBACK == .35
                && DeflectionStrikeRules.MAX_TARGETS == 2 && DeflectionStrikeRules.CAST_FENCE == 4,
                "provisional bounded workbook contract remains explicit");
        System.out.println("DeflectionStrikeRulesRegressionTest passed " + checks + " checks");
    }
}

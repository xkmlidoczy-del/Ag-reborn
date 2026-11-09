package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.MathContext;
import static cn.academy.port.highpower.core.ZeroGapArmorRules.*;

/** Continuous theoretical CP/cooldown curves, with rounding restricted to the engine boundary. */
public final class ZeroGapArmorRulesRegressionTest {
    private static int checks;
    private static final MathContext MC = new MathContext(30);

    public static void main(String[] args) {
        String[] activation = {"8", "4", "2", ".5", ".05"};
        int[] cooldown = {60, 30, 16, 10, 1};
        for (int level = 1; level <= 5; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            equal(theoreticalActivationCost(q), d(activation[level - 1]), "exact approved activation anchor");
            check(activationCost(q) == Double.parseDouble(activation[level - 1]), "explicit CP engine conversion");
            equal(theoreticalCooldown(q), BigDecimal.valueOf(cooldown[level - 1]), "exact approved theoretical cooldown anchor");
            check(cooldownTicks(q) == cooldown[level - 1], "exact integer cooldown anchor");
            equal(theoreticalSustainCost(q), BigDecimal.ONE, "one CP sustain at every anchor");
            check(sustainCost(q) == 1, "sustain engine conversion");
        }
        check(STARTUP_TICKS == 6 && BREAK_TICKS == 4 && SUSTAIN_INTERVAL_TICKS == 20, "approved physical cadence");
        check(BREAK_TICKS == ZeroGapArmorTiming.BREAK_TICKS, "break debt uses existing persisted timing authority");
        BigDecimal previousCost = theoreticalActivationCost(BigDecimal.ONE), previousCooldown = theoreticalCooldown(BigDecimal.ONE);
        for (int step = 1; step <= 400; step++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(step, 2));
            BigDecimal cost = theoreticalActivationCost(q), ticks = theoreticalCooldown(q);
            check(cost.compareTo(previousCost) < 0, "strictly decreasing continuous activation");
            check(ticks.compareTo(previousCooldown) < 0, "strictly decreasing continuous theoretical cooldown");
            check(cost.compareTo(d(".05")) >= 0 && cost.compareTo(d("8")) <= 0, "activation never overshoots approved bounds");
            check(ticks.compareTo(BigDecimal.ONE) >= 0 && ticks.compareTo(d("60")) <= 0, "cooldown never overshoots approved bounds");
            check(cooldownTicks(q) == ContinuousHighPowerGrowth.ceilCooldownTicks(ticks), "ceil only at engine scheduling boundary");
            check(BigDecimal.valueOf(cooldownTicks(q)).compareTo(ticks) >= 0 && BigDecimal.valueOf(cooldownTicks(q) - 1).compareTo(ticks) < 0, "minimum enclosing physical cooldown");
            equal(theoreticalSustainCost(q), BigDecimal.ONE, "sustain independent of fractional growth");
            previousCost = cost;
            previousCooldown = ticks;
        }
        BigDecimal epsilon = d(".00000001");
        for (int level = 2; level <= 4; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            smooth(q, epsilon, true);
            smooth(q, epsilon, false);
        }
        check(theoreticalActivationCost(d("3.000000001")).compareTo(theoreticalActivationCost(d("3"))) < 0, "sub-percentage activation growth is not rounded away");
        check(theoreticalCooldown(d("3.000000001")).compareTo(theoreticalCooldown(d("3"))) < 0, "sub-tick theoretical cooldown remains continuous");
        check(cooldownTicks(d("4.99999999")) == 2 && cooldownTicks(d("5")) == 1, "ceil retains the final fractional tick until exact level five");
        equal(theoreticalActivationCost(d("-1")), d("8"), "finite low q clamps through shared growth");
        equal(theoreticalActivationCost(d("999")), d(".05"), "finite high q clamps through shared growth");
        equal(theoreticalCooldown(d("-1")), d("60"), "low cooldown q clamp");
        equal(theoreticalCooldown(d("999")), BigDecimal.ONE, "high cooldown q clamp");
        reject(() -> theoreticalActivationCost(d("1e1000")), "bounded q representation prevents hostile scale");
        reject(() -> theoreticalCooldown(d("1e-1000")), "bounded q representation prevents hostile expansion");
        reject(() -> theoreticalSustainCost(d("1e1000")), "constant sustain still validates q representation");
        System.out.println("ZeroGapArmorRulesRegressionTest: " + checks + " checks passed");
    }

    private static void smooth(BigDecimal q, BigDecimal epsilon, boolean cost) {
        BigDecimal middle = cost ? theoreticalActivationCost(q) : theoreticalCooldown(q);
        BigDecimal left = cost ? theoreticalActivationCost(q.subtract(epsilon)) : theoreticalCooldown(q.subtract(epsilon));
        BigDecimal right = cost ? theoreticalActivationCost(q.add(epsilon)) : theoreticalCooldown(q.add(epsilon));
        BigDecimal leftSlope = middle.subtract(left).divide(epsilon, MC), rightSlope = right.subtract(middle).divide(epsilon, MC);
        check(left.subtract(middle).abs().compareTo(d(".000001")) < 0 && right.subtract(middle).abs().compareTo(d(".000001")) < 0, "no anchor discontinuity");
        check(leftSlope.subtract(rightSlope).abs().compareTo(d(".00001")) < 0, "shared smooth tangent across each growth anchor");
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal actual, BigDecimal expected, String why) { check(actual.compareTo(expected) == 0, why); }
    private static void reject(Runnable action, String why) { try { action.run(); throw new AssertionError(why); } catch (IllegalArgumentException expected) { checks++; } }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
}

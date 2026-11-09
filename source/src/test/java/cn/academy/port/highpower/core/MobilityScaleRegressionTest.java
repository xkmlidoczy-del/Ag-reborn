package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import java.math.BigDecimal;

/** Reach scaling: LV1 unchanged, LV5 doubled, monotone and bounded, and only distances (never speeds) are scaled. */
public final class MobilityScaleRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) {
        check(MobilityScale.reach(new BigDecimal("1")) == 1 && MobilityScale.reach(null) == 1 && MobilityScale.reach(new BigDecimal("0.2")) == 1, "LV1, missing and sub-LV1 positions keep the original reach");
        check(MobilityScale.reach(new BigDecimal("5")) == 2 && MobilityScale.reach(new BigDecimal("9")) == 2, "LV5 and beyond cap at double");
        check(MobilityScale.reach(new BigDecimal("3")) == 1.5, "LV3 is halfway");
        double previous = 1;
        for (int i = 0; i <= 400; i++) {
            double r = MobilityScale.reach(new BigDecimal("1").add(new BigDecimal(i).divide(new BigDecimal(100))));
            check(r >= previous && r >= 1 && r <= 2, "monotone and bounded: " + r);
            previous = r;
        }
        check(PhaseCorridorRules.validJump(32, 32) && !PhaseCorridorRules.validJump(32.01, 32) && PhaseCorridorRules.validJump(64, 64) && !PhaseCorridorRules.validJump(64.01, 64)
                && !PhaseCorridorRules.validJump(1.99, 64) && !PhaseCorridorRules.validJump(Double.NaN, 64) && !PhaseCorridorRules.validJump(10, Double.NaN), "scaled jump validation keeps the lower bound and rejects invalid numbers");
        check(PhaseCorridorRules.validJump(32) && !PhaseCorridorRules.validJump(33), "the unscaled rule is unchanged");
        var unit = ContinuousHighPowerGrowth.sample(3, 0).damage();
        check(MobilityScale.categoryDamage("meltdowner", unit).compareTo(unit.multiply(new BigDecimal("1.5"))) == 0, "meltdowner damage unit is 1.5x");
        for (String other : new String[] {"electromaster", "teleporter", "vecmanip", "classic", null}) check(MobilityScale.categoryDamage(other, unit) == unit, other + " keeps the shared unit");
        check(MobilityScale.categoryDamage("meltdowner", new BigDecimal("9e19")).compareTo(CombatAmounts.CAP) == 0, "the boosted unit never exceeds the per-hit ceiling");
        // Classic damage lifted onto the level unit.
        var d5 = ContinuousHighPowerGrowth.sample(5, 0).damage();
        var d1 = ContinuousHighPowerGrowth.sample(1, 0).damage();
        check(ClassicDamageScale.scaled(110, d5, 50) == d5.multiply(new BigDecimal("2.2")).doubleValue(), "full-mastery railgun is 2.2 D at LV5");
        check(ClassicDamageScale.scaled(5, d1, 50) == d1.multiply(new BigDecimal("0.1")).doubleValue(), "weak arc at LV1 is a tenth of D");
        check(ClassicDamageScale.scaled(110, d1, 50) == 22, "LV1 railgun follows the curve below the original amount");
        check(ClassicDamageScale.scaled(7, BigDecimal.ONE, 50) == .14, "positive fractional damage has no raw floor");
        check(ClassicDamageScale.scaled(1e18, d5, 1) == 1e20, "held at the per-hit ceiling only");
        for (double bad : new double[] {Double.NaN, -1, 0, Double.POSITIVE_INFINITY}) { double out = ClassicDamageScale.scaled(bad, d5, 50); check(Double.isNaN(bad) ? Double.isNaN(out) : out == bad, "invalid input passes through: " + bad); }
        check(ClassicDamageScale.scaled(10, null, 50) == 10 && ClassicDamageScale.scaled(10, d5, 0) == 10 && ClassicDamageScale.scaled(10, d5, Double.NaN) == 10, "missing unit or bad divisor leaves the amount");
        double lastScaled = 0;
        for (double raw = 1; raw < 1000; raw += 7) { double v = ClassicDamageScale.scaled(raw, d5, 50); check(v >= lastScaled, "monotone in the classic amount"); lastScaled = v; }
        // Phase veil: a quarter of each hit comes out of the shield pool, never more than the pool, never from health.
        var veil = PhaseVeilRules.absorb(new BigDecimal("1000"), new BigDecimal("1e9"));
        check(veil.absorbed().compareTo(new BigDecimal("250")) == 0 && veil.remaining().compareTo(new BigDecimal("750")) == 0, "a quarter is absorbed");
        var thin = PhaseVeilRules.absorb(new BigDecimal("1000"), new BigDecimal("100"));
        check(thin.absorbed().compareTo(new BigDecimal("100")) == 0 && thin.remaining().compareTo(new BigDecimal("900")) == 0, "limited by the pool, one for one");
        var empty = PhaseVeilRules.absorb(new BigDecimal("1000"), BigDecimal.ZERO);
        check(empty.absorbed().signum() == 0 && empty.remaining().compareTo(new BigDecimal("1000")) == 0, "an empty pool absorbs nothing");
        check(PhaseVeilRules.absorb(BigDecimal.ZERO, new BigDecimal("5")).absorbed().signum() == 0 && PhaseVeilRules.absorb(null, null).remaining().signum() == 0, "nothing to absorb");
        for (int i = 1; i <= 200; i++) {
            var dmg = new BigDecimal(i * 37); var pool = new BigDecimal(i * 11); var r = PhaseVeilRules.absorb(dmg, pool);
            check(r.absorbed().add(r.remaining()).compareTo(dmg) == 0 && r.absorbed().compareTo(pool) <= 0 && r.absorbed().compareTo(dmg.multiply(new BigDecimal("0.25"))) <= 0, "conserves the hit and respects both limits");
        }
        // Rail relay hop length: unscaled overload unchanged, scaled overload honours the longer hop and rejects invalid ranges.
        var from = java.util.List.of(new V3(0, 0, 0));
        check(RailRelayRules.addAnchor(from, new V3(RailRelayRules.MAX_SEGMENT, 0, 0)).isPresent() && RailRelayRules.addAnchor(from, new V3(RailRelayRules.MAX_SEGMENT + 1e-6, 0, 0)).isEmpty(), "the unscaled hop respects the table");
        check(RailRelayRules.addAnchor(from, new V3(64, 0, 0), 64).isPresent() && RailRelayRules.addAnchor(from, new V3(65, 0, 0), 64).isEmpty(), "a scaled hop reaches 64 m and no further");
        check(RailRelayRules.addAnchor(from, new V3(1, 0, 0), Double.NaN).isEmpty() && RailRelayRules.addAnchor(from, new V3(1, 0, 0), 0).isEmpty(), "invalid ranges are refused");
        // Phase sidestep: distance scales with the level, the geometry honours the admitted distance and nothing beyond the level range.
        check(Math.abs(PhaseSidestepRules.distance(BigDecimal.ONE) - PhaseSidestepRules.DISTANCE) < 1e-12 && Math.abs(PhaseSidestepRules.distance(BigDecimal.valueOf(5)) - PhaseSidestepRules.MAX_DISTANCE) < 1e-12, "sidestep scales from the configured baseline to twice that at LV5");
        var src = new SafeLandingProbeGeometry.Point(0, 64, 0);
        var plain = PhaseSidestepGeometry.destination(src, 0, false);
        double fullStep = PhaseSidestepRules.MAX_DISTANCE;
        var scaled = PhaseSidestepGeometry.destination(src, 0, false, fullStep);
        check(Math.abs(plain.x() + PhaseSidestepRules.DISTANCE) < 1e-9 && Math.abs(scaled.x() + fullStep) < 1e-9 && Math.abs(scaled.y() - 64) < 1e-12 && Math.abs(scaled.z()) < 1e-9, "the maximum step goes exactly twice as far on the same side");
        check(Math.abs(PhaseSidestepGeometry.destination(src, 0, true, fullStep).x() - fullStep) < 1e-9, "the left side mirrors the scaled step");
        for (double bad : new double[] {PhaseSidestepRules.DISTANCE - 1e-6, fullStep + 1e-6, Double.NaN, 0}) { boolean refused = false; try { PhaseSidestepGeometry.destination(src, 0, false, bad); } catch (IllegalArgumentException expected) { refused = true; } check(refused, "distance outside the level range is refused: " + bad); }
        System.out.println("PASS " + checks + " mobility reach scaling checks");
    }
}

package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Pure Java regression entry point; no Minecraft, Gradle daemon, network or assertion flag needed. */
public final class ContinuousHighPowerGrowthRegressionTest {
    private static final MathContext CHECK = new MathContext(64, RoundingMode.HALF_EVEN);
    private static int assertions;

    public static void main(String[] args) {
        exactAnchorsAndLevelBoundaries();
        inputClampsAndCorruption();
        damageAndPoolsAreStrictlyMonotone();
        scalarCurvesAreContinuousAndShapePreserving();
        derivativesJoinAtAllBoundaries();
        exactDecimalReferenceAndDeterminism();
        engineRoundingOccursOnlyAtSchedulingBoundary();
        boundedUntrustedDecimalRepresentations();
        System.out.println("PASS " + assertions + " continuous high-power growth assertions");
    }

    private static void exactAnchorsAndLevelBoundaries() {
        truth(ContinuousHighPowerGrowth.CURVE_VERSION == 2, "rebalance invalidates the old curve schema");
        String[] expected = {"10", "178", "3162", "56234", "1000000"};
        for (int level = 1; level <= 5; level++) {
            var actual = ContinuousHighPowerGrowth.sample(level, 0);
            var preset = HighPowerLevel.forLevel(level);
            eq(BigDecimal.valueOf(level), actual.position(), "exact position");
            eq(preset.damage(), actual.damage(), "exact damage anchor");
            eq(bd(expected[level - 1]), actual.damage(), "approved rebalance anchor");
            eq(preset.maxHealth(), actual.maxHealth(), "exact HP anchor");
            eq(preset.maxShield(), actual.maxShield(), "exact shield anchor");
            if (level < 5) {
                truth(ContinuousHighPowerGrowth.sample(level, 1)
                        .equals(ContinuousHighPowerGrowth.sample(level + 1, 0)), "level boundary identity");
            }
        }
        var top = ContinuousHighPowerGrowth.sample(5, 0);
        for (double progress : new double[] {0, 0.0001, 0.5, 1, Double.MAX_VALUE, Double.NaN}) {
            truth(top.equals(ContinuousHighPowerGrowth.sample(5, progress)), "LV5 fixed upper endpoint");
        }
        eq(bd("20000000"), top.maxHealth(), "LV5 HP is twenty damage units");
        eq(bd("10000000"), top.maxShield(), "LV5 shield is ten damage units");
    }

    private static void inputClampsAndCorruption() {
        for (double bad : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                -0.0, -0.01, -Double.MAX_VALUE}) {
            eq(BigDecimal.valueOf(3), ContinuousHighPowerGrowth.position(3, bad), "corrupt progress fails closed");
        }
        eq(bd("4"), ContinuousHighPowerGrowth.position(3, Double.MAX_VALUE), "finite progress overshoot clamps");
        eq(bd("1"), ContinuousHighPowerGrowth.position(Integer.MIN_VALUE, 1), "negative level clamps");
        eq(bd("1"), ContinuousHighPowerGrowth.position(0, 0.9), "level below domain clamps");
        eq(bd("5"), ContinuousHighPowerGrowth.position(Integer.MAX_VALUE, 0), "huge level clamps");
        eq(bd("2.0000000000000001"), ContinuousHighPowerGrowth.position(2, 1e-16),
                "decimal position keeps progress that binary level addition would lose");
        eq(bd("2"), ContinuousHighPowerGrowth.position(2, Double.MIN_VALUE),
                "subnormal progress safely rounds at documented precision");
        eq(bd("1"), ContinuousHighPowerGrowth.clampPosition(bd("-100")), "decimal lower clamp");
        eq(bd("5"), ContinuousHighPowerGrowth.sample(bd("999999")).position(), "decimal upper clamp");
        eq(bd("2.5"), ContinuousHighPowerGrowth.requirePosition(bd("2.50000")), "canonical checkpoint");
        rejects(() -> ContinuousHighPowerGrowth.requirePosition(BigDecimal.ZERO));
        rejects(() -> ContinuousHighPowerGrowth.requirePosition(bd("5.0001")));
        rejects(() -> ContinuousHighPowerGrowth.requirePosition(null));
    }

    private static void damageAndPoolsAreStrictlyMonotone() {
        var previous = ContinuousHighPowerGrowth.sample(1, 0);
        for (int i = 1; i <= 1600; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i).divide(bd("400")));
            var current = ContinuousHighPowerGrowth.sample(q);
            truth(current.damage().compareTo(previous.damage()) > 0, "strictly increasing damage at q=" + q);
            truth(current.maxHealth().compareTo(previous.maxHealth()) > 0, "strictly increasing health");
            truth(current.maxShield().compareTo(previous.maxShield()) > 0, "strictly increasing shield");
            eq(current.damage().multiply(bd("20")), current.maxHealth(), "exact continuous HP ratio");
            eq(current.damage().multiply(BigDecimal.TEN), current.maxShield(), "exact continuous shield ratio");
            truth(current.maxHealth().compareTo(CombatAmounts.CAP) <= 0, "global cap never exceeded");
            truth(current.damage().precision() <= ContinuousHighPowerGrowth.OUTPUT_PRECISION,
                    "bounded output significant digits");
            previous = current;
        }
        for (int level = 1; level <= 4; level++) {
            var start = ContinuousHighPowerGrowth.sample(level, 0);
            var after = ContinuousHighPowerGrowth.sample(level, 1e-16);
            var before = ContinuousHighPowerGrowth.sample(level, Math.nextDown(1.0));
            var end = ContinuousHighPowerGrowth.sample(level, 1);
            truth(after.damage().compareTo(start.damage()) > 0, "no lower-anchor snap at double near-zero");
            truth(before.damage().compareTo(end.damage()) < 0, "no upper-anchor snap at double near-one");
            relativeWithin(start.damage(), after.damage(), bd("1e-14"), "near lower anchor");
            relativeWithin(end.damage(), before.damage(), bd("1e-14"), "near upper anchor");
        }
        BigDecimal unrounded = ContinuousHighPowerGrowth.sample(2, 0.123456789).damage();
        truth(unrounded.scale() > 0, "growth values retain fractions instead of integer rounding");
    }

    private static void scalarCurvesAreContinuousAndShapePreserving() {
        checkScalar(new String[] {"200", "600", "2000", "10000", "100000"}, 1);
        checkScalar(new String[] {"10", "50", "250", "2500", "25000"}, 1);
        checkScalar(new String[] {"80", "65", "50", "35", "20"}, -1);
        checkScalar(new String[] {"30", "30", "30", "30", "30"}, 0);

        String[] reversals = {"1", "100", "10", "10", "1000"};
        var reversible = ContinuousHighPowerGrowth.curve(reversals);
        for (int level = 1; level < 5; level++) {
            BigDecimal a = bd(reversals[level - 1]);
            BigDecimal b = bd(reversals[level]);
            for (int i = 0; i <= 40; i++) {
                BigDecimal value = reversible.value(level, i / 40.0);
                truth(value.compareTo(a.min(b)) >= 0 && value.compareTo(a.max(b)) <= 0,
                        "no overshoot across flat/reversing generic anchors");
            }
        }

        var extreme = ContinuousHighPowerGrowth.curve("1e-200", "1e-150", "1e-100", "1e-50", "1e20");
        for (int level = 1; level <= 4; level++) {
            BigDecimal value = extreme.value(level, 0.789);
            truth(value.signum() > 0 && value.compareTo(CombatAmounts.CAP) <= 0, "bounded full-domain math");
            CombatAmounts.requirePositiveAmount(value, "extreme output remains ledger-compatible");
        }
        var steep = ContinuousHighPowerGrowth.curve("1e-200", "1e20", "1e-200", "1e20", "1e-200");
        truth(steep.value(1, 0.5).signum() > 0, "maximum anchor ratio remains finite");
        eq(steep.value(1, 0.5), steep.value(2, 0.5), "opposite full-range intervals agree at midpoint");
    }

    private static void checkScalar(String[] anchors, int direction) {
        var curve = ContinuousHighPowerGrowth.curve(anchors);
        for (int level = 1; level <= 5; level++) {
            eq(bd(anchors[level - 1]), curve.value(level, 0), "generic exact anchor");
            if (level < 5) eq(curve.value(level, 1), curve.value(level + 1, 0), "generic level continuity");
        }
        BigDecimal previous = curve.value(1, 0);
        for (int i = 1; i <= 400; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i).divide(bd("100")));
            BigDecimal current = curve.value(q);
            truth(Integer.signum(current.compareTo(previous)) == direction, "generic shape monotonicity");
            previous = current;
        }
    }

    private static void derivativesJoinAtAllBoundaries() {
        var curves = new ContinuousHighPowerGrowth.Curve[] {
                ContinuousHighPowerGrowth.curve("10", "178", "3162", "56234", "1000000"),
                ContinuousHighPowerGrowth.curve("200", "600", "2000", "10000", "100000"),
                ContinuousHighPowerGrowth.curve("10", "50", "250", "2500", "25000"),
                ContinuousHighPowerGrowth.curve("80", "65", "50", "35", "20")
        };
        BigDecimal h = bd("0.000001");
        for (var curve : curves) {
            for (int level = 2; level <= 4; level++) {
                BigDecimal q = BigDecimal.valueOf(level);
                BigDecimal anchor = curve.value(q);
                BigDecimal left = anchor.subtract(curve.value(q.subtract(h))).divide(h, CHECK);
                BigDecimal right = curve.value(q.add(h)).subtract(anchor).divide(h, CHECK);
                relativeWithin(left, right, bd("0.0001"), "C1 matching one-sided derivatives at " + level);
            }
            for (int endpoint : new int[] {1, 5}) {
                BigDecimal q = BigDecimal.valueOf(endpoint);
                BigDecimal direction = endpoint == 1 ? BigDecimal.ONE : BigDecimal.ONE.negate();
                BigDecimal coarseH = bd("0.0001");
                BigDecimal fineH = bd("0.00001");
                BigDecimal coarseSlope = curve.value(q.add(direction.multiply(coarseH)))
                        .subtract(curve.value(q)).abs().divide(coarseH, CHECK);
                BigDecimal fineSlope = curve.value(q.add(direction.multiply(fineH)))
                        .subtract(curve.value(q)).abs().divide(fineH, CHECK);
                truth(fineSlope.compareTo(coarseSlope.multiply(bd("0.11"))) < 0,
                        "zero endpoint derivative joins clamped domain at " + endpoint);
            }
            BigDecimal midpoint = bd("2.5");
            BigDecimal middle = curve.value(midpoint);
            BigDecimal fromLeft = middle.subtract(curve.value(midpoint.subtract(h))).divide(h, CHECK);
            BigDecimal fromRight = curve.value(midpoint.add(h)).subtract(middle).divide(h, CHECK);
            relativeWithin(fromLeft, fromRight, bd("0.0001"), "near-endpoint evaluation branches join smoothly");
        }
    }

    private static void exactDecimalReferenceAndDeterminism() {
        BigDecimal expected = bd("1e23").sqrt(CHECK)
                .round(new MathContext(40, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        var equalSlopes = ContinuousHighPowerGrowth.curve("1e7", "1e10", "1e13", "1e16", "5e18");
        eq(expected, equalSlopes.value(2, 0.5),
                "equal log slopes yield independently computed decimal geometric mean");
        var baseTen = ContinuousHighPowerGrowth.curve("1", "10", "100", "1000", "10000");
        eq(bd("1000").sqrt(CHECK).round(new MathContext(40, RoundingMode.HALF_EVEN)),
                baseTen.value(2, 0.5), "40-digit reference with noninteger result");
        BigDecimal[] anchors = {bd("1"), bd("10"), bd("100"), bd("1000"), bd("10000")};
        var isolated = ContinuousHighPowerGrowth.curve(anchors);
        anchors[2] = bd("99999");
        eq(bd("100"), isolated.value(3, 0), "defensive copy prevents mutable configuration aliasing");
        var original = ContinuousHighPowerGrowth.sample(4, 0.987654321);
        for (int i = 0; i < 100; i++) {
            truth(original.equals(ContinuousHighPowerGrowth.sample(4, 0.987654321)),
                    "repeated identical inputs are bit-for-bit decimal deterministic");
        }
    }

    private static void engineRoundingOccursOnlyAtSchedulingBoundary() {
        var cooldown = ContinuousHighPowerGrowth.curve("80", "65", "50", "35", "20");
        BigDecimal duration = cooldown.value(2, 0.123456789);
        truth(duration.scale() > 0, "theoretical CD remains continuous");
        int scheduled = ContinuousHighPowerGrowth.ceilCooldownTicks(duration);
        truth(bd(Integer.toString(scheduled)).compareTo(duration) >= 0
                        && bd(Integer.toString(scheduled - 1)).compareTo(duration) < 0,
                "only engine boundary uses ceiling");
        truth(ContinuousHighPowerGrowth.ceilCooldownTicks(bd("12")) == 12, "exact ticks unchanged");
        truth(ContinuousHighPowerGrowth.ceilCooldownTicks(bd("12.00000000000000000001")) == 13,
                "positive fractions never truncated");
        truth(ContinuousHighPowerGrowth.ceilCooldownTicks(bd("0.01")) == 1, "minimum one tick");
        truth(ContinuousHighPowerGrowth.ceilCooldownTicks(BigDecimal.ZERO) == 1, "zero theoretical floor");
        truth(ContinuousHighPowerGrowth.ceilCooldownTicks(CombatAmounts.CAP) == Integer.MAX_VALUE,
                "engine bound never overflows int");
        rejects(() -> ContinuousHighPowerGrowth.ceilCooldownTicks(bd("-0.01")));
    }

    private static void boundedUntrustedDecimalRepresentations() {
        for (String corrupt : new String[] {"0e-2147483647", "1e-2147483647", "1e2147483647",
                "1e257", "1e-257", "1." + "1".repeat(100)}) {
            rejects(() -> ContinuousHighPowerGrowth.clampPosition(bd(corrupt)));
            rejects(() -> ContinuousHighPowerGrowth.requirePosition(bd(corrupt)));
            rejects(() -> ContinuousHighPowerGrowth.curve(bd(corrupt), bd("2"), bd("3"), bd("4"), bd("5")));
        }
        for (String corrupt : new String[] {"NaN", "Infinity", "-1", "0", "1e21", "1e-201",
                "1." + "1".repeat(40)}) {
            rejects(() -> ContinuousHighPowerGrowth.curve(corrupt, "2", "3", "4", "5"));
        }
        rejects(() -> ContinuousHighPowerGrowth.curve("1", "2", "3", "4"));
        rejects(() -> ContinuousHighPowerGrowth.curve((BigDecimal[]) null));
        rejects(() -> ContinuousHighPowerGrowth.curve((String[]) null));
        rejects(() -> ContinuousHighPowerGrowth.curve(BigDecimal.ONE, null, bd("3"), bd("4"), bd("5")));
        rejects(() -> ContinuousHighPowerGrowth.sample((BigDecimal) null));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static void eq(BigDecimal expected, BigDecimal actual, String reason) {
        truth(expected.compareTo(actual) == 0, reason + ": expected " + expected + ", got " + actual);
    }

    private static void relativeWithin(BigDecimal expected, BigDecimal actual, BigDecimal tolerance, String reason) {
        BigDecimal difference = expected.subtract(actual).abs();
        truth(difference.compareTo(expected.abs().multiply(tolerance)) <= 0,
                reason + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("expected invalid input to be rejected");
    }

    private static void truth(boolean condition, String reason) {
        assertions++;
        if (!condition) throw new AssertionError(reason);
    }
}

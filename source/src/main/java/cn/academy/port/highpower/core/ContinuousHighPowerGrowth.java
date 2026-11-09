package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure, bounded continuous balance curves. Integer levels remain unlock gates owned by classic
 * progression; this class only evaluates q = level + normalized classic progress.
 *
 * <p>The interpolant is monotone cubic Hermite in natural-log space. Shared harmonic-mean
 * tangents make adjacent intervals C1; zero endpoint tangents join the clamped ends smoothly.
 * Strictly increasing anchors give a strictly increasing mathematical curve before the final
 * clamp. As with any finite representation, changes below the 40-significant-digit output
 * resolution can compare equal. There is no integer/percentage/tick quantization here.
 *
 * <p>All amount arithmetic is decimal. Logs use four square-root reductions and 96 atanh
 * terms; exp uses at most 13 halvings, 96 Taylor terms and 13 squarings. The 64-digit working
 * precision leaves 24 guard digits before HALF_EVEN output rounding. On the admitted
 * [1e-200, 1e20] anchor domain, truncation error is below 1e-100 and conservative accumulated
 * relative roundoff is below 1e-55, well below the 40-digit output rounding. Anchor evaluations
 * return the exact supplied decimals. Curves precompute their logs/tangents once; cache evaluated
 * snapshots in the caller rather than evaluating transcendental functions on every game tick.
 */
public final class ContinuousHighPowerGrowth {
    /** Change when balance anchors or interpolation semantics change, for persisted checkpoints. */
    public static final int CURVE_VERSION = 2;
    public static final int OUTPUT_PRECISION = 40;
    public static final int MAX_INPUT_PRECISION = 64;
    public static final int MAX_INPUT_SCALE = 256;

    private static final MathContext OUTPUT = new MathContext(OUTPUT_PRECISION, RoundingMode.HALF_EVEN);
    private static final MathContext WORK = new MathContext(64, RoundingMode.HALF_EVEN);
    private static final BigDecimal MIN_POSITION = BigDecimal.ONE;
    private static final BigDecimal MAX_POSITION = BigDecimal.valueOf(5);
    private static final BigDecimal MIN_ANCHOR = new BigDecimal("1e-200");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal EXP_REDUCTION_LIMIT = new BigDecimal("0.125");
    private static final BigDecimal EXP_LIMIT = BigDecimal.valueOf(512);
    private static final BigDecimal LN_TEN = new BigDecimal(
            "2.3025850929940456840179914546843642076011014886287729760333279009675726096773524802");
    private static final BigDecimal MAX_TICKS = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final int SERIES_TERMS = 96;
    private static final int MAX_EXP_HALVINGS = 13;

    private static final Curve DAMAGE = curve("10", "178", "3162", "56234", "1000000");

    private ContinuousHighPowerGrowth() {}

    public record Snapshot(BigDecimal position, BigDecimal damage,
                           BigDecimal maxHealth, BigDecimal maxShield) {}

    /** Corrupted nonfinite progress never grants advancement; finite overshoots are clipped. */
    public static double normalizedProgress(double progress) {
        if (!Double.isFinite(progress) || progress <= 0) return 0;
        return Math.min(1, progress);
    }

    /** No binary addition of level + progress, and no binary conversion of any combat amount. */
    public static BigDecimal position(int level, double progress) {
        if (level < 1) return MIN_POSITION;
        if (level >= 5) return MAX_POSITION;
        // BigDecimal.valueOf also bounds subnormal-double input work to a few hundred digits.
        return clampPosition(BigDecimal.valueOf(level)
                .add(BigDecimal.valueOf(normalizedProgress(progress))).round(OUTPUT));
    }

    /** Strict checkpoint validation. Resource bounds are checked before any scale expansion. */
    public static BigDecimal requirePosition(BigDecimal position) {
        validateRepresentation(position, "growth position");
        if (position.compareTo(MIN_POSITION) < 0 || position.compareTo(MAX_POSITION) > 0) {
            throw new IllegalArgumentException("growth position must be between 1 and 5");
        }
        return canonical(position.round(OUTPUT));
    }

    /** Mathematical input clamp; persisted checkpoints should use requirePosition instead. */
    public static BigDecimal clampPosition(BigDecimal position) {
        validateRepresentation(position, "growth position");
        return canonical(position.max(MIN_POSITION).min(MAX_POSITION).round(OUTPUT));
    }

    public static Snapshot sample(int level, double progress) {
        return sample(position(level, progress));
    }

    public static Snapshot sample(BigDecimal position) {
        BigDecimal q = clampPosition(position);
        BigDecimal damage = DAMAGE.value(q);
        // Pool ratios remain exact in the decimal ledger; never round an amount to integer units.
        BigDecimal health = CombatAmounts.requirePositiveAmount(
                damage.multiply(BigDecimal.valueOf(20)).min(CombatAmounts.CAP), "growth health");
        BigDecimal shield = CombatAmounts.requirePositiveAmount(
                damage.multiply(BigDecimal.TEN).min(CombatAmounts.CAP), "growth shield");
        return new Snapshot(q, damage, health, shield);
    }

    /** Five LV1..LV5 positive anchors. Increasing, decreasing and flat sequences are supported. */
    public static Curve curve(BigDecimal... anchors) {
        return new Curve(anchors);
    }

    public static Curve curve(String... anchors) {
        Objects.requireNonNull(anchors, "anchors");
        if (anchors.length != 5) throw new IllegalArgumentException("exactly five anchors are required");
        BigDecimal[] parsed = new BigDecimal[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            parsed[i] = CombatAmounts.parse(anchors[i], "growth anchor");
        }
        return curve(parsed);
    }

    /**
     * Explicit engine boundary only: retain Curve.value() as theoretical cooldown duration,
     * then ceil once when scheduling a cast. The engine representation is at least one tick.
     */
    public static int ceilCooldownTicks(BigDecimal theoreticalTicks) {
        BigDecimal ticks = CombatAmounts.requireAmount(theoreticalTicks, "theoretical cooldown ticks");
        if (ticks.compareTo(BigDecimal.ONE) <= 0) return 1;
        if (ticks.compareTo(MAX_TICKS) >= 0) return Integer.MAX_VALUE;
        return ticks.setScale(0, RoundingMode.CEILING).intValueExact();
    }

    public static final class Curve {
        private final BigDecimal[] anchors;
        private final BigDecimal[] differences = new BigDecimal[4];
        private final BigDecimal[] tangents = new BigDecimal[5];

        private Curve(BigDecimal[] supplied) {
            Objects.requireNonNull(supplied, "anchors");
            if (supplied.length != 5) throw new IllegalArgumentException("exactly five anchors are required");
            anchors = supplied.clone();
            BigDecimal[] logs = new BigDecimal[5];
            for (int i = 0; i < anchors.length; i++) {
                validateRepresentation(anchors[i], "growth anchor");
                if (anchors[i].precision() > OUTPUT_PRECISION || anchors[i].compareTo(MIN_ANCHOR) < 0) {
                    throw new IllegalArgumentException("growth anchors require at most 40 digits and at least 1e-200");
                }
                anchors[i] = CombatAmounts.requirePositiveAmount(anchors[i], "growth anchor");
                logs[i] = logarithm(anchors[i]);
                if (i > 0) differences[i - 1] = logs[i].subtract(logs[i - 1], WORK);
            }
            tangents[0] = BigDecimal.ZERO;
            tangents[4] = BigDecimal.ZERO;
            for (int i = 1; i < 4; i++) {
                BigDecimal left = differences[i - 1];
                BigDecimal right = differences[i];
                // A shared zero slope also preserves extrema and completely flat intervals.
                tangents[i] = left.signum() != right.signum() || left.signum() == 0
                        ? BigDecimal.ZERO
                        : TWO.multiply(left, WORK).multiply(right, WORK)
                                .divide(left.add(right, WORK), WORK);
            }
        }

        public BigDecimal value(int level, double progress) {
            return value(position(level, progress));
        }

        public BigDecimal value(BigDecimal position) {
            BigDecimal q = clampPosition(position);
            // Safe floor: q has already been validated/clamped to the positive range [1,5].
            int lower = q.intValue();
            return evaluate(q, lower);
        }

        private BigDecimal evaluate(BigDecimal q, int lower) {
            int index = lower - 1;
            BigDecimal t = q.subtract(BigDecimal.valueOf(lower));
            if (t.signum() == 0 || lower == 5) return anchors[index];

            // Evaluate from the nearer endpoint to avoid cancellation for progress near 0/1.
            boolean fromLeft = t.compareTo(HALF) <= 0;
            BigDecimal u = fromLeft ? t : BigDecimal.ONE.subtract(t);
            BigDecimal delta = fromLeft ? differences[index] : differences[index].negate();
            BigDecimal start = fromLeft ? tangents[index] : tangents[index + 1].negate();
            BigDecimal end = fromLeft ? tangents[index + 1] : tangents[index].negate();
            BigDecimal quadratic = THREE.multiply(delta, WORK)
                    .subtract(TWO.multiply(start, WORK), WORK).subtract(end, WORK);
            BigDecimal cubic = start.add(end, WORK).subtract(TWO.multiply(delta, WORK), WORK);
            BigDecimal logChange = u.multiply(start.add(u.multiply(
                    quadratic.add(u.multiply(cubic, WORK), WORK), WORK), WORK), WORK);
            BigDecimal base = anchors[fromLeft ? index : index + 1];
            BigDecimal result = base.multiply(exponential(logChange), WORK).round(OUTPUT);
            // Defensive local bounds also make finite-precision roundoff incapable of cap overflow.
            return canonical(result.max(anchors[index].min(anchors[index + 1]))
                    .min(anchors[index].max(anchors[index + 1])));
        }
    }

    private static void validateRepresentation(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.precision() > MAX_INPUT_PRECISION
                || value.scale() < -MAX_INPUT_SCALE || value.scale() > MAX_INPUT_SCALE) {
            throw new IllegalArgumentException(name + " exceeds bounded decimal precision or scale");
        }
    }

    private static BigDecimal canonical(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private static BigDecimal logarithm(BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        BigDecimal mantissa = value.movePointLeft(exponent);
        // Mantissa is [1,10); after four roots z below .072 gives rapid bounded convergence.
        for (int i = 0; i < 4; i++) mantissa = mantissa.sqrt(WORK);
        BigDecimal z = mantissa.subtract(BigDecimal.ONE, WORK)
                .divide(mantissa.add(BigDecimal.ONE, WORK), WORK);
        BigDecimal zSquared = z.multiply(z, WORK);
        BigDecimal power = z;
        BigDecimal sum = BigDecimal.ZERO;
        for (int n = 0; n < SERIES_TERMS; n++) {
            sum = sum.add(power.divide(BigDecimal.valueOf(2L * n + 1), WORK), WORK);
            power = power.multiply(zSquared, WORK);
        }
        return sum.multiply(BigDecimal.valueOf(32), WORK)
                .add(LN_TEN.multiply(BigDecimal.valueOf(exponent), WORK), WORK);
    }

    private static BigDecimal exponential(BigDecimal exponent) {
        if (exponent.abs().compareTo(EXP_LIMIT) > 0) {
            throw new IllegalArgumentException("growth exponential exceeds bounded domain");
        }
        BigDecimal reduced = exponent;
        int halvings = 0;
        while (reduced.abs().compareTo(EXP_REDUCTION_LIMIT) > 0 && halvings < MAX_EXP_HALVINGS) {
            reduced = reduced.divide(TWO, WORK);
            halvings++;
        }
        if (reduced.abs().compareTo(EXP_REDUCTION_LIMIT) > 0) {
            throw new IllegalArgumentException("growth exponential reduction limit exceeded");
        }
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        for (int n = 1; n <= SERIES_TERMS; n++) {
            term = term.multiply(reduced, WORK).divide(BigDecimal.valueOf(n), WORK);
            sum = sum.add(term, WORK);
        }
        for (int i = 0; i < halvings; i++) sum = sum.multiply(sum, WORK);
        return sum;
    }
}

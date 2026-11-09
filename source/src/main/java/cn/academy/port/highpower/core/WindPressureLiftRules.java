package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/**
 * NEW443's bounded additive motion and payment arithmetic. This is the provisional
 * unpublished integrated-self slice: no damage, experience, flight or fall-state API.
 * A proposal alone never grants permission to write native velocity.
 */
public final class WindPressureLiftRules {
    public static final int LIFETIME = SkillTuning.integer("wind_pressure_lift", "duration_ticks"), PAYMENT_PERIOD = 20, RESTART_FENCE = 8,
            MAX_COOLDOWN = 60;
    public static final double VERTICAL_ADDITION = .10, UP_MAX_Y = .16,
            SIDE_TARGET_Y = -.02, HORIZONTAL_ADDITION = .03,
            MAX_HORIZONTAL_SPEED = .18, MAX_ACCEPTED_MOVE = .9;
    private static final BigDecimal ONE_EIGHTH = new BigDecimal(".125");
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");

    private WindPressureLiftRules() {}

    public enum Mode { UP, SIDE }

    public record Velocity(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public record Horizontal(double x, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(z); }
    }

    /** Freeze this once at activation. Later camera turns must not replace it. */
    public static Horizontal freezeDirection(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return null;
        double scale = Math.max(Math.abs(x), Math.abs(z));
        if (scale == 0) return null;
        double nx = x / scale, nz = z / scale;
        double length = Math.hypot(nx, nz);
        return new Horizontal(nx / length, nz / length);
    }

    /**
     * Add at most .10 upward. UP assists only toward .16; SIDE only toward -.02
     * before ordinary gravity. Native travel moves before applying gravity, so the
     * negative SIDE target provides gentle descent instead of a repeated .02 climb.
     * Existing higher Y, including zero and ascent, is copied, never reduced.
     * A large descent retains its speed apart from this bounded addition and can
     * still cause native fall damage. The caller must preserve native fall state.
     *
     * SIDE adds at most .03 along the frozen heading only when both the existing
     * and proposed horizontal speed are at most .18 and current motion does not
     * oppose the frozen heading. A refusal preserves X/Z
     * exactly, including signed zero; it does not normalize or brake old motion.
     */
    public static Velocity assist(Velocity before, Mode mode, Horizontal frozenDirection) {
        if (before == null || !before.finite() || mode == null) return null;
        Horizontal direction = mode == Mode.SIDE && frozenDirection != null
                ? freezeDirection(frozenDirection.x, frozenDirection.z) : null;
        if (mode == Mode.SIDE && direction == null) return null;
        double y = upward(before.y, mode == Mode.UP ? UP_MAX_Y : SIDE_TARGET_Y);
        if (mode == Mode.UP || Math.hypot(before.x, before.z) > MAX_HORIZONTAL_SPEED
                || opposed(before, direction))
            return unchangedOrNew(before, before.x, y, before.z);

        double dx = direction.x * HORIZONTAL_ADDITION, dz = direction.z * HORIZONTAL_ADDITION;
        // Rounding a normalized diagonal must not create an oversized addition.
        while (Math.hypot(dx, dz) > HORIZONTAL_ADDITION) {
            dx = towardZero(dx); dz = towardZero(dz);
        }
        double x = dx == 0 ? before.x : before.x + dx;
        double z = dz == 0 ? before.z : before.z + dz;
        // Test the actual represented change as well as the desired addition.
        // Refuse rather than changing direction, clipping old motion, or scaling it.
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || Math.hypot(x - before.x, z - before.z) > HORIZONTAL_ADDITION
                || Math.hypot(x, z) > MAX_HORIZONTAL_SPEED)
            return unchangedOrNew(before, before.x, y, before.z);
        return unchangedOrNew(before, x, y, z);
    }

    private static double upward(double before, double target) {
        if (before >= target) return before;
        double after = Math.min(target, before + Math.min(VERTICAL_ADDITION, target - before));
        // One rounding step can put the actual delta just above the admitted cap.
        if (after - before > VERTICAL_ADDITION) after = Math.nextDown(after);
        return after > before ? after : before;
    }

    private static double towardZero(double value) {
        return value == 0 ? value : Math.nextAfter(value, 0);
    }

    private static boolean opposed(Velocity before, Horizontal direction) {
        double x = before.x * direction.x, z = before.z * direction.z;
        double dot = x + z, uncertainty = 8 * Math.ulp(Math.max(Math.abs(x), Math.abs(z)));
        if (Math.abs(dot) > uncertainty) return dot < 0;
        // A subnormal or almost-perpendicular native vector still cannot be
        // silently braked by cancellation/underflow in the binary dot product.
        return new BigDecimal(before.x).multiply(new BigDecimal(direction.x))
                .add(new BigDecimal(before.z).multiply(new BigDecimal(direction.z))).signum() < 0;
    }

    private static Velocity unchangedOrNew(Velocity before, double x, double y, double z) {
        return bits(x, before.x) && bits(y, before.y) && bits(z, before.z)
                ? before : new Velocity(x, y, z);
    }

    private static boolean bits(double left, double right) {
        return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return ACTIVATION.value(q); }
    public static double activationCost(BigDecimal q) { return theoreticalActivationCost(q).doubleValue(); }
    /** Exactly one eighth of the same continuous activation curve, not a second interpolant. */
    public static BigDecimal theoreticalSustainCost(BigDecimal q) {
        return theoreticalActivationCost(q).multiply(ONE_EIGHTH);
    }
    public static double sustainCost(BigDecimal q) { return theoreticalSustainCost(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    /** Economic cooldown only; the adapter separately enforces the eight-tick restart fence. */
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }

    /**
     * Strict remaining-native-tick persistence shape. The adapter must additionally
     * validate exact serialized keys, types/version and its certified resource clock.
     * Receipt zero is only genuinely new, entirely debt-free state. Never replace
     * malformed saved state with zeros, and never subtract elapsed/offline time.
     */
    public static boolean paymentShape(int remaining, int restart, int cooldown, long receipt) {
        return remaining >= 0 && remaining <= PAYMENT_PERIOD
                && restart >= 0 && restart <= RESTART_FENCE
                && cooldown >= 0 && cooldown <= MAX_COOLDOWN && receipt >= 0
                && (receipt != 0 || remaining == 0 && restart == 0 && cooldown == 0);
    }

    /** Exactly one genuine native tick; callers own deduplication and payment-tick holds. */
    public static int ageRemaining(int remaining, int maximum) {
        if (maximum < 0 || remaining < 0 || remaining > maximum)
            throw new IllegalArgumentException("Invalid NEW443 remaining native-tick debt");
        return remaining == 0 ? 0 : remaining - 1;
    }

    /**
     * A grant is usable only in its issuing real server tick. Missed grants are
     * discarded, never accumulated. Exact owner, server, world and lease identities
     * are separate native-adapter requirements; commit watermarks before callbacks.
     */
    public static boolean freshPermission(long issuedTick, long expiresTick, long sequence,
            long now, long lastConsumedTick, long lastConsumedSequence) {
        return issuedTick >= 0 && issuedTick < Long.MAX_VALUE
                && expiresTick == issuedTick + 1 && now == issuedTick
                && sequence > 0 && lastConsumedTick >= -1 && lastConsumedSequence >= 0
                && now > lastConsumedTick && sequence > lastConsumedSequence;
    }
}

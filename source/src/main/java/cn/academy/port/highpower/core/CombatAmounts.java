package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Objects;

/** Decimal boundaries for the isolated high-power ledger. No binary floating-point storage. */
public final class CombatAmounts {
    public static final BigDecimal CAP = new BigDecimal("100000000000000000000");
    /** Resource limits apply before expanding untrusted save strings/exponents. */
    public static final int MAX_ENCODED_LENGTH = 512;
    // Enough for all 21 integer digits at CAP plus the supported fraction scale.
    public static final int MAX_DECIMAL_PRECISION = 512;
    public static final int MAX_ABSOLUTE_SCALE = 256;

    private CombatAmounts() {}

    /** Validates rather than silently clipping a health, shield, or damage input. */
    public static BigDecimal requireAmount(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.scale() < -MAX_ABSOLUTE_SCALE || value.scale() > MAX_ABSOLUTE_SCALE
                || value.precision() > MAX_DECIMAL_PRECISION) {
            throw new IllegalArgumentException(name + " exceeds supported decimal precision or scale");
        }
        if (value.signum() < 0 || value.compareTo(CAP) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and " + CAP.toPlainString());
        }
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    /**
     * A hit beyond the per-hit ceiling is an overkill, not an invalid hit: it is held at {@link #CAP}, which is never below any health pool
     * (the largest pool is the cap itself), so it defeats its target instead of being ignored. Negative or null values are left for the
     * validators to refuse.
     */
    public static BigDecimal overkillClamp(BigDecimal value) {
        return value != null && value.compareTo(CAP) > 0 ? CAP : value;
    }

    public static BigDecimal requirePositiveAmount(BigDecimal value, String name) {
        BigDecimal result = requireAmount(value, name);
        if (result.signum() == 0) throw new IllegalArgumentException(name + " must be positive");
        return result;
    }

    public static BigDecimal parse(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > MAX_ENCODED_LENGTH) throw new IllegalArgumentException(name + " decimal string is too long");
        try {
            return requireAmount(new BigDecimal(value), name);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a decimal amount", e);
        }
    }

    public static String decimalString(BigDecimal value) {
        return requireAmount(value, "amount").toPlainString();
    }
}

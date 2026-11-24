package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * Converts classic damage (railgun 60-110, thunderclap 36-86, arc 5-9) to the enrolled
 * esper's damage unit: original amount times D divided by {@link #DEFAULT_DIVISOR}.
 * The cast still owns its original coefficient, range and mastery. The catalogue rank
 * adds a modest premium above the shared growth unit.
 */
public final class ClassicDamageScale {
    public static final double DEFAULT_DIVISOR = 50;
    private ClassicDamageScale() {}

    public static BigDecimal rankFactor(int skillLevel) {
        return switch (skillLevel) {
            case 2 -> BigDecimal.valueOf(1.10);
            case 3 -> BigDecimal.valueOf(1.20);
            case 4 -> BigDecimal.valueOf(1.28);
            case 5 -> BigDecimal.valueOf(1.38);
            default -> BigDecimal.ONE;
        };
    }

    public static double scaled(double raw, BigDecimal unit, double divisor) {
        if (!Double.isFinite(raw) || raw <= 0 || unit == null || unit.signum() <= 0 || !Double.isFinite(divisor) || divisor <= 0) return raw;
        BigDecimal lifted = BigDecimal.valueOf(raw).multiply(unit).divide(BigDecimal.valueOf(divisor), java.math.MathContext.DECIMAL64);
        return lifted.min(CombatAmounts.CAP).doubleValue();
    }
}

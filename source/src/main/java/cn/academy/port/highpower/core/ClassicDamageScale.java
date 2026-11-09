package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * Converts classic damage (railgun 60-110, thunderclap 36-86, arc 5-9) to the enrolled
 * esper's damage unit: original amount times D divided by {@link #DEFAULT_DIVISOR}.
 * A mastered railgun is 2.2D, including 22 at LV1. A raw-damage floor would defeat
 * the rebalance below D=50; valid scaling therefore permits smaller positive hits.
 */
public final class ClassicDamageScale {
    public static final double DEFAULT_DIVISOR = 50;
    private ClassicDamageScale() {}

    public static double scaled(double raw, BigDecimal unit, double divisor) {
        if (!Double.isFinite(raw) || raw <= 0 || unit == null || unit.signum() <= 0 || !Double.isFinite(divisor) || divisor <= 0) return raw;
        BigDecimal lifted = BigDecimal.valueOf(raw).multiply(unit).divide(BigDecimal.valueOf(divisor), java.math.MathContext.DECIMAL64);
        return lifted.min(CombatAmounts.CAP).doubleValue();
    }
}

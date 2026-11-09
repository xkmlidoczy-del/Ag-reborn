package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * Reach of the high-power movement skills grows gently with the growth position, from the original distance at LV1 to {@link #MAX_REACH}
 * times at LV5. It is a multiplier of a distance, never of a speed or of any amount, so it cannot enlarge damage or break per-tick
 * movement validation; the damage and HP of a level grow by orders of magnitude, travel grows by a small factor.
 */
public final class MobilityScale {
    public static final double MAX_REACH = 2;
    private MobilityScale() {}

    /** 1 at position 1, {@link #MAX_REACH} at position 5, linear in between; anything outside or invalid is treated as LV1. */
    public static double reach(BigDecimal position) {
        if (position == null) return 1;
        double q = position.doubleValue();
        if (!Double.isFinite(q) || q <= 1) return 1;
        return 1 + Math.min(1, (q - 1) / 4) * (MAX_REACH - 1);
    }

    /** Meltdowner is the single-shot destroyer of the four: its damage unit is one and a half times the shared level unit (HP and shield are unchanged). */
    public static final BigDecimal MELTDOWNER_DAMAGE = new BigDecimal("1.5");
    public static BigDecimal categoryDamage(String category, BigDecimal unit) {
        return "meltdowner".equals(category) ? unit.multiply(MELTDOWNER_DAMAGE).min(CombatAmounts.CAP) : unit;
    }
}

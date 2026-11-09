package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * Teleporters have the shared shield pool but no shield skill, so they were the thinnest of the four categories. The phase veil gives
 * them a thin, passive layer: each ordinary hit has {@link #SHARE} of its damage taken from the shield pool (never more than the pool holds,
 * one for one) and only the rest reaches health. It is mitigation of a hit, not damage, so it never depends on current health; the pool
 * refills through the ordinary shield recovery, which waits after every spend.
 */
public final class PhaseVeilRules {
    public static final BigDecimal SHARE = new BigDecimal("0.25");
    public record Result(BigDecimal absorbed, BigDecimal remaining) {}
    private PhaseVeilRules() {}

    public static Result absorb(BigDecimal damage, BigDecimal shield) {
        if (damage == null || shield == null || damage.signum() <= 0 || shield.signum() <= 0) return new Result(BigDecimal.ZERO, damage == null ? BigDecimal.ZERO : damage.max(BigDecimal.ZERO));
        BigDecimal absorbed = damage.multiply(SHARE).min(shield);
        return new Result(absorbed, damage.subtract(absorbed));
    }
}

package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** NEW242's nominal 1.5S shield: existing base plus the fixed lower reserve share only. */
public final class DissipationCurtainShieldRules {
    private static final BigDecimal ARROW_ABSORPTION = new BigDecimal("1.25");
    private static final BigDecimal ARROW_SPEND = new BigDecimal("0.8");
    private DissipationCurtainShieldRules() {}

    public record Result(ExactCombatState state, SharedShieldReserveShares shares,
                         BigDecimal absorbed, BigDecimal spent, BigDecimal remaining) {}

    public static BigDecimal capacity(ExactCombatState base) {
        SharedShieldReserveShares.ZERO.validate(base);
        return CombatAmounts.requireAmount(base.maxShield().add(SharedShieldReserveShares.halfCapacity(base)),
                "curtain capacity");
    }

    public static BigDecimal available(ExactCombatState base, SharedShieldReserveShares shares) {
        return base.shield().add(shares.validate(base).lower());
    }

    /** The caller retains ownership of applying residual damage and the native death path. */
    public static Result absorb(ExactCombatState base, SharedShieldReserveShares shares,
                                BigDecimal incoming, boolean arrow) {
        return absorb(base, shares, incoming, arrow, available(base, shares));
    }

    /** Immutable crossing ceiling limits spending even if another valid callback credits recovery. */
    public static Result absorb(ExactCombatState base, SharedShieldReserveShares shares,
                                BigDecimal incoming, boolean arrow, BigDecimal spendingCeiling) {
        shares.validate(base);
        incoming = CombatAmounts.requireAmount(incoming, "curtain incoming damage");
        spendingCeiling = CombatAmounts.requireAmount(spendingCeiling, "curtain captured spending ceiling");
        if (base.isDead() || incoming.signum() == 0)
            return new Result(base.withShieldEnabled(false), shares, BigDecimal.ZERO, BigDecimal.ZERO, incoming);
        BigDecimal available = available(base, shares).min(spendingCeiling);
        // At scale 256 a fractional quantum must never become uncharged absorption.
        BigDecimal limit = arrow ? floor(available.multiply(ARROW_ABSORPTION)) : available;
        BigDecimal absorbed = CombatAmounts.requireAmount(incoming.min(limit), "curtain absorbed damage");
        BigDecimal spent = arrow ? ceil(absorbed.multiply(ARROW_SPEND)) : absorbed;
        spent = CombatAmounts.requireAmount(spent, "curtain shield spend");
        if (spent.compareTo(available) > 0) throw new IllegalArgumentException("Curtain spend exceeds eligible shield");
        BigDecimal basePaid = spent.min(base.shield());
        ExactCombatState next = base.spendShield(basePaid).withShieldEnabled(false);
        SharedShieldReserveShares remainingShares = shares.debit(spent.subtract(basePaid), false).validate(next);
        return new Result(next, remainingShares, absorbed, spent, incoming.subtract(absorbed));
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                ? value.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.FLOOR) : value;
    }
    private static BigDecimal ceil(BigDecimal value) {
        return value.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                ? value.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.CEILING) : value;
    }
}

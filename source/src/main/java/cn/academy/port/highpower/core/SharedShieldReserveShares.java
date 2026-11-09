package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Persisted identities within the shared reserve: NEW242 cannot spend the upper half. */
public record SharedShieldReserveShares(BigDecimal lower, BigDecimal upper) {
    public static final String SCHEMA_VERSION = "2";
    public static final SharedShieldReserveShares ZERO = new SharedShieldReserveShares(BigDecimal.ZERO, BigDecimal.ZERO);

    public SharedShieldReserveShares {
        lower = CombatAmounts.requireAmount(lower, "lower shield reserve");
        upper = CombatAmounts.requireAmount(upper, "upper shield reserve");
        CombatAmounts.requireAmount(lower.add(upper), "total shield reserve");
    }

    public record Recovery(ExactCombatState state, SharedShieldReserveShares shares) {}

    public BigDecimal total() { return lower.add(upper); }

    /** Mathematical ceiling; persisted balances themselves still obey the decimal boundary. */
    public static BigDecimal halfCapacity(ExactCombatState base) {
        return Objects.requireNonNull(base, "base").maxShield().divide(BigDecimal.valueOf(2));
    }

    public SharedShieldReserveShares validate(ExactCombatState base) {
        Objects.requireNonNull(base, "base");
        BigDecimal half = halfCapacity(base);
        if (base.maxShield().add(base.maxShield()).compareTo(CombatAmounts.CAP) > 0
                || lower.compareTo(half) > 0 || upper.compareTo(half) > 0
                || base.shield().add(total()).compareTo(CombatAmounts.CAP) > 0)
            throw new IllegalArgumentException("Shared shield reserves exceed certified capacity");
        return this;
    }

    /** Only v1 migration may split an aggregate balance. Every later write preserves identities. */
    public static SharedShieldReserveShares migrateV1(ExactCombatState base, BigDecimal amount) {
        amount = MagneticDomainShieldRules.validate(base, amount);
        BigDecimal lower = amount.min(halfCapacity(base));
        return new SharedShieldReserveShares(lower, amount.subtract(lower)).validate(base);
    }

    /** NEW152 pays the lower share before the upper share. No redistribution follows the debit. */
    public SharedShieldReserveShares debit(BigDecimal amount, boolean upperEligible) {
        amount = CombatAmounts.requireAmount(amount, "shared reserve debit");
        BigDecimal available = upperEligible ? total() : lower;
        if (amount.compareTo(available) > 0) throw new IllegalArgumentException("Insufficient eligible reserve");
        BigDecimal lowerPaid = amount.min(lower);
        return new SharedShieldReserveShares(lower.subtract(lowerPaid), upper.subtract(amount.subtract(lowerPaid)));
    }

    /** Preserve share identity for the legacy NEW152 aggregate writer, including recovery. */
    public SharedShieldReserveShares withTotal(ExactCombatState base, BigDecimal amount) {
        validate(base);
        amount = MagneticDomainShieldRules.validate(base, amount);
        BigDecimal delta = amount.subtract(total());
        if (delta.signum() <= 0) return debit(delta.negate(), true).validate(base);
        BigDecimal lowerAdded = delta.min(halfCapacity(base).subtract(lower));
        return new SharedShieldReserveShares(lower.add(lowerAdded), upper.add(delta.subtract(lowerAdded))).validate(base);
    }

    /** One existing recovery budget: base, then lower, then the NEW152-only upper share. */
    public Recovery recover(ExactCombatState base, BigDecimal budget, boolean lowerEligible, boolean upperEligible) {
        validate(base);
        budget = CombatAmounts.requireAmount(budget, "shared shield recovery budget");
        if (upperEligible && !lowerEligible) throw new IllegalArgumentException("Upper recovery requires lower eligibility");
        if (base.isDead()) return new Recovery(base.withShieldEnabled(false), this);
        BigDecimal baseAdded = budget.min(base.maxShield().subtract(base.shield()));
        BigDecimal remainder = budget.subtract(baseAdded);
        BigDecimal lowerAdded = lowerEligible ? remainder.min(halfCapacity(base).subtract(lower)) : BigDecimal.ZERO;
        remainder = remainder.subtract(lowerAdded);
        BigDecimal upperAdded = upperEligible ? remainder.min(halfCapacity(base).subtract(upper)) : BigDecimal.ZERO;
        ExactCombatState next = base.regenerateShield(baseAdded).withShieldEnabled(false);
        return new Recovery(next, new SharedShieldReserveShares(lower.add(lowerAdded), upper.add(upperAdded)).validate(next));
    }

    public Map<String, String> toDecimalMap(ExactCombatState base) {
        validate(base);
        return Map.of("version", SCHEMA_VERSION, "amount", CombatAmounts.decimalString(total()),
                "capacity", CombatAmounts.decimalString(base.maxShield()),
                "lower", CombatAmounts.decimalString(lower), "upper", CombatAmounts.decimalString(upper));
    }

    /** Capacity, exact sum, version and individual share ceilings all fail closed. */
    public static SharedShieldReserveShares fromDecimalMap(ExactCombatState base, Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        String version = required(values, "version");
        if (!"1".equals(version) && !SCHEMA_VERSION.equals(version))
            throw new IllegalArgumentException("Unsupported shared shield reserve schema");
        BigDecimal capacity = CombatAmounts.parse(required(values, "capacity"), "saved reserve capacity");
        if (capacity.compareTo(base.maxShield()) != 0) throw new IllegalArgumentException("Reserve capacity mismatch");
        BigDecimal amount = CombatAmounts.parse(required(values, "amount"), "saved reserve amount");
        if ("1".equals(version)) return migrateV1(base, amount);
        SharedShieldReserveShares shares = new SharedShieldReserveShares(
                CombatAmounts.parse(required(values, "lower"), "saved lower reserve"),
                CombatAmounts.parse(required(values, "upper"), "saved upper reserve")).validate(base);
        if (shares.total().compareTo(amount) != 0) throw new IllegalArgumentException("Reserve share sum mismatch");
        return shares;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing reserve field: " + key);
        return value;
    }
}

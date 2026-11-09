package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * Explicit defense inputs for standalone vanilla-style reduction math.
 * resistanceLevel is zero for no effect, one for Resistance I (amplifier + 1).
 * Protection points above 20 and resistance levels above 5 are accepted, but
 * their effectiveness saturates at the vanilla reduction limits.
 * Damage-type bypass decisions belong to the runtime adapter.
 */
public record DefenseProfile(BigDecimal armor, BigDecimal toughness,
                             int resistanceLevel, int protectionPoints,
                             boolean bypassArmor, boolean bypassResistance,
                             boolean bypassEnchantments) {
    public static final DefenseProfile NONE = new DefenseProfile(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

    public DefenseProfile {
        armor = CombatAmounts.requireAmount(armor, "armor");
        toughness = CombatAmounts.requireAmount(toughness, "toughness");
        if (resistanceLevel < 0) throw new IllegalArgumentException("resistanceLevel must not be negative");
        if (protectionPoints < 0) throw new IllegalArgumentException("protectionPoints must not be negative");
    }

    public DefenseProfile(BigDecimal armor, BigDecimal toughness, int resistanceLevel, int protectionPoints) {
        this(armor, toughness, resistanceLevel, protectionPoints, false, false, false);
    }

    /** Convenience only for bounded native attributes, never for combat ledger values. */
    public DefenseProfile(double armor, double toughness, int resistanceLevel, int protectionPoints) {
        this(decimalAttribute(armor, "armor"), decimalAttribute(toughness, "toughness"), resistanceLevel, protectionPoints);
    }

    private static BigDecimal decimalAttribute(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return BigDecimal.valueOf(value);
    }
}

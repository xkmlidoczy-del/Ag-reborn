package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Provisional pack balance presets, not a claim of completed gameplay balance. */
public enum HighPowerLevel {
    LV1(1, "10"),
    LV2(2, "178"),
    LV3(3, "3162"),
    LV4(4, "56234"),
    LV5(5, "1000000");

    private final int level;
    private final BigDecimal damage;
    private final BigDecimal maxHealth;
    private final BigDecimal maxShield;

    HighPowerLevel(int level, String damage) {
        this.level = level;
        this.damage = CombatAmounts.parse(damage, "preset damage");
        this.maxHealth = CombatAmounts.requirePositiveAmount(this.damage.multiply(BigDecimal.valueOf(20)), "preset health");
        this.maxShield = CombatAmounts.requireAmount(this.damage.multiply(BigDecimal.TEN), "preset shield");
    }

    public int level() { return level; }
    public BigDecimal damage() { return damage; }
    public BigDecimal maxHealth() { return maxHealth; }
    public BigDecimal maxShield() { return maxShield; }

    public static HighPowerLevel forLevel(int level) {
        for (HighPowerLevel preset : values()) if (preset.level == level) return preset;
        throw new IllegalArgumentException("High-power level must be 1 through 5");
    }
}

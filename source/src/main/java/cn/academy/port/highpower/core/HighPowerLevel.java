package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Provisional pack balance presets, not a claim of completed gameplay balance. */
public enum HighPowerLevel {
    LV1(1, 24, 6, 5),
    LV2(2, 36, 12, 8),
    LV3(3, 52, 22, 12),
    LV4(4, 72, 35, 18),
    LV5(5, 100, 50, 24);

    private final int level;
    private final BigDecimal damage;
    private final BigDecimal maxHealth;
    private final BigDecimal maxShield;

    HighPowerLevel(int level, int health, int shield, int power) {
        this.level = level;
        this.damage = BigDecimal.valueOf(power);
        this.maxHealth = BigDecimal.valueOf(health);
        this.maxShield = BigDecimal.valueOf(shield);
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

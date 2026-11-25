package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/** Native capacity and skill-power anchors for ordinary ability progression. */
public enum HighPowerLevel {
    LV1(1, 26, 6, 8),
    LV2(2, 42, 12, 15),
    LV3(3, 150, 50, 42),
    LV4(4, 580, 190, 76),
    LV5(5, 2000, 600, 110);

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

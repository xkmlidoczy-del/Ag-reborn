package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative immutable health and shared-shield pool. Each field and each
 * hit is independently bounded by 1e20; health plus shield may exceed that cap.
 * There is deliberately no passive health regeneration or tick-based refill.
 * Activation/category changes should retain this state; toggling only changes
 * whether incoming damage uses the shield. Constructing full() is explicit
 * enrollment/reset, never an activation operation.
 */
public record ExactCombatState(BigDecimal health, BigDecimal maxHealth,
                               BigDecimal shield, BigDecimal maxShield,
                               boolean shieldEnabled) {
    public static final String SCHEMA_VERSION = "1";

    public ExactCombatState {
        health = CombatAmounts.requireAmount(health, "health");
        maxHealth = CombatAmounts.requirePositiveAmount(maxHealth, "maximum health");
        shield = CombatAmounts.requireAmount(shield, "shield");
        maxShield = CombatAmounts.requireAmount(maxShield, "maximum shield");
        if (health.compareTo(maxHealth) > 0) throw new IllegalArgumentException("Health exceeds maximum health");
        if (shield.compareTo(maxShield) > 0) throw new IllegalArgumentException("Shield exceeds maximum shield");
    }

    public static ExactCombatState full(HighPowerLevel level) {
        Objects.requireNonNull(level, "level");
        return new ExactCombatState(level.maxHealth(), level.maxHealth(), level.maxShield(), level.maxShield(), true);
    }

    public boolean isDead() { return health.signum() == 0; }

    public ExactCombatState withShieldEnabled(boolean enabled) {
        if (enabled == shieldEnabled) return this;
        return new ExactCombatState(health, maxHealth, shield, maxShield, enabled);
    }

    public DamageResult attack(BigDecimal rawDamage, DefenseProfile defenses) {
        BigDecimal raw = CombatAmounts.requireAmount(rawDamage, "raw damage");
        return apply(raw, DamageMitigation.mitigate(raw, defenses));
    }

    /** For a runtime adapter that already performed the reduction pipeline exactly once. */
    public DamageResult applyMitigatedDamage(BigDecimal damage) {
        BigDecimal checked = CombatAmounts.requireAmount(damage, "mitigated damage");
        return apply(checked, checked);
    }

    private DamageResult apply(BigDecimal raw, BigDecimal damage) {
        if (isDead() || damage.signum() == 0) {
            return new DamageResult(this, raw, damage, BigDecimal.ZERO, BigDecimal.ZERO, damage);
        }
        BigDecimal absorbed = shieldEnabled ? damage.min(shield) : BigDecimal.ZERO;
        BigDecimal remaining = damage.subtract(absorbed);
        BigDecimal lostHealth = remaining.min(health);
        ExactCombatState next = new ExactCombatState(health.subtract(lostHealth), maxHealth,
                shield.subtract(absorbed), maxShield, shieldEnabled);
        return new DamageResult(next, raw, damage, absorbed, lostHealth, remaining.subtract(lostHealth));
    }

    /** All-or-nothing shared-pool ability cost; insufficient balance cannot underflow. */
    public ExactCombatState spendShield(BigDecimal amount) {
        BigDecimal cost = CombatAmounts.requireAmount(amount, "shield cost");
        if (cost.compareTo(shield) > 0) throw new IllegalArgumentException("Insufficient shared shield");
        if (isDead() && cost.signum() > 0) throw new IllegalStateException("A dead combatant cannot spend shield");
        return new ExactCombatState(health, maxHealth, shield.subtract(cost), maxShield, shieldEnabled);
    }

    /**
     * An explicit recovery budget, not elapsed time: the caller schedules recovery.
     * Valid inputs saturate at available shield capacity. Dead combatants do not
     * recover. The shield toggle itself never calls this operation.
     */
    public ExactCombatState regenerateShield(BigDecimal amount) {
        BigDecimal budget = CombatAmounts.requireAmount(amount, "shield regeneration");
        if (isDead()) return this;
        BigDecimal recovered = budget.min(maxShield.subtract(shield));
        return new ExactCombatState(health, maxHealth, shield.add(recovered), maxShield, shieldEnabled);
    }

    /**
     * Changes capacities without awarding health or shield on level/category changes.
     * Reducing a maximum drops only the balance above that new, validated maximum.
     */
    public ExactCombatState withMaximums(BigDecimal newMaxHealth, BigDecimal newMaxShield) {
        BigDecimal hpCap = CombatAmounts.requirePositiveAmount(newMaxHealth, "maximum health");
        BigDecimal shieldCap = CombatAmounts.requireAmount(newMaxShield, "maximum shield");
        return new ExactCombatState(health.min(hpCap), hpCap, shield.min(shieldCap), shieldCap, shieldEnabled);
    }

    /** Decimal strings are suitable for NBT string tags, JSON strings, or other codecs. */
    public Map<String, String> toDecimalMap() {
        return Map.of("version", SCHEMA_VERSION,
                "health", CombatAmounts.decimalString(health),
                "maxHealth", CombatAmounts.decimalString(maxHealth),
                "shield", CombatAmounts.decimalString(shield),
                "maxShield", CombatAmounts.decimalString(maxShield),
                "shieldEnabled", Boolean.toString(shieldEnabled));
    }

    /** Fails closed on malformed, incomplete, future-version, or out-of-cap saves. */
    public static ExactCombatState fromDecimalMap(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (!SCHEMA_VERSION.equals(required(values, "version"))) throw new IllegalArgumentException("Unsupported combat schema version");
        String enabled = required(values, "shieldEnabled");
        if (!enabled.equals("true") && !enabled.equals("false")) throw new IllegalArgumentException("Invalid shieldEnabled flag");
        return new ExactCombatState(CombatAmounts.parse(required(values, "health"), "health"),
                CombatAmounts.parse(required(values, "maxHealth"), "maximum health"),
                CombatAmounts.parse(required(values, "shield"), "shield"),
                CombatAmounts.parse(required(values, "maxShield"), "maximum shield"),
                Boolean.parseBoolean(enabled));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing combat state field: " + key);
        return value;
    }
}

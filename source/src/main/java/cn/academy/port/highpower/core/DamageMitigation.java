package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Armor/toughness, then resistance, then enchantment protection.
 * Terminating arithmetic is exact within the supported ledger scale. A
 * non-terminating armor division uses 80 significant digits (HALF_EVEN).
 * Only a generated result beyond the ledger fraction scale is rounded DOWN
 * to that boundary, so valid fixed shares cannot disappear through rejection
 * after ordinary armor multiplies their final decimal places. Raw inputs
 * retain strict validation. The ledger subtracts the admitted result exactly.
 * Runtime events/mod-specific effects remain the adapter's responsibility.
 */
public final class DamageMitigation {
    public static final int REPEATING_DIVISION_PRECISION = 80;
    private static final MathContext REPEATING_DIVISION = new MathContext(REPEATING_DIVISION_PRECISION, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);
    private static final BigDecimal TWENTY_FIVE = BigDecimal.valueOf(25);

    private DamageMitigation() {}

    public static BigDecimal mitigate(BigDecimal rawDamage, DefenseProfile defense) {
        BigDecimal damage = CombatAmounts.requireAmount(rawDamage, "raw damage");
        Objects.requireNonNull(defense, "defense");
        if (!defense.bypassArmor()) damage = afterArmor(damage, defense.armor(), defense.toughness());
        if (!defense.bypassResistance()) {
            int resistance = Math.min(defense.resistanceLevel(), 5);
            damage = damage.multiply(BigDecimal.valueOf(5 - resistance)).divide(FIVE);
        }
        if (!defense.bypassEnchantments()) {
            int protection = Math.min(defense.protectionPoints(), 20);
            damage = damage.multiply(BigDecimal.valueOf(25 - protection)).divide(TWENTY_FIVE);
        }
        // This is trusted arithmetic derived from the validated raw amount, never
        // normalization of an untrusted save or packet. A three/six-way share may
        // already use all 256 fraction places; ordinary armor adds three more.
        // Dropping only the unsupported tail cannot amplify any allocated share.
        if (damage.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE)
            damage = damage.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        return CombatAmounts.requireAmount(damage, "mitigated damage");
    }

    private static BigDecimal afterArmor(BigDecimal damage, BigDecimal armor, BigDecimal toughness) {
        if (damage.signum() == 0 || armor.signum() == 0) return damage;
        BigDecimal divisor = TWO.add(toughness.divide(FOUR));
        // Compare the clamp branches without prematurely rounding damage / divisor.
        BigDecimal effectiveNumerator = armor.multiply(divisor).subtract(damage);
        BigDecimal floor = armor.divide(FIVE).min(TWENTY);
        if (effectiveNumerator.compareTo(floor.multiply(divisor)) <= 0) {
            return damage.multiply(TWENTY_FIVE.subtract(floor)).divide(TWENTY_FIVE);
        }
        if (effectiveNumerator.compareTo(TWENTY.multiply(divisor)) >= 0) {
            return damage.divide(FIVE);
        }
        BigDecimal numerator = damage.multiply(TWENTY_FIVE.subtract(armor).multiply(divisor).add(damage));
        BigDecimal denominator = TWENTY_FIVE.multiply(divisor);
        try {
            return numerator.divide(denominator);
        } catch (ArithmeticException nonTerminatingExpansion) {
            return numerator.divide(denominator, REPEATING_DIVISION);
        }
    }
}

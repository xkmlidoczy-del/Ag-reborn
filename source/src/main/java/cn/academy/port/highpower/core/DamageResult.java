package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact accounting of one hit; callers must store state(), not the input state. */
public record DamageResult(ExactCombatState state, BigDecimal rawDamage,
                           BigDecimal mitigatedDamage, BigDecimal shieldAbsorbed,
                           BigDecimal healthDamage, BigDecimal overkill) {
    public DamageResult {
        Objects.requireNonNull(state, "state");
        rawDamage = CombatAmounts.requireAmount(rawDamage, "raw damage");
        mitigatedDamage = CombatAmounts.requireAmount(mitigatedDamage, "mitigated damage");
        shieldAbsorbed = CombatAmounts.requireAmount(shieldAbsorbed, "shield absorbed");
        healthDamage = CombatAmounts.requireAmount(healthDamage, "health damage");
        overkill = CombatAmounts.requireAmount(overkill, "overkill");
        if (mitigatedDamage.compareTo(rawDamage) > 0) throw new IllegalArgumentException("Mitigation may not increase damage");
        if (shieldAbsorbed.add(healthDamage).add(overkill).compareTo(mitigatedDamage) != 0) {
            throw new IllegalArgumentException("Damage accounting must conserve the mitigated amount");
        }
    }

    public boolean didDamage() { return shieldAbsorbed.signum() > 0 || healthDamage.signum() > 0; }
    public boolean killed() { return state.isDead() && healthDamage.signum() > 0; }
}

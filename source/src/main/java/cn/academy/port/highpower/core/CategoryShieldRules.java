package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Objects;

/** The base category shield catches the residual after a directional/reserve defense has paid its own cost. */
public final class CategoryShieldRules {
    private CategoryShieldRules() {}

    public static ExactCombatState finish(ExactCombatState before, ExactCombatState planned, BigDecimal residual) {
        Objects.requireNonNull(before); Objects.requireNonNull(planned);
        residual = CombatAmounts.requireAmount(residual, "category shield residual");
        if (before.isDead()) return planned;
        // Rebuild from the pre-hit HP and the already-paid shield. Using HP loss as the residual would discard
        // overkill after a specialized plan clipped it to HP, incorrectly rescuing lethal attacks.
        var base = new ExactCombatState(before.health(), planned.maxHealth(), planned.shield(), planned.maxShield(), true);
        return base.applyMitigatedDamage(residual).state().withShieldEnabled(false);
    }
}

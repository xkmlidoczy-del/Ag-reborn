/**
 * Dependency-free prototype combat arithmetic for the isolated pack fork.
 *
 * <p>Integration contract: enroll with {@link cn.academy.port.highpower.core.ExactCombatState#full},
 * retain one state per combatant, and persist every result state. Ability or
 * category activation must not enroll again. Use attack for standalone defense
 * math, or applyMitigatedDamage only after the native adapter has reduced damage
 * once. Decimal maps must be stored as strings, never converted through float or
 * double. Native health bars are a display/compatibility projection, not this
 * ledger. Shield ability costs and damage must spend the same stored pool.</p>
 *
 * <p>All health, shield, and damage values independently reject values outside
 * [0, 1e20]. Inputs also have explicit decimal resource limits to reject hostile
 * save strings or exponents before decimal expansion. Precision-limit failures
 * are errors, never truncation. Shield regeneration is explicit and saturates
 * at capacity. No passive health regeneration exists. Reducing a configured
 * capacity clips only the old balance above that new validated capacity;
 * increasing it never refills either pool.</p>
 *
 * <p>The only rounded operation is a mathematically non-terminating division
 * in armor/toughness mitigation: 80 significant digits, HALF_EVEN. All finite
 * decimal ledger additions and subtractions are exact. These standalone rules
 * do not claim to execute arbitrary modded damage events, invulnerability,
 * death, drops, rewards, or progression; those require runtime integration and
 * independent game tests.</p>
 */
package cn.academy.port.highpower.core;

package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Set;

/**
 * What may reach an enrolled esper's exact ledger. Every damage source that deals damage is accounted exactly by its absolute amount:
 * attacks, projectiles, explosions, lightning, falls, the void, fire, lava, drowning, starvation, suffocation, freezing, wither, magic and
 * dragon breath all hurt an esper, and there is no per-hit limit on ordinary damage. Only what is not damage is refused up front:
 * <ul>
 * <li><b>the kill command</b>: {@code generic_kill} asks for death itself rather than an amount (switchable by the server owner);</li>
 * <li><b>invalid amounts</b>: NaN and negative values, which are no damage at all. An enormous or infinite hit is the opposite of invalid:
 *     it is an overkill that defeats its target, never an ignored hit.</li>
 * </ul>
 * Percentage-of-maximum damage cannot exist on this path: a hit of D removes exactly D from the ledger, never a share of it.
 * A hit beyond {@link CombatAmounts#CAP} is held at the ceiling, which defeats any unprotected pool (the largest pool is the ceiling itself).
 * Death that did not come out of the ledger (forced native health 0, a direct {@code die()} call) is refused by the runtime guard,
 * see {@code EsperDamageGuard}. Nothing here limits damage an esper deals.
 */
public final class EsperDamageRules {
    public enum Decision { ALLOW, BLOCK_KILL, BLOCK_INVALID }

    /** @param environmentShare per-point multiplier of the level's damage unit D applied to environmental damage (0 switches the scaling off). */
    public record Policy(boolean enabled, boolean blockKillCommand, boolean blockForcedDeath, double environmentShare) {
        public static final double DEFAULT_ENVIRONMENT_SHARE = .001;
        public static final Policy DEFAULT = new Policy(true, true, true, DEFAULT_ENVIRONMENT_SHARE);
    }

    /** Fire, lava, drowning, starvation, falling and the like: damage that comes from the world, not from an attacker. */
    static final Set<String> ENVIRONMENT = Set.of("minecraft:in_fire", "minecraft:campfire", "minecraft:on_fire", "minecraft:lava", "minecraft:hot_floor",
            "minecraft:drown", "minecraft:starve", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:freeze", "minecraft:dry_out", "minecraft:in_wall",
            "minecraft:cramming", "minecraft:wither", "minecraft:fall", "minecraft:out_of_world", "minecraft:outside_border", "minecraft:fly_into_wall");

    public static boolean environmental(String typeId) { return typeId != null && ENVIRONMENT.contains(typeId); }

    /**
     * Vanilla world damage is a few points per second, which no high-power ledger would ever notice. It is therefore multiplied by a level
     * constant (the unit D times a fixed small share), never by anything about the victim's current health: the same hit costs the same at
     * full health and at one point, so this is not percentage damage. With the default share a full-health esper survives about forty
     * minutes standing in lava at any level.
     */
    public static double environmentalAmount(Policy policy, String typeId, double amount, BigDecimal damageUnit) {
        if (policy == null || !policy.enabled() || !environmental(typeId) || !(policy.environmentShare() > 0) || damageUnit == null
                || Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) return amount;
        BigDecimal scaled = BigDecimal.valueOf(amount).multiply(damageUnit).multiply(BigDecimal.valueOf(policy.environmentShare()));
        return scaled.max(BigDecimal.valueOf(amount)).min(CombatAmounts.CAP).doubleValue();
    }

    public static final String KILL = "minecraft:generic_kill";

    private EsperDamageRules() {}

    /** @param exactSkill true for the closed skill hits between espers, which carry their own exact accounting. */
    public static Decision decide(Policy policy, String typeId, double amount, boolean exactSkill) {
        if (policy == null || !policy.enabled() || exactSkill) return Decision.ALLOW;
        if (KILL.equals(typeId)) return policy.blockKillCommand() ? Decision.BLOCK_KILL : Decision.ALLOW;
        if (Double.isNaN(amount) || amount < 0) return Decision.BLOCK_INVALID;
        return Decision.ALLOW;
    }

    /** A death is legitimate only if the exact ledger is spent; otherwise something killed the native body behind the ledger's back. */
    public static boolean forcedDeath(Policy policy, boolean ledgerPresent, boolean ledgerDead) {
        return policy != null && policy.enabled() && policy.blockForcedDeath() && ledgerPresent && !ledgerDead;
    }
}

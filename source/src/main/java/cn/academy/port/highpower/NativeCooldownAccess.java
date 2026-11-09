package cn.academy.port.highpower;

import net.minecraft.world.damagesource.DamageSource;

/** Present only when the pinned cooldown and LivingEntity damage-commit seams were applied. */
public interface NativeCooldownAccess {
    /** Validate only a private authenticated source; never expose the mutable native container. */
    boolean academy$vetoClosedDamageCommit(DamageSource source);

    /** Read-only NEW423 query of the current native hurt container; never exposes mutable stack state. */
    boolean academy$yawPalmShieldBlocked(DamageSource source);

    /** Player overrides actuallyHurt; inheriting the LivingEntity marker cannot certify that override. */
    interface PlayerDamageCommit {}
}

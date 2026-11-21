package cn.academy.port.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.EntityCapability;

/** Optional provider at the target's own damage delegation point, before native settlement. */
public interface CombatDefense {
    EntityCapability<CombatDefense, Void> CAPABILITY =
            EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("academy", "combat_defense"),
                    CombatDefense.class);

    /**
     * The provider supplies the same legal phase and multipliers with its fixed per-hit ceiling
     * opened.
     */
    float withoutDamageCap(
            LivingEntity target, DamageSource source, float requested, float nativeAmount);

    /**
     * Observations only. A provider may expose its own health, its additional shield, and current
     * phase.
     */
    record Pools(float health, java.math.BigDecimal shield, String phase) {
        public Pools {
            if (!Float.isFinite(health)
                    || health < 0
                    || shield == null
                    || shield.signum() < 0
                    || phase == null)
                throw new IllegalArgumentException("Invalid observed combat pools");
        }
    }

    default Pools observe(LivingEntity target) {
        return null;
    }

    /** Call at the cooperating owner's original debit, after its one native transaction. */
    static float settledDamage(LivingEntity target, DamageSource source, float originalAmount) {
        return cn.academy.port.highpower.CapBreak.ownedDebit(target, source, originalAmount);
    }

    default boolean boss() {
        return false;
    }
}

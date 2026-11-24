package cn.academy.port.highpower;

import net.minecraft.world.damagesource.DamageSource;

/** Calls the inherited living hurt path for an authored spell rejected before settlement. */
public interface NativeSkillAccess {
    boolean academy$hurtFromOrigin(DamageSource source, float amount);
}

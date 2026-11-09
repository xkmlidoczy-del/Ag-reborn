package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.NativeEmergencyReturnMovementAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** No setters: movement remains entirely native. */
@Mixin(LivingEntity.class)
abstract class LivingEntityEmergencyReturnMotionMixin extends Entity implements NativeEmergencyReturnMovementAccess {
    protected LivingEntityEmergencyReturnMotionMixin(EntityType<?> type, Level level) { super(type, level); }
    @Shadow protected boolean jumping;
    @Shadow protected int lerpSteps;
    @Shadow protected abstract boolean isImmobile();
    @Override public boolean academy$emergencyJumping() { return jumping; }
    @Override public int academy$emergencyLerpSteps() { return lerpSteps; }
    @Override public boolean academy$emergencyImmobile() { return isImmobile(); }
    @Override public Vec3 academy$emergencyStuckSpeed() { return stuckSpeedMultiplier; }
    @Override public float academy$emergencyBlockSpeedFactor() { return getBlockSpeedFactor(); }
}

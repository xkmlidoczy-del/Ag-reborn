package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.BrakingTouchLandingProof;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityBrakingTouchMixin {
    @WrapMethod(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z")
    private boolean academy$brakingCause(float distance, float multiplier, DamageSource source, Operation<Boolean> original) {
        return BrakingTouchLandingProof.nativeCause((LivingEntity)(Object)this, distance, multiplier, source, original);
    }
    @WrapOperation(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingFall(Lnet/minecraft/world/entity/LivingEntity;FF)[F"),
            require = 1, expect = 1, allow = 1)
    private float[] academy$brakingEvent(LivingEntity entity, float distance, float multiplier, Operation<float[]> original) {
        return BrakingTouchLandingProof.nativeEvent(entity, distance, multiplier, original);
    }
    // Optional NEW422 seam. Its independent final witness fails closed without disabling NEW413.
    @WrapOperation(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 0, expect = 0)
    private boolean anchor$landingHurt(LivingEntity entity, DamageSource source, float amount, Operation<Boolean> original) {
        return BrakingTouchLandingProof.nativeLandingHurt(entity, source, amount, original);
    }
}

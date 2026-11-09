package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.NativeVectorParryAccess;
import cn.academy.port.highpower.VectorParryNativeBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Optional NEW412 call-site adapter; vanilla damage and knockback still execute normally. */
@Mixin(LivingEntity.class)
abstract class LivingEntityVectorParryMixin implements NativeVectorParryAccess {
    @Override public boolean academy$vectorParryNativeSeam() {
        return NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported();
    }

    @WrapMethod(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean academy$vectorParryHurt(DamageSource source, float amount, Operation<Boolean> original) {
        return VectorParryNativeBridge.nativeHurt((LivingEntity)(Object)this, source, amount, original);
    }

    @WrapOperation(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),
            require = 1, expect = 1, allow = 1)
    private void academy$vectorParryKnockback(LivingEntity entity, double strength, double x, double z, Operation<Void> original) {
        VectorParryNativeBridge.nativeKnockback(entity, strength, x, z, original);
    }

    @WrapOperation(method = "knockback(DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingKnockBack(Lnet/minecraft/world/entity/LivingEntity;FDD)Lnet/neoforged/neoforge/event/entity/living/LivingKnockBackEvent;"),
            require = 1, expect = 1, allow = 1)
    private LivingKnockBackEvent academy$vectorParryEvent(LivingEntity entity, float strength, double x, double z,
            Operation<LivingKnockBackEvent> original) {
        return VectorParryNativeBridge.nativeEvent(entity, strength, x, z, original);
    }
}

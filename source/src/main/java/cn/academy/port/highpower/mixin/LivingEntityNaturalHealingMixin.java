package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.HighPowerNaturalRecovery;
import cn.academy.port.highpower.NativeNaturalHealAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Invocation depth proves that a nested event cannot steal a FoodData source token. */
@Mixin(LivingEntity.class)
abstract class LivingEntityNaturalHealingMixin implements NativeNaturalHealAccess {
    @WrapMethod(method = "heal(F)V")
    private void academy$naturalHealDepth(float amount, Operation<Void> original) {
        HighPowerNaturalRecovery.nativeHeal((LivingEntity)(Object)this, () -> original.call(amount));
    }
}

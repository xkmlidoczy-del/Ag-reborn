package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.MagneticDomainSlowFall;
import cn.academy.port.highpower.VectorCageSlowdown;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only exact internally pending NEW152/NEW441 additions can be cancelled by these guards. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMagneticDomainSlowFallMixin implements MagneticDomainSlowFall.NativeGuard, VectorCageSlowdown.NativeGuard {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void academy$beginDomainSlowFall(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callback) {
        if (!MagneticDomainSlowFall.beginNativeAddition((LivingEntity)(Object)this, effect)
                || !VectorCageSlowdown.beginNativeAddition((LivingEntity)(Object)this, effect)) callback.setReturnValue(false);
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
                    shift = At.Shift.AFTER), cancellable = true, require = 1, expect = 1, allow = 1)
    private void academy$commitDomainSlowFall(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callback) {
        if (!MagneticDomainSlowFall.allowCommit((LivingEntity)(Object)this, effect)
                || !VectorCageSlowdown.allowCommit((LivingEntity)(Object)this, effect)) callback.setReturnValue(false);
    }
}

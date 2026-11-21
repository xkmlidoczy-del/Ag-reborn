package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CapBreak;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingSetHealthMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void academy$friendlySource(
            net.minecraft.world.damagesource.DamageSource source,
            float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        if (cn.academy.port.api.ControlRelations.refuses((LivingEntity) (Object) this, source))
            ci.setReturnValue(false);
    }

    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"), cancellable = true)
    private void academy$guardAbsorption(float amount, CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (cn.academy.port.api.ControlRelations.blocksWrite(target)
                && amount < target.getAbsorptionAmount()) ci.cancel();
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void academy$guardDeath(
            net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        if (cn.academy.port.api.ControlRelations.blocksWrite((LivingEntity) (Object) this))
            ci.cancel();
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void academy$usedTotem(
            net.minecraft.world.damagesource.DamageSource source,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ())
            cn.academy.port.api.CombatReceipt.usedTotem((LivingEntity) (Object) this, source);
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void academy$rescue(float health, CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (CapBreak.rescueWrite(target, health)
                || cn.academy.port.api.ControlRelations.blocksWrite(target)
                        && health < target.getHealth()) ci.cancel();
    }
}

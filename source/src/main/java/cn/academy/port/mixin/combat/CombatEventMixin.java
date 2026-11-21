package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CapBreak;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommonHooks.class)
abstract class CombatEventMixin {
    @Inject(method = "onEntityIncomingDamage", at = @At("HEAD"))
    private static void academy$incoming(
            LivingEntity target, DamageContainer damage, CallbackInfoReturnable<Boolean> ci) {
        CapBreak.incoming(target, damage);
    }

    @Inject(method = "onEntityIncomingDamage", at = @At("RETURN"))
    private static void academy$cancelResult(
            LivingEntity target, DamageContainer damage, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) {
            CapBreak.canceled(target, damage);
            cn.academy.port.api.CombatReceipt.canceled(target, damage.getSource());
        }
    }

    @Inject(method = "onLivingDeath", at = @At("RETURN"))
    private static void academy$deathOutcome(
            LivingEntity target, DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        cn.academy.port.api.CombatReceipt.deathEvent(target, source, ci.getReturnValueZ());
    }

    @Inject(method = "onLivingDamagePost", at = @At("HEAD"))
    private static void academy$settled(
            LivingEntity target, DamageContainer damage, CallbackInfo ci) {
        CapBreak.settled(target, damage);
        cn.academy.port.api.CombatReceipt.nativePost(target, damage);
    }
}

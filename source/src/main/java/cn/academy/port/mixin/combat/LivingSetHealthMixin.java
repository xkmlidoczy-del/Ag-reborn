package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CapBreak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingSetHealthMixin implements cn.academy.port.highpower.NativeSkillAccess {
    @Unique
    private static final MethodHandle academy$LIVING_HURT = academy$nativeHurt();

    @Unique
    private static MethodHandle academy$nativeHurt() {
        try {
            return MethodHandles.lookup().findSpecial(LivingEntity.class, "hurt",
                    MethodType.methodType(boolean.class, DamageSource.class, float.class), LivingEntity.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @Override
    public boolean academy$hurtFromOrigin(DamageSource source, float amount) {
        try {
            return (boolean) academy$LIVING_HURT.invokeExact((LivingEntity) (Object) this, source, amount);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot invoke native living damage", error);
        }
    }
    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void academy$commitOrigin(LivingEntity target, float value, Operation<Void> original) {
        if (!CapBreak.writeOriginHealth(target, value, false)) original.call(target, value);
    }

    @WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void academy$restoreOrigin(LivingEntity target, float value, Operation<Void> original) {
        if (!CapBreak.writeOriginHealth(target, value, true)) original.call(target, value);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void academy$captureLegacyEquipmentHealth(net.minecraft.nbt.CompoundTag save, CallbackInfo ci) {
        if ((Object)this instanceof net.minecraft.server.level.ServerPlayer player)
            cn.academy.port.highpower.CapacityProfile.captureLoadedVitals(player, save);
    }

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

    @Inject(method = "forceAddEffect", at = @At("HEAD"), cancellable = true)
    private void academy$guardForcedDebuff(net.minecraft.world.effect.MobEffectInstance effect,
            net.minecraft.world.entity.Entity source, CallbackInfo ci) {
        if (cn.academy.port.highpower.PlayerShieldArmor.rejectsEffect((LivingEntity) (Object) this, effect))
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
        if (cn.academy.port.api.CombatErasure.awaitingRespawn(target)) {
            if (health > 0F) ci.cancel();
            return;
        }
        if (CapBreak.rescueWrite(target, health)
                || cn.academy.port.api.ControlRelations.blocksWrite(target)
                        && health < target.getHealth()) ci.cancel();
    }
}

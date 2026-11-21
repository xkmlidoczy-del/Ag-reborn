package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CapBreak;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SynchedEntityData.class)
abstract class VitalityDataMixin {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Object academy$settle(
            Object value, EntityDataAccessor<?> key, Object proposed, boolean force) {
        return CapBreak.vitalityWrite((SynchedEntityData) (Object) this, key, value);
    }

    @SuppressWarnings("unchecked")
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T academy$readDuringRescue(T original, EntityDataAccessor<T> key) {
        Float rescued = CapBreak.rescueHealth((SynchedEntityData) (Object) this, key);
        return rescued == null ? original : (T) rescued;
    }
}

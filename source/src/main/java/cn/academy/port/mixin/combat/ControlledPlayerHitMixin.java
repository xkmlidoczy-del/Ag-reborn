package cn.academy.port.mixin.combat;

import cn.academy.port.api.ControlRelations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ControlledPlayerHitMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void academy$friendlySource(
            DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (ControlRelations.refuses((ServerPlayer) (Object) this, source))
            ci.setReturnValue(false);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void academy$guardDeath(DamageSource source, CallbackInfo ci) {
        if (ControlRelations.blocksWrite((ServerPlayer) (Object) this)) ci.cancel();
    }
}

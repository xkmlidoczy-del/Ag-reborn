package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.AscalonService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class AscalonServerPlayerGuard {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void omnitrix$serverPlayerHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (AscalonService.isProtected(self)) {
            AscalonService.onInterceptedHurt(self, source, amount);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void omnitrix$serverPlayerDie(DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (AscalonService.isProtected(self)) {
            self.deathTime = 0;
            self.hurtTime = 0;
            ci.cancel();
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void omnitrix$serverPlayerInvuln(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (AscalonService.isProtected((ServerPlayer) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}

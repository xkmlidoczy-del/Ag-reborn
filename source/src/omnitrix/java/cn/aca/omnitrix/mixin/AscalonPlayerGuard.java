package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.AscalonService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class AscalonPlayerGuard {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void omnitrix$playerHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (AscalonService.isProtected(self)) {
            AscalonService.onInterceptedHurt(self, source, amount);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void omnitrix$playerActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        if (AscalonService.isProtected((Player) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void omnitrix$playerDie(DamageSource source, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (AscalonService.isProtected(self)) {
            self.deathTime = 0;
            self.hurtTime = 0;
            ci.cancel();
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void omnitrix$playerInvuln(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (AscalonService.isProtected((Player) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void omnitrix$playerRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
                && !sp.hasDisconnected() && !sp.wonGame
                && AscalonService.blockForcedRemoval(sp)) {
            ci.cancel();
        }
    }
}

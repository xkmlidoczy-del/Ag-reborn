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

@Mixin(Entity.class)
abstract class EntityRemovalGuard {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void omnitrix$guardAscalonKill(CallbackInfo ci) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void omnitrix$guardAscalonRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
                && !sp.hasDisconnected() && !sp.wonGame
                && AscalonService.blockForcedRemoval(sp)) {
            ci.cancel();
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void omnitrix$guardEntityInvuln(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            cir.setReturnValue(true);
        }
    }
}

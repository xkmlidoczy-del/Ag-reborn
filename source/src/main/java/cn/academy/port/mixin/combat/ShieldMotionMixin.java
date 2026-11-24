package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.PlayerShieldArmor;
import cn.academy.port.api.ControlRelations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class ShieldMotionMixin {
    @ModifyVariable(
            method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Vec3 academy$preserveContactVelocity(Vec3 proposed) {
        return (Object) this instanceof ServerPlayer player && (PlayerShieldArmor.contacting(player)
                        || ControlRelations.inForeignTick(player) && PlayerShieldArmor.holding(player))
                ? player.getDeltaMovement()
                : proposed;
    }

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$blockPush(double x, double y, double z, CallbackInfo ci) {
        if (PlayerShieldArmor.ignoresControl((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void academy$blockForeignDisplacement(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof ServerPlayer player && ControlRelations.inForeignTick(player)
                && PlayerShieldArmor.ignoresControl(player)) ci.cancel();
    }

    @Inject(method = "unsetRemoved", at = @At("HEAD"), cancellable = true)
    private void academy$keepErased(CallbackInfo ci) {
        if (cn.academy.port.api.CombatErasure.erasedBody((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setTicksFrozen", at = @At("HEAD"), cancellable = true)
    private void academy$blockFreeze(int ticks, CallbackInfo ci) {
        if (ticks > 0 && PlayerShieldArmor.ignoresControl((Entity) (Object) this)) ci.cancel();
    }
}

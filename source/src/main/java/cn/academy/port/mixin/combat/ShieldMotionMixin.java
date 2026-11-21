package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.PlayerShieldArmor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
abstract class ShieldMotionMixin {
    @ModifyVariable(
            method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Vec3 academy$preserveContactVelocity(Vec3 proposed) {
        return (Object) this instanceof ServerPlayer player && PlayerShieldArmor.contacting(player)
                ? player.getDeltaMovement()
                : proposed;
    }
}

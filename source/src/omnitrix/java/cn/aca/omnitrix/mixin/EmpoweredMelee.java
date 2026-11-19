package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.Omnitrix;
import cn.academy.port.highpower.CapBreak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class EmpoweredMelee {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean omnitrix$primaryHit(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        return omnitrix$hurt(target, source, amount, original);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean omnitrix$sweepHit(LivingEntity target, DamageSource source, float amount, Operation<Boolean> original) {
        if (omnitrix$empowered()) {
            return CapBreak.aroundHurt(target, source, amount, () -> original.call(target, source, amount));
        }
        return original.call(target, source, amount);
    }

    private boolean omnitrix$hurt(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        if (omnitrix$empowered()) {
            return CapBreak.aroundHurt(target, source, amount, () -> original.call(target, source, amount));
        }
        return original.call(target, source, amount);
    }

    private boolean omnitrix$empowered() {
        return (Object) this instanceof ServerPlayer player
                && (player.getMainHandItem().is(Omnitrix.ASCALON.get())
                || player.hasData(Omnitrix.WRIST) && Omnitrix.wrist(player).hasForm());
    }
}

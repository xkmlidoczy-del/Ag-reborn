package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.CirculationListeningMotionProof;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class ArrowCirculationListeningMotionMixin {
    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;setPos(DDD)V"),
            require = 1, expect = 1, allow = 1)
    private void academy$listeningArrow(AbstractArrow entity, double x, double y, double z, Operation<Void> original) {
        CirculationListeningMotionProof.physicalSet(entity, x, y, z, original);
    }
}

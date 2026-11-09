package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.CirculationListeningMotionProof;
import cn.academy.port.highpower.NativeCirculationListeningMotionAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityCirculationListeningMotionMixin implements NativeCirculationListeningMotionAccess {
    @Unique private CirculationListeningMotionProof.State academy$circulationListeningMotion;
    public CirculationListeningMotionProof.State academy$circulationListeningMotionState() { return academy$circulationListeningMotion; }
    public void academy$circulationListeningMotionState(CirculationListeningMotionProof.State state) { academy$circulationListeningMotion = state; }
    @Inject(method = "setPosRaw(DDD)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void academy$listeningRaw(double x, double y, double z, CallbackInfo callback) {
        CirculationListeningMotionProof.beforeRaw((Entity)(Object)this, x, y, z);
    }
    @WrapOperation(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", ordinal = 1),
            require = 1, expect = 1, allow = 1)
    private void academy$listeningPhysical(Entity entity, double x, double y, double z, Operation<Void> original) {
        CirculationListeningMotionProof.physicalSet(entity, x, y, z, original);
    }
}

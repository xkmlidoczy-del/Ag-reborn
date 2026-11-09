package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.BrakingTouchLandingProof;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class EntityBrakingTouchMixin implements BrakingTouchLandingProof.Access {
    public boolean academy$brakingLandingSeam() { return NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(); }
    @WrapOperation(method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V"),
            require = 1, expect = 1, allow = 1)
    private void academy$brakingFallOn(Block block, Level level, BlockState state, BlockPos pos, Entity entity, float distance, Operation<Void> original) {
        BrakingTouchLandingProof.nativeFallOn(block, level, state, pos, entity, distance, original);
    }
}

package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.SurvivalForms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
abstract class DispenserOverload {
    @Inject(method = "tick", at = @At("TAIL"))
    private void omnitrix$afterRedstone(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        SurvivalForms.queueDispenses(level, pos, state);
    }
}

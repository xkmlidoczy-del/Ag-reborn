package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.SurvivalForms;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
abstract class WakeSurface {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void omnitrix$atSurface(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                   CallbackInfoReturnable<VoxelShape> callback) {
        if (!(context instanceof EntityCollisionContext collision) || !(collision.getEntity() instanceof Player player)
                || !SurvivalForms.surfaceSprint(player) || !state.getFluidState().is(FluidTags.WATER)
                || level.getFluidState(pos.above()).is(FluidTags.WATER)) return;
        VoxelShape surface = Shapes.box(0, 0, 0, 1, state.getFluidState().isSource() ? 1 : state.getFluidState().getOwnHeight(), 1);
        if (context.isAbove(surface, pos, false)) callback.setReturnValue(surface);
    }
}

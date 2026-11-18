package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.SurvivalForms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PowderSnowBlock.class)
abstract class PowderRunning {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void omnitrix$surface(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                 CallbackInfoReturnable<VoxelShape> callback) {
        if (context instanceof EntityCollisionContext collision && collision.getEntity() instanceof Player player
                && SurvivalForms.surfaceSprint(player) && player.getY() >= pos.getY()+.42) {
            double top = Math.clamp(player.getY()-pos.getY()-.00001,.42,1);
            callback.setReturnValue(Shapes.box(0,0,0,1,top,1));
        }
    }

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void omnitrix$walkOnSnow(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof Player player && player.isSprinting() && !player.isShiftKeyDown()
                && SurvivalForms.isClassic(player, Form.XLR8)) callback.setReturnValue(true);
    }
}

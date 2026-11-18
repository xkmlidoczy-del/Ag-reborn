package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.AscalonService;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.SurvivalForms;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class FormPhysics {
    @Shadow protected boolean jumping;

    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void omnitrix$xrl8Water(FluidState fluid, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Player player && SurvivalForms.surfaceSprint(player) && fluid.is(FluidTags.WATER)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void omnitrix$viltrumFlight(Vec3 input, CallbackInfo callback) {
        if ((Object) this instanceof Player player && AscalonService.viltrumTravel(player, input, this.jumping)) {
            callback.cancel();
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void omnitrix$heatblastLava(Vec3 input, CallbackInfo callback) {
        if (!((Object) this instanceof Player player) || !player.isInLava() || !player.isAffectedByFluids()
                || player.getAbilities().flying || player.isPassenger() || !SurvivalForms.isClassic(player, Form.HEATBLAST)) return;
        if (player.isControlledByLocalInstance()) {
            player.moveRelative(.075f, input);
            Vec3 look = player.getLookAngle();
            Vec3 motion = player.getDeltaMovement().add(0, -0.007 + Math.max(0, input.z) * look.y * .052, 0);
            if (player.isShiftKeyDown()) motion = motion.add(0, -.04, 0);
            player.setDeltaMovement(motion);
            player.move(MoverType.SELF, motion);
            player.setDeltaMovement(player.getDeltaMovement().multiply(.84, .83, .84));
            player.resetFallDistance();
        }
        callback.cancel();
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void omnitrix$snowStep(Vec3 input, CallbackInfo callback) {
        if (!((Object) this instanceof Player player) || !player.isControlledByLocalInstance() || !SurvivalForms.surfaceSprint(player)) return;
        var feet = player.blockPosition();
        double fraction = player.getY() - feet.getY();
        if (fraction < .4 || fraction > .998 || !player.level().getBlockState(feet).is(Blocks.POWDER_SNOW)) return;
        double step = 1 - fraction;
        if (player.level().noCollision(player, player.getBoundingBox().move(0, step + .0001, 0))) {
            player.setPos(player.getX(), feet.getY() + 1, player.getZ());
            if (player.getDeltaMovement().y < 0) player.setDeltaMovement(player.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0));
        }
    }
}

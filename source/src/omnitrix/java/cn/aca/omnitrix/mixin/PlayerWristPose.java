package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.client.WatchRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerWristPose {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void omnitrix$raiseWrist(LivingEntity entity, float walk, float amount, float time, float yaw, float pitch, CallbackInfo ci) {
        WatchRender.poseHuman((PlayerModel<?>)(Object)this, entity, time);
    }
}

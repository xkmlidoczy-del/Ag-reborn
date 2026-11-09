package cn.academy.port.client.mixin;

import cn.academy.port.client.CastPoseClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the cosmetic casting arm pose after vanilla sets the humanoid pose. Client only; a failure leaves the vanilla pose untouched. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelCastPoseMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void academy$castPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        try { CastPoseClient.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks, limbSwingAmount); }
        catch (RuntimeException cosmetic) { /* never break rendering for a gesture */ }
    }
}

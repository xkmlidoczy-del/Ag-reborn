package cn.academy.port.client.canon.mixin;

import cn.academy.port.client.canon.CanonPoseClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separate cosmetic layer after vanilla and existing pose setup. */
@Mixin(value=HumanoidModel.class,priority=900)
public abstract class CanonPoseMixin<T extends LivingEntity> {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"),require=0)
    private void academy$canonPose(T entity,float limbSwing,float limbSwingAmount,float ageInTicks,float netHeadYaw,float headPitch,CallbackInfo ci){
        try{CanonPoseClient.apply((HumanoidModel<?>)(Object)this,entity,ageInTicks);}catch(RuntimeException ignored){}
    }
}

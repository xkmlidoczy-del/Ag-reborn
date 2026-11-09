package cn.academy.port.fleet.mixin;

import cn.academy.port.fleet.GunItem;
import cn.academy.port.fleet.LabVehicle;
import cn.academy.port.fleet.client.ReloadTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla humanoid pose adapter. Its arms have one segment each; this does not
 * replace a full third-person skeletal animation layer or certify foregrip IK.
 */
@Mixin(HumanoidModel.class)
public abstract class PlayerPoseMixin<T extends LivingEntity> {
    @org.spongepowered.asm.mixin.Unique private boolean aclab$customSupport;
    @org.spongepowered.asm.mixin.Unique private boolean aclab$driver;
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void aclab$pose(T entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (player.isSpectator() || player.isSleeping() || player.isFallFlying() || player.isSwimming()) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if(aclab$driver){model.rightArm.visible=model.leftArm.visible=model.rightLeg.visible=model.leftLeg.visible=true;if(model instanceof net.minecraft.client.model.PlayerModel<?> pm){pm.rightSleeve.visible=pm.leftSleeve.visible=pm.rightPants.visible=pm.leftPants.visible=true;}aclab$driver=false;}
        if(aclab$customSupport){
            model.rightArm.visible=true;model.leftArm.visible=true;
            if(model instanceof net.minecraft.client.model.PlayerModel<?> pm){pm.rightSleeve.visible=player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE);pm.leftSleeve.visible=player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE);}
            aclab$customSupport=false;
        }

        // Do not overwrite arm pivot positions: the old z=0 reset broke crouch
        // and other vanilla poses, and the scooter z=-4 could leak across players.
        if (player.getVehicle() instanceof LabVehicle vehicle) {
            if(vehicle.getFirstPassenger()==player){model.rightArm.visible=model.leftArm.visible=model.rightLeg.visible=model.leftLeg.visible=false;if(model instanceof net.minecraft.client.model.PlayerModel<?> pm){pm.rightSleeve.visible=pm.leftSleeve.visible=pm.rightPants.visible=pm.leftPants.visible=false;}aclab$driver=true;return;}
            float steer = Float.isFinite(vehicle.steering()) ? Math.max(-1, Math.min(1, vehicle.steering())) : 0;
            float reach = vehicle.variant() == 2 ? -1.40f : -1.08f;
            model.rightArm.xRot = reach + steer * .10f;
            model.leftArm.xRot = reach - steer * .10f;
            model.rightArm.yRot = -.24f;
            model.leftArm.yRot = .24f;
            model.rightArm.zRot = .08f + steer * .22f;
            model.leftArm.zRot = -.08f + steer * .22f;
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof GunItem held)) return;
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart other = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        if(held.variant>=0){
            other.visible=false;aclab$customSupport=true;
            if(model instanceof net.minecraft.client.model.PlayerModel<?> pm){if(right)pm.leftSleeve.visible=false;else pm.rightSleeve.visible=false;}

            double time=(double)player.level().getGameTime()+Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            var rig=cn.academy.port.fleet.client.G03Rig.get(held.variant);var frame=cn.academy.port.fleet.client.G03Renderer.frame(player,player.getMainHandItem(),time,player.isSprinting()?1:0,(float)Math.min(1,player.getDeltaMovement().horizontalDistance()*5),0);
            var a=frame.poses[rig.id("third_main")].rot.getEulerAnglesXYZ(new org.joml.Vector3f());
            var b=frame.poses[rig.id("third_support")].rot.getEulerAnglesXYZ(new org.joml.Vector3f());
            main.xRot=a.x+model.head.xRot;main.yRot=model.head.yRot+side*a.y;main.zRot=side*a.z;
            other.xRot=b.x+model.head.xRot;other.yRot=model.head.yRot+side*b.y;other.zRot=side*b.z;
            return;
        }

        // Both axes track the head. The handover used only half the pitch and
        // hard-coded a right trigger arm for every player's main-hand setting.
        main.xRot = -(float) Math.PI / 2 + model.head.xRot;
        other.xRot = -1.46f + model.head.xRot;
        main.yRot = model.head.yRot - side * .10f;
        other.yRot = model.head.yRot + side * .36f;
        main.zRot = side * .02f;
        other.zRot = -side * .06f;
        var stack = player.getMainHandItem();
        double now = player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float r = ReloadTimeline.progress(GunItem.reloadStart(stack), GunItem.reloadEnd(stack), now);
        ReloadTimeline.Pose motion = ReloadTimeline.sample(r, GunItem.ammo(stack) == 0);
        main.xRot += (float) Math.toRadians(motion.rootPitchDeg());
        main.zRot += side * (float) Math.toRadians(motion.rootRollDeg());
        other.xRot += .40f * motion.handToMagazine() + .25f * motion.magazineDrop();
        other.yRot -= side * .20f * motion.handToMagazine();
        other.zRot -= side * .18f * motion.handToMagazine();
        other.xRot -= .28f * motion.handToCharge();
    }
}

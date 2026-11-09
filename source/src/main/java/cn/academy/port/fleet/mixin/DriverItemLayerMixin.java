package cn.academy.port.fleet.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cn.academy.port.fleet.GunItem;
import cn.academy.port.fleet.InteractionLab;
import cn.academy.port.fleet.LabVehicle;
import cn.academy.port.fleet.client.GunAnchors;
import cn.academy.port.fleet.client.LabMesh;
import cn.academy.port.fleet.client.ReloadTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person attachment patch for the independent experiment, not a TACZ layer. */
@Mixin(ItemInHandLayer.class)
public abstract class DriverItemLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void aclab$drawAnchoredGun(LivingEntity entity, ItemStack item, ItemDisplayContext context,
                                      HumanoidArm arm, PoseStack pose, MultiBufferSource buffers,
                                      int light, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (player.getVehicle() instanceof LabVehicle) { ci.cancel(); return; }
        if (player.isSpectator() || player.isSleeping() || player.isFallFlying() || player.isSwimming()) return;
        if (!(player.getMainHandItem().getItem() instanceof GunItem)) return;
        // A two-handed gun owns the support hand; do not display an intersecting
        // offhand item. It remains in inventory and is not consumed or moved.
        if (arm != player.getMainArm()) { ci.cancel(); return; }
        if (!(item.getItem() instanceof GunItem gun)) return;
        GunAnchors.Profile anchors = GunAnchors.forVariant(gun.variant);
        Minecraft mc = Minecraft.getInstance();
        double now = (double)entity.level().getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        float progress = ReloadTimeline.progress(GunItem.reloadStart(item), GunItem.reloadEnd(item), now);
        boolean empty = GunItem.ammo(item) == 0;
        double distance2 = mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        int lod = distance2 >= 32 * 32 ? 2 : distance2 >= 12 * 12 ? 1 : 0;
        org.joml.Matrix4f entityPose=new org.joml.Matrix4f(pose.last().pose());
        pose.pushPose();
        try {
            var layer = (ItemInHandLayer<?, ?>) (Object) this;
            ((ArmedModel) layer.getParentModel()).translateToHand(arm, pose);
            // translateToHand reaches the arm pivot, not the palm. This is the
            // vanilla held-item endpoint expressed directly in arm coordinates:
            // Rx(-90) * Ry(180) * (side/16, .125, -.625).
            // Then orient the custom +Z muzzle toward +Y of the arm, rather than
            // copying the vanilla item-axis rotation and reversing the rifle.
            pose.translate(arm == HumanoidArm.RIGHT ? -1f / 16f : 1f / 16f, 10f / 16f, -2f / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            if (arm == HumanoidArm.LEFT) pose.scale(-1, 1, 1);
            GunAnchors.Point grip = anchors.grip();
            pose.translate(-grip.x(), -grip.y(), -grip.z());
            if(gun.variant>=0){
                var frame=cn.academy.port.fleet.client.G03Renderer.frame(player,item,now,player.isSprinting()?1:0,(float)Math.min(1,player.getDeltaMovement().horizontalDistance()*5),0);
                var mainContact=cn.academy.port.fleet.client.G03Rig.get(gun.variant).point(frame,"right_hand");
                pose.translate(anchors.grip().x()-mainContact.x,anchors.grip().y()-mainContact.y,anchors.grip().z()-mainContact.z);
                cn.academy.port.fleet.client.G03Renderer.draw(pose,buffers,light,lod,frame,gun.variant);
                if(layer.getParentModel() instanceof net.minecraft.client.model.HumanoidModel<?> hm){
                    var support=arm==HumanoidArm.RIGHT?hm.leftArm:hm.rightArm;
                    var inverse=new org.joml.Matrix4f(pose.last().pose()).invert().mul(entityPose);
                    var shoulder=inverse.transformPosition(support.x/16f,support.y/16f,support.z/16f,new org.joml.Vector3f());
                    var pole=inverse.transformDirection((arm==HumanoidArm.RIGHT?1:-1)*.25f,.8f,.05f,new org.joml.Vector3f());
                    var rig=cn.academy.port.fleet.client.G03Rig.get(gun.variant);
                    cn.academy.port.fleet.client.GunRig.drawThirdContact(pose,buffers,light,player,shoulder,pole,rig,frame);
                }

                cn.academy.port.fleet.client.G03Renderer.burst(pose,buffers,frame,(now-GunItem.lastFire(item))/20.0,gun.variant);
            }else LabMesh.drawLod(InteractionLab.GUN_IDS[gun.variant], lod, pose, buffers, light,
                    0, 0, progress, 0, false, empty);
        } finally {
            pose.popPose();
        }
        ci.cancel();
    }
}

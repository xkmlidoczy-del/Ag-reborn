package cn.academy.port.fleet.client;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.academy.port.fleet.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Matrix4f;

public final class DriverLimbLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
 public DriverLimbLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}
 @Override public void render(PoseStack ps,MultiBufferSource buffers,int light,AbstractClientPlayer player,float walk,float speed,float partial,float age,float headYaw,float headPitch){
  if(!(player.getVehicle() instanceof LabVehicle vehicle)||vehicle.getFirstPassenger()!=player||player.isInvisible())return;
  var cfg=vehicle.config();var seat=cfg.seats.get(0);var entityPose=new Matrix4f(ps.last().pose());ps.pushPose();ps.translate(0,1.501,0);ps.scale(1/.9375f,-1/.9375f,-1/.9375f);ps.translate(-seat.x,-seat.y+.75,-seat.z);
  var toVehicle=new Matrix4f(ps.last().pose()).invert().mul(entityPose);GunRig.drawDriverLegs(ps,buffers,light,player,toVehicle,cfg);
  ps.pushPose();DriverHands.turn(ps,vehicle,partial);var inverse=new Matrix4f(ps.last().pose()).invert().mul(entityPose);var rig=DriverHands.rig(vehicle.variant());GunRig.drawDriverThirdArms(ps,buffers,light,player,rig,rig.clip("idle",0),inverse);ps.popPose();ps.popPose();
 }
}

package cn.academy.port.fleet.client;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cn.academy.port.fleet.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Hands use the actual world steering/handlebar pivot, including head look and configured FOV. */
public final class DriverHands {
 private static final Map<Integer,G03Rig> RIGS=new HashMap<>();
 public static void clear(){RIGS.clear();}
 public static G03Rig rig(int variant){return RIGS.computeIfAbsent(variant,v->{String file="vehicles/driver_"+InteractionLab.CAR_IDS[v]+".json";try(var in=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("academy",file)).orElseThrow().open()){return G03Rig.fromJson(JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());}catch(Exception ex){throw new IllegalStateException("Driver hand contact file "+file,ex);}});}
 public static void turn(PoseStack ps,LabVehicle v,float partial){var cfg=v.config();var centre=cfg.scooter()?cfg.nodes.stream().filter(n->n.name().equals("steer_front")).findFirst().orElseThrow().origin():cfg.steering;
  ps.translate(centre.x,centre.y,centre.z);ps.mulPose((cfg.scooter()?Axis.YP:Axis.ZP).rotationDegrees(-v.renderSteering(partial)*cfg.steeringMax));ps.translate(-centre.x,-centre.y,-centre.z);
 }
 public static void first(RenderHandEvent event,LabVehicle vehicle){
  var mc=Minecraft.getInstance();var cfg=vehicle.config();var camera=mc.gameRenderer.getMainCamera();var ps=event.getPoseStack();var base=new Matrix4f(ps.last().pose());float partial=event.getPartialTick();
  ps.pushPose();float fov=(float)(Math.tan(Math.toRadians(35))/Math.tan(Math.toRadians(mc.options.fov().get()/2.0)));ps.scale(fov,fov,1);
  ps.mulPose(new Quaternionf(camera.rotation()).conjugate());ps.mulPose(Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(partial,vehicle.yRotO,vehicle.getYRot())));ps.mulPose(Axis.XP.rotationDegrees(-vehicle.renderPitch(partial)));ps.mulPose(Axis.ZP.rotationDegrees(vehicle.renderRoll(partial)));
  ps.translate(-cfg.eye.x,-cfg.eye.y,-cfg.eye.z);turn(ps,vehicle,partial);var rig=rig(vehicle.variant());GunRig.drawDrivingAuthored(ps,event.getMultiBufferSource(),event.getPackedLight(),base,rig,rig.clip("idle",0));ps.popPose();
 }
}

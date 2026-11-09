package cn.academy.port.fleet.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import cn.academy.port.fleet.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Hierarchical controls, real cabin glazing and explicit LOD; no first-person replacement dashboard. */
public final class VehicleModel {
 private static final Map<String,AcmMeshReader.Mesh> CACHE=new HashMap<>();
 private static final Map<Integer,Integer> LODS=new HashMap<>();
 public static void clear(){CACHE.clear();LODS.clear();VehicleGpu.clear();}
 private static AcmMeshReader.Mesh load(String key){return CACHE.computeIfAbsent(key,id->{try(var in=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("academy","mesh/"+id+".bin")).orElseThrow().open()){return AcmMeshReader.read(in);}catch(Exception ex){throw new IllegalStateException("Vehicle mesh "+id,ex);}});}
 private static void apply(PoseStack ps,VehicleConfig c,String name,LabVehicle vehicle,float partial,Set<String> path){
  if(!path.add(name))throw new IllegalStateException("vehicle node cycle");var n=c.nodes.stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();
  if(n.parent()!=null)apply(ps,c,n.parent(),vehicle,partial,path);
  ps.translate(n.origin().x,n.origin().y,n.origin().z);
  float steer=vehicle.renderSteering(partial);
  if(name.equals("steer_front"))ps.mulPose(Axis.YP.rotationDegrees(-steer*24));
  if(name.startsWith("wheel_")){
   if(name.startsWith("wheel_0")&&!c.scooter())ps.mulPose(Axis.YP.rotationDegrees(-steer*27));
   ps.mulPose(Axis.XP.rotationDegrees(vehicle.wheelPhase+partial*vehicle.speed()*115));
  }
  if(name.equals("steering_wheel")&&!c.scooter())ps.mulPose(Axis.ZP.rotationDegrees(-steer*c.steeringMax));
  if(name.equals("door_slide"))ps.translate(0,0,-vehicle.doorAmount(partial)*.90);
  else if(c.doorSides.containsKey(name))ps.mulPose(Axis.YP.rotationDegrees(c.doorRotation(name,vehicle.doorAmount(partial))));
 }
 public static void draw(LabVehicle v,float partial,PoseStack ps,MultiBufferSource buffers,int light){
  var c=VehicleConfig.get(v.variant());var mc=Minecraft.getInstance();boolean inside=mc.player!=null&&mc.player.getVehicle()==v&&mc.options.getCameraType().isFirstPerson();
  double d=mc.gameRenderer.getMainCamera().getPosition().distanceTo(v.position());int old=LODS.getOrDefault(v.getId(),0),lod=old;
  if(inside)lod=0;else if(old==0&&d>19)lod=1;else if(old==1&&d<15)lod=0;else if(old==1&&d>42)lod=2;else if(old==2&&d<35)lod=1;
  LODS.put(v.getId(),lod);String id=c.id+(lod==0?"":"_lod"+lod);var mesh=load(id);
  var tex=ResourceLocation.fromNamespaceAndPath("academy","textures/models/"+c.id+".png");var glow=ResourceLocation.fromNamespaceAndPath("academy","textures/models/"+c.id+"_emission.png");
  for(var part:mesh.parts()){
   ps.pushPose();apply(ps,c,part.name(),v,partial,new HashSet<>());
   var glass=ResourceLocation.fromNamespaceAndPath("academy","textures/models/fleet_glass.png");
   if(part.name().startsWith("glass"))emit(part,ps,buffers.getBuffer(RenderType.entityTranslucent(glass)),light);
   else if(!VehicleGpu.draw(id,part,ps,tex,light))emit(part,ps,buffers.getBuffer(RenderType.entityCutoutNoCull(tex)),light);
   if(part.name().equals("lights"))emit(part,ps,buffers.getBuffer(RenderType.entityTranslucentEmissive(glow)),LightTexture.FULL_BRIGHT);
   ps.popPose();
  }
 }
 private static void emit(AcmMeshReader.Part part,PoseStack ps,VertexConsumer out,int light){float[] a=part.values();for(int tri=0;tri<part.triangles();tri++)for(int corner=0;corner<4;corner++){int i=(tri*3+Math.min(corner,2))*8;out.addVertex(ps.last(),a[i],a[i+1],a[i+2]).setColor(255,255,255,255).setUv(a[i+6],a[i+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(),a[i+3],a[i+4],a[i+5]);}}
}

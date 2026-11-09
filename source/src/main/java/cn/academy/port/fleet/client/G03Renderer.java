package cn.academy.port.fleet.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import cn.academy.port.fleet.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/** G03 owns a fresh mesh, authored hierarchy and synchronized part/hand actions. */
public final class G03Renderer {
 private static final Map<Integer,AcmMeshReader.Mesh> MESHES=new HashMap<>();
 private enum View {FIRST,THIRD}
 private static final Map<LivingEntity,EnumMap<View,Playback>> PLAYBACK=new WeakHashMap<>();
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/models/g03_coil_rifle.png");
 private static final ResourceLocation EMISSION=ResourceLocation.fromNamespaceAndPath("academy","textures/models/g03_coil_rifle_emission.png");
 private static final class Playback {
  int variant=-1;final G03Playback motion=new G03Playback();
  long action=Long.MIN_VALUE;double transitionStart,transitionTicks;
  G03Rig.Frame previous,from;
 }
 public static void clear(){MESHES.clear();PLAYBACK.clear();G03Rig.clear();}
 public static void resetFirstPerson(){for(var views:PLAYBACK.values())views.remove(View.FIRST);}
 public static G03Rig.Frame frame(LivingEntity entity,ItemStack stack,double time,float run,float walk,float equip){
  return frame(entity,stack,time,run,walk,View.THIRD,0);
 }
 private static G03Rig.Frame frame(LivingEntity entity,ItemStack stack,double requested,float run,float walk,View view,float aim){
  int variant=((GunItem)stack.getItem()).variant;G03Rig rig=G03Rig.get(variant);var views=PLAYBACK.computeIfAbsent(entity,k->new EnumMap<>(View.class));
  Playback state=views.computeIfAbsent(view,k->new Playback());if(state.variant!=variant){state=new Playback();state.variant=variant;views.put(view,state);}
  state.motion.update(requested,GunItem.lastFire(stack),run,walk,rig.duration("shoot"));
  double time=state.motion.time();long start=GunItem.reloadStart(stack),end=GunItem.reloadEnd(stack);
  boolean active=start>0&&end>start&&time>=start&&time<end;G03Rig.Frame f;
  if(active)f=rig.clip(GunItem.reloadWasEmpty(stack)?"reload_empty":"reload_tactical",(time-start)/20.0);
  else{
   f=rig.clip("idle",(time/20)%rig.duration("idle"));
   if(aim>0)f=rig.blend(f,rig.clip("aim",rig.duration("aim")),aim*.85f);
   if(state.motion.walk()>0)rig.additive(f,"walk",(time/20)%rig.duration("walk"),Math.min(.8f,state.motion.walk())*(1-.85f*aim));
   if(state.motion.run()>0)f=rig.blend(f,rig.clip("sprint",0),state.motion.run());
   // Draw is keyed to selecting the weapon, never to vanilla attack-strength/equip height.
   if(view==View.FIRST&&state.motion.drawSeconds()<rig.duration("draw"))
    f=rig.blend(f,rig.clip("draw",state.motion.drawSeconds()),1-G03Playback.ease(state.motion.drawSeconds()/rig.duration("draw")));
   // Keep the outgoing shot's recovery tail when a subsequent shot starts at zero displacement.
   for(long shot:state.motion.shots())rig.additive(f,"shoot",(time-shot)/20.0,1-.22f*aim);
  }
  long action=active?start:0;
  if(state.action!=action){
   state.from=state.previous==null?null:state.previous.copy();state.transitionStart=time;
   state.transitionTicks=active?2:4;state.action=action;
  }
  if(state.from!=null){double elapsed=time-state.transitionStart;
   if(elapsed<state.transitionTicks)f=rig.blend(state.from,f,G03Playback.ease(elapsed/state.transitionTicks));
   else state.from=null;
  }
  if(!active){f.poses[rig.id("mag_old")].visible=GunItem.hasMagazine(stack)?1:0;f.poses[rig.id("mag_new")].visible=0;}
  state.previous=f.copy();return f;
 }
 public static void first(RenderHandEvent e,ItemStack stack,float aim,float sprint,double time){
  int variant=((GunItem)stack.getItem()).variant;Minecraft mc=Minecraft.getInstance();GunAnchors.Profile profile=GunAnchors.forVariant(variant);PoseStack ps=e.getPoseStack();Matrix4f base=new Matrix4f(ps.last().pose());float side=mc.player.getMainArm()==HumanoidArm.RIGHT?1:-1;
  G03Rig.Frame f=frame(mc.player,stack,time,sprint,(float)Math.min(1,mc.player.getDeltaMovement().horizontalDistance()*5),View.FIRST,aim);
  G03MotionQa.sample(stack,time,e.getEquipProgress(),sprint,(float)Math.min(1,mc.player.getDeltaMovement().horizontalDistance()*5),f);
  ps.pushPose();ps.translate(side*(profile.hip().x()+(profile.sight().x()-profile.hip().x())*aim),profile.hip().y()+(-profile.sight().y()-profile.hip().y())*aim,profile.hip().z()+(-profile.adsDistance()-profile.hip().z())*aim);ps.scale(side,1,1);ps.mulPose(Axis.YP.rotationDegrees(180));
  draw(ps,e.getMultiBufferSource(),e.getPackedLight(),0,f,variant);
  burst(ps,e.getMultiBufferSource(),f,(time-GunItem.lastFire(stack))/20.0,variant);
  G03Rig rig=G03Rig.get(variant);GunRig.drawContactAuthored(ps,e.getMultiBufferSource(),e.getPackedLight(),base,rig,f);
  ps.popPose();
 }
 public static void draw(PoseStack ps,MultiBufferSource buffers,int light,int lod,G03Rig.Frame frame){draw(ps,buffers,light,lod,frame,2);}
 public static void draw(PoseStack ps,MultiBufferSource buffers,int light,int lod,G03Rig.Frame frame,int variant){
  AcmMeshReader.Mesh mesh=MESHES.computeIfAbsent(variant*3+lod,k->{try(var stream=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("academy","mesh/"+InteractionLab.GUN_IDS[variant]+(lod==0?"":"_lod"+lod)+".bin")).orElseThrow().open()){return AcmMeshReader.read(stream);}catch(Exception ex){throw new IllegalStateException("G03 mesh",ex);}});
  G03Rig rig=G03Rig.get(variant);ResourceLocation texture=ResourceLocation.fromNamespaceAndPath("academy","textures/models/"+InteractionLab.GUN_IDS[variant]+".png"),emission=ResourceLocation.fromNamespaceAndPath("academy","textures/models/"+InteractionLab.GUN_IDS[variant]+"_emission.png");
  for(var part:mesh.parts()){
   int n=rig.id(part.name());if(!rig.visible(frame,n))continue;ps.pushPose();rig.apply(ps,frame,n);
   emit(part,ps,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light);
   if(part.name().equals("status")||part.name().startsWith("mag_"))emit(part,ps,buffers.getBuffer(RenderType.entityTranslucentEmissive(emission)),LightTexture.FULL_BRIGHT);
   ps.popPose();
  }
 }
 public static void burst(PoseStack ps,MultiBufferSource buffers,G03Rig.Frame frame,double age){burst(ps,buffers,frame,age,2);}
 public static void burst(PoseStack ps,MultiBufferSource buffers,G03Rig.Frame frame,double age,int variant){
  if(age<0||age>.075)return;ps.pushPose();var rig=G03Rig.get(variant);rig.apply(ps,frame,rig.id("weapon"));var muzzle=GunAnchors.forVariant(variant).muzzle();ps.translate(muzzle.x(),muzzle.y(),muzzle.z());
  var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath("academy","textures/models/c03_discharge.png")));
  float size=.027f*(1-(float)age/.1f);float[][] xy={{-size,-size},{size,-size},{size,size},{-size,size}};
  for(int i=0;i<4;i++)out.addVertex(ps.last(),xy[i][0],xy[i][1],0).setColor(255,255,255,210).setUv(i==0||i==3?0:1,i<2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(ps.last(),0,0,-1);
  ps.popPose();
 }
 private static void emit(AcmMeshReader.Part part,PoseStack ps,VertexConsumer out,int light){float[] v=part.values();for(int tri=0;tri<part.triangles();tri++)for(int corner=0;corner<4;corner++){int i=(tri*3+Math.min(corner,2))*8;out.addVertex(ps.last(),v[i],v[i+1],v[i+2]).setColor(255,255,255,255).setUv(v[i+6],v[i+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(),v[i+3],v[i+4],v[i+5]);}}
}

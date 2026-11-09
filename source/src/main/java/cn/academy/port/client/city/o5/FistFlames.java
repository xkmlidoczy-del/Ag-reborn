package cn.academy.port.client.city.o5;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Compact looping black plasma, attached to each actual glove transform. */
public final class FistFlames {
 public static final ResourceLocation TEX=ResourceLocation.fromNamespaceAndPath("academy","textures/combat/fist_flames.png");
 public static final ResourceLocation SHELL=ResourceLocation.fromNamespaceAndPath("academy","textures/combat/fist_shell.png");
 public static int firstPersonPasses,thirdPersonPasses,surfacePasses;public static final Set<Integer> phases=new HashSet<>();
 private static void vertex(VertexConsumer out,PoseStack ps,float x,float y,float z,float u,float v,float alpha){
  out.addVertex(ps.last(),x,y,z).setColor(255,255,255,(int)(255*alpha)).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(),0,0,1);
 }
 private static void tongue(VertexConsumer out,PoseStack ps,int cell,double time,float width,float height,float alpha,float bend){
  float u=(cell%4)*.25f+.0005f,v=(cell/4)*.25f+.0005f,span=.249f;
  for(int row=0;row<10;row++){
   float a=row/10f,b=(row+1)/10f;
   float da=(float)Math.sin(a*6-time*.19)*bend*a*a,db=(float)Math.sin(b*6-time*.19)*bend*b*b;
   float za=a*a*bend*.65f,zb=b*b*bend*.65f;
   vertex(out,ps,-width/2+da,a*height,za,u,v+span*(1-a),alpha);vertex(out,ps,-width/2+db,b*height,zb,u,v+span*(1-b),alpha);
   vertex(out,ps,width/2+db,b*height,zb,u+span,v+span*(1-b),alpha);vertex(out,ps,width/2+da,a*height,za,u+span,v+span*(1-a),alpha);
  }
 }
 private static float signedPower(float x){return Math.copySign((float)Math.pow(Math.abs(x),.62),x);}
 private static void shellVertex(VertexConsumer out,PoseStack ps,double theta,double phi,float u,float v,double time,float drive,float curl,float alpha){
  float sin=(float)Math.sin(phi),nx=sin*(float)Math.cos(theta),ny=(float)Math.cos(phi),nz=sin*(float)Math.sin(theta);
  float ripple=(float)(.0025*Math.sin(theta*7+phi*5-time*.24)+.0015*Math.sin(theta*13-phi*8+time*.31));
  float open=1-curl;
  float x=signedPower(nx)*(.110f+ripple),y=-.026f+signedPower(ny)*(.101f+ripple),z=-.09f-open*.022f+signedPower(nz)*(.14f+open*.035f+ripple);
  vertex(out,ps,x,y,z,u,v,alpha);
 }
 private static void shell(PoseStack ps,MultiBufferSource buffers,int phase,double time,float drive,float curl){
  var out=buffers.getBuffer(RenderType.entityTranslucent(SHELL));float u0=(phase%4)*.25f+.0004f,v0=(phase/4)*.25f+.0004f,span=.2492f;
  for(int lat=0;lat<12;lat++)for(int lon=0;lon<24;lon++){
   float a=lon/24f,b=(lon+1)/24f,c=lat/12f,d=(lat+1)/12f,alpha=.43f+.43f*drive;
   shellVertex(out,ps,a*Math.PI*2,c*Math.PI,u0+a*span,v0+c*span,time,drive,curl,alpha);
   shellVertex(out,ps,a*Math.PI*2,d*Math.PI,u0+a*span,v0+d*span,time,drive,curl,alpha);
   shellVertex(out,ps,b*Math.PI*2,d*Math.PI,u0+b*span,v0+d*span,time,drive,curl,alpha);
   shellVertex(out,ps,b*Math.PI*2,c*Math.PI,u0+b*span,v0+c*span,time,drive,curl,alpha);
  }surfacePasses++;
 }
 public static int contactPulsePasses,firstContactPasses,thirdContactPasses;
 public static float contactPulse(float age){return age>=0&&age<3.2f?(float)Math.exp(-age/1.15):0;}
 public static float handWeight(int move,boolean left){return switch(move){case 0->left?1:.24f;case 1,4,5->left?.24f:1;case 6,7,8->.32f;default->1;};}
 public static void draw(PoseStack ps,MultiBufferSource buffers,double time,float drive,float turn,float curl,boolean left,boolean first,float hitAge,int move){
  int phase=Math.floorMod((int)(time*.85+(left?0:7)),16);phases.add(phase);if(first)firstPersonPasses++;else thirdPersonPasses++;
  float weight=handWeight(move,left);drive*=weight;float contact=contactPulse(hitAge)*weight;
  float residual=hitAge>=0&&hitAge<7?(float)Math.exp(-hitAge/2.8)*.18f*weight:0;
  float power=Math.min(1,.055f+drive*.66f+contact*.85f+residual);
  if(contact>.05f){contactPulsePasses++;if(first)firstContactPasses++;else thirdContactPasses++;}
  shell(ps,buffers,phase,time,power,curl);
  var out=buffers.getBuffer(RenderType.entityTranslucent(TEX));float direction=turn<0?-1:1;
  // Compact ink tears peel from the knuckles; silhouette stays close to the fist.
  for(int stream=0;stream<3;stream++){
   double a=stream*Math.PI*2/3+time*.04+direction*drive*.24;
   ps.pushPose();ps.translate(Math.cos(a)*.102,-.026+Math.sin(a)*.093,-.10);
   ps.mulPose(Axis.ZP.rotation((float)(a-Math.PI/2)));ps.mulPose(Axis.XP.rotationDegrees(38+drive*34));
   tongue(out,ps,(phase+stream*5)%16,time+stream*9,.12f+.04f*power,.05f+.12f*power,.28f+.48f*power,.018f+power*.014f);ps.popPose();
  }
  if(drive>.10f)for(int side:new int[]{-1,1}){ps.pushPose();ps.translate(side*.07,-.02,.025);ps.mulPose(Axis.XP.rotationDegrees(turn<0?108:65));ps.mulPose(Axis.ZP.rotation(side*.25f));
   tongue(out,ps,(phase+9)%16,time,.16f,.09f+.20f*drive,.6f*drive,.03f);ps.popPose();}
  var bolts=buffers.getBuffer(RenderType.entityTranslucent(CombatEffects.WHITE));BlackFlashBolts.draw(bolts,ps,time,power,contact,left);
  if(contact>.14f){var stamp=buffers.getBuffer(RenderType.entityTranslucent(CombatEffects.ENERGY));
   CombatEffects.sprite(stamp,ps,new net.minecraft.world.phys.Vec3(0,-.012,-.245),new net.minecraft.world.phys.Vec3(1,0,0),new net.minecraft.world.phys.Vec3(0,1,0),.24+contact*.06,.24+contact*.06,1,contact*.84f);}
 }
}

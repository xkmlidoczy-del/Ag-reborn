package cn.academy.port.client.city.o5;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** 18 rigid joints; two-bone arms/legs and independently planted feet. */
public final class FullBodyRig {
 static final ResourceLocation BASE=id("textures/item/armor/black_gold.png"),GLOW=id("textures/item/armor/black_gold_glowmask.png");
 static final ResourceLocation SHEEN=id("textures/item/armor/black_gold_sheen.png");
 public static int sheenFrames;public static float maximumSheenAlpha;public static final Set<Integer> sheenPhases=new TreeSet<>();
 record Part(int texture,float[] vertices){}record Group(String name,Vector3f pivot,List<Part> parts){}
 record Chain(Vector3f start,Vector3f middle,Vector3f end,Quaternionf upper,Quaternionf lower){}
 static final Map<String,Group> GROUPS=new LinkedHashMap<>();
 public static float maxBoneError,maxPlantedError;public static int frames,visibleFrames;
 public static final Set<Integer> seenVisibleMoves=new TreeSet<>();
 static ResourceLocation id(String p){return ResourceLocation.fromNamespaceAndPath("academy",p);}
 static void load(){if(!GROUPS.isEmpty())return;
  try(var in=Minecraft.getInstance().getResourceManager().getResource(id("rig/o5_fullbody.json")).orElseThrow().open()){
   var root=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
   for(var e:root.getAsJsonArray("joints")){var g=e.getAsJsonObject();var a=g.getAsJsonArray("pivot");Vector3f pivot=new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());List<Part> parts=new ArrayList<>();
    for(var p:g.getAsJsonArray("parts")){var part=p.getAsJsonObject();var values=part.getAsJsonArray("vertices");float[] data=new float[values.size()];for(int i=0;i<data.length;i++)data[i]=values.get(i).getAsFloat();parts.add(new Part(part.get("texture").getAsInt(),data));}
    var group=new Group(g.get("name").getAsString(),pivot,parts);GROUPS.put(group.name,group);
   }
  }catch(Exception ex){throw new IllegalStateException("O5 full-body rig cannot load",ex);}
 }
 static Vector3f bind(String n){return new Vector3f(GROUPS.get(n).pivot);}
 static Matrix4f at(Vector3f p,Quaternionf q){return new Matrix4f().translation(p).rotate(q);}
 static Quaternionf rotation(Vector3f v){return new Quaternionf().rotationXYZ(v.x,v.y,v.z);}
 static Matrix4f child(Matrix4f parent,String node,String parentName,Vector3f rot){return new Matrix4f(parent).translate(bind(node).sub(bind(parentName))).rotate(rotation(rot));}
 static Chain solve(Vector3f start,Vector3f target,Vector3f pole,float a,float b){
  Vector3f delta=new Vector3f(target).sub(start);float raw=delta.length();Vector3f dir=raw>1e-6f?delta.div(raw):new Vector3f(0,-1,0);
  float d=Math.min(a+b-.0001f,Math.max(Math.abs(a-b)+.0001f,raw));Vector3f end=new Vector3f(start).fma(d,dir);
  Vector3f bend=new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
  if(bend.lengthSquared()<1e-8f)bend.set(0,0,-1).sub(new Vector3f(dir).mul(-dir.z));bend.normalize();
  float x=(a*a-b*b+d*d)/(2*d),h=(float)Math.sqrt(Math.max(0,a*a-x*x));Vector3f mid=new Vector3f(start).fma(x,dir).fma(h,bend);
  Quaternionf upper=new Quaternionf().rotationTo(new Vector3f(0,-1,0),new Vector3f(mid).sub(start).normalize());
  Quaternionf lower=new Quaternionf().rotationTo(new Vector3f(0,-1,0),new Vector3f(end).sub(mid).normalize());
  maxBoneError=Math.max(maxBoneError,Math.max(Math.abs(start.distance(mid)-a),Math.abs(mid.distance(end)-b)));
  return new Chain(new Vector3f(start),mid,end,upper,lower);
 }
 public static Map<String,Matrix4f> matrices(FullBodyMotion.Frame f,float yaw,float pitch){
  load();Map<String,Matrix4f> m=new HashMap<>();var pelvis=new Matrix4f().translation(bind("pelvis").add(f.root)).rotate(rotation(f.pelvis));m.put("pelvis",pelvis);
  var spine=child(pelvis,"spine","pelvis",f.spine);m.put("spine",spine);var chest=child(spine,"chest","spine",f.chest);m.put("chest",chest);
  Vector3f headRot=new Vector3f(-pitch-f.pelvis.x-f.spine.x-f.chest.x,yaw-f.pelvis.y-f.spine.y-f.chest.y,-f.pelvis.z-f.spine.z-f.chest.z);
  m.put("head",child(chest,"head","chest",headRot));
  for(String s:List.of("l","r")){
   boolean left=s.equals("l");Vector3f shoulder=chest.transformPosition(bind(s+"UpperArm").sub(bind("chest")),new Vector3f());
   Vector3f pole=chest.transformDirection(new Vector3f(left?f.leftPole:f.rightPole),new Vector3f());
   var arm=solve(shoulder,left?f.lh:f.rh,pole,.300f,.325f);m.put(s+"UpperArm",at(arm.start,arm.upper));m.put(s+"Forearm",at(arm.middle,arm.lower));
   Quaternionf wrist=new Quaternionf(arm.lower).rotateY(left?-.10f:.10f).rotateZ(left?0:f.wristRoll);m.put(s+"Hand",at(arm.end,wrist));
   Quaternionf cq=chest.getNormalizedRotation(new Quaternionf()),relative=new Quaternionf(cq).conjugate().mul(arm.upper);
   m.put(s+"Shoulder",at(shoulder,new Quaternionf(cq).mul(new Quaternionf().slerp(relative,.22f))));
   Vector3f hip=pelvis.transformPosition(bind(s+"Thigh").sub(bind("pelvis")),new Vector3f()),foot=left?f.lf:f.rf;
   var leg=solve(hip,foot,new Vector3f(0,.08f,-1),.34375f,.34375f);m.put(s+"Thigh",at(leg.start,leg.upper));m.put(s+"Shin",at(leg.middle,leg.lower));
   m.put(s+"Foot",new Matrix4f().translation(leg.end).rotateX(left?f.leftFootPitch:f.rightFootPitch));
   if(left?f.leftPlanted:f.rightPlanted)maxPlantedError=Math.max(maxPlantedError,leg.end.distance(foot));
  }return m;
 }
 static void pass(PoseStack ps,MultiBufferSource buffers,Map<String,Matrix4f> poses,int texture,RenderType type,int light,int color){
  VertexConsumer out=buffers.getBuffer(type);int a=color>>>24,r=(color>>16)&255,g=(color>>8)&255,b=color&255;
  for(var group:GROUPS.values()){var transform=poses.get(group.name);if(transform==null)continue;ps.pushPose();ps.mulPose(transform);
   for(var part:group.parts)if(part.texture==texture){float[] v=part.vertices;for(int i=0;i<v.length;i+=8)out.addVertex(ps.last(),v[i],v[i+1],v[i+2]).setColor(r,g,b,a).setUv(v[i+6],v[i+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(),v[i+3],v[i+4],v[i+5]);}
   ps.popPose();
  }
 }
 static void glove(PoseStack ps,MultiBufferSource buffers,Matrix4f matrix,int light,boolean left,float curl,float index){
  ps.pushPose();ps.mulPose(matrix);int side=left?1:-1;
  CombatHands.box(ps,buffers,light,0,-.04,-.035,.19,.15,.15,7);
  for(int i=0;i<4;i++){
   float xx=(i-1.5f)*.045f,c=i==0?index:curl;
   CombatHands.box(ps,buffers,light,xx,-.08,-.12,.038,.035,.034,1);
   ps.pushPose();ps.translate(xx,-.10,-.025);ps.mulPose(com.mojang.math.Axis.XP.rotation(c*.85f));
   CombatHands.box(ps,buffers,light,0,-.036,0,.037,.072,.038,7);ps.translate(0,-.071,0);ps.mulPose(com.mojang.math.Axis.XP.rotation(c));CombatHands.box(ps,buffers,light,0,-.027,0,.034,.054,.036,7);ps.popPose();
  }
  ps.pushPose();ps.translate(side*.105,-.025,-.02);ps.mulPose(com.mojang.math.Axis.ZP.rotation(-side*.65f));CombatHands.box(ps,buffers,light,0,-.037,0,.045,.08,.045,7);ps.popPose();ps.popPose();
 }
 static void sheen(PoseStack ps,MultiBufferSource buffers,Map<String,Matrix4f> poses,FullBodyMotion.Frame frame,double now,boolean active){
  var out=buffers.getBuffer(RenderType.entityTranslucent(SHEEN));float travel=(float)((now%128)/128);float center=2.16f-travel*2.42f;
  Vector3f pos=new Vector3f(),normal=new Vector3f(),view=new Vector3f(),half=new Vector3f(),key=new Vector3f(.46f+(float)Math.sin(now*.02)*.09f,.72f,-.43f).normalize();
  for(var group:GROUPS.values()){var transform=poses.get(group.name);if(transform==null)continue;ps.pushPose();ps.mulPose(transform);
   for(var part:group.parts)if(part.texture==0){float[] v=part.vertices;for(int i=0;i<v.length;i+=8){
    transform.transformPosition(v[i],v[i+1],v[i+2],pos);transform.transformDirection(v[i+3],v[i+4],v[i+5],normal).normalize();
    view.set(frame.viewLocal).sub(pos).normalize();half.set(view).add(key).normalize();
    float spec=(float)Math.pow(Math.max(0,normal.dot(half)),32),rim=(float)Math.pow(1-Math.abs(normal.dot(view)),3.2);
    float distance=(pos.y-center)/.145f,band=(float)Math.exp(-distance*distance),alpha=Math.min(.58f,(.26f*spec+.09f*rim)+band*(.25f+.16f*Math.max(0,normal.dot(view)))*(active?1.12f:1));
    maximumSheenAlpha=Math.max(maximumSheenAlpha,alpha);
    out.addVertex(ps.last(),v[i],v[i+1],v[i+2]).setColor(255,255,255,(int)(255*alpha)).setUv(v[i+6],v[i+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(),v[i+3],v[i+4],v[i+5]);
   }}ps.popPose();
  }sheenFrames++;sheenPhases.add(Math.floorMod((int)(now/4),32));
 }
 public static void draw(PoseStack ps,MultiBufferSource buffers,FullBodyMotion.Frame frame,float headYaw,float headPitch,int light,double now,boolean charged,boolean evade,int tint,boolean ghost){
  var pose=matrices(frame,headYaw,headPitch);
  pass(ps,buffers,pose,0,ghost?RenderType.entityTranslucent(BASE):RenderType.entityCutoutNoCull(BASE),light,tint);
  pass(ps,buffers,pose,1,ghost?RenderType.entityTranslucent(CombatHands.TEX):RenderType.entityCutoutNoCull(CombatHands.TEX),light,tint);
  if(!ghost){
   visibleFrames++;
   sheen(ps,buffers,pose,frame,now,charged);
   pass(ps,buffers,pose,0,RenderType.entityTranslucent(GLOW),0xF000F0,0xFFFFFFFF);
   int phase=Math.floorMod((int)(now*(charged||evade?.8:.22)),8),alpha=(int)(255*(evade?.93:charged?.80:.32));
   var texture=id("textures/item/armor/black_gold_flow_"+phase+".png");pass(ps,buffers,pose,0,RenderType.entityTranslucent(texture),0xF000F0,(alpha<<24)|0xFFFFFF);
   glove(ps,buffers,pose.get("lHand"),light,true,frame.leftCurl,frame.leftIndex);glove(ps,buffers,pose.get("rHand"),light,false,frame.rightCurl,frame.rightIndex);
   for(String side:List.of("l","r")){boolean left=side.equals("l");ps.pushPose();ps.mulPose(pose.get(side+"Hand"));ps.translate(0,0,-.02);ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));FistFlames.draw(ps,buffers,now,frame.energyDrive,frame.energyTurn,left?frame.leftCurl:frame.rightCurl,left,false,O5CombatVisuals.contactAge(frame.owner,now),frame.move);ps.popPose();}
  }
  frames++;
 }
}

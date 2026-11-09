package cn.academy.port.worldboss.client;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.WorldBossContent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;

/**
 * Forced, real-time spawn cinematic. The player's view is taken over by a detached camera (a client-side Marker entity), all input and HUD are locked,
 * and the shots are functions of the boss' own animation clock, so camera, animation, FX and the server-side terrain changes stay in lock-step
 * at any tick rate. The player's original view is blended back in at the end and input is released.
 */
public final class Cinematic {
 public static boolean active;public static int shownFor=-1;public static float time,fovNow=70;public static boolean finished;
 static WorldBossContent.Boss boss;static float startGT;static Marker cam;static Vec3 startPos;static float startYaw,startPitch;static int swapTick,ticks;static net.minecraft.world.entity.Entity savedCam;
 static final float END=8.6f;
 static float previousTime=-1;
 record Shot(float t0,float t1,double[] p0,double[] p1,double[] g0,double[] g1,float f0,float f1){}
 static Shot S(float t0,float t1,double[] p0,double[] p1,double[] g0,double[] g1,float f0,float f1){return new Shot(t0,t1,p0,p1,g0,g1,f0,f1);}
 static double[] v(double x,double y,double z){return new double[]{x,y,z};}
 static final Shot[] SHOTS={
  S(0,2.4f,v(6,2.4,-84),v(3,2.0,-56),v(0,6,0),v(0,10,-2),60,46),        // low push-in toward the storm-lit pit
  S(2.4f,3.35f,v(-30,1.4,-26),v(-24,2.6,-22),v(0,4,-8),v(0,12,-10),72,62), // cut: pit edge, the head clears the rim
  S(3.35f,4.45f,v(16,1.0,-14),v(12,1.6,-12),v(5,2,0),v(4,9,-3),76,64),     // cut: foot level for the stomp
  S(4.45f,6.8f,v(1,3.5,-48),v(-1,6,-70),v(0,19,-18),v(0,24,-20),38,66),   // roar: dolly back while zooming out
  S(6.8f,8.6f,v(-38,15,-70),v(-30,10,-88),v(0,14,-6),v(0,15,-4),55,50)};  // wide hero crane
 static final float[] CUTS={2.4f,3.35f,4.45f,6.8f};

 public static void start(WorldBossContent.Boss b){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!b.isAlive()||b.isRemoved())return;active=true;finished=false;boss=b;startGT=b.start();shownFor=b.getId();savedCam=mc.getCameraEntity();
  startPos=savedCam==null?mc.player.getEyePosition():savedCam.getEyePosition();startYaw=savedCam==null?mc.player.getYRot():savedCam.getYRot();startPitch=savedCam==null?mc.player.getXRot():savedCam.getXRot();cutFlag=-1;stomp=false;previousTime=-1;cam=new Marker(EntityType.MARKER,mc.level);cam.setPos(startPos);swapTick=ticks;mc.setCameraEntity(cam);
  System.out.println("WORLDBOSS_CINEMATIC_START gt="+mc.level.getGameTime());}
 static void stop(){var mc=Minecraft.getInstance();active=false;finished=true;
  if(mc.getCameraEntity()==cam){var restore=savedCam!=null&&!savedCam.isRemoved()&&savedCam.level()==mc.level?savedCam:mc.player;mc.setCameraEntity(restore);}
  cam=null;boss=null;savedCam=null;System.out.println("WORLDBOSS_CINEMATIC_END");}
 static void clear(){if(active)stop();shownFor=-1;finished=false;time=0;fovNow=70;cutFlag=-1;stomp=false;previousTime=-1;}

 static double smooth(double x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
 static double[] lerp(double[] a,double[] b,double t){return new double[]{Mth.lerp(t,a[0],b[0]),Mth.lerp(t,a[1],b[1]),Mth.lerp(t,a[2],b[2])};}
 public static void tick(ClientTickEvent.Post e){ticks++;}

 public static void frame(RenderFrameEvent.Pre e){
  if(!active)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||boss==null||boss.isRemoved()||!boss.isAlive()||boss.level()!=mc.level){if(active)stop();return;}
  float gt=Shake.gt();time=(gt-startGT)/20f;if(time>END){stop();return;}
  Shot s=SHOTS[SHOTS.length-1];for(Shot x:SHOTS)if(time<x.t1){s=x;break;}
  double k=smooth((time-s.t0)/(s.t1-s.t0));double[] p=lerp(s.p0,s.p1,k),g=lerp(s.g0,s.g1,k);double fov=Mth.lerp(k,s.f0,s.f1);
  // handheld drift
  p[0]+=.12*Math.sin(time*1.7)+.05*Math.sin(time*5.3);p[1]+=.10*Math.sin(time*2.3+1)+.04*Math.sin(time*6.1);g[1]+=.15*Math.sin(time*1.9+.5);
  Vec3 pos=boss.local(p[0],p[1],p[2]),tgt=boss.local(g[0],g[1],g[2]);Vec3 d=tgt.subtract(pos);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
  // blend in from the player's own view, and back out at the end
  double w=1;if(time<.6)w=smooth(time/.6);Vec3 from=startPos;float fy=startYaw,fp=startPitch;
  if(time>END-.8){w=smooth((END-time)/.8);}
  if(w<1){pos=from.lerp(pos,w);yaw=fy+Mth.wrapDegrees(yaw-fy)*(float)w;pitch=Mth.lerp((float)w,fp,pitch);fov=Mth.lerp(w,mc.options.fov().get(),fov);}
  fovNow=(float)fov;
  // the detached camera's eye height decays toward the Marker's 0; compensate so the shot lands exactly on `pos`
  int k2=Math.max(0,ticks-swapTick);float pt=mc.getTimer().getGameTimeDeltaPartialTick(false);float eh=Mth.lerp(pt,(float)(1.62*Math.pow(.5,k2)),(float)(1.62*Math.pow(.5,k2+1)));
  cam.setPos(pos.x,pos.y-eh,pos.z);cam.xo=cam.xOld=pos.x;cam.yo=cam.yOld=pos.y-eh;cam.zo=cam.zOld=pos.z;cam.setYRot(yaw);cam.setXRot(pitch);cam.yRotO=yaw;cam.xRotO=pitch;
  if(mc.getCameraEntity()!=cam)mc.setCameraEntity(cam);
  for(float c:CUTS)if(previousTime<c&&time>=c){cutFlag=c;EnvClient.pulse(.55f);}
  if(previousTime<3.75f&&time>=3.75f&&!stomp){stomp=true;Shake.punch(-9);}
  previousTime=time;
 }
 static float cutFlag;static boolean stomp;static boolean cutDone(float c){return cutFlag==c;}
 public static void fov(ViewportEvent.ComputeFov e){if(active)e.setFOV(fovNow);}
 public static void hand(RenderHandEvent e){if(active)e.setCanceled(true);}
 public static void layers(RenderGuiLayerEvent.Pre e){if(active)e.setCanceled(true);}
 public static void move(MovementInputUpdateEvent e){if(!active)return;var i=e.getInput();i.forwardImpulse=0;i.leftImpulse=0;i.jumping=false;i.shiftKeyDown=false;i.up=i.down=i.left=i.right=false;}
 public static void interact(InputEvent.InteractionKeyMappingTriggered e){if(active){e.setCanceled(true);e.setSwingHand(false);}}
 public static void overlay(RenderGuiEvent.Post e){if(!active)return;var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();var mc=Minecraft.getInstance();
  float in=(float)Mth.clamp(time/.5,0,1),out=(float)Mth.clamp((END-time)/.5,0,1);int bar=(int)(h*.115f*Math.min(in,out));g.fill(0,0,w,bar,0xff000000);g.fill(0,h-bar,w,h,0xff000000);
  float a=(float)(Mth.clamp((time-5.1)/.5,0,1)*Mth.clamp((7.9-time)/.4,0,1));if(a>.02f){int al=(int)(a*255)<<24;String t1="WORLD BOSS";g.pose().pushPose();g.pose().translate(w/2f,h*.80f,0);g.pose().scale(3f,3f,1);g.drawString(mc.font,t1,-mc.font.width(t1)/2,0,al|0xffd9a0,true);g.pose().popPose();
   var sub=Component.literal("世界BOSS · 熔岩霸王龙");g.drawString(mc.font,sub,(w-mc.font.width(sub))/2,(int)(h*.80f)+30,al|0xffb070,true);}}
}

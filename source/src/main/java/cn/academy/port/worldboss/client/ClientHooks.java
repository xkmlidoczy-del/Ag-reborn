package cn.academy.port.worldboss.client;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.FxPacket;
import cn.academy.port.worldboss.WorldBossContent;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
/** Glue between the synced boss state and client-only systems (blend, event-driven FX, anchors). */
public final class ClientHooks {
 static float[][][][] anchors;
 public static final int MOUTH=0,GROUND=1,LFOOT=2,RFOOT=3,CREST=4,TAIL=5,MOUTH_DIR=6;
 static void load(){if(anchors==null)anchors=new Gson().fromJson(new InputStreamReader(ClientHooks.class.getResourceAsStream("/assets/academy/worldboss/anchors.json")),float[][][][].class);}
 /** Original authored emergence: rise sixteen blocks from the production spawn pit over 3.3 seconds. */
 public static float rise(int clip,float frame){if(clip!=Clips.SPAWN)return 0;float t=frame/20f;float u=Mth.clamp(t/3.3f,0,1);return -16f*(1-u*u*(3-2*u));}
 /** World position of a locator at an animation frame (fractional frames are interpolated). */
 public static Vec3 anchor(WorldBossContent.Boss b,int clip,float frame,int idx){load();float[][][] a=anchors[clip];int f0=Mth.clamp((int)Math.floor(frame),0,a.length-1),f1=Math.min(f0+1,a.length-1);float t=Mth.clamp(frame,0,a.length-1)-f0;float[] p=a[f0][idx],q=a[f1][idx];
  return b.local(Mth.lerp(t,p[0],q[0]),Mth.lerp(t,p[1],q[1])+rise(clip,frame),Mth.lerp(t,p[2],q[2]));}
 public static Vec3 anchor(WorldBossContent.Boss b,int idx,float partial){return anchor(b,b.clip(),b.frame(partial),idx);}
 public static Vec3 mouthDir(WorldBossContent.Boss b,float partial){return anchor(b,MOUTH_DIR,partial).subtract(anchor(b,MOUTH,partial)).normalize();}

 public static void clipChanged(WorldBossContent.Boss b){var mc=Minecraft.getInstance();if(mc.level==null)return;
  if(b.seenClip>=0&&b.seenClip!=b.clip()){Clips.Clip c=Clips.get(b.seenClip);float f=(float)(mc.level.getGameTime()-b.seenStart);b.blendFrom=b.seenClip;b.blendFromFrame=c.loops()?f%(c.frames()-1):Math.min(f,c.frames()-1);b.blendStart=(int)mc.level.getGameTime();}
  b.clientEvent=-1;}
 public static void bossTick(WorldBossContent.Boss b,int age,Clips.Clip c){
  int clip=b.clip();b.seenClip=clip;b.seenStart=b.start();
  if(b.clientEvent<0)b.clientEvent=Math.max(-1,age-3);
  for(Clips.Event ev:c.events())if(ev.tick()>b.clientEvent&&ev.tick()<=age)ModernFx.bossEvent(b,ev.type(),clip,ev.tick());
  b.clientEvent=age;
  ModernFx.continuous(b,clip,age);
  // This is normal production behavior driven by the synced animation, independent of any QA recorder.
  if(b.intro()&&clip==Clips.SPAWN&&age<10&&!Cinematic.active&&Cinematic.shownFor!=b.getId()){
   var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.distanceTo(b)<600)Cinematic.start(b);
  }
 }
 public static void fx(FxPacket p){ModernFx.packet(p);}
}

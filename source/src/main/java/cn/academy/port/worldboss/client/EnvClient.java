package cn.academy.port.worldboss.client;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.WorldBossContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.*;
/** Client half of the environment abilities: storm fog, ember-red sky glow, lightning flashes, falling ash. */
public final class EnvClient {
 public static float storm,glow,flash;static float stormT,glowT;
 public static void pulse(float f){flash=Math.max(flash,f);}
 static void clear(){storm=glow=flash=stormT=glowT=0;}
 public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){storm=glow=flash=0;return;}
  WorldBossContent.Boss b=null;double bd=1e9;for(var en:mc.level.entitiesForRendering())if(en instanceof WorldBossContent.Boss x&&x.isAlive()&&!x.isRemoved()){double d=x.distanceTo(mc.player);if(d<bd){bd=d;b=x;}}
  stormT=b==null?0:1;glowT=0;if(b!=null){int c=b.clip();float age=(float)(mc.level.getGameTime()-b.start())/20;glowT=.3f;if(c==Clips.WORLDBREAKER)glowT=.3f+.7f*Mth.clamp(age/3,0,1)*(age<8?1:1-(age-8)/2);else if(c==Clips.NOVA||c==Clips.TWIN)glowT=.6f;else if(c==Clips.BREATH||c==Clips.LANCE)glowT=.55f;else if(c==Clips.SPAWN)glowT=.5f+.4f*Mth.clamp(age/4,0,1);}
  storm+=(stormT-storm)*.03f;glow+=(glowT-glow)*.04f;flash*=.66f;
  if(storm>.05f&&mc.level.getGameTime()%3==0){var r=mc.level.random;var cp=mc.gameRenderer.getMainCamera().getPosition();for(int i=0;i<2;i++){double ang=r.nextDouble()*6.283,d=28+r.nextDouble()*45;mc.level.addParticle(ParticleTypes.ASH,cp.x+Math.cos(ang)*d,cp.y+r.nextDouble()*20-4,cp.z+Math.sin(ang)*d,0,-.03,0);}}}
 public static void fogColor(ViewportEvent.ComputeFogColor e){float s=storm*.85f,g=glow;float r=Mth.lerp(s,e.getRed(),.17f+.34f*g),gr=Mth.lerp(s,e.getGreen(),.17f+.06f*g),bl=Mth.lerp(s,e.getBlue(),.24f-.06f*g);
  e.setRed(Math.min(1,r+flash*.45f));e.setGreen(Math.min(1,gr+flash*.5f));e.setBlue(Math.min(1,bl+flash*.6f));}
 public static void fog(ViewportEvent.RenderFog e){if(storm<.02f||e.getType()!=net.minecraft.world.level.material.FogType.NONE)return;float far=Mth.lerp(storm*(.55f+.3f*glow),e.getFarPlaneDistance(),Math.min(e.getFarPlaneDistance(),170));e.setFarPlaneDistance(far);e.setNearPlaneDistance(Mth.lerp(storm,e.getNearPlaneDistance(),0));e.setCanceled(true);}
 public static void overlay(RenderGuiEvent.Post e){var g=e.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
  if(flash>.02f)g.fill(0,0,w,h,((int)(Math.min(1,flash)*120)<<24)|0xdde6ff);
  if(glow>.05f){int a=(int)(glow*34);g.fillGradient(0,0,w,h/2,(a<<24)|0x300500,0);g.fillGradient(0,h/2,w,h,0,(a<<24)|0xff3a00);}}
}

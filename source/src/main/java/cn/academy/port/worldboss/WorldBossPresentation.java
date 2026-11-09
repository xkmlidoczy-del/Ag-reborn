package cn.academy.port.worldboss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original V9 presentation, independent of the vanilla-scale damage calculation. */
public final class WorldBossPresentation {
 static void fx(ServerLevel l,String type,double x,double y,double z,float a,float b,float c,int i,int j){
  PacketDistributor.sendToPlayersNear(l,null,x,y,z,420,new FxPacket(type,(float)x,(float)y,(float)z,a,b,c,i,j));
 }
 static void shake(ServerLevel l,double x,double y,double z,float amp,int ticks,float radius){fx(l,"shake",x,y,z,amp,radius,0,ticks,0);}
 static void sound(ServerLevel l,String n,double x,double y,double z,float vol,float pitch){var h=WorldBossContent.SND.get(n);if(h!=null)l.playSound(null,x,y,z,h.get(),SoundSource.HOSTILE,vol,pitch);}
 static void crack(ServerLevel l,double x,double y,double z,int radius){fx(l,"crack",x,y,z,radius,0,0,60,0);}
 public static void event(WorldBossContent.Boss b,String type,int clip,int age){
  ServerLevel l=(ServerLevel)b.level();double bx=b.getX(),by=b.getY(),bz=b.getZ();
  switch(type){
   case "storm"->{sound(l,"storm",bx,by+10,bz,12,1);sound(l,"rumble",bx,by,bz,10,.9f);fx(l,"env",bx,by,bz,1,0,0,0,0);}
   case "lightning"->{}
   case "step_left","step_right"->{Vec3 f=b.local(type.endsWith("left")?5.1:-5.1,0,2.5);fx(l,"ring",f.x,f.y+.2,f.z,5,.45f,0,16,0);shake(l,f.x,f.y,f.z,0.18f,8,60);sound(l,"stomp",f.x,f.y,f.z,5,1.1f);}
   case "bite"->{Vec3 m=WorldBossCombat.mouth(b).add(WorldBossCombat.mouthDirection(b).scale(1.5));sound(l,"bite",m.x,m.y,m.z,9,1);sound(l,"impact",m.x,m.y,m.z,7,.9f);fx(l,"burst",m.x,m.y,m.z,1.1f,0,0,26,0);shake(l,m.x,m.y,m.z,0.38f,10,80);}
   case "ram"->{Vec3 m=b.local(0,8,-23);sound(l,"impact",m.x,m.y,m.z,12,.8f);fx(l,"ring",m.x,by+.2,m.z,16,.8f,0,26,0);fx(l,"burst",m.x,by+1,m.z,1.5f,0,0,40,1);shake(l,m.x,by,m.z,0.75f,16,120);crack(l,m.x,by,m.z,9);}
   case "left_slash"->{Vec3 m=b.local(4.5,9,-18);sound(l,"slash",m.x,m.y,m.z,9,1);fx(l,"slash",m.x,m.y,m.z,1,0,0,12,0);shake(l,m.x,m.y,m.z,0.30f,8,70);}
   case "right_slash"->{Vec3 m=b.local(-4.5,9,-18);sound(l,"slash",m.x,m.y,m.z,9,.9f);fx(l,"slash",m.x,m.y,m.z,-1,0,0,12,0);shake(l,m.x,m.y,m.z,0.30f,8,70);}
   case "cross_slash"->{Vec3 m=b.local(0,12,-22);sound(l,"slash",m.x,m.y,m.z,11,.8f);fx(l,"slash",m.x,m.y,m.z,1,0,0,14,1);shake(l,m.x,m.y,m.z,0.45f,10,80);}
   case "tail_sweep"->{Vec3 m=b.local(0,5,26);sound(l,"tail",m.x,m.y,m.z,11,1);fx(l,"ring",m.x,by+.2,m.z,18,.6f,0,24,0);shake(l,m.x,by,m.z,0.52f,14,100);}
   case "tail_quake"->{Vec3 m=b.local(0,0,28);sound(l,"impact",m.x,m.y,m.z,13,.7f);sound(l,"crack",m.x,m.y,m.z,11,1);fx(l,"ring",m.x,by+.2,m.z,22,.9f,0,30,0);shake(l,m.x,by,m.z,0.83f,20,140);crack(l,m.x,by,m.z,14);}
   case "stomp"->{Vec3 f=b.local(5.1,0,0);sound(l,"stomp",f.x,f.y,f.z,14,.8f);sound(l,"crack",f.x,f.y,f.z,10,.9f);fx(l,"ring",f.x,by+.2,f.z,26,1f,0,32,0);shake(l,f.x,by,f.z,0.90f,22,160);crack(l,f.x,by,f.z,18);}
   case "quake"->{sound(l,"stomp",bx,by,bz,15,.7f);sound(l,"rumble",bx,by,bz,13,1);fx(l,"ring",bx,by+.2,bz,30,1f,0,36,0);fx(l,"ring",bx,by+.3,bz,20,.8f,0,28,1);shake(l,bx,by,bz,1.00f,30,200);crack(l,bx,by,bz,20);}
   case "eruption"->{sound(l,"crack",bx,by,bz,15,.7f);sound(l,"lava",bx,by,bz,12,1);fx(l,"ring",bx,by+.2,bz,34,1.2f,0,40,0);shake(l,bx,by,bz,1.00f,34,220);crack(l,bx,by,bz,24);}
   case "nova"->{Vec3 c=b.local(0,26,2);sound(l,"nova",c.x,c.y,c.z,15,1);fx(l,"ring",bx,by+.4,bz,46,1.3f,0,44,2);fx(l,"nova",c.x,c.y,c.z,40,0,0,50,0);shake(l,bx,by,bz,1.00f,30,260);crack(l,bx,by,bz,26);}
   case "cataclysm"->{sound(l,"thunder",bx,by,bz,16,.8f);sound(l,"stomp",bx,by,bz,16,.6f);sound(l,"nova",bx,by,bz,16,.8f);fx(l,"ring",bx,by+.4,bz,64,1.6f,0,56,0);fx(l,"ring",bx,by+.5,bz,42,1.2f,0,48,2);fx(l,"env",bx,by,bz,2,0,0,0,0);shake(l,bx,by,bz,1f,48,320);crack(l,bx,by,bz,30);}
   case "roar"->{Vec3 m=b.local(0,20,-21);sound(l,"growl",m.x,m.y,m.z,16,1);sound(l,"thunder",bx,by,bz,10,1);fx(l,"roar",m.x,m.y,m.z,(float)b.forward().x,(float)b.forward().z,0,34,0);shake(l,m.x,m.y,m.z,0.75f,30,200);
     }
   case "charge_orb","crest_charge"->{Vec3 m=type.equals("crest_charge")?b.local(0,26,2):b.local(0,19,-21);sound(l,"charge",m.x,m.y,m.z,11,1);}
   case "charge"->{sound(l,"whoosh",bx,by+6,bz,12,.7f);sound(l,"rumble",bx,by,bz,10,1.2f);}
   case "breath"->{Vec3 m=b.local(0,19,-21);sound(l,"breath",m.x,m.y,m.z,13,1);}
   case "beam"->{Vec3 m=b.local(0,19,-21);sound(l,"beam",m.x,m.y,m.z,14,1);shake(l,m.x,m.y,m.z,0.45f,60,200);}
   case "meteor"->{} // Native per-cast meteor scheduling owns its matching visuals.
   case "pit_rise"->{sound(l,"rise",bx,by-6,bz,13,1);sound(l,"lava",bx,by,bz,11,.9f);shake(l,bx,by,bz,0.27f,60,140);}
   case "pit_crack"->{sound(l,"crack",bx,by,bz,13,.8f);sound(l,"rumble",bx,by,bz,11,1.1f);fx(l,"burst",bx,by-1,bz,2.2f,0,0,40,2);shake(l,bx,by,bz,0.60f,30,160);}
   case "ambient"->{}
   default->{}
  }}
 private WorldBossPresentation(){}
}

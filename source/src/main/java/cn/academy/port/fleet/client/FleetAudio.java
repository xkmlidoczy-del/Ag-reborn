package cn.academy.port.fleet.client;

import com.google.gson.JsonObject;
import cn.academy.port.fleet.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Authority packets drive gun transients; continuous vehicle sounds read the synchronized simulation. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class FleetAudio {
 private record Pending(LabNetwork.Audio packet,long received){}
 private record Voice(int entity,long action,SoundInstance sound){}
 private static final List<Pending> pending=new ArrayList<>();
 private static final List<Voice> cancellable=new ArrayList<>();
 private static final Map<Integer,Car> cars=new HashMap<>();
 private static Object world;private static int held=-1,slot=-1;private static boolean aimed;
 private static Minecraft mc(){return Minecraft.getInstance();}
 public static void receive(LabNetwork.Audio p){
  if(mc().level==null||p.variant()<0||p.variant()>5)return;
  if(p.phase().equals("cancel")){
   pending.removeIf(q->q.packet.entity()==p.entity()&&q.packet.action()==p.action());
   cancellable.removeIf(v->{if(v.entity()==p.entity()&&v.action()==p.action()){mc().getSoundManager().stop(v.sound());return true;}return false;});
  }
  pending.add(new Pending(p,mc().level.getGameTime()));
 }
 private static boolean ready(Pending q,long now){
  var p=q.packet();if(p.entity()!=mc().player.getId()||now-q.received()>=2)return true;
  ItemStack stack=mc().player.getMainHandItem();if(!(stack.getItem() instanceof GunItem gun)||gun.variant!=p.variant())return true;
  if(p.phase().equals("fire"))return GunItem.lastFire(stack)>=p.tick();
  if(p.phase().equals("mag_out"))return !GunItem.hasMagazine(stack);
  if(p.phase().equals("mag_in"))return GunItem.hasMagazine(stack);
  return now>=p.tick();
 }
 private static float gain(String phase){return switch(phase){case "latch"->.32f;case "mag_out"->.55f;case "guide"->.20f;case "mag_in"->.68f;case "charge","activate"->.49f;case "settle","cancel"->.19f;case "dry"->.34f;case "horn"->.64f;default->.16f;};}
 private static SoundInstance one(String key,SoundSource source,Vec3 point,float volume,float pitch,long seed){
  var sound=new SimpleSoundInstance(AudioDefs.get(key),source,volume,pitch,RandomSource.create(seed),point.x,point.y,point.z);
  mc().getSoundManager().play(sound);return sound;
 }
 private static void play(LabNetwork.Audio p){
  var entity=mc().level.getEntity(p.entity());boolean vehicle=entity instanceof LabVehicle;
  Vec3 point=new Vec3(p.x(),p.y(),p.z());float pitch=1+(float)((new Random(p.seed()).nextDouble()-.5)*.024);
  if(p.phase().equals("horn"))one(AudioDefs.vehicle(p.variant(),"horn"),SoundSource.BLOCKS,point,gain("horn"),pitch,p.seed());
  else if(p.phase().equals("fire")){
   double distance=mc().gameRenderer.getMainCamera().getPosition().distanceTo(point),mix=Math.clamp((distance-8)/20,0,1);
   float close=(float)Math.cos(mix*Math.PI/2),far=(float)Math.sin(mix*Math.PI/2);
   if(close>.005)one(AudioDefs.gun(p.variant(),"fire_near"),SoundSource.PLAYERS,point,.94f*close,pitch,p.seed());
   if(far>.005)one(AudioDefs.gun(p.variant(),"fire_far"),SoundSource.PLAYERS,point,far,pitch,p.seed());
  }else if(!vehicle){
   var sound=one(AudioDefs.gun(p.variant(),p.phase()),SoundSource.PLAYERS,point,gain(p.phase()),pitch,p.seed());
   if(p.phase().equals("guide")||p.phase().equals("charge"))cancellable.add(new Voice(p.entity(),p.action(),sound));
  }
  if(QaAvCapture.ENABLED){var j=new JsonObject();j.addProperty("entity",p.entity());j.addProperty("variant",p.variant());j.addProperty("phase",p.phase());j.addProperty("authority_tick",p.tick());j.addProperty("client_tick",mc().level.getGameTime());j.addProperty("action",p.action());
   if(p.entity()==mc().player.getId()){var s=mc().player.getMainHandItem();j.addProperty("loaded",GunItem.ammo(s));j.addProperty("has_magazine",GunItem.hasMagazine(s));j.addProperty("last_fire",GunItem.lastFire(s));}
   QaAvCapture.note("phase_play",j);
  }
 }
 public static void reset(){for(Car c:cars.values())c.stop();cars.clear();pending.clear();for(var v:cancellable)mc().getSoundManager().stop(v.sound());cancellable.clear();held=slot=-1;aimed=false;}
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void tick(ClientTickEvent.Post e){
  if(mc().level==null||mc().player==null){if(world!=null)reset();world=null;return;}
  if(world!=mc().level){reset();world=mc().level;}
  long now=mc().level.getGameTime();for(var it=pending.iterator();it.hasNext();){var q=it.next();if(ready(q,now)){play(q.packet());it.remove();}}
  cancellable.removeIf(v->!mc().getSoundManager().isActive(v.sound()));
  for(var entity:mc().level.entitiesForRendering())if(entity instanceof LabVehicle v&&v.distanceToSqr(mc().player)<10000)cars.computeIfAbsent(v.getId(),n->new Car(v)).update(now);
  cars.entrySet().removeIf(e2->{Car c=e2.getValue();if(c.vehicle.isRemoved()||c.vehicle.level()!=mc().level||c.vehicle.distanceToSqr(mc().player)>11025){c.stop();return true;}return false;});
  var stack=mc().player.getMainHandItem();int variant=stack.getItem() instanceof GunItem gun&&!(mc().player.getVehicle() instanceof LabVehicle)?gun.variant:-1;
  boolean aim=LabClient.aimAmount()>.08;
  if(variant>=0&&mc().screen==null){Vec3 at=mc().player.getEyePosition();
   if(held!=variant||slot!=mc().player.getInventory().selected)one(AudioDefs.gun(variant,"equip"),SoundSource.PLAYERS,at,.13f,1,now);
   else if(aim!=aimed)one(AudioDefs.gun(variant,aim?"ads_in":"ads_out"),SoundSource.PLAYERS,at,.13f,1,now);
  }held=variant;slot=mc().player.getInventory().selected;aimed=aim;
 }
 private static final class Loop extends AbstractTickableSoundInstance {
  private final LabVehicle vehicle;private final boolean cabin;private float target,pitchTarget=1;
  Loop(LabVehicle v,String phase,boolean inside){super(AudioDefs.get(AudioDefs.vehicle(v.variant(),phase)),SoundSource.BLOCKS,RandomSource.create(v.getId()*193L+phase.hashCode()));vehicle=v;cabin=inside;looping=true;delay=0;relative=inside;attenuation=inside?SoundInstance.Attenuation.NONE:SoundInstance.Attenuation.LINEAR;volume=.0001f;}
  void target(float v,float p){target=Math.max(0,v);pitchTarget=Math.clamp(p,.5f,1.9f);}
  @Override public boolean canStartSilent(){return true;}
  @Override public void tick(){if(vehicle.isRemoved()){stop();return;}volume+=(target-volume)*.30f;pitch+=(pitchTarget-pitch)*.18f;if(cabin){x=y=z=0;}else{Vec3 at=vehicle.localToWorld(new Vec3(0,.55,-.45));x=at.x;y=at.y;z=at.z;}}
  void end(){stop();mc().getSoundManager().stop(this);}
 }
 private static final class Car {
  final LabVehicle vehicle;final Map<String,Loop> loops=new HashMap<>();boolean powered,initialized,doorTarget,doorDone=true;float lastSpeed;long brakeTick=-100,bumpTick=-100;SoundInstance doorVoice;
  Car(LabVehicle v){vehicle=v;lastSpeed=v.speed();doorTarget=v.doorOpen();}
  void loop(String phase,float gain,float pitch,boolean cabin){var voice=loops.get(phase);if(voice==null&&gain>.0005f){voice=new Loop(vehicle,phase,cabin);voice.target(gain,pitch);loops.put(phase,voice);mc().getSoundManager().play(voice);}else if(voice!=null){voice.target(gain,pitch);}}
  void hit(String phase,float volume){one(AudioDefs.vehicle(vehicle.variant(),phase),SoundSource.BLOCKS,vehicle.localToWorld(new Vec3(0,1,0)),volume,1,mc().level.getGameTime()+vehicle.getId());
   if(QaAvCapture.ENABLED){var j=new JsonObject();j.addProperty("variant",vehicle.variant());j.addProperty("phase",phase);j.addProperty("speed",vehicle.speed());j.addProperty("door_amount",vehicle.doorAmount(1));j.addProperty("tick",mc().level.getGameTime());QaAvCapture.note("vehicle_phase",j);}}
  void update(long now){
   boolean power=vehicle.getFirstPassenger()!=null;float signed=vehicle.speed(),speed=Math.abs(signed),normalized=Math.clamp(speed/.32f,0,1),load=Math.abs(vehicle.driveLoad());
   boolean cabin=mc().player.getVehicle()==vehicle&&mc().options.getCameraType().isFirstPerson();float outside=cabin?.08f:1,inside=cabin?1:0;
   if(power&&!powered)hit("start",.52f);if(!power&&powered)hit("stop",.43f);powered=power;
   float drive=power?(.07f+.38f*normalized+.08f*load):0,idle=power?.25f*(1-normalized*.72f):0;
   float pitch=.70f+.83f*normalized+.08f*load,rolling=.28f*normalized,wind=.12f*normalized*normalized;
   loop("idle",idle*outside,1, false);loop("drive",drive*outside,pitch,false);loop("roll",rolling*outside,.72f+.65f*normalized,false);loop("wind",wind*(cabin?.18f:1),.85f+.25f*normalized,false);
   loop("idle_cabin",idle*inside*.88f,1,true);loop("drive_cabin",drive*inside*.67f,pitch,true);loop("roll_cabin",rolling*inside*.65f,.72f+.65f*normalized,true);
   loop("reverse",signed<-.025f?.18f:0,1,false);
   if(vehicle.variant()==3||vehicle.variant()==4)loop("siren",vehicle.siren()?(cabin?.15f:.56f):0,1,false);
   if(initialized&&Math.abs(lastSpeed)>.025f&&speed<=.025f&&now-brakeTick>15){hit("brake",vehicle.variant()==1?.48f:.34f);brakeTick=now;}
   if(initialized&&Math.abs(lastSpeed)-speed>.075f&&Math.signum(vehicle.driveLoad())==Math.signum(lastSpeed)&&now-bumpTick>15){hit("bump",.48f);bumpTick=now;}
   if(!vehicle.config().doorSides.isEmpty()){
    if(vehicle.doorOpen()!=doorTarget){doorTarget=vehicle.doorOpen();doorDone=false;if(doorVoice!=null)mc().getSoundManager().stop(doorVoice);doorVoice=one(AudioDefs.vehicle(vehicle.variant(),doorTarget?"door_open":"door_close"),SoundSource.BLOCKS,vehicle.localToWorld(new Vec3(-vehicle.config().width/2,1,.1)),.38f,1,now);}
    float amount=vehicle.doorAmount(1);if(!doorDone&&(doorTarget?amount>=.95f:amount<=.05f)){hit(doorTarget?"door_open_end":"door_close_end",doorTarget?.28f:.64f);doorDone=true;}
   }
   if(QaAvCapture.ENABLED&&now%5==0){var j=new JsonObject();j.addProperty("variant",vehicle.variant());j.addProperty("entity",vehicle.getId());j.addProperty("tick",now);j.addProperty("speed",signed);j.addProperty("load",vehicle.driveLoad());j.addProperty("cabin",cabin);j.addProperty("motor_pitch",pitch);j.addProperty("door_amount",vehicle.doorAmount(1));QaAvCapture.note("vehicle_state",j);}
   initialized=true;lastSpeed=signed;
   if(!power&&speed<.001f&&!vehicle.siren()){
    for(var it=loops.entrySet().iterator();it.hasNext();){var l=it.next().getValue();l.end();it.remove();}
   }
  }
  void stop(){for(var l:loops.values())l.end();loops.clear();if(doorVoice!=null)mc().getSoundManager().stop(doorVoice);}
 }
}

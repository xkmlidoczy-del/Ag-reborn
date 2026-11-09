package cn.academy.port.fleet;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Sound identities are shared; synthesis, playback and ammunition stay separate. */
public final class AudioDefs {
 private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new LinkedHashMap<>();
 public static void bootstrap(){
  if(!EVENTS.isEmpty())return;
  try(var in=AudioDefs.class.getResourceAsStream("/assets/academy/sounds.json")){
   var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
   for(String key:json.keySet())if(key.matches("[gv]0[1-6]_.*"))EVENTS.put(key,InteractionLab.SOUNDS.register(key,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",key))));
  }catch(Exception e){throw new IllegalStateException("Missing fleet sound definitions",e);}
 }
 public static SoundEvent get(String key){var holder=EVENTS.get(key);if(holder==null)throw new IllegalArgumentException("Unknown fleet sound "+key);return holder.get();}
 public static String gun(int variant,String phase){return "g0"+(variant+1)+"_"+phase;}
 public static String vehicle(int variant,String phase){return "v0"+(variant+1)+"_"+phase;}
 public static void emit(Entity actor,int variant,String phase,long action){
  if(actor.level().isClientSide)return;
  // Send the visible stack change first on the same ordered connection. Waiting for the
  // normal end-of-tick inventory broadcast can otherwise put a contact sound two ticks late.
  if(actor instanceof net.minecraft.server.level.ServerPlayer p && (phase.equals("fire")||phase.equals("mag_out")||phase.equals("mag_in")))p.containerMenu.broadcastChanges();
  var packet=new LabNetwork.Audio(actor.getId(),variant,phase,actor.getX(),actor.getY()+1.1,actor.getZ(),actor.level().getGameTime(),action,actor.getRandom().nextLong());
  PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor,packet);
 }
}

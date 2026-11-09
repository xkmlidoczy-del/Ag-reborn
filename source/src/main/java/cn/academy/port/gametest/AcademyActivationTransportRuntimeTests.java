/* Actual authenticated boolean ingress and official embedded packet transport. Compile only in worker. GPLv3. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.function.Consumer;
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyActivationTransportRuntimeTests {
 private static final String TEMPLATE="runtime_empty",BATCH="academy_activation_transport";
 /** Reads the owned fixture's actual embedded outbound packets; never intercepts a real user's connection. */
 private static List<CompoundTag> payloads(AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors){
  try{var field=AcademyWirelessDeviceRuntimeTests.NativeMenuActors.class.getDeclaredField("channels");field.setAccessible(true);var channels=(List<?>)field.get(actors);var result=new ArrayList<CompoundTag>();for(Object value:channels){var channel=(EmbeddedChannel)value;channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AcademyNetwork.ClientData data)result.add(data.data().copy());io.netty.util.ReferenceCountUtil.release(packet);}}return result;}catch(ReflectiveOperationException e){throw new AssertionError(e);}
 }
 private static List<CompoundTag> observers(AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors){return payloads(actors).stream().filter(tag->tag.getString("kind").equals("activation_event")).toList();}
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void desired_boolean_ingress_is_canonical_authenticated_busy_safe_and_same_state_observer_silent(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","true")),"new explicit wire state cannot bypass absent-category guard");h.assertFalse(s.activated,"rejected request leaves raw flag unchanged");s.changeCategoryClassic("electromaster");s.learn("arc_gen");s.overloadFine=false;s.interfering=true;var exact=AbilityStorage.encode(s);
   for(String value:List.of("","TRUE","False","1"," true","false "))h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state",value)),"noncanonical desired boolean rejected: "+value);h.assertTrue(AbilityStorage.encode(s).equals(exact),"malformed explicit state leaves entire live snapshot unchanged");observers(actors);
   h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","true")),"source activation while overload-locked/interfered remains same as established toggle");h.assertTrue(s.activated&&!s.canUse("arc_gen"),"unchanged ability-use gates still block overload/interference");var packets=observers(actors);h.assertTrue(packets.size()==1&&Boolean.TRUE.equals(AcademyNetwork.activationEventState(packets.getFirst(),p.getId())),"actual official embedded packet carries explicit true observer for this player");
   h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","true")),"same desired state is valid authenticated request");h.assertTrue(observers(actors).isEmpty()&&s.activated,"same state sends no observer and cannot blindly toggle");
   Consumer<SkillPerformEvent> busy=e->{if(e.player==p){h.assertFalse(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","false")),"consume-busy recursion remains denied at original authenticated entry");h.assertTrue(s.activated,"denied recursive wire state cannot change activation");}};NeoForge.EVENT_BUS.addListener(SkillPerformEvent.class,busy);try{h.assertTrue(s.consumeSkill("arc_gen",0,0,false),"source consumption call supplies actual busy event boundary");h.assertTrue(observers(actors).isEmpty(),"busy rejection sends no activation observer");}finally{NeoForge.EVENT_BUS.unregister(busy);}
   h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","false")),"explicit false uses established deactivation path");h.assertFalse(s.activated,"explicit false deactivates");packets=observers(actors);h.assertTrue(packets.size()==1&&Boolean.FALSE.equals(AcademyNetwork.activationEventState(packets.getFirst(),p.getId())),"real embedded deactivation observer payload");h.succeed();
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void native_nested_self_listener_sends_inner_and_outer_live_false_after_priority_dispatch(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("teleporter");var phases=new ArrayList<String>();observers(actors);
   Consumer<AbilityActivateEvent> high=e->{if(e.getEntity()==p){phases.add("activate-high:"+s.activated);s.setActivateState(false);}};
   Consumer<AbilityActivateEvent> low=e->{if(e.getEntity()==p)phases.add("activate-low:"+s.activated);};Consumer<AbilityDeactivateEvent> deactive=e->{if(e.getEntity()==p)phases.add("deactivate:"+s.activated);};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityActivateEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,AbilityActivateEvent.class,low);NeoForge.EVENT_BUS.addListener(AbilityDeactivateEvent.class,deactive);
   try{h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","true")),"actual authenticated request begins source nested activation");h.assertTrue(phases.equals(List.of("activate-high:true","deactivate:false","activate-low:false"))&&!s.activated,"native synchronous self observers expose live post-nested state");var all=payloads(actors);h.assertTrue(all.stream().map(tag->tag.getString("kind")).toList().equals(List.of("activation_event","activation_event","state","state")),"actual embedded channel sends both post-self observers before the existing two unified request snapshots");var packets=all.stream().filter(tag->tag.getString("kind").equals("activation_event")).toList();h.assertTrue(packets.size()==2&&packets.stream().allMatch(tag->Boolean.FALSE.equals(AcademyNetwork.activationEventState(tag,p.getId()))),"actual embedded remote inner and outer messages both read false after their self listeners");h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("activate_state","false")),"already false authoritative request valid");h.assertTrue(observers(actors).isEmpty()&&phases.size()==3,"equal request emits no self/remote observer");h.succeed();}finally{for(Object listener:List.of(high,low,deactive))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
}

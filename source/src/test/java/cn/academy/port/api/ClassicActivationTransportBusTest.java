/* Exact native event poster/observer tags on official global NeoForge bus; no game bootstrap. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.*;
import cn.academy.port.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.function.Consumer;
public final class ClassicActivationTransportBusTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  NeoForge.EVENT_BUS.start();var server=new AbilityProgress();server.changeCategoryClassic("teleporter");var client=new AbilityProgress();client.changeCategoryClassic("teleporter");var phases=new ArrayList<String>();var remote=new ArrayList<CompoundTag>();int[] events={0,0};
  Consumer<AbilityActivateEvent> activeHigh=e->{if(e.state==server){phases.add("server-activate-high:"+e.state.activated);server.setActivateState(false);}else if(e.state==client){events[0]++;phases.add("client-activate:"+e.state.activated);}};
  Consumer<AbilityActivateEvent> activeLow=e->{if(e.state==server)phases.add("server-activate-low:"+e.state.activated);};
  Consumer<AbilityDeactivateEvent> inactiveHigh=e->{if(e.state==server)phases.add("server-deactivate:"+e.state.activated);else if(e.state==client){events[1]++;phases.add("client-deactivate:"+e.state.activated);}};
  NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityActivateEvent.class,activeHigh);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,AbilityActivateEvent.class,activeLow);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityDeactivateEvent.class,inactiveHigh);
  try{
   server.bindActivationEvents(value->ActivationTransport.dispatch(server,value,active->AbilityActivationLifecycle.post((Player)null,server,active),active->{phases.add("remote:"+active);remote.add(AcademyNetwork.encodeActivationEvent(71,active));}));
   ActivationTransport.predict(client,true,value->{check(value&&client.activated&&events[0]==0&&events[1]==0,"raw prediction precedes explicit send with no early local event");});server.setActivateState(true);
   check(phases.equals(List.of("server-activate-high:true","server-deactivate:false","remote:false","server-activate-low:false","remote:false")),"actual common poster and global priorities run self/nested observer before remote reads live flag");check(remote.size()==2&&remote.stream().allMatch(t->Boolean.FALSE.equals(AcademyNetwork.activationEventState(t,71))),"both inner/outer observer tags carry current false");
   phases.clear();for(var tag:remote){Boolean active=AcademyNetwork.activationEventState(tag,71);AbilityActivationLifecycle.post((Player)null,client,active);}check(phases.equals(List.of("client-deactivate:true","client-deactivate:true"))&&events[0]==0&&events[1]==2&&client.activated,"received duplicate false events post each observer without overwriting predicted true");
   AbilityActivationLifecycle.post((Player)null,client,true);check(events[0]==1&&events[1]==2&&client.activated,"common Player constructor/posts support actual client observer type");int messages=remote.size();phases.clear();server.setActivateState(false);check(remote.size()==messages&&phases.isEmpty(),"same desired server state emits no self or remote observer");
   var snapshot=AbilityStorage.encode(server);var replaced=AbilityStorage.decode(snapshot);check(!replaced.activated&&events[0]==1&&events[1]==2,"authoritative snapshot restore can correct prediction silently without fabricated events");
   var valid=AcademyNetwork.encodeActivationEvent(71,true);check(valid.getAllKeys().equals(Set.of("kind","entity","active"))&&valid.contains("active",Tag.TAG_BYTE)&&valid.contains("entity",Tag.TAG_INT),"observer payload contains only kind/player identity/explicit boolean, no ability ledger");check(AcademyNetwork.activationEventState(valid,72)==null&&AcademyNetwork.activationEventState(null,71)==null,"wrong-player and null observer tags do not dispatch");
   for(String field:List.of("kind","entity","active")){var malformed=valid.copy();malformed.remove(field);check(AcademyNetwork.activationEventState(malformed,71)==null,"missing observer field rejects: "+field);}
   var malformed=valid.copy();malformed.putDouble("active",1);check(AcademyNetwork.activationEventState(malformed,71)==null,"nonboolean observer field cannot trigger default deactivation");malformed=valid.copy();malformed.putLong("entity",71);check(AcademyNetwork.activationEventState(malformed,71)==null,"wrong native identity tag type rejects");malformed=valid.copy();malformed.putString("kind","state");check(AcademyNetwork.activationEventState(malformed,71)==null,"snapshot does not become an observer message");
   var sent=new ArrayList<Boolean>();client.bindActivationEvents(value->{throw new AssertionError("prediction cannot use authoritative setter events");});ActivationTransport.predict(client,false,sent::add);ActivationTransport.predict(client,true,sent::add);ActivationTransport.predict(client,false,sent::add);check(sent.equals(List.of(false,true,false))&&!client.activated,"rapid prediction sends exact desired sequence and retains latest flag");
   for(String value:List.of("","TRUE","False","1"," true","false "))check(ActivationTransport.requested(value)==null,"noncanonical desired boolean rejects: "+value);
   System.out.println("PASS "+checks+" exact native global-bus/transport/live-remote/observer-tag/quiet-snapshot comparisons; no game bootstrap");
  }finally{for(Object listener:List.of(activeHigh,activeLow,inactiveHigh))NeoForge.EVENT_BUS.unregister(listener);}
 }
}

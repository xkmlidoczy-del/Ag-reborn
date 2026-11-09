/* Executes the production AcademyClient state/activation seam with real native codec/latches/session guard. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.*;
import cn.academy.port.api.*;
import cn.academy.port.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.function.Consumer;
public final class ClassicActivationClientStateRegressionTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static String oldSignature(AbilityProgress s){return s.category+":"+s.activated+":"+s.presets.current()+":"+s.presets.revision();}
 static final class Fixture {
  final ClassicActivationClientState adapter=new ClassicActivationClientState();final ClassicInputLatch key=new ClassicInputLatch();final ClassicActivationKey activation=new ClassicActivationKey();final ClientSessionGuard session=new ClientSessionGuard();
  final AbilityProgress server=new AbilityProgress();final AbilityProgress[] local={new AbilityProgress()};final List<Boolean> sent=new ArrayList<>();boolean physicalKey;int resets,aborts,overrides;Object world,player;
  Fixture(){server.changeCategoryClassic("electromaster");server.learn("arc_gen");server.presets.edit(0,0,"arc_gen",id->true);adapter.resetSession(local[0]);accept(false);resets=0;aborts=0;}
  CompoundTag packet(boolean active){server.activated=active;var tag=AbilityStorage.encode(server);tag.putBoolean("interfering",server.interfering);tag.putString("kind","state");return tag;}
  void accept(boolean active){var tag=packet(active);adapter.receive(local[0],tag,next->local[0]=next,()->{check(local[0].activated==active,"actual adapter installs authoritative snapshot before delegate reset callback");resets++;if(key.active())aborts++;key.replaceSession(physicalKey);});}
  void defaultV(){adapter.activate(local[0],false,()->{throw new AssertionError("unexpected override");},value->{check(local[0].activated==value,"actual default adapter predicts before its explicit send");sent.add(value);});}
  ClassicInputLatch.Transition key(boolean down){physicalKey=down;return key.update(down,local[0].canUse("arc_gen"));}
  void overrideV(){adapter.activate(local[0],true,()->{overrides++;key.abort();},sent::add);}
  void replace(Object nextWorld,Object nextPlayer,boolean vDown,long now){if(session.synchronize(nextWorld,nextPlayer)){world=nextWorld;player=nextPlayer;local[0]=new AbilityProgress();adapter.resetSession(local[0]);key.replaceSession(physicalKey);activation.replaceSession(vDown,now);}}
 }
 static void matchingAcknowledgement(){
  var f=new Fixture();f.defaultV();var predicted=f.local[0];var ack=f.packet(true);check(oldSignature(predicted).equals(oldSignature(AbilityStorage.decode(ack))),"frozen M30 counterexample: predicted flag makes old receive signature equal to acknowledgement");check(f.key(true).press()&&f.key.active(),"a new K press can become active between source prediction and acknowledgement");
  f.accept(true);check(f.local[0]!=predicted&&f.resets==1&&f.aborts==1&&!f.key.active(),"production adapter restores the acknowledgement reset that the earlier M30 suppressed");check(!f.key(true).press()&&!f.key.active(),"K held across acknowledgement cannot become a fresh cast after reset");f.accept(true);check(f.resets==1,"repeated authoritative acknowledgement remains a no-op");f.key(false);check(f.key(true).press(),"release then new K press still works");
 }
 static void rejectedPrediction(){
  var f=new Fixture();f.defaultV();check(f.key(true).press(),"predicted activation can briefly admit a logical delegate before rejection");f.accept(false);check(!f.local[0].activated&&f.resets==1&&f.aborts==1&&!f.key.active(),"rejected prediction still clears the local delegate even when authoritative state never changed");f.accept(false);check(f.resets==1,"duplicate rejection snapshot does not repeat cleanup");f.defaultV();f.accept(true);check(f.resets==2&&!f.key(true).press(),"later successful activation retains the physical held-key fence");
 }
 static void rapidToggleAndOverrides(){
  var f=new Fixture();f.defaultV();f.defaultV();check(f.sent.equals(List.of(true,false))&&!f.local[0].activated,"real default branch queues explicit rapid true/false prediction");f.accept(true);check(f.resets==1&&f.local[0].activated,"earlier true acknowledgement still resets the delegate once");f.defaultV();check(f.sent.equals(List.of(true,false,false))&&!f.local[0].activated,"a new default V request uses the current reconciled source flag");f.accept(false);check(f.resets==2&&!f.local[0].activated,"matching predicted false cannot hide authoritative deactivation cleanup");f.accept(false);check(f.resets==2,"duplicate false snapshot stays quiet");
  var active=new Fixture();active.accept(true);active.resets=0;check(active.key(true).press(),"owned skill/delegate is active before V override");var exact=AbilityStorage.encode(active.local[0]);active.overrideV();check(active.overrides==1&&active.sent.isEmpty()&&active.local[0].activated&&AbilityStorage.encode(active.local[0]).equals(exact),"existing skill override wins and cannot accidentally send default activation or mutate raw state");check(!active.key(true).press(),"override abort preserves held K physical state");active.accept(true);check(active.resets==0,"unchanged acknowledgement after override does not invent default reset");
  var none=new AbilityProgress();int[] calls={0};new ClassicActivationClientState().activate(none,false,()->calls[0]++,value->calls[0]++);check(calls[0]==0&&!none.activated,"actual default UI seam retains category gate");
 }
 static void presetAndCategoryReconciliation(){
  var f=new Fixture();f.local[0].presets.switchTo(1);f.server.presets.switchTo(1);f.accept(false);check(f.resets==0,"local preset prediction matching its acknowledgement does not gain an extra reset");
  f.local[0].presets.edit(1,0,"arc_gen",id->true);f.server.presets.edit(1,0,"arc_gen",id->true);f.accept(false);check(f.resets==0,"matching local preset revision prediction keeps existing cleanup semantics");
  f.server.presets.switchTo(2);f.accept(false);check(f.resets==1&&f.local[0].presets.current()==2,"different authoritative preset retains original fallback reset");f.server.changeCategoryClassic("teleporter");f.accept(false);check(f.resets==2&&f.local[0].category.equals("teleporter"),"category change retains the existing reset and live snapshot replacement");
 }
 static void deathReconnectAndSameDimensionRespawn(){
  var f=new Fixture();Object world=new Object(),player=new Object();f.replace(world,player,false,0);f.accept(true);f.resets=0;check(f.key(true).press(),"live K delegate precedes death");f.overrideV();f.defaultV();check(!f.local[0].activated,"after the active-skill V override, a second default V prediction can precede server death");f.key.abort();check(!f.key.active(),"existing dead-client cleanup aborts its logical latch independently of prediction");f.accept(false);check(f.resets==1&&!f.local[0].activated&&!f.key(true).press(),"death acknowledgement cannot be hidden by a matching prediction and held K stays fenced");
  check(!f.activation.update(true,true,10),"V press before reconnect is armed");f.replace(null,null,true,20);check(!f.local[0].hasCategory()&&!f.activation.update(false,true,30),"disconnect clears real state/authoritative tracker and cannot release old armed V");f.replace(world,new Object(),true,40);check(!f.activation.update(false,true,50),"same-dimension replacement player cannot activate from old held V");f.accept(true);check(f.resets==2&&f.local[0].activated&&!f.key(true).press(),"first active snapshot in a replacement session resets independently of the previous dead session");
  f.replace(world,new Object(),true,60);f.accept(false);check(!f.local[0].activated&&!f.activation.update(false,true,70)&&!f.key(true).press(),"same-dimension respawn starts inactive and keeps physical K/V fences");
 }
 static void observersDoNotPerformSnapshotReconciliation(){
  NeoForge.EVENT_BUS.start();var f=new Fixture();f.defaultV();int[] observed={0};Consumer<AbilityDeactivateEvent> listener=e->{if(e.state==f.local[0]){observed[0]++;check(e.state.activated,"false observer does not assign the predicted true flag");}};NeoForge.EVENT_BUS.addListener(AbilityDeactivateEvent.class,listener);
  try{AbilityActivationLifecycle.post((Player)null,f.local[0],false);AbilityActivationLifecycle.post((Player)null,f.local[0],false);check(observed[0]==2&&f.resets==0&&f.local[0].activated,"duplicate explicit observers remain observers without fabricating snapshot cleanup");f.accept(false);check(f.resets==1&&!f.local[0].activated&&observed[0]==2,"subsequent authoritative rejection restores cleanup without synthesizing another observer");}finally{NeoForge.EVENT_BUS.unregister(listener);}
 }
 private static final class SeamLoader extends java.net.URLClassLoader {
  int denied;SeamLoader(java.net.URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
  @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.")||name.startsWith("com.mojang.blaze3d.")||name.startsWith("org.lwjgl.")){denied++;throw new ClassNotFoundException("seam denies Minecraft client/graphics "+name);}return super.loadClass(name,resolve);}
 }
 public static void main(String[] args)throws Exception{
  if(args.length==0){var urls=new ArrayList<java.net.URL>();for(String path:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))urls.add(java.nio.file.Path.of(path).toUri().toURL());try(var loader=new SeamLoader(urls.toArray(java.net.URL[]::new))){var runner=loader.loadClass(ClassicActivationClientStateRegressionTest.class.getName());try{runner.getMethod("main",String[].class).invoke(null,(Object)new String[]{"isolated"});}catch(java.lang.reflect.InvocationTargetException e){throw new AssertionError("Actual production client seam failed",e.getCause());}if(loader.denied!=0)throw new AssertionError("Client seam requested Minecraft client/graphics implementations");System.out.println("PASS real production client adapter executes with Minecraft client/NeoForge-client/Blaze3D/LWJGL namespaces denied; requests=0");}return;}
  var productionLocation=ClassicActivationClientState.class.getProtectionDomain().getCodeSource().getLocation();
  var testLocation=ClassicActivationClientStateRegressionTest.class.getProtectionDomain().getCodeSource().getLocation();
  var productionPath=java.nio.file.Path.of(productionLocation.toURI()).toRealPath();
  var formalMain=java.nio.file.Path.of("build/classes/java/main").toRealPath();
  check(productionLocation.equals(testLocation)||productionPath.equals(formalMain),"production adapter originates in fresh combined stage or formal Gradle main classes, not an external cached fixture/JAR");
  try(var bytes=ClassicActivationClientState.class.getResourceAsStream("ClassicActivationClientState.class")){
   if(bytes==null)throw new AssertionError("Missing actual production class resource");
   if(!java.util.Arrays.equals(bytes.readAllBytes(),java.nio.file.Files.readAllBytes(productionPath.resolve("cn/academy/port/client/ClassicActivationClientState.class"))))throw new AssertionError("Actual production class bytes differ from verified compiler output");
  }matchingAcknowledgement();rejectedPrediction();rapidToggleAndOverrides();presetAndCategoryReconciliation();deathReconnectAndSameDimensionRespawn();observersDoNotPerformSnapshotReconciliation();System.out.println("PASS "+checks+" actual production client state/activation adapter, native snapshot codec, held-key, override, death/session and observer regressions; no Minecraft/graphics bootstrap");
 }
}

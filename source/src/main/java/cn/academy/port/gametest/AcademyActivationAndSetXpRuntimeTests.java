/* Source activation/raw-XP server boundaries. Stage compiles only; owner runs the serial native lane. GPLv3. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.*;
import cn.academy.port.core.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyActivationAndSetXpRuntimeTests {
 private static final String TEMPLATE="runtime_empty",BATCH="academy_activation_and_setxp";
 private static void prepare(AbilityProgress s){s.restoreCalculatedMaxima(9999,444);s.extraCp=100.125f;s.extraOverload=33.25f;s.cp=713.125f;s.overload=17.75f;s.levelExperience=7.7f;s.overloadFine=false;s.cpDelay=13;s.overloadDelay=21;}
 private static void command(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,String args){try{h.assertValueEqual(p.server.getCommands().getDispatcher().execute("aimp "+p.getGameProfile().getName()+" "+args,p.server.createCommandSourceStack().withPermission(4)),1,"actual production command succeeds: "+args);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(e);}}
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void actual_toggle_posts_activation_after_flag_and_same_state_is_silent(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);boolean rejected=false;try{s.setActivateState(true);}catch(IllegalStateException e){rejected=true;}h.assertTrue(rejected&&!s.activated,"no-category activation rejected before same-state or event mutation");s.changeCategoryClassic("teleporter");s.learn("threatening_teleport");prepare(s);s.presets.edit(0,0,"threatening_teleport",id->true);s.setCooldown("threatening_teleport",81);var phases=new ArrayList<String>();
   Consumer<AbilityActivateEvent> high=e->{if(e.getEntity()==p){phases.add("activate-high");h.assertTrue(e.state==s&&s.activated&&s.isActivated()&&s.cp==713.125f&&s.overload==17.75f&&s.extraCp==100.125f&&s.levelExperience==7.7f,"actual bound HIGHEST sees new flag with untouched resource/progress ledgers");}};
   Consumer<AbilityActivateEvent> low=e->{if(e.getEntity()==p)phases.add("activate-low");};Consumer<AbilityDeactivateEvent> deactive=e->{if(e.getEntity()==p){phases.add("deactivate");h.assertTrue(e.state==s&&!s.activated&&!s.isActivated()&&s.cp==713.125f&&s.overload==17.75f,"actual deactivation occurs after flag write and before any resource mutation");}};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityActivateEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,AbilityActivateEvent.class,low);NeoForge.EVENT_BUS.addListener(AbilityDeactivateEvent.class,deactive);
   try{
    h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("toggle","")),"actual authenticated toggle ingress accepts registered request");h.assertTrue(phases.equals(List.of("activate-high","activate-low")),"native activation priorities reflect source synchronous event");var exact=AbilityStorage.encode(s);phases.clear();s.setActivateState(true);h.assertTrue(phases.isEmpty()&&AbilityStorage.encode(s).equals(exact),"same active setter is exact live-state/event no-op");
    h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("toggle","")),"actual second authenticated toggle ingress");h.assertTrue(phases.equals(List.of("deactivate"))&&!s.activated&&s.presets.currentSkill(0).equals("threatening_teleport")&&s.cooldowns.get("threatening_teleport")==81,"actual toggle preserves mapped skill/cooldown and deactivates once");phases.clear();s.setActivateState(false);h.assertTrue(phases.isEmpty(),"same inactive setter does not emit duplicate event");h.succeed();
   }finally{for(Object listener:List.of(high,low,deactive))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void source_category_removal_nests_deactivation_before_normal_recalc_and_other_category_retains_active(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.learn("arc_gen");s.setActivateState(true);prepare(s);var phases=new ArrayList<String>();
   Consumer<CategoryChangeEvent> high=e->{if(e.getEntity()==p){phases.add("category-high");h.assertTrue(!s.hasCategory()&&s.activated&&s.baseCp()==9999&&s.cp==713.125f,"HIGHEST sees removed category before CP NORMAL deactivation/recalc");}};
   Consumer<AbilityDeactivateEvent> deactive=e->{if(e.getEntity()==p){phases.add("deactivate");h.assertTrue(!s.activated&&s.baseCp()==9999&&s.cp==713.125f&&s.extraCp==100.125f&&s.levelExperience==7.7f,"deactivation nests in category common handler before recalc/refill");}};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p)phases.add("maxcp");};Consumer<AbilityCalculationEvent.MaxOverload> over=e->{if(e.player==p)phases.add("maxoverload");};Consumer<CategoryChangeEvent> low=e->{if(e.getEntity()==p)phases.add("category-low");};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,CategoryChangeEvent.class,high);NeoForge.EVENT_BUS.addListener(AbilityDeactivateEvent.class,deactive);NeoForge.EVENT_BUS.addListener(AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(AbilityCalculationEvent.MaxOverload.class,over);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CategoryChangeEvent.class,low);
   try{s.changeCategoryClassic("");h.assertTrue(phases.equals(List.of("category-high","deactivate","maxcp","maxoverload","category-low"))&&!s.activated&&s.level==0&&s.extraCp==100.125f&&s.levelExperience==7.7f,"actual native category bus follows source nested event and preserves training/progress");phases.clear();s.changeCategoryClassic("");h.assertTrue(phases.isEmpty(),"duplicate category removal is silent");}finally{for(Object listener:List.of(high,deactive,cp,over,low))NeoForge.EVENT_BUS.unregister(listener);}
   s.changeCategoryClassic("electromaster");s.setActivateState(true);int[] count={0};Consumer<AbilityDeactivateEvent> watch=e->{if(e.getEntity()==p)count[0]++;};NeoForge.EVENT_BUS.addListener(AbilityDeactivateEvent.class,watch);try{s.changeCategoryClassic("meltdowner");h.assertTrue(s.activated&&count[0]==0,"source replacement with another real category retains activation");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(watch);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void real_death_recovers_before_deactivation_and_death_clone_has_no_duplicate_event(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.learn("arc_gen");s.setActivateState(true);prepare(s);s.setCooldown("arc_gen",81);int[] count={0};
   Consumer<AbilityDeactivateEvent> listener=e->{if(e.getEntity()==p){count[0]++;h.assertTrue(e.state==s&&!s.activated&&s.cp==s.maxCp()&&s.overload==0&&!s.overloadFine&&s.cpDelay==13&&s.overloadDelay==21&&s.extraCp==100.125f&&s.levelExperience==7.7f,"source LOWEST death recovers before posting changed activation and preserves delays/growth/progress");}};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityDeactivateEvent.class,listener);
   try{p.setInvulnerable(false);p.hurt(p.damageSources().fellOutOfWorld(),Float.MAX_VALUE);h.assertTrue(!p.isAlive()&&count[0]==1&&!s.activated&&s.cooldowns.isEmpty(),"actual player death emits exactly one deactivation and preserves modern cleanup");var replacement=actors.player();replacement.restoreFrom(p,false);var clone=AbilityStorage.get(replacement);h.assertTrue(count[0]==1&&!clone.activated&&clone.cp==clone.maxCp()&&clone.cooldowns.isEmpty(),"actual death clone uses setter silently for already inactive source state");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void actual_command_xp_setter_posts_changed_only_equal_writes_and_cold_rebind_is_silent(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);command(h,p,"cat electromaster");command(h,p,"learn arc_gen");prepare(s);s.setActivateState(true);var phases=new ArrayList<String>();int[] count={0,0,0};
   Consumer<SkillExpChangedEvent> changed=e->{if(e.getEntity()==p){count[0]++;phases.add("changed");h.assertTrue(e.state==s&&e.category.equals("electromaster")&&e.skill.equals("arc_gen")&&s.experience.get("arc_gen")==((double).2f)&&s.cp==713.125f&&s.overload==17.75f&&s.baseCp()==9999&&s.levelExperience==7.7f,"production command posts Changed after single-precision raw write without refill/progress");}};
   Consumer<SkillExpAddedEvent> added=e->{if(e.getEntity()==p)count[1]++;};Consumer<SkillLearnEvent> learned=e->{if(e.getEntity()==p)count[2]++;};Consumer<AbilityActivateEvent> active=e->{if(e.getEntity()==p)phases.add("activate");};
   NeoForge.EVENT_BUS.addListener(SkillExpChangedEvent.class,changed);NeoForge.EVENT_BUS.addListener(SkillExpAddedEvent.class,added);NeoForge.EVENT_BUS.addListener(SkillLearnEvent.class,learned);NeoForge.EVENT_BUS.addListener(AbilityActivateEvent.class,active);
   try{
    command(h,p,"exp arc_gen 0.2");command(h,p,"exp arc_gen 0.2");h.assertTrue(phases.equals(List.of("changed","changed"))&&count[0]==2&&count[1]==0&&count[2]==0,"actual equal command writes each emit Changed without Added/Learned");var exact=AbilityStorage.encode(s);command(h,p,"exp charging 0.8");h.assertTrue(!s.learned("charging")&&AbilityStorage.encode(s).equals(exact)&&count[0]==2,"actual command setter silently ignores unlearned valid skill");
    for(String raw:List.of("NaN","Infinity","-0.01","1.01"))try{h.assertValueEqual(p.server.getCommands().getDispatcher().execute("aimp "+p.getGameProfile().getName()+" exp arc_gen "+raw,p.server.createCommandSourceStack().withPermission(4)),0,"existing finite/range command guard: "+raw);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new AssertionError(e);}h.assertTrue(AbilityStorage.encode(s).equals(exact)&&count[0]==2,"rejected command values cannot mutate or post events");
    AbilityStorage.remove(p);var cold=AbilityStorage.get(p);h.assertTrue(cold!=s&&cold.activated&&cold.experience.get("arc_gen")==((double).2f)&&cold.cp==713.125f&&cold.baseCp()==9999&&cold.levelExperience==7.7f&&count[0]==2&&phases.equals(List.of("changed","changed")),"actual save/cache drop/wake binds observers without inventing restore events");h.succeed();
   }finally{for(Object listener:List.of(changed,added,learned,active))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
}

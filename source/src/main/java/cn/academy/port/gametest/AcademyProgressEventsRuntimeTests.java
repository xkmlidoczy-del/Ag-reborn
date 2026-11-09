/* Native AbilityData/CPData progression priority boundaries. Compiled only by stage worker. GPLv3. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.*;
import cn.academy.port.core.*;
import cn.academy.port.skill.ThreateningTeleport;
import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyProgressEventsRuntimeTests {
 private static final String TEMPLATE="runtime_empty",BATCH="academy_progress_events";
 private static void flags(GameTestHelper h,AbilityProgress s){h.assertTrue(s.activated&&!s.overloadFine&&s.cpDelay==13&&s.overloadDelay==21,"source progress events preserve activation, overload lock and recovery delays");}
 private static void prepare(AbilityProgress s){s.restoreCalculatedMaxima(9999,444);s.extraCp=100.125f;s.extraOverload=33.25f;s.cp=713.125f;s.overload=17.75f;s.levelExperience=7.7f;s.activated=true;s.overloadFine=false;s.cpDelay=13;s.overloadDelay=21;}
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void generic_xp_autolearns_without_tree_and_posts_original_priority_payload(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(1);s.unlearnedExperience.put("space_fluct",(double).73f);prepare(s);var phases=new ArrayList<String>();int[] counts={0,0,0};float[] expectedProgress={7.7f};
   Consumer<SkillLearnEvent> high=e->{if(e.getEntity()==p){counts[0]++;phases.add("learn-high");h.assertTrue(e.state==s&&e.skill.equals("space_fluct")&&s.learned("space_fluct")&&!s.learned("dim_folding_theorem")&&s.level==1,"real generic mutation bypasses source tree/level prerequisites");h.assertTrue(s.cp==713.125f&&s.baseCp()==9999&&s.overload==17.75f&&s.levelExperience==7.7f&&s.exp("space_fluct")==((double).73f),"HIGHEST sees learned bit before CP/common or award mutation");flags(h,s);}};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p){phases.add("maxcp");e.value=(float)e.value+17.25f;}};
   Consumer<AbilityCalculationEvent.MaxOverload> over=e->{if(e.player==p){phases.add("maxoverload");e.value=(float)e.value+17.25f;}};
   Consumer<SkillLearnEvent> low=e->{if(e.getEntity()==p){phases.add("learn-low");h.assertTrue(s.cp==1917.375f&&s.baseCp()==1817.25f&&s.overload==0&&s.extraCp==100.125f&&s.levelExperience==7.7f&&s.exp("space_fluct")==((double).73f),"LOWEST sees source CP NORMAL effects before outer XP award");flags(h,s);}};
   Consumer<SkillExpChangedEvent> changed=e->{if(e.getEntity()==p){counts[1]++;expectedProgress[0]+=.35f;phases.add("changed");h.assertTrue(e.state==s&&e.skill.equals("space_fluct")&&s.exp("space_fluct")==1&&s.levelExperience==(double)expectedProgress[0],"Changed sees capped raw XP and full requested progression");}};
   Consumer<SkillExpAddedEvent> added=e->{if(e.getEntity()==p){counts[2]++;phases.add("added");h.assertTrue(e.state==s&&e.amount==.35f&&s.exp("space_fluct")==1,"Added carries original float request beyond mastery cap");}};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,SkillLearnEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxOverload.class,over);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,SkillLearnEvent.class,low);NeoForge.EVENT_BUS.addListener(SkillExpChangedEvent.class,changed);NeoForge.EVENT_BUS.addListener(SkillExpAddedEvent.class,added);
   try{
    s.addExperienceRaw("space_fluct",.35f);h.assertTrue(phases.equals(List.of("learn-high","maxcp","maxoverload","learn-low","changed","added")),"real native bus posts learn/common/Changed/Added source sequence");phases.clear();s.cp=23;s.overload=11;s.addPassiveExperienceRaw("space_fluct",.35f);h.assertTrue(counts[0]==1&&counts[1]==2&&counts[2]==2&&phases.equals(List.of("changed","added"))&&s.cp==23&&s.overload==11,"duplicate awards remain capped, post both events, and do not refill CP");
    var cold=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(s),AcademyConfig.consumptionConfig());h.assertTrue(cold.learned("space_fluct")&&cold.exp("space_fluct")==1&&cold.levelExperience==s.levelExperience&&counts[0]==1&&counts[1]==2&&counts[2]==2,"actual native save/cold restore emit no new acquisition or XP events");h.succeed();
   }finally{for(Object listener:List.of(high,cp,over,low,changed,added))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void level_changes_post_before_normal_growth_reset_and_same_level_is_silent(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(3);s.learn("space_fluct");s.experience.put("space_fluct",(double).62f);prepare(s);var phases=new ArrayList<String>();int[] calls={0};
   Consumer<LevelChangeEvent> high=e->{if(e.getEntity()==p){calls[0]++;phases.add("level-high");h.assertTrue(e.state==s&&s.level==5&&s.levelExperience==0&&s.extraCp==100.125f&&s.extraOverload==33.25f&&s.cp==713.125f&&s.overload==17.75f&&s.baseCp()==9999,"real level HIGHEST sees new primitive level and old CP/growth");flags(h,s);}};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p){phases.add("maxcp");h.assertTrue(s.extraCp==0&&s.extraOverload==0,"source common clears growth before calculation");}};
   Consumer<AbilityCalculationEvent.MaxOverload> over=e->{if(e.player==p)phases.add("maxoverload");};
   Consumer<LevelChangeEvent> low=e->{if(e.getEntity()==p){phases.add("level-low");h.assertTrue(s.level==5&&s.extraCp==0&&s.extraOverload==0&&s.cp==8000&&s.overload==0&&s.exp("space_fluct")==((double).62f),"level LOWEST sees NORMAL reset/refill with retained mastery");flags(h,s);}};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LevelChangeEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxOverload.class,over);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LevelChangeEvent.class,low);
   try{
    s.setLevel(5);h.assertTrue(phases.equals(List.of("level-high","maxcp","maxoverload","level-low"))&&calls[0]==1,"native level event surrounds exactly one MAX pair");s.extraCp=7.5;s.extraOverload=1.25;s.cp=42;s.overload=19;s.levelExperience=.73;var exact=AbilityStorage.encode(s);phases.clear();s.setLevel(5);s.learn("space_fluct");h.assertTrue(phases.isEmpty()&&calls[0]==1&&AbilityStorage.encode(s).equals(exact),"same level and duplicate learn preserve exact live snapshot");boolean wrong=false;try{s.addExperienceRaw("arc_gen",.1f);}catch(IllegalStateException e){wrong=true;}h.assertTrue(wrong&&AbilityStorage.encode(s).equals(exact),"wrong-category award rejects before any state/event mutation");h.succeed();
   }finally{for(Object listener:List.of(high,cp,over,low))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void threatening_passives_share_generic_events_and_no_category_mutators_reject(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);boolean rejected=false;try{s.setLevel(0);}catch(IllegalStateException e){rejected=true;}h.assertTrue(rejected&&s.level==0&&s.cp==0&&s.baseCp()==100,"source no-category setLevel checks before same-value comparison");rejected=false;try{s.addExperienceRaw("space_fluct",.1f);}catch(IllegalStateException e){rejected=true;}h.assertTrue(rejected&&!s.learned("space_fluct")&&s.levelExperience==0,"source no-category awards reject without partial state");s.selectCategory("teleporter");s.setLevel(1);var phases=new ArrayList<String>();
   Consumer<SkillLearnEvent> learned=e->{if(e.getEntity()==p)phases.add("learn:"+e.skill);};Consumer<SkillExpChangedEvent> changed=e->{if(e.getEntity()==p)phases.add("changed:"+e.skill);};Consumer<SkillExpAddedEvent> added=e->{if(e.getEntity()==p)phases.add("added:"+e.skill+":"+e.amount);};
   NeoForge.EVENT_BUS.addListener(SkillLearnEvent.class,learned);NeoForge.EVENT_BUS.addListener(SkillExpChangedEvent.class,changed);NeoForge.EVENT_BUS.addListener(SkillExpAddedEvent.class,added);
   try{ThreateningTeleport.recordCritical(s,2);h.assertTrue(phases.equals(List.of("learn:dim_folding_theorem","changed:dim_folding_theorem","added:dim_folding_theorem:0.015","learn:space_fluct","changed:space_fluct","added:space_fluct:1.0E-4")),"existing critical caller uses the generic source event sequence for both passives");h.assertTrue(s.learned("dim_folding_theorem")&&s.learned("space_fluct")&&s.exp("dim_folding_theorem")==((double).015f)&&s.exp("space_fluct")==((double).0001f),"existing raw passive arithmetic remains source float");h.succeed();}finally{for(Object listener:List.of(learned,changed,added))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void xp_changed_listener_may_change_live_category_without_changing_added_identity(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(3);s.learn("space_fluct");var phases=new ArrayList<String>();
   Consumer<SkillExpChangedEvent> changed=e->{if(e.getEntity()==p){phases.add("changed");h.assertTrue(e.category.equals("teleporter")&&e.skill.equals("space_fluct")&&e.state==s,"Changed keeps original skill identity");s.changeCategoryClassic("electromaster");}};
   Consumer<SkillExpAddedEvent> added=e->{if(e.getEntity()==p){phases.add("added");h.assertTrue(e.category.equals("teleporter")&&e.skill.equals("space_fluct")&&e.amount==.2f&&e.state==s&&s.category.equals("electromaster")&&s.experience.isEmpty(),"Added retains original skill/request and exposes current post-listener ability state");}};
   NeoForge.EVENT_BUS.addListener(SkillExpChangedEvent.class,changed);NeoForge.EVENT_BUS.addListener(SkillExpAddedEvent.class,added);
   try{s.addExperienceRaw("space_fluct",.2f);h.assertTrue(phases.equals(List.of("changed","added")),"synchronous native Changed completes before Added");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(changed);NeoForge.EVENT_BUS.unregister(added);}
  }
 }

}

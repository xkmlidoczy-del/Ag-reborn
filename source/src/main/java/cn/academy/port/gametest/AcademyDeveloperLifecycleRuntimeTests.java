/* Native category event/real development boundaries. Compiled here; execution belongs to serial owner. GPLv3. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.achievements.ClassicAchievements;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyDeveloperLifecycleRuntimeTests {
 private static final String TEMPLATE="runtime_empty",BATCH="academy_developer_lifecycle";
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void fresh_cp_defaults_survive_get_binding_snapshot_and_absent_category_ticks(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);int[] maxima={0};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p)maxima[0]++;};
   Consumer<AbilityCalculationEvent.MaxOverload> overload=e->{if(e.player==p)maxima[0]++;};
   NeoForge.EVENT_BUS.addListener(AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(AbilityCalculationEvent.MaxOverload.class,overload);
   try{
    h.assertTrue(!s.hasCategory()&&s.level==0&&!s.activated&&s.cp==0&&s.overload==0&&s.baseCp()==100&&s.baseOverload()==100&&s.extraCp==0&&s.extraOverload==0&&s.overloadFine,"unchanged fresh CPData field defaults reach actual native storage");
    s.cpDelay=7;s.overloadDelay=9;s.overloadFine=false;
    for(int tick=0;tick<40;tick++){h.assertTrue(AbilityStorage.get(p)==s,"cache identity survives native rebinding");s.tick();h.assertTrue(s.maxCp()==100&&s.maxOverload()==100,"repeated native getters retain constructor maxima");}
    h.assertTrue(s.cp==0&&s.cpDelay==7&&s.overloadDelay==9&&!s.overloadFine&&maxima[0]==0,"absent category skips CP/O recovery/delays and no Max events");
    var snapshot=AbilityStorage.encode(s);var cold=AbilityStorage.decode(snapshot,AcademyConfig.consumptionConfig());h.assertTrue(cold.cp==0&&cold.baseCp()==100&&cold.baseOverload()==100&&cold.cpDelay==7&&cold.overloadDelay==9&&!cold.overloadFine,"actual native codec retains fresh cached raw values");
    AbilityStorage.save(p);h.assertTrue(p.getPersistentData().getCompound("academy:classic_progress").getDouble("raw_max_cp")==100&&maxima[0]==0,"save/binding/getters never calculate new capacity");
    s.recoverAll();h.assertTrue(s.cp==100&&s.overload==0&&!s.overloadFine&&maxima[0]==0,"recoverAll uses cached raw100 without Max events");var replacement=actors.player();AbilityStorage.clone(p,replacement);var copy=AbilityStorage.get(replacement);h.assertTrue(copy.cpDataConfig()==s.cpDataConfig()&&copy.cp==100&&copy.baseCp()==100&&copy.baseOverload()==100&&maxima[0]==0,"actual clone transfers live wake data snapshot and cached defaults without recalculation");h.succeed();
   }finally{NeoForge.EVENT_BUS.unregister(cp);NeoForge.EVENT_BUS.unregister(overload);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void authenticated_portable_acquisition_posts_category_only_without_lv1_achievement(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);h.assertTrue(!s.hasCategory()&&s.cp==0&&s.baseCp()==100&&s.baseOverload()==100,"actual fresh CPData defaults before portable acquisition");
   var portable=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,portable);var factor=InductionFactors.stack("meltdowner");factor.setCount(7);p.getInventory().setItem(7,factor);new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(10000);
   var phases=new ArrayList<String>();int[] category={0},level={0},calcs={0};
   Consumer<CategoryChangeEvent> high=e->{if(e.getEntity()==p){category[0]++;phases.add("category-high");h.assertTrue(s.category.equals("meltdowner")&&s.level==1&&s.experience.isEmpty()&&s.unlearnedExperience.isEmpty(),"primitive source category/slot mutation precedes HIGHEST");h.assertTrue(s.baseCp()==100&&s.cp==0,"HIGHEST sees retained actual fresh pre-category raw max/resource");h.assertTrue(p.getInventory().getItem(7).isEmpty(),"source chooseCategory consumes entire factor before category event");}};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p){calcs[0]++;phases.add("maxcp");}};
   Consumer<AbilityCalculationEvent.MaxOverload> overload=e->{if(e.player==p){calcs[0]++;phases.add("maxoverload");}};
   Consumer<CategoryChangeEvent> low=e->{if(e.getEntity()==p){phases.add("category-low");h.assertTrue(s.cp==s.maxCp()&&s.overload==0&&s.extraCp==0&&s.levelExperience==0,"NORMAL source effects finish before LOWEST");}};
   Consumer<LevelChangeEvent> levels=e->{if(e.getEntity()==p)level[0]++;};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,CategoryChangeEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxOverload.class,overload);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CategoryChangeEvent.class,low);NeoForge.EVENT_BUS.addListener(LevelChangeEvent.class,levels);
   try{h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("develop_level","")),"authenticated real portable ingress");for(int tick=1;tick<=130;tick++){DevelopmentController.tick(p,v->{});if(tick<130)h.assertTrue(category[0]==0&&level[0]==0&&!s.hasCategory(),"five source26tick stimulations delay acquisition");}
    h.assertTrue(category[0]==1&&level[0]==0&&calcs[0]==2,"one category, no false level, one max pair");h.assertTrue(phases.equals(List.of("category-high","maxcp","maxoverload","category-low")),"real priority dispatch surrounds source common effects");h.assertFalse(ClassicAchievements.earned(p,"meltdowner.lv1"),"unchanged DispatcherAch has no acquisition LevelChangeEvent");h.assertTrue(s.cp==1800&&s.level==1&&s.category.equals("meltdowner"),"ordinary acquired final resources");h.succeed();
   }finally{for(Object listener:List.of(high,cp,overload,low,levels))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void completed_advanced_reset_retains_activation_and_exact_native_category_phases(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(5);s.learn("vec_deviation");s.activated=true;h.assertTrue(cn.academy.port.skill.VecDeviation.start(p,1),"real persistent old-category context starts");s.experience.put("vec_deviation",(double).4f);s.unlearnedExperience.put("vec_accel",(double).3f);s.presets.switchTo(2);s.presets.edit(2,0,"vec_deviation",id->true);s.setCooldown("vec_deviation",91);s.restoreCalculatedMaxima(9999,444);s.extraCp=100.125f;s.extraOverload=33.25f;s.levelExperience=7.7f;s.cp=713.125f;s.overload=17.75f;s.cpDelay=13;s.overloadDelay=21;s.overloadFine=false;
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AcademyCraft.MAGNETIC_COIL.get(),9));var factor=InductionFactors.stack("teleporter");factor.setCount(7);p.getInventory().setItem(5,factor);
   var completing=DevelopmentController.process(p);var phases=new ArrayList<String>();int[] category={0},level={0};
   Consumer<CategoryChangeEvent> high=e->{if(e.getEntity()==p){category[0]++;phases.add("category-high");h.assertTrue(s.category.equals("teleporter")&&s.level==5&&s.activated,"category mutation retains previous level/activation");h.assertTrue(s.baseCp()==9999&&s.baseOverload()==444&&s.cp==713.125f&&s.overload==17.75f,"HIGHEST retains cached maxima/resources");h.assertTrue(s.extraCp==100.125f&&s.extraOverload==33.25f&&s.levelExperience==7.7f,"HIGHEST retains training/progress");h.assertTrue(s.experience.isEmpty()&&s.unlearnedExperience.isEmpty()&&!s.cooldowns.isEmpty()&&s.presets.currentSkill(0).equals("vec_deviation"),"raw/learned slots clear before mapped common handlers");h.assertTrue(cn.academy.port.skill.VecDeviation.active(p),"HIGHEST precedes source context disposal");}};
   Consumer<AbilityCalculationEvent.MaxCP> cp=e->{if(e.player==p){phases.add("maxcp:"+s.level);h.assertTrue(s.cooldowns.isEmpty()&&s.cooldownMaxTicks.isEmpty(),"original sorted cooldown handler precedes CP calculation");h.assertFalse(cn.academy.port.skill.VecDeviation.active(p),"category NORMAL disposes old skill context before CP handler");h.assertTrue(DevelopmentController.process(p)==completing&&completing.isDeveloping(),"category handler does not remove or abort completing process");if(s.level==5)h.assertTrue(s.presets.currentSkill(0).equals("vec_deviation"),"original preset handler is after CP calculation");e.value=(float)e.value+17.25f;}};
   Consumer<AbilityCalculationEvent.MaxOverload> overload=e->{if(e.player==p){phases.add("maxoverload:"+s.level);e.value=(float)e.value+17.25f;}};
   Consumer<CategoryChangeEvent> low=e->{if(e.getEntity()==p){phases.add("category-low");h.assertTrue(s.level==5&&s.activated&&s.cp==8117.375f&&s.baseCp()==8017.25f&&s.levelExperience==7.7f,"LOWEST observes previous-level category recalc retaining growth/progress");h.assertTrue(s.presets.current()==2&&s.presets.currentSkill(0).isEmpty(),"source clear preserves selected preset");}};
   Consumer<LevelChangeEvent> levels=e->{if(e.getEntity()==p){level[0]++;phases.add("level-low");h.assertTrue(s.level==4&&s.activated&&s.extraCp==0&&s.extraOverload==0&&s.levelExperience==0&&s.cp==5817.25f,"unchanged level seam observes final reduced-level CP state");}};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,CategoryChangeEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,cp);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxOverload.class,overload);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CategoryChangeEvent.class,low);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LevelChangeEvent.class,levels);
   try{var battery=new DevelopmentProcess.Developer(){double energy=200000;public DeveloperType type(){return DeveloperType.ADVANCED;}public boolean tryPullEnergy(double value){if(value>energy)return false;energy-=value;return true;}public double energy(){return energy;}public double maxEnergy(){return 200000;}};
    h.assertTrue(DevelopmentController.start(p,battery,DevelopmentActions.reset(s,InductionFactors.inventory(p))),"server-owned real reset action and complete process");for(int tick=1;tick<=800;tick++)DevelopmentController.tick(p,v->{});
    h.assertTrue(category[0]==1&&level[0]==1&&phases.equals(List.of("category-high","maxcp:5","maxoverload:5","category-low","maxcp:4","maxoverload:4","level-low")),"ordered category phase then reduced-level phase");h.assertTrue(p.getMainHandItem().isEmpty()&&p.getInventory().getItem(5).isEmpty(),"entire source coil/factor slots clear after both phases");h.assertTrue(DevelopmentController.process(p)==completing&&completing.state()==DevelopmentProcess.State.DONE&&!cn.academy.port.skill.VecDeviation.active(p),"same process finishes after context-only category disposal");h.assertTrue(s.cpDelay==13&&s.overloadDelay==21&&!s.overloadFine&&s.activated,"category/reset preserve source recovery/lock/activation fields");h.succeed();
   }finally{for(Object listener:List.of(high,cp,overload,low,levels))NeoForge.EVENT_BUS.unregister(listener);}
  }
 }
 @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
 public static void same_category_is_silent_and_removal_deactivates_inside_normal_handler(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
   var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(3);s.learn("threatening_teleport");s.activated=true;s.extraCp=101.25f;s.extraOverload=7.5f;s.levelExperience=.4f;s.restoreCalculatedMaxima(4567,231);int[] events={0};
   Consumer<CategoryChangeEvent> high=e->{if(e.getEntity()==p){events[0]++;h.assertTrue(!s.hasCategory()&&s.level==0&&s.activated&&s.baseCp()==4567,"removal HIGHEST sees changed primitive category with retained raw activation/max");}};
   Consumer<CategoryChangeEvent> low=e->{if(e.getEntity()==p)h.assertTrue(!s.activated&&s.cp==1901.25f&&s.extraCp==101.25f&&s.levelExperience==.4f,"NORMAL disables absent category and recalculates without level reset");};
   NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,CategoryChangeEvent.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CategoryChangeEvent.class,low);
   try{long revision=s.presets.revision();s.changeCategoryClassic("teleporter");h.assertTrue(events[0]==0&&s.activated&&s.learned("threatening_teleport")&&s.baseCp()==4567&&s.presets.revision()==revision,"same-category no event or mutation");s.changeCategoryClassic("");h.assertTrue(events[0]==1&&!s.activated&&s.level==0,"one actual removal event");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(high);NeoForge.EVENT_BUS.unregister(low);}
  }
 }
}

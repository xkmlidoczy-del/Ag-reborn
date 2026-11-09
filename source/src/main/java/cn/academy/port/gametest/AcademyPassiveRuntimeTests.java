package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.api.AbilityCalculationEvent;
import cn.academy.port.skill.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;
/** Finite declared prerequisite and energy fixtures. Native execution belongs to the parent. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyPassiveRuntimeTests {
 private static final String TEMPLATE="runtime_empty";
 private static FakePlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Passives]"));var at=h.absoluteVec(new Vec3(3.5,1,2.5));p.moveTo(at.x,at.y,at.z,0,0);p.getAbilities().instabuild=false;return p;}
 private static void cleanup(FakePlayer p){DevelopmentController.remove(p);MachineDeveloperSessions.close(p);AbilityStorage.remove(p);}
 private static AbilityProgress prerequisites(FakePlayer p,SkillCatalog.Skill skill){var s=AbilityStorage.get(p);s.selectCategory(skill.category());s.setLevel(skill.level());for(var r:skill.requirements()){s.learn(r.id());s.experience.put(r.id(),r.exp());}if(skill.anyLearnedSkillLevel()>0){var other=SkillCatalog.ALL.stream().filter(k->k.category().equals(skill.category())&&k.level()==skill.anyLearnedSkillLevel()&&!k.id().equals(skill.id())).findFirst().orElseThrow();s.learn(other.id());}return s;}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=30) public static void passive_portable_actual_ingress(GameTestHelper h){learning(h,DeveloperType.PORTABLE);}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=30) public static void passive_normal_actual_ingress(GameTestHelper h){learning(h,DeveloperType.NORMAL);}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=30) public static void passive_advanced_actual_ingress(GameTestHelper h){learning(h,DeveloperType.ADVANCED);}
 private static void learning(GameTestHelper h,DeveloperType tier){var p=player(h);try{
  MachineDeveloperBlockEntity machine=null;
  if(tier!=DeveloperType.PORTABLE){var item=tier==DeveloperType.NORMAL?MachineDevelopers.NORMAL_ITEM.get():MachineDevelopers.ADVANCED_ITEM.get();var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);var at=h.absolutePos(new BlockPos(3,1,5));h.assertTrue(item.place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false))).consumesAction(),"physical finite developer placed");machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(at);p.moveTo(at.getX()+.5,at.getY(),at.getZ()-1.5,0,0);}
  for(var skill:SkillCatalog.ALL){if(skill.controllable())continue;DevelopmentController.remove(p);var s=prerequisites(p,skill);String token="";ItemStack portable=null;
   if(machine==null){portable=AcademyCraft.DEVELOPER.get().getDefaultInstance();p.setItemInHand(InteractionHand.MAIN_HAND,portable);new DeveloperItemEnergy(portable,tier).energy(tier.energy);}else{machine.battery().load(tier.energy);h.assertTrue(machine.use(p),"sender-bound physical machine GUI");token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();}
   var request=new AcademyNetwork.Request(machine==null?"learn":"machine_learn",machine==null?skill.id():token+":"+skill.id());if(machine==null)AcademyGameplay.requestFromClient(p,request);else MachineDeveloperSessions.request(p,request);
   if(!tier.supportsSkill(skill.level())){h.assertFalse(DevelopmentController.process(p).isDeveloping(),"actual ingress rejects insufficient source tier "+skill.id());continue;}
   var process=DevelopmentController.process(p);h.assertTrue(process.isDeveloping(),"actual ingress accepts passive with declared prerequisites");int stimuli=ClassicRules.learningStimulations(skill.level());for(int i=0;i<stimuli*tier.ticksPerStimulation();i++)DevelopmentController.tick(p,snapshot->{});
   h.assertTrue(process.state()==DevelopmentProcess.State.DONE&&s.learned(skill.id())&&s.experience.get(skill.id())==0,"finite native learning leaves zero stored mastery "+skill.id());double energy=machine==null?new DeveloperItemEnergy(portable,tier).energy():machine.battery().getEnergy();h.assertTrue(Math.abs(energy-(tier.energy-tier.actualConsumption(stimuli)))<1e-6,"native TPS+1 source IF cost");h.assertFalse(PresetSkills.selectable(s,skill.id()),"passive never becomes preset action");var cold=AbilityStorage.decode(AbilityStorage.encode(s));h.assertTrue(cold.learned(skill.id())&&cold.presets.currentSkill(0).isEmpty(),"NBT retains passive without preset binding");
  }h.succeed();
 }finally{cleanup(p);}}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=20)
 public static void learned_courses_mutate_real_ordered_calculation_events(GameTestHelper h){var p=player(h);Consumer<AbilityCalculationEvent.MaxCP> high=e->{if(e.player==p)e.value*=2;};Consumer<AbilityCalculationEvent.MaxCP> low=e->{if(e.player==p)e.value+=17;};NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,high);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,AbilityCalculationEvent.MaxCP.class,low);try{var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(5);s.learn("brain_course");s.learn("brain_course_advanced");s.learn("mind_course");h.assertTrue(s.maxCp()==18517&&s.maxOverload()==600,"actual registered NORMAL course listener lies between extension listeners");s.cp=0;s.cpDelay=0;s.tick();h.assertTrue(s.cp==ClassicPassiveSkills.cpRecovery(0,18517,1,1.2f),"native MindCourse multiplier reaches real CP recovery");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(high);NeoForge.EVENT_BUS.unregister(low);cleanup(p);}}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=20)
 public static void radiation_native_target_mark_float_multiplier_and_rejected_hit(GameTestHelper h){var p=player(h);try{var s=AbilityStorage.get(p);s.selectCategory("meltdowner");s.setLevel(1);s.learn("rad_intensify");var target=h.spawn(EntityType.ZOMBIE,new BlockPos(3,1,5));target.setNoAi(true);target.invulnerableTime=0;RadiationMarks.attack(p,target,1);h.assertValueEqual(target.getPersistentData().getInt("academy:md_mark_ticks"),60,"learned radiation applies source target mark");float rate=ClassicPassiveSkills.radiationRate(s.exp("rad_intensify"));h.assertTrue(target.getPersistentData().getFloat("academy:md_mark_rate")==rate,"source exact float NBT multiplier");float before=target.getHealth();target.invulnerableTime=0;target.hurt(target.damageSources().generic(),2);h.assertTrue(Math.abs((before-target.getHealth())-2*rate)<1e-5,"real later native LivingIncomingDamage effect");target.setInvulnerable(true);target.getPersistentData().putInt("academy:md_mark_ticks",0);p.getPersistentData().putInt("academy:md_mark_ticks",83);RadiationMarks.attack(p,target,1);h.assertValueEqual(target.getPersistentData().getInt("academy:md_mark_ticks"),83,"source marks rejected hit and takes caster mark duration");h.succeed();}finally{cleanup(p);}}
 @GameTest(template=TEMPLATE,batch="academy_passives",timeoutTicks=20)
 public static void threatening_native_critical_event_autolearning_and_damage(GameTestHelper h){var p=player(h);int[] fired={0};Consumer<FleshRipping.CriticalHitEvent> listener=e->{if(e.player==p)fired[0]++;};NeoForge.EVENT_BUS.addListener(FleshRipping.CriticalHitEvent.class,listener);try{var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(1);s.learn("threatening_teleport");s.learn("dim_folding_theorem");s.activated=true;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COBBLESTONE,3));var target=h.spawn(EntityType.ZOMBIE,new BlockPos(3,1,5));target.setNoAi(true);target.invulnerableTime=0;long seed=0;for(;seed<100000;seed++){h.getLevel().random.setSeed(seed);if(h.getLevel().random.nextFloat()<.1f)break;}h.assertTrue(seed<100000,"finite deterministic native critical seed");h.getLevel().random.setSeed(seed);h.assertTrue(ThreateningTeleport.perform(p),"real learned passive affects ThreateningTeleport");h.assertValueEqual(fired[0],1,"real common critical event emitted exactly once");h.assertTrue(s.learned("space_fluct")&&s.level==1&&s.exp("dim_folding_theorem")==.005f&&s.exp("space_fluct")==.0001f,"source critical awards auto-learn high-level passive");h.assertTrue(target.getHealth()<20&&p.getMainHandItem().getCount()==2&&p.getPersistentData().getBoolean("ac_teleporter_critical_attack"),"native damage/inventory/source achievement marker");h.succeed();}finally{NeoForge.EVENT_BUS.unregister(listener);cleanup(p);}}
}

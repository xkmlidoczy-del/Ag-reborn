/* Native transport fixtures: compiled only here; main owns server/client execution. GPLv3; see NOTICE. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.*;
import cn.academy.port.core.*;
import cn.academy.port.skill.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademySkillConsumptionRuntimeTests {
 private static final String TEMPLATE="shared_skill_consumption_empty";
 @SubscribeEvent public static void install(LevelEvent.Load e){if(!GameTestHooks.isGametestEnabled()||!(e.getLevel() instanceof ServerLevel world))return;var t=new CompoundTag();var size=new ListTag();size.add(IntTag.valueOf(16));size.add(IntTag.valueOf(8));size.add(IntTag.valueOf(16));t.put("size",size);var blocks=new ListTag();var cell=new CompoundTag();var pos=new ListTag();pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(0));cell.put("pos",pos);cell.putInt("state",0);blocks.add(cell);t.put("blocks",blocks);t.put("entities",new ListTag());var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);t.put("palette",palette);world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(world.registryAccess().lookupOrThrow(Registries.BLOCK),t);}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void actual_AbilityStorage_binding_mutates_before_insufficient_CP_and_trains_results(GameTestHelper h){var p=player(h);try{var s=ready(p,"electromaster","arc_gen");s.cp=1;var hook=new Hook(p,e->{h.assertTrue(e.skill.equals("arc_gen")&&!e.force&&!e.creative,"modern metadata");e.cp=0;e.overload=2;});with(hook,()->{h.assertTrue(s.consumeSkill("arc_gen",100,40,false),"real Forge hook makes cost affordable");near(h,s.cp,1,"zero cost");near(h,s.overload,2,"mutated overload");near(h,s.extraOverload,2*.0058,"mutated training");});h.succeed();}finally{clean(p);}}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void real_creative_and_force_paths_post_once_and_train_full_mutated_CP(GameTestHelper h){var p=player(h);try{var s=ready(p,"vecmanip","vec_deviation");var session=VecDeviationSession.begin(s,false);s.cp=1;int[] seen={0};var hook=new Hook(p,e->{seen[0]++;h.assertTrue(e.force&&!e.creative,"force emulation replaced by actual forced call");e.cp=200;});with(hook,()->session.affect(1,false));near(h,s.cp,0,"forced resource floor");near(h,s.extraCp,.5,"full mutated requested training");h.assertValueEqual(seen[0],1,"single force event");p.getAbilities().instabuild=true;s.cp=17;var creative=new Hook(p,e->{h.assertTrue(e.creative&&!e.force,"real creative metadata");e.cp=80;});with(creative,()->s.consumeSkill("vec_deviation",1000,0,true));near(h,s.cp,17,"creative no drain");near(h,s.extraCp,.7,"creative still trains mutated costs");p.getAbilities().instabuild=false;s.cp=1;with(new Hook(p,e->{h.assertFalse(e.creative,"initial diagnostic creative snapshot");p.getAbilities().instabuild=true;}),()->h.assertTrue(s.consumeSkill("vec_deviation",200,30,false),"actual player creative flag read after event"));near(h,s.cp,1,"live creative switch skips drain");near(h,s.extraCp,1.2,"live creative switch still trains");h.succeed();}finally{clean(p);}}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void real_overload_callback_observes_debit_and_delays_before_lock_and_training(GameTestHelper h){var p=player(h);try{var s=ready(p,"electromaster","arc_gen");int[] calls={0};var hook=new OverloadHook(p,()->{calls[0]++;h.assertTrue(s.overloadFine,"old overloadFine at callback");near(h,s.cp,7988,"CP already drained");h.assertValueEqual(s.cpDelay,15,"CP delay set");h.assertValueEqual(s.overloadDelay,32,"O delay set");near(h,s.extraCp,0,"training is later");});with(hook,()->s.consumeSkill("arc_gen",12,500,false));h.assertValueEqual(calls[0],1,"one overload event");h.assertFalse(s.overloadFine,"lock after callback");near(h,s.extraCp,.03,"training after callback");h.succeed();}finally{clean(p);}}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void constructor_callback_cannot_recursively_install_body_Holds(GameTestHelper h){var p=player(h);try{var s=ready(p,"electromaster","body_intensify");int[] calls={0};var hook=new Hook(p,e->{calls[0]++;h.assertFalse(BodyIntensify.start(p,2),"constructor admission blocked during callback");});with(hook,()->h.assertTrue(BodyIntensify.start(p,1),"single outer constructor installs"));h.assertValueEqual(calls[0],1,"no recursion");near(h,s.overload,200,"one startup overload");BodyIntensify.remove(p);h.succeed();}finally{clean(p);}}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void JetEngine_callback_abort_prevents_stale_flight_and_reentrant_release(GameTestHelper h){var p=player(h);try{var s=ready(p,"meltdowner","jet_engine");h.assertTrue(JetEngine.start(p,1),"real Hold starts");var hook=new Hook(p,e->{h.assertFalse(JetEngine.release(p,1),"nested release denied");JetEngine.abort(p,1);});double before=s.cp;with(hook,()->h.assertFalse(JetEngine.release(p,1),"disposed callback rejects outer release"));near(h,s.cp,before,"stale release no debit");h.assertFalse(JetEngine.active(p),"no stale Hold");h.assertFalse(JetEngine.triggering(p),"no flight");h.succeed();}finally{clean(p);}}
 @GameTest(template=TEMPLATE,batch="academy_shared_consumption",timeoutTicks=10)
 public static void callback_category_revoke_cannot_charge_old_skill_and_nonfinite_mutation_is_denied(GameTestHelper h){var p=player(h);try{var s=ready(p,"electromaster","arc_gen");with(new Hook(p,e->s.selectCategory("teleporter")),()->h.assertFalse(s.consumeSkill("arc_gen",20,2,false),"category revocation denies stale debit"));near(h,s.extraCp,0,"new category untrained");s.selectCategory("electromaster");s.setLevel(5);s.learn("arc_gen");s.activated=true;with(new Hook(p,e->e.cp=Float.NaN),()->h.assertFalse(s.consumeSkill("arc_gen",20,2,false),"unsafe event mutation denied"));h.assertTrue(Double.isFinite(s.cp)&&!s.consumptionInProgress(),"state and admission remain usable");h.succeed();}finally{clean(p);}}
 private static FakePlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Consumption]"));Vec3 at=h.absoluteVec(new Vec3(4.5,1,4.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setNoGravity(false);p.getAbilities().instabuild=false;return p;}
 private static AbilityProgress ready(ServerPlayer p,String category,String id){var s=AbilityStorage.get(p);s.selectCategory(category);s.setLevel(5);s.learn(id);s.activated=true;return s;}
 private static void clean(ServerPlayer p){BodyIntensify.remove(p);JetEngine.remove(p);AbilityStorage.remove(p);p.discard();}
 private static void with(Object hook,Runnable action){NeoForge.EVENT_BUS.register(hook);try{action.run();}finally{NeoForge.EVENT_BUS.unregister(hook);}}
 private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1E-5,why+" actual="+actual+" expected="+expected);}
 public static final class Hook{final ServerPlayer player;final Consumer<SkillPerformEvent> action;Hook(ServerPlayer p,Consumer<SkillPerformEvent> action){player=p;this.action=action;}@SubscribeEvent public void cost(SkillPerformEvent e){if(e.player==player)action.accept(e);}}
 public static final class OverloadHook{final ServerPlayer player;final Runnable action;OverloadHook(ServerPlayer p,Runnable action){player=p;this.action=action;}@SubscribeEvent public void overloaded(AbilityOverloadEvent e){if(e.player==player)action.run();}}
}

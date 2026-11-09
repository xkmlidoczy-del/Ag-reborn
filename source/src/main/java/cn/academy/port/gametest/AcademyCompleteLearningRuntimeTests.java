package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.stream.Collectors;

/** Exhaustive real menu ingress, finite development and source tier checks. Category,
 * level, prerequisites, machine/input energy are declared fixtures; targets are never granted. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyCompleteLearningRuntimeTests {
    private static final String TEMPLATE="complete_learning_empty";
    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new CompoundTag();var size=new ListTag();for(int value:new int[]{32,16,32})size.add(IntTag.valueOf(value));tag.put("size",size);
        var blocks=new ListTag();var first=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));first.put("pos",pos);first.putInt("state",0);blocks.add(first);tag.put("blocks",blocks);tag.put("entities",new ListTag());
        var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy",TEMPLATE)).load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch="academy_complete_learning",timeoutTicks=30)
    public static void every_source_active_skill_obeys_real_portable_menu_tier_and_finite_learning(GameTestHelper h){run(h,DeveloperType.PORTABLE);}
    @GameTest(template=TEMPLATE,batch="academy_complete_learning",timeoutTicks=30)
    public static void every_source_active_skill_obeys_real_normal_menu_tier_and_finite_learning(GameTestHelper h){run(h,DeveloperType.NORMAL);}
    @GameTest(template=TEMPLATE,batch="academy_complete_learning",timeoutTicks=30)
    public static void every_source_active_skill_learns_via_real_advanced_menu_with_finite_IF(GameTestHelper h){run(h,DeveloperType.ADVANCED);}

    private static void run(GameTestHelper h,DeveloperType tier) {
        var active=SkillCatalog.ALL.stream().filter(SkillCatalog.Skill::controllable).toList();
        h.assertValueEqual(active.size(),35,"classic controllable source catalog count");
        h.assertTrue(active.stream().map(SkillCatalog.Skill::id).collect(Collectors.toSet()).equals(PresetSkills.IMPLEMENTED),"advertised active registry exactly covers source catalog");
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-All-Learning]"));
        var at=h.absoluteVec(new Vec3(10.5,1,10.5));p.moveTo(at.x,at.y,at.z,0,0);p.getAbilities().instabuild=false;
        h.testInfo.addListener(new GameTestListener(){private void cleanup(){DevelopmentController.remove(p);MachineDeveloperSessions.close(p);AbilityStorage.remove(p);p.discard();}public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){cleanup();}});
        MachineDeveloperBlockEntity machine=null;
        if(tier!=DeveloperType.PORTABLE){
            var item=tier==DeveloperType.NORMAL?MachineDevelopers.NORMAL_ITEM.get():MachineDevelopers.ADVANCED_ITEM.get();var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);var base=h.absolutePos(new BlockPos(12,1,14));
            h.assertTrue(item.place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"real eight-cell "+tier+" fixture placement");
            machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);p.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);
        }
        for(var skill:active){
            DevelopmentController.remove(p);MachineDeveloperSessions.close(p);var state=AbilityStorage.get(p);state.selectCategory(skill.category());state.setLevel(skill.level());
            for(var req:skill.requirements()){state.learn(req.id());state.experience.put(req.id(),req.exp());}
            if(skill.anyLearnedSkillLevel()>0){var parent=SkillCatalog.ALL.stream().filter(s->s.category().equals(skill.category())&&s.level()==skill.anyLearnedSkillLevel()&&!s.id().equals(skill.id())).findFirst().orElseThrow();state.learn(parent.id());}
            h.assertFalse(state.learned(skill.id()),"target initially unlearned: "+skill.id());
            boolean accepted;double before;DeveloperItemEnergy portable=null;
            if(tier==DeveloperType.PORTABLE){
                var stack=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);portable=new DeveloperItemEnergy(stack,tier);portable.energy(tier.energy);before=portable.energy();
                h.assertTrue(AcademyCraft.DEVELOPER.get().use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"real portable screen entry");
                h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("learn",skill.id())),"actual portable wire dispatcher");accepted=DevelopmentController.process(p).isDeveloping();
            }else{
                machine.battery().load(tier.energy);before=machine.battery().getEnergy();h.assertTrue(machine.use(p),"real "+tier+" DeveloperMenu entry");var nonce=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();
                h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",UUID.randomUUID()+":"+skill.id())),"forged session cannot learn "+skill.id());
                accepted=MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_learn",nonce+":"+skill.id()));
            }
            h.assertTrue(accepted==tier.supportsSkill(skill.level()),"source tier gate for "+skill.id()+" on "+tier);
            if(!accepted){h.assertFalse(state.learned(skill.id()),"rejected tier grants no target");near(h,tier==DeveloperType.PORTABLE?portable.energy():machine.battery().getEnergy(),before,"rejected tier consumes no IF");continue;}
            int stim=ClassicRules.learningStimulations(skill.level()),ticks=stim*tier.ticksPerStimulation();
            for(int tick=1;tick<=ticks;tick++){DevelopmentController.tick(p,v->{});if(tick<ticks)h.assertFalse(state.learned(skill.id()),"source finite-tick gate for "+skill.id());}
            h.assertTrue(state.learned(skill.id())&&state.exp(skill.id())==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"real zero-mastery earning of "+skill.id());
            near(h,tier==DeveloperType.PORTABLE?portable.energy():machine.battery().getEnergy(),before-tier.actualConsumption(stim),"finite authentic IF for "+skill.id());
            h.assertTrue(state.presets.currentSkill(0).isEmpty(),"learning does not auto-bind");state.activated=true;
            h.assertTrue(state.presets.edit(0,0,skill.id(),id->PresetSkills.selectable(state,id)),"real earned preset eligibility for "+skill.id());
        }
        h.succeed();
    }
    private static void near(GameTestHelper h,double actual,double expected,String reason){h.assertTrue(Math.abs(actual-expected)<1E-4,reason+" actual="+actual+" expected="+expected);}
}

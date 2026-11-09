package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.machine.*;
import cn.academy.port.skill.FleshRipping;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** QA-only: the tested passive is learned exclusively by the real machine screen's confirm button.
 * World, prerequisite skills and finite initial resources are fixtures. Never calls attack/start/tick/effect receivers. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class GameplayAcceptancePassives {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> COURSES=Set.of("brain_course","brain_course_advanced","mind_course");
    private static final Set<String> PASSIVES=Set.of("rad_intensify","dim_folding_theorem","space_fluct");
    private static final class Fixture {
        final UUID owner; final String category; final AbilityProgress state;
        MachineDeveloperBlockEntity machine; String learning="", active="", recoveryStage="";
        double beforeIF,beforeMax,beforeOverload,cpBefore,controlRecovery,rateSum; int rateSamples;
        boolean measuring,recoveryPending,attributed,criticalProof; int tier=-1,criticalTier=-1;
        Husk target; BigDecimal poolBefore,controlLoss,markedLoss,criticalLoss; double raw,controlRaw;
        Fixture(ServerPlayer p,String category){owner=p.getUUID();this.category=category;state=AbilityStorage.get(p);}
    }
    private static Fixture fixture;
    private GameplayAcceptancePassives(){}

    /** Route: case "passives" -> { GameplayAcceptancePassives.run(mc,arg); wait=6; }. */
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"actual integrated player required");
        UUID owner=mc.player.getUUID();
        server.submit(()->{
            var p=server.getPlayerList().getPlayer(owner);require(p!=null,"actual owner online");
            if(action.startsWith("setup:"))setup(p,action.substring(6));
            else {
                var f=fixture;require(f!=null&&f.owner.equals(owner)&&AbilityStorage.get(p)==f.state,"same finite passive fixture");
                if(action.startsWith("learn:"))prepareLearning(p,action.substring(6));
                else if(action.equals("learned"))learned(p);
                else if(action.startsWith("recovery:"))recovery(p,action.substring(9));
                else if(action.startsWith("radiation:"))radiation(p,action.substring(10));
                else if(action.startsWith("critical:"))critical(p,action.substring(9));
                else throw new IllegalArgumentException(action);
            }
            return true;
        }).join();
    }

    private static void setup(ServerPlayer p,String key){
        if(fixture!=null&&fixture.target!=null)fixture.target.discard();
        String category=PASSIVES.contains(key)?(key.equals("rad_intensify")?"meltdowner":"teleporter"):key;
        require(DevelopmentActions.CATEGORIES.contains(category),"registered category fixture");
        DevelopmentController.remove(p);MachineDeveloperSessions.close(p);
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        var s=AbilityStorage.get(p);s.selectCategory(category);s.setLevel(5);s.activated=true;s.overloadFine=true;
        p.getPersistentData().remove(HighPowerCombat.DATA_KEY);require(HighPowerCombat.enroll(p,5),"ordinary finite player enrollment");
        fixture=new Fixture(p,category);var f=fixture;f.active=PASSIVES.contains(key)?key:"";
        var feet=p.blockPosition();
        for(int x=-3;x<=4;x++)for(int z=-2;z<=10;z++){
            p.serverLevel().setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<=4;y++)p.serverLevel().setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        // Prerequisites are fixtures; the record tested in this scenario must not be granted here.
        if(f.active.isEmpty())for(int level=3;level<=5;level++){
            final int rank=level;
            var prerequisite=SkillCatalog.ALL.stream().filter(a->a.category().equals(category)&&a.controllable()&&a.level()==rank).findFirst().orElseThrow();
            prerequisites(s,prerequisite,new HashSet<>(),true);
        } else {
            String attack=f.active.equals("rad_intensify")?"electron_bomb":"threatening_teleport";
            var entry=SkillCatalog.find(category,attack).orElseThrow();prerequisites(s,entry,new HashSet<>(),true);
            s.experience.put(attack,.5);s.presets.edit(0,2,attack,s::learned);
            p.getInventory().setItem(0,new ItemStack(Items.COBBLESTONE,64));p.getInventory().setItem(1,new ItemStack(Items.COBBLESTONE,64));
            var target=EntityType.HUSK.create(p.serverLevel());require(target!=null,"real ordinary hostile target");
            target.setNoAi(true);target.setSilent(true);target.setPos(p.getX(),p.getY(),p.getZ()+6);
            require(p.serverLevel().addFreshEntity(target)&&HighPowerCombat.enroll(target,5),"durable normal-gravity native target");f.target=target;
        }
        // Explicitly exclude target passive/course grants from setup, including incidental prerequisite chains.
        for(String id:COURSES)require(!s.learned(id),"training course must be genuinely unlearned at setup");
        if(!f.active.isEmpty())for(String id:PASSIVES)if(SkillCatalog.find(category,id).isPresent())s.unlearn(id);
        var origin=feet.offset(2,0,-1);var block=MachineDevelopers.ADVANCED.get();
        for(int part=0;part<8;part++)p.serverLevel().setBlock(origin.offset(MachineDeveloperBlock.offset(part,Direction.NORTH)),
                block.defaultBlockState().setValue(MachineDeveloperBlock.FACING,Direction.NORTH).setValue(MachineDeveloperBlock.PART,part),3);
        f.machine=(MachineDeveloperBlockEntity)p.serverLevel().getBlockEntity(origin);
        require(f.machine!=null&&f.machine.available(),"complete reachable eight-cell advanced developer");
        f.machine.battery().energy(DeveloperType.ADVANCED.energy); // Once per category/passive, before learning baseline.
        s.cp=s.maxCp();s.overload=0;s.overloadFine=true;
        p.inventoryMenu.broadcastChanges();AbilityStorage.save(p);AcademyNetwork.sync(p);
        LOG.info("QA_PASSIVE_SETUP category={} passive={} maxCp={} maxOverload={} finiteMachineIF={} fixtureOnly=true",category,f.active,s.maxCp(),s.maxOverload(),f.machine.battery().getEnergy());
    }

    private static void prerequisites(AbilityProgress s,SkillCatalog.Skill skill,Set<String> visited,boolean include){
        if(!visited.add(skill.id()))return;
        for(var need:SkillCatalog.requirements(skill)){
            var parent=SkillCatalog.find(s.category,need.id()).orElseThrow();
            prerequisites(s,parent,visited,true);
            s.experience.put(need.id(),Math.max(s.exp(need.id()),need.exp()));
        }
        if(include&&!COURSES.contains(skill.id())&&!PASSIVES.contains(skill.id()))s.learn(skill.id());
    }

    private static void prepareLearning(ServerPlayer p,String id){
        var f=fixture;var s=f.state;require(COURSES.contains(id)||PASSIVES.contains(id),"only audited non-cast records");
        require(!s.learned(id),"tested record has not been granted");
        var entry=SkillCatalog.find(f.category,id).orElseThrow();
        // Prerequisites may include other classic passives; tested record itself is never granted.
        for(var need:SkillCatalog.requirements(entry)){
            if(!s.learned(need.id()))require(!COURSES.contains(need.id()),"earlier training must have been learned through its own real machine operation");
            prerequisites(s,SkillCatalog.find(f.category,need.id()).orElseThrow(),new HashSet<>(),true);
            if(!s.learned(need.id())){
                s.learn(need.id());
            }
            s.experience.put(need.id(),Math.max(s.exp(need.id()),need.exp()));
        }
        require(!s.learned(id),"prerequisite setup did not grant the tested record");
        require(DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,entry),"actual machine tier, level and prerequisites permit learning");
        f.learning=id;f.beforeIF=f.machine.battery().getEnergy();f.beforeMax=s.maxCp();f.beforeOverload=s.maxOverload();
        MachineDeveloperSessions.close(p);AbilityStorage.save(p);AcademyNetwork.sync(p);
        require(f.machine.use(p),"real machine use opens the production developer screen");
        LOG.info("QA_PASSIVE_LEARNING_READY category={} skill={} beforeIF={} maxCp={} maxOverload={} granted=false",f.category,id,f.beforeIF,f.beforeMax,f.beforeOverload);
    }

    private static void learned(ServerPlayer p){
        var f=fixture;var s=f.state;String id=f.learning;require(!id.isEmpty()&&s.learned(id),"production development actually learned the record");
        var entry=SkillCatalog.find(f.category,id).orElseThrow();double spent=f.beforeIF-f.machine.battery().getEnergy();
        near(spent,DeveloperType.ADVANCED.actualConsumption(ClassicRules.learningStimulations(entry.level())),1e-6,"real finite machine IF consumption");
        if(id.equals("brain_course"))near(s.maxCp()-f.beforeMax,1000,1e-6,"actual maximum CP increase");
        if(id.equals("brain_course_advanced")){
            near(s.maxCp()-f.beforeMax,1500,1e-6,"actual stacked advanced CP capacity");near(s.maxOverload()-f.beforeOverload,100,1e-6,"actual overload capacity");
        }
        LOG.info("QA_PASSIVE_LEARNING_PASS category={} skill={} realMachineIFSpent={} beforeMaxCp={} afterMaxCp={} beforeMaxOverload={} afterMaxOverload={} learnedViaNormalUi=true",
                f.category,id,spent,f.beforeMax,s.maxCp(),f.beforeOverload,s.maxOverload());
        if(id.equals("brain_course")||id.equals("brain_course_advanced"))
            LOG.info("QA_PASSIVE_EFFECT_PASS category={} skill={} witness=actual_native_capacity maxCp={} maxOverload={}",f.category,id,s.maxCp(),s.maxOverload());
    }

    private static void recovery(ServerPlayer p,String stage){
        var f=fixture;
        if(stage.equals("control")||stage.equals("learned")){
            require(f.state.learned("mind_course")==stage.equals("learned"),"real learning transition precedes recovery comparison");
            f.state.cp=f.state.maxCp()*.25;f.state.cpDelay=0; // Matched initial wallet deficit, never injected recovery.
            f.recoveryStage=stage;f.rateSamples=0;f.rateSum=0;f.measuring=true;f.recoveryPending=false;
        } else if(stage.equals("assert")){
            f.measuring=false;require(f.rateSamples>=12,"at least twelve actual server ticks observed");double actual=f.rateSum/f.rateSamples;
            if(f.recoveryStage.equals("control")){f.controlRecovery=actual;near(actual,1,.005,"unlearned real CP recovery control");}
            else {near(actual/f.controlRecovery,1.2,.005,"actual learned/control recovery ratio");
                LOG.info("QA_PASSIVE_EFFECT_PASS category={} skill=mind_course witness=real_player_tick_recovery samples={} controlNormalized={} learnedNormalized={} actualRatio={} noRecoveryTickCalled=true",f.category,f.rateSamples,f.controlRecovery,actual,actual/f.controlRecovery);}
            LOG.info("QA_PASSIVE_RECOVERY_SAMPLE category={} stage={} samples={} normalized={}",f.category,f.recoveryStage,f.rateSamples,actual);
        } else throw new IllegalArgumentException(stage);
    }

    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){
        var f=fixture;if(f==null||!f.measuring)return;var p=event.getServer().getPlayerList().getPlayer(f.owner);
        if(p==null||AbilityStorage.get(p)!=f.state)return;
        f.cpBefore=f.state.cp;f.recoveryPending=f.state.cpDelay==0&&f.cpBefore<f.state.maxCp()*.8;
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post event){
        var f=fixture;if(f==null||!f.measuring||!f.recoveryPending)return;f.recoveryPending=false;
        double base=f.state.baseCp(),raw=f.state.cpDataConfig().cpRecovery*.0003*base*(1+f.cpBefore/base);
        if(raw>0){f.rateSum+=(f.state.cp-f.cpBefore)/raw;f.rateSamples++;}
    }

    private static void baselineDamage(){var f=fixture;require(f.target!=null&&f.target.isAlive(),"real target survives");f.poolBefore=pool(f.target);f.attributed=false;f.tier=-1;f.raw=0;}
    private static BigDecimal damage(){var f=fixture;var loss=f.poolBefore.subtract(pool(f.target));require(loss.signum()>0&&f.attributed&&f.raw>0,"actual player-attributed native damage and persistent pool loss");return loss;}
    private static void radiation(ServerPlayer p,String action){
        var f=fixture;require(f.active.equals("rad_intensify"),"radiation fixture");
        if(action.equals("baseline")){baselineDamage();return;}
        var loss=damage();
        if(action.equals("control")){
            require(!f.state.learned("rad_intensify")&&f.target.getPersistentData().getInt("academy:md_mark_ticks")==0,"unlearned attack cannot mark target");f.controlLoss=loss;
        } else if(action.equals("marked")){
            require(f.state.learned("rad_intensify")&&f.target.getPersistentData().getInt("academy:md_mark_ticks")>0&&f.target.getPersistentData().getFloat("academy:md_mark_rate")>1,"real learned attack marked actual target");f.markedLoss=loss;
        } else if(action.equals("boosted")){
            require(f.markedLoss!=null&&loss.compareTo(f.markedLoss.multiply(new BigDecimal("1.2")))>0,"subsequent real atomic attack gains actual radiation damage");
            LOG.info("QA_PASSIVE_EFFECT_PASS category=meltdowner skill=rad_intensify witness=actual_mark_and_followup_damage controlLoss={} markingLoss={} boostedLoss={} markTicks={} markRate={}",f.controlLoss,f.markedLoss,loss,f.target.getPersistentData().getInt("academy:md_mark_ticks"),f.target.getPersistentData().getFloat("academy:md_mark_rate"));
        } else throw new IllegalArgumentException(action);
        LOG.info("QA_PASSIVE_RADIATION_SAMPLE stage={} actualLoss={}",action,loss);
    }

    private static void critical(ServerPlayer p,String action){
        var f=fixture;require(f.active.equals("dim_folding_theorem")||f.active.equals("space_fluct"),"critical passive fixture");
        if(action.equals("baseline")){baselineDamage();return;}
        if(action.equals("control")){require(!f.state.learned("dim_folding_theorem")&&!f.state.learned("space_fluct")&&f.tier==-1,"both passives absent in actual control attack");f.controlLoss=damage();f.controlRaw=f.raw;return;}
        if(action.equals("sample")){
            require(f.state.learned(f.active)&&f.learning.equals(f.active),"tested passive came through its own real machine learning step");
            var loss=damage();int required=f.active.equals("space_fluct")?1:0;
            if(f.tier>=required&&loss.compareTo(f.controlLoss.multiply(new BigDecimal("1.1")))>0&&f.raw>f.controlRaw*1.1){f.criticalProof=true;f.criticalTier=f.tier;f.criticalLoss=loss;}
            LOG.info("QA_PASSIVE_CRITICAL_SAMPLE skill={} tier={} raw={} actualLoss={} qualifying={} finiteMaterialsRemaining={}",f.active,f.tier,f.raw,loss,f.tier>=required,p.getInventory().countItem(Items.COBBLESTONE));return;
        }
        if(action.equals("assert")){
            require(f.criticalProof,"bounded real casts must actually produce the required critical tier; random absence is not a pass");
            LOG.info("QA_PASSIVE_EFFECT_PASS category=teleporter skill={} witness=actual_random_critical_native_damage observedTier={} controlLoss={} criticalLoss={} noSeedOrCriticalRollOverride=true",f.active,f.criticalTier,f.controlLoss,f.criticalLoss);return;
        }
        throw new IllegalArgumentException(action);
    }
    @SubscribeEvent public static void criticalEvent(FleshRipping.CriticalHitEvent event){var f=fixture;if(f!=null&&event.player.getUUID().equals(f.owner)&&event.target==f.target)f.tier=event.tier;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void raw(SkillAttackEvent event){var f=fixture;if(f!=null&&event.player.getUUID().equals(f.owner)&&event.target==f.target)f.raw=event.amount;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingDamageEvent.Post event){var f=fixture;if(f!=null&&event.getEntity()==f.target&&event.getNewDamage()>0&&event.getSource().getEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner))f.attributed=true;}
    private static BigDecimal pool(Husk target){return new BigDecimal(HighPowerCombat.healthText(target)).add(HighPowerCombat.shield(target));}
    private static void near(double actual,double expected,double epsilon,String why){require(Double.isFinite(actual)&&Math.abs(actual-expected)<=epsilon,why+": "+actual+" vs "+expected);}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalStateException("QA_PASSIVE_FAILED "+why);}
}

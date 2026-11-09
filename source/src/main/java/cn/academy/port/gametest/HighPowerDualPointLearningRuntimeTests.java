package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native developer work; finite batteries and accelerated development clocks are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDualPointLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerDualPointLearningRuntimeTests(){}

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void fresh_meltdowner_root_pays_all_native_ticks_before_opt_in_and_binding(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var battery=f.portable(p,2340);
        h.assertTrue(s.level==1&&s.experience.isEmpty()&&!HighPowerCombat.enabled(p),"native MD acquisition alone grants no learned skills or high-power enrollment");
        h.assertFalse(wire(p,"preset_edit","0:3:dual_point"),"unlearned NEW211 cannot bind");
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.DUAL_POINT,Integer.MAX_VALUE);
        h.assertTrue(wire(p,"hp_learn",request.wire()),"canonical correlated MD root request starts real finite developer work");
        f.develop(p,77);
        h.assertTrue(!s.learned(HighPowerSkills.DUAL_POINT)&&!HighPowerCombat.enabled(p)&&battery.energy()==30,"no learned bit, HP ledger or refill before the final paid tick");
        f.develop(p,1);
        h.assertTrue(s.learned(HighPowerSkills.DUAL_POINT)&&HighPowerCombat.enabled(p)&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"three native stimulations cost exactly 78 times 30 IF and enroll once");
        h.assertTrue(HighPowerResources.cp(p)>0&&s.exp(HighPowerSkills.DUAL_POINT)==0&&s.levelExperience==0,"first genuine opt-in initializes shared CP without free mastery or progress");
        h.assertTrue(wire(p,"preset_edit","0:3:dual_point")&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,HighPowerSkills.DUAL_POINT),"ordinary existing slot binds the genuinely learned MD root");
        var saved=AbilityStorage.encodeSaved(s);var restored=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        h.assertTrue(restored.learned(HighPowerSkills.DUAL_POINT)&&restored.presets.currentSkill(3).equals(HighPowerSkills.DUAL_POINT)&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW211 roundtrips unchanged native ability save and preset schema");
        h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.DUAL_POINT),"already learned root cannot be purchased again");
        h.assertTrue(battery.energy()==0,"duplicate rejected before spending any IF");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void native_category_switch_clears_skills_but_paid_relearning_preserves_shared_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");
        f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"EM root uses real initial enrollment");f.develop(p,78);
        h.assertTrue(wire(p,"preset_edit","0:0:forked_arc"),"initial EM preset fixture uses normal editor");
        s.addExperience(HighPowerSkills.FORKED_ARC,.125);
        double retainedXp=s.levelExperience;
        h.assertTrue(retainedXp>0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(BigDecimal.ONE)>0,"real positive EM XP certifies nonzero progress before the denominator-change probe");
        HighPowerCombat.spendShield(p,new BigDecimal("1000000"));
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putDouble("cp",37);
        long deadline=p.serverLevel().getGameTime()+500;resources.putLong("next_cast",deadline);resources.putLong("next_dual_point",deadline);
        p.getPersistentData().put(HighPowerResources.DATA_KEY,resources);
        String hp=HighPowerCombat.healthText(p);var shield=HighPowerCombat.shield(p);var position=HighPowerCombat.earnedGrowthPosition(p);
        var activePosition=HighPowerGrowth.values(p).position();
        s.extraCp=2.5;s.extraOverload=1.25;
        for(String category:List.of("meltdowner","electromaster","meltdowner")){
            s.changeCategoryClassic(category);
            h.assertTrue(s.level==1&&s.levelExperience==retainedXp&&s.extraCp==2.5&&s.extraOverload==1.25&&s.experience.isEmpty(),"native category change retains real level/progress/training and clears learned skills");
            for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"native category change clears every preset mapping");
            String root=category.equals("meltdowner")?HighPowerSkills.DUAL_POINT:HighPowerSkills.FORKED_ARC;
            h.assertFalse(HighPowerSkills.eligible(s,root),"returning category does not restore learned root");
            var battery=f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,root),"new category root requires normal finite relearning");f.develop(p,78);
            h.assertTrue(s.learned(root)&&battery.energy()==0,"each actual relearning consumes 2340 IF");
            h.assertTrue(HighPowerCombat.healthText(p).equals(hp)&&HighPowerCombat.shield(p).equals(shield)&&HighPowerCombat.earnedGrowthPosition(p).compareTo(position)==0,"switch/relearn cannot re-enroll or refill exact balances or certify free growth");
            h.assertTrue(HighPowerGrowth.values(p).position().compareTo(activePosition)==0,"category denominator changes preserve the earned active coordinate through paid relearning");
            h.assertTrue(HighPowerResources.cp(p)==37&&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_cast")==deadline&&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_dual_point")==deadline,"shared CP and persistent cooldown deadlines survive switches and relearning");
            h.assertTrue(wire(p,"preset_edit","0:0:"+root),"genuinely relearned root can be rebound after native clear");
        }
        s.changeCategoryClassic("unsupported_test");
        h.assertFalse(HighPowerResources.ready(p)||HighPowerSkills.eligible(s,HighPowerSkills.DUAL_POINT),"unadmitted category cannot use shared resources or stale MD skill");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void tiny_actual_post_switch_awards_never_import_retained_native_progress(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");
        f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"real initial EM root learning");f.develop(p,78);
        s.addExperience(HighPowerSkills.FORKED_ARC,ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(s))*.3);
        var active=HighPowerGrowth.values(p).position();var earned=HighPowerCombat.earnedGrowthPosition(p);
        h.assertTrue(active.compareTo(BigDecimal.ONE)>0&&active.compareTo(new BigDecimal("1.31"))<0,"initial active coordinate comes from genuine EM XP");
        for(String category:List.of("meltdowner","electromaster","meltdowner","electromaster")){
            double nativeBeforeSwitch=s.levelExperience;
            s.changeCategoryClassic(category);
            String root=HighPowerCategories.recoveryRoot(category);
            f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,root),"each switched root requires real finite relearning");f.develop(p,78);
            h.assertTrue(s.levelExperience==nativeBeforeSwitch&&HighPowerGrowth.values(p).position().compareTo(active)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(earned)==0,"switch and relearn preserve native XP and both shared coordinates");
            s.addExperience(root,Double.MIN_VALUE);
            h.assertTrue(s.levelExperience==nativeBeforeSwitch&&HighPowerGrowth.values(p).position().compareTo(active)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(earned)==0,"a nominally positive double rounded to zero native float XP earns no coordinate change");
            double threshold=ClassicRules.levelThreshold(s.level,SkillCatalog.levelSkillCount(s));
            s.addExperience(root,.000001);
            double actualDelta=s.levelExperience-nativeBeforeSwitch;
            var next=HighPowerGrowth.values(p).position();var increment=next.subtract(active);
            h.assertTrue(actualDelta>0&&increment.signum()>0&&increment.compareTo(new BigDecimal("0.00001"))<0,"tiny actual post-switch XP grants only a tiny advance with no retained-progress catch-up");
            var normalizedBefore=BigDecimal.valueOf((float)nativeBeforeSwitch/(float)threshold);
            var normalizedAfter=BigDecimal.valueOf((float)s.levelExperience/(float)threshold);
            h.assertTrue(increment.compareTo(normalizedAfter.subtract(normalizedBefore))==0,"growth telescopes the actual native float quotient delta without importing the retained fraction");
            h.assertTrue(HighPowerCombat.earnedGrowthPosition(p).compareTo(next)==0,"fresh high-water growth follows the same actual delta");
            active=next;earned=HighPowerCombat.earnedGrowthPosition(p);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void paid_level_with_retained_native_threshold_never_catches_up_q_and_rollback_relogin_never_refill(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");
        f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"real EM enrollment before retained-progress boundary probe");f.develop(p,78);
        s.addExperience(HighPowerSkills.FORKED_ARC,ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(s))*.6);
        h.assertTrue(HighPowerCombat.spendShield(p,BigDecimal.ONE),"one actual shield debit before leaving EM");
        var originalQ=HighPowerGrowth.values(p).position();
        h.assertTrue(originalQ.compareTo(new BigDecimal("1.59"))>0&&originalQ.compareTo(new BigDecimal("1.61"))<0,"EM actual awards establish a continuous coordinate below LV2");
        s.changeCategoryClassic("meltdowner");
        f.portable(p,2340);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.DUAL_POINT),"MD root requires finite paid learning");f.develop(p,78);
        h.assertTrue(s.levelProgress(SkillCatalog.levelSkillCount(s))==1&&HighPowerGrowth.values(p).position().compareTo(originalQ)==0,"native retained XP satisfies the smaller MD threshold without raising active q");
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putDouble("cp",37);
        String hp=HighPowerCombat.healthText(p),maximum=HighPowerCombat.maxHealthText(p);var shield=HighPowerCombat.shield(p);
        var battery=f.portable(p,7800);h.assertTrue(DevelopmentController.startLevel(p),"native threshold authorizes the ordinary finite level action");f.develop(p,259);
        h.assertTrue(s.level==1&&HighPowerCombat.earnedLevel(p)==1&&battery.energy()==30,"real integer level remains locked before final paid stimulation tick");f.develop(p,1);
        h.assertTrue(s.level==2&&HighPowerCombat.earnedLevel(p)==2&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"ten native portable stimulations certify LV2 only after exactly 7800 IF");
        h.assertTrue(HighPowerGrowth.values(p).position().compareTo(originalQ)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(originalQ)==0,"paid level widens the future ceiling and preserves lagging q/Q continuously");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p))&&maximum.equals(HighPowerCombat.maxHealthText(p))&&shield.equals(HighPowerCombat.shield(p))&&HighPowerResources.cp(p)==37,"paid level grants no exact HP/shield/CP or retained-progress capacity catch-up");
        s.addExperience(HighPowerSkills.DUAL_POINT,ClassicRules.levelThreshold(2,SkillCatalog.levelSkillCount(s))*.1);
        var earned=HighPowerCombat.earnedGrowthPosition(p);double cpMaximum=HighPowerGrowth.values(p).cpMaximum();
        h.assertTrue(earned.compareTo(originalQ)>0&&earned.compareTo(BigDecimal.TWO)<0,"actual new LV2 XP advances the lagging coordinate without jumping to its integer unlock");
        s.setLevel(1);
        h.assertTrue(HighPowerGrowth.values(p).position().compareTo(BigDecimal.ONE)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(earned)==0,"raw rollback clamps active q to the lower level and retains earned capacity Q");
        s.setLevel(2);
        h.assertTrue(HighPowerGrowth.values(p).position().compareTo(BigDecimal.ONE)==0,"raw level increase cannot recover the discarded active fraction");
        var restored=f.reload(p);var loaded=AbilityStorage.get(restored);
        h.assertTrue(loaded.level==2&&HighPowerGrowth.values(restored).position().compareTo(BigDecimal.ONE)==0&&HighPowerCombat.earnedGrowthPosition(restored).compareTo(earned)==0,"actual native entity save/load preserves lagging active q and global earned Q");
        h.assertTrue(HighPowerGrowth.values(restored).cpMaximum()==cpMaximum&&HighPowerResources.cp(restored)==37&&hp.equals(HighPowerCombat.healthText(restored))&&shield.equals(HighPowerCombat.shield(restored)),"reload never refills balances or shrinks previously earned CP capacity");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void finite_shortage_and_late_category_level_config_changes_never_enroll_or_refund(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var shortBattery=f.portable(p,2339);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.DUAL_POINT),"server-owned finite battery starts real root work");f.develop(p,78);
        h.assertTrue(shortBattery.energy()==0&&!s.learned(HighPowerSkills.DUAL_POINT)&&!HighPowerCombat.enabled(p)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"one-IF shortage consumes only available energy and grants nothing");
        for(String invalidation:List.of("category","level","config")){
            s.changeCategoryClassic("meltdowner");s.setLevel(1);var battery=f.portable(p,2340);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.DUAL_POINT),"valid MD root work starts before late "+invalidation);f.develop(p,77);
            try{
                switch(invalidation){
                    case "category"->s.changeCategoryClassic("electromaster");
                    case "level"->s.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.DUAL_POINT),new ClassicSkillConfiguration.Flags(false,false)));
                }
                f.develop(p,1);
                h.assertTrue(battery.energy()==0&&!s.learned(HighPowerSkills.DUAL_POINT)&&!HighPowerCombat.enabled(p)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final tick revalidates "+invalidation+" without learning, enrollment or refund");
            }finally{SkillCatalog.resetConfiguration();}
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_dual_point_learning",timeoutTicks=15)
    public static void legacy_ledger_approval_and_existing_missing_budget_never_reinitialize_on_learning(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var battery=f.portable(p,2340);h.assertTrue(HighPowerCombat.enroll(p,1),"declared older ledger fixture");
        p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).remove("adapterContract");
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.DUAL_POINT,1).wire()),"unapproved old ledger rejects MD learning before IF");
        h.assertTrue(battery.energy()==2340&&!s.learned(HighPowerSkills.DUAL_POINT),"early compatibility rejection leaves finite battery untouched");
        h.assertTrue(HighPowerCombat.approveVanillaContract(p),"explicit fixture approval retains the old ledger");
        var before=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
        h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.DUAL_POINT),"approved existing ledger may learn MD root");f.develop(p,78);
        h.assertTrue(s.learned(HighPowerSkills.DUAL_POINT)&&battery.energy()==0&&p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).equals(before),"existing exact ledger stays byte-for-byte unchanged after paid learning");
        h.assertTrue(!p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).contains("cp")&&HighPowerResources.cp(p)==0,"an existing ledger with no CP field does not trigger first-enrollment budget initialization");h.succeed();
    }

    private static boolean wire(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DualLearning"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer player,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer player,int ticks){for(int tick=0;tick<ticks;tick++)DevelopmentController.tick(player,ignored->{});}
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[DualReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

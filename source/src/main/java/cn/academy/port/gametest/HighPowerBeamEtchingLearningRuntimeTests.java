package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
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

/** NEW213 native finite learning, exact shared-ledger preservation and existing preset/save contracts.
 * Batteries, injury, EM-only shield debits and accelerated developer clocks are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerBeamEtchingLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerBeamEtchingLearningRuntimeTests(){}

    @GameTest(template=TEMPLATE,batch="highpower_beam_etching_learning",timeoutTicks=15)
    public static void paid_native_learning_requires_dual_point_and_never_refills_shared_resources(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.BEAM_ETCHING).orElseThrow();
        h.assertTrue(skill.level()==1&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DUAL_POINT,0)))&&HighPowerSkills.BEAM_ETCHING_PROPOSAL==213,"NEW213 has the canonical MD LV1 registration and exact learned-root prerequisite");
        h.assertTrue(SkillCatalog.ALL.size()==50&&cn.academy.port.preset.PresetSkills.IMPLEMENTED.size()==35&&HighPowerSkills.ALL.size() == 61 && cn.academy.port.highpower.HighPowerSkills.ALL.get(51).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "scatter_locator_plate", 2, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.SCATTER_LOCATOR_PLATE_PROPOSAL == 223 && cn.academy.port.highpower.HighPowerSkills.ALL.get(52).equals(new cn.academy.port.SkillCatalog.Skill("vecmanip", "autonomous_vector_circle", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("multi_axis_reflection", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL == 452 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).id().equals("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(46).id().equals("vector_cage") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))&&SkillCatalog.levelSkillCount(s)==1
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),"thirty-six additions retain NEW333/331/343/342/353 ordinals and append NEW341 while preserving every classic catalog and MD LV1 XP denominator");
        h.assertTrue(SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("meltdowner",skill.id()))&&AcademyConfig.defaultSkillEnabled(ClassicSkillConfiguration.key("meltdowner",skill.id()))&&AcademyConfig.defaultSkillEnabled(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP)),"NEW213 and graduated NEW132 each receive their normal default-on config");
        var blocked=f.portable(p,2340);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),1).wire()),"fresh MD cannot skip learned NEW211");
        h.assertFalse(wire(p,"preset_edit","0:3:"+skill.id()),"unlearned NEW213 cannot bind");
        h.assertTrue(blocked.energy()==2340&&!HighPowerCombat.enabled(p),"missing prerequisite spends no IF and creates no ledger");
        f.learn(p,HighPowerSkills.DUAL_POINT);s.addExperience(HighPowerSkills.DUAL_POINT,.125);f.learn(p,HighPowerSkills.SCATTER_PLATES);
        f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);HighPowerGrowth.values(p);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
        double xp=s.levelExperience;
        var battery=f.portable(p,2340);
        h.assertTrue(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE).wire()),"canonical bounded request starts real finite NEW213 development");
        f.develop(p,77);
        h.assertTrue(!s.learned(skill.id())&&battery.energy()==30&&combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)),"no early skill grant or shared ledger mutation before the final paid tick");
        f.develop(p,1);
        h.assertTrue(s.learned(skill.id())&&s.exp(skill.id())==0&&s.levelExperience==xp&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"NEW213 pays exactly 78 ticks and 2340 IF without mastery or level XP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"NEW213 learning changes no shared HP, shield, independent plate shares, q/Q, CP or cooldowns");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"+skill.id()),"NEW213 binds each ordinary existing slot");
            h.assertFalse(wire(p,"preset_edit",preset+":"+((slot+1)%4)+":"+skill.id()),"ordinary duplicate-per-preset mappings stay forbidden");
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"),"clear tested binding before using another slot");
        }
        h.assertTrue(wire(p,"preset_edit","3:3:"+skill.id()),"retain a genuine NEW213 save binding");
        h.assertTrue(wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,skill.id()),"genuine paid skill is usable under ordinary activation");
        var saved=AbilityStorage.encodeSaved(s);var restored=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        h.assertTrue(saved.getInt("schema")==3&&restored.learned(skill.id())&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW213 uses the unchanged native save and four-by-four preset schema");
        h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"duplicate learned NEW213 cannot start a second purchase");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"binding and duplicate rejection preserve all exact shared ledgers");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_beam_etching_learning",timeoutTicks=15)
    public static void category_switch_paid_relearning_and_native_reload_preserve_depleted_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        f.learn(p,HighPowerSkills.DUAL_POINT);s.addExperience(HighPowerSkills.DUAL_POINT,.125);f.learn(p,HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.BEAM_ETCHING);
        var plate=ScatterPlateStorage.read(p).orElseThrow();f.spendShield(p,plate.plate(0).current().add(BigDecimal.ONE));f.injure(p);f.cp(p,37);
        long deadline=p.serverLevel().getGameTime()+500;var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putLong("next_beam_etching",deadline);resources.putLong("next_dual_point",deadline);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var budget=resources.copy();double xp=s.levelExperience;
        h.assertTrue(wire(p,"preset_edit","3:2:scatter_plates")&&wire(p,"preset_edit","3:3:beam_etching"),"old and new MD additions coexist in native presets");
        for(String category:List.of("electromaster","meltdowner","teleporter","meltdowner")){
            s.changeCategoryClassic(category);
            h.assertTrue(s.experience.isEmpty()&&s.levelExperience==xp,"native category switch retains XP and clears learned skills");
            for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"category switch clears each ordinary preset slot");
            if(category.equals("electromaster"))f.learn(p,HighPowerSkills.FORKED_ARC);
            if(category.equals("meltdowner")){
                var missingParent=f.portable(p,2340);h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.BEAM_ETCHING),"returning to MD cannot bypass NEW211 relearning");
                h.assertTrue(missingParent.energy()==2340,"missing parent rejection consumes no IF");
                f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.BEAM_ETCHING);
                h.assertTrue(wire(p,"preset_edit","3:3:beam_etching"),"paid relearned NEW213 can use its old ordinary slot");
            }
            h.assertTrue(stableLedger(combat).equals(stableLedger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"switch and paid relearning never refill, rebalance or advance existing exact ledgers");
        }
        var loaded=f.reload(p);var restored=AbilityStorage.get(loaded);
        h.assertTrue(restored.learned(HighPowerSkills.BEAM_ETCHING)&&restored.presets.skill(3,3).equals(HighPowerSkills.BEAM_ETCHING),"actual native entity save/load preserves canonical learning and binding");
        h.assertTrue(stableLedger(combat).equals(stableLedger(loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(loaded.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"actual reload preserves injury, all independent shields, q/Q, depleted CP and cooldown deadlines");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_beam_etching_learning",timeoutTicks=15)
    public static void shortage_and_late_category_level_config_parent_changes_never_grant_or_refill(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","category","level","config","parent")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");f.learn(p,HighPowerSkills.DUAL_POINT);f.cp(p,37);f.injure(p);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            var battery=f.portable(p,invalidation.equals("battery")?2339:2340);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.BEAM_ETCHING),"valid NEW213 action starts before late "+invalidation);f.develop(p,77);
            try{
                switch(invalidation){
                    case "category"->s.changeCategoryClassic("electromaster");
                    case "level"->s.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.BEAM_ETCHING),new ClassicSkillConfiguration.Flags(false,false)));
                    case "parent"->s.unlearn(HighPowerSkills.DUAL_POINT);
                }
                f.develop(p,1);
                h.assertTrue(battery.energy()==0&&!s.learned(HighPowerSkills.BEAM_ETCHING)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final paid tick revalidates "+invalidation+" with no learning or refund");
                h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"failed NEW213 completion cannot repair/refill balances or initialize plate shares");
            }finally{SkillCatalog.resetConfiguration();}
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_beam_etching_learning",timeoutTicks=15)
    public static void config_disable_and_missing_existing_budget_fail_closed_without_erasing_saved_learning(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.BEAM_ETCHING);
        h.assertTrue(wire(p,"preset_edit","3:3:beam_etching")&&wire(p,"activate_state","true"),"normal native binding and activation");
        var saved=AbilityStorage.encodeSaved(s);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.BEAM_ETCHING),new ClassicSkillConfiguration.Flags(false,false)));
            h.assertTrue(!HighPowerSkills.eligible(s,HighPowerSkills.BEAM_ETCHING)&&AbilityStorage.encodeSaved(s).equals(saved),"live config disable gates use without deleting learned state or mappings");
            h.assertFalse(wire(p,"preset_edit","0:0:beam_etching"),"disabled skill cannot create a new binding");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            h.assertTrue(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("meltdowner",HighPowerSkills.BEAM_ETCHING),"NEW213 uses the existing bounded configuration snapshot");
        }finally{SkillCatalog.resetConfiguration();}
        p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).remove("cp");
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
        s.unlearn(HighPowerSkills.BEAM_ETCHING);f.learn(p,HighPowerSkills.BEAM_ETCHING);
        h.assertTrue(HighPowerResources.cp(p)==0&&combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"paid relearning cannot take the fresh-enrollment CP path or alter existing exact ledgers");
        s.unlearn(HighPowerSkills.BEAM_ETCHING);p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield","not-a-decimal");var corrupt=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var battery=f.portable(p,2340);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.BEAM_ETCHING,213).wire()),"corrupt existing ledger rejects learning at compatibility ingress");
        h.assertTrue(battery.energy()==2340&&!s.learned(HighPowerSkills.BEAM_ETCHING)&&corrupt.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)),"corrupt ledger spends no energy and stays unchanged");h.succeed();
    }

    /** Native category/level witness metadata may rebase; exact balances and actual q/Q cannot. */
    private static net.minecraft.nbt.CompoundTag stableLedger(net.minecraft.nbt.CompoundTag value){
        var copy=value.copy();var growth=copy.getCompound("activeGrowth");String q=growth.getString("position");
        copy.remove("activeGrowth");copy.putString("asserted_active_position",q);return copy;
    }
    private static boolean wire(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"EtchLearning"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer player,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer player,int ticks){for(int tick=0;tick<ticks;tick++)DevelopmentController.tick(player,ignored->{});}
        void learn(ServerPlayer player,String skill){var battery=portable(player,2340);h.assertTrue(DevelopmentController.startSkill(player,skill),"ordinary finite learning starts for "+skill);develop(player,78);h.assertTrue(AbilityStorage.get(player).learned(skill)&&battery.energy()==0&&DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"ordinary learning pays all 2340 IF for "+skill);}
        /** Same declared debit fixture as the foundation tests; production MD ingress stays closed. */
        void spendShield(ServerPlayer player,BigDecimal amount){
            var ability=AbilityStorage.get(player);String category=ability.category;var before=HighPowerCombat.shield(player);
            h.assertTrue(category.equals(HighPowerCategories.MELTDOWNER),"setup begins in the genuine MD learning category");
            h.assertFalse(HighPowerCombat.spendShield(player,amount),"MD cannot use the EM-only public shield debit");
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before)==0,"rejected MD debit preserves the exact base balance");
            ability.category=HighPowerCategories.ELECTROMASTER;
            try{h.assertTrue(HighPowerCombat.spendShield(player,amount),"declared EM fixture uses the reviewed ordinary base debit");}
            finally{ability.category=category;}
            h.assertTrue(HighPowerCombat.shield(player).compareTo(before.subtract(amount))==0,"setup spends exactly the requested base shield through the central paired write");
        }
        void cp(ServerPlayer player,double amount){player.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",amount);}
        void injure(ServerPlayer player){var maximum=new BigDecimal(HighPowerCombat.maxHealthText(player));player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",CombatAmounts.decimalString(maximum.divide(BigDecimal.TWO)));player.setHealth(10);}
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[EtchReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

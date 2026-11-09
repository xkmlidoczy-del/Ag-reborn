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

/** NEW222 native learning, paid final validation, ordinary bindings and exact save/ledger retention.
 * Finite batteries, injury, shield debit and accelerated developer ticks are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerThermalShellLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerThermalShellLearningRuntimeTests(){}

    @GameTest(template=TEMPLATE,batch="highpower_thermal_shell_learning",timeoutTicks=15)
    public static void genuine_lv2_scatter_prerequisite_and_130_paid_ticks_preserve_shared_ledgers(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.THERMAL_SHELL).orElseThrow();
        h.assertTrue(skill.level()==2&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.SCATTER_PLATES,0)))&&HighPowerSkills.THERMAL_SHELL_PROPOSAL==222,"NEW222 canonical MD LV2 and exact learned NEW212 prerequisite");
        var blocked=f.portable(p,3900);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),1).wire()),"fresh MD cannot bypass level or parent");
        h.assertFalse(wire(p,"preset_edit","0:3:"+skill.id()),"unlearned NEW222 cannot bind");
        h.assertTrue(blocked.energy()==3900&&!HighPowerCombat.enabled(p),"early rejection spends no IF or creates a ledger");
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);
        blocked=f.portable(p,3900);h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"genuine NEW212 cannot bypass integer LV2");
        h.assertTrue(blocked.energy()==3900,"integer level rejection consumes no IF");
        s.setLevel(2);HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();double xp=s.levelExperience;
        var battery=f.portable(p,3900);
        h.assertTrue(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE).wire()),"canonical bounded learning ingress starts actual NEW222 development");
        f.develop(p,129);
        h.assertTrue(!s.learned(skill.id())&&battery.energy()==30&&combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)),"NEW222 waits for all 130 ticks without early grant or shield mutation");
        f.develop(p,1);
        h.assertTrue(s.learned(skill.id())&&s.exp(skill.id())==0&&s.levelExperience==xp&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"NEW222 exact3900IF completion gives no mastery or levelXP");
        h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"learning never refills HP, shared shield, independent plate shares, CP or q/Q");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"+skill.id()),"NEW222 binds each native preset slot");
            h.assertFalse(wire(p,"preset_edit",preset+":"+((slot+1)%4)+":"+skill.id()),"NEW222 retains ordinary duplicate-per-preset rejection");
            h.assertTrue(wire(p,"preset_edit",preset+":"+slot+":"),"clear only tested binding");
        }
        h.assertTrue(wire(p,"preset_edit","3:3:"+skill.id())&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,skill.id()),"paid NEW222 uses ordinary binding and activation");
        var saved=AbilityStorage.encodeSaved(s);var restored=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        h.assertTrue(saved.getInt("schema")==3&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW222 preserves native save schema and canonical mapping");
        h.assertFalse(DevelopmentController.startSkill(p,skill.id()),"already learned skill cannot purchase twice");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_thermal_shell_learning",timeoutTicks=15)
    public static void final_shortage_or_late_level_category_parent_config_change_never_grants(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","level","category","parent","config")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);HighPowerGrowth.values(p);f.injure(p);f.cp(p,37);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            var battery=f.portable(p,invalidation.equals("battery")?3899:3900);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.THERMAL_SHELL),"valid native NEW222 development starts before late "+invalidation);f.develop(p,129);
            try{
                switch(invalidation){
                    case "level"->s.setLevel(1);
                    case "category"->s.changeCategoryClassic("electromaster");
                    case "parent"->s.unlearn(HighPowerSkills.SCATTER_PLATES);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.THERMAL_SHELL),new ClassicSkillConfiguration.Flags(false,false)));
                }
                f.develop(p,1);
                h.assertTrue(battery.energy()==0&&!s.learned(HighPowerSkills.THERMAL_SHELL)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final paid tick rejects "+invalidation+" without a grant or refund");
                h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY))&&resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"failed completion cannot refill or alter existing exact ledgers");
            }finally{SkillCatalog.resetConfiguration();}
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_thermal_shell_learning",timeoutTicks=15)
    public static void native_reload_category_changes_and_paid_relearning_keep_depleted_resources(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");s.setLevel(2);
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.BEAM_ETCHING);f.learn(p,HighPowerSkills.THERMAL_SHELL);
        HighPowerGrowth.values(p);f.spendShield(p,new BigDecimal("12.325"));f.injure(p);f.cp(p,37);
        var resources=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);resources.putLong("next_thermal_shell",p.serverLevel().getGameTime()+500);
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var budget=resources.copy();
        h.assertTrue(wire(p,"preset_edit","3:0:dual_point")&&wire(p,"preset_edit","3:1:scatter_plates")&&wire(p,"preset_edit","3:2:beam_etching")&&wire(p,"preset_edit","3:3:thermal_shell"),"all four approved MD additions share existing ordinary slots");
        var loaded=f.reload(p);var restored=AbilityStorage.get(loaded);
        h.assertTrue(restored.learned(HighPowerSkills.THERMAL_SHELL)&&restored.presets.skill(3,3).equals("thermal_shell"),"real native player save/load retains NEW222 learning and mapping");
        h.assertTrue(stableLedger(combat).equals(stableLedger(loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(loaded.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"native reload cannot refill balances or cooldown");
        s=AbilityStorage.get(p);
        s.changeCategoryClassic("electromaster");f.learn(p,HighPowerSkills.FORKED_ARC);s.changeCategoryClassic("meltdowner");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"ordinary category change clears old mappings");
        var blocked=f.portable(p,3900);h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.THERMAL_SHELL),"category return cannot bypass renewed NEW212 learning");h.assertTrue(blocked.energy()==3900,"missing prerequisite consumes no energy");
        f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.THERMAL_SHELL);
        h.assertTrue(stableLedger(combat).equals(stableLedger(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)))&&budget.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),"category switches and paid relearning never refill existing shared resources or reset cooldown");h.succeed();
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
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ShellLearning"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer player,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer player,int ticks){for(int tick=0;tick<ticks;tick++)DevelopmentController.tick(player,ignored->{});}
        void learn(ServerPlayer player,String skill){int level=SkillCatalog.find(AbilityStorage.get(player).category,skill).orElseThrow().level();int stimulations=cn.academy.port.core.ClassicRules.learningStimulations(level);double cost=DeveloperType.PORTABLE.actualConsumption(stimulations);var battery=portable(player,cost);h.assertTrue(DevelopmentController.startSkill(player,skill),"ordinary finite learning starts for "+skill);develop(player,stimulations*DeveloperType.PORTABLE.ticksPerStimulation());h.assertTrue(AbilityStorage.get(player).learned(skill)&&battery.energy()==0&&DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"ordinary learning pays all native IF for "+skill);}
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
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[ShellReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

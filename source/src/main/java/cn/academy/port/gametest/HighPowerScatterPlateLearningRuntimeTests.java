package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ScatterPlateShares;
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

/** Real native paid learning; batteries, injury, EM-only setup debits and accelerated development clocks are fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerScatterPlateLearningRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerScatterPlateLearningRuntimeTests(){}

    @GameTest(template=TEMPLATE,batch="highpower_scatter_plate_learning",timeoutTicks=15)
    public static void finite_paid_learning_requires_dual_point_and_partitions_only_current_base_shield(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        var blocked=f.portable(p,2340);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.SCATTER_PLATES,1).wire()),"NEW212 refuses a fresh MD player without learned NEW211");
        h.assertTrue(blocked.energy()==2340&&!HighPowerCombat.enabled(p)&&ScatterPlateStorage.read(p).isEmpty(),"missing parent spends no IF and creates no ledger");
        f.learn(p,HighPowerSkills.DUAL_POINT);
        f.spendShield(p,new BigDecimal("12.325"));
        f.injure(p);f.cp(p,37);
        var hp=HighPowerCombat.healthText(p);var base=HighPowerCombat.shield(p);var maximum=new BigDecimal(HighPowerCombat.maxShieldText(p));
        var q=HighPowerGrowth.values(p).position();var earned=HighPowerCombat.earnedGrowthPosition(p);
        var battery=f.portable(p,2340);var request=new HighPowerLearningProtocol.Request(HighPowerSkills.SCATTER_PLATES,Integer.MAX_VALUE);
        h.assertTrue(wire(p,"hp_learn",request.wire()),"canonical correlated NEW212 request uses the existing finite Portable developer");
        f.develop(p,77);
        h.assertTrue(!s.learned(HighPowerSkills.SCATTER_PLATES)&&battery.energy()==30&&ScatterPlateStorage.read(p).isEmpty(),"77 paid ticks cannot grant the skill or initialize shares");
        f.develop(p,1);
        h.assertTrue(s.learned(HighPowerSkills.SCATTER_PLATES)&&s.exp(HighPowerSkills.SCATTER_PLATES)==0&&battery.energy()==0&&DevelopmentController.process(p).state()==DevelopmentProcess.State.DONE,"three native stimulations finish after exactly 78 ticks and 2340 IF");
        var shares=ScatterPlateStorage.read(p).orElseThrow();
        h.assertTrue(shares.toDecimalMap().equals(ScatterPlateShares.initialize(base,maximum).toDecimalMap()),"first learning partitions only the already depleted base balance into three exact shares");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p))&&base.equals(HighPowerCombat.shield(p))&&HighPowerResources.cp(p)==37&&HighPowerGrowth.values(p).position().compareTo(q)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(earned)==0,"NEW212 learning never refills HP, shared CP, shield or q/Q");
        h.assertTrue(wire(p,"preset_edit","3:3:scatter_plates")&&wire(p,"activate_state","true")&&HighPowerSkills.eligible(s,HighPowerSkills.SCATTER_PLATES),"genuine learned NEW212 binds an existing preset slot");
        var saved=AbilityStorage.encodeSaved(s);var restored=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        h.assertTrue(restored.learned(HighPowerSkills.SCATTER_PLATES)&&restored.presets.skill(3,3).equals(HighPowerSkills.SCATTER_PLATES)&&AbilityStorage.encodeSaved(restored).equals(saved),"learned bit and slot use the unchanged native save schema");
        h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.SCATTER_PLATES),"duplicate learned NEW212 is refused before spending");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_scatter_plate_learning",timeoutTicks=15)
    public static void category_switch_paid_relearning_and_native_reload_keep_each_independent_share(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");
        f.learn(p,HighPowerSkills.DUAL_POINT);s.addExperience(HighPowerSkills.DUAL_POINT,.125);f.learn(p,HighPowerSkills.SCATTER_PLATES);
        var beforeSpend=ScatterPlateStorage.read(p).orElseThrow();
        f.spendShield(p,beforeSpend.plate(0).current().add(BigDecimal.ONE));
        var shares=ScatterPlateStorage.read(p).orElseThrow().toDecimalMap();
        h.assertTrue(new BigDecimal(shares.get("plate0")).signum()==0&&new BigDecimal(shares.get("plate1")).compareTo(new BigDecimal(shares.get("plate2")))<0,"persistent shares are deliberately unequal with one exhausted plate");
        f.injure(p);f.cp(p,37);var hp=HighPowerCombat.healthText(p);var base=HighPowerCombat.shield(p);
        var q=HighPowerGrowth.values(p).position();var earned=HighPowerCombat.earnedGrowthPosition(p);double nativeXp=s.levelExperience;
        long deadline=p.serverLevel().getGameTime()+500;p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_dual_point",deadline);
        h.assertTrue(wire(p,"preset_edit","3:2:dual_point")&&wire(p,"preset_edit","3:3:scatter_plates"),"both MD skills start in normal saved slots");
        for(String category:List.of("electromaster","meltdowner","teleporter","meltdowner")){
            s.changeCategoryClassic(category);
            h.assertTrue(s.experience.isEmpty()&&s.levelExperience==nativeXp,"native category change clears learned skills and retains actual XP");
            for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)h.assertTrue(s.presets.skill(preset,slot).isEmpty(),"native category change clears every old preset mapping");
            h.assertTrue(ScatterPlateStorage.read(p).orElseThrow().toDecimalMap().equals(shares),"category suspension retains all independent shares, including an exhausted plate");
            if(category.equals("electromaster"))f.learn(p,HighPowerSkills.FORKED_ARC);
            if(category.equals("meltdowner")){
                var missingParent=f.portable(p,2340);h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.SCATTER_PLATES),"MD return cannot bypass NEW211 relearning");
                h.assertTrue(missingParent.energy()==2340,"missing-parent relearning attempt spends no IF");
                f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);
                h.assertTrue(wire(p,"preset_edit","3:3:scatter_plates"),"paid relearned NEW212 can be rebound");
            }
            h.assertTrue(ScatterPlateStorage.read(p).orElseThrow().toDecimalMap().equals(shares)&&hp.equals(HighPowerCombat.healthText(p))&&base.equals(HighPowerCombat.shield(p))&&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp")==37,"category and paid relearning cannot rebalance shares or refill shared injury/CP/shield");
            h.assertTrue(new BigDecimal(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound("activeGrowth").getString("position")).compareTo(q)==0&&HighPowerCombat.earnedGrowthPosition(p).compareTo(earned)==0&&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_dual_point")==deadline,"category denominators cannot catch up q/Q or reset the existing cooldown");
        }
        var loaded=f.reload(p);var loadedState=AbilityStorage.get(loaded);
        h.assertTrue(loadedState.learned(HighPowerSkills.SCATTER_PLATES)&&loadedState.presets.skill(3,3).equals(HighPowerSkills.SCATTER_PLATES)&&ScatterPlateStorage.read(loaded).orElseThrow().toDecimalMap().equals(shares),"actual entity save/load preserves learned ID, binding and every exact share");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(loaded))&&base.equals(HighPowerCombat.shield(loaded))&&HighPowerResources.cp(loaded)==37&&HighPowerGrowth.values(loaded).position().compareTo(q)==0&&HighPowerCombat.earnedGrowthPosition(loaded).compareTo(earned)==0,"native reload preserves injury and all shared balances and growth coordinates");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_scatter_plate_learning",timeoutTicks=15)
    public static void finite_shortage_and_late_category_level_config_parent_changes_grant_no_shares(GameTestHelper h){
        var f=new Fixture(h);
        for(String invalidation:List.of("battery","category","level","config","parent")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");f.learn(p,HighPowerSkills.DUAL_POINT);
            var base=HighPowerCombat.shield(p);var battery=f.portable(p,invalidation.equals("battery")?2339:2340);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.SCATTER_PLATES),"valid finite NEW212 action starts before late "+invalidation);f.develop(p,77);
            try{
                switch(invalidation){
                    case "category"->s.changeCategoryClassic("electromaster");
                    case "level"->s.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.SCATTER_PLATES),new ClassicSkillConfiguration.Flags(false,false)));
                    case "parent"->s.unlearn(HighPowerSkills.DUAL_POINT);
                }
                f.develop(p,1);
                h.assertTrue(battery.energy()==0&&!s.learned(HighPowerSkills.SCATTER_PLATES)&&DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED,"final paid tick refuses "+invalidation+" without a learned grant or refund");
                h.assertTrue(base.equals(HighPowerCombat.shield(p))&&ScatterPlateStorage.read(p).isEmpty()&&!p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(ScatterPlateStorage.INITIALIZED_KEY),"failed NEW212 completion cannot initialize or refill shares");
            }finally{SkillCatalog.resetConfiguration();}
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_scatter_plate_learning",timeoutTicks=15)
    public static void paid_relearning_never_repairs_missing_or_corrupt_share_ledgers_or_cp(GameTestHelper h){
        var f=new Fixture(h);
        for(String corruption:List.of("missing","malformed","marker","invalidated")){
            var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");f.learn(p,HighPowerSkills.DUAL_POINT);f.learn(p,HighPowerSkills.SCATTER_PLATES);
            f.spendShield(p,new BigDecimal("12.325"));f.injure(p);
            var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            switch(corruption){
                case "missing"->combat.remove(ScatterPlateStorage.KEY);
                case "malformed"->combat.getCompound(ScatterPlateStorage.KEY).putString("plate1","not-a-decimal");
                case "marker"->combat.putString(ScatterPlateStorage.INITIALIZED_KEY,"true");
                case "invalidated"->combat.putBoolean(ScatterPlateStorage.INVALIDATED_KEY,true);
            }
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).remove("cp");var before=combat.copy();
            s.unlearn(HighPowerSkills.SCATTER_PLATES);f.learn(p,HighPowerSkills.SCATTER_PLATES);
            h.assertTrue(ScatterPlateStorage.read(p).isEmpty()&&ScatterPlateStorage.ensure(p).isEmpty()&&p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).equals(before),"paid relearning leaves "+corruption+" saved shares unchanged and fail-closed");
            h.assertTrue(HighPowerResources.cp(p)==0&&!p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).contains("cp"),"existing missing shared CP never takes the fresh-enrollment refill path");
            var loaded=f.reload(p);
            h.assertTrue(ScatterPlateStorage.read(loaded).isEmpty()&&ScatterPlateStorage.ensure(loaded).isEmpty()&&loaded.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).equals(before),"native reload cannot heal "+corruption+" plate metadata");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_scatter_plate_learning",timeoutTicks=15)
    public static void corrupt_existing_base_ledger_blocks_learning_before_finite_energy_is_spent(GameTestHelper h){
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("meltdowner");f.learn(p,HighPowerSkills.DUAL_POINT);
        p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield","not-a-decimal");
        var before=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();var battery=f.portable(p,2340);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.SCATTER_PLATES,212).wire()),"corrupt existing exact ledger fails the native compatibility gate");
        h.assertTrue(battery.energy()==2340&&!s.learned(HighPowerSkills.SCATTER_PLATES)&&ScatterPlateStorage.ensure(p).isEmpty()&&p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).equals(before),"corrupt ledger spends no IF, grants no skill and remains unmodified");h.succeed();
    }

    private static boolean wire(ServerPlayer player,String action,String value){return AcademyGameplay.requestFromClient(player,new AcademyNetwork.Request(action,value));}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"PlateLearning"),false);
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
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());AbilityStorage.remove(original);var restored=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[PlateReload]"));players.add(restored);restored.load(saved);AbilityStorage.get(restored);return restored;}
        void close(){if(closed)return;closed=true;SkillCatalog.resetConfiguration();for(var player:players){DevelopmentController.remove(player);AbilityStorage.remove(player);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(player.getUUID())==player)list.remove(player);else player.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

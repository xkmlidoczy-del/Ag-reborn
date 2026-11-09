package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillExpChangedEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual float receipts, finite paid level work and declared old-save/corruption fixture mutations. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerActiveGrowthRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_active_growth";
    private HighPowerActiveGrowthRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void native_death_clone_keeps_pending_active_credit_below_unchanged_earned_capacity(GameTestHelper h) {
        Fixture f=new Fixture(h);var original=f.connectedPlayer();var state=AbilityStorage.get(original);state.setLevel(1);
        BigDecimal capacity=HighPowerCombat.earnedGrowthPosition(original);
        equal(h,HighPowerGrowth.values(original).position(),BigDecimal.ONE,"declared rollback leaves active q below earned Q5");
        DeathAward receipt=new DeathAward(original);f.hook(receipt);
        h.runAfterDelay(61,()->{
            original.doTick();original.kill();
            h.assertTrue(receipt.called&&!original.isAlive(),"real native lethal hurt produced an XP award inside its live Post frame");
            equal(h,HighPowerGrowth.values(original).position(),BigDecimal.ONE,"dead owner cannot prematurely commit pending active credit");
            var replacement=f.player(HighPowerCategories.ELECTROMASTER,1);
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,original,true));
            BigDecimal expected=ContinuousHighPowerGrowth.position(1,AbilityStorage.get(replacement).levelProgress(2));
            h.assertTrue(expected.compareTo(BigDecimal.ONE)>0&&expected.compareTo(capacity)<0,"receipt advances only active q, remaining below prior Q");
            equal(h,HighPowerGrowth.values(replacement).position(),expected,"death clone retains the exact pending active coordinate");
            equal(h,HighPowerCombat.earnedGrowthPosition(replacement),capacity,"already higher earned capacity is unchanged");
            h.assertTrue(HighPowerCombat.healthText(replacement).equals(HighPowerCombat.maxHealthText(replacement))
                    &&HighPowerCombat.shieldText(replacement).equals(HighPowerCombat.maxShieldText(replacement)),
                    "clone applies only the established death HP/shield balance policy");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void uninterrupted_em_and_md_receipts_telescope_to_original_float_formula_through_paid_boundaries(GameTestHelper h) {
        Fixture f=new Fixture(h);
        for(String category:List.of(HighPowerCategories.ELECTROMASTER,HighPowerCategories.MELTDOWNER)) {
            var p=f.player(category,1);var s=AbilityStorage.get(p);String root=HighPowerCategories.recoveryRoot(category);
            String hp=HighPowerCombat.healthText(p),shield=HighPowerCombat.shieldText(p);f.cp(p,37);
            for(int level=1;level<5;level++) {
                for(int award=0;award<128;award++) {
                    s.addExperience(root,.001);
                    equal(h,HighPowerGrowth.values(p).position(),ContinuousHighPowerGrowth.position(level,s.levelProgress(SkillCatalog.levelSkillCount(s))),
                            "each small native float award preserves original coordinate "+category+" LV"+level+" #"+award);
                }
                s.addExperience(root,ClassicRules.levelThreshold(level,SkillCatalog.levelSkillCount(s)));
                equal(h,HighPowerGrowth.values(p).position(),BigDecimal.valueOf(level+1),"saturated native fraction reaches the next integer ceiling");
                // Additional native XP is not a hidden reserve carried through the paid reset.
                s.addExperience(root,.2);equal(h,HighPowerGrowth.values(p).position(),BigDecimal.valueOf(level+1),"overflow at current ceiling is discarded");
                f.level(p);
                equal(h,HighPowerGrowth.values(p).position(),BigDecimal.valueOf(level+1),"finite native level completion is continuous");
                h.assertTrue(s.level==level+1&&s.levelExperience==0,"unchanged native level/reset semantics");
            }
            equal(h,HighPowerGrowth.values(p).position(),BigDecimal.valueOf(5),"ordinary uninterrupted path reaches q5");
            h.assertTrue(hp.equals(HighPowerCombat.healthText(p))&&shield.equals(HighPowerCombat.shieldText(p))&&HighPowerResources.cp(p)==37,
                    "all awards and paid boundaries preserve balances exactly");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void saturated_switch_tiny_award_and_lagging_native_lv5_remain_continuous_until_genuine_xp_finishes(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(HighPowerCategories.ELECTROMASTER,1);var s=AbilityStorage.get(p);
        s.addExperience(HighPowerSkills.FORKED_ARC,ClassicRules.levelThreshold(1,2)*.5);
        BigDecimal before=HighPowerGrowth.values(p).position();
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);s.learn(HighPowerSkills.DUAL_POINT);
        h.assertTrue(s.levelProgress(1)==1,"retained native XP is already saturated under MD's unchanged denominator");
        equal(h,HighPowerGrowth.values(p).position(),before,"category denominator change contributes zero effective growth");
        float threshold=(float)ClassicRules.levelThreshold(1,1),previous=(float)s.levelExperience;
        s.addExperience(HighPowerSkills.DUAL_POINT,.001);
        BigDecimal tiny=BigDecimal.valueOf((double)((float)s.levelExperience/threshold)).subtract(BigDecimal.valueOf((double)(previous/threshold)));
        equal(h,HighPowerGrowth.values(p).position(),before.add(tiny),"first genuine award credits only its own float quotient delta");
        f.level(p);BigDecimal expected=before.add(tiny);
        equal(h,HighPowerGrowth.values(p).position(),expected,"native LV2 unlock does not jump lagging q");
        while(s.level<5) {
            int current=s.level;s.addExperience(HighPowerSkills.DUAL_POINT,ClassicRules.levelThreshold(current,SkillCatalog.levelSkillCount(s)));
            expected=expected.add(BigDecimal.ONE).min(BigDecimal.valueOf(5));
            equal(h,HighPowerGrowth.values(p).position(),expected,"each real full native level of XP advances q by one");
            f.level(p);equal(h,HighPowerGrowth.values(p).position(),expected,"paid level keeps q continuous");
        }
        h.assertTrue(expected.compareTo(BigDecimal.valueOf(5))<0&&s.level==5,"native LV5 can arrive with explicitly lagging q");
        s.addExperience(HighPowerSkills.DUAL_POINT,ClassicRules.levelThreshold(5,SkillCatalog.levelSkillCount(s)));
        equal(h,HighPowerGrowth.values(p).position(),BigDecimal.valueOf(5),"continued genuine native LV5 XP reaches q5, without a level-fixed jump or permanent stall");
        equal(h,HighPowerCombat.earnedGrowthPosition(p),BigDecimal.valueOf(5),"global earned capacity follows completed q");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void v16_migration_preserves_actual_old_output_before_switch_and_dormant_migration_is_conservative(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(HighPowerCategories.ELECTROMASTER,5);var s=AbilityStorage.get(p);s.setLevel(1);
        s.addExperience(HighPowerSkills.FORKED_ARC,.2);BigDecimal old=ContinuousHighPowerGrowth.position(1,s.levelProgress(2));
        var combat=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);combat.remove("activeGrowth");
        String hp=HighPowerCombat.healthText(p),shield=HighPowerCombat.shieldText(p);double cp=HighPowerResources.cp(p);
        equal(h,HighPowerGrowth.values(p).position(),old,"active old save migration exactly reproduces .16 current output");
        combat.remove("activeGrowth");s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);s.learn(HighPowerSkills.DUAL_POINT);
        equal(h,HighPowerGrowth.values(p).position(),old,"before-mutation migration preserves old EM output across first MD switch");
        s.changeCategoryClassic("unsupported_test");combat.remove("activeGrowth");
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);s.learn(HighPowerSkills.DUAL_POINT);
        equal(h,HighPowerGrowth.values(p).position(),BigDecimal.ONE,"unsupported-category old save seeds only conservative native integer on return");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p))&&shield.equals(HighPowerCombat.shieldText(p))&&HighPowerResources.cp(p)==cp,
                "all migrations preserve achieved HP/shield/CP balances");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void nested_receipts_and_repeated_delivery_credit_actual_float_progress_once(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(HighPowerCategories.ELECTROMASTER,1);var s=AbilityStorage.get(p);
        f.hook(new NestedAward(p));s.addExperience(HighPowerSkills.FORKED_ARC,.01);
        equal(h,HighPowerGrowth.values(p).position(),ContinuousHighPowerGrowth.position(1,s.levelProgress(2)),"nested native Changed callback deltas commute and preserve original total");
        var captured=new ArrayList<cn.academy.port.core.AbilityProgress.ExperienceReceipt>();
        // Exercise the same native core receipt's closed claim directly without replacing server bindings.
        var receiptState=new cn.academy.port.core.AbilityProgress();receiptState.changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
        receiptState.setLevel(1);receiptState.learn(HighPowerSkills.FORKED_ARC);int[] claims={0};
        receiptState.bindEarnedProgress(()->{},receipt->{captured.add(receipt);if(receipt.claim(receiptState))claims[0]++;if(receipt.claim(receiptState))claims[0]++;});
        receiptState.addExperience(HighPowerSkills.FORKED_ARC,.02);
        h.assertTrue(captured.size()==1&&claims[0]==1&&!captured.getFirst().claim(receiptState),"opaque native receipt claims exactly once while open and never after return");
        BigDecimal q=HighPowerGrowth.values(p).position();HighPowerCombat.earnedExperience(p,captured.getFirst());
        equal(h,HighPowerGrowth.values(p).position(),q,"retained or foreign-owner receipt cannot replay into enrolled player");
        s.addExperience(HighPowerSkills.FORKED_ARC,Float.MIN_VALUE);
        equal(h,HighPowerGrowth.values(p).position(),q,"positive requested amount with unchanged native float XP grants nothing");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void malformed_growth_metadata_never_blocks_classic_mutations_or_corrupts_pending_native_heals(GameTestHelper h) {
        Fixture f=new Fixture(h);var p=f.player(HighPowerCategories.ELECTROMASTER,1);var s=AbilityStorage.get(p);
        BigDecimal half=new BigDecimal(HighPowerCombat.maxHealthText(p)).divide(BigDecimal.TWO);f.hp(p,half);f.food(p);
        f.hook(new CorruptPending(p));p.getFoodData().tick(p);
        for(int tick=1;tick<10;tick++)p.getFoodData().tick(p);
        HighPowerCombat.tick(new EntityTickEvent.Post(p));
        var bad=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound("activeGrowth").copy();
        h.assertTrue(bad.getInt("version")==99,"fixture corrupts active metadata only after a genuine deferred receipt exists");
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);s.learn(HighPowerSkills.DUAL_POINT);s.addExperience(HighPowerSkills.DUAL_POINT,.01);s.setLevel(2);
        h.assertTrue(s.level==2&&s.category.equals(HighPowerCategories.MELTDOWNER),"ordinary native category, XP and level mutations still complete");
        h.assertFalse(HighPowerResources.ready(p)||HighPowerResources.tick(p),"malformed high-power output remains disabled");
        h.assertTrue(bad.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound("activeGrowth")),"invalid metadata is retained for explicit repair, never silently reset");
        equal(h,new BigDecimal(HighPowerCombat.healthText(p)),half,"canceled native heal, deferred flush and classic mutations never change exact injury");h.succeed();
    }

    private static void equal(GameTestHelper h,BigDecimal actual,BigDecimal expected,String message){h.assertTrue(actual.compareTo(expected)==0,message+": "+actual+" expected "+expected);}
    public static final class NestedAward {
        final ServerPlayer player;boolean nested;NestedAward(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void changed(SkillExpChangedEvent event){if(event.getEntity()==player&&!nested){nested=true;event.state.addExperience(HighPowerSkills.FORKED_ARC,.003);}}
    }
    public static final class CorruptPending {
        final ServerPlayer player;boolean called;CorruptPending(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void heal(LivingHealEvent event){if(event.getEntity()!=player||called)return;called=true;
            AbilityStorage.get(player).addExperience(HighPowerSkills.FORKED_ARC,.1);
            player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound("activeGrowth").putInt("version",99);event.setCanceled(true);
        }
    }
    public static final class DeathAward {
        final ServerPlayer player;boolean called;DeathAward(ServerPlayer player){this.player=player;}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void post(LivingDamageEvent.Post event){
            if(event.getEntity()==player&&!called){called=true;AbilityStorage.get(player).addExperience(HighPowerSkills.FORKED_ARC,.01);}
        }
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy=200000;public DeveloperType type(){return DeveloperType.ADVANCED;}public double energy(){return energy;}public double maxEnergy(){return 200000;}
        public boolean tryPullEnergy(double amount){double paid=Math.min(energy,amount);energy-=paid;return paid==amount;}
    }
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<io.netty.channel.embedded.EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(String category,int level){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-ActiveQ]"));players.add(p);p.setNoGravity(true);p.getAbilities().instabuild=false;var s=AbilityStorage.get(p);s.changeCategoryClassic(category);s.setLevel(level);s.learn(HighPowerCategories.recoveryRoot(category));s.activated=true;s.overloadFine=true;h.assertTrue(HighPowerCombat.enroll(p,level),"native active-coordinate fixture");HighPowerResources.initializeBudget(p);return p;}
        ServerPlayer connectedPlayer(){
            var server=h.getLevel().getServer();var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPDeathActiveQ"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);channels.add(new io.netty.channel.embedded.EmbeddedChannel(connection));
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setNoGravity(true);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(8,3,8)));p.connection.resetPosition();
            var s=AbilityStorage.get(p);s.changeCategoryClassic(HighPowerCategories.ELECTROMASTER);s.setLevel(5);s.learn(HighPowerSkills.FORKED_ARC);s.activated=true;s.overloadFine=true;
            h.assertTrue(HighPowerCombat.enroll(p,5),"real connected death/growth fixture");HighPowerResources.initializeBudget(p);return p;
        }
        void level(ServerPlayer p){var s=AbilityStorage.get(p);var work=new DevelopmentProcess();var battery=new Battery();work.start(battery,HighPowerProgression.leveling(p,DevelopmentActions.level(s,DevelopmentActions.CategoryItems.NONE,bound->0)));for(int ticks=0;work.isDeveloping()&&ticks<2000;ticks++)work.tick();h.assertTrue(work.state()==DevelopmentProcess.State.DONE&&battery.energy<200000,"genuine finite paid level work completes");}
        void cp(ServerPlayer p,double amount){p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",amount);}
        void hp(ServerPlayer p,BigDecimal amount){p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",CombatAmounts.decimalString(amount));p.setHealth(amount.divide(new BigDecimal(HighPowerCombat.maxHealthText(p)),MathContext.DECIMAL128).multiply(BigDecimal.valueOf(20)).floatValue());}
        void food(ServerPlayer p){var food=p.getFoodData();food.setFoodLevel(20);food.setSaturation(6);food.setExhaustion(0);var data=new CompoundTag();food.addAdditionalSaveData(data);data.putInt("foodTickTimer",0);food.readAdditionalSaveData(data);}
        void hook(Object value){hooks.add(value);NeoForge.EVENT_BUS.register(value);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);for(var p:players){AbilityStorage.remove(p);if(h.getLevel().getServer().getPlayerList().getPlayer(p.getUUID())==p)h.getLevel().getServer().getPlayerList().remove(p);else p.discard();}channels.forEach(io.netty.channel.embedded.EmbeddedChannel::finishAndReleaseAll);}
    }
}

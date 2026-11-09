package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real XP events and finite developer work; direct ledger injuries are declared test fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerGrowthRuntimeTests {
    private static final String TEMPLATE = "highpower_empty";
    private HighPowerGrowthRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void v1_curve_checkpoint_migrates_once_without_refilling_and_survives_native_reload(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 3);
        var position = new BigDecimal("3.25");
        var oldDamage = ContinuousHighPowerGrowth.curve("1e7","1e10","1e13","1e16","5e18").value(position);
        var old = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        old.putInt("growthCurve", 1); old.putString("growthPosition", "3.25");
        old.putString("maxHealth", oldDamage.multiply(BigDecimal.valueOf(20)).toPlainString());
        old.putString("maxShield", oldDamage.multiply(BigDecimal.TEN).toPlainString());
        old.putString("health", oldDamage.multiply(BigDecimal.valueOf(8)).toPlainString());
        old.putString("shield", oldDamage.multiply(new BigDecimal("2.5")).toPlainString());
        var saved = old.copy();
        var resources = new CompoundTag(); resources.putDouble("cp", 123.5);
        p.getPersistentData().put(HighPowerResources.DATA_KEY, resources);
        HighPowerCombat.beforeTick(new EntityTickEvent.Pre(p));
        h.assertTrue(HighPowerCombat.enabled(p), "ordinary entity tick admits the migrated ledger");
        var current = ContinuousHighPowerGrowth.sample(position);
        f.amount(HighPowerCombat.healthText(p), current.maxHealth().multiply(new BigDecimal(".4")).round(new MathContext(40, java.math.RoundingMode.DOWN)), "injured health fraction retained");
        f.amount(HighPowerCombat.shieldText(p), current.maxShield().multiply(new BigDecimal(".25")).round(new MathContext(40, java.math.RoundingMode.DOWN)), "spent shield fraction retained");
        h.assertTrue(p.getPersistentData().getCompound(HighPowerCurveMigration.BACKUP_KEY).getCompound("combat").equals(saved), "complete v1 checkpoint preserved");
        h.assertTrue(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).equals(resources), "CP is not refilled");
        h.assertTrue(HighPowerCombat.earnedGrowthPosition(p).compareTo(position) == 0, "certified coordinate retained");
        h.assertFalse(HighPowerCurveMigration.migrate(p), "migration cannot be replayed");
        var loaded = f.reload(p);
        h.assertTrue(HighPowerCombat.enabled(loaded), "real entity NBT reload admits v2");
        h.assertTrue(HighPowerCombat.healthText(loaded).equals(HighPowerCombat.healthText(p)), "reload preserves injury");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void curve_migration_rejects_forged_old_maximum_and_future_versions(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 1);
        var data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        data.putInt("growthCurve", 1); // Current caps cannot masquerade as an authentic old checkpoint.
        var forged = data.copy();
        h.assertFalse(HighPowerCurveMigration.migrate(p), "wrong old maximum rejected");
        h.assertTrue(data.equals(forged) && !p.getPersistentData().contains(HighPowerCurveMigration.BACKUP_KEY), "refusal is read-only");
        data.putInt("growthCurve", ContinuousHighPowerGrowth.CURVE_VERSION + 1);
        h.assertFalse(HighPowerCurveMigration.migrate(p), "future version never guessed");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void classic_low_level_attack_event_applies_reduction_without_scaling_extension_budgets(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 1);
        var classic = new cn.academy.port.api.SkillAttackEvent(p, "electromaster.railgun", null, 110);
        EsperLegacyBypass.force(true);
        try { ClassicDamageScaleRuntime.attack(classic); } finally { EsperLegacyBypass.force(false); }
        h.assertTrue(classic.amount == 22, "runtime accepts the reduced LV1 railgun amount");
        var extension = new cn.academy.port.api.SkillAttackEvent(p, "electromaster.forked_arc", null, 10);
        EsperLegacyBypass.force(true);
        try { ClassicDamageScaleRuntime.attack(extension); } finally { EsperLegacyBypass.force(false); }
        h.assertTrue(extension.amount == 10, "closed extension budget is not scaled twice");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void real_xp_grows_inside_level_without_refill_and_raw_relevel_never_certifies(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true,1); var s = AbilityStorage.get(p);
        BigDecimal hp = HighPowerLevel.LV1.maxHealth().subtract(BigDecimal.ONE);
        BigDecimal shield = HighPowerLevel.LV1.maxShield().subtract(BigDecimal.ONE);
        f.hp(p,hp); h.assertTrue(HighPowerCombat.spendShield(p,BigDecimal.ONE),"one exact shield point spent");
        int count = SkillCatalog.levelSkillCount(s);
        s.addExperience(HighPowerSkills.FORKED_ARC,ClassicRules.levelThreshold(1,count)*.25);
        var expected = ContinuousHighPowerGrowth.sample(1,s.levelProgress(count));
        h.assertTrue(s.level==1 && count==SkillCatalog.levelSkillCount(s),"ordinary level and classic denominator unchanged");
        f.amount(HighPowerCombat.maxHealthText(p),expected.maxHealth(),"capacity grows within LV1");
        f.amount(HighPowerCombat.healthText(p),hp,"growth grants no health");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield)==0,"growth grants no shield");
        BigDecimal q = HighPowerCombat.earnedGrowthPosition(p); String cap = HighPowerCombat.maxHealthText(p);
        for(int i=0;i<10;i++) h.assertFalse(HighPowerCombat.synchronizeEarnedProgress(p),"same earned point is idempotent");
        s.setLevel(5); s.addExperience(HighPowerSkills.FORKED_ARC,.2);
        h.assertTrue(HighPowerCombat.earnedLevel(p)==1 && HighPowerCombat.earnedGrowthPosition(p).compareTo(q)==0,"raw admin promotion plus XP cannot certify LV5");
        s.setLevel(1);
        h.assertTrue(HighPowerCombat.maxHealthText(p).equals(cap),"rollback retains earned capacity");
        f.amount(HighPowerCombat.healthText(p),hp,"rollback preserves exact injury and current health");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield)==0,"rollback preserves shield expenditure");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void threshold_and_paid_level_unlock_share_exact_cap_and_preserve_food_clock(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true,1); var s = AbilityStorage.get(p);
        f.hp(p,HighPowerLevel.LV1.maxHealth().multiply(new BigDecimal(".975"))); f.food(p,.3F); f.ticks(p,7);
        s.addExperience(HighPowerSkills.FORKED_ARC,ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(s)));
        f.amount(HighPowerCombat.maxHealthText(p),HighPowerLevel.LV2.maxHealth(),"LV1 at full progress reaches LV2 capacity");
        h.assertTrue(s.level==1 && HighPowerCombat.earnedLevel(p)==1,"capacity never unlocks integer level early");
        String hp=HighPowerCombat.healthText(p), shield=HighPowerCombat.shieldText(p);
        var recovery=p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).copy();
        var battery = new Battery(); var process = new DevelopmentProcess();
        process.start(battery,HighPowerProgression.leveling(p,DevelopmentActions.level(s,DevelopmentActions.CategoryItems.NONE,bound->0)));
        for(int i=0;i<259;i++) process.tick();
        h.assertTrue(s.level==1 && process.isDeveloping(),"ten paid stimulations do not complete early");
        process.tick();
        h.assertTrue(s.level==2 && HighPowerCombat.earnedLevel(p)==2 && process.state()==DevelopmentProcess.State.DONE,"only genuine completed finite level work certifies LV2");
        h.assertTrue(battery.energy==2200,"exactly 7800 IF paid");
        h.assertTrue(HighPowerCombat.earnedGrowthPosition(p).compareTo(BigDecimal.TWO)==0,"boundary coordinate is identical");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && shield.equals(HighPowerCombat.shieldText(p)),"level boundary neither heals nor refills");
        h.assertTrue(recovery.equals(p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY)),"equal-cap boundary leaves persisted recovery shadow byte-equivalent");
        h.assertTrue(((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer()==7,"growth and unlock do not reset food timer");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void legacy_migration_reload_clone_and_forged_caps_keep_fail_closed_contract(GameTestHelper h) {
        var f=new Fixture(h); var p=f.player(true,1); var data=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        data.remove("growthCurve"); data.remove("growthPosition"); data.remove("earnedLevel");
        var hp=HighPowerLevel.LV1.maxHealth().subtract(BigDecimal.ONE); f.hp(p,hp); HighPowerCombat.spendShield(p,BigDecimal.ONE);
        var shield=HighPowerCombat.shield(p);
        h.assertTrue(HighPowerCombat.enabled(p) && HighPowerCombat.earnedGrowthPosition(p).compareTo(BigDecimal.ONE)==0,"unchanged v9 integer ledger admitted");
        h.assertFalse(data.contains("growthCurve"),"read-only legacy inspection never edits the save");
        AbilityStorage.get(p).addExperience(HighPowerSkills.FORKED_ARC,.01);
        h.assertTrue(data.getInt("growthCurve")==ContinuousHighPowerGrowth.CURVE_VERSION,"first genuine growth migrates legacy metadata");
        f.amount(HighPowerCombat.healthText(p),hp,"migration preserves exact HP");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield)==0,"migration preserves exact shield");
        var restored=f.reload(p); var q=HighPowerCombat.earnedGrowthPosition(restored); var maximum=HighPowerCombat.maxHealthText(restored);
        f.amount(HighPowerCombat.healthText(restored),hp,"actual entity save/load retains one-point injury");
        h.assertTrue(HighPowerCombat.shield(restored).compareTo(shield)==0,"actual entity save/load retains shield debit");
        var clone=f.player(false,1); NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone,restored,true));
        h.assertTrue(HighPowerCombat.maxHealthText(clone).equals(maximum) && HighPowerCombat.healthText(clone).equals(maximum),"native death clone restores achieved continuous capacity, not old integer preset");
        h.assertTrue(HighPowerCombat.earnedGrowthPosition(clone).compareTo(q)==0,"death clone retains certified coordinate");
        var saved=restored.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy();
        restored.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("maxHealth",new BigDecimal(maximum).add(BigDecimal.ONE).toPlainString());
        h.assertFalse(HighPowerCombat.enabled(restored),"hot cached validation still rejects a forged maximum");
        restored.getPersistentData().put(HighPowerCombat.DATA_KEY,saved.copy());
        restored.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("growthPosition","5");
        h.assertFalse(HighPowerCombat.enabled(restored),"coordinate above certification is rejected");
        restored.getPersistentData().put(HighPowerCombat.DATA_KEY,saved.copy());
        restored.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putInt("growthCurve",ContinuousHighPowerGrowth.CURVE_VERSION+1);
        h.assertFalse(HighPowerCombat.enabled(restored),"future curve schema rejected");
        restored.getPersistentData().put(HighPowerCombat.DATA_KEY,saved); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void fractional_native_shadow_survives_growth_reload_and_finishes_on_control_pulse(GameTestHelper h) {
        var f=new Fixture(h); var control=f.player(false,1); var p=f.player(true,1);
        control.setHealth(19.5F); f.hp(p,HighPowerLevel.LV1.maxHealth().multiply(new BigDecimal(".975")));
        f.food(control,.3F); f.food(p,.3F); f.ticks(control,53); f.ticks(p,53);
        var before=new BigDecimal(HighPowerCombat.maxHealthText(p)); var hp=HighPowerCombat.healthText(p);
        float shadow=p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).getFloat("referenceHealth");
        h.assertTrue(shadow==control.getHealth(),"pre-growth float trajectory matches native control");
        AbilityStorage.get(p).addExperience(HighPowerSkills.FORKED_ARC,.01);
        var after=new BigDecimal(HighPowerCombat.maxHealthText(p));
        float transported=new BigDecimal(Float.toString(shadow)).multiply(before).divide(after,new MathContext(80)).floatValue();
        control.setHealth(transported);
        h.assertTrue(p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).getFloat("referenceHealth")==transported,"capacity growth transports prior shadow once");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)),"shadow transport itself never heals");
        h.assertTrue(((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer()==3,"mid-cycle timer retained");
        var restored=f.reload(p);
        h.assertTrue(restored.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).getFloat("referenceHealth")==transported,"transported reference persists on reload");
        int ticks=0;
        while(control.getHealth()<20 && ticks<800) {
            control.getFoodData().setFoodLevel(20); restored.getFoodData().setFoodLevel(20);
            control.getFoodData().setSaturation(.3F); restored.getFoodData().setSaturation(.3F);
            f.ticks(control,1); f.ticks(restored,1); ticks++;
            boolean exactFull=new BigDecimal(HighPowerCombat.healthText(restored)).compareTo(after)==0;
            h.assertTrue(exactFull==(control.getHealth()==20),"same genuine completion pulse after growth/reload tick "+ticks);
            f.sameFood(control,restored);
        }
        h.assertTrue(ticks>0 && ticks<800,"fractional control terminates"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void category_exit_suspends_em_benefits_keeps_ordinary_injury_and_never_refills_on_return(GameTestHelper h) {
        var f=new Fixture(h); var p=f.connectedPlayer(); var s=AbilityStorage.get(p);
        s.addExperience(HighPowerSkills.FORKED_ARC,.01); HighPowerCombat.spendShield(p,BigDecimal.ONE);
        var hp=new BigDecimal(HighPowerCombat.healthText(p)); var shield=HighPowerCombat.shield(p); var maximum=HighPowerCombat.maxHealthText(p);
        HighPowerCombat.shieldActive(p,true); s.changeCategoryClassic("teleporter");
        h.assertFalse(HighPowerCombat.shieldActive(p),"stale EM bracer flag is immediately inactive outside category");
        HighPowerCombat.tickShield(p);
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield)==0,"other category never regenerates EM shield");
        h.assertFalse(ElectromasterPrototype.ready(p),"TP cannot invoke retained EM casts");
        var recovery=HighPowerNaturalRecovery.snapshot(p).orElseThrow();
        h.assertTrue(recovery.status().equals("unlearned")&&recovery.exactHpPerSecond().equals("0"),"neutral TP HUD has no proportional recovery before learned313");
        h.assertTrue(p.hurt(p.damageSources().fellOutOfWorld(),1),"ordinary native damage remains available outside EM");
        HighPowerCombat.tick(new EntityTickEvent.Post(p));
        f.amount(HighPowerCombat.healthText(p),hp.subtract(BigDecimal.ONE),"ordinary damage keeps exact one-point injury outside EM");
        s.changeCategoryClassic("electromaster"); s.learn(HighPowerSkills.FORKED_ARC);
        h.assertTrue(HighPowerCombat.maxHealthText(p).equals(maximum),"category roundtrip retains achieved capacity");
        f.amount(HighPowerCombat.healthText(p),hp.subtract(BigDecimal.ONE),"return never heals injury");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield)==0,"return never refills shield"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_growth",timeoutTicks=10)
    public static void explicit_reset_refuses_overflow_then_preserves_fitting_balances_exactly(GameTestHelper h) {
        var f=new Fixture(h); var p=f.player(true,2); var s=AbilityStorage.get(p); s.setLevel(1);
        String before=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).toString();
        h.assertFalse(HighPowerCombat.resetGrowthContract(p),"non-destructive reset refuses current resources above new caps");
        h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).toString()),"refused reset leaves full ledger unchanged");
        f.hp(p,HighPowerLevel.LV1.maxHealth().subtract(BigDecimal.ONE));
        HighPowerCombat.spendShield(p,HighPowerCombat.shield(p).subtract(HighPowerLevel.LV1.maxShield()).add(BigDecimal.ONE));
        String hp=HighPowerCombat.healthText(p),shield=HighPowerCombat.shieldText(p);
        h.assertTrue(HighPowerCombat.resetGrowthContract(p),"explicit reset admits fitting exact balances");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && shield.equals(HighPowerCombat.shieldText(p)),"reset never clamps or heals exact balances");
        h.assertTrue(HighPowerCombat.earnedLevel(p)==1 && HighPowerCombat.earnedGrowthPosition(p).compareTo(BigDecimal.ONE)==0,"explicit reset rebaselines certification");
        h.succeed();
    }

    private static final class Battery implements DevelopmentProcess.Developer {
        double energy=10000;
        public DeveloperType type(){return DeveloperType.PORTABLE;}
        public double energy(){return energy;}
        public double maxEnergy(){return 10000;}
        public boolean tryPullEnergy(double amount){double used=Math.min(energy,amount);energy-=used;return used==amount;}
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players=new ArrayList<>(); final List<io.netty.channel.embedded.EmbeddedChannel> channels=new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(boolean enrolled,int level){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-Growth]"));players.add(p);p.setNoGravity(true);p.getAbilities().instabuild=false;if(enrolled){var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(level);s.learn(HighPowerSkills.FORKED_ARC);h.assertTrue(HighPowerCombat.enroll(p,level),"reviewed growth fixture enrollment "+HighPowerCombat.unsupportedReason(p));}return p;}
        ServerPlayer connectedPlayer(){
            var server=h.getLevel().getServer();var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPGrowthProbe"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            channels.add(new io.netty.channel.embedded.EmbeddedChannel(connection));net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setNoGravity(true);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var position=h.absoluteVec(new net.minecraft.world.phys.Vec3(5,2,5));p.moveTo(position.x,position.y,position.z,0,0);
            var state=AbilityStorage.get(p);state.changeCategoryClassic("electromaster");state.setLevel(1);state.learn(HighPowerSkills.FORKED_ARC);
            h.assertTrue(HighPowerCombat.enroll(p,1),"connected category-transition damage fixture");return p;
        }
        ServerPlayer reload(ServerPlayer original){AbilityStorage.save(original);CompoundTag saved=original.saveWithoutId(new CompoundTag());AbilityStorage.remove(original);var p=player(false,1);p.load(saved);AbilityStorage.get(p);return p;}
        void hp(ServerPlayer p,BigDecimal amount){p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health",CombatAmounts.decimalString(amount));p.setHealth(amount.divide(new BigDecimal(HighPowerCombat.maxHealthText(p)),MathContext.DECIMAL128).multiply(BigDecimal.valueOf(20)).floatValue());}
        void food(ServerPlayer p,float saturation){var food=p.getFoodData();food.setFoodLevel(20);food.setSaturation(saturation);food.setExhaustion(0);CompoundTag data=new CompoundTag();food.addAdditionalSaveData(data);data.putInt("foodTickTimer",0);food.readAdditionalSaveData(data);}
        void ticks(ServerPlayer p,int count){for(int i=0;i<count;i++)p.getFoodData().tick(p);}
        void amount(String actual,BigDecimal expected,String message){h.assertTrue(new BigDecimal(actual).compareTo(expected)==0,message+": "+actual+" expected "+expected);}
        void sameFood(ServerPlayer a,ServerPlayer b){var x=a.getFoodData();var y=b.getFoodData();h.assertTrue(x.getFoodLevel()==y.getFoodLevel() && x.getSaturationLevel()==y.getSaturationLevel() && x.getExhaustionLevel()==y.getExhaustionLevel(),"same native hunger/saturation/exhaustion after growth");}
        void close(){if(closed)return;closed=true;for(var p:players){AbilityStorage.remove(p);var list=h.getLevel().getServer().getPlayerList();if(list.getPlayer(p.getUUID())==p)list.remove(p);else p.discard();}channels.forEach(io.netty.channel.embedded.EmbeddedChannel::finishAndReleaseAll);}
    }
}

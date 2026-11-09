package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW212's optional persisted allocations follow the one authoritative base shield. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerScatterPlateFoundationRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_scatter_plate_foundation";
    private static BigDecimal n(String amount){return new BigDecimal(amount);}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static CompoundTag tag(ServerPlayer p){return p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);}
    private static ScatterPlateShares shares(ServerPlayer p){return ScatterPlateStorage.read(p).orElseThrow();}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void first_learning_partitions_only_existing_base_and_reading_is_not_initialization(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(1);
        h.assertTrue(ScatterPlateStorage.read(p).isEmpty(),"read-only snapshot does not invent shares");
        h.assertFalse(tag(p).contains(ScatterPlateStorage.KEY),"read leaves missing sidecar untouched");
        f.spend(p,n("70"));BigDecimal hp=n(HighPowerCombat.healthText(p)),base=HighPowerCombat.shield(p);
        ScatterPlateShares first=ScatterPlateStorage.ensure(p).orElseThrow();
        eq(h,first.shield(),base,"first enrollment allocates only the existing balance");
        eq(h,first.plate(0).current().add(first.plate(1).current()).add(first.plate(2).current()),base,"exact sum of three currents is base S");
        eq(h,first.plate(0).capacity().add(first.plate(1).capacity()).add(first.plate(2).capacity()),n(HighPowerCombat.maxShieldText(p)),"three capacities conserve one S");
        eq(h,HighPowerCombat.shield(p),base,"initialization awards no base shield");eq(h,n(HighPowerCombat.healthText(p)),hp,"initialization does not heal");
        h.assertTrue(tag(p).getBoolean(ScatterPlateStorage.INITIALIZED_KEY),"durable initialized marker persists");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void ordinary_debits_relearn_category_and_zero_changes_cannot_repartition_broken_plate(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(1);ScatterPlateStorage.ensure(p);
        BigDecimal first=shares(p).plate(0).current();f.spend(p,first);
        ScatterPlateShares broken=shares(p);eq(h,broken.plate(0).current(),BigDecimal.ZERO,"real base cost exhausted first persisted allocation");
        var ability=AbilityStorage.get(p);ability.unlearn(HighPowerSkills.SCATTER_PLATES);ability.learn(HighPowerSkills.SCATTER_PLATES);
        ScatterPlateStorage.ensure(p);ability.category=HighPowerCategories.ELECTROMASTER;
        h.assertTrue(HighPowerCombat.spendShield(p,BigDecimal.ZERO),"zero ordinary shared cost");ability.category=HighPowerCategories.MELTDOWNER;
        h.assertTrue(ScatterPlateStorage.ensure(p).orElseThrow().toDecimalMap().equals(broken.toDecimalMap()),"relearn/category/zero delta retain independent balances");
        h.assertFalse(tag(p).getString("shieldEnabled").equals("true"),"generic damage ledger never becomes a forcefield");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void missing_or_future_sidecar_is_inert_without_blocking_base_damage_or_reset(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.connectedPlayer(1);
        // FakePlayer intentionally rejects every DamageSource. A real admitted player also has
        // sixty ticks of native login protection, which must expire without changing its fields.
        h.runAfterDelay(61,()->{
        p.doTick();ScatterPlateStorage.ensure(p);CompoundTag original=tag(p).getCompound(ScatterPlateStorage.KEY).copy();
        tag(p).remove(ScatterPlateStorage.KEY);
        h.assertTrue(ScatterPlateStorage.ensure(p).isEmpty(),"missing initialized sidecar never reinitializes");
        h.assertFalse(tag(p).contains(ScatterPlateStorage.KEY),"missing saved payload remains missing");
        tag(p).put(ScatterPlateStorage.KEY,original);original.putString("version","999");tag(p).put(ScatterPlateStorage.KEY,original);
        h.assertTrue(ScatterPlateStorage.ensure(p).isEmpty(),"future schema cannot reset into a full plate");
        BigDecimal base=HighPowerCombat.shield(p),hp=n(HighPowerCombat.healthText(p));f.spend(p,BigDecimal.ONE);
        h.assertTrue(HighPowerCombat.enabled(p),"malformed optional sidecar never invalidates base ledger");
        eq(h,HighPowerCombat.shield(p),base.subtract(BigDecimal.ONE),"ordinary resource expense remains available");
        h.assertTrue(p.hurt(p.damageSources().generic(),2),"native ordinary damage accepted with bad optional metadata");
        eq(h,n(HighPowerCombat.healthText(p)),hp.subtract(n("2")),"native ordinary damage retains HP outcome");
        h.assertTrue(HighPowerCombat.resetGrowthContract(p),"optional bad metadata cannot block valid growth reset");
        h.assertTrue(tag(p).getCompound(ScatterPlateStorage.KEY).equals(original),"unknown payload retained byte-for-byte");
        h.assertTrue(tag(p).getBoolean(ScatterPlateStorage.INVALIDATED_KEY),"later matching totals cannot revive rejected metadata");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void real_earned_growth_preserves_each_plate_current_hp_and_cp(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(1);ScatterPlateStorage.ensure(p);f.spend(p,shares(p).plate(0).current());
        List<BigDecimal> currents=shares(p).plates().stream().map(ScatterPlateShares.Plate::current).toList();
        BigDecimal oldMaximum=shares(p).maxShield(),hp=n(HighPowerCombat.healthText(p)),base=HighPowerCombat.shield(p);
        double cp=HighPowerResources.cp(p);var ability=AbilityStorage.get(p);
        ability.addExperience(HighPowerSkills.DUAL_POINT,ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(ability))*.2);
        h.assertTrue(shares(p).maxShield().compareTo(oldMaximum)>0,"real native XP advances certified capacity Q");
        for(int i=0;i<3;i++)eq(h,shares(p).plate(i).current(),currents.get(i),"Q growth never refills plate "+i);
        eq(h,HighPowerCombat.shield(p),base,"capacity growth never increases current base");eq(h,n(HighPowerCombat.healthText(p)),hp,"growth preserves exact injury");
        h.assertTrue(HighPowerResources.cp(p)==cp,"larger CP maximum does not refill current CP");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void administrative_capacity_reduction_refuses_one_overflowing_share_even_when_total_fits(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(2);ScatterPlateStorage.ensure(p);
        // Declared injured fixture permits an LV1 total; real shield spending leaves the last share asymmetric.
        tag(p).putString("health","100");f.spend(p,HighPowerCombat.shield(p).subtract(n("80")));
        eq(h,shares(p).plate(0).current(),BigDecimal.ZERO,"first plate spent");eq(h,shares(p).plate(1).current(),BigDecimal.ZERO,"second plate spent");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(HighPowerLevel.LV1.maxShield())<0,"total balance fits lower base capacity");
        var prior=tag(p).copy();AbilityStorage.get(p).level=1;AbilityStorage.get(p).levelExperience=0;
        h.assertFalse(HighPowerCombat.resetGrowthContract(p),"cannot clip or redistribute last plate into lower individual capacities");
        h.assertTrue(tag(p).equals(prior),"refused operator reset keeps complete exact ledger and sidecar");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void save_reload_and_clone_preserve_shares_and_existing_death_base_policy(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(1),replacement=f.player(1);ScatterPlateStorage.ensure(p);
        f.spend(p,shares(p).plate(0).current().add(n("2")));var before=shares(p).toDecimalMap();
        AbilityStorage.save(p);CompoundTag saved=new CompoundTag();p.saveWithoutId(saved);AbilityStorage.remove(p);p.load(saved);
        h.assertTrue(shares(p).toDecimalMap().equals(before),"native save/load preserves exhausted plate and exact shares");
        HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement,p,false));
        h.assertTrue(shares(replacement).toDecimalMap().equals(before),"nondeath clone copies exact allocation without refill");
        HighPowerCombat.clonePlayer(new PlayerEvent.Clone(replacement,p,true));
        eq(h,shares(replacement).shield(),HighPowerCombat.shield(replacement),"death policy gives no shield beyond base");
        for(int i=0;i<3;i++)eq(h,shares(replacement).plate(i).current(),shares(replacement).plate(i).capacity(),"existing full-base death policy fills allocation "+i+" using actual gain only");
        h.assertFalse(HighPowerCombat.shieldActive(replacement),"clone cannot restore an active defense");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void md_recovery_waits_sixty_ticks_and_shares_exactly_one_existing_budget(GameTestHelper h){
        Fixture f=new Fixture(h);ServerPlayer p=f.player(1);ScatterPlateStorage.ensure(p);
        BigDecimal budget=n(HighPowerCombat.maxShieldText(p)).divide(n("100"));f.spend(p,budget.multiply(n("4")));
        BigDecimal spent=HighPowerCombat.shield(p);long started=p.level().getGameTime();
        HighPowerCombat.tickShield(p);eq(h,HighPowerCombat.shield(p),spent,"same-spend tick cannot recover");
        h.runAfterDelay(59,()->{
            HighPowerCombat.tickShield(p);eq(h,HighPowerCombat.shield(p),spent,"fifty-nine ticks are insufficient");
            h.runAfterDelay(1,()->{
                h.assertTrue(p.level().getGameTime()-started==60,"native game clock reaches exactly sixty ticks");
                HighPowerCombat.tickShield(p);eq(h,HighPowerCombat.shield(p),spent.add(budget),"one unchanged one-percent base budget");
                eq(h,shares(p).shield(),spent.add(budget),"only actual recovered base amount enters shares");
                HighPowerCombat.tickShield(p);eq(h,HighPowerCombat.shield(p),spent.add(budget),"same-tick calls cannot recover twice");
                h.assertFalse(tag(p).contains(MagneticDomainReserve.KEY),"MD recovery cannot create or fill EM reserve");h.succeed();
            });
        });
    }

    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>(),connected=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}
            public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
        });}
        ServerPlayer player(int level){
            return prepare(new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"[HP-PlateLedger]")),level);
        }
        ServerPlayer connectedPlayer(int level){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPPlateLedger"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channels.add(new EmbeddedChannel(connection));NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie);connected.add(p);p.setGameMode(GameType.SURVIVAL);
            p.setPos(h.absoluteVec(new Vec3(6,2,6)));p.connection.resetPosition();return prepare(p,level);
        }
        ServerPlayer prepare(ServerPlayer p,int level){
            players.add(p);p.setNoGravity(true);p.getAbilities().instabuild=false;
            var ability=AbilityStorage.get(p);ability.changeCategoryClassic(HighPowerCategories.MELTDOWNER);ability.setLevel(level);
            ability.learn(HighPowerSkills.DUAL_POINT);ability.learn(HighPowerSkills.SCATTER_PLATES);ability.setActivateState(true);ability.overloadFine=true;
            h.assertTrue(HighPowerCombat.enroll(p,level),"reviewed native plate-ledger fixture");HighPowerResources.initializeBudget(p);return p;
        }
        void spend(ServerPlayer p,BigDecimal amount){var ability=AbilityStorage.get(p);String category=ability.category;ability.category=HighPowerCategories.ELECTROMASTER;
            try{h.assertTrue(HighPowerCombat.spendShield(p,amount),"existing EM-only base debit");}finally{ability.category=category;}}
        void close(){if(closed)return;closed=true;for(var p:players){ScatterPlates.remove(p);AbilityStorage.remove(p);if(connected.contains(p))h.getLevel().getServer().getPlayerList().remove(p);else p.discard();}channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}

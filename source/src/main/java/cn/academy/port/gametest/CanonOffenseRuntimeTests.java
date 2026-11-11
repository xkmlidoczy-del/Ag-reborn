package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.*;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Numeric, material, lifecycle and real AI-duration tests for the new offensive branches. */
@GameTestHolder("academy_canon_offense")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CanonOffenseRuntimeTests {
    private static final String TEMPLATE="offense_room",BATCH="canon_offense";
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel world))return;
        var tag=new CompoundTag();var size=new ListTag();for(int n:new int[]{64,16,64})size.add(IntTag.valueOf(n));
        var pos=new ListTag();for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);
        var blocks=new ListTag();blocks.add(cell);var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new ListTag());
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_canon_offense",TEMPLATE))
                .load(world.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    private static BigDecimal hp(Husk target){return new BigDecimal(HighPowerCombat.healthText(target).replace(",",""));}
    private static void near(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<.05,message+": "+a+" / "+b);}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 at;final List<Husk> targets=new ArrayList<>();
        final CanonOffenseSkills.Spec spec;
        Fixture(GameTestHelper h,CanonOffenseSkills.Spec spec,int index){
            this.h=h;this.spec=spec;var world=h.getLevel();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"OF"+UUID.randomUUID().toString().substring(0,8)),false);
            p=new ServerPlayer(world.getServer(),world,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
            world.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);
            at=h.absoluteVec(new Vec3(7+(index%4)*14,3,4+(index/4)*14));
            for(int x=-5;x<=5;x++)for(int z=-2;z<=9;z++)world.setBlockAndUpdate(BlockPos.containing(at.add(x,-1,z)),Blocks.STONE.defaultBlockState());
            for(int x=-5;x<=5;x++)for(int y=0;y<5;y++)world.setBlockAndUpdate(BlockPos.containing(at.add(x,y,9)),Blocks.STONE.defaultBlockState());
            place();var state=AbilityStorage.get(p);state.changeCategoryClassic(spec.category());state.setLevel(5);
            for(var skill:SkillCatalog.REGISTERED)if(skill.category().equals(spec.category()))state.learn(skill.id());
            state.activated=true;state.overloadFine=true;select(spec.id());
            h.assertTrue(HighPowerCombat.enroll(p,5),"explicit player enrollment");HighPowerResources.initializeBudget(p);
            var scoreboard=world.getScoreboard();var team=scoreboard.getPlayerTeam("acaOffenseQA");if(team==null){team=scoreboard.addPlayerTeam("acaOffenseQA");team.setAllowFriendlyFire(false);}
            scoreboard.addPlayerToTeam(p.getScoreboardName(),team);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,32));
        }
        void place(){p.moveTo(at,0,0);p.setYHeadRot(0);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();}
        void select(String id){var s=AbilityStorage.get(p);h.assertTrue(s.presets.edit(0,0,id,s::learned),"real preset binding "+id);}
        Husk target(double x,double y,double z){
            var t=EntityType.HUSK.create(h.getLevel());t.setNoAi(true);t.setNoGravity(true);t.moveTo(at.add(x,y,z),0,0);t.setOnGround(y==0);
            h.getLevel().addFreshEntity(t);h.assertTrue(HighPowerCombat.enroll(t,5),"real hostile ledger");targets.add(t);return t;
        }
        void body(){
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CanonLiquidEntities.DENSE_MEDIUM.get(),4));
            select("liquid_scout");h.assertTrue(CanonLiquidSkills.cast(p,"liquid_scout"),"real medium materializes body");
            select("liquid_pause");h.assertTrue(CanonLiquidSkills.cast(p,"liquid_pause"),"real body pause");select(spec.id());
        }
        void close(){for(var t:targets)t.discard();p.server.getPlayerList().remove(p);p.discard();channel.finishAndReleaseAll();}
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=700)
    public static void all_sixteen_attacks_resolve_once_with_player_tiers_and_real_materials(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();var counts=new HashMap<UUID,Integer>();var failures=new ArrayList<String>();
        for(int i=0;i<CanonOffenseSkills.ALL.size();i++)fixtures.add(new Fixture(h,CanonOffenseSkills.ALL.get(i),i));
        var old=CanonOffense.observer;
        CanonOffense.observer=r->{old.accept(r);if(!r.phase().equals("RESOLVE"))return;
            var f=fixtures.stream().filter(v->v.p.getUUID().equals(r.owner())).findFirst().orElse(null);if(f==null)return;
            counts.merge(r.owner(),1,Integer::sum);
            double expected=1_000_000*CanonEmission.levelFactor(f.spec.level()).doubleValue()*.984;
            if(Math.abs(r.damage()-expected)>.05)failures.add(r.skill()+" damage "+r.damage()+" / "+expected);
            double cost=.1*f.spec.cp();if(Math.abs(r.cp()-cost)>1e-6||Math.abs(r.cpBefore()-r.cpAfter()-cost)>1e-5)failures.add(r.skill()+" CP debit");
            if(AbilityStorage.get(f.p).cooldowns.getOrDefault(r.skill(),0)!=f.spec.cooldown())failures.add(r.skill()+" cooldown");
        };
        // Expanded areas must not share targets with adjacent fixtures. Trigger and retire one fixture at a time.
        for(int index=0;index<fixtures.size();index++){
            var f=fixtures.get(index);int start=65+index*36;
            h.runAfterDelay(start,()->{f.place();if(f.spec.body())f.body();
                f.target(0,f.spec.shape()==CanonOffenseSkills.Shape.AIR_POINT?1:0,f.spec.body()?3.2:5);
                h.assertTrue(CanonOffense.cast(f.p,f.spec.id()),"cast accepted: "+f.spec.id());});
            h.runAfterDelay(start+32,()->{
                try{if(f.spec.category().equals("paper_psychokinesis")){
                    int sheets=f.spec.id().equals("paper_fold_fan")?3:f.spec.id().equals("paper_piercing_lance")?2:4;
                    h.assertTrue(f.p.getMainHandItem().getCount()==32-sheets,"real paper debit "+f.spec.id());
                }}finally{f.close();}
            });
        }
        h.runAfterDelay(65+fixtures.size()*36,()->{try{
            h.assertTrue(failures.isEmpty(),"settlement issues: "+failures);
            h.assertTrue(counts.size()==16&&counts.values().stream().allMatch(n->n==1),"all 16 actual attacks settle exactly once: "+counts.size());h.succeed();
        }finally{CanonOffense.observer=old;}});

    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=180)
    public static void changing_paper_during_warmup_cancels_without_damage_or_debit(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("paper_fold_fan"),0);var t=f.target(0,0,5);var before=hp(t);
        h.runAfterDelay(65,()->{f.place();h.assertTrue(CanonOffense.cast(f.p,f.spec.id()),"warmup accepted");f.p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));});
        h.runAfterDelay(90,()->{try{h.assertTrue(hp(t).compareTo(before)==0&&!CanonOffense.busy(f.p),"no cancelled hit");near(h,HighPowerResources.cp(f.p),100000,"no reserve debit");h.succeed();}finally{f.close();}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=180)
    public static void inserting_cover_during_warmup_prevents_the_hit(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("aero_pressure_bullet"),0);var t=f.target(0,0,5);var before=hp(t);
        h.runAfterDelay(65,()->{f.place();h.assertTrue(CanonOffense.cast(f.p,f.spec.id()),"warmup accepted");
            for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(BlockPos.containing(f.at.add(0,y,2)),Blocks.STONE.defaultBlockState());});
        h.runAfterDelay(90,()->{try{h.assertTrue(hp(t).compareTo(before)==0&&!CanonOffense.busy(f.p),"cover blocks actual damage");near(h,HighPowerResources.cp(f.p),100000,"no refused debit");h.succeed();}finally{f.close();}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=180)
    public static void paper_fan_shares_one_budget_across_three_targets_and_spends_three_sheets(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("paper_fold_fan"),0);
        for(double x:new double[]{-1,0,1})f.target(x,0,5);var before=f.targets.stream().map(CanonOffenseRuntimeTests::hp).reduce(BigDecimal.ZERO,BigDecimal::add);
        h.runAfterDelay(65,()->{f.place();h.assertTrue(CanonOffense.cast(f.p,f.spec.id()),"real fan cast");});
        h.runAfterDelay(95,()->{try{
            var after=f.targets.stream().map(CanonOffenseRuntimeTests::hp).reduce(BigDecimal.ZERO,BigDecimal::add);
            near(h,before.subtract(after).doubleValue(),800000*.984,"whole-cast conservation");
            h.assertTrue(f.p.getMainHandItem().getCount()==29,"three sheets, not three per target");h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=180)
    public static void buoyant_impact_creates_a_real_airborne_window_for_reversal(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("float_buoyant_strike"),0);var t=f.target(0,0,5);var before=hp(t);
        h.runAfterDelay(65,()->{f.place();t.setNoAi(false);t.setNoGravity(false);h.assertTrue(CanonOffense.cast(f.p,f.spec.id()),"buoyant impact starts");});
        h.runAfterDelay(76,()->{f.place();h.assertTrue(t.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)&&!t.onGround(),"real airborne control window");
            f.select("float_reversal_drop");h.assertTrue(CanonOffense.cast(f.p,"float_reversal_drop"),"reversal chain starts");});
        h.runAfterDelay(94,()->{try{
            near(h,before.subtract(hp(t)).doubleValue(),1_400_000*.984,"two separate conserved hits");
            h.assertTrue(!t.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION),"reversal ends upward buoyancy");h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=180)
    public static void a_grounded_drop_and_a_missing_liquid_body_are_refused(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("float_reversal_drop"),0);f.target(0,0,5);
        var l=new Fixture(h,CanonOffenseSkills.find("liquid_touch_spike"),1);l.target(0,0,3.2);
        h.runAfterDelay(65,()->{try{f.place();l.place();h.assertTrue(!CanonOffense.cast(f.p,f.spec.id()),"grounded target refused");
            h.assertTrue(!CanonOffense.cast(l.p,l.spec.id()),"missing real body refused");h.succeed();}finally{f.close();l.close();}});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=960)
    public static void mental_control_survives_the_old_deadline_and_ends_after_ten_times_as_long(GameTestHelper h){
        var f=new Fixture(h,CanonOffenseSkills.find("mental_neural_sting"),0);var t=f.target(0,0,5);t.setNoAi(false);
        h.assertTrue(SkillTuning.integer("mental_disrupt","duration_ticks")==800&&SkillTuning.integer("mental_out_crowd","duration_ticks")==1400
                &&SkillTuning.integer("mental_puppet","duration_ticks")==2000,"requested x10 durations");
        h.runAfterDelay(65,()->{f.place();f.select("mental_disrupt");t.setTarget(f.p);h.assertTrue(CanonSkillsCombatA.cast(f.p,"mental_disrupt"),"real mental lease acquired");});
        h.runAfterDelay(160,()->{h.assertTrue(CanonSkillsCombatA.mentalTargetLeased(t),"control remains after the old 80-tick deadline");t.setTarget(f.p);});
        h.runAfterDelay(162,()->h.assertTrue(t.getTarget()==null,"real AI tick clears reacquisition while controlled"));
        h.runAfterDelay(870,()->{try{h.assertTrue(!CanonSkillsCombatA.mentalTargetLeased(t),"lease naturally ends after 800 ticks");t.setTarget(f.p);h.assertTrue(t.getTarget()==f.p,"target can reacquire after expiry");h.succeed();}finally{f.close();}});
    }
}

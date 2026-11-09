package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.RailThunderArrayRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW151 real authenticated inputs, native controllers, exact ledgers and callback/lifecycle fencing.
 * Each96m controller gets its own batch: vanilla template spacing37m cannot isolate simultaneous query fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRailThunderArrayRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_rail_thunder_array";
    private static int pvpFixtures;private static boolean originalPvp;
    private HighPowerRailThunderArrayRuntimeTests(){}
    private static BigDecimal hp(LivingEntity target){return new BigDecimal(HighPowerCombat.healthText(target));}
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+"; actual="+actual+" expected="+expected);}
    private static void same(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.compare(actual,expected)==0,why+"; actual="+actual+" expected="+expected);}

    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_nine_native_rays_have_one_immutable_whole_cast_budget",timeoutTicks=80)
    public static void rail_nine_native_rays_have_one_immutable_whole_cast_budget(GameTestHelper h){
        Fixture f=new Fixture(h);float[] directions={0,-30,30};List<Zombie> targets=new ArrayList<>();for(float yaw:directions)targets.add(f.target(yaw,9));
        List<BigDecimal> before=targets.stream().map(HighPowerRailThunderArrayRuntimeTests::hp).toList();BigDecimal budget=RailThunderArrayRules.budget(HighPowerGrowth.values(f.p).damage());Observe observer=new Observe(f.p);f.hook(observer);double cp=f.cp();
        f.prepare(directions,()->{
            long started=f.now();h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"fourth ordinary tap commits nine-slot controller");
            exact(h,RailThunderArray.castBudget(f.p),budget,"captured damage budget");h.assertFalse(RailThunderArray.press(f.p,f.wire(5)),"active mode is exclusive");
            h.runAfterDelay(47,()->{
                h.assertFalse(RailThunderArray.active(f.p),"nine finite slots end");same(h,f.cp(),cp-1,"entire volley pays once");
                BigDecimal loss=BigDecimal.ZERO;for(int i=0;i<targets.size();i++)loss=loss.add(before.get(i).subtract(hp(targets.get(i))));exact(h,loss,budget,"native target sum is4D, never4D per ray");
                h.assertTrue(observer.times.equals(List.of(started,started+1,started+2,started+22,started+23,started+24,started+44,started+45,started+46)),"actual incoming callbacks follow0/1/2,22/23/24,44/45/46");
                h.assertTrue(observer.physical==9&&observer.electrical==0,"all projected rails use real native thrown source");
                same(h,AbilityStorage.get(f.p).exp(RailThunderArray.ID),(double).005F,"productive whole cast awards exactly once");h.succeed();
            });
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_one_target_admission_per_round_keeps_other_shares_lost",timeoutTicks=80)
    public static void rail_one_target_admission_per_round_keeps_other_shares_lost(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);List<BigDecimal> portions=RailThunderArrayRules.split(RailThunderArrayRules.budget(HighPowerGrowth.values(f.p).damage()),9);Observe observer=new Observe(f.p);f.hook(observer);
        f.prepare(new float[]{0,-1,1},()->{h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"near-parallel axes accepted");h.runAfterDelay(47,()->{
            exact(h,before.subtract(hp(target)),portions.get(0).add(portions.get(3)).add(portions.get(6)),"six overlapping shares lost rather than redistributed");
            h.assertTrue(observer.times.size()==3&&observer.times.get(1)-observer.times.get(0)==22&&observer.times.get(2)-observer.times.get(1)==22,"native iframe and20tick same target cadence retained");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_frozen_slots_drop_without_reacquisition_or_redistribution",timeoutTicks=95)
    public static void rail_hold_frozen_slots_drop_without_reacquisition_or_redistribution(GameTestHelper h){
        Fixture f=new Fixture(h);f.nativePlayerTicks();Zombie first=f.target(0,7),second=f.target(-10,8);BigDecimal a=hp(first),b=hp(second),budget=RailThunderArrayRules.budget(HighPowerGrowth.values(f.p).damage());double cp=f.cp();Observe observer=new Observe(f.p);f.hook(observer);
        f.prepare(new float[]{0,-10,10},()->{h.runAfterDelay(8,()->{
            RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.phase(f.p)==RailThunderArray.HOLD&&RailThunderArray.pulseCount(f.p)==1,"fourth held press selects only hold mode; "+candidateSummary(f));h.assertTrue(RailThunderArray.lockedCount(f.p)==2,"two original frozen target slots");
            Vec3 original=second.position();second.setPos(original.add(0,0,-20));RailThunderArray.tick(f.p);
            h.runAfterDelay(1,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.lockedCount(f.p)==1,"departed target slot retired");second.setPos(original);});
            h.runAfterDelay(41,()->{
                h.assertTrue(RailThunderArray.pulseCount(f.p)==3&&observer.physical==0&&observer.electrical==4,"three pulses plus only first pulse second target, never ray mode");
                List<BigDecimal> pulseBudgets=RailThunderArrayRules.split(budget,3);BigDecimal expectedA=BigDecimal.ZERO;for(BigDecimal pulse:pulseBudgets)expectedA=expectedA.add(RailThunderArrayRules.split(pulse,2).get(0));
                exact(h,a.subtract(hp(first)),expectedA,"remaining target retains original half-pulse slots");exact(h,b.subtract(hp(second)),RailThunderArrayRules.split(pulseBudgets.get(0),2).get(1),"returning target never reacquired");
                exact(h,f.netDebit(cp),BigDecimal.ONE,"three exact binary paid shares consume one CP after observed native regeneration credits");h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"hold release ends immediately");h.assertFalse(RailThunderArray.active(f.p),"release cleanup");h.succeed();
            });
        });});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_is_finite_and_release_never_creates_tap",timeoutTicks=90)
    public static void rail_hold_is_finite_and_release_never_creates_tap(GameTestHelper h){
        Fixture f=new Fixture(h);f.nativePlayerTicks();f.target(0,7);Observe observer=new Observe(f.p);f.hook(observer);double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.pulseCount(f.p)==1,"first hold pulse");});
            h.runAfterDelay(69,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"hold expires within60ticks");h.assertTrue(observer.electrical==3&&observer.physical==0,"exactly three net pulses; electrical="+observer.electrical+" physical="+observer.physical+" targets="+observer.targets+" "+candidateSummary(f));h.assertFalse(RailThunderArray.release(f.p,f.wire(4)),"late release cannot revive as tap");exact(h,f.netDebit(cp),BigDecimal.ONE,"finite hold pays exactly one CP including actual native regeneration");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_empty_volley_is_free_and_does_not_award_mastery",timeoutTicks=80)
    public static void rail_empty_volley_is_free_and_does_not_award_mastery(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();f.prepare(new float[]{0,-35,35},()->{h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"empty ray sequence may display misses");h.runAfterDelay(47,()->{
            same(h,f.cp(),cp,"no eligible emission opportunity means no payment");same(h,AbilityStorage.get(f.p).exp(RailThunderArray.ID),0,"marks and misses give no mastery");h.assertFalse(RailThunderArray.active(f.p),"empty sequence finite");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_solid_wall_is_first_blocker_and_never_modified",timeoutTicks=40)
    public static void rail_solid_wall_is_first_blocker_and_never_modified(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,3));
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)h.getLevel().setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{RailThunderArray.release(f.p,f.wire(4));h.runAfterDelay(3,()->{
            exact(h,hp(target),before,"native ray never pierces solid block");same(h,f.cp(),cp,"blocked opportunities free");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"wall remains unchanged");RailThunderArray.abort(f.p);h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_passive_living_blocker_never_allows_shooting_through",timeoutTicks=40)
    public static void rail_passive_living_blocker_never_allows_shooting_through(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);Villager blocker=EntityType.VILLAGER.create(h.getLevel());blocker.setNoAi(true);blocker.setNoGravity(true);blocker.setPos(f.p.position().add(0,0,3));h.getLevel().addFreshEntity(blocker);f.entities.add(blocker);BigDecimal before=hp(target);float passive=blocker.getHealth();double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{RailThunderArray.release(f.p,f.wire(4));h.runAfterDelay(3,()->{
            exact(h,hp(target),before,"nonenemy front body stops physical ray");same(h,blocker.getHealth(),passive,"passive blocker untouched");same(h,f.cp(),cp,"refused blocker free");RailThunderArray.abort(f.p);h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_query_cap_and_target_limit_are_fail_closed",timeoutTicks=35)
    public static void rail_hold_query_cap_and_target_limit_are_fail_closed(GameTestHelper h){
        Fixture f=new Fixture(h);for(int i=0;i<64;i++)f.targetAt(new Vec3((i%8)*.15,0,6+(i/8)*.15));double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"capped64 candidate scan cannot freeze ambiguous set");same(h,f.cp(),cp,"capped set is free");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_freezes_at_most_sixteen_targets",timeoutTicks=40)
    public static void rail_hold_freezes_at_most_sixteen_targets(GameTestHelper h){
        Fixture f=new Fixture(h);List<Zombie> targets=new ArrayList<>();for(int i=0;i<20;i++)targets.add(f.targetAt(new Vec3((i%4)*.3,0,5+(i/4)*.7)));Observe observer=new Observe(f.p);f.hook(observer);
        f.prepare(new float[]{0,-1,1},()->{h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.lockedCount(f.p)==16&&observer.electrical==16,"only16 original slots and16 actual native hits");RailThunderArray.abort(f.p);h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_marker_rate_duplicate_direction_and_crouch_clear_are_free",timeoutTicks=30)
    public static void rail_marker_rate_duplicate_direction_and_crouch_clear_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();h.assertTrue(RailThunderArray.press(f.p,f.wire(1))&&RailThunderArray.release(f.p,f.wire(1)),"first observed direction");
        h.assertFalse(RailThunderArray.press(f.p,f.wire(2)),"same-clock mark burst rejected");h.runAfterDelay(4,()->{
            h.assertTrue(RailThunderArray.press(f.p,f.wire(3)),"fresh mark request after4ticks");h.assertFalse(RailThunderArray.release(f.p,f.wire(3)),"identical direction explicitly refused");h.assertTrue(RailThunderArray.markCount(f.p)==1,"duplicate did not create axis");
            h.runAfterDelay(4,()->{f.p.setShiftKeyDown(true);h.assertTrue(RailThunderArray.press(f.p,f.wire(4)),"crouch gesture clears preparation");h.assertFalse(RailThunderArray.active(f.p),"marks cleared");same(h,f.cp(),cp,"all preparation is free");same(h,AbilityStorage.get(f.p).exp(RailThunderArray.ID),0,"no marker mastery");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_preparation_expires_at_two_hundred_and_preserves_velocity",timeoutTicks=215)
    public static void rail_preparation_expires_at_two_hundred_and_preserves_velocity(GameTestHelper h){
        Fixture f=new Fixture(h);h.assertTrue(RailThunderArray.press(f.p,f.wire(1))&&RailThunderArray.release(f.p,f.wire(1)),"first free mark");
        h.runAfterDelay(199,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.active(f.p),"199ticks still prepared");});h.runAfterDelay(200,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"200ticks exact expiry");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_movement_and_preset_invalidation_preserve_velocity",timeoutTicks=30)
    public static void rail_movement_and_preset_invalidation_preserve_velocity(GameTestHelper h){
        Fixture f=new Fixture(h);RailThunderArray.press(f.p,f.wire(1));RailThunderArray.release(f.p,f.wire(1));Vec3 velocity=new Vec3(.123,.045,-.067);f.p.setDeltaMovement(velocity);f.p.setPos(f.p.position().add(1.001,0,0));RailThunderArray.tick(f.p);
        h.assertFalse(RailThunderArray.active(f.p),"more than1m cancels preparation");h.assertTrue(f.p.getDeltaMovement().equals(velocity),"controller never freezes or rewrites velocity");f.p.setDeltaMovement(Vec3.ZERO);f.p.connection.resetPosition();
        h.runAfterDelay(4,()->{RailThunderArray.press(f.p,f.wire(2));RailThunderArray.release(f.p,f.wire(2));AbilityStorage.get(f.p).presets.edit(0,1,"forked_arc",id->true);RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"any preset revision invalidates captured marks");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_pre_hook_reentrant_release_and_target_move_keep_paid_miss",timeoutTicks=35)
    public static void rail_pre_hook_reentrant_release_and_target_move_keep_paid_miss(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{f.hook(new SkillMutation(f.p,()->{
            h.assertFalse(RailThunderArray.release(f.p,f.wire(4)),"reentrant release cannot double emit");RailThunderArray.tick(f.p);target.setPos(target.position().add(.25,0,0));
        }));h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"paid attempt remains committed");exact(h,hp(target),before,"callback movement fails guard before hurt");same(h,f.cp(),cp-1,"already reserved opportunity retains one CP spent miss");same(h,AbilityStorage.get(f.p).exp(RailThunderArray.ID),0,"paid native refusal no mastery");RailThunderArray.abort(f.p);h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_native_incoming_callback_wall_prevents_exact_ledger_application",timeoutTicks=35)
    public static void rail_native_incoming_callback_wall_prevents_exact_ledger_application(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{f.hook(new IncomingMutation(f.p,target,()->{BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,3));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());}));
            RailThunderArray.release(f.p,f.wire(4));exact(h,hp(target),before,"new native callback occlusion refuses before ledger");same(h,f.cp(),cp-1,"incoming callback retains paid attempt");RailThunderArray.abort(f.p);h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_real_post_hit_abort_stops_siblings_and_stamps_reload_fence",timeoutTicks=35)
    public static void rail_real_post_hit_abort_stops_siblings_and_stamps_reload_fence(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{f.hook(new PostAction(target,()->RailThunderArray.abort(f.p)));h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"first real hit accepted before callback abort");
            h.assertTrue(hp(target).compareTo(before)<0,"real native partial hit stays committed");same(h,f.cp(),cp-1,"one paid partial cast");h.assertFalse(RailThunderArray.active(f.p),"callback cleaned controller");
            long fence=f.p.getPersistentData().getCompound("academy:electromaster_prototype").getLong(RailThunderArray.RECAST);h.assertTrue(fence==f.now()+4,"exact persistent postcast4tick fence");
            AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(RailThunderArray.press(f.p,f.wire(6)),"state reload and new epoch cannot bypass persistent fence");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_owner_death_and_session_retirement_cancel_without_replay",timeoutTicks=35)
    public static void rail_owner_death_and_session_retirement_cancel_without_replay(GameTestHelper h){
        Fixture f=new Fixture(h);f.target(0,7);f.prepare(new float[]{0,-1,1},()->{h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"paid start");double cp=f.cp();String old=f.wire(4);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();RailThunderArray.tick(f.p);
            h.assertFalse(RailThunderArray.active(f.p),"retired server session cleans paid controller");h.assertFalse(RailThunderArray.release(f.p,old),"old epoch release cannot revive");same(h,f.cp(),cp,"session cancellation never refunds");f.p.kill();h.assertFalse(RailThunderArray.press(f.p,f.wire(8)),"dead owner cannot prepare");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_release_pays_only_first_assigned_share",timeoutTicks=35)
    public static void rail_hold_release_pays_only_first_assigned_share(GameTestHelper h){
        Fixture f=new Fixture(h);f.target(0,7);double cp=f.cp();f.prepare(new float[]{0,-1,1},()->{h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.pulseCount(f.p)==1,"one productive pulse");
            same(h,f.cp(),cp-RailThunderArrayRules.pulseCost(0),"assigned first binary fraction only");h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"held release cancels remaining two shares");same(h,f.cp(),cp-RailThunderArrayRules.pulseCost(0),"future unpaid shares are lost, not charged");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_learning_binding_save_never_refills_exact_pools_and_lv4_refuses",timeoutTicks=35)
    public static void rail_learning_binding_save_never_refills_exact_pools_and_lv4_refuses(GameTestHelper h){
        Fixture f=new Fixture(h),low=new Fixture(h,4);var state=AbilityStorage.get(f.p);state.unlearn(RailThunderArray.ID);
        CompoundTag ledger=f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);ledger.putString("health","1234567.89");ledger.putString("shield","987654.321");f.p.getPersistentData().put(HighPowerCombat.DATA_KEY,ledger);
        CompoundTag cpTag=f.p.getPersistentData().getCompound("academy:electromaster_prototype");cpTag.putDouble("cp",37.125);f.p.getPersistentData().put("academy:electromaster_prototype",cpTag);
        BigDecimal health=hp(f.p),shield=HighPowerCombat.shield(f.p);double cp=f.cp();state.learn(RailThunderArray.ID);state.presets.edit(0,0,RailThunderArray.ID,id->true);AbilityStorage.save(f.p);ElectromasterPrototype.initializeBudget(f.p);
        exact(h,hp(f.p),health,"learning and binding preserve every HP digit");exact(h,HighPowerCombat.shield(f.p),shield,"learning and binding preserve shield pool");same(h,f.cp(),cp,"initialize never refills existing CP");AbilityStorage.remove(f.p);f.epoch();exact(h,hp(f.p),health,"save reload preserves HP");exact(h,HighPowerCombat.shield(f.p),shield,"save reload preserves shield");same(h,f.cp(),cp,"save reload preserves CP");
        h.assertFalse(RailThunderArray.press(low.p,low.wire(1)),"LV4 cannot use151 even with fixture learned bit and bind");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_hold_never_admits_targets_behind_all_forward_axes",timeoutTicks=35)
    public static void rail_hold_never_admits_targets_behind_all_forward_axes(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie rear=f.targetAt(new Vec3(0,0,-1));BigDecimal before=hp(rear);double cp=f.cp();
        f.prepare(new float[]{0,-1,1},()->{h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"rear radius overlap is not a forward corridor target");exact(h,hp(rear),before,"rear target untouched");same(h,f.cp(),cp,"rear-only preparation free");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_screen_and_category_change_cancel_preparation",timeoutTicks=35)
    public static void rail_screen_and_category_change_cancel_preparation(GameTestHelper h){
        Fixture f=new Fixture(h);RailThunderArray.press(f.p,f.wire(1));RailThunderArray.release(f.p,f.wire(1));
        f.p.containerMenu=net.minecraft.world.inventory.ChestMenu.threeRows(99,f.p.getInventory());RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"server-observed container screen cancels");f.p.containerMenu=f.p.inventoryMenu;
        h.runAfterDelay(4,()->{RailThunderArray.press(f.p,f.wire(2));RailThunderArray.release(f.p,f.wire(2));AbilityStorage.get(f.p).category="meltdowner";RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"category switch invalidates marks");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_current_server_pvp_gate_is_checked_before_first_payment",timeoutTicks=110)
    public static void rail_current_server_pvp_gate_is_checked_before_first_payment(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);other.p.setPos(f.p.position().add(0,0,7));other.p.connection.resetPosition();BigDecimal before=hp(other.p);double cp=f.cp();
        h.runAfterDelay(72,()->f.prepare(new float[]{0,-1,1},()->{boolean old=f.p.server.isPvpAllowed();
            try{f.p.server.setPvpAllowed(false);h.assertTrue(RailThunderArray.release(f.p,f.wire(4)),"finite refused ray still consumes its slot");exact(h,hp(other.p),before,"current PvP gate refuses selected player before hurt");same(h,f.cp(),cp,"PvP refusal before emission is free");}
            finally{RailThunderArray.abort(f.p);f.p.server.setPvpAllowed(old);}h.succeed();}));
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_new_team_retires_an_already_paid_hold_target",timeoutTicks=120)
    public static void rail_new_team_retires_an_already_paid_hold_target(GameTestHelper h){
        Fixture f=new Fixture(h),other=new Fixture(h);other.p.setPos(f.p.position().add(0,0,7));other.p.connection.resetPosition();f.enablePvp();double cp=f.cp();
        h.runAfterDelay(72,()->f.prepare(new float[]{0,-1,1},()->h.runAfterDelay(8,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.pulseCount(f.p)==1,"actual first native player pulse before team change");BigDecimal after=hp(other.p);
            var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("ra"+UUID.randomUUID().toString().substring(0,10));team.setAllowFriendlyFire(false);board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(other.p.getScoreboardName(),team);f.teams.add(team);
            h.runAfterDelay(1,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"current allied status retires the last frozen slot");exact(h,hp(other.p),after,"team change prevents later damage");same(h,f.cp(),cp-RailThunderArrayRules.pulseCost(0),"only original paid pulse remains charged");h.succeed();});
        })));
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_same_uuid_entity_reload_cannot_inherit_a_frozen_slot",timeoutTicks=75)
    public static void rail_same_uuid_entity_reload_cannot_inherit_a_frozen_slot(GameTestHelper h){
        Fixture f=new Fixture(h);f.nativePlayerTicks();Zombie continuing=f.target(0,7);Zombie original=f.target(-10,8);f.prepare(new float[]{0,-10,10},()->h.runAfterDelay(8,()->{
            RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.lockedCount(f.p)==2,"two live original objects");CompoundTag saved=original.saveWithoutId(new CompoundTag());original.discard();Zombie replacement=EntityType.HUSK.create(h.getLevel());replacement.load(saved);h.getLevel().addFreshEntity(replacement);f.entities.add(replacement);BigDecimal preserved=hp(replacement),continuingBefore=hp(continuing);
            h.runAfterDelay(1,()->{RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.lockedCount(f.p)==1,"removed object permanently retires its frozen slot");});
            h.runAfterDelay(21,()->{RailThunderArray.tick(f.p);exact(h,hp(replacement),preserved,"sameUUID replacement cannot receive next slot");h.assertTrue(hp(continuing).compareTo(continuingBefore)<0,"original remaining target really received second native pulse");RailThunderArray.abort(f.p);h.succeed();});
        }));
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_target_leaving_ninety_six_metres_retires_without_following_or_reacquiring",timeoutTicks=45)
    public static void rail_target_leaving_ninety_six_metres_retires_without_following_or_reacquiring(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);f.prepare(new float[]{0,-1,1},()->h.runAfterDelay(8,()->{
            RailThunderArray.tick(f.p);h.assertTrue(RailThunderArray.pulseCount(f.p)==1,"initial in-range target hit; "+candidateSummary(f));Vec3 original=target.position();BigDecimal after=hp(target);double cp=f.cp();target.setPos(f.p.getEyePosition().add(0,0,96.01));
            h.runAfterDelay(1,()->{RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"outside96m immediately ends last slot");target.setPos(original);RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"return cannot recreate context");exact(h,hp(target),after,"out-of-range target takes no later damage");same(h,f.cp(),cp,"range cancellation does not charge unproductive future pulse");h.succeed();});
        }));
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_border_refuses_emission_without_requesting_new_chunks",timeoutTicks=35)
    public static void rail_border_refuses_emission_without_requesting_new_chunks(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,7);BigDecimal before=hp(target);double cp=f.cp();f.prepare(new float[]{0,-1,1},()->{
            var border=h.getLevel().getWorldBorder();double oldX=border.getCenterX(),oldZ=border.getCenterZ(),oldSize=border.getSize();int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();
            try{border.setCenter(f.p.getX(),f.p.getZ());border.setSize(8);RailThunderArray.release(f.p,f.wire(4));exact(h,hp(target),before,"target beyond border is not damaged");same(h,f.cp(),cp,"border-blocked opportunity free");h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==chunks,"bounded loaded-path validation never forces a chunk");}
            finally{RailThunderArray.abort(f.p);border.setCenter(oldX,oldZ);border.setSize(oldSize);}h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_real_dimension_transition_cancels_owned_preparation",timeoutTicks=45)
    public static void rail_real_dimension_transition_cancels_owned_preparation(GameTestHelper h){
        Fixture f=new Fixture(h);RailThunderArray.press(f.p,f.wire(1));RailThunderArray.release(f.p,f.wire(1));String old=f.wire(1);double cp=f.cp();var other=f.p.server.getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"native Nether fixture exists");
        other.getChunk(f.p.blockPosition());f.p.teleportTo(other,f.p.getX(),f.p.getY(),f.p.getZ(),Set.of(),0,0);h.assertTrue(f.p.serverLevel()==other,"real connected native dimension transition");RailThunderArray.tick(f.p);h.assertFalse(RailThunderArray.active(f.p),"world change retires captured controller");h.assertFalse(RailThunderArray.release(f.p,old),"old dimension identity never releases in new world");same(h,f.cp(),cp,"unpaid dimension cancellation free");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_new_session_low_input_is_legitimate_old_session_cannot_replay",timeoutTicks=30)
    public static void rail_new_session_low_input_is_legitimate_old_session_cannot_replay(GameTestHelper h){
        Fixture f=new Fixture(h);String old=f.wire(1000);h.assertTrue(RailThunderArray.press(f.p,old)&&RailThunderArray.release(f.p,old),"old session high input issued");RailThunderArray.remove(f.p);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();
        h.assertFalse(RailThunderArray.press(f.p,old),"old authenticated tuple cannot replay into replacement session");h.assertFalse(RailThunderArray.press(f.p,f.wire(1)),"persistent4tick marker fence survives replacement session");
        h.runAfterDelay(4,()->{h.assertTrue(RailThunderArray.press(f.p,f.wire(2))&&RailThunderArray.release(f.p,f.wire(2)),"new session low nonce works after original mark fence");h.assertFalse(RailThunderArray.release(f.p,old),"old release cannot affect new session context");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_rail_native_pitch_yaw_directions_are_normalized_before_marker_gates",timeoutTicks=35)
    public static void rail_native_pitch_yaw_directions_are_normalized_before_marker_gates(GameTestHelper h){
        Fixture f=new Fixture(h);float[][] rotations={{1,0},{35,17},{-79,-33},{179,70},{-135,-70}};
        for(int i=0;i<rotations.length;i++){final int index=i;Runnable marker=()->{
            f.p.setYRot(rotations[index][0]);f.p.setXRot(rotations[index][1]);long input=index+1;
            h.assertTrue(RailThunderArray.press(f.p,f.wire(input)),"finite ordinary native look accepted at yaw/pitch "+Arrays.toString(rotations[index])+" raw length="+f.p.getLookAngle().lengthSqr());
            h.assertTrue(RailThunderArray.release(f.p,f.wire(input)),"normalized server look commits marker");
            for(var segment:RailThunderArray.serverSegments(f.p))h.assertTrue(segment.from().distanceToSqr(segment.to())<=96*96,"normalized preview stays within96metres");
            RailThunderArray.abort(f.p);if(index==rotations.length-1)h.succeed();
        };if(i==0)marker.run();else h.runAfterDelay(i*4,marker);}
    }

    public static final class Observe {
        final ServerPlayer owner;final List<Long> times=new ArrayList<>();final Set<UUID> targets=new LinkedHashSet<>();int physical,electrical;
        Observe(ServerPlayer owner){this.owner=owner;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e){if(e.getSource().getEntity()!=owner)return;targets.add(e.getEntity().getUUID());
            if(e.getSource().is(DamageTypes.THROWN)){physical++;times.add(owner.server.overworld().getGameTime());}
            else if(e.getSource().is(DamageTypes.PLAYER_ATTACK)){electrical++;times.add(owner.server.overworld().getGameTime());}}
    }
    public static final class SkillMutation {
        final ServerPlayer owner;final Runnable action;boolean done;
        SkillMutation(ServerPlayer owner,Runnable action){this.owner=owner;this.action=action;}
        @SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==owner&&e.skill.equals("electromaster.rail_thunder_array")&&!done){done=true;action.run();}}
    }
    public static final class IncomingMutation {
        final ServerPlayer owner;final LivingEntity target;final Runnable action;boolean done;
        IncomingMutation(ServerPlayer owner,LivingEntity target,Runnable action){this.owner=owner;this.target=target;this.action=action;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==owner&&!done){done=true;action.run();}}
    }
    public static final class PostAction {
        final LivingEntity target;final Runnable action;boolean done;
        PostAction(LivingEntity target,Runnable action){this.target=target;this.action=action;}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()==target&&!done){done=true;action.run();}}
    }
    private static String candidateSummary(Fixture f){
        List<LivingEntity> scanned=new ArrayList<>();f.p.serverLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),new net.minecraft.world.phys.AABB(f.p.getEyePosition(),f.p.getEyePosition()).inflate(96),e->e!=f.p&&e.isAlive()&&!e.isRemoved(),scanned,65);
        List<LivingEntity> foreign=scanned.stream().filter(e->!f.entities.contains(e)).toList();
        return "phase="+RailThunderArray.phase(f.p)+" pulses="+RailThunderArray.pulseCount(f.p)+" rawCandidates="+scanned.size()+" foreign="+foreign.size()+" examples="+foreign.stream().limit(3).map(e->e.getType()+"@"+e.position()).toList();
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();BigDecimal nativeCpCredits=BigDecimal.ZERO;boolean pvpEnabled,closed;
        Fixture(GameTestHelper h){this(h,5);}
        Fixture(GameTestHelper h,int level){
            this.h=h;for(int x=2;x<=22;x++)for(int z=2;z<=26;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPR"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(12.5,2,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();h.assertTrue(HighPowerCombat.enroll(p,level),"real authenticated enrolled caster");
            var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(level);state.learn("forked_arc");state.learn(ThunderNet.ID);state.learn(RailThunderArray.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,RailThunderArray.ID,id->true),"fixture binds learned151");ElectromasterPrototype.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(float yaw,double distance){return targetAt(Vec3.directionFromRotation(0,yaw).scale(distance));}
        Zombie targetAt(Vec3 offset){Zombie e=EntityType.HUSK.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setSilent(true);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.setPos(p.position().add(offset));h.getLevel().addFreshEntity(e);h.assertTrue(HighPowerCombat.enroll(e,5),"supported exact native target");entities.add(e);return e;}
        void prepare(float[] yaw,Runnable ready){mark(1,yaw[0]);h.runAfterDelay(4,()->mark(2,yaw[1]));h.runAfterDelay(8,()->mark(3,yaw[2]));h.runAfterDelay(12,()->{h.assertTrue(RailThunderArray.markCount(p)==3,"three separately observed directions");h.assertTrue(RailThunderArray.press(p,wire(4)),"fourth press arms existing marks");ready.run();});}
        void mark(long input,float yaw){p.setYRot(yaw);p.setXRot(0);String diagnostic=" tick="+now()+" markUntil="+p.getPersistentData().getCompound("academy:electromaster_prototype").getLong(RailThunderArray.MARK)+" phase="+RailThunderArray.phase(p)+" marks="+RailThunderArray.markCount(p)+" usable="+AbilityStorage.get(p).canUse(RailThunderArray.ID)+" alive="+p.isAlive()+" pos="+p.position()+" lookLengthSquared="+p.getLookAngle().lengthSqr();h.assertTrue(RailThunderArray.press(p,wire(input)),"server accepts marker press"+input+diagnostic);h.assertTrue(RailThunderArray.release(p,wire(input)),"server commits marker release"+input);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        long now(){return p.server.overworld().getGameTime();}
        double cp(){return ElectromasterPrototype.prototypeCp(p);}
        // Embedded connections omit the real network tick. Exercise actual player ticks, never rewrite cooldowns or CP.
        void nativePlayerTicks(){h.onEachTick(()->{if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();BigDecimal credit=new BigDecimal(cp()).subtract(before);h.assertTrue(credit.signum()>=0,"native fixture tick only regenerates CP");nativeCpCredits=nativeCpCredits.add(credit);}});}
        BigDecimal netDebit(double initial){return new BigDecimal(initial).add(nativeCpCredits).subtract(new BigDecimal(cp()));}
        void enablePvp(){if(pvpEnabled)return;pvpEnabled=true;if(pvpFixtures++==0)originalPvp=p.server.isPvpAllowed();p.server.setPvpAllowed(true);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);if(pvpEnabled&&--pvpFixtures==0)p.server.setPvpAllowed(originalPvp);RailThunderArray.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}

package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.DualPointRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real player inputs and native entity ticks. No repeated delayed insertion inside scheduled callbacks. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDualPointRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_dual_point";
    private HighPowerDualPointRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_one_native_hit_pays_one_beam_and_replay_is_free(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);BigDecimal before=hp(target),damage=f.damage();double cp=f.cp();
        h.assertTrue(f.tap(1),"one authenticated current-aim opportunity commits");
        exact(h,before.subtract(hp(target)),damage,"one native .65D transaction");
        near(h,f.cp(),cp-10,"one beam at LV1 costs ten CP");
        h.assertTrue(DualPoint.receipts(f.p).size()==1&&DualPoint.receipts(f.p).getFirst().status()==DualPoint.DAMAGED,"receipt represents actual first hit");
        h.assertTrue(DualPoint.receipts(f.p).getFirst().origin().equals(DualPoint.hand(f.p,0)),"source is the real left focusing point");
        h.assertFalse(f.tap(1),"exact packet replay cannot deal damage or debit again");
        h.assertFalse(f.tap(2),"new input cannot bypass the native skill cooldown");
        DualPoint.tick(f.p);DualPoint.tick(f.p);near(h,f.cp(),cp-10,"duplicate callbacks have no resource transaction");
        exact(h,before.subtract(hp(target)),damage,"replays leave one real hit");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_two_targets_have_separate_origins_and_whole_1_3d_budget(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8),behind=f.target(6,16);
        BigDecimal a=hp(primary),b=hp(marked),third=hp(behind),damage=f.damage();double cp=f.cp();
        f.face(marked);h.assertTrue(f.mark(1),"one free secondary endpoint");near(h,f.cp(),cp,"mark is free");Vec3 frozen=DualPoint.markedEndpoint(f.p);
        f.face(primary);h.assertTrue(f.tap(2),"whole cast commits once");near(h,f.cp(),cp-20,"two admitted slots paid before first native hit");
        exact(h,a.subtract(hp(primary)),damage,"primary emitted immediately");exact(h,hp(marked),b,"secondary has not emitted early");
        h.assertTrue(f.damage().compareTo(damage)>0,"successful first hit advances live growth before second emission");
        f.pump();
        h.runAtTickTime(2,()->{
            exact(h,a.subtract(hp(primary)),damage,"primary has one transaction");exact(h,b.subtract(hp(marked)),damage,"secondary emits one tick later");
            exact(h,a.subtract(hp(primary)).add(b.subtract(hp(marked))),damage.multiply(BigDecimal.valueOf(2)),"nominal whole-cast budget is exactly 1.3D");
            exact(h,hp(behind),third,"no third enemy is pierced or reacquired");
            var receipts=DualPoint.receipts(f.p);h.assertTrue(receipts.size()==2&&receipts.get(0).slot()==0&&receipts.get(1).slot()==1,"two real server receipts");
            h.assertTrue(receipts.get(0).origin().distanceToSqr(receipts.get(1).origin())>.3,"spatially separate hand origins");
            h.assertTrue(DualPoint.markedEndpoint(f.p).equals(frozen),"marker coordinate never follows aim");
            near(h,f.debit(cp),20,"native regeneration accounted, no second debit");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_same_victim_keeps_native_equal_hit_iframes(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);BigDecimal before=hp(target),damage=f.damage();double cp=f.cp();
        h.assertTrue(f.mark(1)&&f.tap(2),"two independent opportunities converge on same identity");f.pump();
        h.runAtTickTime(2,()->{
            exact(h,before.subtract(hp(target)),damage,"equal second native hit naturally refuses; no forced 1.3D aggregation");
            h.assertTrue(target.invulnerableTime>10,"native iframe timer is retained");
            var receipts=DualPoint.receipts(f.p);h.assertTrue(receipts.size()==2&&receipts.get(0).status()==DualPoint.DAMAGED&&receipts.get(1).status()==DualPoint.REFUSED,"second equal hit has its own refused receipt");
            near(h,f.debit(cp),20,"a later native refusal keeps both prepaid CP shares");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_mark_does_not_track_or_reacquire_replacement(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8),replacement=f.target(-10,8);
        f.face(marked);h.assertTrue(f.mark(1),"server captures coordinate and UUID");Vec3 frozen=DualPoint.markedEndpoint(f.p),old=marked.position();UUID uuid=marked.getUUID();
        f.face(primary);double cp=f.cp();h.assertTrue(f.tap(2),"both currently valid slots committed");BigDecimal m=hp(marked),r=hp(replacement);
        marked.setPos(old.add(-4,0,0));replacement.setPos(old);f.pump();
        h.runAtTickTime(2,()->{
            h.assertTrue(DualPoint.markedEndpoint(f.p).equals(frozen)&&DualPoint.markedTarget(f.p).equals(uuid),"both frozen mark fields survive movement");
            exact(h,hp(marked),m,"secondary never tracks original into new position");exact(h,hp(replacement),r,"replacement at exact old point is never reacquired");
            h.assertTrue(DualPoint.receipts(f.p).get(1).status()==DualPoint.REFUSED,"replacement physically terminates the beam without damage");
            near(h,f.debit(cp),20,"postcommit target movement does not refund");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_invalid_mark_slot_cannot_return_as_unpaid_later_beam(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8);Vec3 old=marked.position();BigDecimal before=hp(marked);
        f.face(marked);h.assertTrue(f.mark(1),"valid preparatory mark");marked.setPos(old.add(-5,0,0));f.face(primary);double cp=f.cp();
        h.assertTrue(f.tap(2),"current aim alone is productive");near(h,f.cp(),cp-10,"invalid secondary was not purchased");marked.setPos(old);f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"returned mark target cannot appear in absent committed slot");h.assertTrue(DualPoint.receipts(f.p).size()==1,"no unpaid visual or damage emission");near(h,f.debit(cp),10,"single paid slot remains single");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_new_wall_stops_secondary_and_never_edits_terrain(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8);BigDecimal before=hp(marked);
        f.face(marked);h.assertTrue(f.mark(1),"clear marked target");f.face(primary);h.assertTrue(f.tap(2),"two initial opportunities");
        Vec3 middle=DualPoint.hand(f.p,1).lerp(DualPoint.markedEndpoint(f.p),.5);BlockPos wall=BlockPos.containing(middle);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());f.blocks.add(wall);f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"current first solid wins over captured target");h.assertTrue(DualPoint.receipts(f.p).get(1).status()==DualPoint.BLOCKED,"receipt stops at current wall");h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"no destruction path exists");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_new_ally_is_physical_blocker_without_damage(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8),ally=f.target(-12,8);BigDecimal before=hp(marked),friendly=hp(ally);
        var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("dp"+f.p.getId());board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);f.teams.add(team);
        f.face(marked);h.assertTrue(f.mark(1),"free mark");f.face(primary);h.assertTrue(f.tap(2),"commit before interception");
        Vec3 crossing=DualPoint.hand(f.p,1).lerp(DualPoint.markedEndpoint(f.p),.5);ally.setPos(crossing.add(0,-ally.getBbHeight()/2,0));f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"beam does not pass friendly body");exact(h,hp(ally),friendly,"friendly blocker not damaged");h.assertTrue(DualPoint.receipts(f.p).get(1).status()==DualPoint.REFUSED,"physical friendly contact has refused receipt");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_empty_preparation_wrong_mode_and_forged_actor_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();h.assertFalse(f.tap(1),"empty current aim has no commit");h.assertFalse(f.mark(2),"empty mark cannot be stored");
        Zombie target=f.target(0,8);f.face(target);f.p.setShiftKeyDown(false);h.assertFalse(DualPoint.mark(f.p,f.wire(3)),"opcode cannot invent server crouch");
        f.p.setShiftKeyDown(true);h.assertFalse(DualPoint.tap(f.p,f.wire(4)),"tap opcode cannot bypass crouch mark mode");f.p.setShiftKeyDown(false);
        h.assertFalse(DualPoint.tap(f.p,new HighPowerInput.Input(0,5,f.p.getId()+1,f.epoch()).wire()),"foreign actor is rejected");
        String old=f.wire(6);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(DualPoint.tap(f.p,old),"retired epoch cannot commit");
        near(h,f.cp(),cp,"all invalid preparation remains free");h.assertFalse(DualPoint.active(f.p),"no empty controller retained");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=150)
    public static void dual_point_marks_have_four_tick_cadence_and_fixed_expiry(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie first=f.target(-3,8),second=f.target(3,8);f.face(first);double cp=f.cp();h.assertTrue(f.mark(1),"first mark");Vec3 old=DualPoint.markedEndpoint(f.p);
        f.face(second);h.assertFalse(f.mark(2),"same tick replacement rejected");h.assertTrue(DualPoint.markedEndpoint(f.p).equals(old),"rejected replacement preserves old endpoint");
        h.runAtTickTime(3,()->h.assertFalse(f.mark(3),"third tick still below mark cadence"));
        h.runAtTickTime(4,()->h.assertTrue(f.mark(4),"fourth tick allows replacement"));
        h.runAtTickTime(4+DualPointRules.MARK_TICKS-1,()->h.assertTrue(DualPoint.marked(f.p),"heartbeat never shortens the final valid tick"));
        h.runAtTickTime(4+DualPointRules.MARK_TICKS+1,()->{h.assertFalse(DualPoint.active(f.p),"original replacement deadline expires without refresh");near(h,f.cp(),cp,"only mark maintenance has no CP cost");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_abort_does_not_reset_mark_cadence_or_refund_paid_slots(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);h.assertTrue(f.mark(1),"first mark");
        h.assertTrue(DualPoint.abort(f.p,f.wire(1)),"authenticated screen abort clears mark");h.assertFalse(f.mark(2),"abort cannot erase server mark cadence");
        h.runAtTickTime(4,()->{h.assertTrue(f.mark(3),"fresh mark after real cadence");double cp=f.cp();h.assertTrue(f.tap(4),"paid two-slot cast");h.assertTrue(DualPoint.abort(f.p,f.wire(4)),"same identity can abort after first hit");near(h,f.cp(),cp-20,"abort never refunds either prepaid share");h.assertFalse(DualPoint.active(f.p),"screen abort clears all remaining state");h.assertFalse(f.tap(4),"retired tap cannot resurrect");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_origins_cannot_clip_out_through_wall(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 at=f.p.position();f.p.setPos(Math.floor(at.x)+.05,at.y,at.z);f.p.connection.resetPosition();Zombie target=f.target(0,8);f.face(target);
        Vec3 origin=DualPoint.hand(f.p,0);BlockPos wall=BlockPos.containing(origin);h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());f.blocks.add(wall);double cp=f.cp();BigDecimal before=hp(target);
        h.assertFalse(f.tap(1),"left focusing point inside stone cannot originate a ray beyond it");near(h,f.cp(),cp,"invalid origin is free preparation");exact(h,hp(target),before,"no through-origin wall damage");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_range_and_candidate_overflow_fail_closed(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie beyond=f.target(0,DualPointRules.RANGE+.1);f.face(beyond);double cp=f.cp();h.assertFalse(f.tap(1),"target beyond caster reach cannot be acquired");
        beyond.setPos(f.p.position().add(0,0,8));for(int i=0;i<65;i++)f.target(0,8);f.face(beyond);
        h.assertFalse(f.tap(2),"overflow over 64 physical candidates never picks an incomplete first-blocker set");near(h,f.cp(),cp,"range and overflow refusals remain free");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_movement_revalidates_second_origin_and_both_range_limits(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8);f.face(marked);h.assertTrue(f.mark(1),"mark before movement");f.face(primary);double cp=f.cp();h.assertTrue(f.tap(2),"two prepaid opportunities");BigDecimal before=hp(marked);
        f.p.setPos(f.p.position().add(0,0,-24));f.p.connection.resetPosition();f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"old endpoint beyond current caster range cannot fire from remote old position");near(h,f.debit(cp),20,"movement keeps committed CP spent");h.assertTrue(DualPoint.receipts(f.p).get(1).origin().equals(DualPoint.hand(f.p,1)),"refused emission records current actual hand origin");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_presets_category_interference_death_and_logout_clear_marks(GameTestHelper h){
        for(int mode=0;mode<5;mode++){
            Fixture f=new Fixture(h);f.p.setPos(f.p.position().add(mode*2,0,0));f.p.connection.resetPosition();Zombie target=f.target(0,8);f.face(target);h.assertTrue(f.mark(1),"valid mark for lifecycle "+mode);
            switch(mode){
                case 0->AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);
                case 1->AbilityStorage.get(f.p).selectCategory("electromaster");
                case 2->AbilityStorage.get(f.p).interfering=true;
                case 3->f.p.setHealth(0);
                default->DualPoint.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            }
            DualPoint.tick(f.p);h.assertFalse(DualPoint.active(f.p),"lifecycle clears controller "+mode);f.close();
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_callback_owner_movement_revokes_live_emission_permission(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);BigDecimal before=hp(target);double cp=f.cp();
        Object hook=new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==target&&event.getSource().getEntity()==f.p)f.p.setPos(f.p.position().add(3,0,0));}};
        f.hook(hook);h.assertTrue(f.tap(1),"paid opportunity reaches external native callback");
        exact(h,hp(target),before,"live authority revalidates actual source after callbacks");near(h,f.cp(),cp-10,"callback refusal keeps spent opportunity");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_dimension",timeoutTicks=30)
    public static void dual_point_real_dimension_transition_retires_mark_and_old_packet(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);h.assertTrue(f.mark(1),"mark in original world");String old=f.wire(2);double cp=f.cp();
        var other=f.p.server.getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"native destination dimension exists");
        other.getChunk(f.p.blockPosition());f.p.teleportTo(other,f.p.getX(),f.p.getY(),f.p.getZ(),Set.of(),0,0);
        h.assertTrue(f.p.serverLevel()==other,"actual native world transition");DualPoint.tick(f.p);h.assertFalse(DualPoint.active(f.p),"world ownership loss discards frozen mark");
        f.p.setShiftKeyDown(false);h.assertFalse(DualPoint.tap(f.p,old),"old world input cannot replay");near(h,f.cp(),cp,"unpaid preparation world cleanup is free");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_border",timeoutTicks=10)
    public static void dual_point_world_border_refusal_never_loads_extra_chunks(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);double cp=f.cp();BigDecimal hp=hp(target);var border=h.getLevel().getWorldBorder();
        double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();
        try{border.setCenter(f.p.getX(),f.p.getZ());border.setSize(8);h.assertFalse(f.tap(1),"outside current border is not an admitted opportunity");exact(h,hp(target),hp,"border refusal does no damage");near(h,f.cp(),cp,"border refusal free");h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==chunks,"geometry check does not force chunks");}
        finally{border.setCenter(x,z);border.setSize(size);}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_postcommit_protection_and_current_team_changes_revoke_secondary(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8);f.face(marked);h.assertTrue(f.mark(1),"eligible secondary mark");f.face(primary);h.assertTrue(f.tap(2),"two targets commit before policy change");BigDecimal before=hp(marked);
        var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("dpp"+f.p.getId());board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(marked.getScoreboardName(),team);f.teams.add(team);marked.getPersistentData().putBoolean("academy:protected",true);f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"current protection and current team take precedence over committed identity");h.assertTrue(DualPoint.receipts(f.p).get(1).status()==DualPoint.REFUSED,"current policy denial produces spent refused contact");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_new_owner_epoch_keeps_mark_cadence_and_fresh_input_sequence(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,8);f.face(target);h.assertTrue(f.mark(1000),"old incarnation high input");String old=f.wire(1001);
        DualPoint.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(DualPoint.mark(f.p,old),"old issued tuple cannot replay into new incarnation");h.assertFalse(f.mark(1),"fresh low input cannot erase persistent cadence");
        h.runAtTickTime(4,()->{h.assertTrue(f.mark(2),"new incarnation lower monotonic sequence is legitimate after cadence");h.assertFalse(DualPoint.abort(f.p,old),"old abort cannot touch new mark");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void dual_point_mark_range_is_also_measured_from_actual_secondary_hand(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(0,DualPointRules.RANGE-.2),primary=f.target(0,-8);f.face(marked);h.assertTrue(f.mark(1),"forward hand admits near-limit marked endpoint");
        Vec3 end=DualPoint.markedEndpoint(f.p);f.face(primary);h.assertTrue(f.p.position().distanceTo(end)<DualPointRules.RANGE&&DualPoint.hand(f.p,1).distanceTo(end)>DualPointRules.RANGE,"turning leaves mark inside caster sphere but outside current real-origin sphere");
        BigDecimal before=hp(marked);double cp=f.cp();h.assertTrue(f.tap(2),"only current primary opportunity remains");near(h,f.cp(),cp-10,"out-of-origin-range secondary is not prepaid");f.pump();
        h.runAtTickTime(2,()->{exact(h,hp(marked),before,"no free shot from old hand or mark point");h.assertTrue(DualPoint.receipts(f.p).size()==1,"only admitted real-origin beam emits");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void dual_point_final_emission_callback_cannot_replace_cast_identity_or_poison_next_input(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie marked=f.target(-3,8),primary=f.target(3,8);f.face(marked);h.assertTrue(f.mark(1),"original free mark");Vec3 original=DualPoint.markedEndpoint(f.p);double cp=f.cp();boolean[] called={false},accepted={true};
        f.hook(new Object(){@SubscribeEvent public void skill(cn.academy.port.api.SkillAttackEvent event){
            if(event.player==f.p&&event.target==marked&&event.skill.equals("meltdowner.dual_point")){
                called[0]=true;f.p.setShiftKeyDown(true);accepted[0]=DualPoint.mark(f.p,f.wire(3));f.p.setShiftKeyDown(false);
            }
        }});
        f.pump();
        h.runAtTickTime(4,()->{f.face(primary);h.assertTrue(f.tap(2),"cast after original mark cadence has elapsed");});
        h.runAtTickTime(6,()->{
            h.assertTrue(called[0]&&!accepted[0],"real final-slot callback cannot mutate a resolving controller");
            h.assertTrue(DualPoint.markedEndpoint(f.p).equals(original),"callback did not replace frozen mark");
            var receipts=DualPoint.receipts(f.p);h.assertTrue(receipts.size()==2&&receipts.get(0).input()==2&&receipts.get(1).input()==2,"both native receipts retain committed cast input");
            near(h,f.debit(cp),20,"reentry does not add a CP debit");
            h.assertTrue(f.mark(3),"refused synchronous reentry did not poison the next legitimate input");h.assertFalse(f.mark(3),"accepted input remains protected by the monotonic replay fence");h.succeed();
        });
    }

    private static BigDecimal hp(LivingEntity target){return new BigDecimal(HighPowerCombat.healthText(target));}
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" / "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" / "+expected);}
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();
        final List<Object> hooks=new ArrayList<>();final List<BlockPos> blocks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();
        BigDecimal credits=BigDecimal.ZERO;boolean closed,pumping;
        Fixture(GameTestHelper h){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"DP"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;Vec3 at=h.absoluteVec(new Vec3(12.5,18,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,1),"real authenticated exact caster");var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(1);state.learn(DualPoint.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,DualPoint.ID,id->true),"bind learned NEW211 root");HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double x,double z){
            Zombie target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(target);
            h.assertTrue(HighPowerCombat.enroll(target,5),"native enrolled durable target");entities.add(target);return target;
        }
        void face(LivingEntity target){Vec3 delta=target.getBoundingBox().getCenter().subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
        boolean mark(long input){p.setShiftKeyDown(true);return DualPoint.mark(p,wire(input));}
        boolean tap(long input){p.setShiftKeyDown(false);return DualPoint.tap(p,wire(input));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        BigDecimal damage(){return DualPointRules.damage(HighPowerGrowth.values(p).damage());}
        /** Embedded transports omit connection ticks: use real player ticks and measure their actual CP credit. */
        void pump(){if(pumping)return;pumping=true;h.onEachTick(()->{if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();credits=credits.add(new BigDecimal(cp()).subtract(before));}});}
        double debit(double initial){return new BigDecimal(initial).add(credits).subtract(new BigDecimal(cp())).doubleValue();}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));DualPoint.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}

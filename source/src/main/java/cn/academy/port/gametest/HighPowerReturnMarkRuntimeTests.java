package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ReturnMarkRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW322 native server acceptance. Enrollment, learned skills, bounded floors and
 * relocations are declared fixtures, not survival acquisition evidence. Players
 * are resident, acknowledge native login packets and use a real native tick pump.
 * Post observers inspect commits before next-tick recovery; completion is returned
 * to GameTestInfo's own callback. No LocalPlayer, keyboard, GUI or renderer runs here.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerReturnMarkRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_return_mark";
    private static final String OWN="academy:return_mark_fences", SHARED="academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_return_mark_fixture",UUID::compareTo);
    private HighPowerReturnMarkRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void authenticated_mark_is_free_exact_and_not_a_held_movement_context(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();CompoundTag shared=f.shared();
        h.assertTrue(f.press(1),"first authenticated mapped tap records current native feet");
        var r=ReturnMark.receipt(f.p);
        h.assertTrue(r!=null&&r.input()==1&&r.token()>0&&r.phase()==ReturnMark.MARKED&&!r.paid()&&!r.moved()
                &&r.source().equals(f.anchor)&&r.destination().equals(f.anchor),"free exact anchor receipt");
        h.assertTrue(ReturnMark.marked(f.p)&&!ReturnMark.active(f.p),"stored mark does not monopolize movement consumption");
        h.assertTrue(f.own().getInt("version")==1&&f.own().getInt("request")==8&&f.own().getInt("cooldown")==0,"mark writes only its own bounded request fence");
        ReturnMark.release(f.p,f.wire(1));h.assertFalse(f.press(1),"released duplicate cannot start a return");
        f.same(before,"mark, release and duplicate");h.assertTrue(f.shared().equals(shared)&&f.positions().isEmpty(),"mark emits no native move or shared movement fence");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void warning_requires_four_real_post_ticks_and_duplicate_calls_never_age_it(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();int issued=f.p.server.getTickCount();
        TeleportHook hook=new TeleportHook(f.p,event->{});f.hook(hook);
        for(int i=0;i<64;i++)ReturnMark.tick(f.p);
        h.assertTrue(ReturnMark.active(f.p)&&f.p.position().equals(f.source),"same-tick direct calls cannot turn warning into a move");
        f.same(before,"same-tick warning calls");h.assertFalse(f.press(3),"further starts cannot replace a warning");
        for(int tick=1;tick<=3;tick++)f.post(tick,()->{
            for(int i=0;i<64;i++)ReturnMark.tick(f.p);
            h.assertTrue(ReturnMark.active(f.p)&&f.p.position().equals(f.source)&&hook.calls==0,"each of the first three genuine ticks stays warning-only");f.same(before,"unpaid warning");
        });
        f.post(4,()->{
            f.success(2);h.assertTrue(hook.calls==1&&hook.tick-issued==4,"one native event occurs exactly on fourth new server tick");
            near(h,f.cp(),before.cp()-3,"one LV3 debit at genuine fourth boundary");f.unchangedPools(before,"successful return");
            h.assertTrue(f.shared().getInt("movement")==8&&f.own().getInt("cooldown")==20,"successful native return writes both full physical fences");
            Snapshot paid=f.snapshot();var receipt=ReturnMark.receipt(f.p);ReturnMark.tick(f.p);ReturnMark.release(f.p,f.wire(2));
            h.assertFalse(f.press(2),"original confirmation cannot replay");f.same(paid,"post-success release, direct tick and duplicate");
            h.assertTrue(ReturnMark.receipt(f.p).equals(receipt),"paid receipt stays historical");
        });f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void native_absolute_packet_and_ack_never_add_source_twice(GameTestHelper h) {
        Fixture f=new Fixture(h);f.begin();f.p.setYRot(37);f.p.setXRot(-31);f.drain();
        f.post(4,()->{
            f.success(2);var packets=f.positions();h.assertTrue(packets.size()==1,"one real native outbound position packet");var packet=packets.getFirst();
            Vec3 xyz=new Vec3(packet.getX(),packet.getY(),packet.getZ());h.assertTrue(xyz.equals(f.anchor)&&xyz.equals(f.p.position()),"validated anchor is absolute native XYZ");
            h.assertTrue(packet.getRelativeArguments().equals(RelativeMovement.ROTATION),"only native rotation fields are relative");
            near(h,packet.getYRot(),0,"native yaw delta");near(h,packet.getXRot(),0,"native pitch delta");near(h,f.p.getYRot(),37,"live server yaw retained");near(h,f.p.getXRot(),-31,"live server pitch retained");
            h.assertTrue(xyz.equals(awaiting(f.p)),"native listener awaits exact anchor");Snapshot paid=f.snapshot();
            f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()+1));h.assertTrue(xyz.equals(awaiting(f.p)),"wrong native acknowledgment is inert");
            f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));h.assertTrue(awaiting(f.p)==null&&f.p.position().equals(f.anchor),"correct acknowledgment clears pending target without another displacement");
            f.same(paid,"native acknowledgments");ReturnMark.tick(f.p);h.assertTrue(f.positions().isEmpty(),"ack and idle tick emit no repeated native move");
        });f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void real_lv2_through_lv5_commits_use_continuous_growth_costs_and_separate_fences(GameTestHelper h) {
        economics(h,2);
    }
    private static void economics(GameTestHelper h,int level) {
        if(level>5){h.succeed();return;}Fixture f=new Fixture(h,level,0);CompoundTag shared=shared(3,0,17,13);shared.putString("unrelated","retain");f.p.getPersistentData().put(SHARED,shared);
        f.begin();f.post(4,()->{
            f.success(2);BigDecimal q=HighPowerGrowth.values(f.p).position();near(h,f.cp(),HighPowerGrowth.values(f.p).cpMaximum()-ReturnMarkRules.cpCost(q),"one actual q-dependent debit at LV"+level);
            int cd=ReturnMarkRules.cooldownTicks(q);h.assertTrue(f.own().getInt("cooldown")==cd&&f.budget().getLong("next_"+ReturnMark.ID)==f.p.serverLevel().getGameTime()+cd,"own physical cooldown and economic deadline agree");
            h.assertTrue(f.shared().getInt("movement")==8&&f.shared().getInt("cooldown")==13&&f.shared().getInt("mastery")==9&&f.shared().getString("unrelated").equals("retain"),"return preserves naturally aged existing313 fields and unrelated data");near(h,f.mastery(),0,"no return mastery");
        });h.runAfterDelay(5,()->{f.verifyPosts();f.close();economics(h,level+1);});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void fractional_native_growth_changes_return_cost_without_unlocking_lv1(GameTestHelper h) {
        Fixture denied=new Fixture(h,1,0);h.assertFalse(denied.press(1),"LV1 fixture cannot use catalog LV2 return");denied.close();
        Fixture f=new Fixture(h,2,0);var s=AbilityStorage.get(f.p);s.addExperience(SafeLandingProbe.ID,.01);BigDecimal q=HighPowerGrowth.values(f.p).position();
        h.assertTrue(q.compareTo(BigDecimal.valueOf(2))>0&&q.compareTo(BigDecimal.valueOf(3))<0&&s.level==2,"actual prerequisite XP gives fractional native q without integer level promotion");
        double[] beforeEvent={Double.NaN};f.hook(new TeleportHook(f.p,event->beforeEvent[0]=f.cp()));
        f.begin();f.post(4,()->{f.success(2);double cost=beforeEvent[0]-f.cp();h.assertTrue(cost>3&&cost<6,"fractional native return debit lies between growth anchors");near(h,cost,ReturnMarkRules.cpCost(q),"exact event-to-commit debit avoids conflating native recovery with cost");near(h,f.mastery(),0,"return grants no mastery");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_catalog_requires_learned_probe_and_canonical_request_routes_mark_release_abort(GameTestHelper h) {
        Fixture f=new Fixture(h);var entry=SkillCatalog.find(HighPowerCategories.TELEPORTER,ReturnMark.ID).orElseThrow();
        h.assertTrue(entry.level()==2&&entry.requirements().equals(List.of(new SkillCatalog.Requirement(SafeLandingProbe.ID,0))),"native LV2 entry needs learned313 without fabricated mastery threshold");
        Snapshot before=f.snapshot();h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_return_mark_start",f.wire(1))),"canonical native request reaches first mark");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_return_mark_release",f.wire(1))),"canonical release keeps mark");
        f.place(f.source);h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_return_mark_start",f.wire(2))),"canonical second tap starts warning despite own request fence");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_return_mark_abort",f.wire(2))),"canonical abort closes authenticated warning");
        h.assertFalse(ReturnMark.active(f.p)||ReturnMark.marked(f.p),"abort consumes pending return anchor");f.same(before,"canonical mark and cancelled warning");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void stored_anchor_survives_ordinary_movement_and_native_jump(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();h.assertTrue(f.press(1),"native grounded mark");
        f.place(f.source);f.p.jumpFromGround();h.assertTrue(!ReturnMark.active(f.p)&&ReturnMark.marked(f.p),"ordinary movement and jump do not consume stored anchor");
        for(int i=0;i<1500;i++)ReturnMark.tick(f.p);
        h.assertTrue(ReturnMark.marked(f.p)&&ReturnMark.remainingTicks(f.p)==1200,"1500 direct calls in one server tick cannot age 1200-tick mark lifetime");f.same(before,"stored jumping marker");
        f.post(4,()->{h.assertTrue(ReturnMark.marked(f.p)&&!ReturnMark.active(f.p)&&ReturnMark.remainingTicks(f.p)==1196,"four genuine airborne native ticks preserve and age unused mark exactly four times");near(h,f.cp(),before.cp(),"unused mark never debits CP");h.assertTrue(f.positions().isEmpty(),"jumping marker never becomes automatic air rescue");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void crouch_clears_freely_before_source_and_cooldown_validation(GameTestHelper h) {
        Fixture f=new Fixture(h);h.assertTrue(f.press(1),"safe mark before clear");f.p.getPersistentData().put(OWN,own(8,80));
        f.p.setOnGround(false);f.p.fallDistance=9;f.p.setDeltaMovement(0,-.4,0);f.p.setShiftKeyDown(true);Snapshot before=f.snapshot();Vec3 position=f.p.position(),motion=f.p.getDeltaMovement();
        h.assertTrue(f.press(2),"fresh actual crouch input clears before own cooldown and airborne source gates");
        h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"clear leaves no stored or warning context");f.same(before,"airborne cooldown clear");
        h.assertTrue(f.p.position().equals(position)&&f.p.getDeltaMovement().equals(motion)&&f.p.fallDistance==9&&f.own().equals(own(8,80)),"clear does not repair fall motion, position or fences");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void unsafe_native_feet_never_become_a_stored_return_destination(GameTestHelper h) {
        for(Block hazard:List.of(Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.WATER,Blocks.COBWEB,Blocks.POWDER_SNOW,Blocks.NETHER_PORTAL)) {
            Fixture f=new Fixture(h);f.block(8,hazard==Blocks.MAGMA_BLOCK?4:5,8,hazard);Snapshot before=f.snapshot();Vec3 position=f.p.position();
            h.assertFalse(f.press(1),"unsafe current feet refuse mark: "+hazard);h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"unsafe feet store no anchor");
            f.same(before,"unsafe mark");h.assertTrue(f.p.position().equals(position),"unsafe mark has no corrective movement");f.close();
        }
        Fixture missing=new Fixture(h);missing.block(8,4,8,Blocks.AIR);h.assertFalse(missing.press(1),"native unsupported feet cannot be saved");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_return_world_helper_accepts_beyond_twelve_through_exact_reach(GameTestHelper h) {
        Fixture f=new Fixture(h);double anchorX=.5;f.floor(0,7);f.place(f.absolute(anchorX,5,8.5));var mark=SafeLandingProbeWorld.mark(f.p);
        h.assertTrue(mark.valid(),"native current safe feet supply a closed owner/world anchor");
        double reach=ReturnMarkRules.RANGE;
        f.floor(15,7);f.floor(17,21);
        f.floor((int)Math.floor(anchorX+reach)-1,7);
        f.floor((int)Math.floor(anchorX+.8*(reach-1e-6))-1,(int)Math.floor(8.5+.6*(reach-1e-6))-1);
        Vec3 diagonal=mark.anchor().destination().add(new Vec3(4,0,3).normalize().scale(reach-1e-6));
        f.hold(new AABB(mark.anchor().destination(),diagonal).inflate(2));Snapshot before=f.snapshot();CompoundTag persistent=f.p.getPersistentData().copy();
        for(Vec3 point:List.of(f.absolute(17,5,8.5),f.absolute(anchorX+reach-1,5,8.5),f.absolute(anchorX+reach,5,8.5),f.absolute(18.5,5,22.5),diagonal)) {
            f.place(point);var assessment=SafeLandingProbeWorld.returning(f.p,mark.anchor());
            h.assertTrue(assessment.valid(),"actual resident native corridor beyond 12 m through its exact reach passes: "+point+" reason="+assessment.reason());
            h.assertTrue(assessment.candidate().destination().equals(mark.anchor().destination()),"long and diagonal native return retains exact closed anchor");
        }
        f.place(f.absolute(anchorX+reach,5,8.5));
        int barrierX=(int)Math.floor(anchorX+reach)-1;
        f.block(barrierX,6,8,Blocks.STONE);
        var blocked=SafeLandingProbeWorld.returning(f.p,mark.anchor());
        h.assertTrue(!blocked.valid()&&blocked.reason().equals("corridor_block_collision"),
                "actual native obstruction along the expanded reach still blocks return: "+blocked.reason());
        f.block(barrierX,6,8,Blocks.AIR);
        f.place(f.absolute(anchorX+reach+.01,5,8.5));var refused=SafeLandingProbeWorld.returning(f.p,mark.anchor());
        h.assertTrue(!refused.valid()&&refused.reason().equals("destination_out_of_range"),"actual native return just beyond reach fails closed");f.same(before,"read-only native long-range assessments");
        h.assertTrue(f.p.getPersistentData().equals(persistent)&&f.positions().isEmpty(),"native geometry inspection does not move, debit or persist an anchor");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_horizontal_diagonal_at_reach_crosses_dense_ordinary_floor(GameTestHelper h) {
        Fixture f=new Fixture(h);
        f.shooter.discard();
        for(int x=2;x<=26;x++)for(int z=2;z<=26;z++) {
            f.block(x,3,z,Blocks.STONE);
            f.block(x,4,z,Blocks.STONE);
        }
        f.place(f.absolute(3.5,5,3.5));
        var mark=SafeLandingProbeWorld.mark(f.p);
        h.assertTrue(mark.valid(),"native two-layer stone ground admits a valid mark");
        double axis=(ReturnMarkRules.RANGE-1e-6)/Math.sqrt(2);
        f.place(f.absolute(3.5+axis,5,3.5+axis));
        var assessment=SafeLandingProbeWorld.returning(f.p,mark.anchor());
        h.assertTrue(assessment.valid(),"same-height diagonal at reach over ordinary solid ground passes: "+assessment.reason());
        h.assertTrue(assessment.candidate().destination().equals(mark.anchor().destination()),"the dense-floor corridor retains the exact destination");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void airborne_slowfall_same_point_and_out_of_range_requests_cannot_return(GameTestHelper h) {
        for(int mode=0;mode<6;mode++) {
            Fixture f=new Fixture(h);h.assertTrue(f.press(1),"declared safe mark");if(mode!=4)f.place(f.source);
            switch(mode){case 0->f.p.setOnGround(false);case 1->f.p.fallDistance=3;case 2->f.p.setDeltaMovement(0,-.3,0);case 3->f.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,80));case 5->{f.floor(27,27);f.place(f.absolute(28.5,5,28.5));}default->{}}
            Snapshot before=f.snapshot();Vec3 position=f.p.position();h.assertFalse(f.press(2),"native unsafe return request refuses case "+mode);f.freeReceipt(null);f.same(before,"unsafe return request");
            h.assertTrue(f.p.position().equals(position)&&!ReturnMark.marked(f.p),"return refusal consumes anchor without rescuing source");f.close();
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=65)
    public static void changed_native_anchor_support_body_hazards_entities_and_corridor_refuse_free(GameTestHelper h) {
        changedGeometry(h,0);
    }
    private static void changedGeometry(GameTestHelper h,int mode) {
        if(mode==10){h.succeed();return;}Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();
        switch(mode){case 0->f.block(8,4,8,Blocks.AIR);case 1->f.block(8,4,8,Blocks.MAGMA_BLOCK);case 2->f.block(8,6,8,Blocks.STONE);case 3->f.block(8,5,8,Blocks.WATER);case 4->f.block(8,5,8,Blocks.COBWEB);case 5->f.block(8,5,8,Blocks.POWDER_SNOW);case 6->f.mob(f.anchor);case 7->{for(int i=0;i<65;i++)f.mob(f.anchor);}case 8->f.block(10,6,8,Blocks.GLASS_PANE);default->f.block(10,5,8,Blocks.WATER);}
        f.post(4,()->{f.freeReceipt(null);f.same(before,"changed native geometry "+mode);h.assertTrue(f.p.position().equals(f.source)&&!ReturnMark.marked(f.p),"unsafe return consumes anchor and stays at source");h.assertTrue(f.positions().isEmpty(),"unsafe geometry emits no native move");});
        h.runAfterDelay(5,()->{f.verifyPosts();f.close();changedGeometry(h,mode+1);});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void cancelled_native_event_precedes_debit_and_keeps_foreign_resource_edit(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();TeleportHook hook=new TeleportHook(f.p,event->{near(h,f.cp(),before.cp(),"native event precedes own CP debit");f.budget().putDouble("cp",f.cp()-1);f.p.getPersistentData().putInt("academy:return_test_callback",7);event.setCanceled(true);});f.hook(hook);
        f.post(4,()->{f.freeReceipt("event_cancelled");near(h,f.cp(),before.cp()-1,"cancel preserves callback CP debit without own debit or rollback");f.unchangedPools(before,"cancelled native event");h.assertTrue(hook.calls==1&&f.p.position().equals(f.source)&&f.budget().getLong("next_"+ReturnMark.ID)==before.deadline()&&f.p.getPersistentData().getInt("academy:return_test_callback")==7,"cancellation keeps native source, callback data and original deadline");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void native_event_redirect_never_moves_or_charges(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();f.hook(new TeleportHook(f.p,event->event.setTargetZ(event.getTargetZ()+.1)));
        f.post(4,()->{f.freeReceipt("event_redirected");f.same(before,"native redirected event");h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty(),"redirect is refused instead of used as a new destination");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void native_callback_state_and_world_geometry_are_revalidated_before_payment(GameTestHelper h) {
        callbackMutation(h,0);
    }
    private static void callbackMutation(GameTestHelper h,int mode) {
        if(mode==9){h.succeed();return;}Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();
        f.hook(new TeleportHook(f.p,event->{switch(mode){case 0->f.p.setPos(f.source.add(.1,0,0));case 1->f.p.setPose(Pose.CROUCHING);case 2->f.p.setYRot(90);case 3->f.p.setXRot(31);case 4->f.p.setDeltaMovement(.1,-.0784,0);case 5->f.p.setOnGround(false);case 6->f.p.fallDistance=2;case 7->f.p.setShiftKeyDown(true);default->f.block(10,6,8,Blocks.STONE);}}));
        f.post(4,()->{f.freeReceipt(null);f.same(before,"callback native mutation "+mode);h.assertTrue(f.positions().isEmpty(),"changed callback state cannot reach native return transport");if(mode==0)h.assertTrue(f.p.position().equals(f.source.add(.1,0,0)),"foreign position change is not rolled back");});
        h.runAfterDelay(5,()->{f.verifyPosts();f.close();callbackMutation(h,mode+1);});
    }

    @GameTest(template=TEMPLATE,batch="highpower_return_mark_world",timeoutTicks=12)
    public static void real_callback_dimension_change_is_preserved_and_old_world_return_is_free(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();var other=f.p.server.getLevel(Level.NETHER);h.assertTrue(other!=null,"native Nether exists");f.begin();
        f.hook(new TeleportHook(f.p,event->{if(f.p.serverLevel()==h.getLevel())f.p.teleportTo(other,f.source.x,f.source.y,f.source.z,Set.of(),f.p.getYRot(),f.p.getXRot());}));
        f.post(4,()->{near(h,f.cp(),before.cp(),"world-changing callback adds no322 debit");h.assertTrue(f.p.serverLevel()==other&&!ReturnMark.active(f.p)&&!ReturnMark.marked(f.p),"actual world transition is retained and old anchor discarded");h.assertTrue(f.budget().getLong("next_"+ReturnMark.ID)==before.deadline(),"world change sets no return cooldown deadline");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void native_callback_same_controller_reentry_cannot_repeat_event_move_or_debit(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();boolean[] nested={true};TeleportHook hook=new TeleportHook(f.p,event->nested[0]=f.press(3));f.hook(hook);
        f.post(4,()->{f.success(2);h.assertTrue(!nested[0]&&hook.calls==1&&f.positions().size()==1,"same-controller reentry is blocked with one actual event and transport");near(h,f.cp(),before.cp()-3,"only one charge through callback reentry");});f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_callback_abort_and_remove_revoke_captured_commit(GameTestHelper h) {
        callbackRetirement(h,false);
    }
    private static void callbackRetirement(GameTestHelper h,boolean remove) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();f.begin();boolean[] aborted={false};
        f.hook(new TeleportHook(f.p,event->{if(remove)ReturnMark.remove(f.p);else aborted[0]=ReturnMark.abort(f.p,f.wire(3));}));
        f.post(4,()->{f.same(before,"callback retirement");h.assertTrue(!ReturnMark.active(f.p)&&!ReturnMark.marked(f.p)&&f.p.position().equals(f.source)&&f.positions().isEmpty(),"retired captured context cannot pay or move");if(remove)h.assertTrue(ReturnMark.receipt(f.p)==null,"remove cannot reinsert a terminal receipt for retired player");else h.assertTrue(aborted[0],"callback abort authenticates processing identity");});
        h.runAfterDelay(5,()->{f.verifyPosts();f.close();if(remove)h.succeed();else callbackRetirement(h,true);});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void declared_fault_after_actual_native_transport_keeps_paid_moved_history_without_retry(GameTestHelper h) {
        Fixture f=new Fixture(h);f.p.connection=new ServerGamePacketListenerImpl(f.p.server,f.connection,f.p,f.cookie){
            @Override public void teleport(double x,double y,double z,float yaw,float pitch,Set<RelativeMovement> relative){
                super.teleport(x,y,z,yaw,pitch,relative);throw new IllegalStateException("declared NEW322 fault after actual native transport");
            }
        };f.epoch();Snapshot before=f.snapshot();f.begin();
        f.post(4,()->{
            var receipt=ReturnMark.receipt(f.p);h.assertTrue(receipt!=null&&receipt.paid()&&receipt.moved()&&receipt.phase()==ReturnMark.REFUSED&&!receipt.reason().isEmpty(),"late native transport fault retains already paid and moved facts");
            h.assertTrue(f.p.position().equals(f.anchor)&&!ReturnMark.marked(f.p)&&!ReturnMark.active(f.p)&&f.positions().size()==1,"actual native transport occurred once and consumes anchor despite late fault");near(h,f.cp(),before.cp()-3,"late transport fault never refunds committed payment");
            Snapshot paid=f.snapshot();h.assertFalse(f.press(2),"original return confirmation cannot retry ambiguous transport");ReturnMark.tick(f.p);f.same(paid,"late transport duplicate");
            h.assertTrue(ReturnMark.receipt(f.p).equals(receipt)&&f.shared().getInt("movement")==8,"historical receipt and full shared fence survive ambiguous completion");
        });f.finish(5);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void stale_owner_epoch_wrong_slot_and_old_input_cannot_use_or_cancel_fresh_mark(GameTestHelper h) {
        Fixture f=new Fixture(h);long epoch=f.epoch();Snapshot before=f.snapshot();
        h.assertFalse(ReturnMark.press(f.p,new HighPowerInput.Input(0,1,f.p.getId()+1,epoch).wire()),"wrong native entity identity refuses");
        h.assertFalse(ReturnMark.press(f.p,new HighPowerInput.Input(1,1,f.p.getId(),epoch).wire()),"mapped probe slot cannot invoke return controller");
        h.assertTrue(f.press(3),"new native authenticated mark");var marked=ReturnMark.receipt(f.p);
        h.assertFalse(f.press(2)||ReturnMark.abort(f.p,f.wire(2)),"stale input cannot return or cancel newer mark");h.assertTrue(ReturnMark.marked(f.p)&&ReturnMark.receipt(f.p).equals(marked),"stale input keeps exact mark receipt");
        String old=f.wire(4);f.p.connection=new ServerGamePacketListenerImpl(f.p.server,f.connection,f.p,f.cookie);f.epoch();
        h.assertFalse(ReturnMark.press(f.p,old),"connection replacement rejects old issued epoch");f.same(before,"stale native identity paths");
        f.post(1,()->h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"next genuine controller tick retires old connection anchor"));f.finish(2);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void authenticated_abort_before_queued_press_retires_that_input_without_allocating_mark(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();h.assertFalse(ReturnMark.abort(f.p,f.wire(1)),"empty authenticated abort has no live context to terminate");
        h.assertFalse(f.press(1),"delayed identical queued press cannot revive aborted input");h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"queued abort leaves no anchor");f.same(before,"queued abort");
        h.assertTrue(f.press(2),"fresh later input can place native mark");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void preset_change_and_category_roundtrip_never_restore_stored_anchor_or_refill(GameTestHelper h) {
        List<Fixture> fixtures=new ArrayList<>();for(boolean category:new boolean[]{false,true}) {
            Fixture f=new Fixture(h,3,category?16:0);fixtures.add(f);f.budget().putDouble("cp",37);h.assertTrue(f.press(1),"declared mark with depleted live CP");CompoundTag own=f.own(),shared=f.shared();String hp=f.hp(),shield=f.shield();
            var state=AbilityStorage.get(f.p);long revision=state.presets.revision();
            if(category){state.changeCategoryClassic(HighPowerCategories.MELTDOWNER);state.changeCategoryClassic(HighPowerCategories.TELEPORTER);f.learn(3);}
            else h.assertTrue(state.presets.edit(0,0,"",state::learned),"actual clear removes322 binding without duplicating the313 binding already in slot1");
            h.assertTrue(state.presets.revision()>revision,"declared preset/category mutation advances actual native mapping revision");
            near(h,f.cp(),37,"lifecycle change never refills already enrolled CP");
            h.assertTrue(f.hp().equals(hp)&&f.shield().equals(shield)&&f.own().equals(own)&&f.shared().equals(shared),"control invalidation preserves exact pools and remaining fences");
            f.post(1,()->h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"next genuine native tick retires changed preset/category anchor"));
        }h.runAfterDelay(2,()->{fixtures.forEach(Fixture::verifyPosts);h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_save_reload_has_remaining_fences_but_never_serializes_a_return_anchor(GameTestHelper h) {
        Fixture f=new Fixture(h);f.budget().putDouble("cp",37);h.assertTrue(f.press(1),"native mark before save");CompoundTag own=f.own(),shared=f.shared();String hp=f.hp(),shield=f.shield();
        AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());ReturnMark.remove(f.p);f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.place(f.anchor);f.epoch();ReturnMark.tick(f.p);
        h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"real player NBT reload creates no live saved mark");near(h,f.cp(),37,"native reload does not refill depleted CP");
        h.assertTrue(f.own().equals(own)&&f.shared().equals(shared)&&f.hp().equals(hp)&&f.shield().equals(shield),"actual reload retains remaining fences and exact HP/shield");h.assertFalse(f.press(2),"fresh epoch cannot bypass retained mark request fence");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_logout_death_and_clone_events_clear_anchors_and_clone_remaining_fences(GameTestHelper h) {
        for(boolean death:new boolean[]{false,true}) {
            Fixture f=new Fixture(h);h.assertTrue(f.press(1),"native mark before lifecycle event");CompoundTag own=f.own();
            if(death)NeoForge.EVENT_BUS.post(new LivingDeathEvent(f.p,f.p.damageSources().generic()));else NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            h.assertFalse(ReturnMark.marked(f.p)||ReturnMark.active(f.p),"actual event bus clears original live anchor");h.assertTrue(f.own().equals(own),"lifecycle event does not erase remaining request fence");f.close();
        }
        Fixture original=new Fixture(h),replacement=new Fixture(h,3,16);h.assertTrue(original.press(1),"original mark before actual clone event");original.p.getPersistentData().put(OWN,own(6,37));original.p.getPersistentData().put(SHARED,shared(0,7,13,11));
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.p,original.p,false));
        h.assertTrue(!ReturnMark.marked(original.p)&&!ReturnMark.marked(replacement.p)&&!ReturnMark.active(original.p),"native clone carries no anchor into either incarnation");
        h.assertTrue(replacement.own().equals(original.own())&&replacement.shared().equals(original.shared()),"native clone copies exact remaining own and existing shared fences");
        Snapshot before=replacement.snapshot();replacement.epoch();h.assertFalse(replacement.press(2),"clone cannot retry through copied request/cooldown");replacement.same(before,"clone retry");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void malformed_own_and_existing_shared_fence_storage_fail_closed_without_normalizing(GameTestHelper h) {
        List<Consumer<CompoundTag>> corruptions=List.of(t->t.putInt("version",2),t->t.putString("request","8"),t->t.putInt("request",9),t->t.putInt("cooldown",81),t->t.putInt("cooldown",-1));
        for(var corrupt:corruptions){Fixture f=new Fixture(h);CompoundTag tag=own(0,0);corrupt.accept(tag);f.p.getPersistentData().put(OWN,tag);Snapshot before=f.snapshot();h.assertFalse(f.press(1),"malformed own fence refuses native mark");f.same(before,"corrupt own fence");h.assertTrue(f.own().equals(tag)&&!ReturnMark.marked(f.p),"corrupt own data is never normalized into eligibility");f.close();}
        Fixture f=new Fixture(h);CompoundTag bad=shared(0,9,0,0);f.p.getPersistentData().put(SHARED,bad);Snapshot before=f.snapshot();h.assertTrue(f.press(1),"free marker does not require shared movement permission");f.place(f.source);h.assertFalse(f.press(2),"malformed existing shared movement fails closed before return");f.same(before,"corrupt shared fence");h.assertTrue(f.shared().equals(bad),"existing malformed313 data stays intact");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void return_and_probe_share_one_eight_tick_physical_fence_in_both_directions(GameTestHelper h) {
        Fixture f=new Fixture(h,5,0);f.begin();f.post(4,()->{f.success(2);h.assertTrue(f.shared().getInt("movement")==8,"322 writes existing313 movement field");});
        h.runAfterDelay(8,()->{
            f.verifyPosts();h.assertTrue(f.shared().getInt("movement")==4&&f.own().getInt("cooldown")==0,"four genuine later ticks age shared once despite expired LV5 economic cooldown");
            f.aim(f.source);double cp=f.cp();h.assertFalse(f.probe(1),"313 respects322 physical fence");near(h,f.cp(),cp,"blocked probe is free");
        });
        h.runAfterDelay(12,()->{
            h.assertTrue(f.shared().getInt("movement")==0,"322 fence expires at actual eighth later native tick");f.aim(f.source);
            h.assertTrue(f.probe(2)&&f.probe(3),"313 native move is admitted at exact eighth boundary");h.assertTrue(f.shared().getInt("movement")==8,"313 writes the same physical field");
            Snapshot before=f.snapshot();h.assertTrue(f.press(3),"free322 mark is permitted during313 movement fence");f.place(f.anchor);h.assertFalse(f.press(4),"322 return cannot chain through active313 movement fence");f.same(before,"reverse same-tick shared refusal");
        });
        h.runAfterDelay(20,()->{h.assertTrue(f.shared().getInt("movement")==0&&f.press(5),"322 can store a fresh mark at genuine eighth tick after313");f.place(f.source);h.assertTrue(f.press(6),"fresh322 return starts when313 shared fence expires");f.post(4,()->f.success(6));});
        h.runAfterDelay(25,()->{f.verifyPosts();h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void native_return_preserves_hurt_fire_status_gravity_and_exact_live_pools(GameTestHelper h) {
        Fixture f=new Fixture(h);int started=f.p.server.getTickCount();BodyState[] atEvent={null};
        h.runAfterDelay(61,()->{
            residency(h,started,f);h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter),4),"ordinary actual native damage establishes injury and hurt timers");f.place(f.anchor);
            f.p.setRemainingFireTicks(80);f.p.addEffect(new MobEffectInstance(MobEffects.POISON,120));f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,140));f.begin();
            f.hook(new TeleportHook(f.p,event->atEvent[0]=BodyState.of(f.p)));
            f.post(4,()->{
                f.success(2);h.assertTrue(atEvent[0]!=null&&BodyState.of(f.p).equals(atEvent[0]),"same native commit preserves velocity, ground, fall, gravity, HP, shield, fire, hurt timers and attached effects");
                h.assertTrue(f.p.isOnFire()&&f.p.hasEffect(MobEffects.POISON)&&f.p.hasEffect(MobEffects.WEAKNESS),"attached burning and native effects remain after safe-ground return");
            });
        });
        f.finish(66);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void genuine_native_fall_damage_after_return_equals_matched_stationary_control(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int started=f.p.server.getTickCount();
        h.runAfterDelay(61,()->{
            residency(h,started,f,control);f.begin();f.post(4,()->{
                f.success(2);BigDecimal hp=f.health(),other=control.health();String shield=f.shield();
                f.p.setOnGround(false);control.p.setOnGround(false);f.p.fallDistance=8;control.p.fallDistance=8;
                h.assertTrue(f.p.causeFallDamage(8,1,f.p.damageSources().fall())&&control.p.causeFallDamage(8,1,control.p.damageSources().fall()),"both real native fall paths accept equal measured fall after genuine login protection expiry");
                BigDecimal loss=hp.subtract(f.health()),expected=other.subtract(control.health());h.assertTrue(loss.signum()>0&&loss.compareTo(expected)==0,"returned native player has no fall-damage immunity or HP advantage");
                h.assertTrue(f.shield().equals(shield)&&!f.p.isNoGravity()&&!f.p.getAbilities().flying,"native fall spends no high-power shield and return grants no gravity or flight settings");
            });
        });f.finish(66);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void genuine_native_lethal_damage_never_triggers_automatic_return_or_rescue(GameTestHelper h) {
        Fixture f=new Fixture(h);int started=f.p.server.getTickCount();
        h.runAfterDelay(61,()->{
            residency(h,started,f);h.assertTrue(f.press(1),"grounded mark before actual lethal native attack");f.place(f.source);f.drain();
            float lethal=f.health().multiply(BigDecimal.valueOf(2)).floatValue();h.assertTrue(Float.isFinite(lethal)&&lethal<1e20f,"declared native lethal hit stays within central combat amount cap");
            h.assertTrue(f.p.hurt(f.p.damageSources().fellOutOfWorld(),lethal),"actual bounded native void damage is accepted after genuine login protection expiry");
            h.assertTrue(f.p.isDeadOrDying()&&!ReturnMark.marked(f.p)&&!ReturnMark.active(f.p),"native lethal flow kills and invalidates anchor");
            h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty(),"death never uses stored anchor for an automatic rescue packet");h.succeed();
        });
    }

    private record Snapshot(double cp,long deadline,int cooldown,double mastery,String hp,String shield,double overload) {}
    private record BodyState(Vec3 motion,float fall,boolean ground,boolean gravity,int hurt,int invulnerable,int fire,int poison,int weakness,String hp,String shield) {
        static BodyState of(ServerPlayer p){return new BodyState(p.getDeltaMovement(),p.fallDistance,p.onGround(),p.isNoGravity(),p.hurtTime,p.invulnerableTime,p.getRemainingFireTicks(),duration(p,MobEffects.POISON),duration(p,MobEffects.WEAKNESS),HighPowerCombat.healthText(p),HighPowerCombat.shieldText(p));}
        private static int duration(ServerPlayer p,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect){var instance=p.getEffect(effect);return instance==null?0:instance.getDuration();}
    }
    private static void near(GameTestHelper h,double actual,double wanted,String why){h.assertTrue(Math.abs(actual-wanted)<1e-7,why+": "+actual+" != "+wanted);}
    private static Object read(Object owner,String name){try{Class<?> type=owner.getClass();while(type!=null){try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}}
    private static Vec3 awaiting(ServerPlayer p){return (Vec3)read(p.connection,"awaitingPositionFromClient");}
    private static CompoundTag own(int request,int cooldown){CompoundTag tag=new CompoundTag();tag.putInt("version",1);tag.putInt("request",request);tag.putInt("cooldown",cooldown);return tag;}
    private static CompoundTag shared(int preview,int movement,int cooldown,int mastery){CompoundTag tag=new CompoundTag();tag.putInt("version",1);tag.putInt("preview",preview);tag.putInt("movement",movement);tag.putInt("cooldown",cooldown);tag.putInt("mastery",mastery);return tag;}
    private static void residency(GameTestHelper h,int issued,Fixture... fixtures){for(Fixture f:fixtures){h.assertTrue(f.p.server.getTickCount()-issued>=61&&(Integer)read(f.p,"spawnInvulnerableTime")<=0,"resident native ticks genuinely expire login protection");h.assertTrue(f.p.onGround()&&f.p.position().equals(f.anchor),"real native floor contact stays stable without repeated placements");}}
    public static final class TeleportHook {
        final ServerPlayer p;final Consumer<EntityTeleportEvent> callback;int calls,tick;
        TeleportHook(ServerPlayer p,Consumer<EntityTeleportEvent> callback){this.p=p;this.callback=callback;}
        @SubscribeEvent public void teleport(EntityTeleportEvent event){if(event.getEntity()==p){calls++;tick=p.server.getTickCount();callback.accept(event);}}
    }
    public static final class PostCheck {
        final Fixture f;final int tick;final Runnable action;boolean ran;
        PostCheck(Fixture f,int delay,Runnable action){this.f=f;tick=f.p.server.getTickCount()+delay;this.action=action;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event){
            if(ran||event.getServer()!=f.p.server||event.getServer().getTickCount()!=tick)return;ran=true;
            try{action.run();}catch(Throwable failure){if(f.postFailure==null)f.postFailure=failure;}
        }
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final Connection connection;final CommonListenerCookie cookie;final EmbeddedChannel channel;final Vec3 anchor,source;final int offset;
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<PostCheck> checks=new ArrayList<>();final Zombie shooter;Throwable postFailure;boolean closed;
        Fixture(GameTestHelper h){this(h,3,0);}
        Fixture(GameTestHelper h,int level,int offset){
            this.h=h;this.offset=offset;anchor=absolute(8.5,5,8.5);source=absolute(12.5,5,8.5);
            AABB arena=new AABB(anchor.add(-5,-1,-5),source.add(2,3,6));h.assertTrue(h.getBounds().contains(arena.minX,arena.minY,arena.minZ)&&h.getBounds().contains(arena.maxX,arena.maxY,arena.maxZ),"bounded fixture stays in actual native template");
            for(int x=4;x<=14;x++)for(int z=4;z<=14;z++)block(x,4,z,Blocks.STONE);
            var server=h.getLevel().getServer();cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"RM"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            for(var packet:positions())p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.getAbilities().invulnerable=false;place(anchor);
            h.assertTrue(HighPowerCombat.enroll(p,level),"declared first native high-power enrollment");learn(level);HighPowerResources.initializeBudget(p);epoch();drain();shooter=mob(absolute(13.5,5,13.5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
        }
        void learn(int level){var s=AbilityStorage.get(p);s.selectCategory(HighPowerCategories.TELEPORTER);s.setLevel(level);s.learn(SafeLandingProbe.ID);s.learn(ReturnMark.ID);s.activated=true;s.overloadFine=true;h.assertTrue(s.presets.edit(0,0,ReturnMark.ID,s::learned)&&s.presets.edit(0,1,SafeLandingProbe.ID,s::learned),"declared real learned322 and313 native slots");}
        void place(Vec3 at){p.moveTo(at.x,at.y,at.z,0,0);p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();h.assertTrue(p.onGround()&&p.position().equals(at),"native downward collision establishes genuine full-floor contact");}
        Vec3 absolute(double x,double y,double z){return h.absoluteVec(new Vec3(x+offset,y,z));}
        void hold(BlockPos pos){ChunkPos chunk=new ChunkPos(pos);if(!held.add(chunk))return;h.assertTrue(held.size()<=9,"fixture holds at most nine bounded native chunks");h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(chunk.x,chunk.z);}
        void hold(AABB bounds){for(int x=((int)Math.floor(bounds.minX))>>4;x<=((int)Math.floor(bounds.maxX))>>4;x++)for(int z=((int)Math.floor(bounds.minZ))>>4;z<=((int)Math.floor(bounds.maxZ))>>4;z++)hold(new BlockPos(x<<4,4,z<<4));}
        void block(int x,int y,int z,Block block){BlockPos pos=h.absolutePos(new BlockPos(x+offset,y,z));h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)),"fixture block stays in actual template");hold(pos);changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());}
        void floor(int x,int z){for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)block(x+dx,4,z+dz,Blocks.STONE);}
        Zombie mob(Vec3 at){Zombie mob=EntityType.HUSK.create(h.getLevel());mob.setNoAi(true);mob.setNoGravity(true);mob.setSilent(true);mob.setPos(at);h.getLevel().addFreshEntity(mob);entities.add(mob);return mob;}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean press(long input){return ReturnMark.press(p,wire(input));}
        boolean probe(long input){return SafeLandingProbe.press(p,new HighPowerInput.Input(1,input,p.getId(),epoch()).wire());}
        void begin(){h.assertTrue(press(1),"first native tap stores safe feet");place(source);h.assertTrue(press(2),"second fresh tap bypasses mark request fence and starts warning");var r=ReturnMark.receipt(p);h.assertTrue(ReturnMark.active(p)&&r!=null&&r.phase()==ReturnMark.WARNING&&!r.paid()&&!r.moved(),"warning starts without payment or native movement");drain();}
        void success(long input){var r=ReturnMark.receipt(p);h.assertTrue(r!=null&&r.input()==input&&r.token()>0&&r.phase()==ReturnMark.SUCCESS&&r.paid()&&r.moved()&&r.reason().isEmpty(),"one paid/moved historical native return: "+r);h.assertTrue(p.position().equals(anchor)&&r.source().equals(source)&&r.destination().equals(anchor)&&!ReturnMark.active(p)&&!ReturnMark.marked(p),"native success consumes anchor and returns to exact captured feet");}
        void freeReceipt(String expected){var r=ReturnMark.receipt(p);h.assertTrue(r!=null&&!r.paid()&&!r.moved()&&(r.phase()==ReturnMark.REFUSED||r.phase()==ReturnMark.ENDED)&&(expected==null?!r.reason().isEmpty():expected.equals(r.reason())),"free native terminal receipt, expected="+expected+", actual="+r);h.assertFalse(ReturnMark.active(p),"free terminal closes warning");}
        CompoundTag budget(){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);}
        CompoundTag own(){return p.getPersistentData().getCompound(OWN).copy();}
        CompoundTag shared(){return p.getPersistentData().getCompound(SHARED).copy();}
        double cp(){return HighPowerResources.cp(p);}
        double mastery(){return AbilityStorage.get(p).exp(ReturnMark.ID);}
        String hp(){return HighPowerCombat.healthText(p);}
        BigDecimal health(){return new BigDecimal(hp());}
        String shield(){return HighPowerCombat.shieldText(p);}
        Snapshot snapshot(){var s=AbilityStorage.get(p);return new Snapshot(cp(),budget().getLong("next_"+ReturnMark.ID),s.cooldowns.getOrDefault(ReturnMark.ID,0),mastery(),hp(),shield(),s.overload);}
        void same(Snapshot before,String why){h.assertTrue(snapshot().equals(before),why+" preserves CP, deadline, cooldown, mastery, HP, shield and overload");}
        void unchangedPools(Snapshot before,String why){h.assertTrue(hp().equals(before.hp())&&shield().equals(before.shield())&&mastery()==before.mastery()&&AbilityStorage.get(p).overload==before.overload(),why+" changes no HP, shield, mastery or overload");}
        List<Object> drain(){List<Object> packets=new ArrayList<>();channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){packets.add(packet);ReferenceCountUtil.release(packet);}return packets;}
        List<ClientboundPlayerPositionPacket> positions(){List<ClientboundPlayerPositionPacket> result=new ArrayList<>();for(Object packet:drain())if(packet instanceof ClientboundPlayerPositionPacket position)result.add(position);return result;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void post(int ticks,Runnable action){PostCheck check=new PostCheck(this,ticks,action);checks.add(check);hook(check);}
        void verifyPosts(){if(postFailure instanceof RuntimeException failure)throw failure;if(postFailure instanceof Error failure)throw failure;if(postFailure!=null)throw new AssertionError(postFailure);h.assertTrue(checks.stream().allMatch(check->check.ran),"all requested genuine native Post observations occurred");}
        void finish(int ticks){h.runAfterDelay(ticks,()->{verifyPosts();h.succeed();});}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);ReturnMark.remove(p);SafeLandingProbe.remove(p);PhaseSidestep.remove(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));held.clear();channel.finishAndReleaseAll();}
    }
}

package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW312 native tests. Learned skills and enrollment are declared fixtures, never
 * survival acquisition evidence. Native players are resident, acknowledge login,
 * and really age out login protection before any damage assertion. Arrow.tick calls
 * are explicit single native flight steps, not fabricated arrow damage sources.
 * This server suite does not execute LocalPlayer or establish keyboard/rendering QA.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerPhaseSidestepRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_phase_sidestep";
    private static final String OWN="academy:phase_sidestep_fences", SHARED="academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_phase_sidestep_fixture",UUID::compareTo);
    private HighPowerPhaseSidestepRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void one_press_moves_exactly_three_metres_with_one_debit_and_no_mastery(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();Vec3 velocity=new Vec3(.13,-.0784,-.09);f.p.setDeltaMovement(velocity);
        boolean ground=f.p.onGround();float fall=f.p.fallDistance;
        h.assertTrue(f.start(1),"one authenticated input immediately commits");
        var receipt=PhaseSidestep.receipt(f.p);Vec3 wanted=f.source.add(-dist(f.p),0,0);
        h.assertTrue(receipt!=null&&receipt.input()==1&&receipt.token()>0&&receipt.paid()&&receipt.moved()&&!receipt.left()&&receipt.reason().isEmpty(),"one paid historical right-step receipt");
        h.assertTrue(receipt.source().equals(f.source)&&receipt.destination().equals(wanted)&&f.p.position().equals(wanted),"exact absolute three-metre destination and unchanged Y");
        near(h,f.cp(),before.cp()-2,"one LV3 charge");
        h.assertTrue(f.p.getDeltaMovement().equals(velocity)&&f.p.onGround()==ground&&f.p.fallDistance==fall,"server velocity, fall and ground are preserved exactly");
        h.assertTrue(f.hp().equals(before.hp())&&f.shield().equals(before.shield())&&f.mastery()==before.mastery(),"no HP, shield or mastery mutation");
        Snapshot paid=f.snapshot();CompoundTag own=f.own(),shared=f.shared();
        h.assertFalse(PhaseSidestep.active(f.p),"immediate action leaves no active held context");
        PhaseSidestep.release(f.p,f.wire(1));h.assertFalse(f.start(1),"duplicate cannot move or charge twice");
        f.same(paid,"release and duplicate");h.assertTrue(f.own().equals(own)&&f.shared().equals(shared),"duplicates do not reset any persisted fence");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_absolute_xyz_rotation_packet_and_ack_have_no_double_offset(GameTestHelper h) {
        Fixture f=new Fixture(h);f.p.setYRot(37);f.p.setXRot(-31);Vec3 wanted=destination(f.p);f.drain();
        h.assertTrue(f.start(1),"native immediate commit");var packets=f.positions();
        h.assertTrue(packets.size()==1,"exactly one actual outbound native position packet");var packet=packets.getFirst();
        Vec3 actual=new Vec3(packet.getX(),packet.getY(),packet.getZ());
        h.assertTrue(actual.distanceTo(wanted)<1e-7&&actual.equals(f.p.position()),"packet carries validated absolute XYZ");
        h.assertTrue(packet.getRelativeArguments().equals(RelativeMovement.ROTATION),"only rotation is relative, leaving vanilla client XYZ velocity-reset semantics");
        near(h,packet.getYRot(),0,"one native yaw delta");near(h,packet.getXRot(),0,"one native pitch delta");
        near(h,f.p.getYRot(),37,"server yaw retained");near(h,f.p.getXRot(),-31,"server pitch retained");
        h.assertTrue(actual.equals(awaiting(f.p)),"native listener awaits exact absolute target");Snapshot paid=f.snapshot();
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()+1));
        h.assertTrue(actual.equals(awaiting(f.p)),"wrong native acknowledgment ID is inert");
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
        h.assertTrue(awaiting(f.p)==null&&f.p.position().equals(actual),"matching native acknowledgment never adds the source twice");f.same(paid,"native acknowledgment");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void server_yaw_cardinals_diagonals_pitch_and_sneak_define_side(GameTestHelper h) {
        for(float yaw:new float[]{0,90,180,270,45,-45,135})for(boolean left:new boolean[]{false,true}) {
            Fixture f=new Fixture(h);f.p.setYRot(yaw);f.p.setXRot(left?89:-89);f.p.setShiftKeyDown(left);Vec3 wanted=destination(f.p);
            h.assertTrue(f.start(1),"native direction accepts yaw="+yaw+" left="+left);
            h.assertTrue(f.p.position().distanceTo(wanted)<1e-6&&f.p.getY()==f.source.y,"yaw-only horizontal displacement has no pitch distance or vertical component");
            near(h,f.source.distanceTo(f.p.position()),dist(f.p),"all directions retain exact level-scaled length");
            h.assertTrue(PhaseSidestep.receipt(f.p).left()==left,"receipt captures actual server sneak state");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void forced_crouch_without_sneak_stays_right_while_actual_sneak_goes_left(GameTestHelper h) {
        for(boolean sneak:new boolean[]{false,true}) {
            Fixture f=new Fixture(h);f.p.setPose(Pose.CROUCHING);f.p.setShiftKeyDown(sneak);
            h.assertTrue(f.start(1),"supported native crouching body can step");
            near(h,f.p.getX(),f.source.x+(sneak?dist(f.p):-dist(f.p)),"server key state chooses side independently of forced pose");
            h.assertTrue(f.p.getPose()==Pose.CROUCHING&&PhaseSidestep.receipt(f.p).left()==sneak,"native pose retained with declared side");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void five_growth_anchors_keep_own_economics_and_existing_shared_tag_fields(GameTestHelper h) {
        double[] cost={8,4,2,.5,.05};int[] cooldown={40,20,10,4,1};
        for(int level=1;level<=5;level++) {
            Fixture f=new Fixture(h,level,0);CompoundTag old=shared(3,0,17,13);old.putString("unrelated","retain");f.p.getPersistentData().put(SHARED,old);
            double cp=f.cp();long time=f.p.serverLevel().getGameTime();h.assertTrue(f.start(1),"native anchor "+level);
            near(h,f.cp(),cp-cost[level-1],"one native curve debit at anchor "+level);
            h.assertTrue(f.own().getInt("version")==1&&f.own().getInt("request")==4&&f.own().getInt("cooldown")==cooldown[level-1],"separate request and economic physical fence");
            CompoundTag wanted=old.copy();wanted.putInt("movement",8);h.assertTrue(f.shared().equals(wanted),"312 updates only existing movement while preserving313 preview, cooldown, mastery and unrelated fields");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.get(PhaseSidestep.ID)==cooldown[level-1]&&f.budget().getLong("next_"+PhaseSidestep.ID)==time+cooldown[level-1],"native UI and economic clocks agree");
            near(h,f.mastery(),0,"312 grants no action mastery");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_fractional_growth_changes_cost_without_integer_level_jump(GameTestHelper h) {
        Fixture f=new Fixture(h,1,0);var state=AbilityStorage.get(f.p);state.addExperience(SafeLandingProbe.ID,.01);
        BigDecimal q=HighPowerGrowth.values(f.p).position();h.assertTrue(q.compareTo(BigDecimal.ONE)>0&&q.compareTo(BigDecimal.valueOf(2))<0&&state.level==1,"genuine prerequisite XP gives fractional native q");
        double before=f.cp(),mastery=f.mastery();h.assertTrue(f.start(1),"fractional q native action");double debit=before-f.cp();
        h.assertTrue(debit>4&&debit<8,"fractional q has an interior cost instead of an integer anchor");near(h,f.mastery(),mastery,"movement does not farm312 XP");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_full_wall_never_shortens_reverses_or_searches_behind_it(GameTestHelper h) {
        Fixture f=new Fixture(h);f.block(7,5,8,Blocks.STONE);f.block(7,6,8,Blocks.STONE);f.refuse("swept native full wall");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void shoulder_only_block_and_thin_native_pane_block_the_swept_body(GameTestHelper h) {
        for(boolean shoulder:new boolean[]{true,false}) {
            Fixture f=new Fixture(h);f.block(7,shoulder?6:5,8,shoulder?Blocks.STONE:Blocks.GLASS_PANE);
            f.refuse(shoulder?"head-height wall despite free feet ray":"thin native shape between safe endpoints");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void fluid_between_clear_endpoints_blocks_full_three_metre_path(GameTestHelper h) {
        Fixture f=new Fixture(h);f.block(7,5,8,Blocks.WATER);f.refuse("native fluid in swept path");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void diagonal_broadphase_corner_is_not_a_filled_sector_or_spurious_wall(GameTestHelper h) {
        Fixture f=new Fixture(h);f.p.setYRot(45);f.block(6,5,8,Blocks.STONE);Vec3 wanted=destination(f.p);
        h.assertTrue(f.start(1)&&f.p.position().distanceTo(wanted)<1e-7,"native block inside broad-phase rectangle but outside analytic swept body does not spuriously block");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void unregistered_native_phase_guard_refuses_unloaded_source_without_loading(GameTestHelper h) {
        Vec3 remote=new Vec3(25000000.5,90,25000000.5);ChunkPos chunk=new ChunkPos(BlockPos.containing(remote));
        var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"PSUnloaded"),false);
        var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);p.connection=new ServerGamePacketListenerImpl(server,connection,p,cookie);
        try {
            // Native setPos loads chunks for ADDED entities. This guard-only fixture is
            // deliberately not added, and makes no authenticated live-player cast claim.
            h.assertTrue(!p.isAddedToLevel()&&h.getLevel().getEntity(p.getId())==null&&h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"declared remote guard source is absent before native relocation");
            p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);p.setPos(remote);p.connection.resetPosition();
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"unregistered native preparation did not load source chunk");CompoundTag before=p.getPersistentData().copy();
            var assessment=SafeLandingProbeWorld.phase(p,false);h.assertTrue(!assessment.valid()&&assessment.reason().equals("unloaded_chunk"),"native phase guard fails closed on exact unloaded source");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&p.getPersistentData().equals(before)&&p.position().equals(remote),"read-only phase guard performs no load, correction or metadata write");h.succeed();
        }finally{p.discard();channel.finishAndReleaseAll();}
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void both_source_and_destination_need_genuine_full_footprint_support(GameTestHelper h) {
        for(int x:new int[]{8,-1}){Fixture f=new Fixture(h);int at=x<0?(int)Math.floor(8.5-dist(f.p))+0:x;if(x<0)at=(int)Math.floor(8.5-dist(f.p));f.block(at,4,8,Blocks.AIR);f.refuse("missing native support at "+at);f.close();}
        Fixture partial=new Fixture(h);partial.p.setPos(partial.source.add(-.4,0,0));partial.p.connection.resetPosition();partial.block((int)Math.floor(8.1-dist(partial.p)),4,8,Blocks.AIR);
        partial.refuse("only part of destination footprint is supported");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void full_destination_body_and_support_hazards_are_free_refusals(GameTestHelper h) {
        for(var hazard:List.of(Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.FIRE,Blocks.COBWEB,Blocks.POWDER_SNOW,Blocks.WATER)) {
            Fixture f=new Fixture(h);int y=hazard==Blocks.MAGMA_BLOCK?4:5;int hx=(int)Math.floor(8.5-dist(f.p));f.block(hx,y,8,hazard);
            h.assertTrue(h.getLevel().getBlockState(f.pos(hx,y,8)).is(hazard),"declared actual hazard exists");f.refuse("destination hazard "+hazard);f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void occupied_native_landing_and_crowded_entity_scan_are_free(GameTestHelper h) {
        for(int count:new int[]{1,65}) {
            Fixture f=new Fixture(h);for(int i=0;i<count;i++)f.mob(f.source.add(-dist(f.p),0,0));
            f.refuse("native occupied destination count="+count);f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_phase_sidestep_border",timeoutTicks=10)
    public static void entire_destination_envelope_must_remain_inside_native_world_border(GameTestHelper h) {
        Fixture f=new Fixture(h);var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
        try{border.setCenter(f.source.x,f.source.z);border.setSize(6.1);f.refuse("destination shoulder exceeds border although centre fits");}
        finally{border.setCenter(x,z);border.setSize(size);}h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void airborne_fall_motion_and_slowfall_never_become_air_rescue(GameTestHelper h) {
        for(int mode=0;mode<4;mode++) {
            Fixture f=new Fixture(h);switch(mode){case 0->f.p.setOnGround(false);case 1->f.p.fallDistance=7;case 2->f.p.setDeltaMovement(0,-.3,0);default->f.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,80));}
            Vec3 motion=f.p.getDeltaMovement();float fall=f.p.fallDistance;boolean ground=f.p.onGround();f.refuse("unsupported native airborne state "+mode);
            h.assertTrue(f.p.getDeltaMovement().equals(motion)&&f.p.fallDistance==fall&&f.p.onGround()==ground,"refusal never corrects gravity, grounding or accumulated fall");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void cancellation_is_before_own_debit_and_preserves_callback_resource_edit(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();TeleportHook hook=new TeleportHook(f.p,event->{f.budget().putDouble("cp",f.cp()-1);f.p.getPersistentData().putInt("academy:phase_test_callback",7);event.setCanceled(true);});f.hook(hook);
        h.assertFalse(f.start(1),"actual native event cancellation refuses");f.freeReceipt("event_cancelled");near(h,f.cp(),before.cp()-1,"foreign callback edit is retained without own debit or rollback");
        h.assertTrue(hook.calls==1&&f.p.position().equals(f.source)&&f.hp().equals(before.hp())&&f.shield().equals(before.shield())&&f.mastery()==before.mastery(),"cancellation keeps native source and all unrelated pools");
        h.assertTrue(f.budget().getLong("next_"+PhaseSidestep.ID)==before.deadline()&&f.p.getPersistentData().getInt("academy:phase_test_callback")==7,"cancellation neither sets economic deadline nor erases callback data");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void teleport_event_redirect_is_free_and_never_uses_new_destination(GameTestHelper h) {
        Fixture f=new Fixture(h);f.hook(new TeleportHook(f.p,event->event.setTargetZ(event.getTargetZ()+.1)));f.refuse("native callback redirect");f.freeReceipt("event_redirected");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void all_post_callback_source_pose_aim_motion_and_ground_changes_refuse(GameTestHelper h) {
        for(int mode=0;mode<8;mode++) {
            Fixture f=new Fixture(h);final int selected=mode;Snapshot before=f.snapshot();
            f.hook(new TeleportHook(f.p,event->{switch(selected){case 0->f.p.setPos(f.source.add(.1,0,0));case 1->f.p.setPose(Pose.CROUCHING);case 2->f.p.setYRot(90);case 3->f.p.setXRot(31);case 4->f.p.setDeltaMovement(.1,-.0784,0);case 5->f.p.setOnGround(false);case 6->f.p.fallDistance=2;default->f.p.setShiftKeyDown(true);}}));
            h.assertFalse(f.start(1),"post-event native state mutation refuses "+mode);f.freeReceipt("event_changed_state");f.same(before,"free changed-state callback "+mode);
            if(mode==0)h.assertTrue(f.p.position().equals(f.source.add(.1,0,0)),"foreign source edit is not rolled back");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void world_geometry_is_revalidated_after_native_teleport_callback(GameTestHelper h) {
        Fixture f=new Fixture(h);f.hook(new TeleportHook(f.p,event->f.block(7,6,8,Blocks.STONE)));f.refuse("new native swept shoulder blocker created by callback");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_phase_sidestep_world",timeoutTicks=10)
    public static void real_callback_dimension_change_is_retained_without_paying_old_world_action(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();var other=f.p.server.getLevel(Level.NETHER);h.assertTrue(other!=null,"native Nether exists");
        f.hook(new TeleportHook(f.p,event->{if(f.p.serverLevel()==h.getLevel())f.p.teleportTo(other,f.source.x,f.source.y,f.source.z,Set.of(),f.p.getYRot(),f.p.getXRot());}));
        h.assertFalse(f.start(1),"real callback world change revokes captured same-world action");f.freeReceipt("event_changed_state");near(h,f.cp(),before.cp(),"native world transition adds no312 debit");
        h.assertTrue(f.p.serverLevel()==other&&f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"controller preserves native world transition without rollback or pool changes");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void controller_callback_reentry_cannot_double_move_or_double_pay(GameTestHelper h) {
        Fixture f=new Fixture(h);double before=f.cp();boolean[] nested={true};TeleportHook hook=new TeleportHook(f.p,event->nested[0]=f.start(2));f.hook(hook);
        h.assertTrue(f.start(1),"outer immediate native action succeeds");h.assertFalse(nested[0],"inner same-controller attempt is denied while processing");
        near(h,f.cp(),before-2,"only one debit through reentry");h.assertTrue(hook.calls==1&&PhaseSidestep.receipt(f.p).input()==1&&f.p.position().equals(f.source.add(-dist(f.p),0,0)),"one event, original receipt and one displacement");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void callback_abort_of_captured_action_cannot_commit_or_pay(GameTestHelper h) {
        Fixture f=new Fixture(h);boolean[] aborted={false};f.hook(new TeleportHook(f.p,event->aborted[0]=PhaseSidestep.abort(f.p,f.wire(2))));
        f.refuse("explicit native callback abort");h.assertTrue(aborted[0],"abort authenticates captured processing identity");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void callback_remove_retires_player_without_reinserting_terminal_receipt(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();TeleportHook hook=new TeleportHook(f.p,event->PhaseSidestep.remove(f.p));f.hook(hook);
        h.assertFalse(f.start(1),"native callback removal retires the captured action before payment");
        f.same(before,"retired callback action");
        h.assertTrue(hook.calls==1&&f.p.position().equals(f.source)&&!PhaseSidestep.active(f.p)&&PhaseSidestep.receipt(f.p)==null,"outer refusal neither moves nor reinserts a receipt for the retired player");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void declared_fault_after_real_native_teleport_keeps_one_paid_moved_history(GameTestHelper h) {
        Fixture f=new Fixture(h);f.p.connection=new ServerGamePacketListenerImpl(f.p.server,f.connection,f.p,f.cookie){
            @Override public void teleport(double x,double y,double z,float yaw,float pitch,Set<RelativeMovement> relative){super.teleport(x,y,z,yaw,pitch,relative);throw new IllegalStateException("declared NEW312 fault after actual native commit");}
        };f.epoch();double cp=f.cp();f.drain();
        h.assertFalse(f.start(1),"injected post-native transport fault reports unconfirmed completion");var receipt=PhaseSidestep.receipt(f.p);
        h.assertTrue(receipt!=null&&receipt.paid()&&receipt.moved()&&!receipt.reason().isEmpty()&&!PhaseSidestep.active(f.p),"already paid and moved facts survive late fault");
        near(h,f.cp(),cp-2,"late fault never refunds");h.assertTrue(f.p.position().equals(f.source.add(-dist(f.p),0,0))&&f.positions().size()==1,"real native move occurred once before injected failure");
        Snapshot after=f.snapshot();h.assertFalse(f.start(1),"original input cannot retry postcommit fault");f.same(after,"late fault duplicate");h.assertTrue(PhaseSidestep.receipt(f.p).equals(receipt)&&f.shared().getInt("movement")==8,"historical receipt and physical fence remain");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void level_five_shared_motion_fence_decrements_once_and_312_then_313_waits_eight_ticks(GameTestHelper h) {
        Fixture f=new Fixture(h,5,0);h.assertTrue(f.start(1),"first312 move");int issued=f.p.server.getTickCount();
        f.after(4,()->{
            h.assertTrue(f.p.server.getTickCount()-issued==4&&f.shared().getInt("movement")==4&&f.own().getInt("cooldown")==0&&f.own().getInt("request")==0,"four genuine ticks decrement shared motion exactly once");
            Snapshot before=f.snapshot();h.assertFalse(f.start(2),"312 stays blocked after own economic expiry");f.same(before,"312 four-tick refusal");
            f.aim(f.source);double cp=f.cp();h.assertFalse(f.probe(1),"313 also respects312 physical movement fence");near(h,f.cp(),cp,"blocked313 remains free");
        });
        f.after(8,()->{
            h.assertTrue(f.shared().getInt("movement")==0&&f.p.onGround(),"genuine eighth boundary frees motion on native ground");
            f.aim(f.source);var preview=SafeLandingProbeWorld.preview(f.p);
            h.assertTrue(preview.valid(),"actual native313 return preview is valid");
            h.assertTrue(f.probe(2)&&f.probe(3),"313 can move at exact eighth boundary");
            h.assertTrue(f.p.position().equals(preview.candidate().destination()),"313 returns to its exact native float-ray preview, not the ideal aiming coordinate");
            h.assertTrue(f.shared().getInt("movement")==8,"313 return writes same eight-tick field");
            Snapshot before=f.snapshot();h.assertFalse(f.start(3),"312 cannot chain immediately after313");f.same(before,"reverse same-tick shared fence");
        });
        f.after(16,()->{f.p.setYRot(0);f.p.setXRot(0);h.assertTrue(f.shared().getInt("movement")==0&&f.start(4),"312 succeeds after313's real eighth boundary");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void existing_313_move_alone_blocks_312_until_exact_eighth_native_tick(GameTestHelper h) {
        Fixture f=new Fixture(h,5,0);f.aim(f.absolute(11.5,5,11.5));h.assertTrue(f.probe(1)&&f.probe(2),"real313 native move commits first");f.p.setYRot(90);f.p.setXRot(0);
        f.after(4,()->{h.assertTrue(f.shared().getInt("movement")==4,"one shared decrement per real tick");f.refuse("312 cannot bypass existing313 movement at four ticks",1);});
        f.after(8,()->{h.assertTrue(f.shared().getInt("movement")==0&&f.start(2),"312 becomes eligible exactly eight native ticks after313");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void malformed_own_or_legacy_shared_storage_fails_closed_without_normalizing(GameTestHelper h) {
        List<Consumer<CompoundTag>> ownBad=List.of(t->t.putInt("version",2),t->t.putString("request","4"),t->t.putInt("request",5),t->t.putInt("cooldown",41),t->t.putInt("cooldown",-1));
        for(var corrupt:ownBad){Fixture f=new Fixture(h);CompoundTag tag=own(0,0);corrupt.accept(tag);f.p.getPersistentData().put(OWN,tag);f.refuse("malformed312 tag");h.assertTrue(f.own().equals(tag),"312 corrupt storage is never normalized into eligibility");f.close();}
        List<Consumer<CompoundTag>> sharedBad=List.of(t->t.putInt("version",2),t->t.putString("movement","8"),t->t.putInt("movement",9),t->t.putInt("preview",5),t->t.putInt("cooldown",41),t->t.putInt("mastery",21),t->t.remove("mastery"));
        for(var corrupt:sharedBad){Fixture f=new Fixture(h);CompoundTag tag=shared(0,0,0,0);corrupt.accept(tag);f.p.getPersistentData().put(SHARED,tag);f.refuse("malformed legacy313 tag");h.assertTrue(f.shared().equals(tag),"legacy corrupt storage stays intact and fails closed");f.close();}h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void actual_player_nbt_reload_and_category_roundtrip_preserve_paid_resources_and_fences(GameTestHelper h) {
        Fixture f=new Fixture(h,5,0);h.assertTrue(f.start(1),"paid native action");double cp=f.cp();CompoundTag own=f.own(),shared=f.shared();String hp=f.hp(),shield=f.shield();
        var state=AbilityStorage.get(f.p);state.changeCategoryClassic(HighPowerCategories.MELTDOWNER);state.changeCategoryClassic(HighPowerCategories.TELEPORTER);f.learn(5);
        near(h,f.cp(),cp,"category roundtrip does not refill shared CP");h.assertTrue(f.own().equals(own)&&f.shared().equals(shared),"category roundtrip retains both existing tags");
        AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());PhaseSidestep.remove(f.p);SafeLandingProbe.remove(f.p);f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.p.setOnGround(true);f.p.setDeltaMovement(0,-.0784,0);f.p.connection.resetPosition();f.epoch();
        near(h,f.cp(),cp,"actual native NBT reload never refills paid CP");h.assertTrue(f.hp().equals(hp)&&f.shield().equals(shield)&&f.own().equals(own)&&f.shared().equals(shared),"native reload retains health/shield and both physical fences");
        Snapshot before=f.snapshot();h.assertFalse(f.start(2),"fresh authenticated epoch does not clear retained movement fence");f.same(before,"native reload retry");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void actual_clone_event_retains_both_motion_and_economic_fences_without_retry_refill(GameTestHelper h) {
        Fixture f=new Fixture(h,5,0),replacement=new Fixture(h,5,16);h.assertTrue(f.start(1),"original paid native action");CompoundTag own=f.own(),shared=f.shared();
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.p,f.p,false));
        h.assertTrue(replacement.own().equals(own)&&replacement.shared().equals(shared)&&!PhaseSidestep.active(f.p),"actual native clone copies both remaining fences and closes original action");
        // Replacement CP follows the existing shared clone policy; this case does not
        // redefine that policy or claim its declared enrollment budget was a death refill.
        Snapshot before=replacement.snapshot();Vec3 position=replacement.p.position();replacement.epoch();h.assertFalse(replacement.start(2),"clone cannot evade copied physical movement fence");
        replacement.same(before,"clone retry grants no resource refill or extra debit");h.assertTrue(replacement.p.position().equals(position),"clone retry has no movement");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void real_portable_parent_then_312_each_pay_2340_if_without_refilling_live_depleted_ledgers(GameTestHelper h) {
        Fixture f=new Fixture(h,1,0);var s=AbilityStorage.get(f.p);s.unlearn(PhaseSidestep.ID);s.unlearn(SafeLandingProbe.ID);int issued=f.p.server.getTickCount();
        var entry=SkillCatalog.find(HighPowerCategories.TELEPORTER,PhaseSidestep.ID).orElseThrow();
        h.assertTrue(entry.level()==1&&entry.requirements().equals(List.of(new SkillCatalog.Requirement(SafeLandingProbe.ID,0))),"312 is native TP LV1 with learned313 and no invented mastery threshold");
        f.after(61,()->{
            residency(h,issued,f);h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter),3),"actual native hit depletes already enrolled HP");
            String category=s.category;s.category=HighPowerCategories.ELECTROMASTER;
            try{h.assertTrue(HighPowerCombat.spendShield(f.p,BigDecimal.ONE),"declared setup performs real central EM base-shield debit");}finally{s.category=category;}
            f.budget().putDouble("cp",37);var growth=HighPowerGrowth.values(f.p);String hp=f.hp(),shield=f.shield();CompoundTag resources=f.budget().copy();double xp=s.levelExperience;
            var blocked=f.portable(2340);h.assertFalse(f.requestLearn(PhaseSidestep.ID,1),"canonical native learning ingress refuses missing learned313");h.assertTrue(blocked.energy()==2340,"missing parent takes no IF");
            var parent=f.portable(2340);h.assertTrue(f.requestLearn(SafeLandingProbe.ID,2),"actual finite native prerequisite purchase starts");f.develop(77);
            h.assertTrue(parent.energy()==30&&!s.learned(SafeLandingProbe.ID),"313 still awaits final paid tick");f.develop(1);h.assertTrue(parent.energy()==0&&s.learned(SafeLandingProbe.ID)&&s.exp(SafeLandingProbe.ID)==0,"78 native developer ticks genuinely learn313 for2340IF");
            var battery=f.portable(2340);h.assertTrue(f.requestLearn(PhaseSidestep.ID,Integer.MAX_VALUE),"canonical native312 request starts with actual learned parent");f.develop(77);
            h.assertTrue(battery.energy()==30&&!s.learned(PhaseSidestep.ID),"312 stays unlearned before78th paid native tick");f.develop(1);
            h.assertTrue(battery.energy()==0&&s.learned(PhaseSidestep.ID)&&s.exp(PhaseSidestep.ID)==0&&s.levelExperience==xp&&DevelopmentController.process(f.p).state()==DevelopmentProcess.State.DONE,"exact2340IF payment grants312 once without mastery or level XP");
            h.assertTrue(f.hp().equals(hp)&&f.shield().equals(shield)&&f.budget().equals(resources)&&HighPowerGrowth.values(f.p).position().compareTo(growth.position())==0&&HighPowerGrowth.values(f.p).earnedPosition().compareTo(growth.earnedPosition())==0,"real paid learning preserves depleted live HP/shield/CP and q/Q");
            h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("preset_edit","0:0:"+PhaseSidestep.ID)),"genuinely learned canonical312 binds through ordinary native preset ingress");
            h.assertFalse(f.requestLearn(PhaseSidestep.ID,3),"already learned312 cannot buy twice");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void real_portable_final_shortage_abort_or_parent_loss_never_grants_312(GameTestHelper h) {
        for(String reason:List.of("shortage","abort","parent")) {
            Fixture f=new Fixture(h,1,0);var s=AbilityStorage.get(f.p);s.unlearn(PhaseSidestep.ID);f.budget().putDouble("cp",37);String hp=f.hp(),shield=f.shield();
            var battery=f.portable(reason.equals("shortage")?2339:2340);h.assertTrue(f.requestLearn(PhaseSidestep.ID,1),"actual native finite312 development starts before "+reason);f.develop(77);
            h.assertTrue(!s.learned(PhaseSidestep.ID)&&battery.energy()==(reason.equals("shortage")?29:30),"exact remaining IF before failed final tick");
            if(reason.equals("abort"))DevelopmentController.abort(f.p);else if(reason.equals("parent"))s.unlearn(SafeLandingProbe.ID);f.develop(1);
            h.assertTrue(!s.learned(PhaseSidestep.ID)&&DevelopmentController.process(f.p).state()==DevelopmentProcess.State.FAILED&&battery.energy()==(reason.equals("abort")?30:0),"final shortage/abort/parent revalidation gives no312 grant or refund");
            h.assertTrue(f.cp()==37&&f.hp().equals(hp)&&f.shield().equals(shield),"failed real purchase never refills shared live pools");f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void genuine_arrow_hits_stationary_control_misses_stepped_body_and_destination_arrow_still_hurts(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int issued=f.p.server.getTickCount();
        f.after(61,()->{
            residency(h,issued,f,control);BigDecimal hp=f.health(),controlHp=control.health();String shield=f.shield();double mastery=f.mastery();
            Arrow miss=f.arrow(f.source.add(0,1,3),new Vec3(0,0,-4)),hit=control.arrow(control.source.add(0,1,3),new Vec3(0,0,-4));
            Vec3 motion=miss.getDeltaMovement();h.assertTrue(f.start(1),"step commits before actual incoming native arrow flight");miss.tick();hit.tick();
            h.assertTrue(control.health().compareTo(controlHp)<0&&hit.isRemoved(),"paired stationary control is genuinely hit by its native arrow");
            h.assertTrue(f.health().compareTo(hp)==0&&!miss.isRemoved()&&miss.position().z<f.source.z,"same lane physically misses displaced native body");
            h.assertTrue(miss.getOwner()==f.shooter&&!miss.isNoPhysics()&&motion.x==miss.getDeltaMovement().x,"sidestep neither changes arrow attribution nor adds collision bypass");
            h.assertTrue(f.shield().equals(shield)&&f.mastery()==mastery,"avoidance spends no shield and awards no312 mastery");
            Arrow destination=f.arrow(f.p.position().add(0,1,3),new Vec3(0,0,-4));destination.tick();
            h.assertTrue(destination.isRemoved()&&f.health().compareTo(hp)<0,"arrow aimed at actual destination still causes native damage immediately after the step");
            h.assertTrue(f.shield().equals(shield)&&f.p.hurtTime>0,"destination hit stays ordinary HP damage with native hurt timer");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void ordinary_world_ticked_arrows_hit_control_miss_step_and_later_hit_actual_destination(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int issued=f.p.server.getTickCount();
        f.after(61,()->{
            residency(h,issued,f,control);BigDecimal hp=f.health(),controlHp=control.health();String shield=f.shield();double mastery=f.mastery();
            Vec3 launch=f.source.add(0,1.4,6),velocity=new Vec3(0,0,-1.5);
            Arrow miss=f.arrow(launch,velocity),hit=control.arrow(control.source.add(0,1.4,6),velocity);int launched=f.p.server.getTickCount();
            h.assertTrue(miss.tickCount==0&&hit.tickCount==0,"fresh native arrows begin before any world flight tick");
            h.assertTrue(f.start(1),"manual sidestep completes before ordinary world flight reaches original body");
            // No Arrow.tick call: resident ServerLevel entity ticking owns both flights.
            f.after(5,()->{
                h.assertTrue(f.p.server.getTickCount()-launched==5&&miss.tickCount>0&&miss.position().distanceTo(launch)>5,"five genuine server boundaries let the resident native world advance arrow flight");
                h.assertTrue(hit.isRemoved()&&control.health().compareTo(controlHp)<0,"world-ticked paired arrow genuinely hits stationary control");
                h.assertTrue(!miss.isRemoved()&&miss.position().z<f.source.z&&f.health().compareTo(hp)==0,"world-ticked original lane passes the source and physically misses displaced body");
                h.assertTrue(f.shield().equals(shield)&&f.mastery()==mastery,"ordinary-flight avoidance neither spends shield nor awards312 mastery");
                Vec3 actualDestination=f.p.position();Arrow aimed=f.arrow(actualDestination.add(0,1.4,6),velocity);int relaunched=f.p.server.getTickCount();
                h.assertTrue(aimed.tickCount==0,"destination arrow also begins as an unticked native entity");
                f.after(5,()->{
                    h.assertTrue(f.p.server.getTickCount()-relaunched==5&&aimed.tickCount>0&&aimed.isRemoved(),"ordinary resident world flight settles the later destination impact");
                    h.assertTrue(f.health().compareTo(hp)<0&&f.shield().equals(shield)&&f.mastery()==mastery,"arrow aimed at actual destination causes ordinary native HP damage without protection or mastery");h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void genuine_native_armor_mitigation_matches_stepped_and_stationary_controls(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int issued=f.p.server.getTickCount();
        for(Fixture x:List.of(f,control))x.p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        f.after(61,()->{
            residency(h,issued,f,control);h.assertTrue(f.start(1),"native sidestep while wearing actual armor");BigDecimal hp=f.health(),other=control.health();String shield=f.shield();
            f.arrow(f.p.position().add(0,1,3),new Vec3(0,0,-4)).tick();control.arrow(control.p.position().add(0,1,3),new Vec3(0,0,-4)).tick();
            BigDecimal damage=hp.subtract(f.health()),expected=other.subtract(control.health());h.assertTrue(damage.signum()>0&&damage.compareTo(expected)==0,"same actual armor gives identical post-native damage with or without step");
            h.assertTrue(f.p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()>0&&f.shield().equals(shield),"native armor durability applies while high-power shield is untouched");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void real_handheld_shield_blocks_ordinary_front_arrow_without_sidesteps_spending_shared_shield(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int issued=f.p.server.getTickCount();
        f.after(61,()->{
            residency(h,issued,f,control);for(Fixture x:List.of(f,control)){x.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));x.p.startUsingItem(InteractionHand.OFF_HAND);}
        });
        f.after(68,()->{
            h.assertTrue(f.p.isBlocking()&&control.p.isBlocking(),"both native handheld shields pass real use-duration delay");BigDecimal hp=f.health(),other=control.health();String shield=f.shield();
            h.assertTrue(f.start(1)&&f.p.isBlocking(),"sidestep preserves actual handheld blocking");
            f.arrow(f.p.position().add(0,1,3),new Vec3(0,0,-4)).tick();control.arrow(control.p.position().add(0,1,3),new Vec3(0,0,-4)).tick();
            h.assertTrue(f.health().compareTo(hp)==0&&control.health().compareTo(other)==0,"actual native front-arrow blocking matches stationary control");
            h.assertTrue(f.p.getOffhandItem().getDamageValue()>0&&control.p.getOffhandItem().getDamageValue()>0&&f.shield().equals(shield),"native item durability, not high-power pool, pays ordinary shield block");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void native_hurt_timers_fire_poison_and_other_attached_statuses_are_not_cleared(GameTestHelper h) {
        Fixture f=new Fixture(h);int issued=f.p.server.getTickCount();f.after(61,()->{
            residency(h,issued,f);h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter),4),"real native damage establishes ordinary hurt timer");
            // Ground contact is established once after genuine knockback, without changing timers.
            f.p.setDeltaMovement(0,-.0784,0);f.p.setOnGround(true);f.p.fallDistance=0;
            f.p.setRemainingFireTicks(80);f.p.addEffect(new MobEffectInstance(MobEffects.POISON,120));f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,140));
            int hurt=f.p.hurtTime,invulnerable=f.p.invulnerableTime,fire=f.p.getRemainingFireTicks(),poison=f.p.getEffect(MobEffects.POISON).getDuration();Snapshot before=f.snapshot();
            h.assertTrue(f.start(1),"grounded injured and burning source can step");
            h.assertTrue(f.p.hurtTime==hurt&&f.p.invulnerableTime==invulnerable&&f.p.getRemainingFireTicks()==fire&&f.p.isOnFire(),"step does not clear or extend ordinary damage timers or fire");
            h.assertTrue(f.p.getEffect(MobEffects.POISON).getDuration()==poison&&f.p.hasEffect(MobEffects.WEAKNESS),"poison and other attached effects stay intact");
            h.assertTrue(f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"step adds neither healing nor high-power shield mutation");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void genuine_native_fall_damage_after_step_equals_matched_stationary_control(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,16);int issued=f.p.server.getTickCount();f.after(61,()->{
            residency(h,issued,f,control);h.assertTrue(f.start(1),"grounded native sidestep before declared fall");BigDecimal hp=f.health(),other=control.health();String shield=f.shield();
            f.p.setOnGround(false);control.p.setOnGround(false);f.p.fallDistance=8;control.p.fallDistance=8;
            h.assertTrue(f.p.causeFallDamage(8,1,f.p.damageSources().fall())&&control.p.causeFallDamage(8,1,control.p.damageSources().fall()),"both genuine native fall damage paths accept equal measured fall");
            BigDecimal damage=hp.subtract(f.health()),expected=other.subtract(control.health());h.assertTrue(damage.signum()>0&&damage.compareTo(expected)==0,"sidestep grants no native fall-damage advantage");
            h.assertTrue(f.shield().equals(shield)&&!f.p.isNoGravity()&&!f.p.getAbilities().flying,"no shield spend, gravity toggle or flight setting");h.succeed();
        });
    }

    private record Snapshot(double cp,long deadline,int cooldown,double mastery,String hp,String shield,double overload) {}
    private static void near(GameTestHelper h,double value,double wanted,String why){h.assertTrue(Math.abs(value-wanted)<1e-7,why+": "+value+" != "+wanted);}
    /** The admitted sidestep distance of this player's growth (3 m at LV1, up to 6 m at LV5). */
    static double dist(ServerPlayer p){return cn.academy.port.highpower.core.PhaseSidestepRules.distance(HighPowerGrowth.values(p).position());}
    private static Vec3 destination(ServerPlayer p){double a=Math.toRadians(p.getYRot()),side=p.isShiftKeyDown()?dist(p):-dist(p);return p.position().add(Math.cos(a)*side,0,Math.sin(a)*side);}
    private static Object read(Object owner,String name){try{Class<?> type=owner.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}}
    private static Vec3 awaiting(ServerPlayer p){return (Vec3)read(p.connection,"awaitingPositionFromClient");}
    private static CompoundTag own(int request,int cooldown){CompoundTag t=new CompoundTag();t.putInt("version",1);t.putInt("request",request);t.putInt("cooldown",cooldown);return t;}
    private static CompoundTag shared(int preview,int movement,int cooldown,int mastery){CompoundTag t=new CompoundTag();t.putInt("version",1);t.putInt("preview",preview);t.putInt("movement",movement);t.putInt("cooldown",cooldown);t.putInt("mastery",mastery);return t;}
    private static void residency(GameTestHelper h,int issued,Fixture... fixtures){for(Fixture f:fixtures){h.assertTrue(f.p.server.getTickCount()-issued>=61&&(Integer)read(f.p,"spawnInvulnerableTime")<=0,"real resident native ticks expire login protection");h.assertTrue(f.p.onGround()&&f.p.position().equals(f.source),"genuine native floor contact stays stable without repeated placement");}}
    public static final class TeleportHook {
        final ServerPlayer p;final Consumer<EntityTeleportEvent> callback;int calls;
        TeleportHook(ServerPlayer p,Consumer<EntityTeleportEvent> callback){this.p=p;this.callback=callback;}
        @SubscribeEvent public void teleport(EntityTeleportEvent event){if(event.getEntity()==p){calls++;callback.accept(event);}}
    }
    /** Player physics precedes native GameTest callbacks without sharing their schedule map. */
    public static final class NativePlayerPump {
        final ServerPlayer p;final int until;int last;
        NativePlayerPump(ServerPlayer p,int duration){this.p=p;last=p.server.getTickCount();until=last+duration;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Pre event){
            if(event.getServer()!=p.server)return;int tick=p.server.getTickCount();
            if(tick<=last||tick>until||p.isRemoved()||p.hasDisconnected())return;
            last=tick;p.doTick();
        }
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final Connection connection;final CommonListenerCookie cookie;final EmbeddedChannel channel;final Vec3 source;final int offset;
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final Zombie shooter;boolean closed;
        Fixture(GameTestHelper h){this(h,3,0);}
        Fixture(GameTestHelper h,int level,int offset){
            this.h=h;this.offset=offset;source=h.absoluteVec(new Vec3(8.5+offset,5,8.5));
            AABB arena=new AABB(source.add(-5,-1,-5),source.add(6,3,7));h.assertTrue(h.getBounds().contains(arena.minX,arena.minY,arena.minZ)&&h.getBounds().contains(arena.maxX,arena.maxY,arena.maxZ),"bounded native fixture stays inside supplied template");
            int minX=((int)Math.floor(arena.minX))>>4,maxX=((int)Math.floor(arena.maxX))>>4,minZ=((int)Math.floor(arena.minZ))>>4,maxZ=((int)Math.floor(arena.maxZ))>>4;
            h.assertTrue((maxX-minX+1)*(maxZ-minZ+1)<=9,"at most nine bounded resident chunks");
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){ChunkPos chunk=new ChunkPos(x,z);held.add(chunk);h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(x,z);}
            for(int x=2;x<=14;x++)for(int z=2;z<=14;z++)block(x,4,z,Blocks.STONE);
            var server=h.getLevel().getServer();cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"PS"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            for(var packet:positions())p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.getAbilities().invulnerable=false;p.moveTo(source.x,source.y,source.z,0,0);p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);
            // One real downward collision avoids initial zero-motion travel clearing ground.
            p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();
            h.assertTrue(p.onGround()&&p.position().equals(source),"actual native downward collision establishes floor contact");
            h.assertTrue(HighPowerCombat.enroll(p,level),"declared native high-power enrollment");learn(level);HighPowerResources.initializeBudget(p);epoch();drain();
            shooter=mob(source.add(3,0,6));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            hook(new NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
        }
        void learn(int level){var s=AbilityStorage.get(p);s.selectCategory(HighPowerCategories.TELEPORTER);s.setLevel(level);s.learn(SafeLandingProbe.ID);s.learn(PhaseSidestep.ID);s.activated=true;s.overloadFine=true;h.assertTrue(s.presets.edit(0,0,PhaseSidestep.ID,s::learned)&&s.presets.edit(0,1,SafeLandingProbe.ID,s::learned),"real native learned312 and313 slots");}
        Vec3 absolute(double x,double y,double z){return h.absoluteVec(new Vec3(x+offset,y,z));}
        BlockPos pos(int x,int y,int z){return h.absolutePos(new BlockPos(x+offset,y,z));}
        void block(int x,int y,int z,net.minecraft.world.level.block.Block block){BlockPos pos=pos(x,y,z);h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)),"fixture block bounded by template");changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());}
        Zombie mob(Vec3 at){Zombie mob=EntityType.HUSK.create(h.getLevel());mob.setNoAi(true);mob.setNoGravity(true);mob.setSilent(true);mob.setPos(at);h.getLevel().addFreshEntity(mob);entities.add(mob);return mob;}
        Arrow arrow(Vec3 at,Vec3 velocity){Arrow arrow=new Arrow(h.getLevel(),shooter,new ItemStack(Items.ARROW),null);arrow.setPos(at);arrow.setDeltaMovement(velocity);h.getLevel().addFreshEntity(arrow);entities.add(arrow);return arrow;}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean start(long input){return PhaseSidestep.start(p,wire(input));}
        boolean probe(long input){return SafeLandingProbe.press(p,new HighPowerInput.Input(1,input,p.getId(),epoch()).wire());}
        DeveloperItemEnergy portable(double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);var battery=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);battery.energy(amount);return battery;}
        boolean requestLearn(String skill,int input){return AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("hp_learn",new HighPowerLearningProtocol.Request(skill,input).wire()));}
        void develop(int ticks){for(int i=0;i<ticks;i++)DevelopmentController.tick(p,ignored->{});}
        // Completion must run inside GameTestInfo.tick so its native runner is notified.
        void after(int ticks,Runnable action){h.runAfterDelay(ticks,action);}
        CompoundTag budget(){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);}
        CompoundTag own(){return p.getPersistentData().getCompound(OWN).copy();}
        CompoundTag shared(){return p.getPersistentData().getCompound(SHARED).copy();}
        double cp(){return HighPowerResources.cp(p);}
        double mastery(){return AbilityStorage.get(p).exp(PhaseSidestep.ID);}
        String hp(){return HighPowerCombat.healthText(p);}
        BigDecimal health(){return new BigDecimal(hp());}
        String shield(){return HighPowerCombat.shieldText(p);}
        Snapshot snapshot(){var s=AbilityStorage.get(p);return new Snapshot(cp(),budget().getLong("next_"+PhaseSidestep.ID),s.cooldowns.getOrDefault(PhaseSidestep.ID,0),mastery(),hp(),shield(),s.overload);}
        void same(Snapshot before,String why){h.assertTrue(snapshot().equals(before),why+" preserves CP, economic deadline, cooldown, mastery, HP, shield and overload");}
        void freeReceipt(String reason){var r=PhaseSidestep.receipt(p);h.assertTrue(r!=null&&!r.paid()&&!r.moved()&&(reason==null?!r.reason().isEmpty():reason.equals(r.reason())),"free terminal receipt: expected="+reason+", actual="+r);h.assertFalse(PhaseSidestep.active(p),"free refusal closes exact context");}
        void refuse(String why){refuse(why,1);}
        void refuse(String why,long input){Snapshot before=snapshot();Vec3 position=p.position();h.assertFalse(start(input),why);freeReceipt(null);same(before,why);h.assertTrue(p.position().equals(position),why+" leaves native source unchanged");}
        List<Object> drain(){List<Object> result=new ArrayList<>();channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){result.add(packet);ReferenceCountUtil.release(packet);}return result;}
        List<ClientboundPlayerPositionPacket> positions(){List<ClientboundPlayerPositionPacket> result=new ArrayList<>();for(Object packet:drain())if(packet instanceof ClientboundPlayerPositionPacket p)result.add(p);return result;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);DevelopmentController.remove(p);PhaseSidestep.remove(p);SafeLandingProbe.remove(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}

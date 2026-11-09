package cn.academy.port.gametest;

import cn.academy.port.highpower.core.SafeLandingProbeRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW313 native definitions. Learned bits are declared fixtures, not acquisition evidence.
 * Uses native full-block floors, actual server ticks, real outbound position packets and
 * the pinned native acknowledgment handler. This suite does not execute a LocalPlayer
 * or establish client rendering, client velocity preservation or actual keyboard input.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerSafeLandingProbeRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_safe_landing_probe";
    private static final String FENCES="academy:safe_landing_probe_fences";
    private static final TicketType<UUID> FIXTURE_TICKET=TicketType.create("academy_safe_landing_fixture",UUID::compareTo);
    private HighPowerSafeLandingProbeRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void preview_release_and_duplicate_press_are_free_then_new_press_commits_once(GameTestHelper h) {
        Fixture f=new Fixture(h); Snapshot before=f.snapshot();
        Vec3 velocity=new Vec3(.13,-.0784,-.09); f.p.setDeltaMovement(velocity);
        boolean grounded=f.p.onGround(); float fall=f.p.fallDistance;
        var candidate=f.candidate(); f.hook(new TeleportHook(f.p,event->{}));
        h.assertTrue(f.press(1)&&SafeLandingProbe.active(f.p),"first authenticated input creates one frozen preview");
        f.same(before,"preview"); h.assertTrue(f.p.position().equals(f.source),"preview leaves exact source position");
        h.assertTrue(SafeLandingProbe.release(f.p,f.wire(1))&&SafeLandingProbe.active(f.p),"key-up is inert");
        h.assertFalse(f.press(1),"same input cannot confirm"); f.same(before,"release and duplicate preview");
        h.assertTrue(f.press(2),"second new sequence explicitly confirms");
        var receipt=SafeLandingProbe.receipt(f.p);
        h.assertTrue(receipt!=null&&receipt.input()==2&&receipt.token()>0&&receipt.paid()&&receipt.moved()&&receipt.reason().isEmpty(),"public receipt certifies one paid native move");
        h.assertTrue(receipt.source().equals(f.source)&&receipt.destination().equals(candidate.destination())
                &&f.p.position().equals(candidate.destination()),"actual native server position equals the frozen landing");
        near(h,f.cp(),before.cp()-2,"one LV3 debit");
        h.assertTrue(f.p.getDeltaMovement().equals(velocity)&&f.p.fallDistance==fall&&f.p.onGround()==grounded,"native commit leaves server velocity, fall and ground untouched");
        h.assertTrue(f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"teleport spends neither HP nor shield");
        double paid=f.cp(), mastery=f.mastery(); CompoundTag fences=f.fences();
        h.assertFalse(f.press(2),"duplicate confirmation cannot replay");
        near(h,f.cp(),paid,"duplicate never debits twice"); near(h,f.mastery(),mastery,"duplicate never rewards twice");
        h.assertTrue(f.fences().equals(fences)&&!SafeLandingProbe.active(f.p),"success cleans exact context without resetting fences"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void real_native_packet_has_absolute_xyz_rotation_flags_and_exact_ack_without_double_offset(GameTestHelper h) {
        Fixture f=new Fixture(h); var candidate=f.candidate();
        h.assertTrue(Math.abs(f.source.x)>1&&Math.abs(f.source.y)>1&&Math.abs(f.source.z)>1&&Math.abs(f.p.getYRot())>1&&Math.abs(f.p.getXRot())>1,"nonzero native XYZ and nonzero yaw/pitch expose offset bugs");
        float yaw=f.p.getYRot(),pitch=f.p.getXRot(); f.drain();
        h.assertTrue(f.press(1)&&f.press(2),"real public controller commits");
        var packets=f.positions(); h.assertTrue(packets.size()==1,"exactly one actual outbound native position packet");
        var packet=packets.getFirst(); Vec3 destination=candidate.destination();
        h.assertTrue(new Vec3(packet.getX(),packet.getY(),packet.getZ()).equals(destination),"outbound XYZ are absolute validated coordinates");
        h.assertTrue(packet.getRelativeArguments().equals(RelativeMovement.ROTATION),"only the pinned native rotation flags are set");
        near(h,packet.getYRot(),0,"native server calculates yaw delta once"); near(h,packet.getXRot(),0,"native server calculates pitch delta once");
        near(h,f.p.getYRot(),yaw,"server retains current yaw"); near(h,f.p.getXRot(),pitch,"server retains current pitch");
        h.assertTrue(destination.equals(awaiting(f.p)),"native listener awaits the exact absolute target");
        double paid=f.cp(),xp=f.mastery();
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()+1));
        h.assertTrue(destination.equals(awaiting(f.p))&&f.p.position().equals(destination),"wrong-ID native acknowledgment is inert");
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
        h.assertTrue(awaiting(f.p)==null&&f.p.position().equals(destination),"actual native acknowledgment clears pending target with no double offset");
        near(h,f.cp(),paid,"acknowledgment never repeats debit"); near(h,f.mastery(),xp,"acknowledgment never repeats award");
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
        h.assertTrue(!f.p.connection.isAcceptingMessages(),"matching-ID replay follows pinned vanilla invalid-movement disconnect");
        h.assertTrue(f.p.position().equals(destination),"replayed native acknowledgment cannot move again");
        near(h,f.cp(),paid,"native replay spends no extra CP"); near(h,f.mastery(),xp,"native replay gives no extra XP"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void all_five_native_growth_anchors_pay_one_smooth_curve_debit_and_keep_exact_fences(GameTestHelper h) {
        double[] costs={8,4,2,.5,.05}; int[] cooldowns={40,20,10,4,1};
        for(int level=1;level<=5;level++) {
            Fixture f=new Fixture(h,level,5); double cp=f.cp(); long time=f.p.serverLevel().getGameTime();
            h.assertTrue(f.press(1)&&f.press(2),"native confirmed cast at level "+level);
            near(h,f.cp(),cp-costs[level-1],"native curve debit at level "+level);
            h.assertTrue(f.fences().getInt("preview")==4&&f.fences().getInt("movement")==8
                    &&f.fences().getInt("cooldown")==cooldowns[level-1]&&f.fences().getInt("mastery")==20,"persisted physical fences at anchor "+level);
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.get(SafeLandingProbe.ID)==cooldowns[level-1]
                    &&f.budget().getLong("next_"+SafeLandingProbe.ID)==time+cooldowns[level-1],"native UI and economic clocks agree at anchor "+level);
            f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void first_native_wall_refuses_without_searching_for_safe_floor_behind_it(GameTestHelper h) {
        Fixture f=new Fixture(h); for(int y=5;y<=7;y++)for(int z=7;z<=8;z++)f.block(7,y,z,Blocks.STONE);
        f.refusePreview("first_surface_not_up"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void full_destination_body_ceiling_collision_is_free(GameTestHelper h) {
        Fixture f=new Fixture(h); f.block(10,6,10,Blocks.STONE); f.refusePreview("destination_block_collision"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_up_hit_with_incomplete_footprint_support_is_free(GameTestHelper h) {
        Fixture f=new Fixture(h); f.clearDestination(); f.block(10,4,10,Blocks.STONE); f.aim(f.absolute(10.02,5,10.5));
        f.refusePreview("destination_floor_unsupported"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void first_native_fluid_hit_is_free_and_has_no_floor_search(GameTestHelper h) {
        Fixture f=new Fixture(h); f.block(10,5,10,Blocks.WATER);
        f.refusePreview("first_surface_fluid"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void real_living_entity_occupancy_refuses_full_destination_body(GameTestHelper h) {
        Fixture f=new Fixture(h); f.mob(f.target); f.refusePreview("destination_entity_collision"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void source_requires_genuine_full_native_floor_before_preview(GameTestHelper h) {
        Fixture f=new Fixture(h); for(int x=4;x<=6;x++)for(int z=4;z<=6;z++)f.block(x,4,z,Blocks.AIR);
        f.refusePreview("source_floor_unsupported"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void nonzero_fall_distance_is_refused_and_never_becomes_air_rescue(GameTestHelper h) {
        Fixture f=new Fixture(h); f.p.fallDistance=6; f.refusePreview("source_airborne");
        h.assertTrue(f.p.fallDistance==6&&f.p.onGround(),"refusal leaves accumulated native fall and ground state"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void airborne_source_is_free_even_with_zero_fall_distance_and_visible_floor(GameTestHelper h) {
        Fixture f=new Fixture(h); f.p.setOnGround(false); f.p.setDeltaMovement(0,-.3,0); Vec3 motion=f.p.getDeltaMovement();
        f.refusePreview("source_airborne"); h.assertTrue(!f.p.onGround()&&f.p.getDeltaMovement().equals(motion),"no airborne movement or fall correction"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void unregistered_native_source_guard_refuses_unloaded_chunk_without_loading_it(GameTestHelper h) {
        Vec3 remote=new Vec3(25000000.5,90,25000000.5);ChunkPos chunk=new ChunkPos(BlockPos.containing(remote));
        var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SLUnloaded"),false);
        // Pinned Entity.setPosRaw explicitly loads the center chunk for an added entity.
        // This direct native-world guard fixture is deliberately unregistered; it makes
        // no authenticated-controller or live-player movement claim.
        var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
        p.connection=new ServerGamePacketListenerImpl(server,connection,p,cookie);
        try {
            h.assertTrue(!p.isAddedToLevel()&&h.getLevel().getEntity(p.getId())==null,"native no-load source fixture is unregistered");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"declared remote source chunk starts unloaded");
            p.setOnGround(true);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"ground preparation occurs before relocation and leaves target chunk absent");
            p.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"motion preparation leaves exact target chunk absent");
            p.setPos(remote);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"unregistered native relocation leaves exact target chunk absent");
            p.connection.resetPosition();
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"native connection staging leaves exact target chunk absent immediately before guard");
            CompoundTag persistent=p.getPersistentData().copy();Vec3 motion=p.getDeltaMovement();boolean grounded=p.onGround();
            h.assertTrue(SafeLandingProbeWorld.sourceRefusal(p).equals("unloaded_chunk"),"direct native source guard refuses exact absent chunk");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"guard never loads denied native chunk");
            h.assertTrue(p.getPersistentData().equals(persistent)&&p.position().equals(remote)&&p.getDeltaMovement().equals(motion)&&p.onGround()==grounded
                    &&SafeLandingProbe.receipt(p)==null&&!SafeLandingProbe.active(p),"direct native refusal mutates no resources, movement, ground or controller state");
            h.succeed();
        } finally {p.discard();channel.finishAndReleaseAll();}
    }

    @GameTest(template=TEMPLATE,batch="highpower_safe_landing_border",timeoutTicks=10)
    public static void complete_native_destination_border_envelope_is_checked(GameTestHelper h) {
        Fixture f=new Fixture(h); var border=h.getLevel().getWorldBorder(); double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
        try { border.setCenter(f.source.x,f.source.z); border.setSize(10.5); f.refusePreview("outside_world_border"); }
        finally { border.setCenter(x,z); border.setSize(size); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_ray_stops_at_twelve_metres_instead_of_finding_distant_floor(GameTestHelper h) {
        Fixture f=new Fixture(h); f.clearDestination(); f.floor(13,13); f.aim(f.absolute(14.5,5,14.5));
        f.refusePreview("no_visible_surface"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void world_destination_change_after_preview_refuses_without_payment(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"valid frozen native preview"); f.block(10,6,10,Blocks.STONE);
        f.refuseConfirm("destination_block_collision"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_cancel_is_before_cost_and_preserves_unrelated_callback_resource_edits(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); Snapshot before=f.snapshot();
        TeleportHook hook=new TeleportHook(f.p,event->{f.budget().putDouble("cp",f.cp()-1);f.p.getPersistentData().putString("academy:safe_landing_test_callback","retained");event.setCanceled(true);});f.hook(hook);
        h.assertFalse(f.press(2),"real EntityTeleportEvent cancels before own debit"); f.receiptRefused("event_cancelled");
        near(h,f.cp(),before.cp()-1,"callback's independent resource edit is neither rolled back nor double debited");
        h.assertTrue(f.budget().getLong("next_"+SafeLandingProbe.ID)==before.deadline()&&f.hp().equals(before.hp())
                &&f.shield().equals(before.shield())&&f.mastery()==before.mastery(),"cancellation adds no cooldown, HP/shield mutation or mastery");
        h.assertTrue(hook.calls==1&&f.p.position().equals(f.source)&&"retained".equals(f.p.getPersistentData().getString("academy:safe_landing_test_callback")),"one event and callback state survive refusal"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_redirect_is_refused_without_using_the_callback_destination(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); f.hook(new TeleportHook(f.p,event->event.setTargetX(event.getTargetX()+.1)));
        f.refuseConfirm("event_redirected"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void same_controller_event_reentry_cannot_pay_or_confirm_twice(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); double cp=f.cp(); boolean[] nested={true};
        TeleportHook hook=new TeleportHook(f.p,event->nested[0]=f.press(3));f.hook(hook);
        h.assertTrue(f.press(2),"outer valid confirmation commits"); h.assertFalse(nested[0],"same controller reentry refuses while processing");
        near(h,f.cp(),cp-2,"reentrant path still has one exact debit");
        h.assertTrue(hook.calls==1&&SafeLandingProbe.receipt(f.p).input()==2&&!SafeLandingProbe.active(f.p),"one event, one original receipt, exact cleanup"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_abort_of_captured_identity_ends_preview_before_cost(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); boolean[] aborted={false};
        f.hook(new TeleportHook(f.p,event->aborted[0]=SafeLandingProbe.abort(f.p,f.wire(3))));
        f.refuseConfirm("event_changed_state"); h.assertTrue(aborted[0],"explicit abort reaches exact processing context"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_source_mutation_is_retained_and_free_without_snapshot_rollback(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); Vec3 changed=f.source.add(.1,0,0);
        f.hook(new TeleportHook(f.p,event->{f.p.setPos(changed);f.p.getPersistentData().putInt("academy:safe_landing_test_edit",7);}));
        f.refuseConfirm("event_changed_state"); h.assertTrue(f.p.position().equals(changed)&&f.p.getPersistentData().getInt("academy:safe_landing_test_edit")==7,"callback edits remain without controller rollback"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_pose_mutation_refuses_before_cost_and_keeps_callback_pose(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); f.hook(new TeleportHook(f.p,event->f.p.setPose(Pose.CROUCHING)));
        f.refuseConfirm("event_changed_state"); h.assertTrue(f.p.getPose()==Pose.CROUCHING,"callback pose was not overwritten"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void event_category_change_revokes_old_tp_identity_before_cost(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); Snapshot before=f.snapshot();
        f.hook(new TeleportHook(f.p,event->AbilityStorage.get(f.p).changeCategoryClassic(HighPowerCategories.MELTDOWNER)));
        h.assertFalse(f.press(2),"callback category mutation cannot commit old TP context");f.receiptRefused("event_changed_state");
        near(h,f.cp(),before.cp(),"category callback adds no TP debit");h.assertTrue(f.p.position().equals(f.source)&&f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"category callback adds no movement, HP or shield spend");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_safe_landing_world",timeoutTicks=10)
    public static void native_event_dimension_change_is_retained_and_cannot_pay_old_world_context(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.press(1),"native preview"); Snapshot before=f.snapshot();
        var other=f.p.server.getLevel(Level.NETHER); h.assertTrue(other!=null,"native Nether exists");
        f.hook(new TeleportHook(f.p,event->{if(f.p.serverLevel()==h.getLevel())f.p.teleportTo(other,f.source.x,f.source.y,f.source.z,Set.of(),f.p.getYRot(),f.p.getXRot());}));
        h.assertFalse(f.press(2),"native callback world change revokes captured same-world context");f.receiptRefused("event_changed_state");
        near(h,f.cp(),before.cp(),"callback dimension transition adds no TP debit");
        h.assertTrue(f.p.serverLevel()==other&&f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"controller never rolls back real native world transition or edits pools");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void changed_current_aim_keeps_frozen_destination_and_preserves_current_rotation(GameTestHelper h) {
        Fixture f=new Fixture(h); var frozen=f.candidate();h.assertTrue(f.press(1),"native frozen preview");
        f.p.setYRot(127);f.p.setXRot(-31);h.assertTrue(f.press(2),"new aim does not retarget the captured safe landing");
        h.assertTrue(f.p.position().equals(frozen.destination()),"exact first destination stays frozen");near(h,f.p.getYRot(),127,"current yaw preserved");near(h,f.p.getXRot(),-31,"current pitch preserved");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void changed_pose_after_preview_refuses_the_frozen_body(GameTestHelper h) {
        Fixture f=new Fixture(h);h.assertTrue(f.press(1),"native preview");f.p.setPose(Pose.CROUCHING);f.refuseConfirm("body_changed");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void source_drift_above_quarter_metre_is_free_and_below_it_keeps_exact_target(GameTestHelper h) {
        Fixture f=new Fixture(h);var frozen=f.candidate();h.assertTrue(f.press(1),"native preview");f.p.setPos(f.source.add(.2,0,0));
        h.assertTrue(f.press(2)&&f.p.position().equals(frozen.destination()),"bounded source drift keeps the frozen target");f.close();
        Fixture g=new Fixture(h);h.assertTrue(g.press(1),"second native preview");g.p.setPos(g.source.add(.25001,0,0));g.refuseConfirm("source_moved");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=150)
    public static void genuine_server_ticks_expire_preview_and_late_old_confirmation_cannot_spend(GameTestHelper h) {
        Fixture f=new Fixture(h);
        // Native travel with an initial ZERO movement clears ground before applying gravity.
        // Prepare actual floor collision and ordinary gravity residue once; all subsequent
        // grounding and movement come from the native ticks, with no repeated flag edits.
        f.p.move(MoverType.SELF,new Vec3(0,-.0784,0));f.p.setDeltaMovement(0,-.0784,0);
        h.assertTrue(f.p.onGround()&&f.p.position().equals(f.source)&&SafeLandingProbeWorld.sourceRefusal(f.p).isEmpty(),"actual native downward collision prepares stable grounded source");
        int issued=f.p.server.getTickCount();h.assertTrue(f.press(1),"native preview starts");
        h.runAfterDelay(SafeLandingProbeRules.PREVIEW_TICKS,()->{
            h.assertTrue(f.p.server.getTickCount()-issued==SafeLandingProbeRules.PREVIEW_TICKS,"the full preview duration elapsed on the genuine server clock");
            h.assertTrue(f.p.onGround()&&f.p.position().equals(f.source),"real ticks preserve native floor contact and exact source without forced flags");
            SafeLandingProbe.tick(f.p);h.assertFalse(SafeLandingProbe.active(f.p),"preview expires at its exact boundary");f.receiptRefused("expired");
            Snapshot before=f.snapshot();long retired=SafeLandingProbe.receipt(f.p).token();h.assertFalse(f.press(1),"late original input stays retired");f.same(before,"late preview input");
            f.drain();h.assertTrue(f.press(2)&&SafeLandingProbe.active(f.p),"late fresh second sequence starts a free new preview instead of confirming the expired one");f.same(before,"late new sequence");
            var visuals=f.visuals();h.assertTrue(visuals.size()==1&&visuals.getFirst().token()>retired&&visuals.getFirst().remainingTicks()==SafeLandingProbeRules.PREVIEW_TICKS&&visuals.getFirst().phase()==SafeLandingProbe.PREVIEW,"new token and full lifetime distinguish the free new preview");h.assertTrue(f.p.position().equals(f.source),"late second sequence never commits retired destination");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void level_five_movement_preview_and_mastery_fences_expire_only_on_genuine_ticks(GameTestHelper h) {
        Fixture f=new Fixture(h,5,5);h.assertTrue(f.press(1)&&f.press(2),"first native LV5 cast");double initialXp=f.mastery();int castAt=f.p.server.getTickCount();
        f.aim(f.source);Snapshot immediate=f.snapshot();h.assertFalse(f.press(3),"same-tick recast cannot bypass physical fence");f.same(immediate,"same-tick fence");
        h.runAfterDelay(4,()->{h.assertTrue(f.p.server.getTickCount()-castAt==4,"four genuine ticks");h.assertTrue(f.fences().getInt("movement")==4&&f.fences().getInt("cooldown")==0&&f.fences().getInt("preview")==0,"one-tick economic cooldown never replaces eight-tick motion fence");Snapshot before=f.snapshot();h.assertFalse(f.press(4),"motion fence still rejects at4");f.same(before,"four-tick fence");});
        h.runAfterDelay(8,()->{h.assertTrue(f.p.server.getTickCount()-castAt==8,"eight genuine ticks");h.assertTrue(f.fences().getInt("movement")==0&&f.fences().getInt("preview")==0,"movement and second preview fences naturally expire");double cp=f.cp();h.assertTrue(f.press(5)&&f.press(6),"second native move is eligible at8");near(h,f.cp(),cp-.05,"second move pays once");near(h,f.mastery(),initialXp,"twenty-tick mastery fence blocks repeat reward");f.aim(f.target);});
        h.runAfterDelay(20,()->{h.assertTrue(f.p.server.getTickCount()-castAt==20,"twenty genuine ticks");h.assertTrue(f.press(7)&&f.press(8),"third native move after all physical fences");near(h,f.mastery(),initialXp+.002,"productive actual movement rewards once after20");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_reload_and_category_roundtrip_keep_depleted_cp_and_remaining_fences(GameTestHelper h) {
        Fixture f=new Fixture(h,5,5);h.assertTrue(f.press(1)&&f.press(2),"native paid cast");CompoundTag fences=f.fences();double cp=f.cp();
        var state=AbilityStorage.get(f.p);state.changeCategoryClassic(HighPowerCategories.MELTDOWNER);state.changeCategoryClassic(HighPowerCategories.TELEPORTER);state.learn(SafeLandingProbe.ID);state.activated=true;state.overloadFine=true;state.presets.edit(0,0,SafeLandingProbe.ID,state::learned);
        h.assertTrue(f.fences().equals(fences),"category switches/relearning keep physical remaining integers");near(h,f.cp(),cp,"category roundtrip cannot refill shared CP");
        AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());SafeLandingProbe.remove(f.p);f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.p.setOnGround(true);f.p.setDeltaMovement(Vec3.ZERO);f.p.connection.resetPosition();f.epoch();
        h.assertTrue(f.fences().equals(fences),"actual player NBT reload retains all four remaining fences");near(h,f.cp(),cp,"actual player reload retains paid CP");
        h.assertFalse(f.press(3),"fresh authenticated epoch after reload cannot clear physical fence");h.assertTrue(f.fences().equals(fences),"reload refusal does not rewrite retained fence");near(h,f.cp(),cp,"reload retry never debits again");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void actual_native_clone_event_copies_remaining_fences_and_revokes_original_preview(GameTestHelper h) {
        Fixture f=new Fixture(h,5,5), replacement=new Fixture(h,5,5,16);h.assertTrue(f.press(1)&&f.press(2),"original paid native move");CompoundTag fences=f.fences();
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.p,f.p,false));
        h.assertTrue(replacement.fences().equals(fences)&&!SafeLandingProbe.active(f.p),"actual clone event keeps physical fences and closes original context");
        // The replacement has its own declared enrollment budget. This case establishes
        // NEW313 fence retention, not an additional shared-CP cloning/refill policy.
        Snapshot before=replacement.snapshot();Vec3 position=replacement.p.position();replacement.epoch();
        h.assertFalse(replacement.press(3),"clone cannot reopen before physical fences expire");replacement.same(before,"clone retry cannot debit or reward");
        h.assertTrue(replacement.p.position().equals(position)&&replacement.fences().equals(fences),"refused clone cast moves nothing and retains copied fences");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void malformed_remaining_fence_storage_fails_closed_without_refill_or_payment(GameTestHelper h) {
        List<Consumer<CompoundTag>> corruptions=List.of(t->t.putInt("version",2),t->t.putString("movement","8"),t->t.putInt("preview",5),t->t.putInt("movement",9),t->t.putInt("cooldown",41),t->t.putInt("mastery",21),t->t.putInt("cooldown",-1));
        for(var corrupt:corruptions){Fixture f=new Fixture(h);CompoundTag tag=new CompoundTag();tag.putInt("version",1);for(String key:List.of("preview","movement","cooldown","mastery"))tag.putInt(key,0);corrupt.accept(tag);f.p.getPersistentData().put(FENCES,tag);Snapshot before=f.snapshot();h.assertFalse(f.press(1),"malformed native fence storage is rejected");f.receiptRefused("storage_invalid");f.same(before,"malformed storage");h.assertTrue(f.fences().equals(tag),"refusal never normalizes corrupt storage into spend permission");f.close();}
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void crowded_native_destination_scan_is_bounded_and_free(GameTestHelper h) {
        Fixture f=new Fixture(h);for(int i=0;i<65;i++)f.mob(f.target);f.refusePreview("entity_scan_budget");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void genuine_residency_expires_login_protection_and_native_fall_damage_stays_ordinary(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,3,5,16);int started=f.p.server.getTickCount();
        h.runAfterDelay(61,()->{
            h.assertTrue(f.p.server.getTickCount()-started==61,"sixty-one genuine native server ticks elapsed");
            h.assertTrue(spawnProtection(f.p)<=0&&spawnProtection(control.p)<=0,"resident native ServerPlayer.tick naturally expires login protection");
            f.place();control.place();h.assertTrue(f.press(1)&&f.press(2),"grounded native move after login-protection expiry");
            BigDecimal hp=new BigDecimal(f.hp()),controlHp=new BigDecimal(control.hp());
            f.p.setOnGround(false);control.p.setOnGround(false);f.p.fallDistance=8;control.p.fallDistance=8;
            h.assertTrue(f.p.causeFallDamage(8,1,f.p.damageSources().fall())&&control.p.causeFallDamage(8,1,control.p.damageSources().fall()),"both actual native fall paths accept the same measured fall");
            BigDecimal loss=hp.subtract(new BigDecimal(f.hp())),controlLoss=controlHp.subtract(new BigDecimal(control.hp()));
            h.assertTrue(loss.signum()>0&&loss.compareTo(controlLoss)==0,"paid move grants no fall immunity or HP advantage against matched native control");
            h.assertFalse(f.p.isNoGravity()||f.p.getAbilities().flying,"movement leaves native gravity and flight settings");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void native_destination_hazards_changed_after_preview_refuse_for_entire_body_and_support(GameTestHelper h) {
        for(var hazard:List.of(Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE,Blocks.FIRE,Blocks.SOUL_FIRE,
                Blocks.CACTUS,Blocks.SWEET_BERRY_BUSH,Blocks.WITHER_ROSE,Blocks.POWDER_SNOW,Blocks.COBWEB,
                Blocks.NETHER_PORTAL,Blocks.END_PORTAL,Blocks.END_GATEWAY)) {
            Fixture f=new Fixture(h);h.assertTrue(f.press(1),"native safe preview before destination hazard");
            int y=6;
            // BaseFireBlock.onPlace immediately removes unsupported fire. These native
            // supports retain the intended blocks without changing the captured top face.
            if(hazard==Blocks.MAGMA_BLOCK)y=4;
            else if(hazard==Blocks.FIRE)y=5;
            else if(hazard==Blocks.SOUL_FIRE){f.block(10,4,10,Blocks.SOUL_SOIL);y=5;}
            else if(hazard==Blocks.CACTUS){f.block(10,4,10,Blocks.SAND);y=5;}
            else if(hazard==Blocks.SWEET_BERRY_BUSH||hazard==Blocks.WITHER_ROSE){f.block(10,4,10,Blocks.DIRT);y=5;}
            f.block(10,y,10,hazard);
            BlockPos actual=h.absolutePos(new BlockPos(10,y,10));
            h.assertTrue(h.getLevel().getBlockState(actual).is(hazard),"intended native hazard remains before confirm: "+hazard);
            boolean stableGate=hazard==Blocks.MAGMA_BLOCK||hazard==Blocks.FIRE||hazard==Blocks.SOUL_FIRE;
            Set<String> reasons=stableGate?Set.of("destination_hazard")
                    :Set.of("destination_hazard","first_surface_not_up","surface_changed","no_visible_surface");
            // Changed first collision geometry may refuse before envelope hazard scanning.
            // Only those explicit native sight-line refusals or the hazard gate are accepted.
            f.refuseConfirm(reasons,"retained native hazard "+hazard);f.close();
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void genuine_grounded_burning_source_can_escape_without_clearing_fire_or_spending_health(GameTestHelper h) {
        Fixture f=new Fixture(h);f.block(5,4,5,Blocks.MAGMA_BLOCK);f.p.setRemainingFireTicks(60);Snapshot before=f.snapshot();
        h.assertTrue(SafeLandingProbeWorld.sourceRefusal(f.p).isEmpty(),"actual grounded source on full native magma remains eligible");
        h.assertTrue(f.press(1)&&f.press(2),"attached burning permits a normal safe-floor escape");
        h.assertTrue(f.p.getRemainingFireTicks()==60&&f.p.isOnFire(),"native move preserves existing attached burning");
        h.assertTrue(f.hp().equals(before.hp())&&f.shield().equals(before.shield()),"escape neither heals nor spends exact HP/shield");near(h,f.cp(),before.cp()-2,"burning escape pays normal one-cast CP");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void throwing_after_success_mastery_callback_keeps_one_paid_moved_receipt_and_one_terminal(GameTestHelper h) {
        Fixture f=new Fixture(h);var frozen=f.candidate();h.assertTrue(frozen.sourcePosition().distanceTo(frozen.destination())>=3&&f.press(1),"productive genuine native preview");
        int[] callbacks={0};f.hook(new Object(){@SubscribeEvent public void xp(cn.academy.port.api.SkillExpAddedEvent event){if(event.getEntity()==f.p&&event.skill.equals(SafeLandingProbe.ID)){callbacks[0]++;throw new IllegalStateException("declared NEW313 post-success native callback failure");}}});
        double cp=f.cp();f.drain();h.assertTrue(f.press(2),"already confirmed movement stays successful after optional award callback fails");
        var receipt=SafeLandingProbe.receipt(f.p);h.assertTrue(callbacks[0]==1&&receipt!=null&&receipt.paid()&&receipt.moved()&&receipt.reason().isEmpty()
                &&f.p.position().equals(frozen.destination())&&!SafeLandingProbe.active(f.p),"one historical paid/moved success survives callback failure");
        var packets=f.drain();long nativeMoves=packets.stream().filter(packet->packet instanceof ClientboundPlayerPositionPacket).count();
        var visuals=packets.stream().filter(packet->packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof SafeLandingProbe.Visual)
                .map(packet->(SafeLandingProbe.Visual)((net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)packet).payload()).toList();
        h.assertTrue(nativeMoves==1&&visuals.size()==1&&visuals.getFirst().phase()==SafeLandingProbe.SUCCESS,"actual outgoing stream contains one native move and one SUCCESS without contradictory terminal");
        near(h,f.cp(),cp-2,"throwing callback does not refund or duplicate charge");h.assertTrue(f.fences().getInt("mastery")==20&&f.fences().getInt("movement")==8,"possibly applied award and confirmed movement retain their fences");
        Snapshot after=f.snapshot();h.assertFalse(f.press(2),"failed optional bookkeeping never replays original confirmation");SafeLandingProbe.tick(f.p);f.same(after,"post-success duplicate and idle tick");
        h.assertTrue(callbacks[0]==1&&SafeLandingProbe.receipt(f.p).equals(receipt),"optional award is never retried and success receipt stays historical");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void finite_corrupt_source_coordinate_refuses_before_fences_context_or_visual_allocation(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();Vec3 invalid=new Vec3(f.source.x,5000,f.source.z);f.drain();
        try {
            f.p.setPos(invalid);
            h.assertFalse(f.press(1),"out-of-contract finite native Y refuses before context allocation");
            h.assertTrue(!SafeLandingProbe.active(f.p)&&SafeLandingProbe.receipt(f.p)==null&&!f.p.getPersistentData().contains(FENCES),"early corrupt-coordinate refusal allocates no controller, receipt or physical fences");
            f.same(before,"early corrupt-coordinate refusal");h.assertTrue(f.p.position().equals(invalid),"refusal itself performs no corrective movement");
            h.assertTrue(f.visuals().isEmpty(),"invalid coordinates are never serialized into a visual payload");
        } finally {
            // Restore the declared safe arena before any native tick can observe corrupt Y.
            f.place();
        }
        h.succeed();
    }

    private record Snapshot(double cp,long deadline,int cooldown,double mastery,String hp,String shield,double overload) {}
    private static void near(GameTestHelper h,double actual,double wanted,String why){h.assertTrue(Math.abs(actual-wanted)<1e-7,why+": "+actual+" != "+wanted);}
    private static Object read(Object owner,String name){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}}
    private static Vec3 awaiting(ServerPlayer p){return (Vec3)read(p.connection,"awaitingPositionFromClient");}
    private static int spawnProtection(ServerPlayer p){return (Integer)read(p,"spawnInvulnerableTime");}
    public static final class TeleportHook {
        final ServerPlayer owner;final Consumer<EntityTeleportEvent> callback;int calls;
        TeleportHook(ServerPlayer owner,Consumer<EntityTeleportEvent> callback){this.owner=owner;this.callback=callback;}
        @SubscribeEvent public void teleport(EntityTeleportEvent event){if(event.getEntity()==owner){calls++;callback.accept(event);}}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Vec3 source,target;final int floorY,offsetX;
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this(h,3,5);}
        Fixture(GameTestHelper h,int level,int feetY){this(h,level,feetY,0);}
        Fixture(GameTestHelper h,int level,int feetY,int offsetX){
            this.h=h;this.offsetX=offsetX;floorY=feetY-1;source=h.absoluteVec(new Vec3(5.5+offsetX,feetY,5.5));target=h.absoluteVec(new Vec3(10.5+offsetX,feetY,10.5));
            h.assertTrue(h.getBounds().contains(source)&&h.getBounds().contains(target.add(0,2,0)),"source and destination stay inside actual supplied native template");
            // Exact bounded residency starts at setup, so native ServerPlayer.tick runs
            // during the entire61tick login interval; doTick alone is not that evidence.
            AABB arena=new AABB(source.add(-3,-1,-3),target.add(3,3,3));int minX=((int)Math.floor(arena.minX))>>4,maxX=((int)Math.floor(arena.maxX))>>4,minZ=((int)Math.floor(arena.minZ))>>4,maxZ=((int)Math.floor(arena.maxZ))>>4;
            h.assertTrue((maxX-minX+1)*(maxZ-minZ+1)<=9,"at most nine native fixture chunks");
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){ChunkPos chunk=new ChunkPos(x,z);held.add(chunk);h.getLevel().getChunkSource().addRegionTicket(FIXTURE_TICKET,chunk,2,ticket);h.getLevel().getChunk(x,z);}
            floor(4,4);floor(9,9);
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SL"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            // Acknowledge the actual login packet before moving into the test arena.
            for(var packet:positions())p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.getAbilities().invulnerable=false;place();
            h.assertTrue(HighPowerCombat.enroll(p,level),"declared first native enrollment");var state=AbilityStorage.get(p);state.selectCategory(HighPowerCategories.TELEPORTER);state.setLevel(level);state.learn(SafeLandingProbe.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,SafeLandingProbe.ID,state::learned),"real learned native NEW313 slot");HighPowerResources.initializeBudget(p);epoch();drain();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
        }
        Vec3 absolute(double x,double y,double z){return h.absoluteVec(new Vec3(x+offsetX,y+floorY-4,z));}
        void place(){p.moveTo(source.x,source.y,source.z,0,0);p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.setOnGround(true);p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();aim(target);}
        void aim(Vec3 at){Vec3 delta=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));}
        void floor(int minX,int minZ){for(int x=minX;x<=minX+2;x++)for(int z=minZ;z<=minZ+2;z++)block(x,4,z,Blocks.STONE);}
        void clearDestination(){for(int x=9;x<=11;x++)for(int z=9;z<=11;z++)block(x,4,z,Blocks.AIR);}
        void block(int x,int y,int z,net.minecraft.world.level.block.Block block){BlockPos pos=h.absolutePos(new BlockPos(x+offsetX,y+floorY-4,z));h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)),"fixture block remains inside actual supplied template");changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());}
        void mob(Vec3 at){Zombie mob=EntityType.HUSK.create(h.getLevel());mob.setNoAi(true);mob.setNoGravity(true);mob.setSilent(true);mob.setPos(at);h.getLevel().addFreshEntity(mob);entities.add(mob);}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        boolean press(long input){return SafeLandingProbe.press(p,wire(input));}
        SafeLandingProbeWorld.Candidate candidate(){var assessment=SafeLandingProbeWorld.preview(p);h.assertTrue(assessment.valid(),"actual source and native full-footprint landing are valid: "+assessment.reason());return assessment.candidate();}
        CompoundTag budget(){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);}
        CompoundTag fences(){return p.getPersistentData().getCompound(FENCES).copy();}
        double cp(){return HighPowerResources.cp(p);}
        double mastery(){return AbilityStorage.get(p).exp(SafeLandingProbe.ID);}
        String hp(){return HighPowerCombat.healthText(p);}
        String shield(){return HighPowerCombat.shieldText(p);}
        Snapshot snapshot(){var s=AbilityStorage.get(p);return new Snapshot(cp(),budget().getLong("next_"+SafeLandingProbe.ID),s.cooldowns.getOrDefault(SafeLandingProbe.ID,0),mastery(),hp(),shield(),s.overload);}
        void same(Snapshot before,String why){h.assertTrue(snapshot().equals(before),why+" preserves CP, deadline, cooldown, mastery, HP, shield and overload");}
        void receiptRefused(String reason){var receipt=SafeLandingProbe.receipt(p);h.assertTrue(receipt!=null&&!receipt.paid()&&!receipt.moved()&&receipt.reason().equals(reason),"free public refusal receipt: expected "+reason+", actual "+receipt);h.assertFalse(SafeLandingProbe.active(p),"refusal cleans exact controller");}
        void refusePreview(String reason){Snapshot before=snapshot();Vec3 position=p.position();h.assertFalse(press(1),"native preview refuses "+reason);receiptRefused(reason);same(before,"preview refusal");h.assertTrue(p.position().equals(position),"preview refusal does not move source");}
        void refuseConfirm(String reason){Snapshot before=snapshot();h.assertFalse(press(2),"native confirm refuses "+reason);receiptRefused(reason);same(before,"confirm refusal");}
        void refuseConfirm(Set<String> reasons,String why){Snapshot before=snapshot();Vec3 position=p.position();h.assertFalse(press(2),"native confirmation refuses "+why);var receipt=SafeLandingProbe.receipt(p);h.assertTrue(receipt!=null&&!receipt.paid()&&!receipt.moved()&&reasons.contains(receipt.reason()),"enumerated free native geometry/hazard refusal for "+why+": "+receipt);h.assertFalse(SafeLandingProbe.active(p),"hazard refusal cleans exact controller");same(before,"hazard confirm refusal");h.assertTrue(p.position().equals(position),"hazard refusal never moves the source");}
        List<Object> drain(){List<Object> result=new ArrayList<>();channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){result.add(packet);ReferenceCountUtil.release(packet);}return result;}
        List<SafeLandingProbe.Visual> visuals(){List<SafeLandingProbe.Visual> result=new ArrayList<>();for(Object packet:drain())if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof SafeLandingProbe.Visual visual)result.add(visual);return result;}
        List<ClientboundPlayerPositionPacket> positions(){List<ClientboundPlayerPositionPacket> result=new ArrayList<>();for(Object packet:drain())if(packet instanceof ClientboundPlayerPositionPacket position)result.add(position);return result;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);SafeLandingProbe.remove(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(FIXTURE_TICKET,chunk,2,ticket));held.clear();channel.finishAndReleaseAll();}
    }
}

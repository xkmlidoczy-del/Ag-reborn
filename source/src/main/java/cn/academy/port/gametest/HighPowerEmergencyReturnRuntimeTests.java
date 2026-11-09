package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.EmergencyReturnRules;
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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW342 native acceptance. Enrollment, learned skills and bounded floor cells are
 * declared fixtures. Every damage case uses a connected resident player and at
 * least 61 genuine native ticks to expire login protection. The strict post-connection tests retain their HIGHEST native player pump.
 * Separate connection-order tests drive the genuine listener once per Level.Post,
 * then observe next Pre before another native connection cycle. No post-hit ground or velocity
 * repair, artificial damage Post, removed pump, or fabricated knockback is used to
 * make a successful automatic return. This suite makes no LocalPlayer/UI claim.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerEmergencyReturnRuntimeTests {
    private static final String TEMPLATE="highpower_spatial_relay_empty", BATCH="highpower_emergency_return";
    private static final String OWN="academy:emergency_return_fences", SHARED="academy:safe_landing_probe_fences";
    private static final TicketType<UUID> TICKET=TicketType.create("academy_emergency_return_fixture",UUID::compareTo);
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private HighPowerEmergencyReturnRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void authenticated_short_release_stores_only_current_feet_for_free(GameTestHelper h) {
        Fixture f=new Fixture(h);Snapshot before=f.snapshot();
        h.assertTrue(f.press(1),"authenticated down edge begins one held input");
        h.assertFalse(EmergencyReturn.marked(f.p),"down edge alone does not store feet");
        h.assertTrue(f.release(1),"short authenticated release records current feet");
        var r=EmergencyReturn.receipt(f.p);
        h.assertTrue(r!=null&&r.input()==1&&r.token()>0&&!r.paid()&&!r.moved()&&!r.automatic()
                &&r.source().equals(f.anchor)&&r.destination().equals(f.anchor),"free exact current-feet mark receipt");
        h.assertTrue(EmergencyReturn.marked(f.p)&&!EmergencyReturn.active(f.p)&&EmergencyReturn.remainingTicks(f.p)==1200,"released mark is passive with full1200 genuine-tick lifetime");
        h.assertFalse(f.release(1)||f.press(1),"duplicate released input cannot arm or return again");
        f.same(before,"free mark and duplicate");h.assertTrue(f.positions().isEmpty(),"free mark sends no native movement");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=16)
    public static void six_real_tick_hold_clears_freely_after_airborne_or_cooldown_changes(GameTestHelper h) {
        Fixture f=new Fixture(h);f.mark();int issued=f.p.server.getTickCount();
        h.assertTrue(f.press(2),"fresh held input starts while marked");
        for(int i=0;i<1500;i++)EmergencyReturn.tick(f.p);
        h.assertTrue(EmergencyReturn.marked(f.p),"direct calls cannot replace six genuine held ticks");
        h.runAfterDelay(6,()->{
            h.assertTrue(f.p.server.getTickCount()-issued==6,"exact six genuine boundaries");
            f.p.setOnGround(false);f.p.fallDistance=9;f.p.setDeltaMovement(0,-.4,0);Snapshot before=f.snapshot();
            Vec3 at=f.p.position(),motion=f.p.getDeltaMovement();
            h.assertTrue(f.release(2),"six-tick release clears before unsafe movement checks");
            h.assertFalse(EmergencyReturn.marked(f.p)||EmergencyReturn.active(f.p),"hold clear leaves no mark or held context");
            f.same(before,"free long release");h.assertTrue(f.p.position().equals(at)&&f.p.getDeltaMovement().equals(motion)&&f.p.fallDistance==9,"clear never rescues the airborne owner");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void manual_return_uses_native_absolute_packet_ack_and_preserves_damage_state(GameTestHelper h) {
        Fixture f=new Fixture(h);f.mark();h.runAfterDelay(8,()->{f.place(f.source);f.p.setYRot(37);f.p.setXRot(-31);
        f.p.setDeltaMovement(.13,-.0784,-.09);f.p.hurtTime=7;f.p.invulnerableTime=14;
        f.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100,0));Snapshot before=f.snapshot();BodyState body=BodyState.of(f.p);f.drain();
        h.assertTrue(f.tap(2),"second short release commits native manual return");f.success(false);
        near(h,f.cp(),before.cp()-.5,"one LV4 manual CP debit");f.unchangedPools(before,"manual return");
        h.assertTrue(BodyState.of(f.p).equals(body),"manual return preserves motion, pose, fall, ground, effects and native hurt timers");
        var packets=f.positions();h.assertTrue(packets.size()==1,"one native outbound position packet");var packet=packets.getFirst();
        Vec3 xyz=new Vec3(packet.getX(),packet.getY(),packet.getZ());
        h.assertTrue(xyz.equals(f.anchor)&&xyz.equals(f.p.position())&&packet.getRelativeArguments().equals(RelativeMovement.ROTATION),"exact absolute XYZ with only rotation relative");
        near(h,packet.getYRot(),0,"native yaw delta");near(h,packet.getXRot(),0,"native pitch delta");
        h.assertTrue(xyz.equals(awaiting(f.p)),"native listener awaits exact anchor");Snapshot paid=f.snapshot();
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()+1));h.assertTrue(xyz.equals(awaiting(f.p)),"wrong native acknowledgment is inert");
        f.p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
        h.assertTrue(awaiting(f.p)==null&&f.p.position().equals(f.anchor),"matching acknowledgment never adds source twice");
        EmergencyReturn.tick(f.p);h.assertFalse(f.tap(2),"original input cannot replay");f.same(paid,"ack and duplicate");h.assertTrue(f.positions().isEmpty(),"no duplicate native move");h.succeed();});
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void native_forty_metre_boundary_is_exact_and_first_outside_point_refuses(GameTestHelper h) { range(h,false); }
    private static void range(GameTestHelper h,boolean outside){
        Fixture f=new Fixture(h);f.mark();h.runAfterDelay(8,()->{
            Vec3 end=f.anchor.add(40,0,0);if(outside)end=new Vec3(Math.nextUp(end.x),end.y,end.z);f.place(end);Snapshot before=f.snapshot();
            boolean moved=f.tap(2);
            if(!outside){h.assertTrue(moved,"native exact40m manual corridor succeeds");f.success(false);near(h,f.cp(),before.cp()-.5,"exact40m debit");}
            else{h.assertFalse(moved,"first representable native point beyond40m refuses");f.freeReceipt();f.same(before,"outside40m");h.assertTrue(f.p.position().equals(end),"range refusal never shortens to40m");}
            f.close();if(outside)h.succeed();else range(h,true);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void full_body_wall_thin_pane_and_missing_support_refuse_manual_return_for_free(GameTestHelper h) { blockedManual(h,0); }
    private static void blockedManual(GameTestHelper h,int mode){
        if(mode==4){h.succeed();return;}Fixture f=new Fixture(h);f.mark();h.runAfterDelay(8,()->{f.place(f.source);
            if(mode==0){f.block(6,5,8,Blocks.STONE);f.block(6,6,8,Blocks.STONE);}
            else if(mode==1)f.block(6,6,8,Blocks.GLASS_PANE);
            else if(mode==2)f.block(4,4,8,Blocks.AIR);
            else f.block(8,4,8,Blocks.AIR);
            Snapshot before=f.snapshot();h.assertFalse(f.tap(2),"native wall/pane/source/destination support gate "+mode);f.freeReceipt();f.same(before,"blocked manual geometry");
            h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty(),"blocked manual has no corrective movement");f.close();blockedManual(h,mode+1);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void actual_monster_melee_returns_at_next_pre_after_native_damage_and_knockback(GameTestHelper h) { automaticSuccess(h,0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void actual_player_hurt_with_reviewed_monster_source_keeps_native_post_order(GameTestHelper h) { automaticSuccess(h,1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void actual_owned_plain_arrow_flight_returns_only_after_native_impact(GameTestHelper h) { automaticSuccess(h,2); }

    @GameTest(template=TEMPLATE,batch="highpower_category_emergency_return",timeoutTicks=95)
    public static void real_base_shield_loss_can_fund_automatic_escape_without_refund(GameTestHelper h) {
        Fixture f=new Fixture(h);
        cn.academy.port.highpower.EsperLegacyBypass.force(true);
        h.testInfo.addListener(new GameTestListener(){boolean ended;private void end(){if(!ended){ended=true;cn.academy.port.highpower.EsperLegacyBypass.force(false);}}
            public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){end();}public void testFailed(GameTestInfo i,GameTestRunner r){end();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){end();}});
        f.ready(()->{
            f.armAtSource();h.assertTrue(cn.academy.port.highpower.EsperShields.holding(f.p),"actual base category shield is enabled");
            String hpBefore=f.hp();BigDecimal shieldBefore=new BigDecimal(f.shield());double cpBefore=f.cp();
            f.shooter.setPos(f.source.add(0,0,1.5));h.assertTrue(f.shooter.doHurtTarget(f.p),"real hostile native melee completes");
            h.assertTrue(f.hp().equals(hpBefore),"base shield protects HP from the triggering hit");
            BigDecimal afterHit=new BigDecimal(f.shield());h.assertTrue(afterHit.compareTo(shieldBefore)<0,"native hit actually debits shield before escape");
            f.nextPre(()->{f.success(true);h.assertTrue(f.hp().equals(hpBefore),"automatic escape neither heals nor damages HP");
                eq(h,new BigDecimal(f.shield()),afterHit.subtract(f.autoCost()),"real damage remains spent plus exactly ten percent capacity evacuation budget");near(h,f.cp(),cpBefore,"automatic shield-funded return adds no CP fee");});
            f.postAfter(2,()->h.assertTrue(f.p.position().equals(f.anchor),"same-hit category random phase cannot move a successful escape off its saved ground"));f.finishAfter(3);
        });
    }

    private static void automaticSuccess(GameTestHelper h,int kind) {
        Fixture f=new Fixture(h);f.ready(()->{
            f.armAtSource();Snapshot before=f.snapshot();DamageWatch watch=new DamageWatch(f.p);f.hook(watch);
            TeleportHook teleport=new TeleportHook(f.p,event->{});f.hook(teleport);int hitTick=f.p.server.getTickCount();
            if(kind==0){f.shooter.setPos(f.source.add(0,0,1.5));h.assertTrue(f.shooter.distanceTo(f.p)<2&&f.shooter.doHurtTarget(f.p),"real Monster.doHurtTarget accepts native melee at actual close range");}
            else if(kind==1)h.assertTrue(f.hit(3),"real ServerPlayer.hurt accepts reviewed mob source");
            else{Arrow arrow=f.arrow(f.source.add(0,1,3),new Vec3(0,0,-4));arrow.tick();h.assertTrue(arrow.isRemoved()&&watch.source!=null&&watch.source.getDirectEntity()==arrow,"one genuine Arrow.tick hits native owner body");}
            h.assertTrue(watch.posts==1&&watch.damage>0&&f.health().compareTo(new BigDecimal(before.hp()))<0&&f.p.isAlive(),"one positive nonlethal native Post has already debited exact HP");
            h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty()&&teleport.calls==0,"hurt completion has not teleported inside damage callbacks");
            Vec3 sealed=f.p.getDeltaMovement();h.assertTrue(sealed.y>0&&sealed.lengthSqr()>0,"actual native hit created upward knockback before seal");
            String postHp=f.hp(),postShield=f.shield();BodyState body=BodyState.of(f.p);double cp=f.cp();
            f.nextPre(()->{
                f.success(true);h.assertTrue(f.p.server.getTickCount()==hitTick+1&&teleport.calls==1,"exactly one native teleport at next genuine ServerTick.Pre");
                h.assertTrue(f.hp().equals(postHp)&&BodyState.of(f.p).equals(body),"automatic return preserves debited HP and all sealed native damage/motion state");
                near(h,f.cp(),cp,"automatic return spends no CP");eq(h,new BigDecimal(f.shield()),new BigDecimal(postShield).subtract(f.autoCost()),"automatic return spends exactly10% certified baseS once");
                h.assertTrue(EmergencyReturn.automaticRemaining(f.p)==1200&&f.shared().getInt("movement")==8,"automatic and shared physical fences stamp full values at commit");
                var receipt=EmergencyReturn.receipt(f.p);for(int repeat=0;repeat<1500;repeat++)EmergencyReturn.tick(f.p);EmergencyReturn.clear(f.p);h.assertTrue(EmergencyReturn.automaticRemaining(f.p)==1200,"direct calls cannot age the physical automatic fence");h.assertTrue(EmergencyReturn.receipt(f.p).equals(receipt),"clear/direct calls cannot erase historical paid movement");
                var packets=f.positions();h.assertTrue(packets.size()==1,"one actual automatic native packet");f.ack(packets.getFirst());
            });
            f.postAfter(1,()->h.assertTrue(f.shared().getInt("movement")==8&&EmergencyReturn.automaticRemaining(f.p)==1200,"same commit tick finishes Post with full shared8 and automatic1200"));
            f.postAfter(8,()->h.assertTrue(f.shared().getInt("movement")==1&&EmergencyReturn.automaticRemaining(f.p)==1193,"seven subsequent genuine boundaries cannot release the shared movement fence early"));
            f.postAfter(9,()->h.assertTrue(f.shared().getInt("movement")==0&&EmergencyReturn.automaticRemaining(f.p)==1192,"eighth subsequent genuine boundary releases only the shared movement fence"));
            f.finishAfter(10);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void prehit_airborne_source_never_becomes_grounded_rescue(GameTestHelper h) { invalidBefore(h,0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void prehit_falling_motion_never_becomes_grounded_rescue(GameTestHelper h) { invalidBefore(h,1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void prehit_unsupported_source_never_becomes_grounded_rescue(GameTestHelper h) { invalidBefore(h,2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void prehit_swimming_pose_never_becomes_standing_rescue(GameTestHelper h) { invalidBefore(h,3); }
    private static void invalidBefore(GameTestHelper h,int kind){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();
            switch(kind){case 0->f.p.setOnGround(false);case 1->{f.p.fallDistance=5;f.p.setDeltaMovement(0,-.3,0);}case 2->f.block(8,4,8,Blocks.AIR);default->f.p.setPose(Pose.SWIMMING);}
            h.assertTrue(f.hit(3),"native damage remains accepted for unsupported prehit state");f.expectNoEscapeNextPre("prehit source eligibility "+kind);f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void first_representable_posthit_position_change_refuses_without_tolerance(GameTestHelper h) { invalidAfter(h,0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void first_representable_posthit_velocity_change_refuses_without_zeroing(GameTestHelper h) { invalidAfter(h,1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void posthit_pose_change_refuses_without_repairing_body(GameTestHelper h) { invalidAfter(h,2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void posthit_source_support_loss_refuses_without_air_rescue(GameTestHelper h) { invalidAfter(h,3); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void posthit_destination_support_loss_consumes_admitted_mark_without_payment(GameTestHelper h) { invalidAfter(h,4); }
    private static void invalidAfter(GameTestHelper h,int kind){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();h.assertTrue(f.hit(3),"genuine reviewed hit before external postseal change");
            switch(kind){case 0->f.p.setPos(Math.nextUp(f.p.getX()),f.p.getY(),f.p.getZ());case 1->{Vec3 v=f.p.getDeltaMovement();f.p.setDeltaMovement(v.x,Math.nextUp(v.y),v.z);}case 2->f.p.setPose(Pose.CROUCHING);case 3->f.block(8,4,8,Blocks.AIR);default->f.block(4,4,8,Blocks.AIR);}
            f.expectNoEscapeNextPre("postseal external change "+kind);f.nextPre(()->h.assertFalse(EmergencyReturn.marked(f.p),"admitted automatic attempt consumes stale mark once"));f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void canceled_incoming_damage_does_not_arm_or_pay_escape(GameTestHelper h) { nonTrigger(h,0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void zero_native_post_damage_does_not_arm_or_pay_escape(GameTestHelper h) { nonTrigger(h,1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void full_native_absorption_does_not_arm_or_pay_escape(GameTestHelper h) { nonTrigger(h,2); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void native_environmental_dot_and_unknown_source_do_not_arm_escape(GameTestHelper h) { nonTrigger(h,3); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void native_player_attack_never_arms_pve_escape(GameTestHelper h) {
        Fixture f=new Fixture(h),control=new Fixture(h,4,16);f.ready(()->{
            h.assertTrue((Integer)read(control.p,"spawnInvulnerableTime")<=0,"paired native PvP control also completes61 real login ticks");
            f.armAtSource();control.place(f.source.add(0,0,1.5));
            h.assertFalse(EmergencyReturn.marked(control.p),"only tested owner has a342 mark");
            BigDecimal hp=f.health(),other=control.health();DamageWatch a=new DamageWatch(f.p),b=new DamageWatch(control.p);f.hook(a);control.hook(b);
            boolean prior=f.p.server.isPvpAllowed();
            try {
                f.p.server.setPvpAllowed(false);
                h.assertFalse(f.p.canHarmPlayer(control.p)||control.p.canHarmPlayer(f.p),"native PvP-off gate refuses both real players");
                h.assertFalse(f.p.hurt(f.p.damageSources().playerAttack(control.p),3)||control.p.hurt(control.p.damageSources().playerAttack(f.p),3),"native PvP-off blocks both hits before damage callbacks");
                h.assertTrue(a.posts==0&&b.posts==0&&f.health().equals(hp)&&control.health().equals(other),"PvP-off has no actual native Post or HP loss");
                f.p.server.setPvpAllowed(true);
                h.assertTrue(f.p.canHarmPlayer(control.p)&&control.p.canHarmPlayer(f.p),"scoped native PvP fixture admits both real players");
                h.assertTrue(f.p.hurt(f.p.damageSources().playerAttack(control.p),3)&&control.p.hurt(control.p.damageSources().playerAttack(f.p),3),"both actual native player attacks cause ordinary damage");
                h.assertTrue(a.posts==1&&b.posts==1&&a.damage>0&&Float.compare(a.damage,b.damage)==0,"marked and unmarked players settle equal native positive Post damage");
                eq(h,hp.subtract(f.health()),other.subtract(control.health()),"342 does not mitigate ordinary player damage");
            } finally {f.p.server.setPvpAllowed(prior);}
            h.assertTrue(f.p.server.isPvpAllowed()==prior,"native PvP setting restored immediately after synchronous hits");
            f.expectNoEscapeNextPre("real player attack remains outside PvE escape");control.expectNoEscapeNextPre("paired unmarked player attack control");
            h.runAfterDelay(2,()->{f.verify();control.verify();h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void modified_native_arrow_never_arms_plain_arrow_escape(GameTestHelper h) { nonTrigger(h,5); }
    private static void nonTrigger(GameTestHelper h,int kind){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();String hp=f.hp();
            if(kind==0)f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==f.p)e.setCanceled(true);}});
            if(kind==1)f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()==f.p)e.setNewDamage(0);}});
            if(kind==2){f.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);f.p.setAbsorptionAmount(10);}
            if(kind<3){boolean accepted=f.hit(3);h.assertTrue(accepted==(kind!=0),"native cancellation versus accepted zero/absorption semantics stay authoritative");h.assertTrue(f.hp().equals(hp),"no positive HP loss through zero/cancel/full absorption");}
            else if(kind==3){h.assertTrue(f.p.hurt(f.p.damageSources().magic(),3),"actual native magic damage accepted");f.p.hurt(f.p.damageSources().generic(),5);f.p.hurt(f.p.damageSources().onFire(),7);}
            else{Arrow arrow=f.arrow(f.source.add(0,1,3),new Vec3(0,0,-4));arrow.setCritArrow(true);arrow.tick();h.assertTrue(arrow.isRemoved()&&!f.hp().equals(hp),"actual critical arrow impact remains native damage");}
            f.expectNoEscapeNextPre("non-trigger damage case "+kind);f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void actual_vanilla_shield_block_does_not_trigger_without_hp_loss(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));f.aim(f.shooter.getEyePosition());f.p.startUsingItem(InteractionHand.OFF_HAND);
            h.runAfterDelay(6,()->{h.assertTrue(f.p.isBlocking(),"real six-tick shield use is active");String hp=f.hp();f.hit(3);h.assertTrue(f.hp().equals(hp),"native shield fully prevents HP loss");f.expectNoEscapeNextPre("vanilla shield block");f.finishAfter(2);});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void native_lethal_monster_damage_never_rescues_dead_owner(GameTestHelper h){lethal(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void native_totem_is_consumed_without_an_automatic_return_or_extra_life(GameTestHelper h){lethal(h,true);}
    private static void lethal(GameTestHelper h,boolean totem){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();String shield=f.shield();double cp=f.cp();
            if(totem)f.p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
            float amount=f.health().multiply(BigDecimal.valueOf(2)).floatValue();h.assertTrue(Float.isFinite(amount)&&amount<1e20f,"declared native lethal hit respects central exact amount limit");
            h.assertTrue(f.hit(amount),"actual reviewed lethal native hit accepted");
            if(totem)h.assertTrue(f.p.isAlive()&&f.p.getOffhandItem().isEmpty()&&f.p.hasEffect(MobEffects.REGENERATION),"ordinary native totem consumes exactly once and grants native effect");
            else h.assertTrue(f.p.isDeadOrDying(),"native death proceeds normally");
            f.nextPre(()->{h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty()&&EmergencyReturn.automaticRemaining(f.p)==0,"lethal or native rescue never schedules342 teleport or lock");near(h,f.cp(),cp,"lethal has no342 CP charge");h.assertTrue(f.shield().equals(shield),"lethal has no342 baseS charge");});f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void second_same_tick_hit_invalidates_the_first_queued_escape(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();h.assertTrue(f.hit(3),"first native hit accepts and queues");h.assertTrue(EmergencyReturn.isTransporting(f.p),"first hit has pending next-Pre return");
            h.assertTrue(f.hit(5),"second stronger native hit uses ordinary iframe differential");f.expectNoEscapeNextPre("later native hit invalidates older seal");f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void nested_native_hurt_poisoning_prevents_reusing_outer_escape_evidence(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();boolean[] entered={false};f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==f.p&&!entered[0]){entered[0]=true;f.p.hurt(f.p.damageSources().generic(),1);}}});
            f.hit(3);h.assertTrue(entered[0],"actual nested native hurt runs in incoming callback");f.expectNoEscapeNextPre("nested frame cannot mint escape");f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void automatic_event_cancel_is_before_payment_and_consumes_mark(GameTestHelper h){callbackRefusal(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void automatic_event_redirect_is_before_payment_and_consumes_mark(GameTestHelper h){callbackRefusal(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void automatic_event_reentry_does_not_create_second_charge_or_move(GameTestHelper h){callbackRefusal(h,2);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void automatic_event_death_remains_authoritative_and_unpaid(GameTestHelper h){callbackRefusal(h,3);}
    private static void callbackRefusal(GameTestHelper h,int kind){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();h.assertTrue(f.hit(3),"reviewed damage queues before callback veto");String shield=f.shield();double cp=f.cp();
            TeleportHook hook=new TeleportHook(f.p,event->{h.assertTrue(f.shield().equals(shield),"native event is before automaticS debit");
                switch(kind){case 0->event.setCanceled(true);case 1->event.setTargetX(event.getTargetX()+1);case 2->{h.assertFalse(f.press(3),"reentrant cast cannot enter held return");EmergencyReturn.clear(f.p);}default->f.p.hurt(f.p.damageSources().fellOutOfWorld(),f.health().multiply(BigDecimal.valueOf(2)).floatValue());}
            });f.hook(hook);
            f.nextPre(()->{h.assertTrue(hook.calls==1,"one actual native teleport event");if(kind!=3)f.freeReceipt();else{var receipt=EmergencyReturn.receipt(f.p);h.assertTrue(receipt==null||!receipt.paid()&&!receipt.moved(),"native death cleanup retains no false paid movement");}near(h,f.cp(),cp,"callback refusal has noCP charge");h.assertTrue(f.shield().equals(shield)&&EmergencyReturn.automaticRemaining(f.p)==0,"callback refusal has noS payment or automatic lock");
                h.assertFalse(EmergencyReturn.marked(f.p)||EmergencyReturn.isTransporting(f.p),"admitted canceled/redirected/reentered/dead mark is consumed");h.assertTrue(f.p.position().equals(f.source)&&f.positions().isEmpty(),"callback refusal emits no native move");
            });f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void insufficient_base_shield_never_borrows_cp_or_reserves(GameTestHelper h){ insufficientBase(h,false); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void zero_base_shield_never_borrows_cp_or_creates_escape(GameTestHelper h){ insufficientBase(h,true); }
    private static void insufficientBase(GameTestHelper h,boolean zero){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();CompoundTag combat=f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            combat.putString("shield",zero?"0":f.autoCost().subtract(BigDecimal.ONE).toPlainString());String shield=f.shield();double cp=f.cp();
            h.assertTrue(f.hit(3),"actual hit still harms underfunded owner");f.expectNoEscapeNextPre("underfunded certified baseS");
            f.nextPre(()->{near(h,f.cp(),cp,"underfunded auto cannot fall back toCP");h.assertTrue(f.shield().equals(shield),"underfunded auto cannot partially spendS");});f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void automatic_lock_survives_remark_and_manual_return_after_shared_eighth_tick(GameTestHelper h){
        Fixture f=new Fixture(h,5,0);f.ready(()->{f.armAtSource();h.assertTrue(f.hit(3),"first automatic trigger");int[] paidTick={0};
            f.nextPre(()->{f.success(true);paidTick[0]=f.p.server.getTickCount();for(var packet:f.positions())f.ack(packet);});
            h.runAfterDelay(14,()->{
                f.verify();h.assertTrue(f.p.server.getTickCount()-paidTick[0]>=8&&f.shared().getInt("movement")==0,"real native shared eight-tick movement fence has expired");
                // A later declared relocation is a new manual setup after normal knockback physics.
                f.place(f.anchor);h.assertTrue(f.tap(3),"automatic lock permits a fresh free mark");f.place(f.source);int remaining=EmergencyReturn.automaticRemaining(f.p);
                h.assertTrue(remaining>0&&remaining<1200,"remark does not clear paid automatic lock");
                h.runAfterDelay(8,()->{String shield=f.shield();double cp=f.cp();h.assertTrue(f.tap(4),"manual path works during remaining global automatic lock");f.success(false);
                    near(h,f.cp(),cp-.05,"native LV5 manual cost during automatic lock");h.assertTrue(f.shield().equals(shield)&&EmergencyReturn.automaticRemaining(f.p)==remaining-8,"manual return preserves naturally aged global automatic lock");h.succeed();});
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void paid_automatic_fence_survives_native_save_reload_and_clone(GameTestHelper h){
        Fixture f=new Fixture(h),replacement=new Fixture(h,4,16);f.ready(()->{f.armAtSource();h.assertTrue(f.hit(3),"automatic hit before persistence checks");
            f.nextPre(()->{f.success(true);CompoundTag own=f.own(),shared=f.shared();String hp=f.hp(),shield=f.shield();double cp=f.cp();
                AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());EmergencyReturn.remove(f.p);f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.epoch();
                h.assertTrue(f.own().equals(own)&&f.shared().equals(shared)&&EmergencyReturn.automaticRemaining(f.p)==1200,"actual native player NBT reload retains both exact physical fences");
                h.assertTrue(f.hp().equals(hp)&&f.shield().equals(shield)&&f.cp()==cp&&!EmergencyReturn.marked(f.p),"reload retains depleted resources without persisting a mark");
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.p,f.p,false));replacement.epoch();
                h.assertTrue(replacement.own().equals(own)&&replacement.shared().equals(shared)&&EmergencyReturn.automaticRemaining(replacement.p)==1200,"actual clone event copies automatic1200 and shared8 fences");
                h.assertFalse(EmergencyReturn.marked(replacement.p),"clone never duplicates unsaved mark");
            });f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void native_armor_resistance_absorption_and_iframes_match_unmarked_control(GameTestHelper h){
        Fixture f=new Fixture(h),control=new Fixture(h,4,16);f.ready(()->{
            h.assertTrue((Integer)read(control.p,"spawnInvulnerableTime")<=0,"matched native control also genuinely aged out login protection");f.armAtSource();control.place(control.source);
            for(Fixture x:List.of(f,control)){x.p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));x.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));x.p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);x.p.setAbsorptionAmount(.5F);}
            DamageWatch a=new DamageWatch(f.p),b=new DamageWatch(control.p);f.hook(a);control.hook(b);BigDecimal hp=f.health(),other=control.health();
            h.assertTrue(f.hit(10)&&control.hit(10),"identical real native mob damage accepted in marked and control owners");
            h.assertTrue(a.posts==1&&b.posts==1&&a.damage>0&&Float.compare(a.damage,b.damage)==0,"native armor/effect/absorption reductions produce identical positive post damage");
            eq(h,hp.subtract(f.health()),other.subtract(control.health()),"actual HP debit matches unmarked native control");
            h.assertTrue(f.p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()==control.p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()&&f.p.invulnerableTime==control.p.invulnerableTime&&f.p.hurtTime==control.p.hurtTime,"native armor durability and iframe/hurt timers are unchanged by342 admission");
            BodyState body=BodyState.of(f.p);String afterHp=f.hp();
            f.nextPre(()->{f.success(true);h.assertTrue(f.hp().equals(afterHp)&&BodyState.of(f.p).equals(body),"return preserves native mitigation outcome, effects and full remaining iframes");});f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void lv5_manual_move_and_phase_sidestep_share_exact_eight_native_ticks(GameTestHelper h){
        Fixture f=new Fixture(h,5,0);f.mark();h.runAfterDelay(8,()->{f.place(f.source);h.assertTrue(f.tap(2),"LV5 manual native return commits");f.success(false);for(var packet:f.positions())f.ack(packet);f.p.setYRot(180);int moved=f.p.server.getTickCount();
            h.runAfterDelay(4,()->{h.assertTrue(f.p.server.getTickCount()-moved==4&&f.shared().getInt("movement")==4&&f.own().getInt("cooldown")==0,"four real ticks expire own LV5 cooldown but retain shared4");double cp=f.cp();h.assertFalse(PhaseSidestep.start(f.p,f.slotWire(2,1)),"312 cannot bypass342 shared movement fence");near(h,f.cp(),cp,"shared-fence refusal stays free");});
            h.runAfterDelay(8,()->{h.assertTrue(f.p.server.getTickCount()-moved==8&&f.shared().getInt("movement")==0&&f.p.onGround(),"exact eighth real boundary releases shared grounded movement");h.assertTrue(PhaseSidestep.start(f.p,f.slotWire(2,2)),"312 can move at the exact eighth boundary");h.assertTrue(f.shared().getInt("movement")==8,"312 stamps the same movement field again");h.succeed();});
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void level_phase_monster_hit_survives_one_real_connection_cycle_and_returns_next_pre(GameTestHelper h){connectionSuccess(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void level_phase_plain_arrow_hit_survives_one_real_connection_cycle_and_returns_next_pre(GameTestHelper h){connectionSuccess(h,true);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void close_slow_plain_arrow_is_native_damage_not_a_connection_sweep_obstacle(GameTestHelper h){connectionSuccess(h,true,null,true);}
    @GameTest(template=TEMPLATE,batch="highpower_emergency_return_native_origin",timeoutTicks=300)
    public static void ordinary_coordinates_preserve_exact_native_connection_roundtrip(GameTestHelper h){connectionSuccess(h,false,new Vec3(122,215,129.76341580498308));}
    private static void connectionSuccess(GameTestHelper h,boolean arrow){connectionSuccess(h,arrow,null);}
    private static void connectionSuccess(GameTestHelper h,boolean arrow,Vec3 origin){connectionSuccess(h,arrow,origin,false);}
    private static void connectionSuccess(GameTestHelper h,boolean arrow,Vec3 origin,boolean closeArrow){
        Fixture f=new Fixture(h,4,0,true,origin);f.ready(()->{f.armAtSource();Vec3[] seal={null},transientPosition={null};String[] hitHp={null};int[] hitTick={0},cycles={0};
            f.hook(new Object(){@SubscribeEvent(priority=EventPriority.LOWEST) public void transientTick(PlayerTickEvent.Post event){if(event.getEntity()==f.p&&seal[0]!=null&&f.p.server.getTickCount()==hitTick[0])transientPosition[0]=f.p.position();}});
            f.nextConnection(()->{
                hitTick[0]=f.p.server.getTickCount();cycles[0]=(Integer)read(f.p.connection,"tickCount");
                nativeCycleHit(f,arrow,closeArrow);seal[0]=f.p.getDeltaMovement();hitHp[0]=f.hp();
                h.assertTrue(f.p.onGround()&&seal[0].y>0&&EmergencyReturn.isTransporting(f.p),"actual level-phase hurt seals grounded upward motion and queues a one-shot return");
                if(!arrow)h.assertTrue(seal[0].x!=0&&Math.abs(seal[0].x)<.003,"actual close-range Monster hit supplies a tiny nonzero X impulse for native clamp proof");
            },failure->{
                h.assertTrue(failure==null,"actual connection cycle completed without a native exception");
                h.assertTrue((Integer)read(f.p.connection,"tickCount")==cycles[0]+1,"exactly one genuine listener.tick ran after the hit");
                Vec3 displacement=new Vec3(Math.abs(seal[0].x)<.003?0:seal[0].x,seal[0].y,Math.abs(seal[0].z)<.003?0:seal[0].z);
                h.assertTrue(transientPosition[0]!=null&&transientPosition[0].equals(f.source.add(displacement)),"actual PlayerTick.Post transient XYZ matches native per-axis source-plus-displacement arithmetic exactly at these coordinates");
                Vec3 evolved=predictedConnectionMotion(f.p,seal[0]);BodyState entry=BodyState.of(f.p);
                h.assertTrue(f.p.position().equals(f.source)&&f.p.getDeltaMovement().equals(evolved)&&!f.p.onGround()&&evolved.y>0,"native doTick plus accepted-XYZ restoration yields the exact evolved motion and airborne flag");
                h.assertTrue(f.hp().equals(hitHp[0])&&f.positions().isEmpty(),"native connection cycle does not heal or teleport the hurt owner");
                String shield=f.shield();double cp=f.cp();TeleportHook event=new TeleportHook(f.p,e->{h.assertTrue(BodyState.of(f.p).equals(entry),"teleport event sees actual evolved attempt-entry state");});f.hook(event);
                f.nextPre(()->{
                    f.success(true);h.assertTrue(f.p.server.getTickCount()==hitTick[0]+1&&event.calls==1,"one return occurs at the immediately following physical Pre");
                    h.assertTrue(BodyState.of(f.p).equals(entry)&&f.hp().equals(hitHp[0]),"automatic return preserves evolved native velocity, fall, airborne flag, effects, timers and hurt HP");
                    eq(h,new BigDecimal(f.shield()),new BigDecimal(shield).subtract(f.autoCost()),"one10% certified baseS debit after witnessed connection cycle");near(h,f.cp(),cp,"witnessed automatic return does not spend CP");
                    var packets=f.positions();h.assertTrue(packets.size()==1,"one actual native return packet");f.ack(packets.getFirst());
                });
                f.postAfter(1,()->h.assertTrue(f.shared().getInt("movement")==8&&EmergencyReturn.automaticRemaining(f.p)==1200,"same successful Pre ends Post with both full physical fences"));
            });
            f.finishAfter(3);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void fabricated_native_evolved_output_without_connection_witness_cannot_escape(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();nativeCycleHit(f,false);Vec3 seal=f.p.getDeltaMovement();
            // Deliberate negative: ordinary player travel and a forged accepted-position
            // restoration reproduce output values but never execute listener.tick.
            f.p.doTick();f.p.absMoveTo(f.source.x,f.source.y,f.source.z,f.p.getYRot(),f.p.getXRot());
            h.assertTrue(f.p.getDeltaMovement().equals(predictedConnectionMotion(f.p,seal))&&!f.p.onGround(),"negative fixture genuinely reproduces native evolved vector without the connection method");
            f.expectNoEscapeNextPre("matching forged output has no opaque native connection receipt");f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=80)
    public static void direct_public_observer_sequence_cannot_forge_native_connection_provenance(GameTestHelper h){
        Fixture f=new Fixture(h);f.ready(()->{f.armAtSource();nativeCycleHit(f,false);Vec3 seal=f.p.getDeltaMovement();
            // Deliberately call the public observations from this test rather than
            // the transformed native listener. Matching phase order is not authority.
            EmergencyReturnConnectionCycle.beforeDoTick(f.p.connection,f.p);
            f.p.doTick();EmergencyReturnConnectionCycle.afterDoTick(f.p.connection,f.p,true);
            EmergencyReturnConnectionCycle.beforeRestore(f.p.connection,f.p,f.source.x,f.source.y,f.source.z);
            f.p.absMoveTo(f.source.x,f.source.y,f.source.z,f.p.getYRot(),f.p.getXRot());
            EmergencyReturnConnectionCycle.afterRestore(f.p.connection,f.p,true);
            EmergencyReturnConnectionCycle.finished(f.p.connection,true);
            h.assertTrue(f.p.getDeltaMovement().equals(predictedConnectionMotion(f.p,seal))&&!f.p.onGround(),"forged public sequence ends with the same native predicted output values");
            f.expectNoEscapeNextPre("public observer calls outside transformed listener cannot mint opaque provenance");f.finishAfter(2);
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void one_ulp_seal_x_change_before_native_clamp_invalidates_connection_evidence(GameTestHelper h){connectionRefusal(h,0);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void one_ulp_evolved_velocity_change_after_connection_invalidates_evidence(GameTestHelper h){connectionRefusal(h,1);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void second_real_connection_cycle_cannot_extend_escape_window(GameTestHelper h){connectionRefusal(h,2);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void accepted_native_move_packet_after_connection_invalidates_escape(GameTestHelper h){connectionRefusal(h,3);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void genuine_weak_knockback_that_evolves_downward_cannot_escape(GameTestHelper h){connectionRefusal(h,4);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_connection_nonzero_strafe_input_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,5);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_connection_jump_input_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,6);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void changed_native_gravity_before_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,7);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void changed_native_floor_friction_before_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,8);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void lost_native_floor_before_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,9);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void changed_pose_before_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,10);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_ceiling_collision_during_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,11);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_wall_collision_during_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,12);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_entity_collision_during_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,13);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void second_actual_hurt_after_connection_invalidates_earlier_receipt(GameTestHelper h){connectionRefusal(h,14);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void stale_owner_epoch_after_connection_cannot_reuse_receipt(GameTestHelper h){connectionRefusal(h,15);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void replaced_connection_cannot_reuse_old_connection_receipt(GameTestHelper h){connectionRefusal(h,16);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void thrown_native_player_tick_callback_leaves_no_escape_or_payment(GameTestHelper h){connectionRefusal(h,17);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void nested_native_connection_tick_poisoning_cannot_escape(GameTestHelper h){connectionRefusal(h,18);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void discarded_native_friction_before_connection_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,19);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_connection_nonzero_vertical_input_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,20);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=85)
    public static void native_connection_nonzero_forward_input_cannot_mint_escape(GameTestHelper h){connectionRefusal(h,21);}

    private static void connectionRefusal(GameTestHelper h,int kind){
        Fixture f=new Fixture(h,4,0,true);f.ready(()->{f.armAtSource();boolean[] callback={false};Vec3[] seal={null};int[] cycles={0};
            if(kind==4)f.p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.9);
            f.nextConnection(()->{
                nativeCycleHit(f,false);seal[0]=f.p.getDeltaMovement();cycles[0]=(Integer)read(f.p.connection,"tickCount");
                h.assertTrue(seal[0].y>0&&EmergencyReturn.isTransporting(f.p),"actual positive nonlethal hit queues before declared connection mutation "+kind);
                switch(kind){
                    case 0->f.p.setDeltaMovement(Math.nextUp(seal[0].x),seal[0].y,seal[0].z);
                    case 5->f.p.xxa=.1F;
                    case 6->f.p.setJumping(true);
                    case 7->f.p.getAttribute(Attributes.GRAVITY).setBaseValue(.09);
                    case 8->f.block(8,4,8,Blocks.ICE);
                    case 9->f.block(8,4,8,Blocks.AIR);
                    case 10->f.p.setPose(Pose.CROUCHING);
                    case 11->f.block(8,7,8,Blocks.STONE);
                    case 12->{f.block(8,5,7,Blocks.STONE);f.block(8,6,7,Blocks.STONE);}
                    case 13->f.mob(f.source.add(0,0,-.8));
                    case 17,18->f.hook(new Object(){@SubscribeEvent public void playerTick(PlayerTickEvent.Pre event){if(event.getEntity()==f.p&&!callback[0]){callback[0]=true;if(kind==17)throw new IllegalStateException("declared NEW342 native connection callback failure");f.p.connection.tick();}}});
                    case 19->f.p.setDiscardFriction(true);
                    case 20->f.p.yya=.1F;
                    case 21->f.p.zza=.1F;
                    default->{}
                }
            },failure->{
                if(kind==17)h.assertTrue(callback[0]&&failure instanceof net.minecraft.ReportedException,"actual native doTick wraps throwing PlayerTick.Pre callback; driver alone catches it");
                else h.assertTrue(failure==null,"native connection mutation remains an ordinary completed native tick "+kind);
                switch(kind){
                    case 0->h.assertTrue(f.p.getDeltaMovement().x==0&&predictedConnectionMotion(f.p,seal[0]).x==0,"native clamp erases one-ulp tiny-X tampering, so output-only matching is insufficient");
                    case 1->{Vec3 v=f.p.getDeltaMovement();f.p.setDeltaMovement(v.x,Math.nextUp(v.y),v.z);}
                    case 2->{f.p.connection.tick();h.assertTrue((Integer)read(f.p.connection,"tickCount")==cycles[0]+2,"negative actually executes two real listener cycles");}
                    case 3->{Vec3 accepted=f.p.position().add(.125,0,0);f.p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(accepted.x,accepted.y,accepted.z,false));h.assertTrue(f.p.position().equals(accepted),"ordinary native move packet is genuinely accepted after the connection cycle");}
                    case 4->h.assertTrue(f.p.getDeltaMovement().y<0,"real small upward knockback evolves into descending native motion");
                    case 14->h.assertTrue(f.p.hurt(f.p.damageSources().generic(),10),"real second stronger damage call invalidates the earlier frame");
                    case 15->{long old=f.epoch();LegacySingleKeyOwnerEpoch.forget(f.p);h.assertTrue(f.epoch()!=old,"new owner epoch makes old opaque capture stale");}
                    case 16->f.p.connection=new ServerGamePacketListenerImpl(f.p.server,f.connection,f.p,f.cookie);
                    case 18->h.assertTrue(callback[0]&&(Integer)read(f.p.connection,"tickCount")==cycles[0]+2,"native PlayerTick.Pre actually reentered the listener once");
                    default->{}
                }
                f.expectNoEscapeNextPre("invalid native connection evidence "+kind);
            });f.finishAfter(3);
        });
    }
    private static void nativeCycleHit(Fixture f,boolean arrow){nativeCycleHit(f,arrow,false);}
    private static void nativeCycleHit(Fixture f,boolean arrow,boolean closeArrow){
        var h=f.h;BigDecimal hp=f.health();f.shooter.setPos(f.source.add(.0001,0,1.5));
        if(arrow){
            Arrow projectile=f.arrow(f.source.add(0,1,closeArrow?.7:3),new Vec3(0,0,closeArrow?-.3:-4));boolean[] liveContact={false};
            if(closeArrow){
                h.assertTrue(projectile.getBoundingBox().inflate(.2).intersects(f.p.getBoundingBox())&&!projectile.isPushable()&&!f.p.canCollideWith(projectile),"close plain arrow has the exact overlapping0.2 sweep apron but no native pushable/collidable body");
                f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==f.p&&event.getSource().getDirectEntity()==projectile){liveContact[0]=projectile.isAlive()&&!projectile.isRemoved()&&projectile.getOwner()==f.shooter&&projectile.getBoundingBox().inflate(.2).intersects(f.p.getBoundingBox());}}});
            }
            projectile.tick();h.assertTrue(projectile.isRemoved()&&(!closeArrow||liveContact[0]),"actual plain arrow is alive and owned through native damage, then removed by its genuine impact: removed="+projectile.isRemoved()+", liveContact="+liveContact[0]);
        }
        else h.assertTrue(f.shooter.distanceTo(f.p)<2&&f.shooter.doHurtTarget(f.p),"actual nearby Monster.doHurtTarget occurs before native connection tick");
        h.assertTrue(f.health().compareTo(hp)<0&&f.p.isAlive()&&f.p.position().equals(f.source),"native level-phase attack already caused positive nonlethal HP loss at source");
    }
    private static Vec3 predictedConnectionMotion(ServerPlayer p,Vec3 seal){
        // Independent arithmetic oracle for the supported ordinary native travel branch.
        float friction=.6F*.91F;double x=Math.abs(seal.x)<.003?0:seal.x,z=Math.abs(seal.z)<.003?0:seal.z;
        return new Vec3(x*(double)friction,(seal.y-.08)*(double).98F,z*(double)friction);
    }

    private record Snapshot(double cp,long deadline,int cooldown,double mastery,String hp,String shield,double overload) {}
    private record BodyState(Vec3 motion,float fall,boolean ground,boolean gravity,Pose pose,int hurt,int invulnerable,int fire,Map<String,Integer> effects){
        static BodyState of(ServerPlayer p){Map<String,Integer> effects=new TreeMap<>();for(var effect:p.getActiveEffects())effects.put(effect.getEffect().toString(),effect.getDuration());return new BodyState(p.getDeltaMovement(),p.fallDistance,p.onGround(),p.isNoGravity(),p.getPose(),p.hurtTime,p.invulnerableTime,p.getRemainingFireTicks(),Map.copyOf(effects));}
    }
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" != "+expected);}
    private static void eq(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static Object read(Object owner,String name){try{Class<?> type=owner.getClass();while(type!=null){try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    private static Vec3 awaiting(ServerPlayer p){return (Vec3)read(p.connection,"awaitingPositionFromClient");}
    public static final class DamageWatch {
        final ServerPlayer p;DamageSource source;int posts;float damage;
        DamageWatch(ServerPlayer p){this.p=p;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==p)source=e.getSource();}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(LivingDamageEvent.Post e){if(e.getEntity()==p){posts++;damage=e.getNewDamage();}}
    }
    public static final class TeleportHook {
        final ServerPlayer p;final Consumer<EntityTeleportEvent> callback;int calls;
        TeleportHook(ServerPlayer p,Consumer<EntityTeleportEvent> callback){this.p=p;this.callback=callback;}
        @SubscribeEvent public void teleport(EntityTeleportEvent e){if(e.getEntity()==p){calls++;callback.accept(e);}}
    }
    private static final class PreCheck {
        final int tick;final Runnable action;final boolean post;boolean ran;
        PreCheck(int tick,Runnable action){this(tick,action,false);}
        PreCheck(int tick,Runnable action,boolean post){this.tick=tick;this.action=action;this.post=post;}
    }
    /** Registered after mod subscribers. Observation precedes physics, which remains enabled. */
    public static final class NativePlayerPump {
        final Fixture f;final int until;int last;
        NativePlayerPump(Fixture f,int duration){this.f=f;last=f.p.server.getTickCount();until=last+duration;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Pre event){
            if(event.getServer()!=f.p.server)return;int tick=f.p.server.getTickCount();if(tick<=last||tick>until)return;last=tick;
            for(PreCheck check:List.copyOf(f.checks))if(!check.post&&!check.ran&&check.tick==tick){check.ran=true;try{check.action.run();}catch(Throwable failure){if(f.failure==null)f.failure=failure;}}
            if(!f.p.isRemoved()&&!f.p.hasDisconnected())f.p.doTick();
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event){
            if(event.getServer()!=f.p.server)return;int tick=f.p.server.getTickCount();
            for(PreCheck check:List.copyOf(f.checks))if(check.post&&!check.ran&&check.tick==tick){check.ran=true;try{check.action.run();}catch(Throwable failure){if(f.failure==null)f.failure=failure;}}
        }
    }
    private static final class ConnectionAction {
        final int tick;final Runnable before;final Consumer<Throwable> after;boolean ran;
        ConnectionAction(int tick,Runnable before,Consumer<Throwable> after){this.tick=tick;this.before=before;this.after=after;}
    }
    /** Read-only diagnosis for the declared ordinary-coordinate arena. Tracking
     * loss is not necessarily removal: native hidden sections also stop tracking. */
    public static final class ResidencyWatch {
        final Fixture f;Zombie monster;Boolean admitted;boolean firstPost;String firstLoss;
        ResidencyWatch(Fixture f){this.f=f;}
        @SubscribeEvent public void left(EntityLeaveLevelEvent event){
            if(f.closed||firstLoss!=null||(event.getEntity()!=f.p&&event.getEntity()!=monster))return;
            Entity entity=event.getEntity();
            firstLoss="tick="+f.p.server.getTickCount()+",entity="+(entity==f.p?"owner":"monster")+",reason="+entity.getRemovalReason();
            String trace=String.join("\n",Arrays.stream(new Throwable().getStackTrace())
                    .filter(frame->!frame.getClassName().startsWith("net.neoforged.bus."))
                    .limit(16).map(StackTraceElement::toString).toList());
            LOG.warn("NEW342 ordinary-coordinate first tracking loss: {}; state={}; bounded caller trace:\n{}",firstLoss,state(),trace);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void level(LevelTickEvent.Post event){
            if(f.closed||firstPost||event.getLevel()!=f.h.getLevel())return;firstPost=true;snapshot("first_level_post");
        }
        void snapshot(String stage){LOG.info("NEW342 ordinary-coordinate residency {}: {}",stage,state());}
        String state(){return "serverTick="+f.p.server.getTickCount()+",issued="+f.issued+",addFreshEntity="+admitted
                +",owner={"+entity(f.p)+"},monster={"+entity(monster)+"},held="+f.held+",firstLoss="+firstLoss;}
        String entity(Entity entity){
            if(entity==null)return "not_created";
            var level=f.h.getLevel();ChunkPos chunk=entity.chunkPosition();
            Entity byId=level.getEntity(entity.getId()),byUuid=level.getEntity(entity.getUUID());
            return "id="+entity.getId()+",uuid="+entity.getUUID()+",byId="+identity(byId,entity)+",byUuid="+identity(byUuid,entity)
                    +",removed="+entity.isRemoved()+",reason="+entity.getRemovalReason()+",added="+entity.isAddedToLevel()
                    +",entityTicks="+entity.tickCount+",level="+entity.level().dimension().location()+",fixtureLevel="+(entity.level()==level)
                    +",position="+entity.position()+",chunk="+chunk+",held="+f.held.contains(chunk)
                    +",chunkNow="+(level.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null)+",hasChunk="+level.hasChunk(chunk.x,chunk.z)
                    +",entitiesLoaded="+level.areEntitiesLoaded(chunk.toLong())+",entityTicking="+level.isPositionEntityTicking(entity.blockPosition())
                    +",sectionTicking="+level.isNaturalSpawningAllowed(entity.blockPosition());
        }
        String identity(Entity found,Entity expected){return found==null?"null":found==expected?"same":"other(id="+found.getId()+",uuid="+found.getUUID()+")";}
    }
    /** Mock channels are not in ServerConnectionListener. This driver replaces
     * the bare player pump for these cases and executes the real listener method
     * once in the level-then-connection phase, before GameTestTicker callbacks. */
    public static final class NativeConnectionPump {
        final Fixture f;final int until;int last;
        NativeConnectionPump(Fixture f,int duration){this.f=f;last=f.p.server.getTickCount();until=last+duration;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void pre(ServerTickEvent.Pre event){
            if(event.getServer()==f.p.server)f.observe(event.getServer().getTickCount(),false);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void level(LevelTickEvent.Post event){
            if(event.getLevel()!=f.p.serverLevel()||f.closed)return;int tick=f.p.server.getTickCount();if(tick<=last||tick>until)return;last=tick;
            ConnectionAction action=f.connections.stream().filter(a->!a.ran&&a.tick==tick).findFirst().orElse(null);
            if(action!=null){action.ran=true;try{action.before.run();}catch(Throwable failure){f.fail(failure);}}
            Throwable nativeFailure=null;
            try{if(!f.p.isRemoved()&&!f.p.hasDisconnected())f.p.connection.tick();}catch(Throwable failure){nativeFailure=failure;}
            if(action!=null){try{action.after.accept(nativeFailure);}catch(Throwable failure){f.fail(failure);}}
            else if(nativeFailure!=null)f.fail(nativeFailure);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event){
            if(event.getServer()==f.p.server)f.observe(event.getServer().getTickCount(),true);
        }
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final Connection connection;final CommonListenerCookie cookie;final EmbeddedChannel channel;final Vec3 anchor,source,fixtureOrigin;final int zOffset,issued,maxHeldChunks;final boolean nativeConnections;
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<PreCheck> checks=new ArrayList<>();final List<ConnectionAction> connections=new ArrayList<>();final Zombie shooter;final ResidencyWatch residency;Throwable failure;boolean closed,setupReady;
        Fixture(GameTestHelper h){this(h,4,0);}
        Fixture(GameTestHelper h,int level,int zOffset){this(h,level,zOffset,false);}
        Fixture(GameTestHelper h,int level,int zOffset,boolean nativeConnections){this(h,level,zOffset,nativeConnections,null);}
        Fixture(GameTestHelper h,int level,int zOffset,boolean nativeConnections,Vec3 fixtureOrigin){
            this.h=h;this.zOffset=zOffset;this.nativeConnections=nativeConnections;this.fixtureOrigin=fixtureOrigin;anchor=absolute(4.5,5,8.5);source=absolute(8.5,5,8.5);
            int maxX=nativeConnections?14:46;AABB arena=new AABB(absolute(2,4,4),absolute(maxX+1,7,15));h.assertTrue(fixtureOrigin!=null||h.getBounds().contains(arena.minX,arena.minY,arena.minZ)&&h.getBounds().contains(arena.maxX,arena.maxY,arena.maxZ),"supplied native arena contains the entire forty-metre fixture or uses the declared isolated ordinary-coordinate arena");
            ChunkPos first=new ChunkPos(BlockPos.containing(absolute(2,4,4))),last=new ChunkPos(BlockPos.containing(absolute(maxX,4,14)));maxHeldChunks=(last.x-first.x+1)*(last.z-first.z+1);
            for(int x=2;x<=maxX;x++)for(int z=4;z<=14;z++)block(x,4,z,Blocks.STONE);
            var server=h.getLevel().getServer();issued=server.getTickCount();cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"ER"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            for(var packet:positions())ack(packet);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().instabuild=false;p.getAbilities().invulnerable=false;place(anchor);
            residency=nativeConnections&&fixtureOrigin!=null?new ResidencyWatch(this):null;if(residency!=null)hook(residency);
            h.assertTrue(HighPowerCombat.enroll(p,level),"declared real native high-power enrollment");learn(level);HighPowerResources.initializeBudget(p);epoch();drain();shooter=mob(absolute(11.5,5,13.5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            if(nativeConnections)hook(new NativeConnectionPump(this,h.testInfo.getTimeoutTicks()+5));else hook(new NativePlayerPump(this,h.testInfo.getTimeoutTicks()+5));
        }
        void learn(int level){var s=AbilityStorage.get(p);s.selectCategory(HighPowerCategories.TELEPORTER);s.setLevel(level);s.learn(SafeLandingProbe.ID);s.learn(ReturnMark.ID);s.learn(EmergencyReturn.ID);s.learn(PhaseSidestep.ID);s.activated=true;s.overloadFine=true;h.assertTrue(s.presets.edit(0,0,EmergencyReturn.ID,s::learned)&&s.presets.edit(0,1,SafeLandingProbe.ID,s::learned)&&s.presets.edit(0,2,PhaseSidestep.ID,s::learned),"declared native learned342 and prerequisite slots");}
        void place(Vec3 at){p.moveTo(at.x,at.y,at.z,0,0);p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();h.assertTrue(p.onGround()&&p.position().equals(at),"declared setup uses a real downward full-floor collision");}
        Vec3 absolute(double x,double y,double z){Vec3 relative=new Vec3(x,y,z+zOffset);return fixtureOrigin==null?h.absoluteVec(relative):fixtureOrigin.add(relative);}
        void block(int x,int y,int z,Block block){BlockPos pos=fixtureOrigin==null?h.absolutePos(new BlockPos(x,y,z+zOffset)):BlockPos.containing(absolute(x,y,z));h.assertTrue(fixtureOrigin==null?h.getBounds().contains(Vec3.atCenterOf(pos)):x>=2&&x<=(nativeConnections?14:46)&&y>=4&&y<=7&&z>=4&&z<=14,"fixture cell remains inside its declared bounded arena");ChunkPos chunk=new ChunkPos(pos);if(held.add(chunk)){h.assertTrue(held.size()<=maxHeldChunks,"fixture holds only chunks intersecting its actual bounded floor");h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(chunk.x,chunk.z);}changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());}
        Zombie mob(Vec3 at){Zombie mob=EntityType.HUSK.create(h.getLevel());mob.setNoAi(true);mob.setNoGravity(true);mob.setSilent(true);mob.setPos(at);if(residency!=null)residency.monster=mob;boolean admitted=h.getLevel().addFreshEntity(mob);entities.add(mob);if(residency!=null){residency.admitted=admitted;residency.snapshot("after_add");}return mob;}
        Arrow arrow(Vec3 at,Vec3 velocity){Arrow arrow=new Arrow(h.getLevel(),shooter,new ItemStack(Items.ARROW),null);arrow.setPos(at);arrow.setDeltaMovement(velocity);h.getLevel().addFreshEntity(arrow);entities.add(arrow);return arrow;}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return slotWire(0,input);}
        String slotWire(int slot,long input){return new HighPowerInput.Input(slot,input,p.getId(),epoch()).wire();}
        boolean press(long input){return EmergencyReturn.press(p,wire(input));}
        boolean release(long input){return EmergencyReturn.release(p,wire(input));}
        boolean tap(long input){return press(input)&&release(input);}
        void mark(){h.assertTrue(tap(1)&&EmergencyReturn.marked(p),"authenticated short release marks current grounded feet");}
        void armAtSource(){mark();place(source);drain();}
        boolean hit(float amount){return p.hurt(p.damageSources().mobAttack(shooter),amount);}
        void ready(Runnable action){
            if(residency==null){readyAfterDelay(action,-1,-1);return;}
            // GameTestInfo gates only structure chunks. This declared offsite
            // arena needs the same native readiness gate for its existing tickets.
            int setupStarted=p.server.getTickCount();
            h.runAfterDelay(200,()->{
                if(setupReady)return;requireSetupIdentity();
                if(!offsiteReady())rejectSetup("offsite fixture did not become native-visible/ticking within200 genuine server ticks");
            });
            h.startSequence().thenWaitUntil(()->{
                if(h.testInfo.isDone())throw new GameTestAssertException("offsite setup already failed");
                requireSetupIdentity();
                if(p.server.getTickCount()-setupStarted>200)rejectSetup("offsite setup exceeded its200 genuine server tick deadline");
                h.assertTrue(offsiteReady(),"waiting only for native visibility/ticking of the two already-held offsite chunks");
            }).thenExecute(()->{
                setupReady=true;int readyTick=p.server.getTickCount(),readyCycles=(Integer)read(p.connection,"tickCount");
                residency.snapshot("setup_ready");readyAfterDelay(action,readyTick,readyCycles);
            });
        }
        void requireSetupIdentity(){
            if(!Boolean.TRUE.equals(residency.admitted))rejectSetup("offsite Monster admission failed");
            var level=h.getLevel();
            for(Entity entity:List.of(p,shooter)){
                if(entity.isRemoved()||entity.level()!=level)rejectSetup("offsite fixture entity was removed or changed level");
                Entity byId=level.getEntity(entity.getId()),byUuid=level.getEntity(entity.getUUID());
                if(byId!=null&&byId!=entity||byUuid!=null&&byUuid!=entity)rejectSetup("offsite fixture entity was replaced");
            }
            if(p.hasDisconnected())rejectSetup("offsite fixture owner disconnected");
        }
        boolean offsiteReady(){
            var level=h.getLevel();
            return held.stream().allMatch(chunk->level.areEntitiesLoaded(chunk.toLong())&&level.isPositionEntityTicking(chunk.getWorldPosition()))
                    &&level.getEntity(p.getId())==p&&level.getEntity(p.getUUID())==p
                    &&level.getEntity(shooter.getId())==shooter&&level.getEntity(shooter.getUUID())==shooter;
        }
        void rejectSetup(String reason){
            GameTestAssertException rejected=new GameTestAssertException(reason+"; "+residency.state());
            // A pending sequence normally retries assertions. Mark irreversible
            // admission/identity failures terminal before stopping its current step.
            h.testInfo.fail(rejected);throw rejected;
        }
        void readyAfterDelay(Runnable action,int readyTick,int readyCycles){h.runAfterDelay(61,()->{if(residency!=null){residency.snapshot("ready61");h.assertTrue(p.server.getTickCount()-readyTick>=61&&(Integer)read(p.connection,"tickCount")-readyCycles>=61,"native-visible offsite fixture completes at least61 fresh genuine server ticks and listener cycles after setup readiness");}h.assertTrue(p.server.getTickCount()-issued>=61&&(Integer)read(p,"spawnInvulnerableTime")<=0,"61 real resident native ticks expire login protection");h.assertTrue(!nativeConnections||(Integer)read(p.connection,"tickCount")>=61,"native connection fixture genuinely completes at least61 listener cycles");h.assertTrue(!nativeConnections||p.serverLevel().getEntity(p.getId())==p&&p.serverLevel().getEntity(shooter.getId())==shooter,"connection fixture owner and actual Monster remain resident in the native level"+(residency==null?"":"; "+residency.state()));h.assertTrue(p.onGround()&&p.position().equals(anchor),"ordinary native physics retained grounded stationary fixture");action.run();});}
        void nextConnection(Runnable before,Consumer<Throwable> after){h.assertTrue(nativeConnections,"only real native connection fixture schedules connection work");connections.add(new ConnectionAction(p.server.getTickCount()+1,before,after));}
        void fail(Throwable e){if(failure==null)failure=e;}
        void observe(int tick,boolean post){for(PreCheck check:List.copyOf(checks))if(check.post==post&&!check.ran&&check.tick==tick){check.ran=true;try{check.action.run();}catch(Throwable e){fail(e);}}}
        void nextPre(Runnable action){checks.add(new PreCheck(p.server.getTickCount()+1,action));}
        void postAfter(int ticks,Runnable action){checks.add(new PreCheck(p.server.getTickCount()+ticks,action,true));}
        void expectNoEscapeNextPre(String why){Vec3 position=p.position(),motion=p.getDeltaMovement();String shield=shield();double cp=cp();
            nextPre(()->{h.assertTrue(p.position().equals(position)&&p.getDeltaMovement().equals(motion)&&positions().isEmpty(),why+" leaves position and real native motion untouched at nextPre");h.assertTrue(shield().equals(shield)&&EmergencyReturn.automaticRemaining(p)==0,why+" has noS debit or automatic fence");near(h,cp(),cp,why+" has noCP debit");});}
        void success(boolean automatic){var r=EmergencyReturn.receipt(p);h.assertTrue(r!=null&&r.token()>0&&r.paid()&&r.moved()&&r.phase()==EmergencyReturn.SUCCESS&&r.reason().isEmpty()&&r.automatic()==automatic,"one exact paid/moved historical native receipt: "+r);h.assertTrue(p.position().equals(anchor)&&r.destination().equals(anchor)&&!EmergencyReturn.marked(p)&&!EmergencyReturn.isTransporting(p),"native success returns to exact feet and consumes mark");}
        void freeReceipt(){var r=EmergencyReturn.receipt(p);h.assertTrue(r!=null&&!r.paid()&&!r.moved()&&!r.reason().isEmpty(),"free native refusal has a reason: "+r);}
        CompoundTag budget(){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);}
        CompoundTag own(){return p.getPersistentData().getCompound(OWN).copy();}
        CompoundTag shared(){return p.getPersistentData().getCompound(SHARED).copy();}
        double cp(){return HighPowerResources.cp(p);}
        double mastery(){return AbilityStorage.get(p).exp(EmergencyReturn.ID);}
        String hp(){return HighPowerCombat.healthText(p);}
        BigDecimal health(){return new BigDecimal(hp());}
        String shield(){return HighPowerCombat.shieldText(p);}
        BigDecimal autoCost(){return new BigDecimal(HighPowerCombat.maxShieldText(p)).divide(BigDecimal.TEN);}
        Snapshot snapshot(){var s=AbilityStorage.get(p);return new Snapshot(cp(),budget().getLong("next_"+EmergencyReturn.ID),s.cooldowns.getOrDefault(EmergencyReturn.ID,0),mastery(),hp(),shield(),s.overload);}
        void same(Snapshot before,String why){h.assertTrue(snapshot().equals(before),why+" preserves CP, deadline, cooldown, mastery, HP, shield and overload");}
        void unchangedPools(Snapshot before,String why){h.assertTrue(hp().equals(before.hp())&&shield().equals(before.shield())&&mastery()==before.mastery()&&AbilityStorage.get(p).overload==before.overload(),why+" changes no HP, shield, mastery or overload");}
        List<Object> drain(){List<Object> packets=new ArrayList<>();channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){packets.add(packet);ReferenceCountUtil.release(packet);}return packets;}
        List<ClientboundPlayerPositionPacket> positions(){List<ClientboundPlayerPositionPacket> result=new ArrayList<>();for(Object packet:drain())if(packet instanceof ClientboundPlayerPositionPacket position)result.add(position);return result;}
        void ack(ClientboundPlayerPositionPacket packet){p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void verify(){if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;if(failure!=null)throw new AssertionError(failure);h.assertTrue(checks.stream().allMatch(check->check.ran)&&connections.stream().allMatch(action->action.ran),"all scheduled genuine Pre/Post/connection observations occurred");}
        void finishAfter(int ticks){h.runAfterDelay(ticks,()->{verify();h.succeed();});}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);EmergencyReturn.remove(p);ReturnMark.remove(p);SafeLandingProbe.remove(p);PhaseSidestep.remove(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}

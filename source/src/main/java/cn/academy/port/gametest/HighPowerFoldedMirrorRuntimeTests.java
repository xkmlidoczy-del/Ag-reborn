package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.FoldedMirrorRules;
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

/** Native authenticated presses edit real geometry. No fixture can fabricate a plate or damage permit. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerFoldedMirrorRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_folded_mirror";
    private HighPowerFoldedMirrorRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_real_release_pays_each_placement_then_one_reflected_1_5d_hit(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);double cp=f.cp();f.place(target,1);
        near(h,f.cp(),cp-2,"LV2 pays exactly two CP for one accepted slab");
        var plate=FoldedMirrorArray.plates(f.p).getFirst();long started=FoldedMirrorArray.placedAt(f.p);BigDecimal before=hp(target);
        f.pump();h.runAtTickTime(2,()->{
            BigDecimal damage=f.damage();h.assertTrue(f.fire(2),"short ordinary release buys one reflected opportunity");
            exact(h,before.subtract(hp(target)),damage,"one native raw transaction is exactly 1.5D");
            var receipt=FoldedMirrorArray.receipt(f.p);h.assertTrue(receipt!=null&&receipt.crossings()==1&&receipt.path().size()==2&&receipt.status()==FoldedMirrorArray.DAMAGED,"one crossing and two authoritative legs");
            h.assertTrue(receipt.path().getFirst().start().equals(FoldedMirrorArray.hand(f.p)),"injection starts at current real focusing hand");
            h.assertTrue(receipt.path().getFirst().end().equals(receipt.path().getLast().start()),"reflection starts at exact contact with no epsilon skip");
            h.assertTrue(receipt.path().stream().mapToDouble(leg->leg.start().distanceTo(leg.end())).sum()<=40,"single shared path budget");
            h.assertTrue(FoldedMirrorArray.plates(f.p).getFirst().equals(plate)&&FoldedMirrorArray.placedAt(f.p)==started,"firing never rotates plate or refreshes lifetime");
            near(h,f.debit(cp),10,"one placement plus one LV2 shot only");
            h.assertFalse(FoldedMirrorArray.release(f.p,f.wire(2)),"released packet cannot emit a second ray");
            h.assertFalse(f.fire(3),"new input cannot bypass native cooldown or same-tick fence");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_aim_changes_never_reorient_fixed_plate(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);var plate=FoldedMirrorArray.plates(f.p).getFirst();
        f.pump();h.runAtTickTime(2,()->{
            f.face(f.p.getEyePosition().add(-5,4,-8));h.assertTrue(FoldedMirrorArray.plates(f.p).getFirst().equals(plate),"normal survives arbitrary current aim");
            f.fire(2);h.assertTrue(FoldedMirrorArray.plates(f.p).getFirst().equals(plate),"actual firing uses immutable stored plane");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_real_path_can_damage_around_cover(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target);
        Vec3 direct=f.p.getEyePosition().lerp(target.getBoundingBox().getCenter(),.5);f.block(BlockPos.containing(direct));
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2),"downstream target uses reflected path permissions, not direct caster LOS");
            h.assertTrue(hp(target).compareTo(before)<0,"real around-cover ray reaches one target");
            h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(direct)).is(Blocks.STONE),"cover is never destroyed");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_empty_and_allied_interception_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();
        Zombie ally=f.target(0,3);var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("fma"+f.p.getId());f.teams.add(team);board.addPlayerToTeam(f.p.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);BigDecimal friendly=hp(ally);
        f.pump();h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"any allied body before first mirror stops free preparation");
            exact(h,hp(target),before,"ray does not pass ally");exact(h,hp(ally),friendly,"ally is never admitted as victim");near(h,f.debit(cp),0,"invalid preflight has no shot payment");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_unsupported_body_is_a_real_blocker(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();
        var stand=EntityType.ARMOR_STAND.create(h.getLevel());stand.setNoGravity(true);stand.setPos(f.p.position().add(0,0,3));h.getLevel().addFreshEntity(stand);f.entities.add(stand);
        h.assertTrue(stand.canBeHitByProjectile()&&!HighPowerCombat.supported(stand),"native unsupported hittable fixture");
        f.pump();h.runAtTickTime(2,()->{h.assertFalse(f.fire(2),"unsupported hit-able body blocks injection");exact(h,hp(target),before,"unsupported blocker is not pierced");near(h,f.debit(cp),0,"unsupported opportunity remains free");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_solid_at_real_hand_never_becomes_remote_origin(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);double cp=f.cp();BigDecimal before=hp(target);
        f.block(BlockPos.containing(FoldedMirrorArray.hand(f.p)));f.pump();h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"real hand inside a solid cannot start beyond it");exact(h,hp(target),before,"no remote plate-origin hit");near(h,f.debit(cp),0,"blocked source is free");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_postpayment_callback_target_movement_is_spent_miss(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8),replacement=f.target(15,8);f.place(target,1);BigDecimal before=hp(target),other=hp(replacement);double cp=f.cp();boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void attack(cn.academy.port.api.SkillAttackEvent event){if(event.player==f.p&&event.target==target&&event.skill.equals("meltdowner.folded_mirror_array")){called[0]=true;Vec3 old=target.position();target.setPos(old.add(5,0,0));replacement.setPos(old);}}});
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2)&&called[0],"paid frozen opportunity reaches real mutable hook");exact(h,hp(target),before,"target movement revokes complete frozen path");exact(h,hp(replacement),other,"replacement never reacquired");near(h,f.debit(cp),8,"postpayment refusal never refunds");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_native_hook_upstream_wall_revokes_whole_path(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent event){if(event.getEntity()==target&&event.getSource().getEntity()==f.p){called[0]=true;f.block(BlockPos.containing(FoldedMirrorArray.hand(f.p).lerp(FoldedMirrorArray.plates(f.p).getFirst().center(),.5)));}}});
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2)&&called[0],"real native incoming hook runs after payment");exact(h,hp(target),before,"upstream obstruction invalidates even unchanged final leg");near(h,f.debit(cp),8,"native callback miss remains paid");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_reentry_cannot_replace_or_poison_live_paid_context(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);var plates=FoldedMirrorArray.plates(f.p);boolean[] called={false},accepted={true};
        f.hook(new Object(){@SubscribeEvent public void attack(cn.academy.port.api.SkillAttackEvent event){if(event.player==f.p&&event.target==target&&event.skill.equals("meltdowner.folded_mirror_array")){called[0]=true;f.p.setShiftKeyDown(true);accepted[0]=FoldedMirrorArray.start(f.p,f.wire(3));f.p.setShiftKeyDown(false);}}});
        f.pump();h.runAtTickTime(2,()->{h.assertTrue(f.fire(2)&&called[0]&&!accepted[0],"synchronous input recursion cannot mutate resolving array");h.assertTrue(FoldedMirrorArray.plates(f.p).equals(plates),"stored normals and centers survive hook");});
        h.runAtTickTime(4,()->{f.p.setShiftKeyDown(false);h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(3)),"rejected callback did not poison the next authentic input");FoldedMirrorArray.abort(f.p,f.wire(3));h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=24)
    public static void folded_mirror_press_mode_is_frozen_and_long_normal_release_recalls_free(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(true);
        h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(1)),"sneak mode observed at press");f.face(target.getBoundingBox().getCenter());f.p.setShiftKeyDown(false);
        h.assertTrue(FoldedMirrorArray.release(f.p,f.wire(1))&&FoldedMirrorArray.plates(f.p).size()==1,"release crouch cannot convert edit into shot");
        double cp=f.cp();BigDecimal before=hp(target);double xp=AbilityStorage.get(f.p).experience.getOrDefault(FoldedMirrorArray.ID,0.0);f.pump();
        h.runAtTickTime(2,()->{f.p.setShiftKeyDown(false);h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"normal recall hold starts");f.p.setShiftKeyDown(true);});
        h.runAtTickTime(15,()->{
            h.assertTrue(FoldedMirrorArray.release(f.p,f.wire(2))&&!FoldedMirrorArray.active(f.p),"normal press remains recall even after crouching");
            exact(h,hp(target),before,"long release never fires");near(h,f.debit(cp),0,"recall is free without placement refund");near(h,AbilityStorage.get(f.p).experience.getOrDefault(FoldedMirrorArray.ID,0.0),xp,"idle and recall award no mastery");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void folded_mirror_edit_hold_is_bounded_without_erasing_paid_array(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);var old=FoldedMirrorArray.plates(f.p);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{f.face(old.getFirst().center());f.p.setShiftKeyDown(true);h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"select own existing slab");});
        h.runAtTickTime(104,()->{h.assertFalse(FoldedMirrorArray.holding(f.p),"unreleased editing expires at 100 ticks");h.assertTrue(FoldedMirrorArray.plates(f.p).equals(old),"timed-out ghost never overwrites paid normal");near(h,f.debit(cp),0,"holding and canceled edit are free");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=515)
    public static void folded_mirror_editing_never_refreshes_first_placement_deadline(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);long first=FoldedMirrorArray.placedAt(f.p);f.pump();
        h.runAtTickTime(20,()->{
            f.face(FoldedMirrorArray.plates(f.p).getFirst().center());f.p.setShiftKeyDown(true);h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"select original mirror for paid orientation edit");
            f.face(target.getBoundingBox().getCenter());h.assertTrue(FoldedMirrorArray.release(f.p,f.wire(2)),"edited plane commits once");h.assertTrue(FoldedMirrorArray.placedAt(f.p)==first,"paid edit preserves original deadline");
        });
        h.runAtTickTime(FoldedMirrorRules.LIFETIME-2,()->h.assertTrue(FoldedMirrorArray.active(f.p),"array remains alive before immutable deadline"));
        h.runAtTickTime(FoldedMirrorRules.LIFETIME+5,()->{h.assertFalse(FoldedMirrorArray.active(f.p),"array expires relative to first placement despite later editing");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void folded_mirror_wrong_actor_epoch_replays_and_abort_spam_are_free(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();f.p.setShiftKeyDown(false);
        h.assertFalse(FoldedMirrorArray.start(f.p,new HighPowerInput.Input(0,1,f.p.getId()+1,f.epoch()).wire()),"foreign actor rejected");
        String stale=f.wire(1);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(FoldedMirrorArray.start(f.p,stale),"retired epoch rejected");
        h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"one authenticated press accepted");h.assertTrue(FoldedMirrorArray.abort(f.p,f.wire(2)),"menu abort retires own context");
        h.assertFalse(FoldedMirrorArray.start(f.p,f.wire(2)),"exact replay cannot resurrect context");h.assertFalse(FoldedMirrorArray.start(f.p,f.wire(3)),"fresh input cannot erase persistent physical tick fence");
        near(h,f.cp(),cp,"all preparations free");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void folded_mirror_category_preset_interference_death_logout_clear_paid_array(GameTestHelper h){
        for(int mode=0;mode<5;mode++){
            Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);double cp=f.cp();
            switch(mode){case 0->AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);case 1->AbilityStorage.get(f.p).selectCategory("electromaster");case 2->AbilityStorage.get(f.p).interfering=true;case 3->f.p.setHealth(0);default->FoldedMirrorArray.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));}
            FoldedMirrorArray.tick(f.p);h.assertFalse(FoldedMirrorArray.active(f.p),"lifecycle clears array "+mode);
            if(mode!=1)near(h,f.cp(),cp,"lifecycle does not refund paid placement "+mode);f.close();
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH+"_border",timeoutTicks=12)
    public static void folded_mirror_each_leg_checks_world_border_without_forced_chunks(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{
            var border=h.getLevel().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();
            try{border.setCenter(f.p.getX(),f.p.getZ());border.setSize(12);h.assertFalse(f.fire(2),"remote plate volume outside border is ineligible");exact(h,hp(target),before,"no outside-border reflected hit");near(h,f.debit(cp),0,"border refusal free");h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==chunks,"no forced chunks for remote geometry");}
            finally{border.setCenter(x,z);border.setSize(size);}h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_all_four_real_bindings_survive_decoded_cp_snapshots_and_release_disposal(GameTestHelper h){
        for(int slot=0;slot<4;slot++){
            Fixture f=new Fixture(h);Zombie target=f.target(6,8);var state=AbilityStorage.get(f.p);
            if(slot!=0){state.presets.edit(0,0,"",id->true);h.assertTrue(state.presets.edit(0,slot,FoldedMirrorArray.ID,id->true),"bind actual slot "+slot);}
            MirrorClientBinding client=new MirrorClientBinding(f,slot);double cp=f.cp();
            f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(false);client.physicalShift=true;client.pre(true);
            h.assertTrue(client.inputs.queuedStart()!=null&&!FoldedMirrorArray.active(f.p),"Pre captures physical edge without overtaking vanilla crouch packet "+slot);
            client.snapshot();h.assertTrue(client.inputs.queuedFor(client.context()),"decoded NBT snapshot preserves unchanged pending dispatch context "+slot);
            f.p.connection.handlePlayerCommand(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(f.p,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            client.post();
            h.assertTrue(client.accepted&&FoldedMirrorArray.holding(f.p),"Post sends authenticated start after native crouch command "+slot);
            client.snapshot();client.binding.modernSnapshotFallback();
            f.face(target.getBoundingBox().getCenter());client.tick(false);
            h.assertTrue(client.releases==1&&FoldedMirrorArray.plates(f.p).size()==1,"snapshot delegate replacement preserves one physical release "+slot);
            h.assertTrue(client.state.cooldowns.getOrDefault(FoldedMirrorArray.ID,0)==1,"actual payment snapshot decodes one-tick guard "+slot);
            client.binding.modernSnapshotFallback();client.tick(false);client.tick(false);
            h.assertTrue(FoldedMirrorArray.active(f.p)&&!FoldedMirrorArray.holding(f.p),"released paid array survives classic delegate disposal "+slot);
            near(h,f.cp(),cp-2,"all bindings debit placement exactly once "+slot);f.close();
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_q5_successful_opportunities_use_one_tick_cooldown(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie target=f.target(6,8);f.place(target,1);BigDecimal before=hp(target),damage=f.damage();double cp=f.cp();
        // GameTest's repeating and scheduled callbacks have no relative ordering contract.
        // Run the actual native player tick once at each explicit server-tick boundary.
        h.runAtTickTime(1,f::tickPlayer);
        h.runAtTickTime(2,()->{
            f.tickPlayer();
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(FoldedMirrorArray.ID,0)==0,"native player tick expires placement cooldown before first opportunity");
            h.assertTrue(f.fire(2),"first LV5 one-tick opportunity commits");exact(h,before.subtract(hp(target)),damage,"first native 1.5D hit");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(FoldedMirrorArray.ID,0)==1,"advertised LV5 cooldown is one native tick");
        });
        h.runAtTickTime(3,()->{
            f.tickPlayer();
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(FoldedMirrorArray.ID,0)==0,"exactly next native player tick expires one-tick cooldown");
            boolean started=FoldedMirrorArray.start(f.p,f.wire(3));
            boolean fired=started&&FoldedMirrorArray.release(f.p,f.wire(3));
            h.assertTrue(fired,"next actual server tick permits another paid LV5 opportunity; started="+started
                    +" cooldown="+AbilityStorage.get(f.p).cooldowns+" clock="+h.getLevel().getGameTime()
                    +" fences="+f.p.getPersistentData().getCompound("academy:folded_mirror_array")
                    +" owner="+f.p.position()+" target="+target.position()+" receipt="+FoldedMirrorArray.receipt(f.p));
            near(h,f.debit(cp),.1,"two shots pay exactly .05 CP each without four-tick successful-shot throttle");
            exact(h,before.subtract(hp(target)),damage,"native equal-hit iframe still refuses second damage");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void folded_mirror_real_reload_keeps_physical_and_productive_mastery_fences(GameTestHelper h){
        Fixture f=new Fixture(h,5);Zombie first=f.target(6,8),second=f.target(18,8);f.place(first,1);f.pump();
        h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2),"first productive reflected hit");near(h,AbilityStorage.get(f.p).exp(FoldedMirrorArray.ID),.005,"one productive mastery award");
            AbilityStorage.save(f.p);var saved=new net.minecraft.nbt.CompoundTag();f.p.saveWithoutId(saved);double cp=f.cp();
            FoldedMirrorArray.remove(f.p);AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);f.p.load(saved);f.p.connection.resetPosition();f.epoch();
            h.assertFalse(FoldedMirrorArray.active(f.p),"ephemeral paid geometry is never restored from save");near(h,f.cp(),cp,"save reload never refunds or refills placements and shot");
            h.assertFalse(FoldedMirrorArray.start(f.p,f.wire(3)),"new owner epoch cannot erase same-tick physical fence");
            Vec3 old=first.position();first.setPos(old.add(15,0,0));second.setPos(old);
        });
        h.runAtTickTime(4,()->f.place(second,4));
        h.runAtTickTime(6,()->{
            BigDecimal hp=hp(second);h.assertTrue(f.fire(5)&&hp(second).compareTo(hp)<0,"new array can earn a separate real hit after reload");
            near(h,AbilityStorage.get(f.p).exp(FoldedMirrorArray.ID),.005,"persisted twenty-tick fence suppresses second early award");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_authenticated_pending_abort_retires_delayed_start_only(GameTestHelper h){
        Fixture f=new Fixture(h);double cp=f.cp();String pending=f.wire(1);
        h.assertTrue(FoldedMirrorArray.abort(f.p,pending),"authenticated cancellation retires a pending edge before its start");
        h.assertFalse(FoldedMirrorArray.start(f.p,pending),"delayed canceled start cannot resurrect a ghost");
        h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"pending cancellation does not spend the next physical input tick");
        h.assertFalse(FoldedMirrorArray.abort(f.p,pending),"old pending cancellation cannot kill newer hold");
        h.assertTrue(FoldedMirrorArray.holding(f.p),"new hold retains exact ownership");
        h.assertTrue(FoldedMirrorArray.abort(f.p,f.wire(3)),"newer canceled press may clear its own older array context");
        h.assertFalse(FoldedMirrorArray.start(f.p,f.wire(3)),"matching delayed newer start is also tombstoned");
        near(h,f.cp(),cp,"all pending-cancel edges remain free");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_body_tied_with_first_plate_wins_without_any_reflection(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(6,8);f.place(target,1);Vec3 center=FoldedMirrorArray.plates(f.p).getFirst().center();
        Zombie tied=f.target(14,14);tied.setPos(center.x,center.y-tied.getBbHeight()/2,center.z+FoldedMirrorRules.RADIUS+tied.getBbWidth()/2);
        BigDecimal before=hp(target),body=hp(tied);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"closed body contact at mirror wins before the first crossing");exact(h,hp(target),before,"target beyond tied plane not reached");exact(h,hp(tied),body,"body before first crossing cannot receive damage");near(h,f.debit(cp),0,"tied first contact never buys a shot");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void folded_mirror_native_solid_terminal_ties_are_closed_on_all_axes(GameTestHelper h){
        Fixture f=new Fixture(h);BlockPos block=f.p.blockPosition().offset(0,1,6);f.block(block);double radius=FoldedMirrorRules.RADIUS;
        Vec3 middle=Vec3.atCenterOf(block);
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            double[] end={middle.x,middle.y,middle.z};int coordinate=axis==0?block.getX():axis==1?block.getY():block.getZ();
            end[axis]=sign>0?coordinate-radius:coordinate+1+radius;
            Vec3 terminal=new Vec3(end[0],end[1],end[2]);double[] start=end.clone();start[axis]-=sign*2;
            NativeTrace result=nativeTrace(f.p,new Vec3(start[0],start[1],start[2]),terminal);
            h.assertTrue(result.permitted&&result.contact&&result.status==FoldedMirrorArray.BLOCKED,"native solid terminal boundary wins axis="+axis+" sign="+sign);
            h.assertTrue(result.point.distanceToSqr(terminal)<1e-20,"candidate expansion never moves the exact ray endpoint or adds collision reach");
        }h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void folded_mirror_outward_candidate_lookup_never_loads_unavailable_volume(GameTestHelper h){
        Fixture f=new Fixture(h);BlockPos absent=new BlockPos(12000007,100,12000007);h.assertFalse(h.getLevel().hasChunkAt(absent),"remote native ray volume starts unloaded");
        int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();Vec3 end=Vec3.atCenterOf(absent);
        NativeTrace result=nativeTrace(f.p,end.add(0,0,-6),end);
        h.assertFalse(result.permitted,"permission and loaded lookup bound is checked before native shapes");
        h.assertTrue(!h.getLevel().hasChunkAt(absent)&&h.getLevel().getChunkSource().getLoadedChunksCount()==chunks,"closed candidate lookup never requests missing chunks");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void folded_mirror_returning_ray_stops_at_caster_without_self_damage(GameTestHelper h){
        Fixture f=new Fixture(h);Zombie target=f.target(0,-6);f.place(target,1);BigDecimal before=hp(target),owner=hp(f.p);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"return leg encounters real caster body before target behind them");
            exact(h,hp(f.p),owner,"mirror never damages its owner");exact(h,hp(target),before,"returning ray cannot pass through caster");near(h,f.debit(cp),0,"caster-blocked preflight remains free");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=14)
    public static void folded_mirror_two_real_placements_produce_two_reflections_and_one_native_target(GameTestHelper h){
        Fixture f=new Fixture(h);Vec3 original=f.p.position();double cp=f.cp();
        f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(true);h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(1)),"first center pinned by real edit input");
        f.face(f.p.getEyePosition().add(24,0,0));h.assertTrue(FoldedMirrorArray.release(f.p,f.wire(1)),"first fixed plane aims toward server-observed air exit");f.p.setShiftKeyDown(false);
        float yaw=f.p.getYRot(),pitch=f.p.getXRot();var first=FoldedMirrorArray.plates(f.p).getFirst();
        Vec3 incoming=first.center().subtract(FoldedMirrorArray.hand(f.p)).normalize();Vec3 outgoing=incoming.subtract(first.normal().scale(2*incoming.dot(first.normal()))).normalize();
        Vec3 desiredSecond=first.center().add(outgoing.scale(4));Zombie target=f.target(15,15);target.setPos(desiredSecond.x,original.y,desiredSecond.z+6);BigDecimal before=hp(target);long placed=FoldedMirrorArray.placedAt(f.p);f.pump();
        h.runAtTickTime(2,()->{
            // A real player move makes the second desired center a visible pin at placement reach.
            // No controller field or permit is injected. The final firing pose returns exactly
            // to the pose from which the first normal was fixed.
            f.p.setPos(desiredSecond.x-FoldedMirrorRules.PLACE_RANGE,original.y,desiredSecond.z);f.p.connection.resetPosition();f.face(f.p.getEyePosition().add(8,0,0));f.p.setShiftKeyDown(true);
            h.assertTrue(FoldedMirrorArray.start(f.p,f.wire(2)),"second distinct center pinned by second real edit");f.face(target.getBoundingBox().getCenter());
            h.assertTrue(FoldedMirrorArray.release(f.p,f.wire(2)),"second fixed normal uses mirror-one center as incoming reference");f.p.setShiftKeyDown(false);
            h.assertTrue(FoldedMirrorArray.plates(f.p).size()==2&&FoldedMirrorArray.plates(f.p).getFirst().equals(first),"one owner has exactly two separately fixed planes");
            h.assertTrue(FoldedMirrorArray.placedAt(f.p)==placed,"second paid placement cannot refresh array lifetime");near(h,f.debit(cp),4,"each accepted LV2 placement costs two CP");
            f.p.setPos(original);f.p.setYRot(yaw);f.p.setXRot(pitch);f.p.connection.resetPosition();
        });
        h.runAtTickTime(4,()->{
            BigDecimal damage=f.damage();h.assertTrue(f.fire(3),"two actual finite-plane crossings admit one paid native target");
            exact(h,before.subtract(hp(target)),damage,"two mirrors still produce exactly one 1.5D native hit");
            var receipt=FoldedMirrorArray.receipt(f.p);h.assertTrue(receipt!=null&&receipt.crossings()==2&&receipt.path().size()==3,"authoritative receipt has two contacts and three legs");
            h.assertTrue(receipt.path().get(0).end().equals(receipt.path().get(1).start())&&receipt.path().get(1).end().equals(receipt.path().get(2).start()),"both continuations begin at exact preceding contact");
            h.assertTrue(receipt.path().stream().mapToDouble(leg->leg.start().distanceTo(leg.end())).sum()<=40,"all three legs share one forty-meter budget");near(h,f.debit(cp),12,"two placements and one shot only");h.succeed();
        });
    }

    private static BigDecimal hp(LivingEntity target){return new BigDecimal(HighPowerCombat.healthText(target));}
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" / "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" / "+expected);}
    private record NativeTrace(Vec3 point,int status,boolean permitted,boolean contact) {}
    /** Reads the actual private native geometry seam; it grants no controller or combat authority. */
    private static NativeTrace nativeTrace(ServerPlayer p,Vec3 start,Vec3 end){
        try{
            var trace=FoldedMirrorArray.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);Object result=trace.invoke(null,p,start,end);
            var point=result.getClass().getDeclaredMethod("point");var status=result.getClass().getDeclaredMethod("status");var permitted=result.getClass().getDeclaredMethod("permitted");var contact=result.getClass().getDeclaredMethod("contact");
            point.setAccessible(true);status.setAccessible(true);permitted.setAccessible(true);contact.setAccessible(true);
            return new NativeTrace((Vec3)point.invoke(result),(Integer)status.invoke(result),(Boolean)permitted.invoke(result),(Boolean)contact.invoke(result));
        }catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
    }
    /** The actual classic coordinator, engine NBT snapshots and high-power physical-release fallback. */
    private static final class MirrorClientBinding implements cn.academy.port.client.ClassicClientInputBinding.Host {
        final Fixture f;final int slot;final cn.academy.port.client.ClassicClientInputBinding binding;
        final cn.academy.port.client.FoldedMirrorInputState inputs=new cn.academy.port.client.FoldedMirrorInputState();
        cn.academy.port.core.AbilityProgress state;boolean held,accepted,physicalShift;long nextInput;int releases;
        MirrorClientBinding(Fixture f,int slot){this.f=f;this.slot=slot;snapshot();binding=new cn.academy.port.client.ClassicClientInputBinding(this);binding.runtime().updateDefaultGroup();}
        void snapshot(){state=AbilityStorage.decode(AbilityStorage.encode(AbilityStorage.get(f.p)));}
        cn.academy.port.client.FoldedMirrorInputState.DispatchContext context(){
            StringBuilder mappings=new StringBuilder();for(int at=0;at<slotCount();at++)mappings.append(mappedSkill(at)).append(':').append(mappedKey(at)).append(';');
            return new cn.academy.port.client.FoldedMirrorInputState.DispatchContext(f.p.serverLevel(),f.p,f.p.connection,ownerEpoch(),state.presets.current(),state.presets.revision(),mappings.toString());
        }
        void pre(boolean down){held=down;state.tickCooldowns();var pending=inputs.releaseWhenUp(down);if(pending!=null)release(pending);binding.tickStart();}
        void post(){
            binding.finishLocalDisposals();var queued=inputs.queuedStart();if(queued==null)return;var identity=queued.identity();String skill=FoldedMirrorArray.ID;
            boolean ready=inGame()&&actorId()==identity.actor()&&ownerEpoch()==identity.epoch()&&inputs.queuedFor(context())
                    &&skill.equals(state.presets.currentSkill(identity.slot()))&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)
                    &&cn.academy.port.SkillCatalog.enabled(state.category,skill)&&state.canUse(skill);
            if(!ready){var canceled=inputs.cancel();if(canceled!=null)cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_folded_mirror_abort",canceled.wire()));snapshot();return;}
            var dispatch=inputs.flushQueuedStart();if(dispatch==null)return;
            accepted=cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_folded_mirror_start",dispatch.start().wire()));snapshot();
            if(dispatch.release()!=null)release(dispatch.release());
        }
        void tick(boolean down){pre(down);post();}
        void release(HighPowerInput.Input input){releases++;cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_folded_mirror_release",input.wire()));snapshot();}
        public boolean available(){return true;}public boolean inGame(){return f.p.isAlive()&&!f.p.isRemoved()&&!f.p.isSpectator();}
        public boolean categoryActivated(){return state.isActivated();}public boolean canUseAbility(){return state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress();}
        public boolean terminalOpen(){return false;}public int actorId(){return f.p.getId();}public long ownerEpoch(){return f.epoch();}
        public boolean physicalDown(int key){return held&&key==33+slot;}
        public boolean registeredMapping(String skill){return cn.academy.port.preset.PresetSkills.registeredMapping(state,skill);}
        public boolean eligible(cn.academy.port.client.ClassicClientInputBinding.Node node){return inGame()&&state.canUse(node.skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,node.skill)&&cn.academy.port.SkillCatalog.enabled(state.category,node.skill);}
        public String mappedSkill(int at){return state.presets.currentSkill(at);}public int mappedKey(int at){return 33+at;}public int slotCount(){return 4;}
        public void rawActivation(boolean desired){throw new AssertionError("No activation edge expected");}public void replaceOverrides(int[] keys){}
        public long down(cn.academy.port.client.ClassicClientInputBinding.Node node){if(inputs.holding()||inputs.queuedStart()!=null)return 0;var input=new HighPowerInput.Input(node.slot,++nextInput,node.actorId(),node.ownerEpoch());inputs.begin(input,node.key,physicalShift,context());return input.input();}
        public void up(cn.academy.port.client.ClassicClientInputBinding.Node node,long nonce){var input=inputs.release(new HighPowerInput.Input(node.slot,nonce,node.actorId(),node.ownerEpoch()));if(input!=null)release(input);}
        public void abort(cn.academy.port.client.ClassicClientInputBinding.Node node,long nonce){var input=inputs.cancel(new HighPowerInput.Input(node.slot,nonce,node.actorId(),node.ownerEpoch()));if(input!=null)cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("hp_folded_mirror_abort",input.wire()));snapshot();}
        public boolean cancelPending(cn.academy.port.client.ClassicClientInputBinding.Node node,long input){return false;}
        public void direction(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context,int direction,cn.academy.port.client.ClassicClientInputBinding.Edge edge,boolean silent){}
        public void localContextEnd(cn.academy.port.client.ClassicClientInputBinding.ContextIdentity context){}public void contextAbort(cn.academy.port.core.TargetedContextTermination.Request request){}
        public void modernCleanup(Collection<cn.academy.port.client.ClassicClientInputBinding.Node> defaults){}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final List<Entity> entities=new ArrayList<>();
        final List<Object> hooks=new ArrayList<>();final List<BlockPos> blocks=new ArrayList<>();final List<net.minecraft.world.scores.PlayerTeam> teams=new ArrayList<>();
        BigDecimal credits=BigDecimal.ZERO;boolean closed,pumping;
        Fixture(GameTestHelper h){this(h,2);}
        Fixture(GameTestHelper h,int level){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"FM"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;Vec3 at=h.absoluteVec(new Vec3(12.5,18,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,level),"real authenticated exact caster at LV"+level);var state=AbilityStorage.get(p);state.selectCategory("meltdowner");state.setLevel(level);state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,FoldedMirrorArray.ID,id->true),"bind learned NEW221 after its prerequisite");HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double x,double z){
            Zombie target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(target);
            h.assertTrue(HighPowerCombat.enroll(target,5),"native enrolled durable target");entities.add(target);return target;
        }
        void face(Vec3 point){Vec3 delta=point.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
        void place(LivingEntity target,long input){
            face(p.getEyePosition().add(0,0,8));p.setShiftKeyDown(true);h.assertTrue(FoldedMirrorArray.start(p,wire(input)),"pin directly visible center from real press");
            face(target.getBoundingBox().getCenter());h.assertTrue(FoldedMirrorArray.release(p,wire(input)),"real release fixes reflected normal and pays once");p.setShiftKeyDown(false);
        }
        boolean fire(long input){p.setShiftKeyDown(false);return FoldedMirrorArray.start(p,wire(input))&&FoldedMirrorArray.release(p,wire(input));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        BigDecimal damage(){return FoldedMirrorRules.damage(HighPowerGrowth.values(p).damage());}
        void block(BlockPos pos){h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());blocks.add(pos);}
        void tickPlayer(){if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();credits=credits.add(new BigDecimal(cp()).subtract(before));}}
        void pump(){if(pumping)return;pumping=true;h.onEachTick(this::tickPlayer);}
        double debit(double initial){return new BigDecimal(initial).add(credits).subtract(new BigDecimal(cp())).doubleValue();}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));FoldedMirrorArray.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}

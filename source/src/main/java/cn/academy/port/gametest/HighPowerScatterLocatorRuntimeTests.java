package cn.academy.port.gametest;

import cn.academy.port.highpower.core.ScatterLocatorRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorRules;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW223 uses real authenticated input, native planes and the existing NEW221 damage bridge.
 * No test fabricates a controller context, plane, fee claim or emission permit. */
@GameTestHolder("academy_scatter_locator_runtime")
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
@PrefixGameTestTemplate(false)
public final class HighPowerScatterLocatorRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="scatter_locator_native";
    private static final String LOCATOR="hp_scatter_locator_", MIRROR="hp_folded_mirror_", RELAY="hp_remote_relay_";
    private HighPowerScatterLocatorRuntimeTests() {}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void installFocusedTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()
                ||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        var original=level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_highpower",TEMPLATE));
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_scatter_locator_runtime",TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),original.save(new CompoundTag()));
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_twenty_two_meter_air_plate_holds_fee_and_next_221_emits_one_1_5d_hit(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);BigDecimal before=hp(target);double cp=f.cp();
        f.place(target,1);var plate=ScatterLocatorPlate.plate(f.p);
        near(h,plate.center().distanceTo(f.p.getEyePosition()),22,"ordinary air pin leaves the approved two-meter movement margin inside hard range");
        near(h,f.cp(),cp,"placement reserves control fee without debiting total CP");
        near(h,HighPowerResources.availableCp(f.p),cp-2,"held two CP is excluded from spendable balance");
        near(h,HighPowerResources.heldAmount(f.p),2,"one fixed control fee owns the plate");
        h.assertTrue(ScatterLocatorPlate.plates(f.p).size()==1,"one owner has exactly one locator plane");
        f.pump();h.runAtTickTime(2,()->{
            BigDecimal damage=FoldedMirrorRules.damage(HighPowerGrowth.values(f.p).damage());
            f.face(f.p.getEyePosition().add(-6,4,-5));
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"current aim does not move or reorient the fixed plane");
            f.face(target.getBoundingBox().getCenter());
            h.assertTrue(f.fire(2),"ordinary NEW221 network input borrows the real locator plane");
            exact(h,before.subtract(hp(target)),damage,"one native terminal transaction is exactly 1.5D");
            var receipt=FoldedMirrorArray.receipt(f.p);
            h.assertTrue(receipt!=null&&receipt.crossings()==1&&receipt.path().size()==2&&receipt.status()==FoldedMirrorArray.DAMAGED,
                    "borrow receipt retains exactly one reflection and two real legs");
            h.assertTrue(receipt.path().getFirst().start().equals(FoldedMirrorArray.hand(f.p)),"ray starts at current real hand");
            h.assertTrue(receipt.path().getFirst().end().equals(receipt.path().getLast().start()),"second leg starts at the exact plane crossing");
            h.assertTrue(receipt.path().stream().mapToDouble(s->s.start().distanceTo(s.end())).sum()<=40,"both legs share one forty-meter budget");
            near(h,f.debit(cp),10,"one atomic payment contains frozen two-CP fee plus eight-CP NEW221 shot");
            near(h,HighPowerResources.heldAmount(f.p),0,"committed fee has no remaining reservation");
            h.assertFalse(ScatterLocatorPlate.active(f.p),"successful borrow retires locator ownership");
            h.assertTrue(FoldedMirrorArray.plates(f.p).isEmpty(),"borrowed plane never becomes a free persistent NEW221 array");
            h.assertFalse(f.request(MIRROR,"release",1,2),"same release cannot replay the borrowed shot");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_crouch_then_standing_release_keeps_real_air_placement_usable(GameTestHelper h) { nativePoseRelease(h,false); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_crouch_then_small_retreat_and_standing_release_keeps_air_placement_usable(GameTestHelper h) { nativePoseRelease(h,true); }

    private static void nativePoseRelease(GameTestHelper h,boolean retreat) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);BigDecimal before=hp(target);double cp=f.cp();Vec3[] center={null};
        f.p.setShiftKeyDown(true);f.pump();
        h.runAtTickTime(2,()->{
            f.tickPlayer();h.assertTrue(f.p.getPose()==net.minecraft.world.entity.Pose.CROUCHING,"actual native player tick establishes crouched placement eye");
            f.face(f.p.getEyePosition().add(0,0,24));center[0]=f.p.getEyePosition().add(0,0,22);
            h.assertTrue(f.request(LOCATOR,"start",0,1),"authenticated press captures real crouched-eye air center");
            f.p.setShiftKeyDown(false);
            if(retreat){f.p.setPos(f.p.position().add(0,0,-.5));f.p.connection.resetPosition();}
        });
        h.runAtTickTime(4,()->{
            f.tickPlayer();h.assertTrue(f.p.getPose()==net.minecraft.world.entity.Pose.STANDING,"actual native player tick observes uncrouch before key release");
            f.face(target.getBoundingBox().getCenter());h.assertTrue(f.request(LOCATOR,"release",0,1),"ordinary pose transition remains inside the unchanged hard twenty-four-meter range");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).center().equals(center[0]),"release never drags the originally pinned air center");
            double distance=f.p.getEyePosition().distanceTo(center[0]);h.assertTrue(distance>22&&distance<24,"real changed eye and optional retreat use the intended interior margin");
            near(h,f.debit(cp),0,"native pose transition reserves fee without total CP debit");near(h,HighPowerResources.heldAmount(f.p),2,"ordinary gesture owns one control fee");
        });
        h.runAtTickTime(6,()->{
            BigDecimal damage=FoldedMirrorRules.damage(HighPowerGrowth.values(f.p).damage());
            h.assertTrue(f.fire(2),"next real NEW221 input borrows plane fixed after ordinary pose transition");
            exact(h,before.subtract(hp(target)),damage,"usable gesture produces exactly one native reflected 1.5D hit");
            near(h,f.debit(cp),10,"ordinary gesture and borrow produce exactly one combined payment");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void directly_visible_surface_beyond_twenty_two_meters_is_not_clamped_to_air_default(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);double cp=f.cp();
        BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,24));f.block(wall);f.place(target,1);
        var plate=ScatterLocatorPlate.plate(f.p);double distance=f.p.getEyePosition().distanceTo(plate.center());
        h.assertTrue(distance>22&&distance<24,"real visible surface retains twenty-two-to-twenty-four-meter placement with slab safety setback: "+distance);
        h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"surface placement never removes its real blocker");
        near(h,f.cp(),cp,"surface placement holds instead of debiting total CP");near(h,HighPowerResources.heldAmount(f.p),2,"surface placement creates one fee claim");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void real_retreat_beyond_twenty_four_meter_hard_range_refuses_before_holding_fee(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);double cp=f.cp();
        f.face(f.p.getEyePosition().add(0,0,24));Vec3 center=f.p.getEyePosition().add(0,0,22);f.p.setShiftKeyDown(true);
        h.assertTrue(f.request(LOCATOR,"start",0,1),"real press captures the interior air center");
        f.p.setPos(f.p.position().add(0,0,-2.01));f.p.connection.resetPosition();f.face(target.getBoundingBox().getCenter());
        h.assertTrue(f.p.getEyePosition().distanceTo(center)>24,"actual owner retreat exceeds the unchanged hard center range");
        h.assertFalse(f.request(LOCATOR,"release",0,1),"hard range rejects out-of-range commit without stretching or dragging the center");
        h.assertFalse(ScatterLocatorPlate.active(f.p),"refused initial placement leaves no controller plane");
        near(h,HighPowerResources.heldAmount(f.p),0,"out-of-range release cannot acquire control fee");near(h,f.cp(),cp,"out-of-range release is free");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void real_route_over_forty_meters_is_free_and_preserves_the_held_fee(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,4);f.place(target,1);var plate=ScatterLocatorPlate.plate(f.p);
        h.assertTrue(FoldedMirrorArray.hand(f.p).distanceTo(plate.center())+plate.center().distanceTo(target.getBoundingBox().getCenter())>40,
                "native fixture really exceeds the shared range after its reflection");
        BigDecimal before=hp(target);double cp=f.cp();f.pump();h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"a genuine overrange path cannot buy a borrowed shot");
            exact(h,hp(target),before,"out-of-budget terminal body receives no damage");near(h,f.debit(cp),0,"overrange preflight is free");
            near(h,HighPowerResources.heldAmount(f.p),2,"failed preparation restores the same held fee");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"invalid preflight preserves the exact fixed plane");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=18)
    public static void native_wall_refusal_keeps_hold_and_later_real_retry_commits_once(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);var plate=ScatterLocatorPlate.plate(f.p);
        BlockPos wall=BlockPos.containing(FoldedMirrorArray.hand(f.p).lerp(plate.center(),.4));f.block(wall);
        BigDecimal before=hp(target);double cp=f.cp();f.pump();h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"actual solid on injection leg refuses before payment");
            exact(h,hp(target),before,"native wall is never pierced");near(h,f.debit(cp),0,"blocked preflight spends nothing");
            near(h,HighPowerResources.heldAmount(f.p),2,"failed claim returns to its held state");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"wall refusal leaves plate owned");f.air(wall);
        });
        h.runAtTickTime(7,()->{
            h.assertTrue(f.fire(3),"after genuine empty-attempt interval, cleared path can borrow once");
            h.assertTrue(hp(target).compareTo(before)<0,"retry reaches original target");near(h,f.debit(cp),10,"retry has exactly one combined payment");
            near(h,HighPowerResources.heldAmount(f.p),0,"successful retry closes original claim");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void protected_terminal_is_free_and_cannot_consume_or_replace_the_locator(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);target.getPersistentData().putBoolean("academy:protected",true);
        var plate=ScatterLocatorPlate.plate(f.p);BigDecimal before=hp(target);double cp=f.cp();f.pump();h.runAtTickTime(2,()->{
            h.assertFalse(f.fire(2),"actual protected target is ineligible before combined payment");exact(h,hp(target),before,"protection keeps exact health");
            near(h,f.debit(cp),0,"protected preflight is free");near(h,HighPowerResources.heldAmount(f.p),2,"protected target does not burn control fee");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"protection cannot replace or consume fixed geometry");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_callback_cancellation_keeps_combined_payment_and_retires_before_callbacks(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();boolean[] called={false,false};
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.target==target&&e.skill.equals("meltdowner.folded_mirror_array")){
            called[0]=true;h.assertFalse(ScatterLocatorPlate.active(f.p),"locator retires before arbitrary skill callback");
            near(h,HighPowerResources.heldAmount(f.p),0,"fee is committed before arbitrary skill callback");
        }}@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==f.p){called[1]=true;e.setCanceled(true);}}});
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2)&&called[0]&&called[1],"viable borrow reaches the skill observer and real cancellable native incoming hook");exact(h,hp(target),before,"canceled callback causes no terminal damage");
            near(h,f.debit(cp),10,"callback cancellation never refunds control fee or shot");
            ScatterLocatorPlate.abort(f.p);FoldedMirrorArray.abort(f.p);near(h,f.debit(cp),10,"later cleanup cannot refund paid resources");
            h.assertFalse(f.request(MIRROR,"release",1,2),"canceled paid packet cannot be replayed");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_incoming_mutation_invalidates_whole_borrowed_path_without_refund(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);var plate=ScatterLocatorPlate.plate(f.p);
        BigDecimal before=hp(target);double cp=f.cp();boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==f.p){
            called[0]=true;f.block(BlockPos.containing(FoldedMirrorArray.hand(f.p).lerp(plate.center(),.4)));
        }}});
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2)&&called[0],"borrow reaches actual native incoming callback after payment");
            exact(h,hp(target),before,"new upstream solid invalidates the entire frozen path");near(h,f.debit(cp),10,"native callback miss remains paid");
            h.assertFalse(ScatterLocatorPlate.active(f.p),"invalidated paid emission cannot return its plane");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void actual_callback_exception_spends_borrow_and_releases_processing_for_a_fresh_locator(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();
        RuntimeException sentinel=new IllegalStateException("Declared NEW223 borrowed-shot callback failure");boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.target==target&&e.skill.equals("meltdowner.folded_mirror_array")){
            called[0]=true;throw sentinel;
        }}});
        f.pump();h.runAtTickTime(2,()->{
            boolean thrown=false;
            try{f.fire(2);}catch(RuntimeException failure){if(failure!=sentinel)throw failure;thrown=true;}
            h.assertTrue(thrown&&called[0],"exact sentinel propagates from the actual paid skill callback");
            exact(h,hp(target),before,"pre-hurt exception cannot manufacture terminal damage");near(h,f.debit(cp),10,"callback exception does not refund the combined fee and shot");
            near(h,HighPowerResources.heldAmount(f.p),0,"exception leaves no retained resource claim");
            h.assertFalse(ScatterLocatorPlate.active(f.p),"paid exception cannot resurrect consumed locator");
            h.assertTrue(FoldedMirrorArray.plates(f.p).isEmpty(),"exception removes temporary NEW221 geometry");
            h.assertFalse(f.request(MIRROR,"release",1,2),"failed callback's release cannot replay its spent opportunity");
        });
        h.runAtTickTime(4,()->{
            f.place(target,3);h.assertTrue(ScatterLocatorPlate.active(f.p),"fresh authentic placement proves no processing lock leaked from exception");
            near(h,HighPowerResources.heldAmount(f.p),2,"new generation can reserve exactly one fresh fee");near(h,f.debit(cp),10,"new fee remains held while original shot remains spent");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_source_replay_and_callback_reentry_cannot_reborrow_a_paid_plate(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);double cp=f.cp();Observation hit=new Observation(f.p,target);f.hook(hit);boolean[] called={false};
        f.hook(new Object(){@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==f.p&&e.target==target&&e.skill.equals("meltdowner.folded_mirror_array")){
            called[0]=true;f.p.setShiftKeyDown(true);
            h.assertFalse(f.request(LOCATOR,"start",0,3),"synchronous NEW223 reentry cannot claim a retired plane");
            f.p.setShiftKeyDown(false);h.assertFalse(f.request(MIRROR,"start",1,3),"synchronous NEW221 reentry cannot mint a second permit");
        }}});
        f.pump();h.runAtTickTime(2,()->{
            h.assertTrue(f.fire(2)&&called[0]&&hit.posts==1&&hit.source!=null,"one real reflected native completion reaches observer");
            BigDecimal after=hp(target);double paid=f.cp();target.invulnerableTime=0;target.hurtTime=0;
            h.assertFalse(target.hurt(hit.source,hit.amount),"captured successful native damage source is closed after its permit");
            exact(h,hp(target),after,"source replay cannot debit target health twice");near(h,f.cp(),paid,"source replay cannot debit or return CP");
            near(h,f.debit(cp),10,"reentry cannot alter the one combined payment");
            h.assertTrue(hit.posts==1,"source replay does not reach another native completion");
            h.assertFalse(HighPowerCombat.attack(f.p,"meltdowner.folded_mirror_array",target,BigDecimal.ONE),"skill string cannot fabricate a borrowed emission");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void normal_preview_never_fires_and_long_normal_release_returns_only_held_fee(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);BigDecimal before=hp(target);double cp=f.cp();var plate=ScatterLocatorPlate.plate(f.p);f.pump();
        h.runAtTickTime(2,()->{
            f.p.setShiftKeyDown(false);h.assertTrue(f.tap(LOCATOR,0,2),"ordinary NEW223 short input performs harmless preview");
            exact(h,hp(target),before,"NEW223 preview is not a damage skill");near(h,f.debit(cp),0,"preview cannot debit CP");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"preview retains exact plane");
        });
        h.runAtTickTime(4,()->{f.p.setShiftKeyDown(false);h.assertTrue(f.request(LOCATOR,"start",0,3),"normal recall hold starts");f.p.setShiftKeyDown(true);});
        h.runAtTickTime(17,()->{
            h.assertTrue(f.request(LOCATOR,"release",0,3),"press mode stays normal despite release-time crouch");
            h.assertFalse(ScatterLocatorPlate.active(f.p),"long normal release retires locator");near(h,f.debit(cp),0,"recall returns availability without inventing total CP");
            near(h,HighPowerResources.heldAmount(f.p),0,"recall releases control hold");near(h,HighPowerResources.availableCp(f.p),f.cp(),"released fee is spendable again");
            exact(h,hp(target),before,"recall never damages target");near(h,AbilityStorage.get(f.p).exp(ScatterLocatorPlate.ID),0,"placement preview and recall award no mastery");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=515)
    public static void edit_timeout_and_later_edit_never_refresh_the_original_four_hundred_tick_lifetime(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);
        f.face(f.p.getEyePosition().add(0,0,24));f.p.setShiftKeyDown(true);
        h.assertTrue(f.request(LOCATOR,"start",0,1),"real air press pins the twenty-two-meter center for strict-boundary fixture");
        f.p.setPos(f.p.position().add(0,0,-2));f.p.connection.resetPosition();f.face(target.getBoundingBox().getCenter());
        h.assertTrue(f.request(LOCATOR,"release",0,1),"actual two-meter retreat commits the unchanged pinned center at exactly twenty-four meters");f.p.setShiftKeyDown(false);
        var plate=ScatterLocatorPlate.plate(f.p);Vec3 placementEye=f.p.getEyePosition();near(h,placementEye.distanceTo(plate.center()),24,"boundary fixture uses the true hard center range");
        long placed=ScatterLocatorPlate.placedAt(f.p);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{f.face(plate.center());f.p.setShiftKeyDown(true);
            String observed=placementObservation(f.p,plate)+" look="+f.p.getLookAngle()+" placedAt="+placed+" now="+f.p.serverLevel().getGameTime()+" persistent="+f.p.getPersistentData().getCompound("academy:scatter_locator_plate");
            System.out.println("NEW223_EDIT_TICK2_BEFORE "+observed);
            boolean accepted=f.request(LOCATOR,"start",0,2);
            System.out.println("NEW223_EDIT_TICK2_AFTER accepted="+accepted+" "+placementObservation(f.p,plate));
            h.assertTrue(accepted,"real edit selects own existing plane: "+observed);
        });
        h.runAtTickTime(104,()->{
            h.assertFalse(ScatterLocatorPlate.holding(f.p),"unreleased edit expires at its hundred-tick bound");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"expired ghost never changes committed orientation");near(h,HighPowerResources.heldAmount(f.p),2,"edit timeout retains original fee once");
            f.face(plate.center());String observed=placementObservation(f.p,plate);
            System.out.println("NEW223_24M_EDIT_CROUCH "+observed);
            h.assertTrue(f.p.getEyePosition().distanceTo(plate.center())>24,"native crouch moves eye beyond exact center range: "+observed);
            h.assertFalse(f.request(LOCATOR,"start",0,3),"beyond-range center is refused even if its finite slab is contacted");
            near(h,HighPowerResources.heldAmount(f.p),2,"strict range refusal preserves the existing held fee");
            // Release the physical crouch and let native player ticks return to the placement pose.
            // The later edit tests immutable lifetime from the original valid observation point.
            f.p.setShiftKeyDown(false);
        });
        h.runAtTickTime(120,()->{
            h.assertTrue(f.p.getEyePosition().equals(placementEye),"native uncrouch restores original placement eye without moving or shortening range");
            f.face(plate.center());System.out.println("NEW223_24M_EDIT_STANDING "+placementObservation(f.p,plate));
            f.p.setShiftKeyDown(true);h.assertTrue(f.request(LOCATOR,"start",0,4),"later edit selects same center from original valid pose");
            f.face(target.getBoundingBox().getCenter());h.assertTrue(f.request(LOCATOR,"release",0,4),"later orientation edit commits");f.p.setShiftKeyDown(false);
            h.assertTrue(ScatterLocatorPlate.placedAt(f.p)==placed,"edit never refreshes first-placement time");near(h,HighPowerResources.heldAmount(f.p),2,"editing never stacks a second fee");
        });
        h.runAtTickTime(ScatterLocatorRules.LIFETIME-2,()->h.assertTrue(ScatterLocatorPlate.active(f.p),"original plane remains valid immediately before lifetime boundary"));
        h.runAtTickTime(ScatterLocatorRules.LIFETIME+5,()->{
            h.assertFalse(ScatterLocatorPlate.active(f.p),"immutable lifetime deadline retires edited plane");near(h,HighPowerResources.heldAmount(f.p),0,"expiry releases its fee");
            near(h,f.debit(cp),0,"all editing and expiry leave total CP conserved");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void exact_center_edit_endpoint_still_refuses_wall_nextup_and_invalid_direction(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);
        f.face(f.p.getEyePosition().add(0,0,24));f.p.setShiftKeyDown(true);
        h.assertTrue(f.request(LOCATOR,"start",0,1),"pin the unchanged22m air center");
        f.p.setPos(f.p.position().add(0,0,-2));f.p.connection.resetPosition();f.face(target.getBoundingBox().getCenter());
        h.assertTrue(f.request(LOCATOR,"release",0,1),"commit exact24m center");f.p.setShiftKeyDown(false);
        var plate=ScatterLocatorPlate.plate(f.p);Vec3 original=f.p.position();double cp=f.cp();
        // Deterministic one-ULP quotient witness exercises the real read-only native selector;
        // it does not create a controller plane, reserve or emission permission.
        var eye=f.p.getEyePosition();var center=eye.add(0,0,24);
        var witness=new FoldedMirrorGeometry.Plate(0,new FoldedMirrorGeometry.Vector(center.x,center.y,center.z),
                new FoldedMirrorGeometry.Vector(-0.5558760818982492,0.06762226384557349,0.8285101152103398));
        f.face(center);
        h.assertTrue(FoldedMirrorGeometry.contact(witness,new FoldedMirrorGeometry.Vector(eye.x,eye.y,eye.z),
                new FoldedMirrorGeometry.Vector(0,0,1),24).isEmpty(),"unchanged firing geometry retains the original oneULP witness");
        try {
            var select=FoldedMirrorArray.class.getDeclaredMethod("locatorCenter",ServerPlayer.class,FoldedMirrorGeometry.Plate.class);select.setAccessible(true);
            h.assertTrue(center.equals(select.invoke(null,f.p,witness)),"only native223 editing selects exact center after original loaded and visibility checks");
        } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        BlockPos wall=BlockPos.containing(f.p.getEyePosition().add(0,0,12));f.pump();
        h.runAtTickTime(2,()->{
            f.block(wall);f.face(plate.center());f.p.setShiftKeyDown(true);
            h.assertFalse(f.request(LOCATOR,"start",0,2),"exact-center recovery cannot skip a native solid blocker");f.p.setShiftKeyDown(false);
            near(h,f.debit(cp),0,"wall refusal free");near(h,HighPowerResources.heldAmount(f.p),2,"wall refusal retains single hold");
        });
        h.runAtTickTime(4,()->{
            f.air(wall);f.p.setPos(original.x,original.y,Math.nextDown(original.z));f.p.connection.resetPosition();f.face(plate.center());f.p.setShiftKeyDown(true);
            h.assertTrue(f.p.getEyePosition().distanceToSqr(plate.center())>24*24,"one representable retreat is really outside24m");
            h.assertFalse(f.request(LOCATOR,"start",0,3),"nextUp range cannot use exact-center recovery");f.p.setShiftKeyDown(false);
        });
        h.runAtTickTime(6,()->{
            f.p.setPos(original);f.p.connection.resetPosition();float before=f.p.getXRot();f.p.setXRot(Float.NaN);
            h.assertTrue(f.p.getXRot()==before,"native setter rejects NaN rather than creating an invalid-direction fixture");
            f.face(f.p.getEyePosition().add(0,0,-24));f.p.setShiftKeyDown(true);
            h.assertFalse(f.request(LOCATOR,"start",0,4),"opposite real direction cannot use exact-center recovery");f.p.setShiftKeyDown(false);f.face(plate.center());
        });
        h.runAtTickTime(8,()->{
            f.face(plate.center());f.p.setShiftKeyDown(true);
            h.assertTrue(f.request(LOCATOR,"start",0,5),"restored exact center can be selected without widening range");
            f.face(target.getBoundingBox().getCenter());h.assertTrue(f.request(LOCATOR,"release",0,5),"valid orientation edit still uses ordinary native checks");f.p.setShiftKeyDown(false);
            near(h,f.debit(cp),0,"all edits and refusals conserve totalCP");near(h,HighPowerResources.heldAmount(f.p),2,"only original control fee remains");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void pending_abort_wrong_actor_and_retired_epoch_cannot_create_or_release_new_hold(GameTestHelper h) {
        Fixture f=new Fixture(h);double cp=f.cp();String pending=f.wire(0,1);
        h.assertTrue(f.raw(LOCATOR+"abort",pending),"authenticated pending cancel retires delayed physical edge");
        h.assertFalse(f.raw(LOCATOR+"start",pending),"canceled delayed press cannot create a locator");
        String wrong=new HighPowerInput.Input(0,2,f.p.getId()+1,f.epoch()).wire();h.assertFalse(f.raw(LOCATOR+"start",wrong),"foreign actor cannot reserve control CP");
        String stale=f.wire(0,2);LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();h.assertFalse(f.raw(LOCATOR+"start",stale),"retired owner epoch cannot reserve control CP");
        Zombie target=f.target(6,20);f.place(target,3);var plate=ScatterLocatorPlate.plate(f.p);
        h.assertFalse(f.raw(LOCATOR+"abort",stale),"old epoch cannot release a newer owner's held fee");
        h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"newest authentic plane retains its ownership");
        h.assertFalse(f.request(LOCATOR,"release",0,3),"duplicate placement release cannot reserve a second fee");
        near(h,HighPowerResources.heldAmount(f.p),2,"all replay attempts leave one hold");near(h,f.cp(),cp,"identity and replay guards never debit total CP");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void lifecycle_invalidation_releases_hold_once_without_crediting_total_cp(GameTestHelper h) {
        for(int mode=0;mode<9;mode++)try(Fixture f=new Fixture(h)){
            Zombie target=f.target(6,20);f.place(target,1);double cp=f.cp();
            switch(mode){
                case 0->AbilityStorage.get(f.p).presets.edit(0,0,"",id->true);
                case 1->AbilityStorage.get(f.p).activated=false;
                case 2->AbilityStorage.get(f.p).interfering=true;
                case 3->f.p.setGameMode(GameType.SPECTATOR);
                case 4->f.p.setHealth(0);
                case 5->{LegacySingleKeyOwnerEpoch.forget(f.p);f.epoch();}
                case 6->{AbilityStorage.save(f.p);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);}
                case 7->NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
                default->NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(f.p,Level.OVERWORLD,Level.NETHER));
            }
            ScatterLocatorPlate.tick(f.p);
            h.assertFalse(ScatterLocatorPlate.active(f.p),"lifecycle boundary retires plane case "+mode);
            near(h,HighPowerResources.heldAmount(f.p),0,"lifecycle releases frozen control fee case "+mode);
            near(h,f.cp(),cp,"lifecycle never credits the total balance case "+mode);
            ScatterLocatorPlate.abort(f.p);ScatterLocatorPlate.remove(f.p);ScatterLocatorPlate.tick(f.p);
            near(h,f.cp(),cp,"repeated release and cleanup remain idempotent case "+mode);
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void genuine_growth_invalidation_releases_frozen_fee_without_resampling_or_refill(GameTestHelper h) {
        for(boolean largerEarnedCapacity:List.of(false,true))try(Fixture f=new Fixture(h,largerEarnedCapacity?5:2,2)){
            Zombie target=f.target(6,20);f.place(target,1);var growth=HighPowerGrowth.values(f.p);double cp=f.cp();
            near(h,HighPowerResources.heldAmount(f.p),2,"control fee uses active q2 even when earned Q5");
            AbilityStorage.get(f.p).addExperience(DualPoint.ID,.02);var changed=HighPowerGrowth.values(f.p);
            h.assertTrue(changed.position().compareTo(growth.position())>0,"real native XP receipt advances active q");
            if(largerEarnedCapacity)exact(h,changed.earnedPosition(),growth.earnedPosition(),"higher earned Q remains fixed while q advances");
            else h.assertTrue(changed.earnedPosition().compareTo(growth.earnedPosition())>0,"genuine growth also advances the initially equal earned Q");
            ScatterLocatorPlate.tick(f.p);h.assertFalse(ScatterLocatorPlate.active(f.p),"frozen growth snapshot invalidates locator");
            near(h,HighPowerResources.heldAmount(f.p),0,"growth releases old fee instead of replacing it at a new price");
            near(h,f.cp(),cp,"growth never refunds or refills total CP");
        }h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_save_reload_restores_total_cp_and_never_restores_transient_plane_or_hold(GameTestHelper h) {
        Fixture f=new Fixture(h);Zombie target=f.target(6,20);f.place(target,1);double cp=f.cp();String old=f.wire(0,1);
        AbilityStorage.save(f.p);CompoundTag saved=new CompoundTag();f.p.saveWithoutId(saved);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));AbilityStorage.remove(f.p);LegacySingleKeyOwnerEpoch.forget(f.p);
        f.p.load(saved);f.p.connection.resetPosition();f.epoch();
        h.assertFalse(ScatterLocatorPlate.active(f.p),"native player reload never restores ephemeral locator geometry");
        near(h,HighPowerResources.heldAmount(f.p),0,"native player reload never restores in-memory fee ownership");
        near(h,f.cp(),cp,"save/load preserves total CP including the unspent held amount");
        near(h,HighPowerResources.availableCp(f.p),cp,"released original amount becomes spendable after reload");
        h.assertFalse(f.raw(LOCATOR+"release",old),"pre-relog wire cannot settle or release anything in the new session");
        ScatterLocatorPlate.remove(f.p);near(h,f.cp(),cp,"repeated rejoin cleanup does not add the fee twice");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void active_locator_refuses_paid_221_and_253_geometry_without_spending_its_hold(GameTestHelper h) {
        Fixture f=new Fixture(h,5,5);Zombie target=f.target(6,20);f.place(target,1);var plate=ScatterLocatorPlate.plate(f.p);double cp=f.cp();f.pump();
        h.runAtTickTime(2,()->{
            f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(true);
            boolean mirror=f.request(MIRROR,"start",1,2);f.face(target.getBoundingBox().getCenter());
            h.assertFalse(mirror&&f.request(MIRROR,"release",1,2),"ordinary paid NEW221 array cannot coexist with locator hold");
            f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(true);
            h.assertFalse(f.tap(RELAY,2,3),"ordinary paid NEW253 node cannot coexist with locator hold");f.p.setShiftKeyDown(false);
            h.assertTrue(FoldedMirrorArray.plates(f.p).isEmpty()&&RemoteScatterRelay.plates(f.p).isEmpty(),"both foreign controller geometries remain empty");
            h.assertTrue(ScatterLocatorPlate.plate(f.p).equals(plate),"failed other placement never replaces locator");
            near(h,f.debit(cp),0,"mutual-exclusion refusal spends no placement CP");near(h,HighPowerResources.heldAmount(f.p),.02,"original q5 fee stays held exactly once");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void existing_paid_221_or_253_refuses_locator_and_old_cleanup_never_refunds(GameTestHelper h) {
        for(boolean relay:List.of(false,true))try(Fixture f=new Fixture(h,5,5)){
            Zombie target=f.target(6,8);double cp=f.cp();String prefix=relay?RELAY:MIRROR;int slot=relay?2:1;
            f.face(f.p.getEyePosition().add(0,0,8));f.p.setShiftKeyDown(true);
            h.assertTrue(f.request(prefix,"start",slot,1),"existing controller receives authentic placement press");
            if(!relay)f.face(target.getBoundingBox().getCenter());
            h.assertTrue(f.request(prefix,"release",slot,1),"existing paid geometry commits normally");
            double paid=f.cp();near(h,cp-paid,relay?.01:.02,"unchanged original placement price is actually debited");
            f.face(f.p.getEyePosition().add(0,0,24));f.p.setShiftKeyDown(true);
            boolean started=f.request(LOCATOR,"start",0,2);f.face(target.getBoundingBox().getCenter());
            h.assertFalse(started&&f.request(LOCATOR,"release",0,2),"existing paid geometry refuses NEW223 placement");
            near(h,HighPowerResources.heldAmount(f.p),0,"refused locator creates no resource claim");near(h,f.cp(),paid,"mutual exclusion cannot refund old paid placement");
            if(relay)RemoteScatterRelay.abort(f.p);else FoldedMirrorArray.abort(f.p);
            ScatterLocatorPlate.remove(f.p);near(h,f.cp(),paid,"all cleanup keeps old NEW221/NEW253 placement spent");
        }h.succeed();
    }

    private static BigDecimal hp(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e));}
    /** Read-only diagnostics of actual native placement guards; never grants geometry or authority. */
    private static String placementObservation(ServerPlayer p,FoldedMirrorArray.PlateVisual visual){
        var center=visual.center();var normal=visual.normal();var eye=p.getEyePosition();var look=p.getLookAngle().normalize();
        var plate=new FoldedMirrorGeometry.Plate(0,new FoldedMirrorGeometry.Vector(center.x,center.y,center.z),new FoldedMirrorGeometry.Vector(normal.x,normal.y,normal.z));
        var contact=FoldedMirrorGeometry.nearestContact(List.of(plate),new FoldedMirrorGeometry.Vector(eye.x,eye.y,eye.z),new FoldedMirrorGeometry.Vector(look.x,look.y,look.z),24,-1);
        try{
            var trace=FoldedMirrorArray.class.getDeclaredMethod("nativeTrace",ServerPlayer.class,Vec3.class,Vec3.class);trace.setAccessible(true);
            return "position="+p.position()+" pose="+p.getPose()+" eye="+eye+" center="+center+" normal="+normal+" distance="+eye.distanceTo(center)+" distanceSquared="+eye.distanceToSqr(center)+" planeDistance="+(center.subtract(eye).dot(normal)/look.dot(normal))+" planeDistanceHex="+Double.toHexString(center.subtract(eye).dot(normal)/look.dot(normal))+" planeContact="+contact+" nativeTrace="+trace.invoke(null,p,eye,center);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void exact(GameTestHelper h,BigDecimal actual,BigDecimal expected,String why){h.assertTrue(actual.compareTo(expected)==0,why+": "+actual+" != "+expected);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-7,why+": "+actual+" != "+expected);}
    private static final class Observation {
        final ServerPlayer owner;final LivingEntity target;DamageSource source;float amount;int posts;
        Observation(ServerPlayer owner,LivingEntity target){this.owner=owner;this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==owner){source=e.getSource();amount=e.getAmount();}}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void post(LivingDamageEvent.Post e){if(e.getEntity()==target&&e.getSource().getEntity()==owner)posts++;}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final Set<BlockPos> blocks=new HashSet<>();
        BigDecimal credits=BigDecimal.ZERO;boolean closed,pumping;
        Fixture(GameTestHelper h){this(h,2,2);}
        Fixture(GameTestHelper h,int earnedLevel,int activeLevel){
            this.h=h;var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"SL"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);
            channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.getAbilities().instabuild=false;
            Vec3 at=h.absoluteVec(new Vec3(12.5,18,5.5));p.moveTo(at.x,at.y,at.z,0,0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            h.assertTrue(HighPowerCombat.enroll(p,earnedLevel),"real authenticated enrolled caster");var state=AbilityStorage.get(p);
            state.selectCategory("meltdowner");state.setLevel(earnedLevel);HighPowerGrowth.values(p);state.setLevel(activeLevel);
            state.learn(DualPoint.ID);state.learn(FoldedMirrorArray.ID);state.learn(ScatterLocatorPlate.ID);if(activeLevel==5)state.learn(RemoteScatterRelay.ID);
            state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ScatterLocatorPlate.ID,id->true)&&state.presets.edit(0,1,FoldedMirrorArray.ID,id->true),"actual learned locator and mirror bindings");
            if(activeLevel==5)h.assertTrue(state.presets.edit(0,2,RemoteScatterRelay.ID,id->true),"actual learned relay binding");
            HighPowerResources.initializeBudget(p);epoch();
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie target(double x,double z){
            Zombie target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setSilent(true);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(p.position().add(x,0,z));h.getLevel().addFreshEntity(target);h.assertTrue(HighPowerCombat.enroll(target,5),"native exact durable target");entities.add(target);return target;
        }
        void face(Vec3 point){Vec3 d=point.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
        void place(LivingEntity target,long input){
            face(p.getEyePosition().add(0,0,24));p.setShiftKeyDown(true);h.assertTrue(request(LOCATOR,"start",0,input),"real crouch press pins directly visible locator center");
            face(target.getBoundingBox().getCenter());h.assertTrue(request(LOCATOR,"release",0,input),"real release fixes reflected normal and reserves one fee");p.setShiftKeyDown(false);
        }
        boolean fire(long input){p.setShiftKeyDown(false);return tap(MIRROR,1,input);}
        boolean tap(String prefix,int slot,long input){return request(prefix,"start",slot,input)&&request(prefix,"release",slot,input);}
        boolean request(String prefix,String action,int slot,long input){return raw(prefix+action,wire(slot,input));}
        boolean raw(String action,String value){return AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request(action,value));}
        long epoch(){return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p));}
        String wire(int slot,long input){return new HighPowerInput.Input(slot,input,p.getId(),epoch()).wire();}
        double cp(){return HighPowerResources.cp(p);}
        void tickPlayer(){if(!closed){BigDecimal before=new BigDecimal(cp());p.doTick();credits=credits.add(new BigDecimal(cp()).subtract(before));}}
        void pump(){if(pumping)return;pumping=true;h.onEachTick(this::tickPlayer);}
        double debit(double initial){return new BigDecimal(initial).add(credits).subtract(new BigDecimal(cp())).doubleValue();}
        void block(BlockPos pos){h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());blocks.add(pos);}
        void air(BlockPos pos){h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());blocks.remove(pos);}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        @Override public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);blocks.forEach(pos->h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));
            ScatterLocatorPlate.remove(p);FoldedMirrorArray.remove(p);RemoteScatterRelay.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}
    }
}

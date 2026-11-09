package cn.academy.port.gametest;

import cn.academy.port.highpower.core.PolarityTetherRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.PolarityTether;
import cn.academy.port.highpower.core.PolarityTetherRules.Phase;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.UUID;

/** Native NEW-113 owner, loaded magnetic identity, shared CP and real velocity-write acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerPolarityTetherRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_polarity_tether";
    private HighPowerPolarityTetherRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void tether_minimum_tick_shared_budget_and_replay_fencing(GameTestHelper h) {
        Fixture f=new Fixture(h);
        double cp=f.cp();
        h.assertTrue(f.tap(1),"first authenticated press captures magnetic surface");
        h.assertTrue(PolarityTether.phase(f.p)==Phase.PENDING,"first same tick is unpaid pending anchor");
        PolarityTether.tick(f.p); near(h,f.cp(),cp,"same-tick tick cannot spend");
        h.assertFalse(f.tap(1),"replayed first nonce refused");
        h.assertFalse(f.tap(2),"same-tick fresh nonce cannot start movement");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p);
            h.assertTrue(PolarityTether.phase(f.p)==Phase.ANCHORED,"one native tick commits one controller");
            // The connected actor naturally regenerates CP; payment is independently visible in the shared next clock.
            h.assertTrue(f.cp()<cp-3 && f.cp()>=cp-4,"LV1 four-CP debit shares the prototype pool with native regeneration");
            double paid=f.cp(); PolarityTether.tick(f.p); near(h,f.cp(),paid,"same tick cannot debit twice");
            h.assertFalse(f.tap(2),"consumed rejected nonce cannot replay later");
            h.assertTrue(f.tap(3),"second fresh later press starts pull");
            h.assertTrue(PolarityTether.phase(f.p)==Phase.PULLING,"pull phase");
            near(h,f.cp(),paid,"second tap never double-charges CP");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void tether_upward_impulse_is_bounded_and_never_teleports(GameTestHelper h) {
        Fixture f=new Fixture(h); f.p.setNoGravity(false);
        h.assertTrue(f.tap(1),"valid elevated magnetic anchor");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p); h.assertTrue(f.tap(2),"begin diagonal pull");
            h.runAfterDelay(1,()->{
                f.p.setDeltaMovement(Vec3.ZERO); Vec3 position=f.p.position(); f.p.fallDistance=7;
                PolarityTether.tick(f.p); Vec3 velocity=f.p.getDeltaMovement();
                h.assertTrue(velocity.z>0 && velocity.y>.08,"upward gravity-compensated attraction");
                h.assertTrue(velocity.length()<=.140000001,"gravity plus attraction maximum step");
                h.assertTrue(f.p.position().equals(position),"constraint changes velocity without teleporting");
                near(h,f.p.fallDistance,7,"tether does not erase accumulated fall distance");
                PolarityTether.tick(f.p); h.assertTrue(f.p.getDeltaMovement().equals(velocity),"same native tick never accelerates twice");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void tether_removed_or_replaced_anchor_preserves_external_inertia(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"pending anchor");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p); h.assertTrue(f.tap(2),"pull starts");
            Vec3 impulse=new Vec3(.25,-.3,.2); f.p.setDeltaMovement(impulse); f.p.fallDistance=11;
            h.setBlock(f.anchor,Blocks.AIR); PolarityTether.tick(f.p);
            h.assertFalse(PolarityTether.active(f.p),"anchor removal aborts before any later controller write");
            h.assertTrue(f.p.getDeltaMovement().equals(impulse),"anchor loss preserves true external inertia");
            near(h,f.p.fallDistance,11,"anchor loss preserves fall distance");
            h.setBlock(f.anchor,Blocks.IRON_BLOCK);
            h.assertFalse(f.tap(3),"same-tick recast cannot bypass one-tick paid-tool cooldown");
            h.runAfterDelay(1,()->{
                // Embedded actors do not run ordinary PlayerTick/AbilityGameplay callbacks.
                // Advance the real ability cooldown once after the actual world tick, as that callback does.
                AbilityStorage.get(f.p).tickCooldowns();
                h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(PolarityTether.ID,0)==0,"one simulated owner tick retires the one-tick cooldown");
                h.assertTrue(f.tap(4),"fresh cast after cooldown and destroyed anchor; "+f.diagnostic());
                h.setBlock(f.anchor,Blocks.LODESTONE); Vec3 before=f.p.getDeltaMovement(); PolarityTether.tick(f.p);
                h.assertFalse(PolarityTether.active(f.p),"another magnetic block cannot silently replace captured anchor identity");
                h.assertTrue(f.p.getDeltaMovement().equals(before),"replacement abort preserves velocity");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void tether_swept_collision_aborts_without_erasing_inertia(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"anchor starts");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p); h.assertTrue(f.tap(2),"pull starts");
            // Feet obstruction does not hide the eye-level magnetic anchor.
            h.setBlock(new BlockPos(5,3,5),Blocks.STONE);
            h.runAfterDelay(1,()->{
                Vec3 impulse=new Vec3(0,0,.4); f.p.setDeltaMovement(impulse); Vec3 position=f.p.position();
                PolarityTether.tick(f.p);
                h.assertFalse(PolarityTether.active(f.p),"whole swept body collision aborts controller");
                h.assertTrue(f.p.getDeltaMovement().equals(impulse)&&f.p.position().equals(position),"collision preflight neither teleports nor masks inertia");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void tether_explicit_third_tap_brakes_but_external_excess_speed_is_preserved(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"anchor starts");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p); h.assertTrue(f.tap(2),"pull starts");
            h.runAfterDelay(1,()->{
                h.assertTrue(f.tap(3),"third tap selects intentional braking");
                f.p.setDeltaMovement(.5,0,0); PolarityTether.tick(f.p);
                near(h,f.p.getDeltaMovement().x,.32,"brake changes real velocity by at most .18");
                h.assertTrue(PolarityTether.phase(f.p)==Phase.BRAKING,"brake controller persists until bounded stop");
                h.runAfterDelay(1,()->{
                    Vec3 knockback=new Vec3(1.2,-.2,0); f.p.setDeltaMovement(knockback); PolarityTether.tick(f.p);
                    h.assertFalse(PolarityTether.active(f.p),"unreviewed external speed retires controller");
                    h.assertTrue(f.p.getDeltaMovement().equals(knockback),"overspeed cancellation preserves knockback");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void tether_permissions_mapping_uuid_and_unlearned_gates(GameTestHelper h) {
        Fixture f=new Fixture(h); long epoch=f.epoch();
        h.assertFalse(PolarityTether.tap(f.p,new HighPowerInput.Input(0,1,f.p.getId()+1,epoch).wire()),"forged actor refused");
        h.assertFalse(PolarityTether.tap(f.p,new HighPowerInput.Input(0,1,f.p.getId(),epoch+1).wire()),"forged owner epoch refused");
        h.assertFalse(PolarityTether.tap(f.p,new HighPowerInput.Input(1,1,f.p.getId(),epoch).wire()),"unmapped slot refused");
        h.setBlock(f.anchor,Blocks.STONE); h.assertFalse(f.tap(2),"nonmagnetic surface refused");
        h.setBlock(f.anchor,Blocks.IRON_BLOCK); h.assertTrue(f.tap(3),"magnetic loaded surface admitted");
        Vec3 inertia=new Vec3(.1,-.2,.15); f.p.setDeltaMovement(inertia); double cp=f.cp();
        AbilityStorage.get(f.p).presets.switchTo(1); PolarityTether.tick(f.p);
        h.assertFalse(PolarityTether.active(f.p),"actual preset revision terminates pending controller");
        h.assertTrue(f.p.getDeltaMovement().equals(inertia),"mapping invalidation never brakes"); near(h,f.cp(),cp,"invalidated pending cast spends no CP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void tether_insufficient_shared_cp_and_real_level_zero_never_commit(GameTestHelper h) {
        Fixture f=new Fixture(h); f.cp(0);
        h.assertTrue(f.tap(1),"unpaid anchor may preview before insufficient resource refusal");
        h.runAfterDelay(1,()->{
            PolarityTether.tick(f.p); h.assertFalse(PolarityTether.active(f.p),"insufficient CP discards pending cast");
            h.assertTrue(f.cp()>=0&&f.cp()<1,"resource failure never makes CP negative");
            f.cp(120); AbilityStorage.get(f.p).setLevel(0);
            h.assertFalse(f.tap(2),"real category LV0 cannot use old enrolled ledger LV1"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void tether_bounded_focus_abort_fences_pending_and_newer_contexts(GameTestHelper h) {
        Fixture f=new Fixture(h);
        String first=new HighPowerInput.Input(0,1,f.p.getId(),f.epoch()).wire();
        h.assertFalse(PolarityTether.abort(f.p,first),"abort before delayed start retires watermark without inventing context");
        h.assertFalse(f.tap(1),"retired delayed initial tap cannot resurrect");
        h.assertTrue(f.tap(2),"fresh controller admitted");
        h.assertFalse(PolarityTether.abort(f.p,first),"stale bound cannot terminate newer controller");
        h.assertTrue(PolarityTether.active(f.p),"newer context survives stale abort");
        Vec3 impulse=new Vec3(.2,-.3,.1);f.p.setDeltaMovement(impulse);f.p.fallDistance=9;
        h.assertTrue(PolarityTether.abort(f.p,new HighPowerInput.Input(0,2,f.p.getId(),f.epoch()).wire()),"matching focus bound cancels current controller");
        h.assertFalse(PolarityTether.active(f.p),"focus cancellation removes one controller");
        h.assertTrue(f.p.getDeltaMovement().equals(impulse),"focus cancellation preserves physical inertia");
        near(h,f.p.fallDistance,9,"focus cancel preserves fall distance");
        h.assertFalse(f.tap(2),"cancelled start nonce remains terminal");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=140)
    public static void tether_controller_has_finite_native_lifetime(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"pending starts");
        h.runAfterDelay(PolarityTetherRules.MAX_TICKS+1,()->{
            Vec3 inertia=new Vec3(.1,-.1,0); f.p.setDeltaMovement(inertia); PolarityTether.tick(f.p);
            h.assertFalse(PolarityTether.active(f.p),"single controller expires by its native tick deadline");
            h.assertTrue(f.p.getDeltaMovement().equals(inertia),"expiry preserves native inertia"); h.succeed();
        });
    }

    private static void near(GameTestHelper h,double actual,double expected,String message) { h.assertTrue(Math.abs(actual-expected)<1e-8,message+": "+actual+" != "+expected); }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel;
        final BlockPos anchor=new BlockPos(5,5,10); boolean closed;
        Fixture(GameTestHelper h) {
            this.h=h; h.setBlock(anchor,Blocks.IRON_BLOCK);
            var server=h.getLevel().getServer();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPTetherProbe"),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND); channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);
            Vec3 at=h.absoluteVec(new Vec3(5.5,3,4.5)); p.moveTo(at.x,at.y,at.z,0,0); p.setNoGravity(true); p.getAbilities().instabuild=false;
            h.assertTrue(HighPowerCombat.enroll(p,1),"approved vanilla LV1 ledger");
            var state=AbilityStorage.get(p); state.selectCategory("electromaster"); state.setLevel(1);
            state.learn("forked_arc"); state.learn(PolarityTether.ID); state.activated=true; state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,PolarityTether.ID,id->id.equals(PolarityTether.ID)),"learned real preset binding");
            cp(120); epoch(); aim();
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info,GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info,GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info,GameTestInfo next,GameTestRunner runner) { close(); }
            });
        }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p)); }
        boolean tap(long input) { return PolarityTether.tap(p,new HighPowerInput.Input(0,input,p.getId(),epoch()).wire()); }
        void aim() {
            Vec3 point=h.absoluteVec(new Vec3(5.5,5.5,10)), direction=point.subtract(p.getEyePosition());
            p.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z))); p.setYHeadRot(p.getYRot());
            p.setXRot((float)Math.toDegrees(Math.atan2(-direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));
        }
        double cp() { return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp"); }
        void cp(double value) { CompoundTag tag=p.getPersistentData().getCompound("academy:electromaster_prototype"); tag.putDouble("cp",value); p.getPersistentData().put("academy:electromaster_prototype",tag); }
        String diagnostic() {
            var s=AbilityStorage.get(p);var from=p.getEyePosition();
            var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(from,from.add(p.getLookAngle().scale(PolarityTetherRules.RANGE)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            return "canUse="+s.canUse(PolarityTether.ID)+", cooldown="+s.cooldowns.get(PolarityTether.ID)
                    +", ready="+cn.academy.port.highpower.ElectromasterPrototype.ready(p)+", active="+s.activated+", overload="+s.overloadFine
                    +", slot="+s.presets.currentSkill(0)+", pos="+p.position()+", velocity="+p.getDeltaMovement()
                    +", anchor="+p.serverLevel().getBlockState(h.absolutePos(anchor))+", hit="+hit.getType()+"/"+hit.getBlockPos()
                    +", epoch="+epoch()+", cp="+cp()+", unsupported="+HighPowerCombat.unsupportedReason(p);
        }
        void close() { if(closed)return;closed=true; PolarityTether.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);channel.finishAndReleaseAll(); }
    }
}

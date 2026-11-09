package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Dedicated native fixtures; positive cases explicitly model integrated admission through a private seam.
 * They do not establish actual LocalPlayer gravity, rendering, or integrated play acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerBeamStepRuntimeTests {
    private static final String TEMPLATE="highpower_empty", BATCH="highpower_beam_step", QUOTA="academy:beam_step_grants";
    private HighPowerBeamStepRuntimeTests() {}
    private static void near(GameTestHelper h,double a,double b,String what) { h.assertTrue(Math.abs(a-b)<1e-7,what+": "+a+" != "+b); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_dedicated_rejects_before_payment(GameTestHelper h) {
        Fixture f=new Fixture(h); double cp=f.cp();
        h.assertFalse(BeamStep.tap(f.p,f.wire(1)),"dedicated runtime has no approved movement bridge");
        near(h,f.cp(),cp,"no unsupported debit");
        h.assertFalse(f.p.getPersistentData().contains(QUOTA),"no unsupported quota");
        h.assertFalse(AbilityStorage.get(f.p).cooldowns.containsKey(BeamStep.ID),"no unsupported cooldown"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_paid_grant_is_single_use_and_native_motion_unchanged(GameTestHelper h) {
        Fixture f=new Fixture(h); Vec3 velocity=new Vec3(.31,-.9,-.24); f.p.setDeltaMovement(velocity); f.p.fallDistance=8;
        double cp=f.cp(); h.assertTrue(f.tap(1),"integrated-contract fixture pays once");
        BeamStep.Grant grant=f.grant(); h.assertTrue(grant!=null,"real paid grant created");
        h.assertTrue(BeamStep.consumeIntegrated(grant,f.p.server),"single-use proof succeeds");
        h.assertFalse(BeamStep.consumeIntegrated(grant,f.p.server),"proof cannot replay");
        near(h,f.cp(),cp-2,"LV3 independent curve cost");
        h.assertTrue(f.p.getDeltaMovement().equals(velocity),"server never overrides current movement"); near(h,f.p.fallDistance,8,"no fall reset");
        near(h,AbilityStorage.get(f.p).experience.getOrDefault(BeamStep.ID,0D),0,"no cast or client proof XP");
        h.assertFalse(f.tap(1),"same input replay denied"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_conflicts_and_upward_cap_reject_before_payment(GameTestHelper h) {
        Fixture f=new Fixture(h); double cp=f.cp();
        f.p.setOnGround(true); h.assertFalse(f.tap(1),"ground rejected"); f.p.setOnGround(false);
        f.p.getAbilities().flying=true; h.assertFalse(f.tap(2),"flight rejected"); f.p.getAbilities().flying=false;
        f.p.setNoGravity(true); h.assertFalse(f.tap(3),"non-native gravity rejected"); f.p.setNoGravity(false);
        f.p.addEffect(new MobEffectInstance(MobEffects.LEVITATION,100)); h.assertFalse(f.tap(4),"levitation rejected"); f.p.removeEffect(MobEffects.LEVITATION);
        f.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,100)); h.assertFalse(f.tap(5),"slowfall rejected"); f.p.removeEffect(MobEffects.SLOW_FALLING);
        f.p.setDeltaMovement(.2,.36,.3); h.assertFalse(f.tap(6),"zero eligible addition not charged");
        f.p.setDeltaMovement(.2,-.1,.3);
        h.setBlock(new BlockPos(5,5,5),Blocks.WATER); h.assertFalse(f.tap(7),"fluid volume rejected before native flag catches up");
        near(h,f.cp(),cp,"all conflicts preserve CP"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_abort_late_grant_and_recast_cannot_clear_physical_fence(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"fixture cast"); BeamStep.Grant g=f.grant(); double cp=f.cp();
        BeamStep.abort(f.p); h.assertFalse(BeamStep.consumeIntegrated(g,f.p.server),"abort revokes immutable grant");
        f.resetCooldown(); h.assertFalse(f.tap(2),"erased UI and resource cooldown cannot erase eight-tick physical fence");
        near(h,f.cp(),cp,"spent abort has no refund and replay has no debit");
        h.assertFalse(BeamStep.abort(f.p,f.wire(3)),"cancel-before-start leaves input tombstone");
        h.assertFalse(f.tap(3),"delayed canceled input stays retired"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_quota_survives_real_nbt_reload_and_malformed_data_fails_closed(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"fixture cast"); BeamStep.abort(f.p); double cp=f.cp();
        AbilityStorage.save(f.p); CompoundTag saved=new CompoundTag(); f.p.saveWithoutId(saved); f.p.load(saved); f.place(); f.resetCooldown();
        h.assertTrue(f.p.getPersistentData().getLongArray(QUOTA).length==1,"native player NBT retained physical receipt");
        h.assertFalse(f.tap(2),"reload and cooldown erase cannot recast inside fence"); near(h,f.cp(),cp,"native reload never refills CP");
        f.p.getPersistentData().putString(QUOTA,"0"); h.assertFalse(f.tap(3),"wrong NBT type fails closed");
        f.p.getPersistentData().putLongArray(QUOTA,new long[]{-1}); h.assertFalse(f.tap(4),"negative quota fails closed");
        f.p.getPersistentData().putLongArray(QUOTA,new long[]{Long.MAX_VALUE}); h.assertFalse(f.tap(5),"future quota fails closed"); h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_expiry_and_preset_revision_revoke_paid_proof(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"fixture cast"); BeamStep.Grant g=f.grant(); double cp=f.cp();
        var state=AbilityStorage.get(f.p); state.presets.switchTo(1); state.presets.switchTo(0); BeamStep.tick(f.p);
        h.assertFalse(BeamStep.consumeIntegrated(g,f.p.server),"changed-away/back preset revision invalidates receipt"); near(h,f.cp(),cp,"late invalidation does not refund");
        h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void beam_step_native_fall_damage_retains_real_health_path(GameTestHelper h) {
        Fixture f=new Fixture(h), control=new Fixture(h);
        // Real newly connected ServerPlayers have sixty native ticks of spawn protection.
        // Only preparation is stationary; restore native gravity before either measured fall.
        f.p.setNoGravity(true); control.p.setNoGravity(true);
        h.runAfterDelay(61,()->{
            f.place(); control.place(); f.p.fallDistance=10; control.p.fallDistance=10;
            h.assertTrue(f.tap(1),"fixture grant after natural spawn-protection expiry");
            h.assertTrue(f.p.fallDistance==10,"grant leaves native accumulated fall distance");
            var before=new java.math.BigDecimal(HighPowerCombat.healthText(f.p));
            var controlBefore=new java.math.BigDecimal(HighPowerCombat.healthText(control.p));
            h.assertTrue(control.p.causeFallDamage(10,1,control.p.damageSources().fall()),"matched control native fall accepted");
            h.assertTrue(f.p.causeFallDamage(10,1,f.p.damageSources().fall()),"paid-grant native fall accepted");
            var loss=before.subtract(new java.math.BigDecimal(HighPowerCombat.healthText(f.p)));
            var controlLoss=controlBefore.subtract(new java.math.BigDecimal(HighPowerCombat.healthText(control.p)));
            h.assertTrue(loss.signum()>0,"native fall source still reaches exact HP; no grace or fall immunity");
            h.assertTrue(loss.compareTo(controlLoss)==0,"paid grant and matched control retain identical native fall loss");
            h.assertFalse(f.p.isNoGravity()||f.p.getAbilities().flying,"grant does not enable gravity or flight bypass"); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_two_tick_server_deadline_retires_unused_grant(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"fixture cast"); BeamStep.Grant g=f.grant();
        h.runAfterDelay(2,()->{ BeamStep.tick(f.p); h.assertFalse(BeamStep.consumeIntegrated(g,f.p.server),"two-tick server deadline cannot be banked"); h.succeed(); });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=45)
    public static void beam_step_rolling_quota_and_persistent_cooldown_are_independent(GameTestHelper h) {
        Fixture f=new Fixture(h);
        h.runAfterDelay(32,()->{
            f.place(); long now=f.p.server.overworld().getGameTime(); double cp=f.cp();
            f.p.getPersistentData().putLongArray(QUOTA,new long[]{now-24,now-16,now-8});
            h.assertFalse(f.tap(1),"three native persisted grants deny fourth despite expired eight-tick fence");
            near(h,f.cp(),cp,"rolling quota rejection is prepayment");
            f.p.getPersistentData().putLongArray(QUOTA,new long[]{now-32,now-16,now-8});
            h.assertTrue(f.tap(2),"oldest grant expires at rolling32 boundary"); BeamStep.abort(f.p);
            f.p.getPersistentData().remove(QUOTA); // Explicit fixture removes physical fence to isolate persisted economic clock.
            AbilityStorage.get(f.p).cooldowns.remove(BeamStep.ID);
            AbilityStorage.get(f.p).cooldownMaxTicks.remove(BeamStep.ID);
            double paid=f.cp(); h.assertFalse(f.tap(3),"UI cooldown deletion cannot bypass persistent resource clock");
            near(h,f.cp(),paid,"cooldown replay never refills or repeats debit"); h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void beam_step_real_preset_request_revokes_proof_in_same_tick(GameTestHelper h) {
        Fixture f=new Fixture(h); h.assertTrue(f.tap(1),"fixture cast"); BeamStep.Grant grant=f.grant();
        h.assertTrue(cn.academy.port.AcademyGameplay.requestFromClient(f.p,new cn.academy.port.AcademyNetwork.Request("preset_switch","1")),"actual preset request accepted");
        h.assertFalse(BeamStep.consumeIntegrated(grant,f.p.server),"published proof revoked synchronously before server tick callback");
        h.assertFalse(BeamStep.active(f.p),"common lifecycle removed exact controller immediately"); h.succeed();
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 start; boolean closed;
        Fixture(GameTestHelper h) {
            this.h=h;
            var server=h.getLevel().getServer(); var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPBeamStep"),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND); channel=new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection,p,cookie); p.setGameMode(GameType.SURVIVAL);
            p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(1));
            start=h.absoluteVec(new Vec3(5.5,5,5.5)); place();
            h.assertTrue(HighPowerCombat.enroll(p,3),"explicit connected LV3 fixture");
            var state=AbilityStorage.get(p); state.selectCategory("meltdowner"); state.setLevel(3); state.learn("thermal_shell"); state.learn(BeamStep.ID); state.activated=true; state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,BeamStep.ID,id->id.equals(BeamStep.ID)),"real learned binding"); HighPowerResources.initializeBudget(p); epoch();
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i,GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i,GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r) { close(); }
            });
        }
        void place() { p.moveTo(start.x,start.y,start.z,0,0); p.setNoGravity(false); p.setOnGround(false); p.setDeltaMovement(0,-.1,0); p.connection.resetPosition(); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0,input,p.getId(),epoch()).wire(); }
        boolean tap(long input) {
            try { Method m=BeamStep.class.getDeclaredMethod("tap",ServerPlayer.class,String.class,boolean.class); m.setAccessible(true); return (boolean)m.invoke(null,p,wire(input),true); }
            catch (InvocationTargetException e) { throw new AssertionError(e.getCause()); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        BeamStep.Grant grant() {
            try { Field f=BeamStep.class.getDeclaredField("CONTEXTS"); f.setAccessible(true); Object c=((Map<?,?>)f.get(null)).get(p.getUUID());
                if(c==null)return null; Field g=c.getClass().getDeclaredField("grant"); g.setAccessible(true); return (BeamStep.Grant)g.get(c);
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        double cp() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"); }
        void resetCooldown() { var s=AbilityStorage.get(p); s.cooldowns.remove(BeamStep.ID); s.cooldownMaxTicks.remove(BeamStep.ID); p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).remove("next_"+BeamStep.ID); }
        void close() { if(closed)return; closed=true; BeamStep.remove(p); AbilityStorage.remove(p); p.server.getPlayerList().remove(p); channel.finishAndReleaseAll(); }
    }
}

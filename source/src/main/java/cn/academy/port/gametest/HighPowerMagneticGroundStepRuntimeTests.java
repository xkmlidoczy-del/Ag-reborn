package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.MagneticGroundStepRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW132 stable native movement packets, geometry, resource and lifecycle acceptance.
 * These embedded actors have no local client; actual client movement is a separate acceptance stage. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticGroundStepRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_magnetic_ground_step";
    private static final String QUOTA = "academy:ground_step_deflection_until";
    private HighPowerMagneticGroundStepRuntimeTests() {}
    private static void near(GameTestHelper h, double a, double b, String message) { h.assertTrue(Math.abs(a - b) < 1e-7, message + ": " + a + " != " + b); }
    private static void exact(GameTestHelper h, BigDecimal a, BigDecimal b, String message) { h.assertTrue(a.compareTo(b) == 0, message + ": " + a + " != " + b); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_authentication_sprint_level_cost_and_replay(GameTestHelper h) {
        Fixture f = new Fixture(h); double cp = f.cp();
        f.p.setSprinting(false); h.assertFalse(f.tap(1), "sprint is required on launch");
        f.p.setSprinting(true); var s = AbilityStorage.get(f.p); s.setLevel(2);
        h.assertFalse(f.tap(2), "real LV2 cannot borrow enrolled LV3 ledger"); s.setLevel(3);
        h.assertFalse(MagneticGroundStep.tap(f.p, new HighPowerInput.Input(0, 3, f.p.getId(), f.epoch() + 1).wire()), "forged epoch rejected");
        h.assertFalse(MagneticGroundStep.tap(f.p, new HighPowerInput.Input(1, 3, f.p.getId(), f.epoch()).wire()), "unmapped slot rejected");
        h.assertTrue(f.tap(4), "authenticated supported LV3 sprint tap accepted");
        near(h, f.cp(), cp - 8, "one continuous LV3 anchor debit");
        h.assertFalse(f.tap(4), "duplicate input refused"); h.assertFalse(f.tap(5), "redundant active tap cannot restart");
        near(h, f.cp(), cp - 8, "no repeated debit");
        h.assertTrue(MagneticGroundStep.abort(f.p, f.wire(5)), "latest rejected input still bounds focus cancellation");
        h.assertFalse(f.tap(6), "native and persisted cooldown prevent immediate recast");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_server_never_replaces_velocity_or_moves_player(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        h.runAfterDelay(1, () -> {
            f.accept(.2); Vec3 inertia = new Vec3(.23, -.11, .04), position = f.p.position();
            f.p.setDeltaMovement(inertia); f.p.fallDistance = 9;
            MagneticGroundStep.tick(f.p);
            h.assertTrue(f.p.position().equals(position), "server authorization does not move or teleport owner");
            h.assertTrue(f.p.getDeltaMovement().equals(inertia), "server preserves full existing velocity instead of sending an absolute replacement");
            near(h, f.p.fallDistance, 9, "server never resets fall distance");
            near(h, MagneticGroundStep.poweredDistance(f.p), .2, "stable native accepted movement is counted");
            MagneticGroundStep.tick(f.p); near(h, MagneticGroundStep.poweredDistance(f.p), .2, "duplicate tick not counted twice");
            MagneticGroundStep.abort(f.p); h.assertTrue(f.p.getDeltaMovement().equals(inertia), "abort preserves inertia"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void ground_step_eight_metres_of_accepted_path_ends_authorization_without_braking(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        class Walk implements Runnable {
            int count;
            public void run() {
                count++; f.accept(.4); Vec3 inertia = new Vec3(.05, -.08, .3); f.p.setDeltaMovement(inertia);
                MagneticGroundStep.tick(f.p);
                double range = MagneticGroundStepRules.range(HighPowerGrowth.values(f.p).position()); int steps = (int) Math.ceil(range / .4 - 1e-9);
                if (count < steps) {
                    h.assertTrue(MagneticGroundStep.active(f.p), "accepted powered path remains below its budget at step " + count + " of " + steps + " range=" + range + " powered=" + MagneticGroundStep.poweredDistance(f.p));
                    near(h, MagneticGroundStep.poweredDistance(f.p), count * .4, "all stable steps counted");
                    h.runAfterDelay(1, () -> this.run());
                } else {
                    h.assertFalse(MagneticGroundStep.active(f.p), "the level-scaled accepted budget terminates authorization");
                    h.assertTrue(f.p.getDeltaMovement().equals(inertia), "budget termination does not freeze native coasting");
                    near(h, f.p.position().distanceTo(f.start), range, "native input packets actually covered the level-scaled budget"); h.succeed();
                }
            }
        }
        h.runAfterDelay(1, new Walk());
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_full_footprint_wall_gap_partial_support_and_loaded_bounds(GameTestHelper h) {
        Fixture f = new Fixture(h); double cp = f.cp();
        BlockPos ahead = new BlockPos(5, 2, 5), below = new BlockPos(5, 1, 5);
        h.setBlock(ahead, Blocks.STONE); h.assertFalse(f.tap(1), "nearby full swept-body wall refused"); h.setBlock(ahead, Blocks.AIR);
        h.setBlock(below, Blocks.AIR); h.assertFalse(f.tap(2), "entire support envelope catches an edge/gap");
        h.setBlock(below, Blocks.STONE_SLAB); h.assertFalse(f.tap(3), "lower half slab cannot masquerade as same-height ground"); h.setBlock(below, Blocks.STONE);
        var border = h.getLevel().getWorldBorder(); double oldX = border.getCenterX(), oldZ = border.getCenterZ(), oldSize = border.getSize();
        try { border.setCenter(f.start.x, f.start.z); border.setSize(1); h.assertFalse(f.tap(4), "loaded body envelope outside border is refused"); }
        finally { border.setCenter(oldX, oldZ); border.setSize(oldSize); }
        f.p.moveTo(f.start.x, h.getLevel().getMaxBuildHeight(), f.start.z, 0, 0); f.p.setOnGround(true);
        h.assertFalse(f.tap(5), "outside build height refused before collision query");
        int far = 1_600_000; h.assertFalse(h.getLevel().hasChunk(far >> 4, far >> 4), "fixture target is unloaded");
        // Moving a tracked native player itself loads its destination chunk. Invoke the exact
        // production loaded-area guard with an unloaded box, without first teleporting a fixture there.
        try {
            var guard = MagneticGroundStep.class.getDeclaredMethod("loadedArea", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.phys.AABB.class);
            guard.setAccessible(true);
            var box = new net.minecraft.world.phys.AABB(far, f.start.y, far, far + 1, f.start.y + 2, far + 1);
            h.assertFalse((Boolean)guard.invoke(null, h.getLevel(), box), "production guard refuses unloaded support envelope");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        h.assertFalse(h.getLevel().hasChunk(far >> 4, far >> 4), "production guard never loads missing chunks");
        f.place(); near(h, f.cp(), cp, "all rejected geometry spends nothing"); h.assertTrue(f.tap(7), "ordinary stone runway remains admitted"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_ground_loss_jump_preset_and_focus_retire_without_motion_write(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        Vec3 impulse = new Vec3(.2, .42, .3); f.p.setDeltaMovement(impulse); f.p.setOnGround(false); f.p.fallDistance = 6;
        MagneticGroundStep.tick(f.p); h.assertFalse(MagneticGroundStep.active(f.p), "jump/airborne state immediately retires");
        h.assertTrue(f.p.getDeltaMovement().equals(impulse), "jump retains exact physical impulse"); near(h, f.p.fallDistance, 6, "air cancellation preserves fall state");
        f.place(); f.resetCooldown(); h.assertTrue(f.tap(2), "fresh fixture recast");
        AbilityStorage.get(f.p).presets.switchTo(1); MagneticGroundStep.tick(f.p);
        h.assertFalse(MagneticGroundStep.active(f.p), "preset identity change invalidates controller");
        AbilityStorage.get(f.p).presets.switchTo(0); f.resetCooldown();
        h.assertFalse(MagneticGroundStep.abort(f.p, f.wire(3)), "abort before delayed start records fence"); h.assertFalse(f.tap(3), "delayed start cannot resurrect canceled input");
        h.assertTrue(f.tap(4), "new input admitted"); h.assertFalse(MagneticGroundStep.abort(f.p, f.wire(3)), "older cancellation cannot clear new owner token");
        MagneticGroundStep.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p)); h.assertFalse(MagneticGroundStep.active(f.p), "logout event removes controller"); h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_real_arrow_turn_pays_exact_fee_once_and_recast_cannot_reset_quota(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        h.runAfterDelay(1, () -> {
            f.accept(.2); Arrow first = f.arrow(.7); Vec3 incoming = first.getDeltaMovement(); BigDecimal shield = HighPowerCombat.shield(f.p);
            BigDecimal fee = f.capacity().multiply(new BigDecimal(".01"));
            MagneticGroundStep.tick(f.p);
            h.assertTrue(MagneticGroundStep.deflected(f.p) && MagneticGroundStep.leased(first), "one genuine native arrow receives owned steering lease");
            h.assertFalse(first.getDeltaMovement().equals(incoming), "actual arrow velocity changed"); near(h, first.getDeltaMovement().length(), incoming.length(), "side turn preserves arrow speed");
            exact(h, HighPowerCombat.shield(f.p), shield.subtract(fee), "one exact current-capacity shield fee");
            h.assertTrue(AbilityStorage.get(f.p).experience.getOrDefault(MagneticGroundStep.ID, 0D) > 0, "only successful deflection practices skill");
            first.discard(); MagneticGroundStep.abort(f.p); f.resetCooldown(); h.assertTrue(f.tap(2), "fixture clears activation CD but not defense quota");
            h.runAfterDelay(1, () -> {
                f.accept(.1); Arrow second = f.arrow(.7); Vec3 before = second.getDeltaMovement(); BigDecimal held = HighPowerCombat.shield(f.p);
                MagneticGroundStep.tick(f.p); h.assertTrue(second.getDeltaMovement().equals(before), "new token cannot acquire another arrow within forty ticks");
                exact(h, HighPowerCombat.shield(f.p), held, "recast quota denial spends no second fee"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_stationary_or_unsupported_arrows_never_spend_or_award_experience(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        h.runAfterDelay(1, () -> {
            Arrow stationary = f.arrow(.7); BigDecimal shield = HighPowerCombat.shield(f.p); Vec3 before = stationary.getDeltaMovement();
            MagneticGroundStep.tick(f.p); h.assertTrue(stationary.getDeltaMovement().equals(before), "no stationary projectile aura without accepted glide motion"); stationary.discard();
            h.runAfterDelay(1, () -> {
                f.accept(.1);
                Arrow own = f.arrow(.7); own.setOwner(f.p);
                Arrow crit = f.arrow(.6); crit.setCritArrow(true);
                Arrow fire = f.arrow(.5); fire.igniteForSeconds(3);
                Arrow stuck = f.arrow(.4); stuck.inGround = true;
                Arrow tipped = new Arrow(h.getLevel(), f.p.getX(), f.p.getY() + 1, f.p.getZ() + .8, new ItemStack(Items.TIPPED_ARROW), null);
                tipped.setDeltaMovement(0, 0, -.3); f.entity(tipped);
                MagneticGroundStep.tick(f.p);
                h.assertFalse(MagneticGroundStep.deflected(f.p), "own, critical, burning, stuck and tipped arrows are outside reviewed ordinary scope");
                exact(h, HighPowerCombat.shield(f.p), shield, "refused arrows debit no shield");
                near(h, AbilityStorage.get(f.p).experience.getOrDefault(MagneticGroundStep.ID, 0D), 0, "no target and refused target grant no practice XP"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_insufficient_shield_is_atomic(GameTestHelper h) {
        Fixture f = new Fixture(h); BigDecimal initial = HighPowerCombat.shield(f.p);
        h.assertTrue(HighPowerCombat.spendShield(f.p, initial), "explicit fixture empties shared shield"); h.assertTrue(f.tap(1), "movement may start without defense resources");
        h.runAfterDelay(1, () -> {
            f.accept(.2); Arrow arrow = f.arrow(.6); Vec3 before = arrow.getDeltaMovement(); MagneticGroundStep.tick(f.p);
            h.assertTrue(arrow.getDeltaMovement().equals(before) && !MagneticGroundStep.leased(arrow), "failed exact payment neither turns nor leases arrow");
            exact(h, HighPowerCombat.shield(f.p), BigDecimal.ZERO, "failed defense never produces negative shield");
            h.assertFalse(MagneticGroundStep.deflected(f.p), "failed transaction never manufactures success visual"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_supported_spectral_arrow_and_tether_exclusion(GameTestHelper h) {
        Fixture f = new Fixture(h);
        h.setBlock(new BlockPos(5, 3, 8), Blocks.IRON_BLOCK);
        var s = AbilityStorage.get(f.p); h.assertTrue(s.presets.edit(0, 1, PolarityTether.ID, HighPowerSkills::contains), "second learned tether slot while preserving existing learned step mapping");
        String tether = new HighPowerInput.Input(1, 1, f.p.getId(), f.epoch()).wire();
        h.assertTrue(PolarityTether.tap(f.p, tether), "pending real tether owns movement");
        h.assertFalse(f.tap(1), "tether-first excludes ground step"); PolarityTether.abort(f.p);
        h.assertTrue(f.tap(2), "ground step starts after tether ends");
        h.assertFalse(PolarityTether.tap(f.p, new HighPowerInput.Input(1, 2, f.p.getId(), f.epoch()).wire()), "ground-step-first excludes tether");
        h.runAfterDelay(1, () -> {
            f.accept(.2); var a = new SpectralArrow(EntityType.SPECTRAL_ARROW, h.getLevel());
            a.setPos(f.p.position().add(0, 1, .65)); a.setDeltaMovement(0, 0, -.35); f.entity(a);
            BigDecimal shield = HighPowerCombat.shield(f.p), fee = f.capacity().multiply(new BigDecimal(".01"));
            MagneticGroundStep.tick(f.p); h.assertTrue(MagneticGroundStep.deflected(f.p), "reviewed vanilla spectral arrow receives one real side turn");
            exact(h, HighPowerCombat.shield(f.p), shield.subtract(fee), "spectral arrow uses same exact fee"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void ground_step_decoy_first_lease_survives_decoy_abort_and_prevents_new_fee(GameTestHelper h) {
        Fixture f = new Fixture(h); AbilityStorage.get(f.p).learn(ElectromasterChargeDecoy.ID);
        // This test template is near minimum build height. A steep full20m ray exits build limits
        // before the decoy's deliberate full-ray loaded-area check; a shallow real ground hit stays inside.
        f.p.setXRot(20);
        h.assertTrue(ElectromasterChargeDecoy.start(f.p), "real decoy previews supported ground");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(f.p), "real decoy commit"); Arrow a = f.arrow(.7);
            ElectromasterChargeDecoy.tick(f.p); h.assertTrue(ElectromasterChargeDecoy.leased(a), "actual decoy steering acquires cross-controller lease");
            ElectromasterChargeDecoy.abort(f.p); f.p.setXRot(0); a.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(f.tap(1), "ground step may begin independently of expired decoy controller");
            h.runAfterDelay(1, () -> {
                f.accept(.1); a.setPos(f.p.position().add(0, 1, .7)); a.setDeltaMovement(0, 0, -.35);
                BigDecimal shield = HighPowerCombat.shield(f.p); Vec3 before = a.getDeltaMovement(); MagneticGroundStep.tick(f.p);
                h.assertTrue(a.getDeltaMovement().equals(before) && !MagneticGroundStep.deflected(f.p), "residual decoy lease prevents a competing step write");
                exact(h, HighPowerCombat.shield(f.p), shield, "decoy-first refusal pays no step shield fee"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void ground_step_saved_player_quota_survives_entity_reload_and_live_map_loss(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        h.runAfterDelay(1, () -> {
            f.accept(.1); Arrow a = f.arrow(.6); MagneticGroundStep.tick(f.p); h.assertTrue(MagneticGroundStep.deflected(f.p), "first real paid turn"); a.discard();
            MagneticGroundStep.abort(f.p); AbilityStorage.save(f.p); CompoundTag saved = new CompoundTag(); f.p.saveWithoutId(saved);
            // Explicit restart fixture: only this actor's live quota is removed. Other tests' maps are untouched.
            try {
                var field = MagneticGroundStep.class.getDeclaredField("QUOTAS"); field.setAccessible(true);
                ((Map<?, ?>)field.get(null)).remove(f.p.getUUID());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            f.p.load(saved); f.p.connection.resetPosition(); f.p.setOnGround(true); f.p.setSprinting(true); f.resetCooldown();
            h.assertTrue(f.p.getPersistentData().getLong(QUOTA) > h.getLevel().getGameTime(), "actual native entity NBT load restored defense timestamp");
            h.assertTrue(f.tap(2), "new controller after loaded fixture cooldown removal");
            h.runAfterDelay(1, () -> {
                f.accept(.1); Arrow b = f.arrow(.6); Vec3 before = b.getDeltaMovement(); BigDecimal shield = HighPowerCombat.shield(f.p); MagneticGroundStep.tick(f.p);
                h.assertTrue(b.getDeltaMovement().equals(before) && !MagneticGroundStep.deflected(f.p), "persisted quota is sufficient with no live quota map");
                exact(h, HighPowerCombat.shield(f.p), shield, "reload cannot buy another defense inside forty ticks"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void ground_step_malformed_quota_fails_closed_and_saved_arrow_lease_survives_reload(GameTestHelper h) {
        Fixture f = new Fixture(h);
        Tag[] bad = {StringTag.valueOf("0"), IntTag.valueOf(0), LongTag.valueOf(-1), LongTag.valueOf(Long.MAX_VALUE)};
        class Check implements Runnable {
            int index;
            public void run() {
                f.p.getPersistentData().put(QUOTA, bad[index]); f.resetCooldown(); h.assertTrue(f.tap(index + 1), "movement launch is independent of defense quota");
                h.runAfterDelay(1, () -> {
                    f.accept(.1); Arrow a = f.arrow(.6); Vec3 before = a.getDeltaMovement(); BigDecimal shield = HighPowerCombat.shield(f.p);
                    MagneticGroundStep.tick(f.p); h.assertTrue(a.getDeltaMovement().equals(before), "malformed/future quota denies arrow " + index);
                    exact(h, HighPowerCombat.shield(f.p), shield, "bad quota never silently normalizes into payment"); a.discard(); MagneticGroundStep.abort(f.p);
                    index++; if (index < bad.length) h.runAfterDelay(1, this);
                    else {
                        long until = h.getLevel().getGameTime() + 40; Arrow saved = f.arrow(.6); saved.getPersistentData().putLong("academy:ground_step_arrow_until", until);
                        CompoundTag nbt = new CompoundTag(); saved.saveWithoutId(nbt); Arrow loaded = new Arrow(EntityType.ARROW, h.getLevel()); loaded.load(nbt);
                        h.assertTrue(MagneticGroundStep.leased(loaded), "actual arrow NBT load retains cross-controller fence without live object identity"); h.succeed();
                    }
                });
            }
        }
        new Check().run();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_bracer_first_lease_prevents_a_second_paid_turn(GameTestHelper h) {
        Fixture f = new Fixture(h); Arrow arrow = f.arrow(.8); arrow.setPos(f.p.getEyePosition().add(0, 0, 1));
        f.p.getInventory().setItem(0, new ItemStack(ElectromasterPrototype.CONTROL.get())); f.p.setShiftKeyDown(true);
        f.p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        ElectromasterPrototype.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(f.p));
        h.assertTrue(ElectromasterPrototype.leased(arrow), "actual paid bracer-first deflection writes its shared lease");
        f.p.stopUsingItem(); f.p.setShiftKeyDown(false); f.p.setSprinting(true); f.p.getInventory().setItem(0, ItemStack.EMPTY);
        arrow.setPos(f.p.position().add(0, 1, .7)); arrow.setDeltaMovement(0, 0, -.35);
        h.assertTrue(f.tap(1), "independent ground step starts after bracer release");
        arrow.setDeltaMovement(Vec3.ZERO); // Keep the already-paid leased fixture arrow from hitting the idle embedded actor before next tick.
        h.runAfterDelay(1, () -> {
            f.accept(.1); arrow.setPos(f.p.position().add(0, 1, .7)); arrow.setDeltaMovement(0, 0, -.35);
            BigDecimal shield = HighPowerCombat.shield(f.p); Vec3 before = arrow.getDeltaMovement(); MagneticGroundStep.tick(f.p);
            h.assertFalse(MagneticGroundStep.deflected(f.p), "leased bracer arrow cannot be steered again by ground step");
            h.assertTrue(arrow.getDeltaMovement().equals(before), "existing bracer lease forbids new write"); exact(h, HighPowerCombat.shield(f.p), shield, "no second fee for leased arrow"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void ground_step_finite_lifetime_and_removed_support_end_authorization(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.tap(1), "start");
        h.runAfterDelay(41, () -> {
            h.assertFalse(MagneticGroundStep.active(f.p), "server-post controller expires without client movement or packet acknowledgments");
            f.resetCooldown(); h.assertTrue(f.tap(2), "new fixture controller"); h.setBlock(new BlockPos(5, 1, 4), Blocks.AIR);
            Vec3 inertia = new Vec3(.2, -.1, .1); f.p.setDeltaMovement(inertia);
            h.runAfterDelay(1, () -> {
                MagneticGroundStep.tick(f.p);
                h.assertFalse(MagneticGroundStep.active(f.p), "removed support terminates before next native authorization");
                h.assertTrue(f.p.getDeltaMovement().equals(inertia), "ground removal retains real motion"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void ground_step_native_grants_keep_two_tick_ttl_unique_sequence_and_terminal_replay_fences(GameTestHelper h){
        Fixture f=new Fixture(h);h.assertTrue(f.tap(1),"real authenticated cast starts");double paid=f.cp();
        var grants=new ArrayList<MagneticGroundStep.Assist>();collectGrants(f,grants);
        h.runAfterDelay(3,()->{
            MagneticGroundStep.tick(f.p);collectGrants(f,grants);
            h.assertTrue(grants.size()>=2,"real server continues distinct outgoing grants across a two-tick client packet gap");
            var first=grants.getFirst();long sequence=0;double remaining=Double.POSITIVE_INFINITY;int ticks=Integer.MAX_VALUE;
            for(var grant:grants){
                h.assertTrue(grant.input()==first.input()&&grant.token()==first.token()&&grant.epoch()==first.epoch()&&grant.origin().equals(first.origin())&&grant.direction().equals(first.direction()),"all refreshes belong to the one immutable actor/input/token/geometry");
                h.assertTrue(grant.expiresAt()-grant.issuedAt()==2&&grant.sequence()>sequence&&grant.remaining()<=remaining&&grant.remainingTicks()<=ticks,"TTL remains exactly two and refreshed sequence/range/lifetime never refill");
                sequence=grant.sequence();remaining=grant.remaining();ticks=grant.remainingTicks();
            }
            h.assertFalse(f.tap(1),"replayed input cannot create a replacement cast");near(h,f.cp(),paid,"a grant gap never repays activation");
            h.assertTrue(MagneticGroundStep.abort(f.p,f.wire(1)),"real authenticated cancellation");grants.clear();collectGrants(f,grants);
            h.assertTrue(grants.stream().anyMatch(g->g.terminal()&&g.remaining()==0&&g.remainingTicks()==0),"real terminal packet carries a permanent zero-authority fence");
            h.assertFalse(f.tap(1),"same canceled input remains rejected");h.assertFalse(MagneticGroundStep.active(f.p),"no replay revives canceled native controller");h.succeed();
        });
    }
    private static void collectGrants(Fixture f,List<MagneticGroundStep.Assist> result){
        f.channel.runPendingTasks();f.channel.flushOutbound();Object packet;
        while((packet=f.channel.readOutbound())!=null){
            if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload&&payload.payload() instanceof MagneticGroundStep.Assist assist)result.add(assist);
            io.netty.util.ReferenceCountUtil.release(packet);
        }
    }

    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 start;
        final List<Entity> entities = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            for (int x = 3; x <= 8; x++) for (int z = 3; z <= 24; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPStepProbe"), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false;
            // Native placeNewPlayer issues exactly the first teleport (id1); emulate its ordinary client acknowledgement.
            p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(1));
            start = h.absoluteVec(new Vec3(5.5, 2, 4.5)); place();
            h.assertTrue(HighPowerCombat.enroll(p, 3), "explicit connected LV3 test enrollment");
            var s = AbilityStorage.get(p); s.selectCategory("electromaster"); s.setLevel(3); s.learn("forked_arc"); s.learn("magnetic_bracer"); s.learn("polarity_tether"); s.learn(MagneticGroundStep.ID); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, MagneticGroundStep.ID, id -> id.equals(MagneticGroundStep.ID)), "real learned preset binding");
            ElectromasterPrototype.initializeBudget(p); epoch();
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void place() { p.moveTo(start.x, start.y, start.z, 0, 0); p.setYHeadRot(0); p.setXRot(0); p.setShiftKeyDown(false); p.setOnGround(true); p.setSprinting(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        boolean tap(long input) { return MagneticGroundStep.tap(p, wire(input)); }
        void accept(double z) {
            Vec3 wanted = p.position().add(0, 0, z); p.connection.resetPosition();
            p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(wanted.x, wanted.y, wanted.z, true));
            h.assertTrue(p.position().distanceToSqr(wanted) < 1e-10, "actual native movement packet accepted fixture position"); p.setSprinting(true);
        }
        Arrow arrow(double forward) { Arrow a = new Arrow(EntityType.ARROW, h.getLevel()); a.setPos(p.position().add(0, 1, forward)); a.setDeltaMovement(0, 0, -.35); entity(a); return a; }
        void entity(Entity e) { e.setNoGravity(true); h.getLevel().addFreshEntity(e); entities.add(e); }
        BigDecimal capacity() { return new BigDecimal(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getString("maxShield")); }
        double cp() { return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp"); }
        void resetCooldown() { var s = AbilityStorage.get(p); s.cooldowns.remove(MagneticGroundStep.ID); s.cooldownMaxTicks.remove(MagneticGroundStep.ID); p.getPersistentData().getCompound("academy:electromaster_prototype").remove("next_" + MagneticGroundStep.ID); }
        void close() { if (closed) return; closed = true; MagneticGroundStep.remove(p); PolarityTether.remove(p); ElectromasterChargeDecoy.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); entities.forEach(Entity::discard); channel.finishAndReleaseAll(); }
    }
}

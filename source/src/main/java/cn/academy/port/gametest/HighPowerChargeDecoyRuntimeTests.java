package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.ElectromasterChargeDecoy;
import cn.academy.port.highpower.HighPowerCombat;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Isolated NEW-123 tests; no mutation of the strict native damage witness suite. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerChargeDecoyRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_charge_decoy";
    private HighPowerChargeDecoyRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void decoy_visible_bottom_ground_precedes_out_of_world_ray_suffix(GameTestHelper h) {
        Fixture f = new Fixture(h,true);ServerPlayer p=f.player();Vec3 from=p.getEyePosition();
        h.assertTrue(from.add(p.getLookAngle().scale(ElectromasterChargeDecoy.PLACEMENT_RANGE)).y<h.getLevel().getMinBuildHeight(),"full placement ray really ends below world minimum");
        double cp=f.cp(p);h.assertTrue(ElectromasterChargeDecoy.start(p),"native visible UP support is admitted before the boundary");
        h.runAfterDelay(1,()->{h.assertTrue(ElectromasterChargeDecoy.release(p),"same real support commits normally");near(h,f.cp(p),cp-5,"normal finite LV2 payment still required");h.succeed();});
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void decoy_bottom_void_without_native_support_stays_free_refusal(GameTestHelper h) {
        Fixture f = new Fixture(h,true);ServerPlayer p=f.player();double cp=f.cp(p);
        for(int x=3;x<=8;x++)for(int z=7;z<=13;z++)h.getLevel().setBlock(new BlockPos(h.absolutePos(new BlockPos(x,0,z)).getX(),h.getLevel().getMinBuildHeight()+(int)Math.ceil(ElectromasterChargeDecoy.INFLUENCE_RADIUS)-1,h.absolutePos(new BlockPos(x,0,z)).getZ()),Blocks.AIR.defaultBlockState(),3);
        h.assertFalse(ElectromasterChargeDecoy.start(p),"boundary clipping cannot invent an UP support in void");near(h,f.cp(p),cp,"no ground cannot debit resources");h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void decoy_preview_minimum_tick_and_shared_cp_commit_are_fenced(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        double cp = f.cp(p);
        h.assertTrue(ElectromasterChargeDecoy.start(p), "visible ground preview starts");
        h.assertTrue(ElectromasterChargeDecoy.previewing(p), "holding is preview only");
        h.assertFalse(ElectromasterChargeDecoy.start(p), "duplicate start cannot replace a held preview");
        h.assertFalse(ElectromasterChargeDecoy.release(p), "same native tick release cannot commit");
        near(h, f.cp(p), cp, "minimum-tick refusal spends nothing");
        h.assertTrue(ElectromasterChargeDecoy.start(p), "new preview after refused release");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "release after one tick commits");
            near(h, f.cp(p), cp - 5, "LV2 debit uses the shared prototype ledger");
            h.assertFalse(ElectromasterChargeDecoy.previewing(p), "committed marker is a pulse");
            near(h, AbilityStorage.get(p).exp(ElectromasterChargeDecoy.ID), 0, "empty marker grants no practice XP");
            h.assertFalse(ElectromasterChargeDecoy.release(p), "duplicate release cannot spend twice");
            near(h, f.cp(p), cp - 5, "replay leaves CP unchanged");
            h.assertTrue(AbilityStorage.get(p).cooldowns.getOrDefault(ElectromasterChargeDecoy.ID, 0) == 1, "tool minimum one-tick cooldown");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void decoy_authenticated_positive_inputs_reject_replays_and_stale_release(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.connectedPlayer();
        h.assertTrue(ElectromasterChargeDecoy.start(p, 1), "connected actor claims positive input");
        h.assertFalse(ElectromasterChargeDecoy.release(p, 2), "wrong physical input cannot terminate current hold");
        h.assertTrue(ElectromasterChargeDecoy.previewing(p), "mismatched release keeps current preview");
        h.assertTrue(ElectromasterChargeDecoy.abort(p, 1), "matching input abort");
        h.assertFalse(ElectromasterChargeDecoy.start(p, 1), "retired input cannot restart");
        h.assertFalse(ElectromasterChargeDecoy.start(p, 2), "terminal-before-start input cannot resurrect later");
        h.assertTrue(ElectromasterChargeDecoy.start(p, 3), "fresh physical input accepted");
        h.runAfterDelay(1, () -> {
            h.assertFalse(ElectromasterChargeDecoy.release(p, 1), "late previous release cannot commit newer preview");
            h.assertTrue(ElectromasterChargeDecoy.previewing(p), "new preview survives stale release");
            h.assertTrue(ElectromasterChargeDecoy.release(p, 3), "matching new input commits");
            h.assertFalse(ElectromasterChargeDecoy.release(p, 3), "duplicate paid release is idempotent");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void decoy_real_arrow_steering_preserves_speed_and_native_motion(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        Arrow arrow = f.arrow(f.point.add(1.2, 1, 0), new Vec3(0, 0, -.5));
        UUID uuid = arrow.getUUID(); int id = arrow.getId(); double damage = arrow.getBaseDamage();
        h.assertTrue(ElectromasterChargeDecoy.start(p), "preview");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "pulse");
            Vec3 before = arrow.getDeltaMovement(); double speed = before.length(); Vec3 position = arrow.position();
            ElectromasterChargeDecoy.tick(p);
            Vec3 changed = arrow.getDeltaMovement();
            h.assertTrue(changed.x < before.x, "existing arrow bends toward ground point");
            near(h, changed.length(), speed, "steering preserves native speed");
            h.assertTrue(changed.normalize().dot(before.normalize()) >= Math.cos(.180001), "per-tick turn bound");
            h.assertTrue(arrow.getUUID().equals(uuid) && arrow.getId() == id && !arrow.isRemoved(), "same projectile identity, never duplication/deletion");
            near(h, arrow.getBaseDamage(), damage, "no projectile damage rewrite");
            ElectromasterChargeDecoy.tick(p);
            h.assertTrue(changed.equals(arrow.getDeltaMovement()), "same native tick does not steer twice");
            near(h, AbilityStorage.get(p).exp(ElectromasterChargeDecoy.ID), .005, "only one first-admission practice gain per pulse");
            h.runAfterDelay(1, () -> {
                h.assertTrue(arrow.getX() < position.x, "native movement follows changed trajectory");
                h.assertTrue(arrow.getUUID().equals(uuid), "native arrow survives its real movement");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void decoy_four_per_second_quota_survives_recast_and_abort(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); List<Arrow> first = new ArrayList<>();
        for (int i = 0; i < 6; i++) first.add(f.arrow(f.point.add(.4 + i * .25, 1, 0), new Vec3(0, 0, -.2)));
        h.assertTrue(ElectromasterChargeDecoy.start(p), "preview");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "commit"); ElectromasterChargeDecoy.tick(p);
            h.assertTrue(ElectromasterChargeDecoy.affectedCount(p) == 4, "at most four unique ordinary arrows admitted");
            near(h, AbilityStorage.get(p).exp(ElectromasterChargeDecoy.ID), .005, "four admitted arrows grant only one practice gain");
            h.assertTrue(first.stream().filter(a -> a.getDeltaMovement().x < 0).count() == 4, "exactly four real velocities changed");
            ElectromasterChargeDecoy.abort(p); AbilityStorage.get(p).tickCooldowns();
            Arrow fresh = f.arrow(f.point.add(.2, .7, 0), new Vec3(0, 0, -.2));
            h.assertTrue(ElectromasterChargeDecoy.start(p), "recast gets a fresh preview");
            h.runAfterDelay(1, () -> {
                h.assertTrue(ElectromasterChargeDecoy.release(p), "replacement pulse commits independently");
                Vec3 velocity = fresh.getDeltaMovement(); ElectromasterChargeDecoy.tick(p);
                h.assertTrue(ElectromasterChargeDecoy.affectedCount(p) == 0 && fresh.getDeltaMovement().equals(velocity), "abort/recast cannot refill rolling admission quota");
                near(h, AbilityStorage.get(p).exp(ElectromasterChargeDecoy.ID), .005, "quota-blocked recast grants no extra XP");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void decoy_owner_nonconductive_embedded_fast_and_wall_arrows_are_untouched(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        Arrow owned = f.arrow(f.point.add(-1, 1, 0), new Vec3(0, 0, -.3)); owned.setOwner(p);
        Arrow fast = f.arrow(f.point.add(-1, 1, .5), new Vec3(0, 0, 3.5));
        Arrow embedded = f.arrow(f.point.add(-.5, 1, 0), new Vec3(0, 0, -.3)); embedded.inGround = true;
        Arrow physicsOff = f.arrow(f.point.add(-1, .5, 0), new Vec3(0, 0, -.3)); physicsOff.setNoPhysics(true);
        Arrow wall = f.arrow(f.point.add(2, .2, 0), new Vec3(0, 0, -.3));
        h.setBlock(new BlockPos(6, 2, 10), Blocks.STONE);
        Snowball snow = new Snowball(EntityType.SNOWBALL, h.getLevel()); snow.setPos(f.point.add(-1, 1, -.4)); snow.setDeltaMovement(0, 0, -.3); f.entity(snow);
        h.assertTrue(ElectromasterChargeDecoy.start(p), "ground before wall remains visible");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "commit");
            embedded.inGround = true; // Controller test keeps the embedded state stable before this manual native tick.
            List<Entity> untouched = List.of(owned, fast, embedded, physicsOff, wall, snow);
            List<Vec3> velocities = untouched.stream().map(Entity::getDeltaMovement).toList();
            ElectromasterChargeDecoy.tick(p);
            for (int i = 0; i < untouched.size(); i++) h.assertTrue(untouched.get(i).getDeltaMovement().equals(velocities.get(i)), "excluded projectile unchanged: " + i);
            h.assertTrue(ElectromasterChargeDecoy.affectedCount(p) == 0, "ineligible arrows never consume quota");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void decoy_crowded_ineligible_arrows_do_not_starve_a_real_candidate(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player();
        for (int i = 0; i < 40; i++) {
            Arrow owned = f.arrow(f.point.add(-.4, 1, (i % 5) * .03), new Vec3(0, 0, -.2)); owned.setOwner(p);
        }
        Arrow eligible = f.arrow(f.point.add(1, 1, 0), new Vec3(0, 0, -.2));
        h.assertTrue(ElectromasterChargeDecoy.start(p), "preview");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "commit"); ElectromasterChargeDecoy.tick(p);
            h.assertTrue(eligible.getDeltaMovement().x < 0 && ElectromasterChargeDecoy.affectedCount(p) == 1,
                    "forty owner-excluded arrows do not consume thirty-two-candidate budget");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void decoy_ground_loss_dimension_cleanup_and_bad_aim_spend_nothing(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); double cp = f.cp(p);
        p.setXRot(-30);
        h.assertFalse(ElectromasterChargeDecoy.start(p), "sky is not a ground point"); near(h, f.cp(p), cp, "invalid placement free");
        f.aim(p); h.assertTrue(ElectromasterChargeDecoy.start(p), "preview");
        ElectromasterChargeDecoy.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p, p.level().dimension(), p.level().dimension()));
        h.assertFalse(ElectromasterChargeDecoy.active(p), "dimension lifecycle clears held point");
        h.assertFalse(ElectromasterChargeDecoy.release(p), "late release after lifecycle cannot commit");
        near(h, f.cp(p), cp, "lifecycle abort free");
        h.assertTrue(ElectromasterChargeDecoy.start(p), "new preview");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(p), "commit");
            h.setBlock(new BlockPos(5, 1, 10), Blocks.AIR);
            ElectromasterChargeDecoy.tick(p);
            h.assertFalse(ElectromasterChargeDecoy.active(p), "support removal ends pulse before physics");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 35)
    public static void decoy_pulse_expires_at_twenty_ticks_and_two_casters_cannot_claim_one_arrow(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer a = f.player(), b = f.player();
        Arrow arrow = f.arrow(f.point.add(1, 1, 0), new Vec3(0, 0, -.2));
        h.assertTrue(ElectromasterChargeDecoy.start(a) && ElectromasterChargeDecoy.start(b), "two independent caster previews");
        h.runAfterDelay(1, () -> {
            h.assertTrue(ElectromasterChargeDecoy.release(a) && ElectromasterChargeDecoy.release(b), "both pay own shared budget");
            ElectromasterChargeDecoy.tick(a); Vec3 velocity = arrow.getDeltaMovement(); ElectromasterChargeDecoy.tick(b);
            h.assertTrue(ElectromasterChargeDecoy.affectedCount(a) == 1 && ElectromasterChargeDecoy.affectedCount(b) == 0, "one steering owner per projectile");
            h.assertTrue(arrow.getDeltaMovement().equals(velocity), "second caster does not add extra turn");
            h.runAfterDelay(20, () -> {
                ElectromasterChargeDecoy.tick(a); ElectromasterChargeDecoy.tick(b);
                h.assertFalse(ElectromasterChargeDecoy.active(a) || ElectromasterChargeDecoy.active(b), "one-second pulse is terminal, no invented persistent lure");
                h.succeed();
            });
        });
    }

    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected); }
    private static final class Fixture {
        final GameTestHelper h; final Vec3 point; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>(); final List<EmbeddedChannel> channels = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) { this(h,false); }
        Fixture(GameTestHelper h,boolean bottom) {
            this.h = h;var base=h.absoluteVec(new Vec3(5.5,2.05,10.5));point=bottom?new Vec3(base.x,h.getLevel().getMinBuildHeight()+Math.ceil(ElectromasterChargeDecoy.INFLUENCE_RADIUS)+.05,base.z):base;
            for (int x = 3; x <= 8; x++) for (int z = 7; z <= 13; z++) {
                if(bottom){var at=h.absolutePos(new BlockPos(x,0,z));h.getLevel().setBlock(new BlockPos(at.getX(),(int)Math.floor(point.y)-1,at.getZ()),Blocks.STONE.defaultBlockState(),3);}
                else h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player() {
            FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Decoy]")); initialize(p); return p;
        }
        ServerPlayer connectedPlayer() {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPDecoyProbe"), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection); channels.add(channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); initialize(p); return p;
        }
        void initialize(ServerPlayer p) {
            players.add(p);
            Vec3 at = point.add(0,.95,-6); p.moveTo(at.x, at.y, at.z, 0, 0); p.setNoGravity(true); p.getAbilities().instabuild = false;
            h.assertTrue(HighPowerCombat.enroll(p, 2), "LV2 enrolled vanilla fixture");
            var s = AbilityStorage.get(p); s.selectCategory("electromaster"); s.setLevel(2); s.learn("forked_arc"); s.learn("magnetic_bracer"); s.learn(ElectromasterChargeDecoy.ID); s.activated = true; s.overloadFine = true;
            CompoundTag tag = new CompoundTag(); tag.putDouble("cp", 600); p.getPersistentData().put("academy:electromaster_prototype", tag);
            aim(p);
        }
        void aim(ServerPlayer p) {
            Vec3 direction = point.add(0, -.05, 0).subtract(p.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));
            p.setYRot(yaw); p.setYHeadRot(yaw); p.setXRot(pitch);
        }
        Arrow arrow(Vec3 at, Vec3 velocity) { Arrow a = new Arrow(EntityType.ARROW, h.getLevel()); a.setPos(at); a.setDeltaMovement(velocity); entity(a); return a; }
        void entity(Entity e) { e.setNoGravity(true); h.getLevel().addFreshEntity(e); entities.add(e); }
        double cp(ServerPlayer p) { return p.getPersistentData().getCompound("academy:electromaster_prototype").getDouble("cp"); }
        void close() { if (closed) return; closed = true; for (ServerPlayer p : players) { ElectromasterChargeDecoy.remove(p); AbilityStorage.remove(p); var list = h.getLevel().getServer().getPlayerList(); if (list.getPlayer(p.getUUID()) == p) list.remove(p); else p.discard(); } entities.forEach(Entity::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll); }
    }
}

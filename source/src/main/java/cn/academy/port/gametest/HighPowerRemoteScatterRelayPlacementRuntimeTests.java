package cn.academy.port.gametest;

import cn.academy.port.highpower.core.RemoteScatterRelayRules;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.DualPoint;
import cn.academy.port.highpower.FoldedMirrorArray;
import cn.academy.port.highpower.HighPowerBracerSession;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.RemoteScatterRelay;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native placement regressions. Only level/learned state and player pose/position are fixtures;
 * every candidate and paid node is selected by authenticated production start/release. Candidate
 * coordinates come from the actual outbound visual packet, never reflected or injected state.
 * The dedicated packet/tick case uses the native pose update across real server game ticks;
 * immediate pose cases isolate release geometry without claiming client key scheduling. */
@GameTestHolder("academy_remote_scatter_relay_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerRemoteScatterRelayPlacementRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "remote_scatter_relay_placement_pose";
    private static final double AIR_DISTANCE = 30;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("academy.remote_scatter_relay.placement_test");

    private HighPowerRemoteScatterRelayPlacementRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH + "_stable", timeoutTicks = 10)
    public static void stable_crouch_places_the_actual_air_candidate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.release(press, true, "stable_crouch");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_standing", timeoutTicks = 10)
    public static void standing_before_release_preserves_the_selected_air_candidate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.p.setShiftKeyDown(false);
            f.p.setPose(Pose.STANDING);
            h.assertTrue(f.p.getEyePosition().y - press.eye().y > .3,
                    "actual crouch-to-standing pose raises the native eye before release");
            h.assertTrue(f.p.getEyePosition().distanceTo(press.center()) < 32,
                    "unchanged 30-meter candidate stays inside the strict eye range after standing");
            f.release(press, true, "crouch_to_standing");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_backward", timeoutTicks = 10)
    public static void five_centimeters_backward_preserves_the_selected_air_candidate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.move(f.p.getLookAngle().normalize().scale(-.05));
            near(h, f.p.getEyePosition().distanceTo(press.center()), AIR_DISTANCE + .05,
                    "five-centimeter retreat leaves the current eye inside the strict range");
            f.release(press, true, "backward_0.05m");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_native_pose_tick", timeoutTicks = 12)
    public static void native_shift_release_and_next_player_tick_preserve_pending_placement(GameTestHelper h) {
        Fixture f = new Fixture(h);
        // Initial standing is fixture setup. Both subsequent pose changes are owned by native
        // command processing and Player.updatePlayerPose, not direct setPose calls.
        f.p.setShiftKeyDown(false);
        f.p.setPose(Pose.STANDING);
        f.shift(true);
        f.p.doTick();
        f.p.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(f.p.getPose() == Pose.CROUCHING, "native shift press plus player tick enters crouch");
        Press press = f.press();
        long started = f.p.server.overworld().getGameTime();
        h.runAtTickTime(2, () -> {
            f.shift(false);
            f.p.doTick();
            f.p.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(f.p.server.overworld().getGameTime() > started,
                    "shift release and placement release happen after a real server game tick");
            h.assertTrue(f.p.getPose() == Pose.STANDING && !f.p.isShiftKeyDown()
                            && f.p.getEyePosition().y - press.eye().y > .3,
                    "native RELEASE_SHIFT_KEY and native player tick raise the actual release eye");
            f.release(press, true, "native_shift_release_after_tick");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_hard_range", timeoutTicks = 10)
    public static void retreat_beyond_the_real_thirty_two_meter_limit_is_free(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.move(f.p.getLookAngle().normalize().scale(-2.05));
            near(h, f.p.getEyePosition().distanceTo(press.center()), 32.05,
                    "genuine movement carries the fixed candidate beyond the unchanged hard limit");
            f.release(press, false, "backward_2.05m_hard_limit");
            h.assertTrue(f.visuals().stream().anyMatch(v -> v.status() == RemoteScatterRelay.TOO_FAR),
                    "the native refusal packet identifies the actual eye-range failure");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_exact_range", timeoutTicks = 10)
    public static void retreat_to_exactly_thirty_two_meters_still_places_the_fixed_candidate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.move(f.p.getLookAngle().normalize().scale(-2));
            near(h, f.p.getEyePosition().distanceTo(press.center()), 32,
                    "movement places the current eye on the unchanged inclusive boundary");
            f.release(press, true, "backward_2m_exact_hard_limit");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_surface", timeoutTicks = 10)
    public static void visible_surface_beyond_thirty_meters_keeps_its_native_clearance_offset(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Vec3 eye = f.p.getEyePosition();
            BlockPos surface = BlockPos.containing(eye.add(0, 0, 32));
            f.block(surface);
            double expected = surface.getZ() - eye.z - .035 - Math.hypot(.45, .65) - .06;
            h.assertTrue(expected > 30 && expected < 32,
                    "visible native block selects a real surface candidate between 30 and 32 meters");
            Press press = f.press(expected);
            f.release(press, true, "surface_beyond_30m");
            h.assertTrue(h.getLevel().getBlockState(surface).is(Blocks.STONE),
                    "surface placement neither moves nor destroys its native obstacle");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_obstacle_reasons", timeoutTicks = 10)
    public static void native_obstacle_and_plate_occupancy_have_distinct_free_refusals(GameTestHelper h) {
        for (boolean plateSpace : List.of(false, true)) try (Fixture f = new Fixture(h)) {
            // At x=.95 the whole plate extends into the eastern block while both native rays
            // (radius .035) remain west of it. This distinguishes plate space from ray blockage.
            if (plateSpace) f.move(new Vec3(.45, 0, 0));
            Press press = f.press();
            f.block(plateSpace ? BlockPos.containing(press.center()).east()
                    : BlockPos.containing(press.eye().lerp(press.center(), .5)));
            f.release(press, false, plateSpace ? "native_plate_occupancy" : "native_ray_obstacle");
            f.assertStatus(plateSpace ? RemoteScatterRelay.NO_SPACE : RemoteScatterRelay.BLOCKED,
                    "actual native geometry supplies its specific placement refusal");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_last_leg", timeoutTicks = 12)
    public static void distant_previous_node_reports_segment_range_without_losing_the_paid_node(GameTestHelper h) {
        Fixture f = new Fixture(h);
        Press first = f.press();
        f.release(first, true, "segment_range_first_node");
        var plates = RemoteScatterRelay.plates(f.p);
        long placedAt = RemoteScatterRelay.placedAt(f.p);
        h.runAtTickTime(2, () -> {
            f.p.doTick();
            f.p.setDeltaMovement(Vec3.ZERO);
            f.crouch();
            f.p.setYRot(180);
            f.visuals();
            double cp = HighPowerResources.cp(f.p);
            String wire = f.input();
            h.assertTrue(RemoteScatterRelay.start(f.p, wire), "authenticated second crouch input is accepted");
            f.assertStatus(RemoteScatterRelay.SEGMENT_TOO_FAR,
                    "actual 30-meter reverse candidate is refused because the prior node is 60 meters away");
            h.assertFalse(RemoteScatterRelay.release(f.p, wire), "overlong inter-node leg does not place on release");
            f.assertStatus(RemoteScatterRelay.SEGMENT_TOO_FAR, "release retains the authoritative inter-node reason");
            near(h, cp - HighPowerResources.cp(f.p), 0, "rejected second placement is free");
            h.assertTrue(RemoteScatterRelay.plates(f.p).equals(plates) && RemoteScatterRelay.placedAt(f.p) == placedAt,
                    "rejected append leaves the original paid node and its expiry unchanged");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_clearing_threshold", timeoutTicks = 22)
    public static void crouch_hold_reports_clearing_at_twelve_ticks_and_clears_for_free(GameTestHelper h) {
        holdFeedback(h, true);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_measuring_threshold", timeoutTicks = 22)
    public static void normal_hold_reports_measuring_at_twelve_ticks_and_keeps_the_array(GameTestHelper h) {
        holdFeedback(h, false);
    }

    private static void holdFeedback(GameTestHelper h, boolean clearing) {
        Fixture f = new Fixture(h);
        Press first = f.press();
        f.release(first, true, clearing ? "clear_hold_first_node" : "measure_hold_first_node");
        var plates = RemoteScatterRelay.plates(f.p);
        long placedAt = RemoteScatterRelay.placedAt(f.p);
        String[] pending = {null};
        long[] started = {-1};
        h.runAtTickTime(2, () -> {
            f.shift(clearing);
            f.p.doTick();
            f.p.setDeltaMovement(Vec3.ZERO);
            if (clearing) f.p.setYRot(-20);
            f.visuals();
            pending[0] = f.input();
            h.assertTrue(RemoteScatterRelay.start(f.p, pending[0]), "actual second press starts the requested hold mode");
            started[0] = f.p.server.overworld().getGameTime();
            f.assertStatus(RemoteScatterRelay.PREPARED, "short-hold preview still offers the ordinary release action");
        });
        h.runAtTickTime(13, () -> {
            h.assertTrue(f.p.server.overworld().getGameTime() - started[0] == 11,
                    "pre-threshold observation is exactly eleven native game ticks after start");
            RemoteScatterRelay.tick(f.p);
            h.assertTrue(f.visuals().stream().noneMatch(v -> v.status() == RemoteScatterRelay.MEASURING
                            || v.status() == RemoteScatterRelay.CLEARING),
                    "eleven-tick hold does not prematurely advertise the long-hold action");
        });
        h.runAtTickTime(14, () -> {
            h.assertTrue(f.p.server.overworld().getGameTime() - started[0] == 12,
                    "threshold observation is exactly twelve native game ticks after start");
            RemoteScatterRelay.tick(f.p);
            f.assertStatus(clearing ? RemoteScatterRelay.CLEARING : RemoteScatterRelay.MEASURING,
                    "authoritative preview changes to the correct release action at the native threshold");
            double cp = HighPowerResources.cp(f.p);
            h.assertTrue(RemoteScatterRelay.release(f.p, pending[0]), "twelve-tick release completes the advertised action");
            near(h, cp - HighPowerResources.cp(f.p), 0, "both long-hold actions are free");
            if (clearing) {
                h.assertFalse(RemoteScatterRelay.active(f.p), "crouch long release clears the paid array");
                h.assertTrue(RemoteScatterRelay.plates(f.p).isEmpty(), "clear publishes no replacement node");
                h.succeed();
            } else {
                f.assertStatus(RemoteScatterRelay.MEASURING, "normal long release confirms measurement without claiming a shot");
                h.assertTrue(RemoteScatterRelay.plates(f.p).equals(plates) && RemoteScatterRelay.placedAt(f.p) == placedAt,
                        "measurement preserves exact paid nodes and the first deployment clock");
            }
        });
        if (!clearing) h.runAtTickTime(16, () -> {
            double cp = HighPowerResources.cp(f.p);
            String wire = f.input();
            h.assertTrue(RemoteScatterRelay.start(f.p, wire) && RemoteScatterRelay.release(f.p, wire),
                    "next normal short press still selects an endpoint, proving measurement never selected or fired it");
            near(h, cp - HighPowerResources.cp(f.p), 0, "endpoint selection after measurement is free");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_forward", timeoutTicks = 10)
    public static void five_centimeters_forward_preserves_the_selected_air_candidate(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Press press = f.press();
            f.move(f.p.getLookAngle().normalize().scale(.05));
            near(h, f.p.getEyePosition().distanceTo(press.center()), AIR_DISTANCE - .05,
                    "forward movement leaves the selected point inside the existing range");
            f.release(press, true, "forward_0.05m");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_repeat", timeoutTicks = 12)
    public static void two_authenticated_air_placements_keep_the_first_node_and_expiry(GameTestHelper h) {
        Fixture f = new Fixture(h);
        Press first = f.press();
        f.release(first, true, "repeated_first");
        long placedAt = RemoteScatterRelay.placedAt(f.p);
        var firstNode = RemoteScatterRelay.plates(f.p).getFirst();
        h.runAtTickTime(2, () -> {
            f.p.doTick();
            f.p.setDeltaMovement(Vec3.ZERO);
            f.crouch();
            f.p.setYRot(-20);
            Press second = f.press();
            h.assertTrue(first.center().distanceTo(second.center()) > 1,
                    "second genuine look selects a distinct node instead of reusing fixture geometry");
            f.release(second, true, "repeated_second");
            var plates = RemoteScatterRelay.plates(f.p);
            h.assertTrue(plates.size() == 2 && plates.getFirst().equals(firstNode)
                            && plates.getLast().center().equals(second.center()),
                    "two authenticated short presses publish exactly two ordered fixed nodes");
            long elapsed = h.getLevel().getGameTime() - placedAt;
            h.assertTrue(elapsed >= 2 && RemoteScatterRelay.placedAt(f.p) == placedAt
                            && RemoteScatterRelay.remainingTicks(f.p) == RemoteScatterRelayRules.LIFETIME - elapsed,
                    "append preserves the first deployment's original clock");
            h.succeed();
        });
    }

    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected);
    }

    private record Press(String wire, Vec3 eye, Vec3 center, double cp, int nodes) {}

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerPlayer p;
        final EmbeddedChannel channel;
        final List<BlockPos> blocks = new ArrayList<>();
        long serial;
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            var server = h.getLevel().getServer();
            UUID id = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "RSP" + id.toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            visuals();
            p.setGameMode(GameType.SURVIVAL);
            p.setNoGravity(true);
            Vec3 at = h.absoluteVec(new Vec3(5.5, 18, 5.5));
            p.moveTo(at.x, at.y, at.z, 0, 0);
            p.setDeltaMovement(Vec3.ZERO);
            p.connection.resetPosition();
            crouch();
            // Explicit loaded-air fixture. Production selection still performs all native
            // permission, solid, entity and per-prefix chunk checks itself.
            for (int x = ((int) Math.floor(at.x - 34)) >> 4; x <= ((int) Math.floor(at.x + 34)) >> 4; x++)
                for (int z = ((int) Math.floor(at.z - 34)) >> 4; z <= ((int) Math.floor(at.z + 34)) >> 4; z++)
                    h.getLevel().getChunk(x, z);
            h.assertTrue(HighPowerCombat.enroll(p, 5), "registered real LV5 server player");
            var state = AbilityStorage.get(p);
            state.selectCategory("meltdowner");
            state.setLevel(5);
            state.learn(DualPoint.ID);
            state.learn(FoldedMirrorArray.ID);
            state.learn(RemoteScatterRelay.ID);
            state.activated = true;
            state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, RemoteScatterRelay.ID, skill -> true), "bind real NEW253 input slot");
            HighPowerResources.initializeBudget(p);
            LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), state);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }

        void crouch() {
            p.setShiftKeyDown(true);
            p.setPose(Pose.CROUCHING);
        }

        void move(Vec3 delta) {
            p.setPos(p.position().add(delta));
            p.connection.resetPosition();
        }

        void shift(boolean down) {
            p.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(p, down
                    ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                    : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }

        void block(BlockPos at) {
            blocks.add(at);
            h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        }

        Press press() {
            return press(AIR_DISTANCE);
        }

        Press press(double expectedDistance) {
            visuals();
            String wire = input();
            double cp = HighPowerResources.cp(p);
            int nodes = RemoteScatterRelay.plates(p).size();
            Vec3 eye = p.getEyePosition();
            h.assertTrue(RemoteScatterRelay.start(p, wire), "actual crouch production start accepts the new physical input");
            var packets = visuals();
            var preview = packets.stream().filter(v -> v.input() == serial && v.phase() == RemoteScatterRelay.EDIT)
                    .reduce((earlier, later) -> later).orElse(null);
            h.assertTrue(preview != null && preview.status() == RemoteScatterRelay.PREPARED && preview.ghostNormal().lengthSqr() > .9,
                    "native outbound EDIT packet proves the actual candidate is valid at press time");
            near(h, eye.distanceTo(preview.ghostCenter()), expectedDistance, "production selected the measured candidate distance");
            near(h, cp - HighPowerResources.cp(p), 0, "preview does not charge CP");
            h.assertTrue(RemoteScatterRelay.plates(p).size() == nodes, "preview never publishes an unpaid node");
            return new Press(wire, eye, preview.ghostCenter(), cp, nodes);
        }

        String input() {
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p));
            var input = new HighPowerInput.Input(0, ++serial, p.getId(), epoch);
            h.assertTrue(HighPowerBracerSession.authenticated(p, input), "actor, connection, world and epoch authenticate the actual press");
            return input.wire();
        }

        void assertStatus(int expected, String why) {
            var packets = visuals();
            h.assertTrue(packets.stream().anyMatch(v -> v.status() == expected),
                    why + ": expected=" + expected + ", observed=" + packets.stream().map(RemoteScatterRelay.Visual::status).toList());
        }

        void release(Press press, boolean expected, String scenario) {
            double releaseDistance = p.getEyePosition().distanceTo(press.center());
            boolean result = RemoteScatterRelay.release(p, press.wire());
            double paid = press.cp() - HighPowerResources.cp(p);
            var plates = RemoteScatterRelay.plates(p);
            LOGGER.info(String.format(Locale.ROOT,
                    "NEW253_PLACEMENT_EVIDENCE case=%s pressDistance=%.9f releaseDistance=%.9f eyeDelta=%.9f released=%s cpSpent=%.9f nodes=%d",
                    scenario, press.eye().distanceTo(press.center()), releaseDistance,
                    p.getEyePosition().distanceTo(press.eye()), result, paid, plates.size()));
            h.assertTrue(result == expected, "observed production release matches the declared boundary case " + scenario);
            near(h, paid, expected ? .01 : 0, "only a committed LV5 placement pays exactly once");
            h.assertTrue(plates.size() == press.nodes() + (expected ? 1 : 0), "release publishes only its one successfully paid node");
            if (expected) h.assertTrue(plates.getLast().center().equals(press.center()), "release keeps the selected point fixed through movement");
            h.assertFalse(RemoteScatterRelay.release(p, press.wire()), "same physical release cannot pay or publish twice");
            near(h, press.cp() - HighPowerResources.cp(p), paid, "replayed release never repeats CP payment");
        }

        List<RemoteScatterRelay.Visual> visuals() {
            List<RemoteScatterRelay.Visual> result = new ArrayList<>();
            channel.runPendingTasks();
            channel.flushOutbound();
            channel.runPendingTasks();
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundPlayerPositionPacket position)
                    p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof RemoteScatterRelay.Visual visual
                        && visual.ownerUuid().equals(p.getUUID())) result.add(visual);
                ReferenceCountUtil.release(packet);
            }
            return result;
        }

        public void close() {
            if (closed) return;
            closed = true;
            blocks.forEach(at -> h.getLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()));
            RemoteScatterRelay.remove(p);
            AbilityStorage.remove(p);
            LegacySingleKeyOwnerEpoch.forget(p);
            p.server.getPlayerList().remove(p);
            channel.finishAndReleaseAll();
        }
    }
}

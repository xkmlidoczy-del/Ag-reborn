package cn.academy.port.highpower;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Read-only evidence of one pinned doTick -> absMoveTo connection cycle.
 * The real native operations remain in the mixin, execute exactly once, and keep
 * their own exception. An observation failure only poisons this optional proof. */
public final class EmergencyReturnConnectionCycle {
    private static final Map<ServerGamePacketListenerImpl, Witness> IN_FLIGHT = new IdentityHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.emergency_return");
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.emergencyReturnDiagnostic");
    private static int diagnosticLines;
    private static final StackWalker CALLERS = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final int ENTERED = 0, EVOLVED = 1, RESTORING = 2, RESTORED = 3, COMPLETE = 4, INVALID = 5;

    private EmergencyReturnConnectionCycle() {}

    /** Optional evidence is frozen during hurt sealing, never refreshed at native tick entry.
     * The bounded excursion is an eligibility limit only; no native motion is changed. */
    static void prepareSeal(EmergencyReturn.Capture capture) {
        SafeLandingProbeWorld.EmergencyConnectionGround ground = null;
        try {
            ServerPlayer p = capture.owner(); Vec3 motion = capture.postMotion();
            if (capture.matchesOriginalSeal(p) && ordinary(p)) {
                Vec3 displacement = new Vec3(clamp(motion.x), clamp(motion.y), clamp(motion.z));
                if (displacement.y > p.getGravity() && displacement.y <= .4 && Math.abs(displacement.x) <= 2 && Math.abs(displacement.z) <= 2)
                    ground = SafeLandingProbeWorld.emergencyConnectionGround(p, capture, displacement);
            }
        } catch (Throwable unavailable) { /* Missing optional evidence cannot alter the native hurt. */ }
        capture.freezeConnectionGround(ground);
    }

    /** Constructor and state are private; no boolean from a caller can mint a completed witness. */
    static final class Witness {
        private final EmergencyReturn.Capture capture;
        private final ServerPlayer owner;
        private final ServerGamePacketListenerImpl connection;
        private final SafeLandingProbeWorld.EmergencyConnectionGround ground;
        private final Vec3 sealMotion, displacement, evolvedMotion;
        private final double gravity;
        private int stage = ENTERED;
        private Witness(EmergencyReturn.Capture capture, ServerGamePacketListenerImpl connection,
                        SafeLandingProbeWorld.EmergencyConnectionGround ground, Vec3 displacement) {
            this.capture = capture; owner = capture.owner(); this.connection = connection; this.ground = ground;
            sealMotion = capture.postMotion(); this.displacement = displacement; gravity = owner.getGravity();
            double horizontalDrag = (double)(ground.friction() * .91F);
            evolvedMotion = new Vec3(displacement.x * horizontalDrag, (displacement.y - gravity) * (double).98F,
                    displacement.z * horizontalDrag);
        }
        boolean belongsTo(EmergencyReturn.Capture value) { return capture == value; }
        private boolean owner(ServerPlayer p) {
            return p == owner && p.connection == connection && connection.player == p && p.serverLevel() == ground.world
                    && p.server.isSameThread() && !p.hasDisconnected() && !p.isRemoved() && p.isAlive();
        }
        private boolean entry(ServerPlayer p) {
            return owner(p) && capture.currentConnectionCycle(p) && capture.matchesOriginalSeal(p)
                    && sealMotion.equals(p.getDeltaMovement()) && ordinary(p) && Double.compare(gravity, p.getGravity()) == 0
                    && evolvedMotion.y > 0 && finite(evolvedMotion);
        }
        private boolean evolved(ServerPlayer p) {
            return owner(p) && capture.currentConnectionCycle(p) && ordinary(p) && Double.compare(gravity, p.getGravity()) == 0
                    && ground.transientPosition.equals(p.position()) && ground.transientBody.equals(p.getBoundingBox())
                    && evolvedMotion.equals(p.getDeltaMovement()) && unsupportedCollision(p) == false
                    && !p.onGround() && p.fallDistance == 0;
        }
        private boolean restoredState(ServerPlayer p) {
            return owner(p) && ordinary(p) && Double.compare(gravity, p.getGravity()) == 0
                    && ground.source.equals(p.position()) && ground.sourceBody.equals(p.getBoundingBox())
                    && evolvedMotion.equals(p.getDeltaMovement()) && evolvedMotion.y > 0 && !p.onGround()
                    && p.fallDistance == 0 && !unsupportedCollision(p);
        }
        boolean matchesRestored(ServerPlayer p) { return stage == COMPLETE && restoredState(p); }
        boolean revalidateGround(ServerPlayer p) {
            try {
                if (!matchesRestored(p) || !SafeLandingProbeWorld.emergencyConnectionGroundValid(p, ground)) { invalidate("ground_changed"); return false; }
                return true;
            } catch (Throwable unavailable) { invalidate("ground_unavailable"); return false; }
        }
        private void invalidate(String reason) {
            stage = INVALID; capture.invalidateConnectionCycle(); diagnostic(this, reason);
        }
    }

    /** Immediately before the one native doTick invocation; idle players return without world reads. */
    public static void beforeDoTick(ServerGamePacketListenerImpl connection, ServerPlayer player) {
        EmergencyReturn.Capture capture = null;
        try {
            capture = EmergencyReturn.captureForConnectionCycle(player);
            Witness prior = IN_FLIGHT.get(connection);
            if (prior != null) { prior.invalidate("second_cycle"); if (capture != null) capture.invalidateConnectionCycle(); return; }
            if (capture == null) return;
            if (!nativeCaller() || player.connection != connection || connection.player != player
                    || !capture.beginConnectionCycle(player) || !ordinary(player)) {
                capture.invalidateConnectionCycle(); return;
            }
            Vec3 original = capture.postMotion();
            Vec3 displacement = new Vec3(clamp(original.x), clamp(original.y), clamp(original.z));
            if (displacement.y <= player.getGravity() || displacement.y > .4 || Math.abs(displacement.x) > 2 || Math.abs(displacement.z) > 2) {
                capture.invalidateConnectionCycle(); return;
            }
            var ground = capture.connectionGround();
            if (ground == null || !SafeLandingProbeWorld.emergencyConnectionGroundValid(player, ground)
                    || !capture.matchesOriginalSeal(player)) { capture.invalidateConnectionCycle(); return; }
            Witness witness = new Witness(capture, connection, ground, displacement);
            if (!witness.entry(player)) { witness.invalidate("entry_changed"); return; }
            capture.attachConnectionWitness(witness); IN_FLIGHT.put(connection, witness);
        } catch (Throwable unavailable) {
            if (capture != null) capture.invalidateConnectionCycle();
            Witness witness = IN_FLIGHT.get(connection); if (witness != null) witness.invalidate("entry_unavailable");
        }
    }

    /** Native output is observed before the listener restores firstGoodXYZ. */
    public static void afterDoTick(ServerGamePacketListenerImpl connection, ServerPlayer player, boolean completed) {
        Witness witness = IN_FLIGHT.get(connection); if (witness == null) return;
        try {
            if (!nativeCaller() || !completed || witness.stage != ENTERED || !witness.evolved(player)
                    || !SafeLandingProbeWorld.emergencyConnectionGroundValid(player, witness.ground) || !witness.evolved(player)) {
                witness.invalidate(completed ? "native_output_changed" : "native_tick_threw"); return;
            }
            witness.stage = EVOLVED;
        } catch (Throwable unavailable) { witness.invalidate("native_output_unavailable"); }
    }

    /** Verify the actual native restoration arguments; never replace or clamp them here. */
    public static void beforeRestore(ServerGamePacketListenerImpl connection, ServerPlayer player, double x, double y, double z) {
        Witness witness = IN_FLIGHT.get(connection); if (witness == null) return;
        try {
            if (!nativeCaller() || witness.stage != EVOLVED || !witness.evolved(player) || Double.compare(x, witness.ground.source.x) != 0
                    || Double.compare(y, witness.ground.source.y) != 0 || Double.compare(z, witness.ground.source.z) != 0) {
                witness.invalidate("restore_arguments_changed"); return;
            }
            witness.stage = RESTORING;
        } catch (Throwable unavailable) { witness.invalidate("restore_unavailable"); }
    }

    /** Exactly restored body, evolved velocity and native false onGround are all retained. */
    public static void afterRestore(ServerGamePacketListenerImpl connection, ServerPlayer player, boolean completed) {
        Witness witness = IN_FLIGHT.get(connection); if (witness == null) return;
        try {
            if (!nativeCaller() || !completed || witness.stage != RESTORING || !witness.capture.currentConnectionCycle(player)
                    || !witness.restoredState(player) || !SafeLandingProbeWorld.emergencyConnectionGroundValid(player, witness.ground)
                    || !witness.restoredState(player)) { witness.invalidate("restored_state_changed"); return; }
            witness.stage = RESTORED;
        } catch (Throwable unavailable) { witness.invalidate("restored_state_unavailable"); }
    }

    /** Whole-tick finally: stale, nested and thrown native work cannot leave a reusable token. */
    public static void finished(ServerGamePacketListenerImpl connection, boolean completed) {
        Witness witness = IN_FLIGHT.remove(connection); if (witness == null) return;
        try {
            if (!nativeCaller() || !completed || witness.stage != RESTORED || !witness.capture.currentConnectionCycle(witness.owner)
                    || !witness.restoredState(witness.owner)) { witness.invalidate("connection_tick_incomplete"); return; }
            witness.stage = COMPLETE; diagnostic(witness, "witnessed");
        } catch (Throwable unavailable) { witness.invalidate("connection_tick_unavailable"); }
    }

    private static boolean ordinary(ServerPlayer p) {
        return p instanceof NativeEmergencyReturnMovementAccess access && p.isAlive() && !p.isRemoved()
                && p.getPose() == Pose.STANDING && !p.isShiftKeyDown() && !p.noPhysics && !p.isPassenger() && !p.isVehicle()
                && !p.isSleeping() && !p.isFallFlying() && !p.isSwimming() && !p.isAutoSpinAttack()
                && !p.getAbilities().flying && !p.getAbilities().instabuild && !p.getAbilities().invulnerable
                && !p.isInWater() && !p.isInLava() && !p.isInPowderSnow && !p.isNoGravity()
                && !p.shouldDiscardFriction() && Double.compare(p.getGravity(), .08D) == 0
                && p.xxa == 0F && p.yya == 0F && p.zza == 0F && !access.academy$emergencyJumping()
                && access.academy$emergencyLerpSteps() == 0 && !access.academy$emergencyImmobile()
                && Vec3.ZERO.equals(access.academy$emergencyStuckSpeed()) && p.fallDistance == 0
                && !p.hasEffect(MobEffects.LEVITATION) && !p.hasEffect(MobEffects.SLOW_FALLING)
                && finite(p.position()) && finite(p.getDeltaMovement());
    }
    private static boolean unsupportedCollision(ServerPlayer p) {
        return p.horizontalCollision || p.verticalCollision || p.verticalCollisionBelow || p.minorHorizontalCollision;
    }
    /** The merged pinned listener is the immediate caller of each observation.
     * Direct helper calls or calls from a native event callback cannot manufacture provenance.
     * This is a closed-runtime contract, not a claim against arbitrary bytecode/reflection. */
    private static boolean nativeCaller() {
        return CALLERS.walk(frames -> frames.skip(2).limit(1)
                .anyMatch(frame -> frame.getDeclaringClass() == ServerGamePacketListenerImpl.class));
    }
    private static double clamp(double value) { return Math.abs(value) < .003D ? 0D : value; }
    private static boolean finite(Vec3 vector) { return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    private static void diagnostic(Witness witness, String reason) {
        if (!DIAGNOSTIC || diagnosticLines >= 128) return;
        diagnosticLines++;
        try { LOG.info("NEW342_CONNECTION_DIAGNOSTIC reason={} stage={} source={} seal={} excursion={} expected={} actual={} position={} grounded={} fall={}",
                reason, witness.stage, witness.ground.source, witness.sealMotion, witness.displacement, witness.evolvedMotion,
                witness.owner.getDeltaMovement(), witness.owner.position(), witness.owner.onGround(), witness.owner.fallDistance); }
        catch (Throwable unavailable) { /* The optional observer must never replace a native exception. */ }
    }
    static void remove(ServerPlayer p) {
        IN_FLIGHT.entrySet().removeIf(entry -> {
            if (entry.getValue().owner != p) return false; entry.getValue().invalidate("owner_removed"); return true;
        });
    }
    static void remove(MinecraftServer server) {
        IN_FLIGHT.entrySet().removeIf(entry -> {
            if (entry.getValue().owner.server != server) return false; entry.getValue().invalidate("server_stopped"); return true;
        });
    }
}

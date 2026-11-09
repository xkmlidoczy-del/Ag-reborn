package cn.academy.port.client;

import cn.academy.port.highpower.MagneticGroundStep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * NEW-132 only: a server permission permits one small addition to CURRENT local XZ.
 * No absolute motion packet, teleport, Y rewrite, braking, ground flag or fall-distance reset.
 * The server's growth-bounded distance budget covers the assisted trajectory; normal inertia after retirement is untouched.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticGroundStepClientMotion {
    private static final double RANGE = 8, MAX_RANGE = RANGE * 2, MAX_STEP = .4, MAX_ADDITION = .08, MAX_OBSERVED_STEP = .8;
    private static final int MAX_TICKS = 40, PERMISSION_TTL = 2;
    private static ClientLevel world;
    private static LocalPlayer player;
    private static Object connection;
    private static volatile long generation;
    private static long localTick, lastAppliedTick = -1, lastAutoJumpInputTick = -1, maxToken;
    private static int autoJumpOffInputs;
    // One local actor: constant-sized retirement fences cannot be evicted by remote-owner churn.
    private static Stamp fence, canceled;
    private static long sequence;
    private static boolean retired;
    private static Session session;
    private record Stamp(int entity, long input, long token, long epoch) {}
    private static final class Session {
        final Stamp stamp;
        final Vec3 origin, direction;
        final long started;
        Vec3 previous;
        double spent, budget;
        /** This cast's own range: the server's first grant is its full range plus whatever was already walked from the origin. */
        final double limit;
        long received, deadline, consumedSequence;
        int permissions, applied;
        boolean acceptedLogged, firstAppliedLogged, retirementLogged;
        MagneticGroundStep.Assist permission;
        Session(MagneticGroundStep.Assist packet) {
            stamp = stamp(packet); origin = packet.origin(); direction = packet.direction(); started = localTick;
            previous = player.position(); spent = previous.subtract(origin).horizontalDistance();
            limit = Math.min(MAX_RANGE, packet.remaining() + spent);
            budget = Math.min(limit - spent, packet.remaining());
            received = localTick; deadline = localTick + packet.remainingTicks(); permission = packet;
        }
    }
    private MagneticGroundStepClientMotion() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            MagneticGroundStep.motionReceiver = MagneticGroundStepClientMotion::receive;
        }
    }

    public static void receive(MagneticGroundStep.Assist packet) {
        if (!validPacket(packet)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel capturedWorld = mc.level; LocalPlayer capturedPlayer = mc.player;
        Object capturedConnection = mc.getConnection(); long capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || capturedConnection == null
                    || mc.level != capturedWorld || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration) return;
            synchronize(mc);
            if (packet.entity() != player.getId() || !packet.owner().equals(player.getUUID())
                    || world.getEntity(packet.entity()) != player || !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            Stamp incoming = stamp(packet);
            if (canceled != null && compare(incoming, canceled) <= 0) { diagnosticGrant(packet, false, "canceled_input"); return; }
            if (fence != null) {
                int order = compare(incoming, fence);
                if (order < 0 || sameInput(incoming, fence) && incoming.token() != fence.token()
                        || order == 0 && (retired || !incoming.equals(fence) || packet.sequence() <= sequence)) { diagnosticGrant(packet, false, "replay_or_retired"); return; }
                if (order > 0 && packet.token() <= maxToken) { diagnosticGrant(packet, false, "token_floor"); return; }
            }
            if (packet.terminal() || packet.remaining() == 0 || packet.remainingTicks() == 0) {
                fence = incoming; sequence = packet.sequence(); maxToken = Math.max(maxToken, packet.token());
                diagnosticGrant(packet, false, "server_terminal"); retire("server_terminal"); return;
            }
            if (!localEligible(mc)) { diagnosticGrant(packet, false, "local_eligibility"); return; }
            if (mc.isPaused()) { diagnosticGrant(packet, false, "paused"); return; }
            if (!fresh(packet)) { diagnosticGrant(packet, false, "stale_clock"); return; }
            if (session != null && incoming.equals(session.stamp)) {
                if (!packet.origin().equals(session.origin) || !packet.direction().equals(session.direction)
                        || packet.remaining() > session.permission.remaining()
                        || packet.remainingTicks() > session.permission.remainingTicks()) { retire("changed_cast_geometry_or_budget"); return; }
                if(localTick-session.received>=PERMISSION_TTL||!fresh(session.permission))
                    diagnostic(session,"client_fresh_resume","higher_sequence_"+packet.sequence());
                session.budget = Math.min(session.budget, packet.remaining());
                session.deadline = Math.min(session.deadline, localTick + packet.remainingTicks());
                session.received = localTick; session.permission = packet;
            } else {
                diagnosticRetirement(session, "superseded");
                session = new Session(packet);
                if (session.spent > MAX_RANGE || Math.abs(player.getY() - packet.origin().y) > .025) {
                    fence = incoming; sequence = packet.sequence(); maxToken = Math.max(maxToken, packet.token()); diagnosticGrant(packet, false, "initial_origin_range_or_height"); retire("initial_origin_range_or_height"); return;
                }
            }
            fence = incoming; sequence = packet.sequence(); maxToken = Math.max(maxToken, packet.token()); retired = false;
            session.permissions++;
            if (!session.acceptedLogged) {
                session.acceptedLogged = true;
                diagnosticGrant(packet, true, "first_grant");
            }
        });
    }

    private static boolean validPacket(MagneticGroundStep.Assist p) {
        return p != null && p.entity() >= 0 && p.owner() != null && p.input() > 0 && p.token() > 0
                && p.epoch() > 0 && p.sequence() > 0 && finite(p.origin()) && finite(p.direction())
                && p.direction().y == 0 && Math.abs(p.direction().horizontalDistance() - 1) <= 1e-7
                && Double.isFinite(p.remaining()) && p.remaining() >= 0 && p.remaining() <= MAX_RANGE
                && p.remainingTicks() >= 0 && p.remainingTicks() <= MAX_TICKS
                && p.issuedAt() >= 0 && p.issuedAt() <= Long.MAX_VALUE - PERMISSION_TTL
                && p.expiresAt() == p.issuedAt() + PERMISSION_TTL
                && (!p.terminal() || p.remaining() == 0);
    }
    private static boolean fresh(MagneticGroundStep.Assist p) {
        long now = world.getGameTime();
        return now >= p.issuedAt() - 1 && now <= p.expiresAt();
    }
    private static Stamp stamp(MagneticGroundStep.Assist p) { return new Stamp(p.entity(), p.input(), p.token(), p.epoch()); }
    private static boolean sameInput(Stamp a, Stamp b) { return a.epoch() == b.epoch() && a.input() == b.input(); }
    private static int compare(Stamp a, Stamp b) {
        int c = Long.compare(a.epoch(), b.epoch());
        if (c == 0) c = Long.compare(a.input(), b.input());
        return c == 0 ? Long.compare(a.token(), b.token()) : c;
    }
    private static void diagnosticGrant(MagneticGroundStep.Assist packet, boolean accepted, String reason) {
        MagneticGroundStep.diagnostic(packet.token(), "client_grant", "accepted=" + accepted + " reason=" + reason
                + " lastLocalTick=" + localTick + " clientGameTime=" + (world == null ? -1 : world.getGameTime())
                + " issuedAt=" + packet.issuedAt() + " expiresAt=" + packet.expiresAt());
    }
    private static void diagnostic(Session s, String stage, String reason) {
        MagneticGroundStep.diagnostic(s.stamp.token(), stage, "reason=" + reason
                + " permissions=" + s.permissions + " applied=" + s.applied
                + " spent=" + s.spent + " budget=" + s.budget + " lastLocalTick=" + localTick
                + " clientGameTime=" + (world == null ? -1 : world.getGameTime())
                + " issuedAt=" + s.permission.issuedAt() + " expiresAt=" + s.permission.expiresAt());
    }
    private static void diagnosticRetirement(Session s, String reason) {
        if (s == null || s.retirementLogged) return;
        s.retirementLogged = true;
        diagnostic(s, "client_retired", reason);
    }
    private static void retire(String reason) { diagnosticRetirement(session, reason); retired = true; session = null; }
    public static void abortLocal() { synchronize(Minecraft.getInstance()); if (session != null) retire("local_abort"); }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (player == null || input <= 0 || !AcademyClient.singleOwnerEpochMatches(epoch)) return;
        Stamp next = new Stamp(player.getId(), input, Long.MAX_VALUE, epoch);
        if (canceled == null || compare(next, canceled) > 0) canceled = next;
        if (session != null && compare(session.stamp, next) <= 0) retire("captured_input_abort");
    }
    /** Retain replay fences within the connection, including cancellation before the first packet. */
    public static void clear() { retire("explicit_clear"); generation++; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && player == mc.player && connection == mc.getConnection()) return;
        diagnosticRetirement(session, "world_player_or_connection_changed");
        session = null; fence = canceled = null; retired = false; sequence = maxToken = 0;
        localTick = 0; lastAppliedTick = lastAutoJumpInputTick = -1; autoJumpOffInputs = 0; generation++;
        world = mc.level; player = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible(Minecraft mc) {
        var state = AcademyClient.state;
        return world != null && player != null && connection != null && mc.screen == null && mc.isWindowActive()
                && mc.getCameraEntity() == player && world.getEntity(player.getId()) == player
                && player.isAlive() && !player.isRemoved() && state != null && state.activated && state.overloadFine
                && !state.interfering && !state.consumptionInProgress() && state.category.equals("electromaster")
                && state.level >= 3 && state.level <= 5 && state.learned(MagneticGroundStep.ID)
                && state.presets.currentContains(MagneticGroundStep.ID);
    }
    public static boolean canStartLocal() { return startBlocker().isEmpty(); }
    /** Localization key for a local pre-payment failure; never sends a claim to the server. */
    public static String startBlocker() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        String prefix = "academy.highpower.ground_step.";
        if (!localEligible(mc) || mc.isPaused()) return prefix + "unsupported";
        if (mc.options.autoJump().get()) return prefix + "autojump";
        if (autoJumpOffInputs < 2) return prefix + "settling";
        if (!player.onGround() || player.getDeltaMovement().y > 0) return prefix + "ground";
        Input input = player.input;
        if (!player.isSprinting() || input.forwardImpulse <= 0 || input.jumping || input.shiftKeyDown)
            return prefix + "input";
        if (!normalMovement(player, input)) return prefix + "unsupported";
        float friction = world.getBlockState(player.getBlockPosBelowThatAffectsMyMovement())
                .getFriction(world, player.getBlockPosBelowThatAffectsMyMovement(), player);
        float speed = player.getSpeed();
        if (friction != .6F || !Float.isFinite(speed) || speed <= 0 || speed > .4F) return prefix + "unsupported";
        Vec3 look = player.getLookAngle();
        if (!finite(look) || look.horizontalDistance() < .2) return prefix + "unsupported";
        Vec3 direction = new Vec3(look.x, 0, look.z).normalize();
        Vec3 current = player.getDeltaMovement();
        if (current.x * direction.x + current.z * direction.z < 0) return prefix + "unsupported";
        Vec3 step = predicted(current, nativeInput(input, player.getYRot(), speed, friction), direction, MAX_ADDITION);
        // Pre-payment may conservatively reject a cap-limited cast rather than charge a cast that cannot move.
        return step.horizontalDistance() > MAX_STEP || !safeGroundSweep(player, step) ? prefix + "ground" : "";
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (session != null && (!localEligible(mc) || !AcademyClient.singleOwnerEpochMatches(session.stamp.epoch()))) retire("local_eligibility_or_epoch");
        // Pausing retires unspent permissions rather than banking an impulse for resume.
        if (mc.isPaused()) { if (session != null) retire("paused"); autoJumpOffInputs = 0; return; }
        localTick++;
        if (session != null) {
            if (localTick >= session.deadline) retire("permission_deadline");
            else if (localTick - session.started >= MAX_TICKS) retire("cast_lifetime");
        }
    }

    /** LOWEST runs after ordinary input editing; incompatible later event writers are not supported. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (event.getEntity() != player || player == null || event.getInput() != player.input) return;
        if (mc.isPaused() || mc.options.autoJump().get()) { autoJumpOffInputs = 0; if (session != null) retire("paused_or_autojump"); return; }
        // Native autoJumpTime is private and assigned only 1. Two previous OFF input passes drain it.
        int previousOff = autoJumpOffInputs;
        if (lastAutoJumpInputTick != localTick) {
            autoJumpOffInputs = Math.min(3, autoJumpOffInputs + 1); lastAutoJumpInputTick = localTick;
        }
        Session s = session;
        if (s == null || lastAppliedTick == localTick) return;
        if (!localEligible(mc)) { retire("local_eligibility"); return; }
        if (!AcademyClient.singleOwnerEpochMatches(s.stamp.epoch())) { retire("owner_epoch"); return; }
        if (previousOff < 2) { retire("autojump_settling"); return; }
        if (localTick >= s.deadline) { retire("permission_deadline"); return; }
        if (localTick - s.started >= MAX_TICKS) { retire("cast_lifetime"); return; }
        if (!normalMovement(player, event.getInput())) { retire("input_or_ground"); return; }
        Vec3 position = player.position(), delta = position.subtract(s.previous);
        double travelled = delta.horizontalDistance();
        if (!finite(delta) || travelled > MAX_OBSERVED_STEP || Math.abs(delta.y) > .025) { retire("observed_displacement"); return; }
        s.spent += travelled; s.budget = Math.min(s.budget - travelled, s.limit - s.spent); s.previous = position;
        if (!(s.budget > 0) || s.spent >= s.limit) { retire("distance_budget"); return; }
        // An expired grant never moves the player. Keep only the bounded cast identity/path observation;
        // a later independently fresh, strictly higher server sequence may authorize its own one addition.
        // Permanent cancellation/terminal/lifecycle fences remain handled by retire(), never here.
        if (localTick - s.received >= PERMISSION_TTL || !fresh(s.permission)) {
            diagnostic(s,"client_waiting","expired_grant_no_addition");return;
        }
        if (s.consumedSequence == s.permission.sequence()) return;
        Vec3 current = player.getDeltaMovement();
        if (!finite(current) || current.x * s.direction.x + current.z * s.direction.z < 0) { retire("nonfinite_or_opposing_motion"); return; }
        float friction = world.getBlockState(player.getBlockPosBelowThatAffectsMyMovement())
                .getFriction(world, player.getBlockPosBelowThatAffectsMyMovement(), player);
        float speed = player.getSpeed();
        if (friction != .6F || !Float.isFinite(speed) || speed <= 0 || speed > .4F) { retire("native_speed_or_friction"); return; }
        Vec3 nativeInput = nativeInput(event.getInput(), player.getYRot(), speed, friction);
        if (!finite(nativeInput)) { retire("nonfinite_native_input"); return; }
        double limit = Math.min(MAX_STEP, s.budget);
        Vec3 without = predicted(current, nativeInput, s.direction, 0);
        if (without.horizontalDistance() >= limit) { retire("native_step_at_limit"); return; }
        // Clamp AFTER addition just as LivingEntity.aiStep does, including its .003 discontinuity.
        double amount = Math.min(MAX_ADDITION, MAX_ADDITION / s.direction.horizontalDistance());
        Vec3 predicted = predicted(current, nativeInput, s.direction, amount);
        if (predicted.horizontalDistance() > limit) {
            double low = 0, high = amount;
            for (int i = 0; i < 40; i++) {
                double middle = (low + high) * .5;
                if (predicted(current, nativeInput, s.direction, middle).horizontalDistance() <= limit) low = middle;
                else high = middle;
            }
            amount = low; predicted = predicted(current, nativeInput, s.direction, amount);
        }
        Vec3 changed = new Vec3(current.x + s.direction.x * amount, current.y, current.z + s.direction.z * amount);
        if (!(amount > 1e-9) || !finite(changed) || predicted.horizontalDistance() > limit) { retire("addition_or_prediction_limit"); return; }
        if (!safeGroundSweep(player, predicted)) { retire("unsafe_sweep"); return; }
        // The permission is single-use, but keep its immutable budget/identity for the next server grant.
        lastAppliedTick = localTick; s.consumedSequence = s.permission.sequence();
        player.setDeltaMovement(changed);
        s.applied++;
        if (!s.firstAppliedLogged) {
            s.firstAppliedLogged = true;
            diagnostic(s, "client_applied", "first_addition");
        }
    }
    private static boolean normalMovement(LocalPlayer p, Input input) {
        return p.onGround() && p.isSprinting() && p.isEffectiveAi() && p.getPose() == Pose.STANDING
                && p.yya == 0 && !p.isMovingSlowly() && !p.noPhysics && !p.horizontalCollision && !p.isPassenger()
                && !p.isVehicle() && !p.isSleeping() && !p.isFallFlying() && !p.isAutoSpinAttack()
                && !p.getAbilities().flying && !p.getAbilities().mayfly && !p.isInWaterOrBubble() && !p.isInLava()
                && p.getMaxHeightFluidType().isAir() && !p.onClimbable() && !p.isShiftKeyDown()
                && !p.isUsingItem() && !p.isInPowderSnow && !p.isSwimming() && p.getActiveEffects().isEmpty()
                && p.getFoodData().getFoodLevel() > 6 && !input.jumping && !input.shiftKeyDown
                && Float.isFinite(input.forwardImpulse) && input.forwardImpulse > 0 && input.forwardImpulse <= 1
                && Float.isFinite(input.leftImpulse) && Math.abs(input.leftImpulse) <= 1
                && finite(p.position()) && finite(p.getDeltaMovement()) && p.getDeltaMovement().y <= 0
                && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot());
    }
    private static Vec3 nativeInput(Input input, float yaw, float speed, float friction) {
        Vec3 vector = new Vec3((double)(input.leftImpulse * .98F), 0, (double)(input.forwardImpulse * .98F));
        double length = vector.lengthSqr();
        if (length < 1e-7) return Vec3.ZERO;
        float acceleration = speed * (.21600002F / (friction * friction * friction));
        vector = (length > 1 ? vector.normalize() : vector).scale((double)acceleration);
        float sin = Mth.sin(yaw * ((float)Math.PI / 180F)), cos = Mth.cos(yaw * ((float)Math.PI / 180F));
        return new Vec3(vector.x * cos - vector.z * sin, 0, vector.z * cos + vector.x * sin);
    }
    private static Vec3 predicted(Vec3 current, Vec3 input, Vec3 direction, double addition) {
        double x = current.x + direction.x * addition, z = current.z + direction.z * addition;
        return new Vec3((Math.abs(x) < .003 ? 0 : x) + input.x, 0, (Math.abs(z) < .003 ? 0 : z) + input.z);
    }
    /** Full continuous swept AABB and full support union, not point or corner probes. */
    private static boolean safeGroundSweep(LocalPlayer p, Vec3 step) {
        AABB body = p.getBoundingBox().expandTowards(step).deflate(1e-7);
        AABB support = new AABB(body.minX, p.getY() - .025, body.minZ, body.maxX, p.getY() - .005, body.maxZ);
        AABB area = body.minmax(support);
        if (!loadedArea(area) || !world.getWorldBorder().isWithinBounds(area) || !world.noCollision(p, body)) return false;
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            var state = world.getBlockState(at);
            // These exact vanilla classes add only growth/state/orientation logic, not entity movement callbacks.
            // Their unknown subclasses remain outside this contract; the full support shape is still checked below.
            if (!world.getFluidState(at).isEmpty() || !state.isAir() && (!ordinarySurface(state.getBlock())
                    || state.getBlock().getSpeedFactor() != 1 || state.getBlock().getJumpFactor() != 1
                    || state.getFriction(world, at, p) != .6F)) return false;
        }
        // LocalPlayer's ensuing push-out checks full block columns, wider than the player's body.
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            BlockPos at = BlockPos.containing(p.getX() + x * p.getBbWidth() * .35, p.getY(), p.getZ() + z * p.getBbWidth() * .35);
            AABB column = new AABB(at.getX(), body.minY, at.getZ(), at.getX() + 1, body.maxY, at.getZ() + 1).deflate(1e-7);
            if (!loadedArea(column) || world.collidesWithSuffocatingBlock(p, column)) return false;
        }
        var needed = Shapes.create(support); var supported = Shapes.empty();
        for (var shape : world.getBlockCollisions(p, support)) supported = Shapes.or(supported, shape);
        return !Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST);
    }
    private static boolean ordinarySurface(Block block){
        Class<?> type=block.getClass();
        return type==Block.class||type==GrassBlock.class||type==SnowyDirtBlock.class||type==RotatedPillarBlock.class;
    }
    private static boolean loadedArea(AABB area) {
        if (!Double.isFinite(area.minX) || !Double.isFinite(area.minY) || !Double.isFinite(area.minZ)
                || !Double.isFinite(area.maxX) || !Double.isFinite(area.maxY) || !Double.isFinite(area.maxZ)
                || Math.abs(area.minX) > 30_000_000 || Math.abs(area.maxX) > 30_000_000
                || Math.abs(area.minZ) > 30_000_000 || Math.abs(area.maxZ) > 30_000_000
                || area.getXsize() > 8 || area.getYsize() > 8 || area.getZsize() > 8) return false;
        if (area.minY < world.getMinBuildHeight() || area.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = Mth.floor(area.minX) >> 4; x <= Mth.floor(area.maxX) >> 4; x++)
            for (int z = Mth.floor(area.minZ) >> 4; z <= Mth.floor(area.maxZ) >> 4; z++)
                if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}

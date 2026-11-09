package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.CrossSubstitutionRules;
import cn.academy.port.highpower.core.SafeLandingProbeRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW321: free A/B preparation, then a crouched self-swap or three paid native A/B/A needles. */
@EventBusSubscriber(modid = "academy")
public final class CrossSubstitution {
    public static final String ID = "cross_substitution";
    public static final int MARKED = 1, PROJECTED = 2, IMPACT = 3, ENDED = 4, REFUSED = 5;
    public static final int SWAP_REFUSED = 6, SWAPPED = 7;
    private static final String DATA = "academy:cross_substitution_fences";
    private static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    /** A callback cannot erase the in-flight transport guard by removing its context. */
    private static final Map<ServerPlayer, Context> TRANSPORTS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Integer> POST_CLOCKS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.cross_substitution");
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown, int cast) {
        static final Fences ZERO = new Fences(0, 0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1), Math.max(0, cast - 1)); }
    }
    public record Receipt(long input, long token, boolean paid, int paidNeedles, int published,
                          int contacts, int hurts, int phase, String reason, UUID medium, Vec3 source, Vec3 arrival,
                          boolean selfSwap, boolean swapAdmitted, boolean moved) {}
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouching) {
        static Source capture(ServerPlayer p) { return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(),
                p.getBoundingBox(), p.getPose(), p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown()); }
        boolean matches(ServerPlayer p) { return position.equals(p.position()) && eye.equals(p.getEyePosition())
                && motion.equals(p.getDeltaMovement()) && body.equals(p.getBoundingBox()) && pose == p.getPose()
                && yaw == p.getYRot() && pitch == p.getXRot() && fall == p.fallDistance
                && grounded == p.onGround() && crouching == p.isShiftKeyDown(); }
    }
    private record TargetSnapshot(LivingEntity target, Vec3 position, AABB body, int id, UUID uuid) {
        static TargetSnapshot capture(LivingEntity t) { return new TargetSnapshot(t, t.position(), t.getBoundingBox(), t.getId(), t.getUUID()); }
        boolean matches() { return position.equals(target.position()) && body.equals(target.getBoundingBox())
                && id == target.getId() && uuid.equals(target.getUUID()) && target.isAlive() && !target.isRemoved(); }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset; final long revision;
        HighPowerInput.Input command;
        SafeLandingProbeWorld.CrossPoint a, b;
        CrossSubstitutionWorld.Plan plan;
        SafeLandingProbeWorld.CrossSwapCandidate swapCandidate;
        ItemStack hand; int originalCount;
        BigDecimal damage; double totalCost; int cooldown;
        int remaining = CrossSubstitutionRules.PREPARATION_TICKS, nextSlot, delay, paid, published, contacts, hurts;
        int transactionSlot = -1;
        long lastTick, lastVisual, sequence;
        boolean processing, closing, terminal, paying;
        boolean selfSwap, swapping, swapAdmitted, swapPaid, moved;
        Vec3 source, arrival;
        final Flight[] flights = new Flight[3];
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = arrival = p.getEyePosition(); lastTick = now(p); lastVisual = lastTick;
        }
    }
    private static final class Flight {
        final Context context; final int slot; final DisplacedNeedleEntity medium;
        final DisplacedNeedleEntity.Identity identity; final Vec3 source, origin;
        Vec3 last; double traveled; int age; boolean closed; long bornTick;
        EmissionPermit claim;
        Flight(Context c, int slot, DisplacedNeedleEntity medium, Vec3 origin) {
            context = c; this.slot = slot; this.medium = medium; identity = medium.identity();
            source = c.source; this.origin = origin; last = origin; bornTick = now(c.identity.owner());
        }
    }
    /** Unforgeable authority for exactly one native first-contact segment and frozen .5D share. */
    public static final class EmissionPermit {
        private final Flight flight; private final LivingEntity target; private final Vec3 origin, endpoint;
        private final Source source; private final TargetSnapshot targetSnapshot; private boolean consumed;
        private EmissionPermit(Flight f, LivingEntity target, Vec3 origin, Vec3 endpoint) {
            flight = f; this.target = target; this.origin = origin; this.endpoint = endpoint;
            source = Source.capture(player()); targetSnapshot = TargetSnapshot.capture(target);
        }
        public ServerPlayer player() { return flight.context.identity.owner(); }
        public LivingEntity target() { return target; }
        public BigDecimal damage() { return flight.context.damage; }
        public Vec3 origin() { return origin; }
        public Vec3 endpoint() { return endpoint; }
        public DisplacedNeedleEntity needle() { return flight.medium; }
        public boolean consume() { if (consumed) return false; consumed = true; return true; }
        private boolean unchanged() {
            return consumed && flight.claim == this && validFlight(flight) && target == flight.context.plan.target()
                    && source.matches(player()) && targetSnapshot.matches()
                    && origin.equals(needle().contactSegmentOrigin()) && endpoint.equals(needle().contactPoint())
                    && endpoint.equals(needle().position()) && origin.equals(flight.last)
                    && CrossSubstitutionRules.path(player().getEyePosition().distanceTo(flight.origin),
                            flight.traveled + origin.distanceTo(endpoint));
        }
        public boolean valid() {
            return unchanged() && CrossSubstitutionWorld.sameTarget(flight.context.plan)
                    && CrossSubstitutionWorld.contactClear(player(), origin, endpoint, target, needle()) && unchanged();
        }
    }
    private CrossSubstitution() {}
    public static boolean start(ServerPlayer p, String wire) { return press(p, wire); }
    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p) || !mapped(p, input.slot())
                || !finite(p.getEyePosition()) || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return false;
        Context c = CONTEXTS.get(p.getUUID());
        var previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        // A fresh reentrant edge is rejected and remembered, never queued for after callbacks.
        if (TRANSPORTS.containsKey(p) || c != null && c.processing) return false;
        if (c != null && (!identity(c) || c.command.epoch() != input.epoch() || c.command.slot() != input.slot())) {
            finish(c, ENDED, "context_changed"); return false;
        }
        if (c != null && c.plan != null) return false;
        Fences fences = readFences(p);
        if (c == null && !p.isShiftKeyDown() && fences != null && fences.request > 0) return false;
        if (c == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= 64) return false;
            c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        }
        c.command = input;
        c.selfSwap = p.isShiftKeyDown();
        if (c.selfSwap) return swap(c);
        if (fences == null) return refuse(c, "storage_invalid");
        if (fences.request > 0) { if (c.a == null) finish(c, REFUSED, "request_fence"); return false; }
        writeFences(p, new Fences(CrossSubstitutionRules.REQUEST_FENCE, fences.cooldown, fences.cast));
        if (c.a == null || c.b == null) return prepare(c);
        return begin(c);
    }
    /** The passive pair is spent only after a complete, affordable movement preflight.
     * From admission onward every outcome is terminal, including cancelled native events. */
    private static boolean swap(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; c.swapping = true;
        TRANSPORTS.put(p, c);
        try {
            Source before = Source.capture(p); c.source = c.arrival = before.position;
            if (c.a == null || c.b == null) return refuseSwap(c, "two_points_required");
            if (c.remaining <= 0) return refuse(c, "expired");
            String reason = swapEligibility(c);
            if (!reason.isEmpty()) return refuseSwap(c, reason);
            Fences fences = readFences(p);
            int shared = SafeLandingProbe.sharedMovementRemaining(p);
            if (fences == null || shared < 0) return refuseSwap(c, "storage_invalid");
            if (fences.request > 0) return refuseSwap(c, "request_fence");
            writeFences(p, new Fences(CrossSubstitutionRules.REQUEST_FENCE, fences.cooldown, fences.cast));
            if (fences.cooldown > 0 || fences.cast > 0 || shared > 0 || !c.identity.state().canUse(ID))
                return refuseSwap(c, "cooldown");
            var assessment = SafeLandingProbeWorld.crossSwap(p, c.a, c.b);
            if (!assessment.valid()) return refuseSwap(c, assessment.reason());
            var candidate = assessment.candidate(); c.arrival = candidate.destination();
            reason = swapControlRefusal(c);
            if (!reason.isEmpty()) return refuseSwap(c, reason);
            fences = readFences(p); shared = SafeLandingProbe.sharedMovementRemaining(p);
            if (fences == null || shared < 0) return refuseSwap(c, "storage_invalid");
            if (fences.cooldown > 0 || fences.cast > 0 || shared > 0) return refuseSwap(c, "cooldown");
            BigDecimal growth = HighPowerGrowth.values(p).position();
            double cost = CrossSubstitutionRules.cpCost(growth);
            int cooldown = CrossSubstitutionRules.cooldownTicks(growth);
            reason = swapResourceRefusal(c, cost);
            if (!reason.isEmpty()) return refuseSwap(c, reason);
            if (!swapUnchanged(c, before) || !candidate.sourcePosition().equals(before.position))
                return refuseSwap(c, "event_changed_state");

            // Keep A/B only as immutable diagnostic coordinates for the terminal packet.
            // No public controller route can use or expose the consumed pair after this line.
            c.swapCandidate = candidate; c.swapAdmitted = true; c.remaining = 0;
            Vec3 destination = candidate.destination();
            EntityTeleportEvent event = new EntityTeleportEvent(p, destination.x, destination.y, destination.z);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return refuse(c, "event_cancelled");
            if (Double.compare(event.getTargetX(), destination.x) != 0
                    || Double.compare(event.getTargetY(), destination.y) != 0
                    || Double.compare(event.getTargetZ(), destination.z) != 0) return refuse(c, "event_redirected");
            if (!swapUnchanged(c, before)) return refuse(c, "event_changed_state");
            reason = swapEligibility(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            var check = SafeLandingProbeWorld.revalidateCrossSwap(p, candidate);
            if (!check.valid()) return refuse(c, check.reason());
            reason = swapControlRefusal(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            if (!swapUnchanged(c, before) || HighPowerGrowth.values(p).position().compareTo(growth) != 0)
                return refuse(c, "event_changed_state");
            fences = readFences(p); shared = SafeLandingProbe.sharedMovementRemaining(p);
            if (fences == null || shared < 0) return refuse(c, "storage_invalid");
            if (fences.cooldown > 0 || fences.cast > 0 || shared > 0) return refuse(c, "cooldown");
            reason = swapResourceRefusal(c, cost);
            if (!reason.isEmpty()) return refuse(c, reason);
            // Final owner and source reads follow every callback-capable world assessment.
            if (!swapUnchanged(c, before)) return refuse(c, "event_changed_state");
            // Closed commit: exactly one whole CP charge, both physical fences, one native move.
            if (!HighPowerResources.trySpend(p, ID, cost, cooldown)) return refuse(c, "insufficient_cp");
            c.swapPaid = true;
            writeFences(p, new Fences(fences.request, cooldown, CrossSubstitutionRules.CAST_FENCE));
            SafeLandingProbe.commitSharedMovement(p);
            p.connection.teleport(destination.x, destination.y, destination.z,
                    before.yaw, before.pitch, RelativeMovement.ROTATION);
            c.moved = p.serverLevel() == c.identity.level() && p.position().equals(destination);
            if (!c.moved || !p.getDeltaMovement().equals(before.motion)
                    || Float.compare(p.fallDistance, before.fall) != 0 || p.onGround() != before.grounded)
                return refuse(c, "commit_unconfirmed");
            finish(c, SWAPPED, "");
            try {
                if (c.identity.owns(p) && !p.hasDisconnected()) { AbilityStorage.save(p); AcademyNetwork.sync(p); }
            } catch (RuntimeException failure) { LOG.warn("NEW321 self-swap succeeded; final sync failed without replay or refund", failure); }
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW321 self-swap ended without replay or refund; admitted={}, paid={}", c.swapAdmitted, c.swapPaid, failure);
            observeSwapDestination(c);
            return c.swapAdmitted ? refuse(c, c.swapPaid ? "transport_unconfirmed" : "validation_failed")
                    : refuseSwap(c, "validation_failed");
        } catch (Error failure) {
            // Callbacks may have run irreversible work before throwing. Close the operation
            // and retain debit/fences before preserving the native failure, never retry it.
            try { observeSwapDestination(c); finish(c, REFUSED, c.swapPaid ? "transport_unconfirmed" : "validation_failed"); }
            finally { throw failure; }
        } finally {
            try { if (!c.terminal && (c.swapAdmitted || c.closing)) finish(c, REFUSED, "context_changed"); }
            finally { c.swapping = false; c.processing = false; TRANSPORTS.remove(p, c); }
        }
    }
    private static boolean swapUnchanged(Context c, Source source) {
        return c.swapping && c.processing && TRANSPORTS.get(c.identity.owner()) == c
                && !c.closing && identity(c) && c.plan == null && noFlights(c)
                && source.matches(c.identity.owner());
    }
    private static String swapResourceRefusal(Context c, double cost) {
        ServerPlayer p = c.identity.owner();
        if (HighPowerResources.hasReservation(p)) return "resource_busy";
        if (!HighPowerResources.ready(p) || !c.identity.state().canUse(ID)) return "cooldown";
        if (p.serverLevel().getGameTime() < p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID))
            return "cooldown";
        return !Double.isFinite(cost) || cost <= 0 || HighPowerResources.availableCp(p) < cost ? "insufficient_cp" : "";
    }
    private static String swapEligibility(Context c) {
        String reason = swapControlRefusal(c);
        return reason.isEmpty() ? SafeLandingProbeWorld.sourceRefusal(c.identity.owner()) : reason;
    }
    /** No geometry reads: run again after the last callback-capable shape assessment. */
    private static String swapControlRefusal(Context c) {
        ServerPlayer p = c.identity.owner();
        if (!eligible(c) || c.plan != null || !noFlights(c)) return "not_ready";
        if (!p.isShiftKeyDown()) return "swap_requires_crouch";
        if (!p.onGround() || !Float.isFinite(p.fallDistance) || p.fallDistance != 0
                || !SafeLandingProbeRules.groundedVerticalMotion(p.getDeltaMovement().y)) return "source_airborne";
        // Only cached native entity flags here. A late collision-shape callback may change
        // them after source() ran; avoid loading onClimbable/in-block lookups at the final seam.
        if (p.noPhysics || p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.isInWater() || p.isInLava() || p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING
                || !finite(p.position()) || !finite(p.getDeltaMovement())
                || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return "source_motion_or_pose_unsupported";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                || p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) return "movement_conflict";
        if (CornerExchange.active(p) || SafeLandingProbe.active(p) || ReturnMark.active(p) || PhaseSidestep.active(p) || (BeamStep.active(p)||WindPressureLift.active(p))
                || MagneticGroundStep.active(p) || PolarityTether.active(p)
                || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.LocationTeleport.active(p) || cn.academy.port.skill.Flashing.active(p))
            return "movement_conflict";
        return "";
    }
    private static void observeSwapDestination(Context c) {
        ServerPlayer p = c.identity.owner();
        if (c.swapPaid && c.swapCandidate != null)
            c.moved |= p.serverLevel() == c.identity.level() && p.position().equals(c.swapCandidate.destination());
    }
    /** Ordinary movement preflight failures retain the existing marks and their original TTL. */
    private static boolean refuseSwap(Context c, String reason) {
        if (c.swapAdmitted || c.closing || !identity(c)) return refuse(c, reason);
        if (c.a == null) { finish(c, SWAP_REFUSED, reason); return false; }
        record(c, SWAP_REFUSED, reason, null);
        try { emit(c, SWAP_REFUSED, null, c.arrival, false, reason); }
        catch (RuntimeException failure) { LOG.warn("NEW321 self-swap refusal retained marks but visual delivery failed", failure); }
        return false;
    }
    private static boolean prepare(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true;
        try {
            Source source = Source.capture(p);
            if (!eligible(c)) return refuse(c, "not_ready");
            if (c.a != null && !CrossSubstitutionWorld.point(p, c.a, null).isEmpty()) return refuse(c, "point_changed");
            var assessment = SafeLandingProbeWorld.crossPoint(p);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            if (c.a != null && source.body.move(c.a.destination().subtract(source.position))
                    .intersects(source.body.move(assessment.point().destination().subtract(source.position))))
                return refuse(c, "points_overlap");
            String reason = CrossSubstitutionWorld.point(p, assessment.point(), null);
            if (!reason.isEmpty()) return refuse(c, reason);
            if (!identity(c) || c.closing || !source.matches(p)) return refuse(c, "transaction_changed");
            if (c.a == null) c.a = assessment.point(); else c.b = assessment.point();
            c.source = source.eye; c.arrival = CrossSubstitutionWorld.origin(assessment.point());
            record(c, MARKED, "", null); emit(c, MARKED, null, c.arrival, true, ""); return true;
        } catch (RuntimeException failure) { LOG.warn("NEW321 point assessment failed", failure); return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
    }
    private static boolean begin(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true;
        try {
            Source source = Source.capture(p); Fences fences = readFences(p);
            if (!eligible(c) || c.remaining <= 0) return refuse(c, "not_ready");
            if (fences == null || fences.cooldown > 0 || fences.cast > 0 || !c.identity.state().canUse(ID)) return refuse(c, "cooldown");
            var availability = CrossNeedleRegistry.availability(p);
            if (availability == CrossNeedleRegistry.Availability.REFUND_PENDING)
                return refuse(c, CrossNeedleRegistry.retryRefund(p) ? "medium_returned" : "refund_pending");
            if (availability != CrossNeedleRegistry.Availability.READY) return refuse(c, "medium_unavailable");
            ItemStack hand = p.getOffhandItem();
            if (!plain(hand) || hand.getCount() < 3) return refuse(c, "three_needles_required");
            var assessment = CrossSubstitutionWorld.acquire(p);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            var plan = assessment.plan(); TargetSnapshot target = TargetSnapshot.capture(plan.target());
            String a = CrossSubstitutionWorld.validate(plan, c.a, null), b = CrossSubstitutionWorld.validate(plan, c.b, null);
            if (!a.isEmpty() || !b.isEmpty()) return refuse(c, !a.isEmpty() ? a : b);
            if (!identity(c) || c.closing || !source.matches(p) || !target.matches() || p.getOffhandItem() != hand
                    || !plain(hand) || hand.getCount() < 3) return refuse(c, "transaction_changed");
            var growth = HighPowerGrowth.values(p);
            c.damage = CrossSubstitutionRules.damage(growth.damage()); c.totalCost = CrossSubstitutionRules.cpCost(growth.position());
            c.cooldown = CrossSubstitutionRules.cooldownTicks(growth.position()); c.plan = plan;
            c.hand = hand; c.originalCount = hand.getCount(); c.remaining = 0;
            // Only observed future server ticks may advance the next two publications.
            c.lastTick = now(p);
        } catch (RuntimeException failure) { LOG.warn("NEW321 acquisition failed", failure); return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
        return !c.terminal && launch(c);
    }
    private static boolean launch(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true;
        int slot = c.nextSlot; c.transactionSlot = slot;
        try {
            if (slot < 0 || slot >= 3 || c.paid != slot || c.published != slot) return refuse(c, "transaction_changed");
            Source source = Source.capture(p); TargetSnapshot target = TargetSnapshot.capture(c.plan.target());
            var point = CrossSubstitutionRules.usesA(slot) ? c.a : c.b;
            Vec3 origin = CrossSubstitutionWorld.origin(point);
            c.source = source.eye; c.arrival = origin;
            if (!publicationValid(c, source, target, slot)) return refuse(c, "transaction_changed");
            var deployed = CrossNeedleRegistry.deploy(p, origin,
                    () -> publicationValid(c, source, target, slot), () -> pay(c, source, target, slot));
            if (deployed.isEmpty()) return refuse(c, c.paid > slot ? "publication_unconfirmed" : "spawn_rejected");
            DisplacedNeedleEntity medium = deployed.get(); c.published++; c.nextSlot++; c.delay = CrossSubstitutionRules.LAUNCH_INTERVAL;
            Flight flight = new Flight(c, slot, medium, origin); c.flights[slot] = flight; FLIGHTS.put(medium.getUUID(), flight);
            medium.setDeltaMovement(c.plan.endpoint().subtract(origin).normalize().scale(CrossSubstitutionRules.SPEED));
            if (!medium.beginFlight(flight.identity)) return refuse(c, "publication_unconfirmed");
            record(c, PROJECTED, "", medium); emit(c, PROJECTED, flight, c.plan.endpoint(), false, "");
            AbilityStorage.save(p); AcademyNetwork.sync(p); return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW321 publication ended without replay or refund; paid={}", c.paid, failure);
            return refuse(c, c.paid > slot ? "publication_unconfirmed" : "validation_failed");
        } catch (Error failure) {
            // Registry custody has already quarantined an uncertain pending original.
            // Retire every older flight and the schedule before preserving the native failure.
            try { finish(c, REFUSED, c.paid > slot ? "publication_unconfirmed" : "validation_failed"); }
            finally { throw failure; }
        } finally {
            c.paying = false; c.transactionSlot = -1; c.processing = false;
            if (c.closing && !c.terminal) finish(c, ENDED, "context_changed");
        }
    }
    private static boolean publicationValid(Context c, Source source, TargetSnapshot target, int slot) {
        ServerPlayer p = c.identity.owner();
        if (!publicationState(c, source, target, slot) || HighPowerResources.hasReservation(p)) return false;
        DisplacedNeedleEntity pending = CrossNeedleRegistry.pending(p);
        int needed = c.originalCount - c.published - (pending == null ? 0 : 1);
        if (p.getOffhandItem() != c.hand || c.hand.getCount() != needed || needed > 0 && !plain(c.hand)) return false;
        var point = CrossSubstitutionRules.usesA(slot) ? c.a : c.b;
        if (!CrossSubstitutionWorld.validate(c.plan, point, pending).isEmpty()) return false;
        return publicationState(c, source, target, slot) && p.getOffhandItem() == c.hand && c.hand.getCount() == needed;
    }
    private static boolean publicationState(Context c, Source source, TargetSnapshot target, int slot) {
        return c.processing && c.transactionSlot == slot && c.nextSlot == slot && c.paid == slot && c.published == slot
                && eligible(c) && !c.closing && source.matches(c.identity.owner()) && target.matches();
    }
    /** Invoked after the registry's final callback-capable guard and native identity checks. */
    private static boolean pay(Context c, Source source, TargetSnapshot target, int slot) {
        ServerPlayer p = c.identity.owner();
        if (c.terminal || c.closing || !c.processing || c.transactionSlot != slot || c.paid != slot || c.nextSlot != slot
                || CONTEXTS.get(p.getUUID()) != c || !c.identity.owns(p) || !source.matches(p) || !target.matches()
                || p.getOffhandItem() != c.hand || c.hand.getCount() != c.originalCount - slot - 1) return false;
        Fences fences = readFences(p);
        if (fences == null || slot == 0 && (fences.cooldown > 0 || fences.cast > 0)) return false;
        c.paying = true;
        try {
            if (!HighPowerResources.trySpendCrossSubstitutionNeedle(p, CrossSubstitutionRules.cpShare(c.totalCost, slot), c.cooldown, slot == 0)) return false;
            c.paid++;
            if (slot == 0) writeFences(p, new Fences(fences.request, c.cooldown, CrossSubstitutionRules.CAST_FENCE));
            return true;
        } finally { c.paying = false; }
    }
    static boolean payingNeedle(ServerPlayer p) {
        Context c = p == null ? null : CONTEXTS.get(p.getUUID());
        return c != null && c.identity.owner() == p && c.paying && c.processing && !c.closing && !c.terminal
                && c.transactionSlot == c.nextSlot && c.nextSlot >= 0 && c.nextSlot < 3 && c.paid == c.nextSlot;
    }
    private static boolean identity(Context c) {
        ServerPlayer p = c.identity.owner();
        return !c.terminal && CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && mapped(p, c.command.slot())
                && c.identity.state().presets.current() == c.preset && c.identity.state().presets.revision() == c.revision;
    }
    private static boolean eligible(Context c) {
        ServerPlayer p = c.identity.owner(); var s = c.identity.state();
        return identity(c) && !c.closing && p.isAlive() && !p.isRemoved() && !p.hasDisconnected()
                && !p.isCreative() && !p.isSpectator() && !p.getAbilities().invulnerable && p.containerMenu == p.inventoryMenu
                && s.activated && s.overloadFine && !s.interfering && !s.consumptionInProgress() && !AbilityConsumption.busy(p)
                && s.learned(ID) && s.learned(HighPowerSkills.SAFE_LANDING_PROBE) && s.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && SkillCatalog.enabled("teleporter", ID) && HighPowerResources.ready(p)
                && HighPowerResources.effectiveLevel(p) >= 2 && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean mapped(ServerPlayer p, int slot) {
        return p != null && p.server.isSameThread() && slot >= 0 && slot < 4
                && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
                && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));
    }
    private static boolean validFlight(Flight f) {
        return f != null && !f.closed && f.age < CrossSubstitutionRules.FLIGHT_TICKS && !f.context.terminal
                && FLIGHTS.get(f.medium.getUUID()) == f && f.context.flights[f.slot] == f && eligible(f.context)
                && f.medium.identity() != null && f.medium.identity().equals(f.identity)
                && f.medium.getOwner() == f.context.identity.owner() && f.medium.level() == f.context.plan.world()
                && !f.medium.isRemoved() && f.medium.isAdmitted() && CrossNeedleRegistry.authentic(f.context.identity.owner(), f.medium);
    }
    static boolean canContact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity id, Entity target) {
        Flight f = FLIGHTS.get(medium.getUUID());
        return f != null && f.medium == medium && id.equals(f.identity) && target == f.context.plan.target() && validFlight(f);
    }
    static void contact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity id, EntityHitResult hit) {
        Flight f = FLIGHTS.get(medium.getUUID()); if (f == null || f.medium != medium) return;
        boolean hurt = false;
        try {
            Vec3 origin = medium.contactSegmentOrigin();
            if (!canContact(medium, id, hit.getEntity()) || origin == null || !origin.equals(f.last)
                    || !(hit.getEntity() instanceof LivingEntity target) || !CrossNeedleRegistry.consumeContact(medium, id.generation())) return;
            f.context.contacts++;
            EmissionPermit permit = new EmissionPermit(f, target, origin, hit.getLocation()); f.claim = permit;
            hurt = HighPowerCombat.attackCrossSubstitution(permit); if (hurt) f.context.hurts++;
        } catch (RuntimeException | Error failure) {
            // Damage callbacks may throw after irreversible native work. Preserve that
            // result and all original custody, but never continue the uncertain volley.
            try { finish(f.context, ENDED, "contact_unconfirmed"); }
            finally { throw failure; }
        } finally { f.claim = null; finishFlight(f, IMPACT, "", hurt, hit.getLocation()); }
    }
    static void retire(DisplacedNeedleEntity medium) {
        if (medium == null) return;
        Flight f = FLIGHTS.get(medium.getUUID());
        if (f != null && f.medium == medium) finishFlight(f, IMPACT, "flight_ended", false, medium.position());
        CrossNeedleRegistry.retireContact(medium);
    }
    private static void finishFlight(Flight f, int phase, String reason, boolean hurt, Vec3 impact) {
        if (f.closed) return; f.closed = true; FLIGHTS.remove(f.medium.getUUID(), f); f.context.flights[f.slot] = null;
        f.medium.markHarmless(f.medium.identity()); CrossNeedleRegistry.retireContact(f.medium);
        if (!f.context.terminal) {
            record(f.context, phase, reason, f.medium);
            try { emit(f.context, phase, f, impact, hurt, reason); } catch (RuntimeException ignored) {}
            if (!f.context.processing && f.context.nextSlot >= 3 && noFlights(f.context)) finish(f.context, ENDED, "");
        }
    }
    private static boolean noFlights(Context c) { for (Flight f : c.flights) if (f != null) return false; return true; }
    private static boolean refuse(Context c, String reason) { finish(c, REFUSED, reason); return false; }
    private static void finish(Context c, int phase, String reason) {
        if (c.terminal) return; c.closing = true; c.terminal = true; c.remaining = 0;
        CONTEXTS.remove(c.identity.owner().getUUID(), c);
        for (Flight f : c.flights.clone()) if (f != null) finishFlight(f, IMPACT, reason, false, f.medium.position());
        record(c, phase, reason, null);
        try { emit(c, phase, null, c.arrival, phase == SWAPPED, reason); } catch (RuntimeException ignored) {}
    }
    private static void record(Context c, int phase, String reason, DisplacedNeedleEntity medium) {
        ServerPlayer p = c.identity.owner();
        if (p.isRemoved() || p.hasDisconnected() || p.server.getPlayerList().getPlayer(p.getUUID()) != p) return;
        RECEIPTS.put(p, new Receipt(c.command.input(), c.identity.token(), c.paid > 0 || c.swapPaid, c.paid, c.published,
                c.contacts, c.hurts, phase, reason, medium == null ? null : medium.getUUID(), c.source, c.arrival,
                c.selfSwap, c.swapAdmitted, c.moved));
    }
    public static Receipt receipt(ServerPlayer p) { return RECEIPTS.get(p); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && (c.processing || c.plan != null); }
    /** Movement conflicts use this narrow guard; passive marks and an attack volley are not transport. */
    public static boolean isTransporting(ServerPlayer p) { return p != null && TRANSPORTS.containsKey(p); }
    public static boolean marked(ServerPlayer p) { Context c = owned(p); return c != null && !c.swapAdmitted && c.a != null && c.plan == null; }
    public static int preparedPoints(ServerPlayer p) { Context c = owned(p); return c == null || c.swapAdmitted || c.a == null ? 0 : c.b == null ? 1 : 2; }
    public static int remainingTicks(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.remaining; }
    /** Shared bracer/runtime entry; duplicate observations of the same physical tick are inert. */
    public static void tick(ServerPlayer p) { Context c = owned(p); if (c != null && p.server.isSameThread()) tick(c); }
    private static Context owned(ServerPlayer p) { Context c = p == null ? null : CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    public static boolean release(ServerPlayer p, String wire) { return HighPowerBracerSession.authenticated(p, HighPowerInput.parse(wire)); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var previous = INPUTS.get(p.getUUID());
        if (previous == null || previous.owner != p || previous.epoch != input.epoch() || input.input() > previous.input)
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.command.epoch() != input.epoch() || c.command.slot() != input.slot() || input.input() < c.command.input()) return false;
        c.command = input; cancelActive(p); return true;
    }
    public static void cancelActive(ServerPlayer p) {
        Context c = owned(p); if (c == null || !p.server.isSameThread()) return;
        c.closing = true; if (!c.processing) finish(c, ENDED, "context_changed");
    }
    public static void clear(ServerPlayer p) { cancelActive(p); }
    private static boolean plain(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.is(AcademyCraft.NEEDLE.get()) && stack.isComponentsPatchEmpty(); }
    private static boolean finite(Vec3 v) { return CrossSubstitutionWorld.finite(v); }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }
    private static Fences readFences(ServerPlayer p) {
        var root = p.getPersistentData(); if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null; var t = root.getCompound(DATA);
        if (!t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1 || !t.contains("request", Tag.TAG_INT)
                || !t.contains("cooldown", Tag.TAG_INT) || !t.contains("cast", Tag.TAG_INT)) return null;
        int r = t.getInt("request"), d = t.getInt("cooldown"), f = t.getInt("cast");
        return r < 0 || r > 4 || d < 0 || d > 60 || f < 0 || f > 8 ? null : new Fences(r, d, f);
    }
    private static void writeFences(ServerPlayer p, Fences fences) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putInt("request", fences.request);
        t.putInt("cooldown", fences.cooldown); t.putInt("cast", fences.cast); p.getPersistentData().put(DATA, t);
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e) {
        Integer previous = CLOCKS.put(e.getServer(), e.getServer().getTickCount());
        if (previous != null && previous == e.getServer().getTickCount()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) if (p.getPersistentData().contains(DATA)) {
            Fences fences = readFences(p); if (fences != null && !fences.equals(Fences.ZERO)) writeFences(p, fences.advance());
        }
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c);
    }
    private static void tick(Context c) {
        try { advance(c); }
        catch (RuntimeException failure) {
            LOG.warn("NEW321 observed tick ended without resuming its schedule", failure);
            finish(c, ENDED, "validation_failed");
        }
        catch (Error failure) { try { finish(c, ENDED, "validation_failed"); } finally { throw failure; } }
    }
    private static void advance(Context c) {
        ServerPlayer p = c.identity.owner(); if (c.processing || c.lastTick == now(p)) return; c.lastTick = now(p);
        if (!eligible(c)) { finish(c, ENDED, "context_changed"); return; }
        if (c.plan == null) {
            if (--c.remaining <= 0) { finish(c, ENDED, "expired"); return; }
            if (now(p) < c.lastVisual || now(p) - c.lastVisual >= 10)
                try { emit(c, MARKED, null, c.arrival, true, ""); } catch (RuntimeException failure) { finish(c, ENDED, "visual_unavailable"); }
            return;
        }
        for (Flight f : c.flights.clone()) if (f != null && f.bornTick != now(p)) {
            f.age++;
            if (!validFlight(f)) finishFlight(f, IMPACT, "flight_ended", false, f.medium.position());
        }
        if (c.terminal) return;
        if (!CrossSubstitutionWorld.sameTarget(c.plan)) { finish(c, ENDED, "target_changed"); return; }
        if (c.nextSlot < 3 && --c.delay <= 0) launch(c);
        else if (c.nextSlot >= 3 && noFlights(c)) finish(c, ENDED, "");
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post e) {
        Integer previous = POST_CLOCKS.put(e.getServer(), e.getServer().getTickCount());
        if (previous != null && previous == e.getServer().getTickCount()) return;
        for (Flight f : new ArrayList<>(FLIGHTS.values())) if (f.context.identity.owner().server == e.getServer()) {
            Vec3 position = f.medium.position();
            if (!validFlight(f) || !finite(position)) { finishFlight(f, IMPACT, "flight_ended", false, f.last); continue; }
            f.traveled += f.last.distanceTo(position); f.last = position;
            if (!CrossSubstitutionRules.path(f.context.identity.owner().getEyePosition().distanceTo(f.origin), f.traveled)
                    || f.medium.inGround || !f.medium.isContactEnabled()) finishFlight(f, IMPACT, "flight_ended", false, position);
        }
    }
    private static void remove(ServerPlayer p) {
        cancelActive(p); INPUTS.computeIfPresent(p.getUUID(), (id, w) -> w.owner == p ? null : w); RECEIPTS.remove(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p && !e.isCanceled()) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer p) {
            remove(old); if (old.getPersistentData().contains(DATA)) p.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c, ENDED, "server_stopped");
        INPUTS.entrySet().removeIf(x -> x.getValue().owner.server == e.getServer());
        RECEIPTS.keySet().removeIf(p -> p.server == e.getServer()); CLOCKS.remove(e.getServer()); POST_CLOCKS.remove(e.getServer());
    }
    private static void emit(Context c, int phase, Flight flight, Vec3 impact, boolean accepted, String reason) {
        ServerPlayer p = c.identity.owner(); c.lastVisual = now(p);
        Vec3 a = c.a == null ? Vec3.ZERO : c.a.destination(), b = c.b == null ? Vec3.ZERO : c.b.destination();
        int prepared = c.a == null ? 0 : c.b == null ? 1 : 2;
        Visual packet = new Visual(p.getId(), p.getUUID(), c.command.input(), c.command.epoch(), c.identity.token(), ++c.sequence,
                phase, prepared, c.plan == null && !c.terminal ? c.remaining : 0, a, b, flight == null ? c.source : flight.source,
                flight == null ? c.arrival : flight.origin, impact, flight == null ? -1 : flight.slot,
                flight == null ? -1 : flight.medium.getId(), flight == null ? NONE : flight.medium.getUUID(), accepted, reason);
        if (!p.hasDisconnected()) PacketDistributor.sendToPlayer(p, packet);
        int sent = 1;
        for (ServerPlayer observer : p.serverLevel().players())
            if (observer != p && !observer.hasDisconnected() && observer.position().distanceToSqr(c.arrival) <= 64 * 64) {
                PacketDistributor.sendToPlayer(observer, packet); if (++sent >= 32) break;
            }
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         int prepared, int remaining, Vec3 pointA, Vec3 pointB, Vec3 source, Vec3 arrival, Vec3 impact,
                         int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0 || phase < 1 || phase > SWAPPED
                    || prepared < 0 || prepared > 2 || remaining < 0 || remaining > CrossSubstitutionRules.PREPARATION_TICKS || needleSlot < -1 || needleSlot > 2
                    || needleEntity < -1 || needleUuid == null || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || !finite(pointA) || !finite(pointB) || !finite(source) || !finite(arrival) || !finite(impact)
                    || accepted && phase != MARKED && phase != IMPACT && phase != SWAPPED
                    || phase == MARKED && (prepared < 1 || remaining < 1 || !accepted)
                    || phase == SWAP_REFUSED && (accepted || prepared == 0 && remaining != 0 || prepared > 0 && remaining < 1
                            || needleSlot != -1 || needleEntity != -1 || !needleUuid.equals(NONE))
                    || phase == SWAPPED && (!accepted || prepared != 2 || remaining != 0 || needleSlot != -1 || needleEntity != -1
                            || !needleUuid.equals(NONE) || !impact.equals(arrival) || !CrossSubstitutionRules.swapDistance(source.distanceTo(arrival)))
                    || (phase == PROJECTED || phase == IMPACT) && (prepared != 2 || remaining != 0 || needleSlot < 0 || needleEntity < 0 || needleUuid.equals(NONE)))
                throw new IllegalArgumentException("Invalid NEW321 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_cross_substitution"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch); b.writeLong(v.token); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeByte(v.prepared); b.writeInt(v.remaining); vec(b, v.pointA); vec(b, v.pointB);
            vec(b, v.source); vec(b, v.arrival); vec(b, v.impact); b.writeInt(v.needleSlot); b.writeInt(v.needleEntity);
            b.writeUUID(v.needleUuid); b.writeBoolean(v.accepted); b.writeUtf(v.reason, 64);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readUnsignedByte(), b.readUnsignedByte(), b.readInt(), vec(b), vec(b), vec(b), vec(b), vec(b),
                b.readInt(), b.readInt(), b.readUUID(), b.readBoolean(), b.readUtf(64)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MultidimensionalLandingArrayRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW351: six immutable anchors and six retained originals, with one bounded frozen round. */
@EventBusSubscriber(modid = "academy")
public final class MultidimensionalLandingArray {
    public static final String ID = "multidimensional_landing_array";
    public static final int MARKED = 1, PREVIEW = 2, PROJECTED = 3, IMPACT = 4,
            ENDED = 5, REFUSED = 6, FREE_ORIGIN = 7, ROUND = FREE_ORIGIN, RECOVERY = 8;
    private static final int HOLD_TICKS = 6;
    private static final String DATA = "academy:multidimensional_landing_array_fences";
    private static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<ServerPlayer, Context> TRANSACTIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.multidimensional_landing_array");
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown, int cast) {
        static final Fences ZERO = new Fences(0, 0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1), Math.max(0, cast - 1)); }
    }
    public record Receipt(long input, long token, int shotsTotal, int spent, int published, int paid,
                          int contacts, int hurts, int retained, int pending, boolean freeOrigin,
                          int phase, String reason, UUID medium, Vec3 source, Vec3 impact) {}
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose, float yaw, float pitch,
                          float fall, boolean grounded, boolean crouching, boolean noPhysics, boolean passenger,
                          boolean sleeping, boolean gliding, boolean flying, boolean water, boolean lava) {
        static Source capture(ServerPlayer p) { return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(),
                p.getBoundingBox(), p.getPose(), p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown(),
                p.noPhysics, p.isPassenger(), p.isSleeping(), p.isFallFlying(), p.getAbilities().flying, p.isInWater(), p.isInLava()); }
        boolean matches(ServerPlayer p) { return position.equals(p.position()) && eye.equals(p.getEyePosition())
                && motion.equals(p.getDeltaMovement()) && body.equals(p.getBoundingBox()) && pose == p.getPose()
                && yaw == p.getYRot() && pitch == p.getXRot() && fall == p.fallDistance && grounded == p.onGround()
                && crouching == p.isShiftKeyDown() && noPhysics == p.noPhysics && passenger == p.isPassenger()
                && sleeping == p.isSleeping() && gliding == p.isFallFlying() && flying == p.getAbilities().flying
                && water == p.isInWater() && lava == p.isInLava(); }
    }
    private record TargetSnapshot(LivingEntity target, Vec3 position, AABB body, int id, UUID uuid) {
        static TargetSnapshot capture(LivingEntity t) { return new TargetSnapshot(t, t.position(), t.getBoundingBox(), t.getId(), t.getUUID()); }
        boolean matches() { return position.equals(target.position()) && body.equals(target.getBoundingBox())
                && id == target.getId() && uuid.equals(target.getUUID()) && target.isAlive() && !target.isRemoved(); }
    }
    private static final class Gesture {
        final HighPowerInput.Input input; final boolean crouching;
        int held; long lastTick; boolean freeAdmitted, changed;
        Gesture(ServerPlayer p, HighPowerInput.Input input) { this.input = input; crouching = p.isShiftKeyDown(); lastTick = now(p); }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final int preset; final long revision;
        HighPowerInput.Input command, roundCommand; Gesture gesture;
        final MultidimensionalLandingArrayWorld.Point[] points = new MultidimensionalLandingArrayWorld.Point[6];
        final int[] life = new int[6];
        List<MultidimensionalLandingArrayWorld.Plan> targets = List.of();
        boolean[] available = new boolean[0];
        BorrowedNeedleEscrow.Volley volley;
        final Flight[] flights = new Flight[6];
        BigDecimal damage; double totalCost; int cooldown;
        int shotsTotal, nextShot, published, paid, contacts, hurts, roundRemaining;
        int transactionShot = -1, transactionPaid;
        boolean confirmed, freeOrigin, processing, paying, closing, terminal;
        long previewInput, lastTick, lastVisual, lastEmission = Long.MIN_VALUE, sequence, roundStart;
        Vec3 source, impact;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = impact = p.getEyePosition(); lastTick = lastVisual = now(p);
        }
    }
    private static final class Flight {
        final Context context; final int shot, slot; final BorrowedNeedleEntity medium;
        final MultidimensionalLandingArrayWorld.Plan plan;
        final MultidimensionalLandingArrayWorld.Point anchor, actualSource;
        final Vec3 origin, velocity; final double sourceDistance; final UUID uuid; final int id; final long generation;
        Vec3 last; double traveled; int age; long lastTick; boolean closed, contact;
        ClosedEmissionPermit claim;
        Flight(Context c, int shot, BorrowedNeedleEntity medium, MultidimensionalLandingArrayWorld.Point actual) {
            context = c; this.shot = shot; slot = shot % 6; this.medium = medium; uuid = medium.getUUID(); id = medium.getId(); generation = medium.leaseGeneration();
            plan = assignedTarget(c, shot); anchor = c.points[slot]; actualSource = actual;
            origin = MultidimensionalLandingArrayWorld.origin(actual); sourceDistance = MultidimensionalLandingArrayWorld.sourceDistance(c.identity.owner(), actual);
            last = origin; velocity = medium.getDeltaMovement(); lastTick = now(c.identity.owner());
        }
    }
    /** A single native first collision is the only mint site; callers cannot create or retarget this authority. */
    public static final class ClosedEmissionPermit {
        private final Flight flight; private final LivingEntity target; private final Vec3 origin, endpoint;
        private final Source source; private final TargetSnapshot targetSnapshot; private boolean consumed;
        private ClosedEmissionPermit(Flight flight, LivingEntity target, Vec3 origin, Vec3 endpoint) {
            this.flight = flight; this.target = target; this.origin = origin; this.endpoint = endpoint;
            source = Source.capture(player()); targetSnapshot = TargetSnapshot.capture(target);
        }
        public ServerPlayer player() { return flight.context.identity.owner(); }
        public LivingEntity target() { return target; }
        public BigDecimal damage() { return MultidimensionalLandingArrayRules.damageShare(flight.context.damage, flight.shot, flight.context.shotsTotal); }
        public Vec3 origin() { return origin; }
        public Vec3 endpoint() { return endpoint; }
        public BorrowedNeedleEntity needle() { return flight.medium; }
        public boolean consume() { if (consumed) return false; consumed = true; return true; }
        private boolean unchanged() { return consumed && flight.claim == this && flight.contact && validFlight(flight)
                && target == flight.plan.target() && source.matches(player()) && targetSnapshot.matches()
                && endpoint.equals(needle().position()) && origin.equals(flight.last)
                && MultidimensionalLandingArrayRules.path(Math.max(flight.sourceDistance, MultidimensionalLandingArrayWorld.sourceDistance(player(), flight.actualSource)),
                        flight.traveled + origin.distanceTo(endpoint)); }
        public boolean valid() {
            if(!unchanged())return contactProof(false,"unchanged",Boolean.getBoolean("academy.qa.tpContactTrace")?"flight="+validFlight(flight)+" source="+source.matches(player())+" target="+targetSnapshot.matches()+" endpoint="+endpoint.equals(needle().position()):"");
            if(!MultidimensionalLandingArrayWorld.sameTarget(flight.plan))return contactProof(false,"sameTarget","");
            if(!anchorsValid(flight.context,needle()))return contactProof(false,"anchors","");
            if(!MultidimensionalLandingArrayWorld.point(player(),flight.anchor,needle()).isEmpty())return contactProof(false,"anchorPoint","");
            if(!MultidimensionalLandingArrayWorld.point(player(),flight.actualSource,needle()).isEmpty())return contactProof(false,"sourcePoint","");
            if(!MultidimensionalLandingArrayWorld.contactClear(player(),origin,endpoint,target,needle()))return contactProof(false,"contactClear","origin="+origin+" endpoint="+endpoint+" body="+target.getBoundingBox());
            return contactProof(unchanged(),"finalUnchanged","");
        }
    }
    private static boolean contactProof(boolean accepted,String stage,String detail) {
        if(Boolean.getBoolean("academy.qa.tpContactTrace")) com.mojang.logging.LogUtils.getLogger().info("QA_TP_CONTACT skill=multidimensional_landing_array stage={} accepted={} {}",stage,accepted,detail);
        return accepted;
    }
    private MultidimensionalLandingArray() {}
    /** Read-only isolated QA diagnostics. Disabled unless explicitly enabled at client launch. */
    public static void diagnostic(String message) {
        if (Boolean.getBoolean("academy.multidimensionalArrayDiagnostics")) com.mojang.logging.LogUtils.getLogger().info("NEW351 QA {}", message);
    }
    public static boolean start(ServerPlayer p, String wire) { return press(p, wire); }
    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        boolean authenticated = HighPowerBracerSession.authenticated(p, input);
        diagnostic("server ingress input=" + (input == null ? -1 : input.input()) + " authenticated=" + authenticated);
        if (!authenticated) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { transaction.closing = true; return false; }
        if (AbilityConsumption.busy(p) || !mapped(p, input.slot()) || !finite(p.getEyePosition())) return false;
        Context c = owned(p);
        if (c != null && (!identity(c) || c.command.epoch() != input.epoch() || c.command.slot() != input.slot())) {
            finish(c, ENDED, "context_changed"); return false;
        }
        if (c != null && (c.processing || c.gesture != null || c.confirmed && !p.isShiftKeyDown())) {
            if (!c.processing) emit(c, REFUSED, null, false, "busy", input);
            return false;
        }
        if (c == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= 64) return false;
            c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        }
        c.command = input; c.gesture = new Gesture(p, input); return true;
    }
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        boolean authenticated = HighPowerBracerSession.authenticated(p, input);
        diagnostic("server ingress input=" + (input == null ? -1 : input.input()) + " authenticated=" + authenticated);
        if (!authenticated) return false;
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { transaction.closing = true; return false; }
        Context c = owned(p);
        if (c == null || c.gesture == null || !c.gesture.input.equals(input) || c.processing || c.closing) return false;
        Gesture gesture = c.gesture; c.gesture = null;
        diagnostic("server release input=" + input.input() + " held=" + gesture.held + " points=" + countPoints(c));
        // Crouch clear always precedes mark acquisition, preview, recovery or admission.
        if (gesture.crouching && !gesture.changed && p.isShiftKeyDown() && gesture.held >= HOLD_TICKS) { finish(c, ENDED, "cleared"); return true; }
        if (gesture.freeAdmitted) { finish(c, ENDED, "hold_released"); return true; }
        if (!gesture.crouching && gesture.held >= HOLD_TICKS) return reject(c, "short_release_required");
        if (gesture.changed || gesture.crouching || p.isShiftKeyDown()) return reject(c, "gesture_changed");
        if (c.confirmed) return false;
        var custody = BorrowedNeedleEscrow.view(p);
        if (custody.state() == BorrowedNeedleEscrow.State.RETURN_PENDING) {
            boolean returned = BorrowedNeedleEscrow.claim(p);
            finish(c, RECOVERY, returned ? "medium_returned" : "return_pending"); return returned;
        }
        if (custody.state() != BorrowedNeedleEscrow.State.EMPTY) return reject(c,
                custody.state() == BorrowedNeedleEscrow.State.QUARANTINED ? "quarantined" : "medium_unavailable");
        if (!eligible(c)) return reject(c, "not_ready");
        Fences fences = readFences(p);
        if (fences == null) return reject(c, "storage_invalid");
        if (fences.request > 0) return reject(c, "request_fence");
        writeFences(p, new Fences(MultidimensionalLandingArrayRules.REQUEST_FENCE, fences.cooldown, fences.cast));
        return countPoints(c) < 6 ? prepare(c) : c.targets.isEmpty() ? freeze(c) : begin(c, false);
    }
    private static boolean prepare(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p); int slot = countPoints(c);
            if (!eligible(c) || slot >= 6 || !anchorsValid(c)) return reject(c, "point_changed");
            var assessment = MultidimensionalLandingArrayWorld.capturePoint(p);
            if (!assessment.valid()) return reject(c, assessment.reason());
            String reason = MultidimensionalLandingArrayWorld.point(p, assessment.point(), null);
            if (!reason.isEmpty()) return reject(c, reason);
            for (var point : c.points) if (point != null && MultidimensionalLandingArrayWorld.needleBody(
                    MultidimensionalLandingArrayWorld.origin(point)).intersects(MultidimensionalLandingArrayWorld.needleBody(
                    MultidimensionalLandingArrayWorld.origin(assessment.point())))) return reject(c, "points_overlap");
            if (!eligible(c) || !source.matches(p) || !anchorsValid(c)) return refuse(c, "transaction_changed");
            c.points[slot] = assessment.point(); c.life[slot] = MultidimensionalLandingArrayRules.PREPARATION_TICKS;
            c.source = source.eye; c.impact = assessment.point().destination();
            emit(c, MARKED, null, true, ""); return true;
        } catch (RuntimeException failure) { return validationFailure(c, failure); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { endTransaction(c); }
    }
    private static boolean freeze(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p);
            if (!eligible(c) || countPoints(c) != 6 || !anchorsValid(c)) return reject(c, "six_points_required");
            if (!plainSix(p)) return reject(c, "six_needles_required");
            var assessment = MultidimensionalLandingArrayWorld.acquireVolley(p);
            if (!assessment.valid()) return reject(c, assessment.reason());
            var plans = assessment.plans(); int count = MultidimensionalLandingArrayRules.needleCount(plans.size());
            var snapshots = plans.stream().map(plan -> TargetSnapshot.capture(plan.target())).toList();
            for (int i = 0; i < count; i++) {
                String reason = MultidimensionalLandingArrayWorld.validate(plans.get(i % plans.size()), c.points[i % 6], null);
                if (!reason.isEmpty()) return reject(c, reason);
            }
            if (!eligible(c) || !source.matches(p) || !plainSix(p) || !anchorsValid(c)
                    || snapshots.stream().anyMatch(t -> !t.matches()) || plans.stream().anyMatch(plan -> !MultidimensionalLandingArrayWorld.sameTargetState(plan)))
                return refuse(c, "transaction_changed");
            var growth = HighPowerGrowth.values(p);
            c.damage = MultidimensionalLandingArrayRules.damageTotal(growth.damage());
            c.totalCost = MultidimensionalLandingArrayRules.cpCost(growth.position());
            c.cooldown = MultidimensionalLandingArrayRules.cooldownTicks(growth.position());
            c.targets = List.copyOf(plans); c.available = new boolean[plans.size()]; java.util.Arrays.fill(c.available, true);
            c.shotsTotal = count; c.previewInput = c.command.input(); c.source = source.eye; c.impact = plans.getFirst().endpoint();
            emit(c, PREVIEW, null, true, ""); return true;
        } catch (RuntimeException failure) { return validationFailure(c, failure); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { endTransaction(c); }
    }
    private static boolean begin(Context c, boolean free) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p); Fences fences = readFences(p);
            if (!eligible(c) || c.targets.isEmpty() || c.confirmed || c.command.input() <= c.previewInput || countPoints(c) != 6 || !anchorsValid(c))
                return reject(c, "six_points_required");
            if (fences == null || fences.cooldown > 0 || fences.cast > 0 || !c.identity.state().canUse(ID)) return reject(c, "cooldown");
            if (BorrowedNeedleEscrow.view(p).state() != BorrowedNeedleEscrow.State.EMPTY || !plainSix(p)) return reject(c, "medium_unavailable");
            if (HighPowerResources.availableCp(p) < c.totalCost) return reject(c, "cp_required");
            if (free && !MultidimensionalLandingArrayWorld.capturePoint(p).valid()) return reject(c, "unsupported_origin");
            refreshAvailability(c);
            if (!eligible(c) || !source.matches(p) || !anchorsValid(c) || !plainSix(p)) return refuse(c, "transaction_changed");
            var reserved = BorrowedNeedleEscrow.reserveVolley(p, UUID.randomUUID(), c.shotsTotal);
            if (reserved.isEmpty()) return refuse(c, "reservation_failed");
            c.volley = reserved.get(); c.confirmed = true; c.freeOrigin = free; c.roundCommand = c.command;
            writeFences(p, new Fences(fences.request, fences.cooldown, MultidimensionalLandingArrayRules.CAST_FENCE));
            c.roundStart = c.lastTick = now(p); c.roundRemaining = MultidimensionalLandingArrayRules.ROUND_TICKS;
            if (free && c.gesture != null) c.gesture.freeAdmitted = true;
            c.source = source.eye; emit(c, ROUND, null, true, "");
        } catch (RuntimeException failure) { return validationFailure(c, failure); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { endTransaction(c); }
        return !c.terminal && launch(c);
    }
    private static MultidimensionalLandingArrayWorld.Plan assignedTarget(Context c, int shot) { return c.targets.get(shot % c.targets.size()); }
    private static void refreshAvailability(Context c) {
        for (int i = 0; i < c.available.length; i++) if (c.available[i] && (!MultidimensionalLandingArrayWorld.sameTarget(c.targets.get(i))
                || !MultidimensionalLandingArrayWorld.currentlyVisible(c.targets.get(i)))) c.available[i] = false;
    }
    private static boolean launch(Context c) {
        ServerPlayer p = c.identity.owner(); int shot = c.nextShot;
        if (shot >= c.shotsTotal || c.processing || c.terminal) return false;
        c.processing = true; TRANSACTIONS.put(p, c); c.transactionShot = shot; c.transactionPaid = c.paid;
        try {
            Source source = Source.capture(p);
            if (!BorrowedNeedleEscrow.current(c.volley)) return false;
            refreshAvailability(c);
            if (!eligible(c) || !source.matches(p)) return refuse(c, "transaction_changed");
            if (!c.available[shot % c.targets.size()]) {
                if (!BorrowedNeedleEscrow.skipShot(c.volley, shot)) return refuse(c, "custody_changed");
                c.nextShot++; c.lastEmission = now(p); return true;
            }
            if (!BorrowedNeedleEscrow.slotAvailable(c.volley, shot) || c.flights[shot % 6] != null) return false;
            var plan = assignedTarget(c, shot); TargetSnapshot target = TargetSnapshot.capture(plan.target());
            var actual = c.points[shot % 6];
            if (c.freeOrigin) {
                var assessment = MultidimensionalLandingArrayWorld.capturePoint(p);
                if (!assessment.valid()) return refuse(c, "unsupported_origin");
                actual = assessment.point();
            }
            final var actualSource = actual;
            String reason = MultidimensionalLandingArrayWorld.validate(plan, c.points[shot % 6], actualSource, null);
            if (!reason.isEmpty()) {
                c.available[shot % c.targets.size()] = false;
                if (!BorrowedNeedleEscrow.skipShot(c.volley, shot)) return refuse(c, "custody_changed");
                c.nextShot++; c.lastEmission = now(p); return true;
            }
            Vec3 origin = MultidimensionalLandingArrayWorld.origin(actualSource);
            Vec3 velocity = plan.endpoint().subtract(origin).normalize().scale(MultidimensionalLandingArrayRules.SPEED);
            c.source = origin; c.impact = plan.endpoint();
            if (!publicationValid(c, source, target, actualSource, shot)) return refuse(c, "transaction_changed");
            var deployed = BorrowedNeedleEscrow.publishShot(c.volley, shot, origin, velocity,
                    () -> publicationValid(c, source, target, actualSource, shot), () -> pay(c, source, target, shot), MultidimensionalLandingArray::shotTick);
            // Custody burned the index before the callback; the controller never replays it.
            c.nextShot = c.volley.nextShotIndex(); c.lastEmission = now(p);
            if (deployed.isEmpty()) return refuse(c, c.paid > c.transactionPaid ? "publication_unconfirmed" : "spawn_rejected");
            BorrowedNeedleEntity medium = deployed.get(); c.published++;
            Flight flight = new Flight(c, shot, medium, actualSource); c.flights[shot % 6] = flight;
            if (!validFlight(flight)) {
                if (!eligible(c)) return refuse(c, "context_changed");
                if (!BorrowedNeedleEscrow.current(c.volley)) return refuse(c, "custody_changed");
                uncertain(c, "publication_unconfirmed"); return false;
            }
            emit(c, PROJECTED, flight, true, ""); AbilityStorage.save(p); AcademyNetwork.sync(p); return true;
        } catch (RuntimeException failure) { return validationFailure(c, failure); }
        catch (Error failure) { try { finish(c, REFUSED, "publication_unconfirmed"); } finally { throw failure; } }
        finally { c.paying = false; c.transactionShot = -1; endTransaction(c); }
    }
    private static boolean publicationState(Context c, Source source, TargetSnapshot target, int shot) {
        return c.processing && c.transactionShot == shot && c.paid == c.transactionPaid && c.nextShot == shot
                && TRANSACTIONS.get(c.identity.owner()) == c && eligible(c) && !c.closing && c.roundRemaining > 0
                && source.matches(c.identity.owner()) && target.matches() && c.available[shot % c.targets.size()]
                && MultidimensionalLandingArrayWorld.sameTargetState(assignedTarget(c, shot));
    }
    private static boolean publicationValid(Context c, Source source, TargetSnapshot target, MultidimensionalLandingArrayWorld.Point actual, int shot) {
        return publicationState(c, source, target, shot) && !HighPowerResources.hasReservation(c.identity.owner())
                && anchorsValid(c)
                && MultidimensionalLandingArrayWorld.validate(assignedTarget(c, shot), c.points[shot % 6], actual, null).isEmpty()
                && publicationState(c, source, target, shot);
    }
    /** The core invokes this after actual native publication and all callback-capable guards. */
    private static boolean pay(Context c, Source source, TargetSnapshot target, int shot) {
        ServerPlayer p = c.identity.owner();
        if (!publicationState(c, source, target, shot)) return false;
        Fences fences = readFences(p);
        if (fences == null || c.paid == 0 && fences.cooldown > 0) return false;
        c.paying = true;
        try {
            if (!HighPowerResources.trySpendMultidimensionalLandingArrayNeedle(p,
                    MultidimensionalLandingArrayRules.cpShare(c.totalCost, shot, c.shotsTotal), c.cooldown, c.paid == 0)) return false;
            c.paid++;
            if (c.paid == 1) writeFences(p, new Fences(fences.request, c.cooldown, fences.cast));
            return true;
        } finally { c.paying = false; }
    }
    static boolean payingNeedle(ServerPlayer p) { Context c = owned(p); return c != null && c.paying && c.processing
            && !c.closing && !c.terminal && TRANSACTIONS.get(p) == c && c.transactionShot == c.nextShot
            && c.transactionShot >= 0 && c.transactionShot < c.shotsTotal && c.paid == c.transactionPaid; }
    private static boolean identity(Context c) { ServerPlayer p = c.identity.owner(); return !c.terminal && CONTEXTS.get(p.getUUID()) == c
            && c.identity.owns(p) && mapped(p, c.command.slot()) && c.identity.state().presets.current() == c.preset
            && c.identity.state().presets.revision() == c.revision; }
    private static boolean eligible(Context c) { ServerPlayer p = c.identity.owner(); var s = c.identity.state();
        return identity(c) && !c.closing && p.isAlive() && !p.isRemoved() && !p.hasDisconnected() && !p.isCreative()
                && !p.isSpectator() && !p.getAbilities().invulnerable && p.containerMenu == p.inventoryMenu
                && s.activated && s.overloadFine && !s.interfering && !s.consumptionInProgress() && !AbilityConsumption.busy(p)
                && s.learned(ID) && s.learned(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID) && HighPowerResources.ready(p)
                && HighPowerResources.effectiveLevel(p) >= 5 && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean mapped(ServerPlayer p, int slot) { return p != null && p.server.isSameThread() && slot >= 0 && slot < 4
            && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category) && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot)); }
    private static boolean anchorsValid(Context c) { return anchorsValid(c, null); }
    private static boolean anchorsValid(Context c, net.minecraft.world.entity.Entity ignored) {
        for (int i = 0; i < c.points.length; i++) if (c.points[i] != null && c.life[i] <= 0) return false;
        if (countPoints(c) == 6) return MultidimensionalLandingArrayWorld.anchors(c.identity.owner(), List.of(c.points), ignored).isEmpty();
        for (var point : c.points) if (point != null && !MultidimensionalLandingArrayWorld.point(c.identity.owner(), point, ignored).isEmpty()) return false;
        return true;
    }
    private static boolean validFlight(Flight f) {
        ServerPlayer p = f.context.identity.owner();
        return !f.closed && !f.context.terminal && f.context.confirmed && f.context.roundRemaining > 0 && eligible(f.context)
                && f.context.flights[f.slot] == f && f.age < MultidimensionalLandingArrayRules.FLIGHT_TICKS
                && BorrowedNeedleEscrow.current(f.context.volley) && f.medium.isLeaseActive()
                && f.medium.leaseOwner() == p && f.medium.roundId().equals(f.context.volley.roundId())
                && f.medium.slotIndex() == f.slot && f.medium.shotIndex() == f.shot && f.medium.level() == f.plan.world()
                && f.medium.getUUID().equals(f.uuid) && f.medium.getId() == f.id && f.medium.leaseGeneration() == f.generation
                && !f.medium.isRemoved() && f.medium.isAdmitted() && finite(f.medium.position())
                && f.medium.getBoundingBox().equals(MultidimensionalLandingArrayWorld.needleBody(f.medium.position()))
                && f.medium.noPhysics && f.medium.isNoGravity() && !f.medium.isPassenger() && !f.medium.isVehicle() && !f.medium.isOnFire()
                && f.medium.getDeltaMovement().equals(f.velocity)
                && p.serverLevel().getEntity(f.medium.getUUID()) == f.medium && p.serverLevel().getEntity(f.medium.getId()) == f.medium;
    }
    /** Native admitted entity tick owns one segment; duplicate same-tick observations are inert. */
    private static void shotTick(BorrowedNeedleEntity medium) {
        ServerPlayer p = medium.leaseOwner(); Context c = owned(p);
        if (c == null || medium.slotIndex() < 0 || medium.slotIndex() >= 6) { BorrowedNeedleEscrow.retireShot(medium); return; }
        Flight f = c.flights[medium.slotIndex()];
        if (f == null || f.medium != medium) { uncertain(c, "custody_changed"); return; }
        if (f.lastTick == now(p)) return; f.lastTick = now(p);
        if (c.processing) { c.closing = true; return; }
        c.processing = true; TRANSACTIONS.put(p, c);
        try {
            if (!validFlight(f) || !f.last.equals(medium.position())) {
                if (!eligible(c)) finish(c, ENDED, "context_changed");
                else uncertain(c, "flight_changed");
                return;
            }
            f.age++;
            if (!c.available[f.shot % c.targets.size()] || !MultidimensionalLandingArrayWorld.sameTarget(f.plan)) {
                c.available[f.shot % c.targets.size()] = false; retireFlight(f, "target_unavailable", false, f.last); return;
            }
            Source source = Source.capture(p); TargetSnapshot target = TargetSnapshot.capture(f.plan.target());
            Vec3 next = f.last.add(f.velocity);
            var swept = MultidimensionalLandingArrayWorld.sweep(f.plan, f.last, next, medium);
            if (!eligible(c) || !source.matches(p)) { finish(c, ENDED, "source_changed"); return; }
            if (!target.matches()) { c.available[f.shot % c.targets.size()] = false; retireFlight(f, "target_unavailable", false, f.last); return; }
            if (!validFlight(f)) { uncertain(c, "contact_changed"); return; }
            Vec3 endpoint = swept.position();
            if (!finite(endpoint) || !MultidimensionalLandingArrayRules.path(Math.max(f.sourceDistance, MultidimensionalLandingArrayWorld.sourceDistance(p, f.actualSource)),
                    f.traveled + f.last.distanceTo(endpoint))) { retireFlight(f, "range_exhausted", false, f.last); return; }
            if (!swept.reason().isEmpty() && !swept.reason().equals("block_collision")) { retireFlight(f, swept.reason(), false, f.last); return; }
            medium.setPos(endpoint);
            if (!eligible(c) || !source.matches(p)) { finish(c, ENDED, "source_changed"); return; }
            if (!target.matches()) { c.available[f.shot % c.targets.size()] = false; retireFlight(f, "target_unavailable", false, endpoint); return; }
            if (!validFlight(f) || !endpoint.equals(medium.position())) { uncertain(c, "contact_changed"); return; }
            if (swept.blocked()) { retireFlight(f, "path_blocked", false, endpoint); return; }
            if (swept.entity() != null) {
                if (swept.entity() != f.plan.target()) { retireFlight(f, "intercepted", false, endpoint); return; }
                f.contact = true; c.contacts++;
                ClosedEmissionPermit permit = new ClosedEmissionPermit(f, f.plan.target(), f.last, endpoint); f.claim = permit;
                boolean hurt = HighPowerCombat.attackMultidimensionalLandingArray(permit);
                if (hurt) c.hurts++;
                retireFlight(f, hurt ? "" : "contact_refused", hurt, endpoint); return;
            }
            f.traveled += f.last.distanceTo(endpoint); f.last = endpoint;
            // Retire on native lease tick39, before the independent tick40 hard cap revokes this projection.
            if (medium.leaseAge() >= BorrowedNeedleEntity.MAX_LEASE_TICKS - 1) retireFlight(f, "lease_expired", false, endpoint);
        } catch (RuntimeException failure) { LOG.warn("NEW351 uncertain native contact ended its round", failure); uncertain(c, "contact_unconfirmed"); }
        catch (Error failure) { try { uncertain(c, "contact_unconfirmed"); } finally { throw failure; } }
        finally { endTransaction(c); }
    }
    private static void retireFlight(Flight f, String reason, boolean hurt, Vec3 impact) {
        if (f.closed) return; f.closed = true; f.context.flights[f.slot] = null;
        f.context.source = f.origin; f.context.impact = impact;
        BorrowedNeedleEscrow.retireShot(f.medium);
        if (!f.context.terminal) emit(f.context, IMPACT, f, hurt, reason);
    }
    private static int inFlight(Context c) { int n = 0; for (Flight f : c.flights) if (f != null) n++; return n; }
    private static boolean reject(Context c, String reason) { emit(c, REFUSED, null, false, reason); return false; }
    private static void uncertain(Context c, String reason) {
        if (c.volley != null) BorrowedNeedleEscrow.quarantineVolley(c.volley, reason);
        finish(c, REFUSED, reason);
    }
    private static boolean refuse(Context c, String reason) { finish(c, REFUSED, reason); return false; }
    private static boolean validationFailure(Context c, RuntimeException failure) {
        LOG.warn("NEW351 validation terminated its round", failure);
        if (c.volley != null) { uncertain(c, "validation_failed"); return false; }
        return refuse(c, "validation_failed");
    }
    private static void endTransaction(Context c) { c.processing = false; TRANSACTIONS.remove(c.identity.owner(), c);
        if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
    private static void finish(Context c, int phase, String reason) {
        if (c.terminal) return; c.closing = true; c.terminal = true; c.roundRemaining = 0; c.gesture = null;
        CONTEXTS.remove(c.identity.owner().getUUID(), c);
        for (Flight f : c.flights.clone()) if (f != null) { f.closed = true; c.flights[f.slot] = null; }
        if (c.volley != null) BorrowedNeedleEscrow.finishVolley(c.volley);
        try { emit(c, phase, null, false, reason); } catch (RuntimeException failure) { LOG.debug("NEW351 terminal visual unavailable", failure); }
    }
    public static Receipt receipt(ServerPlayer p) { return RECEIPTS.get(p); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && (c.processing || c.confirmed); }
    public static boolean marked(ServerPlayer p) { Context c = owned(p); return c != null && countPoints(c) > 0 && !c.confirmed; }
    public static boolean previewed(ServerPlayer p) { Context c = owned(p); return c != null && !c.targets.isEmpty() && !c.confirmed; }
    public static int preparedPoints(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : countPoints(c); }
    public static int plannedNeedles(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.shotsTotal; }
    public static int heldTicks(ServerPlayer p) { Context c = owned(p); return c == null || c.gesture == null ? 0 : c.gesture.held; }
    public static int remainingTicks(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : remaining(c); }
    public static List<TargetVisual> frozenTargets(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : targetVisuals(c); }
    public static void tick(ServerPlayer p) { Context c = owned(p); if (c != null && p.server.isSameThread()) tick(c); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && input.input() < previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || !identity(c) || c.command.epoch() != input.epoch() || c.command.slot() != input.slot() || input.input() < c.command.input()) return false;
        c.command = input; cancelActive(p); return true;
    }
    public static void cancelActive(ServerPlayer p) { Context c = owned(p); if (c == null || !p.server.isSameThread()) return;
        c.closing = true; if (!c.processing) finish(c, ENDED, "context_changed"); }
    public static void clear(ServerPlayer p) { cancelActive(p); }
    private static Context owned(ServerPlayer p) { Context c = p == null ? null : CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static int countPoints(Context c) { int n = 0; for (var point : c.points) if (point != null) n++; return n; }
    private static int remaining(Context c) { if (c.terminal) return 0; if (c.confirmed) return c.roundRemaining;
        int n = MultidimensionalLandingArrayRules.PREPARATION_TICKS; boolean found = false;
        for (int i = 0; i < 6; i++) if (c.points[i] != null) { n = Math.min(n, c.life[i]); found = true; } return found ? n : 0; }
    private static boolean plainSix(ServerPlayer p) { var hand = p.getOffhandItem(); return hand != null && hand.getCount() >= 6
            && hand.is(cn.academy.port.AcademyCraft.NEEDLE.get()) && hand.isComponentsPatchEmpty(); }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }
    private static Fences readFences(ServerPlayer p) {
        var root = p.getPersistentData(); if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null; var t = root.getCompound(DATA);
        if (!t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1 || !t.contains("request", Tag.TAG_INT)
                || !t.contains("cooldown", Tag.TAG_INT) || !t.contains("cast", Tag.TAG_INT)) return null;
        int r = t.getInt("request"), d = t.getInt("cooldown"), f = t.getInt("cast");
        return r < 0 || r > 4 || d < 0 || d > 120 || f < 0 || f > 20 ? null : new Fences(r, d, f);
    }
    private static void writeFences(ServerPlayer p, Fences fences) { CompoundTag t = new CompoundTag();
        t.putInt("version", 1); t.putInt("request", fences.request); t.putInt("cooldown", fences.cooldown); t.putInt("cast", fences.cast); p.getPersistentData().put(DATA, t); }
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
        catch (RuntimeException failure) { validationFailure(c, failure); }
        catch (Error failure) { try { finish(c, ENDED, "validation_failed"); } finally { throw failure; } }
    }
    private static void advance(Context c) {
        ServerPlayer p = c.identity.owner(); long current = now(p);
        if (c.processing || c.lastTick == current) return;
        if (current < c.lastTick) { finish(c, ENDED, "clock_changed"); return; } c.lastTick = current;
        if (!eligible(c)) { finish(c, ENDED, "context_changed"); return; }
        Gesture gesture = c.gesture;
        if (gesture != null && gesture.lastTick != current) {
            gesture.lastTick = current;
            if (!gesture.freeAdmitted && gesture.crouching != p.isShiftKeyDown()) {
                gesture.changed = true; c.gesture = null; reject(c, "gesture_changed");
            } else gesture.held = Math.min(HOLD_TICKS, gesture.held + 1);
            if (!gesture.changed && gesture.crouching && p.isShiftKeyDown() && gesture.held >= HOLD_TICKS) { finish(c, ENDED, "cleared"); return; }
            if (!gesture.changed && !gesture.crouching && !p.isShiftKeyDown() && gesture.held >= HOLD_TICKS && !c.confirmed && !c.targets.isEmpty()) {
                if (!begin(c, true)) { c.gesture = null; return; }
            }
        }
        for (int i = 0; i < 6; i++) if (c.points[i] != null && --c.life[i] <= 0) { finish(c, ENDED, "expired"); return; }
        if (c.confirmed) {
            if (--c.roundRemaining <= 0 || !BorrowedNeedleEscrow.current(c.volley)) { finish(c, ENDED, "round_expired"); return; }
            for (Flight f : c.flights.clone()) if (f != null && !validFlight(f)) { uncertain(c, "flight_changed"); return; }
            if (c.nextShot < c.shotsTotal && MultidimensionalLandingArrayRules.emissionDue(current, c.roundStart, c.lastEmission, inFlight(c))) launch(c);
            if (!c.terminal && c.nextShot >= c.shotsTotal && inFlight(c) == 0) { finish(c, ENDED, ""); return; }
        }
        if (!c.terminal && (countPoints(c) > 0 || c.confirmed) && current - c.lastVisual >= 10) {
            refreshAvailability(c); emit(c, c.confirmed ? ROUND : c.targets.isEmpty() ? MARKED : PREVIEW, null, true, "");
        }
    }
    private static void remove(ServerPlayer p) { cancelActive(p); INPUTS.computeIfPresent(p.getUUID(), (id, w) -> w.owner == p ? null : w); RECEIPTS.remove(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p && !e.isCanceled()) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer p) {
        remove(old); if (old.getPersistentData().contains(DATA)) p.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy()); } }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c, ENDED, "server_stopped");
        INPUTS.entrySet().removeIf(x -> x.getValue().owner.server == e.getServer()); RECEIPTS.keySet().removeIf(p -> p.server == e.getServer()); CLOCKS.remove(e.getServer());
    }
    private static List<TargetVisual> targetVisuals(Context c) { List<TargetVisual> result = new ArrayList<>(c.targets.size());
        for (int i = 0; i < c.targets.size(); i++) { var p = c.targets.get(i); result.add(new TargetVisual(p.targetUuid(), p.targetId(), p.endpoint(), c.available[i])); }
        return List.copyOf(result); }
    private static int pending(ServerPlayer p) { int n = 0; for (var slot : BorrowedNeedleEscrow.allView(p))
        if (slot.state() == BorrowedNeedleEscrow.State.RETURN_PENDING || slot.state() == BorrowedNeedleEscrow.State.QUARANTINED) n += slot.retainedCount(); return n; }
    private static void emit(Context c, int phase, Flight flight, boolean accepted, String reason) {
        emit(c, phase, flight, accepted, reason, null);
    }
    private static void emit(Context c, int phase, Flight flight, boolean accepted, String reason, HighPowerInput.Input refusalInput) {
        ServerPlayer p = c.identity.owner(); c.lastVisual = now(p);
        List<Vec3> points = new ArrayList<>(6); for (var point : c.points) if (point != null) points.add(point.destination());
        var input = refusalInput != null ? refusalInput : c.confirmed && c.roundCommand != null && phase != ENDED && phase != REFUSED ? c.roundCommand : c.command;
        int retained = BorrowedNeedleEscrow.retainedCount(p), pending = pending(p);
        Vec3 source = flight == null ? c.source : flight.origin, impact = c.impact;
        RECEIPTS.put(p, new Receipt(input.input(), c.identity.token(), c.shotsTotal, c.nextShot, c.published, c.paid,
                c.contacts, c.hurts, retained, pending, c.freeOrigin, phase, reason, flight == null ? null : flight.medium.getUUID(), source, impact));
        Visual packet = new Visual(p.getUUID(), input.epoch(), input.input(), c.identity.token(), ++c.sequence, phase,
                points, c.terminal || phase == REFUSED ? 0 : remaining(c), targetVisuals(c), c.shotsTotal, c.nextShot, c.published,
                retained, pending, c.freeOrigin, source, impact, flight == null ? NONE : flight.medium.getUUID(),
                flight == null ? -1 : flight.slot, flight == null ? -1 : flight.shot, accepted, c.terminal, reason);
        diagnostic("server receipt input=" + input.input() + " phase=" + phase + " points=" + points.size()
                + " shot=" + packet.shotIndex() + " nativeHurtAccepted=" + (phase == IMPACT && accepted) + " contacts=" + c.contacts
                + " hurts=" + c.hurts + " published=" + c.published + " retained=" + retained + " pending=" + pending
                + " source=" + source + " impact=" + impact + " terminal=" + c.terminal + " reason=" + reason);
        if (!p.hasDisconnected()) PacketDistributor.sendToPlayer(p, packet);
    }
    public record TargetVisual(UUID uuid, int id, Vec3 position, boolean available) { public TargetVisual {
        if (uuid == null || uuid.equals(NONE) || id < 0 || !finite(position)) throw new IllegalArgumentException("Invalid NEW351 target"); } }
    public record Visual(UUID owner, long epoch, long input, long token, long sequence, int phase,
                         List<Vec3> points, int remainingTicks, List<TargetVisual> targets, int shotsTotal, int spent,
                         int published, int retained, int pending, boolean freeOrigin, Vec3 source, Vec3 impact,
                         UUID medium, int slot, int shotIndex, boolean accepted, boolean terminal, String reason) implements CustomPacketPayload {
        public Visual {
            if (owner == null || owner.equals(NONE) || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > RECOVERY || points == null || points.size() > 6 || targets == null || targets.size() > 16
                    || remainingTicks < 0 || remainingTicks > MultidimensionalLandingArrayRules.PREPARATION_TICKS || shotsTotal < 0 || shotsTotal > 16 || shotsTotal > 0 && shotsTotal < 6
                    || spent < 0 || spent > shotsTotal || published < 0 || published > spent || retained < 0 || retained > 6
                    || pending < 0 || pending > retained || !finite(source) || !finite(impact) || medium == null || slot < -1 || slot >= 6
                    || shotIndex < -1 || shotIndex >= shotsTotal || (slot == -1) != (shotIndex == -1) || (slot == -1) != medium.equals(NONE)
                    || slot >= 0 && slot != shotIndex % 6 || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || (phase == PROJECTED || phase == IMPACT) && (slot < 0 || points.size() != 6 || remainingTicks > 160)
                    || phase == ROUND && (points.size() != 6 || shotsTotal < 6 || remainingTicks > 160)
                    || phase == PREVIEW && (points.size() != 6 || targets.isEmpty() || shotsTotal != MultidimensionalLandingArrayRules.needleCount(targets.size()))
                    || phase == ENDED && (!terminal || remainingTicks != 0) || terminal && remainingTicks != 0
                    || phase == REFUSED && remainingTicks != 0)
                throw new IllegalArgumentException("Invalid NEW351 visual");
            points = List.copyOf(points); targets = List.copyOf(targets);
            for (Vec3 point : points) if (!finite(point)) throw new IllegalArgumentException("Invalid NEW351 point");
            var ids = new HashSet<Integer>(); var uuids = new HashSet<UUID>();
            for (var target : targets) if (!ids.add(target.id) || !uuids.add(target.uuid)) throw new IllegalArgumentException("Duplicate NEW351 target");
        }
        public static final String PROTOCOL_VERSION = "1";
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_multidimensional_landing_array"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeUUID(v.owner); b.writeLong(v.epoch); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeByte(v.points.size()); for (Vec3 point : v.points) vec(b, point); b.writeInt(v.remainingTicks);
            b.writeByte(v.targets.size()); for (TargetVisual t : v.targets) { b.writeUUID(t.uuid); b.writeInt(t.id); vec(b, t.position); b.writeBoolean(t.available); }
            b.writeByte(v.shotsTotal); b.writeByte(v.spent); b.writeByte(v.published); b.writeByte(v.retained); b.writeByte(v.pending); b.writeBoolean(v.freeOrigin);
            vec(b, v.source); vec(b, v.impact); b.writeUUID(v.medium); b.writeInt(v.slot); b.writeInt(v.shotIndex); b.writeBoolean(v.accepted); b.writeBoolean(v.terminal); b.writeUtf(v.reason, 64);
        }, b -> {
            UUID owner = b.readUUID(); long epoch = b.readLong(), input = b.readLong(), token = b.readLong(), sequence = b.readLong(); int phase = b.readUnsignedByte();
            int count = b.readUnsignedByte(); if (count > 6) throw new IllegalArgumentException("Invalid NEW351 points"); List<Vec3> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) points.add(vec(b)); int remaining = b.readInt();
            count = b.readUnsignedByte(); if (count > 16) throw new IllegalArgumentException("Invalid NEW351 targets"); List<TargetVisual> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) targets.add(new TargetVisual(b.readUUID(), b.readInt(), vec(b), b.readBoolean()));
            return new Visual(owner, epoch, input, token, sequence, phase, points, remaining, targets, b.readUnsignedByte(), b.readUnsignedByte(), b.readUnsignedByte(),
                    b.readUnsignedByte(), b.readUnsignedByte(), b.readBoolean(), vec(b), vec(b), b.readUUID(), b.readInt(), b.readInt(), b.readBoolean(), b.readBoolean(), b.readUtf(64));
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network { @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar(Visual.PROTOCOL_VERSION).playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); } }
}

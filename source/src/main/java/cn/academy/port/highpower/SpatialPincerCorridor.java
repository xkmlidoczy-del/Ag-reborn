package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashSet;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW341: three immutable marks, a fixed endpoint volley, and a separately consented teammate passage. */
@EventBusSubscriber(modid = "academy")
public final class SpatialPincerCorridor {
    public static final String ID = "spatial_pincer_corridor";
    public static final int MARKED = 1, PROJECTED = 2, IMPACT = 3, ENDED = 4, REFUSED = 5;
    public static final int PICKER = 6, PREVIEW = 7;
    private static final String DATA = "academy:spatial_pincer_corridor_fences";
    private static final UUID NONE = new UUID(0, 0);
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    /** A callback cannot erase the transaction guard by removing its context. */
    private static final Map<ServerPlayer, Context> TRANSACTIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Integer> POST_CLOCKS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.spatial_pincer_corridor");
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown, int cast) {
        static final Fences ZERO = new Fences(0, 0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1), Math.max(0, cast - 1)); }
    }
    public record Receipt(long input, long token, boolean paid, int paidNeedles, int published,
                          int contacts, int hurts, int phase, String reason, UUID medium, Vec3 source, Vec3 arrival) {}
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouching,
                          boolean noPhysics, boolean passenger, boolean sleeping, boolean gliding, boolean flying,
                          boolean water, boolean lava) {
        static Source capture(ServerPlayer p) { return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(),
                p.getBoundingBox(), p.getPose(), p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown(),
                p.noPhysics, p.isPassenger(), p.isSleeping(), p.isFallFlying(), p.getAbilities().flying, p.isInWater(), p.isInLava()); }
        boolean matches(ServerPlayer p) { return position.equals(p.position()) && eye.equals(p.getEyePosition())
                && motion.equals(p.getDeltaMovement()) && body.equals(p.getBoundingBox()) && pose == p.getPose()
                && yaw == p.getYRot() && pitch == p.getXRot() && fall == p.fallDistance
                && grounded == p.onGround() && crouching == p.isShiftKeyDown() && noPhysics == p.noPhysics
                && passenger == p.isPassenger() && sleeping == p.isSleeping() && gliding == p.isFallFlying()
                && flying == p.getAbilities().flying && water == p.isInWater() && lava == p.isInLava(); }
    }
    private record TargetSnapshot(LivingEntity target, Vec3 position, AABB body, int id, UUID uuid) {
        static TargetSnapshot capture(LivingEntity t) { return new TargetSnapshot(t, t.position(), t.getBoundingBox(), t.getId(), t.getUUID()); }
        boolean matches() { return position.equals(target.position()) && body.equals(target.getBoundingBox())
                && id == target.getId() && uuid.equals(target.getUUID()) && target.isAlive() && !target.isRemoved(); }
    }
    private static final class Gesture {
        final HighPowerInput.Input input; final boolean crouching;
        int held; long lastTick;
        Gesture(ServerPlayer p, HighPowerInput.Input input) { this.input = input; crouching = p.isShiftKeyDown(); lastTick = now(p); }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset; final long revision;
        HighPowerInput.Input command;
        final SpatialPincerCorridorWorld.Point[] points = new SpatialPincerCorridorWorld.Point[3];
        SpatialPincerCorridorWorld.Plan plan;
        List<SpatialPincerCorridorWorld.Plan> targets = List.of();
        List<TargetVisual> targetVisuals = List.of();
        int needles, transactionPaid; long previewInput, preparationInput; boolean confirmed;
        Gesture gesture;
        ItemStack hand; int originalCount;
        BigDecimal damage; double totalCost; int cooldown;
        int remaining, nextSlot, delay, paid, published, contacts, hurts;
        int transactionSlot = -1;
        long lastTick, lastVisual, sequence;
        boolean processing, closing, terminal, paying, consentAdmitted, consentCompleted;
        Vec3 source, arrival;
        final Flight[] flights = new Flight[SpatialPincerCorridorRules.MAX_NEEDLES];
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = arrival = p.getEyePosition(); lastTick = now(p); lastVisual = lastTick;
        }
    }
    /** Closed, read-only binding to exactly this preparation; coordinate getters never authorize a move. */
    public static final class ConsentPlan {
        private final Context context;
        private final HighPowerInput.Input command;
        private final SpatialPincerCorridorWorld.Point a, b, c;
        private ConsentPlan(Context context) {
            this.context = context; command = context.command;
            a = context.points[0]; b = context.points[1]; c = context.points[2];
        }
        public ServerPlayer owner() { return context.identity.owner(); }
        public net.minecraft.server.level.ServerLevel world() { return context.identity.level(); }
        public long token() { return context.identity.token(); }
        public long epoch() { return command.epoch(); }
        public long input() { return command.input(); }
        public int preset() { return context.preset; }
        public long revision() { return context.identity.token(); }
        public HighPowerInput.Input command() { return command; }
        public Vec3 pointA() { return a.destination(); }
        public Vec3 pointB() { return b.destination(); }
        public Vec3 pointC() { return c.destination(); }
        public float yaw() { return c.yaw(); }
        public float pitch() { return c.pitch(); }
        public int remainingTicks() { return consentCurrent(this) ? context.remaining : 0; }
    }
    public static boolean consentCurrent(ConsentPlan plan) {
        if (plan == null) return false;
        Context c = plan.context;
        return eligible(c) && c.command.equals(plan.command) && c.points[0] == plan.a && c.points[1] == plan.b
                && c.points[2] == plan.c && plan.c != null && c.plan == null
                && c.remaining > 0 && !c.consentAdmitted && noFlights(c);
    }
    public static boolean revalidateConsentPoints(ConsentPlan plan) { return revalidateConsentPoints(plan, null); }
    public static boolean revalidateConsentPoints(ConsentPlan plan, ServerPlayer traveler) {
        if (!consentCurrent(plan)) return false;
        Source before = Source.capture(plan.owner());
        for (int slot = 0; slot < 3; slot++)
            if (!SpatialPincerCorridorWorld.consentPoint(plan.owner(), plan.context.points[slot], traveler, slot == 0).isEmpty()) return false;
        return consentCurrent(plan) && before.matches(plan.owner());
    }
    /** Persistent physical cooldown is independent of rewindable game time and GUI request throttling. */
    public static boolean consentResourceReady(ConsentPlan plan) {
        if (!consentCurrent(plan)) return false;
        Fences fences = readFences(plan.owner());
        return fences != null && fences.cooldown == 0 && fences.cast == 0;
    }
    /** Closed post-debit write; the helper has already validated this same storage before admission. */
    public static boolean commitConsentCooldown(ConsentPlan plan, int cooldown) {
        if (plan == null || !plan.context.consentAdmitted || !plan.context.terminal
                || !SpatialPincerConsent.committing(plan.owner()) || cooldown < 1 || cooldown > 80) return false;
        Fences fences = readFences(plan.owner());
        if (fences == null || fences.cooldown > 0 || fences.cast > 0) return false;
        writeFences(plan.owner(), new Fences(fences.request, cooldown, SpatialPincerCorridorRules.CAST_FENCE));
        return true;
    }
    /** Called by the consent helper only after its native event revalidation, before debit/move. */
    public static boolean consumeConsent(ConsentPlan plan) {
        if (!consentCurrent(plan) || !SpatialPincerConsent.committing(plan.owner())) return false;
        Context c = plan.context; c.consentAdmitted = true; c.closing = true; c.terminal = true;
        c.remaining = 0; c.gesture = null; CONTEXTS.remove(plan.owner().getUUID(), c);
        return true;
    }
    /** Deferred terminal visual, after the consent helper has finished its closed native commit. */
    public static void completeConsent(ConsentPlan plan) {
        if (plan == null || !plan.context.consentAdmitted || plan.context.consentCompleted) return;
        Context c = plan.context; c.consentCompleted = true;
        record(c, ENDED, "consent_admitted", null);
        try { emit(c, ENDED, null, c.arrival, false, "consent_admitted"); } catch (RuntimeException ignored) {}
    }
    private static final class Flight {
        final Context context; final int slot; final DisplacedNeedleEntity medium;
        final DisplacedNeedleEntity.Identity identity; final Vec3 source, origin;
        final SpatialPincerCorridorWorld.Plan plan;
        Vec3 last; double traveled; int age; boolean closed; long bornTick;
        EmissionPermit claim;
        Flight(Context c, int slot, DisplacedNeedleEntity medium, Vec3 origin) {
            context = c; this.slot = slot; this.medium = medium; identity = medium.identity();
            source = c.source; this.origin = origin; last = origin; bornTick = now(c.identity.owner());
            plan = assignedTarget(c, slot);
        }
    }
    /** Unforgeable authority for exactly one native first-contact segment and its frozen share of the whole capped 2.8D. */
    public static final class EmissionPermit {
        private final Flight flight; private final LivingEntity target; private final Vec3 origin, endpoint;
        private final Source source; private final TargetSnapshot targetSnapshot; private boolean consumed;
        private EmissionPermit(Flight f, LivingEntity target, Vec3 origin, Vec3 endpoint) {
            flight = f; this.target = target; this.origin = origin; this.endpoint = endpoint;
            source = Source.capture(player()); targetSnapshot = TargetSnapshot.capture(target);
        }
        public ServerPlayer player() { return flight.context.identity.owner(); }
        public LivingEntity target() { return target; }
        public BigDecimal damage() { return SpatialPincerCorridorRules.damageShare(flight.context.damage, flight.slot, flight.context.needles); }
        public Vec3 origin() { return origin; }
        public Vec3 endpoint() { return endpoint; }
        public DisplacedNeedleEntity needle() { return flight.medium; }
        public boolean consume() { if (consumed) return false; consumed = true; return true; }
        private boolean unchanged() {
            return consumed && flight.claim == this && validFlight(flight) && target == flight.plan.target()
                    && source.matches(player()) && targetSnapshot.matches()
                    && origin.equals(needle().contactSegmentOrigin()) && endpoint.equals(needle().contactPoint())
                    && endpoint.equals(needle().position()) && origin.equals(flight.last)
                    && SpatialPincerCorridorRules.path(player().getEyePosition().distanceTo(flight.origin),
                            flight.traveled + origin.distanceTo(endpoint));
        }
        public boolean valid() {
            boolean initial=unchanged();
            if(!initial) return contactProof(false,"unchanged", Boolean.getBoolean("academy.qa.tpContactTrace")?"flight="+validFlight(flight)+" source="+source.matches(player())+" target="+targetSnapshot.matches()+" origin="+origin.equals(needle().contactSegmentOrigin())+" endpoint="+endpoint.equals(needle().position()):"");
            if(!SpatialPincerCorridorWorld.sameTarget(flight.plan))return contactProof(false,"sameTarget","");
            if(!SpatialPincerCorridorWorld.contactClear(player(), origin, endpoint, target, needle()))return contactProof(false,"contactClear","origin="+origin+" endpoint="+endpoint+" body="+target.getBoundingBox());
            return contactProof(unchanged(),"finalUnchanged","");
        }
    }
    private static boolean contactProof(boolean accepted,String stage,String detail) {
        if(Boolean.getBoolean("academy.qa.tpContactTrace")) com.mojang.logging.LogUtils.getLogger().info("QA_TP_CONTACT skill=spatial_pincer_corridor stage={} accepted={} {}",stage,accepted,detail);
        return accepted;
    }
    private SpatialPincerCorridor() {}
    public static boolean start(ServerPlayer p, String wire) { return press(p, wire); }
    /** Press only records authenticated intent. All marks and costs are release-authoritative. */
    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        if (SpatialPincerConsent.committing(p)) return false;
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { transaction.closing = true; return false; }
        if (AbilityConsumption.busy(p) || !mapped(p, input.slot()) || !finite(p.getEyePosition())
                || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return false;
        Context c = owned(p);
        if (c != null && (!identity(c) || c.command.epoch() != input.epoch() || c.command.slot() != input.slot())) {
            finish(c, ENDED, "context_changed"); return false;
        }
        if (c != null && (c.processing || c.confirmed || c.gesture != null)) return false;
        if (c == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= 64) return false;
            c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        }
        c.command = input; c.gesture = new Gesture(p, input);
        return true;
    }
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        if (SpatialPincerConsent.committing(p)) return false;
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { transaction.closing = true; return false; }
        Context c = owned(p);
        if (c == null || c.gesture == null || !c.gesture.input.equals(input) || c.closing || c.processing) return false;
        Gesture gesture = c.gesture; c.gesture = null;
        if (gesture.held >= SpatialPincerCorridorRules.CLEAR_HOLD_TICKS) { finish(c, ENDED, "cleared"); return true; }
        if (!eligible(c)) return refuse(c, "not_ready");
        Fences fences = readFences(p);
        if (fences == null) return refuse(c, "storage_invalid");
        if (fences.request > 0) return refuse(c, "request_fence");
        writeFences(p, new Fences(SpatialPincerCorridorRules.REQUEST_FENCE, fences.cooldown, fences.cast));
        if (gesture.crouching) {
            if (!p.isShiftKeyDown() || c.points[2] == null) return refuse(c, "three_points_required");
            return offer(c);
        }
        if (p.isShiftKeyDown()) return refuse(c, "gesture_changed");
        return c.points[2] == null ? prepare(c) : c.plan == null ? freeze(c) : begin(c);
    }
    private static boolean offer(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            ConsentPlan plan = new ConsentPlan(c);
            if (!revalidateConsentPoints(plan)) return refuse(c, "point_changed");
            if (!SpatialPincerConsent.offer(p, plan)) return refuse(c, "picker_unavailable");
            if (!consentCurrent(plan)) return refuse(c, "context_changed");
            c.preparationInput = c.command.input();
            record(c, PICKER, "", null); emit(c, PICKER, null, c.points[2].destination(), true, "");
            return true;
        } catch (RuntimeException failure) { return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; TRANSACTIONS.remove(p, c); if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
    }
    private static boolean prepare(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p);
            if (!eligible(c)) return refuse(c, "not_ready");
            int slot = countPoints(c);
            if (slot >= 3) return refuse(c, "three_points_required");
            for (var point : c.points) if (point != null && !SpatialPincerCorridorWorld.point(p, point, null).isEmpty())
                return refuse(c, "point_changed");
            var assessment = SpatialPincerCorridorWorld.capturePoint(p);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            for (var point : c.points) if (point != null && point.standingBody().intersects(assessment.point().standingBody()))
                return refuse(c, "points_overlap");
            String reason = SpatialPincerCorridorWorld.point(p, assessment.point(), null);
            if (!reason.isEmpty()) return refuse(c, reason);
            if (!eligible(c) || !source.matches(p)) return refuse(c, "transaction_changed");
            c.points[slot] = assessment.point();
            if (slot == 0) { c.remaining = SpatialPincerCorridorRules.PREPARATION_TICKS; c.lastTick = now(p); }
            c.source = source.eye; c.arrival = SpatialPincerCorridorWorld.origin(assessment.point());
            c.preparationInput = c.command.input();
            record(c, MARKED, "", null); emit(c, MARKED, null, c.arrival, true, ""); return true;
        } catch (RuntimeException failure) { LOG.warn("NEW341 point assessment failed", failure); return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; TRANSACTIONS.remove(p, c); if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
    }
    /** The fourth fresh tap freezes all identities, endpoints, assignments, and whole-volley budgets. */
    private static boolean freeze(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p);
            if (!eligible(c) || c.remaining <= 0 || c.plan != null) return refuse(c, "not_ready");
            var assessment = SpatialPincerCorridorWorld.acquireVolley(p);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            var plans = assessment.plans(); int needles = SpatialPincerCorridorRules.needleCount(plans.size());
            var snapshots = plans.stream().map(plan -> TargetSnapshot.capture(plan.target())).toList();
            for (int slot = 0; slot < needles; slot++) {
                String reason = SpatialPincerCorridorWorld.validate(plans.get(slot % plans.size()), c.points[slot % 3], null);
                if (!reason.isEmpty()) return refuse(c, reason);
            }
            if (!eligible(c) || !source.matches(p) || snapshots.stream().anyMatch(target -> !target.matches())
                    || plans.stream().anyMatch(plan -> !SpatialPincerCorridorWorld.sameTargetState(plan)
                            || !plan.endpoint().equals(plan.target().getBoundingBox().getCenter())))
                return refuse(c, "transaction_changed");
            var growth = HighPowerGrowth.values(p);
            c.damage = SpatialPincerCorridorRules.damageTotal(growth.damage());
            c.totalCost = SpatialPincerCorridorRules.cpCost(growth.position());
            c.cooldown = SpatialPincerCorridorRules.cooldownTicks(growth.position());
            c.targets = List.copyOf(plans); c.plan = plans.getFirst(); c.needles = needles; c.previewInput = c.command.input(); c.preparationInput = c.previewInput;
            c.targetVisuals = plans.stream().map(plan -> new TargetVisual(plan.targetId(), plan.targetUuid(), plan.endpoint())).toList();
            c.source = source.eye; c.arrival = SpatialPincerCorridorWorld.origin(c.points[0]);
            record(c, PREVIEW, "", null); emit(c, PREVIEW, null, c.plan.endpoint(), true, "");
            return true;
        } catch (RuntimeException failure) { LOG.warn("NEW341 frozen selection failed", failure); return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; TRANSACTIONS.remove(p, c); if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
    }
    /** The fifth fresh tap confirms exactly the preview; no acquisition or budget refresh occurs here. */
    private static boolean begin(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        try {
            Source source = Source.capture(p); Fences fences = readFences(p);
            if (!eligible(c) || c.remaining <= 0 || c.plan == null || c.confirmed || c.command.input() <= c.previewInput)
                return refuse(c, "not_ready");
            if (fences == null || fences.cooldown > 0 || fences.cast > 0 || !c.identity.state().canUse(ID)) return refuse(c, "cooldown");
            var availability = CorridorNeedleRegistry.availability(p);
            if (availability == CorridorNeedleRegistry.Availability.REFUND_PENDING)
                return refuse(c, CorridorNeedleRegistry.retryRefund(p) ? "medium_returned" : "refund_pending");
            if (availability != CorridorNeedleRegistry.Availability.READY) return refuse(c, "medium_unavailable");
            ItemStack hand = p.getOffhandItem();
            if (!plain(hand) || hand.getCount() < c.needles) return refuse(c, "needles_required");
            var snapshots = c.targets.stream().map(plan -> TargetSnapshot.capture(plan.target())).toList();
            for (int slot = 0; slot < c.needles; slot++) {
                String reason = SpatialPincerCorridorWorld.validate(assignedTarget(c, slot), c.points[slot % 3], null);
                if (!reason.isEmpty()) return refuse(c, reason);
            }
            if (!eligible(c) || !source.matches(p) || snapshots.stream().anyMatch(target -> !target.matches())
                    || c.targets.stream().anyMatch(plan -> !SpatialPincerCorridorWorld.sameTargetState(plan))
                    || p.getOffhandItem() != hand || !plain(hand) || hand.getCount() < c.needles)
                return refuse(c, "transaction_changed");
            c.hand = hand; c.originalCount = hand.getCount(); c.remaining = 0; c.confirmed = true;
            // Only observed future server ticks advance the fixed 0/4/.../28 publication schedule.
            c.lastTick = now(p);
        } catch (RuntimeException failure) { LOG.warn("NEW341 confirmation failed", failure); return refuse(c, "validation_failed"); }
        catch (Error failure) { try { finish(c, REFUSED, "validation_failed"); } finally { throw failure; } }
        finally { c.processing = false; TRANSACTIONS.remove(p, c); if (c.closing && !c.terminal) finish(c, ENDED, "context_changed"); }
        return !c.terminal && launch(c);
    }
    private static SpatialPincerCorridorWorld.Plan assignedTarget(Context c, int slot) { return c.targets.get(slot % c.targets.size()); }
    /** Lost/invalid assignments discard only their original shares and retain the fixed schedule. */
    private static boolean skip(Context c) {
        c.nextSlot++; c.delay = SpatialPincerCorridorRules.LAUNCH_INTERVAL;
        return true;
    }
    private static boolean launch(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; TRANSACTIONS.put(p, c);
        int slot = c.nextSlot; c.transactionSlot = slot; c.transactionPaid = c.paid;
        try {
            if (!c.confirmed || slot < 0 || slot >= c.needles || c.paid != c.published) return refuse(c, "transaction_changed");
            Source source = Source.capture(p);
            var plan = assignedTarget(c, slot);
            if (!eligible(c)) return refuse(c, "context_changed");
            if (!SpatialPincerCorridorWorld.sameTarget(plan)) return skip(c);
            TargetSnapshot target = TargetSnapshot.capture(plan.target());
            // Native pickup may merge an earlier spent original back into the offhand between emissions.
            // Freeze inventory only for this publication; monotone reserved slot indices bound the volley.
            c.hand = p.getOffhandItem(); c.originalCount = c.hand.getCount();
            if (!plain(c.hand) || c.originalCount < c.needles - slot) return refuse(c, "needles_required");
            var point = c.points[slot % 3];
            Vec3 origin = SpatialPincerCorridorWorld.origin(point);
            c.source = source.eye; c.arrival = origin;
            if (!SpatialPincerCorridorWorld.validate(plan, point, null).isEmpty()) return skip(c);
            if (!publicationValid(c, source, target, slot)) {
                if (eligible(c) && !SpatialPincerCorridorWorld.currentlyVisible(plan)) return skip(c);
                return refuse(c, "transaction_changed");
            }
            var deployed = CorridorNeedleRegistry.deploy(p, slot, origin,
                    () -> publicationValid(c, source, target, slot), () -> pay(c, source, target, slot));
            if (deployed.isEmpty()) {
                if (c.paid == c.transactionPaid && eligible(c) && !SpatialPincerCorridorWorld.currentlyVisible(plan)) return skip(c);
                return refuse(c, c.paid > c.transactionPaid ? "publication_unconfirmed" : "spawn_rejected");
            }
            DisplacedNeedleEntity medium = deployed.get(); c.published++; c.nextSlot++; c.delay = SpatialPincerCorridorRules.LAUNCH_INTERVAL;
            Flight flight = new Flight(c, slot, medium, origin); c.flights[slot] = flight; FLIGHTS.put(medium.getUUID(), flight);
            medium.setDeltaMovement(plan.endpoint().subtract(origin).normalize().scale(SpatialPincerCorridorRules.SPEED));
            if (!medium.beginFlight(flight.identity)) return refuse(c, "publication_unconfirmed");
            record(c, PROJECTED, "", medium); emit(c, PROJECTED, flight, plan.endpoint(), false, "");
            AbilityStorage.save(p); AcademyNetwork.sync(p); return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW341 publication ended without replay or refund; paid={}", c.paid, failure);
            return refuse(c, c.paid > c.transactionPaid ? "publication_unconfirmed" : "validation_failed");
        } catch (Error failure) {
            // Registry custody has already quarantined an uncertain pending original.
            // Retire every older flight and the schedule before preserving the native failure.
            try { finish(c, REFUSED, c.paid > c.transactionPaid ? "publication_unconfirmed" : "validation_failed"); }
            finally { throw failure; }
        } finally {
            c.paying = false; c.transactionSlot = -1; c.processing = false; TRANSACTIONS.remove(p, c);
            if (c.closing && !c.terminal) finish(c, ENDED, "context_changed");
        }
    }
    private static boolean publicationValid(Context c, Source source, TargetSnapshot target, int slot) {
        ServerPlayer p = c.identity.owner();
        if (!publicationState(c, source, target, slot) || HighPowerResources.hasReservation(p)) return false;
        DisplacedNeedleEntity pending = CorridorNeedleRegistry.pending(p);
        int needed = c.originalCount - (pending == null ? 0 : 1);
        if (p.getOffhandItem() != c.hand || c.hand.getCount() != needed || needed > 0 && !plain(c.hand)) return false;
        var point = c.points[slot % 3];
        if (!SpatialPincerCorridorWorld.validate(assignedTarget(c, slot), point, pending).isEmpty()) return false;
        return publicationState(c, source, target, slot) && p.getOffhandItem() == c.hand && c.hand.getCount() == needed;
    }
    private static boolean publicationState(Context c, Source source, TargetSnapshot target, int slot) {
        return c.processing && c.transactionSlot == slot && c.nextSlot == slot && c.paid == c.transactionPaid && c.published == c.transactionPaid
                && TRANSACTIONS.get(c.identity.owner()) == c && eligible(c) && !c.closing && source.matches(c.identity.owner()) && target.matches()
                && SpatialPincerCorridorWorld.sameTargetState(assignedTarget(c, slot));
    }
    /** Invoked after the registry's final callback-capable guard and native identity checks. */
    private static boolean pay(Context c, Source source, TargetSnapshot target, int slot) {
        ServerPlayer p = c.identity.owner();
        if (c.terminal || c.closing || !c.processing || c.transactionSlot != slot || c.paid != c.transactionPaid || c.published != c.transactionPaid || c.nextSlot != slot
                || CONTEXTS.get(p.getUUID()) != c || TRANSACTIONS.get(p) != c || !eligible(c) || !source.matches(p) || !target.matches()
                || !SpatialPincerCorridorWorld.sameTargetState(assignedTarget(c, slot)) || p.getOffhandItem() != c.hand || c.hand.getCount() != c.originalCount - 1) return false;
        Fences fences = readFences(p);
        if (fences == null || c.paid == 0 && (fences.cooldown > 0 || fences.cast > 0)) return false;
        c.paying = true;
        try {
            if (!HighPowerResources.trySpendSpatialPincerCorridorNeedle(p, SpatialPincerCorridorRules.cpShare(c.totalCost, slot, c.needles), c.cooldown, c.paid == 0)) return false;
            c.paid++;
            if (c.paid == 1) writeFences(p, new Fences(fences.request, c.cooldown, SpatialPincerCorridorRules.CAST_FENCE));
            return true;
        } finally { c.paying = false; }
    }
    static boolean payingNeedle(ServerPlayer p) {
        Context c = p == null ? null : CONTEXTS.get(p.getUUID());
        return c != null && c.identity.owner() == p && c.paying && c.processing && !c.closing && !c.terminal
                && c.transactionSlot == c.nextSlot && c.nextSlot >= 0 && c.nextSlot < c.needles && c.paid == c.transactionPaid && c.published == c.transactionPaid;
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
                && s.activated && s.overloadFine && !s.interfering && !s.consumptionInProgress()
                && (c.plan == null && SpatialPincerConsent.committing(p) || !AbilityConsumption.busy(p))
                && s.learned(ID) && s.learned(HighPowerSkills.CROSS_SUBSTITUTION) && s.learned(HighPowerSkills.SAFE_LANDING_PROBE) && s.learned(HighPowerSkills.DISPLACED_PROJECTION)
                && SkillCatalog.enabled("teleporter", ID) && HighPowerResources.ready(p)
                && HighPowerResources.effectiveLevel(p) >= 4 && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean mapped(ServerPlayer p, int slot) {
        return p != null && p.server.isSameThread() && slot >= 0 && slot < 4
                && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
                && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));
    }
    private static boolean validFlight(Flight f) {
        return f != null && !f.closed && f.age < SpatialPincerCorridorRules.FLIGHT_TICKS && !f.context.terminal
                && FLIGHTS.get(f.medium.getUUID()) == f && f.context.flights[f.slot] == f && eligible(f.context)
                && f.medium.identity() != null && f.medium.identity().equals(f.identity)
                && f.medium.getOwner() == f.context.identity.owner() && f.medium.level() == f.plan.world() && SpatialPincerCorridorWorld.currentlyVisible(f.plan, f.medium)
                && !f.medium.isRemoved() && f.medium.isAdmitted() && CorridorNeedleRegistry.authentic(f.context.identity.owner(), f.medium);
    }
    static boolean canContact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity id, Entity target) {
        Flight f = FLIGHTS.get(medium.getUUID());
        return f != null && f.medium == medium && id.equals(f.identity) && target == f.plan.target() && validFlight(f);
    }
    static void contact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity id, EntityHitResult hit) {
        Flight f = FLIGHTS.get(medium.getUUID()); if (f == null || f.medium != medium) return;
        boolean hurt = false;
        try {
            Vec3 origin = medium.contactSegmentOrigin();
            if (!canContact(medium, id, hit.getEntity()) || origin == null || !origin.equals(f.last)
                    || !(hit.getEntity() instanceof LivingEntity target) || !CorridorNeedleRegistry.consumeContact(medium, id.generation())) return;
            f.context.contacts++;
            EmissionPermit permit = new EmissionPermit(f, target, origin, hit.getLocation()); f.claim = permit;
            hurt = HighPowerCombat.attackSpatialPincerCorridor(permit); if (hurt) f.context.hurts++;
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
        CorridorNeedleRegistry.retireContact(medium);
    }
    private static void finishFlight(Flight f, int phase, String reason, boolean hurt, Vec3 impact) {
        if (f.closed) return; f.closed = true; FLIGHTS.remove(f.medium.getUUID(), f); f.context.flights[f.slot] = null;
        f.medium.markHarmless(f.medium.identity()); CorridorNeedleRegistry.retireContact(f.medium);
        if (!f.context.terminal) {
            record(f.context, phase, reason, f.medium);
            try { emit(f.context, phase, f, impact, hurt, reason); } catch (RuntimeException ignored) {}
            if (!f.context.processing && f.context.nextSlot >= f.context.needles && noFlights(f.context)) finish(f.context, ENDED, "");
        }
    }
    private static boolean noFlights(Context c) { for (Flight f : c.flights) if (f != null) return false; return true; }
    private static boolean refuse(Context c, String reason) { finish(c, REFUSED, reason); return false; }
    private static void finish(Context c, int phase, String reason) {
        if (c.terminal) return; c.closing = true; c.terminal = true; c.remaining = 0; c.gesture = null;
        CONTEXTS.remove(c.identity.owner().getUUID(), c);
        for (Flight f : c.flights.clone()) if (f != null) finishFlight(f, IMPACT, reason, false, f.medium.position());
        record(c, phase, reason, null);
        try { emit(c, phase, null, c.arrival, false, reason); } catch (RuntimeException ignored) {}
        if (!c.consentAdmitted && !SpatialPincerConsent.committing(c.identity.owner())) SpatialPincerConsent.clear(c.identity.owner());
    }
    private static void record(Context c, int phase, String reason, DisplacedNeedleEntity medium) {
        ServerPlayer p = c.identity.owner();
        if (p.isRemoved() || p.hasDisconnected() || p.server.getPlayerList().getPlayer(p.getUUID()) != p) return;
        RECEIPTS.put(p, new Receipt(c.command.input(), c.identity.token(), c.paid > 0, c.paid, c.published,
                c.contacts, c.hurts, phase, reason, medium == null ? null : medium.getUUID(), c.source, c.arrival));
    }
    public static Receipt receipt(ServerPlayer p) { return RECEIPTS.get(p); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return SpatialPincerConsent.committing(p) || c != null && (c.processing || c.confirmed); }
    public static boolean isTransporting(ServerPlayer p) { return SpatialPincerConsent.committing(p); }
    public static boolean marked(ServerPlayer p) { Context c = owned(p); return c != null && c.points[0] != null && !c.confirmed; }
    public static boolean previewed(ServerPlayer p) { Context c = owned(p); return c != null && c.plan != null && !c.confirmed; }
    public static List<TargetVisual> frozenTargets(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : c.targetVisuals; }
    public static int plannedNeedles(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.needles; }
    private static int countPoints(Context c) { int n = 0; for (var point : c.points) if (point != null) n++; return n; }
    public static int preparedPoints(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : countPoints(c); }
    public static int heldTicks(ServerPlayer p) { Context c = owned(p); return c == null || c.gesture == null ? 0 : c.gesture.held; }
    public static int remainingTicks(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.remaining; }
    /** Shared bracer/runtime entry; duplicate observations of the same physical tick are inert. */
    public static void tick(ServerPlayer p) { Context c = owned(p); if (c != null && p.server.isSameThread()) tick(c); }
    private static Context owned(ServerPlayer p) { Context c = p == null ? null : CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
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
    private static boolean finite(Vec3 v) { return SpatialPincerCorridorWorld.finite(v); }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }
    private static Fences readFences(ServerPlayer p) {
        var root = p.getPersistentData(); if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null; var t = root.getCompound(DATA);
        if (!t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1 || !t.contains("request", Tag.TAG_INT)
                || !t.contains("cooldown", Tag.TAG_INT) || !t.contains("cast", Tag.TAG_INT)) return null;
        int r = t.getInt("request"), d = t.getInt("cooldown"), f = t.getInt("cast");
        return r < 0 || r > 4 || d < 0 || d > 80 || f < 0 || f > 8 ? null : new Fences(r, d, f);
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
            LOG.warn("NEW341 observed tick ended without resuming its schedule", failure);
            finish(c, ENDED, "validation_failed");
        }
        catch (Error failure) { try { finish(c, ENDED, "validation_failed"); } finally { throw failure; } }
    }
    private static void advance(Context c) {
        ServerPlayer p = c.identity.owner(); if (c.processing || c.lastTick == now(p)) return; c.lastTick = now(p);
        if (!eligible(c)) { finish(c, ENDED, "context_changed"); return; }
        if (c.gesture != null && c.gesture.lastTick != now(p)) { c.gesture.lastTick = now(p); c.gesture.held = Math.min(SpatialPincerCorridorRules.CLEAR_HOLD_TICKS, c.gesture.held + 1); }
        if (!c.confirmed) {
            if (c.points[0] == null) return;
            if (--c.remaining <= 0) { finish(c, ENDED, "expired"); return; }
            if (now(p) < c.lastVisual || now(p) - c.lastVisual >= 10)
                try { emit(c, c.plan == null ? MARKED : PREVIEW, null, c.plan == null ? c.arrival : c.plan.endpoint(), true, ""); } catch (RuntimeException failure) { finish(c, ENDED, "visual_unavailable"); }
            return;
        }
        for (Flight f : c.flights.clone()) if (f != null && f.bornTick != now(p)) {
            f.age++;
            if (!validFlight(f)) finishFlight(f, IMPACT, "flight_ended", false, f.medium.position());
        }
        if (c.terminal) return;
        if (c.nextSlot < c.needles && --c.delay <= 0) launch(c);
        if (c.nextSlot >= c.needles && noFlights(c)) finish(c, ENDED, "");
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post e) {
        Integer previous = POST_CLOCKS.put(e.getServer(), e.getServer().getTickCount());
        if (previous != null && previous == e.getServer().getTickCount()) return;
        for (Flight f : new ArrayList<>(FLIGHTS.values())) if (f.context.identity.owner().server == e.getServer()) {
            Vec3 position = f.medium.position();
            if (!validFlight(f) || !finite(position)) { finishFlight(f, IMPACT, "flight_ended", false, f.last); continue; }
            f.traveled += f.last.distanceTo(position); f.last = position;
            if (!SpatialPincerCorridorRules.path(f.context.identity.owner().getEyePosition().distanceTo(f.origin), f.traveled)
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
        Vec3 a = c.points[0] == null ? Vec3.ZERO : c.points[0].destination();
        Vec3 b = c.points[1] == null ? Vec3.ZERO : c.points[1].destination();
        Vec3 d = c.points[2] == null ? Vec3.ZERO : c.points[2].destination();
        long visualInput = phase == MARKED || phase == PICKER || phase == PREVIEW ? c.preparationInput : c.command.input();
        Visual packet = new Visual(p.getId(), p.getUUID(), visualInput, c.command.epoch(), c.identity.token(), ++c.sequence,
                phase, countPoints(c), !c.confirmed && !c.terminal ? c.remaining : 0, a, b, d,
                flight == null ? c.source : flight.source, flight == null ? c.arrival : flight.origin, impact,
                flight == null ? -1 : flight.slot, flight == null ? -1 : flight.medium.getId(),
                flight == null ? NONE : flight.medium.getUUID(), accepted, reason, c.targetVisuals, c.needles);
        if (!p.hasDisconnected()) PacketDistributor.sendToPlayer(p, packet);
        // Planning and frozen candidate identities are private to the authenticated owner.
        if (phase != PROJECTED && phase != IMPACT && phase != ENDED) return;
        Visual observed = new Visual(packet.entity, packet.owner, packet.input, packet.epoch, packet.token, packet.sequence,
                packet.phase, packet.prepared, packet.remaining, packet.pointA, packet.pointB, packet.pointC,
                packet.source, packet.arrival, packet.impact, packet.needleSlot, packet.needleEntity, packet.needleUuid,
                packet.accepted, packet.reason, List.of(), packet.volleyNeedles);
        int sent = 1;
        for (ServerPlayer observer : p.serverLevel().players())
            if (observer != p && !observer.hasDisconnected() && observer.position().distanceToSqr(c.arrival) <= 64 * 64) {
                PacketDistributor.sendToPlayer(observer, observed); if (++sent >= 32) break;
            }
    }
    public record TargetVisual(int entity, UUID uuid, Vec3 endpoint) {
        public TargetVisual {
            if (entity < 0 || uuid == null || uuid.equals(NONE) || !finite(endpoint))
                throw new IllegalArgumentException("Invalid NEW341 frozen target");
        }
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         int prepared, int remaining, Vec3 pointA, Vec3 pointB, Vec3 pointC, Vec3 source, Vec3 arrival, Vec3 impact,
                         int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason,
                         List<TargetVisual> targets, int volleyNeedles) implements CustomPacketPayload {
        public Visual {
            if (targets == null || targets.size() > SpatialPincerCorridorRules.MAX_TARGETS)
                throw new IllegalArgumentException("Invalid NEW341 frozen targets");
            targets = List.copyOf(targets);
            var entities = new HashSet<Integer>(); var identities = new HashSet<UUID>();
            for (TargetVisual target : targets)
                if (!entities.add(target.entity) || !identities.add(target.uuid)) throw new IllegalArgumentException("Duplicate NEW341 frozen target");
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0 || phase < 1 || phase > PREVIEW
                    || prepared < 0 || prepared > 3 || remaining < 0 || remaining > SpatialPincerCorridorRules.PREPARATION_TICKS || needleSlot < -1 || needleSlot >= SpatialPincerCorridorRules.MAX_NEEDLES
                    || volleyNeedles < 0 || volleyNeedles > SpatialPincerCorridorRules.MAX_NEEDLES || volleyNeedles > 0 && volleyNeedles < 3
                    || !targets.isEmpty() && volleyNeedles != SpatialPincerCorridorRules.needleCount(targets.size())
                    || needleEntity < -1 || needleUuid == null || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || !finite(pointA) || !finite(pointB) || !finite(pointC) || !finite(source) || !finite(arrival) || !finite(impact)
                    || accepted && phase != MARKED && phase != IMPACT && phase != PICKER && phase != PREVIEW
                    || phase == MARKED && (prepared < 1 || remaining < 1 || !accepted)
                    || phase == PICKER && (prepared != 3 || remaining < 1 || !accepted)
                    || (phase == MARKED || phase == PICKER) && (!targets.isEmpty() || volleyNeedles != 0)
                    || phase == PREVIEW && (prepared != 3 || remaining < 1 || !accepted || targets.isEmpty())
                    || (phase == ENDED || phase == REFUSED) && remaining != 0
                    || (phase == PROJECTED || phase == IMPACT) && (prepared != 3 || remaining != 0 || needleSlot < 0
                            || needleSlot >= volleyNeedles || needleEntity < 0 || needleUuid.equals(NONE))
                    || phase != PROJECTED && phase != IMPACT && (needleSlot != -1 || needleEntity != -1 || !needleUuid.equals(NONE)))
                throw new IllegalArgumentException("Invalid NEW341 visual");
        }
        /** Source compatibility for retained three-shot fixtures and observer-only receipts. */
        public Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                      int prepared, int remaining, Vec3 pointA, Vec3 pointB, Vec3 pointC, Vec3 source, Vec3 arrival, Vec3 impact,
                      int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason) {
            this(entity, owner, input, epoch, token, sequence, phase, prepared, remaining, pointA, pointB, pointC,
                    source, arrival, impact, needleSlot, needleEntity, needleUuid, accepted, reason, List.of(),
                    phase == PROJECTED || phase == IMPACT ? 3 : 0);
        }
        public static final String PROTOCOL_VERSION = "2";
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_spatial_pincer_corridor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch); b.writeLong(v.token); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeByte(v.prepared); b.writeInt(v.remaining); vec(b, v.pointA); vec(b, v.pointB); vec(b, v.pointC);
            vec(b, v.source); vec(b, v.arrival); vec(b, v.impact); b.writeInt(v.needleSlot); b.writeInt(v.needleEntity);
            b.writeUUID(v.needleUuid); b.writeBoolean(v.accepted); b.writeUtf(v.reason, 64);
            b.writeByte(v.targets.size());
            for (TargetVisual target : v.targets) { b.writeInt(target.entity); b.writeUUID(target.uuid); vec(b, target.endpoint); }
            b.writeByte(v.volleyNeedles);
        }, b -> {
            int entity = b.readInt(); UUID owner = b.readUUID(); long input = b.readLong(), epoch = b.readLong(), token = b.readLong(), sequence = b.readLong();
            int phase = b.readUnsignedByte(), prepared = b.readUnsignedByte(), remaining = b.readInt();
            Vec3 a = vec(b), c = vec(b), d = vec(b), source = vec(b), arrival = vec(b), impact = vec(b);
            int slot = b.readInt(), needle = b.readInt(); UUID uuid = b.readUUID(); boolean accepted = b.readBoolean(); String reason = b.readUtf(64);
            int count = b.readUnsignedByte();
            if (count > SpatialPincerCorridorRules.MAX_TARGETS) throw new IllegalArgumentException("Invalid NEW341 target count");
            List<TargetVisual> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) targets.add(new TargetVisual(b.readInt(), b.readUUID(), vec(b)));
            return new Visual(entity, owner, input, epoch, token, sequence, phase, prepared, remaining, a, c, d,
                    source, arrival, impact, slot, needle, uuid, accepted, reason, targets, b.readUnsignedByte());
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar(Visual.PROTOCOL_VERSION).playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

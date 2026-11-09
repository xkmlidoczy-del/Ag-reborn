package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.EmergencyReturnRules;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.CombatAmounts;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
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

/** NEW342: one unsaved grounded mark; manual CP return or one settled PvE escape.
 * Damage is observed only through HighPowerCombat's closed native hurt frame. A
 * queued escape gets exactly the next physical Pre, never an airborne rescue or retry. */
@EventBusSubscriber(modid = "academy")
public final class EmergencyReturn {
    public static final String ID = "emergency_return";
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    public static final int HOLD_TICKS = 6;
    private static final String DATA = "academy:emergency_return_fences";
    private static final int MAX_CONTEXTS = 64, MAX_TERMINALS = 256;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<ServerPlayer, Context> TRANSACTIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new LinkedHashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, PhysicalClock> CLOCKS = new WeakHashMap<>();
    private static long nextToken;
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.emergencyReturnDiagnostic");
    private static int diagnosticLines;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.emergency_return");
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record PhysicalClock(int tick, long generation) {}
    private record Fences(int request, int cooldown, int automatic) {
        static final Fences ZERO = new Fences(0, 0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1), Math.max(0, automatic - 1)); }
    }
    public record Receipt(long input, long token, boolean paid, boolean moved, int phase,
                          String reason, Vec3 source, Vec3 destination, boolean automatic) {}
    private static final class Gesture {
        final HighPowerInput.Input input;
        int held;
        Gesture(HighPowerInput.Input input) { this.input = input; }
    }
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouching) {
        static Source capture(ServerPlayer p) {
            return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(), p.getPose(),
                    p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown());
        }
        boolean sameBody(ServerPlayer p) {
            return grounded == p.onGround() && position.equals(p.position()) && body.equals(p.getBoundingBox())
                    && pose == p.getPose() && Float.compare(fall, p.fallDistance) == 0 && fall == 0
                    && crouching == p.isShiftKeyDown();
        }
        boolean sameGroundedBody(ServerPlayer p) { return grounded && sameBody(p); }
        boolean matches(ServerPlayer p) {
            return sameBody(p) && eye.equals(p.getEyePosition()) && motion.equals(p.getDeltaMovement())
                    && Float.compare(yaw, p.getYRot()) == 0 && Float.compare(pitch, p.getXRot()) == 0;
        }
    }
    /** Everything a teleport callback could otherwise change between quote and debit. */
    private record Snapshot(Source source, CompoundTag ledger, CompoundTag ledgerCopy, ExactCombatState combat,
                            BigDecimal growth, BigDecimal earned, long growthRevision, int level,
                            double experience, Map<String, Double> skills, Map<String, Integer> cooldowns,
                            HighPowerInput.Input command) {
        static Snapshot capture(Context c) {
            var p = c.identity.owner(); var state = c.identity.state(); var growth = HighPowerGrowth.values(p);
            var ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            return new Snapshot(Source.capture(p), ledger, ledger.copy(), HighPowerCombat.exactStateSnapshot(p).orElse(null),
                    growth.position(), growth.earnedPosition(), HighPowerActiveGrowth.revision(p), state.level,
                    state.levelExperience, Map.copyOf(state.experience), Map.copyOf(state.cooldowns), c.command);
        }
        boolean resourcesMatch(Context c) {
            var p = c.identity.owner(); var state = c.identity.state(); var values = HighPowerGrowth.values(p);
            return command == c.command && ledger == p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)
                    && ledgerCopy.equals(ledger) && growth.compareTo(values.position()) == 0
                    && earned.compareTo(values.earnedPosition()) == 0 && growthRevision == HighPowerActiveGrowth.revision(p)
                    && level == state.level && Double.compare(experience, state.levelExperience) == 0
                    && skills.equals(state.experience) && cooldowns.equals(state.cooldowns);
        }
        boolean matches(Context c) {
            return source.matches(c.identity.owner()) && resourcesMatch(c) && combat != null
                    && HighPowerCombat.exactStateSnapshot(c.identity.owner()).filter(combat::equals).isPresent();
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;
        final long revision;
        HighPowerInput.Input command;
        Gesture gesture;
        SafeLandingProbeWorld.EmergencyAnchor anchor;
        SafeLandingProbeWorld.EmergencyCandidate candidate;
        Capture capture, queued;
        Snapshot attempt;
        Vec3 source;
        int remaining;
        long lastPhysical, queuedAfter, lastVisual, sequence;
        boolean admitted, automatic, processing, closing, reentered, terminal, retired, paid, moved, paymentClaimed, warningAdvertised;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = p.position(); lastPhysical = lastVisual = generation(p.server);
        }
    }

    /** Opaque native-frame evidence, bound to one owner, one mark and one accepted hurt.
     * No public constructor accepts positions, loss amounts or a substitute velocity. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Source before;
        private final long physical;
        private Vec3 postMotion;
        private boolean settled, survived, sealed, invalid;
        private boolean connectionCycleSeen;
        private EmergencyReturnConnectionCycle.Witness connectionWitness;
        private SafeLandingProbeWorld.EmergencyConnectionGround connectionGround;
        private boolean connectionGroundPrepared;
        private BigDecimal actualPoolLoss = BigDecimal.ZERO;
        private Capture(Context context, DamageSource source) {
            this.context = context; this.source = source; before = Source.capture(owner()); physical = generation(owner().server);
        }
        public ServerPlayer owner() { return context.identity.owner(); }
        public Vec3 prePosition() { return before.position; }
        public Vec3 preMotion() { return before.motion; }
        public AABB preBody() { return before.body; }
        public Pose prePose() { return before.pose; }
        public boolean preGrounded() { return before.grounded; }
        public Vec3 postMotion() { return postMotion; }
        private boolean validEvidence(ServerPlayer p) {
            return p == owner() && sealed && !invalid && settled && survived && actualPoolLoss.signum() > 0
                    && context.capture == this && context.queued == this && context.admitted && context.automatic
                    && !context.closing && !context.terminal && identity(context)
                    && generation(p.server) >= physical && generation(p.server) <= physical + 1;
        }
        boolean matchesOriginalSeal(ServerPlayer p) {
            return validEvidence(p) && before.sameGroundedBody(p) && finite(postMotion) && postMotion.equals(p.getDeltaMovement());
        }
        boolean beginConnectionCycle(ServerPlayer p) {
            if (connectionCycleSeen || !matchesOriginalSeal(p) || generation(p.server) != physical || context.processing) {
                invalid = true; return false;
            }
            connectionCycleSeen = true; return true;
        }
        void freezeConnectionGround(SafeLandingProbeWorld.EmergencyConnectionGround ground) {
            if (connectionGroundPrepared || connectionCycleSeen) { invalid = true; return; }
            connectionGroundPrepared = true; connectionGround = ground;
        }
        SafeLandingProbeWorld.EmergencyConnectionGround connectionGround() { return connectionGround; }
        boolean currentConnectionCycle(ServerPlayer p) {
            return connectionCycleSeen && validEvidence(p) && generation(p.server) == physical && !context.processing;
        }
        void attachConnectionWitness(EmergencyReturnConnectionCycle.Witness witness) {
            if (!connectionCycleSeen || connectionWitness != null || witness == null || !witness.belongsTo(this)) { invalid = true; return; }
            connectionWitness = witness;
        }
        void invalidateConnectionCycle() { invalid = true; }
        boolean revalidateConnectionGround(ServerPlayer p) {
            return !connectionCycleSeen || connectionWitness != null && connectionWitness.revalidateGround(p);
        }
        public boolean matchesAutoSource(ServerPlayer p) {
            return validEvidence(p) && (connectionCycleSeen
                    ? connectionWitness != null && connectionWitness.matchesRestored(p)
                    : matchesOriginalSeal(p));
        }
    }

    /** The pending capture stays package-private and can only be returned for its authenticated owner. */
    static Capture captureForConnectionCycle(ServerPlayer p) {
        Context c = owned(p);
        return c == null || c.queued == null || !c.automatic || !c.admitted || c.terminal || c.closing ? null : c.queued;
    }

    private EmergencyReturn() {}

    public static boolean start(ServerPlayer p, String wire) { return press(p, wire); }
    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { invalidate(transaction); return false; }
        if (!claimInput(p, input) || AbilityConsumption.busy(p) || !mapped(p, input.slot())) return false;
        Context c = CONTEXTS.get(p.getUUID());
        if (c != null && (!identity(c) || c.identity.owner() != p || c.command.epoch() != input.epoch()
                || c.command.slot() != input.slot())) { finish(c, ENDED, "context_changed"); return false; }
        if (c != null && (c.processing || c.gesture != null || c.admitted || c.closing)) return false;
        if (c == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= MAX_CONTEXTS) return false;
            c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        }
        c.gesture = new Gesture(input); return true;
    }
    /** Press never acts. Six genuine ticks followed by release clears for free. */
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context transaction = TRANSACTIONS.get(p);
        if (transaction != null) { invalidate(transaction); return false; }
        Context c = owned(p);
        if (c == null || c.gesture == null || !c.gesture.input.equals(input) || c.processing) return false;
        int held = c.gesture.held; c.gesture = null; c.command = input;
        if (held >= HOLD_TICKS) { finish(c, ENDED, "cleared"); return true; }
        if (c.admitted || c.closing) return false;
        begin(c);
        try {
            String reason = controlRefusal(c, false);
            if (!reason.isEmpty()) return refuse(c, reason);
            Fences fences = readFences(p);
            if (fences == null) return refuse(c, "storage_invalid");
            if (fences.request > 0) return refuse(c, "request_fence");
            writeFences(p, new Fences(EmergencyReturnRules.REQUEST_INTERVAL_TICKS, fences.cooldown, fences.automatic));
            Snapshot before = Snapshot.capture(c);
            if (c.anchor == null) {
                var marked = SafeLandingProbeWorld.emergencyMark(p);
                if (!marked.valid()) return refuse(c, marked.reason());
                if (!unchanged(c, before)) return refuse(c, "event_changed_state");
                c.anchor = marked.anchor(); c.remaining = EmergencyReturnRules.MARK_LIFE_TICKS;
                c.source = before.source.position; record(c, MARKED, ""); emit(c, MARKED, ""); return true;
            }
            reason = manualResources(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            var assessment = SafeLandingProbeWorld.emergencyReturning(p, c.anchor);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            if (!unchanged(c, before)) return refuse(c, "event_changed_state");
            // The admitted operation owns this mark even if a later native callback vetoes it.
            c.admitted = true; c.candidate = assessment.candidate(); c.source = before.source.position;
            c.attempt = before; return commit(c);
        } catch (RuntimeException failure) {
            LOG.warn("NEW342 release ended after native validation failure; paid={}", c.paid, failure);
            observeMovement(c);
            return refuse(c, c.paid ? "transport_unconfirmed" : "validation_failed");
        } finally { end(c); }
    }

    /** Called at the native hurt head before any incoming callback or knockback. */
    public static Capture captureBeforeHurt(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null) return null;
        // Every later hit invalidates earlier evidence, including an unsupported/zero-loss hit.
        if (c.capture != null) c.capture.invalid = true;
        c.capture = null;
        if (c.admitted || c.queued != null) {
            if (c.processing) invalidate(c); else finish(c, REFUSED, "new_damage");
            return null;
        }
        if (c.processing) { invalidate(c); return null; }
        if (c.anchor == null || c.remaining <= 0 || c.closing) return null;
        begin(c);
        try {
            if (!controlRefusal(c, false).isEmpty() || !HighPowerCombat.reviewedEmergencyReturnSource(p, source)) return null;
            Fences fences = readFences(p);
            if (fences == null || fences.automatic > 0 || SafeLandingProbe.sharedMovementRemaining(p) != 0) return null;
            Source before = Source.capture(p);
            if (!SafeLandingProbeWorld.emergencySourceRefusal(p).isEmpty() || !before.matches(p)
                    || !controlRefusal(c, false).isEmpty() || c.reentered) return null;
            Capture capture = new Capture(c, source); c.capture = capture; return capture;
        } catch (RuntimeException failure) {
            LOG.warn("NEW342 ignored unsupported native pre-hit state", failure); return null;
        } finally { end(c); }
    }
    /** A positive surviving exact HP or base-shield loss, after the common ledger commit, is necessary. */
    public static void settled(Capture capture, BigDecimal actualPoolLoss, boolean survived) {
        try { settleEvidence(capture, actualPoolLoss, survived); }
        catch (RuntimeException failure) {
            if (capture != null) capture.invalid = true;
            LOG.warn("NEW342 ignored unavailable settled evidence; native damage remains authoritative", failure);
        }
    }
    private static void settleEvidence(Capture capture, BigDecimal actualPoolLoss, boolean survived) {
        if (capture == null || capture.invalid || capture.sealed || capture.settled || !thread(capture.owner())) return;
        Context c = capture.context;
        if (c.capture != capture || c.admitted || c.closing || !identity(c)
                || capture.physical != generation(capture.owner().server)) { capture.invalid = true; return; }
        capture.settled = true;
        if (actualPoolLoss == null || actualPoolLoss.signum() <= 0 || !survived) { capture.invalid = true; return; }
        try { capture.actualPoolLoss = CombatAmounts.requirePositiveAmount(actualPoolLoss, "settled emergency-return loss"); }
        catch (IllegalArgumentException invalid) { capture.invalid = true; return; }
        capture.survived = true;
    }
    /** Runs only after the complete native hurt has sealed its real knockback and recovery. */
    public static void sealAfterHurt(ServerPlayer p, Capture capture, boolean completed) {
        try { sealEvidence(p, capture, completed); }
        catch (RuntimeException failure) {
            if (capture != null) {
                capture.invalid = true;
                if (capture.context.admitted) invalidate(capture.context);
            }
            LOG.warn("NEW342 ignored unavailable native hurt seal; native damage remains authoritative", failure);
        }
    }
    private static void sealEvidence(ServerPlayer p, Capture capture, boolean completed) {
        if (capture == null || p != capture.owner() || !thread(p) || capture.sealed) return;
        capture.sealed = true; Context c = capture.context;
        if (!completed || capture.invalid || !capture.settled || !capture.survived || capture.actualPoolLoss.signum() <= 0
                || c.capture != capture || c.anchor == null || c.admitted || c.processing || c.closing
                || !controlEligible(c) || capture.physical != generation(p.server) || !capture.before.sameGroundedBody(p)
                || !finite(p.getDeltaMovement()) || !commonMovementRefusal(c).isEmpty()) { capture.invalid = true; return; }
        Fences fences = readFences(p);
        if (fences == null || fences.automatic > 0 || SafeLandingProbe.sharedMovementRemaining(p) != 0
                || HighPowerCombat.exactStateSnapshot(p).map(EmergencyReturnRules::automaticSpend).orElse(null) == null) {
            capture.invalid = true; return;
        }
        double distance = p.position().distanceToSqr(c.anchor.destination());
        if (distance <= 1e-8 || distance > EmergencyReturnRules.RANGE * EmergencyReturnRules.RANGE) {
            capture.invalid = true; return;
        }
        // The native impulse is evidence, not an instruction to reset or reconstruct velocity.
        capture.postMotion = p.getDeltaMovement(); c.queued = capture; c.automatic = true; c.admitted = true;
        c.gesture = null; c.source = capture.before.position; c.queuedAfter = generation(p.server);
        // Freeze optional native-cycle footing no later than the hurt seal. Later floor or
        // friction changes cannot become a fresh, apparently valid starting environment.
        EmergencyReturnConnectionCycle.prepareSeal(capture);
        if (c.terminal) return;
        if (capture.invalid || !capture.matchesOriginalSeal(p)) { finish(c, REFUSED, "source_motion_changed"); return; }
        try { record(c, WARNING, ""); emit(c, WARNING, ""); }
        catch (RuntimeException failure) { finish(c, REFUSED, "visual_unavailable"); }
    }

    private static void attemptAutomatic(Context c) {
        begin(c);
        try {
            String reason = controlRefusal(c, true);
            if (!reason.isEmpty()) { refuse(c, reason); return; }
            Fences fences = readFences(c.identity.owner());
            if (fences == null || SafeLandingProbe.sharedMovementRemaining(c.identity.owner()) < 0) { refuse(c, "storage_invalid"); return; }
            if (fences.automatic > 0 || SafeLandingProbe.sharedMovementRemaining(c.identity.owner()) > 0) { refuse(c, "cooldown"); return; }
            c.attempt = Snapshot.capture(c);
            var assessment = SafeLandingProbeWorld.emergencyReturning(c.identity.owner(), c.anchor, c.queued);
            if (!assessment.valid()) { refuse(c, assessment.reason()); return; }
            if (!unchanged(c, c.attempt)) { refuse(c, "event_changed_state"); return; }
            c.candidate = assessment.candidate(); commit(c);
        } catch (RuntimeException failure) {
            LOG.warn("NEW342 one-shot automatic attempt ended; paid={}", c.paid, failure);
            observeMovement(c);
            refuse(c, c.paid ? "transport_unconfirmed" : "validation_failed");
        } finally {
            if (!c.terminal) finish(c, REFUSED, "attempt_ended");
            end(c);
        }
    }
    private static boolean commit(Context c) {
        var p = c.identity.owner(); Snapshot before = c.attempt;
        if (before == null || !unchanged(c, before)) return refuse(c, "event_changed_state");
        EmergencyReturnBudget.Payment payment = c.automatic ? EmergencyReturnBudget.quote(p, c) : null;
        if (c.automatic && payment == null) return refuse(c, "insufficient_budget");
        Vec3 destination = c.candidate.destination();
        EntityTeleportEvent event = new EntityTeleportEvent(p, destination.x, destination.y, destination.z);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return refuse(c, "event_cancelled");
        if (Double.compare(event.getTargetX(), destination.x) != 0 || Double.compare(event.getTargetY(), destination.y) != 0
                || Double.compare(event.getTargetZ(), destination.z) != 0) return refuse(c, "event_redirected");
        if (!unchanged(c, before)) return refuse(c, "event_changed_state");
        String reason = controlRefusal(c, c.automatic);
        if (!reason.isEmpty()) return refuse(c, reason);
        var check = SafeLandingProbeWorld.revalidateEmergencyReturn(p, c.candidate);
        if (!check.valid()) return refuse(c, check.reason());
        if (!unchanged(c, before)) return refuse(c, "event_changed_state");
        if (c.automatic) {
            if (!EmergencyReturnBudget.valid(p, payment)) return refuse(c, "budget_changed");
            var result = EmergencyReturnBudget.pay(p, payment);
            if (result == EmergencyReturnBudget.Result.NOT_PAID) return refuse(c, "insufficient_budget");
            c.paid = true;
            if (result != EmergencyReturnBudget.Result.PAID) return refuse(c, "payment_unconfirmed");
            if (!controlEligible(c) || c.closing || c.reentered || !before.source.matches(p) || !before.resourcesMatch(c))
                return refuse(c, "commit_unconfirmed");
        } else {
            reason = manualResources(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            Fences fences = readFences(p);
            int cooldown = EmergencyReturnRules.cooldownTicks(before.growth);
            if (!HighPowerResources.trySpend(p, ID, EmergencyReturnRules.cpCost(before.growth), cooldown))
                return refuse(c, "insufficient_cp");
            c.paid = true;
            writeFences(p, new Fences(fences.request, cooldown, fences.automatic));
            SafeLandingProbe.commitSharedMovement(p);
        }
        ExactCombatState expected = c.automatic ? EmergencyReturnRules.automaticSpend(before.combat) : before.combat;
        if (c.closing || c.reentered || !controlEligible(c) || !before.source.matches(p) || expected == null
                || !HighPowerCombat.exactStateSnapshot(p).filter(expected::equals).isPresent())
            return refuse(c, "commit_unconfirmed");
        // Native arguments are absolute; ROTATION changes packet encoding, not the server target.
        // Absolute XYZ preserves the proven destination; no velocity/fall/health repair is applied.
        p.connection.teleport(destination.x, destination.y, destination.z, before.source.yaw, before.source.pitch, RelativeMovement.ROTATION);
        c.moved = p.serverLevel() == c.identity.level() && p.position().equals(destination);
        if (c.closing || c.reentered || !controlEligible(c) || !c.moved || !p.getDeltaMovement().equals(before.source.motion)
                || Float.compare(p.fallDistance, before.source.fall) != 0 || p.onGround() != before.source.grounded
                || Float.compare(p.getYRot(), before.source.yaw) != 0 || Float.compare(p.getXRot(), before.source.pitch) != 0
                || expected == null || !HighPowerCombat.exactStateSnapshot(p).filter(expected::equals).isPresent())
            return refuse(c, "commit_unconfirmed");
        if(c.automatic&&c.capture!=null)EsperShields.suppressPhaseForEscape(p,c.capture.source);
        finish(c, SUCCESS, "");
        try { if (retainReceipt(c) && c.identity.owns(p)) { AbilityStorage.save(p); AcademyNetwork.sync(p); } }
        catch (RuntimeException failure) { LOG.warn("NEW342 completed transport; sync failure cannot refund or retry", failure); }
        return true;
    }

    /** Closed budget capability check; no external callbacks and no arbitrary skill spender. */
    static boolean validPayment(ServerPlayer p, Object transaction) {
        if (!(transaction instanceof Context c) || !thread(p) || c.identity.owner() != p || TRANSACTIONS.get(p) != c
                || !c.processing || !c.automatic || !c.admitted || c.paymentClaimed || c.paid || c.attempt == null
                || c.candidate == null || c.queued == null || c.closing || c.reentered || !identity(c)
                || !c.queued.matchesAutoSource(p) || !c.attempt.matches(c)) return false;
        Fences fences = readFences(p);
        return fences != null && fences.automatic == 0 && SafeLandingProbe.sharedMovementRemaining(p) == 0;
    }
    /** The non-refundable fence is installed before the exact S writer can notify observers. */
    static boolean claimAutomaticPayment(ServerPlayer p, Object transaction) {
        if (!validPayment(p, transaction)) return false;
        Context c = (Context)transaction; Fences fences = readFences(p);
        c.paymentClaimed = true; c.paid = true;
        writeFences(p, new Fences(fences.request, fences.cooldown, EmergencyReturnRules.AUTOMATIC_FENCE_TICKS));
        SafeLandingProbe.commitSharedMovementFromPre(p); return true;
    }

    private static String manualResources(Context c) {
        var p = c.identity.owner(); Fences fences = readFences(p); int shared = SafeLandingProbe.sharedMovementRemaining(p);
        if (fences == null || shared < 0) return "storage_invalid";
        if (HighPowerResources.hasReservation(p)) return "resource_busy";
        if (fences.cooldown > 0 || shared > 0 || !c.identity.state().canUse(ID)
                || p.serverLevel().getGameTime() < p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID))
            return "cooldown";
        double cost = EmergencyReturnRules.cpCost(HighPowerGrowth.values(p).position());
        return !Double.isFinite(cost) || cost <= 0 || HighPowerResources.availableCp(p) < cost ? "insufficient_cp" : "";
    }
    private static String controlRefusal(Context c, boolean automatic) {
        var p = c.identity.owner();
        if (!controlEligible(c) || c.closing || c.reentered || c.remaining <= 0 && c.anchor != null) return "context_changed";
        if ((!p.onGround() && (!automatic || c.queued == null || !c.queued.matchesAutoSource(p)))
                || !Float.isFinite(p.fallDistance) || p.fallDistance != 0) return "source_airborne";
        if (automatic ? c.queued == null || !c.queued.matchesAutoSource(p)
                : !EmergencyReturnRules.groundedVerticalMotion(p.getDeltaMovement().y)) return "source_motion_changed";
        return commonMovementRefusal(c);
    }
    private static String commonMovementRefusal(Context c) {
        var p = c.identity.owner();
        if (p.noPhysics || p.isPassenger() || p.isVehicle() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.isInWater() || p.isInLava() || p.getPose() != Pose.STANDING
                || !finite(p.position()) || !finite(p.getEyePosition()) || !finite(p.getDeltaMovement())
                || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return "source_motion_or_pose_unsupported";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                || p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) return "movement_conflict";
        if (CornerExchange.active(p) || CrossSubstitution.isTransporting(p) || ReturnMark.active(p) || SafeLandingProbe.active(p)
                || PhaseSidestep.active(p) || (BeamStep.active(p)||WindPressureLift.active(p)) || MagneticGroundStep.active(p) || PolarityTether.active(p)
                || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.LocationTeleport.active(p) || cn.academy.port.skill.Flashing.active(p)) return "movement_conflict";
        return "";
    }
    private static boolean controlEligible(Context c) {
        var p = c.identity.owner(); var state = c.identity.state();
        return identity(c) && p.isAlive() && !p.isRemoved() && !p.hasDisconnected() && !p.isSpectator()
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && p.containerMenu == p.inventoryMenu
                && state.activated && state.overloadFine && !state.interfering && !state.consumptionInProgress()
                && !AbilityConsumption.busy(p) && state.learned(ID) && state.level >= 4 && state.level <= 5
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID) && HighPowerCombat.emergencyReturnBenefitsActive(p);
    }
    private static boolean identity(Context c) {
        var p = c.identity.owner();
        return !c.terminal && CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && mapped(p, c.command.slot())
                && c.identity.state().presets.current() == c.preset && c.identity.state().presets.revision() == c.revision;
    }
    private static boolean mapped(ServerPlayer p, int slot) {
        return thread(p) && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
                && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));
    }
    private static boolean unchanged(Context c, Snapshot snapshot) {
        return !c.closing && !c.reentered && c.processing && TRANSACTIONS.get(c.identity.owner()) == c
                && controlEligible(c) && snapshot.matches(c);
    }
    private static void begin(Context c) { c.processing = true; TRANSACTIONS.put(c.identity.owner(), c); }
    private static void invalidate(Context c) { c.reentered = true; c.closing = true; if (c.capture != null) c.capture.invalid = true; }
    private static void end(Context c) {
        TRANSACTIONS.remove(c.identity.owner(), c); c.processing = false;
        if (!c.terminal && c.closing) finish(c, ENDED, "context_changed");
    }
    private static boolean refuse(Context c, String reason) {
        if (!c.admitted && c.anchor != null && c.remaining > 0 && !c.closing && !c.reentered && controlEligible(c)) {
            record(c, REFUSED, reason); emit(c, REFUSED, reason);
        } else finish(c, REFUSED, reason);
        return false;
    }
    private static void finish(Context c, int phase, String reason) {
        if (c.terminal) return;
        c.terminal = true; c.closing = true; c.remaining = 0; c.gesture = null;
        if (c.capture != null) c.capture.invalid = true;
        CONTEXTS.remove(c.identity.owner().getUUID(), c);
        record(c, phase, reason);
        try { if (retainReceipt(c) || c.warningAdvertised) emit(c, phase, reason); }
        catch (RuntimeException failure) { LOG.warn("NEW342 retained terminal receipt after unavailable visual", failure); }
    }
    private static boolean claimInput(ServerPlayer p, HighPowerInput.Input input) {
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.owner == p && old.epoch == input.epoch() && input.input() <= old.input) return false;
        if (old == null && INPUTS.size() >= MAX_TERMINALS) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input())); return true;
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.owner != p || old.epoch != input.epoch() || input.input() > old.input) claimInput(p, input);
        Context c = owned(p);
        if (c == null || c.command.epoch() != input.epoch() || c.command.slot() != input.slot()
                || input.input() < (c.gesture == null ? c.command.input() : c.gesture.input.input())) return false;
        c.command = input; clear(p); return true;
    }
    /** Cleanup never resets a paid persistent fence. */
    public static void clear(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        invalidate(c); if (!c.processing) finish(c, ENDED, "context_changed");
    }
    public static void cancelActive(ServerPlayer p) { clear(p); }
    public static boolean active(ServerPlayer p) { return isTransporting(p); }
    public static boolean isTransporting(ServerPlayer p) {
        Context c = owned(p); return c != null && !c.terminal && (c.processing || c.admitted);
    }
    public static boolean marked(ServerPlayer p) { Context c = owned(p); return c != null && c.anchor != null && !c.admitted && !c.closing; }
    public static int remainingTicks(ServerPlayer p) { Context c = owned(p); return c == null || c.admitted ? 0 : c.remaining; }
    public static int automaticRemaining(ServerPlayer p) { if (!thread(p)) return -1; Fences f = readFences(p); return f == null ? -1 : f.automatic; }
    /** An admitted deterministic escape attempt takes precedence over the same hit's optional random veil step. */
    static boolean automaticPending(ServerPlayer p) { Context c=owned(p);return c!=null&&c.automatic&&c.admitted&&c.queued!=null&&!c.closing&&!c.terminal; }
    public static Receipt receipt(ServerPlayer p) { return p == null ? null : RECEIPTS.get(p); }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static void record(Context c, int phase, String reason) {
        if (!retainReceipt(c)) return;
        if (!RECEIPTS.containsKey(c.identity.owner()) && RECEIPTS.size() >= MAX_TERMINALS)
            RECEIPTS.remove(RECEIPTS.keySet().iterator().next());
        RECEIPTS.put(c.identity.owner(), new Receipt(c.command.input(), c.identity.token(), c.paid, c.moved,
                phase, reason, c.source, destination(c), c.automatic));
        diagnostic(c, phase, reason);
    }
    /** Optional bounded QA observer: snapshot reads only, never an alternate gameplay path. */
    private static void diagnostic(Context c, int phase, String reason) {
        if (!DIAGNOSTIC || diagnosticLines >= 128) return;
        diagnosticLines++;
        try {
            var p = c.identity.owner(); var state = HighPowerCombat.exactStateSnapshot(p).orElse(null);
            LOG.info("NEW342_DIAGNOSTIC phase={} reason={} automatic={} paid={} moved={} input={} token={} source={} destination={} current={} hp={} maxHp={} baseS={} maxBaseS={} cp={} velocity={} fall={} grounded={} autoFence={}",
                    phase, reason, c.automatic, c.paid, c.moved, c.command.input(), c.identity.token(), c.source, destination(c), p.position(),
                    state == null ? "unavailable" : CombatAmounts.decimalString(state.health()),
                    state == null ? "unavailable" : CombatAmounts.decimalString(state.maxHealth()),
                    state == null ? "unavailable" : CombatAmounts.decimalString(state.shield()),
                    state == null ? "unavailable" : CombatAmounts.decimalString(state.maxShield()),
                    p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"),
                    p.getDeltaMovement(), p.fallDistance, p.onGround(), automaticRemaining(p));
        } catch (RuntimeException unavailable) { LOG.info("NEW342_DIAGNOSTIC phase={} snapshot_unavailable", phase); }
    }
    private static boolean retainReceipt(Context c) {
        var p = c.identity.owner(); return !c.retired && !p.isRemoved() && !p.hasDisconnected()
                && p.server.getPlayerList().getPlayer(p.getUUID()) == p;
    }

    private static Fences readFences(ServerPlayer p) {
        var root = p.getPersistentData(); if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(DATA);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1 || !tag.contains("request", Tag.TAG_INT)
                || !tag.contains("cooldown", Tag.TAG_INT) || !tag.contains("automatic", Tag.TAG_INT)) return null;
        int request = tag.getInt("request"), cooldown = tag.getInt("cooldown"), automatic = tag.getInt("automatic");
        return request < 0 || request > EmergencyReturnRules.REQUEST_INTERVAL_TICKS || cooldown < 0 || cooldown > 60
                || automatic < 0 || automatic > EmergencyReturnRules.AUTOMATIC_FENCE_TICKS ? null : new Fences(request, cooldown, automatic);
    }
    private static void writeFences(ServerPlayer p, Fences fences) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("request", fences.request);
        tag.putInt("cooldown", fences.cooldown); tag.putInt("automatic", fences.automatic); p.getPersistentData().put(DATA, tag);
    }
    /** Physical countdowns cannot be skipped by game-time jumps, duplicate events, relog or relearn. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void physical(ServerTickEvent.Pre event) {
        var server = event.getServer(); PhysicalClock old = CLOCKS.get(server);
        if (old != null && old.tick == server.getTickCount()) return;
        long physical = old == null ? 1 : old.generation + 1;
        CLOCKS.put(server, new PhysicalClock(server.getTickCount(), physical));
        for (var p : server.getPlayerList().getPlayers()) if (p.getPersistentData().contains(DATA)) {
            Fences fences = readFences(p); if (fences != null && !fences.equals(Fences.ZERO)) writeFences(p, fences.advance());
        }
        for (Context c : new ArrayList<>(CONTEXTS.values())) {
            if (c.identity.owner().server != server || c.lastPhysical == physical) continue;
            c.lastPhysical = physical;
            if (c.processing) { invalidate(c); continue; }
            try {
                if (c.closing || !controlEligible(c)) { finish(c, ENDED, "context_changed"); continue; }
                if (c.anchor != null && --c.remaining <= 0) { finish(c, ENDED, "expired"); continue; }
                if (c.gesture != null) c.gesture.held = Math.min(HOLD_TICKS, c.gesture.held + 1);
                if (c.queued != null) {
                    // Exactly one genuine Pre after sealing. There is no deferred retry path.
                    if (physical != c.queuedAfter + 1) { finish(c, REFUSED, "attempt_expired"); continue; }
                    attemptAutomatic(c); continue;
                }
                if (c.anchor != null && physical - c.lastVisual >= 10) emit(c, MARKED, "");
            } catch (RuntimeException failure) {
                LOG.warn("NEW342 retired invalid physical-tick context", failure); finish(c, ENDED, "validation_failed");
            }
        }
    }
    /** Compatibility entry point deliberately does not advance clocks or execute a queued escape. */
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c != null && !c.processing && (c.closing || !controlEligible(c))) finish(c, ENDED, "context_changed");
    }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return; Context c = owned(p);
        EmergencyReturnConnectionCycle.remove(p);
        if (c != null) { c.retired = true; invalidate(c); if (!c.processing) finish(c, ENDED, "context_changed"); }
        INPUTS.computeIfPresent(p.getUUID(), (id, mark) -> mark.owner == p ? null : mark); RECEIPTS.remove(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer p) {
            remove(old); if (old.getPersistentData().contains(DATA)) p.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        EmergencyReturnConnectionCycle.remove(event.getServer());
        for (var c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) {
            c.retired = true; invalidate(c); CONTEXTS.remove(c.identity.owner().getUUID(), c);
        }
        TRANSACTIONS.keySet().removeIf(p -> p.server == event.getServer());
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer());
        RECEIPTS.keySet().removeIf(p -> p.server == event.getServer()); CLOCKS.remove(event.getServer());
    }

    private static Vec3 destination(Context c) { return c.anchor == null ? c.source : c.anchor.destination(); }
    private static void observeMovement(Context c) {
        if (c.paid && c.candidate != null) c.moved |= c.identity.owner().serverLevel() == c.identity.level()
                && c.identity.owner().position().equals(c.candidate.destination());
    }
    private static void emit(Context c, int phase, String reason) {
        if (c.sequence == Long.MAX_VALUE) return;
        var p = c.identity.owner(); Vec3 destination = destination(c);
        AABB bounds = c.anchor == null ? null : c.anchor.relativeBody().move(destination);
        int remaining = phase == MARKED || phase == REFUSED && !c.terminal ? c.remaining : phase == WARNING ? 1 : 0;
        String current = HighPowerCombat.shieldText(p), maximum = HighPowerCombat.maxShieldText(p);
        if (!budgetText(current) || !budgetText(maximum)) { current = "0"; maximum = "0"; }
        var packet = new Visual(p.getId(), p.getUUID(), c.command.input(), c.command.epoch(), c.identity.token(), ++c.sequence,
                phase, c.source, destination, bounds, phase == SUCCESS, reason, remaining, current, maximum,
                Math.max(0, automaticRemaining(p)));
        c.lastVisual = generation(p.server);
        if (retainReceipt(c) && p.connection != null && p.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(p, packet);
        if (phase == WARNING || phase == SUCCESS || c.warningAdvertised && (phase == ENDED || phase == REFUSED)) {
            if (phase == WARNING) c.warningAdvertised = true;
            int sent = 0;
            for (var observer : c.identity.level().players()) if (observer != p && observer.connection != null && !observer.hasDisconnected()
                    && Math.min(observer.position().distanceToSqr(c.source), observer.position().distanceToSqr(destination)) <= 32 * 32) {
                PacketDistributor.sendToPlayer(observer, packet); if (++sent >= 32) break;
            }
        }
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long generation(MinecraftServer server) { PhysicalClock clock = CLOCKS.get(server); return clock == null ? 0 : clock.generation; }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                && Math.abs(point.x) <= 30_000_032 && Math.abs(point.z) <= 30_000_032 && Math.abs(point.y) <= 4096;
    }
    private static boolean budgetText(String value) {
        if (value == null || value.isEmpty() || value.length() > CombatAmounts.MAX_ENCODED_LENGTH || !value.matches("[0-9]+(?:\\.[0-9]+)?")) return false;
        try { BigDecimal amount = new BigDecimal(value); return amount.signum() >= 0 && amount.compareTo(new BigDecimal("1e20")) <= 0; }
        catch (NumberFormatException invalid) { return false; }
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         Vec3 source, Vec3 destination, AABB bounds, boolean moved, String reason, int remainingTicks,
                         String currentBudget, String maxBudget, int automaticCooldownTicks) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > REFUSED || !finite(source) || !finite(destination)
                    || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || remainingTicks < 0 || remainingTicks > EmergencyReturnRules.MARK_LIFE_TICKS
                    || phase == WARNING && remainingTicks != 1 || (phase == SUCCESS || phase == ENDED) && remainingTicks != 0
                    || moved != (phase == SUCCESS) || phase <= SUCCESS && (bounds == null || !reason.isEmpty())
                    || !budgetText(currentBudget) || !budgetText(maxBudget) || new BigDecimal(currentBudget).compareTo(new BigDecimal(maxBudget)) > 0
                    || automaticCooldownTicks < 0 || automaticCooldownTicks > EmergencyReturnRules.AUTOMATIC_FENCE_TICKS
                    || bounds != null && (!finite(new Vec3(bounds.minX, bounds.minY, bounds.minZ))
                        || !finite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ)) || bounds.getXsize() <= 0
                        || bounds.getYsize() <= 0 || bounds.getZsize() <= 0 || bounds.getXsize() > 4
                        || bounds.getYsize() > 4 || bounds.getZsize() > 4)) throw new IllegalArgumentException("Invalid NEW342 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_emergency_return"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.epoch);
            buffer.writeLong(value.token); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.source); writeVec(buffer, value.destination); buffer.writeBoolean(value.bounds != null);
            if (value.bounds != null) {
                writeVec(buffer, new Vec3(value.bounds.minX, value.bounds.minY, value.bounds.minZ));
                writeVec(buffer, new Vec3(value.bounds.maxX, value.bounds.maxY, value.bounds.maxZ));
            }
            buffer.writeBoolean(value.moved); buffer.writeUtf(value.reason, 64); buffer.writeVarInt(value.remainingTicks);
            buffer.writeUtf(value.currentBudget, CombatAmounts.MAX_ENCODED_LENGTH); buffer.writeUtf(value.maxBudget, CombatAmounts.MAX_ENCODED_LENGTH); buffer.writeVarInt(value.automaticCooldownTicks);
        }, buffer -> {
            int entity = buffer.readInt(); UUID owner = buffer.readUUID();
            long input = buffer.readLong(), epoch = buffer.readLong(), token = buffer.readLong(), sequence = buffer.readLong();
            int phase = buffer.readUnsignedByte(); Vec3 source = readVec(buffer), destination = readVec(buffer);
            AABB bounds = buffer.readBoolean() ? new AABB(readVec(buffer), readVec(buffer)) : null;
            return new Visual(entity, owner, input, epoch, token, sequence, phase, source, destination, bounds,
                    buffer.readBoolean(), buffer.readUtf(64), buffer.readVarInt(), buffer.readUtf(CombatAmounts.MAX_ENCODED_LENGTH),
                    buffer.readUtf(CombatAmounts.MAX_ENCODED_LENGTH), buffer.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

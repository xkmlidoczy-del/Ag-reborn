package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.CornerExchangeRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
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

/** NEW333: two free temporary ground marks, reusable paid swaps and a release-classified
 * six-tick clear. Every swap owns a terminal nonce; the pair's first-mark lifetime never resets. */
@EventBusSubscriber(modid = "academy")
public final class CornerExchange {
    public static final String ID = "corner_exchange";
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    private static final String DATA = "academy:corner_exchange_fences";
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    /** Kept independently of CONTEXTS so a callback cannot clear its own reentry guard. */
    private static final Map<ServerPlayer, Context> TRANSACTIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Integer> POST_CLOCKS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.corner_exchange");
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.cornerExchangeDiagnostic");
    private static int diagnosticEvents;
    private static long nextToken, nextAttempt;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown) {
        static final Fences ZERO = new Fences(0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1)); }
    }
    public record Receipt(long input, long token, long attempt, boolean paid, boolean moved, int phase,
                          String reason, Vec3 source, Vec3 destination, boolean reversed, int remainingTicks,
                          float yaw, float pitch, boolean terminal) {}
    /** Facing is deliberately absent: turning during a warning is permitted and read at commit. */
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                          float fall, boolean grounded, boolean crouching, boolean noPhysics,
                          boolean passenger, boolean sleeping, boolean gliding, boolean flying,
                          boolean water, boolean lava) {
        static Source capture(ServerPlayer p) {
            return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(), p.getPose(),
                    p.fallDistance, p.onGround(), p.isShiftKeyDown(), p.noPhysics, p.isPassenger(), p.isSleeping(),
                    p.isFallFlying(), p.getAbilities().flying, p.isInWater(), p.isInLava());
        }
        boolean anchorMatches(ServerPlayer p) {
            return position.equals(p.position()) && body.equals(p.getBoundingBox())
                    && pose == p.getPose() && crouching == p.isShiftKeyDown();
        }
        boolean matches(ServerPlayer p) {
            return anchorMatches(p) && eye.equals(p.getEyePosition()) && motion.equals(p.getDeltaMovement())
                    && Float.compare(fall, p.fallDistance) == 0 && grounded == p.onGround()
                    && noPhysics == p.noPhysics && passenger == p.isPassenger() && sleeping == p.isSleeping()
                    && gliding == p.isFallFlying() && flying == p.getAbilities().flying
                    && water == p.isInWater() && lava == p.isInLava();
        }
    }
    private static final class Gesture {
        final HighPowerInput.Input input;
        final boolean reversed;
        long ageTick;
        int held;
        Gesture(ServerPlayer p, HighPowerInput.Input input) {
            this.input = input; reversed = p.isShiftKeyDown(); ageTick = now(p);
        }
    }
    private static final class Attempt {
        final long nonce;
        final HighPowerInput.Input input;
        final Source source;
        final boolean reversed;
        SafeLandingProbeWorld.CornerSwapCandidate candidate;
        int warning = CornerExchangeRules.WARNING_TICKS;
        long warningTick;
        boolean paid, moved, terminal, advertised;
        float yaw, pitch;
        Attempt(Context c, Source source) {
            nonce = c.lastAttempt; input = c.command; this.source = source; reversed = c.releasedReversed;
            warningTick = now(c.identity.owner());
            yaw = c.identity.owner().getYRot(); pitch = c.identity.owner().getXRot();
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;
        final long revision;
        HighPowerInput.Input command;
        SafeLandingProbeWorld.CornerPoint a, b;
        Gesture gesture;
        Attempt attempt;
        Vec3 source, destination;
        int remaining;
        long ageTick, lastVisual, sequence, lastAttempt;
        boolean processing, reentered, closing, retired, terminal, releasedReversed;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = destination = p.position(); ageTick = lastVisual = now(p);
        }
    }
    private CornerExchange() {}

    public static boolean start(ServerPlayer p, String wire) { return press(p, wire); }
    /** Press has no world assessment, payment, mark or swap. This is essential to hold-to-clear. */
    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch()
                && input.input() <= previous.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context guard = TRANSACTIONS.get(p);
        if (guard != null) { guard.reentered = true; return false; }
        if (AbilityConsumption.busy(p) || !mapped(p, input.slot()) || !finite(p.position())
                || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return false;
        Context c = owned(p);
        if (c != null && (!identity(c) || c.command.epoch() != input.epoch() || c.command.slot() != input.slot())) {
            finish(c, ENDED, "context_changed"); return false;
        }
        if (c != null && (c.gesture != null || c.attempt != null || c.processing || c.closing)) return false;
        if (c == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= 64) return false;
            c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        }
        c.command = input; c.gesture = new Gesture(p, input);
        return true;
    }
    /** The client sends this after its movement-post seam. The server owns the held count. */
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context guard = TRANSACTIONS.get(p);
        if (guard != null) { guard.reentered = true; return false; }
        Context c = owned(p);
        if (c == null || c.gesture == null || !c.gesture.input.equals(input) || c.closing || c.processing) return false;
        int held = c.gesture.held; c.releasedReversed = c.gesture.reversed; c.gesture = null;
        if (CornerExchangeRules.holdClears(held)) { finish(c, ENDED, "cleared"); return true; }
        if (!CornerExchangeRules.tapRelease(held) || nextAttempt == Long.MAX_VALUE) {
            finish(c, ENDED, "context_changed"); return false;
        }
        c.lastAttempt = ++nextAttempt;
        return action(c);
    }
    private static boolean action(Context c) {
        ServerPlayer p = c.identity.owner(); c.processing = true; c.reentered = false; TRANSACTIONS.put(p, c);
        try {
            c.source = c.destination = p.position();
            String reason = controlRefusal(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            Fences fences = readFences(p);
            if (fences == null) return refuse(c, "storage_invalid");
            if (fences.request > 0) return refuse(c, "request_fence");
            writeFences(p, new Fences(CornerExchangeRules.REQUEST_INTERVAL_TICKS, fences.cooldown));
            Source before = Source.capture(p);
            if (c.a == null || c.b == null) {
                if (c.releasedReversed) return refuse(c, "standing_required");
                if (c.a != null) {
                    var saved = SafeLandingProbeWorld.revalidateCornerPoint(p, c.a);
                    if (!saved.valid()) return refuse(c, saved.reason());
                    if (!unchanged(c, before)) return refuse(c, "event_changed_state");
                }
                var assessment = SafeLandingProbeWorld.cornerPoint(p);
                if (!assessment.valid()) return refuse(c, assessment.reason());
                if (!unchanged(c, before)) return refuse(c, "event_changed_state");
                var point = assessment.point();
                if (c.a != null && !CornerExchangeRules.withinRange(c.a.destination().distanceTo(point.destination())))
                    return refuse(c, "points_out_of_range");
                if (c.a != null && c.a.destination().distanceToSqr(point.destination()) <= 1e-8)
                    return refuse(c, "points_overlap");
                if (c.a == null) { c.a = point; c.remaining = CornerExchangeRules.MARK_LIFE_TICKS; c.ageTick = now(p); }
                else c.b = point;
                c.destination = point.destination(); record(c, MARKED, ""); safeEmit(c, MARKED, "");
                return true;
            }
            if (c.remaining <= 0) { finish(c, ENDED, "expired"); return false; }
            reason = resourceRefusal(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            c.attempt = new Attempt(c, before);
            var assessment = SafeLandingProbeWorld.cornerSwap(p, c.a, c.b);
            if (!assessment.valid()) return refuse(c, assessment.reason());
            if (!unchanged(c, before)) return refuse(c, "event_changed_state");
            c.attempt.candidate = assessment.candidate(); c.destination = c.attempt.candidate.destination();
            if (!c.attempt.candidate.sourcePosition().equals(before.position)) return refuse(c, "event_changed_state");
            record(c, WARNING, ""); safeEmit(c, WARNING, ""); return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW333 release validation failed before payment", failure); return refuse(c, "validation_failed");
        } catch (Error failure) {
            refuse(c, "validation_failed"); throw failure;
        } finally { endTransaction(c); }
    }
    private static boolean commit(Context c) {
        ServerPlayer p = c.identity.owner(); Attempt a = c.attempt;
        if (a == null || a.terminal || a.candidate == null) return false;
        c.processing = true; c.reentered = false; TRANSACTIONS.put(p, c);
        try {
            String reason = controlRefusal(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            if (c.remaining <= 0) { finish(c, ENDED, "expired"); return false; }
            if (!a.source.anchorMatches(p)) return refuse(c, "source_changed");
            Source before = Source.capture(p);
            var check = SafeLandingProbeWorld.revalidateCornerSwap(p, a.candidate);
            if (!check.valid()) return refuse(c, check.reason());
            if (!unchanged(c, before)) return refuse(c, "event_changed_state");
            Vec3 destination = a.candidate.destination();
            EntityTeleportEvent event = new EntityTeleportEvent(p, destination.x, destination.y, destination.z);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return refuse(c, "event_cancelled");
            if (Double.compare(event.getTargetX(), destination.x) != 0 || Double.compare(event.getTargetY(), destination.y) != 0
                    || Double.compare(event.getTargetZ(), destination.z) != 0) return refuse(c, "event_redirected");
            if (!unchanged(c, before)) return refuse(c, "event_changed_state");
            check = SafeLandingProbeWorld.revalidateCornerSwap(p, a.candidate);
            if (!check.valid()) return refuse(c, check.reason());
            // All cached source/control flags are checked AFTER the final callback-capable world read.
            if (!unchanged(c, before)) return refuse(c, "event_changed_state");
            reason = resourceRefusal(c);
            if (!reason.isEmpty()) return refuse(c, reason);
            Fences fences = readFences(p);
            var growth = HighPowerGrowth.values(p).position();
            int cooldown = CornerExchangeRules.cooldownTicks(growth);
            a.yaw = CornerExchangeRules.commitYaw(p.getYRot(), a.reversed); a.pitch = p.getXRot();
            // Closed commit: one terminal attempt, one whole CP charge, physical fences, one absolute move.
            // No health, XP, ammo, invulnerability, fall-distance or velocity repair is performed.
            if (!HighPowerResources.trySpend(p, ID, CornerExchangeRules.cpCost(growth), cooldown))
                return refuse(c, "insufficient_cp");
            a.paid = true;
            writeFences(p, new Fences(fences.request, cooldown)); SafeLandingProbe.commitSharedMovement(p);
            // Absolute XYZ also makes the native client reset XYZ velocity; server velocity is preserved.
            p.connection.teleport(destination.x, destination.y, destination.z, a.yaw, a.pitch, Set.of());
            a.moved = p.serverLevel() == c.identity.level() && p.position().equals(destination);
            if (c.closing || c.terminal || c.reentered || !identity(c) || !a.moved
                    || !p.getDeltaMovement().equals(before.motion) || Float.compare(p.fallDistance, before.fall) != 0
                    || p.onGround() != before.grounded || Float.compare(p.getYRot(), a.yaw) != 0
                    || Float.compare(p.getXRot(), a.pitch) != 0 || p.getPose() != before.pose
                    || p.isShiftKeyDown() != before.crouching) return refuse(c, "commit_unconfirmed");
            completeAttempt(c, SUCCESS, "");
            try { if (retainReceipt(c) && c.identity.owns(p)) { AbilityStorage.save(p); AcademyNetwork.sync(p); } }
            catch (RuntimeException failure) { LOG.warn("NEW333 success sync failed without replay or refund", failure); }
            return true;
        } catch (RuntimeException failure) {
            observeDestination(c, a); LOG.warn("NEW333 transport ended without replay/refund; paid={}", a.paid, failure);
            return refuse(c, a.paid ? "transport_unconfirmed" : "validation_failed");
        } catch (Error failure) {
            observeDestination(c, a); refuse(c, a.paid ? "transport_unconfirmed" : "validation_failed"); throw failure;
        } finally { endTransaction(c); }
    }
    private static void observeDestination(Context c, Attempt a) {
        if (a.paid && a.candidate != null) a.moved |= c.identity.owner().serverLevel() == c.identity.level()
                && c.identity.owner().position().equals(a.candidate.destination());
    }
    private static void endTransaction(Context c) {
        c.processing = false; TRANSACTIONS.remove(c.identity.owner(), c);
        if (c.closing && !c.terminal) finish(c, ENDED, "context_changed");
    }
    private static boolean unchanged(Context c, Source before) {
        return c.processing && TRANSACTIONS.get(c.identity.owner()) == c && !c.reentered
                && before.matches(c.identity.owner()) && controlRefusal(c).isEmpty();
    }
    private static String resourceRefusal(Context c) {
        ServerPlayer p = c.identity.owner(); Fences f = readFences(p); int shared = SafeLandingProbe.sharedMovementRemaining(p);
        if (f == null || shared < 0) return "storage_invalid";
        if (HighPowerResources.hasReservation(p)) return "resource_busy";
        if (f.cooldown > 0 || shared > 0 || !c.identity.state().canUse(ID)
                || p.serverLevel().getGameTime() < p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID))
            return "cooldown";
        double cost = CornerExchangeRules.cpCost(HighPowerGrowth.values(p).position());
        return !Double.isFinite(cost) || cost <= 0 || HighPowerResources.availableCp(p) < cost ? "insufficient_cp" : "";
    }
    /** No collision or block reads; safe to repeat immediately after hostile shape/event callbacks. */
    private static String controlRefusal(Context c) {
        ServerPlayer p = c.identity.owner();
        if (!controlEligible(c) || c.closing) return "context_changed";
        if (!p.onGround() || !Float.isFinite(p.fallDistance) || p.fallDistance != 0
                || !CornerExchangeRules.groundedVerticalMotion(p.getDeltaMovement().y)) return "source_airborne";
        if (p.noPhysics || p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.isInWater() || p.isInLava() || p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING
                || !finite(p.position()) || !finite(p.getEyePosition()) || !finite(p.getDeltaMovement())
                || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return "source_motion_or_pose_unsupported";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                || p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) return "movement_conflict";
        if (CrossSubstitution.isTransporting(p) || ReturnMark.active(p) || SafeLandingProbe.active(p)
                || PhaseSidestep.active(p) || (BeamStep.active(p)||WindPressureLift.active(p)) || MagneticGroundStep.active(p) || PolarityTether.active(p)
                || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.LocationTeleport.active(p) || cn.academy.port.skill.Flashing.active(p)) return "movement_conflict";
        return "";
    }
    private static boolean controlEligible(Context c) {
        var p = c.identity.owner(); var s = c.identity.state();
        return identity(c) && p.isAlive() && !p.isRemoved() && !p.hasDisconnected() && !p.isSpectator()
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && p.containerMenu == p.inventoryMenu
                && s.activated && s.overloadFine && !s.interfering && !s.consumptionInProgress() && !AbilityConsumption.busy(p)
                && s.learned(ID) && s.level >= 3 && s.level <= 5 && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID)
                && HighPowerResources.ready(p) && HighPowerResources.effectiveLevel(p) >= 3
                && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean identity(Context c) {
        var p = c.identity.owner(); return CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p)
                && mapped(p, c.command.slot()) && c.identity.state().presets.current() == c.preset
                && c.identity.state().presets.revision() == c.revision;
    }
    private static boolean mapped(ServerPlayer p, int slot) {
        return thread(p) && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
                && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    public static boolean canBegin(ServerPlayer p) {
        if (!thread(p) || TRANSACTIONS.containsKey(p)) return false;
        Context c = owned(p); return c == null || !c.closing && c.gesture == null && c.attempt == null && !c.processing;
    }
    public static boolean active(ServerPlayer p) { return isTransporting(p); }
    public static boolean isTransporting(ServerPlayer p) {
        if (!thread(p)) return false; Context c = owned(p);
        return TRANSACTIONS.containsKey(p) || c != null && c.attempt != null && !c.attempt.terminal;
    }
    public static boolean marked(ServerPlayer p) { return preparedPoints(p) > 0; }
    public static int preparedPoints(ServerPlayer p) {
        Context c = owned(p); return c == null || c.closing || c.remaining <= 0 || c.a == null ? 0 : c.b == null ? 1 : 2;
    }
    public static int remainingTicks(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.remaining; }
    public static int warningTicks(ServerPlayer p) { Context c = owned(p); return c == null || c.attempt == null ? 0 : c.attempt.warning; }
    public static int heldTicks(ServerPlayer p) { Context c = owned(p); return c == null || c.gesture == null ? 0 : c.gesture.held; }
    public static Receipt receipt(ServerPlayer p) { return p == null ? null : RECEIPTS.get(p); }
    public static boolean abort(ServerPlayer p, String wire) { return cancel(p, wire); }
    public static boolean cancel(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var previous = INPUTS.get(p.getUUID());
        if (previous == null || previous.owner != p || previous.epoch != input.epoch() || input.input() > previous.input)
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || input.epoch() != c.command.epoch() || input.slot() != c.command.slot()
                || input.input() < c.command.input()) return false;
        c.command = input; finish(c, ENDED, "context_changed"); return true;
    }
    public static void clear(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c, ENDED, "context_changed"); }
    public static void cancelActive(ServerPlayer p) { clear(p); }
    private static boolean refuse(Context c, String reason) {
        if (c.terminal) {
            if (c.attempt != null && c.attempt.paid) { record(c, REFUSED, reason); safeEmit(c, REFUSED, reason); }
            return false;
        }
        if (c.a == null || c.closing || !identity(c) || c.remaining <= 0 || c.attempt != null && c.attempt.paid) {
            finish(c, REFUSED, reason); return false;
        }
        completeAttempt(c, REFUSED, reason); return false;
    }
    private static void completeAttempt(Context c, int phase, String reason) {
        if (c.attempt != null) c.attempt.terminal = true;
        record(c, phase, reason); safeEmit(c, phase, reason); c.attempt = null;
    }
    private static void finish(Context c, int phase, String reason) {
        if (c.terminal) return;
        c.terminal = true; c.closing = true; c.remaining = 0; c.gesture = null;
        if (c.attempt != null) c.attempt.terminal = true;
        CONTEXTS.remove(c.identity.owner().getUUID(), c);
        record(c, phase, reason); safeEmit(c, phase, reason);
    }
    private static void record(Context c, int phase, String reason) {
        Attempt a = c.attempt; var p = c.identity.owner();
        if (retainReceipt(c)) RECEIPTS.put(p, new Receipt(c.command.input(), c.identity.token(), c.lastAttempt,
                a != null && a.paid, a != null && a.moved, phase, reason, c.source, c.destination,
                a != null && a.reversed, c.remaining, a == null ? p.getYRot() : a.yaw,
                a == null ? p.getXRot() : a.pitch, phase != WARNING));
        if (DIAGNOSTIC && diagnosticEvents++ < 256) LOG.info("NEW333 phase={} input={} token={} attempt={} paid={} moved={} remaining={} source={} destination={} reason={}",
                phase, c.command.input(), c.identity.token(), c.lastAttempt, a != null && a.paid, a != null && a.moved,
                c.remaining, c.source, c.destination, reason);
    }
    private static boolean retainReceipt(Context c) {
        var p = c.identity.owner(); return !c.retired && !p.isRemoved() && !p.hasDisconnected()
                && p.server.getPlayerList().getPlayer(p.getUUID()) == p;
    }
    /** Genuine observed physical ticks, never world game-time differences or duplicate callbacks. */
    private static void age(Context c) {
        var p = c.identity.owner(); if (c.terminal || c.processing) return;
        if (c.closing || !controlEligible(c)) { finish(c, ENDED, "context_changed"); return; }
        if (c.ageTick != now(p)) {
            c.ageTick = now(p);
            if (c.a != null && --c.remaining <= 0) { finish(c, ENDED, "expired"); return; }
        }
        if (c.gesture != null && c.gesture.ageTick != now(p)) {
            c.gesture.ageTick = now(p);
            if (CornerExchangeRules.holdClears(++c.gesture.held)) finish(c, ENDED, "cleared");
        }
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.processing) return;
        age(c); if (c.terminal) return;
        Attempt a = c.attempt;
        if (a != null) {
            if (a.terminal || a.warningTick == now(p)) return;
            a.warningTick = now(p);
            if (!a.source.anchorMatches(p)) { refuse(c, "source_changed"); return; }
            if (--a.warning <= 0) { commit(c); return; }
            c.processing = true; c.reentered = false; TRANSACTIONS.put(p, c);
            try {
                Source before = Source.capture(p); String reason = controlRefusal(c);
                if (!reason.isEmpty()) { refuse(c, reason); return; }
                var check = SafeLandingProbeWorld.revalidateCornerSwap(p, a.candidate);
                if (!check.valid()) { refuse(c, check.reason()); return; }
                if (!unchanged(c, before)) { refuse(c, "event_changed_state"); return; }
                safeEmit(c, WARNING, "");
            } catch (RuntimeException failure) { LOG.warn("NEW333 warning validation failed", failure); refuse(c, "validation_failed"); }
            catch (Error failure) { refuse(c, "validation_failed"); throw failure; }
            finally { endTransaction(c); }
        } else if (c.a != null && c.gesture == null && (now(p) < c.lastVisual || now(p) - c.lastVisual >= 10))
            safeEmit(c, MARKED, "");
    }
    private static Fences readFences(ServerPlayer p) {
        var root = p.getPersistentData(); if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null; var tag = root.getCompound(DATA);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1 || !tag.contains("request", Tag.TAG_INT)
                || !tag.contains("cooldown", Tag.TAG_INT)) return null;
        int request = tag.getInt("request"), cooldown = tag.getInt("cooldown");
        return request < 0 || request > CornerExchangeRules.REQUEST_INTERVAL_TICKS || cooldown < 0 || cooldown > 40
                ? null : new Fences(request, cooldown);
    }
    private static void writeFences(ServerPlayer p, Fences f) {
        var tag = p.getPersistentData().contains(DATA, Tag.TAG_COMPOUND) ? p.getPersistentData().getCompound(DATA).copy() : new CompoundTag();
        tag.putInt("version", 1); tag.putInt("request", f.request); tag.putInt("cooldown", f.cooldown); p.getPersistentData().put(DATA, tag);
    }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return; Context c = owned(p);
        if (c != null) { c.retired = true; finish(c, ENDED, "context_changed"); }
        INPUTS.computeIfPresent(p.getUUID(), (id, w) -> w.owner == p ? null : w); RECEIPTS.remove(p);
    }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre event) {
        Integer previous = CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (previous != null && previous == event.getServer().getTickCount()) return;
        for (var p : event.getServer().getPlayerList().getPlayers()) if (p.getPersistentData().contains(DATA)) {
            Fences f = readFences(p); if (f != null && !f.equals(Fences.ZERO)) writeFences(p, f.advance());
        }
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) age(c);
    }
    @SubscribeEvent public static void controllers(ServerTickEvent.Post event) {
        Integer previous = POST_CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (previous != null && previous == event.getServer().getTickCount()) return;
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) tick(c.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old && e.getEntity() instanceof ServerPlayer p) {
            remove(old); if (old.getPersistentData().contains(DATA)) p.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        CONTEXTS.entrySet().removeIf(x -> x.getValue().identity.owner().server == e.getServer());
        TRANSACTIONS.entrySet().removeIf(x -> x.getKey().server == e.getServer());
        INPUTS.entrySet().removeIf(x -> x.getValue().owner.server == e.getServer());
        RECEIPTS.keySet().removeIf(p -> p.server == e.getServer()); CLOCKS.remove(e.getServer()); POST_CLOCKS.remove(e.getServer());
    }
    private static void safeEmit(Context c, int phase, String reason) {
        try { emit(c, phase, reason); }
        catch (RuntimeException failure) { LOG.warn("NEW333 state retained despite visual delivery failure", failure); }
    }
    private static void emit(Context c, int phase, String reason) {
        if (c.sequence == Long.MAX_VALUE) return;
        var p = c.identity.owner(); Attempt a = c.attempt;
        Vec3 markA = c.terminal || c.a == null ? null : c.a.destination(), markB = c.terminal || c.b == null ? null : c.b.destination();
        var point = c.b == null ? c.a : c.b;
        AABB bounds = point == null ? null : point.relativeBody().move(c.destination);
        var packet = new Visual(p.getId(), p.getUUID(), c.command.input(), c.command.epoch(), c.identity.token(), ++c.sequence,
                phase, c.source, c.destination, bounds, phase == SUCCESS, reason, c.remaining, markA, markB,
                a != null && a.reversed, phase == WARNING && a != null ? a.warning : 0);
        c.lastVisual = now(p); if (phase == WARNING && a != null) a.advertised = true;
        if (retainReceipt(c) && p.connection != null && p.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(p, packet);
        // Ground marks and warning origin are honest server coordinates; bounded observer fanout.
        int sent = 0;
        for (var observer : c.identity.level().players()) {
            if (observer == p || observer.connection == null || observer.hasDisconnected()) continue;
            double distance = Math.min(observer.position().distanceToSqr(c.source), observer.position().distanceToSqr(c.destination));
            if (c.a != null) distance = Math.min(distance, observer.position().distanceToSqr(c.a.destination()));
            if (c.b != null) distance = Math.min(distance, observer.position().distanceToSqr(c.b.destination()));
            if (distance <= 32 * 32) { PacketDistributor.sendToPlayer(observer, packet); if (++sent >= 32) break; }
        }
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }
    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30_000_032 && Math.abs(v.z) <= 30_000_032 && Math.abs(v.y) <= 4096;
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         Vec3 source, Vec3 destination, AABB bounds, boolean moved, String reason, int remainingTicks,
                         Vec3 markA, Vec3 markB, boolean reversed, int warningTicks) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > REFUSED || !finite(source) || !finite(destination)
                    || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*") || moved != (phase == SUCCESS)
                    || remainingTicks < 0 || remainingTicks > CornerExchangeRules.MARK_LIFE_TICKS
                    || warningTicks < 0 || warningTicks > CornerExchangeRules.WARNING_TICKS
                    || (phase == WARNING) != (warningTicks > 0) || markB != null && markA == null
                    || markA != null && !finite(markA) || markB != null && !finite(markB)
                    || markA != null && markB != null && markA.distanceToSqr(markB) > 24.000001 * 24.000001
                    || (markA != null) != (remainingTicks > 0) || phase == ENDED && remainingTicks != 0
                    || phase == WARNING && (markB == null || bounds == null)
                    || (phase == WARNING || phase == SUCCESS) && source.distanceToSqr(destination) > 24.000001 * 24.000001
                    || phase <= SUCCESS && !reason.isEmpty()
                    || bounds != null && (!finite(new Vec3(bounds.minX, bounds.minY, bounds.minZ))
                        || !finite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ)) || bounds.getXsize() <= 0
                        || bounds.getYsize() <= 0 || bounds.getZsize() <= 0 || bounds.getXsize() > 4
                        || bounds.getYsize() > 4 || bounds.getZsize() > 4)) throw new IllegalArgumentException("Invalid NEW333 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_corner_exchange"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch); b.writeLong(v.token);
            b.writeLong(v.sequence); b.writeByte(v.phase); writeVec(b, v.source); writeVec(b, v.destination); b.writeBoolean(v.bounds != null);
            if (v.bounds != null) { writeVec(b, new Vec3(v.bounds.minX, v.bounds.minY, v.bounds.minZ)); writeVec(b, new Vec3(v.bounds.maxX, v.bounds.maxY, v.bounds.maxZ)); }
            b.writeBoolean(v.moved); b.writeUtf(v.reason, 64); b.writeVarInt(v.remainingTicks);
            writeOptionalVec(b, v.markA); writeOptionalVec(b, v.markB); b.writeBoolean(v.reversed); b.writeVarInt(v.warningTicks);
        }, b -> {
            int entity = b.readInt(); UUID owner = b.readUUID(); long input = b.readLong(), epoch = b.readLong(), token = b.readLong(), sequence = b.readLong();
            int phase = b.readUnsignedByte(); Vec3 source = readVec(b), destination = readVec(b);
            AABB bounds = b.readBoolean() ? new AABB(readVec(b), readVec(b)) : null;
            return new Visual(entity, owner, input, epoch, token, sequence, phase, source, destination, bounds,
                    b.readBoolean(), b.readUtf(64), b.readVarInt(), readOptionalVec(b), readOptionalVec(b), b.readBoolean(), b.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static void writeOptionalVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeBoolean(v != null); if (v != null) writeVec(b, v); }
    private static Vec3 readOptionalVec(RegistryFriendlyByteBuf b) { return b.readBoolean() ? readVec(b) : null; }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

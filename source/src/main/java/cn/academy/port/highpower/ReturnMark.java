package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ReturnMarkRules;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
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

/** NEW322 one temporary same-world anchor and a manual four-tick return warning.
 * Anchors are never saved, cloned or triggered by damage. Native transport does not
 * repair health, fall distance, velocity, immunity or attached status effects. */
@EventBusSubscriber(modid = "academy")
public final class ReturnMark {
    public static final String ID = "return_mark";
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    private static final String DATA = "academy:return_mark_fences";
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.return_mark");
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown) {
        static final Fences ZERO = new Fences(0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1)); }
    }
    public record Receipt(long input, long token, boolean paid, boolean moved, int phase,
                          String reason, Vec3 source, Vec3 destination) {}
    private record Source(Vec3 position, Vec3 motion, AABB body, Pose pose, float yaw,
                          float pitch, float fall, boolean grounded, boolean crouching) {
        static Source capture(ServerPlayer player) {
            return new Source(player.position(), player.getDeltaMovement(), player.getBoundingBox(), player.getPose(),
                    player.getYRot(), player.getXRot(), player.fallDistance, player.onGround(), player.isShiftKeyDown());
        }
        boolean matches(ServerPlayer player) {
            return position.equals(player.position()) && motion.equals(player.getDeltaMovement())
                    && body.equals(player.getBoundingBox()) && pose == player.getPose()
                    && Float.compare(yaw, player.getYRot()) == 0 && Float.compare(pitch, player.getXRot()) == 0
                    && Float.compare(fall, player.fallDistance) == 0 && grounded == player.onGround()
                    && crouching == player.isShiftKeyDown();
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;
        final long revision;
        HighPowerInput.Input command;
        SafeLandingProbeWorld.ReturnAnchor anchor;
        SafeLandingProbeWorld.ReturnCandidate candidate;
        Vec3 source;
        int remaining = ReturnMarkRules.MARK_LIFE_TICKS, warning;
        long lastTick, warningTick, lastVisual, sequence;
        boolean processing, closing, retired, terminal, paid, moved, warningAdvertised;
        Context(ServerPlayer player, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(player, input.input(), ++nextToken, AbilityStorage.get(player));
            command = input;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            source = player.position(); lastTick = now(player); lastVisual = lastTick;
        }
    }

    private ReturnMark() {}

    public static boolean press(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input) || AbilityConsumption.busy(player)
                || !mapped(player, input.slot()) || !finite(player.position()) || !finite(player.getEyePosition())
                || !Float.isFinite(player.getYRot()) || !Float.isFinite(player.getXRot())) return false;
        Context current = CONTEXTS.get(player.getUUID());
        if (current != null && current.processing) return false;
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous != null && previous.owner == player && previous.epoch == input.epoch()
                && input.input() <= previous.input) return false;
        INPUTS.put(player.getUUID(), new Watermark(player, input.epoch(), input.input()));
        if (current != null) {
            if (!identity(current) || current.command.epoch() != input.epoch() || current.command.slot() != input.slot()) {
                finish(current, ENDED, "context_changed"); return false;
            }
            // A new crouch edge can always clear its own mark, including an unpaid warning.
            if (player.isShiftKeyDown()) {
                current.command = input; finish(current, ENDED, "cleared"); return true;
            }
            if (current.candidate != null || current.closing) return false;
            current.command = input;
            return beginReturn(current);
        }
        // A bounded owner-only terminal acknowledges a clear racing a prior refusal.
        // It creates no anchor, world assessment, persisted fence or movement request.
        if (player.isShiftKeyDown()) {
            if (nextToken == Long.MAX_VALUE) return false;
            finish(new Context(player, input), ENDED, "cleared"); return true;
        }
        Fences fences = readFences(player);
        if (fences != null && fences.request > 0 || nextToken == Long.MAX_VALUE || CONTEXTS.size() >= 64) return false;
        Context context = new Context(player, input);
        CONTEXTS.put(player.getUUID(), context);
        context.processing = true;
        try {
            if (fences == null) return refuse(context, "storage_invalid");
            writeFences(player, new Fences(ReturnMarkRules.REQUEST_INTERVAL_TICKS, fences.cooldown));
            if (fences.cooldown > 0 || !context.identity.state().canUse(ID)) return refuse(context, "cooldown");
            Source before = Source.capture(player);
            String reason = eligibility(context);
            if (!reason.isEmpty()) return refuse(context, reason);
            var assessment = SafeLandingProbeWorld.mark(player);
            if (!assessment.valid()) return refuse(context, assessment.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            context.anchor = assessment.anchor();
            record(context, MARKED, ""); emit(context, MARKED, "");
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW322 mark refused after native validation failure", failure);
            return refuse(context, "validation_failed");
        } finally {
            context.processing = false;
            if (context.closing && !context.terminal) finish(context, ENDED, "context_changed");
        }
    }

    private static boolean beginReturn(Context context) {
        var player = context.identity.owner(); context.processing = true;
        try {
            Source before = Source.capture(player);
            String reason = eligibility(context);
            if (!reason.isEmpty()) return refuse(context, reason);
            if (context.remaining <= 0) return refuse(context, "expired");
            Fences fences = readFences(player);
            int shared = SafeLandingProbe.sharedMovementRemaining(player);
            if (fences == null || shared < 0) return refuse(context, "storage_invalid");
            if (fences.cooldown > 0 || shared > 0 || !context.identity.state().canUse(ID)) return refuse(context, "cooldown");
            var assessment = SafeLandingProbeWorld.returning(player, context.anchor);
            if (!assessment.valid()) return refuse(context, assessment.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            context.candidate = assessment.candidate(); context.source = before.position;
            context.warning = ReturnMarkRules.WARNING_TICKS;
            // The input's own server tick never consumes any of the four future warning ticks.
            context.warningTick = now(player);
            record(context, WARNING, ""); emit(context, WARNING, "");
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW322 warning refused after native validation failure", failure);
            return refuse(context, "validation_failed");
        } finally {
            context.processing = false;
            if (context.closing && !context.terminal) finish(context, ENDED, "context_changed");
        }
    }

    private static boolean commit(Context context) {
        var player = context.identity.owner(); context.processing = true;
        try {
            Source before = Source.capture(player);
            String reason = eligibility(context);
            if (!reason.isEmpty()) return refuse(context, reason);
            if (context.remaining <= 0) return refuse(context, "expired");
            var check = SafeLandingProbeWorld.revalidateReturn(player, context.candidate);
            if (!check.valid()) return refuse(context, check.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            Vec3 destination = context.candidate.destination();
            EntityTeleportEvent event = new EntityTeleportEvent(player, destination.x, destination.y, destination.z);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return refuse(context, "event_cancelled");
            if (Double.compare(event.getTargetX(), destination.x) != 0
                    || Double.compare(event.getTargetY(), destination.y) != 0
                    || Double.compare(event.getTargetZ(), destination.z) != 0) return refuse(context, "event_redirected");
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            reason = eligibility(context);
            if (!reason.isEmpty()) return refuse(context, reason);
            check = SafeLandingProbeWorld.revalidateReturn(player, context.candidate);
            if (!check.valid()) return refuse(context, check.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            Fences fences = readFences(player);
            int shared = SafeLandingProbe.sharedMovementRemaining(player);
            if (fences == null || shared < 0) return refuse(context, "storage_invalid");
            if (fences.cooldown > 0 || shared > 0) return refuse(context, "cooldown");
            var growth = HighPowerGrowth.values(player);
            int cooldown = ReturnMarkRules.cooldownTicks(growth.position());
            // The shared ledger and movement fence have no external callback before transport.
            if (!HighPowerResources.trySpend(player, ID, ReturnMarkRules.cpCost(growth.position()), cooldown))
                return refuse(context, "insufficient_cp");
            context.paid = true;
            SafeLandingProbe.commitSharedMovement(player);
            writeFences(player, new Fences(fences.request, cooldown));
            player.connection.teleport(destination.x, destination.y, destination.z,
                    before.yaw, before.pitch, RelativeMovement.ROTATION);
            context.moved = player.serverLevel() == context.identity.level() && player.position().equals(destination);
            if (!context.moved || !player.getDeltaMovement().equals(before.motion)
                    || Float.compare(player.fallDistance, before.fall) != 0 || player.onGround() != before.grounded)
                return refuse(context, "commit_unconfirmed");
            finish(context, SUCCESS, "");
            try {
                if (retainReceipt(context) && context.identity.owns(player)) { AbilityStorage.save(player); AcademyNetwork.sync(player); }
            } catch (RuntimeException failure) { LOG.warn("NEW322 moved successfully; final sync failed without rollback", failure); }
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW322 confirmation ended without retry/refund after native failure; paid={}", context.paid, failure);
            if (context.candidate != null) context.moved |= player.serverLevel() == context.identity.level()
                    && player.position().equals(context.candidate.destination());
            return refuse(context, context.paid ? "transport_unconfirmed" : "validation_failed");
        } finally {
            context.processing = false;
            if (!context.terminal) finish(context, ENDED, "context_changed");
        }
    }

    /** Release is inert: neither a held key nor its release can return the player. */
    public static boolean release(ServerPlayer player, String wire) {
        return HighPowerBracerSession.authenticated(player, HighPowerInput.parse(wire));
    }
    public static boolean abort(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        // A cancelled queued edge cannot arrive later and create a fresh mark after cleanup.
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous == null || previous.owner != player || previous.epoch != input.epoch() || input.input() > previous.input)
            INPUTS.put(player.getUUID(), new Watermark(player, input.epoch(), input.input()));
        Context context = owned(player);
        if (context == null || input.epoch() != context.command.epoch() || input.slot() != context.command.slot()
                || input.input() < context.command.input()) return false;
        context.command = input; context.closing = true;
        if (!context.processing) finish(context, ENDED, "context_changed");
        return true;
    }
    /** Administrative cancellation preserves the input watermark and all paid fences. */
    public static void clear(ServerPlayer player) {
        Context context = owned(player);
        if (context == null) return;
        context.closing = true;
        if (!context.processing) finish(context, ENDED, "context_changed");
    }
    public static void cancelActive(ServerPlayer player) { clear(player); }
    public static boolean active(ServerPlayer player) {
        Context context = owned(player);
        return context != null && (context.processing || context.candidate != null);
    }
    public static boolean marked(ServerPlayer player) {
        Context context = owned(player); return context != null && context.anchor != null && !context.closing;
    }
    public static int remainingTicks(ServerPlayer player) {
        Context context = owned(player); return context == null ? 0 : context.remaining;
    }
    public static Receipt receipt(ServerPlayer player) { return player == null ? null : RECEIPTS.get(player); }

    public static void tick(ServerPlayer player) {
        Context context = owned(player);
        if (context == null || context.processing || context.lastTick == now(player)) return;
        context.lastTick = now(player);
        if (context.closing || !controlEligible(context)) { finish(context, ENDED, "context_changed"); return; }
        // One decrement per observed physical server tick, never wall time, duplicate callbacks or game-time jumps.
        if (--context.remaining <= 0) { finish(context, ENDED, "expired"); return; }
        if (context.candidate != null) {
            if (context.warningTick == now(player)) return;
            context.warningTick = now(player);
            if (--context.warning <= 0) { commit(context); return; }
            context.processing = true;
            try {
                Source before = Source.capture(player);
                String reason = eligibility(context);
                if (!reason.isEmpty()) { finish(context, REFUSED, reason); return; }
                var check = SafeLandingProbeWorld.revalidateReturn(player, context.candidate);
                if (!check.valid()) { finish(context, REFUSED, check.reason()); return; }
                if (!unchanged(context, before)) { finish(context, REFUSED, "event_changed_state"); return; }
                emit(context, WARNING, "");
            } catch (RuntimeException failure) {
                LOG.warn("NEW322 warning ended after native validation failure", failure);
                finish(context, REFUSED, "validation_failed");
            } finally {
                context.processing = false;
                if (context.closing && !context.terminal) finish(context, ENDED, "context_changed");
            }
        } else if (now(player) - context.lastVisual >= 10 || now(player) < context.lastVisual) {
            try { emit(context, MARKED, ""); }
            catch (RuntimeException failure) { finish(context, ENDED, "visual_unavailable"); }
        }
    }

    private static boolean unchanged(Context context, Source before) {
        return identity(context) && !context.closing && before.matches(context.identity.owner());
    }
    private static boolean identity(Context context) {
        var player = context.identity.owner();
        return CONTEXTS.get(player.getUUID()) == context && context.identity.owns(player)
                && mapped(player, context.command.slot()) && context.identity.state().presets.current() == context.preset
                && context.identity.state().presets.revision() == context.revision;
    }
    private static boolean mapped(ServerPlayer player, int slot) {
        return thread(player) && HighPowerCategories.isTeleporter(AbilityStorage.get(player).category)
                && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(player).presets.currentSkill(slot));
    }
    private static boolean controlEligible(Context context) {
        var player = context.identity.owner(); var state = context.identity.state();
        return identity(context) && player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && !player.isSpectator() && !player.getAbilities().instabuild && !player.getAbilities().invulnerable
                && player.containerMenu == player.inventoryMenu && state.activated && state.overloadFine && !state.interfering
                && !state.consumptionInProgress() && state.learned(ID) && state.level >= 2 && state.level <= 5
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID) && HighPowerResources.ready(player)
                && HighPowerResources.effectiveLevel(player) >= 2 && HighPowerCombat.unsupportedReason(player).isEmpty();
    }
    private static String eligibility(Context context) {
        var player = context.identity.owner();
        if (!controlEligible(context) || context.closing) return "context_changed";
        if (!Float.isFinite(player.fallDistance) || player.fallDistance != 0) return "source_airborne";
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) return "movement_conflict";
        if (CornerExchange.active(player) || CrossSubstitution.isTransporting(player) || SafeLandingProbe.active(player) || PhaseSidestep.active(player) || (BeamStep.active(player)||WindPressureLift.active(player))
                || MagneticGroundStep.active(player) || PolarityTether.active(player)
                || cn.academy.port.skill.MagMovement.active(player) || cn.academy.port.skill.VecAccel.active(player)
                || cn.academy.port.skill.JetEngine.active(player) || cn.academy.port.skill.StormWing.active(player)
                || cn.academy.port.skill.MarkTeleport.active(player) || cn.academy.port.skill.PenetrateTeleport.active(player)
                || cn.academy.port.skill.LocationTeleport.active(player) || cn.academy.port.skill.Flashing.active(player))
            return "movement_conflict";
        return SafeLandingProbeWorld.sourceRefusal(player);
    }
    private static boolean refuse(Context context, String reason) { finish(context, REFUSED, reason); return false; }
    private static void finish(Context context, int phase, String reason) {
        if (context.terminal) return;
        context.terminal = true; context.closing = true;
        CONTEXTS.remove(context.identity.owner().getUUID(), context);
        record(context, phase, reason);
        try { if (retainReceipt(context) || context.warningAdvertised) emit(context, phase, reason); }
        catch (RuntimeException failure) { LOG.warn("NEW322 terminal result retained despite visual delivery failure", failure); }
    }
    private static void record(Context context, int phase, String reason) {
        if (retainReceipt(context)) RECEIPTS.put(context.identity.owner(), new Receipt(context.command.input(),
                context.identity.token(), context.paid, context.moved, phase, reason, context.source, destination(context)));
    }
    private static boolean retainReceipt(Context context) {
        var player = context.identity.owner();
        return !context.retired && !player.isRemoved() && !player.hasDisconnected()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    private static Context owned(ServerPlayer player) {
        if (!thread(player)) return null;
        Context context = CONTEXTS.get(player.getUUID());
        return context != null && context.identity.owner() == player ? context : null;
    }
    private static Fences readFences(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(DATA);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("request", Tag.TAG_INT) || !tag.contains("cooldown", Tag.TAG_INT)) return null;
        int request = tag.getInt("request"), cooldown = tag.getInt("cooldown");
        return request < 0 || request > ReturnMarkRules.REQUEST_INTERVAL_TICKS || cooldown < 0 || cooldown > 80
                ? null : new Fences(request, cooldown);
    }
    private static void writeFences(ServerPlayer player, Fences fences) {
        CompoundTag tag = player.getPersistentData().contains(DATA, Tag.TAG_COMPOUND)
                ? player.getPersistentData().getCompound(DATA).copy() : new CompoundTag();
        tag.putInt("version", 1); tag.putInt("request", fences.request); tag.putInt("cooldown", fences.cooldown);
        player.getPersistentData().put(DATA, tag);
    }
    public static void remove(ServerPlayer player) {
        if (!thread(player)) return;
        Context context = owned(player);
        if (context != null) {
            context.retired = true; context.closing = true;
            if (!context.processing) finish(context, ENDED, "context_changed");
        }
        INPUTS.computeIfPresent(player.getUUID(), (id, watermark) -> watermark.owner == player ? null : watermark);
        RECEIPTS.remove(player);
    }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre event) {
        Integer previous = CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (previous != null && previous == event.getServer().getTickCount()) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) if (player.getPersistentData().contains(DATA)) {
            Fences fences = readFences(player);
            if (fences != null && !fences.equals(Fences.ZERO)) writeFences(player, fences.advance());
        }
    }
    @SubscribeEvent public static void controllers(ServerTickEvent.Post event) {
        for (Context context : new ArrayList<>(CONTEXTS.values()))
            if (context.identity.owner().server == event.getServer()) tick(context.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer player) {
            remove(old);
            if (old.getPersistentData().contains(DATA)) player.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        CONTEXTS.entrySet().removeIf(entry -> {
            Context context = entry.getValue();
            if (context.identity.owner().server != event.getServer()) return false;
            context.retired = true; context.closing = true; return true;
        });
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer());
        RECEIPTS.keySet().removeIf(player -> player.server == event.getServer()); CLOCKS.remove(event.getServer());
    }

    private static Vec3 destination(Context context) { return context.anchor == null ? context.source : context.anchor.destination(); }
    private static void emit(Context context, int phase, String reason) {
        if (context.sequence == Long.MAX_VALUE) return;
        var player = context.identity.owner(); Vec3 destination = destination(context);
        AABB bounds = context.anchor == null ? null : context.anchor.relativeBody().move(destination);
        int remaining = phase == MARKED ? context.remaining : phase == WARNING ? context.warning : 0;
        var packet = new Visual(player.getId(), player.getUUID(), context.command.input(), context.command.epoch(),
                context.identity.token(), ++context.sequence, phase, context.source, destination, bounds,
                phase == SUCCESS, reason, remaining);
        context.lastVisual = now(player);
        if (retainReceipt(context) && player.connection != null && player.serverLevel() == context.identity.level())
            PacketDistributor.sendToPlayer(player, packet);
        // The origin warning is visible before transport to nearby observers, with the same bounded fanout as312/313.
        if (phase == WARNING || phase == SUCCESS || context.warningAdvertised && (phase == ENDED || phase == REFUSED)) {
            if (phase == WARNING) context.warningAdvertised = true;
            int sent = 0;
            for (var observer : context.identity.level().players())
                if (observer != player && observer.connection != null && !observer.hasDisconnected()
                        && Math.min(observer.position().distanceToSqr(context.source), observer.position().distanceToSqr(destination)) <= 32 * 32) {
                    PacketDistributor.sendToPlayer(observer, packet); if (++sent >= 32) break;
                }
        }
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.server.getTickCount()); }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                && Math.abs(point.x) <= 30_000_032 && Math.abs(point.z) <= 30_000_032 && Math.abs(point.y) <= 4096;
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         Vec3 source, Vec3 destination, AABB bounds, boolean moved, String reason,
                         int remainingTicks) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > REFUSED || !finite(source) || !finite(destination)
                    || source.distanceToSqr(destination) > Math.pow(ReturnMarkRules.RANGE + .000001, 2)
                    || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || remainingTicks < 0 || remainingTicks > ReturnMarkRules.MARK_LIFE_TICKS
                    || phase == WARNING && remainingTicks > ReturnMarkRules.WARNING_TICKS
                    || phase >= SUCCESS && remainingTicks != 0 || moved != (phase == SUCCESS)
                    || phase <= SUCCESS && (bounds == null || !reason.isEmpty())
                    || bounds != null && (!finite(new Vec3(bounds.minX, bounds.minY, bounds.minZ))
                        || !finite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ)) || bounds.getXsize() <= 0
                        || bounds.getYsize() <= 0 || bounds.getZsize() <= 0 || bounds.getXsize() > 4
                        || bounds.getYsize() > 4 || bounds.getZsize() > 4))
                throw new IllegalArgumentException("Invalid NEW322 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_return_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.epoch);
            buffer.writeLong(value.token); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.source); writeVec(buffer, value.destination); buffer.writeBoolean(value.bounds != null);
            if (value.bounds != null) {
                writeVec(buffer, new Vec3(value.bounds.minX, value.bounds.minY, value.bounds.minZ));
                writeVec(buffer, new Vec3(value.bounds.maxX, value.bounds.maxY, value.bounds.maxZ));
            }
            buffer.writeBoolean(value.moved); buffer.writeUtf(value.reason, 64); buffer.writeVarInt(value.remainingTicks);
        }, buffer -> {
            int entity = buffer.readInt(); UUID owner = buffer.readUUID();
            long input = buffer.readLong(), epoch = buffer.readLong(), token = buffer.readLong(), sequence = buffer.readLong();
            int phase = buffer.readUnsignedByte(); Vec3 source = readVec(buffer), destination = readVec(buffer);
            AABB bounds = buffer.readBoolean() ? new AABB(readVec(buffer), readVec(buffer)) : null;
            return new Visual(entity, owner, input, epoch, token, sequence, phase, source, destination, bounds,
                    buffer.readBoolean(), buffer.readUtf(64), buffer.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
    }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

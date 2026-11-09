package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import cn.academy.port.highpower.core.SpatialRelayRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

/** NEW343: immutable marks and a bounded queue of the owner's actual vanilla dropped items. */
@EventBusSubscriber(modid = "academy")
public final class SpatialRelay {
    public static final String ID = "spatial_relay";
    public static final int MARKED = 0, QUEUED = 1, MOVED = 2, ENDED = 3, REFUSED = 4;
    private static final String DATA = "academy:spatial_relay_fences";
    private static final int MAX_CONTEXTS = 64, MAX_TERMINALS = 256;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<ServerPlayer, Context> TRANSACTIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new LinkedHashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>(), POST_CLOCKS = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.spatial_relay");
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Preview(int eligible, List<Leg> planned, String reason) {
        static final Preview EMPTY = new Preview(0, List.of(), "");
    }
    public record Receipt(long input, long token, boolean paid, boolean moved, String reason,
                          int completed, @Nullable UUID item, @Nullable Vec3 source, @Nullable Vec3 destination) {}
    private static final class Gesture {
        final HighPowerInput.Input input;
        long tick;
        int held;
        Gesture(ServerPlayer owner, HighPowerInput.Input input) { this.input = input; tick = now(owner); }
    }
    private static final class Handle {
        final SpatialRelayMedium medium;
        int next;
        boolean retired;
        Handle(SpatialRelayMedium medium) { this.medium = medium; }
        void retire() { retired = true; medium.retire(); }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;
        final long revision;
        final String category;
        final List<SpatialRelayWorld.Anchor> anchors = new ArrayList<>(3);
        final List<Handle> queue = new ArrayList<>(SpatialRelayRules.MAX_ITEMS);
        HighPowerInput.Input command;
        Gesture gesture;
        long firstMark, tick, lastLegTick, lastVisual, sequence, generation, transactionGeneration;
        int remaining, cursor, eligible, paid, completed;
        boolean queued, processing, reentered, closing, terminal, paying, legPaid;
        UUID item;
        Vec3 source, destination;
        String lastFailure = "";
        Context(ServerPlayer owner, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(owner, input.input(), ++nextToken, AbilityStorage.get(owner));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            category = identity.state().category; tick = lastLegTick = lastVisual = now(owner);
        }
    }
    /** All mutable player facts used for this particular leg, captured before any native event. */
    private record OwnerSnapshot(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                                 float yaw, float pitch, float fall, boolean grounded, boolean crouching,
                                 int hurtTime, int selected, List<ItemStack> inventory, ItemStack carried,
                                 BigDecimal growth, BigDecimal earned, long growthRevision, int level,
                                 double experience, HighPowerInput.Input command) {
        static OwnerSnapshot capture(Context context) {
            var p = context.identity.owner(); var state = context.identity.state();
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) inventory.add(p.getInventory().getItem(i).copy());
            var values = HighPowerGrowth.values(p);
            return new OwnerSnapshot(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(), p.getPose(),
                    p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown(), p.hurtTime,
                    p.getInventory().selected, List.copyOf(inventory), p.containerMenu.getCarried().copy(),
                    values.position(), values.earnedPosition(), HighPowerActiveGrowth.revision(p),
                    state.level, state.levelExperience, context.command);
        }
        boolean matches(Context context) {
            var p = context.identity.owner(); var state = context.identity.state(); var values = HighPowerGrowth.values(p);
            if (context.command != command || !position.equals(p.position()) || !eye.equals(p.getEyePosition())
                    || !motion.equals(p.getDeltaMovement()) || !body.equals(p.getBoundingBox()) || pose != p.getPose()
                    || Float.compare(yaw, p.getYRot()) != 0 || Float.compare(pitch, p.getXRot()) != 0
                    || Float.compare(fall, p.fallDistance) != 0 || grounded != p.onGround() || crouching != p.isShiftKeyDown()
                    || hurtTime != p.hurtTime || selected != p.getInventory().selected
                    || inventory.size() != p.getInventory().getContainerSize()
                    || !ItemStack.matches(carried, p.containerMenu.getCarried())
                    || growth.compareTo(values.position()) != 0 || earned.compareTo(values.earnedPosition()) != 0
                    || growthRevision != HighPowerActiveGrowth.revision(p) || level != state.level
                    || Double.compare(experience, state.levelExperience) != 0) return false;
            for (int i = 0; i < inventory.size(); i++)
                if (!ItemStack.matches(inventory.get(i), p.getInventory().getItem(i))) return false;
            return true;
        }
    }

    private SpatialRelay() {}

    public static boolean start(ServerPlayer player, String wire) { return press(player, wire); }
    public static boolean press(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Context transaction = TRANSACTIONS.get(player);
        if (transaction != null) { invalidateTransaction(transaction); return false; }
        if (!claimInput(player, input) || AbilityConsumption.busy(player) || !mapped(player, input.slot())) return false;
        Context context = CONTEXTS.get(player.getUUID());
        if (context != null && (!identity(context) || context.identity.owner() != player
                || context.command.epoch() != input.epoch() || context.command.slot() != input.slot())) {
            finish(context, ENDED, "context_changed"); return false;
        }
        if (context != null && (context.processing || context.gesture != null)) return false;
        if (context == null) {
            if (nextToken == Long.MAX_VALUE || CONTEXTS.size() >= MAX_CONTEXTS) return false;
            context = new Context(player, input); CONTEXTS.put(player.getUUID(), context);
        }
        context.gesture = new Gesture(player, input);
        return true;
    }

    /** A hold is counted only on genuine server ticks, and clearing happens only on this release. */
    public static boolean release(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Context transaction = TRANSACTIONS.get(player);
        if (transaction != null) { invalidateTransaction(transaction); return false; }
        Context context = owned(player);
        if (context == null || context.gesture == null || !context.gesture.input.equals(input) || context.processing) return false;
        int held = context.gesture.held; context.gesture = null;
        if (held >= SpatialRelayRules.HOLD_TICKS) { context.command = input; clear(player); return true; }
        if (context.queued) { emit(context, REFUSED, null, "queue_running", input); return false; }
        context.command = input;
        return action(context);
    }

    private static boolean action(Context context) {
        var player = context.identity.owner(); beginTransaction(context);
        try {
            if (!context.anchors.isEmpty() && ((now(player) - context.firstMark) & 0xffffffffL) >= SpatialRelayRules.PREPARATION_TICKS) {
                finish(context, ENDED, "expired"); return false;
            }
            if (!identity(context) || !HighPowerResources.ready(player)) return refuse(context, "not_ready");
            Integer fence = readFence(player);
            if (fence == null) return refuse(context, "storage_invalid");
            if (fence > 0) return refuse(context, "request_fence");
            writeFence(player, SpatialRelayRules.REQUEST_FENCE);
            if (!context.identity.state().canUse(ID)) return refuse(context, "cooldown");
            OwnerSnapshot before = OwnerSnapshot.capture(context);
            if (context.anchors.size() == 3 || context.anchors.size() == 2 && player.isShiftKeyDown())
                return prepareQueue(context, before);
            var assessment = SpatialRelayWorld.mark(player);
            if (!assessment.valid()) return refuse(context, assessment.reason());
            if (!unchanged(context, before)) return refuse(context, "transaction_changed");
            if (!context.anchors.isEmpty()) {
                Vec3 previous = context.anchors.get(context.anchors.size() - 1).destination();
                if (!SpatialRelayRules.withinRange(point(previous), point(assessment.anchor().destination())))
                    return refuse(context, "out_of_range");
            }
            if (context.anchors.isEmpty()) { context.firstMark = now(player); context.remaining = SpatialRelayRules.PREPARATION_TICKS; }
            context.anchors.add(assessment.anchor());
            record(context, ""); emit(context, MARKED, null, ""); return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW343 free preparation refused after native validation failure", failure);
            return refuse(context, "validation_failed");
        } finally { endTransaction(context); }
    }

    private static boolean prepareQueue(Context context, OwnerSnapshot owner) {
        var player = context.identity.owner();
        for (var anchor : context.anchors) {
            var check = SpatialRelayWorld.checkAnchor(player, anchor);
            if (!check.valid()) return refuse(context, check.reason());
            if (!unchanged(context, owner)) return refuse(context, "transaction_changed");
        }
        var selection = SpatialRelayWorld.gather(player, context.anchors.getFirst());
        if (!selection.valid()) return refuse(context, selection.reason());
        if (selection.media().isEmpty()) return refuse(context, "no_items");
        if (selection.media().size() > SpatialRelayRules.MAX_ITEMS) return refuse(context, "item_limit");
        List<SpatialRelayMedium.Snapshot> snapshots = new ArrayList<>();
        int legs = 0;
        boolean checkedFirstLeg = false;
        for (var medium : selection.media()) {
            if (!medium.valid()) return refuse(context, "item_changed");
            var snapshot = medium.snapshot(); snapshots.add(snapshot);
            Vec3 source = snapshot.position();
            for (var anchor : context.anchors) {
                Vec3 destination = anchor.destination();
                if (!SpatialRelayRules.withinRange(point(source), point(destination))) return refuse(context, "out_of_range");
                if (!source.equals(destination)) {
                    legs++;
                    // One bounded launch probe only. Subsequent planned legs are checked on their own tick.
                    if (!checkedFirstLeg) {
                        var check = SpatialRelayWorld.checkLeg(player, medium, anchor);
                        if (!check.valid()) return refuse(context, check.reason());
                        if (!unchanged(context, owner) || !snapshot.matches(medium)) return refuse(context, "transaction_changed");
                        checkedFirstLeg = true;
                    }
                }
                source = destination;
            }
            if (!unchanged(context, owner) || !snapshot.matches(medium)) return refuse(context, "transaction_changed");
        }
        if (legs > SpatialRelayRules.MAX_LEGS) return refuse(context, "leg_limit");
        if (legs > 0 && (HighPowerResources.hasReservation(player)
                || HighPowerResources.availableCp(player) < SpatialRelayRules.cpCost(owner.growth)))
            return refuse(context, "insufficient_cp");
        for (int i = 0; i < selection.media().size(); i++)
            if (!snapshots.get(i).matches(selection.media().get(i))) return refuse(context, "item_changed");
        if (!unchanged(context, owner)) return refuse(context, "transaction_changed");
        context.queue.clear();
        for (var medium : selection.media()) context.queue.add(new Handle(medium));
        context.cursor = context.completed = context.paid = 0; context.eligible = context.queue.size(); context.queued = true;
        context.lastLegTick = now(player); context.lastFailure = "";
        record(context, ""); emit(context, QUEUED, null, "");
        return true;
    }

    /** An invalid native handle is retired, never rebound to a replacement or merged survivor. */
    private static void runQueue(Context context) {
        var player = context.identity.owner();
        if (context.lastLegTick == now(player)) return;
        context.lastLegTick = now(player); beginTransaction(context);
        try {
            while (context.cursor < context.queue.size()) {
                Handle handle = context.queue.get(context.cursor);
                if (handle.retired || !handle.medium.valid()) { skip(context, handle, "item_changed"); continue; }
                while (handle.next < context.anchors.size()
                        && handle.medium.entity.position().equals(context.anchors.get(handle.next).destination())) handle.next++;
                if (handle.next == context.anchors.size()) { handle.retire(); context.cursor++; continue; }
                if (context.completed >= SpatialRelayRules.MAX_LEGS) { finish(context, ENDED, "leg_limit"); return; }
                moveLeg(context, handle);
                // Even a cancelled or refused positive attempt cannot admit another one this tick.
                break;
            }
            if (!context.terminal && context.cursor >= context.queue.size())
                finish(context, ENDED, context.lastFailure.isEmpty() ? "completed" : context.lastFailure);
        } catch (RuntimeException failure) {
            LOG.warn("NEW343 stopped without rollback or replay; current leg paid={}", context.legPaid, failure);
            if (context.legPaid) finish(context, ENDED, "commit_unconfirmed");
            else if (context.reentered || context.closing || !identity(context)) finish(context, ENDED, "context_changed");
            else if (context.cursor < context.queue.size()) {
                skip(context, context.queue.get(context.cursor), "validation_failed");
                if (context.cursor >= context.queue.size()) finish(context, ENDED, "validation_failed");
            }
        } finally { endTransaction(context); }
    }

    private static void moveLeg(Context context, Handle handle) {
        var player = context.identity.owner(); var medium = handle.medium;
        var anchor = context.anchors.get(handle.next); Vec3 destination = anchor.destination();
        context.item = medium.uuid; context.source = medium.entity.position(); context.destination = destination; context.legPaid = false;
        OwnerSnapshot owner = OwnerSnapshot.capture(context);
        SpatialRelayMedium.Snapshot before = medium.snapshot();
        var check = SpatialRelayWorld.checkLeg(player, medium, anchor);
        if (!check.valid()) { skip(context, handle, check.reason()); return; }
        if (!unchanged(context, owner) || !before.matches(medium)) { changed(context, handle); return; }
        EntityTeleportEvent event = new EntityTeleportEvent(medium.entity, destination.x, destination.y, destination.z);
        NeoForge.EVENT_BUS.post(event);
        if (!unchanged(context, owner) || !before.matches(medium)) { changed(context, handle); return; }
        if (event.isCanceled() || Double.compare(event.getTargetX(), destination.x) != 0
                || Double.compare(event.getTargetY(), destination.y) != 0 || Double.compare(event.getTargetZ(), destination.z) != 0) {
            skip(context, handle, "teleport_canceled"); return;
        }
        check = SpatialRelayWorld.checkLeg(player, medium, anchor);
        if (!check.valid()) { skip(context, handle, check.reason()); return; }
        // The final source, medium, inventory, active growth and session checks precede closed CP commit.
        if (!unchanged(context, owner) || !before.matches(medium) || HighPowerResources.hasReservation(player)) {
            changed(context, handle); return;
        }
        double cost = SpatialRelayRules.cpCost(owner.growth);
        context.paying = true;
        boolean paid;
        try { paid = HighPowerResources.trySpendSpatialRelayLeg(player, cost); }
        finally { context.paying = false; }
        if (!paid) { finish(context, ENDED, "insufficient_cp"); return; }
        // Retire this transaction's payment before native setter callbacks can reenter the controller.
        context.legPaid = true; context.paid++;
        if (!unchanged(context, owner) || !before.matches(medium)) { finish(context, ENDED, "commit_unconfirmed"); return; }
        record(context, "commit_pending");
        medium.entity.setPos(destination);
        if (!unchanged(context, owner) || !before.matchesMoved(medium, destination)) {
            finish(context, ENDED, "commit_unconfirmed"); return;
        }
        context.completed++; handle.next++;
        Leg confirmed = new Leg(medium.entityId, before.position(), destination);
        if (handle.next == context.anchors.size()) { handle.retire(); context.cursor++; }
        record(context, ""); emit(context, MOVED, confirmed, "");
        try { player.serverLevel().getChunkSource().broadcastAndSend(medium.entity, new ClientboundTeleportEntityPacket(medium.entity)); }
        catch (RuntimeException delivery) { LOG.warn("NEW343 retained confirmed native movement after packet delivery failure", delivery); }
        try { AbilityStorage.save(player); AcademyNetwork.sync(player); }
        catch (RuntimeException delivery) { LOG.warn("NEW343 retained paid movement after resource sync failure", delivery); }
    }

    static boolean payingLeg(ServerPlayer player) {
        Context context = TRANSACTIONS.get(player);
        return context != null && context.processing && context.paying && !context.legPaid && !context.reentered
                && !context.closing && !context.terminal && context.queued && context.transactionGeneration == context.generation
                && context.paid < SpatialRelayRules.MAX_LEGS && identity(context);
    }
    private static void changed(Context context, Handle handle) {
        if (context.reentered || context.closing || !identity(context)) finish(context, ENDED, "context_changed");
        else skip(context, handle, "transaction_changed");
    }
    private static void skip(Context context, Handle handle, String reason) {
        handle.retire(); context.cursor++; context.lastFailure = reason;
        record(context, reason);
        if (!context.terminal) emit(context, QUEUED, null, reason);
    }
    private static void beginTransaction(Context context) {
        context.processing = true; context.reentered = false; context.legPaid = false;
        context.transactionGeneration = ++context.generation;
        TRANSACTIONS.put(context.identity.owner(), context);
    }
    private static void endTransaction(Context context) {
        context.paying = false; context.processing = false;
        TRANSACTIONS.remove(context.identity.owner(), context);
    }
    private static void invalidateTransaction(Context context) { context.reentered = true; context.generation++; }
    private static boolean unchanged(Context context, OwnerSnapshot snapshot) {
        return TRANSACTIONS.get(context.identity.owner()) == context && context.processing && !context.reentered
                && !context.closing && !context.terminal && context.transactionGeneration == context.generation
                && identity(context) && snapshot.matches(context);
    }
    private static boolean identity(Context context) {
        var player = context.identity.owner(); var state = context.identity.state();
        return !context.terminal && CONTEXTS.get(player.getUUID()) == context && context.identity.owns(player)
                && context.command.epoch() == context.identity.epoch() && mapped(player, context.command.slot())
                && state.presets.current() == context.preset && state.presets.revision() == context.revision
                && context.category.equals(state.category) && player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && !player.isCreative() && !player.isSpectator() && !player.getAbilities().invulnerable
                && player.containerMenu == player.inventoryMenu && state.activated && state.overloadFine && !state.interfering
                && !state.consumptionInProgress() && !AbilityConsumption.busy(player) && state.learned(ID)
                && state.learned(RecoveryMark.ID) && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID)
                && state.level >= 4 && state.level <= 5 && HighPowerCategories.isTeleporter(state.category)
                && HighPowerCombat.enabled(player) && HighPowerCombat.unsupportedReason(player).isEmpty()
                && HighPowerResources.effectiveLevel(player) >= 4 && finite(player.position()) && finite(player.getEyePosition())
                && finite(player.getDeltaMovement()) && Float.isFinite(player.getYRot()) && Float.isFinite(player.getXRot());
    }
    private static boolean mapped(ServerPlayer player, int slot) {
        return thread(player) && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(player).presets.currentSkill(slot));
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.server.getTickCount()); }
    private static Context owned(ServerPlayer player) {
        if (!thread(player)) return null;
        Context context = CONTEXTS.get(player.getUUID());
        return context != null && context.identity.owner() == player ? context : null;
    }
    private static boolean claimInput(ServerPlayer player, HighPowerInput.Input input) {
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous != null && previous.owner == player && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        if (previous == null && INPUTS.size() >= MAX_TERMINALS) return false;
        INPUTS.put(player.getUUID(), new Watermark(player, input.epoch(), input.input())); return true;
    }
    public static boolean canBegin(ServerPlayer player) {
        Context context = owned(player);
        return thread(player) && !TRANSACTIONS.containsKey(player) && (context == null || !context.processing && context.gesture == null);
    }
    public static boolean active(ServerPlayer player) {
        Context context = owned(player); return context != null && (context.queued || context.processing) && !context.terminal;
    }
    public static boolean marked(ServerPlayer player) { Context context = owned(player); return context != null && !context.anchors.isEmpty() && context.remaining > 0; }
    public static int remainingTicks(ServerPlayer player) { Context context = owned(player); return context == null ? 0 : context.remaining; }
    public static int heldTicks(ServerPlayer player) { Context context = owned(player); return context == null || context.gesture == null ? 0 : context.gesture.held; }
    public static int points(ServerPlayer player) { Context context = owned(player); return context == null ? 0 : context.anchors.size(); }
    public static List<Vec3> preparedPoints(ServerPlayer player) {
        Context context = owned(player);
        return context == null ? List.of() : context.anchors.stream().map(SpatialRelayWorld.Anchor::destination).toList();
    }
    public static int queuedCount(ServerPlayer player) { Context context = owned(player); return context == null ? 0 : queuedCount(context); }
    public static Receipt receipt(ServerPlayer player) { return thread(player) ? RECEIPTS.get(player) : null; }
    private static int queuedCount(Context context) {
        if (!context.queued || context.terminal) return 0;
        int count = 0;
        for (int i = context.cursor; i < context.queue.size(); i++) if (!context.queue.get(i).retired) count++;
        return count;
    }
    public static boolean abort(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Context context = owned(player);
        if (context != null && (context.command.epoch() != input.epoch() || context.command.slot() != input.slot()
                || input.input() < context.command.input() || context.gesture != null && input.input() < context.gesture.input.input())) return false;
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous != null && previous.owner == player && previous.epoch == input.epoch() && input.input() < previous.input) return false;
        if (previous == null && INPUTS.size() >= MAX_TERMINALS) return false;
        INPUTS.put(player.getUUID(), new Watermark(player, input.epoch(), input.input()));
        if (context != null) context.command = input;
        clear(player); return true;
    }
    public static void cancelActive(ServerPlayer player) { clear(player); }
    public static void clear(ServerPlayer player) {
        if (!thread(player)) return;
        Context context = owned(player);
        if (context == null) context = TRANSACTIONS.get(player);
        if (context != null) { invalidateTransaction(context); context.closing = true; finish(context, ENDED, "cleared"); }
    }
    public static void remove(ServerPlayer player) {
        clear(player); if (player == null) return;
        INPUTS.computeIfPresent(player.getUUID(), (id, value) -> value.owner == player ? null : value);
        RECEIPTS.remove(player);
    }
    /** Called synchronously before native merging or another native movement owner mutates the item. */
    public static void invalidateNativeItems(ItemEntity... items) {
        if (items == null) return;
        for (Context context : new ArrayList<>(CONTEXTS.values())) {
            if (!thread(context.identity.owner())) continue;
            for (Handle handle : context.queue) for (ItemEntity item : items) if (item != null && handle.medium.entity == item) {
                handle.retire();
            }
        }
    }

    private static boolean refuse(Context context, String reason) {
        if (context.closing || context.reentered || context.terminal || !identity(context)
                || context.anchors.isEmpty() || context.remaining <= 0) { finish(context, REFUSED, reason); return false; }
        record(context, reason); emit(context, REFUSED, null, reason); return false;
    }
    private static void finish(Context context, int phase, String reason) {
        if (context.terminal) {
            // Reentrant clear may retire the queue from inside the paid native setter.
            if (context.legPaid && "commit_unconfirmed".equals(reason)) record(context, reason);
            return;
        }
        context.closing = true; context.terminal = true; context.remaining = 0; context.gesture = null; context.queued = false;
        for (Handle handle : context.queue) handle.retire();
        context.anchors.clear(); context.queue.clear();
        CONTEXTS.remove(context.identity.owner().getUUID(), context);
        record(context, reason); emit(context, phase, null, reason);
    }
    private static void record(Context context, String reason) {
        var player = context.identity.owner();
        if (player.hasDisconnected() || player.isRemoved()) return;
        if (!RECEIPTS.containsKey(player) && RECEIPTS.size() >= MAX_TERMINALS) RECEIPTS.remove(RECEIPTS.keySet().iterator().next());
        RECEIPTS.put(player, new Receipt(context.command.input(), context.identity.token(), context.paid > 0,
                context.completed > 0, reason, context.completed, context.item, context.source, context.destination));
    }
    public static void tick(ServerPlayer player) {
        Context context = owned(player);
        if (context == null || context.processing) return;
        try {
            if (!identity(context)) { finish(context, ENDED, "context_changed"); return; }
            long tick = now(player);
            if (context.tick == tick) return;
            context.tick = tick;
            if (!context.anchors.isEmpty()) {
                long elapsed = (tick - context.firstMark) & 0xffffffffL;
                context.remaining = (int)Math.max(0, SpatialRelayRules.PREPARATION_TICKS - elapsed);
                if (context.remaining == 0) { finish(context, ENDED, "expired"); return; }
            }
            if (context.gesture != null && context.gesture.tick != tick) {
                context.gesture.tick = tick;
                context.gesture.held = Math.min(SpatialRelayRules.HOLD_TICKS, context.gesture.held + 1);
            }
            if (context.queued) runQueue(context);
            if (!context.terminal && !context.anchors.isEmpty() && ((tick - context.lastVisual) & 0xffffffffL) >= 5)
                emit(context, context.queued ? QUEUED : MARKED, null, "");
        } catch (RuntimeException failure) {
            LOG.warn("NEW343 retired invalid controller state", failure); finish(context, ENDED, "validation_failed");
        }
    }
    private static Integer readFence(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA)) return 0;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(DATA);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1 || !tag.contains("request", Tag.TAG_INT)) return null;
        int request = tag.getInt("request");
        return request < 0 || request > SpatialRelayRules.REQUEST_FENCE ? null : request;
    }
    private static void writeFence(ServerPlayer player, int request) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("request", request);
        player.getPersistentData().put(DATA, tag);
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event) {
        Integer old = CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (old != null && old == event.getServer().getTickCount()) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) if (player.getPersistentData().contains(DATA)) {
            Integer fence = readFence(player); if (fence != null && fence > 0) writeFence(player, fence - 1);
        }
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post event) {
        Integer old = POST_CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (old != null && old == event.getServer().getTickCount()) return;
        for (var context : new ArrayList<>(CONTEXTS.values()))
            if (context.identity.owner().server == event.getServer()) tick(context.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer player) {
            remove(old);
            if (old.getPersistentData().contains(DATA)) player.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (var context : new ArrayList<>(CONTEXTS.values()))
            if (context.identity.owner().server == event.getServer()) { for (Handle handle : context.queue) handle.retire(); CONTEXTS.remove(context.identity.owner().getUUID(), context); }
        TRANSACTIONS.keySet().removeIf(player -> player.server == event.getServer());
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer());
        RECEIPTS.keySet().removeIf(player -> player.server == event.getServer()); CLOCKS.remove(event.getServer()); POST_CLOCKS.remove(event.getServer());
    }

    private static List<Leg> planned(Context context) {
        if (!context.queued || context.terminal) return List.of();
        List<Leg> legs = new ArrayList<>(SpatialRelayRules.MAX_LEGS);
        for (int i = context.cursor; i < context.queue.size(); i++) {
            Handle handle = context.queue.get(i);
            if (handle.retired || !handle.medium.valid()) continue;
            Vec3 from = handle.medium.entity.position();
            for (int j = handle.next; j < context.anchors.size(); j++) {
                Vec3 to = context.anchors.get(j).destination();
                if (!from.equals(to)) legs.add(new Leg(handle.medium.entityId, from, to));
                from = to;
            }
        }
        return List.copyOf(legs);
    }
    /** Bounded owner preview uses fresh real drops without freezing or mutating the eventual queue. */
    private static Preview preview(Context context) {
        if (context.anchors.isEmpty() || context.terminal || context.reentered) return Preview.EMPTY;
        boolean ownsTransaction = !context.processing;
        if (ownsTransaction) beginTransaction(context);
        List<SpatialRelayMedium> media = List.of();
        try {
            OwnerSnapshot owner = OwnerSnapshot.capture(context);
            var selected = SpatialRelayWorld.gather(context.identity.owner(), context.anchors.getFirst());
            media = selected.media();
            if (!selected.valid()) return new Preview(0, List.of(), selected.reason());
            if (media.size() > SpatialRelayRules.MAX_ITEMS) return new Preview(0, List.of(), "item_limit");
            List<Leg> legs = new ArrayList<>();
            List<SpatialRelayMedium.Snapshot> snapshots = new ArrayList<>();
            for (var medium : media) {
                if (!medium.valid()) return new Preview(0, List.of(), "item_changed");
                var snapshot = medium.snapshot(); snapshots.add(snapshot); Vec3 from = snapshot.position();
                for (var anchor : context.anchors) {
                    Vec3 to = anchor.destination();
                    if (!from.equals(to)) legs.add(new Leg(medium.entityId, from, to));
                    from = to;
                }
            }
            if (!unchanged(context, owner)) return new Preview(0, List.of(), "transaction_changed");
            for (int i = 0; i < media.size(); i++)
                if (!snapshots.get(i).matches(media.get(i))) return new Preview(0, List.of(), "item_changed");
            return new Preview(media.size(), List.copyOf(legs), "");
        } catch (RuntimeException failure) {
            return new Preview(0, List.of(), "preview_unavailable");
        } finally {
            for (var medium : media) medium.retire();
            if (ownsTransaction) endTransaction(context);
        }
    }
    private static void emit(Context context, int phase, @Nullable Leg confirmed, String reason) {
        emit(context, phase, confirmed, reason, context.command);
    }
    private static void emit(Context context, int phase, @Nullable Leg confirmed, String reason, HighPowerInput.Input input) {
        try {
            if (context.sequence == Long.MAX_VALUE) return;
            var player = context.identity.owner();
            Preview preview = !context.queued && !context.terminal ? preview(context) : Preview.EMPTY;
            if (context.terminal && phase != ENDED && phase != REFUSED) return;
            if (!context.terminal && (context.reentered || context.closing || !identity(context))) {
                finish(context, ENDED, "context_changed"); return;
            }
            Vec3 a = context.anchors.size() > 0 ? context.anchors.get(0).destination() : null;
            Vec3 b = context.anchors.size() > 1 ? context.anchors.get(1).destination() : null;
            Vec3 c = context.anchors.size() > 2 ? context.anchors.get(2).destination() : null;
            var visual = new Visual(player.getId(), player.getUUID(), input.epoch(), input.input(),
                    context.identity.token(), ++context.sequence, phase, context.remaining, a, b, c,
                    context.queued || context.terminal ? context.eligible : preview.eligible,
                    queuedCount(context), context.completed, context.queued ? planned(context) : preview.planned,
                    confirmed, reason.isEmpty() ? preview.reason : reason);
            context.lastVisual = now(player); PacketDistributor.sendToPlayer(player, visual);
            if (phase == MOVED) for (var observer : player.serverLevel().players())
                if (observer != player && observer.distanceToSqr(player) <= 64 * 64) PacketDistributor.sendToPlayer(observer, visual);
        } catch (RuntimeException failure) { LOG.warn("NEW343 retained authoritative state after visual delivery failure", failure); }
    }
    private static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
    private static boolean finite(Vec3 vector) { return SpatialRelayMedium.finite(vector); }
    public record Leg(int itemEntityId, Vec3 from, Vec3 to) {
        public Leg {
            if (itemEntityId < 0 || !finite(from) || !finite(to)) throw new IllegalArgumentException("Invalid native item relay leg");
        }
    }
    public record Visual(int entity, UUID owner, long epoch, long input, long token, long sequence, int phase,
                         int remainingTicks, @Nullable Vec3 markA, @Nullable Vec3 markB, @Nullable Vec3 markC,
                         int eligible, int queued, int completed, List<Leg> planned, @Nullable Leg confirmed,
                         String reason) implements CustomPacketPayload {
        public Visual {
            planned = List.copyOf(planned);
            if (entity < 0 || owner == null || owner.equals(new UUID(0, 0)) || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > REFUSED || remainingTicks < 0 || remainingTicks > SpatialRelayRules.PREPARATION_TICKS
                    || markA != null && !finite(markA) || markB != null && !finite(markB) || markC != null && !finite(markC)
                    || markA == null && markB != null || markB == null && markC != null || (markA == null) != (remainingTicks == 0)
                    || eligible < 0 || eligible > SpatialRelayRules.MAX_ITEMS || queued < 0 || queued > SpatialRelayRules.MAX_ITEMS
                    || completed < 0 || completed > SpatialRelayRules.MAX_LEGS || planned.size() > SpatialRelayRules.MAX_LEGS
                    || reason == null || reason.length() > 128 || (phase == MOVED) != (confirmed != null)
                    || confirmed != null && confirmed.from.equals(confirmed.to)
                    || phase == ENDED && (markA != null || queued != 0 || !planned.isEmpty())
                    || (phase == QUEUED || phase == MOVED) && markB == null)
                throw new IllegalArgumentException("Invalid relay visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_spatial_relay"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.entity); buffer.writeUUID(value.owner); buffer.writeVarLong(value.epoch); buffer.writeVarLong(value.input);
            buffer.writeVarLong(value.token); buffer.writeVarLong(value.sequence); buffer.writeByte(value.phase); buffer.writeVarInt(value.remainingTicks);
            optionalVec(buffer, value.markA); optionalVec(buffer, value.markB); optionalVec(buffer, value.markC);
            buffer.writeVarInt(value.eligible); buffer.writeVarInt(value.queued); buffer.writeVarInt(value.completed); buffer.writeVarInt(value.planned.size());
            for (Leg leg : value.planned) leg(buffer, leg);
            buffer.writeBoolean(value.confirmed != null); if (value.confirmed != null) leg(buffer, value.confirmed);
            buffer.writeUtf(value.reason, 128);
        }, buffer -> {
            int entity = buffer.readVarInt(); UUID owner = buffer.readUUID();
            long epoch = buffer.readVarLong(), input = buffer.readVarLong(), token = buffer.readVarLong(), sequence = buffer.readVarLong();
            int phase = buffer.readUnsignedByte(), remaining = buffer.readVarInt();
            Vec3 a = optionalVec(buffer), b = optionalVec(buffer), c = optionalVec(buffer);
            int eligible = buffer.readVarInt(), queued = buffer.readVarInt(), completed = buffer.readVarInt(), size = buffer.readVarInt();
            if (size < 0 || size > SpatialRelayRules.MAX_LEGS) throw new IllegalArgumentException("Too many relay legs");
            List<Leg> planned = new ArrayList<>(size); for (int i = 0; i < size; i++) planned.add(leg(buffer));
            Leg confirmed = buffer.readBoolean() ? leg(buffer) : null;
            return new Visual(entity, owner, epoch, input, token, sequence, phase, remaining, a, b, c, eligible, queued, completed, planned, confirmed, buffer.readUtf(128));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void optionalVec(RegistryFriendlyByteBuf buffer, @Nullable Vec3 value) { buffer.writeBoolean(value != null); if (value != null) vec(buffer, value); }
    private static Vec3 optionalVec(RegistryFriendlyByteBuf buffer) { return buffer.readBoolean() ? vec(buffer) : null; }
    private static void vec(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    private static void leg(RegistryFriendlyByteBuf buffer, Leg value) { buffer.writeVarInt(value.itemEntityId); vec(buffer, value.from); vec(buffer, value.to); }
    private static Leg leg(RegistryFriendlyByteBuf buffer) { return new Leg(buffer.readVarInt(), vec(buffer), vec(buffer)); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

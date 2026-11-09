package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.RecoveryMarkRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW323 bounded session registrations for eight existing, proven needles.
 * Recovery never creates a medium, damage permit, inventory insertion, XP or refund. */
@EventBusSubscriber(modid = "academy")
public final class RecoveryMark {
    public static final String ID = "recovery_mark";
    public static final int MARKED = 1, RECOVERED = 2, CLEARED = 3, REFUSED = 4;
    private static final String DATA = "academy:recovery_mark_fences";
    public static final int MAX_HANDLES = 8;
    private static final int MAX_CONTEXTS = 64, MAX_TERMINALS = 256, MAX_SELECTION = 64;
    /** Selection tolerance only; custody and transport still use the real entity body. */
    private static final double AIM_TOLERANCE = 0.1;
    private static final Map<UUID, Session> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new LinkedHashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.recovery_mark");
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fences(int request, int cooldown) {
        static final Fences ZERO = new Fences(0, 0);
        Fences advance() { return new Fences(Math.max(0, request - 1), Math.max(0, cooldown - 1)); }
    }
    public record Receipt(long input, long token, boolean paid, boolean moved, int phase,
                          String reason, Vec3 source, Vec3 destination, int needleEntity, UUID needleUuid) {}
    private record Source(Vec3 position, Vec3 motion, AABB body, Pose pose, float yaw,
                          float pitch, float fall, boolean grounded, boolean crouching, int hurtTime) {
        static Source capture(ServerPlayer player) {
            return new Source(player.position(), player.getDeltaMovement(), player.getBoundingBox(), player.getPose(),
                    player.getYRot(), player.getXRot(), player.fallDistance, player.onGround(), player.isShiftKeyDown(), player.hurtTime);
        }
        boolean matches(ServerPlayer player) {
            return position.equals(player.position()) && motion.equals(player.getDeltaMovement())
                    && body.equals(player.getBoundingBox()) && pose == player.getPose()
                    && Float.compare(yaw, player.getYRot()) == 0 && Float.compare(pitch, player.getXRot()) == 0
                    && Float.compare(fall, player.fallDistance) == 0 && grounded == player.onGround()
                    && crouching == player.isShiftKeyDown() && hurtTime == player.hurtTime;
        }
    }
    private record Snapshot(Source player, HighPowerInput.Input input, Vec3 position, Vec3 motion,
                            AABB body, float yaw, float pitch, ItemStack medium, CompoundTag nativeState, List<ItemStack> inventory, BigDecimal growth) {
        static Snapshot capture(Context context) {
            var player = context.identity.owner(); var needle = context.needle;
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) inventory.add(player.getInventory().getItem(i).copy());
            return new Snapshot(Source.capture(player), context.command, needle.position(), needle.getDeltaMovement(),
                    needle.getBoundingBox(), needle.getYRot(), needle.getXRot(), context.medium.stack(), context.medium.nativeState(), inventory,
                    HighPowerGrowth.values(player).position());
        }
        boolean matches(Context context) {
            var needle = context.needle;
            return matchesOwner(context) && needleIdentity(context)
                    && position.equals(needle.position()) && motion.equals(needle.getDeltaMovement())
                    && body.equals(needle.getBoundingBox()) && Float.compare(yaw, needle.getYRot()) == 0
                    && Float.compare(pitch, needle.getXRot()) == 0 && ItemStack.matches(medium, context.medium.stack())
                    && nativeState.equals(context.medium.nativeState());
        }
        /** The owner's snapshot remains meaningful after the expected needle relocation. */
        boolean matchesOwner(Context context) {
            var owner = context.identity.owner();
            if (context.command != input || !player.matches(owner)
                    || inventory.size() != owner.getInventory().getContainerSize()
                    || growth.compareTo(HighPowerGrowth.values(owner).position()) != 0) return false;
            for (int i = 0; i < inventory.size(); i++)
                if (!ItemStack.matches(inventory.get(i), owner.getInventory().getItem(i))) return false;
            return true;
        }
    }
    private static final class Session {
        final LegacySingleKeyServerIdentity identity;
        final int preset;
        final long revision;
        final Map<Long, Context> handles = new LinkedHashMap<>();
        HighPowerInput.Input command;
        long sequence;
        boolean processing, closing, retired, paidFence;
        Session(ServerPlayer player, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(player, input.input(), ++nextToken, AbilityStorage.get(player));
            command = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
        }
    }
    private static final class Context {
        final Session session;
        final LegacySingleKeyServerIdentity identity;
        final long handleToken;
        HighPowerInput.Input command;
        Entity needle;
        RecoveryMedium medium;
        int needleEntity = -1;
        RecoveryMarkWorld.Anchor anchor;
        Vec3 source;
        int remaining = RecoveryMarkRules.MARK_LIFE_TICKS;
        long lastTick, lastVisual;
        boolean processing, closing, retired, terminal, paid, moved, recovering, eventPosted;
        String failure = "";
        Context(Session session, HighPowerInput.Input input) {
            this.session = session; identity = session.identity; command = input; handleToken = ++nextToken;
            lastTick = now(identity.owner()); lastVisual = lastTick;
        }
    }
    private record Selection(RecoveryMedium medium, String reason) {}

    private RecoveryMark() {}

    public static boolean press(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input) || AbilityConsumption.busy(player)
                || !mapped(player, input.slot()) || !finite(player.position()) || !finite(player.getEyePosition())
                || !Float.isFinite(player.getYRot()) || !Float.isFinite(player.getXRot())) return false;
        Session current = CONTEXTS.get(player.getUUID());
        if (current != null && current.processing) return false;
        if (!claimInput(player, input)) return false;
        if (current != null && (!sessionIdentity(current) || current.command.epoch() != input.epoch()
                || current.command.slot() != input.slot())) {
            finishAll(current, "context_changed"); return false;
        }
        if (nextToken >= Long.MAX_VALUE - 2) return false;
        if (current == null) {
            Fences prior = readFences(player);
            if (!player.isShiftKeyDown() && prior != null && prior.request > 0) return false;
            if (CONTEXTS.size() >= MAX_CONTEXTS) return false;
            current = new Session(player, input); CONTEXTS.put(player.getUUID(), current);
        }
        Session session = current;
        session.command = input;
        session.processing = true;
        try {
            // Clearing is free even when an anchor, ground, or cooldown is no longer valid.
            if (player.isShiftKeyDown()) {
                if (session.handles.isEmpty()) finish(new Context(session, input), CLEARED, "cleared");
                else finishAll(session, "cleared");
                return true;
            }
            if (session.closing) return false;
            Selection selection = select(player);
            if (!selection.reason.isEmpty()) return registrationRefusal(session, input, selection.reason);
            Context selected = null;
            if (selection.medium != null) {
                for (Context handle : session.handles.values())
                    if (handle.needle == selection.medium.entity) { selected = handle; break; }
            } else if (session.handles.size() == 1) {
                // Compatibility is intentionally limited to one registration, and only
                // when the current ray did not select a different eligible medium.
                selected = session.handles.values().iterator().next();
            }
            if (selected != null) { selected.command = input; return recover(selected, selection.medium != null); }
            if (selection.medium == null)
                return registrationRefusal(session, input, session.handles.size() > 1 ? "explicit_aim_required" : "needle_missing");
            if (session.handles.size() >= MAX_HANDLES) return registrationRefusal(session, input, "registration_limit");
            Fences fences = readFences(player);
            if (fences != null && fences.request > 0)
                return session.handles.isEmpty() ? false : refuse(new Context(session, input), "request_fence");
            if (fences != null && fences.request == 0) session.paidFence = false;
            Context context = new Context(session, input);
            context.medium = selection.medium; context.needle = selection.medium.entity;
            context.needleEntity = context.needle.getId(); context.source = context.needle.position();
            session.handles.put(context.handleToken, context);
            return register(context, fences);
        } catch (RuntimeException failure) {
            LOG.warn("NEW323 selection refused after native validation failure", failure);
            return refuse(new Context(session, input), "validation_failed");
        } finally {
            session.processing = false;
            if (session.closing) finishAll(session, "context_changed");
            removeEmpty(session);
        }
    }

    /** Invalid registrations keep the original request throttle; existing handles survive the refusal. */
    private static boolean registrationRefusal(Session session, HighPowerInput.Input input, String reason) {
        var player = session.identity.owner(); Fences fences = readFences(player);
        if (fences != null && fences.request > 0)
            return session.handles.isEmpty() ? false : refuse(new Context(session, input), "request_fence");
        Context context = new Context(session, input);
        if (fences == null) return refuse(context, "storage_invalid");
        session.paidFence = false;
        writeFences(player, new Fences(RecoveryMarkRules.REQUEST_INTERVAL_TICKS, fences.cooldown));
        if (fences.cooldown > 0 || !session.identity.state().canUse(ID)) return refuse(context, "cooldown");
        return refuse(context, reason);
    }

    private static boolean register(Context context, Fences fences) {
        var player = context.identity.owner(); context.processing = true;
        try {
            if (fences == null) return refuse(context, "storage_invalid");
            writeFences(player, new Fences(RecoveryMarkRules.REQUEST_INTERVAL_TICKS, fences.cooldown));
            if (fences.cooldown > 0 || !context.identity.state().canUse(ID)) return refuse(context, "cooldown");
            String reason = eligibility(context, true);
            if (!reason.isEmpty()) return refuse(context, reason);
            Snapshot before = Snapshot.capture(context);
            var sight = RecoveryMarkWorld.sight(player, context.needle);
            if (!sight.valid()) return refuse(context, sight.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            var assessment = RecoveryMarkWorld.mark(player, context.needle);
            if (!assessment.valid()) return refuse(context, assessment.reason());
            if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            context.anchor = assessment.anchor();
            record(context, MARKED, ""); emit(context, MARKED, "");
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW323 mark refused after native validation failure", failure);
            return refuse(context, "validation_failed");
        } finally {
            context.processing = false;
            if (context.closing && !context.terminal) finish(context, CLEARED, "context_changed");
        }
    }

    /** Loaded-only, bounded actual-body ray selection. Never discovers or rebinds an unloaded mark. */
    private static Selection select(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(RecoveryMarkRules.RANGE));
        if (!finite(eye) || !finite(end)) return new Selection(null, "invalid_server_aim");
        List<Entity> candidates = new ArrayList<>();
        // Fetch at most one over the TOTAL-entity budget, then classify locally.
        // A rejecting predicate would keep scanning unbounded nonmatching entities.
        player.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), new AABB(eye, end).inflate(AIM_TOLERANCE + .25),
                entity -> true, candidates, MAX_SELECTION + 1);
        if (candidates.size() > MAX_SELECTION) return new Selection(null, "entity_scan_budget");
        Entity selected = null; double nearest = Double.POSITIVE_INFINITY;
        for (Entity entity : candidates) {
            if (!(entity instanceof DisplacedNeedleEntity || entity instanceof ItemEntity item
                    && item.getItem().is(AcademyCraft.NEEDLE.get()))) continue;
            if (player.position().distanceToSqr(entity.position()) > RecoveryMarkRules.RANGE * RecoveryMarkRules.RANGE) continue;
            var hit = entity.getBoundingBox().clip(eye, end);
            if (hit.isEmpty() || entity.getBoundingBox().contains(eye)) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < nearest || distance == nearest && selected != null && entity.getId() < selected.getId()) {
                nearest = distance; selected = entity;
            }
        }
        if (selected == null) return new Selection(null, "");
        RecoveryMedium medium = RecoveryMedium.bind(player, selected);
        return new Selection(medium, medium == null ? "needle_unavailable" : "");
    }

    private static boolean recover(Context context, boolean explicitAim) {
        var player = context.identity.owner();
        Fences paidFence = readFences(player);
        if (context.session.paidFence && paidFence != null && paidFence.request > 0)
            return refuse(new Context(context.session, context.session.command), "request_fence");
        if (paidFence != null && paidFence.request == 0) context.session.paidFence = false;
        context.processing = true;
        try {
            String reason = eligibility(context, true);
            if (!reason.isEmpty()) return refuse(context, reason);
            if (context.remaining <= 0) return refuse(context, "expired");
            Fences fences = readFences(player);
            if (fences == null) return refuse(context, "storage_invalid");
            if (fences.cooldown > 0 || !context.identity.state().canUse(ID)) return refuse(context, "cooldown");
            context.source = context.needle.position();
            Vec3 destination = destination(context);
            if (destination == null || context.source.equals(destination)) return refuse(context, "no_movement");
            if (!inventoryRoom(player, context.medium.stack())) return refuse(context, "inventory_full");
            Snapshot before = Snapshot.capture(context);
            if (explicitAim) {
                var sight = RecoveryMarkWorld.sight(player, context.needle);
                if (!sight.valid()) return refuse(context, sight.reason());
                if (!unchanged(context, before)) return refuse(context, "event_changed_state");
            }
            if (!guard(context, before)) return refuse(context, failure(context));
            var growth = HighPowerGrowth.values(player);
            double cost = RecoveryMarkRules.cpCost(growth.position());
            int cooldown = RecoveryMarkRules.cooldownTicks(growth.position());
            context.recovering = true;
            boolean relocated = context.medium.recover(destination, () -> {
                        if (!guard(context, before)) return false;
                        if (!context.eventPosted) {
                            context.eventPosted = true;
                            EntityTeleportEvent event = new EntityTeleportEvent(context.needle, destination.x, destination.y, destination.z);
                            NeoForge.EVENT_BUS.post(event);
                            if (event.isCanceled()) { context.failure = "event_cancelled"; return false; }
                            if (Double.compare(event.getTargetX(), destination.x) != 0
                                    || Double.compare(event.getTargetY(), destination.y) != 0
                                    || Double.compare(event.getTargetZ(), destination.z) != 0) {
                                context.failure = "event_redirected"; return false;
                            }
                        }
                        return guard(context, before);
                    }, () -> {
                        // This final resource commit deliberately has no world/event callback.
                        if (context.paid || !unchanged(context, before) || !eligibility(context, true).isEmpty()) return false;
                        Fences current = readFences(player);
                        if (current == null) { context.failure = "storage_invalid"; return false; }
                        if (current.cooldown > 0 || !context.identity.state().canUse(ID)) { context.failure = "cooldown"; return false; }
                        if (!HighPowerResources.trySpend(player, ID, cost, cooldown)) { context.failure = "insufficient_cp"; return false; }
                        context.paid = true; context.session.paidFence = true;
                        writeFences(player, new Fences(RecoveryMarkRules.REQUEST_INTERVAL_TICKS, cooldown));
                        // The paid receipt exists before the native position mutation. A throw
                        // remains explicitly unconfirmed and cannot replay this consumed input.
                        record(context, REFUSED, "transport_unconfirmed");
                        return true;
                    });
            context.moved = context.paid && context.needle.level() == context.identity.level()
                    && context.needle.position().equals(destination) && !context.source.equals(destination);
            if (!relocated || !context.moved) return refuse(context, context.paid ? "transport_unconfirmed" : failure(context));
            // Native section/tracking callbacks can change the owner during setPos.
            // Confirm the captured owner without rejecting our own legitimate cooldown,
            // rerunning world callbacks, undoing transport or refunding a paid attempt.
            if (context.closing || !controlEligible(context) || !before.matchesOwner(context))
                return refuse(context, "transport_unconfirmed");
            finish(context, RECOVERED, "");
            try {
                if (retainReceipt(context) && context.identity.owns(player)) { AbilityStorage.save(player); AcademyNetwork.sync(player); }
            } catch (RuntimeException failure) { LOG.warn("NEW323 recovery committed; final sync failed without rollback", failure); }
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW323 ended without retry or refund after native failure; paid={}", context.paid, failure);
            if (context.paid && context.needle != null && destination(context) != null)
                context.moved |= context.needle.level() == context.identity.level()
                        && context.needle.position().equals(destination(context)) && !context.source.equals(destination(context));
            return refuse(context, context.paid ? "transport_unconfirmed" : "validation_failed");
        } finally {
            context.processing = false; context.recovering = false;
            if (!context.terminal) finish(context, CLEARED, "context_changed");
        }
    }

    private static boolean guard(Context context, Snapshot before) {
        if (!unchanged(context, before)) { context.failure = "event_changed_state"; return false; }
        String reason = eligibility(context, true);
        if (!reason.isEmpty()) { context.failure = reason; return false; }
        if (!inventoryRoom(context.identity.owner(), before.medium)) { context.failure = "inventory_full"; return false; }
        var check = RecoveryMarkWorld.check(context.identity.owner(), context.needle, context.anchor);
        if (!check.valid()) { context.failure = check.reason(); return false; }
        if (!unchanged(context, before)) { context.failure = "event_changed_state"; return false; }
        return true;
    }
    private static String failure(Context context) { return context.failure.isEmpty() ? "validation_failed" : context.failure; }
    /** Includes the actual native partial-stack capacity; nothing is inserted here. */
    private static boolean inventoryRoom(ServerPlayer player, ItemStack medium) {
        return DisplacedNeedleEntity.isPlainNeedle(medium)
                && (player.getInventory().getSlotWithRemainingSpace(medium) >= 0 || player.getInventory().getFreeSlot() >= 0);
    }
    /** Holding or releasing never performs recovery. Only a fresh authenticated press can. */
    public static boolean release(ServerPlayer player, String wire) {
        return HighPowerBracerSession.authenticated(player, HighPowerInput.parse(wire));
    }
    public static boolean abort(ServerPlayer player, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(player, input)) return false;
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous == null || previous.owner != player || previous.epoch != input.epoch() || input.input() > previous.input)
            claimInput(player, input);
        Session session = owned(player);
        if (session == null || input.epoch() != session.command.epoch() || input.slot() != session.command.slot()
                || input.input() < session.command.input()) return false;
        // Paid native callbacks must retain the exact original paid-input receipt.
        if (session.handles.values().stream().noneMatch(handle -> handle.paid)) session.command = input;
        cancel(session); return true;
    }
    public static void cancelActive(ServerPlayer player) { Session session = owned(player); if (session != null) cancel(session); }
    private static void cancel(Session session) {
        session.closing = true;
        for (Context context : session.handles.values()) context.closing = true;
        if (!session.processing) finishAll(session, "context_changed");
    }
    public static void clear(ServerPlayer player) { cancelActive(player); }
    /** A stored mark does not reserve a movement or consumption channel. */
    public static boolean active(ServerPlayer player) { Session session = owned(player); return session != null && session.processing; }
    public static boolean marked(ServerPlayer player) { return markedCount(player) > 0; }
    public static int markedCount(ServerPlayer player) { return handles(player).size(); }
    public static int remainingTicks(ServerPlayer player) {
        var handles = handles(player); return handles.isEmpty() ? 0 : handles.getFirst().remainingTicks();
    }
    public static List<HandleVisual> handles(ServerPlayer player) {
        Session session = owned(player);
        if (session == null || session.closing) return List.of();
        List<HandleVisual> handles = new ArrayList<>();
        for (Context context : session.handles.values())
            if (context.anchor != null && !context.closing && !context.terminal && context.remaining > 0)
                handles.add(new HandleVisual(context.handleToken, context.needleEntity, needleUuid(context),
                        context.needle.position(), destination(context), context.remaining));
        return List.copyOf(handles);
    }
    public static Receipt receipt(ServerPlayer player) { return player == null ? null : RECEIPTS.get(player); }

    public static void tick(ServerPlayer player) {
        Session session = owned(player);
        if (session == null || session.processing) return;
        for (Context context : new ArrayList<>(session.handles.values())) {
            if (context.lastTick == now(player)) continue;
            context.lastTick = now(player);
            if (context.closing || !controlEligible(context)) { finish(context, CLEARED, "context_changed"); continue; }
            if (!needleIdentity(context)) { finish(context, CLEARED, "needle_changed"); continue; }
            // One observed physical tick, regardless of duplicate callbacks or game-time jumps.
            if (--context.remaining <= 0) { finish(context, CLEARED, "expired"); continue; }
            if (now(player) - context.lastVisual >= 10 || now(player) < context.lastVisual) {
                try { context.source = context.needle.position(); emit(context, MARKED, ""); }
                catch (RuntimeException failure) { finish(context, CLEARED, "visual_unavailable"); }
            }
        }
        removeEmpty(session);
    }
    private static boolean unchanged(Context context, Snapshot before) {
        return identity(context) && !context.closing && !context.session.closing && before.matches(context);
    }
    private static boolean sessionIdentity(Session session) {
        var player = session.identity.owner();
        return CONTEXTS.get(player.getUUID()) == session && session.identity.owns(player)
                && session.command.epoch() == session.identity.epoch() && mapped(player, session.command.slot())
                && session.identity.state().presets.current() == session.preset
                && session.identity.state().presets.revision() == session.revision;
    }
    private static boolean identity(Context context) {
        return sessionIdentity(context.session) && context.session.handles.get(context.handleToken) == context
                && context.command.epoch() == context.identity.epoch() && context.command.slot() == context.session.command.slot();
    }
    private static boolean needleIdentity(Context context) {
        return context.medium != null && context.needle == context.medium.entity && context.medium.valid();
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
    private static String eligibility(Context context, boolean requireNeedle) {
        var player = context.identity.owner();
        if (!controlEligible(context) || context.closing || context.session.closing) return "context_changed";
        if (!context.identity.state().canUse(ID)) return "cooldown";
        // Explicit post-flight boundary, not a claim to detect every enemy's combat AI.
        if (player.hurtTime != 0 || DisplacedProjection.active(player)) return "combat_active";
        if (requireNeedle && !needleIdentity(context)) return "needle_changed";
        if (requireNeedle && context.medium.busy(context.recovering)) return "needle_busy";
        return "";
    }
    private static boolean claimInput(ServerPlayer player, HighPowerInput.Input input) {
        Watermark previous = INPUTS.get(player.getUUID());
        if (previous != null && previous.owner == player && previous.epoch == input.epoch() && input.input() <= previous.input) return false;
        // Never evict a live replay fence to admit an unbounded stream of owners.
        if (previous == null && INPUTS.size() >= MAX_TERMINALS) return false;
        INPUTS.put(player.getUUID(), new Watermark(player, input.epoch(), input.input()));
        return true;
    }
    private static boolean refuse(Context context, String reason) { finish(context, REFUSED, reason); return false; }
    private static void finish(Context context, int phase, String reason) {
        if (context.terminal) return;
        context.terminal = true; context.closing = true;
        context.session.handles.remove(context.handleToken, context);
        record(context, phase, reason);
        try { if (retainReceipt(context)) emit(context, phase, reason); }
        catch (RuntimeException failure) { LOG.warn("NEW323 terminal retained despite visual delivery failure", failure); }
    }
    private static void record(Context context, int phase, String reason) {
        if (!retainReceipt(context)) return;
        var player = context.identity.owner();
        Receipt previous = RECEIPTS.get(player);
        if (previous != null && previous.token() == context.identity.token()
                && (previous.input() > context.command.input()
                || previous.input() == context.command.input() && previous.paid() && !context.paid)) return;
        if (!RECEIPTS.containsKey(player) && RECEIPTS.size() >= MAX_TERMINALS)
            RECEIPTS.remove(RECEIPTS.keySet().iterator().next());
        RECEIPTS.put(player, new Receipt(context.command.input(), context.identity.token(), context.paid,
                context.moved, phase, reason, context.source, destination(context), context.needleEntity, needleUuid(context)));
    }
    private static boolean retainReceipt(Context context) {
        var player = context.identity.owner();
        return !context.retired && !context.session.retired && !player.isRemoved() && !player.hasDisconnected()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    private static Session owned(ServerPlayer player) {
        if (!thread(player)) return null;
        Session session = CONTEXTS.get(player.getUUID());
        return session != null && session.identity.owner() == player ? session : null;
    }
    private static void finishAll(Session session, String reason) {
        session.closing = true;
        for (Context context : new ArrayList<>(session.handles.values())) {
            if (!context.paid) context.command = session.command;
            finish(context, CLEARED, reason);
        }
        removeEmpty(session);
    }
    private static void removeEmpty(Session session) {
        if (!session.processing && session.handles.isEmpty()) CONTEXTS.remove(session.identity.owner().getUUID(), session);
    }
    private static Fences readFences(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains(DATA)) return Fences.ZERO;
        if (!root.contains(DATA, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(DATA);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("request", Tag.TAG_INT) || !tag.contains("cooldown", Tag.TAG_INT)) return null;
        int request = tag.getInt("request"), cooldown = tag.getInt("cooldown");
        return request < 0 || request > RecoveryMarkRules.REQUEST_INTERVAL_TICKS || cooldown < 0 || cooldown > 40
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
        Session session = owned(player);
        if (session != null) {
            session.retired = true;
            for (Context context : session.handles.values()) context.retired = true;
            cancel(session);
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
        for (Session session : new ArrayList<>(CONTEXTS.values()))
            if (session.identity.owner().server == event.getServer()) tick(session.identity.owner());
    }
    @SubscribeEvent public static void needleLeft(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof DisplacedNeedleEntity || entity instanceof ItemEntity) invalidateEntity(entity);
    }
    /** Native toss/leave retirement; merge uses the atomic pair entry point below. */
    static void invalidateNativeItem(ItemEntity item) { invalidateEntity(item); }
    /** Both proofs are already gone; retire both handles before any terminal packet can be observed. */
    static void invalidateNativeItems(ItemEntity first, ItemEntity second) { invalidateEntities(first, second); }
    private static void invalidateEntity(Entity entity) { invalidateEntities(entity, null); }
    private static void invalidateEntities(Entity first, Entity second) {
        if (!(first.level() instanceof net.minecraft.server.level.ServerLevel world) || !world.getServer().isSameThread()) return;
        List<Context> affected = new ArrayList<>();
        List<Session> sessions = new ArrayList<>(CONTEXTS.values());
        for (Session session : sessions)
            for (Context context : session.handles.values()) if (context.needle == first || context.needle == second) {
                context.closing = true; context.medium.retire(); affected.add(context);
            }
        // Packet delivery may invoke foreign code. Neither participant retains
        // any registration authority when the first notification is delivered.
        for (Context context : affected)
            if (!context.processing) finish(context, CLEARED, "needle_changed");
        for (Session session : sessions) removeEmpty(session);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) remove(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer player) {
            remove(old);
            if (old.getPersistentData().contains(DATA)) player.getPersistentData().put(DATA, old.getPersistentData().get(DATA).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        CONTEXTS.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (session.identity.owner().server != event.getServer()) return false;
            session.retired = true; session.closing = true;
            for (Context context : session.handles.values()) { context.retired = true; context.closing = true; }
            return true;
        });
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer());
        RECEIPTS.keySet().removeIf(player -> player.server == event.getServer()); CLOCKS.remove(event.getServer());
    }

    private static Vec3 destination(Context context) { return context.anchor == null ? null : context.anchor.destination(); }
    private static UUID needleUuid(Context context) { return context.medium == null ? null : context.medium.uuid; }
    private static void emit(Context context, int phase, String reason) {
        if (context.session.sequence == Long.MAX_VALUE) return;
        var player = context.identity.owner();
        var packet = new Visual(player.getId(), player.getUUID(), context.session.command.input(), context.command.epoch(),
                context.identity.token(), ++context.session.sequence, phase, context.source, destination(context),
                context.needleEntity, needleUuid(context), phase == MARKED ? context.remaining : 0, phase != REFUSED, reason, handles(player));
        context.lastVisual = now(player);
        if (retainReceipt(context) && player.connection != null && player.serverLevel() == context.identity.level())
            PacketDistributor.sendToPlayer(player, packet);
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.server.getTickCount()); }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                && Math.abs(point.x) <= 30_000_032 && Math.abs(point.z) <= 30_000_032 && Math.abs(point.y) <= 4096;
    }
    public record HandleVisual(long token, int needleEntity, UUID needleUuid, Vec3 source, Vec3 anchor, int remainingTicks) {
        public HandleVisual {
            if (token <= 0 || needleEntity < 0 || needleUuid == null || needleUuid.equals(new UUID(0, 0))
                    || !finite(source) || !finite(anchor) || remainingTicks <= 0 || remainingTicks > RecoveryMarkRules.MARK_LIFE_TICKS)
                throw new IllegalArgumentException("Invalid NEW323 handle visual");
        }
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                         Vec3 source, Vec3 anchor, int needleEntity, UUID needleUuid, int remainingTicks,
                         boolean accepted, String reason, List<HandleVisual> handles) implements CustomPacketPayload {
        /** Legacy single-registration fixture constructor; the wire always carries the bounded list. */
        public Visual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase,
                      Vec3 source, Vec3 anchor, int needleEntity, UUID needleUuid, int remainingTicks, boolean accepted, String reason) {
            this(entity, owner, input, epoch, token, sequence, phase, source, anchor, needleEntity, needleUuid,
                    remainingTicks, accepted, reason, phase == MARKED
                            ? List.of(new HandleVisual(token, needleEntity, needleUuid, source, anchor, remainingTicks)) : List.of());
        }
        public Visual {
            boolean hasNeedle = needleEntity >= 0 && needleUuid != null && !needleUuid.equals(new UUID(0, 0));
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0 || token <= 0 || sequence <= 0
                    || phase < MARKED || phase > REFUSED || source != null && !finite(source) || anchor != null && !finite(anchor)
                    || needleEntity < -1 || (needleEntity >= 0) != (needleUuid != null)
                    || reason == null || reason.length() > 64 || !reason.matches("[a-z_]*")
                    || remainingTicks < 0 || remainingTicks > RecoveryMarkRules.MARK_LIFE_TICKS
                    || phase != MARKED && remainingTicks != 0 || phase == MARKED && remainingTicks == 0
                    || phase <= RECOVERED && (!accepted || source == null || anchor == null || !hasNeedle || !reason.isEmpty())
                    || phase == REFUSED && accepted || handles == null || handles.size() > MAX_HANDLES)
                throw new IllegalArgumentException("Invalid NEW323 visual");
            handles = List.copyOf(handles);
            Set<Long> tokens = new HashSet<>(); Set<UUID> uuids = new HashSet<>(); Set<Integer> entities = new HashSet<>();
            boolean selected = false;
            for (HandleVisual handle : handles) {
                if (!tokens.add(handle.token()) || !uuids.add(handle.needleUuid()) || !entities.add(handle.needleEntity()))
                    throw new IllegalArgumentException("Duplicate NEW323 visual handle");
                if (handle.needleUuid().equals(needleUuid)) {
                    if (phase == RECOVERED) throw new IllegalArgumentException("Recovered NEW323 handle remains registered");
                    selected = handle.needleEntity() == needleEntity && handle.source().equals(source)
                            && handle.anchor().equals(anchor) && handle.remainingTicks() == remainingTicks;
                }
            }
            if (phase == MARKED && !selected) throw new IllegalArgumentException("Missing NEW323 marked handle");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_recovery_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.epoch);
            buffer.writeLong(value.token); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.source); writeVec(buffer, value.anchor); buffer.writeInt(value.needleEntity);
            buffer.writeBoolean(value.needleUuid != null); if (value.needleUuid != null) buffer.writeUUID(value.needleUuid);
            buffer.writeVarInt(value.remainingTicks); buffer.writeBoolean(value.accepted); buffer.writeUtf(value.reason, 64);
            buffer.writeVarInt(value.handles.size());
            for (HandleVisual handle : value.handles) {
                buffer.writeLong(handle.token()); buffer.writeInt(handle.needleEntity()); buffer.writeUUID(handle.needleUuid());
                writeVec(buffer, handle.source()); writeVec(buffer, handle.anchor()); buffer.writeVarInt(handle.remainingTicks());
            }
        }, buffer -> new Visual(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), buffer.readLong(), buffer.readUnsignedByte(), readVec(buffer), readVec(buffer),
                buffer.readInt(), buffer.readBoolean() ? buffer.readUUID() : null, buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(64), readHandles(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static List<HandleVisual> readHandles(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_HANDLES) throw new IllegalArgumentException("Invalid NEW323 visual count");
        List<HandleVisual> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new HandleVisual(buffer.readLong(), buffer.readInt(), buffer.readUUID(),
                readVec(buffer), readVec(buffer), buffer.readVarInt()));
        return List.copyOf(result);
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeBoolean(value != null);
        if (value != null) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) : null;
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            // The bounded-list wire is incompatible with the .39 single-handle payload.
            event.registrar("2").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}

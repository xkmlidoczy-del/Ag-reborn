package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.TacticalCoordinateRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
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

/** NEW353 server-owned inert coordinates and one-use, explicitly reviewed teammate invitations.
 * No classic LocationTeleport call, companion sweep, chunk acquisition, recipient enrollment,
 * resource reservation, automatic acceptance, mastery, healing or velocity repair exists here.
 * Vanilla's absolute-position packet resets client velocity; server motion/fall remain untouched. */
@EventBusSubscriber(modid = "academy")
public final class TacticalCoordinateFormation {
    public static final String ID = "tactical_coordinate_formation";
    public static final String DATA_KEY = TacticalCoordinateStore.KEY;
    private static final int MAX_CONTEXTS = 64;
    private static final Set<String> ACTIONS = Set.of("select", "rotate_left", "rotate_right", "save", "delete",
            "confirm", "invite", "close", "open_review", "accept", "reject");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Invitation> OUTGOING = new IdentityHashMap<>(), INCOMING = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Object> COMMITS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.tactical_coordinate_formation");
    public static Consumer<ClientData> clientReceiver = ignored -> {};

    public record Template(int index, UUID id, long revision, String dimension, Vec3 feet, float yaw, float pitch) {}
    public record Receipt(long input, long token, boolean paid, boolean moved, String reason,
                          Vec3 source, Vec3 destination, UUID traveler) {}
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record EffectView(int amplifier, int duration, boolean ambient, boolean visible, boolean icon) {}
    /** Direct tag views neither initialize an absent recipient ledger nor normalize corrupt data. */
    private record StoredView(Tag reference, Tag copy) {
        static StoredView capture(ServerPlayer player, String key) {
            Tag tag = player.getPersistentData().get(key); return new StoredView(tag, tag == null ? null : tag.copy());
        }
        boolean matches(ServerPlayer player, String key) {
            return player.getPersistentData().get(key) == reference && java.util.Objects.equals(reference, copy);
        }
    }
    /** Only the synchronous commit boundary is frozen; waiting invitations permit ordinary recovery. */
    private record ParticipantState(float health, float absorption, int hurt, int invulnerability, int fire,
                                    int air, boolean noGravity, boolean flying, Object menu, Object gameMode,
                                    int selected, List<ItemStack> inventory, ItemStack carried,
                                    Map<Object, EffectView> effects, int experienceLevel, int totalExperience,
                                    float experienceProgress, StoredView combat, StoredView budget) {
        static ParticipantState capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                inventory.add(player.getInventory().getItem(slot).copy());
            return new ParticipantState(player.getHealth(), player.getAbsorptionAmount(), player.hurtTime,
                    player.invulnerableTime, player.getRemainingFireTicks(), player.getAirSupply(), player.isNoGravity(),
                    player.getAbilities().flying, player.containerMenu, player.gameMode.getGameModeForPlayer(),
                    player.getInventory().selected, List.copyOf(inventory), player.containerMenu.getCarried().copy(),
                    effects(player), player.experienceLevel, player.totalExperience, player.experienceProgress,
                    StoredView.capture(player, HighPowerCombat.DATA_KEY), StoredView.capture(player, HighPowerResources.DATA_KEY));
        }
        static Map<Object, EffectView> effects(ServerPlayer player) {
            Map<Object, EffectView> result = new HashMap<>();
            player.getActiveEffectsMap().forEach((key, value) -> result.put(key,
                    new EffectView(value.getAmplifier(), value.getDuration(), value.isAmbient(), value.isVisible(), value.showIcon())));
            return Map.copyOf(result);
        }
        boolean matches(ServerPlayer player) { return nonBudgetMatches(player) && budget.matches(player, HighPowerResources.DATA_KEY); }
        boolean nonBudgetMatches(ServerPlayer player) {
            if (Float.compare(health, player.getHealth()) != 0 || Float.compare(absorption, player.getAbsorptionAmount()) != 0
                    || hurt != player.hurtTime || invulnerability != player.invulnerableTime || fire != player.getRemainingFireTicks()
                    || air != player.getAirSupply() || noGravity != player.isNoGravity() || flying != player.getAbilities().flying
                    || menu != player.containerMenu || gameMode != player.gameMode.getGameModeForPlayer()
                    || selected != player.getInventory().selected || inventory.size() != player.getInventory().getContainerSize()
                    || !ItemStack.matches(carried, player.containerMenu.getCarried()) || !effects.equals(effects(player))
                    || experienceLevel != player.experienceLevel || totalExperience != player.totalExperience
                    || Float.compare(experienceProgress, player.experienceProgress) != 0
                    || !combat.matches(player, HighPowerCombat.DATA_KEY)) return false;
            for (int slot = 0; slot < inventory.size(); slot++)
                if (!ItemStack.matches(inventory.get(slot), player.getInventory().getItem(slot))) return false;
            return true;
        }
    }
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouching, ParticipantState participant) {
        static Source capture(ServerPlayer p) {
            return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(), p.getPose(),
                    p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown(), ParticipantState.capture(p));
        }
        boolean matches(ServerPlayer p) {
            return position.equals(p.position()) && eye.equals(p.getEyePosition()) && motion.equals(p.getDeltaMovement())
                    && body.equals(p.getBoundingBox()) && pose == p.getPose() && Float.compare(yaw, p.getYRot()) == 0
                    && Float.compare(pitch, p.getXRot()) == 0 && Float.compare(fall, p.fallDistance) == 0
                    && grounded == p.onGround() && crouching == p.isShiftKeyDown() && participant.matches(p);
        }
    }
    /** Exact recipient connection/entity/world identity, deliberately independent of ability storage. */
    private record TravelerIdentity(ServerPlayer player, Object connection, ServerLevel world, int entity, UUID uuid) {
        static TravelerIdentity capture(ServerPlayer p) {
            return new TravelerIdentity(p, p.connection, p.serverLevel(), p.getId(), p.getUUID());
        }
        boolean matches() {
            return live(player) && player.connection == connection && player.serverLevel() == world
                    && player.getId() == entity && player.getUUID().equals(uuid) && world.getEntity(entity) == player
                    && player.server.getPlayerList().getPlayer(uuid) == player;
        }
    }
    /** Frozen just before native callbacks, allowing ordinary regeneration while an invitation waits. */
    private record ResourceView(CompoundTag ledger, CompoundTag ledgerCopy, CompoundTag ability,
                                HighPowerGrowth.Values growth, long growthRevision) {
        static ResourceView capture(Session s) {
            ServerPlayer p = s.identity.owner(); CompoundTag ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            return new ResourceView(ledger, ledger.copy(), AbilityStorage.encode(s.identity.state()),
                    HighPowerGrowth.values(p), HighPowerActiveGrowth.revision(p));
        }
        boolean matches(Session s) {
            ServerPlayer p = s.identity.owner();
            return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == ledger && ledger.equals(ledgerCopy)
                    && ability.equals(AbilityStorage.encode(s.identity.state())) && growth.equals(HighPowerGrowth.values(p))
                    && growthRevision == HighPowerActiveGrowth.revision(p);
        }
    }
    private static final class Session {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input command;
        final int preset;
        final long presetRevision;
        final UUID nonce = UUID.randomUUID();
        int selected;
        float draftYaw;
        boolean dirty, processing, rendering, closing, terminal;
        long generation, sequence, actionFloor, lastVisual;
        CompoundTag lastData;
        Session(ServerPlayer p, HighPowerInput.Input command) {
            identity = new LegacySingleKeyServerIdentity(p, command.input(), ++nextToken, AbilityStorage.get(p));
            this.command = command; preset = identity.state().presets.current(); presetRevision = identity.state().presets.revision();
            var store = TacticalCoordinateStore.read(p); generation = store == null ? -1 : store.generation();
            Template t = store == null ? null : store.at(0); draftYaw = t == null ? normalizedYaw(p.getYRot()) : t.yaw();
            lastVisual = now(p);
        }
    }
    private static final class Invitation {
        final Session session;
        final TravelerIdentity recipient;
        final Team team;
        final Template template;
        final double cost;
        final int cooldown;
        final UUID nonce = UUID.randomUUID();
        final long token;
        final long generation;
        int remaining = TacticalCoordinateRules.INVITE_TICKS;
        long sequence, lastTick;
        boolean reviewed, processing, terminal;
        CompoundTag lastData;
        Invitation(Session s, ServerPlayer recipient, Template template, double cost, int cooldown) {
            session = s; this.recipient = TravelerIdentity.capture(recipient); team = s.identity.owner().getTeam();
            this.template = template; this.cost = cost; this.cooldown = cooldown; token = ++nextToken;
            generation = s.generation; lastTick = now(recipient);
        }
    }
    private TacticalCoordinateFormation() {}

    public static boolean press(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p) || !mapped(p, input.slot())
                || !TacticalCoordinateStore.finite(p.position()) || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return false;
        Session old = owned(p);
        if (old != null && (old.processing || old.rendering)) return false;
        Watermark before = INPUTS.get(p.getUUID());
        if (before != null && before.owner == p && before.epoch == input.epoch() && input.input() <= before.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        if (old != null) finish(old, "context_changed", false, null, null);
        if (nextToken == Long.MAX_VALUE || SESSIONS.size() >= MAX_CONTEXTS) return false;
        Session s = new Session(p, input); SESSIONS.put(p.getUUID(), s);
        if (!control(s)) { finish(s, "context_changed", false, null, null); return false; }
        return menu(s, "");
    }
    public static boolean release(ServerPlayer p, String wire) { return HighPowerBracerSession.authenticated(p, HighPowerInput.parse(wire)); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var before = INPUTS.get(p.getUUID());
        if (before == null || before.owner != p || before.epoch != input.epoch() || input.input() > before.input)
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Session s = owned(p);
        if (s == null || input.epoch() != s.command.epoch() || input.slot() != s.command.slot() || input.input() < s.command.input()) return false;
        clear(p); return true;
    }
    public static boolean request(ServerPlayer p, Request request) {
        if (!thread(p) || request == null || request.index < 0 || request.index >= TacticalCoordinateRules.MAX_TEMPLATES || !live(p) || request.actor != p.getId() || !request.owner.equals(p.getUUID())) return false;
        if (Set.of("open_review", "accept", "reject").contains(request.action)) {
            try { return recipientRequest(p, request); }
            catch (RuntimeException failure) {
                LOG.warn("NEW353 recipient request failed closed", failure);
                endInvitation(INCOMING.get(p), "validation_failed", false, null); return false;
            }
        }
        Session s = owned(p);
        if (s == null || s.processing || s.rendering || !s.command.wire().equals(request.wire) || s.identity.token() != request.token
                || !s.nonce.equals(request.nonce) || !HighPowerBracerSession.authenticated(p, HighPowerInput.parse(request.wire))) return false;
        if (request.action.equals("close")) { clear(p); return true; }
        if (s.sequence == Long.MAX_VALUE || s.actionFloor <= 0 || s.actionFloor > s.sequence) {
            finish(s, "sequence_exhausted", false, null, null); return false;
        }
        // Heartbeats publish fresher previews without revoking an in-flight user action.
        // Every action response advances the floor, so a pre-action snapshot cannot replay
        // even a draft rotation or selection whose persistent template revision is unchanged.
        if (request.sequence < s.actionFloor || request.sequence > s.sequence) return refuse(s, "stale_request");
        if (!control(s)) { finish(s, "context_changed", false, null, null); return false; }
        s.processing = true;
        try {
            var store = TacticalCoordinateStore.read(p);
            if (store == null) return refuse(s, "storage_invalid");
            if (s.generation != store.generation()) { finish(s, "template_changed", false, null, null); return false; }
            int index = request.action.equals("select") ? request.index : s.selected;
            Template selected = store.at(index);
            if (request.index != index || !binding(selected, request.templateId, request.revision)) return refuse(s, "template_changed");
            switch (request.action) {
                case "select" -> {
                    cancelOutgoing(s, "session_changed"); s.selected = index;
                    s.draftYaw = selected == null ? normalizedYaw(p.getYRot()) : selected.yaw(); s.dirty = false;
                    return menu(s, "");
                }
                case "rotate_left", "rotate_right" -> {
                    if (selected == null) return refuse(s, "template_missing");
                    cancelOutgoing(s, "session_changed");
                    s.draftYaw = normalizedYaw(s.draftYaw + (request.action.equals("rotate_left") ? -90 : 90));
                    s.dirty = Float.compare(s.draftYaw, selected.yaw()) != 0; return menu(s, "");
                }
                case "save" -> { return save(s, store, selected); }
                case "delete" -> { return delete(s, store, selected); }
                case "confirm", "invite" -> {
                    if (selected == null) return refuse(s, "template_missing");
                    if (s.dirty) return refuse(s, "save_required");
                    if (store.request() > 0) return refuse(s, "request_cooldown");
                    if (!TacticalCoordinateStore.write(p, store, store.fences(TacticalCoordinateRules.REQUEST_INTERVAL_TICKS, store.cooldown())))
                        return refuse(s, "storage_invalid");
                    if (request.action.equals("confirm")) {
                        cancelOutgoing(s, "session_changed");
                        var growth = HighPowerGrowth.values(p);
                        return transport(s, null, p, selected, TacticalCoordinateRules.cpCost(growth.position()),
                                TacticalCoordinateRules.cooldownTicks(growth.position()));
                    }
                    return invite(s, selected, request.recipient);
                }
                default -> { return false; }
            }
        } catch (RuntimeException failure) {
            LOG.warn("NEW353 request refused after validation failure", failure);
            return refuse(s, "validation_failed");
        } finally {
            s.processing = false;
            if (s.closing && !s.terminal) finish(s, "context_changed", false, null, null);
        }
    }
    private static boolean save(Session s, TacticalCoordinateStore.State store, Template old) {
        ServerPlayer p = s.identity.owner();
        if (store.generation() >= Long.MAX_VALUE - 1) return refuse(s, "revision_exhausted");
        Source before = Source.capture(p); ResourceView resources = ResourceView.capture(s);
        Template next;
        if (old == null) {
            String reason = travelerRefusal(p);
            if (!reason.isEmpty()) return refuse(s, reason);
            var assessment = SafeLandingProbeWorld.tacticalCapture(p);
            if (!assessment.valid()) return refuse(s, assessment.reason());
            if (!control(s) || !before.matches(p) || !resources.matches(s) || !store.equals(TacticalCoordinateStore.read(p)))
                return refuse(s, "event_changed_state");
            next = new Template(s.selected, UUID.randomUUID(), store.generation() + 1,
                    assessment.template().world().dimension().location().toString(), assessment.template().feet(),
                    normalizedYaw(before.yaw), before.pitch);
        } else next = new Template(old.index(), old.id(), store.generation() + 1, old.dimension(), old.feet(), s.draftYaw, old.pitch());
        cancelOutgoing(s, "template_changed");
        var rows = new ArrayList<>(store.templates()); rows.removeIf(row -> row.index() == s.selected); rows.add(next);
        rows.sort(java.util.Comparator.comparingInt(Template::index));
        var replacement = new TacticalCoordinateStore.State(store.generation() + 1, store.request(), store.cooldown(), rows);
        if (!TacticalCoordinateStore.write(p, store, replacement)) return refuse(s, "storage_invalid");
        s.generation = replacement.generation(); s.draftYaw = next.yaw(); s.dirty = false; return menu(s, "saved");
    }
    private static boolean delete(Session s, TacticalCoordinateStore.State store, Template old) {
        if (old == null) return refuse(s, "template_missing");
        if (store.generation() >= Long.MAX_VALUE - 1) return refuse(s, "revision_exhausted");
        cancelOutgoing(s, "template_changed");
        var rows = new ArrayList<>(store.templates()); rows.removeIf(row -> row.index() == s.selected);
        var replacement = new TacticalCoordinateStore.State(store.generation() + 1, store.request(), store.cooldown(), rows);
        if (!TacticalCoordinateStore.write(s.identity.owner(), store, replacement)) return refuse(s, "storage_invalid");
        s.generation = replacement.generation(); s.draftYaw = normalizedYaw(s.identity.owner().getYRot()); s.dirty = false;
        return menu(s, "deleted");
    }
    private static boolean invite(Session s, Template template, UUID recipientId) {
        ServerPlayer caster = s.identity.owner();
        if (OUTGOING.containsKey(caster) || nextToken == Long.MAX_VALUE || OUTGOING.size() >= MAX_CONTEXTS)
            return refuse(s, "invitation_pending");
        Set<UUID> allowed = advertised(s);
        ServerPlayer recipient = recipientId == null ? null : caster.server.getPlayerList().getPlayer(recipientId);
        if (recipient == null || !allowed.contains(recipientId) || recipient == caster || INCOMING.containsKey(recipient)
                || !sameTeam(caster, recipient) || recipient.serverLevel() != caster.serverLevel() || !live(recipient))
            return refuse(s, "recipient_unavailable");
        var growth = HighPowerGrowth.values(caster); double cost = TacticalCoordinateRules.cpCost(growth.position());
        int cooldown = TacticalCoordinateRules.cooldownTicks(growth.position());
        String reason = resources(s, recipient, cost);
        if (reason.isEmpty()) reason = travelerRefusal(recipient);
        if (reason.isEmpty() && (!within(caster, template) || !within(recipient, template))) reason = "out_of_range";
        if (!reason.isEmpty()) return refuse(s, reason);
        Invitation i = new Invitation(s, recipient, template, cost, cooldown);
        OUTGOING.put(caster, i); INCOMING.put(recipient, i);
        try { emitInvitation(i, "invite", "", false, null); }
        catch (RuntimeException failure) { endInvitation(i, "validation_failed", false, null); return refuse(s, "validation_failed"); }
        return menu(s, "invited");
    }
    private static boolean recipientRequest(ServerPlayer p, Request request) {
        Invitation i = INCOMING.get(p);
        if (i == null || i.processing || i.terminal || !request.wire.isEmpty() || request.token != i.token
                || !request.nonce.equals(i.nonce) || request.sequence != i.sequence
                || request.index != i.template.index() || !binding(i.template, request.templateId, request.revision)
                || !i.recipient.matches()) return false;
        if (request.action.equals("reject")) { endInvitation(i, "rejected", false, null); return true; }
        if (i.sequence == Long.MAX_VALUE) { endInvitation(i, "sequence_exhausted", false, null); return false; }
        if (!invitationValid(i)) { endInvitation(i, "context_changed", false, null); return false; }
        if (request.action.equals("open_review")) {
            i.reviewed = true; emitInvitation(i, "review", "", false, null); return true;
        }
        if (!request.action.equals("accept") || !i.reviewed || i.session.processing) return false;
        i.processing = true; i.session.processing = true;
        try { return transport(i.session, i, p, i.template, i.cost, i.cooldown); }
        finally { i.processing = false; i.session.processing = false; }
    }
    private static boolean transport(Session s, Invitation invitation, ServerPlayer traveler, Template template, double cost, int cooldown) {
        ServerPlayer caster = s.identity.owner(); Object transaction = new Object();
        if (COMMITS.containsKey(caster) || COMMITS.containsKey(traveler)) return refuse(s, "movement_conflict");
        COMMITS.put(caster, transaction); COMMITS.put(traveler, transaction);
        boolean paid = false, moved = false; String result = "validation_failed";
        Source source = null, casterSource;
        try {
            source = Source.capture(traveler); casterSource = Source.capture(caster);
            TravelerIdentity travelerIdentity = TravelerIdentity.capture(traveler);
            if (!control(s) || invitation != null && !invitationValid(invitation)) { result = "context_changed"; return false; }
            String reason = resources(s, traveler, cost);
            if (reason.isEmpty()) reason = travelerRefusal(caster);
            if (reason.isEmpty() && traveler != caster) reason = travelerRefusal(traveler);
            if (reason.isEmpty() && (!within(caster, template) || !within(traveler, template))) reason = "out_of_range";
            if (!reason.isEmpty()) { result = reason; return false; }
            var store = TacticalCoordinateStore.read(caster);
            if (store == null || !template.equals(store.at(template.index())) || s.generation != store.generation()) {
                result = "template_changed"; return false;
            }
            ResourceView resourceView = ResourceView.capture(s);
            var assessment = SafeLandingProbeWorld.tacticalDestination(traveler, s.identity.level(), template.feet());
            if (!assessment.valid()) { result = assessment.reason(); return false; }
            var candidate = assessment.candidate();
            if (!unchanged(s, invitation, travelerIdentity, source, casterSource, resourceView, store, transaction)) {
                result = "event_changed_state"; return false;
            }
            EntityTeleportEvent event = new EntityTeleportEvent(traveler, template.feet().x, template.feet().y, template.feet().z);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) { result = "event_cancelled"; return false; }
            if (Double.compare(event.getTargetX(), template.feet().x) != 0
                    || Double.compare(event.getTargetY(), template.feet().y) != 0
                    || Double.compare(event.getTargetZ(), template.feet().z) != 0) { result = "event_redirected"; return false; }
            if (!unchanged(s, invitation, travelerIdentity, source, casterSource, resourceView, store, transaction)) {
                result = "event_changed_state"; return false;
            }
            reason = resources(s, traveler, cost);
            if (reason.isEmpty()) reason = travelerRefusal(caster);
            if (reason.isEmpty() && traveler != caster) reason = travelerRefusal(traveler);
            if (reason.isEmpty() && (!within(caster, template) || !within(traveler, template))) reason = "out_of_range";
            if (!reason.isEmpty()) { result = reason; return false; }
            var revalidated = SafeLandingProbeWorld.tacticalRevalidate(traveler, candidate);
            if (!revalidated.valid()) { result = revalidated.reason(); return false; }
            // Recipient route and both 64 m distances are checked after every native callback.
            if (!unchanged(s, invitation, travelerIdentity, source, casterSource, resourceView, store, transaction)
                    || !within(caster, template) || !within(traveler, template)) { result = "event_changed_state"; return false; }
            reason = resources(s, traveler, cost);
            if (!reason.isEmpty()) { result = reason; return false; }
            if (!HighPowerResources.trySpend(caster, ID, cost, cooldown)) { result = "insufficient_cp"; return false; }
            paid = true;
            // There are no callbacks between debit, the two physical movement fences, and native transport.
            if (!TacticalCoordinateStore.write(caster, store, store.fences(TacticalCoordinateRules.REQUEST_INTERVAL_TICKS, cooldown))) {
                result = "commit_unconfirmed"; return false;
            }
            SafeLandingProbe.commitSharedMovement(caster);
            if (traveler != caster) SafeLandingProbe.commitSharedMovement(traveler);
            traveler.connection.teleport(template.feet().x, template.feet().y, template.feet().z,
                    template.yaw(), template.pitch(), Set.of());
            moved = traveler.serverLevel() == s.identity.level() && traveler.position().equals(template.feet());
            if (!moved || !traveler.getDeltaMovement().equals(source.motion)
                    || Float.compare(traveler.fallDistance, source.fall) != 0 || traveler.onGround() != source.grounded
                    || Float.compare(traveler.getYRot(), template.yaw()) != 0 || Float.compare(traveler.getXRot(), template.pitch()) != 0
                    || traveler.getPose() != source.pose || traveler.isShiftKeyDown() != source.crouching
                    || !source.participant.nonBudgetMatches(traveler) || !casterSource.participant.nonBudgetMatches(caster)
                    || traveler != caster && !source.participant.budget.matches(traveler, HighPowerResources.DATA_KEY)
                    || s.closing || !identity(s) || !travelerIdentity.matches()) { result = "commit_unconfirmed"; return false; }
            result = ""; return true;
        } catch (RuntimeException failure) {
            moved |= paid && traveler.serverLevel() == s.identity.level() && traveler.position().equals(template.feet());
            result = paid ? "transport_unconfirmed" : "validation_failed";
            LOG.warn("NEW353 terminal native failure; paid={}, no retry or refund", paid, failure); return false;
        } catch (Error failure) {
            moved |= paid && traveler.serverLevel() == s.identity.level() && traveler.position().equals(template.feet());
            result = paid ? "transport_unconfirmed" : "validation_failed"; throw failure;
        } finally {
            COMMITS.remove(caster, transaction); COMMITS.remove(traveler, transaction);
            Vec3 receiptSource = source == null ? traveler.position() : source.position;
            if (live(caster)) RECEIPTS.put(caster, new Receipt(s.command.input(), s.identity.token(), paid, moved, result, receiptSource, template.feet(), traveler.getUUID()));
            if (traveler != caster && live(traveler)) RECEIPTS.put(traveler,
                    new Receipt(0, invitation == null ? s.identity.token() : invitation.token, paid, moved, result, receiptSource, template.feet(), traveler.getUUID()));
            if (invitation != null) endInvitation(invitation, result, moved, receiptSource);
            String ownerReason = traveler != caster && moved && result.isEmpty() ? "recipient_moved" : result;
            finish(s, ownerReason, traveler == caster && moved, receiptSource, template);
            if (paid && live(caster)) {
                try { AbilityStorage.save(caster); AcademyNetwork.sync(caster); }
                catch (RuntimeException failure) { LOG.warn("NEW353 final sync failed; payment remains terminal", failure); }
            }
        }
    }
    private static boolean unchanged(Session s, Invitation invitation, TravelerIdentity traveler, Source source,
                                     Source casterSource, ResourceView resources, TacticalCoordinateStore.State store, Object transaction) {
        return COMMITS.get(s.identity.owner()) == transaction && COMMITS.get(traveler.player) == transaction
                && control(s) && !s.closing && traveler.matches() && source.matches(traveler.player)
                && casterSource.matches(s.identity.owner()) && resources.matches(s)
                && store.equals(TacticalCoordinateStore.read(s.identity.owner()))
                && (invitation == null || invitationValid(invitation));
    }
    private static String resources(Session s, ServerPlayer traveler, double cost) {
        ServerPlayer caster = s.identity.owner(); var store = TacticalCoordinateStore.read(caster);
        int casterFence = SafeLandingProbe.sharedMovementRemaining(caster), recipientFence = SafeLandingProbe.sharedMovementRemaining(traveler);
        CompoundTag ledger = caster.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        if (store == null || casterFence < 0 || recipientFence < 0 || !ledger.contains("cp", Tag.TAG_DOUBLE)
                || !Double.isFinite(ledger.getDouble("cp")) || ledger.getDouble("cp") < 0) return "storage_invalid";
        if (HighPowerResources.hasReservation(caster)) return "resource_busy";
        if (store.cooldown() > 0 || casterFence > 0 || recipientFence > 0 || !s.identity.state().canUse(ID)
                || caster.serverLevel().getGameTime() < ledger.getLong("next_" + ID)) return "cooldown";
        return !Double.isFinite(cost) || cost <= 0 || HighPowerResources.availableCp(caster) < cost ? "insufficient_cp" : "";
    }
    private static boolean identity(Session s) {
        ServerPlayer p = s.identity.owner();
        return SESSIONS.get(p.getUUID()) == s && s.identity.owns(p) && mapped(p, s.command.slot())
                && s.identity.state().presets.current() == s.preset && s.identity.state().presets.revision() == s.presetRevision;
    }
    private static boolean control(Session s) {
        ServerPlayer p = s.identity.owner(); var state = s.identity.state();
        return !s.terminal && !s.closing && identity(s) && live(p) && !p.isSpectator()
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && p.containerMenu == p.inventoryMenu
                && state.activated && state.overloadFine && !state.interfering && !state.consumptionInProgress()
                && (committing(p) || !AbilityConsumption.busy(p)) && state.learned(ID) && state.learned(SpatialRelay.ID) && state.level == 5
                && SkillCatalog.enabled(HighPowerCategories.TELEPORTER, ID) && HighPowerResources.ready(p)
                && HighPowerResources.effectiveLevel(p) == 5 && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean mapped(ServerPlayer p, int slot) {
        return thread(p) && HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
                && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));
    }
    /** Recipient path never obtains or initializes an ability ledger. */
    private static String travelerRefusal(ServerPlayer p) {
        if (!live(p) || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable
                || p.containerMenu != p.inventoryMenu) return "source_unavailable";
        var state = AbilityStorage.peek(p);
        if (state != null && (state.interfering || state.consumptionInProgress())) return "movement_conflict";
        if (!committing(p) && AbilityConsumption.busy(p)) return "movement_conflict";
        if (!p.onGround() || !Float.isFinite(p.fallDistance) || p.fallDistance != 0) return "source_airborne";
        if (p.noPhysics || p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying
                || p.isInWater() || p.isInLava() || p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING
                || !TacticalCoordinateStore.finite(p.position()) || !TacticalCoordinateStore.finite(p.getEyePosition())
                || !TacticalCoordinateStore.finite(p.getDeltaMovement()) || !Float.isFinite(p.getYRot()) || !TacticalCoordinateRules.validPitch(p.getXRot()))
            return "source_motion_or_pose_unsupported";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION) || p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                || CornerExchange.active(p) || EmergencyReturn.active(p) || ReturnMark.active(p) || CrossSubstitution.isTransporting(p)
                || SafeLandingProbe.active(p) || PhaseSidestep.active(p) || (BeamStep.active(p)||WindPressureLift.active(p)) || MagneticGroundStep.active(p)
                || PolarityTether.active(p) || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.LocationTeleport.active(p) || cn.academy.port.skill.Flashing.active(p)) return "movement_conflict";
        return "";
    }
    private static boolean invitationValid(Invitation i) {
        ServerPlayer caster = i.session.identity.owner(), recipient = i.recipient.player;
        var store = TacticalCoordinateStore.read(caster);
        return !i.terminal && i.remaining > 0 && OUTGOING.get(caster) == i && INCOMING.get(recipient) == i
                && control(i.session) && i.recipient.matches() && caster.getTeam() == i.team && i.team != null
                && recipient.getTeam() == i.team && recipient.serverLevel() == i.session.identity.level()
                && store != null && store.generation() == i.generation && i.session.generation == i.generation
                && i.template.equals(store.at(i.template.index())) && !i.session.dirty
                && i.session.selected == i.template.index();
    }
    private static boolean sameTeam(ServerPlayer a, ServerPlayer b) { return a.getTeam() != null && a.getTeam() == b.getTeam(); }
    private static boolean within(ServerPlayer player, Template template) {
        return player.serverLevel().dimension().location().toString().equals(template.dimension())
                && TacticalCoordinateStore.finite(player.position())
                && player.position().distanceToSqr(template.feet()) <= TacticalCoordinateRules.RANGE * TacticalCoordinateRules.RANGE;
    }
    private static boolean binding(Template t, UUID id, long revision) {
        return t == null ? id == null && revision == 0 : t.id().equals(id) && t.revision() == revision;
    }
    private static float normalizedYaw(float yaw) { return TacticalCoordinateRules.normalizeYaw(yaw); }
    private static boolean refuse(Session s, String reason) { menu(s, reason); return false; }
    private static boolean menu(Session s, String reason) { return menu(s, reason, false); }
    private static boolean menu(Session s, String reason, boolean heartbeat) {
        if (s.rendering) return false;
        s.rendering = true;
        try {
            boolean published = buildMenu(s, reason);
            if (published && !heartbeat) s.actionFloor = s.sequence;
            return published;
        }
        catch (RuntimeException failure) {
            LOG.warn("NEW353 menu assessment failed closed", failure);
            finish(s, "validation_failed", false, null, null); return false;
        } finally { s.rendering = false; }
    }
    private static boolean buildMenu(Session s, String reason) {
        if (s.terminal || s.closing || s.sequence == Long.MAX_VALUE) return false;
        ServerPlayer p = s.identity.owner(); var store = TacticalCoordinateStore.read(p);
        CompoundTag data = base(p, s.command.input(), s.command.epoch(), s.identity.token(), s.nonce, ++s.sequence, "menu", reason);
        data.putInt("selected", s.selected); data.putLong("generation", store == null ? -1 : store.generation());
        data.putBoolean("storage_valid", store != null); data.putFloat("draft_yaw", s.draftYaw); data.putBoolean("dirty", s.dirty);
        data.putString("dimension", p.serverLevel().dimension().location().toString());
        ListTag rows = new ListTag(); boolean canConfirm = false; String confirmReason = store == null ? "storage_invalid" : "template_missing";
        double cost = 0; int cooldown = 0;
        if (control(s)) {
            var growth = HighPowerGrowth.values(p); cost = TacticalCoordinateRules.cpCost(growth.position());
            cooldown = TacticalCoordinateRules.cooldownTicks(growth.position());
        }
        data.putDouble("cost", cost); data.putInt("cooldown", cooldown); data.putInt("remaining", 0);
        if (store != null) for (Template template : store.templates()) {
            CompoundTag row = TacticalCoordinateStore.row(template); String invalid = "";
            if (!control(s)) invalid = "context_changed";
            else if (!within(p, template)) invalid = "out_of_range";
            else invalid = travelerRefusal(p);
            if (invalid.isEmpty()) {
                Source before = Source.capture(p);
                var assessment = SafeLandingProbeWorld.tacticalDestination(p, s.identity.level(), template.feet());
                invalid = assessment.valid() ? "" : assessment.reason();
                if (!before.matches(p) || !control(s) || !store.equals(TacticalCoordinateStore.read(p))) invalid = "event_changed_state";
            }
            row.putBoolean("valid", invalid.isEmpty()); row.putString("reason", invalid); rows.add(row);
            if (template.index() == s.selected) {
                confirmReason = !invalid.isEmpty() ? invalid : s.dirty ? "save_required" : !control(s) ? "context_changed"
                        : store.request() > 0 ? "request_cooldown" : resources(s, p, cost);
                canConfirm = confirmReason.isEmpty();
            }
        }
        data.put("templates", rows); data.putBoolean("can_confirm", canConfirm); data.putString("confirm_reason", confirmReason);
        ListTag recipients = new ListTag();
        if (p.getTeam() != null) for (ServerPlayer candidate : p.server.getPlayerList().getPlayers()) {
            if (candidate == p || !live(candidate) || !sameTeam(p, candidate) || candidate.serverLevel() != p.serverLevel()
                    || candidate.isSpectator() || candidate.getAbilities().instabuild || candidate.getAbilities().invulnerable) continue;
            CompoundTag row = new CompoundTag(); row.putUUID("uuid", candidate.getUUID());
            row.putString("name", candidate.getGameProfile().getName()); recipients.add(row);
            if (recipients.size() == 32) break;
        }
        data.put("recipients", recipients); data.putBoolean("invitation_pending", OUTGOING.containsKey(p));
        if (s.terminal || s.closing) return false;
        s.lastData = data.copy(); s.lastVisual = now(p); send(p, data); return true;
    }
    private static Set<UUID> advertised(Session s) {
        var ids = new java.util.HashSet<UUID>();
        if (s.lastData != null) {
            ListTag rows = s.lastData.getList("recipients", Tag.TAG_COMPOUND);
            for (int index = 0; index < rows.size(); index++) ids.add(rows.getCompound(index).getUUID("uuid"));
        }
        return Set.copyOf(ids);
    }
    private static CompoundTag base(ServerPlayer viewer, long input, long epoch, long token, UUID nonce,
                                    long sequence, String phase, String reason) {
        CompoundTag data = new CompoundTag(); data.putInt("actor", viewer.getId()); data.putUUID("owner", viewer.getUUID());
        data.putLong("input", input); data.putLong("epoch", epoch); data.putLong("token", token); data.putUUID("nonce", nonce);
        data.putLong("sequence", sequence); data.putString("phase", phase); data.putString("reason", reason);
        data.putBoolean("moved", false); return data;
    }
    private static void emitInvitation(Invitation i, String phase, String reason, boolean moved, Vec3 source) {
        if (i.sequence == Long.MAX_VALUE) return;
        ServerPlayer recipient = i.recipient.player, caster = i.session.identity.owner(); Template template = i.template;
        CompoundTag data = base(recipient, 0, 0, i.token, i.nonce, ++i.sequence, phase, reason);
        data.putInt("selected", template.index()); data.putUUID("caster", caster.getUUID());
        data.putString("caster_name", caster.getGameProfile().getName()); data.putUUID("template", template.id());
        data.putLong("revision", template.revision()); data.putString("dimension", template.dimension());
        data.putDouble("x", template.feet().x); data.putDouble("y", template.feet().y); data.putDouble("z", template.feet().z);
        data.putFloat("yaw", template.yaw()); data.putFloat("pitch", template.pitch());
        data.putDouble("cost", i.cost); data.putInt("cooldown", i.cooldown); data.putInt("remaining", i.terminal ? 0 : i.remaining);
        data.putBoolean("moved", moved); data.putUUID("moved_owner", recipient.getUUID()); data.putInt("moved_actor", recipient.getId());
        if (source != null) { data.putDouble("from_x", source.x); data.putDouble("from_y", source.y); data.putDouble("from_z", source.z); }
        i.lastData = data.copy(); send(recipient, data);
    }
    private static void endInvitation(Invitation i, String reason, boolean moved, Vec3 source) {
        if (i == null || i.terminal) return;
        i.terminal = true; OUTGOING.remove(i.session.identity.owner(), i); INCOMING.remove(i.recipient.player, i);
        try { emitInvitation(i, moved && reason.isEmpty() ? "success" : "end", reason, moved, source); }
        catch (RuntimeException failure) { LOG.warn("NEW353 invitation ended despite delivery failure", failure); }
    }
    private static void cancelOutgoing(Session s, String reason) { endInvitation(OUTGOING.get(s.identity.owner()), reason, false, null); }
    private static void finish(Session s, String reason, boolean moved, Vec3 source, Template destination) {
        if (s.terminal) return;
        s.terminal = true; s.closing = true; SESSIONS.remove(s.identity.owner().getUUID(), s); cancelOutgoing(s, reason);
        if (s.sequence == Long.MAX_VALUE) return;
        ServerPlayer player = s.identity.owner();
        CompoundTag data = base(player, s.command.input(), s.command.epoch(), s.identity.token(), s.nonce,
                ++s.sequence, moved && reason.isEmpty() ? "success" : "end", reason);
        data.putInt("selected", s.selected); data.putString("dimension", s.identity.level().dimension().location().toString());
        data.putBoolean("moved", moved); data.putInt("remaining", 0);
        data.putUUID("moved_owner", player.getUUID()); data.putInt("moved_actor", player.getId());
        if (source != null) { data.putDouble("from_x", source.x); data.putDouble("from_y", source.y); data.putDouble("from_z", source.z); }
        if (destination != null) {
            data.putDouble("x", destination.feet().x); data.putDouble("y", destination.feet().y); data.putDouble("z", destination.feet().z);
            data.putFloat("yaw", destination.yaw()); data.putFloat("pitch", destination.pitch());
        }
        s.lastData = data.copy();
        try { send(player, data); } catch (RuntimeException failure) { LOG.warn("NEW353 menu ended despite delivery failure", failure); }
    }
    private static void send(ServerPlayer player, CompoundTag data) {
        if (live(player)) PacketDistributor.sendToPlayer(player, new ClientData(data));
    }
    public static CompoundTag snapshot(ServerPlayer p) {
        Session s = owned(p); return s == null || s.lastData == null ? null : s.lastData.copy();
    }
    public static CompoundTag invitation(ServerPlayer p) {
        Invitation i = INCOMING.get(p); return i == null || i.lastData == null ? null : i.lastData.copy();
    }
    public static List<Template> templates(ServerPlayer p) {
        if (!thread(p)) return null; var store = TacticalCoordinateStore.read(p); return store == null ? null : store.templates();
    }
    public static Receipt receipt(ServerPlayer p) { return RECEIPTS.get(p); }
    public static boolean committing(ServerPlayer p) { return p != null && COMMITS.containsKey(p); }
    public static boolean active(ServerPlayer p) { return owned(p) != null || committing(p); }
    public static void clear(ServerPlayer p) {
        if (!thread(p)) return;
        Session s = owned(p);
        if (s != null) { s.closing = true; cancelOutgoing(s, "context_changed"); if (!s.processing) finish(s, "context_changed", false, null, null); }
        endInvitation(INCOMING.get(p), "context_changed", false, null);
    }
    public static void cancelActive(ServerPlayer p) { clear(p); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return; clear(p);
        INPUTS.computeIfPresent(p.getUUID(), (id, value) -> value.owner == p ? null : value); RECEIPTS.remove(p);
    }
    public static void tick(ServerPlayer p) {
        Session s = owned(p);
        if (s == null || s.processing) return;
        var store = TacticalCoordinateStore.read(p);
        if (!control(s) || store == null && s.generation >= 0 || store != null && s.generation != store.generation()) {
            finish(s, store == null ? "storage_invalid" : "context_changed", false, null, null); return;
        }
        if (now(p) - s.lastVisual >= 10 || now(p) < s.lastVisual) {
            s.processing = true;
            try { menu(s, "", true); }
            catch (RuntimeException failure) { finish(s, "validation_failed", false, null, null); }
            finally { s.processing = false; if (s.closing && !s.terminal) finish(s, "context_changed", false, null, null); }
        }
    }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre event) {
        Integer before = CLOCKS.put(event.getServer(), event.getServer().getTickCount());
        if (before != null && before == event.getServer().getTickCount()) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) if (p.getPersistentData().contains(DATA_KEY)) {
            var store = TacticalCoordinateStore.read(p);
            if (store != null && (store.request() > 0 || store.cooldown() > 0)) TacticalCoordinateStore.write(p, store, store.advance());
        }
        for (Invitation i : new ArrayList<>(OUTGOING.values())) {
            if (i.session.identity.owner().server != event.getServer() || i.processing || i.lastTick == now(i.recipient.player)) continue;
            i.lastTick = now(i.recipient.player);
            if (--i.remaining <= 0) endInvitation(i, "expired", false, null);
            else if (!invitationValid(i)) endInvitation(i, "context_changed", false, null);
        }
    }
    @SubscribeEvent public static void controllers(ServerTickEvent.Post event) {
        for (Session s : new ArrayList<>(SESSIONS.values())) if (s.identity.owner().server == event.getServer()) tick(s.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && event.getEntity() instanceof ServerPlayer replacement) {
            remove(old);
            if (old.getPersistentData().contains(DATA_KEY)) replacement.getPersistentData().put(DATA_KEY, old.getPersistentData().get(DATA_KEY).copy());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (Session s : new ArrayList<>(SESSIONS.values())) if (s.identity.owner().server == event.getServer()) clear(s.identity.owner());
        for (Invitation i : new ArrayList<>(INCOMING.values())) if (i.recipient.player.server == event.getServer()) endInvitation(i, "context_changed", false, null);
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer());
        RECEIPTS.keySet().removeIf(p -> p.server == event.getServer()); COMMITS.keySet().removeIf(p -> p.server == event.getServer()); CLOCKS.remove(event.getServer());
    }
    private static Session owned(ServerPlayer p) {
        if (!thread(p)) return null; Session s = SESSIONS.get(p.getUUID()); return s != null && s.identity.owner() == p ? s : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static boolean live(ServerPlayer p) {
        return thread(p) && p.connection != null && !p.hasDisconnected() && p.isAlive() && !p.isRemoved()
                && p.serverLevel().getEntity(p.getId()) == p && p.server.getPlayerList().getPlayer(p.getUUID()) == p;
    }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }

    /** Only bounded identities/actions; the wire contains no destination coordinates or free-form heading. */
    public record Request(String wire, long token, UUID nonce, long sequence, String action, int index,
                          UUID templateId, long revision, UUID recipient, int actor, UUID owner) implements CustomPacketPayload {
        public Request {
            if (wire == null || wire.length() > 64 || !wire.isEmpty() && HighPowerInput.parse(wire) == null
                    || token <= 0 || nonce == null || sequence <= 0 || action == null || !ACTIONS.contains(action)
                    || index < 0 || index >= TacticalCoordinateRules.MAX_TEMPLATES || revision < 0
                    || templateId == null && revision != 0 || templateId != null && revision <= 0
                    || actor < 0 || owner == null) throw new IllegalArgumentException("Invalid NEW353 request");
        }
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_tactical_coordinate_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buffer, request) -> {
            buffer.writeUtf(request.wire, 64); buffer.writeLong(request.token); buffer.writeUUID(request.nonce);
            buffer.writeLong(request.sequence); buffer.writeUtf(request.action, 16); buffer.writeByte(request.index);
            buffer.writeBoolean(request.templateId != null); if (request.templateId != null) buffer.writeUUID(request.templateId);
            buffer.writeLong(request.revision); buffer.writeBoolean(request.recipient != null);
            if (request.recipient != null) buffer.writeUUID(request.recipient); buffer.writeInt(request.actor); buffer.writeUUID(request.owner);
        }, buffer -> new Request(buffer.readUtf(64), buffer.readLong(), buffer.readUUID(), buffer.readLong(), buffer.readUtf(16),
                buffer.readUnsignedByte(), buffer.readBoolean() ? buffer.readUUID() : null, buffer.readLong(),
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readInt(), buffer.readUUID()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ClientData(CompoundTag data) implements CustomPacketPayload {
        public ClientData {
            if (data == null || data.getAllKeys().size() > 40 || !data.hasUUID("owner") || !data.hasUUID("nonce")
                    || data.getInt("actor") < 0 || data.getLong("token") <= 0 || data.getLong("sequence") <= 0
                    || !Set.of("menu", "invite", "review", "success", "end").contains(data.getString("phase"))
                    || data.getString("reason").length() > 64 || data.getString("dimension").length() > 256
                    || data.getList("templates", Tag.TAG_COMPOUND).size() > 6 || data.getList("recipients", Tag.TAG_COMPOUND).size() > 32)
                throw new IllegalArgumentException("Invalid NEW353 client data");
            data = data.copy();
        }
        @Override public CompoundTag data() { return data.copy(); }
        public static final Type<ClientData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_tactical_coordinate_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientData> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeNbt(value.data), buffer -> {
                    Tag tag = buffer.readNbt(net.minecraft.nbt.NbtAccounter.create(32768));
                    if (!(tag instanceof CompoundTag data)) throw new IllegalArgumentException("Invalid NEW353 client tag");
                    return new ClientData(data);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(ClientData.TYPE, ClientData.CODEC, (packet, context) -> context.enqueueWork(() -> clientReceiver.accept(packet)));
            event.registrar("1").playToServer(Request.TYPE, Request.CODEC, (packet, context) -> {
                if (context.player() instanceof ServerPlayer p) context.enqueueWork(() -> request(p, packet));
            });
        }
    }
}

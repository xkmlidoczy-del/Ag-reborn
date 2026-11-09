package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
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
import net.minecraft.world.entity.EntityDimensions;
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

/** NEW341's one-use, optional teammate consent. A is the entrance; C is the sole destination.
 * B never grants movement authority. Team membership is eligibility, never consent.
 * The recipient neither learns NEW353 nor receives an ability/resource ledger.
 * An explicit Review action must precede a separate Accept. Absolute native transport
 * resets client velocity; server velocity, fall distance and grounded state are untouched. */
@EventBusSubscriber(modid = "academy")
public final class SpatialPincerConsent {
    private static final int MAX_CONTEXTS = 64, MAX_RECIPIENTS = 32;
    private static final String SHARED_MOVEMENT_DATA = "academy:safe_landing_probe_fences";
    private static final double RANGE_SQUARED = SpatialPincerCorridorRules.RANGE * SpatialPincerCorridorRules.RANGE;
    private static final Set<String> ACTIONS = Set.of("invite", "close", "open_review", "accept", "reject");
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Invitation> OUTGOING = new IdentityHashMap<>(), INCOMING = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Object> COMMITS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Integer> CLOCKS = new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.spatial_pincer_consent");
    public static Consumer<ClientData> clientReceiver = ignored -> {};

    public record Receipt(long input, long token, long planToken, long revision, boolean paid, boolean moved,
                          String reason, Vec3 source, Vec3 destination, UUID traveler) {}
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
    /** A recipient may have no cached ability state at all; never create one to inspect it. */
    private record AbilityView(cn.academy.port.core.AbilityProgress reference, CompoundTag copy) {
        static AbilityView capture(ServerPlayer player) {
            var state = AbilityStorage.peek(player);
            return new AbilityView(state, state == null ? null : AbilityStorage.encode(state));
        }
        boolean matches(ServerPlayer player) {
            return AbilityStorage.peek(player) == reference
                    && (reference == null || copy.equals(AbilityStorage.encode(reference)));
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
    private record Source(Vec3 position, Vec3 eye, Vec3 motion, AABB body, Pose pose, EntityDimensions dimensions, float eyeHeight,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouching,
                          boolean noPhysics, boolean passenger, boolean vehicle, boolean sleeping,
                          boolean gliding, boolean water, boolean lava, ParticipantState participant) {
        static Source capture(ServerPlayer p) {
            return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(), p.getPose(),
                    p.getDimensions(p.getPose()), p.getEyeHeight(),
                    p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown(), p.noPhysics,
                    p.isPassenger(), p.isVehicle(), p.isSleeping(), p.isFallFlying(), p.isInWater(), p.isInLava(), ParticipantState.capture(p));
        }
        boolean matches(ServerPlayer p) {
            return nonBudgetMatches(p) && participant.budget.matches(p, HighPowerResources.DATA_KEY);
        }
        boolean nonBudgetMatches(ServerPlayer p) {
            return position.equals(p.position()) && eye.equals(p.getEyePosition()) && motion.equals(p.getDeltaMovement())
                    && body.equals(p.getBoundingBox()) && pose == p.getPose() && Float.compare(yaw, p.getYRot()) == 0
                    && dimensionsMatch(p) && Float.compare(eyeHeight, p.getEyeHeight()) == 0
                    && Float.compare(pitch, p.getXRot()) == 0 && Float.compare(fall, p.fallDistance) == 0
                    && nativeFlagsMatch(p) && participant.nonBudgetMatches(p);
        }
        boolean nativeFlagsMatch(ServerPlayer p) {
            return grounded == p.onGround() && crouching == p.isShiftKeyDown() && noPhysics == p.noPhysics
                    && passenger == p.isPassenger() && vehicle == p.isVehicle() && sleeping == p.isSleeping()
                    && gliding == p.isFallFlying() && water == p.isInWater() && lava == p.isInLava();
        }
        AABB destinationBody(Vec3 destination) { return dimensions.makeBoundingBox(destination); }
        Vec3 destinationEye(Vec3 destination) { return new Vec3(destination.x, destination.y + (double)eyeHeight, destination.z); }
        boolean dimensionsMatch(ServerPlayer p) {
            EntityDimensions current = p.getDimensions(p.getPose());
            // Native non-default scale creates fresh attachment objects on every query.
            // Compare the frozen geometry operands, not unrelated attachment identity.
            return Float.compare(dimensions.width(), current.width()) == 0
                    && Float.compare(dimensions.height(), current.height()) == 0 && dimensions.fixed() == current.fixed();
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
        final SpatialPincerCorridor.ConsentPlan plan;
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input command;
        final UUID nonce = UUID.randomUUID();
        final ServerLevel world;
        final Vec3 entrance, destination;
        final long planToken, revision;
        final float yaw, pitch;
        final double cost;
        final int cooldown;
        long sequence, actionFloor, lastVisual;
        boolean processing, rendering, closing, terminal;
        CompoundTag lastData;
        Session(ServerPlayer caster, SpatialPincerCorridor.ConsentPlan plan) {
            this.plan = plan; command = plan.command();
            world = plan.world(); entrance = plan.pointA(); destination = plan.pointC();
            planToken = plan.token(); revision = plan.revision(); yaw = plan.yaw(); pitch = plan.pitch();
            identity = new LegacySingleKeyServerIdentity(caster, command.input(), ++nextToken, AbilityStorage.get(caster));
            var position = HighPowerGrowth.values(caster).position();
            cost = SpatialPincerCorridorRules.transferCpCost(position);
            cooldown = SpatialPincerCorridorRules.transferCooldownTicks(position);
            lastVisual = now(caster);
        }
    }
    private static final class Invitation {
        final Session session;
        final TravelerIdentity recipient;
        final Team team;
        final UUID nonce = UUID.randomUUID();
        final long token;
        final String casterName, recipientName;
        int remaining;
        long sequence, lastTick;
        boolean reviewed, processing, terminal;
        CompoundTag lastData;
        Invitation(Session session, ServerPlayer recipient) {
            this.session = session; this.recipient = TravelerIdentity.capture(recipient);
            team = session.identity.owner().getTeam(); token = ++nextToken;
            casterName = session.identity.owner().getGameProfile().getName();
            recipientName = recipient.getGameProfile().getName();
            remaining = Math.min(SpatialPincerCorridorRules.INVITATION_TICKS, session.plan.remainingTicks()); lastTick = now(recipient);
        }
    }
    private SpatialPincerConsent() {}

    /** Only the controller's closed, exact three-mark plan can open this picker. */
    static boolean offer(ServerPlayer caster, SpatialPincerCorridor.ConsentPlan plan) {
        if (!thread(caster) || plan == null || plan.owner() != caster || !live(caster)
                || AbilityConsumption.busy(caster) || nextToken == Long.MAX_VALUE || SESSIONS.size() >= MAX_CONTEXTS
                || !SpatialPincerCorridor.consentCurrent(plan) || plan.remainingTicks() <= 0
                || plan.command() == null || !HighPowerBracerSession.authenticated(caster, plan.command())) return false;
        Session old = SESSIONS.get(caster);
        if (old != null && (old.processing || old.rendering)) return false;
        if (old != null) finish(old, "context_changed", false, null);
        Session session = new Session(caster, plan); SESSIONS.put(caster, session);
        if (!control(session)) { finish(session, "context_changed", false, null); return false; }
        return menu(session, "", false);
    }

    /** Request packets contain only bounded identities and actions, never coordinates. */
    public static boolean request(ServerPlayer player, Request request) {
        if (!thread(player) || !live(player) || request == null || request.actor != player.getId()
                || !request.owner.equals(player.getUUID())) return false;
        if (Set.of("open_review", "accept", "reject").contains(request.action)) {
            try { return recipientRequest(player, request); }
            catch (RuntimeException failure) {
                LOG.warn("NEW341 consent request failed closed", failure);
                endInvitation(INCOMING.get(player), "validation_failed", false, null); return false;
            }
        }
        Session session = SESSIONS.get(player);
        if (session == null || session.processing || session.rendering || session.terminal
                || !session.command.wire().equals(request.wire) || session.identity.token() != request.token
                || !session.nonce.equals(request.nonce) || !bound(session, request)
                || !HighPowerBracerSession.authenticated(player, HighPowerInput.parse(request.wire))) return false;
        if (request.action.equals("close")) { clear(player); return true; }
        if (session.sequence == Long.MAX_VALUE || session.actionFloor <= 0 || session.actionFloor > session.sequence) {
            finish(session, "sequence_exhausted", false, null); return false;
        }
        if (request.sequence < session.actionFloor || request.sequence > session.sequence) return refuse(session, "stale_request");
        if (!control(session)) { finish(session, "context_changed", false, null); return false; }
        session.processing = true;
        try { return request.action.equals("invite") && invite(session, request.recipient); }
        catch (RuntimeException failure) { LOG.warn("NEW341 invitation failed closed", failure); return refuse(session, "validation_failed"); }
        finally {
            session.processing = false;
            if (session.closing && !session.terminal) finish(session, "context_changed", false, null);
        }
    }

    private static boolean invite(Session session, UUID recipientId) {
        ServerPlayer caster = session.identity.owner();
        if (OUTGOING.containsKey(caster) || nextToken == Long.MAX_VALUE || OUTGOING.size() >= MAX_CONTEXTS)
            return refuse(session, "invitation_pending");
        ServerPlayer recipient = recipientId == null ? null : caster.server.getPlayerList().getPlayer(recipientId);
        if (recipient == null || !advertised(session).contains(recipientId) || recipient == caster
                || INCOMING.containsKey(recipient) || !eligibleRecipient(caster, recipient)
                || !within(caster, session.plan) || !within(recipient, session.plan)) return refuse(session, "recipient_unavailable");
        String reason = resources(session, recipient);
        if (!reason.isEmpty()) return refuse(session, reason);
        Invitation invitation = new Invitation(session, recipient);
        if (invitation.remaining <= 0 || !control(session)) return refuse(session, "expired");
        OUTGOING.put(caster, invitation); INCOMING.put(recipient, invitation);
        try { emitInvitation(invitation, "invite", "", false, null); }
        catch (RuntimeException failure) { endInvitation(invitation, "validation_failed", false, null); return refuse(session, "validation_failed"); }
        return menu(session, "invited", false);
    }

    private static boolean recipientRequest(ServerPlayer player, Request request) {
        Invitation invitation = INCOMING.get(player);
        if (invitation == null || invitation.processing || invitation.terminal || !request.wire.isEmpty()
                || request.token != invitation.token || !request.nonce.equals(invitation.nonce)
                || request.sequence != invitation.sequence || !bound(invitation.session, request)
                || !invitation.recipient.matches()) return false;
        if (request.action.equals("reject")) { endInvitation(invitation, "rejected", false, null); return true; }
        if (invitation.sequence == Long.MAX_VALUE) { endInvitation(invitation, "sequence_exhausted", false, null); return false; }
        if (!invitationValid(invitation)) { endInvitation(invitation, "context_changed", false, null); return false; }
        if (request.action.equals("open_review")) {
            // Publishing review advances the sequence. The original notice cannot also accept.
            invitation.reviewed = true; emitInvitation(invitation, "review", "", false, null); return true;
        }
        if (!request.action.equals("accept") || !invitation.reviewed || invitation.session.processing) return false;
        invitation.processing = true; invitation.session.processing = true;
        try { return transport(invitation); }
        finally { invitation.processing = false; invitation.session.processing = false; }
    }

    private static boolean transport(Invitation invitation) {
        Session session = invitation.session;
        ServerPlayer caster = session.identity.owner(), traveler = invitation.recipient.player;
        var plan = session.plan; Object transaction = new Object();
        if (COMMITS.containsKey(caster) || COMMITS.containsKey(traveler)) return refuse(session, "movement_conflict");
        COMMITS.put(caster, transaction); COMMITS.put(traveler, transaction);
        boolean paid = false, moved = false, admitted = false; String result = "validation_failed";
        Source source = null;
        try {
            source = Source.capture(traveler); Source casterSource = Source.capture(caster);
            AbilityView travelerAbility = AbilityView.capture(traveler);
            ResourceView resourceView = ResourceView.capture(session);
            if (!invitationValid(invitation)) { result = "context_changed"; return false; }
            String reason = admissionRefusal(session, traveler);
            if (!reason.isEmpty()) { result = reason; return false; }
            if (!SpatialPincerCorridor.revalidateConsentPoints(plan, traveler)) { result = "plan_changed"; return false; }
            if (!unchanged(invitation, source, casterSource, travelerAbility, resourceView, transaction)) { result = "event_changed_state"; return false; }
            var assessment = SafeLandingProbeWorld.tacticalDestination(traveler, session.world, session.destination);
            if (!assessment.valid()) { result = assessment.reason(); return false; }
            var candidate = assessment.candidate();
            if (!unchanged(invitation, source, casterSource, travelerAbility, resourceView, transaction)) { result = "event_changed_state"; return false; }
            Vec3 destination = session.destination;
            EntityTeleportEvent event = new EntityTeleportEvent(traveler, destination.x, destination.y, destination.z);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) { result = "event_cancelled"; return false; }
            if (Double.compare(event.getTargetX(), destination.x) != 0 || Double.compare(event.getTargetY(), destination.y) != 0
                    || Double.compare(event.getTargetZ(), destination.z) != 0) { result = "event_redirected"; return false; }
            if (!unchanged(invitation, source, casterSource, travelerAbility, resourceView, transaction)) { result = "event_changed_state"; return false; }
            reason = admissionRefusal(session, traveler);
            if (!reason.isEmpty()) { result = reason; return false; }
            if (!SpatialPincerCorridor.revalidateConsentPoints(plan, traveler)) { result = "plan_changed"; return false; }
            if (!unchanged(invitation, source, casterSource, travelerAbility, resourceView, transaction)) { result = "event_changed_state"; return false; }
            var revalidated = SafeLandingProbeWorld.tacticalRevalidate(traveler, candidate);
            if (!revalidated.valid()) { result = revalidated.reason(); return false; }
            if (!unchanged(invitation, source, casterSource, travelerAbility, resourceView, transaction)) { result = "event_changed_state"; return false; }
            reason = admissionRefusal(session, traveler);
            if (!reason.isEmpty()) { result = reason; return false; }
            // The controller admission, debit, both shared physical fences and native call have no intervening callbacks.
            // Once admitted, even an unknown native result is terminal; no plan replay or refund is possible.
            if (!SpatialPincerCorridor.consumeConsent(plan)) { result = "plan_changed"; return false; }
            admitted = true;
            if (!HighPowerResources.trySpend(caster, SpatialPincerCorridor.ID, session.cost, session.cooldown)) {
                result = "insufficient_cp"; return false;
            }
            paid = true;
            if (!SpatialPincerCorridor.commitConsentCooldown(plan, session.cooldown)) { result = "commit_unconfirmed"; return false; }
            SafeLandingProbe.commitSharedMovement(caster); SafeLandingProbe.commitSharedMovement(traveler);
            StoredView casterFence = StoredView.capture(caster, SHARED_MOVEMENT_DATA), travelerFence = StoredView.capture(traveler, SHARED_MOVEMENT_DATA);
            ResourceView paidResources = ResourceView.capture(session);
            // Native setPos reconstructs bounds from dimensions and adds the cached float eye height.
            // Use those same frozen operands; sourceBox.move(destination-source) can round differently.
            AABB destinationBody = source.destinationBody(destination); Vec3 destinationEye = source.destinationEye(destination);
            traveler.connection.teleport(destination.x, destination.y, destination.z, session.yaw, session.pitch, Set.of());
            moved = traveler.serverLevel() == session.world && traveler.position().equals(destination);
            if (!moved || !traveler.getDeltaMovement().equals(source.motion)
                    || !destinationBody.equals(traveler.getBoundingBox()) || !destinationEye.equals(traveler.getEyePosition())
                    || !source.dimensionsMatch(traveler) || Float.compare(source.eyeHeight, traveler.getEyeHeight()) != 0
                    || Float.compare(traveler.fallDistance, source.fall) != 0 || traveler.onGround() != source.grounded
                    || Float.compare(traveler.getYRot(), session.yaw) != 0 || Float.compare(traveler.getXRot(), session.pitch) != 0
                    || traveler.getPose() != source.pose || !source.nativeFlagsMatch(traveler)
                    || !source.participant.nonBudgetMatches(traveler) || !casterSource.nonBudgetMatches(caster)
                    || !source.participant.budget.matches(traveler, HighPowerResources.DATA_KEY)
                    || !travelerAbility.matches(traveler) || !paidResources.matches(session) || session.closing
                    || !casterFence.matches(caster, SHARED_MOVEMENT_DATA) || !travelerFence.matches(traveler, SHARED_MOVEMENT_DATA)
                    || !session.identity.owns(caster) || !invitation.recipient.matches()
                    || caster.getTeam() != invitation.team || traveler.getTeam() != invitation.team) {
                result = "commit_unconfirmed"; return false;
            }
            result = ""; return true;
        } catch (RuntimeException failure) {
            moved |= paid && traveler.serverLevel() == session.world && traveler.position().equals(session.destination);
            result = paid ? "transport_unconfirmed" : "validation_failed";
            LOG.warn("NEW341 terminal native transport failure; paid={}, no retry or refund", paid, failure); return false;
        } catch (Error failure) {
            moved |= paid && traveler.serverLevel() == session.world && traveler.position().equals(session.destination);
            result = paid ? "transport_unconfirmed" : "validation_failed"; throw failure;
        } finally {
            COMMITS.remove(caster, transaction); COMMITS.remove(traveler, transaction);
            if (admitted) {
                try { SpatialPincerCorridor.completeConsent(plan); }
                catch (RuntimeException failure) { LOG.warn("NEW341 completion delivery failed; admission remains terminal", failure); }
            }
            Vec3 from = source == null ? traveler.position() : source.position;
            if (live(caster)) RECEIPTS.put(caster, new Receipt(session.command.input(), session.identity.token(), session.planToken, session.revision,
                    paid, moved, result, from, session.destination, traveler.getUUID()));
            if (live(traveler)) RECEIPTS.put(traveler, new Receipt(0, invitation.token, session.planToken, session.revision,
                    paid, moved, result, from, session.destination, traveler.getUUID()));
            endInvitation(invitation, result, moved, from);
            finish(session, moved && result.isEmpty() ? "recipient_moved" : result, false, from);
            if (paid && live(caster)) {
                try { AbilityStorage.save(caster); AcademyNetwork.sync(caster); }
                catch (RuntimeException failure) { LOG.warn("NEW341 transport sync failed; payment remains terminal", failure); }
            }
        }
    }

    private static boolean unchanged(Invitation invitation, Source travelerSource, Source casterSource, AbilityView travelerAbility,
                                     ResourceView resources, Object transaction) {
        Session session = invitation.session; ServerPlayer caster = session.identity.owner(), traveler = invitation.recipient.player;
        return COMMITS.get(caster) == transaction && COMMITS.get(traveler) == transaction && invitationValid(invitation)
                && travelerSource.matches(traveler) && casterSource.matches(caster) && travelerAbility.matches(traveler) && resources.matches(session)
                && nearEntrance(traveler, session.plan) && within(caster, session.plan) && within(traveler, session.plan);
    }
    private static String admissionRefusal(Session session, ServerPlayer traveler) {
        String reason = resources(session, traveler);
        if (reason.isEmpty()) reason = travelerRefusal(session.identity.owner());
        if (reason.isEmpty()) reason = travelerRefusal(traveler);
        if (reason.isEmpty() && !nearEntrance(traveler, session.plan)) reason = "entrance_required";
        if (reason.isEmpty() && (!within(session.identity.owner(), session.plan) || !within(traveler, session.plan))) reason = "out_of_range";
        return reason;
    }
    private static String resources(Session session, ServerPlayer traveler) {
        ServerPlayer caster = session.identity.owner();
        int casterFence = SafeLandingProbe.sharedMovementRemaining(caster), travelerFence = SafeLandingProbe.sharedMovementRemaining(traveler);
        Tag raw = caster.getPersistentData().get(HighPowerResources.DATA_KEY);
        if (!(raw instanceof CompoundTag ledger) || !ledger.contains("cp", Tag.TAG_DOUBLE)
                || !Double.isFinite(ledger.getDouble("cp")) || ledger.getDouble("cp") < 0 || casterFence < 0 || travelerFence < 0)
            return "storage_invalid";
        if (HighPowerResources.hasReservation(caster)) return "resource_busy";
        if (!SpatialPincerCorridor.consentResourceReady(session.plan) || casterFence > 0 || travelerFence > 0 || !session.identity.state().canUse(SpatialPincerCorridor.ID)
                || caster.serverLevel().getGameTime() < ledger.getLong("next_" + SpatialPincerCorridor.ID)) return "cooldown";
        return !Double.isFinite(session.cost) || session.cost <= 0 || session.cooldown <= 0
                || HighPowerResources.availableCp(caster) < session.cost ? "insufficient_cp" : "";
    }
    private static boolean control(Session session) {
        ServerPlayer caster = session.identity.owner();
        return !session.terminal && !session.closing && SESSIONS.get(caster) == session
                && session.identity.owns(caster) && live(caster) && SpatialPincerCorridor.consentCurrent(session.plan)
                && session.world == session.plan.world() && session.planToken == session.plan.token() && session.revision == session.plan.revision()
                && session.entrance.equals(session.plan.pointA()) && session.destination.equals(session.plan.pointC())
                && Float.compare(session.yaw, session.plan.yaw()) == 0 && Float.compare(session.pitch, session.plan.pitch()) == 0
                && session.plan.remainingTicks() > 0 && (committing(caster) || !AbilityConsumption.busy(caster));
    }
    private static boolean invitationValid(Invitation invitation) {
        Session session = invitation.session; ServerPlayer caster = session.identity.owner(), recipient = invitation.recipient.player;
        return !invitation.terminal && invitation.remaining > 0 && OUTGOING.get(caster) == invitation && INCOMING.get(recipient) == invitation
                && control(session) && invitation.recipient.matches() && invitation.team != null
                && caster.getTeam() == invitation.team && recipient.getTeam() == invitation.team && recipient.serverLevel() == session.plan.world();
    }
    private static boolean eligibleRecipient(ServerPlayer caster, ServerPlayer recipient) {
        return recipient != caster && live(recipient) && caster.getTeam() != null && caster.getTeam() == recipient.getTeam()
                && caster.serverLevel() == recipient.serverLevel() && !recipient.isSpectator()
                && !recipient.getAbilities().instabuild && !recipient.getAbilities().invulnerable;
    }
    private static boolean within(ServerPlayer player, SpatialPincerCorridor.ConsentPlan plan) {
        return player.serverLevel() == plan.world() && finite(player.position()) && player.position().distanceToSqr(plan.pointC()) <= RANGE_SQUARED;
    }
    private static boolean nearEntrance(ServerPlayer player, SpatialPincerCorridor.ConsentPlan plan) {
        Vec3 source = player.position(), entrance = plan.pointA();
        return finite(source) && SpatialPincerCorridorRules.nearEntrance(
                Math.hypot(source.x - entrance.x, source.z - entrance.z), source.y - entrance.y);
    }
    private static boolean bound(Session session, Request request) {
        return request.planToken == session.planToken && request.revision == session.revision;
    }
    /** Reading recipient eligibility never obtains, binds or initializes ability/high-power state. */
    private static String travelerRefusal(ServerPlayer player) {
        if (!live(player) || player.isSpectator() || player.getAbilities().instabuild || player.getAbilities().invulnerable
                || player.containerMenu != player.inventoryMenu) return "source_unavailable";
        var state = AbilityStorage.peek(player);
        if (state != null && (state.interfering || state.consumptionInProgress())) return "movement_conflict";
        if (!committing(player) && AbilityConsumption.busy(player)) return "movement_conflict";
        if (!player.onGround() || !Float.isFinite(player.fallDistance) || player.fallDistance != 0) return "source_airborne";
        if (player.noPhysics || player.isPassenger() || player.isVehicle() || player.isSleeping() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING
                || !finite(player.position()) || !finite(player.getEyePosition()) || !finite(player.getDeltaMovement())
                || !Float.isFinite(player.getYRot()) || !Float.isFinite(player.getXRot()) || Math.abs(player.getXRot()) > 90)
            return "source_motion_or_pose_unsupported";
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION) || player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                || CornerExchange.active(player) || EmergencyReturn.active(player) || ReturnMark.active(player) || CrossSubstitution.isTransporting(player)
                || SafeLandingProbe.active(player) || PhaseSidestep.active(player) || (BeamStep.active(player)||WindPressureLift.active(player)) || MagneticGroundStep.active(player)
                || PolarityTether.active(player) || TacticalCoordinateFormation.active(player)
                || cn.academy.port.skill.MagMovement.active(player) || cn.academy.port.skill.VecAccel.active(player)
                || cn.academy.port.skill.JetEngine.active(player) || cn.academy.port.skill.StormWing.active(player)
                || cn.academy.port.skill.MarkTeleport.active(player) || cn.academy.port.skill.PenetrateTeleport.active(player)
                || cn.academy.port.skill.LocationTeleport.active(player) || cn.academy.port.skill.Flashing.active(player)) return "movement_conflict";
        return "";
    }

    private static boolean refuse(Session session, String reason) { menu(session, reason, false); return false; }
    private static boolean menu(Session session, String reason, boolean heartbeat) {
        if (session.rendering || session.terminal || session.closing || session.sequence == Long.MAX_VALUE) return false;
        session.rendering = true;
        try {
            ServerPlayer caster = session.identity.owner();
            CompoundTag data = base(caster, session.command.input(), session.command.epoch(), session.identity.token(),
                    session.nonce, ++session.sequence, "menu", reason);
            planData(data, session);
            ListTag recipients = new ListTag();
            if (caster.getTeam() != null) for (ServerPlayer recipient : caster.server.getPlayerList().getPlayers()) {
                if (!eligibleRecipient(caster, recipient) || !within(recipient, session.plan)) continue;
                CompoundTag row = new CompoundTag(); row.putUUID("uuid", recipient.getUUID());
                row.putString("name", recipient.getGameProfile().getName()); recipients.add(row);
                if (recipients.size() >= MAX_RECIPIENTS) break;
            }
            data.put("recipients", recipients); data.putBoolean("invitation_pending", OUTGOING.containsKey(caster));
            data.putInt("remaining", session.plan.remainingTicks());
            if (!control(session)) { finish(session, "context_changed", false, null); return false; }
            session.lastData = data.copy(); session.lastVisual = now(caster);
            if (!heartbeat) session.actionFloor = session.sequence;
            send(caster, data); return true;
        } catch (RuntimeException failure) {
            LOG.warn("NEW341 consent picker failed closed", failure); finish(session, "validation_failed", false, null); return false;
        } finally { session.rendering = false; }
    }
    private static Set<UUID> advertised(Session session) {
        var ids = new java.util.HashSet<UUID>();
        if (session.lastData != null) {
            var rows = session.lastData.getList("recipients", Tag.TAG_COMPOUND);
            for (int index = 0; index < rows.size(); index++) if (rows.getCompound(index).hasUUID("uuid")) ids.add(rows.getCompound(index).getUUID("uuid"));
        }
        return Set.copyOf(ids);
    }
    private static CompoundTag base(ServerPlayer viewer, long input, long epoch, long token, UUID nonce,
                                    long sequence, String phase, String reason) {
        CompoundTag data = new CompoundTag(); data.putInt("actor", viewer.getId()); data.putUUID("owner", viewer.getUUID());
        data.putLong("input", input); data.putLong("epoch", epoch); data.putLong("token", token); data.putUUID("nonce", nonce);
        data.putLong("sequence", sequence); data.putString("phase", phase); data.putString("reason", reason); data.putBoolean("moved", false);
        return data;
    }
    private static void planData(CompoundTag data, Session session) {
        var plan = session.plan; ServerPlayer caster = session.identity.owner(); Vec3 entrance = session.entrance, destination = session.destination;
        data.putLong("plan_token", session.planToken); data.putLong("revision", session.revision);
        data.putString("dimension", session.world.dimension().location().toString());
        data.putDouble("a_x", entrance.x); data.putDouble("a_y", entrance.y); data.putDouble("a_z", entrance.z);
        data.putDouble("x", destination.x); data.putDouble("y", destination.y); data.putDouble("z", destination.z);
        data.putFloat("yaw", session.yaw); data.putFloat("pitch", session.pitch);
        data.putDouble("cost", session.cost); data.putInt("cooldown", session.cooldown); data.putInt("plan_remaining", Math.max(0, plan.remainingTicks()));
        data.putUUID("caster", caster.getUUID()); data.putString("caster_name", caster.getGameProfile().getName());
    }
    private static void emitInvitation(Invitation invitation, String phase, String reason, boolean moved, Vec3 source) {
        if (invitation.sequence == Long.MAX_VALUE) return;
        ServerPlayer recipient = invitation.recipient.player; Session session = invitation.session;
        CompoundTag data = base(recipient, 0, 0, invitation.token, invitation.nonce, ++invitation.sequence, phase, reason);
        planData(data, session);
        data.putUUID("recipient", recipient.getUUID()); data.putString("recipient_name", invitation.recipientName);
        data.putString("caster_name", invitation.casterName);
        data.putInt("remaining", invitation.terminal ? 0 : invitation.remaining); data.putBoolean("moved", moved);
        data.putUUID("moved_owner", recipient.getUUID()); data.putInt("moved_actor", recipient.getId());
        if (source != null) { data.putDouble("from_x", source.x); data.putDouble("from_y", source.y); data.putDouble("from_z", source.z); }
        invitation.lastData = data.copy(); send(recipient, data);
    }
    private static void endInvitation(Invitation invitation, String reason, boolean moved, Vec3 source) {
        if (invitation == null || invitation.terminal) return;
        invitation.terminal = true; OUTGOING.remove(invitation.session.identity.owner(), invitation); INCOMING.remove(invitation.recipient.player, invitation);
        try { emitInvitation(invitation, moved && reason.isEmpty() ? "success" : "end", reason, moved, source); }
        catch (RuntimeException failure) { LOG.warn("NEW341 invitation ended despite delivery failure", failure); }
    }
    private static void finish(Session session, String reason, boolean moved, Vec3 source) {
        if (session.terminal) return;
        session.terminal = true; session.closing = true; SESSIONS.remove(session.identity.owner(), session);
        Invitation outgoing = OUTGOING.get(session.identity.owner());
        if (outgoing != null && outgoing.session == session) endInvitation(outgoing, reason, false, null);
        if (session.sequence == Long.MAX_VALUE) return;
        ServerPlayer caster = session.identity.owner();
        CompoundTag data = base(caster, session.command.input(), session.command.epoch(), session.identity.token(), session.nonce,
                ++session.sequence, moved && reason.isEmpty() ? "success" : "end", reason);
        planData(data, session); data.putInt("remaining", 0); data.putBoolean("moved", moved);
        data.putUUID("moved_owner", caster.getUUID()); data.putInt("moved_actor", caster.getId());
        if (source != null) { data.putDouble("from_x", source.x); data.putDouble("from_y", source.y); data.putDouble("from_z", source.z); }
        session.lastData = data.copy();
        try { send(caster, data); } catch (RuntimeException failure) { LOG.warn("NEW341 picker ended despite delivery failure", failure); }
    }
    private static void send(ServerPlayer player, CompoundTag data) { if (live(player)) PacketDistributor.sendToPlayer(player, new ClientData(data)); }
    public static CompoundTag snapshot(ServerPlayer player) {
        Session session = SESSIONS.get(player); return session == null || session.lastData == null ? null : session.lastData.copy();
    }
    public static CompoundTag invitation(ServerPlayer player) {
        Invitation invitation = INCOMING.get(player); return invitation == null || invitation.lastData == null ? null : invitation.lastData.copy();
    }
    public static Receipt receipt(ServerPlayer player) { return RECEIPTS.get(player); }
    public static boolean committing(ServerPlayer player) { return player != null && COMMITS.containsKey(player); }
    public static boolean active(ServerPlayer player) { return player != null && (SESSIONS.containsKey(player) || committing(player)); }
    public static void clear(ServerPlayer player) {
        if (!thread(player)) return;
        Session session = SESSIONS.get(player);
        if (session != null) {
            session.closing = true; endInvitation(OUTGOING.get(player), "context_changed", false, null);
            if (!session.processing) finish(session, "context_changed", false, null);
        }
        endInvitation(INCOMING.get(player), "context_changed", false, null);
    }
    public static void cancelActive(ServerPlayer player) { clear(player); }
    public static void remove(ServerPlayer player) { if (thread(player)) { clear(player); RECEIPTS.remove(player); } }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre event) {
        int tick = event.getServer().getTickCount(); Integer previous = CLOCKS.put(event.getServer(), tick);
        if (previous != null && previous == tick) return;
        for (Invitation invitation : new ArrayList<>(OUTGOING.values())) {
            if (invitation.session.identity.owner().server != event.getServer() || invitation.processing
                    || invitation.lastTick == now(invitation.recipient.player)) continue;
            invitation.lastTick = now(invitation.recipient.player);
            if (--invitation.remaining <= 0) endInvitation(invitation, "expired", false, null);
            else if (!invitationValid(invitation)) endInvitation(invitation, "context_changed", false, null);
        }
    }
    @SubscribeEvent public static void controllers(ServerTickEvent.Post event) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer caster = session.identity.owner();
            if (caster.server != event.getServer() || session.processing) continue;
            if (!control(session)) { finish(session, "context_changed", false, null); continue; }
            if (now(caster) - session.lastVisual >= 10 || now(caster) < session.lastVisual) menu(session, "", true);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if (event.getOriginal() instanceof ServerPlayer player) remove(player); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (Session session : new ArrayList<>(SESSIONS.values())) if (session.identity.owner().server == event.getServer()) clear(session.identity.owner());
        for (Invitation invitation : new ArrayList<>(INCOMING.values())) if (invitation.recipient.player.server == event.getServer()) endInvitation(invitation, "context_changed", false, null);
        COMMITS.keySet().removeIf(player -> player.server == event.getServer()); RECEIPTS.keySet().removeIf(player -> player.server == event.getServer()); CLOCKS.remove(event.getServer());
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
    private static boolean live(ServerPlayer player) {
        return thread(player) && player.connection != null && !player.hasDisconnected() && player.isAlive() && !player.isRemoved()
                && player.serverLevel().getEntity(player.getId()) == player && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    private static boolean finite(Vec3 point) { return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }
    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.server.getTickCount()); }

    public record Request(String wire, long token, UUID nonce, long sequence, String action, long planToken,
                          long revision, UUID recipient, int actor, UUID owner) implements CustomPacketPayload {
        public Request {
            if (wire == null || wire.length() > 64 || !wire.isEmpty() && HighPowerInput.parse(wire) == null
                    || token <= 0 || nonce == null || sequence <= 0 || action == null || !ACTIONS.contains(action)
                    || planToken <= 0 || revision <= 0 || actor < 0 || owner == null) throw new IllegalArgumentException("Invalid NEW341 consent request");
        }
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_spatial_pincer_consent_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buffer, request) -> {
            buffer.writeUtf(request.wire, 64); buffer.writeLong(request.token); buffer.writeUUID(request.nonce);
            buffer.writeLong(request.sequence); buffer.writeUtf(request.action, 16); buffer.writeLong(request.planToken);
            buffer.writeLong(request.revision); buffer.writeBoolean(request.recipient != null);
            if (request.recipient != null) buffer.writeUUID(request.recipient); buffer.writeInt(request.actor); buffer.writeUUID(request.owner);
        }, buffer -> new Request(buffer.readUtf(64), buffer.readLong(), buffer.readUUID(), buffer.readLong(), buffer.readUtf(16),
                buffer.readLong(), buffer.readLong(), buffer.readBoolean() ? buffer.readUUID() : null, buffer.readInt(), buffer.readUUID()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ClientData(CompoundTag data) implements CustomPacketPayload {
        public ClientData {
            if (data == null || data.getAllKeys().size() > 48 || !data.hasUUID("owner") || !data.hasUUID("nonce") || !data.hasUUID("caster")
                    || data.getInt("actor") < 0 || data.getLong("token") <= 0 || data.getLong("sequence") <= 0
                    || data.getLong("plan_token") <= 0 || data.getLong("revision") <= 0
                    || !Set.of("menu", "invite", "review", "success", "end").contains(data.getString("phase"))
                    || data.getString("reason").length() > 64 || data.getString("dimension").length() > 256
                    || data.getString("caster_name").length() > 32 || data.getString("recipient_name").length() > 32
                    || data.getList("recipients", Tag.TAG_COMPOUND).size() > MAX_RECIPIENTS)
                throw new IllegalArgumentException("Invalid NEW341 consent data");
            data = data.copy();
        }
        @Override public CompoundTag data() { return data.copy(); }
        public static final Type<ClientData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_spatial_pincer_consent_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientData> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeNbt(value.data), buffer -> {
                    Tag tag = buffer.readNbt(net.minecraft.nbt.NbtAccounter.create(32768));
                    if (!(tag instanceof CompoundTag data)) throw new IllegalArgumentException("Invalid NEW341 consent tag");
                    return new ClientData(data);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(ClientData.TYPE, ClientData.CODEC, (packet, context) -> context.enqueueWork(() -> clientReceiver.accept(packet)));
            event.registrar("1").playToServer(Request.TYPE, Request.CODEC, (packet, context) -> {
                if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> request(player, packet));
            });
        }
    }
}

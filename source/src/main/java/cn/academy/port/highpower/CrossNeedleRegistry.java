package cn.academy.port.highpower;

import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-wide NEW321 three-slot physical-item custody, separate from NEW311.
 * The overworld owns three independent one-item ledgers per owner, including other dimensions.
 * Native player/entity saves are not atomic: interrupted transfers remain outstanding,
 * never a license to mint a replacement. Corrupt serialized records are retained verbatim.
 */
@EventBusSubscriber(modid="academy")
public final class CrossNeedleRegistry extends SavedData implements DisplacedNeedleEntity.Listener {
    public static final String ID = "academy_cross_needle_custody";
    public static final int MAX_OWNERS = 4096;
    public static final int SLOT_COUNT = 3;
    public enum Availability { READY, NO_MEDIUM, REFUND_PENDING, OUTSTANDING, CORRUPT, OWNER_INVALID, REGISTRY_FULL }
    private static final int VERSION = 1;
    private static final Set<String> ROOT_KEYS = Set.of("version", "entries");
    private static final Set<String> OWNER_KEYS = Set.of("owner", "slots");
    private static final Set<String> ENTRY_KEYS = Set.of("slot", "dimension", "ledger", "escrow");
    private final MinecraftServer server;
    private final Map<UUID, OwnerEntry> entries = new LinkedHashMap<>();
    private CompoundTag unreadableOriginal;

    private static final class OwnerEntry {
        final Entry[] slots = new Entry[SLOT_COUNT];
        boolean deploying;
        CompoundTag unreadableOriginal;
        OwnerEntry(UUID owner) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) slots[slot] = new Entry(owner);
        }
    }

    private static final class Entry {
        final DisplacedNeedleLedger ledger;
        String dimension = "";
        ItemStack escrow = ItemStack.EMPTY;
        DisplacedNeedleEntity live;
        DisplacedNeedleLedger.ReturnTicket returning;
        DisplacedNeedleLedger.PickupTicket pickingUp;
        DisplacedNeedleLedger.AttackPermit attack;
        CompoundTag unreadableOriginal;
        Entry(UUID owner) { ledger = new DisplacedNeedleLedger(owner); }
        Entry(DisplacedNeedleLedger ledger) { this.ledger = ledger; }
    }

    private record StagePose(Vec3 position, AABB bounds, Vec3 velocity, float yaw, float pitch) { }

    private CrossNeedleRegistry(MinecraftServer server) { this.server = server; }

    private static SavedData.Factory<CrossNeedleRegistry> factory(MinecraftServer server) {
        return new SavedData.Factory<>(() -> new CrossNeedleRegistry(server),
                (tag, lookup) -> load(server, tag, lookup));
    }

    public static CrossNeedleRegistry get(ServerLevel level) {
        requireThread(level.getServer());
        return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(level.getServer()), ID);
    }

    /** Read an existing file without creating either a ledger or a missing registry. */
    public static CrossNeedleRegistry getExisting(ServerLevel level) {
        requireThread(level.getServer());
        return level.getServer().overworld().getDataStorage().get(factory(level.getServer()), ID);
    }



    @SubscribeEvent public static void ownerDied(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) invalidateOwner(player);
    }

    @SubscribeEvent public static void ownerCloned(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) invalidateOwner(player);
    }

    private static void invalidateOwner(ServerPlayer owner) {
        var registry = getExisting(owner.serverLevel());
        if (registry == null || registry.unreadableOriginal != null) return;
        OwnerEntry group = registry.entries.get(owner.getUUID());
        if (group == null || group.unreadableOriginal != null) return;
        for (Entry entry : group.slots) {
            var state = entry.ledger.snapshot().state();
            if (state == DisplacedNeedleLedger.State.RESERVED || state == DisplacedNeedleLedger.State.PENDING
                    || state == DisplacedNeedleLedger.State.RETURNING) continue;
            if (entry.live != null) {
                entry.live.markHarmless(entry.live.identity());
                CrossSubstitution.retire(entry.live);
            }
            entry.ledger.retireAttack();
            entry.attack = null;
        }
        registry.setDirty();
    }

    /**
     * Removes exactly one actual plain offhand item before native join callbacks. guard may
     * call external code; callbackFreePayment MUST be a narrow, callback-free resource commit.
     * No failed attempt restores an inventory/CP snapshot or refunds a fabricated item.
     */
    public static Optional<DisplacedNeedleEntity> deploy(ServerPlayer owner, Vec3 position,
                                                         BooleanSupplier guard, BooleanSupplier callbackFreePayment) {
        if (!validOwner(owner, null) || !finite(position) || guard == null || callbackFreePayment == null)
            return Optional.empty();
        ServerLevel level = owner.serverLevel();
        CrossNeedleRegistry registry = get(level);
        OwnerEntry group = registry.reserveOwner(owner.getUUID());
        if (group == null || group.deploying) return Optional.empty();
        Entry entry = registry.freeEntry(group);
        if (entry == null) return Optional.empty();
        var token = entry.ledger.reserve();
        if (token == null) return Optional.empty();
        registry.setDirty();
        ItemStack hand = owner.getOffhandItem();
        ItemStack before = hand.copy();
        if (!plainStack(hand)) {
            entry.ledger.cancel(token, null, null, DisplacedNeedleLedger.Disposal.NO_CANDIDATE);
            registry.setDirty();
            return Optional.empty();
        }
        DisplacedNeedleEntity candidate = null;
        UUID candidateId = null;
        DisplacedNeedleEntity.Identity candidateIdentity = null;
        boolean published = false;
        boolean uncertain = false;
        boolean paymentCommitted = false;
        group.deploying = true;
        try {
            if (!guard.getAsBoolean() || !validOwner(owner, level) || owner.getOffhandItem() != hand
                    || !ItemStack.matches(before, hand)) return Optional.empty();
            entry.escrow = hand.split(1);
            if (!entry.ledger.escrow(token, entry.escrow.getCount(), DisplacedNeedleEntity.isPlainNeedle(entry.escrow)))
                return Optional.empty();
            ItemStack remaining = hand.copy();
            entry.dimension = level.dimension().location().toString();
            registry.setDirty();
            candidate = new DisplacedNeedleEntity(TeleporterNeedleEntities.CROSS_NEEDLE.get(), level);
            candidate.setPos(position);
            if (!candidate.prepare(owner, token.nonce(), 1, entry.escrow)) return Optional.empty();
            candidateId = candidate.getUUID();
            candidateIdentity = candidate.identity();
            int nativeId = candidate.getId();
            StagePose pose = new StagePose(position, candidate.getBoundingBox(), candidate.getDeltaMovement(), candidate.getYRot(), candidate.getXRot());
            if (!entry.ledger.publishPending(token, candidateId, candidate)) return Optional.empty();
            published = true;
            entry.live = candidate;
            registry.setDirty(); // pending identity exists before every native EntityJoinLevelEvent
            boolean accepted = level.addFreshEntity(candidate);
            if (!accepted || !registry.spawnValid(owner, level, entry, candidate, candidateId, nativeId,
                    hand, remaining, pose) || !guard.getAsBoolean()
                    || !registry.spawnValid(owner, level, entry, candidate, candidateId, nativeId, hand, remaining, pose))
                return Optional.empty();
            if (!callbackFreePayment.getAsBoolean()) return Optional.empty();
            paymentCommitted = true;
            if (!registry.spawnValid(owner, level, entry, candidate, candidateId, nativeId, hand, remaining, pose))
                return Optional.empty();
            // The supplied commit has no callbacks. No code capable of mutating inventory or
            // replacing the candidate may be introduced between final validation and activation.
            var permit = entry.ledger.activate(token, candidateId, candidate, true, true);
            if (permit == null) return Optional.empty();
            entry.attack = permit;
            entry.escrow = ItemStack.EMPTY; // the exact stack is now solely in native entity custody
            if (!candidate.admit(candidateIdentity, registry)) {
                entry.attack = null;
                entry.ledger.ownerDied(); // payment committed, ambiguous activation: never refund
                registry.setDirty();
                return Optional.empty();
            }
            entry.escrow = ItemStack.EMPTY;
            registry.setDirty();
            return Optional.of(candidate);
        } catch (RuntimeException failure) {
            // A callback may already have published, repositioned, or charged before throwing.
            // Unknown completion never authorizes restoring stock or another launch.
            uncertain = true;
            entry.attack = null;
            entry.ledger.retireAttack();
            if (candidate != null) candidate.markHarmless(candidate.identity());
            registry.setDirty();
            return Optional.empty();
        } catch (Error failure) {
            uncertain = true;
            entry.attack = null;
            entry.ledger.retireAttack();
            if (candidate != null) candidate.markHarmless(candidate.identity());
            registry.setDirty();
            throw failure;
        } finally {
            var state = entry.ledger.snapshot().state();
            if (state == DisplacedNeedleLedger.State.RESERVED || state == DisplacedNeedleLedger.State.PENDING) {
                boolean disposed = candidate == null;
                if (candidate != null) {
                    try {
                        candidate.discard();
                        disposed = candidate.isRemoved() && !registry.presentAnywhere(candidate);
                        if (disposed && candidateIdentity != null) {
                            ItemStack released = candidate.releaseUnadmittedEscrow(candidateIdentity);
                            disposed = released == entry.escrow;
                        }
                    } catch (RuntimeException failure) { disposed = false; }
                }
                var proof = uncertain || paymentCommitted ? DisplacedNeedleLedger.Disposal.UNRESOLVED
                        : !published ? (disposed ? DisplacedNeedleLedger.Disposal.NO_CANDIDATE
                        : DisplacedNeedleLedger.Disposal.UNRESOLVED)
                        : (disposed ? DisplacedNeedleLedger.Disposal.CANDIDATE_DISCARDED
                        : DisplacedNeedleLedger.Disposal.UNRESOLVED);
                entry.returning = entry.ledger.cancel(token, candidateId, candidate, proof);
                if (disposed) entry.live = null;
                registry.setDirty();
                if (entry.returning != null) registry.returnEscrow(owner, entry);
            }
            group.deploying = false;
        }
    }

    /** Read-only refusal classification; OUTSTANDING includes unloaded or ambiguous identity. */
    public static Availability availability(ServerPlayer owner) {
        if (!validOwner(owner, null)) return Availability.OWNER_INVALID;
        var registry = getExisting(owner.serverLevel());
        if (registry != null) {
            if (registry.unreadableOriginal != null) return Availability.CORRUPT;
            OwnerEntry group = registry.entries.get(owner.getUUID());
            if (group != null) {
                if (group.unreadableOriginal != null) return Availability.CORRUPT;
                boolean outstanding = group.deploying, refund = false;
                for (Entry entry : group.slots) {
                    var saved = entry.ledger.snapshot();
                    if (entry.unreadableOriginal != null || saved.state() == DisplacedNeedleLedger.State.BLOCKED)
                        return Availability.CORRUPT;
                    refund |= saved.state() == DisplacedNeedleLedger.State.RETURNING
                            && saved.custody() == DisplacedNeedleLedger.Custody.RETURN_PENDING
                            && saved.generation() == 0 && saved.entityId() == null
                            && DisplacedNeedleEntity.isPlainNeedle(entry.escrow);
                    outstanding |= saved.unsettled();
                }
                if (refund) return Availability.REFUND_PENDING;
                if (outstanding) return Availability.OUTSTANDING;
            } else if (registry.entries.size() >= MAX_OWNERS) return Availability.REGISTRY_FULL;
        }
        return plainStack(owner.getOffhandItem()) ? Availability.READY : Availability.NO_MEDIUM;
    }

    private OwnerEntry reserveOwner(UUID owner) {
        if (unreadableOriginal != null) return null;
        OwnerEntry group = entries.get(owner);
        if (group != null) return group.unreadableOriginal == null ? group : null;
        if (entries.size() >= MAX_OWNERS) return null;
        group = new OwnerEntry(owner);
        entries.put(owner, group);
        return group;
    }

    private Entry freeEntry(OwnerEntry group) {
        for (Entry entry : group.slots) {
            if (entry.unreadableOriginal != null) return null;
            var state = entry.ledger.snapshot().state();
            if (state == DisplacedNeedleLedger.State.BLOCKED || state == DisplacedNeedleLedger.State.LOST
                    || state == DisplacedNeedleLedger.State.RESERVED || state == DisplacedNeedleLedger.State.PENDING
                    || state == DisplacedNeedleLedger.State.RETURNING || state == DisplacedNeedleLedger.State.PICKING_UP) return null;
            if (state == DisplacedNeedleLedger.State.ACTIVE
                    && (entry.live == null || !bound(entry, entry.live, entry.live.identity()))) return null;
        }
        for (Entry entry : group.slots) {
            var state = entry.ledger.snapshot().state();
            if (state == DisplacedNeedleLedger.State.EMPTY || state == DisplacedNeedleLedger.State.SPENT) return entry;
        }
        return null;
    }

    private Entry entry(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity) {
        if (unreadableOriginal != null || medium == null || !medium.isCrossNeedle() || identity == null
                || !identity.equals(medium.identity()) || !identity.entityId().equals(medium.getUUID())) return null;
        OwnerEntry group = entries.get(identity.owner());
        if (group == null || group.unreadableOriginal != null) return null;
        Entry match = null;
        for (Entry slot : group.slots) {
            var saved = slot.ledger.snapshot();
            if (saved.owner().equals(identity.owner()) && saved.nonce() == identity.nonce()
                    && saved.generation() == identity.generation() && identity.entityId().equals(saved.entityId())) {
                if (match != null || slot.unreadableOriginal != null) return null;
                match = slot;
            }
        }
        return match;
    }

    private boolean spawnValid(ServerPlayer owner, ServerLevel level, Entry entry, DisplacedNeedleEntity candidate,
                               UUID id, int nativeId, ItemStack hand, ItemStack remaining, StagePose pose) {
        return candidate.isCrossNeedle() && getExisting(level) == this && validOwner(owner, level)
                && entry.ledger.snapshot().state() == DisplacedNeedleLedger.State.PENDING
                && entry.live == candidate && candidate.getUUID().equals(id) && candidate.getId() == nativeId
                && candidate.level() == level && !candidate.isRemoved() && level.getEntity(id) == candidate
                && level.getEntity(nativeId) == candidate && candidate.identity() != null
                && candidate.position().equals(pose.position()) && candidate.getBoundingBox().equals(pose.bounds())
                && candidate.getDeltaMovement().equals(pose.velocity()) && candidate.getYRot() == pose.yaw() && candidate.getXRot() == pose.pitch()
                && level.getWorldBorder().isWithinBounds(candidate.getBoundingBox())
                && candidate.getBoundingBox().minY >= level.getMinBuildHeight() && candidate.getBoundingBox().maxY <= level.getMaxBuildHeight()
                && candidate.identity().owner().equals(owner.getUUID()) && candidate.identity().nonce() == entry.ledger.snapshot().nonce()
                && candidate.identity().entityId().equals(id) && candidate.identity().generation() == 1 && candidate.getOwner() == owner
                && candidate.holdsEscrow(candidate.identity(), entry.escrow) && !candidate.isAdmitted()
                && owner.getOffhandItem() == hand && ItemStack.matches(hand, remaining);
    }

    public static boolean retryRefund(ServerPlayer owner) {
        if (!validOwner(owner, null)) return false;
        var registry = getExisting(owner.serverLevel());
        if (registry == null || registry.unreadableOriginal != null) return false;
        OwnerEntry group = registry.entries.get(owner.getUUID());
        if (group == null || group.unreadableOriginal != null || group.deploying) return false;
        boolean settled = false;
        group.deploying = true;
        try {
            for (Entry entry : group.slots) settled |= registry.returnEscrow(owner, entry);
            return settled;
        } finally { group.deploying = false; }
    }

    private boolean returnEscrow(ServerPlayer owner, Entry entry) {
        // A completed safe cancellation persisted before any inventory insertion is not
        // an ambiguous refund: exact original escrow remains and there is no candidate UUID.
        if(entry!=null&&entry.unreadableOriginal==null&&entry.returning==null&&DisplacedNeedleEntity.isPlainNeedle(entry.escrow)){
            var s=entry.ledger.snapshot();
            if(s.state()==DisplacedNeedleLedger.State.RETURNING&&s.custody()==DisplacedNeedleLedger.Custody.RETURN_PENDING
                    &&s.generation()==0&&s.entityId()==null){
                entry.returning=entry.ledger.recoverUnpublished(s,1,true,DisplacedNeedleLedger.Disposal.NO_CANDIDATE);
                setDirty();
            }
        }
        if (entry == null || entry.unreadableOriginal != null || entry.returning == null
                || !validOwner(owner, null) || !entry.ledger.owner().equals(owner.getUUID())
                || !DisplacedNeedleEntity.isPlainNeedle(entry.escrow)) return false;
        var inventory = owner.getInventory();
        if (inventory.getSlotWithRemainingSpace(entry.escrow) < 0 && inventory.getFreeSlot() < 0) return false;
        if (!entry.ledger.beginReturn(entry.returning)) return false;
        setDirty(); // a saved interruption here is REFUND_ESCROW, never automatically refundable
        ItemStack original = entry.escrow;
        try {
            boolean inserted = inventory.add(original);
            if (inserted && original.isEmpty()) {
                boolean settled = entry.ledger.finishReturn(entry.returning, true);
                entry.returning = null;
                entry.escrow = ItemStack.EMPTY;
                setDirty();
                return settled;
            }
            if (DisplacedNeedleEntity.isPlainNeedle(original)) {
                entry.ledger.finishReturn(entry.returning, false);
                setDirty();
            } else {
                entry.returning = null; // ambiguous partial/foreign mutation cannot be retried
                setDirty();
            }
        } catch (RuntimeException failure) {
            entry.returning = null;
            setDirty(); // preserve the actual remaining object and the ambiguous custody state
        }
        return false;
    }

    /** Only the current closed publication guard may ignore its own inert candidate in clearance. */
    static DisplacedNeedleEntity pending(ServerPlayer owner) {
        if (!validOwner(owner, null)) return null;
        var registry = getExisting(owner.serverLevel());
        if (registry == null || registry.unreadableOriginal != null) return null;
        OwnerEntry group = registry.entries.get(owner.getUUID());
        if (group == null || group.unreadableOriginal != null || !group.deploying) return null;
        for (Entry entry : group.slots) {
            if (entry.unreadableOriginal != null || entry.ledger.snapshot().state() != DisplacedNeedleLedger.State.PENDING) continue;
            DisplacedNeedleEntity medium = entry.live;
            if (medium != null && medium.isCrossNeedle() && !medium.isRemoved() && !medium.isAdmitted()
                    && medium.identity() != null && medium.identity().owner().equals(owner.getUUID())
                    && medium.identity().nonce() == entry.ledger.snapshot().nonce()
                    && owner.serverLevel().getEntity(medium.getUUID()) == medium
                    && owner.serverLevel().getEntity(medium.getId()) == medium) return medium;
        }
        return null;
    }

    /** Never loads a chunk, invents an entity, refunds, or creates an absent registry. */
    public static List<DisplacedNeedleEntity> existing(ServerPlayer owner) {
        if (!validOwner(owner, null)) return List.of();
        var registry = getExisting(owner.serverLevel());
        if (registry == null || registry.unreadableOriginal != null) return List.of();
        OwnerEntry group = registry.entries.get(owner.getUUID());
        if (group == null || group.unreadableOriginal != null) return List.of();
        List<DisplacedNeedleEntity> found = new ArrayList<>(SLOT_COUNT);
        for (Entry entry : group.slots) {
            if (entry.unreadableOriginal != null || entry.ledger.snapshot().state() != DisplacedNeedleLedger.State.ACTIVE) continue;
            UUID id = entry.ledger.snapshot().entityId();
            ServerLevel level = registry.level(entry.dimension);
            Entity entity = level == null || id == null ? null : level.getEntity(id);
            if (entity instanceof DisplacedNeedleEntity needle && needle.isCrossNeedle() && registry.bind(needle)) found.add(needle);
        }
        return List.copyOf(found);
    }

    public static boolean authentic(ServerPlayer owner, DisplacedNeedleEntity medium) {
        if (!validOwner(owner, null) || medium == null || !medium.isCrossNeedle()
                || medium.identity() == null || !medium.identity().owner().equals(owner.getUUID())
                || medium.getOwner() != owner || medium.level() != owner.level()) return false;
        var registry = getExisting(owner.serverLevel());
        return registry != null && registry.bind(medium)
                && registry.bound(registry.entry(medium, medium.identity()), medium, medium.identity());
    }

    public static boolean tryBind(DisplacedNeedleEntity medium) {
        if (medium == null || !medium.isCrossNeedle() || !(medium.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        var registry = getExisting(level);
        return registry != null && registry.bind(medium);
    }

    private boolean bind(DisplacedNeedleEntity medium) {
        var identity = medium.identity();
        if (!medium.isCrossNeedle() || unreadableOriginal != null || identity == null || !(medium.level() instanceof ServerLevel level)
                || medium.isRemoved() || level.getServer() != server || getExisting(level) != this
                || !(medium.getOwner() instanceof ServerPlayer owner) || !validOwner(owner, level)
                || !owner.getUUID().equals(identity.owner()) || level.getEntity(identity.entityId()) != medium
                || level.getEntity(medium.getId()) != medium || !medium.getUUID().equals(identity.entityId())
                || !DisplacedNeedleEntity.isPlainNeedle(medium.mediumSnapshot())) return false;
        Entry entry = entry(medium, identity);
        if (entry == null || entry.unreadableOriginal != null || !entry.dimension.equals(level.dimension().location().toString())) return false;
        var snapshot = entry.ledger.snapshot();
        if (snapshot.state() != DisplacedNeedleLedger.State.ACTIVE || snapshot.nonce() != identity.nonce()
                || snapshot.generation() != identity.generation() || !Objects.equals(snapshot.entityId(), identity.entityId())) return false;
        if (entry.live == medium) return medium.resumeTracking(identity, this);
        if (entry.live != null) return false;
        if (!entry.ledger.bindLive(identity.owner(), identity.nonce(), identity.entityId(), medium, true)) return false;
        entry.live = medium;
        if (!medium.admit(identity, this)) {
            entry.ledger.unload(identity.owner(), identity.nonce(), identity.entityId(), medium);
            entry.live = null;
            return false;
        }
        setDirty();
        return true;
    }


    /**
     * NEW323 moves one existing loaded entity, retaining its UUID, nonce, generation,
     * original item and native lifetime. This server-internal API is for closed controller
     * callers only; it is not packet authority. guard MUST prove the frozen coordinate,
     * range, destination/support/hazard/body clearance, loaded route and resource preflight
     * through a bounded, non-loading world view. It may call external code; payment must not.
     * A refused attempt leaves custody untouched and old damage authority retired.
     */
    public static boolean recoverExisting(ServerPlayer owner, DisplacedNeedleEntity medium,
                                           DisplacedNeedleEntity.Identity expected, Vec3 destination,
                                           BooleanSupplier guard, BooleanSupplier callbackFreePayment) {
        if (!validOwner(owner, null) || medium == null || !medium.isCrossNeedle() || expected == null || !finite(destination)
                || guard == null || callbackFreePayment == null || medium.impactBusy()
                || medium.level() != owner.level() || medium.getOwner() != owner
                || !finite(medium.position()) || medium.position().distanceToSqr(destination) <= 1e-8
                || !expected.owner().equals(owner.getUUID()) || !expected.equals(medium.identity())) return false;
        var registry = getExisting(owner.serverLevel());
        if (registry == null || !authentic(owner, medium)) return false;
        Entry entry = registry.entry(medium, expected);
        var attempt = medium.beginRecovery(expected);
        if (attempt == null) return false;
        try {
            // Both authorities are irrevocably retired before invoking any caller code.
            CrossSubstitution.retire(medium);
            entry.attack = null;
            entry.ledger.retireAttack();
            registry.setDirty();
            if (!recoveryValid(registry, entry, owner, medium, expected, attempt)
                    || !recoveryDestination(owner, medium, destination)
                    || !guard.getAsBoolean()
                    || !recoveryDestination(owner, medium, destination)
                    || !recoveryValid(registry, entry, owner, medium, expected, attempt)) return false;
            if (!callbackFreePayment.getAsBoolean()) return false;
            // Defensive validation also refuses a misbehaving payment without rolling
            // back external changes. Correct payment is a callback-free resource commit.
            if (!recoveryValid(registry, entry, owner, medium, expected, attempt)) return false;
            return medium.relocateRecovery(attempt, destination) && validOwner(owner, owner.serverLevel())
                    && getExisting(owner.serverLevel()) == registry
                    && medium.level() == owner.level() && medium.getOwner() == owner
                    && expected.equals(medium.identity()) && registry.bound(entry, medium, expected)
                    && medium.position().equals(destination) && DisplacedNeedleEntity.isPlainNeedle(medium.mediumSnapshot())
                    && !medium.isContactEnabled();
        } catch (RuntimeException failure) {
            return false;
        } finally {
            medium.endRecovery(attempt);
        }
    }

    private static boolean recoveryValid(CrossNeedleRegistry registry, Entry entry, ServerPlayer owner,
                                         DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity expected,
                                         DisplacedNeedleEntity.Recovery attempt) {
        return validOwner(owner, (ServerLevel)medium.level()) && getExisting(owner.serverLevel()) == registry
                && medium.level() == owner.level()
                && medium.getOwner() == owner && expected.equals(medium.identity())
                && registry.bound(entry, medium, expected) && medium.recoveryUnchanged(attempt);
    }

    /** Primitive loaded-only checks; the closed guard supplies all bounded collision proof. */
    private static boolean recoveryDestination(ServerPlayer owner, DisplacedNeedleEntity medium, Vec3 destination) {
        if (!finite(medium.position())) return false;
        ServerLevel level = owner.serverLevel();
        AABB body = medium.getBoundingBox().move(destination.subtract(medium.position()));
        if (!level.getWorldBorder().isWithinBounds(body) || body.minY < level.getMinBuildHeight()
                || body.maxY > level.getMaxBuildHeight()) return false;
        for (AABB volume : new AABB[]{medium.getBoundingBox(), body.inflate(1.001)}) {
            int minX = net.minecraft.util.Mth.floor(volume.minX) >> 4, maxX = net.minecraft.util.Mth.floor(volume.maxX) >> 4;
            int minZ = net.minecraft.util.Mth.floor(volume.minZ) >> 4, maxZ = net.minecraft.util.Mth.floor(volume.maxZ) >> 4;
            if (maxX - minX > 1 || maxZ - minZ > 1) return false;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        // Do not call native Level collision helpers here: arbitrary block-shape callbacks
        // would receive a raw loading Level and bypass the controller's bounded world view.
        return true;
    }

    @Override public boolean canContact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, Entity target) {
        return bound(entry(medium, identity), medium, identity) && CrossSubstitution.canContact(medium, identity, target);
    }
    @Override public void onContact(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, net.minecraft.world.phys.EntityHitResult hit) {
        if (bound(entry(medium, identity), medium, identity)) CrossSubstitution.contact(medium, identity, hit);
    }
    @Override public void onFlightRetired(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity) {
        Entry entry = entry(medium, identity);
        if (entry != null && entry.live == medium) CrossSubstitution.retire(medium);
    }
    static void retireContact(DisplacedNeedleEntity medium) {
        if (medium == null || !medium.isCrossNeedle() || !(medium.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || medium.identity() == null) return;
        var registry=getExisting(level); if(registry==null)return;
        Entry entry=registry.entry(medium, medium.identity());
        if(entry!=null&&entry.live==medium){entry.attack=null;entry.ledger.retireAttack();registry.setDirty();}
    }

    @Override public boolean canPickup(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, Player player) {
        return medium != null && medium.isCrossNeedle() && identity != null && !medium.impactBusy()
                && medium.level() instanceof ServerLevel level && player instanceof ServerPlayer owner && validOwner(owner, level)
                && owner.getUUID().equals(identity.owner()) && bound(entry(medium, identity), medium, identity);
    }

    @Override public boolean beginPickup(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, Player player) {
        if (!canPickup(medium, identity, player)) return false;
        Entry entry = entry(medium, identity);
        entry.attack = null;
        entry.pickingUp = entry.ledger.beginPickup(identity.owner(), identity.nonce(), identity.entityId(), medium);
        if (entry.pickingUp == null) return false;
        setDirty();
        return true;
    }

    @Override public void onPickupFailed(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity,
                                         Player player, boolean originalUnchanged) {
        Entry entry = entry(medium, identity);
        if (entry == null || entry.live != medium || entry.pickingUp == null) return;
        if (originalUnchanged) entry.ledger.finishPickup(entry.pickingUp, false);
        else entry.ledger.ownerDied();
        entry.pickingUp = null;
        setDirty();
    }

    @Override public void onPickedUp(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, Player player) {
        Entry entry = entry(medium, identity);
        if (entry == null || entry.live != medium || entry.pickingUp == null) return;
        if (entry.ledger.finishPickup(entry.pickingUp, true)) entry.live = null;
        entry.pickingUp = null;
        setDirty();
    }

    @Override public void onRemoved(DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity, Entity.RemovalReason reason) {
        Entry entry = entry(medium, identity);
        if (entry == null || entry.live != medium || !Objects.equals(entry.ledger.snapshot().entityId(), identity.entityId())) return;
        var snapshot=entry.ledger.snapshot();
        if(snapshot.nonce()!=identity.nonce()||snapshot.generation()!=identity.generation()
                ||!snapshot.owner().equals(identity.owner()))return;
        CrossSubstitution.retire(medium); // retire the matching transient controller in this native removal callback
        // Only this subclass's pinned native tickDespawn window proves whole-item destruction.
        // Generic discard/kill/missing/unload remains ambiguous and cannot free a deployment.
        if (reason == Entity.RemovalReason.DISCARDED && medium.authenticNativeDespawnRemoval())
            entry.ledger.settleEntity(identity.owner(),identity.nonce(),identity.entityId(),medium,DisplacedNeedleLedger.Resolution.CONSUMED);
        else if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER)
            entry.ledger.unload(identity.owner(), identity.nonce(), identity.entityId(), medium);
        else entry.ledger.ownerDied();
        entry.live = null;
        entry.pickingUp = null;
        entry.attack = null;
        setDirty();
    }

    /** Consumes only this exact slot's committed generation before any damage callback. */
    static boolean consumeContact(DisplacedNeedleEntity medium, long generation) {
        if (medium == null || !medium.isCrossNeedle() || !(medium.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        var registry = getExisting(level);
        var identity = medium.identity();
        if (registry == null || identity == null || identity.generation() != generation) return false;
        Entry entry = registry.entry(medium, identity);
        if (!registry.bound(entry, medium, identity) || entry.attack == null) return false;
        boolean claimed = entry.ledger.claimDamage(entry.attack, identity.owner(), identity.nonce(), identity.entityId(), medium);
        entry.attack = null;
        return claimed;
    }

    private boolean bound(Entry entry, DisplacedNeedleEntity medium, DisplacedNeedleEntity.Identity identity) {
        if (medium == null || !medium.isCrossNeedle() || entry == null || entry.unreadableOriginal != null || identity == null || entry.live != medium
                || !medium.isAdmitted() || medium.isRemoved() || !(medium.level() instanceof ServerLevel level)
                || level.getServer() != server || getExisting(level) != this || !identity.equals(medium.identity())
                || !identity.entityId().equals(medium.getUUID()) || !DisplacedNeedleEntity.isPlainNeedle(medium.mediumSnapshot())
                || level.getEntity(identity.entityId()) != medium
                || level.getEntity(medium.getId()) != medium || !entry.dimension.equals(level.dimension().location().toString())) return false;
        var snapshot = entry.ledger.snapshot();
        return snapshot.state() == DisplacedNeedleLedger.State.ACTIVE && snapshot.owner().equals(identity.owner())
                && snapshot.nonce() == identity.nonce() && snapshot.generation() == identity.generation()
                && Objects.equals(snapshot.entityId(), identity.entityId());
    }

    private boolean presentAnywhere(DisplacedNeedleEntity candidate) {
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(candidate.getUUID()) == candidate || level.getEntity(candidate.getId()) == candidate) return true;
        return false;
    }

    private ServerLevel level(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    /** Bounded, immutable inspection for diagnostics; inspecting never reserves or repairs. */
    public List<DisplacedNeedleLedger.Snapshot> snapshots(UUID owner) {
        OwnerEntry group = entries.get(owner);
        if (group == null) return List.of();
        List<DisplacedNeedleLedger.Snapshot> result = new ArrayList<>(SLOT_COUNT);
        for (Entry entry : group.slots) result.add(entry.ledger.snapshot());
        return List.copyOf(result);
    }
    public int retainedEscrowCount(UUID owner) {
        OwnerEntry group = entries.get(owner);
        if (group == null) return 0;
        int count = 0;
        for (Entry entry : group.slots) count += entry.escrow.getCount();
        return count;
    }
    public boolean isBlocked() { return unreadableOriginal != null; }
    public int ownerCount() { return entries.size(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        if (unreadableOriginal != null) return unreadableOriginal.copy();
        tag.putInt("version", VERSION);
        ListTag list = new ListTag();
        for (var pair : entries.entrySet()) {
            OwnerEntry group = pair.getValue();
            if (group.unreadableOriginal != null) { list.add(group.unreadableOriginal.copy()); continue; }
            CompoundTag savedOwner = new CompoundTag();
            savedOwner.putString("owner", pair.getKey().toString());
            ListTag slots = new ListTag();
            for (int index = 0; index < SLOT_COUNT; index++) {
                Entry entry = group.slots[index];
                if (entry.unreadableOriginal != null) { slots.add(entry.unreadableOriginal.copy()); continue; }
                CompoundTag saved = new CompoundTag();
                saved.putInt("slot", index);
                saved.putString("dimension", entry.dimension);
                CompoundTag ledger = new CompoundTag();
                entry.ledger.toDecimalMap().forEach(ledger::putString);
                saved.put("ledger", ledger);
                saved.put("escrow", entry.escrow.isEmpty() ? new CompoundTag() : entry.escrow.save(lookup));
                slots.add(saved);
            }
            savedOwner.put("slots", slots);
            list.add(savedOwner);
        }
        tag.put("entries", list);
        return tag;
    }

    /** Public decode seam uses the exact production SavedData format, never installs fixtures. */
    public static CrossNeedleRegistry load(MinecraftServer server, CompoundTag tag, HolderLookup.Provider lookup) {
        var registry = new CrossNeedleRegistry(server);
        if (!tag.getAllKeys().equals(ROOT_KEYS) || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
                || !tag.contains("entries", Tag.TAG_LIST)) return registry.blocked(tag);
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        if (list.size() != ((ListTag)tag.get("entries")).size() || list.size() > MAX_OWNERS) return registry.blocked(tag);
        Set<UUID> entities = new java.util.HashSet<>();
        for (int ownerIndex = 0; ownerIndex < list.size(); ownerIndex++) {
            CompoundTag savedOwner = list.getCompound(ownerIndex);
            UUID owner;
            try {
                owner = UUID.fromString(savedOwner.getString("owner"));
                if (!owner.toString().equals(savedOwner.getString("owner")) || registry.entries.containsKey(owner)) return registry.blocked(tag);
            } catch (IllegalArgumentException failure) { return registry.blocked(tag); }
            OwnerEntry group = new OwnerEntry(owner);
            registry.entries.put(owner, group);
            ListTag slots = savedOwner.getList("slots", Tag.TAG_COMPOUND);
            if (!savedOwner.getAllKeys().equals(OWNER_KEYS) || !savedOwner.contains("owner", Tag.TAG_STRING)
                    || !savedOwner.contains("slots", Tag.TAG_LIST) || slots.size() != SLOT_COUNT
                    || slots.size() != ((ListTag)savedOwner.get("slots")).size()) {
                group.unreadableOriginal = savedOwner.copy();
                continue;
            }
            boolean[] seen = new boolean[SLOT_COUNT];
            boolean invalidOwner = false;
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag saved = slots.getCompound(i);
                int slot = saved.getInt("slot");
                if (!saved.contains("slot", Tag.TAG_INT) || slot < 0 || slot >= SLOT_COUNT || seen[slot]) {
                    invalidOwner = true; break;
                }
                seen[slot] = true;
                Map<String, String> values = new HashMap<>();
                CompoundTag ledgerTag = saved.getCompound("ledger");
                for (String key : ledgerTag.getAllKeys()) if (ledgerTag.contains(key, Tag.TAG_STRING)) values.put(key, ledgerTag.getString(key));
                Entry entry = new Entry(DisplacedNeedleLedger.restore(owner, values));
                group.slots[slot] = entry;
                entry.dimension = saved.getString("dimension");
                CompoundTag escrowTag = saved.getCompound("escrow");
                entry.escrow = escrowTag.isEmpty() ? ItemStack.EMPTY : ItemStack.parse(lookup, escrowTag).orElse(ItemStack.EMPTY);
                var state = entry.ledger.snapshot();
                boolean invalid = !saved.getAllKeys().equals(ENTRY_KEYS) || !saved.contains("dimension", Tag.TAG_STRING)
                        || !saved.contains("ledger", Tag.TAG_COMPOUND) || !saved.contains("escrow", Tag.TAG_COMPOUND)
                        || values.size() != ledgerTag.size() || state.state() == DisplacedNeedleLedger.State.BLOCKED
                        || (!entry.dimension.isEmpty() && ResourceLocation.tryParse(entry.dimension) == null)
                        || (!escrowTag.isEmpty() && !DisplacedNeedleEntity.isPlainNeedle(entry.escrow))
                        || (state.generation() > 0 && !entry.escrow.isEmpty())
                        || (state.entityId() != null && entry.dimension.isEmpty());
                if (state.entityId() != null && !entities.add(state.entityId())) return registry.blocked(tag);
                if (invalid) { entry.unreadableOriginal = saved.copy(); invalidOwner = true; }
            }
            if (invalidOwner) group.unreadableOriginal = savedOwner.copy();
        }
        return registry;
    }

    private CrossNeedleRegistry blocked(CompoundTag original) {
        entries.clear();
        unreadableOriginal = original.copy();
        return this;
    }
    private static boolean plainStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0 && stack.is(cn.academy.port.AcademyCraft.NEEDLE.get())
                && stack.isComponentsPatchEmpty();
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static boolean validOwner(ServerPlayer owner, ServerLevel expected) {
        return owner != null && owner.getServer() != null && owner.getServer().isSameThread() && owner.isAlive()
                && !owner.isRemoved() && !owner.hasDisconnected() && owner.connection != null
                && !owner.isSpectator() && (expected == null || owner.serverLevel() == expected)
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Needle custody is server-thread only");
    }
}

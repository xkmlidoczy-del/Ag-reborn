package cn.academy.port.highpower.core;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure, single-owner NEW311 one-item custody and attack-generation ledger.
 *
 * <p>This class neither removes, creates, inserts, spawns nor copies an item.
 * The server adapter must escrow the <em>actual one plain academy:needle</em>
 * removed from the offhand, keep a pending candidate inert and unpickable, and
 * call each confirmation only after checking the actual server result. Entity
 * proofs include both the persistent UUID and the exact current Java object.
 * Tokens are opaque capabilities of this ledger instance, never client data.
 *
 * <p>A successful native insertion/payment must precede activation. Before
 * activation, a candidate must be verifiably discarded before its escrow may
 * be returned. Once active, the native item is the only item custody and no
 * operation can issue a refund. Missing entities never cause recreation.
 *
 * <p>Persistence is deliberately conservative, not atomic across native player
 * and entity files. Interrupted native inventory transfers become LOST on
 * restoration. Unpublished original escrow may be reconciled only with an
 * actual retained-item proof and definitive candidate disposition, never a
 * missing/unloaded lookup. Metadata alone never authorizes an item grant.
 * An ACTIVE record restores without damage or a live object binding.
 */
public final class DisplacedNeedleLedger {
    public static final String SCHEMA_VERSION = "1";

    public enum State { EMPTY, RESERVED, PENDING, RETURNING, ACTIVE, PICKING_UP, SPENT, LOST, BLOCKED }
    public enum Custody { NONE, ESCROW, RETURN_PENDING, REFUND_ESCROW, ENTITY, PICKUP_ESCROW, RETURNED, CONSUMED, OUTSTANDING, UNKNOWN }
    /** A fact established by the adapter, not a requested disposition. */
    public enum Disposal { NO_CANDIDATE, CANDIDATE_DISCARDED, UNRESOLVED }
    /** No resolution asks the ledger to insert or return an item. */
    public enum Resolution { NATIVE_PICKUP, CONSUMED, DISCARDED }

    /** Not constructible, serializable or transferable to another ledger. */
    public static final class Token {
        private final DisplacedNeedleLedger ledger;
        private final long nonce;
        private Token(DisplacedNeedleLedger ledger, long nonce) {
            this.ledger = ledger;
            this.nonce = nonce;
        }
        public long nonce() { return nonce; }
    }

    /** The adapter retains the original one-item stack throughout refund retries. */
    public static final class ReturnTicket {
        private final DisplacedNeedleLedger ledger;
        private final long nonce;
        private ReturnTicket(DisplacedNeedleLedger ledger, long nonce) {
            this.ledger = ledger;
            this.nonce = nonce;
        }
        public long nonce() { return nonce; }
    }

    /** One native whole-item pickup attempt; no separate refund is ever issued. */
    public static final class PickupTicket {
        private final DisplacedNeedleLedger ledger;
        private final long nonce;
        private final State previousState;
        private PickupTicket(DisplacedNeedleLedger ledger) {
            this.ledger = ledger;
            this.nonce = ledger.nonce;
            this.previousState = ledger.state;
        }
        public long nonce() { return nonce; }
    }

    /** Authorizes at most this generation of this exact native entity. */
    public static final class AttackPermit {
        private final DisplacedNeedleLedger ledger;
        private final long nonce;
        private final long generation;
        private final UUID entityId;
        private final Object entity;
        private AttackPermit(DisplacedNeedleLedger ledger) {
            this.ledger = ledger;
            this.nonce = ledger.nonce;
            this.generation = ledger.generation;
            this.entityId = ledger.entityId;
            this.entity = ledger.liveEntity;
        }
        public long nonce() { return nonce; }
        public long generation() { return generation; }
        public UUID entityId() { return entityId; }
    }

    /** Bounded inspectable state; transient object identities and tokens are excluded. */
    public record Snapshot(UUID owner, long nonce, long generation, State state,
                           Custody custody, int removedItems, UUID entityId) {
        public int accountedItems() { return custody == Custody.NONE || custody == Custody.UNKNOWN ? 0 : 1; }
        public boolean unsettled() {
            return state == State.RESERVED || state == State.PENDING || state == State.RETURNING
                    || state == State.ACTIVE || state == State.PICKING_UP || state == State.LOST || state == State.BLOCKED;
        }
    }

    private static final Set<String> KEYS = Set.of("version", "owner", "nonce", "generation",
            "state", "custody", "removedItems", "entity");

    private final UUID owner;
    private long nonce;
    private long generation;
    private State state = State.EMPTY;
    private Custody custody = Custody.NONE;
    private int removedItems;
    private UUID entityId;
    private Object liveEntity;
    private Token reservation;
    private ReturnTicket returnTicket;
    private PickupTicket pickupTicket;
    private boolean ownerDiedDuringPickup;
    private AttackPermit attack;
    private boolean attackLive;
    private boolean reprojectionPending;

    /** Use only for a genuinely absent ledger, never as a fallback for corrupt metadata. */
    public DisplacedNeedleLedger(UUID owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

    public UUID owner() { return owner; }
    public synchronized Snapshot snapshot() {
        return new Snapshot(owner, nonce, generation, state, custody, removedItems, entityId);
    }

    /** Consumes a nonce even if the following reservation is cancelled. */
    public synchronized Token reserve() {
        if ((state != State.EMPTY && state != State.SPENT) || nonce == Long.MAX_VALUE) return null;
        nonce++;
        generation = 0;
        state = State.RESERVED;
        custody = Custody.NONE;
        removedItems = 0;
        entityId = null;
        liveEntity = null;
        returnTicket = null;
        pickupTicket = null;
        ownerDiedDuringPickup = false;
        retireAttack();
        reservation = new Token(this, nonce);
        return reservation;
    }

    /**
     * Call once after removal into retained escrow. Count/type validation is a
     * second guard; the adapter must check canonical plain-item metadata itself.
     * A false result never permits destroying or returning a fabricated stack.
     */
    public synchronized boolean escrow(Token token, int actualRemovedItems, boolean canonicalPlainNeedle) {
        if (!isReservation(token) || state != State.RESERVED || custody != Custody.NONE
                || actualRemovedItems != 1 || !canonicalPlainNeedle) return false;
        removedItems = 1;
        custody = Custody.ESCROW;
        return true;
    }

    /** Register the single inert candidate before calling any external spawn callback. */
    public synchronized boolean publishPending(Token token, UUID candidateId, Object exactCandidate) {
        if (!isReservation(token) || state != State.RESERVED || custody != Custody.ESCROW
                || candidateId == null || exactCandidate == null) return false;
        entityId = candidateId;
        liveEntity = exactCandidate;
        state = State.PENDING;
        return true;
    }

    /**
     * Commit only after native spawn acceptance, same-object world lookup and
     * payment have all succeeded. Failed validation does not activate anything.
     * There is no automatic cancellation or refund on a false result.
     */
    public synchronized AttackPermit activate(Token token, UUID candidateId, Object exactCandidate,
                                               boolean spawnValidated, boolean paymentCommitted) {
        if (!isReservation(token) || state != State.PENDING || !spawnValidated || !paymentCommitted
                || !matchesEntity(candidateId, exactCandidate)) return null;
        state = State.ACTIVE;
        custody = Custody.ENTITY;
        reservation = null;
        generation = 1;
        attack = new AttackPermit(this);
        attackLive = true;
        return attack;
    }

    /**
     * Cancel before activation only. A pending candidate requires an exact
     * identity proof and confirmed discard. If its fate is uncertain, custody
     * becomes outstanding and no refund ticket exists. Replayed tokens fail.
     */
    public synchronized ReturnTicket cancel(Token token, UUID candidateId, Object exactCandidate,
                                             Disposal disposal) {
        if (!isReservation(token) || (state != State.RESERVED && state != State.PENDING)
                || disposal == null) return null;
        if (state == State.PENDING && !matchesEntity(candidateId, exactCandidate)) return null;
        boolean safe = state == State.RESERVED ? disposal == Disposal.NO_CANDIDATE
                : disposal == Disposal.CANDIDATE_DISCARDED;
        reservation = null;
        retireAttack();
        if (removedItems == 0) {
            state = State.SPENT;
            custody = Custody.NONE;
        } else if (safe) {
            state = State.RETURNING;
            custody = Custody.RETURN_PENDING;
            entityId = null;
            liveEntity = null;
            returnTicket = new ReturnTicket(this, nonce);
            return returnTicket;
        } else {
            state = State.LOST;
            custody = Custody.OUTSTANDING;
        }
        return null;
    }

    /**
     * Enter before the inventory callback, transferring custody to retained
     * refund escrow. Reentrant calls cannot begin another insertion. This does
     * not grant an item: only the original stack may be offered to inventory.
     */
    public synchronized boolean beginReturn(ReturnTicket ticket) {
        if (!isReturnTicket(ticket) || custody != Custody.RETURN_PENDING) return false;
        custody = Custody.REFUND_ESCROW;
        return true;
    }

    /**
     * Confirm the complete one-item transfer, or retain exactly that same stack
     * for a retry when insertion fails/full inventory. Do not report false if
     * insertion actually happened; native insertion results are authoritative.
     */
    public synchronized boolean finishReturn(ReturnTicket ticket, boolean entireOriginalItemInserted) {
        if (!isReturnTicket(ticket) || custody != Custody.REFUND_ESCROW) return false;
        if (entireOriginalItemInserted) {
            state = State.SPENT;
            custody = Custody.RETURNED;
            returnTicket = null;
        } else custody = Custody.RETURN_PENDING;
        return true;
    }

    /**
     * Reconcile an interrupted, never-active original escrow. expected is a
     * compare-and-set snapshot, not authority to create an item. The adapter
     * must have the exact original retained one-item stack and establish the
     * stated disposition in the native world. A saved pending UUID being absent
     * or unloaded is UNRESOLVED, never CANDIDATE_DISCARDED. In-flight refund
     * insertion and any formerly active medium are deliberately ineligible.
     * No reservation token or new nonce is minted by this operation.
     */
    public synchronized ReturnTicket recoverUnpublished(Snapshot expected, int exactOriginalEscrowCount,
                                                         boolean canonicalPlainNeedle, Disposal disposal) {
        if (expected == null || !snapshot().equals(expected) || reservation != null || returnTicket != null
                || generation != 0 || removedItems != 1 || exactOriginalEscrowCount != 1 || !canonicalPlainNeedle)
            return null;
        boolean safe = (state == State.RESERVED && custody == Custody.ESCROW && disposal == Disposal.NO_CANDIDATE)
                || (state == State.PENDING && custody == Custody.ESCROW && disposal == Disposal.CANDIDATE_DISCARDED)
                || (state == State.RETURNING && custody == Custody.RETURN_PENDING && disposal == Disposal.NO_CANDIDATE);
        if (!safe) return null;
        state = State.RETURNING;
        custody = Custody.RETURN_PENDING;
        entityId = null;
        liveEntity = null;
        returnTicket = new ReturnTicket(this, nonce);
        return returnTicket;
    }

    /** Read-only exact live binding proof; no attack or pickup permission is granted. */
    public synchronized boolean isBound(UUID observedOwner, long observedNonce, UUID observedId, Object exactEntity) {
        return (state == State.ACTIVE || state == State.PICKING_UP || state == State.LOST)
                && matchesProof(observedOwner, observedNonce, observedId, exactEntity);
    }

    /** Retires damage before the native inventory callback and excludes pickup reentry. */
    public synchronized PickupTicket beginPickup(UUID observedOwner, long observedNonce, UUID observedId,
                                                 Object exactEntity) {
        if ((state != State.ACTIVE && state != State.LOST) || generation == 0
                || !matchesProof(observedOwner, observedNonce, observedId, exactEntity)) return null;
        retireAttack();
        pickupTicket = new PickupTicket(this);
        ownerDiedDuringPickup = false;
        state = State.PICKING_UP;
        custody = Custody.PICKUP_ESCROW;
        return pickupTicket;
    }

    /** False retains the same native item; true records its verified whole-item pickup. */
    public synchronized boolean finishPickup(PickupTicket ticket, boolean entireOriginalItemInserted) {
        if (state != State.PICKING_UP || ticket == null || ticket != pickupTicket
                || ticket.ledger != this || ticket.nonce != nonce) return false;
        pickupTicket = null;
        if (entireOriginalItemInserted) {
            state = State.SPENT;
            custody = Custody.RETURNED;
            liveEntity = null;
        } else {
            state = ownerDiedDuringPickup ? State.LOST : ticket.previousState;
            custody = state == State.ACTIVE ? Custody.ENTITY : Custody.OUTSTANDING;
        }
        ownerDiedDuringPickup = false;
        return true;
    }

    /**
     * Retire the previous damaging generation before aim validation or spending.
     * A failed/aborted reprojection leaves the old attack retired. The entity and
     * its one-item custody do not change. One pending reprojection blocks reentry.
     */
    public synchronized AttackPermit beginReprojection(UUID observedOwner, long observedNonce,
                                                       UUID observedId, Object exactEntity) {
        if (state != State.ACTIVE || !matchesProof(observedOwner, observedNonce, observedId, exactEntity)
                || reprojectionPending) return null;
        retireAttack();
        if (generation == Long.MAX_VALUE) return null;
        generation++;
        attack = new AttackPermit(this);
        reprojectionPending = true;
        return attack;
    }

    /** Failed validation/spending consumes this attempt and never revives its predecessor. */
    public synchronized boolean completeReprojection(AttackPermit permit, boolean positionValidated,
                                                       boolean paymentCommitted) {
        if (!isCurrentPermit(permit) || !reprojectionPending) return false;
        reprojectionPending = false;
        attackLive = positionValidated && paymentCommitted;
        if (!attackLive) attack = null;
        return attackLive;
    }

    public synchronized boolean abortReprojection(AttackPermit permit) {
        if (!isCurrentPermit(permit) || !reprojectionPending) return false;
        retireAttack();
        return true;
    }

    /** Check immediately before applying damage, not just when scheduling it. */
    public synchronized boolean mayDamage(AttackPermit permit, UUID observedOwner, long observedNonce,
                                          UUID observedId, Object exactEntity) {
        return attackLive && !reprojectionPending && isCurrentPermit(permit)
                && matchesProof(observedOwner, observedNonce, observedId, exactEntity);
    }

    /** Consumes a hit before any external damage callback, closing same-hit reentry/replay. */
    public synchronized boolean claimDamage(AttackPermit permit, UUID observedOwner, long observedNonce,
                                            UUID observedId, Object exactEntity) {
        if (!mayDamage(permit, observedOwner, observedNonce, observedId, exactEntity)) return false;
        retireAttack();
        return true;
    }

    /** Retirement alone never consumes, refunds, or duplicates the physical needle. */
    public synchronized void retireAttack() {
        attackLive = false;
        reprojectionPending = false;
        attack = null;
    }

    /** Unload/missing lookup retires damage but preserves the outstanding registered UUID. */
    public synchronized boolean unload(UUID observedOwner, long observedNonce, UUID observedId, Object exactEntity) {
        if (state != State.ACTIVE || !matchesProof(observedOwner, observedNonce, observedId, exactEntity)) return false;
        retireAttack();
        liveEntity = null;
        return true;
    }

    /**
     * Rebind only after server validation of a loaded entity's owner, nonce,
     * UUID, plain one-item payload, and uniqueness. This never spawns or attacks.
     * A second object cannot replace an already bound object, even with its UUID.
     */
    public synchronized boolean bindLive(UUID observedOwner, long observedNonce, UUID observedId,
                                          Object exactEntity, boolean nativeEntityValidated) {
        if (state != State.ACTIVE || liveEntity != null || !nativeEntityValidated || exactEntity == null
                || !owner.equals(observedOwner) || nonce != observedNonce || !Objects.equals(entityId, observedId)) return false;
        liveEntity = exactEntity;
        retireAttack();
        return true;
    }

    /**
     * Owner death/unknown native removal closes attacks and refund paths. Keep
     * the identity for later verified disposition; unresolved custody blocks
     * another deployment. This intentionally does not infer item destruction.
     */
    public synchronized void ownerDied() {
        retireAttack();
        // A native inventory callback may reenter here. Keep its barrier and
        // ticket until that callback settles; LOST must not reopen pickup.
        if (state == State.PICKING_UP) {
            ownerDiedDuringPickup = true;
            return;
        }
        reservation = null;
        returnTicket = null;
        pickupTicket = null;
        if (state == State.BLOCKED || state == State.EMPTY || state == State.SPENT) return;
        state = removedItems == 1 ? State.LOST : State.SPENT;
        custody = removedItems == 1 ? Custody.OUTSTANDING : Custody.NONE;
    }

    /**
     * Call after verified whole-item native pickup/consumption/discard. Identity
     * checks precede settlement, and this method never awards an inventory item.
     * Unbound/missing identities cannot be settled by guessing that they vanished.
     */
    public synchronized boolean settleEntity(UUID observedOwner, long observedNonce, UUID observedId,
                                              Object exactEntity, Resolution resolution) {
        if ((state != State.ACTIVE && state != State.LOST) || resolution == null
                || (resolution == Resolution.NATIVE_PICKUP && generation == 0)
                || !matchesProof(observedOwner, observedNonce, observedId, exactEntity)) return false;
        retireAttack();
        reservation = null;
        returnTicket = null;
        pickupTicket = null;
        state = State.SPENT;
        custody = resolution == Resolution.NATIVE_PICKUP ? Custody.RETURNED : Custody.CONSUMED;
        liveEntity = null;
        return true;
    }

    public synchronized Map<String, String> toDecimalMap() {
        return Map.of("version", SCHEMA_VERSION, "owner", owner.toString(),
                "nonce", Long.toString(nonce), "generation", Long.toString(generation),
                "state", state.name(), "custody", custody.name(),
                "removedItems", Integer.toString(removedItems), "entity", entityId == null ? "" : entityId.toString());
    }

    /**
     * Nonthrowing fail-closed decode. Unknown keys/schema, noncanonical numbers,
     * inconsistent custody, or another owner's data produce BLOCKED, not EMPTY.
     */
    public static DisplacedNeedleLedger restore(UUID expectedOwner, Map<String, String> fields) {
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        try {
            if (fields == null || !fields.keySet().equals(KEYS) || !SCHEMA_VERSION.equals(fields.get("version")))
                return blocked(expectedOwner);
            UUID savedOwner = uuid(fields.get("owner"));
            if (!savedOwner.equals(expectedOwner)) return blocked(expectedOwner);
            DisplacedNeedleLedger ledger = new DisplacedNeedleLedger(expectedOwner);
            ledger.nonce = counter(fields.get("nonce"));
            ledger.generation = counter(fields.get("generation"));
            ledger.state = State.valueOf(fields.get("state"));
            ledger.custody = Custody.valueOf(fields.get("custody"));
            String removed = fields.get("removedItems");
            if (!"0".equals(removed) && !"1".equals(removed)) return blocked(expectedOwner);
            ledger.removedItems = Integer.parseInt(removed);
            String entity = Objects.requireNonNull(fields.get("entity"));
            ledger.entityId = entity.isEmpty() ? null : uuid(entity);
            if (!ledger.consistent()) return blocked(expectedOwner);
            if (ledger.state == State.PICKING_UP || ledger.custody == Custody.REFUND_ESCROW) {
                ledger.state = State.LOST;
                ledger.custody = Custody.OUTSTANDING;
            } else if (ledger.state == State.RESERVED && ledger.removedItems == 0) {
                ledger.state = State.SPENT;
                ledger.custody = Custody.NONE;
            }
            // Runtime capabilities, attack liveness, and object bindings do not survive decode.
            return ledger;
        } catch (IllegalArgumentException | NullPointerException ex) {
            return blocked(expectedOwner);
        }
    }

    private static DisplacedNeedleLedger blocked(UUID owner) {
        DisplacedNeedleLedger ledger = new DisplacedNeedleLedger(owner);
        ledger.state = State.BLOCKED;
        ledger.custody = Custody.UNKNOWN;
        ledger.nonce = Long.MAX_VALUE;
        return ledger;
    }

    private boolean consistent() {
        if (state == State.BLOCKED)
            return custody == Custody.UNKNOWN && nonce == Long.MAX_VALUE && generation == 0 && removedItems == 0 && entityId == null;
        if (state == State.EMPTY)
            return nonce == 0 && generation == 0 && custody == Custody.NONE && removedItems == 0 && entityId == null;
        if (nonce == 0 || removedItems != snapshot().accountedItems()) return false;
        return switch (state) {
            case RESERVED -> generation == 0 && entityId == null
                    && ((removedItems == 0 && custody == Custody.NONE) || (removedItems == 1 && custody == Custody.ESCROW));
            case PENDING -> generation == 0 && removedItems == 1 && custody == Custody.ESCROW && entityId != null;
            case RETURNING -> generation == 0 && removedItems == 1 && entityId == null
                    && (custody == Custody.RETURN_PENDING || custody == Custody.REFUND_ESCROW);
            case ACTIVE -> generation > 0 && removedItems == 1 && custody == Custody.ENTITY && entityId != null;
            case PICKING_UP -> generation > 0 && removedItems == 1 && custody == Custody.PICKUP_ESCROW && entityId != null;
            case SPENT -> (removedItems == 0 && generation == 0 && custody == Custody.NONE && entityId == null)
                    || (removedItems == 1 && (custody == Custody.RETURNED || custody == Custody.CONSUMED)
                    && (generation == 0 || entityId != null));
            case LOST -> removedItems == 1 && custody == Custody.OUTSTANDING && (generation == 0 || entityId != null);
            default -> false;
        };
    }

    private boolean isReservation(Token token) {
        return token != null && token == reservation && token.ledger == this && token.nonce == nonce;
    }
    private boolean isReturnTicket(ReturnTicket ticket) {
        return state == State.RETURNING && ticket != null && ticket == returnTicket
                && ticket.ledger == this && ticket.nonce == nonce;
    }
    private boolean isCurrentPermit(AttackPermit permit) {
        return state == State.ACTIVE && permit != null && permit == attack && permit.ledger == this
                && permit.nonce == nonce && permit.generation == generation
                && matchesEntity(permit.entityId, permit.entity);
    }
    private boolean matchesEntity(UUID observedId, Object exactEntity) {
        return entityId != null && entityId.equals(observedId) && liveEntity != null && liveEntity == exactEntity;
    }
    private boolean matchesProof(UUID observedOwner, long observedNonce, UUID observedId, Object exactEntity) {
        return owner.equals(observedOwner) && nonce == observedNonce && matchesEntity(observedId, exactEntity);
    }
    private static long counter(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Noncanonical counter");
        return Long.parseLong(value);
    }
    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID");
        return id;
    }
}
